import { ArrowsOutSimpleIcon } from '@phosphor-icons/react'
import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { Modal } from './ui'

/**
 * Table cell that keeps long content from stretching the table: text wraps inside a fixed width
 * and stops after three lines. When something is cut off, a "Xem thêm" button opens the full row.
 */
export function ClipCell({ children, onExpand }: { children: ReactNode; onExpand: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const [clipped, setClipped] = useState(false)

  useLayoutEffect(() => {
    const body = ref.current
    if (!body) return
    const check = () => setClipped(body.scrollHeight > body.clientHeight + 1 || body.scrollWidth > body.clientWidth + 1)
    check()
    const observer = new ResizeObserver(check)
    observer.observe(body)
    return () => observer.disconnect()
  }, [children])

  return (
    <div className={clipped ? 'cell is-clipped' : 'cell'}>
      <div ref={ref} className="cell-body">
        {children}
      </div>
      {clipped && (
        <button type="button" className="cell-more" onClick={onExpand}>
          <ArrowsOutSimpleIcon size={12} aria-hidden /> Xem thêm
        </button>
      )}
    </div>
  )
}

/** Every column of one row, unclipped, in a dialog. */
export function RowDetailDialog<T>({
  row,
  title,
  columns,
  onClose,
}: {
  row: T | null
  title: string
  columns: { header: string; render: (row: T) => ReactNode }[]
  onClose: () => void
}) {
  return (
    <Modal open={row !== null} title={title} onClose={onClose} size="lg">
      {row !== null && (
        <dl className="row-detail">
          {columns.map((column) => (
            <div key={column.header}>
              <dt>{column.header}</dt>
              <dd>{column.render(row) ?? '-'}</dd>
            </div>
          ))}
        </dl>
      )}
    </Modal>
  )
}
