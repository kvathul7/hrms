@echo off
setlocal enabledelayedexpansion
title AXVON HRMS - Auto Builder
color 0A

echo.
echo  ================================================
echo   AXVON HRMS v2.0 - One Click Builder
echo   Created by Athul KV - (c) 2025 AXVON
echo  ================================================
echo.
echo  This will automatically:
echo   [1] Install Java 17    (if not installed)
echo   [2] Install Node.js    (if not installed)
echo   [3] Install Maven      (if not installed)
echo   [4] Build backend JAR  (Spring Boot)
echo   [5] Build frontend     (React)
echo   [6] Bundle Java runtime (so users need NOTHING installed)
echo   [7] Package into .exe  (Electron)
echo.
echo  Result: AXVON HRMS Setup 2.0.0.exe
echo  Time needed: 5-10 minutes
echo  Internet required: YES
echo.
pause

set WORK=%~dp0
set TOOLS=%WORK%_tools
mkdir "%TOOLS%" 2>nul

:: ── JAVA ────────────────────────────────────────────────────────
echo.
echo [1/6] Checking Java 17...
java -version >nul 2>&1
if %errorlevel% equ 0 (echo [OK] Java found & goto :node)
echo [INFO] Downloading Java 17...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9+9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.msi' -OutFile '%TOOLS%\java.msi' -UseBasicParsing}"
if not exist "%TOOLS%\java.msi" (echo [ERROR] Java download failed. Install from https://adoptium.net then re-run. & pause & exit /b 1)
echo [INFO] Installing Java 17...
msiexec /i "%TOOLS%\java.msi" /quiet ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome
for /f "tokens=2*" %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH 2^>nul') do set PATH=%%b;%PATH%
java -version >nul 2>&1
if %errorlevel% neq 0 (echo [WARN] Restart PC then run again. & pause & exit /b 1)
echo [OK] Java installed

:node
:: ── NODE ────────────────────────────────────────────────────────
echo.
echo [2/6] Checking Node.js...
node --version >nul 2>&1
if %errorlevel% equ 0 (echo [OK] Node.js found & goto :maven)
echo [INFO] Downloading Node.js...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;Invoke-WebRequest -Uri 'https://nodejs.org/dist/v20.11.0/node-v20.11.0-x64.msi' -OutFile '%TOOLS%\node.msi' -UseBasicParsing}"
if not exist "%TOOLS%\node.msi" (echo [ERROR] Node download failed. Install from https://nodejs.org then re-run. & pause & exit /b 1)
echo [INFO] Installing Node.js...
msiexec /i "%TOOLS%\node.msi" /quiet
for /f "tokens=2*" %%a in ('reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v PATH 2^>nul') do set PATH=%%b;%PATH%
node --version >nul 2>&1
if %errorlevel% neq 0 (echo [WARN] Restart PC then run again. & pause & exit /b 1)
echo [OK] Node.js installed

:maven
:: ── MAVEN ───────────────────────────────────────────────────────
:: The project now ships a Maven wrapper (backend\mvnw.cmd), which downloads the
:: right Maven itself. If it is there, nothing needs installing.
echo.
echo [3/6] Checking Maven...
set MVN=mvn
if exist "%WORK%backend\mvnw.cmd" (set MVN=call "%WORK%backend\mvnw.cmd" & echo [OK] Using the bundled Maven wrapper & goto :backend)
mvn -version >nul 2>&1
if %errorlevel% equ 0 (echo [OK] Maven found & goto :backend)
echo [INFO] Downloading Maven...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;Invoke-WebRequest -Uri 'https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TOOLS%\maven.zip' -UseBasicParsing}"
if not exist "%TOOLS%\maven.zip" (echo [ERROR] Maven download failed. Install from https://maven.apache.org then re-run. & pause & exit /b 1)
echo [INFO] Installing Maven to C:\maven...
powershell -Command "Expand-Archive -Path '%TOOLS%\maven.zip' -DestinationPath 'C:\' -Force"
if exist "C:\apache-maven-3.9.6" (if exist "C:\maven" rmdir /s /q "C:\maven" & rename "C:\apache-maven-3.9.6" "maven")
powershell -Command "[Environment]::SetEnvironmentVariable('PATH',$env:PATH+';C:\maven\bin','Machine')"
set PATH=%PATH%;C:\maven\bin
mvn -version >nul 2>&1
if %errorlevel% neq 0 (echo [WARN] Close this window, reopen as Admin, run again. & pause & exit /b 1)
echo [OK] Maven installed

:backend
:: ── BUILD BACKEND ───────────────────────────────────────────────
echo.
echo [4/7] Building Spring Boot backend...
echo       (First run downloads ~50MB dependencies - takes 3-5 min)
cd "%WORK%backend"
:: Tests DO run here (~15 seconds). They cover payroll arithmetic, leave balances and
:: the role rules - the things that quietly cost money if they break - so an installer
:: is never built from code that fails them. NOT -q either: a quiet build hides the
:: error that explains the failure, and the window is usually gone before anyone reads it.
%MVN% clean package
if %errorlevel% neq 0 (
  echo.
  echo  ============================================================
  echo   [ERROR] Backend build FAILED.
  echo   If the failure is a TEST, look for "Tests run:" above - the
  echo   named test says what broke. Otherwise scroll to the first
  echo   [ERROR] line. Check Java 17+ and your internet connection.
  echo  ============================================================
  echo.
  pause & exit /b 1
)
mkdir "%WORK%electron\backend" 2>nul
:: Rebuild order matters: the installer packages whatever jar is sitting here, so a
:: stale copy would ship old code with a new front end.
copy /Y "target\axvon-hrms.jar" "%WORK%electron\backend\axvon-hrms.jar" >nul
if not exist "%WORK%electron\backend\axvon-hrms.jar" (echo [ERROR] Could not copy the backend JAR into electron\backend. & pause & exit /b 1)
echo [OK] Backend JAR built and copied
echo.
pause

:: ── BUILD FRONTEND ──────────────────────────────────────────────
echo.
echo [5/7] Building React frontend...
cd "%WORK%frontend"
call npm install
if %errorlevel% neq 0 (echo [ERROR] npm install failed in frontend. Check your internet connection. & pause & exit /b 1)
call npm run build
if %errorlevel% neq 0 (
  echo.
  echo  ============================================================
  echo   [ERROR] Frontend build FAILED.
  echo   The first error above names the file and line.
  echo  ============================================================
  echo.
  pause & exit /b 1
)
:: vite.config.js writes straight into electron\frontend-dist - nothing may delete it
:: afterwards, or the installer packages an empty window.
if not exist "%WORK%electron\frontend-dist\index.html" (echo [ERROR] electron\frontend-dist\index.html is missing after the build. & pause & exit /b 1)
echo [OK] Frontend built into electron\frontend-dist
echo.
pause

:: ── BUNDLE JRE ──────────────────────────────────────────────────
:: Ships a Java runtime inside the installer, so people who install
:: the .exe do NOT need Java on their PC. App falls back to system
:: Java only if this step is skipped.
echo.
echo [6/7] Bundling Java 17 runtime into the app...
if exist "%WORK%electron\jre\bin\java.exe" (echo [OK] JRE already bundled & goto :exe)
mkdir "%WORK%electron\jre" 2>nul
echo [INFO] Downloading Java 17 JRE (~45MB)...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9+9/OpenJDK17U-jre_x64_windows_hotspot_17.0.9_9.zip' -OutFile '%TOOLS%\jre.zip' -UseBasicParsing}"
if not exist "%TOOLS%\jre.zip" (
  echo [WARN] JRE download failed - installer will require Java on target PCs.
  goto :exe
)
echo [INFO] Extracting JRE...
powershell -Command "Expand-Archive -Path '%TOOLS%\jre.zip' -DestinationPath '%TOOLS%\jre_x' -Force"
for /d %%d in ("%TOOLS%\jre_x\*") do (
  powershell -Command "Copy-Item -Path '%%d\*' -Destination '%WORK%electron\jre' -Recurse -Force"
)
if exist "%WORK%electron\jre\bin\java.exe" (echo [OK] Java runtime bundled) else (echo [WARN] JRE bundling failed - installer will require Java on target PCs.)

:exe
:: ── BUILD EXE ───────────────────────────────────────────────────
echo.
echo [7/7] Packaging into Windows installer...
cd "%WORK%electron"
call npm install
if %errorlevel% neq 0 (echo [ERROR] npm install failed in electron. & pause & exit /b 1)
call npm run build:win
if %errorlevel% neq 0 (
  echo.
  echo  ============================================================
  echo   [ERROR] Installer build FAILED.
  echo   Common causes: this window is not running as Administrator,
  echo   or antivirus is holding a file in electron\dist.
  echo  ============================================================
  echo.
  pause & exit /b 1
)

echo.
echo  ================================================
echo   DONE! Your installer is ready:
echo.
echo   %WORK%electron\dist\AXVON HRMS Setup 2.0.0.exe
echo.
echo   Share this .exe with anyone.
echo   They double-click, install, and it works.
echo.
echo   A fresh install has NO accounts - the first screen
echo   asks the operator to create their own administrator.
echo  ================================================
echo.
explorer "%WORK%electron\dist"
pause
