import { useState, type FormEvent } from 'react'
import { me } from '../api/store'
import type { ExchangeOption, Order, ReturnRequest, ReturnType } from '../api/types'
import { useAsync } from '../hooks'
import { ErrorBanner, Field } from './common'
import { ImageUploader } from './ImageUploader'

interface Line {
  chosen: boolean
  quantity: number
  replacement: string
}

/**
 * Asks the shop to take back (refund) or exchange items of a delivered order. Exchanges go to another size or
 * color of the same product at the price paid; the shop approves the request before anything is sent back.
 */
export function ReturnRequestForm({ order, claimed, onDone, onCancel }: {
  order: Order
  /** Quantity per order item already in open or finished requests. */
  claimed: Map<string, number>
  onDone: (request: ReturnRequest) => void
  onCancel: () => void
}) {
  const [type, setType] = useState<ReturnType>('RETURN')
  const [reason, setReason] = useState('')
  const [images, setImages] = useState<string[]>([])
  const [lines, setLines] = useState<Record<string, Line>>(() =>
    Object.fromEntries(order.items.map((item) => [item.id, { chosen: order.items.length === 1, quantity: 1, replacement: '' }])),
  )
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const options = useAsync(
    async () =>
      type === 'EXCHANGE'
        ? Object.fromEntries(
            await Promise.all(order.items.map(async (item) => [item.id, await me.exchangeOptions(order.id, item.id).catch(() => [])] as const)),
          )
        : ({} as Record<string, ExchangeOption[]>),
    [type, order.id],
  )

  const open = order.items.filter((item) => item.quantity - (claimed.get(item.id) ?? 0) > 0)
  const set = (id: string, patch: Partial<Line>) => setLines((current) => ({ ...current, [id]: { ...current[id], ...patch } }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const chosen = open.filter((item) => lines[item.id].chosen)
    if (chosen.length === 0) return setError(new Error('Chọn ít nhất một sản phẩm.'))
    if (!reason.trim()) return setError(new Error('Cho cửa hàng biết lý do đổi/trả.'))
    if (type === 'EXCHANGE' && chosen.some((item) => !lines[item.id].replacement))
      return setError(new Error('Chọn size/màu muốn đổi sang cho từng sản phẩm.'))
    setBusy(true)
    setError(undefined)
    try {
      onDone(
        await me.requestReturn({
          orderId: order.id,
          requestType: type,
          reason: reason.trim(),
          items: chosen.map((item) => ({
            orderItemId: item.id,
            quantity: lines[item.id].quantity,
            reason: null,
            replacementVariantId: type === 'EXCHANGE' ? lines[item.id].replacement : null,
          })),
          images,
        }),
      )
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  if (open.length === 0) {
    return <p className="muted">Mọi sản phẩm trong đơn đã có yêu cầu đổi/trả.</p>
  }

  return (
    <form className="return-form" onSubmit={submit}>
      <fieldset className="option-cards return-type">
        <legend className="field-label">Bạn muốn</legend>
        {(
          [
            ['RETURN', 'Trả hàng, hoàn tiền', 'Cửa hàng hoàn tiền sau khi nhận lại hàng.'],
            ['EXCHANGE', 'Đổi size / màu', 'Đổi sang size hoặc màu khác của cùng sản phẩm, cùng giá.'],
          ] as const
        ).map(([value, title, hint]) => (
          <label key={value} className="option-card">
            <input type="radio" name="return-type" checked={type === value} onChange={() => setType(value)} />
            <span className="option-card-body">
              <strong>{title}</strong>
              <span className="muted small">{hint}</span>
            </span>
          </label>
        ))}
      </fieldset>

      <fieldset className="return-lines">
        <legend className="field-label">Sản phẩm</legend>
        {open.map((item) => {
          const left = item.quantity - (claimed.get(item.id) ?? 0)
          const line = lines[item.id]
          const choices = options.data?.[item.id] ?? []
          return (
            <div key={item.id} className="return-line">
              <label className="check">
                <input type="checkbox" checked={line.chosen} onChange={(e) => set(item.id, { chosen: e.target.checked })} />
                <span>
                  {item.productName}
                  <span className="muted small">
                    {' '}
                    {item.colorName} / {item.sizeName}
                  </span>
                </span>
              </label>
              {line.chosen && (
                <div className="return-line-controls">
                  {left > 1 && (
                    <label className="inline-field">
                      Số lượng
                      <select value={line.quantity} onChange={(e) => set(item.id, { quantity: Number(e.target.value) })}>
                        {Array.from({ length: left }, (_, i) => i + 1).map((n) => (
                          <option key={n} value={n}>
                            {n}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                  {type === 'EXCHANGE' && (
                    <label className="inline-field">
                      Đổi sang
                      {options.loading ? (
                        <span className="muted small">Đang tải...</span>
                      ) : choices.length === 0 ? (
                        <span className="muted small">Hiện không có size/màu khác cùng giá.</span>
                      ) : (
                        <select value={line.replacement} onChange={(e) => set(item.id, { replacement: e.target.value })}>
                          <option value="">Chọn</option>
                          {choices.map((choice) => (
                            <option key={choice.variantId} value={choice.variantId} disabled={choice.available < line.quantity}>
                              {choice.colorName} / {choice.sizeName}
                              {choice.available < line.quantity ? ' (hết hàng)' : ''}
                            </option>
                          ))}
                        </select>
                      )}
                    </label>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </fieldset>

      <Field label="Lý do" hint="Ví dụ: chật ở vai, màu khác ảnh, đường may lỗi">
        <textarea rows={3} maxLength={2000} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
      <div className="field">
        <span className="field-label">Ảnh sản phẩm (nên có nếu hàng lỗi)</span>
        <ImageUploader value={images} onChange={setImages} max={6} />
      </div>
      <ErrorBanner error={error} />
      <div className="row-gap">
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Đang gửi...' : 'Gửi yêu cầu'}
        </button>
        <button type="button" className="btn btn-outline" onClick={onCancel} disabled={busy}>
          Để sau
        </button>
      </div>
    </form>
  )
}
