const { app, BrowserWindow, Menu, Tray, shell, ipcMain, dialog, nativeImage } = require('electron')
const { spawn } = require('child_process')
const path = require('path')
const fs = require('fs')
const http = require('http')
const os = require('os')

const APP_NAME = 'AXVON HRMS'
const APP_VERSION = '2.0.0'
const BACKEND_PORT = 17823
const DATA_DIR = path.join(os.homedir(), '.axvon')
const JAR_PATH = app.isPackaged
  ? path.join(process.resourcesPath, 'backend', 'axvon-hrms.jar')
  : path.join(__dirname, 'backend', 'axvon-hrms.jar')
function findJava() {
  const jreDir = app.isPackaged ? path.join(process.resourcesPath, 'jre') : path.join(__dirname, 'jre')
  const candidates = [path.join(jreDir, 'bin', 'java.exe'), path.join(jreDir, 'bin', 'java')]
  for (const c of candidates) { if (fs.existsSync(c)) return c }
  return 'java' // fall back to system Java
}
const JAVA_PATH = findJava()
// In packaged app, frontend-dist is in the app directory
// In development, it's relative to electron/
const FRONTEND_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'frontend-dist')
  : path.join(__dirname, 'frontend-dist')

let mainWindow = null, splashWindow = null, tray = null, backendProc = null

function ensureDirs() {
  [DATA_DIR, path.join(DATA_DIR,'data'), path.join(DATA_DIR,'logs'), path.join(DATA_DIR,'uploads')].forEach(d => {
    if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true })
  })
}

function createSplash() {
  splashWindow = new BrowserWindow({
    width: 480, height: 300, frame: false, transparent: true,
    alwaysOnTop: true, resizable: false, center: true,
    webPreferences: { nodeIntegration: false, contextIsolation: true }
  })
  splashWindow.loadFile(path.join(__dirname, 'splash.html'))
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1400, height: 880, minWidth: 1000, minHeight: 650,
    show: false, center: true, title: APP_NAME, backgroundColor: '#F8FAFC',
    icon: path.join(__dirname, 'resources', 'icon.ico'),
    webPreferences: { nodeIntegration: false, contextIsolation: true, preload: path.join(__dirname, 'preload.js') }
  })
  mainWindow.loadFile(path.join(FRONTEND_DIR, 'index.html'))
  // Reports → PDF opens a blank window, writes the table into it and prints.
  // Allow that one; send any real URL to the system browser instead of opening
  // an app window with no navigation controls.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (!url || url === 'about:blank') return { action: 'allow' }
    shell.openExternal(url)
    return { action: 'deny' }
  })
  mainWindow.once('ready-to-show', () => {
    if (splashWindow && !splashWindow.isDestroyed()) { splashWindow.close(); splashWindow = null }
    mainWindow.show(); mainWindow.focus()
  })
  mainWindow.on('close', e => {
    if (!app.isQuiting) { e.preventDefault(); mainWindow.hide() }
  })
  buildMenu()
}

function startBackend() {
  return new Promise((resolve, reject) => {
    const javaExe = JAVA_PATH
    if (!fs.existsSync(JAR_PATH)) { reject(new Error('Backend JAR not found: ' + JAR_PATH)); return }
    const logFile = fs.createWriteStream(path.join(DATA_DIR, 'logs', 'backend.log'), { flags: 'a' })
    // NOTE: -D system properties must come BEFORE -jar, otherwise the JVM
    // treats them as program arguments and Spring Boot ignores them.
    backendProc = spawn(javaExe, [
      `-Dserver.port=${BACKEND_PORT}`,
      // NOTE: H2 2.x rejects AUTO_SERVER=TRUE together with DB_CLOSE_ON_EXIT=FALSE.
      // Only one app instance touches this file, so AUTO_SERVER is not needed.
      `-Dspring.datasource.url=jdbc:h2:file:${path.join(DATA_DIR,'data','axvondb')};DB_CLOSE_ON_EXIT=FALSE`,
      `-Dlogging.file.name=${path.join(DATA_DIR,'logs','axvon.log')}`,
      '-jar', JAR_PATH
    ], { detached: false })
    backendProc.stdout.pipe(logFile)
    backendProc.stderr.pipe(logFile)
    backendProc.on('error', err => reject(err))
    let attempts = 0
    const poll = setInterval(() => {
      attempts++
      http.get(`http://localhost:${BACKEND_PORT}/api/dashboard/health`, res => {
        if (res.statusCode === 200 || res.statusCode === 401) { clearInterval(poll); resolve() }
      }).on('error', () => {})
      if (attempts >= 60) { clearInterval(poll); reject(new Error('Backend failed to start in 60s')) }
    }, 1000)
  })
}

function createTray() {
  const iconPath = path.join(__dirname, 'resources', 'tray-icon.png')
  const icon = fs.existsSync(iconPath) ? nativeImage.createFromPath(iconPath).resize({ width: 16 }) : nativeImage.createEmpty()
  tray = new Tray(icon)
  tray.setToolTip(`${APP_NAME} v${APP_VERSION}`)
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: `${APP_NAME} v${APP_VERSION}`, enabled: false },
    { type: 'separator' },
    { label: '🖥  Open AXVON HRMS', click: () => { mainWindow.show(); mainWindow.focus() } },
    { label: '🔄  Reload', click: () => mainWindow.reload() },
    { type: 'separator' },
    { label: '📁  Open Data Folder', click: () => shell.openPath(DATA_DIR) },
    { type: 'separator' },
    { label: '❌  Quit', click: () => { app.isQuiting = true; app.quit() } }
  ]))
  tray.on('double-click', () => { mainWindow.show(); mainWindow.focus() })
}

function buildMenu() {
  const nav = p => mainWindow.webContents.executeJavaScript(`window.navTo && window.navTo('${p}')`)
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: 'File', submenu: [
      { label: 'Dashboard', accelerator: 'CmdOrCtrl+D', click: () => nav('dashboard') },
      { label: 'Employees', accelerator: 'CmdOrCtrl+E', click: () => nav('employees') },
      { label: 'Leave',     accelerator: 'CmdOrCtrl+L', click: () => nav('leave') },
      { label: 'Payroll',   accelerator: 'CmdOrCtrl+P', click: () => nav('payroll') },
      { type: 'separator' },
      { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: () => mainWindow.reload() },
      { type: 'separator' },
      { label: 'Quit', accelerator: 'CmdOrCtrl+Q', click: () => { app.isQuiting = true; app.quit() } }
    ]},
    { label: 'View', submenu: [
      { label: 'Zoom In',  accelerator: 'CmdOrCtrl+Plus', click: () => { const z=mainWindow.webContents.getZoomFactor(); mainWindow.webContents.setZoomFactor(Math.min(z+.1,2)) }},
      { label: 'Zoom Out', accelerator: 'CmdOrCtrl+-',    click: () => { const z=mainWindow.webContents.getZoomFactor(); mainWindow.webContents.setZoomFactor(Math.max(z-.1,.5)) }},
      { label: 'Reset Zoom', accelerator: 'CmdOrCtrl+0', click: () => mainWindow.webContents.setZoomFactor(1) },
      { type: 'separator' },
      { label: 'Fullscreen', accelerator: 'F11', click: () => mainWindow.setFullScreen(!mainWindow.isFullScreen()) },
      { label: 'DevTools', accelerator: 'F12', click: () => mainWindow.webContents.toggleDevTools() }
    ]},
    { label: 'Help', submenu: [
      { label: 'About AXVON HRMS', click: () => dialog.showMessageBox(mainWindow, {
          type: 'info', title: 'About AXVON HRMS',
          message: `AXVON HRMS v${APP_VERSION}`,
          detail: `Enterprise Human Resource Management System\n\nCreated by: Athul KV\nEmail: kvathul27@gmail.com\nGitHub: github.com/kvathul7\n\nData stored at:\n${DATA_DIR}\n\n© 2025 AXVON. All rights reserved.`,
          buttons: ['OK']
      })},
      { label: 'Open Data Folder', click: () => shell.openPath(DATA_DIR) },
      { label: 'View Logs', click: () => shell.openPath(path.join(DATA_DIR,'logs')) }
    ]}
  ]))
}

ipcMain.handle('get-app-info', () => ({ name: APP_NAME, version: APP_VERSION, dataDir: DATA_DIR, backendPort: BACKEND_PORT, platform: process.platform }))
ipcMain.handle('open-external', (_, url) => shell.openExternal(url))
ipcMain.handle('open-data-folder', () => shell.openPath(DATA_DIR))

// Closing the window only hides it to the tray, so clicking the shortcut again
// would otherwise launch a second copy that fights the first for port 17823 and
// the H2 file lock. Hand the click to the running instance instead.
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.show(); mainWindow.focus()
  })

  app.whenReady().then(async () => {
    ensureDirs()
    createSplash()
    try {
      await startBackend()
    } catch (err) {
      dialog.showErrorBox('AXVON HRMS — Startup Error', `Backend failed to start:\n${err.message}\n\nCheck logs at: ${path.join(DATA_DIR,'logs','backend.log')}\nIf the bundled Java runtime is missing, install Java 17 from adoptium.net`)
      app.quit(); return
    }
    createMainWindow()
    createTray()
  })
}

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit() })
app.on('before-quit', () => { app.isQuiting = true; if (backendProc) { backendProc.kill('SIGTERM'); backendProc = null } })
app.on('activate', () => { if (mainWindow) { mainWindow.show(); mainWindow.focus() } })
