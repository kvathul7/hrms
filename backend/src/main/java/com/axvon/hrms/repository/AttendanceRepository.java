package com.axvon.hrms.repository;
import com.axvon.hrms.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface AttendanceRepository extends JpaRepository<Attendance,Long>{
    List<Attendance> findByAttendanceDate(LocalDate date);
    // Period reports (daily / monthly / 6-month / yearly) pull a window rather than
    // every row ever recorded, which a year of daily marking would otherwise mean.
    List<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate from,LocalDate to);
    List<Attendance> findByEmployeeId(Long empId);
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long empId,LocalDate date);
    void deleteByEmployeeId(Long empId);
    List<Attendance> findByRosterEmployeeId(Long rosterId);
    Optional<Attendance> findByRosterEmployeeIdAndAttendanceDate(Long rosterId,LocalDate date);
    void deleteByRosterEmployeeId(Long rosterId);
    // "Delete all employees" must not wipe the imported roster's attendance history —
    // those rows have no employee FK and survive the employee purge.
    @Modifying @Query("DELETE FROM Attendance a WHERE a.employee IS NOT NULL") void deleteEmployeeLinked();
    @Modifying @Query("DELETE FROM Attendance a WHERE a.rosterEmployee IS NOT NULL") void deleteRosterLinked();
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate=:date AND a.status='Present'") long countPresent(@Param("date") LocalDate date);
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.attendanceDate=:date AND a.status='Absent'") long countAbsent(@Param("date") LocalDate date);
}
