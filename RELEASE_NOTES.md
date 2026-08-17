**AXVON HRMS v2.0.0** — a complete Human Resource Management System that installs
like any desktop app. No Java, no MySQL, no configuration, no internet connection.

Download **`AXVON HRMS Setup 2.0.0.exe`** below (143 MB), double-click, done.

---

### ⚠️ Windows will warn you — this is expected

The installer is not code-signed (a certificate costs several hundred dollars a
year), so Windows Defender SmartScreen shows **"Windows protected your PC"**.

To continue: click **More info** → **Run anyway**.

You do **not** need administrator rights — it installs for your user only.

### What happens on first run

1. A splash screen appears while the embedded server starts (~10 seconds)
2. **Setup asks you to create your own admin account.** There are no default
   passwords, and a fresh install contains no data
3. Tick **"load sample data"** to explore with a fictional company. Leave it
   **off** if you are entering real information

Everything is stored locally at `C:\Users\<you>\.axvon\`. Nothing is uploaded
anywhere — there is no server and no telemetry.

---

### What's in this release

**Twelve modules** — Dashboard, Employees, Attendance, Leave, Payroll,
Recruitment, Performance, Departments, Holidays, Reports, Bulk Import, and Admin.

**Import Employee List** — for companies that keep staff in a spreadsheet rather
than an HR system. Upload an `.xlsx` or a PDF, and start marking attendance
immediately. A single **Employee Name** column is enough: no employee IDs, no
email addresses, no salaries required. Duplicates are detected and reported, and
a scanned PDF tells you OCR is needed instead of silently importing nothing.

**Attendance reports by period** — a single day, a month, the last 6 months, the
last year, or a custom range. Each report opens with a per-person summary
(present / WFH / half-day / leave / absent, days marked, present %, total hours)
and keeps every daily record alongside it.

**Save as Excel, Word, PDF or CSV** — genuine `.xlsx` and `.docx` files, not
renamed spreadsheets, so Office opens them without a corruption warning. Reports
are written straight to `~/.axvon/reports`.

**Role-based access** — an employee sees the staff directory without salaries and
only their own leave requests. Pay data is limited to HR and the owner.

### Requirements

Windows 10 or 11, 64-bit. Roughly 400 MB of disk space. A Java runtime is bundled
inside the installer, so nothing else needs installing.

### Known limitations

- Old `.xls` workbooks are not read — open in Excel and *Save As* `.xlsx`
- Scanned/image-only PDFs need OCR, which is not included
- The installer is unsigned, hence the SmartScreen prompt above
- Windows only for now; the codebase is cross-platform but untested elsewhere

---

Built by **Athul KV** — kvathul27@gmail.com · [github.com/kvathul7](https://github.com/kvathul7)
© 2025 AXVON. See `LICENSE.txt`.
