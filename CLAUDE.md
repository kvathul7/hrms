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
| Frontend | React 18 + Vite + Axios |
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
│       │   └── DataInitializer.java       ← Seeds demo data on first launch
│       ├── controller/
│       │   └── AllControllers.java        ← All REST endpoints
│       ├── dto/
│       │   ├── ApiResponse.java
│       │   └── AuthResponse.java      ← login takes a Map, so there is no AuthRequest
│       ├── entity/
│       │   ├── User.java
│       │   ├── Employee.java
│       │   ├── Department.java
│       │   ├── LeaveRequest.java
│       │   ├── Payroll.java
│       │   ├── Holiday.java
│       │   ├── Candidate.java
│       │   ├── Attendance.java
│       │   └── PerformanceReview.java
│       ├── repository/
│       │   ├── UserRepository.java
│       │   ├── EmployeeRepository.java
│       │   ├── DepartmentRepository.java
│       │   ├── LeaveRequestRepository.java
│       │   ├── PayrollRepository.java
│       │   ├── HolidayRepository.java
│       │   ├── CandidateRepository.java
│       │   └── AttendanceRepository.java
│       ├── security/
│       │   ├── JwtUtil.java
│       │   └── JwtAuthFilter.java
│       ├── service/
│       │   └── UserDetailsServiceImpl.java
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

## Login Credentials (auto-seeded on first launch)
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
| 3 | Attendance | GET/POST/PUT /api/attendance |
| 4 | Leave | GET/POST/PUT /api/leave/requests |
| 5 | Payroll | GET/POST /api/payroll, POST /api/payroll/generate |
| 6 | Recruitment | GET/POST/PUT/DELETE /api/candidates |
| 7 | Performance | GET/POST/PUT /api/performance |
| 8 | Departments | GET/POST/PUT/DELETE /api/departments |
| 9 | Holidays | GET/POST/DELETE /api/holidays |
| 10 | Reports | GET /api/reports/summary (SUPER_ADMIN/HR_ADMIN/MANAGER); CSV/PDF export from each module |
| 11 | Bulk Import | POST /api/employees/bulk-import |
| 12 | Admin & Delete | DELETE /api/admin/delete/* |

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
BUILD_AXVON.bat

# Or manually:
cd backend && mvn clean package -DskipTests
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
