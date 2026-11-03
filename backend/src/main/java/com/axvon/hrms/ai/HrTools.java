package com.axvon.hrms.ai;

import com.axvon.hrms.entity.Employee;
import com.axvon.hrms.entity.LeaveBalance;
import com.axvon.hrms.entity.LeaveRequest;
import com.axvon.hrms.repository.EmployeeRepository;
import com.axvon.hrms.service.LeaveApplicationService;
import com.axvon.hrms.service.LeaveBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * The three things the AXVON HR Assistant can actually do.
 *
 * Every method here is a thin adapter: it resolves the subject from the token via
 * {@link AssistantIdentity}, converts the model's string arguments, and calls the service
 * the rest of the app already calls. No HR rule is reimplemented — quotas and the
 * balance arithmetic belong to {@link LeaveBalanceService}, and filing belongs to
 * {@link LeaveApplicationService}, which is the same path /leave/apply takes.
 *
 * Three security properties hold for all of them:
 *
 *  1. No tool accepts an employee id, name or email to act *as*. The subject is always
 *     the signed-in employee. A question cannot redirect a tool at a colleague.
 *  2. Nothing here is HR- or admin-only, so chat grants no privilege an employee does
 *     not already have in the UI. There is deliberately no tool to approve leave, run
 *     payroll, read the audit log or edit a record.
 *  3. findEmployee returns directory fields only. Salary appears on one record and one
 *     record alone — the caller's own (rule 27).
 *
 * Failures are returned as values rather than thrown. The model has to be able to tell
 * the person *why* it could not help ("your Casual balance is 0"), and a thrown
 * exception would surface as an opaque tool error instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HrTools {

    private final AssistantIdentity identity;
    private final EmployeeRepository empRepo;
    private final LeaveBalanceService balances;
    private final LeaveApplicationService applications;

    // ── 1. Leave balance ─────────────────────────────────────────────────────────
    @Tool(name = "get_my_leave_balance", description = """
        Returns the signed-in employee's own leave balances for a calendar year: the days
        allowed, the days already used, and the days remaining for each leave type.

        Use this for any question about how much leave the person has left, such as
        "how many casual leaves do I have?" or "can I still take sick leave this year?".

        Parameters:
          leaveType - optional. One of: Casual, Sick, Earned, Comp Off. Pass it to narrow
                      the answer to a single type; omit it (or pass null) to get every
                      tracked type. Types with no company quota — Unpaid, Maternity,
                      Paternity — are never returned, because they are not deducted from
                      an allowance; say they are unlimited rather than reporting zero.
          year      - optional. A four-digit calendar year such as 2026. Omit it for the
                      current year.

        This tool can only ever read the balance of the person you are talking to. It
        cannot read a colleague's balance, and you must not claim otherwise.
        """)
    public LeaveBalanceReport getMyLeaveBalance(
            @ToolParam(required = false, description = "Casual, Sick, Earned or Comp Off. Omit for all types.")
            String leaveType,
            @ToolParam(required = false, description = "Four-digit calendar year, e.g. 2026. Omit for the current year.")
            Integer year) {
        try {
            Employee me = identity.requireEmployee();
            int y = year != null ? year : balances.currentYear();
            String wanted = normaliseLeaveType(leaveType);

            if (leaveType != null && !leaveType.isBlank() && wanted == null)
                return LeaveBalanceReport.failed("\"" + leaveType + "\" is not a leave type with a quota. "
                    + "The tracked types are Casual, Sick, Earned and Comp Off. Unpaid, Maternity and "
                    + "Paternity leave have no quota and are not deducted from a balance.");

            List<Line> lines = new ArrayList<>();
            for (LeaveBalance b : balances.forEmployee(me.getId(), y)) {
                if (wanted != null && !wanted.equals(b.getLeaveType())) continue;
                lines.add(new Line(b.getLeaveType(),
                    b.getTotalAllowed() == null ? 0 : b.getTotalAllowed(),
                    b.getUsed() == null ? 0 : b.getUsed(),
                    b.getRemaining()));
            }
            return new LeaveBalanceReport(true, me.getFullName(), y, lines, null);
        } catch (AssistantIdentity.NotLinkedException e) {
            return LeaveBalanceReport.failed(e.getMessage());
        }
    }

    // ── 2. Apply for leave ───────────────────────────────────────────────────────
    @Tool(name = "apply_for_leave", description = """
        Files a new leave request for the signed-in employee and returns the created
        request's id and status. This WRITES to the HR database, so only call it when the
        person has clearly asked to apply, book or take leave — not when they are merely
        asking what their balance is.

        The request is created with status Pending. It is not leave until a manager or HR
        approves it, and this tool cannot approve it. Say so when you confirm.

        Parameters:
          leaveType - required. One of: Casual, Sick, Earned, Comp Off, Unpaid, Maternity,
                      Paternity.
          fromDate  - required. The first day of leave as ISO yyyy-MM-dd, e.g. 2026-11-10.
                      Resolve relative dates such as "next Monday" to an absolute date
                      yourself before calling; if you cannot work out which date the
                      person means, ask them instead of guessing.
          toDate    - required. The last day of leave as ISO yyyy-MM-dd, inclusive. For a
                      single day of leave pass the same date as fromDate. For "2 days from
                      the 10th" pass fromDate 2026-11-10 and toDate 2026-11-11.
          reason    - optional. The person's stated reason, in their own words.

        The company's quota rules are enforced on the server and the call will be refused
        if the balance is too low, so do not try to check the balance first and decide
        yourself — just call this and relay what comes back.
        """)
    public LeaveApplicationReceipt applyForLeave(
            @ToolParam(description = "Casual, Sick, Earned, Comp Off, Unpaid, Maternity or Paternity.")
            String leaveType,
            @ToolParam(description = "First day of leave, ISO yyyy-MM-dd.") String fromDate,
            @ToolParam(description = "Last day of leave, inclusive, ISO yyyy-MM-dd.") String toDate,
            @ToolParam(required = false, description = "The reason the person gave, if any.") String reason) {
        try {
            Employee me = identity.requireEmployee();

            LocalDate from, to;
            try {
                from = LocalDate.parse(fromDate.trim());
                to = LocalDate.parse(toDate.trim());
            } catch (DateTimeParseException | NullPointerException e) {
                return LeaveApplicationReceipt.failed(
                    "I need both dates as yyyy-MM-dd (for example 2026-11-10). I was given from=\""
                        + fromDate + "\" and to=\"" + toDate + "\".");
            }

            // Deliberately NOT passing a day count: the form sends its own because it
            // excludes weekends, whereas the assistant only knows the span the person
            // described. Letting the service derive it keeps the two honest.
            LeaveRequest saved = applications.apply(me, canonicalType(leaveType), from, to, null, reason);

            Integer leftAfter = balances.remaining(me.getId(), saved.getLeaveType(), from.getYear());
            log.info("HR Assistant: leave request {} filed for {} ({} {} to {})",
                saved.getId(), me.getEmail(), saved.getLeaveType(), from, to);
            return new LeaveApplicationReceipt(true, saved.getId(), saved.getStatus().name(),
                saved.getLeaveType(), from.toString(), to.toString(), saved.getTotalDays(), leftAfter, null);
        } catch (AssistantIdentity.NotLinkedException | IllegalArgumentException e) {
            // A refused quota check lands here. It is the answer, not a crash — the
            // person needs to read the reason.
            return LeaveApplicationReceipt.failed(e.getMessage());
        }
    }

    // ── 3. Employee lookup ───────────────────────────────────────────────────────
    @Tool(name = "find_employee", description = """
        Looks a colleague up in the staff directory by name or email address and returns
        their directory details: employee id, full name, work email, designation,
        department, location, joining date and employment status.

        Use this for questions like "who is Priya Sharma?", "what is Arjun's email?" or
        "which department does Ravi work in?".

        Parameters:
          nameOrEmail - required. A full name, part of a name, or an email address. The
                        match is case-insensitive and partial, so "priya" finds
                        "Priya Sharma".

        Salary is NOT directory information. The basicSalary field is returned only on the
        signed-in person's own record and is null on everybody else's — do not infer,
        estimate or comment on a colleague's pay from any other field, and if asked for
        it, say plainly that pay is private and is only visible in Payroll to HR.
        """)
    public EmployeeDirectoryResult findEmployee(
            @ToolParam(description = "Full name, part of a name, or an email address.") String nameOrEmail) {
        if (nameOrEmail == null || nameOrEmail.isBlank())
            return EmployeeDirectoryResult.failed("I need a name or an email address to search for.");

        String q = nameOrEmail.trim().toLowerCase();
        List<Match> hits = new ArrayList<>();
        for (Employee e : empRepo.findAll()) {
            String full = (e.getFullName() == null ? "" : e.getFullName()).toLowerCase();
            String mail = (e.getEmail() == null ? "" : e.getEmail()).toLowerCase();
            String code = (e.getEmpId() == null ? "" : e.getEmpId()).toLowerCase();
            if (!full.contains(q) && !mail.contains(q) && !code.contains(q)) continue;

            // Rule 27, enforced here rather than trusted to the prompt: your own pay is
            // not a secret from you, everybody else's is not yours to see. Note this is
            // stricter than the REST API, where HR can read every salary — the assistant
            // gives nobody a privilege they would not have as an ordinary employee.
            BigDecimal pay = identity.isSelf(e) ? e.getBasicSalary() : null;
            hits.add(new Match(e.getEmpId(), e.getFullName(), e.getEmail(), e.getDesignation(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getLocation(),
                e.getJoinDate() == null ? null : e.getJoinDate().toString(),
                e.getStatus() == null ? null : e.getStatus().name(),
                pay, identity.isSelf(e)));
            if (hits.size() == 10) break;   // a vague query must not dump the whole company
        }
        if (hits.isEmpty())
            return EmployeeDirectoryResult.failed("Nobody in the staff directory matches \"" + nameOrEmail + "\".");
        return new EmployeeDirectoryResult(true, hits, null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** Maps what somebody might type onto the exact quota key, or null if untracked. */
    private String normaliseLeaveType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String c = canonicalType(raw);
        return balances.tracked(c) ? c : null;
    }

    /**
     * "casual leave" / "CASUAL" / "comp-off" become the exact strings the quota map and
     * the leave_requests rows use. An unrecognised value is passed through untouched so
     * the service, not this adapter, decides whether it is acceptable.
     */
    private static String canonicalType(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase().replace("leave", "").replace("-", " ").replace("_", " ").trim();
        return switch (t) {
            case "casual", "cl" -> "Casual";
            case "sick", "sl", "medical" -> "Sick";
            case "earned", "el", "privilege", "annual" -> "Earned";
            case "comp off", "compoff", "comp", "compensatory" -> "Comp Off";
            case "unpaid", "lop", "loss of pay" -> "Unpaid";
            case "maternity" -> "Maternity";
            case "paternity" -> "Paternity";
            default -> raw.trim();
        };
    }

    // ── returned shapes ──────────────────────────────────────────────────────────
    // Records rather than prose strings: Spring AI serialises them to JSON, which the
    // model reads reliably, and `ok:false` plus `error` gives it something definite to
    // relay instead of having to interpret an error sentence.

    public record Line(String leaveType, int entitled, int used, int remaining) {}

    public record LeaveBalanceReport(boolean ok, String employee, Integer year,
                                     List<Line> balances, String error) {
        static LeaveBalanceReport failed(String why) {
            return new LeaveBalanceReport(false, null, null, List.of(), why);
        }
    }

    public record LeaveApplicationReceipt(boolean ok, Long requestId, String status, String leaveType,
                                          String fromDate, String toDate, Integer totalDays,
                                          Integer remainingAfter, String error) {
        static LeaveApplicationReceipt failed(String why) {
            return new LeaveApplicationReceipt(false, null, null, null, null, null, null, null, why);
        }
    }

    public record Match(String empId, String fullName, String email, String designation,
                        String department, String location, String joinDate, String status,
                        BigDecimal basicSalary, boolean isYou) {}

    public record EmployeeDirectoryResult(boolean ok, List<Match> matches, String error) {
        static EmployeeDirectoryResult failed(String why) {
            return new EmployeeDirectoryResult(false, List.of(), why);
        }
    }
}
