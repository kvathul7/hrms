package com.axvon.hrms.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
// Offer letters, ID proofs, photos. Held in the database rather than a folder so a
// single backup file carries them and the browser build behaves the same as the
// desktop one. `data` is @JsonIgnore so listing documents does not ship every byte —
// the download endpoint returns it explicitly.
@Entity @Table(name="employee_documents") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeDocument {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id") private Employee employee;
    private String docType;      // Offer Letter, ID Proof, Photo, Resume …
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    @JsonIgnore @Lob @Column(length=10_000_000) private String data;   // base64
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    @PrePersist protected void onCreate(){uploadedAt=LocalDateTime.now();}
}
