import { KeyIcon, MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, TrashIcon, UserCircleIcon } from '@phosphor-icons/react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { STATUS_LABEL, accessErrorMessage, adminAccounts, fieldErrors, roles as roleApi } from '../../api/access'
import type { AccountStatus, AdminAccountResponse, RoleResponse } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { AvatarField } from '../../components/AvatarField'
import { ConfirmDialog, Modal, Pagination, initials, rowNumber, useDebounced, usePaging } from '../../components/ui'
import { formatDate } from '../../format'
import { useAsync } from '../../hooks'

const STATUSES = Object.keys(STATUS_LABEL) as AccountStatus[]
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE = /^\+?[0-9]{8,15}$/
const URL_PATTERN = /^https?:\/\/\S+$/

type Dialog =
  | { kind: 'create' }
  | { kind: 'edit'; account: AdminAccountResponse }
  | { kind: 'password'; account: AdminAccountResponse }
  | { kind: 'delete'; account: AdminAccountResponse }

export default function AdminAccountsPage() {
  const { user, updateAvatar } = useAuth()
  const canWrite = user?.permissions.includes('ACCOUNT_WRITE') ?? false
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<AccountStatus | undefined>()
  const term = useDebounced(search.trim())
  const { page, size, setPage, setSize } = usePaging(`${term}|${status ?? ''}`)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const toast = useToast()

  const list = useAsync(() => adminAccounts.list({ search: term, status, page, size }), [term, status, page, size])
  // Role names for the table and the form; only loaded for operators who may assign roles.
  const roleList = useAsync(() => (canWrite ? roleApi.all() : Promise.resolve([] as RoleResponse[])), [canWrite])

  const done = (message: string) => {
    setDialog(null)
    toast.success(message)
    list.reload()
  }

  const accounts = list.data?.content ?? []

  return (
    <div className="page page-wide">
      <header className="page-head">
        <div>
          <h1 className="page-title">Tài khoản Admin</h1>
          <p className="page-lede">Người có quyền đăng nhập trang quản trị. Mỗi tài khoản luôn giữ vai trò ADMIN.</p>
        </div>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={() => setDialog({ kind: 'create' })}>
            <PlusIcon size={16} weight="bold" /> Thêm tài khoản
          </button>
        )}
      </header>


      <section className="panel data-panel">
        <div className="toolbar">
          <label className="search-box">
            <MagnifyingGlassIcon size={16} aria-hidden />
            <span className="sr-only">Tìm theo email hoặc họ tên</span>
            <input
              type="search"
              placeholder="Tìm theo email hoặc họ tên"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <div className="segmented" role="group" aria-label="Lọc theo trạng thái">
            <button type="button" aria-pressed={status === undefined} onClick={() => setStatus(undefined)}>
              Tất cả
            </button>
            {STATUSES.map((value) => (
              <button key={value} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>
                {STATUS_LABEL[value]}
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
                <th scope="col">Tài khoản</th>
                <th scope="col">Vai trò</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Đăng nhập gần nhất</th>
                {canWrite && (
                  <th scope="col" className="col-actions">
                    Thao tác
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {list.loading && !list.data
                ? Array.from({ length: 4 }, (_, i) => (
                    <tr key={i} aria-hidden>
                      <td colSpan={canWrite ? 6 : 5}>
                        <span className="skel skel-row" />
                      </td>
                    </tr>
                  ))
                : accounts.map((account, index) => {
                    const self = account.id === user?.id
                    const name = account.fullName?.trim() || account.email
                    return (
                      <tr key={account.id}>
                        <td className="col-index">{rowNumber(list.data, index)}</td>
                        <td>
                          <div className="person">
                            {account.avatarUrl ? (
                              <img className="person-avatar" src={account.avatarUrl} alt="" loading="lazy" />
                            ) : (
                              <span className="person-avatar" aria-hidden>
                                {initials(name)}
                              </span>
                            )}
                            <div className="person-text">
                              <span className="person-name">
                                {name}
                                {self && <span className="tag">Bạn</span>}
                              </span>
                              <span className="person-sub">{account.email}</span>
                            </div>
                          </div>
                        </td>
                        <td>
                          <div className="chips">
                            {account.roles.map((code) => (
                              <span key={code} className={`chip chip-sm${code === 'ADMIN' ? ' chip-dark' : ''}`}>
                                {code}
                              </span>
                            ))}
                          </div>
                        </td>
                        <td>
                          <span className={`status status-${account.status.toLowerCase()}`}>{STATUS_LABEL[account.status]}</span>
                        </td>
                        <td className="muted nowrap">{account.lastLoginAt ? formatDate(account.lastLoginAt) : 'Chưa đăng nhập'}</td>
                        {canWrite && (
                          <td className="col-actions">
                            <div className="row-actions">
                              <button
                                type="button"
                                className="icon-btn"
                                onClick={() => setDialog({ kind: 'edit', account })}
                                aria-label={`Sửa ${name}`}
                                title="Sửa"
                              >
                                <PencilSimpleIcon size={16} />
                              </button>
                              <button
                                type="button"
                                className="icon-btn"
                                onClick={() => setDialog({ kind: 'password', account })}
                                aria-label={`Đặt lại mật khẩu cho ${name}`}
                                title="Đặt lại mật khẩu"
                              >
                                <KeyIcon size={16} />
                              </button>
                              <button
                                type="button"
                                className="icon-btn danger"
                                onClick={() => setDialog({ kind: 'delete', account })}
                                disabled={self}
                                aria-label={`Xóa ${name}`}
                                title={self ? 'Không thể xóa tài khoản của chính bạn' : 'Xóa'}
                              >
                                <TrashIcon size={16} />
                              </button>
                            </div>
                          </td>
                        )}
                      </tr>
                    )
                  })}
            </tbody>
          </table>
        </div>

        {list.data && accounts.length === 0 && (
          <div className="empty-state">
            <UserCircleIcon size={36} aria-hidden />
            <p>{term || status ? 'Không có tài khoản nào khớp bộ lọc.' : 'Chưa có tài khoản admin nào.'}</p>
          </div>
        )}

        {list.data && (
          <Pagination
            page={page}
            size={size}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            itemLabel="tài khoản"
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        )}
      </section>

      <AccountFormDialog
        dialog={dialog?.kind === 'create' || dialog?.kind === 'edit' ? dialog : null}
        roles={roleList.data ?? []}
        rolesError={roleList.error}
        selfId={user?.id}
        onClose={() => setDialog(null)}
        onSaved={(account, created) => {
          if (account.id === user?.id) updateAvatar(account.avatarUrl)
          done(created ? `Đã tạo tài khoản ${account.email}.` : `Đã cập nhật tài khoản ${account.email}.`)
        }}
      />

      <PasswordDialog
        account={dialog?.kind === 'password' ? dialog.account : null}
        onClose={() => setDialog(null)}
        onSaved={(account) => done(`Đã đặt lại mật khẩu cho ${account.email}.`)}
      />

      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title="Xóa tài khoản admin"
        message={
          dialog?.kind === 'delete' ? (
            <>
              Xóa <strong>{dialog.account.email}</strong>? Người này sẽ không đăng nhập được trang quản trị nữa.
            </>
          ) : null
        }
        confirmLabel="Xóa tài khoản"
        formatError={accessErrorMessage}
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          if (dialog?.kind !== 'delete') return
          await adminAccounts.remove(dialog.account.id)
          // Step back a page when the last row on a later page is removed.
          if (accounts.length === 1 && page > 0) setPage(page - 1)
          done(`Đã xóa tài khoản ${dialog.account.email}.`)
        }}
      />
    </div>
  )
}

interface FormState {
  email: string
  fullName: string
  phone: string
  avatarUrl: string
  password: string
  status: AccountStatus
  roleIds: string[]
}

function AccountFormDialog({
  dialog,
  roles,
  rolesError,
  selfId,
  onClose,
  onSaved,
}: {
  dialog: { kind: 'create' } | { kind: 'edit'; account: AdminAccountResponse } | null
  roles: RoleResponse[]
  rolesError: unknown
  selfId?: string
  onClose: () => void
  onSaved: (account: AdminAccountResponse, created: boolean) => void
}) {
  const id = useId()
  const editing = dialog?.kind === 'edit' ? dialog.account : null
  const adminRole = roles.find((role) => role.code === 'ADMIN')
  const assignable = roles.filter((role) => role.code !== 'CUSTOMER')
  const [form, setForm] = useState<FormState>(emptyForm())
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
            email: editing.email,
            fullName: editing.fullName ?? '',
            phone: editing.phone ?? '',
            avatarUrl: editing.avatarUrl ?? '',
            password: '',
            status: editing.status,
            roleIds: editing.roleIds,
          }
        : emptyForm(),
    )
  }, [dialog, editing])

  // The ADMIN role is mandatory, so keep it selected as soon as the role list is known.
  const roleIds = adminRole && !form.roleIds.includes(adminRole.id) ? [adminRole.id, ...form.roleIds] : form.roleIds
  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    // Editing a field clears its stale error until the next submit.
    setErrors((current) => {
      if (!(key in current)) return current
      const { [key]: _cleared, ...rest } = current
      return rest
    })
  }

  const validate = () => {
    const next: Record<string, string> = {}
    if (!EMAIL.test(form.email.trim())) next.email = 'Nhập email hợp lệ.'
    if (!form.fullName.trim()) next.fullName = 'Nhập họ tên.'
    else if (form.fullName.trim().length > 150) next.fullName = 'Họ tên tối đa 150 ký tự.'
    if (form.phone.trim() && !PHONE.test(form.phone.trim())) next.phone = 'Số điện thoại gồm 8-15 chữ số, có thể bắt đầu bằng +.'
    if (form.avatarUrl.trim() && !URL_PATTERN.test(form.avatarUrl.trim())) next.avatarUrl = 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.'
    if (!editing && (form.password.length < 8 || form.password.length > 72)) next.password = 'Mật khẩu phải có từ 8 đến 72 ký tự.'
    if (!adminRole) next.roleIds = 'Chưa tải được danh sách vai trò.'
    return next
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const next = validate()
    setErrors(next)
    setError(undefined)
    if (Object.keys(next).length) return
    setBusy(true)
    const common = {
      email: form.email.trim(),
      fullName: form.fullName.trim(),
      phone: form.phone.trim() || null,
      avatarUrl: form.avatarUrl.trim() || null,
      roleIds,
    }
    try {
      const saved = editing
        ? await adminAccounts.update(editing.id, { ...common, status: form.status })
        : await adminAccounts.create({ ...common, password: form.password })
      onSaved(saved, !editing)
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const self = editing?.id === selfId
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
      title={editing ? 'Sửa tài khoản admin' : 'Thêm tài khoản admin'}
      description={editing ? editing.email : 'Tài khoản mới có thể đăng nhập ngay bằng email và mật khẩu này.'}
      onClose={busy ? () => {} : onClose}
      size="lg"
      footer={
        <>
          <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="submit" form={`${id}-form`} className="btn btn-primary" disabled={busy || uploading}>
            {busy ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Tạo tài khoản'}
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
          <label className="field-label" htmlFor={`${id}-email`}>
            Email
          </label>
          <input {...field('email')} type="email" value={form.email} onChange={(e) => set('email', e.target.value)} maxLength={254} autoComplete="off" />
          {fieldError('email')}
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-phone`}>
            Số điện thoại <span className="optional">(không bắt buộc)</span>
          </label>
          <input {...field('phone')} type="tel" value={form.phone} onChange={(e) => set('phone', e.target.value)} placeholder="+84901234567" />
          {fieldError('phone')}
        </div>
        {editing ? (
          <div className="field">
            <label className="field-label" htmlFor={`${id}-status`}>
              Trạng thái
            </label>
            <select {...field('status')} value={form.status} onChange={(e) => set('status', e.target.value as AccountStatus)} disabled={self}>
              {STATUSES.map((value) => (
                <option key={value} value={value}>
                  {STATUS_LABEL[value]}
                </option>
              ))}
            </select>
            {self ? <span className="field-hint">Bạn không thể đổi trạng thái tài khoản của chính mình.</span> : fieldError('status')}
          </div>
        ) : (
          <div className="field">
            <label className="field-label" htmlFor={`${id}-password`}>
              Mật khẩu
            </label>
            <input
              {...field('password')}
              type="password"
              value={form.password}
              onChange={(e) => set('password', e.target.value)}
              autoComplete="new-password"
              maxLength={72}
            />
            {fieldError('password') ?? <span className="field-hint">Từ 8 đến 72 ký tự.</span>}
          </div>
        )}
        <div className="field span-2">
          <label className="field-label" htmlFor={`${id}-avatarUrl-file`}>
            Ảnh đại diện <span className="optional">(không bắt buộc)</span>
          </label>
          <AvatarField
            id={`${id}-avatarUrl`}
            value={form.avatarUrl}
            name={form.fullName || form.email}
            error={errors.avatarUrl}
            onChange={(url) => set('avatarUrl', url)}
            onUploadingChange={setUploading}
          />
        </div>

        <fieldset className="field span-2 check-group">
          <legend className="field-label">Vai trò</legend>
          {rolesError ? (
            <span className="field-error">Không tải được danh sách vai trò: {accessErrorMessage(rolesError)}</span>
          ) : assignable.length === 0 ? (
            <span className="skel skel-row" aria-hidden />
          ) : (
            <div className="check-list">
              {assignable.map((role) => {
                const locked = role.code === 'ADMIN'
                return (
                  <label key={role.id} className={`check${locked ? ' locked' : ''}`}>
                    <input
                      type="checkbox"
                      checked={roleIds.includes(role.id)}
                      disabled={locked}
                      onChange={(e) =>
                        set('roleIds', e.target.checked ? [...roleIds, role.id] : roleIds.filter((roleId) => roleId !== role.id))
                      }
                    />
                    <span>
                      <span className="check-title">{role.name}</span>
                      <span className="check-sub">{locked ? 'Bắt buộc với tài khoản admin' : role.code}</span>
                    </span>
                  </label>
                )
              })}
            </div>
          )}
          {fieldError('roleIds')}
        </fieldset>
      </form>
    </Modal>
  )
}

function emptyForm(): FormState {
  return { email: '', fullName: '', phone: '', avatarUrl: '', password: '', status: 'ACTIVE', roleIds: [] }
}

function PasswordDialog({
  account,
  onClose,
  onSaved,
}: {
  account: AdminAccountResponse | null
  onClose: () => void
  onSaved: (account: AdminAccountResponse) => void
}) {
  const id = useId()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setPassword('')
    setConfirm('')
    setError(undefined)
  }, [account])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!account) return
    if (password.length < 8 || password.length > 72) return setError('Mật khẩu phải có từ 8 đến 72 ký tự.')
    if (password !== confirm) return setError('Hai mật khẩu chưa khớp.')
    setBusy(true)
    setError(undefined)
    try {
      await adminAccounts.resetPassword(account.id, password)
      onSaved(account)
    } catch (err) {
      setError(accessErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      open={account !== null}
      title="Đặt lại mật khẩu"
      description={account?.email}
      onClose={busy ? () => {} : onClose}
      size="sm"
      footer={
        <>
          <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="submit" form={`${id}-form`} className="btn btn-primary" disabled={busy}>
            {busy ? 'Đang lưu...' : 'Đặt mật khẩu'}
          </button>
        </>
      }
    >
      <form id={`${id}-form`} className="form-stack" onSubmit={submit} noValidate>
        {error && (
          <div className="banner banner-error" role="alert">
            {error}
          </div>
        )}
        <div className="field">
          <label className="field-label" htmlFor={`${id}-new`}>
            Mật khẩu mới
          </label>
          <input id={`${id}-new`} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" maxLength={72} />
          <span className="field-hint">Từ 8 đến 72 ký tự. Gửi mật khẩu mới cho người dùng qua kênh an toàn.</span>
        </div>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-confirm`}>
            Nhập lại mật khẩu
          </label>
          <input id={`${id}-confirm`} type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" maxLength={72} />
        </div>
      </form>
    </Modal>
  )
}
