package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Resignation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ResignationRepository extends JpaRepository<Resignation,Long>{
    List<Resignation> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    List<Resignation> findByStatus(Resignation.Status status);
    void deleteByEmployeeId(Long employeeId);
}
