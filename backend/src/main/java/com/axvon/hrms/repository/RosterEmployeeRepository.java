package com.axvon.hrms.repository;
import com.axvon.hrms.entity.RosterEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RosterEmployeeRepository extends JpaRepository<RosterEmployee,Long>{
    List<RosterEmployee> findAllByOrderByNameAsc();
}
