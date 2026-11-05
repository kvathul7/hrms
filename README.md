<!--
  This meta tag is used by GitHub to generate link previews.
  It will not be displayed on the page.
-->
<meta name="description" content="AXVON HRMS is an enterprise-grade Human Resource Management System for Windows. Includes AI HR Assistant, Payroll, Attendance, and more. No infrastructure required.">

# AXVON HRMS — Enterprise Human Resource Management System

**Complete desktop software for modern HR operations.** AXVON HRMS is a full-featured Human Resource Management System that installs like Chrome or WhatsApp — double-click the `.exe`, click Install, and everything works immediately.

---

## 🚀 Key Features

| Feature | Description |
|---------|-------------|
| **AI HR Assistant** | Natural language Q&A about leave, payroll, and company policies (with RAG from your PDFs) |
| **Employee Management** | Staff directory, bulk import, CSV/upload support |
| **Attendance Tracking** | Daily marking with check-in/out, Excel/PDF imports for employee lists |
| **Leave Management** | Apply, approve, reject leaves with quota enforcement |
| **Payroll Generation** | Auto-calculate salaries, track present/absent days, LOP deductions |
| **Recruitment Pipeline** | Candidate tracking, hiring stages |
| **Performance Reviews** | Create reviews, ratings, and goals |
| **Reports & Analytics** | CSV, Excel, Word, PDF exports for all modules |
| **Document Management** | Store and retrieve employee documents securely |
| **Role-Based Access** | Super Admin, HR Admin, Manager, Employee roles |

---

## 🎯 Why Choose AXVON HRMS?

- **Zero Infrastructure Setup** — Works on any Windows PC without databases, servers, or cloud accounts
- **All-in-One Installer** — Bundled Java 17 runtime means no prerequisites
- **AI-Powered Assistance** — Chat naturally with your HR data
- **True Desktop Experience** — Native Windows application with system tray integration
- **Full Data Ownership** — Your data stays on your machine, never leaves

---

## 🏗️ Architecture

AXVON HRMS is a desktop application built with a modern technology stack:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Desktop Application                           │
│  ┌──────────────┐    ┌───┐    ┌───────────────┐                  │
│  │  Electron    │◄──►│UI │◄──►│  React 18     │                  │
│  │  (Desktop)   │    │   │    │  + Vite       │                  │
│  └──────────────┘    └───┘    └───────────────┘                  │
│                            │                                  │
│                            ▼                                  │
│                    ┌──────────────┐                           │
│                    │  Electron    │                           │
│                    │  Backend     │                           │
│                    │  (Spring     │                           │
│                    │  Boot 3.5)   │                           │
│                    └──────────────┘                           │
│                            │                                  │
│                            ▼                                  │
│                   ┌────────────────┐                          │
│                   │  Embedded H2   │                          │
│                   │  Database      │                          │
│                   └────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology |
|-------|------------|
| Desktop | Electron 28 + electron-builder (NSIS) |
| Frontend | React 18 + Vite + Axios |
| Backend | Spring Boot 3.5 + Spring Security + JWT |
| Database | H2 Embedded (no MySQL required) |
| AI Engine | Spring AI 1.1.8 (local via Ollama, or Anthropic) |

---

## 📦 Building the .exe Installer

### One-Click Build (Windows - Run as Administrator)

```powershell
BUILD_AXVON.bat
```

This script automatically:
1. Installs Java 17, Node.js, Maven (build machine only)
2. Builds backend + frontend
3. Bundles a Java runtime into the app
4. Produces: `electron/dist/AXVON HRMS Setup 2.0.0.exe`

### Manual Build Steps

```powershell
# Terminal 1 — Build Backend
cd backend
mvn clean package -DskipTests

# Terminal 2 — Build Frontend
cd ../frontend
npm install
npm run build

# Terminal 3 — Package Desktop App
cd ../electron
npm install
npm run build:win
```

---

## 🖥️ Installation & First Run

### After Installation

1. Double-click desktop icon
2. Splash screen appears while Spring Boot starts
3. H2 database loads from your user profile
4. **First-run setup** creates your Super Admin account
5. From then on, that account is your login

### No Default Passwords

On an empty database, the app opens its setup screen. You create the owner account, and it locks itself afterwards.

### Sample Data (Optional)

Setup offers a "load sample data" checkbox. Turn it **on** to explore with a fictional company, which also creates these demo logins:

| Role | Email | Password |
|------|-------|----------|
| Super Admin | admin@axvon.io | admin123 |
| HR Admin | hr@axvon.io | hr123 |
| Manager | manager@axvon.io | mgr123 |
| Employee | emp@axvon.io | emp123 |

> **Warning:** For production, leave sample data unchecked and delete demo users from **Users & Access** before adding real information.

---

## 👥 User Roles & Permissions

| Module | Super Admin | HR Admin | Manager | Employee |
|--------|-------------|----------|---------|----------|
| Staff Directory | ✅ | ✅ | ✅ | ✅ (no salaries) |
| Salaries & Payroll | ✅ | ✅ | — | — |
| Attendance & Reports | ✅ | ✅ | ✅ | — |
| Performance Reviews | ✅ | ✅ | ✅ | — |
| Leave Requests | all | all | all | own only |
| Recruitment | ✅ | ✅ | — | — |
| Accounts & Deletion | ✅ | view only | — | — |

---

## ⚙️ Data Storage Locations

```
Windows: C:\Users\<YourName>\.axvon\
├── data\axvondb.mv.db      ← Database file
├── logs\axvon.log          ← Application logs
├── reports\                ← Exported reports
└── ai\                     ← AI index for policy RAG
```

---

## 🤖 AI HR Assistant

### What It Can Do

Ask questions in natural language about your own records:

- "How many leave days do I have left?"
- "What was my February payroll?"
- "How many days was I absent last month?"
- "Apply for 3 days of vacation next week"

The assistant also answers policy questions when you upload company policy PDFs.

### Option A — Ollama (Free, Local, No Account) ⭐ Default

1. Install Ollama from https://ollama.com
2. Pull a tool-capable model:

```powershell
ollama pull qwen2.5:7b
```

3. Restart AXVON → HR Assistant

### Option B — Anthropic (Paid, Better Accuracy)

1. Get an API key from https://console.anthropic.com
2. Set environment variables:

```
ANTHROPIC_API_KEY = sk-ant-...
SPRING_AI_MODEL_CHAT = anthropic
```

3. Restart AXVON

**Note:** The model must support tool calling, or the assistant cannot read your leave balance.

---

## 📊 Modules

1. **Dashboard** — Live stats from database
2. **Employees** — Add, edit, delete, bulk import staff records
3. **Attendance** — Daily marking (Present/WFH/Half/Leave/Absent), check-in/out, Excel/PDF imports
4. **Leave** — Apply, approve, reject with quota tracking
5. **Payroll** — Generate payslips, mark paid, track absent days
6. **Recruitment** — Manage candidate pipeline
7. **Performance** — Reviews, ratings, goals
8. **Departments** — Org structure management
9. **Holidays** — Calendar configuration
10. **Reports** — Export any view as CSV, Excel (.xlsx), Word (.docx) or PDF
11. **Bulk Import** — Upload CSV or paste from Excel
12. **Admin & Delete** — Per-module deletion and full database reset
13. **HR Assistant** — AI-powered Q&A with your data
14. **Policy Library** — Upload PDFs for AI to cite

---

## 📋 API Endpoints

All endpoints return a consistent JSON format:

```json
{
  "success": true,
  "message": "Success",
  "data": { ... }
}
```

| Module | Endpoints |
|--------|-----------|
| Authentication | POST `/api/auth/login`, POST `/api/auth/refresh` |
| Dashboard | GET `/api/dashboard/stats` |
| Employees | GET/POST/PUT/DELETE `/api/employees` |
| Attendance | GET/POST/PUT `/api/attendance`, `/api/roster` |
| Leave | GET/POST/PUT `/api/leave/requests` |
| Payroll | GET/POST `/api/payroll`, POST `/api/payroll/generate` |
| Recruitment | GET/POST/PUT/DELETE `/api/candidates` |
| Performance | GET/POST/PUT `/api/performance` |
| Reports | GET `/api/reports/summary` |
| Settings | GET/PUT `/api/settings` |
| AI Assistant | POST `/api/chat`, GET `/api/chat/status` |

See CLAUDE.md for complete API documentation.

---

## 🔧 Development

### Running Locally (Development Mode)

```powershell
# Terminal 1 — Backend
cd backend
mvn spring-boot:run

# Terminal 2 — Frontend
cd frontend
npm install
npm run dev

# Open: http://localhost:17824
```

### Backend Port: 17823 | Frontend Dev Port: 17824

---

## 📄 License

AXVON HRMS v2.0 — End User License Agreement

Copyright © 2025 Athul KV. All rights reserved.

---

## 📞 Contact & Links

- **Author:** Athul KV — kvathul27@gmail.com
- **GitHub:** [github.com/kvathul7](https://github.com/kvathul7)
- **Issues:** [Report bugs or request features](https://github.com/kvathul7/axvon-hrms/issues)

---

**Download the latest release:** [Releases](https://github.com/kvathul7/axvon-hrms/releases)