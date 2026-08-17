package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
// A name-only person who exists purely to be marked on the attendance sheet.
// Deliberately NOT an Employee: Employee requires a unique email and payroll
// generation runs over every Active employee, so importing a bare name list as
// Employees would mean inventing emails and issuing zero-rupee payslips.
// Everything except name is optional and stays blank unless the operator maps a
// column during import or fills it in later.
@Entity @Table(name="roster_employees") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RosterEmployee {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String code;
    private String department;
    private String designation;
    private String phone;
    @Column(name="is_active") @Builder.Default private Boolean isActive=true;
    private String source;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    // Duplicate matching key: case- and punctuation-insensitive so "Rahul  Kumar",
    // "rahul kumar" and "RAHUL KUMAR" are all the same person. Mirrors norm() in App.jsx.
    public static String key(String name){
        return name==null?"":name.toLowerCase().replaceAll("[^a-z0-9]","");
    }
    public enum Source{EXCEL,PDF,MANUAL}
}
