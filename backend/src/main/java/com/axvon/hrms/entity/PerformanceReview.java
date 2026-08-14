package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="performance_reviews") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PerformanceReview {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    private String reviewPeriod;
    private Integer goalsSet;
    private Integer goalsAchieved;
    @Column(precision=3,scale=1) private BigDecimal rating;
    private String strengths;
    private String improvements;
    private String overallFeedback;
    @Enumerated(EnumType.STRING) @Builder.Default private PerfStatus status=PerfStatus.Pending;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    public enum PerfStatus{Pending,In_Progress,Completed}
}
