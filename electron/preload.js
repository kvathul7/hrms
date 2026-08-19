const { contextBridge, ipcRenderer } = require('electron')
contextBridge.exposeInMainWorld('electronAPI', {
  getAppInfo: () => ipcRenderer.invoke('get-app-info'),
  openExternal: url => ipcRenderer.invoke('open-external', url),
  openDataFolder: () => ipcRenderer.invoke('open-data-folder'),
  // Report exports. saveReport takes base64 so binary (xlsx/docx) survives the bridge;
  // saveReportPdf hands over HTML and gets back a rendered PDF. Both resolve to the
  // absolute path written, so the UI can tell the operator where the file went.
  saveReport: payload => ipcRenderer.invoke('save-report', payload),
  saveReportPdf: payload => ipcRenderer.invoke('save-report-pdf', payload),
  openReportsFolder: () => ipcRenderer.invoke('open-reports-folder'),
  // Automatic database copies taken on every launch.
  openBackupsFolder: () => ipcRenderer.invoke('open-backups-folder'),
  // Native desktop notification, e.g. when a leave request arrives.
  notify: payload => ipcRenderer.invoke('notify', payload),
  isElectron: true,
  platform: process.platform
})
