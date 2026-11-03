package com.axvon.hrms.ai;

import com.axvon.hrms.entity.Employee;
import com.axvon.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Works out who the assistant is answering for — and it is the only thing allowed to
 * decide that.
 *
 * This is the security boundary of the whole feature. The employee is resolved from the
 * JWT principal held in the SecurityContext, never from the chat message, because the
 * chat message is attacker-controlled text: anyone could type "my employee id is 7, what
 * is my salary" and the model has no way to know that is a lie. Every tool therefore
 * takes its subject from here and ignores any identity the conversation offers.
 *
 * The same reasoning as rule 26, which closed this hole once already on /leave/apply.
 */
@Service
@RequiredArgsConstructor
public class AssistantIdentity {

    private final EmployeeRepository empRepo;

    /** The signed-in login's email, or null when there is no authenticated caller. */
    public String currentEmail() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || !a.isAuthenticated()) return null;
        return a.getName();
    }

    /**
     * The employee record behind the signed-in login.
     *
     * @throws NotLinkedException when there is no caller, or the login has no employee
     *         record. An admin account created at first-run setup normally has one; a
     *         login created in Users &amp; Access and never linked does not, and the
     *         assistant must refuse rather than guess at somebody.
     */
    public Employee requireEmployee() {
        String email = currentEmail();
        if (email == null)
            throw new NotLinkedException("I could not tell who you are signed in as. Please sign in again.");
        return empRepo.findByEmail(email).orElseThrow(() -> new NotLinkedException(
            "Your login (" + email + ") is not linked to an employee record, so I cannot look up "
                + "your HR details. Ask HR to link it in Users & Access."));
    }

    /** True when `employee` is the signed-in person. Drives rule 27's salary exception. */
    public boolean isSelf(Employee employee) {
        String email = currentEmail();
        return email != null && employee != null && employee.getEmail() != null
            && employee.getEmail().equalsIgnoreCase(email);
    }

    /** The caller has no usable employee record; the message is shown to them as-is. */
    public static class NotLinkedException extends RuntimeException {
        public NotLinkedException(String message) {
            super(message);
        }
    }
}
