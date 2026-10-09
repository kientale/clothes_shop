import { CheckIcon, KeyIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { MODULE_LABEL, PERMISSION_LABEL, accessErrorMessage, roles } from '../../api/access'
import type { PermissionResponse, RoleResponse } from '../../api/types'
import { PageHead, SearchBox } from '../../components/kit'
import { useAsync } from '../../hooks'

/** Read-only matrix: which role holds which permission, grouped by module. Edit grants on the Roles screen. */
export default function PermissionsPage() {
  const data = useAsync(() => Promise.all([roles.permissions(), roles.all()]), [])
  const [search, setSearch] = useState('')
  const [permissions, roleList] = data.data ?? [[] as PermissionResponse[], [] as RoleResponse[]]
  const term = search.trim().toLowerCase()
  const label = (p: PermissionResponse) => PERMISSION_LABEL[p.code] ?? p.name
  const visible = permissions.filter((p) => !term || p.code.toLowerCase().includes(term) || label(p).toLowerCase().includes(term))
  const modules = [...new Set(visible.map((p) => p.module))].sort((a, b) => (MODULE_LABEL[a] ?? a).localeCompare(MODULE_LABEL[b] ?? b, 'vi'))
  const holds = new Map(roleList.map((r) => [r.id, new Set(r.permissions.map((p) => p.id))]))

  return (
    <div className="page page-wide">
      <PageHead
        title="Quyền truy cập"
        lede={
          <>
            Danh sách quyền theo module và vai trò đang được cấp. Để thay đổi, mở <Link to="/roles">Quản lý vai trò</Link>.
          </>
        }
      />
      <section className="panel data-panel">
        <div className="toolbar">
          <SearchBox value={search} onChange={setSearch} placeholder="Tìm theo tên hoặc mã quyền" />
          <span className="muted small">
            {permissions.length} quyền, {roleList.length} vai trò
          </span>
        </div>
        {data.error ? (
          <div className="banner banner-error" role="alert">
            {accessErrorMessage(data.error)}
          </div>
        ) : null}
        <div className="table-wrap">
          <table className="data-table matrix">
            <thead>
              <tr>
                <th scope="col">Quyền</th>
                {roleList.map((r) => (
                  <th key={r.id} scope="col" className="matrix-role" title={r.name}>
                    {r.code}
                  </th>
                ))}
              </tr>
            </thead>
            {data.loading && !data.data ? (
              <tbody>
                {Array.from({ length: 6 }, (_, i) => (
                  <tr key={i} aria-hidden>
                    <td colSpan={2}>
                      <span className="skel skel-row" />
                    </td>
                  </tr>
                ))}
              </tbody>
            ) : (
              modules.map((module) => (
                <tbody key={module}>
                  <tr className="matrix-group">
                    <th scope="rowgroup" colSpan={roleList.length + 1}>
                      {MODULE_LABEL[module] ?? module}
                    </th>
                  </tr>
                  {visible
                    .filter((p) => p.module === module)
                    .map((p) => (
                      <tr key={p.id}>
                        <th scope="row" className="matrix-perm">
                          <span className="person-name">{label(p)}</span>
                          <span className="mono">{p.code}</span>
                        </th>
                        {roleList.map((r) => (
                          <td key={r.id} className="matrix-cell">
                            {holds.get(r.id)?.has(p.id) ? <CheckIcon size={16} weight="bold" aria-label="Có quyền" /> : <span className="sr-only">Không</span>}
                          </td>
                        ))}
                      </tr>
                    ))}
                </tbody>
              ))
            )}
          </table>
        </div>
        {data.data && visible.length === 0 && (
          <div className="empty-state">
            <KeyIcon size={36} aria-hidden />
            <p>Không có quyền nào khớp tìm kiếm.</p>
          </div>
        )}
      </section>
    </div>
  )
}
