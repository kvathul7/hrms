package com.axvon.hrms.service;

import com.axvon.hrms.entity.Employee;
import com.axvon.hrms.entity.LeaveRequest;
import com.axvon.hrms.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Filing a leave request, once it has been decided *whose* leave it is.
 *
 * This was lifted out of {@code LeaveController.apply} when the HR Assistant arrived, so
 * that the chat tool and the ordinary form go through exactly one copy of the rules:
 * date sanity, the day count, and the quota check in rule 25. Duplicating that in the
 * tool would let the two drift — the assistant would eventually approve a request the
 * form rejects, or spend a balance the form would not.
 *
 * Authorisation deliberately stays OUT of here. Who may file for whom is a property of
 * the caller, not of the request: the controller resolves it from the Authentication
 * (rule 26) and the assistant forces the signed-in employee. Both then hand a settled
 * {@link Employee} to this method.
 */
@Service
@RequiredArgsConstructor
public class LeaveApplicationService {

    private final LeaveRequestRepository repo;
    private final LeaveBalanceService balances;
    private final AuditService audit;

    /**
     * @param requestedDays the caller's own day count, or null to derive it from the dates.
     *                      The form sends one because it excludes weekends; the assistant
     *                      does not, and gets the inclusive span.
     * @throws IllegalArgumentException on a bad date range or an exhausted quota. The
     *         messages are user-facing: GlobalExceptionHandler turns them into the 400
     *         body, and the assistant relays them verbatim.
     */
    public LeaveRequest apply(Employee emp, String leaveType, LocalDate from, LocalDate to,
                              Integer requestedDays, String reason) {
        if (emp == null) throw new IllegalArgumentException("Employee not found");
        if (leaveType == null || leaveType.isBlank()) throw new IllegalArgumentException("Choose a leave type");
        if (from == null || to == null) throw new IllegalArgumentException("Both a start and an end date are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date is before the start date");

        int days = requestedDays != null ? requestedDays : (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        if (days < 1) throw new IllegalArgumentException("A leave request must cover at least one day");

        // Quota check. Types with no quota (Unpaid, Maternity, Paternity) return null
        // and are never blocked.
        Integer left = balances.remaining(emp.getId(), leaveType, from.getYear());
        if (left != null && left <= 0)
            throw new IllegalArgumentException("No " + leaveType + " leave left for " + from.getYear() + " — the balance is 0");
        if (left != null && days > left)
            throw new IllegalArgumentException(days + " days requested but only " + left + " days of " + leaveType + " leave remain");

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(emp);
        lr.setLeaveType(leaveType);
        lr.setFromDate(from);
        lr.setToDate(to);
        lr.setTotalDays(days);
        lr.setReason(reason == null || reason.isBlank() ? "Not stated" : reason);
        lr.setStatus(LeaveRequest.Status.Pending);
        LeaveRequest saved = repo.save(lr);
        audit.log("LEAVE_APPLIED", "LeaveRequest", saved.getId(), null,
            emp.getFullName() + " · " + leaveType + " · " + days + "d");
        return saved;
    }
}
