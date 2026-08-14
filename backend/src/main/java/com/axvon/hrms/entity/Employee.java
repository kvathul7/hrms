package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@Entity @Table(name="employees") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true) private String empId;
    @OneToOne @JoinColumn(name="user_id") private User user;
    @Column(nullable=false) private String firstName;
    @Column(nullable=false) private String lastName;
    @Column(nullable=false,unique=true) private String email;
    private String phone;
    @Enumerated(EnumType.STRING) private Gender gender;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="department_id") private Department department;
    private String designation;
    private LocalDate joinDate;
    @Enumerated(EnumType.STRING) @Builder.Default private Status status=Status.Active;
    private String location;
    @Column(precision=12,scale=2) private BigDecimal basicSalary;
    private String manager;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){createdAt=updatedAt=LocalDateTime.now();}
    @PreUpdate protected void onUpdate(){updatedAt=LocalDateTime.now();}
    @Transient public String getFullName(){return firstName+" "+lastName;}
    public enum Gender{Male,Female,Other}
    public enum Status{Active,On_Leave,Inactive,Terminated}
}
