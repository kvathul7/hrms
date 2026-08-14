package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DepartmentRepository extends JpaRepository<Department,Long>{
    Optional<Department> findByName(String n);
    boolean existsByName(String n);
}
