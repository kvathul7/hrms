package com.axvon.hrms.config;
import com.axvon.hrms.entity.*;
import com.axvon.hrms.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
@Component @RequiredArgsConstructor @Slf4j
public class DataInitializer {
    private final UserRepository userRepo;
    private final DepartmentRepository deptRepo;
    private final EmployeeRepository empRepo;
    private final HolidayRepository holRepo;
    private final PasswordEncoder encoder;
    // Demo data is no longer forced on every installation — a company that installs
    // AXVON creates its own admin through /api/setup/init and starts clean. This
    // only reports what the database already holds.
    @PostConstruct
    public void report() {
        if(userRepo.count()>0) log.info("AXVON: existing database with {} account(s)",userRepo.count());
        else log.info("AXVON: empty database — waiting for first-run setup");
    }

    // Called by /api/setup/init when the operator ticks "load sample data", and by
    // nothing else. Seeds the four demo logins plus a fictional company to explore.
    public void seedDemoData() {
        if(userRepo.count()>0){log.info("AXVON: accounts already exist, skipping demo seed");return;}
        log.info("AXVON: seeding demo data on request...");
        // Departments
        Department eng=deptRepo.save(Department.builder().name("Engineering").headOfDept("Priya Sharma").description("Software development").build());
        Department hr=deptRepo.save(Department.builder().name("HR").headOfDept("Sunita Rao").description("Human resources").build());
        Department fin=deptRepo.save(Department.builder().name("Finance").headOfDept("Kiran Patel").description("Financial operations").build());
        Department mkt=deptRepo.save(Department.builder().name("Marketing").description("Brand and marketing").build());
        Department mgmt=deptRepo.save(Department.builder().name("Management").headOfDept("Rahul Gupta").description("Executive management").build());
        Department des=deptRepo.save(Department.builder().name("Design").description("UI/UX design").build());
        deptRepo.save(Department.builder().name("Operations").description("Business operations").build());
        deptRepo.save(Department.builder().name("Legal").description("Legal and compliance").build());
        // Users
        User admin=userRepo.save(User.builder().email("admin@axvon.io").password(encoder.encode("admin123")).role(User.Role.SUPER_ADMIN).isActive(true).build());
        User hrUser=userRepo.save(User.builder().email("hr@axvon.io").password(encoder.encode("hr123")).role(User.Role.HR_ADMIN).isActive(true).build());
        User mgr=userRepo.save(User.builder().email("manager@axvon.io").password(encoder.encode("mgr123")).role(User.Role.MANAGER).isActive(true).build());
        User emp=userRepo.save(User.builder().email("emp@axvon.io").password(encoder.encode("emp123")).role(User.Role.EMPLOYEE).isActive(true).build());
        // Employees
        empRepo.save(Employee.builder().empId("AXV001").user(emp).firstName("Arjun").lastName("Mehta").email("emp@axvon.io").phone("98765 43210").department(eng).designation("Senior Developer").joinDate(LocalDate.parse("2022-03-15")).status(Employee.Status.Active).location("Bengaluru").basicSalary(new BigDecimal("57000")).manager("Priya Sharma").build());
        empRepo.save(Employee.builder().empId("AXV002").user(mgr).firstName("Priya").lastName("Sharma").email("priya@axvon.io").phone("98765 43211").department(eng).designation("Eng Manager").joinDate(LocalDate.parse("2020-07-01")).status(Employee.Status.Active).location("Bengaluru").basicSalary(new BigDecimal("78000")).manager("Rahul Gupta").build());
        empRepo.save(Employee.builder().empId("AXV003").user(hrUser).firstName("Sunita").lastName("Rao").email("hr@axvon.io").phone("98765 43212").department(hr).designation("HR Manager").joinDate(LocalDate.parse("2021-01-10")).status(Employee.Status.Active).location("Mumbai").basicSalary(new BigDecimal("51000")).manager("Rahul Gupta").build());
        empRepo.save(Employee.builder().empId("AXV004").firstName("Kiran").lastName("Patel").email("kiran@axvon.io").phone("98765 43213").department(fin).designation("Finance Lead").joinDate(LocalDate.parse("2021-06-20")).status(Employee.Status.Active).location("Pune").basicSalary(new BigDecimal("66000")).manager("Rahul Gupta").build());
        empRepo.save(Employee.builder().empId("AXV005").firstName("Deepa").lastName("Nair").email("deepa@axvon.io").phone("98765 43214").department(mkt).designation("Mktg Executive").joinDate(LocalDate.parse("2023-02-14")).status(Employee.Status.Active).location("Bengaluru").basicSalary(new BigDecimal("39000")).manager("Rahul Gupta").build());
        empRepo.save(Employee.builder().empId("AXV006").firstName("Amit").lastName("Singh").email("amit@axvon.io").phone("98765 43215").department(eng).designation("Junior Developer").joinDate(LocalDate.parse("2023-08-01")).status(Employee.Status.Active).location("Bengaluru").basicSalary(new BigDecimal("33000")).manager("Priya Sharma").build());
        empRepo.save(Employee.builder().empId("AXV007").user(admin).firstName("Rahul").lastName("Gupta").email("rahul@axvon.io").phone("98765 43216").department(mgmt).designation("CEO").joinDate(LocalDate.parse("2018-01-01")).status(Employee.Status.Active).location("Mumbai").basicSalary(new BigDecimal("150000")).manager("—").build());
        empRepo.save(Employee.builder().empId("AXV008").firstName("Meera").lastName("Joshi").email("meera@axvon.io").phone("98765 43217").department(des).designation("UI/UX Designer").joinDate(LocalDate.parse("2022-11-05")).status(Employee.Status.On_Leave).location("Bengaluru").basicSalary(new BigDecimal("43000")).manager("Priya Sharma").build());
        // Holidays
        holRepo.save(Holiday.builder().name("Republic Day").holidayDate(LocalDate.parse("2025-01-26")).type(Holiday.HolType.National).isActive(true).build());
        holRepo.save(Holiday.builder().name("Holi").holidayDate(LocalDate.parse("2025-03-14")).type(Holiday.HolType.Festival).isActive(true).build());
        holRepo.save(Holiday.builder().name("Independence Day").holidayDate(LocalDate.parse("2025-08-15")).type(Holiday.HolType.National).isActive(true).build());
        holRepo.save(Holiday.builder().name("Gandhi Jayanti").holidayDate(LocalDate.parse("2025-10-02")).type(Holiday.HolType.National).isActive(true).build());
        holRepo.save(Holiday.builder().name("Diwali").holidayDate(LocalDate.parse("2025-10-20")).type(Holiday.HolType.Festival).isActive(true).build());
        holRepo.save(Holiday.builder().name("Christmas").holidayDate(LocalDate.parse("2025-12-25")).type(Holiday.HolType.National).isActive(true).build());
        log.info("AXVON: Seeded 8 employees, 8 departments, 6 holidays. Login: admin@axvon.io / admin123");
    }
}
