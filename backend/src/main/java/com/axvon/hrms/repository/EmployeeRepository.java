package com.axvon.hrms.repository;
import com.axvon.hrms.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;

public interface EmployeeRepository extends JpaRepository<Employee,Long>{
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByEmpId(String empId);
    List<Employee> findByStatus(Employee.Status s);
    List<Employee> findByDepartmentId(Long deptId);
    boolean existsByEmail(String e);
    boolean existsByEmpId(String e);
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.status='Active'") long countActive();
}
