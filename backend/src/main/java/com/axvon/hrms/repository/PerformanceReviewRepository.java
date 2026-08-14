package com.axvon.hrms.repository;

import com.axvon.hrms.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    List<PerformanceReview> findByEmployeeId(Long employeeId);
    List<PerformanceReview> findByReviewPeriod(String reviewPeriod);
    void deleteByEmployeeId(Long employeeId);
}
