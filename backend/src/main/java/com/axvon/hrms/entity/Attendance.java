package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="attendance") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id",nullable=false) private Employee employee;
    @Column(name="attendance_date",nullable=false) private LocalDate attendanceDate;
    @Column(name="check_in") private LocalTime checkIn;
    @Column(name="check_out") private LocalTime checkOut;
    @Column(name="working_hours",precision=4,scale=2) private BigDecimal workingHours;
    @Enumerated(EnumType.STRING) private AttStatus status;
    private String notes;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    public enum AttStatus{Present,Absent,Half_Day,Leave,Week_Off,Public_Holiday,Work_From_Home}
}
