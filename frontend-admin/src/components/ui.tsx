import { CaretDoubleLeftIcon, CaretDoubleRightIcon, CaretLeftIcon, CaretRightIcon, XIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Modal built on the native <dialog>: showModal() gives focus containment, Esc to close
 * and an inert page behind it without extra libraries.
 */
export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
}: {
  open: boolean
  title: string
  description?: ReactNode
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    else if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className={`modal modal-${size}`}
      aria-labelledby="modal-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        // A click on the backdrop lands on the dialog element itself.
        if (event.target === event.currentTarget) onClose()
      }}
    >
      {open && (
        <div className="modal-body">
          <header className="modal-head">
            <div>
              <h2 id="modal-title">{title}</h2>
              {description && <p className="modal-desc">{description}</p>}
            </div>
            <button type="button" className="icon-btn modal-close" onClick={onClose} aria-label="Đóng">
              <XIcon size={18} />
            </button>
          </header>
          <div className="modal-content">{children}</div>
          {footer && <footer className="modal-foot">{footer}</footer>}
        </div>
      )}
    </dialog>
  )
}

/** Yes/no confirmation for destructive actions; keeps itself open and busy until `onConfirm` settles. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  onConfirm,
  onClose,
  formatError = (err) => (err instanceof Error ? err.message : String(err)),
}: {
  open: boolean
  title: string
  message: ReactNode
  confirmLabel: string
  onConfirm: () => Promise<void>
  onClose: () => void
  formatError?: (error: unknown) => string
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (open) setError(undefined)
  }, [open])

  const confirm = async () => {
    setBusy(true)
    setError(undefined)
    try {
      await onConfirm()
      onClose()
    } catch (err) {
      setError(formatError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={open}
      title={title}
      onClose={busy ? () => {} : onClose}
      size="sm"
      footer={
        <>
          <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="button" className="btn btn-danger" onClick={confirm} disabled={busy}>
            {busy ? 'Đang xử lý...' : confirmLabel}
          </button>
        </>
      }
    >
      <p className="confirm-text">{message}</p>
      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}
    </Modal>
  )
}

const PAGE_SIZES = [10, 20, 50] as const

/**
 * Page index (0-based) and page size kept in the URL as ?page=N (1-based) and ?size=N,
 * so reloads, shared links and the Back button land on the same page.
 * The page returns to 1 whenever `resetKey` changes (new search text or filter).
 */
export function usePaging(resetKey: string, defaultSize = 20) {
  const [params, setParams] = useSearchParams()
  const pageParam = Number.parseInt(params.get('page') ?? '', 10)
  const sizeParam = Number.parseInt(params.get('size') ?? '', 10)
  const page = Number.isFinite(pageParam) && pageParam > 1 ? pageParam - 1 : 0
  const size = (PAGE_SIZES as readonly number[]).includes(sizeParam) ? sizeParam : defaultSize

  const update = useCallback(
    (changes: Record<string, number | null>, replace = false) =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(changes)) {
            if (value === null) next.delete(key)
            else next.set(key, String(value))
          }
          return next
        },
        { replace },
      ),
    [setParams],
  )

  const setPage = useCallback((value: number) => update({ page: value > 0 ? value + 1 : null }), [update])
  const setSize = useCallback(
    (value: number) => update({ size: value === defaultSize ? null : value, page: null }),
    [update, defaultSize],
  )

  // Compare with the previous key instead of skipping the first run, so StrictMode's
  // double effect on mount does not wipe a page that came from the URL.
  const previousKey = useRef(resetKey)
  useEffect(() => {
    if (previousKey.current === resetKey) return
    previousKey.current = resetKey
    update({ page: null }, true)
  }, [resetKey, update])

  return { page, size, setPage, setSize }
}

/** 1-based row number that continues across pages (row 1 of page 2 at size 20 is 21). */
export function rowNumber(page: { page: number; size: number } | undefined, index: number) {
  return page ? page.page * page.size + index + 1 : index + 1
}

/** Page numbers to show: first, last and a window around the current page, with gaps as null. */
function pageItems(current: number, total: number): (number | null)[] {
  const wanted = new Set([0, total - 1, current - 1, current, current + 1].filter((p) => p >= 0 && p < total))
  if (current <= 2) [1, 2, 3].forEach((p) => p < total && wanted.add(p))
  if (current >= total - 3) [total - 2, total - 3, total - 4].forEach((p) => p >= 0 && wanted.add(p))
  const sorted = [...wanted].sort((a, b) => a - b)
  const items: (number | null)[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1]! > 1) items.push(null)
    items.push(p)
  })
  return items
}

export function Pagination({
  page,
  size,
  totalPages,
  totalElements,
  itemLabel,
  onPageChange,
  onSizeChange,
}: {
  page: number
  size: number
  totalPages: number
  totalElements: number
  /** Noun for the counted rows, e.g. "tài khoản". */
  itemLabel: string
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}) {
  const sizeId = useId()
  const pages = Math.max(totalPages, 1)

  // A page past the end (stale URL, or the last row on the last page was deleted): go to the last page.
  useEffect(() => {
    if (totalPages > 0 && page >= totalPages) onPageChange(totalPages - 1)
  }, [page, totalPages, onPageChange])

  const from = totalElements === 0 ? 0 : page * size + 1
  const to = Math.min((page + 1) * size, totalElements)
  const go = (target: number) => {
    if (target !== page && target >= 0 && target < pages) onPageChange(target)
  }

  return (
    <nav className="pager" aria-label="Phân trang">
      <p className="pager-info" aria-live="polite">
        {totalElements === 0 ? (
          <>Không có {itemLabel} nào</>
        ) : (
          <>
            Hiển thị <strong>{from}-{to}</strong> trên <strong>{totalElements}</strong> {itemLabel}
          </>
        )}
      </p>

      <div className="pager-pages">
        <button type="button" className="icon-btn pager-edge" onClick={() => go(0)} disabled={page === 0} aria-label="Trang đầu" title="Trang đầu">
          <CaretDoubleLeftIcon size={15} />
        </button>
        <button type="button" className="icon-btn" onClick={() => go(page - 1)} disabled={page === 0} aria-label="Trang trước" title="Trang trước">
          <CaretLeftIcon size={15} />
        </button>
        {pageItems(page, pages).map((item, index) =>
          item === null ? (
            <span key={`gap-${index}`} className="pager-gap" aria-hidden>
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              className="pager-num"
              onClick={() => go(item)}
              aria-current={item === page ? 'page' : undefined}
              aria-label={`Trang ${item + 1}`}
            >
              {item + 1}
            </button>
          ),
        )}
        <button type="button" className="icon-btn" onClick={() => go(page + 1)} disabled={page >= pages - 1} aria-label="Trang sau" title="Trang sau">
          <CaretRightIcon size={15} />
        </button>
        <button type="button" className="icon-btn pager-edge" onClick={() => go(pages - 1)} disabled={page >= pages - 1} aria-label="Trang cuối" title="Trang cuối">
          <CaretDoubleRightIcon size={15} />
        </button>
      </div>

      <label className="pager-size" htmlFor={sizeId}>
        <span>Mỗi trang</span>
        <select id={sizeId} value={size} onChange={(event) => onSizeChange(Number(event.target.value))}>
          {PAGE_SIZES.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
    </nav>
  )
}

/** Value that only updates after `delay` ms without changes, for search-as-you-type. */
export function useDebounced<T>(value: T, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

export function initials(name: string) {
  return name
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}
