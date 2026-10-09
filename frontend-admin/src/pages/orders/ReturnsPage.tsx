import { ArrowsClockwiseIcon, CurrencyCircleDollarIcon, PlusIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import {
  REFUND_NEXT,
  REFUND_STATUS,
  RETURN_NEXT,
  RETURN_STATUS,
  RETURN_TYPE_LABEL,
  labelOf,
  refunds,
  returns,
  type Refund,
  type RefundStatus,
  type ReturnRequest,
  type ReturnStatus,
  type ReturnType,
} from '../../api/commerce'
import { DateRange, FilterSelect, ListPanel, PageHead, Pill, Ref, Segmented, StatePill, StatusDialog, rangeQuery, useCan, vndOf, whenOf, type DateRangeValue } from '../../components/kit'
import { useOrderCodes } from '../../components/pickers'
import { useToast } from '../../components/Toast'
import { CreateRefundDialog, CreateReturnDialog } from './dialogs'
import { OrderLink } from './PaymentsPage'

export default function ReturnsPage() {
  const canWrite = useCan('RETURN_WRITE')
  const canRefund = useCan('REFUND_WRITE')
  const toast = useToast()
  const [status, setStatus] = useState<ReturnStatus | undefined>()
  const [type, setType] = useState<ReturnType | ''>('')
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [rows, setRows] = useState<ReturnRequest[]>([])
  const [creating, setCreating] = useState(false)
  const [updating, setUpdating] = useState<ReturnRequest | null>(null)
  const [refunding, setRefunding] = useState<ReturnRequest | null>(null)
  const [reload, setReload] = useState(0)
  const codes = useOrderCodes(rows.map((r) => r.orderId))
  const query = { status, requestType: type || undefined, ...rangeQuery(range) }

  const done = (message: string) => {
    setCreating(false)
    setUpdating(null)
    setRefunding(null)
    toast.success(message)
    setReload((n) => n + 1)
  }

  return (
    <div className="page page-wide">
      <PageHead
        title="Đổi/trả hàng"
        lede="Duyệt yêu cầu, xác nhận đã nhận hàng để nhập lại kho, rồi tạo hoàn tiền cho yêu cầu trả hàng đã hoàn tất."
        actions={
          canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon size={16} weight="bold" /> Tạo yêu cầu
            </button>
          )
        }
      />
      <ListPanel<ReturnRequest>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => returns.list({ ...paging, ...query })}
        onLoaded={(page) => setRows(page.content)}
        rowKey={(r) => r.id}
        itemLabel="yêu cầu"
        emptyIcon={<ArrowsClockwiseIcon size={36} aria-hidden />}
        toolbar={
          <>
            <Segmented
              label="Lọc theo trạng thái"
              value={status}
              onChange={setStatus}
              options={[[undefined, 'Tất cả'], ...(Object.keys(RETURN_STATUS) as ReturnStatus[]).map((k) => [k, RETURN_STATUS[k][0]] as [ReturnStatus, string])]}
            />
            <FilterSelect label="Loại" value={type} options={Object.entries(RETURN_TYPE_LABEL) as [ReturnType, string][]} onChange={setType} />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Đơn hàng', render: (r) => <OrderLink id={r.orderId} codes={codes} /> },
          { header: 'Loại', render: (r) => <Pill tone={r.requestType === 'RETURN' ? 'warn' : 'info'}>{RETURN_TYPE_LABEL[r.requestType]}</Pill> },
          { header: 'Lý do', render: (r) => <span className="cell-wrap">{r.reason}</span> },
          {
            header: 'Ảnh khách gửi',
            render: (r) =>
              r.images.length ? (
                <span className="review-images">
                  {r.images.slice(0, 3).map((src) => (
                    <a key={src} href={src} target="_blank" rel="noreferrer" title="Mở ảnh">
                      <img src={src} alt="Ảnh sản phẩm khách gửi" loading="lazy" />
                    </a>
                  ))}
                  {r.images.length > 3 && <span className="muted small">+{r.images.length - 3}</span>}
                </span>
              ) : (
                <span className="muted">-</span>
              ),
          },
          { header: 'Số lượng', render: (r) => r.items.reduce((sum, i) => sum + i.quantity, 0), className: 'num' },
          { header: 'Có thể hoàn', render: (r) => (r.requestType === 'RETURN' ? vndOf(r.refundableAmount) : <span className="muted">-</span>), className: 'num nowrap' },
          { header: 'Trạng thái', render: (r) => <StatePill map={RETURN_STATUS} value={r.status} /> },
          { header: 'Ngày yêu cầu', render: (r) => whenOf(r.requestedAt), className: 'muted nowrap' },
        ]}
        actions={
          canWrite || canRefund
            ? (r) => (
                <>
                  {canRefund && r.status === 'COMPLETED' && r.requestType === 'RETURN' && (
                    <button type="button" className="icon-btn" onClick={() => setRefunding(r)} aria-label="Tạo hoàn tiền" title="Tạo hoàn tiền">
                      <CurrencyCircleDollarIcon size={16} />
                    </button>
                  )}
                  {canWrite && RETURN_NEXT[r.status].length > 0 && (
                    <button type="button" className="btn btn-plain btn-sm" onClick={() => setUpdating(r)}>
                      {r.status === 'REQUESTED' ? 'Duyệt' : 'Cập nhật'}
                    </button>
                  )}
                </>
              )
            : undefined
        }
      />

      <CreateReturnDialog open={creating} onClose={() => setCreating(false)} onSaved={done} />
      <StatusDialog<ReturnStatus>
        open={updating !== null}
        title="Xử lý yêu cầu đổi/trả"
        description="Hoàn tất nghĩa là đã nhận hàng: hàng trả được nhập lại kho, hàng đổi được xuất cho khách."
        options={updating ? RETURN_NEXT[updating.status] : []}
        labels={RETURN_STATUS}
        noteLabel="Ghi chú"
        onClose={() => setUpdating(null)}
        onSubmit={async (next, note) => {
          await returns.setStatus(updating!.id, next, note)
          done(`Yêu cầu đổi/trả: ${labelOf(RETURN_STATUS, next).toLowerCase()}.`)
        }}
      />
      <CreateRefundDialog open={refunding !== null} request={refunding} onClose={() => setRefunding(null)} onSaved={done} />
    </div>
  )
}

export function RefundsPage() {
  const canWrite = useCan('REFUND_WRITE')
  const toast = useToast()
  const [status, setStatus] = useState<RefundStatus | undefined>()
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [updating, setUpdating] = useState<Refund | null>(null)
  const [reload, setReload] = useState(0)
  const query = { status, ...rangeQuery(range) }

  return (
    <div className="page page-wide">
      <PageHead
        title="Hoàn tiền"
        lede="Khoản hoàn cho yêu cầu trả hàng đã hoàn tất. Tạo hoàn tiền từ màn Đổi/trả; xác nhận thành công khi đã chuyển tiền cho khách."
      />
      <ListPanel<Refund>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => refunds.list({ ...paging, ...query })}
        rowKey={(r) => r.id}
        itemLabel="khoản hoàn"
        emptyIcon={<CurrencyCircleDollarIcon size={36} aria-hidden />}
        toolbar={
          <>
            <Segmented
              label="Lọc theo trạng thái"
              value={status}
              onChange={setStatus}
              options={[[undefined, 'Tất cả'], ...(Object.keys(REFUND_STATUS) as RefundStatus[]).map((k) => [k, REFUND_STATUS[k][0]] as [RefundStatus, string])]}
            />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Yêu cầu trả', render: (r) => <Ref id={r.returnRequestId} /> },
          { header: 'Số tiền', render: (r) => <strong className="money">{vndOf(r.amount)}</strong>, className: 'num nowrap' },
          { header: 'Hình thức', render: (r) => <span className="chip chip-sm">{r.refundMethod}</span> },
          { header: 'Lý do', render: (r) => <span className="cell-wrap">{r.reason ?? <span className="muted">-</span>}</span> },
          { header: 'Trạng thái', render: (r) => <StatePill map={REFUND_STATUS} value={r.status} /> },
          { header: 'Xử lý lúc', render: (r) => whenOf(r.processedAt), className: 'nowrap' },
          { header: 'Ngày tạo', render: (r) => whenOf(r.createdAt), className: 'muted nowrap' },
        ]}
        actions={
          canWrite
            ? (r) =>
                REFUND_NEXT[r.status].length > 0 ? (
                  <button type="button" className="btn btn-plain btn-sm" onClick={() => setUpdating(r)}>
                    Cập nhật
                  </button>
                ) : null
            : undefined
        }
      />
      <StatusDialog<RefundStatus>
        open={updating !== null}
        title="Cập nhật hoàn tiền"
        description={updating ? `${vndOf(updating.amount)} qua ${updating.refundMethod}. Thất bại hoặc hủy sẽ trả lại hạn mức để tạo khoản khác.` : undefined}
        options={updating ? REFUND_NEXT[updating.status] : []}
        labels={REFUND_STATUS}
        onClose={() => setUpdating(null)}
        onSubmit={async (next) => {
          await refunds.setStatus(updating!.id, next)
          setUpdating(null)
          toast.success(`Hoàn tiền: ${labelOf(REFUND_STATUS, next).toLowerCase()}.`)
          setReload((n) => n + 1)
        }}
      />
    </div>
  )
}
