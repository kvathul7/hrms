// Report exporters — CSV, real .xlsx, real .docx, and PDF.
//
// .xlsx and .docx are both OPC packages: a ZIP of XML parts. They are written here by
// hand rather than pulling in a spreadsheet library, because the only thing needed is
// "tables in, file out" and the bundle already carries pdf.js. Entries are STORED
// (uncompressed) — the OPC spec allows it and Excel/Word open them fine, which avoids
// needing a deflate implementation.
//
// A report is described by one shape, and every format renders from it:
//   { title, subtitle, sheets: [ { name, cols: [string], rows: [[cell]] } ] }
// Sheet 0 is the main table. Extra sheets become extra worksheets (xlsx) or extra
// sections (docx / pdf); CSV takes the main table only, since CSV holds one table.

// ── zip (stored entries) ─────────────────────────────────────────────
const CRC_TABLE = (() => {
  const t = new Uint32Array(256)
  for (let i = 0; i < 256; i++) { let c = i; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[i] = c >>> 0 }
  return t
})()
const crc32 = buf => {
  let c = 0xFFFFFFFF
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8)
  return (c ^ 0xFFFFFFFF) >>> 0
}
const utf8 = s => new TextEncoder().encode(s)

// files: [{name, data: Uint8Array}] → Uint8Array of a valid ZIP
function zipStore(files) {
  const parts = [], central = []
  let offset = 0
  for (const f of files) {
    const name = utf8(f.name), data = f.data, crc = crc32(data)
    const lh = new Uint8Array(30), lv = new DataView(lh.buffer)
    lv.setUint32(0, 0x04034b50, true); lv.setUint16(4, 20, true); lv.setUint16(6, 0x0800, true)
    lv.setUint16(8, 0, true) // method 0 = stored
    lv.setUint32(14, crc, true); lv.setUint32(18, data.length, true); lv.setUint32(22, data.length, true)
    lv.setUint16(26, name.length, true)
    parts.push(lh, name, data)

    const ch = new Uint8Array(46), cv = new DataView(ch.buffer)
    cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true)
    cv.setUint16(8, 0x0800, true); cv.setUint16(10, 0, true)
    cv.setUint32(16, crc, true); cv.setUint32(20, data.length, true); cv.setUint32(24, data.length, true)
    cv.setUint16(28, name.length, true); cv.setUint32(42, offset, true)
    central.push(ch, name)
    offset += 30 + name.length + data.length
  }
  const cdSize = central.reduce((n, b) => n + b.length, 0)
  const end = new Uint8Array(22), ev = new DataView(end.buffer)
  ev.setUint32(0, 0x06054b50, true)
  ev.setUint16(8, files.length, true); ev.setUint16(10, files.length, true)
  ev.setUint32(12, cdSize, true); ev.setUint32(16, offset, true)
  const all = [...parts, ...central, end]
  const out = new Uint8Array(all.reduce((n, b) => n + b.length, 0))
  let p = 0
  for (const b of all) { out.set(b, p); p += b.length }
  return out
}

// ── shared helpers ───────────────────────────────────────────────────
// XML 1.0 forbids most control characters outright — a stray one makes Excel and Word
// declare the whole file corrupt, so they are dropped rather than escaped.
const xml = v => String(v ?? '')
  .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
const isNum = v => typeof v === 'number' && isFinite(v)
const part = (name, str) => ({ name, data: utf8(str) })
const XML_DECL = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
// Excel caps sheet names at 31 chars and rejects : \ / ? * [ ]
const sheetName = (n, i) => (String(n || `Sheet${i + 1}`).replace(/[:\\/?*[\]]/g, '-').slice(0, 31)) || `Sheet${i + 1}`

// ── .xlsx ────────────────────────────────────────────────────────────
const colRef = n => { let s = ''; n++; while (n > 0) { const m = (n - 1) % 26; s = String.fromCharCode(65 + m) + s; n = (n - 1 - m) / 26 } return s }

function sheetXml(sheet) {
  const cols = sheet.cols || [], rows = sheet.rows || []
  // Width from the widest value in each column, so nothing lands as ####.
  const widths = cols.map((c, i) => {
    let w = String(c ?? '').length
    for (const r of rows) w = Math.max(w, String(r[i] ?? '').length)
    return Math.min(Math.max(w + 3, 9), 46)
  })
  const head = `<row r="1">${cols.map((c, i) =>
    `<c r="${colRef(i)}1" s="1" t="inlineStr"><is><t xml:space="preserve">${xml(c)}</t></is></c>`).join('')}</row>`
  const body = rows.map((r, ri) => `<row r="${ri + 2}">${r.map((v, ci) => {
    const ref = `${colRef(ci)}${ri + 2}`
    if (v == null || v === '') return ''
    return isNum(v)
      ? `<c r="${ref}"><v>${v}</v></c>`
      : `<c r="${ref}" t="inlineStr"><is><t xml:space="preserve">${xml(v)}</t></is></c>`
  }).join('')}</row>`).join('')
  return `${XML_DECL}<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">`
    + `<cols>${widths.map((w, i) => `<col min="${i + 1}" max="${i + 1}" width="${w}" customWidth="1"/>`).join('')}</cols>`
    + `<sheetData>${head}${body}</sheetData></worksheet>`
}

const STYLES_XML = `${XML_DECL}<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">`
  + `<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>`
  + `<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts>`
  + `<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>`
  + `<fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill></fills>`
  + `<borders count="1"><border/></borders>`
  + `<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>`
  + `<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>`
  + `<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs></styleSheet>`

export function buildXlsx(report) {
  const sheets = (report.sheets || []).filter(s => s && (s.cols || []).length)
  if (!sheets.length) throw new Error('nothing to export')
  const files = [
    part('[Content_Types].xml', `${XML_DECL}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">`
      + `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>`
      + `<Default Extension="xml" ContentType="application/xml"/>`
      + `<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>`
      + `<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>`
      + sheets.map((_, i) => `<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('')
      + `</Types>`),
    part('_rels/.rels', `${XML_DECL}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">`
      + `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`),
    part('xl/workbook.xml', `${XML_DECL}<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">`
      + `<sheets>${sheets.map((s, i) => `<sheet name="${xml(sheetName(s.name, i))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join('')}</sheets></workbook>`),
    part('xl/_rels/workbook.xml.rels', `${XML_DECL}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">`
      + sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join('')
      + `<Relationship Id="rId${sheets.length + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`),
    part('xl/styles.xml', STYLES_XML),
    ...sheets.map((s, i) => part(`xl/worksheets/sheet${i + 1}.xml`, sheetXml(s))),
  ]
  return zipStore(files)
}

// ── .docx ────────────────────────────────────────────────────────────
const W_NS = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
const run = (text, opts = {}) => {
  const pr = [opts.b ? '<w:b/>' : '', opts.color ? `<w:color w:val="${opts.color}"/>` : '',
    opts.sz ? `<w:sz w:val="${opts.sz}"/>` : ''].join('')
  return `<w:r>${pr ? `<w:rPr>${pr}</w:rPr>` : ''}<w:t xml:space="preserve">${xml(text)}</w:t></w:r>`
}
const para = (text, opts = {}) => `<w:p>${opts.spacing ? `<w:pPr><w:spacing w:after="${opts.spacing}"/></w:pPr>` : ''}${text === '' ? '' : run(text, opts)}</w:p>`
const cell = (text, opts = {}) =>
  `<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/>${opts.shade ? `<w:shd w:val="clear" w:fill="${opts.shade}"/>` : ''}</w:tcPr>`
  + `<w:p>${run(text, opts)}</w:p></w:tc>`

function docxTable(cols, rows) {
  const borders = ['top', 'left', 'bottom', 'right', 'insideH', 'insideV']
    .map(s => `<w:${s} w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/>`).join('')
  const head = `<w:tr><w:trPr><w:tblHeader/></w:trPr>${cols.map(c => cell(c, { b: true, shade: '0F766E', color: 'FFFFFF' })).join('')}</w:tr>`
  const body = rows.map(r => `<w:tr>${cols.map((_, i) => cell(r[i] == null ? '' : r[i])).join('')}</w:tr>`).join('')
  return `<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>${borders}</w:tblBorders></w:tblPr>${head}${body}</w:tbl>`
}

export function buildDocx(report) {
  const sheets = (report.sheets || []).filter(s => s && (s.cols || []).length)
  if (!sheets.length) throw new Error('nothing to export')
  // Landscape once a table gets wide, or the columns crush together.
  const wide = sheets.some(s => (s.cols || []).length > 5)
  const sectPr = `<w:sectPr><w:pgSz w:w="${wide ? 16838 : 11906}" w:h="${wide ? 11906 : 16838}"${wide ? ' w:orient="landscape"' : ''}/>`
    + `<w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720"/></w:sectPr>`
  const body = [
    para(`AXVON HRMS — ${report.title}`, { b: true, sz: 34 }),
    para(report.subtitle || '', { sz: 18, color: '64748B', spacing: 220 }),
    ...sheets.flatMap((s, i) => [
      i > 0 || sheets.length > 1 ? para(sheetName(s.name, i), { b: true, sz: 26, spacing: 120 }) : '',
      docxTable(s.cols, s.rows || []),
      para(''),
    ]),
    para(`© ${new Date().getFullYear()} AXVON HRMS · generated ${new Date().toLocaleString()}`, { sz: 16, color: '94A3B8' }),
    sectPr,
  ].join('')
  return zipStore([
    part('[Content_Types].xml', `${XML_DECL}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">`
      + `<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>`
      + `<Default Extension="xml" ContentType="application/xml"/>`
      + `<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`),
    part('_rels/.rels', `${XML_DECL}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">`
      + `<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`),
    part('word/document.xml', `${XML_DECL}<w:document ${W_NS}><w:body>${body}</w:body></w:document>`),
  ])
}

// ── CSV ──────────────────────────────────────────────────────────────
const csvCell = v => { const s = String(v ?? ''); return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s }
export function buildCsv(report) {
  const s = (report.sheets || [])[0]
  if (!s) throw new Error('nothing to export')
  // BOM so Excel opens UTF-8 names correctly instead of mangling them.
  return '\uFEFF' + [s.cols.map(csvCell).join(','), ...(s.rows || []).map(r => s.cols.map((_, i) => csvCell(r[i])).join(','))].join('\r\n')
}

// ── PDF (HTML handed to Chromium's PDF writer) ───────────────────────
const PDF_CSS = `@page{margin:14mm}
body{font-family:'Segoe UI',Arial,sans-serif;color:#0F172A;margin:0}
h1{font-size:18px;margin:0 0 2px}h2{font-size:13px;margin:18px 0 6px;color:#0F766E}
.sub{font-size:10.5px;color:#64748B;margin-bottom:14px}
table{width:100%;border-collapse:collapse;font-size:10px;margin-bottom:10px}
th{background:#0F766E;color:#fff;text-align:left;padding:6px 8px;border:1px solid #0F766E;font-size:9.5px;text-transform:uppercase;letter-spacing:.4px}
td{padding:5px 8px;border:1px solid #E2E8F0}tr:nth-child(even) td{background:#F8FAFC}
thead{display:table-header-group}tr{page-break-inside:avoid}
.foot{margin-top:12px;font-size:9.5px;color:#94A3B8}`
export function buildReportHtml(report) {
  const esc = xml
  const sheets = (report.sheets || []).filter(s => s && (s.cols || []).length)
  const tables = sheets.map((s, i) => (sheets.length > 1 ? `<h2>${esc(sheetName(s.name, i))}</h2>` : '')
    + `<table><thead><tr>${s.cols.map(c => `<th>${esc(c)}</th>`).join('')}</tr></thead>`
    + `<tbody>${(s.rows || []).map(r => `<tr>${s.cols.map((_, ci) => `<td>${esc(r[ci] == null ? '' : r[ci])}</td>`).join('')}</tr>`).join('')}</tbody></table>`).join('')
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${esc(report.title)}</title><style>${PDF_CSS}</style></head>`
    + `<body><h1>AXVON HRMS — ${esc(report.title)}</h1><div class="sub">${esc(report.subtitle || '')}</div>${tables}`
    + `<div class="foot">© ${new Date().getFullYear()} AXVON HRMS · generated ${new Date().toLocaleString()}</div></body></html>`
}

// ── saving ───────────────────────────────────────────────────────────
export const canSaveLocally = () => !!(window.electronAPI && window.electronAPI.saveReport)
export const openReportsFolder = () => window.electronAPI?.openReportsFolder?.()

// btoa needs a binary string, and spreading a large array blows the call stack —
// so it is chunked.
function toBase64(bytes) {
  let s = ''
  const CH = 0x8000
  for (let i = 0; i < bytes.length; i += CH) s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH))
  return btoa(s)
}
function download(filename, blob) {
  const a = document.createElement('a')
  const url = URL.createObjectURL(blob)
  a.href = url; a.download = filename
  // Must be in the DOM to click reliably, and the object URL has to outlive the click.
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

const MIME = {
  csv: 'text/csv;charset=utf-8',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  pdf: 'application/pdf',
}

// Writes the file and resolves to the absolute path in the desktop app, or null in a
// browser (where it lands in the normal Downloads folder instead).
export async function saveReportAs(report, kind, filename) {
  if (kind === 'pdf') {
    const html = buildReportHtml(report)
    if (canSaveLocally()) return await window.electronAPI.saveReportPdf({ filename, html })
    // Browser fallback: no printToPDF available, so offer the print dialog.
    const w = window.open('', '_blank', 'width=900,height=700')
    if (!w) throw new Error('Popup blocked — allow popups to save as PDF')
    w.document.write(html + '<script>window.onload=()=>setTimeout(()=>window.print(),300)<\/script>')
    w.document.close()
    return null
  }
  const bytes = kind === 'csv' ? utf8(buildCsv(report))
    : kind === 'xlsx' ? buildXlsx(report)
    : buildDocx(report)
  if (canSaveLocally()) return await window.electronAPI.saveReport({ filename, base64: toBase64(bytes) })
  download(filename, new Blob([bytes], { type: MIME[kind] }))
  return null
}
