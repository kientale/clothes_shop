import { EyeIcon, LockSimpleIcon, MagnifyingGlassIcon, PencilSimpleIcon, PlusIcon, ShieldCheckIcon, TrashIcon } from '@phosphor-icons/react'
import { useEffect, useId, useMemo, useState, type FormEvent } from 'react'
import { MODULE_LABEL, PERMISSION_LABEL, SYSTEM_ROLES, accessErrorMessage, fieldErrors, roles as roleApi } from '../../api/access'
import type { PermissionResponse, RoleResponse } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { useToast } from '../../components/Toast'
import { ConfirmDialog, Modal, Pagination, rowNumber, useDebounced, usePaging } from '../../components/ui'
import { formatDate } from '../../format'
import { useAsync } from '../../hooks'

const CODE = /^[A-Z][A-Z0-9_]{1,49}$/

type Dialog = { kind: 'create' } | { kind: 'edit'; role: RoleResponse } | { kind: 'delete'; role: RoleResponse }

const permissionLabel = (permission: PermissionResponse) => PERMISSION_LABEL[permission.code] ?? permission.name

export default function RolesPage() {
  const { user } = useAuth()
  const canWrite = user?.permissions.includes('ROLE_WRITE') ?? false
  const [search, setSearch] = useState('')
  const term = useDebounced(search.trim())
  const { page, size, setPage, setSize } = usePaging(term)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const toast = useToast()

  const list = useAsync(() => roleApi.list({ search: term, page, size }), [term, page, size])
  const catalog = useAsync(() => roleApi.permissions(), [])

  const done = (message: string) => {
    setDialog(null)
    toast.success(message)
    list.reload()
  }

  const rows = list.data?.content ?? []

  return (
    <div className="page page-wide">
      <header className="page-head">
        <div>
          <h1 className="page-title">Vai trò</h1>
          <p className="page-lede">Mỗi vai trò là một nhóm quyền. Gán vai trò cho tài khoản admin để cấp quyền tương ứng.</p>
        </div>
        {canWrite && (
          <button type="button" className="btn btn-primary" onClick={() => setDialog({ kind: 'create' })}>
            <PlusIcon size={16} weight="bold" /> Thêm vai trò
          </button>
        )}
      </header>


      <section className="panel data-panel">
        <div className="toolbar">
          <label className="search-box">
            <MagnifyingGlassIcon size={16} aria-hidden />
            <span className="sr-only">Tìm theo tên hoặc mã vai trò</span>
            <input
              type="search"
              placeholder="Tìm theo tên hoặc mã vai trò"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
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
                <th scope="col">Vai trò</th>
                <th scope="col">Quyền</th>
                <th scope="col">Cập nhật</th>
                <th scope="col" className="col-actions">
                  Thao tác
                </th>
              </tr>
            </thead>
            <tbody>
              {list.loading && !list.data
                ? Array.from({ length: 3 }, (_, i) => (
                    <tr key={i} aria-hidden>
                      <td colSpan={5}>
                        <span className="skel skel-row" />
                      </td>
                    </tr>
                  ))
                : rows.map((role, index) => {
                    const system = SYSTEM_ROLES.includes(role.code)
                    const editable = canWrite && !system
                    return (
                      <tr key={role.id}>
                        <td className="col-index">{rowNumber(list.data, index)}</td>
                        <td>
                          <div className="role-cell">
                            <span className="role-name">
                              {role.name}
                              {system && (
                                <span className="tag">
                                  <LockSimpleIcon size={11} weight="bold" aria-hidden /> Hệ thống
                                </span>
                              )}
                            </span>
                            <code className="role-code">{role.code}</code>
                          </div>
                        </td>
                        <td>
                          {role.permissions.length === 0 ? (
                            <span className="muted">Chưa có quyền nào</span>
                          ) : (
                            <div className="chips">
                              {role.permissions.slice(0, 3).map((permission) => (
                                <span key={permission.id} className="chip chip-sm" title={permission.code}>
                                  {permissionLabel(permission)}
                                </span>
                              ))}
                              {role.permissions.length > 3 && <span className="chip chip-sm chip-muted">+{role.permissions.length - 3}</span>}
                            </div>
                          )}
                        </td>
                        <td className="muted nowrap">{formatDate(role.updatedAt)}</td>
                        <td className="col-actions">
                          <div className="row-actions">
                            <button
                              type="button"
                              className="icon-btn"
                              onClick={() => setDialog({ kind: 'edit', role })}
                              aria-label={`${editable ? 'Sửa' : 'Xem'} ${role.name}`}
                              title={editable ? 'Sửa' : 'Xem quyền'}
                            >
                              {editable ? <PencilSimpleIcon size={16} /> : <EyeIcon size={16} />}
                            </button>
                            {editable && (
                              <button
                                type="button"
                                className="icon-btn danger"
                                onClick={() => setDialog({ kind: 'delete', role })}
                                aria-label={`Xóa ${role.name}`}
                                title="Xóa"
                              >
                                <TrashIcon size={16} />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
            </tbody>
          </table>
        </div>

        {list.data && rows.length === 0 && (
          <div className="empty-state">
            <ShieldCheckIcon size={36} aria-hidden />
            <p>{term ? 'Không có vai trò nào khớp từ khóa.' : 'Chưa có vai trò nào.'}</p>
          </div>
        )}

        {list.data && (
          <Pagination
            page={page}
            size={size}
            totalPages={list.data.totalPages}
            totalElements={list.data.totalElements}
            itemLabel="vai trò"
            onPageChange={setPage}
            onSizeChange={setSize}
          />
        )}
      </section>

      <RoleFormDialog
        dialog={dialog?.kind === 'create' || dialog?.kind === 'edit' ? dialog : null}
        readOnly={dialog?.kind === 'edit' && (!canWrite || SYSTEM_ROLES.includes(dialog.role.code))}
        catalog={catalog.data ?? []}
        catalogError={catalog.error}
        onClose={() => setDialog(null)}
        onSaved={(role, created) => done(created ? `Đã tạo vai trò ${role.name}.` : `Đã cập nhật vai trò ${role.name}.`)}
      />

      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title="Xóa vai trò"
        message={
          dialog?.kind === 'delete' ? (
            <>
              Xóa vai trò <strong>{dialog.role.name}</strong> ({dialog.role.code})? Chỉ xóa được khi không còn tài khoản nào đang giữ vai trò này.
            </>
          ) : null
        }
        confirmLabel="Xóa vai trò"
        formatError={accessErrorMessage}
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          if (dialog?.kind !== 'delete') return
          await roleApi.remove(dialog.role.id)
          if (rows.length === 1 && page > 0) setPage(page - 1)
          done(`Đã xóa vai trò ${dialog.role.name}.`)
        }}
      />
    </div>
  )
}

function RoleFormDialog({
  dialog,
  readOnly,
  catalog,
  catalogError,
  onClose,
  onSaved,
}: {
  dialog: { kind: 'create' } | { kind: 'edit'; role: RoleResponse } | null
  readOnly: boolean
  catalog: PermissionResponse[]
  catalogError: unknown
  onClose: () => void
  onSaved: (role: RoleResponse, created: boolean) => void
}) {
  const id = useId()
  const editing = dialog?.kind === 'edit' ? dialog.role : null
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!dialog) return
    setName(editing?.name ?? '')
    setCode(editing?.code ?? '')
    setSelected(editing?.permissions.map((permission) => permission.id) ?? [])
    setErrors({})
    setError(undefined)
  }, [dialog, editing])

  const modules = useMemo(() => {
    const groups = new Map<string, PermissionResponse[]>()
    for (const permission of catalog) groups.set(permission.module, [...(groups.get(permission.module) ?? []), permission])
    return [...groups.entries()]
  }, [catalog])

  const toggle = (ids: string[], on: boolean) =>
    setSelected((current) => (on ? [...new Set([...current, ...ids])] : current.filter((value) => !ids.includes(value))))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!name.trim()) next.name = 'Nhập tên vai trò.'
    else if (name.trim().length > 100) next.name = 'Tên tối đa 100 ký tự.'
    if (!editing && !CODE.test(code)) next.code = 'Mã gồm 2-50 ký tự IN HOA, số hoặc dấu gạch dưới, bắt đầu bằng chữ cái.'
    setErrors(next)
    setError(undefined)
    if (Object.keys(next).length) return
    setBusy(true)
    try {
      const saved = editing
        ? await roleApi.update(editing.id, { name: name.trim(), permissionIds: selected })
        : await roleApi.create({ name: name.trim(), code, permissionIds: selected })
      onSaved(saved, !editing)
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const title = readOnly ? 'Quyền của vai trò' : editing ? 'Sửa vai trò' : 'Thêm vai trò'

  return (
    <Modal
      open={dialog !== null}
      title={title}
      description={
        readOnly && editing ? (
          <>
            {editing.name} (<code>{editing.code}</code>).
            {SYSTEM_ROLES.includes(editing.code) && ' Vai trò hệ thống, không thể chỉnh sửa.'}
          </>
        ) : undefined
      }
      onClose={busy ? () => {} : onClose}
      size="lg"
      footer={
        readOnly ? (
          <button type="button" className="btn btn-primary" onClick={onClose}>
            Đóng
          </button>
        ) : (
          <>
            <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
              Hủy
            </button>
            <button type="submit" form={`${id}-form`} className="btn btn-primary" disabled={busy}>
              {busy ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Tạo vai trò'}
            </button>
          </>
        )
      }
    >
      <form id={`${id}-form`} className="form-grid" onSubmit={submit} noValidate>
        {error ? (
          <div className="banner banner-error span-2" role="alert">
            {accessErrorMessage(error)}
          </div>
        ) : null}

        {!readOnly && (
          <>
            <div className="field">
              <label className="field-label" htmlFor={`${id}-name`}>
                Tên vai trò
              </label>
              <input
                id={`${id}-name`}
                value={name}
                onChange={(e) => {
                  setName(e.target.value)
                  setErrors(({ name: _cleared, ...rest }) => rest)
                }}
                maxLength={100}
                placeholder="Nhân viên kho"
                aria-invalid={errors.name ? true : undefined}
                aria-describedby={errors.name ? `${id}-name-error` : undefined}
              />
              {errors.name && (
                <span id={`${id}-name-error`} className="field-error">
                  {errors.name}
                </span>
              )}
            </div>
            <div className="field">
              <label className="field-label" htmlFor={`${id}-code`}>
                Mã vai trò
              </label>
              <input
                id={`${id}-code`}
                className="mono"
                value={code}
                onChange={(e) => {
                  setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))
                  setErrors(({ code: _cleared, ...rest }) => rest)
                }}
                maxLength={50}
                placeholder="WAREHOUSE_STAFF"
                disabled={Boolean(editing)}
                aria-invalid={errors.code ? true : undefined}
                aria-describedby={`${id}-code-hint`}
              />
              <span id={`${id}-code-hint`} className={errors.code ? 'field-error' : 'field-hint'}>
                {errors.code ?? (editing ? 'Mã vai trò không đổi được sau khi tạo.' : 'Chữ IN HOA, số và dấu gạch dưới.')}
              </span>
            </div>
          </>
        )}

        <fieldset className="field span-2 check-group">
          <legend className="field-label">
            Quyền <span className="optional">({selected.length} đã chọn)</span>
          </legend>
          {catalogError ? (
            <span className="field-error">Không tải được danh mục quyền: {accessErrorMessage(catalogError)}</span>
          ) : modules.length === 0 ? (
            <span className="skel skel-row" aria-hidden />
          ) : (
            <div className="perm-groups">
              {modules.map(([module, permissions]) => {
                const ids = permissions.map((permission) => permission.id)
                const all = ids.every((value) => selected.includes(value))
                return (
                  <div key={module} className="perm-group">
                    <div className="perm-group-head">
                      <span>{MODULE_LABEL[module] ?? module}</span>
                      {!readOnly && (
                        <button type="button" className="link-btn" onClick={() => toggle(ids, !all)}>
                          {all ? 'Bỏ chọn nhóm' : 'Chọn cả nhóm'}
                        </button>
                      )}
                    </div>
                    <div className="check-list">
                      {permissions.map((permission) => (
                        <label key={permission.id} className="check">
                          <input
                            type="checkbox"
                            checked={selected.includes(permission.id)}
                            disabled={readOnly}
                            onChange={(e) => toggle([permission.id], e.target.checked)}
                          />
                          <span>
                            <span className="check-title">{permissionLabel(permission)}</span>
                            <span className="check-sub">{permission.code}</span>
                          </span>
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </fieldset>
      </form>
    </Modal>
  )
}
