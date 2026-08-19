package com.axvon.hrms;

import com.axvon.hrms.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The trail. A pay change has to be recoverable after the fact from two places that
 * cannot disagree: the salary history and the audit log.
 */
@WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
class AuditAndSalaryTest extends AxvonTestBase {

    private List<String> actions() {
        return auditRepo.findAll().stream().map(AuditLog::getAction).toList();
    }

    @Test
    @DisplayName("a revision updates the employee and records the history")
    void revisionUpdatesAndRecords() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");

        mvc.perform(post("/salary-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "employeeId", e.getId(), "newSalary", "62000", "reason", "Annual increment"))))
            .andExpect(status().isOk());

        assertThat(empRepo.findById(e.getId())).get()
            .extracting(Employee::getBasicSalary).isEqualTo(new BigDecimal("62000.00"));
        assertThat(revRepo.findByEmployeeIdOrderByEffectiveDateDesc(e.getId())).singleElement()
            .satisfies(r -> {
                assertThat(r.getOldSalary()).isEqualByComparingTo("57000");
                assertThat(r.getNewSalary()).isEqualByComparingTo("62000");
                assertThat(r.getRevisedBy()).isEqualTo("hr@test.io");
            });
        assertThat(actions()).contains("SALARY_CHANGED");
    }

    @Test
    @DisplayName("a pay change made through the ordinary edit form is still recorded")
    void editingSalaryOnTheFormAlsoRecords() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");

        // The revision endpoint is not the only door into basicSalary, so the employee
        // form has to write the same history or the trail can be side-stepped.
        mvc.perform(put("/employees/" + e.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"basicSalary\":65000}"))
            .andExpect(status().isOk());

        assertThat(revRepo.findByEmployeeIdOrderByEffectiveDateDesc(e.getId())).hasSize(1);
        assertThat(actions()).contains("SALARY_CHANGED");
    }

    @Test
    @DisplayName("an edit that leaves pay alone writes no salary history")
    void nonSalaryEditWritesNoRevision() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");

        mvc.perform(put("/employees/" + e.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"Kochi\"}"))
            .andExpect(status().isOk());

        assertThat(revRepo.findByEmployeeIdOrderByEffectiveDateDesc(e.getId())).isEmpty();
        assertThat(actions()).contains("EMPLOYEE_UPDATED").doesNotContain("SALARY_CHANGED");
    }

    @Test
    @DisplayName("a negative salary is refused")
    void negativeSalaryRefused() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");

        mvc.perform(post("/salary-revisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("employeeId", e.getId(), "newSalary", "-500"))))
            .andExpect(status().isBadRequest());

        assertThat(revRepo.count()).isZero();
    }

    @Test
    @DisplayName("creating and deleting an employee both leave a trace")
    void lifecycleIsAudited() throws Exception {
        mvc.perform(post("/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"New\",\"lastName\":\"Hire\",\"email\":\"new@test.io\"}"))
            .andExpect(status().isOk());
        Employee created = empRepo.findByEmail("new@test.io").orElseThrow();

        mvc.perform(delete("/employees/" + created.getId())).andExpect(status().isOk());

        assertThat(actions()).contains("EMPLOYEE_CREATED", "EMPLOYEE_DELETED");
        // The deletion record keeps the name, since the row it pointed at is gone.
        assertThat(auditRepo.findAll()).anySatisfy(a -> {
            if ("EMPLOYEE_DELETED".equals(a.getAction()))
                assertThat(a.getOldValue()).contains("new@test.io");
        });
    }

    @Test
    @DisplayName("deleting an employee takes their dependent rows with them")
    void deleteClearsChildRows() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        mark(e, java.time.LocalDate.of(2026, 8, 4), Attendance.AttStatus.Present);
        approvedLeave(e, "Casual", java.time.LocalDate.of(2026, 8, 10), java.time.LocalDate.of(2026, 8, 11));
        docRepo.save(EmployeeDocument.builder().employee(e).docType("Photo").fileName("p.png")
            .contentType("image/png").sizeBytes(4L).data("AAAA").uploadedBy("hr@test.io").build());

        // Every one of these holds a foreign key back to the employee; H2 enforces them,
        // so missing any single table makes staff undeletable with a 500.
        mvc.perform(delete("/employees/" + e.getId())).andExpect(status().isOk());

        assertThat(empRepo.count()).isZero();
        assertThat(attRepo.count()).isZero();
        assertThat(leaveRepo.count()).isZero();
        assertThat(docRepo.count()).isZero();
        assertThat(balRepo.count()).isZero();
    }

    @Test
    @DisplayName("the audit log filters by action and pages")
    @WithMockUser(username = "owner@test.io", roles = "SUPER_ADMIN")
    void auditIsFilterableAndPaged() throws Exception {
        for (int i = 0; i < 7; i++)
            mvc.perform(post("/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"P" + i + "\",\"lastName\":\"Q\",\"email\":\"p" + i + "@test.io\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/audit").param("action", "EMPLOYEE_CREATED").param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalRows").value(7))
            .andExpect(jsonPath("$.data.rows.length()").value(5))
            .andExpect(jsonPath("$.data.totalPages").value(2));

        mvc.perform(get("/audit").param("action", "NOTHING_LIKE_THIS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalRows").value(0));
    }

    @Test
    @DisplayName("an approved resignation moves the employee off the active roll and back")
    void resignationFlow() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");

        String body = json.writeValueAsString(Map.of("employeeId", e.getId(), "reason", "Moving on"));
        var res = mvc.perform(post("/resignations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        long id = json.readTree(res.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(put("/resignations/" + id + "/approve")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        assertThat(empRepo.findById(e.getId())).get()
            .satisfies(x -> {
                assertThat(x.getStatus()).isEqualTo(Employee.Status.Resigned);
                assertThat(x.getLastWorkingDay()).isNotNull();
            });

        // Reversing it must put them back, or they stay Resigned and vanish from payroll.
        mvc.perform(put("/resignations/" + id + "/reject")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        assertThat(empRepo.findById(e.getId())).get()
            .satisfies(x -> {
                assertThat(x.getStatus()).isEqualTo(Employee.Status.Active);
                assertThat(x.getLastWorkingDay()).isNull();
            });
    }

    @Test
    @DisplayName("a second open resignation is refused")
    void oneOpenResignationAtATime() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        String body = json.writeValueAsString(Map.of("employeeId", e.getId(), "reason", "First"));

        mvc.perform(post("/resignations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(post("/resignations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());

        assertThat(resignRepo.count()).isEqualTo(1);
    }
}
