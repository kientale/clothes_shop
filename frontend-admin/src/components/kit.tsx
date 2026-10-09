import { CaretDownIcon, MagnifyingGlassIcon, XIcon } from '@phosphor-icons/react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { accessErrorMessage } from '../api/access'
import type { Tone } from '../api/commerce'
import type { Page } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks'
import { Field, FormDialog, useDialogForm } from './forms'
import { ClipCell, RowDetailDialog } from './TableCell'
import { Pagination, rowNumber, useDebounced, usePaging } from './ui'

/*
 * Building blocks for the operations screens (inventory, orders, marketing, content, reports,
 * settings). They reuse the table, pill and panel classes of the catalog screens so every page
 * reads as the same product.
 */

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
const integer = new Intl.NumberFormat('vi-VN')
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
const dateOnly = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })

export const vndOf = (value: number | null | undefined) => (value === null || value === undefined ? '-' : vnd.format(value))
export const countOf = (value: number | null | undefined) => (value === null || value === undefined ? '-' : integer.format(value))
export const whenOf = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '-')
export const dayOf = (iso: string | null | undefined) => (iso ? dateOnly.format(new Date(iso)) : '-')
export const shortId = (id: string | null | undefined) => (id ? id.slice(0, 8).toUpperCase() : '-')

/** True when the signed-in admin holds the permission code. */
export function useCan(code: string) {
  const { user } = useAuth()
  return user?.permissions.includes(code) ?? false
}

export function Pill({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`status status-${tone}`}>{children}</span>
}

/** Pill for a value of a `{ KEY: [label, tone] }` map. */
export function StatePill<K extends string>({ map, value }: { map: Record<K, [string, Tone]>; value: K }) {
  const [label, tone] = map[value] ?? [value, 'inactive']
  return <Pill tone={tone}>{label}</Pill>
}

export function PageHead({ title, lede, actions }: { title: string; lede?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-head">
      <div>
        <h1 className="page-title">{title}</h1>
        {lede && <p className="page-lede">{lede}</p>}
      </div>
      {actions && <div className="page-head-actions">{actions}</div>}
    </header>
  )
}

/** Short ID in monospace, the full UUID on hover. */
export function Ref({ id }: { id: string | null | undefined }) {
  return (
    <span className="mono" title={id ?? undefined}>
      {shortId(id)}
    </span>
  )
}

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return (
    <label className="search-box">
      <MagnifyingGlassIcon size={16} aria-hidden />
      <span className="sr-only">{placeholder}</span>
      <input type="search" placeholder={placeholder} value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  )
}

/** Pill-shaped select for toolbars; the empty option means "all". */
export function FilterSelect<K extends string>({
  label,
  value,
  options,
  onChange,
  allLabel = 'Tất cả',
}: {
  label: string
  value: K | ''
  options: [K, string][]
  onChange: (value: K | '') => void
  allLabel?: string
}) {
  return (
    <label className="filter-select">
      <span className="sr-only">{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value as K | '')} aria-label={label}>
        <option value="">
          {label}: {allLabel}
        </option>
        {options.map(([key, text]) => (
          <option key={key} value={key}>
            {text}
          </option>
        ))}
      </select>
    </label>
  )
}

export function Segmented<K extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: K | undefined
  options: [K | undefined, string][]
  onChange: (value: K | undefined) => void
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map(([key, text]) => (
        <button key={key ?? 'all'} type="button" aria-pressed={value === key} onClick={() => onChange(key)}>
          {text}
        </button>
      ))}
    </div>
  )
}

export interface DateRangeValue {
  from: string
  to: string
}

/** Local yyyy-MM-dd for today plus `offset` days. */
export function isoDay(offset = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** API query for a day range: `from` at local midnight, `to` at the midnight after the last day. */
export function rangeQuery(range: DateRangeValue) {
  const start = (day: string) => new Date(`${day}T00:00:00`)
  const next = (day: string) => {
    const date = start(day)
    date.setDate(date.getDate() + 1)
    return date
  }
  return {
    from: range.from ? start(range.from).toISOString() : undefined,
    to: range.to ? next(range.to).toISOString() : undefined,
  }
}

export function DateRange({ value, onChange }: { value: DateRangeValue; onChange: (value: DateRangeValue) => void }) {
  const id = useId()
  return (
    <div className="date-range" role="group" aria-label="Khoảng thời gian">
      <label htmlFor={`${id}-from`} className="sr-only">
        Từ ngày
      </label>
      <input id={`${id}-from`} type="date" value={value.from} max={value.to || undefined} onChange={(e) => onChange({ ...value, from: e.target.value })} />
      <span aria-hidden>đến</span>
      <label htmlFor={`${id}-to`} className="sr-only">
        Đến ngày
      </label>
      <input id={`${id}-to`} type="date" value={value.to} min={value.from || undefined} onChange={(e) => onChange({ ...value, to: e.target.value })} />
      {(value.from || value.to) && (
        <button type="button" className="icon-btn" onClick={() => onChange({ from: '', to: '' })} aria-label="Bỏ lọc ngày" title="Bỏ lọc ngày">
          <XIcon size={14} />
        </button>
      )}
    </div>
  )
}

export function Kpis({ children }: { children: ReactNode }) {
  return <div className="kpis">{children}</div>
}

export function Kpi({ label, value, note, loading }: { label: string; value: ReactNode; note?: ReactNode; loading?: boolean }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">{loading ? <span className="skel" style={{ width: '6ch' }} aria-hidden /> : value}</span>
      {note && <span className="kpi-note">{note}</span>}
    </div>
  )
}

export interface TableColumn<T> {
  header: string
  render: (row: T) => ReactNode
  className?: string
  /** false for cells that must never be cut (e.g. inline controls). Long content is clipped by default. */
  clip?: boolean
}

/**
 * Paged table panel for lists without the catalog CRUD shape: page and size live in the URL,
 * `filterKey` resets to page 1, and the toolbar slot holds the page's filters.
 */
export function ListPanel<T>({
  load,
  filterKey,
  columns,
  rowKey,
  itemLabel,
  emptyIcon,
  toolbar,
  actions,
  reloadToken = 0,
  onLoaded,
  local,
}: {
  load: (query: { page: number; size: number }) => Promise<Page<T>>
  filterKey: string
  columns: TableColumn<T>[]
  rowKey: (row: T) => string
  itemLabel: string
  emptyIcon: ReactNode
  toolbar?: ReactNode
  actions?: (row: T) => ReactNode
  reloadToken?: number
  onLoaded?: (page: Page<T>) => void
  /** Keep the page in component state instead of the URL (for lists inside dialogs). */
  local?: boolean
}) {
  const urlPaging = usePaging(local ? '' : filterKey)
  const localPaging = useLocalPaging(filterKey)
  const { page, size, setPage, setSize } = local ? localPaging : urlPaging
  const list = useAsync(async () => {
    const result = await load({ page, size })
    onLoaded?.(result)
    return result
  }, [filterKey, page, size, reloadToken])
  const rows = list.data?.content ?? []
  const colSpan = columns.length + 1 + (actions ? 1 : 0)
  const [viewing, setViewing] = useState<T | null>(null)

  return (
    <section className="panel data-panel">
      {toolbar && <div className="toolbar">{toolbar}</div>}

      {list.error ? (
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(list.error)}{' '}
          <button type="button" className="link-btn" onClick={list.reload}>
            Thử lại
          </button>
        </div>
      ) : null}

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col" className="col-index">
                STT
              </th>
              {columns.map((column) => (
                <th key={column.header} scope="col" className={column.className}>
                  {column.header}
                </th>
              ))}
              {actions && (
                <th scope="col" className="col-actions">
                  Thao tác
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {list.loading && !list.data
              ? Array.from({ length: 5 }, (_, i) => (
                  <tr key={i} aria-hidden>
                    <td colSpan={colSpan}>
                      <span className="skel skel-row" />
                    </td>
                  </tr>
                ))
              : rows.map((row, index) => (
                  <tr key={rowKey(row)}>
                    <td className="col-index">{rowNumber(list.data, index)}</td>
                    {columns.map((column) => (
                      <td key={column.header} className={column.className}>
                        {column.clip === false ? column.render(row) : <ClipCell onExpand={() => setViewing(row)}>{column.render(row)}</ClipCell>}
                      </td>
                    ))}
                    {actions && (
                      <td className="col-actions">
                        <div className="row-actions">{actions(row)}</div>
                      </td>
                    )}
                  </tr>
                ))}
          </tbody>
        </table>
      </div>

      {list.data && rows.length === 0 && (
        <div className="empty-state">
          {emptyIcon}
          <p>Không có {itemLabel} nào khớp bộ lọc.</p>
        </div>
      )}

      {list.data && (
        <Pagination
          page={page}
          size={size}
          totalPages={list.data.totalPages}
          totalElements={list.data.totalElements}
          itemLabel={itemLabel}
          onPageChange={setPage}
          onSizeChange={setSize}
        />
      )}

      <RowDetailDialog row={viewing} title={`Chi tiết ${itemLabel}`} columns={columns} onClose={() => setViewing(null)} />
    </section>
  )
}

function useLocalPaging(resetKey: string) {
  const [state, setState] = useState({ key: resetKey, page: 0, size: 10 })
  const page = state.key === resetKey ? state.page : 0
  return {
    page,
    size: state.size,
    setPage: (value: number) => setState((s) => ({ ...s, key: resetKey, page: value })),
    setSize: (value: number) => setState({ key: resetKey, page: 0, size: value }),
  }
}

/** Label/value pairs in two columns. */
export function Details({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="details">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value ?? '-'}</dd>
        </div>
      ))}
    </dl>
  )
}

export interface TimelineEntry {
  id: string
  title: ReactNode
  meta: ReactNode
  body?: ReactNode
}

export function Timeline({ entries, empty }: { entries: TimelineEntry[]; empty: string }) {
  if (entries.length === 0) return <p className="muted small">{empty}</p>
  return (
    <ol className="timeline">
      {entries.map((entry) => (
        <li key={entry.id}>
          <div className="timeline-title">{entry.title}</div>
          <div className="timeline-meta">{entry.meta}</div>
          {entry.body && <div className="timeline-body">{entry.body}</div>}
        </li>
      ))}
    </ol>
  )
}

/**
 * Dialog that moves a record to one of its allowed next states, with an optional or required note.
 */
export function StatusDialog<K extends string>({
  open,
  title,
  description,
  options,
  labels,
  noteLabel,
  noteRequired,
  extra,
  onClose,
  onSubmit,
}: {
  open: boolean
  title: string
  description?: ReactNode
  options: K[]
  labels: Record<K, [string, Tone]> | Record<K, string>
  /** Omit to hide the note field. */
  noteLabel?: string
  noteRequired?: boolean
  /** Extra text field (e.g. a bank transaction code) shown for some states. */
  extra?: { label: string; hint?: string; when: (status: K) => boolean }
  onClose: () => void
  onSubmit: (status: K, note: string | null, extra: string | null) => Promise<void>
}) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(open, () => ({ status: options[0] ?? ('' as K), note: '', extra: '' }), [options.join()])
  const label = (key: K) => {
    const entry = (labels as Record<K, [string, Tone] | string>)[key]
    return Array.isArray(entry) ? entry[0] : entry
  }
  const showExtra = extra && form.status && extra.when(form.status)

  return (
    <FormDialog
      open={open}
      title={title}
      description={description}
      busy={busy}
      submitLabel="Cập nhật"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f): Record<string, string> => (noteRequired && !f.note.trim() ? { note: 'Nhập ghi chú.' } : {}),
        async (f) => onSubmit(f.status, f.note.trim() || null, showExtra ? f.extra.trim() || null : null),
      )}
    >
      <fieldset className="choice-group span-2">
        <legend className="field-label">Trạng thái mới</legend>
        <div className="choice-list">
          {options.map((key) => (
            <label key={key} className="choice">
              <input type="radio" name={`${id}-status`} checked={form.status === key} onChange={() => set('status', key)} />
              <span>{label(key)}</span>
            </label>
          ))}
        </div>
      </fieldset>
      {showExtra && (
        <Field label={extra.label} htmlFor={`${id}-extra`} optional hint={extra.hint} wide>
          <input id={`${id}-extra`} value={form.extra} onChange={(e) => set('extra', e.target.value)} maxLength={120} />
        </Field>
      )}
      {noteLabel && (
        <Field label={noteLabel} htmlFor={`${id}-note`} optional={!noteRequired} error={errors.note} wide>
          <textarea id={`${id}-note`} rows={3} value={form.note} onChange={(e) => set('note', e.target.value)} maxLength={1000} aria-invalid={errors.note ? true : undefined} />
        </Field>
      )}
    </FormDialog>
  )
}

export interface PickOption {
  id: string
  title: string
  sub?: string
}

/**
 * Searchable picker (combobox) for any paged source: type to search, arrow keys to move,
 * Enter to pick, Escape to close the list without closing a surrounding dialog.
 */
export function SearchPicker({
  value,
  onChange,
  search,
  placeholder,
  searchPlaceholder,
  emptyText = 'Không có kết quả phù hợp.',
  invalid,
  inputId,
  clearable,
}: {
  value: PickOption | null
  onChange: (option: PickOption | null) => void
  search: (term: string) => Promise<PickOption[]>
  placeholder: string
  searchPlaceholder: string
  emptyText?: string
  invalid?: boolean
  inputId?: string
  clearable?: boolean
}) {
  const listId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const term = useDebounced(query.trim(), 250)
  const results = useAsync(() => (open ? search(term) : Promise.resolve([] as PickOption[])), [open, term])
  const items = results.data ?? []

  useEffect(() => setActive(0), [term, open])
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const pick = (index: number) => {
    const option = items[index]
    if (!option) return
    onChange(option)
    setOpen(false)
    setQuery('')
  }

  return (
    <div className={`combo${invalid ? ' invalid' : ''}`} ref={rootRef}>
      {open ? (
        <label className="combo-input">
          <MagnifyingGlassIcon size={15} aria-hidden />
          <input
            id={inputId}
            autoFocus
            role="combobox"
            aria-expanded
            aria-controls={listId}
            aria-activedescendant={items[active] ? `${listId}-${active}` : undefined}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((i) => Math.min(i + 1, items.length - 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((i) => Math.max(i - 1, 0))
              } else if (e.key === 'Enter') {
                e.preventDefault()
                pick(active)
              } else if (e.key === 'Escape') {
                e.preventDefault()
                e.stopPropagation()
                setOpen(false)
              }
            }}
            placeholder={searchPlaceholder}
          />
        </label>
      ) : (
        <button type="button" id={inputId} className="combo-trigger" onClick={() => setOpen(true)} aria-haspopup="listbox">
          {value ? (
            <span className="combo-value">
              <span className="truncate">{value.title}</span>
              {value.sub && <span className="mono muted small truncate">{value.sub}</span>}
            </span>
          ) : (
            <span className="muted">{placeholder}</span>
          )}
          <CaretDownIcon size={14} aria-hidden />
        </button>
      )}
      {clearable && value && !open && (
        <button type="button" className="combo-clear" onClick={() => onChange(null)} aria-label="Bỏ chọn" title="Bỏ chọn">
          <XIcon size={13} />
        </button>
      )}
      {open && (
        <ul className="combo-list" id={listId} role="listbox">
          {results.loading && !results.data ? (
            <li className="combo-empty">Đang tìm...</li>
          ) : items.length === 0 ? (
            <li className="combo-empty">{emptyText}</li>
          ) : (
            items.map((option, index) => (
              <li
                key={option.id}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={index === active}
                className={index === active ? 'active' : undefined}
                onMouseEnter={() => setActive(index)}
                onMouseDown={(e) => {
                  e.preventDefault()
                  pick(index)
                }}
              >
                <span className="combo-text">
                  <span className="truncate">{option.title}</span>
                  {option.sub && <span className="mono muted small truncate">{option.sub}</span>}
                </span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}

export interface BarPoint {
  key: string
  label: string
  value: number
}

/**
 * Single-series column chart: one brand-blue hue, rounded data ends on a zero baseline,
 * a hover/focus tooltip per column and a sparse x-axis. Negative values (net refunds) drop
 * below the baseline. The page shows the same numbers in a table, so the chart is decorative
 * for screen readers apart from its summary label.
 */
export function BarChart({ points, format, label }: { points: BarPoint[]; format: (value: number) => string; label: string }) {
  const [hover, setHover] = useState<number | null>(null)
  if (points.length === 0) return <p className="muted small">Chưa có dữ liệu trong khoảng này.</p>
  const max = Math.max(0, ...points.map((p) => p.value))
  const min = Math.min(0, ...points.map((p) => p.value))
  const span = max - min || 1
  const zero = (max / span) * 100
  const every = Math.max(1, Math.ceil(points.length / 8))
  const active = hover === null ? null : points[hover]

  return (
    <figure className="chart" aria-label={label}>
      <div className="chart-scale" aria-hidden>
        <span>{format(max)}</span>
        {min < 0 && <span>{format(min)}</span>}
      </div>
      <div className="chart-plot" onMouseLeave={() => setHover(null)}>
        <div className="chart-baseline" style={{ top: `${zero}%` }} aria-hidden />
        {points.map((point, index) => {
          const height = (Math.abs(point.value) / span) * 100
          return (
            <button
              key={point.key}
              type="button"
              className={`chart-col${hover === index ? ' on' : ''}`}
              onMouseEnter={() => setHover(index)}
              onFocus={() => setHover(index)}
              onBlur={() => setHover(null)}
              aria-label={`${point.label}: ${format(point.value)}`}
            >
              <span
                className={`chart-bar${point.value < 0 ? ' neg' : ''}`}
                style={point.value >= 0 ? { bottom: `${100 - zero}%`, height: `${height}%` } : { top: `${zero}%`, height: `${height}%` }}
              />
            </button>
          )
        })}
        {active && hover !== null && (
          <div className="chart-tip" style={{ left: `${((hover + 0.5) / points.length) * 100}%` }} role="status">
            <span className="chart-tip-label">{active.label}</span>
            <strong>{format(active.value)}</strong>
          </div>
        )}
      </div>
      <div className="chart-axis" aria-hidden>
        {points.map((point, index) => (
          <span key={point.key}>{index % every === 0 ? point.label : ''}</span>
        ))}
      </div>
    </figure>
  )
}

/** Horizontal share bars for a status breakdown (count and share of the total). */
export function ShareList({ rows }: { rows: { key: string; label: ReactNode; count: number; extra?: ReactNode }[] }) {
  const total = rows.reduce((sum, row) => sum + row.count, 0)
  if (total === 0) return <p className="muted small">Chưa có dữ liệu.</p>
  return (
    <ul className="share-list">
      {rows.map((row) => (
        <li key={row.key}>
          <div className="share-head">
            <span>{row.label}</span>
            <span className="share-num">
              {countOf(row.count)} <span className="muted">({Math.round((row.count / total) * 100)}%)</span>
            </span>
          </div>
          <span className="share-bar" style={{ width: `${(row.count / total) * 100}%` }} aria-hidden />
          {row.extra && <span className="muted small">{row.extra}</span>}
        </li>
      ))}
    </ul>
  )
}

/** ISO timestamp to the value of an <input type="datetime-local"> in local time. */
export function toLocalInput(iso: string | null | undefined) {
  if (!iso) return ''
  const date = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** <input type="datetime-local"> value to an ISO timestamp, or null when empty. */
export function fromLocalInput(value: string) {
  return value ? new Date(value).toISOString() : null
}

/** "dd/mm/yyyy hh:mm - dd/mm/yyyy hh:mm" for a campaign window. */
export function windowOf(startAt: string | null, endAt: string | null) {
  if (!startAt && !endAt) return 'Không giới hạn'
  return `${startAt ? whenOf(startAt) : 'Ngay'} - ${endAt ? whenOf(endAt) : 'Không hạn'}`
}
