package com.axvon.hrms;

import com.axvon.hrms.entity.*;
import com.axvon.hrms.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Shared setup for the integration tests.
 *
 * Deliberately NOT annotated @Transactional. A rollback-per-test wrapper would hide the
 * exact class of defect these tests exist to catch: the backup restore failed because JPA
 * defers deletes until flush, and flush ordering only misbehaves when the work actually
 * commits. Each test therefore wipes the tables itself and works against committed state.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AxvonTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    @Autowired protected EmployeeRepository empRepo;
    @Autowired protected DepartmentRepository deptRepo;
    @Autowired protected AttendanceRepository attRepo;
    @Autowired protected LeaveRequestRepository leaveRepo;
    @Autowired protected LeaveBalanceRepository balRepo;
    @Autowired protected PayrollRepository payRepo;
    @Autowired protected PerformanceReviewRepository perfRepo;
    @Autowired protected SalaryRevisionRepository revRepo;
    @Autowired protected ResignationRepository resignRepo;
    @Autowired protected EmployeeDocumentRepository docRepo;
    @Autowired protected HolidayRepository holRepo;
    @Autowired protected CandidateRepository candRepo;
    @Autowired protected RosterEmployeeRepository rosterRepo;
    @Autowired protected AuditLogRepository auditRepo;
    @Autowired protected UserRepository userRepo;
    @Autowired protected CompanySettingsRepository settingsRepo;

    /** Children before parents, same order the application's own reset uses. */
    @BeforeEach
    void wipe() {
        payRepo.deleteAllInBatch();
        attRepo.deleteAllInBatch();
        perfRepo.deleteAllInBatch();
        leaveRepo.deleteAllInBatch();
        balRepo.deleteAllInBatch();
        revRepo.deleteAllInBatch();
        docRepo.deleteAllInBatch();
        resignRepo.deleteAllInBatch();
        candRepo.deleteAllInBatch();
        holRepo.deleteAllInBatch();
        empRepo.deleteAllInBatch();
        rosterRepo.deleteAllInBatch();
        deptRepo.deleteAllInBatch();
        auditRepo.deleteAllInBatch();
        userRepo.deleteAllInBatch();
        settingsRepo.deleteAllInBatch();
    }

    protected Employee employee(String first, String last, String email, String salary) {
        return empRepo.save(Employee.builder()
            .empId("AXV" + Math.abs(email.hashCode() % 900 + 100))
            .firstName(first).lastName(last).email(email)
            .designation("Engineer").joinDate(LocalDate.of(2024, 1, 15))
            .status(Employee.Status.Active)
            .basicSalary(salary == null ? null : new BigDecimal(salary))
            .build());
    }

    protected Attendance mark(Employee e, LocalDate day, Attendance.AttStatus status) {
        return attRepo.save(Attendance.builder()
            .employee(e).attendanceDate(day).status(status).build());
    }

    protected LeaveRequest approvedLeave(Employee e, String type, LocalDate from, LocalDate to) {
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(e);
        lr.setLeaveType(type);
        lr.setFromDate(from);
        lr.setToDate(to);
        lr.setTotalDays((int) (to.toEpochDay() - from.toEpochDay()) + 1);
        lr.setReason("test");
        lr.setStatus(LeaveRequest.Status.Approved);
        return leaveRepo.save(lr);
    }

    /** The settings row the application would otherwise create on first read. */
    protected CompanySettings settings() {
        return settingsRepo.findById(1L)
            .orElseGet(() -> settingsRepo.save(CompanySettings.builder().id(1L).build()));
    }
}
