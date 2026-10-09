import { ClockCounterClockwiseIcon, PencilSimpleIcon, PlusIcon, TruckIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { SHIPMENT_NEXT, SHIPPING_STATUS, labelOf, shipments, type Shipment, type ShipmentHistory, type ShipmentStatus } from '../../api/commerce'
import { DateRange, FilterSelect, ListPanel, PageHead, SearchBox, StatePill, StatusDialog, rangeQuery, useCan, vndOf, whenOf, type DateRangeValue } from '../../components/kit'
import { useOrderCodes } from '../../components/pickers'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../components/ui'
import { ShipmentDialog } from './dialogs'
import { OrderLink } from './PaymentsPage'

const SHIPMENT_STATUSES = (Object.keys(SHIPMENT_NEXT) as ShipmentStatus[]).map((k) => [k, SHIPPING_STATUS[k][0]] as [ShipmentStatus, string])

export default function ShipmentsPage() {
  const canWrite = useCan('SHIPMENT_WRITE')
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<ShipmentStatus | ''>('')
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [rows, setRows] = useState<Shipment[]>([])
  const [editing, setEditing] = useState<{ shipment: Shipment | null } | null>(null)
  const [updating, setUpdating] = useState<Shipment | null>(null)
  const [reload, setReload] = useState(0)
  const term = useDebounced(search.trim())
  const codes = useOrderCodes(rows.map((r) => r.orderId))
  const query = { search: term, status: status || undefined, ...rangeQuery(range) }

  const done = (message: string) => {
    setEditing(null)
    setUpdating(null)
    toast.success(message)
    setReload((n) => n + 1)
  }

  return (
    <div className="page page-wide">
      <PageHead
        title="Giao hàng"
        lede="Vận đơn của các đơn đã xác nhận. Hãng và mã vận đơn do admin nhập; trạng thái cập nhật theo thông tin từ hãng."
        actions={
          canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setEditing({ shipment: null })}>
              <PlusIcon size={16} weight="bold" /> Tạo vận đơn
            </button>
          )
        }
      />
      <ListPanel<Shipment>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => shipments.list({ ...paging, ...query })}
        onLoaded={(page) => setRows(page.content)}
        rowKey={(s) => s.id}
        itemLabel="vận đơn"
        emptyIcon={<TruckIcon size={36} aria-hidden />}
        toolbar={
          <>
            <SearchBox value={search} onChange={setSearch} placeholder="Tìm theo hãng hoặc mã vận đơn" />
            <FilterSelect label="Trạng thái" value={status} options={SHIPMENT_STATUSES} onChange={setStatus} />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Đơn hàng', render: (s) => <OrderLink id={s.orderId} codes={codes} /> },
          {
            header: 'Hãng vận chuyển',
            render: (s) => (
              <div className="person-text">
                <span className="person-name">{s.shippingProvider}</span>
                <span className="person-sub mono">{s.trackingCode ?? 'Chưa có mã vận đơn'}</span>
              </div>
            ),
          },
          { header: 'Phí thực tế', render: (s) => vndOf(s.shippingFee), className: 'num nowrap' },
          { header: 'Trạng thái', render: (s) => <StatePill map={SHIPPING_STATUS} value={s.status} /> },
          { header: 'Xuất kho', render: (s) => whenOf(s.shippedAt), className: 'nowrap' },
          { header: 'Đã giao', render: (s) => whenOf(s.deliveredAt), className: 'nowrap' },
        ]}
        actions={
          canWrite
            ? (s) => (
                <>
                  {s.status === 'PENDING' && (
                    <button type="button" className="icon-btn" onClick={() => setEditing({ shipment: s })} aria-label="Sửa vận đơn" title="Sửa">
                      <PencilSimpleIcon size={16} />
                    </button>
                  )}
                  {SHIPMENT_NEXT[s.status].length > 0 && (
                    <button type="button" className="btn btn-plain btn-sm" onClick={() => setUpdating(s)}>
                      Cập nhật
                    </button>
                  )}
                </>
              )
            : undefined
        }
      />

      <ShipmentDialog open={editing !== null} shipment={editing?.shipment} onClose={() => setEditing(null)} onSaved={done} />
      <StatusDialog<ShipmentStatus>
        open={updating !== null}
        title="Cập nhật vận đơn"
        description="Xuất kho trừ hàng đang giữ; Đã giao cập nhật đơn; Hoàn về kho nhập lại hàng và hủy đơn."
        options={updating ? SHIPMENT_NEXT[updating.status] : []}
        labels={SHIPPING_STATUS}
        noteLabel="Mô tả"
        onClose={() => setUpdating(null)}
        onSubmit={async (next, note) => {
          await shipments.setStatus(updating!.id, next, note)
          done(`Vận đơn: ${labelOf(SHIPPING_STATUS, next).toLowerCase()}.`)
        }}
      />
    </div>
  )
}

export function ShipmentHistoryPage() {
  const [status, setStatus] = useState<ShipmentStatus | ''>('')
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const query = { status: status || undefined, ...rangeQuery(range) }
  return (
    <div className="page page-wide">
      <PageHead title="Lịch sử giao hàng" lede="Mỗi lần vận đơn đổi trạng thái được ghi lại kèm mô tả. Lịch sử chỉ đọc." />
      <ListPanel<ShipmentHistory>
        filterKey={JSON.stringify(query)}
        load={(paging) => shipments.history({ ...paging, ...query })}
        rowKey={(h) => h.id}
        itemLabel="sự kiện"
        emptyIcon={<ClockCounterClockwiseIcon size={36} aria-hidden />}
        toolbar={
          <>
            <FilterSelect label="Trạng thái" value={status} options={SHIPMENT_STATUSES} onChange={setStatus} />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Thời gian', render: (h) => whenOf(h.createdAt), className: 'nowrap' },
          { header: 'Vận đơn', render: (h) => <span className="mono" title={h.shipmentId}>{h.shipmentId.slice(0, 8).toUpperCase()}</span> },
          { header: 'Trạng thái', render: (h) => <StatePill map={SHIPPING_STATUS} value={h.status} /> },
          { header: 'Mô tả', render: (h) => <span className="cell-wrap">{h.description ?? <span className="muted">-</span>}</span> },
        ]}
      />
    </div>
  )
}
