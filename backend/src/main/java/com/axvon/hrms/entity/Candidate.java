package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="candidates") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Candidate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String fullName;
    private String email,phone,appliedFor,department,source,stage,status,notes;
    private LocalDate interviewDate;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
}
