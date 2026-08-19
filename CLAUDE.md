# AXVON HRMS v2.0 — Claude Code Project File
**Author:** Athul KV | kvathul27@gmail.com | github.com/kvathul7
**© 2025 AXVON. All rights reserved.**

---

## Project Overview
AXVON HRMS is a complete enterprise-grade Human Resource Management System built as a true desktop software. It installs like Chrome or WhatsApp — double-click the .exe, click Install, and everything works immediately.

---

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + Vite + Axios (read-excel-file for .xlsx, pdfjs-dist for PDF text) |
| Backend | Spring Boot 3 + Spring Security + JWT + Lombok |
| Database | H2 Embedded (no MySQL needed) |
| Desktop | Electron 28 + electron-builder |
| Auth | BCrypt + JWT tokens + Role-based access |
| Build | Maven 3.9 + Node.js + electron-builder NSIS |

---

## Project Structure
```
axvon-final-build/
├── BUILD_AXVON.bat                        ← One-click build (run as Admin)
├── LICENSE.txt
├── README.md
├── CLAUDE.md                              ← This file
│
├── backend/                               ← Spring Boot 3
│   ├── pom.xml
│   └── src/main/java/com/axvon/hrms/
│       ├── AxvonHrmsApplication.java
│       ├── config/
│       │   ├── SecurityConfig.java        ← CORS wildcard, JWT, BCrypt
│       │   ├── SchemaUpgrader.java        ← relaxes NOT NULL that ddl-auto won't
│       │   └── DataInitializer.java       ← Seeds demo data on first launch
│       ├── controller/
│       │   └── AllControllers.java        ← All REST endpoints
│       ├── dto/
│       │   ├── ApiResponse.java
│       │   └── AuthResponse.java      ← login takes a Map, so there is no AuthRequest
│       ├── entity/
│       │   ├── User.java
│       │   ├── Employee.java
│       │   ├── RosterEmployee.java     ← name-only person, attendance list only
│       │   ├── Department.java
│       │   ├── LeaveRequest.java
│       │   ├── Payroll.java
│       │   ├── Holiday.java
│       │   ├── Candidate.java
│       │   ├── Attendance.java
│       │   ├── PerformanceReview.java
│       │   ├── CompanySettings.java     ← one row, id=1: identity, payroll rates, quotas
│       │   ├── LeaveBalance.java        ← per employee / type / calendar year
│       │   ├── AuditLog.java            ← who changed what, append-only
│       │   ├── SalaryRevision.java
│       │   ├── Resignation.java
│       │   └── EmployeeDocument.java    ← base64 in the DB, not a folder
│       ├── repository/
│       │   ├── UserRepository.java
│       │   ├── EmployeeRepository.java
│       │   ├── RosterEmployeeRepository.java
│       │   ├── DepartmentRepository.java
│       │   ├── LeaveRequestRepository.java
│       │   ├── PayrollRepository.java
│       │   ├── HolidayRepository.java
│       │   ├── CandidateRepository.java
│       │   ├── AttendanceRepository.java
│       │   ├── CompanySettingsRepository.java
│       │   ├── LeaveBalanceRepository.java
│       │   ├── AuditLogRepository.java   ← paged; the only table that only grows
│       │   ├── SalaryRevisionRepository.java
│       │   ├── ResignationRepository.java
│       │   └── EmployeeDocumentRepository.java
│       ├── security/
│       │   ├── JwtUtil.java
│       │   └── JwtAuthFilter.java
│       ├── service/
│       │   ├── UserDetailsServiceImpl.java
│       │   ├── AuditService.java          ← every auditable action writes through it
│       │   ├── SettingsService.java       ← get-or-create the single settings row
│       │   └── LeaveBalanceService.java   ← quotas, lazy rows, 1 Jan rollover
│       └── exception/
│           └── GlobalExceptionHandler.java
│
├── frontend/                              ← React 18 + Vite
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js                     ← Proxy /api → :17823, builds to ../electron/frontend-dist
│   └── src/
│       ├── App.jsx                        ← All 12 modules
│       ├── main.jsx
│       ├── index.css
│       └── services/
│           └── api.js                     ← All Axios calls, detects Electron vs browser
│
└── electron/
    ├── main.js                            ← Starts backend JAR, creates window, tray
    ├── preload.js
    ├── splash.html
    ├── package.json                       ← electron-builder NSIS config
    └── resources/
        ├── icon.ico
        ├── icon.png
        └── tray-icon.png
```

---

## Ports
| Service | Port |
|---------|------|
| Backend (Spring Boot) | 17823 |
| Frontend (Vite dev) | 17824 |
| H2 Console | disabled in prod |

---

## Database
- **Type:** H2 Embedded File Database
- **Location:** `~/.axvon/data/axvondb`
- **Logs:** `~/.axvon/logs/axvon.log`
- **Mode:** single embedded connection. Do NOT add `AUTO_SERVER=TRUE` — H2 2.x rejects it
  alongside `DB_CLOSE_ON_EXIT=FALSE` and the backend dies at startup. Electron enforces one
  app instance, so concurrent access is not needed.
- **DDL:** `spring.jpa.hibernate.ddl-auto=update` — auto creates tables

---

## Login Credentials (only exist if "load sample data" was ticked at first-run setup)
A fresh install has NO accounts — `/api/setup/init` creates the first one. These four
come from `DataInitializer.seedDemoData()`, which only runs on request.
| Role | Email | Password |
|------|-------|---------|
| Super Admin | admin@axvon.io | admin123 |
| HR Admin | hr@axvon.io | hr123 |
| Manager | manager@axvon.io | mgr123 |
| Employee | emp@axvon.io | emp123 |

---

## 12 Modules
| # | Module | API Endpoints |
|---|--------|--------------|
| 1 | Dashboard | GET /api/dashboard/stats, /api/dashboard/health |
| 2 | Employees | GET/POST/PUT/DELETE /api/employees |
| 3 | Attendance | GET/POST/PUT /api/attendance; GET/POST/PUT/DELETE /api/roster, POST /api/roster/import |
| 4 | Leave | GET/POST/PUT /api/leave/requests |
| 5 | Payroll | GET/POST /api/payroll, POST /api/payroll/generate |
| 6 | Recruitment | GET/POST/PUT/DELETE /api/candidates |
| 7 | Performance | GET/POST/PUT /api/performance |
| 8 | Departments | GET/POST/PUT/DELETE /api/departments |
| 9 | Holidays | GET/POST/DELETE /api/holidays |
| 10 | Reports | GET /api/reports/summary (SUPER_ADMIN/HR_ADMIN/MANAGER); CSV / Excel / Word / PDF export from each module. Attendance also does daily, monthly, 6-month, 1-year and custom periods via GET /api/attendance/range |
| 11 | Bulk Import | POST /api/employees/bulk-import |
| 12 | Admin & Delete | DELETE /api/admin/delete/*; GET /api/backup/export, POST /api/backup/restore |
| 13 | Company Settings | GET /api/settings, PUT /api/settings (SUPER_ADMIN) — identity, payroll rates, leave quotas |
| 14 | Users & Access | GET/POST/PUT/DELETE /api/users, PUT /api/users/{id}/password, PUT /api/users/me/password |
| 15 | Audit Log | GET /api/audit (paged, filterable), GET /api/audit/actions (SUPER_ADMIN) |
| 16 | Resignations | GET/POST /api/resignations, PUT /api/resignations/{id}/approve, /reject |
| 17 | Salary History | GET /api/salary-revisions/employee/{id}, POST /api/salary-revisions |
| 18 | Documents | GET/POST/DELETE /api/documents, GET /api/documents/{id}/download |
| 19 | Self-Service | GET /api/me/profile, /me/payslips, /me/attendance, /me/leave |
| 20 | Notifications | GET /api/notifications |

---

## How to Run Locally
```bash
# Terminal 1 — Backend
cd backend
mvn spring-boot:run

# Terminal 2 — Frontend
cd frontend
npm install
npm run dev

# Open browser
http://localhost:17824
```

---

## How to Build the .exe
```bash
# Run as Administrator on Windows
BUILD_AXVON.bat        # runs the backend tests, then packages

# Or manually:
cd backend && mvn clean package        # tests run; add -DskipTests only to iterate
cd ../frontend && npm install && npm run build
cd ../electron && npm install && npm run build:win

# Output:
electron/dist/AXVON HRMS Setup 2.0.0.exe
```

---

## API Response Format
All endpoints return:
```json
{
  "success": true,
  "message": "Success",
  "data": { }
}
```

---

## Key Rules for Claude Code
1. **Never change ports** — backend must stay on 17823, frontend on 17824
2. **Never switch database** — keep H2 embedded, do not add MySQL dependency
3. **Never move entity files** — keep all in `entity/` package
4. **Always run backend before frontend** — frontend proxies to backend
5. **JVM args go BEFORE -jar** — in electron/main.js the -D flags must be before -jar
6. **api.js detects Electron** — uses `window.location.protocol === 'file:'` to switch baseURL
7. **vite.config.js builds to** `../electron/frontend-dist` — do not change output dir
8. **Lombok is used** — always have `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` on entities
9. **All controllers are in one file** — `AllControllers.java` — keep it that way
10. **DataInitializer seeds data** — only runs when `userRepo.count() == 0`. It uses
    `@PostConstruct`, not `CommandLineRunner`: runners fire after the web server starts
    accepting requests, which let a fast first login read a half-seeded database.
11. **Auth failures must return 401, not 403** — `api.js` clears the session on 401 only.
    403 is reserved for genuine role denials, which must NOT log the user out.
12. **Rebuild order matters** — after backend changes, copy the new jar to
    `electron/backend/axvon-hrms.jar` before packaging, or the installer ships stale code.
13. **`User.password` is `@JsonIgnore`** — `Employee` embeds `User`, and `/api/employees` is
    readable by every role, so without it the BCrypt hash of every account ships to the
    browser. The `UserDetails` getters are ignored for the same reason.
14. **Duplicates return 409, not 500** — `GlobalExceptionHandler` maps
    `DataIntegrityViolationException` to a readable message (duplicate email, employee ID,
    holiday date). Only genuinely unexpected failures may return 500.
15. **Employee IDs auto-generate** — `create` and `bulk-import` fill a blank `empId` with the
    next `AXVnnn` in the series; bulk import rejects blank names/emails, not just nulls.
16. **`RosterEmployee` is not an `Employee`** — the Attendance "Import Employee List" feature
    stores name-only people in `roster_employees`. Do NOT route them through `Employee`:
    `Employee.email` is `NOT NULL UNIQUE` and payroll generation loops over every Active
    employee, so imported names would need fabricated emails and would draw ₹0 payslips.
    Only `name` is required; `code`/`department`/`designation`/`phone` are optional and stay
    null unless the operator maps those columns.
17. **`Attendance` hangs off exactly one side** — either `employee` or `rosterEmployee` is set,
    never both, never neither. `AttendanceController.mark` enforces it; `getPersonName()`
    returns whichever applies. Both FKs are nullable, so anything reading `a.employee`
    directly must null-check it.
18. **`SchemaUpgrader` exists because `ddl-auto=update` only adds** — it never relaxes an
    existing `NOT NULL`. Databases created before the roster still had
    `attendance.employee_id NOT NULL`, which rejected every roster row. Put idempotent
    `ALTER`s there, and keep the `EntityManagerFactory` constructor dependency: it is what
    guarantees Hibernate finished its schema update first.
19. **pdf.js worker must stay inlined** — `pdfjs-dist/build/pdf.worker.min.mjs?worker&inline`
    becomes a blob URL. A separate worker asset is unreliable when the packaged app serves
    the page over `file://`, so do not "optimise" this into an external chunk.
20. **`.xlsx` and `.docx` are written by hand** — `services/exporters.js` builds real OPC
    packages (a ZIP of XML parts) with STORED entries, so no deflate and no spreadsheet
    library is needed. Do NOT "fix" these into CSV-renamed-to-.xls or HTML-renamed-to-.doc;
    Office shows a corruption warning for those. Every export renders from one shape:
    `{title, subtitle, sheets:[{name, cols, rows}]}`. Put real JS numbers in `rows` — that
    is what makes Excel treat a cell as numeric rather than text.
21. **Exports are saved by the main process, not the browser** — under `file://` a normal
    download has nowhere sensible to go, so `save-report` / `save-report-pdf` IPC write into
    `~/.axvon/reports`. PDFs come from `webContents.printToPDF` in a hidden window, not the
    print dialog. `safeReportName()` reduces the renderer-supplied filename to a basename:
    keep it, or a crafted name could write outside that folder. In a plain browser the same
    code falls back to a blob download.
22. **Report dates are built from local parts** — `ymd()` never uses `toISOString()`, which
    shifts to UTC and can drop or double-count a day at the month boundary.

23. **Payroll rates live in `CompanySettings`, not in code** — `PayrollController.generate`
    reads HRA / special allowance / PF / ESI / professional tax / TDS and
    `workingDaysPerMonth` from the single settings row (id=1, created on first read by
    `SettingsService`). Percentages are stored whole: 40 means 40%. Do NOT put a rate
    back into the method — they change with every budget.
24. **Absence costs pay, approved leave does not** — payroll counts the month's
    attendance and charges `basicSalary / workingDaysPerMonth` per absent day, half that
    for a half day. A date covered by an *approved* `LeaveRequest` is exempt even when
    somebody also marked the person Absent. `Payroll.lopDeduction` / `presentDays` /
    `absentDays` / `leaveDays` / `payableDays` record the working.
25. **Leave balance moves only on a status transition** — `Pending → Approved` spends
    days, `Approved → Rejected/deleted` gives them back. Approving twice must not charge
    twice. Types with no quota (Unpaid, Maternity, Paternity) return `null` from
    `LeaveBalanceService.remaining()` and are never blocked.
26. **`/leave/apply` trusts the token, not the body** — only SUPER_ADMIN and HR_ADMIN may
    file leave for another person; everyone else is forced to their own employee record
    and gets 403 if the body names somebody else. The old version took `employeeId`
    straight from the request, which let any account file and then read back leave in a
    colleague's name.
27. **An employee sees their own salary, nobody else's** — `EmployeeController.withoutPay`
    blanks `basicSalary` for anyone below HR *except* on their own record
    (`isSelf`, matched on email). A blanked salary must render as "—", never ₹0.
28. **Audit failures must not roll back the action** — `AuditService` swallows its own
    exceptions and logs to file. An unwritable audit row is not a reason to refuse the
    edit the operator asked for. The audit table also survives a full reset on purpose.
29. **Restore uses `deleteAllInBatch()`, never `deleteAll()`** — inside one transaction
    JPA queues removals and issues them at flush time, i.e. *after* the inserts, so the
    old rows were still present when the restored ones were written and the restore died
    on the unique department name. `deleteAllInBatch` issues each DELETE immediately.
30. **A backup matches people by email, not by row id** — so a file from one installation
    loads into another. User accounts are deliberately NOT restored (that would lock the
    operator out), which means restored employees come back with `user = null` and must
    be re-linked in Users & Access.
31. **Documents live in the database as base64** — not in a folder, so one backup file
    carries them and the browser build behaves like the desktop one. `EmployeeDocument.data`
    is `@JsonIgnore`: listing documents must never ship every byte, only
    `/documents/{id}/download` returns them. PDF/JPG/PNG only, 8 MB cap, enforced server-side.
32. **Electron copies the database on every launch** — `backupDatabase()` in `main.js` runs
    *before* the backend starts, because that is the only moment the H2 file is not locked.
    Keeps the last 10 in `~/.axvon/backups`.
33. **Token refresh renews once, then gives up** — `api.js` schedules a refresh five minutes
    before expiry and, on a 401, tries exactly one `/auth/refresh` before clearing the
    session. Concurrent 401s share one in-flight refresh, or a burst of failures would
    produce a burst of refreshes.

---

## Common Errors and Fixes

### Backend won't start
```bash
# Check Java version
java -version   # Must be 17+

# Check port not in use
netstat -ano | findstr :17823

# Kill process using port
taskkill /PID <pid> /F
```

### Frontend can't connect to backend
```bash
# Make sure backend is running first
# Check api.js baseURL — should be '/api' in dev, 'http://localhost:17823/api' in Electron
```

### Maven build fails
```bash
# Clean and retry
mvn clean package -DskipTests -X

# Check pom.xml — H2, JWT, Spring Boot 3.2.0, Lombok
```

### Electron app opens but login fails
```bash
# Check api.js — must detect file:// protocol and use absolute URL
# Check main.js — -D flags must be BEFORE -jar
# Check backend is actually running on 17823
```

---

## Tests
```bash
cd backend && mvn test            # 45 tests, about 15 seconds
mvn test -Dtest=PayrollGenerationTest
```
`src/test/resources/application.properties` pins an **in-memory** database and a fixed JWT
secret. Both matter: the real datasource is `~/.axvon/data/axvondb`, and `BackupRestoreTest`
wipes every table — it must never be pointed at live HR data. The fixed secret stops
`JwtUtil` rewriting `~/.axvon/jwt.key` and signing every real user out on each test run.

| Suite | Covers |
|-------|--------|
| `PayrollGenerationTest` | rates read from settings, loss of pay, half days, approved-leave exemption, ESI ceiling, idempotent runs |
| `LeaveBalanceTest` | balance moves only on a status transition, quota enforcement, untracked types |
| `AuthorizationTest` | the leave-authorisation rule, salary masking, role denials, 401 vs 403 |
| `BackupRestoreTest` | export/restore round trip, restoring over live data, documents and settings |
| `AuditAndSalaryTest` | salary history from both doors, audit trail, cascade deletes, resignation flow |

The tests are **not** `@Transactional`. A rollback-per-test wrapper would hide the exact
defect class they exist for — the restore bug only appeared because flush ordering
misbehaves when work actually commits. Each test wipes the tables itself instead.

## Environment Requirements
| Tool | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Node.js | 18+ | `node --version` |
| Maven | 3.8+ | `mvn -version` |
| npm | 8+ | `npm --version` |

---

## Author
**Athul KV**
- Email: kvathul27@gmail.com
- GitHub: github.com/kvathul7
- LinkedIn: linkedin.com/in/athul-kv-16a3ab256
- Role: Full-Stack Developer | VTU 2026

*© 2025 AXVON. All rights reserved.*
