package com.axvon.hrms.repository;
import com.axvon.hrms.entity.SalaryRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SalaryRevisionRepository extends JpaRepository<SalaryRevision,Long>{
    List<SalaryRevision> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
    void deleteByEmployeeId(Long employeeId);
}
