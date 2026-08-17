# AXVON HRMS v2.0 — Complete Desktop Software
### Created by Athul KV | © 2025 AXVON

---

## HOW TO BUILD THE .exe INSTALLER

Double-click `BUILD_AXVON.bat` — that's it.

The script automatically installs Java, Node.js, Maven (build machine only),
builds backend + frontend, bundles a Java runtime into the app,
and produces: `electron/dist/AXVON HRMS Setup 2.0.0.exe`

Share that .exe with anyone. They double-click, install, and it works.

---

## WHAT'S INSIDE

```
axvon-final-build/
├── BUILD_AXVON.bat          ← Double-click to build the .exe
├── LICENSE.txt
├── README.md
│
├── backend/                 ← Spring Boot 3 REST API
│   ├── pom.xml              ← Maven config (H2 embedded database)
│   └── src/main/java/...    ← All Java code
│       ├── entity/          ← Database models
│       ├── repository/      ← Database queries
│       ├── controller/      ← REST API endpoints
│       ├── config/          ← Security + Data seeding
│       └── security/        ← JWT authentication
│
├── frontend/                ← React 18 + Vite
│   ├── package.json
│   ├── vite.config.js       ← Proxies to Spring Boot
│   └── src/
│       ├── App.jsx          ← All 12 modules
│       ├── main.jsx
│       ├── index.css
│       └── services/api.js  ← All API calls to backend
│
└── electron/                ← Desktop app wrapper
    ├── main.js              ← Starts backend, creates window
    ├── preload.js           ← Secure system bridge
    ├── splash.html          ← Loading screen
    ├── package.json         ← electron-builder config
    └── resources/
        ├── icon.ico         ← Windows icon
        ├── icon.png         ← App icon
        └── tray-icon.png    ← System tray icon
```

---

## WHAT HAPPENS AFTER INSTALL

1. User double-clicks desktop icon
2. Splash screen appears while Spring Boot starts
3. H2 database loads from `C:\Users\<name>\.axvon\data\`
4. Demo data auto-seeded on first launch
5. Login screen appears

## LOGIN CREDENTIALS

| Role        | Email              | Password |
|-------------|-------------------|----------|
| Super Admin | admin@axvon.io    | admin123 |
| HR Admin    | hr@axvon.io       | hr123    |
| Manager     | manager@axvon.io  | mgr123   |
| Employee    | emp@axvon.io      | emp123   |

## DATA STORAGE

Saves permanently to:
- Windows: `C:\Users\YourName\.axvon\data\axvondb.mv.db`
- Mac/Linux: `~/.axvon/data/axvondb.mv.db`

## MODULES (all connected to the database)

1. Dashboard      — Live stats from database
2. Employees      — Add, edit, delete, bulk import
3. Attendance     — Daily marking (Present/WFH/Half/Leave/Absent), check-in/out times,
                    search, and "Import Employee List": pull a plain name list straight
                    from Excel or PDF and start marking — no employee ID, email,
                    department or salary needed
4. Leave          — Apply, approve, reject
5. Payroll        — Generate, mark paid
6. Recruitment    — Hiring pipeline
7. Performance    — Create reviews, ratings, goals — saved to DB
8. Departments    — Org structure
9. Holidays       — Calendar
10. Reports       — Save any report to your PC as CSV, Excel (.xlsx), Word (.docx)
                    or PDF. Attendance can be run for a single day, a month, the
                    last 6 months, the last year or a custom range, and includes a
                    per-person summary (present/absent/WFH/half-day/leave, present %,
                    total hours) alongside every daily record.
                    Files are written to: C:\Users\YourName\.axvon\reports\
11. Bulk Import   — Upload CSV file or paste rows from Excel
12. Admin & Delete All — Per module deletion + full reset

## NO JAVA NEEDED ON USER PCs

The build script downloads a Java 17 runtime and bundles it INSIDE the
installer. People who install the .exe need nothing pre-installed —
backend, frontend and database all start automatically.

---
Crafted with love by Athul KV
kvathul27@gmail.com | github.com/kvathul7
