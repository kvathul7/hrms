package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="departments") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private String name;
    private String headOfDept;
    private String description;
    @Builder.Default private Boolean isActive=true;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
}
