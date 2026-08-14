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

api.interceptors.response.use(r => r, e => {
  // Session expired → back to login. (Never for the login call itself,
  // and never a file:// redirect — just clear the session and reload.)
  // Both keys must go: App.jsx restores the session from axvon_user alone, so
  // dropping only the token reloads straight back into a 401 loop.
  if (e.response?.status === 401 && !String(e.config?.url || '').includes('/auth/login')) {
    localStorage.removeItem('axvon_token')
    localStorage.removeItem('axvon_user')
    window.location.reload()
  }
  return Promise.reject(e)
})

export const authAPI = { login: d=>api.post('/auth/login',d), me: ()=>api.get('/auth/me') }
export const employeeAPI = { getAll: ()=>api.get('/employees'), create: d=>api.post('/employees',d), update: (id,d)=>api.put(`/employees/${id}`,d), delete: id=>api.delete(`/employees/${id}`), stats: ()=>api.get('/employees/stats'), bulkImport: rows=>api.post('/employees/bulk-import',rows) }
export const departmentAPI = { getAll: ()=>api.get('/departments'), create: d=>api.post('/departments',d), update: (id,d)=>api.put(`/departments/${id}`,d), delete: id=>api.delete(`/departments/${id}`) }
export const leaveAPI = { getAll: ()=>api.get('/leave/requests'), getPending: ()=>api.get('/leave/requests/pending'), apply: d=>api.post('/leave/apply',d), approve: id=>api.put(`/leave/requests/${id}/approve`), reject: (id,r)=>api.put(`/leave/requests/${id}/reject`,{reason:r}), delete: id=>api.delete(`/leave/requests/${id}`) }
export const payrollAPI = { getAll: ()=>api.get('/payroll'), getByMonth: m=>api.get(`/payroll/month/${m}`), generate: d=>api.post('/payroll/generate',d), markPaid: id=>api.put(`/payroll/${id}/mark-paid`), delete: id=>api.delete(`/payroll/${id}`) }
export const holidayAPI = { getAll: ()=>api.get('/holidays'), create: d=>api.post('/holidays',d), delete: id=>api.delete(`/holidays/${id}`) }
export const candidateAPI = { getAll: ()=>api.get('/candidates'), create: d=>api.post('/candidates',d), update: (id,d)=>api.put(`/candidates/${id}`,d), delete: id=>api.delete(`/candidates/${id}`) }
export const attendanceAPI = {
  byDate: d=>api.get('/attendance', { params: { date: d } }),
  getAll: ()=>api.get('/attendance/all'),
  byEmployee: id=>api.get(`/attendance/employee/${id}`),
  summary: d=>api.get('/attendance/summary', { params: { date: d } }),
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
// date → attendance day, month → payroll YYYY-MM. Both optional; backend defaults to today / current month.
export const reportsAPI = { summary: (date,month)=>api.get('/reports/summary', { params: { date, month } }) }
export const adminAPI = {
  stats: ()=>api.get('/admin/stats'),
  deleteEmployees: ()=>api.delete('/admin/delete/employees?confirm=true'),
  deleteLeave: ()=>api.delete('/admin/delete/leave?confirm=true'),
  deletePayroll: ()=>api.delete('/admin/delete/payroll?confirm=true'),
  deleteCandidates: ()=>api.delete('/admin/delete/candidates?confirm=true'),
  deleteHolidays: ()=>api.delete('/admin/delete/holidays?confirm=true'),
  deleteAll: ()=>api.delete('/admin/delete/all?confirm=true&confirmPhrase=DELETE_ALL_AXVON_DATA')
}
export default api
