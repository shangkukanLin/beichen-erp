// 临时排查脚本：查 YF-20260822007 应付单的来源类型
const BASE = 'http://localhost:8080'
let sid = ''

async function req(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json', cookie: sid }
  const res = await fetch(BASE + path, { ...opts, headers })
  const sc = res.headers.get('set-cookie')
  if (sc) sid = sc.split(';')[0]
  const txt = await res.text()
  let data
  try { data = JSON.parse(txt) } catch { data = txt }
  return { status: res.status, data }
}

async function main() {
  await req('/api/auth/login', { method: 'POST', body: JSON.stringify({ username: 'lin', password: '123', companyId: 1 }) })
  const page = await req('/api/finance/payable/page?pageNum=1&pageSize=200')
  const recs = page.data?.data?.records || []
  const t = recs.filter(r => String(r.billNo) === 'YF-20260822007')
  if (t.length === 0) {
    console.log('NOT FOUND in page 1..200, total=', page.data?.data?.total)
    for (const r of recs.slice(0, 10)) console.log('sample', JSON.stringify(r))
  } else {
    for (const r of t) console.log('FOUND', JSON.stringify({ id: r.id, billNo: r.billNo, sourceBillType: r.sourceBillType, sourceBillNo: r.sourceBillNo, sourceId: r.sourceId, supplierId: r.supplierId }))
  }
}

main().catch(e => { console.error('ERR', e); process.exit(1) })
