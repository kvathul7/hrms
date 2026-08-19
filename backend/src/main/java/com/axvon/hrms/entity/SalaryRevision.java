package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
// An employee's pay history. Written by the revision endpoint and also whenever an
// ordinary employee edit changes basicSalary, so the trail cannot be side-stepped.
@Entity @Table(name="salary_revisions") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryRevision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    @Column(precision=12,scale=2) private BigDecimal oldSalary;
    @Column(precision=12,scale=2) private BigDecimal newSalary;
    private LocalDate effectiveDate;
    @Column(length=500) private String reason;
    private String revisedBy;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
}
