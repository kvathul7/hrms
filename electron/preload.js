const { contextBridge, ipcRenderer } = require('electron')
contextBridge.exposeInMainWorld('electronAPI', {
  getAppInfo: () => ipcRenderer.invoke('get-app-info'),
  openExternal: url => ipcRenderer.invoke('open-external', url),
  openDataFolder: () => ipcRenderer.invoke('open-data-folder'),
  isElectron: true,
  platform: process.platform
})
