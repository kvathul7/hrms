package com.axvon.hrms;

import com.axvon.hrms.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The money. Every figure here is one somebody's salary depends on, so each is asserted
 * against a hand-computed value rather than against whatever the code happens to produce.
 */
@WithMockUser(username = "hr@test.io", roles = "HR_ADMIN")
class PayrollGenerationTest extends AxvonTestBase {

    private void runPayroll(String month) throws Exception {
        mvc.perform(post("/payroll/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payMonth\":\"" + month + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("earnings and deductions follow the configured rates")
    void ratesFromSettings() throws Exception {
        settings();                       // defaults: HRA 40, allowance 16.6, PF 12, TDS 9, PT 200
        employee("Arjun", "Mehta", "arjun@test.io", "57000");

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        // basic 57000 → HRA 40% = 22800, allowance 16.6% = 9462, gross = 89262
        assertThat(p.getHra()).isEqualByComparingTo("22800.00");
        assertThat(p.getSpecialAllowance()).isEqualByComparingTo("9462.00");
        assertThat(p.getGrossSalary()).isEqualByComparingTo("89262.00");
        // PF is 12% of basic, TDS 9% of gross, PT a flat 200.
        assertThat(p.getPfDeduction()).isEqualByComparingTo("6840.00");
        assertThat(p.getTdsDeduction()).isEqualByComparingTo("8033.58");
        assertThat(p.getProfessionalTax()).isEqualByComparingTo("200.00");
        // Gross is well over the ESI ceiling, so no ESI at all.
        assertThat(p.getEsiDeduction()).isEqualByComparingTo("0.00");
        assertThat(p.getNetSalary())
            .isEqualByComparingTo(p.getGrossSalary().subtract(p.getTotalDeduction()));
    }

    @Test
    @DisplayName("a changed rate in settings changes the next payroll run")
    void ratesAreNotHardcoded() throws Exception {
        CompanySettings s = settings();
        s.setHraPercent(new BigDecimal("50"));
        settingsRepo.save(s);
        employee("Arjun", "Mehta", "arjun@test.io", "57000");

        runPayroll("2026-08");

        // 50% of 57000, not the 40% that used to be a literal in the controller.
        assertThat(payRepo.findAll().get(0).getHra()).isEqualByComparingTo("28500.00");
    }

    @Test
    @DisplayName("ESI applies only under the configured ceiling")
    void esiUnderThreshold() throws Exception {
        settings();
        employee("Small", "Earner", "small@test.io", "10000");

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        // gross = 10000 + 4000 + 1660 = 15660, which is under 21000 → ESI at 0.75%
        assertThat(p.getGrossSalary()).isEqualByComparingTo("15660.00");
        assertThat(p.getEsiDeduction()).isEqualByComparingTo("117.45");
    }

    @Test
    @DisplayName("an absent day costs one day of basic pay")
    void absenceBecomesLossOfPay() throws Exception {
        settings();                       // 26 working days a month
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        mark(e, LocalDate.of(2026, 8, 4), Attendance.AttStatus.Absent);
        mark(e, LocalDate.of(2026, 8, 5), Attendance.AttStatus.Absent);
        mark(e, LocalDate.of(2026, 8, 6), Attendance.AttStatus.Present);

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        // 57000 / 26 = 2192.3077 a day, two days lost → 4384.62 to the nearest paisa.
        assertThat(p.getLopDeduction()).isEqualByComparingTo("4384.62");
        assertThat(p.getAbsentDays()).isEqualTo(2);
        assertThat(p.getPresentDays()).isEqualTo(1);
        assertThat(p.getPayableDays()).isEqualTo(24);
        assertThat(p.getTotalDeduction()).isEqualByComparingTo(
            p.getPfDeduction().add(p.getEsiDeduction()).add(p.getProfessionalTax())
             .add(p.getTdsDeduction()).add(p.getLopDeduction()));
    }

    @Test
    @DisplayName("a half day costs half a day of basic pay")
    void halfDayIsHalfTheDeduction() throws Exception {
        settings();
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        mark(e, LocalDate.of(2026, 8, 4), Attendance.AttStatus.Half_Day);

        runPayroll("2026-08");

        assertThat(payRepo.findAll().get(0).getLopDeduction()).isEqualByComparingTo("1096.15");
    }

    @Test
    @DisplayName("approved leave costs nothing, even when also marked absent")
    void approvedLeaveIsExempt() throws Exception {
        settings();
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        LocalDate day = LocalDate.of(2026, 8, 4);
        approvedLeave(e, "Casual", day, day);
        // Somebody also marked the day absent. The approved leave must win, or an
        // employee is charged for a day HR already signed off.
        mark(e, day, Attendance.AttStatus.Absent);

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        assertThat(p.getLopDeduction()).isEqualByComparingTo("0.00");
        assertThat(p.getPayableDays()).isEqualTo(26);
    }

    @Test
    @DisplayName("turning absence deduction off stops the charge")
    void deductionCanBeDisabled() throws Exception {
        CompanySettings s = settings();
        s.setDeductAbsentDays(false);
        settingsRepo.save(s);
        Employee e = employee("Arjun", "Mehta", "arjun@test.io", "57000");
        mark(e, LocalDate.of(2026, 8, 4), Attendance.AttStatus.Absent);

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        assertThat(p.getLopDeduction()).isEqualByComparingTo("0.00");
        assertThat(p.getAbsentDays()).isEqualTo(1);   // still recorded, just not charged
    }

    @Test
    @DisplayName("running the same month twice does not double-pay anybody")
    void generationIsIdempotent() throws Exception {
        settings();
        employee("Arjun", "Mehta", "arjun@test.io", "57000");

        runPayroll("2026-08");
        runPayroll("2026-08");

        List<Payroll> slips = payRepo.findAll();
        assertThat(slips).hasSize(1);
    }

    @Test
    @DisplayName("an employee with no salary recorded draws a zero payslip, not an error")
    void missingSalaryIsSafe() throws Exception {
        settings();
        employee("No", "Salary", "nosalary@test.io", null);

        runPayroll("2026-08");

        Payroll p = payRepo.findAll().get(0);
        assertThat(p.getGrossSalary()).isEqualByComparingTo("0.00");
        assertThat(p.getNetSalary()).isEqualByComparingTo("-200.00");   // professional tax only
    }

    @Test
    @DisplayName("a resigned employee is left out of the run")
    void onlyActiveEmployeesArePaid() throws Exception {
        settings();
        Employee gone = employee("Left", "Already", "gone@test.io", "40000");
        gone.setStatus(Employee.Status.Resigned);
        empRepo.save(gone);
        employee("Still", "Here", "here@test.io", "40000");

        runPayroll("2026-08");

        assertThat(payRepo.findAll()).hasSize(1);
        assertThat(payRepo.findAll().get(0).getEmployee().getEmail()).isEqualTo("here@test.io");
    }
}
