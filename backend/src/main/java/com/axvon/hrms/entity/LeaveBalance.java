package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
// One row per employee per leave type per calendar year. `used` moves only when a
// request crosses into or out of Approved, so a rejected request never costs anybody
// a day. `remaining` is derived, never stored — a stored copy would drift.
@Entity @Table(name="leave_balances",
    uniqueConstraints=@UniqueConstraint(columnNames={"employee_id","leave_type","balance_year"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveBalance {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    @Column(name="leave_type") private String leaveType;
    @Column(name="balance_year") private Integer year;
    @Builder.Default private Integer totalAllowed=0;
    @Builder.Default private Integer used=0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate protected void onUpdate(){updatedAt=LocalDateTime.now();}
    @Transient public int getRemaining(){
        int t=totalAllowed==null?0:totalAllowed, u=used==null?0:used;
        return Math.max(0,t-u);
    }
}
