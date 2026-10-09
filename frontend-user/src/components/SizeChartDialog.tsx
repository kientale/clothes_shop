import { RulerIcon, XIcon } from '@phosphor-icons/react'
import { useRef } from 'react'
import { Link } from 'react-router-dom'
import type { SizeChart } from '../api/types'

/** "Bảng size" link that opens the product's measurements in a modal dialog. */
export function SizeChartButton({ chart, productName }: { chart: SizeChart; productName: string }) {
  const dialog = useRef<HTMLDialogElement>(null)
  return (
    <>
      <button type="button" className="link-btn size-chart-link" onClick={() => dialog.current?.showModal()}>
        <RulerIcon size={16} aria-hidden /> Bảng size
      </button>
      <dialog
        ref={dialog}
        className="sheet-dialog"
        aria-labelledby="size-chart-title"
        onClick={(event) => event.target === dialog.current && dialog.current?.close()}
      >
        <div className="sheet-dialog-body">
          <header className="sheet-dialog-head">
            <h2 id="size-chart-title">Bảng size</h2>
            <button type="button" className="icon-btn" onClick={() => dialog.current?.close()} aria-label="Đóng">
              <XIcon size={18} />
            </button>
          </header>
          <p className="muted small">{productName}</p>
          <div className="size-table-wrap">
            <table className="size-table">
              <thead>
                <tr>
                  {chart.columns.map((column) => (
                    <th key={column} scope="col">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {chart.rows.map((row, index) => (
                  <tr key={index}>
                    {row.map((cell, column) =>
                      column === 0 ? (
                        <th key={column} scope="row">
                          {cell}
                        </th>
                      ) : (
                        <td key={column}>{cell}</td>
                      ),
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {chart.note && <p className="prose small">{chart.note}</p>}
          <p className="muted small">
            Phân vân giữa hai size? Xem <Link to="/policies/RETURN_EXCHANGE">chính sách đổi trả</Link>, đổi size miễn phí khi còn hàng.
          </p>
        </div>
      </dialog>
    </>
  )
}
