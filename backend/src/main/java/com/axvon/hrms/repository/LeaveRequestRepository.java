package com.axvon.hrms.repository;
import com.axvon.hrms.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest,Long>{
    List<LeaveRequest> findByStatus(LeaveRequest.Status s);
    List<LeaveRequest> findByEmployeeId(Long id);
    List<LeaveRequest> findByEmployeeIdAndStatus(Long id,LeaveRequest.Status s);
    void deleteByEmployeeId(Long id);
}
