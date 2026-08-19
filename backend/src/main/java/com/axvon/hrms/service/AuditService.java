package com.axvon.hrms.service;
import com.axvon.hrms.entity.AuditLog;
import com.axvon.hrms.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Central place every auditable action writes through. Deliberately swallows its own
// failures: an audit row that cannot be written must never roll back the business
// action the operator actually asked for — it is logged to the file instead.
@Service @RequiredArgsConstructor @Slf4j
public class AuditService {
    private final AuditLogRepository repo;
    // Request-scoped, and some callers (the scheduled leave-balance job) have no
    // request at all — resolve it lazily rather than injecting it directly.
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public void log(String action,String entityType,Long entityId,String oldValue,String newValue){
        log(action,entityType,entityId,oldValue,newValue,currentUser());
    }

    public void log(String action,String entityType,Long entityId,String oldValue,String newValue,String who){
        try{
            repo.save(AuditLog.builder()
                .action(action).entityType(entityType).entityId(entityId)
                .oldValue(trim(oldValue)).newValue(trim(newValue))
                .performedBy(who==null?"system":who).ipAddress(ip())
                .build());
        }catch(Exception e){
            log.warn("Audit write failed for {} {}: {}",action,entityType,e.getMessage());
        }
    }

    public String currentUser(){
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        return a==null||a.getName()==null?"anonymous":a.getName();
    }

    private String ip(){
        try{
            HttpServletRequest r=requestProvider.getIfAvailable();
            if(r==null) return null;
            String fwd=r.getHeader("X-Forwarded-For");
            return fwd!=null&&!fwd.isBlank()?fwd.split(",")[0].trim():r.getRemoteAddr();
        }catch(Exception e){ return null; }
    }

    private static String trim(String s){
        if(s==null) return null;
        return s.length()<=2000?s:s.substring(0,1997)+"…";
    }
}
