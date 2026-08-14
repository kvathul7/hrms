package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="holidays") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Holiday {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(unique=true) private LocalDate holidayDate;
    @Enumerated(EnumType.STRING) private HolType type;
    @Builder.Default private Boolean isActive=true;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    public enum HolType{National,Festival,Optional}
}
