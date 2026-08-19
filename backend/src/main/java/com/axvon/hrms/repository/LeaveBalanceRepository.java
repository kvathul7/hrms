package com.axvon.hrms.repository;
import com.axvon.hrms.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance,Long>{
    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId,Integer year);
    List<LeaveBalance> findByYear(Integer year);
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeAndYear(Long employeeId,String leaveType,Integer year);
    void deleteByEmployeeId(Long employeeId);
}
