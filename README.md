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
4. **First-run setup** asks you to create your own Super Admin account —
   a fresh installation ships with no accounts and no data
5. From then on, that account is the login

## FIRST RUN

There are no default passwords. On an empty database the app opens its setup
screen, you create the owner account, and it locks itself afterwards.

Setup offers a **"load sample data"** tick box. Leave it **off** for real use.
Turn it **on** to explore the app with a fictional company, which also creates
these four demo logins:

| Role        | Email              | Password |
|-------------|-------------------|----------|
| Super Admin | admin@axvon.io    | admin123 |
| HR Admin    | hr@axvon.io       | hr123    |
| Manager     | manager@axvon.io  | mgr123   |
| Employee    | emp@axvon.io      | emp123   |

Those accounts exist **only** if you ticked sample data. Delete them from
**Users & Access** before putting real information in.

## WHO CAN SEE WHAT

| | Super Admin | HR Admin | Manager | Employee |
|---|---|---|---|---|
| Staff directory | ✅ | ✅ | ✅ | ✅ (no salaries) |
| Salaries & payroll | ✅ | ✅ | — | — |
| Attendance & reports | ✅ | ✅ | ✅ | — |
| Performance reviews | ✅ | ✅ | ✅ | — |
| Leave requests | all | all | all | own only |
| Recruitment | ✅ | ✅ | — | — |
| Accounts & deletion | ✅ | view only | — | — |

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
13. HR Assistant  — Ask about your own leave, pay and records in plain English, apply
                    for leave by typing it, and get company-policy answers quoted from
                    the PDFs HR uploaded, with the document, section and page cited.
                    Off until an API key is set — see below.

## SWITCHING ON THE HR ASSISTANT

The assistant needs a language model. There are two options and AXVON ships configured
for the first. Until one is working, every other module behaves exactly as before and the
assistant simply says it is not ready.

### Option A — Ollama (free, runs on your own PC, no account)  ← the default

1. Install Ollama from https://ollama.com
2. Open a terminal and pull a model that supports tool calling:
   ```
   ollama pull qwen2.5:7b
   ```
   This is a ~4.7 GB download, once. The model must support **tool calling** or the
   assistant cannot read your leave balance — it would answer from nothing.
3. Restart AXVON. My Workspace → HR Assistant.

Nothing leaves your computer and there is nothing to pay. Answers are slower than
Option B, and a small local model is less reliable at saying "the policy does not cover
that" instead of guessing — which matters for HR answers.

### Option B — Anthropic (paid, better at refusing to guess)

1. Get a key from https://console.anthropic.com (a developer account — this is **not** the
   same thing as a Claude chat subscription)
2. Set two **user environment variables** (Windows: Search → "Edit environment variables
   for your account" → New):

   | Variable | Value |
   |---|---|
   | `ANTHROPIC_API_KEY` | `sk-ant-...` |
   | `SPRING_AI_MODEL_CHAT` | `anthropic` |

   The second one is what switches AXVON off Ollama. Setting the key alone is not enough.
3. Restart AXVON.

Roughly a third of a cent per question on the default model, and you can cap monthly
spend in the console. The key is read from the environment only — it is never written
into the installer and never committed.

**To answer policy questions**, sign in as the owner or HR and upload your policy PDFs in
HR Assistant → Policy documents. Until you do, the assistant will say policy questions
are not covered rather than invent an answer. Text-based PDFs only — a scan has to go
through OCR first. The index lives in `C:\Users\YourName\.axvon\ai\` and is **not**
part of the backup file, so keep the original PDFs; re-uploading rebuilds it.

What the assistant can see: only the signed-in person's own records. It takes who you are
from your sign-in rather than from what you type, so it cannot read a colleague's leave
balance, salary or payslip, and it cannot approve leave, run payroll or edit anything.

## NO JAVA NEEDED ON USER PCs

The build script downloads a Java 17 runtime and bundles it INSIDE the
installer. People who install the .exe need nothing pre-installed —
backend, frontend and database all start automatically.

---
Crafted with love by Athul KV
kvathul27@gmail.com | github.com/kvathul7
