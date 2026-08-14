package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="payroll") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payroll {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    private String payMonth;
    @Column(precision=12,scale=2) private BigDecimal basicSalary;
    @Column(precision=12,scale=2) private BigDecimal hra;
    @Column(precision=12,scale=2) private BigDecimal specialAllowance;
    @Column(precision=12,scale=2) private BigDecimal performanceBonus;
    @Column(precision=12,scale=2) private BigDecimal grossSalary;
    @Column(precision=12,scale=2) private BigDecimal pfDeduction;
    @Column(precision=12,scale=2) private BigDecimal esiDeduction;
    @Column(precision=12,scale=2) private BigDecimal professionalTax;
    @Column(precision=12,scale=2) private BigDecimal tdsDeduction;
    @Column(precision=12,scale=2) private BigDecimal totalDeduction;
    @Column(precision=12,scale=2) private BigDecimal netSalary;
    @Enumerated(EnumType.STRING) @Builder.Default private Status status=Status.Draft;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    public enum Status{Draft,Processed,Paid,Cancelled}
}
