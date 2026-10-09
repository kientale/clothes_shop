import { MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, TrashIcon } from '@phosphor-icons/react'
import { useState, type ReactNode } from 'react'
import { accessErrorMessage } from '../api/access'
import type { Page } from '../api/types'
import { useAsync } from '../hooks'
import { ClipCell, RowDetailDialog } from './TableCell'
import { useToast } from './Toast'
import { ConfirmDialog, Pagination, rowNumber, useDebounced, usePaging } from './ui'

export interface Column<T> {
  header: string
  render: (row: T) => ReactNode
  className?: string
  /** false for cells that must never be cut (e.g. inline controls). Long content is clipped by default. */
  clip?: boolean
}

export interface FormSlot<T> {
  open: boolean
  /** null when creating. */
  item: T | null
  onClose: () => void
  /** Closes the dialog, shows a toast and reloads the list. */
  onSaved: (message: string) => void
}

/**
 * Shared list screen for catalog resources: header with add button, search, status filter,
 * extra filters, numbered table with edit/delete actions, pagination, delete confirmation
 * and toasts. Each page supplies its columns and its form.
 */
export function ResourcePage<T extends { id: string }>({
  title,
  lede,
  addLabel,
  itemLabel,
  searchPlaceholder,
  canWrite,
  statusLabels,
  filters,
  filterKey = '',
  load,
  columns,
  rowLabel,
  rowActions,
  deleteTitle,
  deleteMessage,
  remove,
  renderForm,
  emptyIcon,
  onChanged,
  headerActions,
  reloadToken = 0,
  belowHead,
}: {
  title: string
  lede: ReactNode
  addLabel: string
  /** Noun for counts, e.g. "thương hiệu". */
  itemLabel: string
  /** Omit when the list endpoint has no search. */
  searchPlaceholder?: string
  canWrite: boolean
  statusLabels?: Record<string, string>
  /** Page-specific filter controls placed after the status filter. */
  filters?: ReactNode
  /** Changes with the page-specific filters, so the list reloads and returns to page 1. */
  filterKey?: string
  load: (query: { search: string; status?: string; page: number; size: number }) => Promise<Page<T>>
  columns: Column<T>[]
  rowLabel: (row: T) => string
  /** Extra buttons before edit/delete. */
  rowActions?: (row: T) => ReactNode
  deleteTitle: string
  deleteMessage: (row: T) => ReactNode
  remove: (row: T) => Promise<unknown>
  renderForm: (slot: FormSlot<T>) => ReactNode
  emptyIcon: ReactNode
  /** Called after any create, update or delete (e.g. to refresh picker options). */
  onChanged?: () => void
  /** Extra buttons next to the add button. */
  headerActions?: ReactNode
  /** Bump to reload the current page after a change made outside this component. */
  reloadToken?: number
  /** Content between the page header and the table panel (e.g. view tabs or KPIs). */
  belowHead?: ReactNode
}) {
  const toast = useToast()
  // ?search= lets other screens (e.g. the dashboard command box) open the list pre-filtered.
  const [search, setSearch] = useState(() => new URLSearchParams(window.location.search).get('search') ?? '')
  const [status, setStatus] = useState<string | undefined>()
  const term = useDebounced(search.trim())
  const { page, size, setPage, setSize } = usePaging(`${term}|${status ?? ''}|${filterKey}`)
  const [editing, setEditing] = useState<{ item: T | null } | null>(null)
  const [deleting, setDeleting] = useState<T | null>(null)
  const [viewing, setViewing] = useState<T | null>(null)

  const list = useAsync(() => load({ search: term, status, page, size }), [term, status, page, size, filterKey, reloadToken])
  const rows = list.data?.content ?? []
  const done = (message: string) => {
    setEditing(null)
    setDeleting(null)
    toast.success(message)
    list.reload()
    onChanged?.()
  }
  const colSpan = columns.length + 1 + (canWrite || rowActions ? 1 : 0)

  return (
    <div className="page page-wide">
      <header className="page-head">
        <div>
          <h1 className="page-title">{title}</h1>
          <p className="page-lede">{lede}</p>
        </div>
        {canWrite && (
          <div className="page-head-actions">
            {headerActions}
            <button type="button" className="btn btn-primary" onClick={() => setEditing({ item: null })}>
              <PlusIcon size={16} weight="bold" /> {addLabel}
            </button>
          </div>
        )}
      </header>

      {belowHead}

      <section className="panel data-panel">
        <div className="toolbar">
          {searchPlaceholder && (
            <label className="search-box">
              <MagnifyingGlassIcon size={16} aria-hidden />
              <span className="sr-only">{searchPlaceholder}</span>
              <input type="search" placeholder={searchPlaceholder} value={search} onChange={(event) => setSearch(event.target.value)} />
            </label>
          )}
          {statusLabels && (
            <div className="segmented" role="group" aria-label="Lọc theo trạng thái">
              {[undefined, ...Object.keys(statusLabels)].map((value) => (
                <button key={value ?? 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>
                  {value ? statusLabels[value] : 'Tất cả'}
                </button>
              ))}
            </div>
          )}
          {filters}
        </div>

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
                {(canWrite || rowActions) && (
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
                    <tr key={row.id}>
                      <td className="col-index">{rowNumber(list.data, index)}</td>
                      {columns.map((column) => (
                        <td key={column.header} className={column.className}>
                          {column.clip === false ? column.render(row) : <ClipCell onExpand={() => setViewing(row)}>{column.render(row)}</ClipCell>}
                        </td>
                      ))}
                      {(canWrite || rowActions) && (
                        <td className="col-actions">
                          <div className="row-actions">
                            {rowActions?.(row)}
                            {canWrite && (
                              <>
                                <button type="button" className="icon-btn" onClick={() => setEditing({ item: row })} aria-label={`Sửa ${rowLabel(row)}`} title="Sửa">
                                  <PencilSimpleIcon size={16} />
                                </button>
                                <button type="button" className="icon-btn danger" onClick={() => setDeleting(row)} aria-label={`Xóa ${rowLabel(row)}`} title="Xóa">
                                  <TrashIcon size={16} />
                                </button>
                              </>
                            )}
                          </div>
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
            <p>{term || status || filterKey ? `Không có ${itemLabel} nào khớp bộ lọc.` : `Chưa có ${itemLabel} nào.`}</p>
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
      </section>

      <RowDetailDialog row={viewing} title={viewing ? `Chi tiết ${rowLabel(viewing)}` : ''} columns={columns} onClose={() => setViewing(null)} />

      {renderForm({ open: editing !== null, item: editing?.item ?? null, onClose: () => setEditing(null), onSaved: done })}

      <ConfirmDialog
        open={deleting !== null}
        title={deleteTitle}
        message={deleting ? deleteMessage(deleting) : null}
        confirmLabel="Xóa"
        formatError={accessErrorMessage}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          if (!deleting) return
          await remove(deleting)
          done(`Đã xóa ${rowLabel(deleting)}.`)
        }}
      />
    </div>
  )
}

/** Small status pill shared by the catalog tables. */
export function StatusPill({ value, label }: { value: string; label: string }) {
  const tone = value === 'ACTIVE' ? 'active' : value === 'DRAFT' ? 'inactive' : value === 'ARCHIVED' ? 'locked' : 'inactive'
  return <span className={`status status-${tone}`}>{label}</span>
}
