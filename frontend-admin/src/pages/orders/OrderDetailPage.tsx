import { ArrowLeftIcon, ArrowsClockwiseIcon, CreditCardIcon, PencilSimpleIcon, PlusIcon, TruckIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { accessErrorMessage } from '../../api/access'
import {
  ORDER_NEXT,
  ORDER_PAYMENT_STATUS,
  ORDER_STATUS,
  PAYMENT_NEXT,
  PAYMENT_STATUS,
  SHIPMENT_NEXT,
  SHIPPING_STATUS,
  labelOf,
  orders,
  payments,
  shipments,
  type Order,
  type OrderStatus,
  type Payment,
  type PaymentStatus,
  type Shipment,
  type ShipmentStatus,
} from '../../api/commerce'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Details, Pill, StatePill, StatusDialog, Timeline, useCan, vndOf, whenOf } from '../../components/kit'
import { useToast } from '../../components/Toast'
import { useAsync } from '../../hooks'
import { CreatePaymentDialog, CreateReturnDialog, ShipmentDialog } from './dialogs'

type Dialog =
  | { kind: 'status' }
  | { kind: 'edit' }
  | { kind: 'payment' }
  | { kind: 'payment-status'; payment: Payment }
  | { kind: 'shipment'; shipment?: Shipment }
  | { kind: 'shipment-status'; shipment: Shipment }
  | { kind: 'return' }

export default function OrderDetailPage() {
  const { id = '' } = useParams()
  const toast = useToast()
  const canOrder = useCan('ORDER_WRITE')
  const canPay = useCan('PAYMENT_WRITE')
  const canShip = useCan('SHIPMENT_WRITE')
  const canReturn = useCan('RETURN_WRITE')
  const canReadPay = useCan('PAYMENT_READ')
  const canReadShip = useCan('SHIPMENT_READ')
  const [dialog, setDialog] = useState<Dialog | null>(null)

  const order = useAsync(() => orders.get(id), [id])
  const history = useAsync(() => orders.history(id, { page: 0, size: 50 }), [id])
  const pays = useAsync(() => (canReadPay ? payments.list({ orderId: id, page: 0, size: 50 }) : Promise.resolve(null)), [id, canReadPay])
  const ships = useAsync(() => (canReadShip ? shipments.list({ orderId: id, page: 0, size: 50 }) : Promise.resolve(null)), [id, canReadShip])
  const ghtk = useAsync(() => (canShip ? shipments.ghtkStatus().catch(() => null) : Promise.resolve(null)), [canShip])
  const [booking, setBooking] = useState(false)
  const bookGhtk = async () => {
    setBooking(true)
    try {
      const created = await shipments.shipWithGhtk(id)
      done(`Đã tạo vận đơn GHTK ${created.trackingCode ?? ''}.`)
    } catch (err) {
      toast.error(accessErrorMessage(err))
    } finally {
      setBooking(false)
    }
  }

  const done = (message: string) => {
    setDialog(null)
    toast.success(message)
    order.reload()
    history.reload()
    pays.reload()
    ships.reload()
  }

  if (order.error) {
    return (
      <div className="page page-wide">
        <BackLink />
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(order.error)}{' '}
          <button type="button" className="link-btn" onClick={order.reload}>
            Thử lại
          </button>
        </div>
      </div>
    )
  }

  const o = order.data
  if (!o) {
    return (
      <div className="page page-wide" aria-busy="true">
        <BackLink />
        <span className="skel" style={{ width: '18ch', height: 32 }} />
        <div className="detail-grid">
          <section className="panel">
            <span className="skel skel-row" />
            <span className="skel skel-row" />
          </section>
          <section className="panel">
            <span className="skel skel-row" />
          </section>
        </div>
      </div>
    )
  }

  const next = ORDER_NEXT[o.orderStatus]
  const remaining = o.orderStatus === 'CANCELLED' ? 0 : Math.max(0, o.totalAmount - o.paidAmount)
  const editable = o.orderStatus === 'PLACED' || o.orderStatus === 'CONFIRMED'
  const activeShipment = ships.data?.content.find((s) => s.status !== 'CANCELLED')
  const returnable = o.orderStatus === 'DELIVERED' || o.orderStatus === 'COMPLETED' || o.shippingStatus === 'RETURNED'

  return (
    <div className="page page-wide">
      <BackLink />
      <header className="page-head">
        <div>
          <div className="title-row">
            <h1 className="page-title mono-title">{o.orderCode}</h1>
            <StatePill map={ORDER_STATUS} value={o.orderStatus} />
          </div>
          <p className="page-lede">
            Đặt lúc {whenOf(o.placedAt)}
            {o.confirmedAt && <> | xác nhận {whenOf(o.confirmedAt)}</>}
            {o.completedAt && <> | hoàn tất {whenOf(o.completedAt)}</>}
            {o.cancelledAt && <> | hủy {whenOf(o.cancelledAt)}</>}
          </p>
        </div>
        <div className="page-head-actions">
          {canReturn && returnable && (
            <button type="button" className="btn btn-secondary" onClick={() => setDialog({ kind: 'return' })}>
              <ArrowsClockwiseIcon size={16} /> Đổi/trả
            </button>
          )}
          {canOrder && next.length > 0 && (
            <button type="button" className="btn btn-primary" onClick={() => setDialog({ kind: 'status' })}>
              {next[0] === 'CONFIRMED' ? 'Xác nhận đơn' : next[0] === 'COMPLETED' ? 'Hoàn tất đơn' : 'Đổi trạng thái'}
            </button>
          )}
        </div>
      </header>

      <div className="detail-grid">
        <div className="detail-main">
          <section className="panel">
            <h2 className="section-title">Sản phẩm ({o.items.length})</h2>
            <ul className="order-lines">
              {o.items.map((item) => (
                <li key={item.id}>
                  <div className="person-text">
                    <span className="person-name">{item.productName}</span>
                    <span className="person-sub">
                      {item.colorName} / {item.sizeName} <span className="mono">{item.sku}</span>
                    </span>
                  </div>
                  <span className="muted nowrap">
                    {vndOf(item.unitPrice)} x {item.quantity}
                  </span>
                  <span className="order-line-total">
                    {item.discountAmount > 0 && <span className="muted small strike">{vndOf(item.unitPrice * item.quantity)}</span>}
                    <strong className="money">{vndOf(item.totalAmount)}</strong>
                  </span>
                </li>
              ))}
            </ul>
            <dl className="totals">
              <div>
                <dt>Tiền hàng</dt>
                <dd>{vndOf(o.subtotal)}</dd>
              </div>
              {o.discountAmount > 0 && (
                <div>
                  <dt>Giảm giá</dt>
                  <dd>-{vndOf(o.discountAmount)}</dd>
                </div>
              )}
              <div>
                <dt>Phí giao hàng{o.shippingMethodCode && <span className="muted"> ({o.shippingMethodCode})</span>}</dt>
                <dd>{vndOf(o.shippingFee)}</dd>
              </div>
              <div className="totals-grand">
                <dt>Tổng cộng</dt>
                <dd>{vndOf(o.totalAmount)}</dd>
              </div>
            </dl>
          </section>

          {canReadPay && (
            <section className="panel">
              <div className="section-head">
                <h2 className="section-title">Thanh toán</h2>
                {canPay && o.orderStatus !== 'CANCELLED' && remaining > 0 && (
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setDialog({ kind: 'payment' })}>
                    <PlusIcon size={14} /> Ghi nhận thanh toán
                  </button>
                )}
              </div>
              {pays.data?.content.length ? (
                <ul className="record-list">
                  {pays.data.content.map((p) => (
                    <li key={p.id}>
                      <span className="record-icon" aria-hidden>
                        <CreditCardIcon size={16} />
                      </span>
                      <div className="person-text">
                        <span className="person-name">
                          {vndOf(p.amount)} <span className="muted">qua {p.paymentMethod}</span>
                        </span>
                        <span className="person-sub">{p.paidAt ? `Đã thu ${whenOf(p.paidAt)}` : `Tạo ${whenOf(p.createdAt)}`}</span>
                      </div>
                      <StatePill map={PAYMENT_STATUS} value={p.status} />
                      {canPay && PAYMENT_NEXT[p.status].length > 0 && (
                        <button type="button" className="btn btn-plain btn-sm" onClick={() => setDialog({ kind: 'payment-status', payment: p })}>
                          Cập nhật
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="muted small">{pays.loading ? 'Đang tải...' : 'Chưa có khoản thanh toán nào.'}</p>
              )}
            </section>
          )}

          {canReadShip && (
            <section className="panel">
              <div className="section-head">
                <h2 className="section-title">Giao hàng</h2>
                {canShip && o.orderStatus === 'CONFIRMED' && !activeShipment && (
                  <div className="row-actions">
                    {ghtk.data?.configured && (
                      <button type="button" className="btn btn-primary btn-sm" onClick={bookGhtk} disabled={booking}
                        title="Đẩy đơn sang Giao Hàng Tiết Kiệm; trạng thái giao tự cập nhật qua webhook">
                        <TruckIcon size={14} /> {booking ? 'Đang gửi GHTK...' : 'Gửi qua GHTK'}
                      </button>
                    )}
                    <button type="button" className="btn btn-secondary btn-sm" onClick={() => setDialog({ kind: 'shipment' })}>
                      <PlusIcon size={14} /> Tạo vận đơn
                    </button>
                  </div>
                )}
              </div>
              {ships.data?.content.length ? (
                <ul className="record-list">
                  {ships.data.content.map((s) => (
                    <li key={s.id}>
                      <span className="record-icon" aria-hidden>
                        <TruckIcon size={16} />
                      </span>
                      <div className="person-text">
                        <span className="person-name">
                          {s.shippingProvider} {s.trackingCode && <span className="mono">{s.trackingCode}</span>}
                        </span>
                        <span className="person-sub">
                          Phí {vndOf(s.shippingFee)}
                          {s.deliveredAt ? ` | giao ${whenOf(s.deliveredAt)}` : s.shippedAt ? ` | xuất kho ${whenOf(s.shippedAt)}` : ''}
                        </span>
                      </div>
                      <StatePill map={SHIPPING_STATUS} value={s.status} />
                      {canShip && s.status === 'PENDING' && (
                        <button type="button" className="icon-btn" onClick={() => setDialog({ kind: 'shipment', shipment: s })} aria-label="Sửa vận đơn" title="Sửa vận đơn">
                          <PencilSimpleIcon size={15} />
                        </button>
                      )}
                      {canShip && SHIPMENT_NEXT[s.status].length > 0 && (
                        <button type="button" className="btn btn-plain btn-sm" onClick={() => setDialog({ kind: 'shipment-status', shipment: s })}>
                          Cập nhật
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="muted small">
                  {ships.loading ? 'Đang tải...' : o.orderStatus === 'PLACED' ? 'Xác nhận đơn để tạo vận đơn.' : 'Chưa có vận đơn.'}
                </p>
              )}
            </section>
          )}
        </div>

        <aside className="detail-side">
          <section className="panel">
            <div className="section-head">
              <h2 className="section-title">Người nhận</h2>
              {canOrder && editable && (
                <button type="button" className="icon-btn" onClick={() => setDialog({ kind: 'edit' })} aria-label="Sửa thông tin nhận hàng" title="Sửa">
                  <PencilSimpleIcon size={15} />
                </button>
              )}
            </div>
            <Details
              items={[
                ['Họ tên', o.recipientName],
                ['Điện thoại', o.recipientPhone],
                ...(o.contactEmail ? [['Email (khách vãng lai)', o.contactEmail] as [string, string]] : []),
                ['Địa chỉ', o.shippingAddress],
                ['Ghi chú', o.note || <span className="muted">Không có</span>],
              ]}
            />
          </section>

          <section className="panel">
            <h2 className="section-title">Tình trạng</h2>
            <Details
              items={[
                ['Thanh toán', <StatePill map={ORDER_PAYMENT_STATUS} value={o.paymentStatus} />],
                ['Giao hàng', <StatePill map={SHIPPING_STATUS} value={o.shippingStatus} />],
                ['Đã thu', <strong className="money">{vndOf(o.paidAmount)}</strong>],
                ['Đã hoàn', vndOf(o.refundedAmount)],
                ['Còn phải thu', remaining > 0 ? <Pill tone="warn">{vndOf(remaining)}</Pill> : <Pill tone="active">Đủ</Pill>],
              ]}
            />
          </section>

          <section className="panel">
            <h2 className="section-title">Lịch sử</h2>
            <Timeline
              empty="Chưa có thay đổi trạng thái."
              entries={(history.data?.content ?? []).map((h) => ({
                id: h.id,
                title: h.fromStatus ? `${labelOf(ORDER_STATUS, h.fromStatus)} → ${labelOf(ORDER_STATUS, h.toStatus)}` : labelOf(ORDER_STATUS, h.toStatus),
                meta: whenOf(h.createdAt),
                body: h.note,
              }))}
            />
          </section>
        </aside>
      </div>

      <StatusDialog<OrderStatus>
        open={dialog?.kind === 'status'}
        title="Đổi trạng thái đơn"
        description="Hủy trước khi xuất kho sẽ trả hàng giữ và hủy các thanh toán đang chờ. Trạng thái giao hàng cập nhật qua vận đơn."
        options={next}
        labels={ORDER_STATUS}
        noteLabel="Ghi chú"
        onClose={() => setDialog(null)}
        onSubmit={async (status, note) => {
          await orders.setStatus(o.id, status, note)
          done(`Đơn ${o.orderCode}: ${labelOf(ORDER_STATUS, status).toLowerCase()}.`)
        }}
      />
      <EditRecipientDialog open={dialog?.kind === 'edit'} order={o} onClose={() => setDialog(null)} onSaved={done} />
      <CreatePaymentDialog open={dialog?.kind === 'payment'} order={o} onClose={() => setDialog(null)} onSaved={done} />
      <StatusDialog<PaymentStatus>
        open={dialog?.kind === 'payment-status'}
        title="Cập nhật thanh toán"
        description={dialog?.kind === 'payment-status' ? `${vndOf(dialog.payment.amount)} qua ${dialog.payment.paymentMethod}` : undefined}
        options={dialog?.kind === 'payment-status' ? PAYMENT_NEXT[dialog.payment.status] : []}
        labels={PAYMENT_STATUS}
        extra={{ label: 'Mã giao dịch ngân hàng / hóa đơn', hint: 'Mã phải là duy nhất.', when: (s) => s === 'PAID' }}
        onClose={() => setDialog(null)}
        onSubmit={async (status, _note, code) => {
          if (dialog?.kind !== 'payment-status') return
          await payments.setStatus(dialog.payment.id, status, code)
          done(`Thanh toán: ${labelOf(PAYMENT_STATUS, status).toLowerCase()}.`)
        }}
      />
      <ShipmentDialog
        open={dialog?.kind === 'shipment'}
        order={o}
        shipment={dialog?.kind === 'shipment' ? dialog.shipment : null}
        onClose={() => setDialog(null)}
        onSaved={done}
      />
      <StatusDialog<ShipmentStatus>
        open={dialog?.kind === 'shipment-status'}
        title="Cập nhật vận đơn"
        description="Xuất kho trừ hàng đang giữ; Đã giao cập nhật đơn; Hoàn về kho nhập lại hàng và hủy đơn."
        options={dialog?.kind === 'shipment-status' ? SHIPMENT_NEXT[dialog.shipment.status] : []}
        labels={SHIPPING_STATUS}
        noteLabel="Mô tả"
        onClose={() => setDialog(null)}
        onSubmit={async (status, note) => {
          if (dialog?.kind !== 'shipment-status') return
          await shipments.setStatus(dialog.shipment.id, status, note)
          done(`Vận đơn: ${labelOf(SHIPPING_STATUS, status).toLowerCase()}.`)
        }}
      />
      <CreateReturnDialog open={dialog?.kind === 'return'} order={o} onClose={() => setDialog(null)} onSaved={done} />
    </div>
  )
}

function BackLink() {
  return (
    <Link to="/orders" className="back-link">
      <ArrowLeftIcon size={14} /> Tất cả đơn hàng
    </Link>
  )
}

function EditRecipientDialog({ open, order, onClose, onSaved }: { open: boolean; order: Order; onClose: () => void; onSaved: (m: string) => void }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({ recipientName: order.recipientName, recipientPhone: order.recipientPhone, shippingAddress: order.shippingAddress, note: order.note ?? '' }),
    [order.id],
  )
  return (
    <FormDialog
      open={open}
      title="Sửa thông tin nhận hàng"
      description="Chỉ sửa được trước khi xuất kho. Giá, khách hàng, kho và dòng hàng giữ nguyên."
      busy={busy}
      submitLabel="Lưu thay đổi"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.recipientName.trim()) e.recipientName = 'Nhập tên người nhận.'
          if (!/^\+?[0-9]{8,15}$/.test(f.recipientPhone.trim())) e.recipientPhone = 'Số điện thoại gồm 8-15 chữ số.'
          if (!f.shippingAddress.trim()) e.shippingAddress = 'Nhập địa chỉ.'
          return e
        },
        async (f) => {
          await orders.update(order.id, {
            recipientName: f.recipientName.trim(),
            recipientPhone: f.recipientPhone.trim(),
            shippingAddress: f.shippingAddress.trim(),
            note: f.note.trim() || null,
          })
          onSaved('Đã cập nhật thông tin nhận hàng.')
        },
      )}
    >
      <Field label="Người nhận" htmlFor={`${id}-recipientName`} error={errors.recipientName}>
        <input {...bind(id, 'recipientName', errors)} value={form.recipientName} maxLength={150} onChange={(e) => set('recipientName', e.target.value)} />
      </Field>
      <Field label="Số điện thoại" htmlFor={`${id}-recipientPhone`} error={errors.recipientPhone}>
        <input {...bind(id, 'recipientPhone', errors)} value={form.recipientPhone} maxLength={16} onChange={(e) => set('recipientPhone', e.target.value)} />
      </Field>
      <Field label="Địa chỉ" htmlFor={`${id}-shippingAddress`} error={errors.shippingAddress} wide>
        <input {...bind(id, 'shippingAddress', errors)} value={form.shippingAddress} maxLength={500} onChange={(e) => set('shippingAddress', e.target.value)} />
      </Field>
      <Field label="Ghi chú" htmlFor={`${id}-note`} optional wide>
        <textarea id={`${id}-note`} rows={2} value={form.note} maxLength={1000} onChange={(e) => set('note', e.target.value)} />
      </Field>
    </FormDialog>
  )
}
