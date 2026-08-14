package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="leave_requests") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer totalDays;
    private String reason;
    @Enumerated(EnumType.STRING) @Builder.Default private Status status=Status.Pending;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate protected void onUpdate(){updatedAt=LocalDateTime.now();}
    public enum Status{Pending,Approved,Rejected,Cancelled}
}
