package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="attendance") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    // Exactly one of employee / rosterEmployee is set. A row belongs either to a
    // managed Employee or to an imported name-only RosterEmployee, never both and
    // never neither — AttendanceController.mark enforces that.
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="roster_employee_id") private RosterEmployee rosterEmployee;
    @Column(name="attendance_date",nullable=false) private LocalDate attendanceDate;
    @Column(name="check_in") private LocalTime checkIn;
    @Column(name="check_out") private LocalTime checkOut;
    @Column(name="working_hours",precision=4,scale=2) private BigDecimal workingHours;
    @Enumerated(EnumType.STRING) private AttStatus status;
    private String notes;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    // Whoever this row is for, regardless of which side it hangs off. Saves every
    // caller (reports, CSV export) from repeating the null check.
    @Transient public String getPersonName(){
        if(employee!=null) return employee.getFullName();
        return rosterEmployee!=null?rosterEmployee.getName():"";
    }
    public enum AttStatus{Present,Absent,Half_Day,Leave,Week_Off,Public_Holiday,Work_From_Home}
}
