package com.axvon.hrms.service;
import com.axvon.hrms.entity.*;
import com.axvon.hrms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

// Quotas are per calendar year. Rows are created lazily whenever a balance is read or
// an employee is added, and topped up for everybody by the New Year job — lazily as
// well as on a schedule, because a desktop app is frequently not running on 1 January.
@Service @RequiredArgsConstructor @Slf4j
public class LeaveBalanceService {
    private final LeaveBalanceRepository repo;
    private final EmployeeRepository empRepo;
    private final SettingsService settings;

    // Types with no quota (Unpaid, Maternity, Paternity) are intentionally absent:
    // they are not deducted from an allowance and must never be blocked.
    public Map<String,Integer> quotas(){
        CompanySettings s=settings.get();
        Map<String,Integer> q=new LinkedHashMap<>();
        q.put("Casual", n(s.getCasualLeaveQuota(),12));
        q.put("Sick",   n(s.getSickLeaveQuota(),10));
        q.put("Earned", n(s.getEarnedLeaveQuota(),18));
        q.put("Comp Off", n(s.getCompOffQuota(),5));
        return q;
    }
    private static int n(Integer v,int d){ return v==null?d:v; }

    public boolean tracked(String leaveType){ return quotas().containsKey(leaveType); }

    public int currentYear(){ return LocalDate.now().getYear(); }

    /** Every balance row for one employee this year, creating any that are missing. */
    @Transactional
    public List<LeaveBalance> forEmployee(Long employeeId){
        return forEmployee(employeeId,currentYear());
    }

    @Transactional
    public List<LeaveBalance> forEmployee(Long employeeId,int year){
        Employee emp=empRepo.findById(employeeId).orElse(null);
        if(emp==null) return List.of();
        Map<String,Integer> q=quotas();
        List<LeaveBalance> out=new ArrayList<>();
        for(Map.Entry<String,Integer> e:q.entrySet()){
            LeaveBalance b=repo.findByEmployeeIdAndLeaveTypeAndYear(employeeId,e.getKey(),year)
                .orElseGet(()->repo.save(LeaveBalance.builder().employee(emp).leaveType(e.getKey())
                    .year(year).totalAllowed(e.getValue()).used(0).build()));
            // A quota changed in settings applies to the running year too.
            if(!Objects.equals(b.getTotalAllowed(),e.getValue())){
                b.setTotalAllowed(e.getValue());
                b=repo.save(b);
            }
            out.add(b);
        }
        return out;
    }

    /** Days still available, or null when the type carries no quota at all. */
    public Integer remaining(Long employeeId,String leaveType,int year){
        if(!tracked(leaveType)) return null;
        return forEmployee(employeeId,year).stream()
            .filter(b->b.getLeaveType().equals(leaveType))
            .map(LeaveBalance::getRemaining).findFirst().orElse(0);
    }

    /** Move `days` onto (positive) or off (negative) the used counter. */
    @Transactional
    public void adjust(Long employeeId,String leaveType,int year,int days){
        if(!tracked(leaveType)||days==0) return;
        forEmployee(employeeId,year);
        repo.findByEmployeeIdAndLeaveTypeAndYear(employeeId,leaveType,year).ifPresent(b->{
            int used=(b.getUsed()==null?0:b.getUsed())+days;
            b.setUsed(Math.max(0,used));   // never let a correction push it negative
            repo.save(b);
        });
    }

    /** Seed the new year's rows for everybody. Idempotent. */
    @Transactional
    public int openYear(int year){
        int made=0;
        for(Employee e:empRepo.findAll()){
            int before=repo.findByEmployeeIdAndYear(e.getId(),year).size();
            forEmployee(e.getId(),year);
            made+=repo.findByEmployeeIdAndYear(e.getId(),year).size()-before;
        }
        log.info("Leave balances: {} rows opened for {}",made,year);
        return made;
    }

    // 00:05 on 1 January. The lazy path above covers installations that were switched
    // off that night.
    @Scheduled(cron="0 5 0 1 1 *")
    public void newYearRollover(){ openYear(LocalDate.now().getYear()); }
}
