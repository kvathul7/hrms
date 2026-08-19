package com.axvon.hrms;

import com.axvon.hrms.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Backup is the last line of defence behind a "delete everything" button, so the
 * round-trip is asserted end to end rather than trusted.
 *
 * The regression this guards: restore originally used deleteAll(), and JPA defers those
 * removals until flush — which happens AFTER the inserts. The old rows were therefore
 * still present when the restored ones were written, and the whole restore died on the
 * unique department name.
 */
@WithMockUser(username = "owner@test.io", roles = "SUPER_ADMIN")
class BackupRestoreTest extends AxvonTestBase {

    private void seed() {
        Department eng = deptRepo.save(Department.builder().name("Engineering").headOfDept("Priya").build());
        deptRepo.save(Department.builder().name("Finance").build());
        Employee a = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        a.setDepartment(eng);
        a.setDateOfBirth(LocalDate.of(1995, 3, 14));
        empRepo.save(a);
        employee("Priya", "Sharma", "priya@test.io", "72000");
        mark(a, LocalDate.of(2026, 8, 4), Attendance.AttStatus.Present);
        approvedLeave(a, "Casual", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12));
        holRepo.save(Holiday.builder().name("Onam").holidayDate(LocalDate.of(2026, 9, 3))
            .type(Holiday.HolType.Festival).isActive(true).build());
        rosterRepo.save(RosterEmployee.builder().name("Rahul Kumar").source("EXCEL").isActive(true).build());
    }

    private JsonNode export() throws Exception {
        MvcResult r = mvc.perform(get("/backup/export")).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }

    private void restore(JsonNode payload) throws Exception {
        mvc.perform(post("/backup/restore").param("confirmPhrase", "RESTORE_AXVON_BACKUP")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("a backup restores over live data without colliding with it")
    void restoreOverExistingData() throws Exception {
        seed();
        JsonNode backup = export();

        // Restoring on top of the very rows the backup came from is the case that used
        // to fail: every unique key in the file already exists in the table.
        restore(backup);

        assertThat(deptRepo.count()).isEqualTo(2);
        assertThat(empRepo.count()).isEqualTo(2);
        assertThat(attRepo.count()).isEqualTo(1);
        assertThat(leaveRepo.count()).isEqualTo(1);
        assertThat(holRepo.count()).isEqualTo(1);
        assertThat(rosterRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a backup rebuilds an emptied system")
    void restoreIntoEmptySystem() throws Exception {
        seed();
        JsonNode backup = export();
        wipe();                                    // simulate the full reset

        restore(backup);

        assertThat(empRepo.count()).isEqualTo(2);
        Employee arjun = empRepo.findByEmail("arjun@test.io").orElseThrow();
        // Relations are rebuilt by natural key, not by the row ids in the file.
        assertThat(arjun.getDepartment()).isNotNull();
        assertThat(arjun.getDepartment().getName()).isEqualTo("Engineering");
        assertThat(arjun.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 3, 14));
        assertThat(attRepo.findByEmployeeId(arjun.getId())).hasSize(1);
        assertThat(leaveRepo.findByEmployeeId(arjun.getId())).hasSize(1);
    }

    @Test
    @DisplayName("restored employees come back unlinked from any login")
    void loginsAreNotRestored() throws Exception {
        seed();
        JsonNode backup = export();
        wipe();

        restore(backup);

        // Deliberate: restoring accounts could lock the operator out of their own system,
        // so the links are re-made by hand in Users & Access.
        assertThat(empRepo.findAll()).allSatisfy(e -> assertThat(e.getUser()).isNull());
    }

    @Test
    @DisplayName("a document survives the round trip with its bytes")
    void documentsRoundTrip() throws Exception {
        seed();
        Employee arjun = empRepo.findByEmail("arjun@test.io").orElseThrow();
        String bytes = "iVBORw0KGgoAAAANSUhEUg==";
        docRepo.save(EmployeeDocument.builder().employee(arjun).docType("Photo")
            .fileName("photo.png").contentType("image/png").sizeBytes(18L)
            .data(bytes).uploadedBy("owner@test.io").build());

        JsonNode backup = export();
        wipe();
        restore(backup);

        assertThat(docRepo.findAll()).singleElement().satisfies(d -> {
            assertThat(d.getFileName()).isEqualTo("photo.png");
            assertThat(d.getData()).isEqualTo(bytes);
            assertThat(d.getEmployee().getEmail()).isEqualTo("arjun@test.io");
        });
    }

    @Test
    @DisplayName("company settings survive the round trip")
    void settingsRoundTrip() throws Exception {
        CompanySettings s = settings();
        s.setCompanyName("Kerala Textiles Pvt Ltd");
        s.setHraPercent(new java.math.BigDecimal("50"));
        settingsRepo.save(s);
        seed();

        JsonNode backup = export();
        wipe();
        restore(backup);

        CompanySettings back = settingsRepo.findById(1L).orElseThrow();
        assertThat(back.getCompanyName()).isEqualTo("Kerala Textiles Pvt Ltd");
        assertThat(back.getHraPercent()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("restore refuses a file that is not an AXVON backup")
    void rejectsForeignFile() throws Exception {
        seed();
        long before = empRepo.count();

        mvc.perform(post("/backup/restore").param("confirmPhrase", "RESTORE_AXVON_BACKUP")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"format\":\"SOMETHING-ELSE\",\"employees\":[]}"))
            .andExpect(status().isBadRequest());

        assertThat(empRepo.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("restore refuses to run without the confirmation phrase")
    void requiresConfirmation() throws Exception {
        seed();
        JsonNode backup = export();
        long before = empRepo.count();

        mvc.perform(post("/backup/restore")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(backup)))
            .andExpect(status().isBadRequest());

        // Nothing was wiped on the way to being refused.
        assertThat(empRepo.count()).isEqualTo(before);
    }
}
