import axios from 'axios'

// In dev (vite on :17824) '/api' is proxied to Spring Boot.
// In the packaged Electron app the page is loaded from file://, so relative
// URLs would break — talk to the embedded backend directly.
const BACKEND = 'http://localhost:17823/api'
const baseURL = window.location.protocol === 'file:' ? BACKEND : '/api'

const api = axios.create({ baseURL, headers: { 'Content-Type': 'application/json' } })

api.interceptors.request.use(c => {
  const t = localStorage.getItem('axvon_token')
  if (t) c.headers.Authorization = `Bearer ${t}`
  return c
})

// ── Session handling ──────────────────────────────────────────────────
// A token lasts 24 hours. Rather than dropping somebody onto the login screen
// mid-form when it lapses, renew it a few minutes early and, if a request does come
// back 401, try exactly one renewal before giving up on the session.
const clearSession = () => {
  // Both keys must go: App.jsx restores the session from axvon_user alone, so
  // dropping only the token reloads straight back into a 401 loop.
  localStorage.removeItem('axvon_token')
  localStorage.removeItem('axvon_user')
}

const tokenExpiry = () => {
  const t = localStorage.getItem('axvon_token')
  if (!t) return 0
  try {
    // Middle segment only, and base64url → base64 before decoding.
    const p = JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return p.exp ? p.exp * 1000 : 0
  } catch { return 0 }
}

let refreshTimer = null
let refreshing = null

// Renews the token and stores it. Concurrent callers share one in-flight request,
// so a burst of 401s cannot produce a burst of refreshes.
const doRefresh = () => {
  if (refreshing) return refreshing
  refreshing = axios.post(`${baseURL}/auth/refresh`, {}, {
    headers: { Authorization: `Bearer ${localStorage.getItem('axvon_token') || ''}` }
  }).then(r => {
    const token = r.data?.token
    if (!token) throw new Error('no token returned')
    localStorage.setItem('axvon_token', token)
    scheduleRefresh()
    return token
  }).finally(() => { refreshing = null })
  return refreshing
}

export const scheduleRefresh = () => {
  if (refreshTimer) clearTimeout(refreshTimer)
  const exp = tokenExpiry()
  if (!exp) return
  // Five minutes before expiry, but never less than half a minute away — a token
  // that is already nearly dead should be renewed now, not in the past.
  const delay = Math.max(30_000, exp - Date.now() - 5 * 60_000)
  refreshTimer = setTimeout(() => { doRefresh().catch(() => {}) }, delay)
}

export const stopRefresh = () => { if (refreshTimer) clearTimeout(refreshTimer); refreshTimer = null }

api.interceptors.response.use(r => r, async e => {
  const url = String(e.config?.url || '')
  const isAuthCall = url.includes('/auth/login') || url.includes('/auth/refresh')
  if (e.response?.status === 401 && !isAuthCall) {
    // One attempt, and only if we still hold a token to renew.
    if (!e.config._retried && localStorage.getItem('axvon_token')) {
      e.config._retried = true
      try {
        const token = await doRefresh()
        e.config.headers.Authorization = `Bearer ${token}`
        return api.request(e.config)
      } catch { /* fall through to sign-out */ }
    }
    stopRefresh()
    clearSession()
    // Never a file:// redirect — just clear the session and reload.
    window.location.reload()
  }
  return Promise.reject(e)
})

export const authAPI = {
  login: d=>api.post('/auth/login',d),
  me: ()=>api.get('/auth/me'),
  refresh: ()=>api.post('/auth/refresh')
}
export const employeeAPI = { getAll: ()=>api.get('/employees'), getById: id=>api.get(`/employees/${id}`), create: d=>api.post('/employees',d), update: (id,d)=>api.put(`/employees/${id}`,d), delete: id=>api.delete(`/employees/${id}`), stats: ()=>api.get('/employees/stats'), bulkImport: rows=>api.post('/employees/bulk-import',rows) }
export const departmentAPI = { getAll: ()=>api.get('/departments'), create: d=>api.post('/departments',d), update: (id,d)=>api.put(`/departments/${id}`,d), delete: id=>api.delete(`/departments/${id}`) }
export const leaveAPI = {
  getAll: ()=>api.get('/leave/requests'),
  getPending: ()=>api.get('/leave/requests/pending'),
  apply: d=>api.post('/leave/apply',d),
  approve: id=>api.put(`/leave/requests/${id}/approve`),
  reject: (id,r)=>api.put(`/leave/requests/${id}/reject`,{reason:r}),
  delete: id=>api.delete(`/leave/requests/${id}`),
  // Quotas are per calendar year; the server creates any missing rows on read.
  myBalances: ()=>api.get('/leave/balances'),
  balancesFor: id=>api.get(`/leave/balances/${id}`),
  quotas: ()=>api.get('/leave/quotas')
}
export const payrollAPI = {
  getAll: ()=>api.get('/payroll'),
  getByMonth: m=>api.get(`/payroll/month/${m}`),
  generate: d=>api.post('/payroll/generate',d),
  markPaid: id=>api.put(`/payroll/${id}/mark-paid`),
  delete: id=>api.delete(`/payroll/${id}`),
  attendanceSummary: month=>api.get('/payroll/attendance-summary',{ params:{ month } })
}
export const holidayAPI = { getAll: ()=>api.get('/holidays'), create: d=>api.post('/holidays',d), delete: id=>api.delete(`/holidays/${id}`) }
export const candidateAPI = { getAll: ()=>api.get('/candidates'), create: d=>api.post('/candidates',d), update: (id,d)=>api.put(`/candidates/${id}`,d), delete: id=>api.delete(`/candidates/${id}`) }
// The name-only attendance list, for companies that keep staff in a spreadsheet
// and never fill in the full employee record. Name is the only required field.
export const rosterAPI = {
  getAll: ()=>api.get('/roster'),
  create: d=>api.post('/roster',d),
  // people: [{name, code?, department?, designation?, phone?}], source: 'EXCEL'|'PDF'
  importList: (people,source)=>api.post('/roster/import',{ people, source }),
  update: (id,d)=>api.put(`/roster/${id}`,d),
  delete: id=>api.delete(`/roster/${id}`)
}
export const attendanceAPI = {
  byDate: d=>api.get('/attendance', { params: { date: d } }),
  getAll: ()=>api.get('/attendance/all'),
  byEmployee: id=>api.get(`/attendance/employee/${id}`),
  summary: d=>api.get('/attendance/summary', { params: { date: d } }),
  // Inclusive both ends — backs the daily / monthly / 6-month / yearly reports.
  range: (from,to)=>api.get('/attendance/range', { params: { from, to } }),
  mark: (date,records)=>api.post('/attendance/mark', { date, records }),
  delete: id=>api.delete(`/attendance/${id}`)
}
export const performanceAPI = {
  getAll: ()=>api.get('/performance'),
  stats: ()=>api.get('/performance/stats'),
  create: d=>api.post('/performance',d),
  update: (id,d)=>api.put(`/performance/${id}`,d),
  delete: id=>api.delete(`/performance/${id}`)
}
export const dashboardAPI = { stats: ()=>api.get('/dashboard/stats'), health: ()=>api.get('/dashboard/health') }
export const userAPI = {
  getAll: ()=>api.get('/users'),
  create: d=>api.post('/users',d),
  update: (id,d)=>api.put(`/users/${id}`,d),
  resetPassword: (id,password)=>api.put(`/users/${id}/password`,{password}),
  changeOwnPassword: (currentPassword,newPassword)=>api.put('/users/me/password',{currentPassword,newPassword}),
  delete: id=>api.delete(`/users/${id}`)
}
// Reachable with no token — it is what creates the first account on a fresh install.
export const setupAPI = { status: ()=>api.get('/setup/status'), init: d=>api.post('/setup/init',d) }
// date → attendance day, month → payroll YYYY-MM. Both optional; backend defaults to today / current month.
export const reportsAPI = { summary: (date,month)=>api.get('/reports/summary', { params: { date, month } }) }
export const adminAPI = {
  stats: ()=>api.get('/admin/stats'),
  deleteEmployees: ()=>api.delete('/admin/delete/employees?confirm=true'),
  deleteLeave: ()=>api.delete('/admin/delete/leave?confirm=true'),
  deletePayroll: ()=>api.delete('/admin/delete/payroll?confirm=true'),
  deleteCandidates: ()=>api.delete('/admin/delete/candidates?confirm=true'),
  deleteHolidays: ()=>api.delete('/admin/delete/holidays?confirm=true'),
  deleteRoster: ()=>api.delete('/admin/delete/roster?confirm=true'),
  deleteAll: ()=>api.delete('/admin/delete/all?confirm=true&confirmPhrase=DELETE_ALL_AXVON_DATA')
}
// Company identity, payroll percentages and leave quotas. Readable by everyone
// because payslips and the window header need the company's name.
export const settingsAPI = { get: ()=>api.get('/settings'), update: d=>api.put('/settings',d) }
// Paged at the server: the audit table is the one that only ever grows.
export const auditAPI = {
  search: params=>api.get('/audit',{ params }),
  actions: ()=>api.get('/audit/actions')
}
export const backupAPI = {
  export: ()=>api.get('/backup/export'),
  restore: payload=>api.post('/backup/restore?confirmPhrase=RESTORE_AXVON_BACKUP',payload)
}
export const salaryAPI = {
  history: employeeId=>api.get(`/salary-revisions/employee/${employeeId}`),
  revise: d=>api.post('/salary-revisions',d)
}
export const resignationAPI = {
  getAll: ()=>api.get('/resignations'),
  submit: d=>api.post('/resignations',d),
  approve: (id,d)=>api.put(`/resignations/${id}/approve`,d||{}),
  reject: (id,comment)=>api.put(`/resignations/${id}/reject`,{comment}),
  delete: id=>api.delete(`/resignations/${id}`)
}
export const documentAPI = {
  forEmployee: id=>api.get(`/documents/employee/${id}`),
  download: id=>api.get(`/documents/${id}/download`),
  upload: d=>api.post('/documents',d),
  delete: id=>api.delete(`/documents/${id}`)
}
export const notificationAPI = { all: ()=>api.get('/notifications') }
// Self-service. Every one of these is scoped to the signed-in person by the server.
export const meAPI = {
  profile: ()=>api.get('/me/profile'),
  payslips: ()=>api.get('/me/payslips'),
  attendance: (from,to)=>api.get('/me/attendance',{ params:{ from, to } }),
  leave: ()=>api.get('/me/leave')
}
// ── AXVON HR Assistant ───────────────────────────────────────────────
// Every call is scoped to the signed-in person by the server: the employee is taken
// from the JWT, never from the message text, so there is nothing to pass here.
export const chatAPI = {
  // { configured } tells the panel whether to offer the composer at all, instead of
  // letting somebody type a question into an assistant with no API key behind it.
  status: ()=>api.get('/chat/status'),
  // → { reply, sources:[{document,fileName,section,page,score,excerpt}] }
  // `sources` is empty for a data or action answer and populated for a policy one.
  ask: message=>api.post('/chat',{message}),
  policies: ()=>api.get('/chat/policies'),
  // multipart: Content-Type must be unset so the browser adds its own boundary —
  // the instance default of application/json would make the upload unparseable.
  uploadPolicy: (file,title)=>{
    const fd=new FormData()
    fd.append('file',file)
    if(title) fd.append('title',title)
    return api.post('/chat/policies',fd,{ headers:{ 'Content-Type':undefined } })
  },
  deletePolicy: fileName=>api.delete(`/chat/policies/${encodeURIComponent(fileName)}`)
}

export default api
