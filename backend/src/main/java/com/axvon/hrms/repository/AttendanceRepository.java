package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface AttendanceRepository extends JpaRepository<Attendance,Long>{
    List<Attendance> findByAttendanceDate(LocalDate date);
    List<Attendance> findByEmployeeId(Long empId);
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long empId,LocalDate date);
    void deleteByEmployeeId(Long empId);
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate=:date AND a.status='Present'") long countPresent(@Param("date") LocalDate date);
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate=:date AND a.status='Absent'") long countAbsent(@Param("date") LocalDate date);
}
