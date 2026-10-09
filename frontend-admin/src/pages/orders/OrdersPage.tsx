import { CaretRightIcon, PlusIcon, ReceiptIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  ORDER_PAYMENT_STATUS,
  ORDER_STATUS,
  SHIPPING_STATUS,
  orders,
  type Order,
  type OrderPaymentStatus,
  type OrderStatus,
  type ShippingStatus,
} from '../../api/commerce'
import { DateRange, FilterSelect, ListPanel, PageHead, SearchBox, StatePill, rangeQuery, useCan, vndOf, whenOf, type DateRangeValue } from '../../components/kit'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../components/ui'
import { CreateOrderDialog } from './CreateOrderDialog'

const STATUSES = Object.keys(ORDER_STATUS) as OrderStatus[]

export default function OrdersPage() {
  const canWrite = useCan('ORDER_WRITE')
  const navigate = useNavigate()
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const urlStatus = params.get('status') as OrderStatus | null
  const status = urlStatus && STATUSES.includes(urlStatus) ? urlStatus : undefined
  const [search, setSearch] = useState(params.get('search') ?? '')
  const [paymentStatus, setPaymentStatus] = useState<OrderPaymentStatus | ''>('')
  const [shippingStatus, setShippingStatus] = useState<ShippingStatus | ''>('')
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [creating, setCreating] = useState(false)
  const term = useDebounced(search.trim())

  const setStatus = (value: OrderStatus | undefined) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('status', value)
      else next.delete('status')
      next.delete('page')
      return next
    })

  const query = { search: term, status, paymentStatus: paymentStatus || undefined, shippingStatus: shippingStatus || undefined, ...rangeQuery(range) }

  return (
    <div className="page page-wide">
      <PageHead
        title="Đơn hàng"
        lede="Theo dõi đơn từ lúc đặt đến khi hoàn tất. Mở một đơn để xác nhận, ghi nhận thanh toán và tạo vận đơn."
        actions={
          canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon size={16} weight="bold" /> Tạo đơn
            </button>
          )
        }
      />

      <ListPanel<Order>
        filterKey={JSON.stringify(query)}
        load={(paging) => orders.list({ ...paging, ...query })}
        rowKey={(o) => o.id}
        itemLabel="đơn hàng"
        emptyIcon={<ReceiptIcon size={36} aria-hidden />}
        toolbar={
          <>
            <div className="segmented" role="group" aria-label="Lọc theo trạng thái đơn">
              {[undefined, ...STATUSES].map((value) => (
                <button key={value ?? 'all'} type="button" aria-pressed={status === value} onClick={() => setStatus(value)}>
                  {value ? ORDER_STATUS[value][0] : 'Tất cả'}
                </button>
              ))}
            </div>
            <div className="toolbar-break" />
            <SearchBox value={search} onChange={setSearch} placeholder="Mã đơn, người nhận hoặc số điện thoại" />
            <FilterSelect
              label="Thanh toán"
              value={paymentStatus}
              options={(Object.keys(ORDER_PAYMENT_STATUS) as OrderPaymentStatus[]).map((k) => [k, ORDER_PAYMENT_STATUS[k][0]])}
              onChange={setPaymentStatus}
            />
            <FilterSelect
              label="Giao hàng"
              value={shippingStatus}
              options={(Object.keys(SHIPPING_STATUS) as ShippingStatus[]).map((k) => [k, SHIPPING_STATUS[k][0]])}
              onChange={setShippingStatus}
            />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          {
            header: 'Mã đơn',
            render: (o) => (
              <Link to={`/orders/${o.id}`} className="row-link mono-strong">
                {o.orderCode}
              </Link>
            ),
          },
          {
            header: 'Người nhận',
            render: (o) => (
              <div className="person-text">
                <span className="person-name nowrap">{o.recipientName}</span>
                <span className="person-sub nowrap">{o.recipientPhone}</span>
              </div>
            ),
          },
          { header: 'Tổng tiền', render: (o) => <strong className="money">{vndOf(o.totalAmount)}</strong>, className: 'num nowrap' },
          { header: 'Thanh toán', render: (o) => <StatePill map={ORDER_PAYMENT_STATUS} value={o.paymentStatus} /> },
          { header: 'Giao hàng', render: (o) => <StatePill map={SHIPPING_STATUS} value={o.shippingStatus} /> },
          { header: 'Trạng thái', render: (o) => <StatePill map={ORDER_STATUS} value={o.orderStatus} /> },
          { header: 'Ngày đặt', render: (o) => whenOf(o.placedAt), className: 'muted nowrap' },
        ]}
        actions={(o) => (
          <Link to={`/orders/${o.id}`} className="icon-btn" aria-label={`Mở đơn ${o.orderCode}`} title="Xem chi tiết">
            <CaretRightIcon size={16} />
          </Link>
        )}
      />

      <CreateOrderDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(order) => {
          setCreating(false)
          toast.success(`Đã tạo đơn ${order.orderCode}.`)
          navigate(`/orders/${order.id}`)
        }}
      />
    </div>
  )
}
