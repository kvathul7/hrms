package com.axvon.hrms.repository;
import com.axvon.hrms.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument,Long>{
    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
