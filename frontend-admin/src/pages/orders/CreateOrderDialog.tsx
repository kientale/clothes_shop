import { PlusIcon, TrashIcon } from '@phosphor-icons/react'
import { useId } from 'react'
import { api } from '../../api/client'
import { orders, warehouses, type Order } from '../../api/commerce'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { SearchPicker, vndOf, type PickOption } from '../../components/kit'
import { searchCustomers, searchVariants, type VariantOption } from '../../components/pickers'
import { useAsync } from '../../hooks'
import { parseMoney } from '../catalog/shared'

interface ShippingOption {
  code: string
  name: string
  provider: string
  baseFee: number
  estimatedDays: number | null
}

interface Line {
  key: number
  variant: VariantOption | null
  quantity: string
  discount: string
}

let lineKey = 0
const newLine = (): Line => ({ key: ++lineKey, variant: null, quantity: '1', discount: '' })

/** Admin-placed order: customer, warehouse, lines, then the backend prices and reserves stock. */
export function CreateOrderDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (order: Order) => void }) {
  const id = useId()
  const houses = useAsync(() => (open ? warehouses.all() : Promise.resolve([])), [open])
  // Public store configuration lists the enabled shipping methods without extra permissions.
  const config = useAsync(
    () => (open ? api.get<{ shippingMethods: ShippingOption[] }>('/store/configuration') : Promise.resolve({ shippingMethods: [] })),
    [open],
  )
  const activeHouses = (houses.data ?? []).filter((w) => w.status === 'ACTIVE')
  const methods = config.data?.shippingMethods ?? []

  const { form, set, errors, error, busy, submit } = useDialogForm(open, () => ({
    customer: null as PickOption | null,
    warehouseId: '',
    recipientName: '',
    recipientPhone: '',
    shippingAddress: '',
    note: '',
    lines: [newLine()],
    shippingMethodCode: '',
    shippingFee: '',
    discount: '',
    couponCode: '',
    applyMarketing: true,
  }))

  const warehouseId = form.warehouseId || activeHouses[0]?.id || ''
  const updateLine = (key: number, patch: Partial<Line>) =>
    set(
      'lines',
      form.lines.map((line) => (line.key === key ? { ...line, ...patch } : line)),
    )
  const subtotal = form.lines.reduce((sum, line) => sum + (line.variant ? line.variant.price * (Number(line.quantity) || 0) : 0), 0)

  return (
    <FormDialog
      open={open}
      title="Tạo đơn hàng"
      description="Giá lấy theo biến thể đang bán. Đơn mới ở trạng thái chờ xác nhận và giữ hàng ngay tại kho đã chọn."
      busy={busy}
      submitLabel="Tạo đơn"
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.customer) e.customerId = 'Chọn khách hàng.'
          if (!warehouseId) e.warehouseId = 'Chọn kho xuất hàng.'
          if (!f.recipientName.trim()) e.recipientName = 'Nhập tên người nhận.'
          if (!/^\+?[0-9]{8,15}$/.test(f.recipientPhone.trim())) e.recipientPhone = 'Số điện thoại gồm 8-15 chữ số.'
          if (!f.shippingAddress.trim()) e.shippingAddress = 'Nhập địa chỉ giao hàng.'
          const picked = f.lines.filter((l) => l.variant)
          if (picked.length === 0) e.items = 'Thêm ít nhất một sản phẩm.'
          else if (new Set(picked.map((l) => l.variant!.id)).size !== picked.length) e.items = 'Mỗi biến thể chỉ chọn một lần.'
          else if (picked.some((l) => !/^[1-9]\d*$/.test(l.quantity.trim()))) e.items = 'Số lượng phải là số nguyên dương.'
          else if (picked.some((l) => l.discount.trim() && Number.isNaN(parseMoney(l.discount)))) e.items = 'Giảm giá theo dòng không hợp lệ.'
          if (!f.shippingMethodCode && f.shippingFee.trim() && Number.isNaN(parseMoney(f.shippingFee))) e.shippingFee = 'Phí không hợp lệ.'
          if (f.discount.trim() && Number.isNaN(parseMoney(f.discount))) e.discountAmount = 'Số tiền không hợp lệ.'
          return e
        },
        async (f) => {
          const order = await orders.create({
            customerId: f.customer!.id,
            warehouseId,
            recipientName: f.recipientName.trim(),
            recipientPhone: f.recipientPhone.trim(),
            shippingAddress: f.shippingAddress.trim(),
            note: f.note.trim() || null,
            discountAmount: f.discount.trim() ? parseMoney(f.discount) : 0,
            shippingFee: !f.shippingMethodCode && f.shippingFee.trim() ? parseMoney(f.shippingFee) : 0,
            items: f.lines
              .filter((l) => l.variant)
              .map((l) => ({ productVariantId: l.variant!.id, quantity: Number(l.quantity), discountAmount: l.discount.trim() ? parseMoney(l.discount) : 0 })),
            couponCode: f.couponCode.trim() || null,
            applyMarketing: f.applyMarketing,
            shippingMethodCode: f.shippingMethodCode || null,
          })
          onCreated(order)
        },
      )}
    >
      <Field label="Khách hàng" htmlFor={`${id}-customer`} error={errors.customerId}>
        <SearchPicker
          inputId={`${id}-customer`}
          value={form.customer}
          onChange={(customer) => {
            set('customer', customer)
            if (customer && !form.recipientName) set('recipientName', customer.title)
          }}
          search={searchCustomers}
          placeholder="Chọn khách hàng"
          searchPlaceholder="Tìm theo tên, điện thoại, email"
          invalid={Boolean(errors.customerId)}
        />
      </Field>
      <Field label="Kho xuất hàng" htmlFor={`${id}-warehouseId`} error={errors.warehouseId}>
        <select {...bind(id, 'warehouseId', errors)} value={warehouseId} onChange={(e) => set('warehouseId', e.target.value)}>
          {activeHouses.length === 0 && <option value="">Chưa có kho đang dùng</option>}
          {activeHouses.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Người nhận" htmlFor={`${id}-recipientName`} error={errors.recipientName}>
        <input {...bind(id, 'recipientName', errors)} maxLength={150} value={form.recipientName} onChange={(e) => set('recipientName', e.target.value)} />
      </Field>
      <Field label="Số điện thoại" htmlFor={`${id}-recipientPhone`} error={errors.recipientPhone}>
        <input {...bind(id, 'recipientPhone', errors)} inputMode="tel" maxLength={16} value={form.recipientPhone} onChange={(e) => set('recipientPhone', e.target.value)} />
      </Field>
      <Field label="Địa chỉ giao hàng" htmlFor={`${id}-shippingAddress`} error={errors.shippingAddress} wide>
        <input {...bind(id, 'shippingAddress', errors)} maxLength={500} value={form.shippingAddress} onChange={(e) => set('shippingAddress', e.target.value)} />
      </Field>

      <fieldset className="line-editor span-2">
        <legend className="field-label">Sản phẩm</legend>
        <div className="line-head" aria-hidden>
          <span>Biến thể</span>
          <span>SL</span>
          <span>Giảm/dòng</span>
          <span />
        </div>
        {form.lines.map((line, index) => (
          <div key={line.key} className="line-row">
            <SearchPicker
              value={line.variant}
              onChange={(variant) => updateLine(line.key, { variant: variant as VariantOption | null })}
              search={searchVariants}
              placeholder={`Chọn sản phẩm ${index + 1}`}
              searchPlaceholder="Tìm theo SKU hoặc tên sản phẩm"
            />
            <input aria-label={`Số lượng dòng ${index + 1}`} inputMode="numeric" value={line.quantity} onChange={(e) => updateLine(line.key, { quantity: e.target.value })} />
            <input aria-label={`Giảm giá dòng ${index + 1}`} inputMode="numeric" placeholder="0" value={line.discount} onChange={(e) => updateLine(line.key, { discount: e.target.value })} />
            <button
              type="button"
              className="icon-btn danger"
              disabled={form.lines.length === 1}
              onClick={() => set('lines', form.lines.filter((l) => l.key !== line.key))}
              aria-label={`Xóa dòng ${index + 1}`}
              title="Xóa dòng"
            >
              <TrashIcon size={15} />
            </button>
          </div>
        ))}
        {errors.items && (
          <span className="field-error" role="alert">
            {errors.items}
          </span>
        )}
        <div className="line-foot">
          <button type="button" className="btn btn-plain btn-sm" onClick={() => set('lines', [...form.lines, newLine()])} disabled={form.lines.length >= 100}>
            <PlusIcon size={14} /> Thêm dòng
          </button>
          <span className="muted">
            Tạm tính theo giá niêm yết: <strong className="money">{vndOf(subtotal)}</strong>
          </span>
        </div>
      </fieldset>

      <Field label="Phương thức giao hàng" htmlFor={`${id}-method`} hint="Chọn phương thức để hệ thống tự tính phí, hoặc để trống và nhập phí tay.">
        <select id={`${id}-method`} value={form.shippingMethodCode} onChange={(e) => set('shippingMethodCode', e.target.value)}>
          <option value="">Nhập phí thủ công</option>
          {methods.map((m) => (
            <option key={m.code} value={m.code}>
              {m.name} ({vndOf(m.baseFee)})
            </option>
          ))}
        </select>
      </Field>
      <Field label="Phí giao hàng" htmlFor={`${id}-shippingFee`} error={errors.shippingFee} optional>
        <input
          {...bind(id, 'shippingFee', errors)}
          inputMode="numeric"
          placeholder={form.shippingMethodCode ? 'Tính theo phương thức' : '0'}
          disabled={Boolean(form.shippingMethodCode)}
          value={form.shippingMethodCode ? '' : form.shippingFee}
          onChange={(e) => set('shippingFee', e.target.value)}
        />
      </Field>
      <Field label="Mã giảm giá" htmlFor={`${id}-coupon`} optional>
        <input id={`${id}-coupon`} className="mono-input" maxLength={80} value={form.couponCode} onChange={(e) => set('couponCode', e.target.value.toUpperCase())} />
      </Field>
      <Field label="Giảm thêm trên đơn" htmlFor={`${id}-discountAmount`} error={errors.discountAmount} optional>
        <input {...bind(id, 'discountAmount', errors)} inputMode="numeric" placeholder="0" value={form.discount} onChange={(e) => set('discount', e.target.value)} />
      </Field>
      <label className="check span-2">
        <input type="checkbox" checked={form.applyMarketing} onChange={(e) => set('applyMarketing', e.target.checked)} />
        <span>
          <span className="check-title">Áp dụng khuyến mãi và Flash Sale đang chạy</span>
          <span className="check-sub">Hệ thống chọn ưu đãi tốt hơn cho từng dòng, không cộng dồn hai loại trên cùng dòng.</span>
        </span>
      </label>
      <Field label="Ghi chú" htmlFor={`${id}-note`} optional wide>
        <textarea id={`${id}-note`} rows={2} maxLength={1000} value={form.note} onChange={(e) => set('note', e.target.value)} />
      </Field>
    </FormDialog>
  )
}
