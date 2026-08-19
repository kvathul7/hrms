package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
// One row, id=1. Everything the app used to hardcode — the company's own identity,
// the payroll percentages and the leave quotas — lives here so a customer is not
// printing payslips with AXVON's name on them or rebuilding the jar when the
// budget changes a rate.
@Entity @Table(name="company_settings") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanySettings {
    @Id private Long id;

    @Builder.Default private String companyName="Your Company";
    @Lob @Column(length=1_000_000) private String logo;   // data: URI, optional
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private String email;
    private String website;
    private String gstin;

    @Builder.Default private String financialYearStart="04-01";  // MM-DD
    @Builder.Default private String currency="INR";
    @Builder.Default private String currencySymbol="₹";
    @Builder.Default private Integer workingDaysPerWeek=6;
    @Builder.Default private String weeklyOff="Sunday";
    // Divisor for the per-day rate used by loss-of-pay. Kept separate from
    // workingDaysPerWeek because most Indian payrolls fix it at 26.
    @Builder.Default private Integer workingDaysPerMonth=26;

    // ── Payroll rates (percentages, except the two absolute amounts) ──
    @Column(precision=6,scale=3) @Builder.Default private BigDecimal hraPercent=new BigDecimal("40");
    @Column(precision=6,scale=3) @Builder.Default private BigDecimal specialAllowancePercent=new BigDecimal("16.6");
    @Column(precision=6,scale=3) @Builder.Default private BigDecimal pfPercent=new BigDecimal("12");
    @Column(precision=12,scale=2) @Builder.Default private BigDecimal esiThreshold=new BigDecimal("21000");
    @Column(precision=6,scale=3) @Builder.Default private BigDecimal esiPercent=new BigDecimal("0.75");
    @Column(precision=12,scale=2) @Builder.Default private BigDecimal professionalTax=new BigDecimal("200");
    @Column(precision=6,scale=3) @Builder.Default private BigDecimal tdsPercent=new BigDecimal("9");
    // Absent days cost a day's pay each; approved leave does not.
    @Builder.Default private Boolean deductAbsentDays=true;

    // ── Annual leave quotas ──
    @Builder.Default private Integer casualLeaveQuota=12;
    @Builder.Default private Integer sickLeaveQuota=10;
    @Builder.Default private Integer earnedLeaveQuota=18;
    @Builder.Default private Integer compOffQuota=5;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate protected void onUpdate(){updatedAt=LocalDateTime.now();}
}
