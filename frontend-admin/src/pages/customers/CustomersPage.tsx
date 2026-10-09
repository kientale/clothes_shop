import { LinkIcon, MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, TrashIcon, UsersThreeIcon } from '@phosphor-icons/react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { accessErrorMessage, fieldErrors } from '../../api/access'
import { CUSTOMER_STATUS_LABEL, GENDER_LABEL, customers as customerApi, formatBirthDate } from '../../api/customers'
import type { CustomerResponse, CustomerStatus, Gender, LinkableAccountResponse } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { AvatarField } from '../../components/AvatarField'
import { ConfirmDialog, Modal, Pagination, initials, rowNumber, useDebounced, usePaging } from '../../components/ui'
import { formatDate } from '../../format'
import { useAsync } from '../../hooks'

const STATUSES = Object.keys(CUSTOMER_STATUS_LABEL) as CustomerStatus[]
const GENDERS = Object.keys(GENDER_LABEL) as Gender[]
const PHONE = /^\+?[0-9]{8,15}$/
const URL_PATTERN = /^https?:\/\/\S+$/

type Dialog = { kind: 'create' } | { kind: 'edit'; customer: CustomerResponse } | { kind: 'delete'; customer: CustomerResponse }

export default function CustomersPage() {
  const { user } = useAuth()
  const canWrite = user?.permissions.includes('CUSTOMER_WRITE') ?? false
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<CustomerStatus | undefined>()
  const term = useDebounced(search.trim())
  const { page, size, setPage, setSize } = usePaging(`${term}|${status ?? ''}`)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const toast = useToast()

  const list = useAsync(() => customerApi.list({ search: term, status, page, size }), [term, status, page, size])
  const summary = useAsync(() => customerApi.summary(), [])

  const done = (message: string) => {
    setDialog(null)
    toast.success(message)
    list.reload()
    summary.reload()
  }

  const rows = list.data?.content ?? []
  const count = (value?: CustomerStatus) => {
    const data = summary.data
    if (!data) return null
    return value === undefined ? data.total : data[value.toLowerCase() as 'active' | 'inactive' | 'blocked']
  }

  return (
    <div className="page page-wide">
      <header className="page-head">
        <div>
          <h1 className="page-title">Khách hàng</h1>
          <p className="page-lede">Hồ sơ người mua. Hồ sơ có thể gắn với tài khoản đăng nhập, hoặc là khách vãng lai không có tài khoản.</p>
        </div>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={() => setDialog({ kind: 'create' })}>
            <PlusIcon size={16} weight="bold" /> Thêm khách hàng
          </button>
        )}
      </header>


      <section className="panel data-panel">
        <div className="toolbar">
          <label className="search-box">
            <MagnifyingGlassIcon size={16} aria-hidden />
            <span className="sr-only">Tìm theo tên, số điện thoại hoặc email</span>
            <input
              type="search"
              placeholder="Tìm theo tên, số điện thoại hoặc email"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <div className="segmented" role="group" aria-label="Lọc theo trạng thái">
            {[undefined, ...STATUSES].map((value) => (
              <button key={value ?? 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>
                {value ? CUSTOMER_STATUS_LABEL[value] : 'Tất cả'}
                {count(value) !== null && <span className="seg-count">{count(value)}</span>}
              </button>
            ))}
          </div>
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
                <th scope="col">Khách hàng</th>
                <th scope="col">Số điện thoại</th>
                <th scope="col">Giới tính</th>
                <th scope="col">Ngày sinh</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Ngày tạo</th>
                {canWrite && (
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
                      <td colSpan={canWrite ? 8 : 7}>
                        <span className="skel skel-row" />
                      </td>
                    </tr>
                  ))
                : rows.map((customer, index) => (
                    <tr key={customer.id}>
                      <td className="col-index">{rowNumber(list.data, index)}</td>
                      <td>
                        <div className="person">
                          {customer.avatarUrl ? (
                            <img className="person-avatar" src={customer.avatarUrl} alt="" loading="lazy" />
                          ) : (
                            <span className="person-avatar" aria-hidden>
                              {initials(customer.fullName)}
                            </span>
                          )}
                          <div className="person-text">
                            <span className="person-name">{customer.fullName}</span>
                            {customer.email ? (
                              <span className="person-sub">{customer.email}</span>
                            ) : (
                              <span className="person-sub muted-italic">
                                {customer.accountId ? 'Tài khoản đã bị xóa' : 'Khách vãng lai'}
                              </span>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="nowrap">{customer.phone ?? <span className="muted">-</span>}</td>
                      <td>{customer.gender ? GENDER_LABEL[customer.gender] : <span className="muted">-</span>}</td>
                      <td className="nowrap">{customer.dateOfBirth ? formatBirthDate(customer.dateOfBirth) : <span className="muted">-</span>}</td>
                      <td>
                        <span className={`status status-${customer.status.toLowerCase()}`}>{CUSTOMER_STATUS_LABEL[customer.status]}</span>
                      </td>
                      <td className="muted nowrap">{formatDate(customer.createdAt)}</td>
                      {canWrite && (
                        <td className="col-actions">
                          <div className="row-actions">
                            <button
                              type="button"
                              className="icon-btn"
                              onClick={() => setDialog({ kind: 'edit', customer })}
                              aria-label={`Sửa ${customer.fullName}`}
                              title="Sửa"
                            >
                              <PencilSimpleIcon size={16} />
                            </button>
                            <button
                              type="button"
                              className="icon-btn danger"
                              onClick={() => setDialog({ kind: 'delete', customer })}
                              aria-label={`Xóa ${customer.fullName}`}
                              title="Xóa"
                            >
                              <TrashIcon size={16} />
                            </button>
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
            <UsersThreeIcon size={36} aria-hidden />
            <p>{term || status ? 'Không có khách hàng nào khớp bộ lọc.' : 'Chưa có khách hàng nào.'}</p>
          </div>
        )}

        {list.data && (
          <Pagination
            page={page}
            size={size}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            itemLabel="khách hàng"
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        )}
      </section>

      <CustomerFormDialog
        dialog={dialog?.kind === 'create' || dialog?.kind === 'edit' ? dialog : null}
        onClose={() => setDialog(null)}
        onSaved={(customer, created) =>
          done(created ? `Đã thêm khách hàng ${customer.fullName}.` : `Đã cập nhật khách hàng ${customer.fullName}.`)
        }
      />

      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title="Xóa hồ sơ khách hàng"
        message={
          dialog?.kind === 'delete' ? (
            <>
              Xóa hồ sơ <strong>{dialog.customer.fullName}</strong>?
              {dialog.customer.email
                ? ` Tài khoản đăng nhập ${dialog.customer.email} vẫn được giữ, nhưng không thể gắn hồ sơ mới cho tài khoản này.`
                : ' Hồ sơ sẽ không còn hiện trong danh sách.'}
            </>
          ) : null
        }
        confirmLabel="Xóa hồ sơ"
        formatError={accessErrorMessage}
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          if (dialog?.kind !== 'delete') return
          await customerApi.remove(dialog.customer.id)
          done(`Đã xóa hồ sơ ${dialog.customer.fullName}.`)
        }}
      />
    </div>
  )
}

interface FormState {
  fullName: string
  phone: string
  gender: Gender | ''
  dateOfBirth: string
  avatarUrl: string
  status: CustomerStatus
  accountId: string | null
}

const EMPTY: FormState = { fullName: '', phone: '', gender: '', dateOfBirth: '', avatarUrl: '', status: 'ACTIVE', accountId: null }

/** Yesterday as yyyy-MM-dd in local time: the backend requires a date of birth in the past. */
function latestBirthDate() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function CustomerFormDialog({
  dialog,
  onClose,
  onSaved,
}: {
  dialog: { kind: 'create' } | { kind: 'edit'; customer: CustomerResponse } | null
  onClose: () => void
  onSaved: (customer: CustomerResponse, created: boolean) => void
}) {
  const id = useId()
  const editing = dialog?.kind === 'edit' ? dialog.customer : null
  const [form, setForm] = useState<FormState>(EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (!dialog) return
    setErrors({})
    setError(undefined)
    setForm(
      editing
        ? {
            fullName: editing.fullName,
            phone: editing.phone ?? '',
            gender: editing.gender ?? '',
            dateOfBirth: editing.dateOfBirth ?? '',
            avatarUrl: editing.avatarUrl ?? '',
            status: editing.status,
            accountId: editing.accountId,
          }
        : EMPTY,
    )
  }, [dialog, editing])

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => {
      if (!(key in current)) return current
      const { [key]: _cleared, ...rest } = current
      return rest
    })
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!form.fullName.trim()) next.fullName = 'Nhập họ tên.'
    else if (form.fullName.trim().length > 150) next.fullName = 'Họ tên tối đa 150 ký tự.'
    if (form.phone.trim() && !PHONE.test(form.phone.trim())) next.phone = 'Số điện thoại gồm 8-15 chữ số, có thể bắt đầu bằng +.'
    if (form.dateOfBirth && form.dateOfBirth > latestBirthDate()) next.dateOfBirth = 'Ngày sinh phải ở trong quá khứ.'
    if (form.avatarUrl.trim() && !URL_PATTERN.test(form.avatarUrl.trim())) next.avatarUrl = 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.'
    setErrors(next)
    setError(undefined)
    if (Object.keys(next).length) return
    setBusy(true)
    const body = {
      fullName: form.fullName.trim(),
      phone: form.phone.trim() || null,
      gender: form.gender || null,
      dateOfBirth: form.dateOfBirth || null,
      avatarUrl: form.avatarUrl.trim() || null,
      status: form.status,
    }
    try {
      const saved = editing ? await customerApi.update(editing.id, body) : await customerApi.create({ ...body, accountId: form.accountId })
      onSaved(saved, !editing)
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const field = (key: keyof FormState) => ({
    id: `${id}-${key}`,
    'aria-invalid': errors[key] ? true : undefined,
    'aria-describedby': errors[key] ? `${id}-${key}-error` : undefined,
  })
  const fieldError = (key: keyof FormState) =>
    errors[key] ? (
      <span id={`${id}-${key}-error`} className="field-error">
        {errors[key]}
      </span>
    ) : null

  return (
    <Modal
      open={dialog !== null}
      title={editing ? 'Sửa khách hàng' : 'Thêm khách hàng'}
      description={editing ? editing.fullName : 'Tạo hồ sơ cho khách vãng lai, hoặc gắn với một tài khoản khách hàng chưa có hồ sơ.'}
      onClose={busy ? () => {} : onClose}
      size="lg"
      footer={
        <>
          <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="submit" form={`${id}-form`} className="btn btn-primary" disabled={busy || uploading}>
            {busy ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Thêm khách hàng'}
          </button>
        </>
      }
    >
      <form id={`${id}-form`} className="form-grid" onSubmit={submit} noValidate>
        {error ? (
          <div className="banner banner-error span-2" role="alert">
            {accessErrorMessage(error)}
          </div>
        ) : null}

        <div className="field">
          <label className="field-label" htmlFor={`${id}-fullName`}>
            Họ tên
          </label>
          <input {...field('fullName')} value={form.fullName} onChange={(e) => set('fullName', e.target.value)} maxLength={150} autoComplete="off" />
          {fieldError('fullName')}
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-phone`}>
            Số điện thoại <span className="optional">(không bắt buộc)</span>
          </label>
          <input {...field('phone')} type="tel" value={form.phone} onChange={(e) => set('phone', e.target.value)} placeholder="0912345678" />
          {fieldError('phone')}
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-gender`}>
            Giới tính
          </label>
          <select {...field('gender')} value={form.gender} onChange={(e) => set('gender', e.target.value as Gender | '')}>
            <option value="">Không xác định</option>
            {GENDERS.map((value) => (
              <option key={value} value={value}>
                {GENDER_LABEL[value]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-dateOfBirth`}>
            Ngày sinh <span className="optional">(không bắt buộc)</span>
          </label>
          <input {...field('dateOfBirth')} type="date" value={form.dateOfBirth} max={latestBirthDate()} onChange={(e) => set('dateOfBirth', e.target.value)} />
          {fieldError('dateOfBirth')}
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-status`}>
            Trạng thái
          </label>
          <select {...field('status')} value={form.status} onChange={(e) => set('status', e.target.value as CustomerStatus)}>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {CUSTOMER_STATUS_LABEL[value]}
              </option>
            ))}
          </select>
          <span className="field-hint">Trạng thái hồ sơ không khóa việc đăng nhập.</span>
        </div>
        <div className="field span-2">
          <label className="field-label" htmlFor={`${id}-avatarUrl-file`}>
            Ảnh đại diện <span className="optional">(không bắt buộc)</span>
          </label>
          <AvatarField
            id={`${id}-avatarUrl`}
            value={form.avatarUrl}
            name={form.fullName}
            error={errors.avatarUrl}
            onChange={(url) => set('avatarUrl', url)}
            onUploadingChange={setUploading}
          />
        </div>

        {editing ? (
          <div className="field span-2">
            <span className="field-label">Tài khoản đăng nhập</span>
            <div className="field-box linked-box">
              <LinkIcon size={16} aria-hidden />
              {editing.email ?? (editing.accountId ? 'Tài khoản đã bị xóa' : 'Khách vãng lai, không có tài khoản')}
            </div>
            <span className="field-hint">Liên kết tài khoản không thay đổi được sau khi tạo hồ sơ.</span>
          </div>
        ) : (
          dialog && <AccountPicker value={form.accountId} onChange={(value) => set('accountId', value)} />
        )}
      </form>
    </Modal>
  )
}

/** Picks a customer login account that has no profile yet, or none (guest). */
function AccountPicker({ value, onChange }: { value: string | null; onChange: (accountId: string | null) => void }) {
  const id = useId()
  const [query, setQuery] = useState('')
  const term = useDebounced(query.trim())
  const accounts = useAsync(() => customerApi.linkableAccounts(term), [term])
  const [picked, setPicked] = useState<LinkableAccountResponse | null>(null)
  // Keep the chosen account visible even when a new search no longer returns it.
  const options = picked && !accounts.data?.some((account) => account.id === picked.id) ? [picked, ...(accounts.data ?? [])] : (accounts.data ?? [])

  return (
    <fieldset className="field span-2 check-group">
      <legend className="field-label">Tài khoản đăng nhập</legend>
      <label className="search-box picker-search" htmlFor={`${id}-search`}>
        <MagnifyingGlassIcon size={16} aria-hidden />
        <input
          id={`${id}-search`}
          type="search"
          placeholder="Tìm tài khoản khách hàng chưa có hồ sơ theo email"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </label>
      <div className="check-list">
        <label className="check">
          <input type="radio" name={`${id}-account`} checked={value === null} onChange={() => onChange(null)} />
          <span>
            <span className="check-title">Khách vãng lai</span>
            <span className="check-sub">Không gắn tài khoản đăng nhập</span>
          </span>
        </label>
        {options.map((account) => (
          <label key={account.id} className="check">
            <input
              type="radio"
              name={`${id}-account`}
              checked={value === account.id}
              onChange={() => {
                setPicked(account)
                onChange(account.id)
              }}
            />
            <span>
              <span className="check-title truncate">{account.email}</span>
              <span className="check-sub">Đăng ký {formatDate(account.createdAt)}</span>
            </span>
          </label>
        ))}
      </div>
      {accounts.error ? (
        <span className="field-error">{accessErrorMessage(accounts.error)}</span>
      ) : accounts.data && accounts.data.length === 0 ? (
        <span className="field-hint">
          {term ? 'Không có tài khoản nào khớp.' : 'Mọi tài khoản khách hàng đều đã có hồ sơ.'}
        </span>
      ) : (
        <span className="field-hint">Hiện tối đa 10 tài khoản mới nhất, gõ email để tìm thêm.</span>
      )}
    </fieldset>
  )
}
