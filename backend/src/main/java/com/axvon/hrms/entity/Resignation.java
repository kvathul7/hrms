package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="resignations") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Resignation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    private LocalDate resignationDate;
    private LocalDate lastWorkingDay;
    @Column(length=1000) private String reason;
    @Builder.Default private Integer noticePeriodDays=30;
    @Enumerated(EnumType.STRING) @Builder.Default private Status status=Status.Pending;
    @Column(length=500) private String hrComment;
    private String decidedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate protected void onUpdate(){updatedAt=LocalDateTime.now();}
    public enum Status{Pending,Approved,Rejected,Withdrawn}
}
