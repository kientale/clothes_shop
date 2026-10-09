import { MinusIcon, PlusIcon, RulerIcon, TrashIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { accessErrorMessage } from '../../api/access'
import { catalog, type SizeChart } from '../../api/catalog'
import { useCan } from '../../components/kit'
import { useToast } from '../../components/Toast'
import { useAsync } from '../../hooks'

const TEMPLATE: SizeChart = {
  columns: ['Size', 'Ngực (cm)', 'Eo (cm)', 'Dài áo (cm)'],
  rows: [
    ['S', '', '', ''],
    ['M', '', '', ''],
    ['L', '', '', ''],
  ],
  note: null,
}
const MAX_COLUMNS = 8
const MAX_ROWS = 30

/**
 * The size chart shoppers open from the product page ("Bảng size"). Edited as a small table: the first column
 * names the size, the others hold measurements.
 */
export function SizeChartPanel({ productId, sizeNames }: { productId: string; sizeNames: string[] }) {
  const canWrite = useCan('PRODUCT_WRITE')
  const toast = useToast()
  const saved = useAsync(() => catalog.sizeChart.get(productId), [productId])
  const [chart, setChart] = useState<SizeChart | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => setChart(saved.data ?? null), [saved.data])

  const start = () =>
    setChart(sizeNames.length ? { ...TEMPLATE, rows: sizeNames.map((name) => [name, '', '', '']) } : { ...TEMPLATE, rows: TEMPLATE.rows.map((r) => [...r]) })
  const setCell = (row: number, column: number, value: string) =>
    setChart((c) => c && { ...c, rows: c.rows.map((r, i) => (i === row ? r.map((cell, j) => (j === column ? value : cell)) : r)) })
  const setColumn = (column: number, value: string) => setChart((c) => c && { ...c, columns: c.columns.map((h, j) => (j === column ? value : h)) })
  const addColumn = () => setChart((c) => c && { ...c, columns: [...c.columns, ''], rows: c.rows.map((r) => [...r, '']) })
  const removeColumn = (column: number) =>
    setChart((c) => c && { ...c, columns: c.columns.filter((_, j) => j !== column), rows: c.rows.map((r) => r.filter((_, j) => j !== column)) })
  const addRow = () => setChart((c) => c && { ...c, rows: [...c.rows, c.columns.map(() => '')] })
  const removeRow = (row: number) => setChart((c) => c && { ...c, rows: c.rows.filter((_, i) => i !== row) })

  const save = async () => {
    if (!chart) return
    if (chart.columns.some((h) => !h.trim())) return setError('Đặt tên cho mọi cột.')
    if (chart.rows.some((r) => !r[0]?.trim())) return setError('Cột đầu tiên của mỗi dòng là tên size.')
    setBusy(true)
    setError(undefined)
    try {
      saved.setData(await catalog.sizeChart.save(productId, { ...chart, note: chart.note?.trim() || null }))
      toast.success('Đã lưu bảng size.')
    } catch (err) {
      setError(accessErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await catalog.sizeChart.remove(productId)
      saved.setData(null)
      setChart(null)
      toast.success('Đã gỡ bảng size.')
    } catch (err) {
      setError(accessErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const dirty = JSON.stringify(chart) !== JSON.stringify(saved.data ?? null)

  return (
    <section className="panel">
      <div className="section-head">
        <h2 className="section-title">Bảng size</h2>
        {canWrite && chart && saved.data && (
          <button type="button" className="btn btn-plain btn-sm" onClick={remove} disabled={busy}>
            <TrashIcon size={14} /> Gỡ bảng size
          </button>
        )}
      </div>
      {saved.loading && !saved.data ? (
        <span className="skel skel-row" />
      ) : !chart ? (
        <div className="empty-inline">
          <RulerIcon size={28} aria-hidden />
          <p className="muted small">Chưa có bảng size. Khách thấy nút "Bảng size" cạnh lựa chọn size khi sản phẩm có bảng.</p>
          {canWrite && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={start}>
              <PlusIcon size={14} /> Tạo bảng size
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="size-chart-editor">
            <table>
              <thead>
                <tr>
                  {chart.columns.map((heading, column) => (
                    <th key={column} scope="col">
                      <input aria-label={`Tên cột ${column + 1}`} value={heading} maxLength={40} disabled={!canWrite}
                        onChange={(e) => setColumn(column, e.target.value)} />
                      {canWrite && column > 0 && chart.columns.length > 2 && (
                        <button type="button" className="icon-btn" onClick={() => removeColumn(column)} aria-label={`Bỏ cột ${heading}`} title="Bỏ cột">
                          <MinusIcon size={12} />
                        </button>
                      )}
                    </th>
                  ))}
                  {canWrite && chart.columns.length < MAX_COLUMNS && (
                    <th>
                      <button type="button" className="icon-btn" onClick={addColumn} aria-label="Thêm cột" title="Thêm cột">
                        <PlusIcon size={14} />
                      </button>
                    </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {chart.rows.map((row, r) => (
                  <tr key={r}>
                    {row.map((cell, column) => (
                      <td key={column}>
                        <input aria-label={`${chart.columns[column] || 'Ô'} dòng ${r + 1}`} value={cell} maxLength={40} disabled={!canWrite}
                          className={column === 0 ? 'strong' : undefined} onChange={(e) => setCell(r, column, e.target.value)} />
                      </td>
                    ))}
                    {canWrite && (
                      <td>
                        {chart.rows.length > 1 && (
                          <button type="button" className="icon-btn" onClick={() => removeRow(r)} aria-label={`Bỏ dòng ${row[0]}`} title="Bỏ dòng">
                            <MinusIcon size={12} />
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {canWrite && chart.rows.length < MAX_ROWS && (
            <button type="button" className="link-btn" onClick={addRow}>
              <PlusIcon size={12} /> Thêm dòng
            </button>
          )}
          <label className="field">
            <span className="field-label">Ghi chú cách đo (không bắt buộc)</span>
            <textarea rows={2} maxLength={500} disabled={!canWrite} value={chart.note ?? ''}
              onChange={(e) => setChart({ ...chart, note: e.target.value })} placeholder="Ví dụ: đo sát người; giữa hai size nên chọn size lớn hơn." />
          </label>
          {error && (
            <div className="banner banner-error" role="alert">
              {error}
            </div>
          )}
          {canWrite && (
            <div className="row-actions">
              <button type="button" className="btn btn-primary btn-sm" onClick={save} disabled={busy || !dirty}>
                {busy ? 'Đang lưu...' : 'Lưu bảng size'}
              </button>
              {dirty && (
                <button type="button" className="btn btn-plain btn-sm" onClick={() => setChart(saved.data ?? null)} disabled={busy}>
                  Hoàn tác
                </button>
              )}
            </div>
          )}
        </>
      )}
    </section>
  )
}
