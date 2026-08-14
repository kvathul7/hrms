import{useState,useEffect,useCallback}from'react'
// The package exposes no root entry — the browser build is the one that reads a File.
import readXlsxFile from'read-excel-file/browser'
import{authAPI,employeeAPI,departmentAPI,leaveAPI,payrollAPI,holidayAPI,candidateAPI,attendanceAPI,performanceAPI,dashboardAPI,reportsAPI,adminAPI}from'./services/api'

const Logo=({size=34})=>(
  <svg width={size} height={size} viewBox="0 0 200 200" fill="none">
    <defs>
      <linearGradient id="gL" x1="30" y1="175" x2="100" y2="15" gradientUnits="userSpaceOnUse"><stop offset="0%" stopColor="#2563EB"/><stop offset="100%" stopColor="#60A5FA"/></linearGradient>
      <linearGradient id="gR" x1="170" y1="175" x2="100" y2="15" gradientUnits="userSpaceOnUse"><stop offset="0%" stopColor="#14B8A6"/><stop offset="100%" stopColor="#2DD4BF"/></linearGradient>
    </defs>
    <path d="M100 15 Q95 15 90 25 L28 165 Q23 178 35 180 L58 180 Q68 180 73 168 L100 105 Z" fill="url(#gL)"/>
    <path d="M100 15 Q105 15 110 25 L172 165 Q177 178 165 180 L142 180 Q132 180 127 168 L100 105 Z" fill="url(#gR)"/>
    <polygon points="100,108 82,150 118,150" fill="#0F172A"/>
  </svg>
)

const AVC=['#14B8A6','#3B82F6','#8B5CF6','#F97316','#EC4899','#22C55E','#F59E0B','#6366F1']
const avc=s=>AVC[(s||'A').charCodeAt(0)%AVC.length]
const ini=n=>(n||'?').split(' ').map(w=>w[0]).join('').slice(0,2).toUpperCase()
const inr=n=>'₹'+Number(n||0).toLocaleString('en-IN')
const sc=s=>({Active:'#22C55E','On Leave':'#F59E0B','On_Leave':'#F59E0B',Inactive:'#EF4444',Pending:'#F59E0B',Approved:'#22C55E',Rejected:'#EF4444',Present:'#22C55E',Absent:'#EF4444',Processed:'#22C55E',Paid:'#14B8A6','In Progress':'#3B82F6','In_Progress':'#3B82F6',Selected:'#22C55E',Completed:'#22C55E',National:'#3B82F6',Festival:'#F59E0B'}[s]||'#94A3B8')
const Av=({name,size=30})=><div style={{width:size,height:size,borderRadius:'50%',background:avc(ini(name)),display:'flex',alignItems:'center',justifyContent:'center',color:'#fff',fontWeight:700,fontSize:Math.round(size*.34),flexShrink:0}}>{ini(name)}</div>
const Badge=({label})=>{const c=sc(label);return<span style={{display:'inline-block',padding:'2px 9px',borderRadius:20,fontSize:11,fontWeight:700,background:c+'18',color:c,border:`1px solid ${c}28`}}>{String(label||'').replace(/_/g,' ')}</span>}
const Spin=()=><div style={{display:'flex',justifyContent:'center',padding:48}}><div style={{width:32,height:32,border:'3px solid #E2E8F0',borderTopColor:'#14B8A6',borderRadius:'50%',animation:'spin .8s linear infinite'}}/></div>
const Card=({children,style})=><div style={{background:'#fff',borderRadius:13,border:'1px solid #E2E8F0',...style}}>{children}</div>
const Btn=({onClick,children,disabled,style})=><button onClick={onClick} disabled={disabled} style={{background:'#14B8A6',border:'none',borderRadius:9,padding:'9px 18px',color:'#fff',fontSize:13,fontWeight:700,cursor:disabled?'not-allowed':'pointer',opacity:disabled?.6:1,fontFamily:'inherit',display:'inline-flex',alignItems:'center',gap:7,...style}}>{children}</button>
const BtnO=({onClick,children,style})=><button onClick={onClick} style={{background:'transparent',border:'1px solid #E2E8F0',borderRadius:9,padding:'9px 14px',fontSize:13,fontWeight:500,cursor:'pointer',fontFamily:'inherit',...style}}>{children}</button>
const BtnD=({onClick,children})=><button onClick={onClick} style={{background:'#FEF2F2',border:'1px solid #FECACA',borderRadius:9,padding:'5px 9px',color:'#DC2626',fontSize:12,fontWeight:700,cursor:'pointer',fontFamily:'inherit'}}>{children}</button>
const Inp=({value,onChange,placeholder,type='text',style})=><input type={type} value={value||''} onChange={e=>onChange(e.target.value)} placeholder={placeholder} style={{border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none',fontFamily:'inherit',width:'100%',...style}}/>
const Fld=({label,children})=><div style={{marginBottom:13}}><label style={{display:'block',fontSize:12,fontWeight:700,marginBottom:4}}>{label}</label>{children}</div>
const SEL={border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none',fontFamily:'inherit',width:'100%',background:'#fff'}
const Sel=({value,onChange,children})=><select value={value} onChange={e=>onChange(e.target.value)} style={SEL}>{children}</select>
// Every modal form ends the same way: cancel on the left, the primary action on the right.
const FormFoot=({onSave,busy,label})=><div style={{display:'flex',justifyContent:'flex-end',gap:9,marginTop:14,paddingTop:14,borderTop:'1px solid #E2E8F0'}}><BtnO onClick={closeModal}>Cancel</BtnO><Btn onClick={onSave} disabled={busy}>{busy?'Saving…':label}</Btn></div>
const St=({label,value,sub,color='#14B8A6'})=><div style={{background:'#fff',borderRadius:13,border:'1px solid #E2E8F0',padding:'18px 20px',borderTop:`3px solid ${color}`}}><div style={{fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',marginBottom:8}}>{label}</div><div style={{fontSize:26,fontWeight:900,letterSpacing:'-.5px'}}>{value}</div>{sub&&<div style={{fontSize:12,color:'#94A3B8',marginTop:5}}>{sub}</div>}</div>
const PH=({title,sub,children})=><div style={{display:'flex',justifyContent:'space-between',alignItems:'flex-start',marginBottom:20,flexWrap:'wrap',gap:12}}><div><div style={{fontSize:21,fontWeight:900,letterSpacing:'-.5px'}}>{title}</div>{sub&&<div style={{fontSize:13,color:'#64748B',marginTop:2}}>{sub}</div>}</div><div style={{display:'flex',gap:8,flexWrap:'wrap'}}>{children}</div></div>

let _setT=null
const toast=(msg,type='success')=>{if(_setT)_setT(p=>[...p,{id:Date.now(),msg,type}])}
function Toasts(){
  const[ts,setTs]=useState([])
  useEffect(()=>{_setT=setTs},[])
  useEffect(()=>{if(ts.length){const t=setTimeout(()=>setTs(p=>p.slice(1)),3000);return()=>clearTimeout(t)}},[ts])
  const c={success:'#22C55E',error:'#EF4444',warn:'#F59E0B',info:'#3B82F6'}
  return<div style={{position:'fixed',bottom:20,right:20,zIndex:9999,display:'flex',flexDirection:'column',gap:8}}>
    {ts.map(t=><div key={t.id} style={{background:'#fff',border:'1px solid #E2E8F0',padding:'11px 16px',borderRadius:11,fontSize:13,fontWeight:500,display:'flex',alignItems:'center',gap:9,boxShadow:'0 4px 20px rgba(0,0,0,.1)',borderLeft:`4px solid ${c[t.type]||'#14B8A6'}`,animation:'fadeUp .2s ease'}}>
      <span style={{color:c[t.type],fontWeight:800}}>{t.type==='error'?'✕':t.type==='warn'?'⚠':'✓'}</span>{t.msg}
    </div>)}
  </div>
}

let _oM=null,_cM=null
const openModal=(t,b)=>{if(_oM)_oM(t,b)}
const closeModal=()=>{if(_cM)_cM()}
function Modal(){
  const[s,setS]=useState(null)
  useEffect(()=>{_oM=(t,b)=>setS({t,b});_cM=()=>setS(null)},[])
  if(!s)return null
  return<div onClick={closeModal} style={{position:'fixed',inset:0,background:'rgba(15,23,42,.45)',backdropFilter:'blur(3px)',zIndex:700,display:'flex',alignItems:'center',justifyContent:'center',padding:16}}>
    <div onClick={e=>e.stopPropagation()} style={{background:'#fff',borderRadius:16,width:'100%',maxWidth:580,maxHeight:'90vh',overflowY:'auto',boxShadow:'0 24px 64px rgba(0,0,0,.15)',animation:'fadeUp .2s ease'}}>
      <div style={{padding:'17px 21px',borderBottom:'1px solid #E2E8F0',display:'flex',justifyContent:'space-between',alignItems:'center',position:'sticky',top:0,background:'#fff',zIndex:1,borderRadius:'16px 16px 0 0'}}>
        <span style={{fontSize:15,fontWeight:800}}>{s.t}</span>
        <button onClick={closeModal} style={{background:'#F8FAFC',border:'1px solid #E2E8F0',borderRadius:8,padding:'6px 10px',cursor:'pointer'}}>✕</button>
      </div>
      <div style={{padding:21}}>{s.b}</div>
    </div>
  </div>
}

const NAV=[
  {g:'Overview',items:[{id:'dashboard',l:'Dashboard',i:'📊'}]},
  {g:'Workforce',items:[{id:'employees',l:'Employees',i:'👥'},{id:'attendance',l:'Attendance',i:'🕐'},{id:'leave',l:'Leave',i:'🌴'},{id:'payroll',l:'Payroll',i:'💰'}]},
  {g:'Talent',items:[{id:'recruitment',l:'Recruitment',i:'🎯'},{id:'performance',l:'Performance',i:'⭐'}]},
  {g:'Organisation',items:[{id:'departments',l:'Departments',i:'🏢'},{id:'holidays',l:'Holidays',i:'📅'}]},
  {g:'Insights',items:[{id:'reports',l:'Reports',i:'📁'}]},
  {g:'System',items:[{id:'bulk',l:'Bulk Import',i:'⬆'},{id:'admin',l:'Admin & Delete',i:'⚙'},{id:'about',l:'About',i:'★'}]}
]

function Login({onLogin}){
  const[email,setEmail]=useState('')
  const[pass,setPass]=useState('')
  const[err,setErr]=useState('')
  const[loading,setLoading]=useState(false)
  const demos=[{role:'Super Admin',email:'admin@axvon.io',pass:'admin123'},{role:'HR Admin',email:'hr@axvon.io',pass:'hr123'},{role:'Manager',email:'manager@axvon.io',pass:'mgr123'},{role:'Employee',email:'emp@axvon.io',pass:'emp123'}]
  const doLogin=async()=>{
    if(!email||!pass){setErr('Enter email and password');return}
    setLoading(true);setErr('')
    try{const r=await authAPI.login({email,password:pass});const{token,...u}=r.data;localStorage.setItem('axvon_token',token);localStorage.setItem('axvon_user',JSON.stringify(u));onLogin(u)}
    catch(e){setErr(e.response?.data?.message||'Invalid credentials')}
    finally{setLoading(false)}
  }
  return<div style={{display:'flex',height:'100vh'}}>
    <div style={{width:'42%',background:'#0F172A',display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',padding:48,position:'relative',overflow:'hidden'}}>
      <div style={{position:'absolute',width:400,height:400,borderRadius:'50%',background:'rgba(20,184,166,.1)',top:'-20%',right:'-20%',filter:'blur(70px)'}}/>
      <div style={{position:'relative',zIndex:1,width:'100%',maxWidth:330}}>
        <div style={{display:'flex',alignItems:'center',gap:13,marginBottom:26}}><Logo size={46}/><div><div style={{fontSize:22,fontWeight:900,color:'#fff',letterSpacing:2.5}}>AXVON</div><div style={{fontSize:9,color:'#475569',marginTop:2}}>HRMS PLATFORM v2.0</div></div></div>
        <div style={{color:'#94A3B8',fontSize:13.5,lineHeight:1.7,marginBottom:26}}>Streamlining Attendance, Payroll, Leave Management and Workforce Operations in One Platform.</div>
        {['🎯 End-to-end HR in one platform','💾 Real database — H2 embedded','🔒 JWT secured — role-based access','☁ Cloud-ready architecture'].map(f=><div key={f} style={{padding:'10px 14px',background:'rgba(255,255,255,.05)',borderRadius:9,border:'1px solid rgba(255,255,255,.07)',color:'#CBD5E1',fontSize:13,marginBottom:8}}>{f}</div>)}
      </div>
    </div>
    <div style={{flex:1,display:'flex',alignItems:'center',justifyContent:'center',padding:40,background:'#F8FAFC'}}>
      <div style={{width:'100%',maxWidth:390}}>
        <h2 style={{fontSize:26,fontWeight:900,marginBottom:5}}>Welcome back 👋</h2>
        <p style={{fontSize:13.5,color:'#64748B',marginBottom:28}}>Sign in to your AXVON workspace</p>
        <Inp value={email} onChange={setEmail} placeholder="name@company.com" type="email" style={{marginBottom:12}}/>
        <Inp value={pass} onChange={setPass} placeholder="Password" type="password" style={{marginBottom:12}}/>
        {err&&<div style={{background:'#FEF2F2',border:'1px solid #FECACA',color:'#DC2626',fontSize:13,padding:'10px 14px',borderRadius:9,marginBottom:12}}>{err}</div>}
        <Btn onClick={doLogin} disabled={loading} style={{width:'100%',justifyContent:'center',padding:13,fontSize:15}}>{loading?'Signing in…':'Sign In →'}</Btn>
        <div style={{background:'#F8FAFC',border:'1.5px solid #E2E8F0',borderRadius:12,padding:14,marginTop:18}}>
          <div style={{fontSize:10.5,color:'#94A3B8',fontWeight:700,textTransform:'uppercase',letterSpacing:'.7px',marginBottom:10}}>Click to fill demo credentials</div>
          {demos.map(d=><div key={d.email} onClick={()=>{setEmail(d.email);setPass(d.pass)}} style={{display:'flex',justifyContent:'space-between',padding:'8px 10px',borderRadius:8,cursor:'pointer',marginBottom:4}} onMouseOver={e=>e.currentTarget.style.background='#fff'} onMouseOut={e=>e.currentTarget.style.background=''}>
            <div><div style={{fontSize:12,fontWeight:700,color:'#14B8A6'}}>{d.role}</div><div style={{fontSize:11,color:'#94A3B8',fontFamily:'monospace'}}>{d.email} / {d.pass}</div></div>
          </div>)}
        </div>
      </div>
    </div>
  </div>
}

function Dashboard({user}){
  const[stats,setStats]=useState(null)
  const[failed,setFailed]=useState(false)
  // Never substitute invented numbers for a failed call — a dashboard that quietly
  // shows made-up headcount is worse than one that admits it could not load.
  useEffect(()=>{dashboardAPI.stats().then(r=>{setStats(r.data.data);setFailed(false)}).catch(()=>setFailed(true))},[])
  const v=k=>failed?'—':(stats?.[k]??'…')
  return<div>
    <div style={{background:'linear-gradient(135deg,#0F172A,#1E3A5F)',borderRadius:16,padding:'24px 28px',marginBottom:20,display:'flex',alignItems:'center',justifyContent:'space-between',flexWrap:'wrap',gap:14}}>
      <div><div style={{color:'#14B8A6',fontSize:11,fontWeight:700,letterSpacing:1.2,textTransform:'uppercase',marginBottom:5}}>Welcome back</div><div style={{color:'#F1F5F9',fontSize:21,fontWeight:800}}>{user.name} 👋</div><div style={{color:'#94A3B8',fontSize:13,marginTop:3}}>{new Date().toLocaleDateString('en-IN',{weekday:'long',year:'numeric',month:'long',day:'numeric'})}</div></div>
      <div style={{display:'flex',gap:9}}><button onClick={()=>window.navTo&&window.navTo('employees')} style={{padding:'9px 18px',borderRadius:9,fontSize:13,fontWeight:700,cursor:'pointer',border:'none',background:'#14B8A6',color:'#fff',fontFamily:'inherit'}}>+ Add Employee</button></div>
    </div>
    {failed&&<div style={{background:'#FEF2F2',border:'1px solid #FECACA',color:'#B91C1C',fontSize:13,padding:'11px 15px',borderRadius:11,marginBottom:14}}>Could not reach the backend — these figures are unavailable, not zero. Check that AXVON's backend is running on port 17823.</div>}
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:18}}>
      <St label="Total Employees" value={v('totalEmployees')} sub="All departments" color="#14B8A6"/>
      <St label="Active Staff" value={v('activeEmployees')} sub="Currently working" color="#22C55E"/>
      <St label="Pending Leaves" value={v('pendingLeaves')} sub="Awaiting approval" color="#F59E0B"/>
      <St label="Departments" value={v('totalDepartments')} sub="Configured" color="#3B82F6"/>
    </div>
    <Card style={{padding:'20px 22px'}}>
      <div style={{fontSize:14,fontWeight:700,marginBottom:14}}>Quick Actions</div>
      <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:10}}>
        {[['👥','Employees','employees'],['🌴','Leave','leave'],['💰','Payroll','payroll'],['⬆','Bulk Import','bulk'],['📊','Reports','reports'],['⚙','Admin','admin'],['🎯','Recruitment','recruitment'],['📅','Holidays','holidays']].map(([i,t,p])=><button key={p} onClick={()=>window.navTo&&window.navTo(p)} style={{background:'#F8FAFC',border:'1px solid #E2E8F0',borderRadius:10,padding:'13px 10px',cursor:'pointer',fontFamily:'inherit',textAlign:'center'}} onMouseOver={e=>e.currentTarget.style.borderColor='#14B8A6'} onMouseOut={e=>e.currentTarget.style.borderColor='#E2E8F0'}><div style={{fontSize:20,marginBottom:4}}>{i}</div><div style={{fontSize:12,fontWeight:600}}>{t}</div></button>)}
      </div>
    </Card>
  </div>
}

function Employees(){
  const[emps,setEmps]=useState([]);const[depts,setDepts]=useState([]);const[loading,setLoading]=useState(true);const[q,setQ]=useState('');const[saving,setSaving]=useState(false)
  const load=useCallback(()=>{setLoading(true);employeeAPI.getAll().then(r=>setEmps(r.data.data||[])).catch(()=>toast('Failed to load','error')).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  // The API has always accepted departmentId; without this list the form had no way
  // to send one, so every employee added in the app showed no department.
  useEffect(()=>{departmentAPI.getAll().then(r=>setDepts(r.data.data||[])).catch(()=>setDepts([]))},[])
  const filtered=emps.filter(e=>`${e.firstName}${e.lastName}${e.email}${e.empId}`.toLowerCase().includes(q.toLowerCase()))
  const empForm=(e)=>{
    let f={...e}
    const save=async()=>{
      setSaving(true)
      try{
        if(!f.firstName||!f.lastName||!f.email){toast('Fill required fields','error');setSaving(false);return}
        if(e?.id){await employeeAPI.update(e.id,f);toast('Employee updated!')}
        else{await employeeAPI.create({...f,joinDate:f.joinDate||new Date().toISOString().slice(0,10)});toast('Employee added!')}
        closeModal();load()
      }catch(err){toast(err.response?.data?.message||'Error saving','error')}
      finally{setSaving(false)}
    }
    return<div>
      <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'0 14px'}}>
        {[['empId','Emp ID'],['firstName','First Name *'],['lastName','Last Name *'],['email','Email *'],['phone','Phone'],['designation','Designation'],['location','Location'],['manager','Manager']].map(([k,l])=><Fld key={k} label={l}><Inp value={f[k]||''} onChange={v=>{f={...f,[k]:v}}}/></Fld>)}
        <Fld label="Basic Salary (₹)"><Inp type="number" value={f.basicSalary||''} onChange={v=>{f={...f,basicSalary:v}}}/></Fld>
        <Fld label="Join Date"><Inp type="date" value={f.joinDate||''} onChange={v=>{f={...f,joinDate:v}}}/></Fld>
        <Fld label="Status"><select defaultValue={f.status||'Active'} onChange={e=>{f={...f,status:e.target.value}}} style={{width:'100%',border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none'}}><option>Active</option><option>On_Leave</option><option>Inactive</option><option>Terminated</option></select></Fld>
        <Fld label="Gender"><select defaultValue={f.gender||''} onChange={e=>{f={...f,gender:e.target.value}}} style={{width:'100%',border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none'}}><option value="">Select</option><option>Male</option><option>Female</option><option>Other</option></select></Fld>
        <Fld label="Department"><select defaultValue={e?.department?.id||''} onChange={ev=>{f={...f,departmentId:ev.target.value||null}}} style={{width:'100%',border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none'}}><option value="">Unassigned</option>{depts.map(d=><option key={d.id} value={d.id}>{d.name}</option>)}</select></Fld>
      </div>
      <div style={{display:'flex',justifyContent:'flex-end',gap:9,marginTop:14,paddingTop:14,borderTop:'1px solid #E2E8F0'}}>
        <BtnO onClick={closeModal}>Cancel</BtnO>
        <Btn onClick={save} disabled={saving}>{saving?'Saving…':e?'Save Changes':'Add Employee'}</Btn>
      </div>
    </div>
  }
  const del=async(id)=>{if(!confirm('Delete this employee?'))return;try{await employeeAPI.delete(id);toast('Deleted','warn');load()}catch{toast('Error','error')}}
  return<div>
    <PH title="Employees" sub={`${emps.length} people · live from database`}><BtnO onClick={()=>exportReport('employees','csv')}>⬇ Export CSV</BtnO><Btn onClick={()=>openModal('Add New Employee',empForm(null))}>+ Add Employee</Btn></PH>
    <Card style={{padding:12,marginBottom:13}}><Inp value={q} onChange={setQ} placeholder="Search name, ID or email…"/></Card>
    <Card>
      {loading?<Spin/>:<div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Employee','ID','Department','Designation','Location','Salary','Status','Actions'].map(h=><th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{filtered.map(e=><tr key={e.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}} onMouseOver={ev=>ev.currentTarget.style.background='#F8FAFC'} onMouseOut={ev=>ev.currentTarget.style.background=''}>
          <td style={{padding:'11px 14px'}}><div style={{display:'flex',alignItems:'center',gap:10}}><Av name={`${e.firstName} ${e.lastName}`} size={34}/><div><div style={{fontWeight:600}}>{e.firstName} {e.lastName}</div><div style={{fontSize:11,color:'#94A3B8'}}>{e.email}</div></div></div></td>
          <td style={{padding:'11px 14px'}}><span style={{fontFamily:'monospace',fontSize:11,color:'#64748B',background:'#F8FAFC',padding:'2px 7px',borderRadius:4}}>{e.empId}</span></td>
          <td style={{padding:'11px 14px'}}>{e.department?.name||'—'}</td>
          <td style={{padding:'11px 14px'}}>{e.designation||'—'}</td>
          <td style={{padding:'11px 14px'}}>{e.location||'—'}</td>
          <td style={{padding:'11px 14px',fontWeight:600}}>{inr(e.basicSalary)}</td>
          <td style={{padding:'11px 14px'}}><Badge label={e.status}/></td>
          <td style={{padding:'11px 14px',textAlign:'right'}}><div style={{display:'inline-flex',gap:5}}>
            <BtnO onClick={()=>openModal('Edit Employee',empForm(e))} style={{padding:'5px 11px',fontSize:12}}>Edit</BtnO>
            <BtnD onClick={()=>del(e.id)}>🗑</BtnD>
          </div></td>
        </tr>)}
        {!filtered.length&&<tr><td colSpan={8} style={{padding:48,textAlign:'center',color:'#94A3B8'}}>No employees found</td></tr>}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

const LEAVE_TYPES=['Casual','Sick','Earned','Unpaid','Maternity','Paternity']
function LeaveForm({emps,onDone}){
  const[f,setF]=useState({employeeId:'',leaveType:'Casual',fromDate:'',toDate:'',reason:''})
  const[busy,setBusy]=useState(false)
  // Inclusive of both end dates — a single-day leave is 1 day, not 0.
  const days=f.fromDate&&f.toDate?Math.floor((new Date(f.toDate)-new Date(f.fromDate))/86400000)+1:0
  const submit=async()=>{
    if(!f.employeeId){toast('Select an employee','warn');return}
    if(!f.fromDate||!f.toDate){toast('Pick both dates','warn');return}
    if(days<1){toast('To date must be on or after the from date','warn');return}
    setBusy(true)
    try{
      await leaveAPI.apply({employeeId:Number(f.employeeId),leaveType:f.leaveType,fromDate:f.fromDate,toDate:f.toDate,totalDays:days,reason:f.reason.trim()||'Not stated'})
      toast('Leave request submitted');closeModal();onDone()
    }catch(e){toast(e.response?.data?.message||'Could not submit leave request','error')}
    finally{setBusy(false)}
  }
  return<div>
    <Fld label="Employee *"><Sel value={f.employeeId} onChange={v=>setF({...f,employeeId:v})}><option value="">— Select —</option>{emps.map(e=><option key={e.id} value={e.id}>{e.firstName} {e.lastName} ({e.empId})</option>)}</Sel></Fld>
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
      <Fld label="Leave Type"><Sel value={f.leaveType} onChange={v=>setF({...f,leaveType:v})}>{LEAVE_TYPES.map(t=><option key={t} value={t}>{t}</option>)}</Sel></Fld>
      <Fld label="Total Days"><Inp value={days?`${days} day${days>1?'s':''}`:'—'} onChange={()=>{}} style={{background:'#F8FAFC',color:'#64748B'}}/></Fld>
      <Fld label="From *"><Inp type="date" value={f.fromDate} onChange={v=>setF({...f,fromDate:v})}/></Fld>
      <Fld label="To *"><Inp type="date" value={f.toDate} onChange={v=>setF({...f,toDate:v})}/></Fld>
    </div>
    <Fld label="Reason"><textarea value={f.reason} onChange={e=>setF({...f,reason:e.target.value})} rows={3} placeholder="Why is the leave needed?" style={{...SEL,resize:'vertical'}}/></Fld>
    <FormFoot onSave={submit} busy={busy} label="Submit Request"/>
  </div>
}
function Leave(){
  const[lvs,setLvs]=useState([]);const[emps,setEmps]=useState([]);const[loading,setLoading]=useState(true)
  const load=useCallback(()=>{setLoading(true);leaveAPI.getAll().then(r=>setLvs(r.data.data||[])).catch(()=>setLvs([])).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  useEffect(()=>{employeeAPI.getAll().then(r=>setEmps(r.data.data||[])).catch(()=>setEmps([]))},[])
  const approve=async(id)=>{try{await leaveAPI.approve(id);toast('Leave approved!');load()}catch{toast('Error','error')}}
  const reject=async(id)=>{try{await leaveAPI.reject(id,'Rejected');toast('Leave rejected','warn');load()}catch{toast('Error','error')}}
  const del=async(id)=>{if(!window.confirm('Delete this leave request?'))return;try{await leaveAPI.delete(id);toast('Leave request deleted','warn');load()}catch(e){toast(e.response?.data?.message||'Delete failed','error')}}
  const pend=lvs.filter(l=>l.status==='Pending').length
  return<div>
    <PH title="Leave Management" sub={`${pend} pending · live from database`}><BtnO onClick={()=>exportReport('leave','csv')}>⬇ Export CSV</BtnO><Btn onClick={()=>openModal('Apply for Leave',<LeaveForm emps={emps} onDone={load}/>)}>+ Apply Leave</Btn></PH>
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:16}}>
      <St label="Pending" value={pend} color="#F59E0B"/>
      <St label="Approved" value={lvs.filter(l=>l.status==='Approved').length} color="#22C55E"/>
      <St label="Rejected" value={lvs.filter(l=>l.status==='Rejected').length} color="#EF4444"/>
      <St label="Total" value={lvs.length} color="#3B82F6"/>
    </div>
    <Card>
      {loading?<Spin/>:<div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Employee','Type','From','To','Days','Reason','Status','Actions'].map(h=><th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{lvs.map(l=><tr key={l.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}} onMouseOver={e=>e.currentTarget.style.background='#F8FAFC'} onMouseOut={e=>e.currentTarget.style.background=''}>
          <td style={{padding:'11px 14px'}}><div style={{display:'flex',alignItems:'center',gap:8}}><Av name={`${l.employee?.firstName} ${l.employee?.lastName}`} size={26}/><span style={{fontWeight:600}}>{l.employee?.firstName} {l.employee?.lastName}</span></div></td>
          <td style={{padding:'11px 14px'}}>{l.leaveType}</td>
          <td style={{padding:'11px 14px'}}>{l.fromDate}</td>
          <td style={{padding:'11px 14px'}}>{l.toDate}</td>
          <td style={{padding:'11px 14px',fontWeight:700}}>{l.totalDays}d</td>
          <td style={{padding:'11px 14px',color:'#64748B',fontSize:12}}>{l.reason}</td>
          <td style={{padding:'11px 14px'}}><Badge label={l.status}/></td>
          <td style={{padding:'11px 14px',textAlign:'right'}}><div style={{display:'inline-flex',gap:5}}>
            {l.status==='Pending'&&<><button onClick={()=>approve(l.id)} style={{background:'#22C55E',border:'none',borderRadius:7,padding:'5px 10px',color:'#fff',cursor:'pointer',fontFamily:'inherit',fontSize:12,fontWeight:700}}>✓ Approve</button><BtnD onClick={()=>reject(l.id)}>Reject</BtnD></>}
            <BtnD onClick={()=>del(l.id)}>🗑 Delete</BtnD>
          </div></td>
        </tr>)}
        {!lvs.length&&<tr><td colSpan={8} style={{padding:48,textAlign:'center',color:'#94A3B8'}}>No leave requests</td></tr>}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

function Payroll(){
  const[pay,setPay]=useState([]);const[loading,setLoading]=useState(true);const[month,setMonth]=useState(new Date().toISOString().slice(0,7));const[running,setRunning]=useState(false)
  const load=useCallback(()=>{setLoading(true);payrollAPI.getAll().then(r=>setPay(r.data.data||[])).catch(()=>setPay([])).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  const generate=async()=>{setRunning(true);try{const r=await payrollAPI.generate({payMonth:month});toast(`Generated: ${r.data.data?.generated||0} employees`);load()}catch(e){toast(e.response?.data?.message||'Error','error')}finally{setRunning(false)}}
  const markPaid=async(id)=>{try{await payrollAPI.markPaid(id);toast('Marked as paid!');load()}catch{toast('Error','error')}}
  const del=async(id)=>{if(!window.confirm('Delete this payslip?'))return;try{await payrollAPI.delete(id);toast('Payslip deleted','warn');load()}catch(e){toast(e.response?.data?.message||'Delete failed','error')}}
  const tg=pay.reduce((s,p)=>s+(Number(p.grossSalary)||0),0)
  const tn=pay.reduce((s,p)=>s+(Number(p.netSalary)||0),0)
  return<div>
    <PH title="Payroll" sub="Salary processing · live from database"><input type="month" value={month} onChange={e=>setMonth(e.target.value)} style={{border:'1px solid #E2E8F0',borderRadius:9,padding:'8px 12px',fontSize:13,outline:'none',fontFamily:'inherit'}}/><BtnO onClick={()=>exportReport('payroll','csv')}>⬇ Export CSV</BtnO><Btn onClick={generate} disabled={running}>{running?'Generating…':'▶ Run Payroll'}</Btn></PH>
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:16}}>
      <St label="Gross Payable" value={inr(tg)} color="#1E293B"/>
      <St label="Net Payable" value={inr(tn)} color="#22C55E"/>
      <St label="Total PF" value={inr(pay.reduce((s,p)=>s+(Number(p.pfDeduction)||0),0))} color="#8B5CF6"/>
      <St label="Total TDS" value={inr(pay.reduce((s,p)=>s+(Number(p.tdsDeduction)||0),0))} color="#F59E0B"/>
    </div>
    <Card>
      {loading?<Spin/>:<div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Employee','Basic','HRA','Gross','Net','Status','Action'].map(h=><th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{pay.map(p=><tr key={p.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}} onMouseOver={e=>e.currentTarget.style.background='#F8FAFC'} onMouseOut={e=>e.currentTarget.style.background=''}>
          <td style={{padding:'11px 14px'}}><div style={{display:'flex',alignItems:'center',gap:9}}><Av name={`${p.employee?.firstName} ${p.employee?.lastName}`} size={30}/><div><div style={{fontWeight:600}}>{p.employee?.firstName} {p.employee?.lastName}</div><div style={{fontSize:11,color:'#94A3B8'}}>{p.payMonth}</div></div></div></td>
          <td style={{padding:'11px 14px'}}>{inr(p.basicSalary)}</td>
          <td style={{padding:'11px 14px'}}>{inr(p.hra)}</td>
          <td style={{padding:'11px 14px',fontWeight:700}}>{inr(p.grossSalary)}</td>
          <td style={{padding:'11px 14px',fontWeight:900,color:'#22C55E'}}>{inr(p.netSalary)}</td>
          <td style={{padding:'11px 14px'}}><Badge label={p.status}/></td>
          <td style={{padding:'11px 14px'}}><div style={{display:'inline-flex',gap:5}}>
            {p.status==='Processed'&&<button onClick={()=>markPaid(p.id)} style={{background:'#14B8A6',border:'none',borderRadius:7,padding:'5px 10px',color:'#fff',cursor:'pointer',fontFamily:'inherit',fontSize:11,fontWeight:700}}>Mark Paid</button>}
            <BtnD onClick={()=>del(p.id)}>🗑 Delete</BtnD>
            <BtnO onClick={()=>payslip(p)} style={{padding:'5px 10px',fontSize:11}}>⬇ Slip</BtnO>
          </div></td>
        </tr>)}
        {!pay.length&&<tr><td colSpan={7} style={{padding:48,textAlign:'center',color:'#94A3B8'}}>No payroll records. Click "Run Payroll" to generate.</td></tr>}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

// The old "Add Department" modal had an inert input and a button that toasted
// "Added!" without calling the API — it reported success for work never done.
function DeptForm({dept,onDone}){
  const[f,setF]=useState({name:dept?.name||'',headOfDept:dept?.headOfDept||'',description:dept?.description||''})
  const[busy,setBusy]=useState(false)
  const save=async()=>{
    if(!f.name.trim()){toast('Department name is required','warn');return}
    setBusy(true)
    try{
      if(dept?.id) await departmentAPI.update(dept.id,f); else await departmentAPI.create(f)
      toast(dept?.id?'Department updated':'Department added');closeModal();onDone()
    }catch(e){toast(e.response?.data?.message||'Could not save department','error')}
    finally{setBusy(false)}
  }
  return<div>
    <Fld label="Department Name *"><Inp value={f.name} onChange={v=>setF({...f,name:v})} placeholder="Engineering"/></Fld>
    <Fld label="Head of Department"><Inp value={f.headOfDept} onChange={v=>setF({...f,headOfDept:v})} placeholder="Priya Sharma"/></Fld>
    <Fld label="Description"><textarea value={f.description} onChange={e=>setF({...f,description:e.target.value})} rows={3} style={{...SEL,resize:'vertical'}}/></Fld>
    <FormFoot onSave={save} busy={busy} label={dept?.id?'Save Changes':'Add Department'}/>
  </div>
}
function Departments(){
  const[depts,setDepts]=useState([]);const[loading,setLoading]=useState(true)
  const colors=['#14B8A6','#3B82F6','#8B5CF6','#F97316','#EC4899','#22C55E','#F59E0B','#6366F1']
  const load=useCallback(()=>{setLoading(true);departmentAPI.getAll().then(r=>setDepts(r.data.data||[])).catch(()=>setDepts([])).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  return<div>
    <PH title="Departments" sub={`${depts.length} departments · live from database`}><Btn onClick={()=>openModal('Add Department',<DeptForm onDone={load}/>)}>+ Add Dept</Btn></PH>
    {loading?<Spin/>:<div style={{display:'grid',gridTemplateColumns:'repeat(auto-fill,minmax(200px,1fr))',gap:13}}>
      {depts.map((d,i)=><Card key={d.id} style={{padding:'18px 20px',borderTop:`3px solid ${colors[i%colors.length]}`}}>
        <div style={{fontWeight:800,fontSize:14,marginBottom:4}}>{d.name}</div>
        <div style={{fontSize:12,color:'#64748B',marginBottom:2}}>HoD: <span style={{color:'#1E293B',fontWeight:600}}>{d.headOfDept||'—'}</span></div>
        <div style={{fontSize:12,color:'#94A3B8',marginBottom:10}}>{d.description||'—'}</div>
        <div style={{display:'flex',gap:6}}>
          <BtnO onClick={()=>openModal('Edit Department',<DeptForm dept={d} onDone={load}/>)} style={{padding:'5px 11px',fontSize:12}}>Edit</BtnO>
          <BtnD onClick={async()=>{if(!window.confirm(`Delete ${d.name}? Employees in it will become unassigned.`))return;try{const r=await departmentAPI.delete(d.id);toast(`Deleted · ${r.data.data?.unassignedEmployees??0} employees unassigned`,'warn');load()}catch(e){toast(e.response?.data?.message||'Error','error')}}}>🗑 Delete</BtnD>
        </div>
      </Card>)}
    </div>}
  </div>
}

function HolForm({onDone}){
  const[f,setF]=useState({name:'',holidayDate:'',type:'National'})
  const[busy,setBusy]=useState(false)
  const save=async()=>{
    if(!f.name.trim()){toast('Holiday name is required','warn');return}
    if(!f.holidayDate){toast('Pick a date','warn');return}
    setBusy(true)
    try{await holidayAPI.create({...f,isActive:true});toast('Holiday added');closeModal();onDone()}
    catch(e){toast(e.response?.data?.message||'Could not save holiday','error')}
    finally{setBusy(false)}
  }
  return<div>
    <Fld label="Holiday Name *"><Inp value={f.name} onChange={v=>setF({...f,name:v})} placeholder="Diwali"/></Fld>
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
      <Fld label="Date *"><Inp type="date" value={f.holidayDate} onChange={v=>setF({...f,holidayDate:v})}/></Fld>
      <Fld label="Type"><Sel value={f.type} onChange={v=>setF({...f,type:v})}>{['National','Festival','Optional'].map(t=><option key={t} value={t}>{t}</option>)}</Sel></Fld>
    </div>
    <FormFoot onSave={save} busy={busy} label="Add Holiday"/>
  </div>
}
function Holidays(){
  const[hols,setHols]=useState([]);const[loading,setLoading]=useState(true)
  const load=useCallback(()=>{setLoading(true);holidayAPI.getAll().then(r=>setHols(r.data.data||[])).catch(()=>setHols([])).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  const tc={National:'#3B82F6',Festival:'#F59E0B',Optional:'#22C55E'}
  return<div>
    <PH title="Holidays" sub={`${hols.length} holidays · live from database`}><Btn onClick={()=>openModal('Add Holiday',<HolForm onDone={load}/>)}>+ Add Holiday</Btn></PH>
    {loading?<Spin/>:<div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:11}}>
      {hols.map(h=>{const c=tc[h.type]||'#94A3B8';const d=new Date(h.holidayDate);return<div key={h.id} style={{display:'flex',gap:13,alignItems:'center',padding:'13px 16px',border:'1px solid #E2E8F0',borderRadius:11,background:'#fff'}}>
        <div style={{width:50,height:50,borderRadius:10,background:c+'15',border:`1px solid ${c}30`,display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',flexShrink:0}}>
          <div style={{fontSize:19,fontWeight:900,color:c,lineHeight:1}}>{d.getDate()}</div>
          <div style={{fontSize:9,fontWeight:700,textTransform:'uppercase',color:c,marginTop:1}}>{['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][d.getMonth()]}</div>
        </div>
        <div style={{flex:1}}><div style={{fontWeight:700,marginBottom:5}}>{h.name}</div><Badge label={h.type}/></div>
        <BtnD onClick={async()=>{if(!window.confirm(`Remove ${h.name}?`))return;try{await holidayAPI.delete(h.id);toast('Removed','warn');load()}catch{toast('Error','error')}}}>🗑</BtnD>
      </div>})}
    </div>}
  </div>
}

const CAND_STAGES=['Applied','Screening','Technical_Round','HR_Round','Offered']
const CAND_STATUS=['In_Progress','Selected','Rejected','On_Hold']
function CandForm({onDone}){
  const[f,setF]=useState({fullName:'',email:'',phone:'',appliedFor:'',department:'',source:'LinkedIn',stage:'Applied',status:'In_Progress',notes:''})
  const[busy,setBusy]=useState(false)
  const save=async()=>{
    if(!f.fullName.trim()){toast('Candidate name is required','warn');return}
    if(!f.appliedFor.trim()){toast('Which role are they applying for?','warn');return}
    setBusy(true)
    try{await candidateAPI.create(f);toast('Candidate added');closeModal();onDone()}
    catch(e){toast(e.response?.data?.message||'Could not save candidate','error')}
    finally{setBusy(false)}
  }
  return<div>
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:'0 14px'}}>
      <Fld label="Full Name *"><Inp value={f.fullName} onChange={v=>setF({...f,fullName:v})}/></Fld>
      <Fld label="Applied For *"><Inp value={f.appliedFor} onChange={v=>setF({...f,appliedFor:v})} placeholder="Backend Developer"/></Fld>
      <Fld label="Email"><Inp type="email" value={f.email} onChange={v=>setF({...f,email:v})}/></Fld>
      <Fld label="Phone"><Inp value={f.phone} onChange={v=>setF({...f,phone:v})}/></Fld>
      <Fld label="Department"><Inp value={f.department} onChange={v=>setF({...f,department:v})} placeholder="Engineering"/></Fld>
      <Fld label="Source"><Inp value={f.source} onChange={v=>setF({...f,source:v})} placeholder="LinkedIn / Referral"/></Fld>
      <Fld label="Stage"><Sel value={f.stage} onChange={v=>setF({...f,stage:v})}>{CAND_STAGES.map(s=><option key={s} value={s}>{s.replace(/_/g,' ')}</option>)}</Sel></Fld>
      <Fld label="Status"><Sel value={f.status} onChange={v=>setF({...f,status:v})}>{CAND_STATUS.map(s=><option key={s} value={s}>{s.replace(/_/g,' ')}</option>)}</Sel></Fld>
    </div>
    <Fld label="Notes"><textarea value={f.notes} onChange={e=>setF({...f,notes:e.target.value})} rows={2} style={{...SEL,resize:'vertical'}}/></Fld>
    <FormFoot onSave={save} busy={busy} label="Add Candidate"/>
  </div>
}
function Recruitment(){
  const[cands,setCands]=useState([]);const[loading,setLoading]=useState(true)
  const load=useCallback(()=>{setLoading(true);candidateAPI.getAll().then(r=>setCands(r.data.data||[])).catch(()=>setCands([])).finally(()=>setLoading(false))},[])
  useEffect(()=>{load()},[load])
  const stages=CAND_STAGES
  const sc2=['#3B82F6','#8B5CF6','#F59E0B','#F97316','#22C55E']
  // Spread the whole candidate: the API overwrites stage, status and notes together,
  // so sending only the changed field would blank the other two.
  const move=async(c,patch)=>{try{await candidateAPI.update(c.id,{...c,...patch});toast('Candidate updated');load()}catch(e){toast(e.response?.data?.message||'Update failed','error')}}
  return<div>
    <PH title="Recruitment" sub={`${cands.length} candidates · live from database`}><Btn onClick={()=>openModal('Add Candidate',<CandForm onDone={load}/>)}>+ Add Candidate</Btn></PH>
    <Card style={{padding:'18px 20px',marginBottom:16}}>
      <div style={{fontWeight:700,marginBottom:12}}>Hiring Pipeline</div>
      <div style={{display:'flex',borderRadius:11,overflow:'hidden',border:'1px solid #E2E8F0'}}>
        {stages.map((s,i)=>{const cnt=cands.filter(c=>c.stage===s).length;return<div key={s} style={{flex:1,textAlign:'center',padding:'12px 8px',background:'#F8FAFC',borderRight:i<stages.length-1?'1px solid #E2E8F0':'none'}}>
          <div style={{fontSize:22,fontWeight:900,color:sc2[i]}}>{cnt}</div>
          <div style={{fontSize:10,color:'#64748B',marginTop:2}}>{s.replace(/_/g,' ')}</div>
        </div>})}
      </div>
    </Card>
    <Card>
      {loading?<Spin/>:<div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Candidate','Applied For','Source','Stage','Status','Actions'].map(h=><th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{cands.map(c=><tr key={c.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}} onMouseOver={e=>e.currentTarget.style.background='#F8FAFC'} onMouseOut={e=>e.currentTarget.style.background=''}>
          <td style={{padding:'11px 14px'}}><div style={{display:'flex',alignItems:'center',gap:9}}><Av name={c.fullName} size={28}/><span style={{fontWeight:600}}>{c.fullName}</span></div></td>
          <td style={{padding:'11px 14px'}}>{c.appliedFor}</td>
          <td style={{padding:'11px 14px'}}>{c.source}</td>
          <td style={{padding:'11px 14px'}}><select value={c.stage||'Applied'} onChange={e=>move(c,{stage:e.target.value})} style={{...SEL,padding:'4px 8px',fontSize:12,width:'auto'}}>{CAND_STAGES.map(s=><option key={s} value={s}>{s.replace(/_/g,' ')}</option>)}</select></td>
          <td style={{padding:'11px 14px'}}><select value={c.status||'In_Progress'} onChange={e=>move(c,{status:e.target.value})} style={{...SEL,padding:'4px 8px',fontSize:12,width:'auto'}}>{CAND_STATUS.map(s=><option key={s} value={s}>{s.replace(/_/g,' ')}</option>)}</select></td>
          <td style={{padding:'11px 14px'}}><BtnD onClick={async()=>{if(!window.confirm(`Remove ${c.fullName}?`))return;try{await candidateAPI.delete(c.id);toast('Removed','warn');load()}catch{toast('Error','error')}}}>🗑</BtnD></td>
        </tr>)}
        {!cands.length&&<tr><td colSpan={6} style={{padding:48,textAlign:'center',color:'#94A3B8'}}>No candidates</td></tr>}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

function PerfForm({emps,onDone}){
  const[f,setF]=useState({employeeId:'',reviewPeriod:'Q1 '+new Date().getFullYear(),goalsSet:'',goalsAchieved:'',rating:'',status:'Pending',overallFeedback:''})
  const[busy,setBusy]=useState(false)
  const submit=async()=>{
    if(!f.employeeId){toast('Select an employee','warn');return}
    setBusy(true)
    try{await performanceAPI.create(f);toast('Review saved');closeModal();onDone()}
    catch(e){toast(e.response?.data?.message||'Failed to save review','error')}
    finally{setBusy(false)}
  }
  const sel={border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none',fontFamily:'inherit',width:'100%',background:'#fff'}
  return<div>
    <Fld label="Employee *"><select value={f.employeeId} onChange={e=>setF({...f,employeeId:e.target.value})} style={sel}><option value="">— Select —</option>{emps.map(e=><option key={e.id} value={e.id}>{e.firstName} {e.lastName} ({e.empId})</option>)}</select></Fld>
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
      <Fld label="Review Period"><Inp value={f.reviewPeriod} onChange={v=>setF({...f,reviewPeriod:v})} placeholder="Q1 2026"/></Fld>
      <Fld label="Status"><select value={f.status} onChange={e=>setF({...f,status:e.target.value})} style={sel}>{['Pending','In_Progress','Completed'].map(s=><option key={s} value={s}>{s.replace('_',' ')}</option>)}</select></Fld>
      <Fld label="Goals Set"><Inp type="number" value={f.goalsSet} onChange={v=>setF({...f,goalsSet:v})} placeholder="8"/></Fld>
      <Fld label="Goals Achieved"><Inp type="number" value={f.goalsAchieved} onChange={v=>setF({...f,goalsAchieved:v})} placeholder="7"/></Fld>
    </div>
    <Fld label="Rating (0–5)"><Inp type="number" value={f.rating} onChange={v=>setF({...f,rating:v})} placeholder="4.5"/></Fld>
    <Fld label="Feedback"><textarea value={f.overallFeedback} onChange={e=>setF({...f,overallFeedback:e.target.value})} rows={3} style={{border:'1.5px solid #E2E8F0',borderRadius:9,padding:'9px 12px',fontSize:13,outline:'none',fontFamily:'inherit',width:'100%',resize:'vertical'}}/></Fld>
    <div style={{display:'flex',gap:9,marginTop:6}}><Btn onClick={submit} disabled={busy}>{busy?'Saving…':'Save Review'}</Btn><BtnO onClick={closeModal}>Cancel</BtnO></div>
  </div>
}
function Performance(){
  const[reviews,setReviews]=useState([]);const[stats,setStats]=useState(null);const[emps,setEmps]=useState([]);const[loading,setLoading]=useState(true)
  const load=useCallback(async()=>{
    setLoading(true)
    try{
      const[r,s,e]=await Promise.all([performanceAPI.getAll(),performanceAPI.stats(),employeeAPI.getAll()])
      setReviews(r.data.data||[]);setStats(s.data.data);setEmps(e.data.data||[])
    }catch(err){toast('Failed to load reviews','error')}
    finally{setLoading(false)}
  },[])
  useEffect(()=>{load()},[load])
  const del=async id=>{if(!window.confirm('Delete this review?'))return;try{await performanceAPI.delete(id);toast('Review deleted','warn');load()}catch(e){toast('Delete failed','error')}}
  return<div>
    <PH title="Performance Management" sub="Reviews and goals — saved to database">
      <Btn onClick={()=>openModal('New Performance Review',<PerfForm emps={emps} onDone={load}/>)}>+ Add Review</Btn>
    </PH>
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:16}}>
      <St label="Avg Rating" value={stats?`${stats.avgRating} / 5`:'—'} color="#F59E0B"/>
      <St label="Completed" value={stats?.completed??'—'} color="#22C55E"/>
      <St label="Pending" value={stats?.pending??'—'} color="#EF4444"/>
      <St label="Goal Achievement" value={stats?`${stats.goalAchievement}%`:'—'} color="#14B8A6"/>
    </div>
    <Card>
      {loading?<Spin/>:reviews.length===0?<div style={{padding:'40px 20px',textAlign:'center',color:'#94A3B8',fontSize:13}}>No reviews yet. Click "+ Add Review" to create the first one.</div>:
      <div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Employee','Period','Goals','Achieved','Rating','Status',''].map((h,i)=><th key={i} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{reviews.map(p=><tr key={p.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}} onMouseOver={e=>e.currentTarget.style.background='#F8FAFC'} onMouseOut={e=>e.currentTarget.style.background=''}>
          <td style={{padding:'11px 14px'}}><div style={{display:'flex',alignItems:'center',gap:9}}><Av name={p.employee?(p.employee.firstName+' '+p.employee.lastName):'?'} size={28}/><div><div style={{fontWeight:600}}>{p.employee?`${p.employee.firstName} ${p.employee.lastName}`:'—'}</div><div style={{fontSize:11,color:'#94A3B8'}}>{p.employee?.department?.name||''}</div></div></div></td>
          <td style={{padding:'11px 14px'}}>{p.reviewPeriod}</td>
          <td style={{padding:'11px 14px',fontWeight:700}}>{p.goalsSet??'—'}</td>
          <td style={{padding:'11px 14px',fontWeight:700,color:(p.goalsAchieved??0)>=(p.goalsSet??0)?'#22C55E':'#F59E0B'}}>{p.goalsAchieved??0}/{p.goalsSet??0}</td>
          <td style={{padding:'11px 14px'}}>{p.rating!=null?<><span style={{color:'#F59E0B',fontSize:14,letterSpacing:1}}>{'★'.repeat(Math.round(p.rating))}{'☆'.repeat(Math.max(0,5-Math.round(p.rating)))}</span><span style={{fontSize:11,color:'#94A3B8',marginLeft:4}}>{p.rating}</span></>:'—'}</td>
          <td style={{padding:'11px 14px'}}><Badge label={p.status}/></td>
          <td style={{padding:'11px 14px'}}><BtnD onClick={()=>del(p.id)}>Delete</BtnD></td>
        </tr>)}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

const REPORT_DEFS={
  employees:{title:'Employee Report',cols:['Emp ID','Name','Email','Department','Designation','Location','Join Date','Status','Basic Salary'],
    fetch:async()=>(await employeeAPI.getAll()).data.data.map(e=>[e.empId,`${e.firstName} ${e.lastName}`,e.email,e.department?.name||'',e.designation||'',e.location||'',e.joinDate||'',String(e.status||'').replace('_',' '),e.basicSalary||''])},
  // Column keys must match the Payroll entity (specialAllowance / totalDeduction /
  // netSalary). The old names exported three permanently empty columns, Net Pay
  // among them.
  payroll:{title:'Payroll Report',cols:['Employee','Month','Basic','HRA','Allowances','Gross','Deductions','Net Pay','Status'],
    fetch:async()=>(await payrollAPI.getAll()).data.data.map(p=>[p.employee?`${p.employee.firstName} ${p.employee.lastName}`:'',p.payMonth||'',p.basicSalary||'',p.hra||'',p.specialAllowance||'',p.grossSalary||'',p.totalDeduction||'',p.netSalary||'',p.status||''])},
  // Likewise the leave dates are fromDate/toDate, not startDate/endDate.
  leave:{title:'Leave Report',cols:['Employee','Type','From','To','Days','Reason','Status'],
    fetch:async()=>(await leaveAPI.getAll()).data.data.map(l=>[l.employee?`${l.employee.firstName} ${l.employee.lastName}`:'',String(l.leaveType||'').replace('_',' '),l.fromDate||'',l.toDate||'',l.totalDays||'',l.reason||'',l.status||''])},
  attendance:{title:'Attendance Report',cols:['Employee','Date','Status','Check In','Check Out','Hours'],
    fetch:async()=>(await attendanceAPI.getAll()).data.data.map(a=>[a.employee?`${a.employee.firstName} ${a.employee.lastName}`:'',a.attendanceDate||'',String(a.status||'').replace(/_/g,' '),a.checkIn||'',a.checkOut||'',a.workingHours||''])}
}
const csvCell=v=>{const s=String(v??'');return /[",\n]/.test(s)?'"'+s.replace(/"/g,'""')+'"':s}
const esc=s=>String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
const PRINT_CSS=`body{font-family:Segoe UI,Arial,sans-serif;margin:28px;color:#0F172A}
h1{font-size:19px;margin:0 0 2px}.sub{font-size:11px;color:#64748B;margin-bottom:16px}
table{width:100%;border-collapse:collapse;font-size:11px}
th{background:#F1F5F9;text-align:left;padding:7px 9px;border:1px solid #CBD5E1;font-size:10px;text-transform:uppercase;letter-spacing:.4px}
td{padding:6px 9px;border:1px solid #E2E8F0}tr:nth-child(even) td{background:#F8FAFC}
.foot{margin-top:14px;font-size:10px;color:#94A3B8}@media print{.noprint{display:none}}`
// Opens a blank window and prints into it — Electron's window-open handler allows
// about:blank for exactly this, and sends every other URL to the system browser.
function printDoc(title,bodyHtml){
  const w=window.open('','_blank','width=900,height=700')
  if(!w){toast('Popup blocked — allow popups to print or save as PDF','error');return false}
  w.document.write(`<!DOCTYPE html><html><head><title>${esc(title)}</title><style>${PRINT_CSS}</style></head><body>${bodyHtml}
    <div class="foot">© ${new Date().getFullYear()} AXVON HRMS</div>
    <script>window.onload=()=>setTimeout(()=>window.print(),300)<\/script></body></html>`)
  w.document.close()
  return true
}
async function exportReport(key,kind){
  const def=REPORT_DEFS[key];if(!def)return
  toast(kind==='csv'?'Preparing CSV…':'Preparing PDF…','info')
  let rows
  try{rows=await def.fetch()}catch(e){toast('Failed to fetch report data','error');return}
  if(!rows.length){toast('No data to export','warn');return}
  if(kind==='csv'){
    const csv=[def.cols.map(csvCell).join(','),...rows.map(r=>r.map(csvCell).join(','))].join('\r\n')
    const a=document.createElement('a')
    const url=URL.createObjectURL(new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}))
    a.href=url
    a.download=`axvon_${key}_report_${new Date().toISOString().slice(0,10)}.csv`
    // Must be in the DOM to click reliably, and the object URL has to outlive the
    // click \u2014 revoking it synchronously can cancel the download mid-flight.
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    setTimeout(()=>URL.revokeObjectURL(url),10000)
    toast(`${def.title} downloaded (${rows.length} rows)`)
  }else{
    const ok=printDoc(def.title,`
      <h1>AXVON HRMS — ${esc(def.title)}</h1><div class="sub">Generated ${new Date().toLocaleString()} · ${rows.length} records</div>
      <table><thead><tr>${def.cols.map(c=>`<th>${esc(c)}</th>`).join('')}</tr></thead>
      <tbody>${rows.map(r=>`<tr>${r.map(c=>`<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`)
    if(ok) toast('Use "Save as PDF" in the print dialog','info')
  }
}

// A payslip for one row — the "Slip" button used to only raise a toast.
function payslip(p){
  const name=p.employee?`${p.employee.firstName} ${p.employee.lastName}`:'—'
  const row=(l,v)=>`<tr><td>${esc(l)}</td><td style="text-align:right">${esc(inr(v))}</td></tr>`
  const ok=printDoc(`Payslip ${p.payMonth||''} — ${name}`,`
    <h1>AXVON HRMS — Payslip</h1>
    <div class="sub">${esc(name)} · ${esc(p.employee?.empId||'')} · ${esc(p.employee?.designation||'')} · Pay month ${esc(p.payMonth||'')}</div>
    <table>
      <thead><tr><th>Earnings</th><th style="text-align:right">Amount</th></tr></thead>
      <tbody>
        ${row('Basic Salary',p.basicSalary)}${row('HRA',p.hra)}${row('Special Allowance',p.specialAllowance)}${row('Performance Bonus',p.performanceBonus)}
        <tr><td><b>Gross Salary</b></td><td style="text-align:right"><b>${esc(inr(p.grossSalary))}</b></td></tr>
      </tbody>
      <thead><tr><th>Deductions</th><th style="text-align:right">Amount</th></tr></thead>
      <tbody>
        ${row('Provident Fund',p.pfDeduction)}${row('ESI',p.esiDeduction)}${row('Professional Tax',p.professionalTax)}${row('TDS',p.tdsDeduction)}
        <tr><td><b>Total Deductions</b></td><td style="text-align:right"><b>${esc(inr(p.totalDeduction))}</b></td></tr>
      </tbody>
      <tbody><tr><td style="font-size:13px"><b>Net Salary</b></td><td style="text-align:right;font-size:13px"><b>${esc(inr(p.netSalary))}</b></td></tr></tbody>
    </table>
    <div class="sub" style="margin-top:12px">Status: ${esc(p.status||'')} · This is a computer-generated payslip and needs no signature.</div>`)
  if(ok) toast('Use "Save as PDF" in the print dialog','info')
}
function Reports(){
  // /api/reports/summary already existed and was never called — the page claimed
  // "Analytics" but only offered downloads.
  const[sum,setSum]=useState(null);const[denied,setDenied]=useState(false)
  useEffect(()=>{reportsAPI.summary().then(r=>setSum(r.data.data)).catch(e=>{if(e.response?.status===403)setDenied(true)})},[])
  return<div>
    <PH title="Reports & Analytics" sub="Live snapshot · export any module as CSV or PDF"/>
    {denied&&<Card style={{padding:'14px 18px',marginBottom:14,fontSize:13,color:'#64748B'}}>The summary snapshot is available to Super Admin, HR Admin and Manager accounts. You can still export the reports below.</Card>}
    {sum&&<>
      <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:14}}>
        <St label="Headcount" value={sum.headcount?.total??'—'} sub={`${sum.headcount?.active??0} active`} color="#14B8A6"/>
        <St label={`Present (${sum.attendance?.date||''})`} value={`${sum.attendance?.presentRate??0}%`} sub={`${sum.attendance?.present??0} of ${sum.attendance?.marked??0} marked`} color="#22C55E"/>
        <St label="Leave Pending" value={sum.leave?.pending??'—'} sub={`${sum.leave?.approved??0} approved · ${sum.leave?.rejected??0} rejected`} color="#F59E0B"/>
        <St label={`Payroll ${sum.payroll?.month||''}`} value={inr(sum.payroll?.netTotal)} sub={`${sum.payroll?.slips??0} payslips · gross ${inr(sum.payroll?.grossTotal)}`} color="#8B5CF6"/>
      </div>
      <Card style={{padding:'18px 20px',marginBottom:16}}>
        <div style={{fontWeight:700,marginBottom:12}}>Headcount by Department</div>
        {(sum.byDepartment||[]).length===0?<div style={{fontSize:13,color:'#94A3B8'}}>No departments configured.</div>:
        <div style={{display:'flex',flexDirection:'column',gap:7}}>
          {(sum.byDepartment||[]).map(d=>{const max=Math.max(...(sum.byDepartment||[]).map(x=>x.count),1);return<div key={d.name} style={{display:'flex',alignItems:'center',gap:10}}>
            <div style={{width:130,fontSize:12,color:'#64748B',textAlign:'right',flexShrink:0,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{d.name}</div>
            <div style={{flex:1,height:18,background:'#F1F5F9',borderRadius:5,overflow:'hidden'}}><div style={{width:`${Math.round(d.count*100/max)}%`,height:'100%',background:'#14B8A6',borderRadius:5,minWidth:d.count?4:0}}/></div>
            <div style={{width:26,fontSize:12,fontWeight:700}}>{d.count}</div>
          </div>})}
        </div>}
        <div style={{fontSize:11,color:'#94A3B8',marginTop:12}}>Generated {sum.generatedAt?new Date(sum.generatedAt).toLocaleString():''}</div>
      </Card>
    </>}
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:14}}>
      {[['Employee Report','Full employee list with salaries','employees'],['Payroll Report','Monthly salary breakdown','payroll'],['Leave Report','All leave requests','leave'],['Attendance Report','Daily records','attendance']].map(([t,s,p])=><Card key={t} style={{padding:'20px 22px',cursor:'pointer'}} onClick={()=>window.navTo&&window.navTo(p)}>
        <div style={{fontWeight:700,fontSize:14,marginBottom:4}}>{t}</div>
        <div style={{fontSize:13,color:'#64748B',marginBottom:14}}>{s}</div>
        <div style={{display:'flex',gap:7}}>
          <button style={{background:'rgba(34,197,94,.1)',border:'1px solid rgba(34,197,94,.25)',borderRadius:7,padding:'5px 11px',color:'#15803D',fontSize:11,fontWeight:600,cursor:'pointer',fontFamily:'inherit'}} onClick={e=>{e.stopPropagation();exportReport(p,'csv')}}>⬇ CSV</button>
          <button style={{background:'rgba(239,68,68,.1)',border:'1px solid rgba(239,68,68,.25)',borderRadius:7,padding:'5px 11px',color:'#B91C1C',fontSize:11,fontWeight:600,cursor:'pointer',fontFamily:'inherit'}} onClick={e=>{e.stopPropagation();exportReport(p,'pdf')}}>⬇ PDF</button>
        </div>
      </Card>)}
    </div>
  </div>
}

// ── Bulk import ───────────────────────────────────────────────────────
// The importable columns now cover everything the API accepts, so one spreadsheet
// can carry salary, join date and department — not just names.
const IMPORT_MODS={
  employees:{fields:['empId','firstName','lastName','email','phone','designation','department','location','joinDate','basicSalary','status','gender','manager'],
    req:['firstName','lastName','email'],
    sample:['AXV101','Ravi','Kumar','ravi.kumar@axvon.io','9876543220','Senior Developer','Engineering','Bengaluru','2024-06-01','45000','Active','Male','Priya Sharma']},
  holidays:{fields:['name','holidayDate','type'],req:['name','holidayDate'],
    sample:['Dussehra','2025-10-02','Festival']},
  candidates:{fields:['fullName','email','phone','appliedFor','department','source','stage','status'],req:['fullName','appliedFor'],
    sample:['Vikash Tiwari','vikash@mail.com','9876500000','Backend Developer','Engineering','LinkedIn','Applied','In_Progress']}
}
const flabel=f=>f.replace(/([A-Z])/g,' $1').replace(/^./,c=>c.toUpperCase())
const norm=s=>String(s??'').toLowerCase().replace(/[^a-z0-9]/g,'')
// Real spreadsheets never use our exact field names — "DOJ", "Joining Date" and
// "joinDate" all mean the same column. Match on a normalised header instead of
// trusting column order.
const ALIAS={employeeid:['empId'],empid:['empId'],staffid:['empId'],idno:['empId'],
  givenname:['firstName'],surname:['lastName'],familyname:['lastName'],
  emailaddress:['email'],mailid:['email'],mail:['email'],
  mobile:['phone'],mobilenumber:['phone'],contact:['phone'],contactnumber:['phone'],phonenumber:['phone'],
  role:['designation'],jobtitle:['designation'],title:['designation'],position:['designation','appliedFor'],
  dept:['department'],city:['location'],office:['location'],branch:['location'],
  joiningdate:['joinDate'],dateofjoining:['joinDate'],doj:['joinDate'],
  salary:['basicSalary'],basic:['basicSalary'],basicpay:['basicSalary'],monthlysalary:['basicSalary'],
  reportingmanager:['manager'],managername:['manager'],reportsto:['manager'],
  date:['holidayDate'],holiday:['name'],occasion:['name'],
  candidatename:['fullName'],appliedrole:['appliedFor'],appliedposition:['appliedFor'],vacancy:['appliedFor']}
const fieldFor=(cell,fields)=>{
  const n=norm(cell)
  const direct=fields.find(f=>norm(f)===n)
  if(direct) return direct
  return (ALIAS[n]||[]).find(f=>fields.includes(f))||null
}
// Excel exports quote any cell containing a comma, so a naive split on "," tears
// "Kumar, Ravi" in half.
const splitRow=(line,sep)=>{
  const out=[];let cur='',q=false
  for(let i=0;i<line.length;i++){
    const c=line[i]
    if(q){ if(c==='"'){ if(line[i+1]==='"'){cur+='"';i++} else q=false } else cur+=c }
    else if(c==='"') q=true
    else if(c===sep){ out.push(cur);cur='' }
    else cur+=c
  }
  out.push(cur)
  return out.map(s=>s.trim())
}
const textToGrid=t=>{
  const lines=String(t||'').replace(/\r/g,'').split('\n').filter(l=>l.trim())
  const sep=lines.some(l=>l.includes('\t'))?'\t':','
  return lines.map(l=>splitRow(l,sep))
}
const pad2=n=>String(n).padStart(2,'0')
// null = cell was empty, undefined = present but unreadable (becomes a warning).
const toISODate=v=>{
  if(v instanceof Date&&!isNaN(v)) return `${v.getUTCFullYear()}-${pad2(v.getUTCMonth()+1)}-${pad2(v.getUTCDate())}`
  const s=String(v??'').trim()
  if(!s) return null
  if(/^\d{4}-\d{2}-\d{2}$/.test(s)) return s
  const dmy=s.match(/^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$/)
  if(dmy) return `${dmy[3]}-${pad2(dmy[2])}-${pad2(dmy[1])}`
  const d=new Date(s)
  return isNaN(d)?undefined:`${d.getFullYear()}-${pad2(d.getMonth()+1)}-${pad2(d.getDate())}`
}
const toNum=v=>{
  const s=String(v??'').replace(/[₹,\s]/g,'')
  if(!s) return null
  const n=Number(s)
  return isNaN(n)?undefined:n
}
const pickEnum=(v,opts)=>{
  const n=norm(v)
  if(!n) return null
  return opts.find(o=>norm(o)===n)
}
const EMP_STATUS=['Active','On_Leave','Inactive','Terminated']
function validateRow(raw,mod,deptByName){
  const errs=[],warns=[],out={}
  const copy=ks=>ks.forEach(k=>{const v=String(raw[k]??'').trim();if(v)out[k]=v})
  if(mod==='employees'){
    copy(['empId','firstName','lastName','email','phone','designation','location','manager'])
    if(!out.firstName||!out.lastName||!out.email) errs.push('first name, last name and email are all required')
    else if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(out.email)) errs.push(`"${out.email}" is not a valid email address`)
    if(String(raw.joinDate??'').trim()||raw.joinDate instanceof Date){
      const d=toISODate(raw.joinDate)
      if(d===undefined) warns.push(`join date "${raw.joinDate}" not understood — left blank`); else if(d) out.joinDate=d
    }
    if(String(raw.basicSalary??'').trim()){
      const n=toNum(raw.basicSalary)
      if(n===undefined) warns.push(`salary "${raw.basicSalary}" is not a number — left blank`); else if(n!==null) out.basicSalary=n
    }
    if(String(raw.status??'').trim()){
      const s=pickEnum(raw.status,EMP_STATUS)
      if(!s) warns.push(`status "${raw.status}" unknown — will default to Active`); else out.status=s
    }
    if(String(raw.gender??'').trim()){
      const g=pickEnum(raw.gender,['Male','Female','Other'])
      if(!g) warns.push(`gender "${raw.gender}" unknown — left blank`); else out.gender=g
    }
    if(String(raw.department??'').trim()){
      const id=deptByName[norm(raw.department)]
      if(!id) warns.push(`department "${raw.department}" does not exist — imported unassigned`); else out.departmentId=id
    }
  }else if(mod==='holidays'){
    copy(['name'])
    if(!out.name) errs.push('holiday name is required')
    const d=toISODate(raw.holidayDate)
    if(d===undefined) errs.push(`date "${raw.holidayDate}" not understood`)
    else if(!d) errs.push('date is required')
    else out.holidayDate=d
    if(String(raw.type??'').trim()){
      const t=pickEnum(raw.type,['National','Festival','Optional'])
      if(!t) warns.push(`type "${raw.type}" unknown — left blank`); else out.type=t
    }
    out.isActive=true
  }else{
    copy(['fullName','email','phone','appliedFor','department','source'])
    if(!out.fullName) errs.push('candidate name is required')
    if(!out.appliedFor) errs.push('applied-for role is required')
    if(String(raw.stage??'').trim()) out.stage=String(raw.stage).trim().replace(/\s+/g,'_')
    if(String(raw.status??'').trim()) out.status=String(raw.status).trim().replace(/\s+/g,'_')
  }
  return{raw,out,errs,warns}
}
function BulkImport(){
  const[mod,setMod]=useState('employees');const[text,setText]=useState('');const[rows,setRows]=useState([]);const[importing,setImporting]=useState(false);const[done,setDone]=useState(null);const[depts,setDepts]=useState([])
  useEffect(()=>{departmentAPI.getAll().then(r=>setDepts(r.data.data||[])).catch(()=>setDepts([]))},[])
  const deptByName=Object.fromEntries(depts.map(d=>[norm(d.name),d.id]))
  const m=IMPORT_MODS[mod]||IMPORT_MODS.employees
  const gridToRows=grid=>{
    if(!grid.length) return []
    const mapped=grid[0].map(c=>fieldFor(c,m.fields))
    const hasHeader=mapped.filter(Boolean).length>=2
    const header=hasHeader?mapped:null
    const body=(hasHeader?grid.slice(1):grid).filter(cells=>cells.some(c=>String(c??'').trim()))
    const out=body.map(cells=>{
      const raw={}
      cells.forEach((c,i)=>{const f=header?header[i]:m.fields[i];if(f)raw[f]=typeof c==='string'?c.trim():c})
      return validateRow(raw,mod,deptByName)
    })
    // A repeated email fails at the database anyway — flag it here so the row can be
    // fixed before the import instead of showing up as a server-side failure.
    const seen={}
    out.forEach(r=>{
      const key=norm(r.out.email)
      if(!key) return
      if(seen[key]) r.errs.push(`duplicate email in this file (also row ${seen[key]})`)
      else seen[key]=out.indexOf(r)+1
    })
    return out
  }
  const parse=()=>{
    if(!text.trim()){toast('Paste data or upload a file first','error');return}
    const parsed=gridToRows(textToGrid(text))
    if(!parsed.length){toast('No data rows found','warn');return}
    setRows(parsed);setDone(null)
  }
  const loadFile=async f=>{
    if(!f) return
    try{
      if(/\.xlsx$/i.test(f.name)){
        // read-excel-file 9.x resolves to [{sheet,data}]; older versions resolved to
        // the rows themselves. Accept either, and skip leading empty sheets.
        const book=await readXlsxFile(f)
        const grid=Array.isArray(book[0])?book:(book.find(s=>s?.data?.length)?.data||[])
        if(!grid.length){toast('That workbook has no data rows','warn');return}
        const parsed=gridToRows(grid)
        setText(grid.map(r=>r.map(c=>c instanceof Date?toISODate(c):String(c??'')).join('\t')).join('\n'))
        setRows(parsed);setDone(null)
        toast(`${f.name} — ${parsed.length} rows read`,'info')
      }else if(/\.xls$/i.test(f.name)){
        toast('Old .xls format is not supported — open it in Excel and Save As .xlsx or CSV','warn')
      }else{
        const txt=await f.text()
        setText(txt)
        const parsed=gridToRows(textToGrid(txt))
        setRows(parsed);setDone(null)
        toast(`${f.name} — ${parsed.length} rows read`,'info')
      }
    }catch(err){toast('Could not read that file — is it a valid spreadsheet?','error')}
  }
  const doImport=async()=>{
    const valid=rows.filter(r=>!r.errs.length)
    if(!valid.length){toast('No valid rows to import','error');return}
    setImporting(true)
    try{
      let result
      if(mod==='employees'){
        // The server re-checks every row; report what it actually saved rather than
        // assuming the whole batch went in.
        const r=await employeeAPI.bulkImport(valid.map(v=>v.out))
        const d=r.data.data||{}
        result={imported:d.imported??0,failed:d.failed??0,errors:d.errors||[]}
      }else{
        const calls=valid.map(v=>mod==='holidays'?holidayAPI.create(v.out):candidateAPI.create(v.out))
        const settled=await Promise.allSettled(calls)
        const failures=settled.filter(s=>s.status==='rejected').map(s=>s.reason?.response?.data?.message||'could not be saved')
        result={imported:settled.length-failures.length,failed:failures.length,errors:failures}
      }
      setDone(result)
      if(result.failed) toast(`${result.imported} imported, ${result.failed} rejected`,'warn')
      else{toast(`${result.imported} ${mod} imported!`);setRows([]);setText('')}
    }catch(e){toast(e.response?.data?.message||'Import failed','error')}
    finally{setImporting(false)}
  }
  const ok=rows.filter(r=>!r.errs.length).length
  const errCount=rows.filter(r=>r.errs.length).length
  const warnCount=rows.filter(r=>!r.errs.length&&r.warns.length).length
  return<div>
    <PH title="Bulk Import" sub="Upload an Excel (.xlsx) or CSV file, or paste rows straight from a spreadsheet"/>
    <div style={{display:'flex',gap:7,marginBottom:16,flexWrap:'wrap'}}>
      {Object.keys(IMPORT_MODS).map(k=><button key={k} onClick={()=>{setMod(k);setRows([]);setDone(null);setText('')}} style={{padding:'6px 14px',borderRadius:20,fontSize:12,fontWeight:600,cursor:'pointer',border:'1px solid',background:mod===k?'#0F172A':'transparent',borderColor:mod===k?'#0F172A':'#E2E8F0',color:mod===k?'#fff':'#64748B',fontFamily:'inherit',textTransform:'capitalize'}}>{k}</button>)}
    </div>
    <Card style={{padding:'18px 20px',marginBottom:14}}>
      <div style={{fontWeight:700,marginBottom:5}}>Columns <span style={{fontWeight:400,color:'#64748B',fontSize:12.5}}>— a header row is matched by name, so column order does not matter</span></div>
      <div style={{display:'flex',flexWrap:'wrap',gap:5,marginBottom:8}}>
        {m.fields.map(f=><span key={f} style={{fontSize:11,fontWeight:600,padding:'3px 9px',borderRadius:20,background:m.req.includes(f)?'rgba(20,184,166,.12)':'#F1F5F9',color:m.req.includes(f)?'#0F766E':'#64748B'}}>{flabel(f)}{m.req.includes(f)?' *':''}</span>)}
      </div>
      <div style={{fontSize:12,color:'#94A3B8',marginBottom:10}}>* required · dates as YYYY-MM-DD or DD/MM/YYYY · PDF cannot be imported — export the table to Excel or CSV first</div>
      <div style={{display:'flex',gap:9,marginBottom:10,flexWrap:'wrap'}}>
        <label style={{background:'rgba(59,130,246,.1)',border:'1px solid rgba(59,130,246,.25)',borderRadius:8,padding:'6px 13px',cursor:'pointer',fontSize:12,fontWeight:600,fontFamily:'inherit',color:'#1D4ED8'}}>⬆ Upload Excel or CSV
          <input type="file" accept=".xlsx,.csv,.tsv,.txt" style={{display:'none'}} onChange={e=>{const f=e.target.files[0];loadFile(f);e.target.value=''}}/>
        </label>
        <button onClick={()=>{setText([m.fields.join('\t'),m.sample.join('\t')].join('\n'));setRows([]);setDone(null)}} style={{background:'#F8FAFC',border:'1px solid #E2E8F0',borderRadius:8,padding:'6px 13px',cursor:'pointer',fontSize:12,fontWeight:600,fontFamily:'inherit'}}>📋 Load sample</button>
        <button onClick={()=>{
          const csv=[m.fields.map(flabel).map(csvCell).join(','),m.sample.map(csvCell).join(',')].join('\r\n')
          const a=document.createElement('a')
          const url=URL.createObjectURL(new Blob(['﻿'+csv],{type:'text/csv;charset=utf-8'}))
          a.href=url;a.download=`axvon_${mod}_template.csv`
          document.body.appendChild(a);a.click();document.body.removeChild(a)
          setTimeout(()=>URL.revokeObjectURL(url),10000)
        }} style={{background:'rgba(34,197,94,.1)',border:'1px solid rgba(34,197,94,.25)',borderRadius:8,padding:'6px 13px',cursor:'pointer',fontSize:12,fontWeight:600,fontFamily:'inherit',color:'#15803D'}}>⬇ Template</button>
      </div>
      <textarea value={text} onChange={e=>setText(e.target.value)} placeholder="Paste rows copied from Excel or Google Sheets…" style={{width:'100%',minHeight:100,border:'1.5px solid #E2E8F0',borderRadius:9,padding:'10px 12px',fontSize:12,fontFamily:'monospace',outline:'none',resize:'vertical',lineHeight:1.5}}/>
      <div style={{display:'flex',gap:9,marginTop:10}}><Btn onClick={parse}>👁 Preview & Validate</Btn><BtnO onClick={()=>{setText('');setRows([]);setDone(null)}}>Clear</BtnO></div>
    </Card>
    {rows.length>0&&<Card style={{marginBottom:14}}>
      <div style={{padding:'12px 16px',borderBottom:'1px solid #E2E8F0',display:'flex',justifyContent:'space-between',alignItems:'center',flexWrap:'wrap',gap:9}}>
        <div style={{fontWeight:700}}>{rows.length} rows detected</div>
        <div style={{display:'flex',gap:7}}>
          <span style={{padding:'3px 10px',borderRadius:20,fontSize:11,fontWeight:600,background:'rgba(34,197,94,.12)',color:'#15803D'}}>✓ {ok} valid</span>
          {warnCount>0&&<span style={{padding:'3px 10px',borderRadius:20,fontSize:11,fontWeight:600,background:'rgba(245,158,11,.12)',color:'#B45309'}}>⚠ {warnCount} with warnings</span>}
          {errCount>0&&<span style={{padding:'3px 10px',borderRadius:20,fontSize:11,fontWeight:600,background:'rgba(239,68,68,.1)',color:'#B91C1C'}}>✕ {errCount} errors</span>}
        </div>
      </div>
      <div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:12}}>
        <thead><tr style={{background:'#F8FAFC'}}><th style={{padding:'8px 12px',textAlign:'left',fontSize:10,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',borderBottom:'1px solid #E2E8F0'}}>#</th>{m.fields.map(f=><th key={f} style={{padding:'8px 12px',textAlign:'left',fontSize:10,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{flabel(f)}</th>)}<th style={{padding:'8px 12px',textAlign:'left',fontSize:10,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',borderBottom:'1px solid #E2E8F0'}}>Status</th></tr></thead>
        <tbody>{rows.map((r,i)=><tr key={i} style={{borderBottom:'1px solid rgba(226,232,240,.6)',background:r.errs.length?'rgba(239,68,68,.03)':r.warns.length?'rgba(245,158,11,.04)':''}}>
          <td style={{padding:'8px 12px',color:'#94A3B8',fontWeight:600,fontSize:11}}>{i+1}</td>
          {m.fields.map(f=>{const v=r.out[f]??r.raw[f];return<td key={f} style={{padding:'6px 10px',color:r.errs.length?'#DC2626':'#1E293B',whiteSpace:'nowrap'}}>{v instanceof Date?toISODate(v):(v===''||v==null?<span style={{color:'#CBD5E1',fontStyle:'italic'}}>—</span>:String(v))}</td>})}
          <td style={{padding:'8px 10px',minWidth:170}}>
            {r.errs.length?<span title={r.errs.join(' · ')} style={{background:'rgba(239,68,68,.1)',color:'#DC2626',padding:'2px 8px',borderRadius:20,fontSize:11,fontWeight:600}}>✕ {r.errs[0]}</span>
             :r.warns.length?<span title={r.warns.join(' · ')} style={{background:'rgba(245,158,11,.12)',color:'#B45309',padding:'2px 8px',borderRadius:20,fontSize:11,fontWeight:600}}>⚠ {r.warns[0]}</span>
             :<span style={{background:'rgba(34,197,94,.1)',color:'#15803D',padding:'2px 8px',borderRadius:20,fontSize:11,fontWeight:600}}>✓ Ready</span>}
          </td>
        </tr>)}
        </tbody>
      </table></div>
      <div style={{padding:'12px 16px',borderTop:'1px solid #E2E8F0',display:'flex',gap:9,flexWrap:'wrap'}}>
        <Btn onClick={doImport} disabled={importing||!ok}>{importing?'Importing…':`⬆ Import ${ok} valid row${ok===1?'':'s'}`}</Btn>
        {errCount>0&&<BtnO onClick={()=>setRows(rs=>rs.filter(r=>!r.errs.length))}>Remove {errCount} error row{errCount===1?'':'s'}</BtnO>}
      </div>
    </Card>}
    {done&&<div style={{background:done.failed?'rgba(245,158,11,.07)':'rgba(34,197,94,.07)',border:`1px solid ${done.failed?'rgba(245,158,11,.35)':'rgba(34,197,94,.3)'}`,borderRadius:12,padding:'20px 22px',textAlign:'center'}}>
      <div style={{fontSize:32,marginBottom:8}}>{done.failed?'⚠':'🎉'}</div>
      <div style={{fontWeight:700,fontSize:16,marginBottom:4}}>{done.imported} {mod} imported to database{done.failed?` · ${done.failed} rejected`:''}</div>
      {done.failed>0
        ?<div style={{fontSize:12.5,color:'#92400E',textAlign:'left',maxWidth:520,margin:'8px auto 0'}}>{[...new Set(done.errors)].slice(0,6).map((e,i)=><div key={i}>· {e}</div>)}</div>
        :<div style={{fontSize:13,color:'#64748B'}}>Records saved. Open the module to view them.</div>}
    </div>}
  </div>
}

function Admin(){
  const[stats,setStats]=useState(null);const[deleting,setDeleting]=useState('')
  useEffect(()=>{adminAPI.stats().then(r=>setStats(r.data.data)).catch(()=>{})},[])
  const mods=[{key:'employees',l:'Employees',i:'👥',c:'#14B8A6',fn:adminAPI.deleteEmployees},{key:'leave',l:'Leave Requests',i:'🌴',c:'#F59E0B',fn:adminAPI.deleteLeave},{key:'payroll',l:'Payroll',i:'💰',c:'#8B5CF6',fn:adminAPI.deletePayroll},{key:'candidates',l:'Candidates',i:'🎯',c:'#F97316',fn:adminAPI.deleteCandidates},{key:'holidays',l:'Holidays',i:'📅',c:'#22C55E',fn:adminAPI.deleteHolidays}]
  const del=async(m)=>{
    const phrase=window.prompt(`Type DELETE ${m.l.toUpperCase()} to confirm:`)
    if(phrase!==`DELETE ${m.l.toUpperCase()}`){toast('Cancelled','warn');return}
    setDeleting(m.key)
    try{await m.fn();toast(`All ${m.l} deleted`,'warn');const s=await adminAPI.stats();setStats(s.data.data)}
    catch(e){toast(e.response?.data?.message||'Error','error')}
    finally{setDeleting('')}
  }
  const delAll=async()=>{
    const phrase=window.prompt('Type DELETE_ALL_AXVON_DATA to confirm:')
    if(phrase!=='DELETE_ALL_AXVON_DATA'){toast('Cancelled','warn');return}
    if(!confirm('FINAL WARNING: Delete ALL data?'))return
    setDeleting('all')
    try{await adminAPI.deleteAll();toast('Full reset complete','warn')}
    catch(e){toast(e.response?.data?.message||'Error','error')}
    finally{setDeleting('')}
  }
  return<div>
    <PH title="Admin & Data Management" sub="Delete module data · Super Admin only"/>
    <div style={{background:'rgba(239,68,68,.06)',border:'1px solid rgba(239,68,68,.25)',borderRadius:12,padding:'13px 16px',marginBottom:20,display:'flex',gap:10,alignItems:'flex-start'}}>
      <span style={{fontSize:18,flexShrink:0}}>⚠</span>
      <span style={{fontSize:13,color:'#B91C1C',lineHeight:1.6}}>Danger zone — these actions permanently delete data from the database. Cannot be undone. A confirmation phrase is required.</span>
    </div>
    <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:13,marginBottom:16}}>
      {mods.map(m=><Card key={m.key} style={{padding:'18px 20px',borderLeft:`4px solid ${m.c}`}}>
        <div style={{display:'flex',alignItems:'flex-start',gap:10,marginBottom:10}}>
          <span style={{fontSize:20}}>{m.i}</span>
          <div><div style={{fontWeight:700,fontSize:14,marginBottom:2}}>{m.l}</div><div style={{fontSize:13,fontWeight:700,color:m.c}}>{stats?.[m.key]??'…'} records in database</div></div>
        </div>
        <BtnD onClick={()=>del(m)}>{deleting===m.key?'Deleting…':`🗑 Delete All ${m.l}`}</BtnD>
      </Card>)}
    </div>
    <Card style={{padding:'20px 22px',borderTop:'4px solid #EF4444',background:'rgba(239,68,68,.02)'}}>
      <div style={{fontWeight:800,fontSize:15,color:'#DC2626',marginBottom:5}}>☢ Full System Reset</div>
      <div style={{fontSize:13,color:'#64748B',marginBottom:16}}>Deletes ALL data across every module. Cannot be undone.</div>
      <button onClick={delAll} disabled={deleting==='all'} style={{background:'#DC2626',border:'none',borderRadius:9,padding:'10px 20px',color:'#fff',fontSize:13,fontWeight:700,cursor:'pointer',fontFamily:'inherit',opacity:deleting==='all'?.6:1}}>
        {deleting==='all'?'Resetting…':'☢ Delete Everything — Full Reset'}
      </button>
    </Card>
  </div>
}

function About(){
  return<div style={{maxWidth:660,margin:'0 auto'}}>
    <div style={{background:'#0F172A',borderRadius:18,padding:44,textAlign:'center',marginBottom:20,position:'relative',overflow:'hidden'}}>
      <div style={{position:'absolute',width:300,height:300,borderRadius:'50%',background:'rgba(20,184,166,.12)',top:'-30%',left:'-10%',filter:'blur(60px)'}}/>
      <div style={{position:'relative',display:'flex',justifyContent:'center',alignItems:'center',gap:15,marginBottom:18}}><Logo size={54}/><div style={{textAlign:'left'}}><div style={{fontSize:24,fontWeight:900,color:'#fff',letterSpacing:3}}>AXVON</div><div style={{fontSize:9,color:'#475569',marginTop:3}}>HRMS PLATFORM v2.0 · FULL STACK</div></div></div>
      <div style={{color:'#94A3B8',fontSize:14,lineHeight:1.7,maxWidth:420,margin:'0 auto'}}>Streamlining <b style={{color:'#14B8A6'}}>Attendance</b>, <b style={{color:'#3B82F6'}}>Payroll</b>, <b style={{color:'#8B5CF6'}}>Leave</b> & <b style={{color:'#F59E0B'}}>Workforce Operations</b> in One Platform.</div>
    </div>
    <div style={{background:'linear-gradient(135deg,rgba(20,184,166,.1),rgba(59,130,246,.07))',border:'1.5px solid rgba(20,184,166,.3)',borderRadius:18,padding:'38px 44px',textAlign:'center'}}>
      <div style={{fontSize:11,fontWeight:700,color:'#14B8A6',letterSpacing:2,textTransform:'uppercase',marginBottom:16}}>Crafted with ❤ by</div>
      <div style={{width:72,height:72,borderRadius:'50%',background:'linear-gradient(135deg,#14B8A6,#3B82F6)',display:'flex',alignItems:'center',justifyContent:'center',fontSize:28,fontWeight:900,color:'#fff',margin:'0 auto 15px',boxShadow:'0 8px 24px rgba(20,184,166,.35)'}}>A</div>
      <div style={{fontSize:26,fontWeight:900,letterSpacing:'-.3px',marginBottom:5}}>ATHUL KV</div>
      <div style={{fontSize:14,color:'#64748B',marginBottom:18}}>Full-Stack Developer · HRMS Architect · UI/UX Designer</div>
      <div style={{display:'inline-flex',alignItems:'center',gap:9,padding:'9px 22px',background:'#fff',borderRadius:20,border:'1px solid #E2E8F0'}}><span style={{color:'#F59E0B',fontSize:15}}>★</span><span style={{fontSize:13,fontWeight:700}}>AXVON v2.0 · React + Spring Boot + H2 · Full Stack</span></div>
      <div style={{marginTop:14,fontSize:12,color:'#94A3B8'}}>© 2025 AXVON · kvathul27@gmail.com · github.com/kvathul7</div>
    </div>
  </div>
}

const ATT_OPTS=[['Present','#22C55E'],['Work_From_Home','#3B82F6'],['Half_Day','#F59E0B'],['Leave','#8B5CF6'],['Absent','#EF4444']]
function Attendance(){
  const today=new Date().toISOString().slice(0,10)
  const[date,setDate]=useState(today)
  const[emps,setEmps]=useState([]);const[marks,setMarks]=useState({});const[loading,setLoading]=useState(true);const[saving,setSaving]=useState(false)
  const load=useCallback(async(d)=>{
    setLoading(true)
    try{
      const[er,ar]=await Promise.all([employeeAPI.getAll(),attendanceAPI.byDate(d)])
      setEmps(er.data.data||[])
      // Keep the attendance row id so a saved mark can be cleared again.
      const m={};(ar.data.data||[]).forEach(a=>{if(a.employee)m[a.employee.id]={id:a.id,status:a.status,checkIn:a.checkIn||'',checkOut:a.checkOut||''}})
      setMarks(m)
    }catch(e){toast('Failed to load attendance','error')}
    finally{setLoading(false)}
  },[])
  useEffect(()=>{load(date)},[date,load])
  const setStatus=(id,status)=>setMarks(p=>({...p,[id]:{...(p[id]||{}),status}}))
  const setTime=(id,k,v)=>setMarks(p=>({...p,[id]:{...(p[id]||{status:'Present'}),[k]:v}}))
  const markAll=s=>{const m={};emps.forEach(e=>{m[e.id]={...(marks[e.id]||{}),status:s}});setMarks(m)}
  const save=async()=>{
    const records=Object.entries(marks).filter(([,v])=>v.status).map(([id,v])=>({employeeId:Number(id),status:v.status,checkIn:v.checkIn||null,checkOut:v.checkOut||null}))
    if(!records.length){toast('Mark at least one employee','warn');return}
    setSaving(true)
    try{const r=await attendanceAPI.mark(date,records);toast(`Attendance saved for ${r.data.data.saved} employees`);load(date)}
    catch(e){toast(e.response?.data?.message||'Save failed','error')}
    finally{setSaving(false)}
  }
  const cnt=s=>Object.values(marks).filter(m=>m.status===s).length
  // Saved rows are deleted server-side; unsaved marks just drop out of local state.
  const clearMark=async(empId)=>{
    const m=marks[empId]
    if(!m||!m.status){toast('Nothing to clear','warn');return}
    if(!window.confirm('Clear this attendance record?'))return
    if(!m.id){setMarks(p=>{const n={...p};delete n[empId];return n});toast('Mark cleared','warn');return}
    try{await attendanceAPI.delete(m.id);toast('Attendance record deleted','warn');load(date)}
    catch(e){toast(e.response?.data?.message||'Delete failed','error')}
  }
  return<div>
    <PH title="Attendance" sub="Daily tracking — saved to database">
      <input type="date" value={date} max={today} onChange={e=>setDate(e.target.value)} style={{border:'1.5px solid #E2E8F0',borderRadius:9,padding:'8px 12px',fontSize:13,fontFamily:'inherit',outline:'none'}}/>
      <Btn onClick={save} disabled={saving}>{saving?'Saving…':'💾 Save Attendance'}</Btn>
    </PH>
    <div style={{display:'grid',gridTemplateColumns:'repeat(4,1fr)',gap:13,marginBottom:16}}>
      <St label="Employees" value={emps.length} color="#3B82F6"/>
      <St label="Present" value={cnt('Present')+cnt('Work_From_Home')} color="#22C55E"/>
      <St label="On Leave" value={cnt('Leave')+cnt('Half_Day')} color="#F59E0B"/>
      <St label="Absent" value={cnt('Absent')} color="#EF4444"/>
    </div>
    <Card>
      <div style={{padding:'12px 16px',borderBottom:'1px solid #E2E8F0',display:'flex',gap:8,flexWrap:'wrap',alignItems:'center'}}>
        <span style={{fontSize:12,fontWeight:700,color:'#64748B'}}>Quick mark all:</span>
        {ATT_OPTS.map(([s,c])=><button key={s} onClick={()=>markAll(s)} style={{padding:'5px 12px',borderRadius:20,fontSize:11,fontWeight:700,cursor:'pointer',fontFamily:'inherit',background:c+'14',color:c,border:`1px solid ${c}33`}}>{s.replace(/_/g,' ')}</button>)}
      </div>
      {loading?<Spin/>:<div style={{overflowX:'auto'}}><table style={{width:'100%',borderCollapse:'collapse',fontSize:13}}>
        <thead><tr style={{background:'#F8FAFC'}}>{['Employee','Department','Status','Check In','Check Out','Action'].map(h=><th key={h} style={{padding:'10px 14px',textAlign:'left',fontSize:11,fontWeight:700,color:'#94A3B8',textTransform:'uppercase',letterSpacing:'.5px',borderBottom:'1px solid #E2E8F0',whiteSpace:'nowrap'}}>{h}</th>)}</tr></thead>
        <tbody>{emps.map(e=>{const m=marks[e.id]||{}
          return<tr key={e.id} style={{borderBottom:'1px solid rgba(226,232,240,.6)'}}>
            <td style={{padding:'9px 14px'}}><div style={{display:'flex',alignItems:'center',gap:9}}><Av name={e.firstName+' '+e.lastName} size={28}/><div><div style={{fontWeight:600}}>{e.firstName} {e.lastName}</div><div style={{fontSize:11,color:'#94A3B8'}}>{e.empId}</div></div></div></td>
            <td style={{padding:'9px 14px',color:'#64748B'}}>{e.department?.name||'—'}</td>
            <td style={{padding:'9px 14px'}}><div style={{display:'flex',gap:5,flexWrap:'wrap'}}>
              {ATT_OPTS.map(([s,c])=><button key={s} onClick={()=>setStatus(e.id,s)} title={s.replace(/_/g,' ')} style={{padding:'4px 10px',borderRadius:16,fontSize:11,fontWeight:700,cursor:'pointer',fontFamily:'inherit',background:m.status===s?c:c+'10',color:m.status===s?'#fff':c,border:`1px solid ${m.status===s?c:c+'30'}`}}>{s==='Work_From_Home'?'WFH':s==='Half_Day'?'Half':s}</button>)}
            </div></td>
            <td style={{padding:'9px 14px'}}><input type="time" value={m.checkIn||''} onChange={ev=>setTime(e.id,'checkIn',ev.target.value)} style={{border:'1px solid #E2E8F0',borderRadius:7,padding:'5px 8px',fontSize:12,fontFamily:'inherit',outline:'none',width:105}}/></td>
            <td style={{padding:'9px 14px'}}><input type="time" value={m.checkOut||''} onChange={ev=>setTime(e.id,'checkOut',ev.target.value)} style={{border:'1px solid #E2E8F0',borderRadius:7,padding:'5px 8px',fontSize:12,fontFamily:'inherit',outline:'none',width:105}}/></td>
            <td style={{padding:'9px 14px'}}>{m.status?<BtnD onClick={()=>clearMark(e.id)}>🗑 Clear</BtnD>:<span style={{fontSize:11,color:'#CBD5E1'}}>—</span>}</td>
          </tr>})}
        </tbody>
      </table></div>}
    </Card>
  </div>
}

function Shell({user,onLogout}){
  const[page,setPage]=useState('dashboard');const[col,setCol]=useState(false)
  const[online,setOnline]=useState(true)
  window.navTo=setPage
  // The status pill used to be hardcoded green. Poll health instead: any HTTP reply
  // means the backend answered; only a dead connection counts as offline.
  useEffect(()=>{
    let alive=true
    const ping=()=>dashboardAPI.health().then(()=>{if(alive)setOnline(true)}).catch(e=>{if(alive)setOnline(!!e.response)})
    ping()
    const t=setInterval(ping,15000)
    return()=>{alive=false;clearInterval(t)}
  },[])
  const pages={dashboard:<Dashboard user={user}/>,employees:<Employees/>,attendance:<Attendance/>,leave:<Leave/>,payroll:<Payroll/>,recruitment:<Recruitment/>,performance:<Performance/>,departments:<Departments/>,holidays:<Holidays/>,reports:<Reports/>,bulk:<BulkImport/>,admin:<Admin/>,about:<About/>}
  const label=NAV.flatMap(g=>g.items).find(i=>i.id===page)?.l||'Dashboard'
  return<div style={{display:'flex',height:'100vh',overflow:'hidden'}}>
    <nav style={{width:col?62:230,background:'#0F172A',display:'flex',flexDirection:'column',flexShrink:0,transition:'width .2s',overflow:'hidden'}}>
      <div style={{padding:'16px 18px',display:'flex',alignItems:'center',gap:11,borderBottom:'1px solid rgba(255,255,255,.07)',minHeight:64,overflow:'hidden'}}>
        <Logo size={34}/>{!col&&<div><div style={{fontSize:16,fontWeight:900,color:'#fff',letterSpacing:2.5,whiteSpace:'nowrap'}}>AXVON</div><div style={{fontSize:9,color:'#475569',whiteSpace:'nowrap',marginTop:1}}>HRMS PLATFORM</div></div>}
      </div>
      <div style={{flex:1,overflowY:'auto',padding:'10px 7px'}}>
        {NAV.map(g=><div key={g.g}>
          {!col&&<div style={{fontSize:9,fontWeight:700,color:'rgba(255,255,255,.22)',letterSpacing:1.2,textTransform:'uppercase',padding:'10px 10px 4px'}}>{g.g}</div>}
          {g.items.map(it=><button key={it.id} onClick={()=>setPage(it.id)} title={it.l} style={{display:'flex',alignItems:'center',gap:10,padding:'9px 11px',borderRadius:9,cursor:'pointer',color:page===it.id?'#14B8A6':'#94A3B8',fontSize:13,border:'none',background:page===it.id?'rgba(20,184,166,.16)':'transparent',width:'100%',textAlign:'left',marginBottom:2,borderLeft:`2px solid ${page===it.id?'#14B8A6':'transparent'}`,fontWeight:page===it.id?700:400,fontFamily:'inherit',whiteSpace:'nowrap',overflow:'hidden',transition:'all .12s'}}>
            <span style={{fontSize:15,flexShrink:0}}>{it.i}</span>{!col&&it.l}
          </button>)}
        </div>)}
      </div>
      <div style={{padding:'10px 7px',borderTop:'1px solid rgba(255,255,255,.07)'}}>
        <div style={{display:'flex',alignItems:'center',gap:9,padding:'9px 11px',background:'rgba(255,255,255,.05)',borderRadius:9,marginBottom:7,overflow:'hidden'}}>
          <Av name={user.name||'User'} size={28}/>{!col&&<div style={{overflow:'hidden'}}><div style={{fontSize:12,fontWeight:700,color:'#F1F5F9',whiteSpace:'nowrap',overflow:'hidden',textOverflow:'ellipsis'}}>{user.name}</div><div style={{fontSize:10,color:'#64748B'}}>{user.role?.replace(/_/g,' ')}</div></div>}
        </div>
        <button onClick={onLogout} style={{display:'flex',alignItems:'center',gap:9,padding:'9px 11px',borderRadius:9,cursor:'pointer',color:'#EF4444',border:'none',background:'transparent',width:'100%',fontSize:13,fontFamily:'inherit',whiteSpace:'nowrap',overflow:'hidden'}}>
          <span style={{flexShrink:0}}>⇤</span>{!col&&'Sign Out'}
        </button>
      </div>
    </nav>
    <div style={{flex:1,display:'flex',flexDirection:'column',minWidth:0,overflow:'hidden'}}>
      <div style={{height:58,background:'#fff',borderBottom:'1px solid #E2E8F0',display:'flex',alignItems:'center',padding:'0 20px',gap:12,flexShrink:0}}>
        <button onClick={()=>setCol(c=>!c)} style={{background:'#F8FAFC',border:'1px solid #E2E8F0',borderRadius:8,padding:7,cursor:'pointer',fontSize:16,color:'#94A3B8'}}>☰</button>
        <div style={{display:'flex',alignItems:'center',gap:5,fontSize:13.5}}><span style={{color:'#94A3B8',fontSize:12}}>AXVON</span><span style={{color:'#E2E8F0'}}>›</span><span style={{fontWeight:800}}>{label}</span></div>
        <div style={{flex:1}}/>
        <div title={online?'Backend responding on port 17823':'No response from the backend on port 17823'} style={{fontSize:11,color:online?'#15803D':'#B91C1C',background:online?'#F0FDF4':'#FEF2F2',border:`1px solid ${online?'#BBF7D0':'#FECACA'}`,padding:'4px 10px',borderRadius:20,fontWeight:600}}>{online?'🟢 Backend Connected':'🔴 Backend Offline'}</div>
        <div style={{display:'flex',alignItems:'center',gap:9,padding:'5px 11px',background:'#F8FAFC',border:'1px solid #E2E8F0',borderRadius:10}}>
          <Av name={user.name||'User'} size={26}/><div><div style={{fontSize:12,fontWeight:700}}>{user.name}</div><div style={{fontSize:10,color:'#94A3B8'}}>{user.role?.replace(/_/g,' ')}</div></div>
        </div>
      </div>
      <div style={{flex:1,overflowY:'auto',padding:'22px 26px 36px'}}>
        <div key={page} style={{animation:'fadeUp .22s ease'}}>{pages[page]||<Dashboard user={user}/>}</div>
      </div>
      <div style={{background:'#fff',borderTop:'1px solid #E2E8F0',padding:'8px 26px',display:'flex',justifyContent:'space-between',flexShrink:0}}>
        <div style={{fontSize:11,color:'#94A3B8'}}><b style={{color:'#14B8A6'}}>AXVON HRMS</b> · React 18 + Spring Boot 3 + H2 Database · Full Stack</div>
        <div style={{fontSize:11,color:'#94A3B8'}}>Crafted with ❤ by <b style={{color:'#14B8A6'}}>Athul KV</b> · v2.0.0 · © 2025 AXVON</div>
      </div>
    </div>
  </div>
}

export default function App(){
  const[user,setUser]=useState(null);const[splash,setSplash]=useState(true)
  useEffect(()=>{
    setTimeout(()=>setSplash(false),2400)
    const saved=localStorage.getItem('axvon_user')
    if(saved)try{setUser(JSON.parse(saved))}catch{}
  },[])
  const logout=()=>{localStorage.clear();setUser(null)}
  return<>
    <Toasts/><Modal/>
    {splash&&<div style={{position:'fixed',inset:0,background:'#0F172A',display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',zIndex:9999,transition:'opacity .5s'}}>
      <Logo size={88}/>
      <div style={{fontSize:32,fontWeight:900,color:'#fff',letterSpacing:4,marginTop:18}}>AXVON</div>
      <div style={{fontSize:13,color:'#475569',marginTop:6,textAlign:'center'}}>Enterprise Human Resource Management System</div>
      <div style={{width:220,height:3,background:'rgba(255,255,255,.1)',borderRadius:2,marginTop:28,overflow:'hidden'}}><div style={{height:'100%',background:'linear-gradient(90deg,#3B82F6,#14B8A6)',borderRadius:2,animation:'spl 2.1s ease forwards'}}/></div>
      <div style={{position:'absolute',bottom:24,fontSize:12,color:'#334155'}}>Crafted by Athul KV · AXVON HRMS v2.0 · © 2025</div>
    </div>}
    {!user?<Login onLogin={setUser}/>:<Shell user={user} onLogout={logout}/>}
  </>
}
