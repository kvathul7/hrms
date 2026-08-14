package com.axvon.hrms.controller;

import com.axvon.hrms.dto.*;
import com.axvon.hrms.entity.*;
import com.axvon.hrms.repository.*;
import com.axvon.hrms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

// ── AUTH ─────────────────────────────────────────────────────────────
@RestController @RequestMapping("/auth") @RequiredArgsConstructor
class AuthController {
    private final AuthenticationManager authManager;
    private final UserRepository userRepo;
    private final EmployeeRepository empRepo;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String,String> req) {
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.get("email"),req.get("password")));
            User user=userRepo.findByEmail(req.get("email")).orElseThrow();
            user.setLastLogin(LocalDateTime.now()); userRepo.save(user);
            String name=empRepo.findAll().stream()
                .filter(e->e.getUser()!=null&&e.getUser().getId().equals(user.getId()))
                .findFirst().map(Employee::getFullName).orElse(user.getEmail().split("@")[0]);
            String token=jwtUtil.generate(user.getEmail(),user.getRole().name());
            return ResponseEntity.ok(AuthResponse.builder().token(token).email(user.getEmail())
                .role(user.getRole().name()).name(name).userId(user.getId()).message("Login successful").build());
        } catch(BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.builder().message("Invalid credentials").build());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if(auth==null) return ResponseEntity.status(401).build();
        User user=userRepo.findByEmail(auth.getName()).orElseThrow();
        String name=empRepo.findAll().stream()
            .filter(e->e.getUser()!=null&&e.getUser().getId().equals(user.getId()))
            .findFirst().map(Employee::getFullName).orElse(user.getEmail().split("@")[0]);
        return ResponseEntity.ok(AuthResponse.builder().email(user.getEmail())
            .role(user.getRole().name()).name(name).userId(user.getId()).build());
    }
}

// ── EMPLOYEES ─────────────────────────────────────────────────────────
@RestController @RequestMapping("/employees") @RequiredArgsConstructor
class EmployeeController {
    private final EmployeeRepository empRepo;
    private final DepartmentRepository deptRepo;
    private final PayrollRepository payRepo;
    private final AttendanceRepository attRepo;
    private final LeaveRequestRepository leaveRepo;
    private final PerformanceReviewRepository perfRepo;

    @GetMapping public ResponseEntity<ApiResponse<List<Employee>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(empRepo.findAll()));}
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Employee>> getById(@PathVariable Long id){return empRepo.findById(id).map(e->ResponseEntity.ok(ApiResponse.ok(e))).orElse(ResponseEntity.notFound().build());}

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Employee>> create(@RequestBody Map<String,Object> body){
        Employee e=build(body,new Employee());
        require(e);
        if(blank(e.getEmpId())) e.setEmpId(empId(nextEmpSeq()));
        return ResponseEntity.ok(ApiResponse.ok("Created",empRepo.save(e)));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Employee>> update(@PathVariable Long id,@RequestBody Map<String,Object> body){
        return empRepo.findById(id).map(e->ResponseEntity.ok(ApiResponse.ok("Updated",empRepo.save(build(body,e))))).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        if(!empRepo.existsById(id)) return ResponseEntity.notFound().build();
        // Payroll/attendance/leave/review rows carry FKs to this employee, and H2
        // enforces them — deleting the employee first fails with a 500. Since payroll
        // generation covers every active employee, that made staff undeletable.
        payRepo.deleteByEmployeeId(id);
        attRepo.deleteByEmployeeId(id);
        leaveRepo.deleteByEmployeeId(id);
        perfRepo.deleteByEmployeeId(id);
        empRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String,Object>>> stats(){
        return ResponseEntity.ok(ApiResponse.ok(Map.of("total",empRepo.count(),"active",empRepo.countActive())));
    }

    @PostMapping("/bulk-import") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> bulkImport(@RequestBody List<Map<String,Object>> rows){
        int ok=0,fail=0; List<String> errs=new ArrayList<>();
        int seq=nextEmpSeq();
        for(Map<String,Object> row:rows){
            try{
                Employee e=build(row,new Employee());
                // A null check alone let rows of empty strings through — they saved as
                // nameless employees because the columns are NOT NULL, not NOT BLANK.
                if(blank(e.getFirstName())||blank(e.getLastName())||blank(e.getEmail()))
                    throw new Exception("Missing required fields (first name, last name, email)");
                if(empRepo.existsByEmail(e.getEmail())) throw new Exception("Duplicate email: "+e.getEmail());
                // Imported rows carry no employee ID, which left the ID column blank
                // everywhere in the UI. Continue the AXVnnn series instead.
                if(blank(e.getEmpId())) e.setEmpId(empId(seq++));
                empRepo.save(e); ok++;
            }catch(Exception e){fail++;errs.add(e.getMessage());}
        }
        return ResponseEntity.ok(ApiResponse.ok("Bulk import done",Map.of("imported",ok,"failed",fail,"errors",errs)));
    }

    private static boolean blank(String s){return s==null||s.isBlank();}
    private static String empId(int seq){return String.format("AXV%03d",seq);}
    private static void require(Employee e){
        if(blank(e.getFirstName())||blank(e.getLastName())||blank(e.getEmail()))
            throw new IllegalArgumentException("First name, last name and email are required");
    }
    private int nextEmpSeq(){
        int max=0;
        for(Employee e:empRepo.findAll()){
            String id=e.getEmpId();
            if(id!=null&&id.matches("AXV\\d+"))
                try{max=Math.max(max,Integer.parseInt(id.substring(3)));}catch(NumberFormatException ignored){}
        }
        return max+1;
    }

    // Trim on the way in: a pasted spreadsheet cell of " " is not a name, and a
    // trailing space would let the same email be registered twice.
    private static String str(Object v){
        if(v==null) return null;
        String s=v.toString().trim();
        return s.isEmpty()?null:s;
    }

    private Employee build(Map<String,Object> b,Employee e){
        if(b.containsKey("firstName")) e.setFirstName(str(b.get("firstName")));
        if(b.containsKey("lastName")) e.setLastName(str(b.get("lastName")));
        if(b.containsKey("email")) e.setEmail(str(b.get("email")));
        if(b.containsKey("empId")) e.setEmpId(str(b.get("empId")));
        if(b.containsKey("phone")) e.setPhone(str(b.get("phone")));
        if(b.containsKey("designation")) e.setDesignation(str(b.get("designation")));
        if(b.containsKey("location")) e.setLocation(str(b.get("location")));
        if(b.containsKey("manager")) e.setManager(str(b.get("manager")));
        if(b.containsKey("basicSalary")&&b.get("basicSalary")!=null)
            try{e.setBasicSalary(new BigDecimal(b.get("basicSalary").toString()));}catch(Exception ignored){}
        if(b.containsKey("joinDate")&&b.get("joinDate")!=null)
            try{e.setJoinDate(LocalDate.parse(b.get("joinDate").toString()));}catch(Exception ignored){}
        if(b.containsKey("status")&&b.get("status")!=null)
            try{e.setStatus(Employee.Status.valueOf(b.get("status").toString()));}catch(Exception ignored){}
        if(b.containsKey("gender")&&b.get("gender")!=null)
            try{e.setGender(Employee.Gender.valueOf(b.get("gender").toString()));}catch(Exception ignored){}
        if(b.containsKey("departmentId")&&b.get("departmentId")!=null)
            try{deptRepo.findById(Long.valueOf(b.get("departmentId").toString())).ifPresent(e::setDepartment);}catch(Exception ignored){}
        return e;
    }
}

// ── DEPARTMENTS ───────────────────────────────────────────────────────
@RestController @RequestMapping("/departments") @RequiredArgsConstructor
class DepartmentController {
    private final DepartmentRepository repo;
    private final EmployeeRepository empRepo;
    @GetMapping public ResponseEntity<ApiResponse<List<Department>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));}
    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Department>> create(@RequestBody Department d){return ResponseEntity.ok(ApiResponse.ok("Created",repo.save(d)));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Department>> update(@PathVariable Long id,@RequestBody Department body){return repo.findById(id).map(d->{d.setName(body.getName());d.setHeadOfDept(body.getHeadOfDept());d.setDescription(body.getDescription());return ResponseEntity.ok(ApiResponse.ok("Updated",repo.save(d)));}).orElse(ResponseEntity.notFound().build());}
    // Employees FK to department, so unassign them first rather than 500 —
    // deleting a department must never delete the people in it.
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> delete(@PathVariable Long id){
        if(!repo.existsById(id)) return ResponseEntity.notFound().build();
        List<Employee> affected=empRepo.findByDepartmentId(id);
        affected.forEach(e->e.setDepartment(null));
        empRepo.saveAll(affected);
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted",Map.of("unassignedEmployees",affected.size())));
    }
}

// ── LEAVE ─────────────────────────────────────────────────────────────
@RestController @RequestMapping("/leave") @RequiredArgsConstructor
class LeaveController {
    private final LeaveRequestRepository repo;
    private final EmployeeRepository empRepo;
    @GetMapping("/requests") public ResponseEntity<ApiResponse<List<LeaveRequest>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));}
    @GetMapping("/requests/pending") public ResponseEntity<ApiResponse<List<LeaveRequest>>> pending(){return ResponseEntity.ok(ApiResponse.ok(repo.findByStatus(LeaveRequest.Status.Pending)));}
    @PostMapping("/apply") public ResponseEntity<ApiResponse<LeaveRequest>> apply(@RequestBody Map<String,Object> body){
        LeaveRequest lr=new LeaveRequest();
        empRepo.findById(Long.valueOf(body.get("employeeId").toString())).ifPresent(lr::setEmployee);
        lr.setLeaveType(body.get("leaveType").toString());
        lr.setFromDate(LocalDate.parse(body.get("fromDate").toString()));
        lr.setToDate(LocalDate.parse(body.get("toDate").toString()));
        lr.setTotalDays(Integer.valueOf(body.get("totalDays").toString()));
        lr.setReason(body.get("reason").toString());
        lr.setStatus(LeaveRequest.Status.Pending);
        return ResponseEntity.ok(ApiResponse.ok("Applied",repo.save(lr)));
    }
    @PutMapping("/requests/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')") public ResponseEntity<ApiResponse<LeaveRequest>> approve(@PathVariable Long id){return repo.findById(id).map(lr->{lr.setStatus(LeaveRequest.Status.Approved);return ResponseEntity.ok(ApiResponse.ok("Approved",repo.save(lr)));}).orElse(ResponseEntity.notFound().build());}
    @PutMapping("/requests/{id}/reject") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')") public ResponseEntity<ApiResponse<LeaveRequest>> reject(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> body){return repo.findById(id).map(lr->{lr.setStatus(LeaveRequest.Status.Rejected);if(body!=null&&body.get("reason")!=null)lr.setRejectionReason(body.get("reason").toString());return ResponseEntity.ok(ApiResponse.ok("Rejected",repo.save(lr)));}).orElse(ResponseEntity.notFound().build());}
    @DeleteMapping("/requests/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        if(!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }
    @DeleteMapping("/requests") @PreAuthorize("hasRole('SUPER_ADMIN')") public ResponseEntity<ApiResponse<Void>> deleteAll(@RequestParam(defaultValue="false") boolean confirm){if(!confirm)return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));repo.deleteAll();return ResponseEntity.ok(ApiResponse.ok("All leave deleted",null));}
}

// ── PAYROLL ───────────────────────────────────────────────────────────
@RestController @RequestMapping("/payroll") @RequiredArgsConstructor
class PayrollController {
    private final PayrollRepository repo;
    private final EmployeeRepository empRepo;
    @GetMapping public ResponseEntity<ApiResponse<List<Payroll>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));}
    @GetMapping("/month/{month}") public ResponseEntity<ApiResponse<List<Payroll>>> byMonth(@PathVariable String month){return ResponseEntity.ok(ApiResponse.ok(repo.findByPayMonth(month)));}
    @PostMapping("/generate") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Map<String,Object>>> generate(@RequestBody Map<String,Object> body){
        String month=body.get("payMonth").toString();
        List<Employee> emps=empRepo.findByStatus(Employee.Status.Active);
        int generated=0;
        for(Employee emp:emps){
            if(repo.findByEmployeeIdAndPayMonth(emp.getId(),month).isPresent()) continue;
            BigDecimal basic=emp.getBasicSalary()!=null?emp.getBasicSalary():BigDecimal.ZERO;
            BigDecimal hra=basic.multiply(new BigDecimal("0.40"));
            BigDecimal allow=basic.multiply(new BigDecimal("0.166"));
            BigDecimal gross=basic.add(hra).add(allow);
            BigDecimal pf=basic.multiply(new BigDecimal("0.12"));
            BigDecimal esi=gross.compareTo(new BigDecimal("21000"))<=0?gross.multiply(new BigDecimal("0.0075")):BigDecimal.ZERO;
            BigDecimal pt=new BigDecimal("200");
            BigDecimal tds=gross.multiply(new BigDecimal("0.09"));
            BigDecimal totalDed=pf.add(esi).add(pt).add(tds);
            repo.save(Payroll.builder().employee(emp).payMonth(month).basicSalary(basic).hra(hra)
                .specialAllowance(allow).performanceBonus(BigDecimal.ZERO).grossSalary(gross)
                .pfDeduction(pf).esiDeduction(esi).professionalTax(pt).tdsDeduction(tds)
                .totalDeduction(totalDed).netSalary(gross.subtract(totalDed))
                .status(Payroll.Status.Processed).build());
            generated++;
        }
        return ResponseEntity.ok(ApiResponse.ok("Generated",Map.of("generated",generated,"month",month)));
    }
    @PutMapping("/{id}/mark-paid") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Payroll>> markPaid(@PathVariable Long id){return repo.findById(id).map(p->{p.setStatus(Payroll.Status.Paid);return ResponseEntity.ok(ApiResponse.ok("Paid",repo.save(p)));}).orElse(ResponseEntity.notFound().build());}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        if(!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }
    @DeleteMapping @PreAuthorize("hasRole('SUPER_ADMIN')") public ResponseEntity<ApiResponse<Void>> deleteAll(@RequestParam(defaultValue="false") boolean confirm){if(!confirm)return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));repo.deleteAll();return ResponseEntity.ok(ApiResponse.ok("All payroll deleted",null));}
}

// ── HOLIDAYS ─────────────────────────────────────────────────────────
@RestController @RequestMapping("/holidays") @RequiredArgsConstructor
class HolidayController {
    private final HolidayRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Holiday>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findByIsActiveTrue()));}
    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Holiday>> create(@RequestBody Holiday h){return ResponseEntity.ok(ApiResponse.ok("Created",repo.save(h)));}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){repo.findById(id).ifPresent(h->{h.setIsActive(false);repo.save(h);});return ResponseEntity.ok(ApiResponse.ok("Deleted",null));}
}

// ── CANDIDATES ────────────────────────────────────────────────────────
@RestController @RequestMapping("/candidates") @RequiredArgsConstructor
class CandidateController {
    private final CandidateRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Candidate>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));}
    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Candidate>> create(@RequestBody Candidate c){return ResponseEntity.ok(ApiResponse.ok("Created",repo.save(c)));}
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Candidate>> update(@PathVariable Long id,@RequestBody Candidate body){return repo.findById(id).map(c->{c.setStage(body.getStage());c.setStatus(body.getStatus());c.setNotes(body.getNotes());return ResponseEntity.ok(ApiResponse.ok("Updated",repo.save(c)));}).orElse(ResponseEntity.notFound().build());}
    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){repo.deleteById(id);return ResponseEntity.ok(ApiResponse.ok("Deleted",null));}
}

// ── DASHBOARD ─────────────────────────────────────────────────────────
@RestController @RequestMapping("/dashboard") @RequiredArgsConstructor
class DashboardController {
    private final EmployeeRepository empRepo;
    private final DepartmentRepository deptRepo;
    private final LeaveRequestRepository leaveRepo;
    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Object>>> stats(){
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "totalEmployees",empRepo.count(),"activeEmployees",empRepo.countActive(),
            "totalDepartments",deptRepo.count(),"pendingLeaves",leaveRepo.findByStatus(LeaveRequest.Status.Pending).size())));
    }
    @GetMapping("/health") public ResponseEntity<Map<String,String>> health(){
        return ResponseEntity.ok(Map.of("status","UP","app","AXVON HRMS","version","2.0.0","author","Athul KV"));
    }
}

// ── ADMIN DELETE ALL ─────────────────────────────────────────────────
@RestController @RequestMapping("/admin") @RequiredArgsConstructor @Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AdminController {
    private final EmployeeRepository empRepo;
    private final DepartmentRepository deptRepo;
    private final LeaveRequestRepository leaveRepo;
    private final PayrollRepository payRepo;
    private final HolidayRepository holRepo;
    private final CandidateRepository candRepo;
    private final AttendanceRepository attRepo;
    private final PerformanceReviewRepository perfRepo;

    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Long>>> stats(){
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "employees",empRepo.count(),"leave",leaveRepo.count(),
            "payroll",payRepo.count(),"candidates",candRepo.count(),"holidays",holRepo.count())));
    }
    @DeleteMapping("/delete/employees") @Transactional public ResponseEntity<ApiResponse<Map<String,Object>>> delEmp(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=empRepo.count();
        // Everything that FKs to an employee has to go first, or H2 rejects the delete.
        payRepo.deleteAll(); attRepo.deleteAll(); leaveRepo.deleteAll(); perfRepo.deleteAll();
        empRepo.deleteAll();
        log.warn("ADMIN: {} employees deleted",count);
        return ResponseEntity.ok(ApiResponse.ok("All employees deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/leave") public ResponseEntity<ApiResponse<Map<String,Object>>> delLeave(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=leaveRepo.count(); leaveRepo.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok("All leave deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/payroll") public ResponseEntity<ApiResponse<Map<String,Object>>> delPay(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=payRepo.count(); payRepo.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok("All payroll deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/candidates") public ResponseEntity<ApiResponse<Map<String,Object>>> delCands(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=candRepo.count(); candRepo.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok("All candidates deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/holidays") public ResponseEntity<ApiResponse<Map<String,Object>>> delHols(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=holRepo.count(); holRepo.deleteAll();
        return ResponseEntity.ok(ApiResponse.ok("All holidays deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/all") public ResponseEntity<ApiResponse<Map<String,Object>>> delAll(
            @RequestParam(defaultValue="false") boolean confirm,
            @RequestParam(defaultValue="") String confirmPhrase){
        if(!confirm||!"DELETE_ALL_AXVON_DATA".equals(confirmPhrase))
            return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true&confirmPhrase=DELETE_ALL_AXVON_DATA"));
        Map<String,Object> deleted=new LinkedHashMap<>();
        // Order matters: every child of employees must be cleared before employees.
        // attendance and performance were missing here, which 500'd the whole reset.
        deleted.put("payroll",payRepo.count()); payRepo.deleteAll();
        deleted.put("attendance",attRepo.count()); attRepo.deleteAll();
        deleted.put("performance",perfRepo.count()); perfRepo.deleteAll();
        deleted.put("leave",leaveRepo.count()); leaveRepo.deleteAll();
        deleted.put("candidates",candRepo.count()); candRepo.deleteAll();
        deleted.put("holidays",holRepo.count()); holRepo.deleteAll();
        deleted.put("employees",empRepo.count()); empRepo.deleteAll();
        log.warn("ADMIN: FULL RESET performed");
        return ResponseEntity.ok(ApiResponse.ok("Full reset complete",deleted));
    }
}

// ── ATTENDANCE ───────────────────────────────────────────────────────
@RestController @RequestMapping("/attendance") @RequiredArgsConstructor
class AttendanceController {
    private final AttendanceRepository attRepo;
    private final EmployeeRepository empRepo;

    @GetMapping public ResponseEntity<ApiResponse<List<Attendance>>> byDate(@RequestParam(required=false) String date){
        LocalDate d = (date==null||date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findByAttendanceDate(d)));
    }

    @GetMapping("/all") public ResponseEntity<ApiResponse<List<Attendance>>> all(){
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findAll()));
    }

    @GetMapping("/employee/{empId}") public ResponseEntity<ApiResponse<List<Attendance>>> byEmployee(@PathVariable Long empId){
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findByEmployeeId(empId)));
    }

    @GetMapping("/summary") public ResponseEntity<ApiResponse<Map<String,Object>>> summary(@RequestParam(required=false) String date){
        LocalDate d = (date==null||date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        List<Attendance> recs = attRepo.findByAttendanceDate(d);
        long present = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Present||a.getStatus()==Attendance.AttStatus.Work_From_Home).count();
        long absent  = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Absent).count();
        long leave   = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Leave).count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("date",d.toString(),"totalEmployees",empRepo.count(),
            "marked",recs.size(),"present",present,"absent",absent,"leave",leave)));
    }

    // Bulk mark/update attendance for a date. Body: {date:"YYYY-MM-DD", records:[{employeeId,status,checkIn?,checkOut?,notes?}]}
    @PostMapping("/mark") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> mark(@RequestBody Map<String,Object> body){
        LocalDate d = body.get("date")!=null ? LocalDate.parse(body.get("date").toString()) : LocalDate.now();
        @SuppressWarnings("unchecked") List<Map<String,Object>> records = (List<Map<String,Object>>) body.getOrDefault("records", List.of());
        int saved=0; List<String> errs=new ArrayList<>();
        for(Map<String,Object> r : records){
            try{
                Long empId = Long.valueOf(r.get("employeeId").toString());
                Employee emp = empRepo.findById(empId).orElseThrow(()->new IllegalArgumentException("Employee "+empId+" not found"));
                Attendance a = attRepo.findByEmployeeIdAndAttendanceDate(empId, d)
                    .orElse(Attendance.builder().employee(emp).attendanceDate(d).build());
                a.setStatus(Attendance.AttStatus.valueOf(r.get("status").toString()));
                if(r.get("checkIn")!=null && !r.get("checkIn").toString().isBlank()) a.setCheckIn(LocalTime.parse(r.get("checkIn").toString()));
                if(r.get("checkOut")!=null && !r.get("checkOut").toString().isBlank()) a.setCheckOut(LocalTime.parse(r.get("checkOut").toString()));
                if(a.getCheckIn()!=null && a.getCheckOut()!=null)
                    a.setWorkingHours(BigDecimal.valueOf(Duration.between(a.getCheckIn(),a.getCheckOut()).toMinutes()/60.0));
                if(r.get("notes")!=null) a.setNotes(r.get("notes").toString());
                attRepo.save(a); saved++;
            }catch(Exception ex){ errs.add(ex.getMessage()); }
        }
        return ResponseEntity.ok(ApiResponse.ok("Attendance saved", Map.of("date",d.toString(),"saved",saved,"errors",errs)));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        attRepo.deleteById(id); return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }
}

// ── PERFORMANCE ──────────────────────────────────────────────────────
@RestController @RequestMapping("/performance") @RequiredArgsConstructor
class PerformanceController {
    private final PerformanceReviewRepository perfRepo;
    private final EmployeeRepository empRepo;

    @GetMapping public ResponseEntity<ApiResponse<List<PerformanceReview>>> getAll(){
        return ResponseEntity.ok(ApiResponse.ok(perfRepo.findAll()));
    }

    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Object>>> stats(){
        List<PerformanceReview> all = perfRepo.findAll();
        double avg = all.stream().filter(p->p.getRating()!=null).mapToDouble(p->p.getRating().doubleValue()).average().orElse(0);
        long completed = all.stream().filter(p->p.getStatus()==PerformanceReview.PerfStatus.Completed).count();
        long pending = all.size()-completed;
        int goals = all.stream().filter(p->p.getGoalsSet()!=null).mapToInt(PerformanceReview::getGoalsSet).sum();
        int achieved = all.stream().filter(p->p.getGoalsAchieved()!=null).mapToInt(PerformanceReview::getGoalsAchieved).sum();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("avgRating",Math.round(avg*10)/10.0,"completed",completed,
            "pending",pending,"goalAchievement", goals==0?0:Math.round(achieved*100.0/goals))));
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PerformanceReview>> create(@RequestBody Map<String,Object> b){
        Employee emp = empRepo.findById(Long.valueOf(b.get("employeeId").toString()))
            .orElseThrow(()->new IllegalArgumentException("Employee not found"));
        PerformanceReview p = PerformanceReview.builder()
            .employee(emp)
            .reviewPeriod(b.getOrDefault("reviewPeriod","").toString())
            .goalsSet(b.get("goalsSet")!=null?Integer.valueOf(b.get("goalsSet").toString()):0)
            .goalsAchieved(b.get("goalsAchieved")!=null?Integer.valueOf(b.get("goalsAchieved").toString()):0)
            .rating(b.get("rating")!=null?new BigDecimal(b.get("rating").toString()):null)
            .overallFeedback(b.get("overallFeedback")!=null?b.get("overallFeedback").toString():null)
            .status(b.get("status")!=null?PerformanceReview.PerfStatus.valueOf(b.get("status").toString()):PerformanceReview.PerfStatus.Pending)
            .build();
        return ResponseEntity.ok(ApiResponse.ok("Review created", perfRepo.save(p)));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PerformanceReview>> update(@PathVariable Long id,@RequestBody Map<String,Object> b){
        return perfRepo.findById(id).map(p->{
            if(b.get("reviewPeriod")!=null) p.setReviewPeriod(b.get("reviewPeriod").toString());
            if(b.get("goalsSet")!=null) p.setGoalsSet(Integer.valueOf(b.get("goalsSet").toString()));
            if(b.get("goalsAchieved")!=null) p.setGoalsAchieved(Integer.valueOf(b.get("goalsAchieved").toString()));
            if(b.get("rating")!=null) p.setRating(new BigDecimal(b.get("rating").toString()));
            if(b.get("overallFeedback")!=null) p.setOverallFeedback(b.get("overallFeedback").toString());
            if(b.get("status")!=null) p.setStatus(PerformanceReview.PerfStatus.valueOf(b.get("status").toString()));
            return ResponseEntity.ok(ApiResponse.ok("Updated", perfRepo.save(p)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        perfRepo.deleteById(id); return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }
}

// ── REPORTS ──────────────────────────────────────────────────────────
@RestController @RequestMapping("/reports") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
class ReportController {
    private final EmployeeRepository empRepo;
    private final DepartmentRepository deptRepo;
    private final AttendanceRepository attRepo;
    private final LeaveRequestRepository leaveRepo;
    private final PayrollRepository payRepo;

    // One consolidated snapshot for the Reports screen.
    // date  → attendance day  (default today)
    // month → payroll YYYY-MM (default current month)
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String,Object>>> summary(
            @RequestParam(required=false) String date,
            @RequestParam(required=false) String month){
        LocalDate on = (date!=null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        String payMonth = (month!=null && !month.isBlank()) ? month : YearMonth.now().toString();

        Map<String,Object> headcount=new LinkedHashMap<>();
        headcount.put("total",empRepo.count());
        headcount.put("active",empRepo.countActive());

        // Rate is over days actually marked — an unmarked day reads 0, not 100.
        long marked=attRepo.findByAttendanceDate(on).size();
        long present=attRepo.countPresent(on);
        Map<String,Object> attendance=new LinkedHashMap<>();
        attendance.put("date",on.toString());
        attendance.put("marked",marked);
        attendance.put("present",present);
        attendance.put("absent",attRepo.countAbsent(on));
        attendance.put("presentRate",marked==0?0.0:Math.round(present*1000.0/marked)/10.0);

        Map<String,Object> leave=new LinkedHashMap<>();
        leave.put("pending",leaveRepo.findByStatus(LeaveRequest.Status.Pending).size());
        leave.put("approved",leaveRepo.findByStatus(LeaveRequest.Status.Approved).size());
        leave.put("rejected",leaveRepo.findByStatus(LeaveRequest.Status.Rejected).size());

        List<Payroll> slips=payRepo.findByPayMonth(payMonth);
        BigDecimal gross=BigDecimal.ZERO,ded=BigDecimal.ZERO,net=BigDecimal.ZERO;
        for(Payroll p:slips){
            if(p.getGrossSalary()!=null)    gross=gross.add(p.getGrossSalary());
            if(p.getTotalDeduction()!=null) ded=ded.add(p.getTotalDeduction());
            if(p.getNetSalary()!=null)      net=net.add(p.getNetSalary());
        }
        Map<String,Object> payroll=new LinkedHashMap<>();
        payroll.put("month",payMonth);
        payroll.put("slips",slips.size());
        payroll.put("grossTotal",gross);
        payroll.put("deductionTotal",ded);
        payroll.put("netTotal",net);

        // Seed every department at 0 so empty ones still show up on the report.
        Map<String,Long> counts=new LinkedHashMap<>();
        deptRepo.findAll().forEach(d->counts.put(d.getName(),0L));
        for(Employee e:empRepo.findAll())
            counts.merge(e.getDepartment()!=null?e.getDepartment().getName():"Unassigned",1L,Long::sum);
        List<Map<String,Object>> byDepartment=new ArrayList<>();
        counts.forEach((n,c)->{
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("name",n); row.put("count",c);
            byDepartment.add(row);
        });

        Map<String,Object> data=new LinkedHashMap<>();
        data.put("generatedAt",LocalDateTime.now().toString());
        data.put("headcount",headcount);
        data.put("attendance",attendance);
        data.put("leave",leave);
        data.put("payroll",payroll);
        data.put("byDepartment",byDepartment);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
