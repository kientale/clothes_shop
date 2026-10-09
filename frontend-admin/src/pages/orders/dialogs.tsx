import { useId } from 'react'
import {
  RETURN_TYPE_LABEL,
  orders,
  payments,
  refunds,
  returns,
  shipments,
  type Order,
  type Payment,
  type ReturnRequest,
  type ReturnType,
  type Shipment,
} from '../../api/commerce'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { SearchPicker, vndOf, type PickOption } from '../../components/kit'
import { searchOrders, searchVariants, type VariantOption } from '../../components/pickers'
import { useAsync } from '../../hooks'
import { parseMoney } from '../catalog/shared'

// Create/edit dialogs shared by the order detail screen and the payment, shipment,
// return and refund lists. A preset order skips the order picker.

type Saved = (message: string) => void

function OrderPickerField({ id, value, onChange, error }: { id: string; value: PickOption | null; onChange: (o: PickOption | null) => void; error?: string }) {
  return (
    <Field label="Đơn hàng" htmlFor={`${id}-order`} error={error} wide>
      <SearchPicker
        inputId={`${id}-order`}
        value={value}
        onChange={onChange}
        search={searchOrders}
        placeholder="Chọn đơn hàng"
        searchPlaceholder="Tìm theo mã đơn, người nhận, điện thoại"
        invalid={Boolean(error)}
      />
    </Field>
  )
}

const orderOption = (order: Order): PickOption => ({ id: order.id, title: order.orderCode, sub: `${order.recipientName} | ${vndOf(order.totalAmount)}` })

export function CreatePaymentDialog({ open, order, onClose, onSaved }: { open: boolean; order?: Order; onClose: () => void; onSaved: Saved }) {
  const id = useId()
  const methods = useAsync(() => (open ? payments.methods() : Promise.resolve([])), [open])
  const remaining = order ? Math.max(0, order.totalAmount - order.paidAmount) : null
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({ order: order ? orderOption(order) : (null as PickOption | null), method: '', amount: remaining ? String(remaining) : '' }),
    [order?.id],
  )
  const method = form.method || methods.data?.[0]?.code || ''
  return (
    <FormDialog
      open={open}
      title="Ghi nhận thanh toán"
      description="Tạo khoản thanh toán chờ xác nhận. Xác nhận đã thu khi tiền về tài khoản hoặc shipper nộp COD."
      busy={busy}
      submitLabel="Tạo thanh toán"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.order) e.orderId = 'Chọn đơn hàng.'
          if (!method) e.paymentMethod = 'Chọn phương thức.'
          const amount = parseMoney(f.amount)
          if (Number.isNaN(amount) || amount <= 0) e.amount = 'Nhập số tiền lớn hơn 0.'
          return e
        },
        async (f) => {
          await payments.create({ orderId: f.order!.id, paymentMethod: method, amount: parseMoney(f.amount) })
          onSaved(`Đã tạo thanh toán ${vndOf(parseMoney(f.amount))} cho đơn ${f.order!.title}.`)
        },
      )}
    >
      {!order && <OrderPickerField id={id} value={form.order} onChange={(o) => set('order', o)} error={errors.orderId} />}
      <Field label="Phương thức" htmlFor={`${id}-paymentMethod`} error={errors.paymentMethod}>
        <select {...bind(id, 'paymentMethod', errors)} value={method} onChange={(e) => set('method', e.target.value)}>
          {(methods.data ?? []).map((m) => (
            <option key={m.code} value={m.code}>
              {m.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Số tiền" htmlFor={`${id}-amount`} error={errors.amount} hint={remaining !== null ? `Còn phải thu ${vndOf(remaining)}.` : undefined}>
        <input {...bind(id, 'amount', errors)} inputMode="numeric" value={form.amount} onChange={(e) => set('amount', e.target.value)} />
      </Field>
    </FormDialog>
  )
}

export function ShipmentDialog({
  open,
  order,
  shipment,
  onClose,
  onSaved,
}: {
  open: boolean
  order?: Order
  /** Set to edit carrier and tracking code of a pending shipment. */
  shipment?: Shipment | null
  onClose: () => void
  onSaved: Saved
}) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      order: order ? orderOption(order) : (null as PickOption | null),
      provider: shipment?.shippingProvider ?? '',
      tracking: shipment?.trackingCode ?? '',
      fee: shipment ? String(shipment.shippingFee) : order ? String(order.shippingFee) : '',
    }),
    [order?.id, shipment?.id],
  )
  const editing = Boolean(shipment)
  return (
    <FormDialog
      open={open}
      title={editing ? 'Sửa vận đơn' : 'Tạo vận đơn'}
      description={editing ? 'Chỉ sửa được khi vận đơn còn chờ lấy hàng.' : 'Đơn phải đã xác nhận. Vận đơn chở toàn bộ đơn; phí ở đây là chi phí thực trả cho hãng.'}
      busy={busy}
      submitLabel={editing ? 'Lưu thay đổi' : 'Tạo vận đơn'}
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!editing && !f.order) e.orderId = 'Chọn đơn hàng.'
          if (!f.provider.trim()) e.shippingProvider = 'Nhập hãng vận chuyển.'
          if (!editing && Number.isNaN(parseMoney(f.fee))) e.shippingFee = 'Nhập phí vận chuyển.'
          return e
        },
        async (f) => {
          if (shipment) {
            await shipments.update(shipment.id, { shippingProvider: f.provider.trim(), trackingCode: f.tracking.trim() || null })
            onSaved('Đã cập nhật vận đơn.')
          } else {
            await shipments.create({ orderId: f.order!.id, shippingProvider: f.provider.trim(), trackingCode: f.tracking.trim() || null, shippingFee: parseMoney(f.fee) })
            onSaved(`Đã tạo vận đơn cho đơn ${f.order!.title}.`)
          }
        },
      )}
    >
      {!order && !editing && <OrderPickerField id={id} value={form.order} onChange={(o) => set('order', o)} error={errors.orderId} />}
      <Field label="Hãng vận chuyển" htmlFor={`${id}-shippingProvider`} error={errors.shippingProvider}>
        <input {...bind(id, 'shippingProvider', errors)} maxLength={100} placeholder="Ví dụ: GHN, GHTK" value={form.provider} onChange={(e) => set('provider', e.target.value)} />
      </Field>
      <Field label="Mã vận đơn" htmlFor={`${id}-tracking`} optional>
        <input id={`${id}-tracking`} className="mono-input" maxLength={100} value={form.tracking} onChange={(e) => set('tracking', e.target.value)} />
      </Field>
      {!editing && (
        <Field label="Phí vận chuyển thực tế" htmlFor={`${id}-shippingFee`} error={errors.shippingFee} hint="Đơn dùng phí cấu hình phải nhập đúng phí đã tính.">
          <input {...bind(id, 'shippingFee', errors)} inputMode="numeric" value={form.fee} onChange={(e) => set('fee', e.target.value)} />
        </Field>
      )}
    </FormDialog>
  )
}

interface ReturnLine {
  orderItemId: string
  quantity: string
  replacement: VariantOption | null
}

export function CreateReturnDialog({ open, order, onClose, onSaved }: { open: boolean; order?: Order; onClose: () => void; onSaved: Saved }) {
  const id = useId()
  const { form, set, setForm, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      order: order ? orderOption(order) : (null as PickOption | null),
      type: 'RETURN' as ReturnType,
      reason: '',
      note: '',
      lines: {} as Record<string, ReturnLine>,
    }),
    [order?.id],
  )
  const detail = useAsync(() => (form.order ? (order && order.id === form.order.id ? Promise.resolve(order) : orders.get(form.order.id)) : Promise.resolve(null)), [form.order?.id])
  const items = detail.data?.items ?? []
  const line = (itemId: string): ReturnLine => form.lines[itemId] ?? { orderItemId: itemId, quantity: '', replacement: null }
  const setLine = (itemId: string, patch: Partial<ReturnLine>) =>
    setForm((current) => ({ ...current, lines: { ...current.lines, [itemId]: { ...line(itemId), ...current.lines[itemId], ...patch } } }))

  return (
    <FormDialog
      open={open}
      title="Tạo yêu cầu đổi/trả"
      description="Chỉ áp dụng cho đơn đã giao hoặc hàng giao thất bại đã hoàn về kho."
      busy={busy}
      submitLabel="Tạo yêu cầu"
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.order) e.orderId = 'Chọn đơn hàng.'
          if (!f.reason.trim()) e.reason = 'Nhập lý do.'
          const chosen = Object.values(f.lines).filter((l) => l.quantity.trim())
          if (chosen.length === 0) e.items = 'Nhập số lượng cho ít nhất một dòng.'
          else if (chosen.some((l) => !/^[1-9]\d*$/.test(l.quantity.trim()))) e.items = 'Số lượng phải là số nguyên dương.'
          else if (chosen.some((l) => (items.find((i) => i.id === l.orderItemId)?.quantity ?? 0) < Number(l.quantity))) e.items = 'Số lượng vượt số đã mua.'
          else if (f.type === 'EXCHANGE' && chosen.some((l) => !l.replacement)) e.items = 'Chọn biến thể thay thế cho mỗi dòng đổi.'
          return e
        },
        async (f) => {
          await returns.create({
            orderId: f.order!.id,
            requestType: f.type,
            reason: f.reason.trim(),
            note: f.note.trim() || null,
            images: [],
            items: Object.values(f.lines)
              .filter((l) => l.quantity.trim())
              .map((l) => ({
                orderItemId: l.orderItemId,
                quantity: Number(l.quantity),
                reason: null,
                conditionNote: null,
                replacementVariantId: f.type === 'EXCHANGE' ? l.replacement?.id ?? null : null,
              })),
          })
          onSaved(`Đã tạo yêu cầu ${RETURN_TYPE_LABEL[f.type].toLowerCase()} cho đơn ${f.order!.title}.`)
        },
      )}
    >
      {!order && <OrderPickerField id={id} value={form.order} onChange={(o) => setForm((c) => ({ ...c, order: o, lines: {} }))} error={errors.orderId} />}
      <fieldset className="choice-group span-2">
        <legend className="field-label">Loại yêu cầu</legend>
        <div className="choice-list">
          {(Object.keys(RETURN_TYPE_LABEL) as ReturnType[]).map((type) => (
            <label key={type} className="choice">
              <input type="radio" name={`${id}-type`} checked={form.type === type} onChange={() => set('type', type)} />
              <span>{RETURN_TYPE_LABEL[type]}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="line-editor span-2">
        <legend className="field-label">Dòng hàng</legend>
        {!form.order ? (
          <p className="muted small">Chọn đơn để xem các dòng hàng.</p>
        ) : detail.loading ? (
          <span className="skel skel-row" />
        ) : (
          items.map((item) => (
            <div key={item.id} className={`return-row${form.type === 'EXCHANGE' ? ' exchange' : ''}`}>
              <span className="person-text">
                <span className="person-name">{item.productName}</span>
                <span className="person-sub">
                  {item.colorName} / {item.sizeName} | đã mua {item.quantity}
                </span>
              </span>
              <input
                aria-label={`Số lượng trả ${item.productName}`}
                inputMode="numeric"
                placeholder="0"
                value={line(item.id).quantity}
                onChange={(e) => setLine(item.id, { quantity: e.target.value })}
              />
              {form.type === 'EXCHANGE' && (
                <SearchPicker
                  value={line(item.id).replacement}
                  onChange={(v) => setLine(item.id, { replacement: v as VariantOption | null })}
                  search={async (term) => (await searchVariants(term || item.productName)).filter((v) => v.price === item.unitPrice)}
                  placeholder="Biến thể thay thế"
                  searchPlaceholder="Cùng sản phẩm, cùng giá"
                />
              )}
            </div>
          ))
        )}
        {errors.items && (
          <span className="field-error" role="alert">
            {errors.items}
          </span>
        )}
      </fieldset>

      <Field label="Lý do" htmlFor={`${id}-reason`} error={errors.reason} wide>
        <input {...bind(id, 'reason', errors)} maxLength={500} value={form.reason} onChange={(e) => set('reason', e.target.value)} placeholder="Ví dụ: sai size, lỗi đường may" />
      </Field>
      <Field label="Ghi chú" htmlFor={`${id}-note`} optional wide>
        <textarea id={`${id}-note`} rows={2} maxLength={1000} value={form.note} onChange={(e) => set('note', e.target.value)} />
      </Field>
    </FormDialog>
  )
}

export function CreateRefundDialog({ open, request, onClose, onSaved }: { open: boolean; request: ReturnRequest | null; onClose: () => void; onSaved: Saved }) {
  const id = useId()
  const paid = useAsync(
    () => (open && request ? payments.list({ orderId: request.orderId, status: 'PAID', page: 0, size: 50 }).then((p) => p.content) : Promise.resolve([] as Payment[])),
    [open, request?.id],
  )
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({ paymentId: '', amount: request ? String(request.refundableAmount) : '', method: '', reason: '' }),
    [request?.id],
  )
  const paymentId = form.paymentId || paid.data?.[0]?.id || ''
  return (
    <FormDialog
      open={open}
      title="Tạo hoàn tiền"
      description={request ? `Giá trị hàng trả có thể hoàn: ${vndOf(request.refundableAmount)} (không gồm phí giao hàng).` : undefined}
      busy={busy}
      submitLabel="Tạo hoàn tiền"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!paymentId) e.paymentId = 'Đơn chưa có khoản thanh toán đã thu.'
          const amount = parseMoney(f.amount)
          if (Number.isNaN(amount) || amount <= 0) e.amount = 'Nhập số tiền lớn hơn 0.'
          if (!f.method.trim()) e.refundMethod = 'Nhập hình thức hoàn.'
          return e
        },
        async (f) => {
          await refunds.create({ returnRequestId: request!.id, paymentId, amount: parseMoney(f.amount), refundMethod: f.method.trim(), reason: f.reason.trim() || null })
          onSaved(`Đã tạo hoàn tiền ${vndOf(parseMoney(f.amount))}.`)
        },
      )}
    >
      <Field label="Hoàn từ khoản thanh toán" htmlFor={`${id}-paymentId`} error={errors.paymentId} wide>
        <select {...bind(id, 'paymentId', errors)} value={paymentId} onChange={(e) => set('paymentId', e.target.value)}>
          {(paid.data ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.paymentMethod} | {vndOf(p.amount)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Số tiền hoàn" htmlFor={`${id}-amount`} error={errors.amount}>
        <input {...bind(id, 'amount', errors)} inputMode="numeric" value={form.amount} onChange={(e) => set('amount', e.target.value)} />
      </Field>
      <Field label="Hình thức hoàn" htmlFor={`${id}-refundMethod`} error={errors.refundMethod}>
        <input {...bind(id, 'refundMethod', errors)} maxLength={50} placeholder="Ví dụ: BANK_TRANSFER" value={form.method} onChange={(e) => set('method', e.target.value)} />
      </Field>
      <Field label="Lý do" htmlFor={`${id}-reason`} optional wide>
        <input id={`${id}-reason`} maxLength={500} value={form.reason} onChange={(e) => set('reason', e.target.value)} />
      </Field>
    </FormDialog>
  )
}
