package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HolidayRepository extends JpaRepository<Holiday,Long>{
    List<Holiday> findByIsActiveTrue();
}
