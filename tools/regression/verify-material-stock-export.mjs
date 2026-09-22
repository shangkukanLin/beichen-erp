/**
 * 物料库存详情 —— 导出拼装用例（2026-09-22，node 直跑）
 *
 * 为什么是 .mjs 而不是 .ps1：导出逻辑已抽成纯 TS 函数
 * （beichen-erp-web/src/views/outsource/material-stock/export.ts，不依赖 XLSX/Vue），
 * 所以这里用 esbuild 现场把它转成 ESM 再 import，直接断言**块结构 / 小计 / 隐藏规则**。
 * node 读源码按 UTF-8，故本文件可以含中文（PS 5.1 的老毛病不适用）。
 *
 * 断言的规则（都对应真实业务）：
 *   ① 两个 sheet：物料库存汇总（按物料）+ 按仓库明细（按仓库分块）
 *   ② 分块顺序：委外仓在前、自有物料仓在后；同类按仓库 id 升序（可复现）
 *   ③ 仓内排序：物料类型排序位 → 物料名称
 *   ④ 0 库存隐藏并计数；**负数保留**且带备注；只有 0 的仓整块丢弃
 *   ⑤ 每仓「小计」= 该仓可见行之和；全仓总计与汇总视角同源一致
 *   ⑥ 汇总视角的「分布仓库」**含数量为 0 的仓**（与列表页显示一致）
 *   ⑦ 兜底：只有汇总形态数据（无 warehouseId）时，只产出汇总 sheet，且沿用接口给的分布仓库数
 */
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { createRequire } from 'node:module'

const here = fileURLToPath(new URL('.', import.meta.url))
const webDir = join(here, '..', '..', 'beichen-erp-web')
const srcTs = join(webDir, 'src', 'views', 'outsource', 'material-stock', 'export.ts')

let pass = 0, fail = 0
function ok(cond, msg) {
  if (cond) { pass++; console.log('PASS ' + msg) } else { fail++; console.log('FAIL ' + msg) }
}
function step(t) { console.log('--- ' + t) }

// ---- 用 esbuild 把纯函数转成 ESM 后 import（esbuild 随 vite 一起装，离线可用）----
const require = createRequire(join(webDir, 'package.json'))
const esbuild = require('esbuild')
const tmp = mkdtempSync(join(tmpdir(), 'matsto-export-'))
let mod
try {
  const source = readFileSync(srcTs, 'utf8')
  const out = esbuild.transformSync(source, { loader: 'ts', format: 'esm', target: 'es2020' })
  const tmpFile = join(tmp, 'export.mjs')
  writeFileSync(tmpFile, out.code, 'utf8')
  mod = await import(pathToFileURL(tmpFile).href)
} finally {
  rmSync(tmp, { recursive: true, force: true })
}

const { buildMaterialSheets, groupByWarehouse, aggregateByMaterial, warehouseCountOf, rowTotal } = mod
const TIME = '2026-09-22 10:00:00'

// ---- 夹具：4 个仓库（含只有 0 的仓、含负库存仓）+ 1 个自有物料仓 ----
const rows = [
  { warehouseId: 74, warehouseName: 'OwnWH', warehouseCategory: 'INVENTORY', materialId: 33, materialName: 'GlassA1', materialTypeName: 'Glass', materialTypeSortOrder: 1, unit: 'PCS', qtyGood: 150, qtyDefect: 0, totalQuantity: 150 },
  { warehouseId: 66, warehouseName: 'OS-A1', warehouseCategory: 'OUTSOURCE', materialId: 35, materialName: 'IC-A3', materialTypeName: 'IC', materialTypeSortOrder: 2, unit: 'PCS', qtyGood: 220, qtyDefect: 5, totalQuantity: 225 },
  { warehouseId: 66, warehouseName: 'OS-A1', warehouseCategory: 'OUTSOURCE', materialId: 33, materialName: 'GlassA1', materialTypeName: 'Glass', materialTypeSortOrder: 1, unit: 'PCS', qtyGood: 100, qtyDefect: 0, totalQuantity: 100 },
  { warehouseId: 66, warehouseName: 'OS-A1', warehouseCategory: 'OUTSOURCE', materialId: 61, materialName: 'CableX', materialTypeName: 'Cable', materialTypeSortOrder: 5, unit: 'PCS', qtyGood: 0, qtyDefect: 0, totalQuantity: 0 },
  { warehouseId: 70, warehouseName: 'OS-A5', warehouseCategory: 'OUTSOURCE', materialId: 34, materialName: 'CableA2', materialTypeName: 'Cable', materialTypeSortOrder: 5, unit: 'PCS', qtyGood: -36, qtyDefect: 0, totalQuantity: -36 },
  { warehouseId: 58, warehouseName: 'OS-SUP1', warehouseCategory: 'OUTSOURCE', materialId: 33, materialName: 'GlassA1', materialTypeName: 'Glass', materialTypeSortOrder: 1, unit: 'PCS', qtyGood: 0, qtyDefect: 0, totalQuantity: 0 },
]

step('grouping rules')
const g = groupByWarehouse(rows, { hideZero: true })
console.log('  blocks = ' + g.blocks.map((b) => b.warehouseName + '(' + (b.isOutsource ? 'OS' : 'OWN') + ',rows=' + b.rows.length + ',total=' + b.total + ')').join(' -> '))
console.log('  hiddenZero = ' + g.hiddenZero + '  negative = ' + g.negative)
ok(g.blocks.length === 3, 'the zero-only warehouse is dropped (3 blocks left)')
ok(g.blocks.map((b) => b.warehouseId).join(',') === '66,70,74', 'outsource first, own-material warehouse last, id ascending inside each kind')
ok(g.blocks[0].rows.map((r) => r.materialName).join(',') === 'GlassA1,IC-A3', 'inside a warehouse: material-type sortOrder then name')
ok(g.hiddenZero === 2, 'zero rows hidden and counted (2: one in OS-A1 + the zero-only warehouse)')
ok(g.negative === 1, 'negative rows counted (1) and NOT hidden')
ok(g.blocks[1].rows[0].materialName === 'CableA2' && rowTotal(g.blocks[1].rows[0]) === -36, 'the negative row survives with its value intact')
ok(g.blocks[0].total === 325 && g.blocks[0].good === 320 && g.blocks[0].defect === 5, 'per-warehouse subtotal = sum of its visible rows')

step('summary view')
const mats = aggregateByMaterial(rows)
const shown = mats.map((m) => m.materialName + '=' + m.total + '/' + warehouseCountOf(m) + 'wh')
console.log('  materials = ' + shown.join(', '))
ok(mats.map((m) => m.materialName).join(',') === 'GlassA1,IC-A3,CableX,CableA2', 'summary sorted by total desc (zero and negative go last)')
ok(warehouseCountOf(mats[0]) === 3, 'distribution warehouse count INCLUDES the zero-only warehouse (matches the list page)')
ok(mats[2].total === 0, 'a zero total material is still listed in the summary view')

step('sheet specs')
const sheets = buildMaterialSheets(rows, { hideZero: true, exportTime: TIME })
console.log('  sheets = ' + sheets.map((s) => s.name + '(rows=' + s.aoa.length + ',merges=' + s.merges.length + ')').join(', '))
ok(sheets.length === 2, 'two sheets are produced (summary + by-warehouse)')
ok(sheets[0].name === '物料库存汇总' && sheets[1].name === '按仓库明细', 'sheet names are the expected ones')
ok(sheets[1].merges.length === 2 + g.blocks.length, 'each warehouse block contributes one merged title row')

const h2 = sheets[1].aoa[1][0]
console.log('  note = ' + h2)
ok(h2.includes('共 3 个仓库 / 4 条'), 'the header states how many warehouses / rows are inside (4 visible = 2 + 1 + 1)')
ok(h2.includes('已隐藏 0 库存 2 条'), 'the header states how many zero rows were hidden')
ok(h2.includes('含负库存 1 条'), 'the header flags the negative stock')

const flat2 = sheets[1].aoa.map((r) => r.join('|'))
const negLine = flat2.find((l) => l.includes('CableA2')) || ''
ok(negLine.includes('-36'), 'the negative row keeps its negative number')
ok(negLine.includes('负库存'), 'the negative row carries a note explaining why')

const sum1 = sheets[0].aoa.slice(3).reduce((s, r) => s + Number(r[5] || 0), 0)
const sum2 = g.blocks.reduce((s, b) => s + b.total, 0)
ok(sum1 === sum2, 'both views agree on the grand total (' + sum1 + ')')

step('layout preview (by-warehouse sheet)')
sheets[1].aoa.forEach((r, i) => { if (i < 24) console.log('  [' + i + '] ' + r.join(' | ')) })

step('fallback: summary-shaped rows only')
const summaryRows = [
  { materialId: 33, materialName: 'GlassA1', materialTypeName: 'Glass', unit: 'PCS', qtyGood: 250, qtyDefect: 0, totalQuantity: 250, warehouseCount: 3 },
]
const sheets2 = buildMaterialSheets(summaryRows, { hideZero: true, exportTime: TIME })
ok(sheets2.length === 1 && sheets2[0].name === '物料库存汇总', 'with no warehouse dimension only the summary sheet is produced')
ok(Number(sheets2[0].aoa[3][6]) === 3, 'the fallback keeps the distribution count returned by the API')

console.log('RESULT ' + (fail === 0 ? 'PASS' : 'FAIL') + ' material stock export (summary + by-warehouse)  (PASS=' + pass + ' FAIL=' + fail + ')')
process.exit(fail === 0 ? 0 : 1)
