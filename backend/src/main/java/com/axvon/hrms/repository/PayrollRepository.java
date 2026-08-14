package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PayrollRepository extends JpaRepository<Payroll,Long>{
    List<Payroll> findByPayMonth(String m);
    Optional<Payroll> findByEmployeeIdAndPayMonth(Long id,String m);
    void deleteByEmployeeId(Long id);
}
