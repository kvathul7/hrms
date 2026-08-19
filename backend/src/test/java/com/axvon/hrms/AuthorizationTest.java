package com.axvon.hrms;

import com.axvon.hrms.entity.Employee;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The role rules, including the one that was actually broken: /leave/apply used to take
 * employeeId straight from the request body, so any signed-in account could file — and
 * then read back — leave in a colleague's name.
 */
class AuthorizationTest extends AxvonTestBase {

    private Map<String, Object> leaveBody(Long employeeId) {
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        return Map.of("employeeId", employeeId, "leaveType", "Casual",
            "fromDate", d.toString(), "toDate", d.plusDays(1).toString(),
            "totalDays", 2, "reason", "test");
    }

    @Test
    @DisplayName("an employee cannot file leave in a colleague's name")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void employeeCannotApplyForSomebodyElse() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        Employee colleague = employee("Priya", "Sharma", "priya@test.io", "60000");

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(leaveBody(colleague.getId()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("You can only apply for your own leave"));

        assertThat(leaveRepo.count()).isZero();
    }

    @Test
    @DisplayName("an employee can file their own leave")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void employeeCanApplyForThemselves() throws Exception {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(leaveBody(me.getId()))))
            .andExpect(status().isOk());

        assertThat(leaveRepo.findAll()).singleElement()
            .satisfies(l -> assertThat(l.getEmployee().getEmail()).isEqualTo("arjun@test.io"));
    }

    @Test
    @DisplayName("HR can file leave on somebody else's behalf")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void hrCanApplyForAnyone() throws Exception {
        Employee other = employee("Priya", "Sharma", "priya@test.io", "60000");

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(leaveBody(other.getId()))))
            .andExpect(status().isOk());

        assertThat(leaveRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a manager approves leave but does not file it for others")
    @WithMockUser(username = "mgr@test.io", roles = "MANAGER")
    void managerCannotApplyForOthers() throws Exception {
        Employee other = employee("Priya", "Sharma", "priya@test.io", "60000");

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(leaveBody(other.getId()))))
            .andExpect(status().is4xxClientError());

        assertThat(leaveRepo.count()).isZero();
    }

    @Test
    @DisplayName("an employee sees their own salary and nobody else's")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void salaryIsMaskedExceptYourOwn() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        employee("Priya", "Sharma", "priya@test.io", "60000");

        MvcResult r = mvc.perform(get("/employees")).andExpect(status().isOk()).andReturn();
        JsonNode rows = json.readTree(r.getResponse().getContentAsString()).path("data");

        for (JsonNode row : rows) {
            if (row.path("email").asText().equals("arjun@test.io"))
                assertThat(row.path("basicSalary").asDouble()).isEqualTo(50000.0);
            else
                assertThat(row.path("basicSalary").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("HR sees every salary")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void hrSeesEverySalary() throws Exception {
        employee("Arjun", "Mehta", "arjun@test.io", "50000");
        employee("Priya", "Sharma", "priya@test.io", "60000");

        MvcResult r = mvc.perform(get("/employees")).andExpect(status().isOk()).andReturn();
        JsonNode rows = json.readTree(r.getResponse().getContentAsString()).path("data");

        for (JsonNode row : rows) assertThat(row.path("basicSalary").isNull()).isFalse();
    }

    @Test
    @DisplayName("pay, audit and backup are closed to an ordinary employee")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void privilegedEndpointsAreClosed() throws Exception {
        mvc.perform(get("/payroll")).andExpect(status().isForbidden());
        mvc.perform(get("/audit")).andExpect(status().isForbidden());
        mvc.perform(get("/backup/export")).andExpect(status().isForbidden());
        mvc.perform(get("/candidates")).andExpect(status().isForbidden());
        mvc.perform(put("/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HR cannot rewrite company settings — that is the owner's alone")
    @WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
    void settingsAreOwnerOnly() throws Exception {
        mvc.perform(put("/settings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"companyName\":\"Nope\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an employee cannot read a colleague's salary history or documents")
    @WithMockUser(username = "arjun@test.io", roles = "EMPLOYEE")
    void payHistoryIsScopedToYou() throws Exception {
        Employee me = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        Employee other = employee("Priya", "Sharma", "priya@test.io", "60000");

        mvc.perform(get("/salary-revisions/employee/" + me.getId())).andExpect(status().isOk());
        mvc.perform(get("/salary-revisions/employee/" + other.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/documents/employee/" + other.getId())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated caller gets 401, not 403")
    void anonymousGets401() throws Exception {
        // api.js clears the session on 401 only; a 403 here would leave a signed-out
        // browser looping against endpoints it can never reach.
        mvc.perform(get("/employees")).andExpect(status().isUnauthorized());
    }
}
