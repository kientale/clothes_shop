import { BankIcon, CreditCardIcon, MoneyIcon, PencilSimpleIcon, PlusIcon, TrashIcon, TruckIcon } from '@phosphor-icons/react'
import { useId, useState, type ReactNode } from 'react'
import { accessErrorMessage } from '../../api/access'
import { settings, type PaymentMethod, type PaymentMethodConfiguration, type ShippingMethod } from '../../api/settings'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Pill, useCan, vndOf } from '../../components/kit'
import { useToast } from '../../components/Toast'
import { ConfirmDialog } from '../../components/ui'
import { useAsync } from '../../hooks'
import { badNumber, numOrNull, numText } from './SettingsShell'

const CODE = /^[A-Z0-9_-]{1,50}$/
const ONLINE = new Set(['VNPAY', 'MOMO'])

function MethodsPanel<T extends { id: string; code: string; name: string; enabled: boolean; revision: number }>({
  title,
  lede,
  canWrite,
  rows,
  loading,
  error,
  icon,
  meta,
  onAdd,
  onEdit,
  onDelete,
}: {
  title: string
  lede: string
  canWrite: boolean
  rows: T[]
  loading: boolean
  error: unknown
  icon: (row: T) => ReactNode
  meta: (row: T) => ReactNode
  onAdd: () => void
  onEdit: (row: T) => void
  onDelete: (row: T) => void
}) {
  return (
    <section className="panel settings-panel">
      <div className="section-head">
        <div>
          <h2 className="section-title">{title}</h2>
          <p className="muted small">{lede}</p>
        </div>
        {canWrite && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={onAdd}>
            <PlusIcon size={14} /> Thêm phương thức
          </button>
        )}
      </div>
      {error ? (
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(error)}
        </div>
      ) : null}
      {loading ? (
        <span className="skel skel-row" />
      ) : rows.length === 0 ? (
        <p className="muted small">Chưa có phương thức nào.</p>
      ) : (
        <ul className="method-grid">
          {rows.map((row) => (
            <li key={row.id} className={`method-card${row.enabled ? '' : ' off'}`}>
              <span className="method-icon" aria-hidden>
                {icon(row)}
              </span>
              <div className="person-text">
                <span className="person-name">{row.name}</span>
                <span className="person-sub mono">{row.code}</span>
                <span className="person-sub">{meta(row)}</span>
              </div>
              <div className="method-side">
                <Pill tone={row.enabled ? 'active' : 'inactive'}>{row.enabled ? 'Đang bật' : 'Đang tắt'}</Pill>
                {canWrite && (
                  <div className="row-actions">
                    <button type="button" className="icon-btn" onClick={() => onEdit(row)} aria-label={`Sửa ${row.name}`} title="Sửa">
                      <PencilSimpleIcon size={15} />
                    </button>
                    <button type="button" className="icon-btn danger" onClick={() => onDelete(row)} aria-label={`Xóa ${row.name}`} title="Xóa">
                      <TrashIcon size={15} />
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function PaymentMethodsPanel() {
  const canWrite = useCan('SETTINGS_PAYMENT_WRITE')
  const toast = useToast()
  const list = useAsync(() => settings.paymentMethods.list(), [])
  const gateways = useAsync(() => settings.gateways().catch(() => []), [])
  const ready = (kind: string | undefined) => gateways.data?.find((g) => g.gateway === kind)?.configured ?? false
  const [editing, setEditing] = useState<{ item: PaymentMethod | null } | null>(null)
  const [deleting, setDeleting] = useState<PaymentMethod | null>(null)
  const done = (message: string) => {
    setEditing(null)
    toast.success(message)
    list.reload()
  }
  return (
    <>
      <MethodsPanel
        title="Phương thức thanh toán"
        lede="COD và chuyển khoản do admin xác nhận từng khoản thu; VNPay và MoMo tự xác nhận khi cổng thanh toán báo về."
        canWrite={canWrite}
        rows={list.data ?? []}
        loading={list.loading && !list.data}
        error={list.error}
        icon={(m) =>
          m.configuration?.kind === 'BANK_TRANSFER' ? <BankIcon size={18} /> : ONLINE.has(m.configuration?.kind ?? '') ? <CreditCardIcon size={18} /> : <MoneyIcon size={18} />
        }
        meta={(m) =>
          m.configuration?.kind === 'BANK_TRANSFER'
            ? m.configuration.bankDetails
              ? `${m.configuration.bankDetails.bankName} | ${m.configuration.bankDetails.accountNumber}`
              : 'Chưa có thông tin ngân hàng'
            : ONLINE.has(m.configuration?.kind ?? '')
              ? ready(m.configuration?.kind)
                ? 'Cổng thanh toán đã cấu hình khóa'
                : 'Chưa cấu hình khóa cổng: khách chưa dùng được dù đang bật'
              : 'Thu tiền khi giao hàng'
        }
        onAdd={() => setEditing({ item: null })}
        onEdit={(item) => setEditing({ item })}
        onDelete={setDeleting}
      />
      {(gateways.data ?? []).some((g) => g.ipnUrl) && (
        <section className="panel settings-panel">
          <h2 className="section-title">Địa chỉ IPN của cổng thanh toán</h2>
          <p className="muted small">Đăng ký các địa chỉ này trong trang quản trị merchant của VNPay/MoMo để đơn tự chuyển sang Đã thanh toán.</p>
          <ul className="gateway-list">
            {gateways.data!.map((g) => (
              <li key={g.gateway}>
                <strong>{g.gateway === 'VNPAY' ? 'VNPay' : 'MoMo'}</strong>
                <Pill tone={g.configured ? 'active' : 'inactive'}>{g.configured ? 'Đã cấu hình' : 'Chưa cấu hình'}</Pill>
                {g.ipnUrl && <code className="mono">{g.ipnUrl}</code>}
              </li>
            ))}
          </ul>
        </section>
      )}
      <PaymentMethodForm open={editing !== null} item={editing?.item ?? null} onClose={() => setEditing(null)} onSaved={done} />
      <ConfirmDialog
        open={deleting !== null}
        title="Xóa phương thức thanh toán"
        message={deleting && <>Xóa {deleting.name}? Phương thức sẽ ngừng dùng cho đơn mới; mã {deleting.code} không tái sử dụng được.</>}
        confirmLabel="Xóa"
        formatError={accessErrorMessage}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          await settings.paymentMethods.remove(deleting!.id, deleting!.revision)
          done(`Đã xóa ${deleting!.name}.`)
        }}
      />
    </>
  )
}

function PaymentMethodForm({ open, item, onClose, onSaved }: { open: boolean; item: PaymentMethod | null; onClose: () => void; onSaved: (m: string) => void }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      code: item?.code ?? '',
      name: item?.name ?? '',
      enabled: item?.enabled ?? true,
      kind: (item?.configuration?.kind ?? 'BANK_TRANSFER') as PaymentMethodConfiguration['kind'],
      bankName: item?.configuration?.bankDetails?.bankName ?? '',
      accountNumber: item?.configuration?.bankDetails?.accountNumber ?? '',
      accountHolder: item?.configuration?.bankDetails?.accountHolder ?? '',
      instructions: item?.configuration?.instructions ?? '',
    }),
    [item?.id],
  )
  const bank = form.kind === 'BANK_TRANSFER'
  return (
    <FormDialog
      open={open}
      title={item ? `Sửa ${item.name}` : 'Thêm phương thức thanh toán'}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm phương thức'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!item && !CODE.test(f.code.trim())) e.code = 'Mã gồm chữ IN HOA, số, gạch ngang hoặc gạch dưới.'
          if (!f.name.trim()) e.name = 'Nhập tên hiển thị.'
          if (bank) {
            if (!f.bankName.trim()) e.bankName = 'Nhập tên ngân hàng.'
            if (!f.accountNumber.trim()) e.accountNumber = 'Nhập số tài khoản.'
            if (!f.accountHolder.trim()) e.accountHolder = 'Nhập chủ tài khoản.'
          }
          return e
        },
        async (f) => {
          const configuration: PaymentMethodConfiguration = {
            kind: f.kind,
            bankDetails: bank ? { bankName: f.bankName.trim(), accountNumber: f.accountNumber.trim(), accountHolder: f.accountHolder.trim() } : null,
            instructions: f.instructions.trim() || null,
          }
          if (item) await settings.paymentMethods.update(item.id, { expectedRevision: item.revision, name: f.name.trim(), enabled: f.enabled, configuration })
          else await settings.paymentMethods.create({ code: f.code.trim(), name: f.name.trim(), enabled: f.enabled, configuration })
          onSaved(item ? `Đã cập nhật ${f.name.trim()}.` : `Đã thêm ${f.name.trim()}.`)
        },
      )}
    >
      <Field label="Mã" htmlFor={`${id}-code`} error={errors.code} hint={item ? 'Mã không đổi được.' : undefined}>
        <input {...bind(id, 'code', errors)} className="mono-input" disabled={Boolean(item)} maxLength={50} value={form.code} onChange={(e) => set('code', e.target.value.toUpperCase())} />
      </Field>
      <Field label="Tên hiển thị" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} maxLength={100} value={form.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Loại" htmlFor={`${id}-kind`}>
        <select id={`${id}-kind`} value={form.kind} onChange={(e) => set('kind', e.target.value as PaymentMethodConfiguration['kind'])}>
          <option value="BANK_TRANSFER">Chuyển khoản ngân hàng</option>
          <option value="COD">Thanh toán khi nhận hàng (COD)</option>
          <option value="VNPAY">VNPay (cổng online)</option>
          <option value="MOMO">Ví MoMo (cổng online)</option>
        </select>
      </Field>
      {ONLINE.has(form.kind) && (
        <p className="muted small span-2">
          Khóa cổng (VNPAY_TMN_CODE / VNPAY_HASH_SECRET hoặc MOMO_PARTNER_CODE / MOMO_ACCESS_KEY / MOMO_SECRET_KEY) đặt trong biến môi trường của backend, không lưu ở đây.
          Mã phương thức phải là {form.kind}.
        </p>
      )}
      <label className="check">
        <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />
        <span>
          <span className="check-title">Đang bật</span>
          <span className="check-sub">Khách chọn được khi đặt hàng.</span>
        </span>
      </label>
      {bank && (
        <>
          <Field label="Ngân hàng" htmlFor={`${id}-bankName`} error={errors.bankName}>
            <input {...bind(id, 'bankName', errors)} maxLength={100} value={form.bankName} onChange={(e) => set('bankName', e.target.value)} />
          </Field>
          <Field label="Số tài khoản" htmlFor={`${id}-accountNumber`} error={errors.accountNumber}>
            <input {...bind(id, 'accountNumber', errors)} className="mono-input" maxLength={50} value={form.accountNumber} onChange={(e) => set('accountNumber', e.target.value)} />
          </Field>
          <Field label="Chủ tài khoản" htmlFor={`${id}-accountHolder`} error={errors.accountHolder} wide>
            <input {...bind(id, 'accountHolder', errors)} maxLength={150} value={form.accountHolder} onChange={(e) => set('accountHolder', e.target.value.toUpperCase())} />
          </Field>
        </>
      )}
      <Field label="Hướng dẫn cho khách" htmlFor={`${id}-instructions`} optional wide>
        <textarea id={`${id}-instructions`} rows={2} maxLength={1000} value={form.instructions} onChange={(e) => set('instructions', e.target.value)} placeholder="Ví dụ: ghi mã đơn trong nội dung chuyển khoản" />
      </Field>
    </FormDialog>
  )
}

export function ShippingMethodsPanel() {
  const canWrite = useCan('SETTINGS_SHIPPING_WRITE')
  const toast = useToast()
  const list = useAsync(() => settings.shippingMethods.list(), [])
  const [editing, setEditing] = useState<{ item: ShippingMethod | null } | null>(null)
  const [deleting, setDeleting] = useState<ShippingMethod | null>(null)
  const done = (message: string) => {
    setEditing(null)
    toast.success(message)
    list.reload()
  }
  return (
    <>
      <MethodsPanel
        title="Phương thức giao hàng"
        lede="Biểu phí khách chọn khi đặt hàng. Hãng và mã vận đơn vẫn nhập khi tạo vận đơn."
        canWrite={canWrite}
        rows={list.data ?? []}
        loading={list.loading && !list.data}
        error={list.error}
        icon={() => <TruckIcon size={18} />}
        meta={(m) => `${m.provider} | ${vndOf(m.baseFee)}${m.estimatedDays !== null ? ` | ${m.estimatedDays} ngày` : ''}`}
        onAdd={() => setEditing({ item: null })}
        onEdit={(item) => setEditing({ item })}
        onDelete={setDeleting}
      />
      <ShippingMethodForm open={editing !== null} item={editing?.item ?? null} onClose={() => setEditing(null)} onSaved={done} />
      <ConfirmDialog
        open={deleting !== null}
        title="Xóa phương thức giao hàng"
        message={deleting && <>Xóa {deleting.name}? Đơn cũ giữ nguyên phí đã tính; mã {deleting.code} không tái sử dụng được.</>}
        confirmLabel="Xóa"
        formatError={accessErrorMessage}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          await settings.shippingMethods.remove(deleting!.id, deleting!.revision)
          done(`Đã xóa ${deleting!.name}.`)
        }}
      />
    </>
  )
}

function ShippingMethodForm({ open, item, onClose, onSaved }: { open: boolean; item: ShippingMethod | null; onClose: () => void; onSaved: (m: string) => void }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      code: item?.code ?? '',
      name: item?.name ?? '',
      provider: item?.provider ?? '',
      baseFee: numText(item?.baseFee),
      estimatedDays: numText(item?.estimatedDays),
      enabled: item?.enabled ?? true,
    }),
    [item?.id],
  )
  return (
    <FormDialog
      open={open}
      title={item ? `Sửa ${item.name}` : 'Thêm phương thức giao hàng'}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm phương thức'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!item && !CODE.test(f.code.trim())) e.code = 'Mã gồm chữ IN HOA, số, gạch ngang hoặc gạch dưới.'
          if (!f.name.trim()) e.name = 'Nhập tên hiển thị.'
          if (!f.provider.trim()) e.provider = 'Nhập đơn vị giao hàng.'
          if (!f.baseFee.trim() || badNumber(f.baseFee)) e.baseFee = 'Nhập phí hợp lệ.'
          if (badNumber(f.estimatedDays) || Number(f.estimatedDays) > 365) e.estimatedDays = 'Từ 0 đến 365 ngày.'
          return e
        },
        async (f) => {
          const body = { name: f.name.trim(), provider: f.provider.trim(), baseFee: numOrNull(f.baseFee)!, estimatedDays: numOrNull(f.estimatedDays), enabled: f.enabled }
          if (item) await settings.shippingMethods.update(item.id, { expectedRevision: item.revision, ...body })
          else await settings.shippingMethods.create({ code: f.code.trim(), ...body })
          onSaved(item ? `Đã cập nhật ${body.name}.` : `Đã thêm ${body.name}.`)
        },
      )}
    >
      <Field label="Mã" htmlFor={`${id}-code`} error={errors.code} hint={item ? 'Mã không đổi được.' : undefined}>
        <input {...bind(id, 'code', errors)} className="mono-input" disabled={Boolean(item)} maxLength={50} value={form.code} onChange={(e) => set('code', e.target.value.toUpperCase())} />
      </Field>
      <Field label="Tên hiển thị" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} maxLength={100} placeholder="Giao hàng tiêu chuẩn" value={form.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Đơn vị giao hàng" htmlFor={`${id}-provider`} error={errors.provider}>
        <input {...bind(id, 'provider', errors)} maxLength={100} value={form.provider} onChange={(e) => set('provider', e.target.value)} />
      </Field>
      <Field label="Phí" htmlFor={`${id}-baseFee`} error={errors.baseFee}>
        <input {...bind(id, 'baseFee', errors)} inputMode="numeric" value={form.baseFee} onChange={(e) => set('baseFee', e.target.value)} />
      </Field>
      <Field label="Thời gian dự kiến (ngày)" htmlFor={`${id}-estimatedDays`} error={errors.estimatedDays} optional>
        <input {...bind(id, 'estimatedDays', errors)} inputMode="numeric" value={form.estimatedDays} onChange={(e) => set('estimatedDays', e.target.value)} />
      </Field>
      <label className="check">
        <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />
        <span>
          <span className="check-title">Đang bật</span>
          <span className="check-sub">Khách chọn được khi đặt hàng.</span>
        </span>
      </label>
    </FormDialog>
  )
}
