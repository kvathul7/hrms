package com.axvon.hrms.repository;
import com.axvon.hrms.entity.AuditLog;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
// The audit trail is the one table that only grows, so it is read a page at a time
// rather than with findAll().
public interface AuditLogRepository extends JpaRepository<AuditLog,Long>{
    @Query("SELECT a FROM AuditLog a WHERE "
         + "(:action IS NULL OR a.action=:action) AND "
         + "(:who IS NULL OR LOWER(a.performedBy) LIKE LOWER(CONCAT('%',:who,'%'))) AND "
         + "(:from IS NULL OR a.performedAt>=:from) AND "
         + "(:to IS NULL OR a.performedAt<=:to) "
         + "ORDER BY a.performedAt DESC")
    Page<AuditLog> search(@Param("action") String action,@Param("who") String who,
                          @Param("from") LocalDateTime from,@Param("to") LocalDateTime to,Pageable page);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    java.util.List<String> distinctActions();
}
