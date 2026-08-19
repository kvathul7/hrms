package com.axvon.hrms;

import com.axvon.hrms.entity.*;
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
 * Balances move only when a request crosses into or out of Approved. Getting this wrong
 * in either direction is expensive: charge twice and somebody loses days they never took,
 * refund twice and the quota stops meaning anything.
 */
@WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
class LeaveBalanceTest extends AxvonTestBase {

    private int used(Employee e, String type) {
        return balRepo.findByEmployeeIdAndLeaveTypeAndYear(e.getId(), type, LocalDate.now().getYear())
            .map(LeaveBalance::getUsed).orElse(0);
    }

    private long applyFor(Employee e, String type, LocalDate from, LocalDate to, int days) throws Exception {
        MvcResult r = mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "employeeId", e.getId(), "leaveType", type,
                    "fromDate", from.toString(), "toDate", to.toString(),
                    "totalDays", days, "reason", "test"))))
            .andExpect(status().isOk())
            .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("a pending request spends nothing")
    void pendingDoesNotSpend() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        applyFor(e, "Casual", d, d.plusDays(2), 3);

        assertThat(used(e, "Casual")).isZero();
    }

    @Test
    @DisplayName("approving spends the days; approving again does not spend them twice")
    void approveIsIdempotent() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        long id = applyFor(e, "Casual", d, d.plusDays(2), 3);

        mvc.perform(put("/leave/requests/" + id + "/approve")).andExpect(status().isOk());
        assertThat(used(e, "Casual")).isEqualTo(3);

        mvc.perform(put("/leave/requests/" + id + "/approve")).andExpect(status().isOk());
        assertThat(used(e, "Casual")).isEqualTo(3);
    }

    @Test
    @DisplayName("reversing an approval hands the days back")
    void rejectingAnApprovedRequestRefunds() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        long id = applyFor(e, "Casual", d, d.plusDays(2), 3);

        mvc.perform(put("/leave/requests/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(put("/leave/requests/" + id + "/reject")
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed\"}"))
            .andExpect(status().isOk());

        assertThat(used(e, "Casual")).isZero();
    }

    @Test
    @DisplayName("rejecting a request that was never approved refunds nothing")
    void rejectingPendingDoesNotGoNegative() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        long id = applyFor(e, "Casual", d, d.plusDays(2), 3);

        mvc.perform(put("/leave/requests/" + id + "/reject")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk());

        assertThat(used(e, "Casual")).isZero();
    }

    @Test
    @DisplayName("deleting an approved request hands the days back")
    void deletingApprovedRefunds() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        long id = applyFor(e, "Casual", d, d.plusDays(2), 3);
        mvc.perform(put("/leave/requests/" + id + "/approve")).andExpect(status().isOk());

        mvc.perform(delete("/leave/requests/" + id)).andExpect(status().isOk());

        assertThat(used(e, "Casual")).isZero();
    }

    @Test
    @DisplayName("a request beyond the remaining balance is refused")
    void quotaIsEnforced() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "employeeId", e.getId(), "leaveType", "Sick",
                    "fromDate", d.toString(), "toDate", d.plusDays(44).toString(),
                    "totalDays", 45, "reason", "too many"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 10 days")));

        assertThat(leaveRepo.count()).isZero();
    }

    @Test
    @DisplayName("leave types with no quota are never blocked")
    void untrackedTypesAreUnlimited() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);

        // 60 days of unpaid leave: no allowance exists to exceed, so it must go through.
        long id = applyFor(e, "Unpaid", d, d.plusDays(59), 60);
        mvc.perform(put("/leave/requests/" + id + "/approve")).andExpect(status().isOk());

        assertThat(leaveRepo.findById(id)).get()
            .extracting(LeaveRequest::getStatus).isEqualTo(LeaveRequest.Status.Approved);
    }

    @Test
    @DisplayName("a quota raised in settings applies to the running year")
    void quotaChangeAppliesImmediately() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        CompanySettings s = settings();
        s.setCasualLeaveQuota(20);
        settingsRepo.save(s);

        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(1);
        // 15 days would exceed the default quota of 12 but fits inside the new 20.
        applyFor(e, "Casual", d, d.plusDays(14), 15);

        assertThat(leaveRepo.count()).isEqualTo(1);
        assertThat(balRepo.findByEmployeeIdAndLeaveTypeAndYear(e.getId(), "Casual", LocalDate.now().getYear()))
            .get().extracting(LeaveBalance::getTotalAllowed).isEqualTo(20);
    }

    @Test
    @DisplayName("an end date before the start date is refused")
    void backwardsRangeRefused() throws Exception {
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "50000");
        LocalDate d = LocalDate.now().withMonth(9).withDayOfMonth(10);

        mvc.perform(post("/leave/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of(
                    "employeeId", e.getId(), "leaveType", "Casual",
                    "fromDate", d.toString(), "toDate", d.minusDays(3).toString(),
                    "totalDays", 4, "reason", "backwards"))))
            .andExpect(status().isBadRequest());

        assertThat(leaveRepo.count()).isZero();
    }
}
