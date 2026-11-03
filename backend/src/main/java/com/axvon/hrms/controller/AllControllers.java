package com.axvon.hrms.controller;

import com.axvon.hrms.dto.*;
import com.axvon.hrms.entity.*;
import com.axvon.hrms.repository.*;
import com.axvon.hrms.config.DataInitializer;
import com.axvon.hrms.security.JwtUtil;
import com.axvon.hrms.service.AuditService;
import com.axvon.hrms.service.LeaveApplicationService;
import com.axvon.hrms.service.LeaveBalanceService;
import com.axvon.hrms.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

// ── AUTH ─────────────────────────────────────────────────────────────
@RestController @RequestMapping("/auth") @RequiredArgsConstructor
class AuthController {
    private final AuthenticationManager authManager;
    private final UserRepository userRepo;
    private final EmployeeRepository empRepo;
    private final JwtUtil jwtUtil;
    private final AuditService audit;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String,String> req) {
        String email=req.get("email");
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email,req.get("password")));
            User user=userRepo.findByEmail(email).orElseThrow();
            user.setLastLogin(LocalDateTime.now()); userRepo.save(user);
            String name=empRepo.findAll().stream()
                .filter(e->e.getUser()!=null&&e.getUser().getId().equals(user.getId()))
                .findFirst().map(Employee::getFullName).orElse(user.getEmail().split("@")[0]);
            String token=jwtUtil.generate(user.getEmail(),user.getRole().name());
            audit.log("LOGIN_SUCCESS","User",user.getId(),null,user.getRole().name(),user.getEmail());
            return ResponseEntity.ok(AuthResponse.builder().token(token).email(user.getEmail())
                .role(user.getRole().name()).name(name).userId(user.getId()).message("Login successful").build());
        } catch(DisabledException e) {
            // A deactivated account is not a wrong password — say so, and still 401 so
            // the frontend clears the session rather than treating it as a role denial.
            audit.log("LOGIN_FAILED","User",null,null,"account deactivated",email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.builder().message("This account has been deactivated. Contact your administrator.").build());
        } catch(AuthenticationException e) {
            audit.log("LOGIN_FAILED","User",null,null,"invalid credentials",email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(AuthResponse.builder().message("Invalid credentials").build());
        }
    }

    // Hands back a fresh token to a caller who already holds a valid one, so a long
    // working day does not end with an unsaved form and a login screen. Nothing here
    // re-checks the password, so a token that has already expired cannot be renewed —
    // the filter rejects it before this method is reached.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(Authentication auth){
        if(auth==null||auth.getName()==null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.builder().message("Not signed in").build());
        User user=userRepo.findByEmail(auth.getName()).orElse(null);
        if(user==null||!Boolean.TRUE.equals(user.getIsActive()))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(AuthResponse.builder().message("Session is no longer valid").build());
        String name=empRepo.findAll().stream()
            .filter(e->e.getUser()!=null&&e.getUser().getId().equals(user.getId()))
            .findFirst().map(Employee::getFullName).orElse(user.getEmail().split("@")[0]);
        return ResponseEntity.ok(AuthResponse.builder()
            .token(jwtUtil.generate(user.getEmail(),user.getRole().name()))
            .email(user.getEmail()).role(user.getRole().name()).name(name).userId(user.getId())
            .message("Token refreshed").build());
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

// ── USER ACCOUNTS ─────────────────────────────────────────────────────
// A company needs to create its own HR, manager and staff logins — before this
// the only accounts that ever existed were the four seeded demo ones.
@RestController @RequestMapping("/users") @RequiredArgsConstructor @Slf4j
class UserController {
    private final UserRepository userRepo;
    private final EmployeeRepository empRepo;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    private static String str(Object v){
        if(v==null) return null;
        String s=v.toString().trim();
        return s.isEmpty()?null:s;
    }

    private Map<String,Object> view(User u,List<Employee> emps){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",u.getId()); m.put("email",u.getEmail()); m.put("role",u.getRole().name());
        m.put("isActive",u.getIsActive()); m.put("lastLogin",u.getLastLogin()); m.put("createdAt",u.getCreatedAt());
        emps.stream().filter(e->e.getUser()!=null&&e.getUser().getId().equals(u.getId())).findFirst()
            .ifPresent(e->{m.put("employeeId",e.getId()); m.put("employeeName",e.getFullName());});
        return m;
    }

    // Never let the last way into the system disappear — no deleting, disabling or
    // demoting the only remaining active Super Admin.
    private void guardLastAdmin(User target,User.Role newRole,Boolean newActive){
        boolean stillAdmin=(newRole==null?target.getRole():newRole)==User.Role.SUPER_ADMIN
                        && (newActive==null?Boolean.TRUE.equals(target.getIsActive()):newActive);
        if(stillAdmin) return;
        long others=userRepo.findAll().stream()
            .filter(u->!u.getId().equals(target.getId()))
            .filter(u->u.getRole()==User.Role.SUPER_ADMIN&&Boolean.TRUE.equals(u.getIsActive()))
            .count();
        if(others==0) throw new IllegalArgumentException("This is the only active Super Admin — create another one first");
    }

    @GetMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> all(){
        List<Employee> emps=empRepo.findAll();
        return ResponseEntity.ok(ApiResponse.ok(userRepo.findAll().stream().map(u->view(u,emps)).toList()));
    }

    @PostMapping @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> create(@RequestBody Map<String,Object> b){
        String email=str(b.get("email")), password=str(b.get("password")), role=str(b.get("role"));
        if(email==null||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("A valid email address is required");
        if(password==null||password.length()<6) throw new IllegalArgumentException("Password must be at least 6 characters");
        if(userRepo.existsByEmail(email.toLowerCase())) throw new IllegalArgumentException("An account with that email already exists");
        User.Role r;
        try{ r=User.Role.valueOf(role==null?"EMPLOYEE":role); }
        catch(Exception e){ throw new IllegalArgumentException("Unknown role: "+role); }
        User u=userRepo.save(User.builder().email(email.toLowerCase()).password(encoder.encode(password))
            .role(r).isActive(true).build());
        linkEmployee(b.get("employeeId"),u);
        log.info("ADMIN: created account {} ({})",u.getEmail(),r);
        return ResponseEntity.ok(ApiResponse.ok("Account created",view(u,empRepo.findAll())));
    }

    @PutMapping("/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> update(@PathVariable Long id,@RequestBody Map<String,Object> b){
        User u=userRepo.findById(id).orElseThrow();
        User.Role newRole=null;
        if(b.get("role")!=null){
            try{ newRole=User.Role.valueOf(b.get("role").toString()); }
            catch(Exception e){ throw new IllegalArgumentException("Unknown role: "+b.get("role")); }
        }
        Boolean newActive=b.get("isActive")==null?null:Boolean.valueOf(b.get("isActive").toString());
        guardLastAdmin(u,newRole,newActive);
        User.Role oldRole=u.getRole(); Boolean oldActive=u.getIsActive();
        if(newRole!=null) u.setRole(newRole);
        if(newActive!=null) u.setIsActive(newActive);
        userRepo.save(u);
        if(newRole!=null&&newRole!=oldRole)
            audit.log("ROLE_CHANGED","User",u.getId(),oldRole.name(),newRole.name());
        if(newActive!=null&&!newActive.equals(oldActive))
            audit.log(newActive?"ACCOUNT_ENABLED":"ACCOUNT_DISABLED","User",u.getId(),String.valueOf(oldActive),String.valueOf(newActive));
        if(b.containsKey("employeeId")) linkEmployee(b.get("employeeId"),u);
        return ResponseEntity.ok(ApiResponse.ok("Account updated",view(u,empRepo.findAll())));
    }

    @PutMapping("/{id}/password") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,@RequestBody Map<String,Object> b){
        String pw=str(b.get("password"));
        if(pw==null||pw.length()<6) throw new IllegalArgumentException("Password must be at least 6 characters");
        User u=userRepo.findById(id).orElseThrow();
        u.setPassword(encoder.encode(pw)); userRepo.save(u);
        log.warn("ADMIN: password reset for {}",u.getEmail());
        audit.log("PASSWORD_RESET","User",u.getId(),null,u.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Password updated",null));
    }

    // Anyone may change their own password, and must prove they know the current one.
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changeOwnPassword(org.springframework.security.core.Authentication auth,@RequestBody Map<String,Object> b){
        if(auth==null) return ResponseEntity.status(401).build();
        String current=str(b.get("currentPassword")), next=str(b.get("newPassword"));
        if(next==null||next.length()<6) throw new IllegalArgumentException("New password must be at least 6 characters");
        User u=userRepo.findByEmail(auth.getName()).orElseThrow();
        if(current==null||!encoder.matches(current,u.getPassword()))
            throw new IllegalArgumentException("Current password is incorrect");
        u.setPassword(encoder.encode(next)); userRepo.save(u);
        audit.log("PASSWORD_CHANGED","User",u.getId(),null,u.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Password changed",null));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id,org.springframework.security.core.Authentication auth){
        User u=userRepo.findById(id).orElseThrow();
        if(auth!=null&&u.getEmail().equalsIgnoreCase(auth.getName()))
            throw new IllegalArgumentException("You cannot delete the account you are signed in with");
        guardLastAdmin(u,null,false);
        // Employees hold the FK to users, so detach them or the delete fails.
        List<Employee> linked=empRepo.findAll().stream()
            .filter(e->e.getUser()!=null&&e.getUser().getId().equals(id)).toList();
        linked.forEach(e->e.setUser(null));
        empRepo.saveAll(linked);
        userRepo.deleteById(id);
        log.warn("ADMIN: deleted account {}",u.getEmail());
        audit.log("ACCOUNT_DELETED","User",id,u.getEmail(),null);
        return ResponseEntity.ok(ApiResponse.ok("Account deleted",null));
    }

    private void linkEmployee(Object employeeId,User u){
        // One employee record per account: clear any previous holder first.
        List<Employee> current=empRepo.findAll().stream()
            .filter(e->e.getUser()!=null&&e.getUser().getId().equals(u.getId())).toList();
        current.forEach(e->e.setUser(null));
        empRepo.saveAll(current);
        String id=str(employeeId);
        if(id==null) return;
        Employee target=empRepo.findById(Long.valueOf(id)).orElseThrow(()->new IllegalArgumentException("Employee not found"));
        // Refuse to quietly move an employee record off somebody else's login.
        if(target.getUser()!=null&&!target.getUser().getId().equals(u.getId()))
            throw new IllegalArgumentException(target.getFullName()+" is already linked to "+target.getUser().getEmail());
        target.setUser(u);
        empRepo.save(target);
    }
}

// ── FIRST-RUN SETUP ───────────────────────────────────────────────────
// Open before any account exists, closed forever after. This is what replaces
// shipping every installation with the same admin@axvon.io / admin123.
@RestController @RequestMapping("/setup") @RequiredArgsConstructor @Slf4j
class SetupController {
    private final UserRepository userRepo;
    private final EmployeeRepository empRepo;
    private final PasswordEncoder encoder;
    private final DataInitializer demoData;

    private static String str(Object v){
        if(v==null) return null;
        String s=v.toString().trim();
        return s.isEmpty()?null:s;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String,Object>>> status(){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("initialized",userRepo.count()>0);
        // Drives whether the login screen offers the demo credentials at all.
        m.put("demoAccounts",userRepo.existsByEmail("admin@axvon.io"));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping("/init") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> init(@RequestBody Map<String,Object> b){
        if(userRepo.count()>0)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("AXVON is already set up — please sign in"));
        String email=str(b.get("email")), password=str(b.get("password")), fullName=str(b.get("fullName"));
        if(email==null||!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("A valid email address is required");
        if(password==null||password.length()<6) throw new IllegalArgumentException("Password must be at least 6 characters");
        boolean withDemo=Boolean.parseBoolean(String.valueOf(b.getOrDefault("loadDemoData",false)));
        if(withDemo) demoData.seedDemoData();
        User admin=userRepo.save(User.builder().email(email.toLowerCase()).password(encoder.encode(password))
            .role(User.Role.SUPER_ADMIN).isActive(true).build());
        // Give the owner an employee record too, so the app greets them by name.
        if(!empRepo.existsByEmail(email.toLowerCase())){
            String[] parts=(fullName==null?email.split("@")[0]:fullName).trim().split("\\s+",2);
            empRepo.save(Employee.builder()
                .empId(String.format("AXV%03d",empRepo.count()+1))
                .user(admin).firstName(parts[0]).lastName(parts.length>1?parts[1]:"—")
                .email(email.toLowerCase()).designation("Administrator")
                .joinDate(LocalDate.now()).status(Employee.Status.Active).build());
        }
        log.warn("AXVON: first-run setup completed by {} (demo data: {})",admin.getEmail(),withDemo);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("email",admin.getEmail()); out.put("demoDataLoaded",withDemo);
        return ResponseEntity.ok(ApiResponse.ok("Setup complete",out));
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
    private final SalaryRevisionRepository revRepo;
    private final EmployeeDocumentRepository docRepo;
    private final LeaveBalanceRepository balRepo;
    private final ResignationRepository resignRepo;
    private final AuditService audit;

    // Readable by every role because the Attendance sheet and the staff directory need
    // it — but pay is not directory information. Anyone below HR sees the record with
    // basicSalary blanked, so an employee can no longer read every colleague's salary.
    // The one exception is their own record: your own pay is not a secret from you.
    @GetMapping public ResponseEntity<ApiResponse<List<Employee>>> getAll(Authentication auth){
        boolean pay=canSeePay(auth);
        String me=auth==null?null:auth.getName();
        return ResponseEntity.ok(ApiResponse.ok(empRepo.findAll().stream().map(e->pay||isSelf(e,me)?e:withoutPay(e)).toList()));
    }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Employee>> getById(@PathVariable Long id,Authentication auth){
        boolean pay=canSeePay(auth);
        String me=auth==null?null:auth.getName();
        return empRepo.findById(id).map(e->ResponseEntity.ok(ApiResponse.ok(pay||isSelf(e,me)?e:withoutPay(e)))).orElse(ResponseEntity.notFound().build());
    }
    static boolean isSelf(Employee e,String email){
        return email!=null&&e.getEmail()!=null&&e.getEmail().equalsIgnoreCase(email);
    }
    static boolean canSeePay(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch(a->a.equals("ROLE_SUPER_ADMIN")||a.equals("ROLE_HR_ADMIN"));
    }
    // A fresh object, never the entity itself: blanking a field on a JPA instance risks
    // writing that blank back to the database.
    private static Employee withoutPay(Employee e){
        return Employee.builder().id(e.getId()).empId(e.getEmpId()).user(e.getUser())
            .firstName(e.getFirstName()).lastName(e.getLastName()).email(e.getEmail())
            .phone(e.getPhone()).gender(e.getGender()).department(e.getDepartment())
            .designation(e.getDesignation()).joinDate(e.getJoinDate()).status(e.getStatus())
            .location(e.getLocation()).basicSalary(null).manager(e.getManager())
            .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Employee>> create(@RequestBody Map<String,Object> body){
        Employee e=build(body,new Employee());
        require(e);
        if(blank(e.getEmpId())) e.setEmpId(empId(nextEmpSeq()));
        Employee saved=empRepo.save(e);
        audit.log("EMPLOYEE_CREATED","Employee",saved.getId(),null,saved.getFullName()+" <"+saved.getEmail()+">");
        return ResponseEntity.ok(ApiResponse.ok("Created",saved));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Employee>> update(@PathVariable Long id,@RequestBody Map<String,Object> body){
        return empRepo.findById(id).map(e->{
            BigDecimal before=e.getBasicSalary();
            Employee saved=empRepo.save(build(body,e));
            BigDecimal after=saved.getBasicSalary();
            // A pay change made through the ordinary edit form is still a pay change:
            // record it in the salary history rather than only in the audit trail, so
            // the two cannot disagree.
            if(changed(before,after)){
                revRepo.save(SalaryRevision.builder().employee(saved).oldSalary(before).newSalary(after)
                    .effectiveDate(LocalDate.now()).reason("Updated from the employee form")
                    .revisedBy(audit.currentUser()).build());
                audit.log("SALARY_CHANGED","Employee",saved.getId(),String.valueOf(before),String.valueOf(after));
            }
            audit.log("EMPLOYEE_UPDATED","Employee",saved.getId(),null,saved.getFullName());
            return ResponseEntity.ok(ApiResponse.ok("Updated",saved));
        }).orElse(ResponseEntity.notFound().build());
    }
    private static boolean changed(BigDecimal a,BigDecimal b){
        if(a==null&&b==null) return false;
        if(a==null||b==null) return true;
        return a.compareTo(b)!=0;
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        Employee doomed=empRepo.findById(id).orElse(null);
        if(doomed==null) return ResponseEntity.notFound().build();
        String label=doomed.getFullName()+" <"+doomed.getEmail()+">";
        // Payroll/attendance/leave/review rows carry FKs to this employee, and H2
        // enforces them — deleting the employee first fails with a 500. Since payroll
        // generation covers every active employee, that made staff undeletable.
        payRepo.deleteByEmployeeId(id);
        attRepo.deleteByEmployeeId(id);
        leaveRepo.deleteByEmployeeId(id);
        perfRepo.deleteByEmployeeId(id);
        // Same reason, for the tables added since: balances, pay history, documents
        // and resignations all point back here.
        balRepo.deleteByEmployeeId(id);
        revRepo.deleteByEmployeeId(id);
        docRepo.deleteByEmployeeId(id);
        resignRepo.deleteByEmployeeId(id);
        empRepo.deleteById(id);
        audit.log("EMPLOYEE_DELETED","Employee",id,label,null);
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
        if(b.containsKey("dateOfBirth")&&b.get("dateOfBirth")!=null)
            try{e.setDateOfBirth(LocalDate.parse(b.get("dateOfBirth").toString()));}catch(Exception ignored){}
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
    private final LeaveBalanceService balances;
    private final LeaveApplicationService applications;
    private final AuditService audit;
    // A leave reason is medical or personal information. Approvers see the queue;
    // everyone else sees only their own requests, rather than the whole company's.
    @GetMapping("/requests") public ResponseEntity<ApiResponse<List<LeaveRequest>>> getAll(Authentication auth){
        if(canApprove(auth)) return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));
        Long me=ownEmployeeId(auth);
        return ResponseEntity.ok(ApiResponse.ok(me==null?List.of():repo.findByEmployeeId(me)));
    }
    @GetMapping("/requests/pending") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequest>>> pending(){return ResponseEntity.ok(ApiResponse.ok(repo.findByStatus(LeaveRequest.Status.Pending)));}

    static boolean canApprove(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch(a->a.equals("ROLE_SUPER_ADMIN")||a.equals("ROLE_HR_ADMIN")||a.equals("ROLE_MANAGER"));
    }
    // The employee record linked to the signed-in login, or null if there isn't one.
    private Long ownEmployeeId(Authentication auth){
        if(auth==null||auth.getName()==null) return null;
        return empRepo.findByEmail(auth.getName()).map(Employee::getId).orElse(null);
    }
    // Filing leave on somebody else's behalf is an HR action. Managers approve leave;
    // they do not submit it for their reports.
    private static boolean canApplyForOthers(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch(a->a.equals("ROLE_SUPER_ADMIN")||a.equals("ROLE_HR_ADMIN"));
    }

    @PostMapping("/apply") public ResponseEntity<ApiResponse<LeaveRequest>> apply(@RequestBody Map<String,Object> body,Authentication auth){
        Long asked=body.get("employeeId")==null?null:Long.valueOf(body.get("employeeId").toString());
        Long mine=ownEmployeeId(auth);
        // The employee id used to be taken from the request body and trusted, which let
        // any signed-in account file — and then read back — leave in a colleague's name.
        Long targetId;
        if(canApplyForOthers(auth)){
            targetId = asked!=null ? asked : mine;
            if(targetId==null) throw new IllegalArgumentException("Select an employee to apply for");
        }else{
            if(mine==null) throw new IllegalArgumentException(
                "Your login is not linked to an employee record — ask HR to link it before applying for leave");
            if(asked!=null&&!asked.equals(mine))
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only apply for your own leave"));
            targetId = mine;
        }
        Employee emp=empRepo.findById(targetId).orElseThrow(()->new IllegalArgumentException("Employee not found"));
        // Date sanity, the day count, the quota check and the audit row all live in
        // LeaveApplicationService, because the HR Assistant's apply-leave tool files
        // through the very same method. Authorisation above stays here: who may file
        // for whom depends on the caller, not on the request.
        LeaveRequest saved=applications.apply(emp,
            String.valueOf(body.get("leaveType")),
            LocalDate.parse(body.get("fromDate").toString()),
            LocalDate.parse(body.get("toDate").toString()),
            body.get("totalDays")==null?null:Integer.parseInt(body.get("totalDays").toString()),
            body.get("reason")==null?null:body.get("reason").toString());
        return ResponseEntity.ok(ApiResponse.ok("Applied",saved));
    }

    @PutMapping("/requests/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')") @Transactional
    public ResponseEntity<ApiResponse<LeaveRequest>> approve(@PathVariable Long id){
        return repo.findById(id).map(lr->{
            // Only a Pending → Approved transition spends balance. Approving twice
            // must not charge the employee twice.
            boolean spend = lr.getStatus()!=LeaveRequest.Status.Approved;
            lr.setStatus(LeaveRequest.Status.Approved);
            LeaveRequest saved=repo.save(lr);
            if(spend&&saved.getEmployee()!=null)
                balances.adjust(saved.getEmployee().getId(),saved.getLeaveType(),
                    saved.getFromDate().getYear(),days(saved));
            audit.log("LEAVE_APPROVED","LeaveRequest",id,null,label(saved));
            return ResponseEntity.ok(ApiResponse.ok("Approved",saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/requests/{id}/reject") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')") @Transactional
    public ResponseEntity<ApiResponse<LeaveRequest>> reject(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> body){
        return repo.findById(id).map(lr->{
            // Reversing an approval hands the days back.
            boolean refund = lr.getStatus()==LeaveRequest.Status.Approved;
            lr.setStatus(LeaveRequest.Status.Rejected);
            if(body!=null&&body.get("reason")!=null) lr.setRejectionReason(body.get("reason").toString());
            LeaveRequest saved=repo.save(lr);
            if(refund&&saved.getEmployee()!=null)
                balances.adjust(saved.getEmployee().getId(),saved.getLeaveType(),
                    saved.getFromDate().getYear(),-days(saved));
            audit.log("LEAVE_REJECTED","LeaveRequest",id,null,label(saved));
            return ResponseEntity.ok(ApiResponse.ok("Rejected",saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/requests/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        LeaveRequest lr=repo.findById(id).orElse(null);
        if(lr==null) return ResponseEntity.notFound().build();
        if(lr.getStatus()==LeaveRequest.Status.Approved&&lr.getEmployee()!=null)
            balances.adjust(lr.getEmployee().getId(),lr.getLeaveType(),lr.getFromDate().getYear(),-days(lr));
        repo.deleteById(id);
        audit.log("LEAVE_DELETED","LeaveRequest",id,label(lr),null);
        return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }

    private static int days(LeaveRequest lr){ return lr.getTotalDays()==null?0:lr.getTotalDays(); }
    private static String label(LeaveRequest lr){
        return (lr.getEmployee()==null?"—":lr.getEmployee().getFullName())+" · "+lr.getLeaveType()+" · "+days(lr)+"d";
    }

    // ── Balances ──
    // Anyone may read their own; only an approver may read somebody else's.
    @GetMapping("/balances") public ResponseEntity<ApiResponse<List<LeaveBalance>>> myBalances(Authentication auth){
        Long me=ownEmployeeId(auth);
        return ResponseEntity.ok(ApiResponse.ok(me==null?List.of():balances.forEmployee(me)));
    }
    @GetMapping("/balances/{employeeId}") public ResponseEntity<ApiResponse<List<LeaveBalance>>> balancesFor(
            @PathVariable Long employeeId,Authentication auth){
        Long me=ownEmployeeId(auth);
        if(!canApprove(auth)&&!employeeId.equals(me))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not your leave balance"));
        return ResponseEntity.ok(ApiResponse.ok(balances.forEmployee(employeeId)));
    }
    @GetMapping("/quotas") public ResponseEntity<ApiResponse<Map<String,Integer>>> quotas(){
        return ResponseEntity.ok(ApiResponse.ok(balances.quotas()));
    }
    @DeleteMapping("/requests") @PreAuthorize("hasRole('SUPER_ADMIN')") public ResponseEntity<ApiResponse<Void>> deleteAll(@RequestParam(defaultValue="false") boolean confirm){if(!confirm)return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));repo.deleteAll();return ResponseEntity.ok(ApiResponse.ok("All leave deleted",null));}
}

// ── PAYROLL ───────────────────────────────────────────────────────────
@RestController @RequestMapping("/payroll") @RequiredArgsConstructor
class PayrollController {
    private final PayrollRepository repo;
    private final EmployeeRepository empRepo;
    private final AttendanceRepository attRepo;
    private final LeaveRequestRepository leaveRepo;
    private final SettingsService settings;
    private final AuditService audit;
    // Payslips are pay data: HR and owners only, never the whole company.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @GetMapping public ResponseEntity<ApiResponse<List<Payroll>>> getAll(){return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));}
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @GetMapping("/month/{month}") public ResponseEntity<ApiResponse<List<Payroll>>> byMonth(@PathVariable String month){return ResponseEntity.ok(ApiResponse.ok(repo.findByPayMonth(month)));}
    // Rates come from company settings rather than literals in this method, and the
    // month's attendance now feeds loss of pay: an unauthorised absence costs a day,
    // an approved leave day costs nothing.
    @PostMapping("/generate") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") public ResponseEntity<ApiResponse<Map<String,Object>>> generate(@RequestBody Map<String,Object> body){
        String month=body.get("payMonth").toString();
        YearMonth ym;
        try{ ym=YearMonth.parse(month); }
        catch(Exception e){ throw new IllegalArgumentException("Pay month must look like 2026-04"); }
        LocalDate first=ym.atDay(1), last=ym.atEndOfMonth();

        CompanySettings cfg=settings.get();
        BigDecimal hraR=pct(cfg.getHraPercent(),"40"), allowR=pct(cfg.getSpecialAllowancePercent(),"16.6"),
                   pfR=pct(cfg.getPfPercent(),"12"), esiR=pct(cfg.getEsiPercent(),"0.75"),
                   tdsR=pct(cfg.getTdsPercent(),"9");
        BigDecimal esiCap=cfg.getEsiThreshold()==null?new BigDecimal("21000"):cfg.getEsiThreshold();
        BigDecimal pt=cfg.getProfessionalTax()==null?new BigDecimal("200"):cfg.getProfessionalTax();
        int workingDays=cfg.getWorkingDaysPerMonth()==null||cfg.getWorkingDaysPerMonth()<1?26:cfg.getWorkingDaysPerMonth();
        boolean chargeAbsence=!Boolean.FALSE.equals(cfg.getDeductAbsentDays());

        List<Employee> emps=empRepo.findByStatus(Employee.Status.Active);
        int generated=0,skipped=0;
        for(Employee emp:emps){
            if(repo.findByEmployeeIdAndPayMonth(emp.getId(),month).isPresent()){ skipped++; continue; }
            BigDecimal basic=emp.getBasicSalary()!=null?emp.getBasicSalary():BigDecimal.ZERO;

            // Days already covered by an approved leave request are protected from LOP
            // even if somebody also marked the person absent on the attendance sheet.
            Set<LocalDate> onLeave=new HashSet<>();
            for(LeaveRequest lr:leaveRepo.findByEmployeeIdAndStatus(emp.getId(),LeaveRequest.Status.Approved)){
                if(lr.getFromDate()==null||lr.getToDate()==null) continue;
                for(LocalDate d=lr.getFromDate();!d.isAfter(lr.getToDate());d=d.plusDays(1))
                    if(!d.isBefore(first)&&!d.isAfter(last)) onLeave.add(d);
            }

            List<Attendance> recs=attRepo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(emp.getId(),first,last);
            int present=0,absent=0,half=0,markedLeave=0;
            BigDecimal chargeable=BigDecimal.ZERO;
            for(Attendance a:recs){
                if(a.getStatus()==null) continue;
                switch(a.getStatus()){
                    case Present, Work_From_Home -> present++;
                    case Leave -> markedLeave++;
                    case Half_Day -> {
                        half++;
                        if(!onLeave.contains(a.getAttendanceDate())) chargeable=chargeable.add(new BigDecimal("0.5"));
                    }
                    case Absent -> {
                        absent++;
                        if(!onLeave.contains(a.getAttendanceDate())) chargeable=chargeable.add(BigDecimal.ONE);
                    }
                    default -> {}
                }
            }
            int leaveDays=Math.max(markedLeave,onLeave.size());

            BigDecimal hra=money(basic.multiply(hraR));
            BigDecimal allow=money(basic.multiply(allowR));
            BigDecimal gross=money(basic.add(hra).add(allow));
            BigDecimal perDay=basic.divide(new BigDecimal(workingDays),4,RoundingMode.HALF_UP);
            BigDecimal lop=chargeAbsence?money(perDay.multiply(chargeable)):BigDecimal.ZERO;
            BigDecimal pf=money(basic.multiply(pfR));
            BigDecimal esi=gross.compareTo(esiCap)<=0?money(gross.multiply(esiR)):BigDecimal.ZERO;
            BigDecimal tds=money(gross.multiply(tdsR));
            BigDecimal totalDed=money(pf.add(esi).add(pt).add(tds).add(lop));
            BigDecimal net=money(gross.subtract(totalDed));

            repo.save(Payroll.builder().employee(emp).payMonth(month).basicSalary(basic).hra(hra)
                .specialAllowance(allow).performanceBonus(BigDecimal.ZERO).grossSalary(gross)
                .pfDeduction(pf).esiDeduction(esi).professionalTax(pt).tdsDeduction(tds)
                .lopDeduction(lop).presentDays(present).absentDays(absent).leaveDays(leaveDays)
                .payableDays(Math.max(0,workingDays-chargeable.setScale(0,RoundingMode.HALF_UP).intValue()))
                .totalDeduction(totalDed).netSalary(net)
                .status(Payroll.Status.Processed).build());
            generated++;
        }
        audit.log("PAYROLL_GENERATED","Payroll",null,null,month+" · "+generated+" payslips");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("generated",generated); out.put("skipped",skipped); out.put("month",month);
        out.put("workingDaysPerMonth",workingDays);
        return ResponseEntity.ok(ApiResponse.ok("Generated",out));
    }
    // Percentages are stored as 40, not 0.40 — divide once, here.
    private static BigDecimal pct(BigDecimal v,String fallback){
        BigDecimal p=v==null?new BigDecimal(fallback):v;
        return p.divide(new BigDecimal("100"),6,RoundingMode.HALF_UP);
    }
    private static BigDecimal money(BigDecimal v){ return v.setScale(2,RoundingMode.HALF_UP); }

    // Attendance behind the numbers, so the Payroll screen can show why a payslip is
    // short without the operator opening the attendance module.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    @GetMapping("/attendance-summary") public ResponseEntity<ApiResponse<Map<String,Object>>> attendanceSummary(@RequestParam String month){
        YearMonth ym=YearMonth.parse(month);
        LocalDate first=ym.atDay(1),last=ym.atEndOfMonth();
        List<Map<String,Object>> rows=new ArrayList<>();
        int totPresent=0,totAbsent=0,totLeave=0;
        for(Employee e:empRepo.findByStatus(Employee.Status.Active)){
            List<Attendance> recs=attRepo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(e.getId(),first,last);
            int p=0,a=0,l=0,h=0;
            for(Attendance r:recs){
                if(r.getStatus()==null) continue;
                switch(r.getStatus()){
                    case Present, Work_From_Home -> p++;
                    case Absent -> a++;
                    case Leave -> l++;
                    case Half_Day -> h++;
                    default -> {}
                }
            }
            totPresent+=p; totAbsent+=a; totLeave+=l;
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("employeeId",e.getId()); row.put("name",e.getFullName()); row.put("empId",e.getEmpId());
            row.put("present",p); row.put("absent",a); row.put("leave",l); row.put("halfDay",h);
            row.put("marked",recs.size());
            rows.add(row);
        }
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("month",month); out.put("present",totPresent); out.put("absent",totAbsent); out.put("leave",totLeave);
        out.put("rows",rows);
        return ResponseEntity.ok(ApiResponse.ok(out));
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
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
    private final AttendanceRepository attRepo;
    private final ResignationRepository resignRepo;

    // The original four counters are still the first four keys, so an older frontend
    // keeps working; everything after them is new.
    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Object>>> stats(){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("totalEmployees",empRepo.count());
        m.put("activeEmployees",empRepo.countActive());
        m.put("totalDepartments",deptRepo.count());
        m.put("pendingLeaves",leaveRepo.findByStatus(LeaveRequest.Status.Pending).size());
        m.put("pendingResignations",resignRepo.findByStatus(Resignation.Status.Pending).size());

        List<Employee> all=empRepo.findAll();

        // Headcount per department, seeded at zero so an empty department still charts.
        Map<String,Long> counts=new LinkedHashMap<>();
        deptRepo.findAll().forEach(d->counts.put(d.getName(),0L));
        for(Employee e:all) counts.merge(e.getDepartment()!=null?e.getDepartment().getName():"Unassigned",1L,Long::sum);
        List<Map<String,Object>> byDept=new ArrayList<>();
        counts.forEach((n,c)->{ Map<String,Object> r=new LinkedHashMap<>(); r.put("name",n); r.put("count",c); byDept.add(r); });
        m.put("byDepartment",byDept);

        // Present rate for each of the last seven days. A day nobody marked reads as
        // null, not 0 — "not recorded" and "everybody was out" are different facts.
        List<Map<String,Object>> trend=new ArrayList<>();
        LocalDate today=LocalDate.now();
        for(int i=6;i>=0;i--){
            LocalDate d=today.minusDays(i);
            List<Attendance> recs=attRepo.findByAttendanceDate(d);
            long present=recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Present
                                              ||a.getStatus()==Attendance.AttStatus.Work_From_Home).count();
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("date",d.toString());
            row.put("marked",recs.size());
            row.put("present",present);
            row.put("rate",recs.isEmpty()?null:Math.round(present*1000.0/recs.size())/10.0);
            trend.add(row);
        }
        m.put("attendanceTrend",trend);

        m.put("birthdays",upcoming(all,true,today));
        m.put("anniversaries",upcoming(all,false,today));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    // Today through the next six days, comparing month and day only so the year the
    // person was born or joined is irrelevant. 29 February falls back to the 28th in a
    // common year rather than vanishing.
    private List<Map<String,Object>> upcoming(List<Employee> all,boolean birthday,LocalDate today){
        List<Map<String,Object>> out=new ArrayList<>();
        for(Employee e:all){
            if(e.getStatus()==Employee.Status.Inactive||e.getStatus()==Employee.Status.Terminated) continue;
            LocalDate src=birthday?e.getDateOfBirth():e.getJoinDate();
            if(src==null) continue;
            for(int i=0;i<7;i++){
                LocalDate day=today.plusDays(i);
                int m=src.getMonthValue(), d=src.getDayOfMonth();
                if(m==2&&d==29&&!day.isLeapYear()&&day.getMonthValue()==2) d=28;
                if(day.getMonthValue()==m&&day.getDayOfMonth()==d){
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("employeeId",e.getId()); row.put("name",e.getFullName());
                    row.put("date",day.toString()); row.put("daysAway",i);
                    row.put("department",e.getDepartment()!=null?e.getDepartment().getName():null);
                    if(!birthday&&e.getJoinDate()!=null) row.put("years",day.getYear()-e.getJoinDate().getYear());
                    out.add(row);
                    break;
                }
            }
        }
        out.sort(Comparator.comparing(r->String.valueOf(r.get("date"))));
        return out;
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
    private final RosterEmployeeRepository rosterRepo;
    private final LeaveBalanceRepository balRepo;
    private final SalaryRevisionRepository revRepo;
    private final EmployeeDocumentRepository docRepo;
    private final ResignationRepository resignRepo;
    private final AuditLogRepository auditRepo;
    private final AuditService audit;

    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Long>>> stats(){
        Map<String,Long> m=new LinkedHashMap<>();
        m.put("employees",empRepo.count()); m.put("leave",leaveRepo.count());
        m.put("payroll",payRepo.count());   m.put("candidates",candRepo.count());
        m.put("holidays",holRepo.count());  m.put("roster",rosterRepo.count());
        m.put("attendance",attRepo.count());m.put("performance",perfRepo.count());
        m.put("documents",docRepo.count()); m.put("resignations",resignRepo.count());
        m.put("auditLogs",auditRepo.count());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }
    @DeleteMapping("/delete/employees") @Transactional public ResponseEntity<ApiResponse<Map<String,Object>>> delEmp(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=empRepo.count();
        // Everything that FKs to an employee has to go first, or H2 rejects the delete.
        // Only employee-linked attendance goes: the imported roster is a separate list
        // and its attendance history has no business disappearing with the employees.
        payRepo.deleteAll(); attRepo.deleteEmployeeLinked(); leaveRepo.deleteAll(); perfRepo.deleteAll();
        balRepo.deleteAll(); revRepo.deleteAll(); docRepo.deleteAll(); resignRepo.deleteAll();
        empRepo.deleteAll();
        log.warn("ADMIN: {} employees deleted",count);
        audit.log("BULK_DELETE","Employee",null,count+" employees",null);
        return ResponseEntity.ok(ApiResponse.ok("All employees deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/leave") public ResponseEntity<ApiResponse<Map<String,Object>>> delLeave(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=leaveRepo.count(); leaveRepo.deleteAll();
        audit.log("BULK_DELETE","LeaveRequest",null,count+" leave requests",null);
        return ResponseEntity.ok(ApiResponse.ok("All leave deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/payroll") public ResponseEntity<ApiResponse<Map<String,Object>>> delPay(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=payRepo.count(); payRepo.deleteAll();
        audit.log("BULK_DELETE","Payroll",null,count+" payslips",null);
        return ResponseEntity.ok(ApiResponse.ok("All payroll deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/candidates") public ResponseEntity<ApiResponse<Map<String,Object>>> delCands(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=candRepo.count(); candRepo.deleteAll();
        audit.log("BULK_DELETE","Candidate",null,count+" candidates",null);
        return ResponseEntity.ok(ApiResponse.ok("All candidates deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/holidays") public ResponseEntity<ApiResponse<Map<String,Object>>> delHols(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=holRepo.count(); holRepo.deleteAll();
        audit.log("BULK_DELETE","Holiday",null,count+" holidays",null);
        return ResponseEntity.ok(ApiResponse.ok("All holidays deleted",Map.of("deletedCount",count)));
    }
    @DeleteMapping("/delete/roster") @Transactional public ResponseEntity<ApiResponse<Map<String,Object>>> delRoster(@RequestParam(defaultValue="false") boolean confirm){
        if(!confirm) return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirm=true"));
        long count=rosterRepo.count();
        attRepo.deleteRosterLinked(); rosterRepo.deleteAll();
        log.warn("ADMIN: {} imported attendance names deleted",count);
        audit.log("BULK_DELETE","RosterEmployee",null,count+" imported names",null);
        return ResponseEntity.ok(ApiResponse.ok("Imported attendance list cleared",Map.of("deletedCount",count)));
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
        deleted.put("leaveBalances",balRepo.count()); balRepo.deleteAll();
        deleted.put("salaryRevisions",revRepo.count()); revRepo.deleteAll();
        deleted.put("documents",docRepo.count()); docRepo.deleteAll();
        deleted.put("resignations",resignRepo.count()); resignRepo.deleteAll();
        deleted.put("candidates",candRepo.count()); candRepo.deleteAll();
        deleted.put("holidays",holRepo.count()); holRepo.deleteAll();
        deleted.put("employees",empRepo.count()); empRepo.deleteAll();
        // After attendance is gone nothing FKs to the roster any more.
        deleted.put("roster",rosterRepo.count()); rosterRepo.deleteAll();
        log.warn("ADMIN: FULL RESET performed");
        // The audit table deliberately survives a reset: the record of who erased the
        // data is the one thing that must outlive the data.
        audit.log("FULL_RESET","System",null,deleted.toString(),null);
        return ResponseEntity.ok(ApiResponse.ok("Full reset complete",deleted));
    }
}

// ── ATTENDANCE ROSTER ────────────────────────────────────────────────
// The name-only employee list used by companies that keep staff in a spreadsheet
// and only want to take attendance. Imported from Excel/PDF or typed in one at a
// time. Name is the only required field; everything else is optional and only
// filled when the operator chooses to map those columns.
@RestController @RequestMapping("/roster") @RequiredArgsConstructor @Slf4j
class RosterController {
    private final RosterEmployeeRepository rosterRepo;
    private final AttendanceRepository attRepo;

    @GetMapping public ResponseEntity<ApiResponse<List<RosterEmployee>>> getAll(){
        return ResponseEntity.ok(ApiResponse.ok(rosterRepo.findAllByOrderByNameAsc()));
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<RosterEmployee>> create(@RequestBody Map<String,String> body){
        String name = clean(body.get("name"));
        if(name.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("Employee name is required"));
        if(existingKeys().contains(RosterEmployee.key(name)))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(name+" is already on the attendance list"));
        return ResponseEntity.ok(ApiResponse.ok("Added", rosterRepo.save(fill(new RosterEmployee(), body, name, "MANUAL"))));
    }

    // Body: {source:"EXCEL"|"PDF", people:[{name, code?, department?, designation?, phone?}]}
    // Skips blanks and anything already on the list, and reports both counts so the
    // UI can say "8 added, 2 duplicates skipped" instead of silently doing nothing.
    @PostMapping("/import") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> importList(@RequestBody Map<String,Object> body){
        String source = body.get("source")!=null ? body.get("source").toString() : "EXCEL";
        @SuppressWarnings("unchecked") List<Map<String,String>> people =
            (List<Map<String,String>>) body.getOrDefault("people", List.of());
        Set<String> keys = existingKeys();
        List<String> added=new ArrayList<>(), duplicates=new ArrayList<>();
        int blanks=0;
        for(Map<String,String> p : people){
            String name = clean(p.get("name"));
            if(name.isEmpty()){ blanks++; continue; }
            String key = RosterEmployee.key(name);
            // Guard against duplicates already stored AND repeats within this one file.
            if(!keys.add(key)){ duplicates.add(name); continue; }
            rosterRepo.save(fill(new RosterEmployee(), p, name, source));
            added.add(name);
        }
        log.info("ROSTER import from {}: {} added, {} duplicates, {} blank rows", source, added.size(), duplicates.size(), blanks);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("added",added.size()); out.put("skipped",duplicates.size()); out.put("blankRows",blanks);
        out.put("addedNames",added); out.put("skippedNames",duplicates);
        out.put("total",rosterRepo.count());
        return ResponseEntity.ok(ApiResponse.ok(added.size()+" added, "+duplicates.size()+" duplicates skipped", out));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<RosterEmployee>> update(@PathVariable Long id,@RequestBody Map<String,String> body){
        return rosterRepo.findById(id).map(r->{
            String name = body.containsKey("name") ? clean(body.get("name")) : r.getName();
            if(name.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.<RosterEmployee>error("Employee name is required"));
            // A rename may not collide with somebody else already on the list.
            String key = RosterEmployee.key(name);
            boolean taken = rosterRepo.findAll().stream()
                .anyMatch(o->!o.getId().equals(id) && RosterEmployee.key(o.getName()).equals(key));
            if(taken) return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<RosterEmployee>error(name+" is already on the attendance list"));
            return ResponseEntity.ok(ApiResponse.ok("Updated", rosterRepo.save(fill(r, body, name, r.getSource()))));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        if(!rosterRepo.existsById(id)) return ResponseEntity.notFound().build();
        // Attendance rows FK to the roster entry, so they go first (same reason
        // EmployeeController.delete clears children before the parent).
        attRepo.deleteByRosterEmployeeId(id);
        rosterRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("Removed from attendance list",null));
    }

    private Set<String> existingKeys(){
        Set<String> keys=new HashSet<>();
        for(RosterEmployee r : rosterRepo.findAll()) keys.add(RosterEmployee.key(r.getName()));
        return keys;
    }
    // "  Rahul   Kumar " → "Rahul Kumar". Spreadsheet cells are full of stray whitespace.
    private static String clean(String s){ return s==null?"":s.trim().replaceAll("\\s+"," "); }
    private static String opt(Map<String,String> b,String k,String current){
        if(!b.containsKey(k)) return current;
        String v=clean(b.get(k));
        return v.isEmpty()?null:v;
    }
    private static RosterEmployee fill(RosterEmployee r,Map<String,String> b,String name,String source){
        r.setName(name);
        r.setCode(opt(b,"code",r.getCode()));
        r.setDepartment(opt(b,"department",r.getDepartment()));
        r.setDesignation(opt(b,"designation",r.getDesignation()));
        r.setPhone(opt(b,"phone",r.getPhone()));
        if(r.getIsActive()==null) r.setIsActive(true);
        if(r.getSource()==null) r.setSource(source);
        return r;
    }
}

// ── ATTENDANCE ───────────────────────────────────────────────────────
@RestController @RequestMapping("/attendance") @RequiredArgsConstructor
class AttendanceController {
    private final AttendanceRepository attRepo;
    private final EmployeeRepository empRepo;
    private final RosterEmployeeRepository rosterRepo;

    // Marking is already limited to these roles; reading the whole company's
    // attendance should match it.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    @GetMapping public ResponseEntity<ApiResponse<List<Attendance>>> byDate(@RequestParam(required=false) String date){
        LocalDate d = (date==null||date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findByAttendanceDate(d)));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    @GetMapping("/all") public ResponseEntity<ApiResponse<List<Attendance>>> all(){
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findAll()));
    }

    @GetMapping("/employee/{empId}") public ResponseEntity<ApiResponse<List<Attendance>>> byEmployee(@PathVariable Long empId){
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findByEmployeeId(empId)));
    }

    // Backs the period reports (daily / monthly / 6 months / 1 year). Inclusive both ends.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    @GetMapping("/range") public ResponseEntity<ApiResponse<List<Attendance>>> range(
            @RequestParam String from,@RequestParam String to){
        LocalDate a=LocalDate.parse(from), b=LocalDate.parse(to);
        if(a.isAfter(b)){ LocalDate t=a; a=b; b=t; }
        return ResponseEntity.ok(ApiResponse.ok(attRepo.findByAttendanceDateBetweenOrderByAttendanceDateAsc(a,b)));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    @GetMapping("/summary") public ResponseEntity<ApiResponse<Map<String,Object>>> summary(@RequestParam(required=false) String date){
        LocalDate d = (date==null||date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        List<Attendance> recs = attRepo.findByAttendanceDate(d);
        long present = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Present||a.getStatus()==Attendance.AttStatus.Work_From_Home).count();
        long absent  = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Absent).count();
        long leave   = recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Leave).count();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("date",d.toString(),"totalEmployees",empRepo.count(),
            "rosterEmployees",rosterRepo.count(),"totalPeople",empRepo.count()+rosterRepo.count(),
            "marked",recs.size(),"present",present,"absent",absent,"leave",leave)));
    }

    // Bulk mark/update attendance for a date.
    // Body: {date:"YYYY-MM-DD", records:[{employeeId|rosterEmployeeId, status, checkIn?, checkOut?, notes?}]}
    // Each record identifies EITHER a managed employee or an imported roster name.
    @PostMapping("/mark") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> mark(@RequestBody Map<String,Object> body){
        LocalDate d = body.get("date")!=null ? LocalDate.parse(body.get("date").toString()) : LocalDate.now();
        @SuppressWarnings("unchecked") List<Map<String,Object>> records = (List<Map<String,Object>>) body.getOrDefault("records", List.of());
        int saved=0; List<String> errs=new ArrayList<>();
        for(Map<String,Object> r : records){
            try{
                Object empRef = r.get("employeeId"), rosterRef = r.get("rosterEmployeeId");
                if(empRef==null && rosterRef==null)
                    throw new IllegalArgumentException("Record needs employeeId or rosterEmployeeId");
                Attendance a;
                if(empRef!=null){
                    Long empId = Long.valueOf(empRef.toString());
                    Employee emp = empRepo.findById(empId).orElseThrow(()->new IllegalArgumentException("Employee "+empId+" not found"));
                    a = attRepo.findByEmployeeIdAndAttendanceDate(empId, d)
                        .orElse(Attendance.builder().employee(emp).attendanceDate(d).build());
                }else{
                    Long rosterId = Long.valueOf(rosterRef.toString());
                    RosterEmployee person = rosterRepo.findById(rosterId).orElseThrow(()->new IllegalArgumentException("Roster entry "+rosterId+" not found"));
                    a = attRepo.findByRosterEmployeeIdAndAttendanceDate(rosterId, d)
                        .orElse(Attendance.builder().rosterEmployee(person).attendanceDate(d).build());
                }
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

    // Ratings and review comments about other people.
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
    @GetMapping public ResponseEntity<ApiResponse<List<PerformanceReview>>> getAll(){
        return ResponseEntity.ok(ApiResponse.ok(perfRepo.findAll()));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','MANAGER')")
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

// ── COMPANY SETTINGS ─────────────────────────────────────────────────
// One row, read by everyone (payslips and the header need the company's name),
// written only by the owner.
@RestController @RequestMapping("/settings") @RequiredArgsConstructor @Slf4j
class CompanySettingsController {
    private final CompanySettingsRepository repo;
    private final SettingsService settings;
    private final AuditService audit;

    @GetMapping public ResponseEntity<ApiResponse<CompanySettings>> get(){
        return ResponseEntity.ok(ApiResponse.ok(settings.get()));
    }

    @PutMapping @PreAuthorize("hasRole('SUPER_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<CompanySettings>> update(@RequestBody Map<String,Object> b){
        CompanySettings s=settings.get();
        String before=s.getCompanyName()+" · HRA "+s.getHraPercent()+"% · PF "+s.getPfPercent()+"%";
        text(b,"companyName",s::setCompanyName); text(b,"logo",s::setLogo);
        text(b,"address",s::setAddress); text(b,"city",s::setCity); text(b,"state",s::setState);
        text(b,"pincode",s::setPincode); text(b,"phone",s::setPhone); text(b,"email",s::setEmail);
        text(b,"website",s::setWebsite); text(b,"gstin",s::setGstin);
        text(b,"financialYearStart",s::setFinancialYearStart); text(b,"currency",s::setCurrency);
        text(b,"currencySymbol",s::setCurrencySymbol); text(b,"weeklyOff",s::setWeeklyOff);
        num(b,"workingDaysPerWeek",s::setWorkingDaysPerWeek);
        num(b,"workingDaysPerMonth",s::setWorkingDaysPerMonth);
        num(b,"casualLeaveQuota",s::setCasualLeaveQuota); num(b,"sickLeaveQuota",s::setSickLeaveQuota);
        num(b,"earnedLeaveQuota",s::setEarnedLeaveQuota); num(b,"compOffQuota",s::setCompOffQuota);
        dec(b,"hraPercent",s::setHraPercent); dec(b,"specialAllowancePercent",s::setSpecialAllowancePercent);
        dec(b,"pfPercent",s::setPfPercent); dec(b,"esiThreshold",s::setEsiThreshold);
        dec(b,"esiPercent",s::setEsiPercent); dec(b,"professionalTax",s::setProfessionalTax);
        dec(b,"tdsPercent",s::setTdsPercent);
        if(b.containsKey("deductAbsentDays")) s.setDeductAbsentDays(Boolean.valueOf(String.valueOf(b.get("deductAbsentDays"))));
        // A zero or negative divisor would make every per-day rate meaningless.
        if(s.getWorkingDaysPerMonth()==null||s.getWorkingDaysPerMonth()<1) s.setWorkingDaysPerMonth(26);
        CompanySettings saved=repo.save(s);
        audit.log("SETTINGS_UPDATED","CompanySettings",saved.getId(),before,
            saved.getCompanyName()+" · HRA "+saved.getHraPercent()+"% · PF "+saved.getPfPercent()+"%");
        return ResponseEntity.ok(ApiResponse.ok("Settings saved",saved));
    }

    private static void text(Map<String,Object> b,String k,java.util.function.Consumer<String> set){
        if(!b.containsKey(k)) return;
        Object v=b.get(k);
        if(v==null){ set.accept(null); return; }
        String s=v.toString().trim();
        set.accept(s.isEmpty()?null:s);
    }
    private static void num(Map<String,Object> b,String k,java.util.function.Consumer<Integer> set){
        if(!b.containsKey(k)||b.get(k)==null||b.get(k).toString().isBlank()) return;
        try{ set.accept(Integer.valueOf(b.get(k).toString().trim())); }catch(NumberFormatException ignored){}
    }
    private static void dec(Map<String,Object> b,String k,java.util.function.Consumer<BigDecimal> set){
        if(!b.containsKey(k)||b.get(k)==null||b.get(k).toString().isBlank()) return;
        try{ set.accept(new BigDecimal(b.get(k).toString().trim())); }catch(NumberFormatException ignored){}
    }
}

// ── AUDIT LOG ────────────────────────────────────────────────────────
@RestController @RequestMapping("/audit") @RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AuditController {
    private final AuditLogRepository repo;

    // Paged at the database, not in the browser: this is the one table that only ever
    // grows, and a year of it will not fit in a findAll().
    @GetMapping public ResponseEntity<ApiResponse<Map<String,Object>>> search(
            @RequestParam(required=false) String action,
            @RequestParam(required=false) String who,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size){
        LocalDateTime f=parse(from,true), t=parse(to,false);
        String a=(action==null||action.isBlank())?null:action.trim();
        String w=(who==null||who.isBlank())?null:who.trim();
        var result=repo.search(a,w,f,t,PageRequest.of(Math.max(0,page),Math.min(200,Math.max(1,size))));
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("rows",result.getContent());
        out.put("page",result.getNumber());
        out.put("size",result.getSize());
        out.put("totalRows",result.getTotalElements());
        out.put("totalPages",result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/actions") public ResponseEntity<ApiResponse<List<String>>> actions(){
        return ResponseEntity.ok(ApiResponse.ok(repo.distinctActions()));
    }

    // A bare date on the "to" side means the whole of that day — otherwise a filter
    // ending today would hide everything that happened after midnight.
    private static LocalDateTime parse(String s,boolean startOfDay){
        if(s==null||s.isBlank()) return null;
        try{ return LocalDateTime.parse(s.trim()); }catch(Exception ignored){}
        try{ LocalDate d=LocalDate.parse(s.trim()); return startOfDay?d.atStartOfDay():d.atTime(23,59,59); }
        catch(Exception ignored){ return null; }
    }
}

// ── SALARY REVISIONS ─────────────────────────────────────────────────
@RestController @RequestMapping("/salary-revisions") @RequiredArgsConstructor
class SalaryRevisionController {
    private final SalaryRevisionRepository repo;
    private final EmployeeRepository empRepo;
    private final AuditService audit;

    // Pay history is pay data. HR and the owner see anybody's; everyone else sees only
    // their own, which is the one they are entitled to.
    @GetMapping("/employee/{id}") public ResponseEntity<ApiResponse<List<SalaryRevision>>> history(
            @PathVariable Long id,Authentication auth){
        if(!EmployeeController.canSeePay(auth)){
            Employee e=empRepo.findById(id).orElse(null);
            if(e==null||!EmployeeController.isSelf(e,auth==null?null:auth.getName()))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not your salary history"));
        }
        return ResponseEntity.ok(ApiResponse.ok(repo.findByEmployeeIdOrderByEffectiveDateDesc(id)));
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<SalaryRevision>> create(@RequestBody Map<String,Object> b){
        if(b.get("employeeId")==null) throw new IllegalArgumentException("Select an employee");
        Employee emp=empRepo.findById(Long.valueOf(b.get("employeeId").toString()))
            .orElseThrow(()->new IllegalArgumentException("Employee not found"));
        if(b.get("newSalary")==null||b.get("newSalary").toString().isBlank())
            throw new IllegalArgumentException("Enter the new salary");
        BigDecimal newSalary;
        try{ newSalary=new BigDecimal(b.get("newSalary").toString().trim()); }
        catch(NumberFormatException e){ throw new IllegalArgumentException("The new salary must be a number"); }
        if(newSalary.signum()<0) throw new IllegalArgumentException("A salary cannot be negative");
        BigDecimal oldSalary=emp.getBasicSalary();
        LocalDate effective=b.get("effectiveDate")==null||b.get("effectiveDate").toString().isBlank()
            ? LocalDate.now() : LocalDate.parse(b.get("effectiveDate").toString());

        SalaryRevision rev=repo.save(SalaryRevision.builder().employee(emp)
            .oldSalary(oldSalary).newSalary(newSalary).effectiveDate(effective)
            .reason(b.get("reason")==null?null:b.get("reason").toString())
            .revisedBy(audit.currentUser()).build());
        // The revision is the record; the employee row only carries the current value.
        emp.setBasicSalary(newSalary);
        empRepo.save(emp);
        audit.log("SALARY_CHANGED","Employee",emp.getId(),String.valueOf(oldSalary),String.valueOf(newSalary));
        return ResponseEntity.ok(ApiResponse.ok("Salary revised",rev));
    }
}

// ── RESIGNATIONS ─────────────────────────────────────────────────────
@RestController @RequestMapping("/resignations") @RequiredArgsConstructor @Slf4j
class ResignationController {
    private final ResignationRepository repo;
    private final EmployeeRepository empRepo;
    private final AuditService audit;

    static boolean isHr(Authentication auth){
        if(auth==null) return false;
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch(a->a.equals("ROLE_SUPER_ADMIN")||a.equals("ROLE_HR_ADMIN"));
    }
    private Employee me(Authentication auth){
        if(auth==null||auth.getName()==null) return null;
        return empRepo.findByEmail(auth.getName()).orElse(null);
    }

    // HR sees the queue; anybody else sees only the letters they wrote themselves.
    @GetMapping public ResponseEntity<ApiResponse<List<Resignation>>> all(Authentication auth){
        if(isHr(auth)) return ResponseEntity.ok(ApiResponse.ok(repo.findAll()));
        Employee m=me(auth);
        return ResponseEntity.ok(ApiResponse.ok(m==null?List.of():repo.findByEmployeeIdOrderByCreatedAtDesc(m.getId())));
    }

    @PostMapping @Transactional
    public ResponseEntity<ApiResponse<Resignation>> submit(@RequestBody Map<String,Object> b,Authentication auth){
        Employee target;
        if(isHr(auth)&&b.get("employeeId")!=null){
            target=empRepo.findById(Long.valueOf(b.get("employeeId").toString()))
                .orElseThrow(()->new IllegalArgumentException("Employee not found"));
        }else{
            target=me(auth);
            if(target==null) throw new IllegalArgumentException(
                "Your login is not linked to an employee record — ask HR to link it first");
        }
        // One open letter at a time, or approving the older one would silently
        // contradict the newer.
        boolean open=repo.findByEmployeeIdOrderByCreatedAtDesc(target.getId()).stream()
            .anyMatch(r->r.getStatus()==Resignation.Status.Pending);
        if(open) return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("There is already a resignation awaiting a decision"));

        LocalDate on=b.get("resignationDate")==null||b.get("resignationDate").toString().isBlank()
            ? LocalDate.now() : LocalDate.parse(b.get("resignationDate").toString());
        int notice=b.get("noticePeriodDays")==null||b.get("noticePeriodDays").toString().isBlank()
            ? 30 : Integer.parseInt(b.get("noticePeriodDays").toString());
        LocalDate lwd=b.get("lastWorkingDay")==null||b.get("lastWorkingDay").toString().isBlank()
            ? on.plusDays(notice) : LocalDate.parse(b.get("lastWorkingDay").toString());
        if(lwd.isBefore(on)) throw new IllegalArgumentException("The last working day is before the resignation date");

        Resignation r=repo.save(Resignation.builder().employee(target).resignationDate(on)
            .lastWorkingDay(lwd).noticePeriodDays(notice)
            .reason(b.get("reason")==null?null:b.get("reason").toString())
            .status(Resignation.Status.Pending).build());
        audit.log("RESIGNATION_SUBMITTED","Resignation",r.getId(),null,target.getFullName()+" · LWD "+lwd);
        return ResponseEntity.ok(ApiResponse.ok("Resignation submitted",r));
    }

    @PutMapping("/{id}/approve") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Resignation>> approve(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> b){
        return repo.findById(id).map(r->{
            r.setStatus(Resignation.Status.Approved);
            if(b!=null&&b.get("lastWorkingDay")!=null&&!b.get("lastWorkingDay").toString().isBlank())
                r.setLastWorkingDay(LocalDate.parse(b.get("lastWorkingDay").toString()));
            if(b!=null&&b.get("comment")!=null) r.setHrComment(b.get("comment").toString());
            r.setDecidedBy(audit.currentUser());
            Resignation saved=repo.save(r);
            Employee e=saved.getEmployee();
            if(e!=null){
                e.setStatus(Employee.Status.Resigned);
                e.setLastWorkingDay(saved.getLastWorkingDay());
                empRepo.save(e);
            }
            audit.log("RESIGNATION_APPROVED","Resignation",id,null,
                (e==null?"—":e.getFullName())+" · LWD "+saved.getLastWorkingDay());
            return ResponseEntity.ok(ApiResponse.ok("Resignation approved",saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Resignation>> reject(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> b){
        return repo.findById(id).map(r->{
            boolean wasApproved=r.getStatus()==Resignation.Status.Approved;
            r.setStatus(Resignation.Status.Rejected);
            if(b!=null&&b.get("comment")!=null) r.setHrComment(b.get("comment").toString());
            r.setDecidedBy(audit.currentUser());
            Resignation saved=repo.save(r);
            // Undoing an approval must put the person back on the active roll, or they
            // stay Resigned and quietly drop out of payroll.
            Employee e=saved.getEmployee();
            if(wasApproved&&e!=null&&e.getStatus()==Employee.Status.Resigned){
                e.setStatus(Employee.Status.Active);
                e.setLastWorkingDay(null);
                empRepo.save(e);
            }
            audit.log("RESIGNATION_REJECTED","Resignation",id,null,e==null?"—":e.getFullName());
            return ResponseEntity.ok(ApiResponse.ok("Resignation rejected",saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        if(!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        audit.log("RESIGNATION_DELETED","Resignation",id,null,null);
        return ResponseEntity.ok(ApiResponse.ok("Deleted",null));
    }
}
// ── EMPLOYEE DOCUMENTS ───────────────────────────────────────────────
// Held in the database as base64 rather than in a folder, so one backup file carries
// them and the browser build behaves exactly like the desktop one.
@RestController @RequestMapping("/documents") @RequiredArgsConstructor @Slf4j
class EmployeeDocumentController {
    private final EmployeeDocumentRepository repo;
    private final EmployeeRepository empRepo;
    private final AuditService audit;
    private static final long MAX_BYTES = 8L*1024*1024;
    private static final Set<String> ALLOWED = Set.of("application/pdf","image/jpeg","image/jpg","image/png");

    private boolean mayTouch(Long employeeId,Authentication auth){
        if(ResignationController.isHr(auth)) return true;
        Employee e=empRepo.findById(employeeId).orElse(null);
        return e!=null&&EmployeeController.isSelf(e,auth==null?null:auth.getName());
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<EmployeeDocument>>> list(@PathVariable Long employeeId,Authentication auth){
        if(!mayTouch(employeeId,auth))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not your documents"));
        return ResponseEntity.ok(ApiResponse.ok(repo.findByEmployeeIdOrderByUploadedAtDesc(employeeId)));
    }

    // The bytes come back only on an explicit download, never in the listing.
    @GetMapping("/{id}/download")
    public ResponseEntity<ApiResponse<Map<String,Object>>> download(@PathVariable Long id,Authentication auth){
        EmployeeDocument d=repo.findById(id).orElse(null);
        if(d==null) return ResponseEntity.notFound().build();
        if(d.getEmployee()==null||!mayTouch(d.getEmployee().getId(),auth))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Not your document"));
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("fileName",d.getFileName()); out.put("contentType",d.getContentType());
        out.put("sizeBytes",d.getSizeBytes()); out.put("data",d.getData());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<EmployeeDocument>> upload(@RequestBody Map<String,Object> b){
        if(b.get("employeeId")==null) throw new IllegalArgumentException("Select an employee");
        Employee emp=empRepo.findById(Long.valueOf(b.get("employeeId").toString()))
            .orElseThrow(()->new IllegalArgumentException("Employee not found"));
        String data=b.get("data")==null?null:b.get("data").toString();
        if(data==null||data.isBlank()) throw new IllegalArgumentException("No file content received");
        // Strip a data: URI prefix if the browser sent one, so what is stored is always
        // plain base64 and the download side never has to guess.
        int comma=data.indexOf(",");
        if(data.startsWith("data:")&&comma>0) data=data.substring(comma+1);
        long bytes=(long)(data.length()*0.75);
        if(bytes>MAX_BYTES) throw new IllegalArgumentException("That file is larger than 8 MB");
        String type=b.get("contentType")==null?"application/octet-stream":b.get("contentType").toString();
        if(!ALLOWED.contains(type.toLowerCase()))
            throw new IllegalArgumentException("Only PDF, JPG and PNG files can be attached");
        EmployeeDocument d=repo.save(EmployeeDocument.builder().employee(emp)
            .docType(b.get("docType")==null?"Other":b.get("docType").toString())
            .fileName(b.get("fileName")==null?"document":b.get("fileName").toString())
            .contentType(type).sizeBytes(bytes).data(data).uploadedBy(audit.currentUser()).build());
        audit.log("DOCUMENT_UPLOADED","EmployeeDocument",d.getId(),null,emp.getFullName()+" · "+d.getFileName());
        return ResponseEntity.ok(ApiResponse.ok("Document attached",d));
    }

    @DeleteMapping("/{id}") @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id){
        EmployeeDocument d=repo.findById(id).orElse(null);
        if(d==null) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        audit.log("DOCUMENT_DELETED","EmployeeDocument",id,d.getFileName(),null);
        return ResponseEntity.ok(ApiResponse.ok("Document removed",null));
    }
}

// ── NOTIFICATIONS ────────────────────────────────────────────────────
// Everything the bell counts, computed on demand. No separate table: these are all
// questions about live data, and a stored copy would only go stale.
@RestController @RequestMapping("/notifications") @RequiredArgsConstructor
class NotificationController {
    private final LeaveRequestRepository leaveRepo;
    private final ResignationRepository resignRepo;
    private final EmployeeRepository empRepo;
    private final PayrollRepository payRepo;

    @GetMapping public ResponseEntity<ApiResponse<Map<String,Object>>> all(Authentication auth){
        boolean approver=LeaveController.canApprove(auth);
        boolean hr=ResignationController.isHr(auth);
        List<Map<String,Object>> items=new ArrayList<>();

        if(approver){
            int pending=leaveRepo.findByStatus(LeaveRequest.Status.Pending).size();
            if(pending>0) items.add(item("leave","Leave awaiting approval",
                pending+" request"+(pending==1?"":"s")+" need a decision","leave",pending,"warn"));
        }
        if(hr){
            int res=resignRepo.findByStatus(Resignation.Status.Pending).size();
            if(res>0) items.add(item("resignation","Resignations to review",res+" pending","resignations",res,"warn"));
            String month=YearMonth.now().toString();
            long slips=payRepo.findByPayMonth(month).size();
            long active=empRepo.countActive();
            if(active>0&&slips<active) items.add(item("payroll","Payroll not finished for "+month,
                slips+" of "+active+" payslips generated","payroll",(int)(active-slips),"info"));
        }

        LocalDate today=LocalDate.now();
        List<Employee> all=empRepo.findAll();
        for(Employee e:all){
            if(e.getDateOfBirth()==null) continue;
            int away=daysAway(e.getDateOfBirth(),today);
            if(away>=0) items.add(item("birthday",e.getFullName()+"'s birthday",
                away==0?"Today":(away==1?"Tomorrow":"in "+away+" days"),"employees",1,"info"));
        }
        for(Employee e:all){
            if(e.getJoinDate()==null) continue;
            int away=daysAway(e.getJoinDate(),today);
            if(away>=0&&today.plusDays(away).getYear()>e.getJoinDate().getYear())
                items.add(item("anniversary",e.getFullName()+" · work anniversary",
                    (today.plusDays(away).getYear()-e.getJoinDate().getYear())+" years"+(away==0?" — today":""),
                    "employees",1,"info"));
        }

        Map<String,Object> out=new LinkedHashMap<>();
        out.put("count",items.size());
        out.put("items",items);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    // Days until the next occurrence of this month-and-day within a week, or -1.
    private static int daysAway(LocalDate anniversary,LocalDate today){
        for(int i=0;i<7;i++){
            LocalDate d=today.plusDays(i);
            int m=anniversary.getMonthValue(), day=anniversary.getDayOfMonth();
            if(m==2&&day==29&&!d.isLeapYear()&&d.getMonthValue()==2) day=28;
            if(d.getMonthValue()==m&&d.getDayOfMonth()==day) return i;
        }
        return -1;
    }
    private static Map<String,Object> item(String type,String title,String detail,String page,int count,String severity){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("type",type); m.put("title",title); m.put("detail",detail);
        m.put("page",page); m.put("count",count); m.put("severity",severity);
        return m;
    }
}

// ── EMPLOYEE SELF-SERVICE ────────────────────────────────────────────
// Everything here is scoped to the signed-in person by the server, never by a request
// parameter, so there is nothing for a caller to tamper with.
@RestController @RequestMapping("/me") @RequiredArgsConstructor
class SelfServiceController {
    private final EmployeeRepository empRepo;
    private final UserRepository userRepo;
    private final PayrollRepository payRepo;
    private final AttendanceRepository attRepo;
    private final LeaveRequestRepository leaveRepo;
    private final EmployeeDocumentRepository docRepo;
    private final ResignationRepository resignRepo;
    private final PerformanceReviewRepository perfRepo;
    private final LeaveBalanceService balances;

    // The employee record behind this login: matched through the account link first,
    // then by email for records that were never linked.
    private Employee me(Authentication auth){
        if(auth==null||auth.getName()==null) return null;
        User u=userRepo.findByEmail(auth.getName()).orElse(null);
        if(u!=null){
            Employee linked=empRepo.findAll().stream()
                .filter(e->e.getUser()!=null&&e.getUser().getId().equals(u.getId()))
                .findFirst().orElse(null);
            if(linked!=null) return linked;
        }
        return empRepo.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping("/profile") public ResponseEntity<ApiResponse<Map<String,Object>>> profile(Authentication auth){
        Employee e=me(auth);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("linked",e!=null);
        out.put("employee",e);
        if(e!=null){
            out.put("balances",balances.forEmployee(e.getId()));
            out.put("documents",docRepo.findByEmployeeIdOrderByUploadedAtDesc(e.getId()));
            out.put("resignations",resignRepo.findByEmployeeIdOrderByCreatedAtDesc(e.getId()));
            out.put("reviews",perfRepo.findAll().stream()
                .filter(p->p.getEmployee()!=null&&p.getEmployee().getId().equals(e.getId())).toList());
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/payslips") public ResponseEntity<ApiResponse<List<Payroll>>> payslips(Authentication auth){
        Employee e=me(auth);
        if(e==null) return ResponseEntity.ok(ApiResponse.ok(List.of()));
        return ResponseEntity.ok(ApiResponse.ok(payRepo.findAll().stream()
            .filter(p->p.getEmployee()!=null&&p.getEmployee().getId().equals(e.getId()))
            .sorted(Comparator.comparing(Payroll::getPayMonth,Comparator.nullsLast(Comparator.reverseOrder())))
            .toList()));
    }

    @GetMapping("/attendance") public ResponseEntity<ApiResponse<Map<String,Object>>> attendance(
            Authentication auth,@RequestParam(required=false) String from,@RequestParam(required=false) String to){
        Employee e=me(auth);
        Map<String,Object> out=new LinkedHashMap<>();
        if(e==null){ out.put("records",List.of()); out.put("marked",0); return ResponseEntity.ok(ApiResponse.ok(out)); }
        LocalDate f=(from==null||from.isBlank())?LocalDate.now().withDayOfMonth(1):LocalDate.parse(from);
        LocalDate t=(to==null||to.isBlank())?LocalDate.now():LocalDate.parse(to);
        if(f.isAfter(t)){ LocalDate tmp=f; f=t; t=tmp; }
        List<Attendance> recs=attRepo.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(e.getId(),f,t);
        long present=recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Present
                                           ||a.getStatus()==Attendance.AttStatus.Work_From_Home).count();
        out.put("from",f.toString()); out.put("to",t.toString());
        out.put("records",recs);
        out.put("present",present);
        out.put("absent",recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Absent).count());
        out.put("leave",recs.stream().filter(a->a.getStatus()==Attendance.AttStatus.Leave).count());
        out.put("marked",recs.size());
        out.put("presentRate",recs.isEmpty()?0.0:Math.round(present*1000.0/recs.size())/10.0);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/leave") public ResponseEntity<ApiResponse<Map<String,Object>>> leave(Authentication auth){
        Employee e=me(auth);
        Map<String,Object> out=new LinkedHashMap<>();
        if(e==null){ out.put("requests",List.of()); out.put("balances",List.of()); return ResponseEntity.ok(ApiResponse.ok(out)); }
        out.put("employeeId",e.getId());
        out.put("requests",leaveRepo.findByEmployeeId(e.getId()));
        out.put("balances",balances.forEmployee(e.getId()));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }
}

// ── BACKUP & RESTORE ─────────────────────────────────────────────────
// Export is a single JSON document holding every business table. Restore rebuilds
// them from it, matching people by email rather than by row id so a backup taken on
// one installation can be loaded into another. User accounts are deliberately left
// alone: restoring them would lock the operator out of their own system.
@RestController @RequestMapping("/backup") @RequiredArgsConstructor @Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
class BackupController {
    private final EmployeeRepository empRepo;
    private final DepartmentRepository deptRepo;
    private final LeaveRequestRepository leaveRepo;
    private final LeaveBalanceRepository balRepo;
    private final PayrollRepository payRepo;
    private final AttendanceRepository attRepo;
    private final PerformanceReviewRepository perfRepo;
    private final HolidayRepository holRepo;
    private final CandidateRepository candRepo;
    private final RosterEmployeeRepository rosterRepo;
    private final SalaryRevisionRepository revRepo;
    private final ResignationRepository resignRepo;
    private final EmployeeDocumentRepository docRepo;
    private final CompanySettingsRepository settingsRepo;
    private final SettingsService settings;
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @GetMapping("/export")
    public ResponseEntity<ApiResponse<Map<String,Object>>> export(){
        Map<String,Object> d=new LinkedHashMap<>();
        d.put("format","AXVON-HRMS-BACKUP");
        d.put("version","2.0.0");
        d.put("exportedAt",LocalDateTime.now().toString());
        d.put("settings",settings.get());
        d.put("departments",deptRepo.findAll());
        d.put("employees",empRepo.findAll());
        d.put("roster",rosterRepo.findAll());
        d.put("attendance",attRepo.findAll());
        d.put("leaveRequests",leaveRepo.findAll());
        d.put("leaveBalances",balRepo.findAll());
        d.put("payroll",payRepo.findAll());
        d.put("performance",perfRepo.findAll());
        d.put("holidays",holRepo.findAll());
        d.put("candidates",candRepo.findAll());
        d.put("salaryRevisions",revRepo.findAll());
        d.put("resignations",resignRepo.findAll());
        // Documents carry their bytes here, which the entity hides from ordinary listings.
        d.put("documents",docRepo.findAll().stream().map(x->{
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("employeeEmail",x.getEmployee()==null?null:x.getEmployee().getEmail());
            m.put("docType",x.getDocType()); m.put("fileName",x.getFileName());
            m.put("contentType",x.getContentType()); m.put("sizeBytes",x.getSizeBytes());
            m.put("uploadedBy",x.getUploadedBy()); m.put("data",x.getData());
            return m;
        }).toList());

        Map<String,Object> counts=new LinkedHashMap<>();
        counts.put("employees",empRepo.count()); counts.put("departments",deptRepo.count());
        counts.put("attendance",attRepo.count()); counts.put("leaveRequests",leaveRepo.count());
        counts.put("payroll",payRepo.count());    counts.put("documents",docRepo.count());
        d.put("counts",counts);
        audit.log("BACKUP_EXPORTED","System",null,null,counts.toString());
        return ResponseEntity.ok(ApiResponse.ok("Backup ready",d));
    }

    @PostMapping("/restore") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> restore(
            @RequestBody Map<String,Object> body,
            @RequestParam(defaultValue="") String confirmPhrase){
        if(!"RESTORE_AXVON_BACKUP".equals(confirmPhrase))
            return ResponseEntity.badRequest().body(ApiResponse.error("Pass ?confirmPhrase=RESTORE_AXVON_BACKUP"));
        if(!"AXVON-HRMS-BACKUP".equals(String.valueOf(body.get("format"))))
            return ResponseEntity.badRequest().body(ApiResponse.error("That file is not an AXVON backup"));

        // Clear children before parents, exactly as the full reset does.
        //
        // deleteAllInBatch, not deleteAll: inside one transaction JPA queues removals and
        // issues them at flush time, which is AFTER the inserts below. The old rows were
        // therefore still present when the restored ones were written, and a restore died
        // on the unique department name. deleteAllInBatch issues the DELETE immediately,
        // in this order, so the tables really are empty before anything is re-inserted.
        payRepo.deleteAllInBatch(); attRepo.deleteAllInBatch(); perfRepo.deleteAllInBatch();
        leaveRepo.deleteAllInBatch(); balRepo.deleteAllInBatch(); revRepo.deleteAllInBatch();
        docRepo.deleteAllInBatch(); resignRepo.deleteAllInBatch(); candRepo.deleteAllInBatch();
        holRepo.deleteAllInBatch(); empRepo.deleteAllInBatch(); rosterRepo.deleteAllInBatch();
        deptRepo.deleteAllInBatch();

        Map<String,Object> restored=new LinkedHashMap<>();

        Map<String,Department> deptByName=new HashMap<>();
        for(Department d:list(body,"departments",Department.class)){
            d.setId(null);
            Department saved=deptRepo.save(d);
            deptByName.put(key(saved.getName()),saved);
        }
        restored.put("departments",deptByName.size());

        Map<String,Employee> empByEmail=new HashMap<>();
        for(Employee e:list(body,"employees",Employee.class)){
            String deptName=e.getDepartment()==null?null:e.getDepartment().getName();
            e.setId(null);
            // Logins are not part of a backup, so every restored record starts unlinked;
            // Users & Access is where they are re-attached.
            e.setUser(null);
            e.setDepartment(deptName==null?null:deptByName.get(key(deptName)));
            Employee saved=empRepo.save(e);
            if(saved.getEmail()!=null) empByEmail.put(key(saved.getEmail()),saved);
        }
        restored.put("employees",empByEmail.size());

        Map<String,RosterEmployee> rosterByName=new HashMap<>();
        for(RosterEmployee r:list(body,"roster",RosterEmployee.class)){
            r.setId(null);
            RosterEmployee saved=rosterRepo.save(r);
            rosterByName.put(key(saved.getName()),saved);
        }
        restored.put("roster",rosterByName.size());

        int att=0;
        for(Attendance a:list(body,"attendance",Attendance.class)){
            Employee e=a.getEmployee()==null?null:empByEmail.get(key(a.getEmployee().getEmail()));
            RosterEmployee r=a.getRosterEmployee()==null?null:rosterByName.get(key(a.getRosterEmployee().getName()));
            // A row whose person did not survive the restore has nothing to hang off,
            // and the entity requires exactly one side.
            if(e==null&&r==null) continue;
            a.setId(null); a.setEmployee(e); a.setRosterEmployee(e!=null?null:r);
            attRepo.save(a); att++;
        }
        restored.put("attendance",att);

        int lv=0;
        for(LeaveRequest l:list(body,"leaveRequests",LeaveRequest.class)){
            Employee e=l.getEmployee()==null?null:empByEmail.get(key(l.getEmployee().getEmail()));
            if(e==null) continue;
            l.setId(null); l.setEmployee(e); leaveRepo.save(l); lv++;
        }
        restored.put("leaveRequests",lv);

        int bal=0;
        for(LeaveBalance b:list(body,"leaveBalances",LeaveBalance.class)){
            Employee e=b.getEmployee()==null?null:empByEmail.get(key(b.getEmployee().getEmail()));
            if(e==null) continue;
            b.setId(null); b.setEmployee(e); balRepo.save(b); bal++;
        }
        restored.put("leaveBalances",bal);

        int pay=0;
        for(Payroll p:list(body,"payroll",Payroll.class)){
            Employee e=p.getEmployee()==null?null:empByEmail.get(key(p.getEmployee().getEmail()));
            if(e==null) continue;
            p.setId(null); p.setEmployee(e); payRepo.save(p); pay++;
        }
        restored.put("payroll",pay);

        int perf=0;
        for(PerformanceReview p:list(body,"performance",PerformanceReview.class)){
            Employee e=p.getEmployee()==null?null:empByEmail.get(key(p.getEmployee().getEmail()));
            if(e==null) continue;
            p.setId(null); p.setEmployee(e); perfRepo.save(p); perf++;
        }
        restored.put("performance",perf);

        int rev=0;
        for(SalaryRevision r:list(body,"salaryRevisions",SalaryRevision.class)){
            Employee e=r.getEmployee()==null?null:empByEmail.get(key(r.getEmployee().getEmail()));
            if(e==null) continue;
            r.setId(null); r.setEmployee(e); revRepo.save(r); rev++;
        }
        restored.put("salaryRevisions",rev);

        int res=0;
        for(Resignation r:list(body,"resignations",Resignation.class)){
            Employee e=r.getEmployee()==null?null:empByEmail.get(key(r.getEmployee().getEmail()));
            if(e==null) continue;
            r.setId(null); r.setEmployee(e); resignRepo.save(r); res++;
        }
        restored.put("resignations",res);

        int hol=0;
        for(Holiday h:list(body,"holidays",Holiday.class)){ h.setId(null); holRepo.save(h); hol++; }
        restored.put("holidays",hol);

        int cand=0;
        for(Candidate c:list(body,"candidates",Candidate.class)){ c.setId(null); candRepo.save(c); cand++; }
        restored.put("candidates",cand);

        int docs=0;
        Object rawDocs=body.get("documents");
        if(rawDocs instanceof List<?> dl){
            for(Object o:dl){
                if(!(o instanceof Map<?,?> m)) continue;
                Employee e=empByEmail.get(key(String.valueOf(m.get("employeeEmail"))));
                if(e==null||m.get("data")==null) continue;
                docRepo.save(EmployeeDocument.builder().employee(e)
                    .docType(text(m.get("docType"))).fileName(text(m.get("fileName")))
                    .contentType(text(m.get("contentType")))
                    .sizeBytes(m.get("sizeBytes")==null?null:Long.valueOf(m.get("sizeBytes").toString()))
                    .uploadedBy(text(m.get("uploadedBy"))).data(String.valueOf(m.get("data")))
                    .build());
                docs++;
            }
        }
        restored.put("documents",docs);

        if(body.get("settings")!=null){
            CompanySettings s=mapper.convertValue(body.get("settings"),CompanySettings.class);
            s.setId(SettingsService.ID);
            settingsRepo.save(s);
            restored.put("settings",1);
        }

        log.warn("ADMIN: backup restored — {}",restored);
        audit.log("BACKUP_RESTORED","System",null,null,restored.toString());
        return ResponseEntity.ok(ApiResponse.ok("Backup restored",restored));
    }

    private <T> List<T> list(Map<String,Object> body,String field,Class<T> type){
        Object raw=body.get(field);
        if(!(raw instanceof List<?> l)) return List.of();
        List<T> out=new ArrayList<>();
        for(Object o:l){
            // One malformed row must not abandon the whole restore.
            try{ out.add(mapper.convertValue(o,type)); }
            catch(Exception e){ log.warn("Backup: skipped a {} row — {}",field,e.getMessage()); }
        }
        return out;
    }
    private static String key(String s){ return s==null?"":s.trim().toLowerCase(); }
    private static String text(Object o){ return o==null?null:o.toString(); }
}
