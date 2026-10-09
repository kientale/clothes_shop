import { ClockCounterClockwiseIcon, FloppyDiskIcon } from '@phosphor-icons/react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { accessErrorMessage, fieldErrors } from '../../api/access'
import { ApiError } from '../../api/client'
import { settings, type SettingsGroup, type SettingsGroups, type SettingsHistory } from '../../api/settings'
import { ListPanel, PageHead, useCan, whenOf } from '../../components/kit'
import { useToast } from '../../components/Toast'
import { Modal } from '../../components/ui'
import { useAsync } from '../../hooks'

const GROUP_PERMISSION: Record<SettingsGroup, string> = {
  store: 'SETTINGS_STORE',
  payment: 'SETTINGS_PAYMENT',
  shipping: 'SETTINGS_SHIPPING',
  order: 'SETTINGS_ORDER',
  notification: 'SETTINGS_NOTIFICATION',
  general: 'SETTINGS_GENERAL',
}

export interface SettingsFormApi<F> {
  form: F
  set: <K extends keyof F>(key: K, value: F[K]) => void
  errors: Record<string, string>
  readOnly: boolean
}

/**
 * Loads one settings group, edits it as form values, and saves the whole group with the
 * revision it was read at. A 409 means someone else saved first: the page offers a reload.
 */
export function SettingsShell<G extends SettingsGroup, F extends object>({
  group,
  title,
  lede,
  toForm,
  toConfig,
  validate,
  children,
  aside,
}: {
  group: G
  title: string
  lede: ReactNode
  toForm: (config: SettingsGroups[G]) => F
  toConfig: (form: F) => SettingsGroups[G]
  validate: (form: F) => Record<string, string | undefined>
  children: (api: SettingsFormApi<F>) => ReactNode
  /** Extra panels after the form (e.g. payment or shipping methods). */
  aside?: ReactNode
}) {
  const toast = useToast()
  const canWrite = useCan(`${GROUP_PERMISSION[group]}_WRITE`)
  const data = useAsync(() => settings.get(group), [group])
  const [form, setForm] = useState<F | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const [history, setHistory] = useState(false)

  useEffect(() => {
    if (data.data) setForm(toForm(data.data.configuration))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data.data])

  const original = data.data ? JSON.stringify(toForm(data.data.configuration)) : ''
  const dirty = form !== null && JSON.stringify(form) !== original
  const conflict = error instanceof ApiError && error.code === 'SETTINGS_REVISION_CONFLICT'

  const set = <K extends keyof F>(key: K, value: F[K]) => {
    setForm((current) => (current ? { ...current, [key]: value } : current))
    setErrors((current) => {
      if (!((key as string) in current)) return current
      const { [key as string]: _cleared, ...rest } = current
      return rest
    })
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!form || !data.data) return
    const next = Object.fromEntries(Object.entries(validate(form)).filter((entry): entry is [string, string] => Boolean(entry[1])))
    setErrors(next)
    setError(undefined)
    if (Object.keys(next).length) return
    setBusy(true)
    try {
      const saved = await settings.save(group, data.data.revision, toConfig(form))
      data.setData(saved)
      toast.success('Đã lưu cài đặt.')
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page page-wide">
      <PageHead
        title={title}
        lede={lede}
        actions={
          <button type="button" className="btn btn-plain" onClick={() => setHistory(true)}>
            <ClockCounterClockwiseIcon size={16} /> Lịch sử thay đổi
          </button>
        }
      />

      {data.error ? (
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(data.error)}{' '}
          <button type="button" className="link-btn" onClick={data.reload}>
            Thử lại
          </button>
        </div>
      ) : null}

      <form className="panel settings-panel" onSubmit={save} noValidate>
        {!form ? (
          <div aria-busy="true">
            {Array.from({ length: 4 }, (_, i) => (
              <span key={i} className="skel skel-row" />
            ))}
          </div>
        ) : (
          <>
            {error ? (
              <div className="banner banner-error" role="alert">
                {accessErrorMessage(error)}{' '}
                {conflict && (
                  <button
                    type="button"
                    className="link-btn"
                    onClick={() => {
                      setError(undefined)
                      data.reload()
                    }}
                  >
                    Tải bản mới nhất
                  </button>
                )}
              </div>
            ) : null}
            <fieldset className="settings-fields" disabled={!canWrite || busy}>
              {children({ form, set, errors, readOnly: !canWrite })}
            </fieldset>
            <footer className="settings-foot">
              <span className="muted small">
                {data.data?.updatedAt ? `Cập nhật lần cuối ${whenOf(data.data.updatedAt)} | phiên bản ${data.data.revision}` : 'Đang dùng cấu hình mặc định'}
              </span>
              {canWrite ? (
                <div className="actions">
                  <button type="button" className="btn btn-plain" disabled={!dirty || busy} onClick={() => data.data && setForm(toForm(data.data.configuration))}>
                    Hoàn tác
                  </button>
                  <button type="submit" className="btn btn-primary" disabled={!dirty || busy}>
                    <FloppyDiskIcon size={16} /> {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </button>
                </div>
              ) : (
                <span className="muted small">Bạn chỉ có quyền xem.</span>
              )}
            </footer>
          </>
        )}
      </form>

      {aside}

      <Modal open={history} title={`Lịch sử: ${title.toLowerCase()}`} onClose={() => setHistory(false)} size="lg">
        {history && (
          <ListPanel<SettingsHistory>
            local
            filterKey={group}
            load={(paging) => settings.history(group, paging)}
            rowKey={(h) => h.id}
            itemLabel="thay đổi"
            emptyIcon={<ClockCounterClockwiseIcon size={32} aria-hidden />}
            columns={[
              { header: 'Thời gian', render: (h) => whenOf(h.createdAt), className: 'nowrap' },
              { header: 'Thao tác', render: (h) => <span className="chip chip-sm">{ACTION_LABEL[h.action] ?? h.action}</span> },
              { header: 'Thay đổi', render: (h) => <ChangeList oldData={h.oldData} newData={h.newData} /> },
            ]}
          />
        )}
      </Modal>
    </div>
  )
}

const ACTION_LABEL: Record<string, string> = { UPDATE: 'Cập nhật', CREATE: 'Tạo', DELETE: 'Xóa' }

/** Changed keys between two JSON snapshots, flattened one level deep. */
function ChangeList({ oldData, newData }: { oldData: string | null; newData: string | null }) {
  const parse = (value: string | null): Record<string, unknown> => {
    try {
      const parsed = value ? JSON.parse(value) : {}
      return parsed && typeof parsed === 'object' ? (parsed.configuration ?? parsed) : {}
    } catch {
      return {}
    }
  }
  const before = parse(oldData)
  const after = parse(newData)
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter((k) => JSON.stringify(before[k]) !== JSON.stringify(after[k]))
  if (keys.length === 0) return <span className="muted small">Không có khác biệt</span>
  const show = (value: unknown) => (value === null || value === undefined ? 'trống' : typeof value === 'object' ? JSON.stringify(value) : String(value))
  return (
    <ul className="change-list">
      {keys.slice(0, 6).map((k) => (
        <li key={k}>
          <span className="mono">{k}</span>: <span className="muted strike">{show(before[k])}</span> → <strong>{show(after[k])}</strong>
        </li>
      ))}
      {keys.length > 6 && <li className="muted">và {keys.length - 6} mục khác</li>}
    </ul>
  )
}

/** On/off row: title and explanation on the left, a switch on the right. */
export function SwitchRow({ id, label, hint, checked, onChange }: { id: string; label: string; hint?: ReactNode; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <label className="switch-row" htmlFor={id}>
      <span>
        <span className="switch-title">{label}</span>
        {hint && <span className="switch-hint">{hint}</span>}
      </span>
      <input id={id} type="checkbox" role="switch" className="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </label>
  )
}

export function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="settings-section">
      <h2 className="section-title">{title}</h2>
      <div className="settings-section-body">{children}</div>
    </section>
  )
}

/** Optional whole-number fields (VND amounts, counts): '' means null; thousand separators are ignored. */
const digitsOf = (value: string) => value.replace(/[.\s,₫đ]/g, '')
export const numText = (value: number | null | undefined) => (value === null || value === undefined ? '' : String(value))
export const numOrNull = (value: string) => (value.trim() === '' ? null : Number(digitsOf(value)))
export const badNumber = (value: string) => value.trim() !== '' && !/^\d+$/.test(digitsOf(value))
