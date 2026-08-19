package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
// Who changed what, when, from where. Deletes and salary changes used to reach the
// log file only, which is not something anybody can query after a payroll dispute.
@Entity @Table(name="audit_logs") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String action;        // EMPLOYEE_CREATED, SALARY_CHANGED, LOGIN_FAILED …
    private String entityType;    // Employee, Payroll, User …
    private Long entityId;
    @Column(length=2000) private String oldValue;
    @Column(length=2000) private String newValue;
    private String performedBy;   // email, or "anonymous"
    private String ipAddress;
    private LocalDateTime performedAt;
    @PrePersist protected void onCreate(){ if(performedAt==null) performedAt=LocalDateTime.now(); }
}
