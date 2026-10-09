import { CreditCardIcon, PlusIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PAYMENT_NEXT, PAYMENT_STATUS, labelOf, payments, type Payment, type PaymentStatus } from '../../api/commerce'
import { DateRange, ListPanel, PageHead, Segmented, StatePill, StatusDialog, rangeQuery, useCan, vndOf, whenOf, type DateRangeValue } from '../../components/kit'
import { useOrderCodes } from '../../components/pickers'
import { useToast } from '../../components/Toast'
import { CreatePaymentDialog } from './dialogs'

export function OrderLink({ id, codes }: { id: string; codes: Record<string, string> }) {
  return (
    <Link to={`/orders/${id}`} className="row-link mono-strong" title={id}>
      {codes[id] ?? id.slice(0, 8).toUpperCase()}
    </Link>
  )
}

export default function PaymentsPage() {
  const canWrite = useCan('PAYMENT_WRITE')
  const toast = useToast()
  const [status, setStatus] = useState<PaymentStatus | undefined>()
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [rows, setRows] = useState<Payment[]>([])
  const [creating, setCreating] = useState(false)
  const [updating, setUpdating] = useState<Payment | null>(null)
  const [reload, setReload] = useState(0)
  const codes = useOrderCodes(rows.map((r) => r.orderId))
  const query = { status, ...rangeQuery(range) }

  const done = (message: string) => {
    setCreating(false)
    setUpdating(null)
    toast.success(message)
    setReload((n) => n + 1)
  }

  return (
    <div className="page page-wide">
      <PageHead
        title="Thanh toán"
        lede="Các khoản thu theo đơn. Thanh toán do admin xác nhận thủ công; tiền đã thu không hủy được, dùng hoàn tiền khi cần trả lại."
        actions={
          canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon size={16} weight="bold" /> Ghi nhận thanh toán
            </button>
          )
        }
      />
      <ListPanel<Payment>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => payments.list({ ...paging, ...query })}
        onLoaded={(page) => setRows(page.content)}
        rowKey={(p) => p.id}
        itemLabel="thanh toán"
        emptyIcon={<CreditCardIcon size={36} aria-hidden />}
        toolbar={
          <>
            <Segmented
              label="Lọc theo trạng thái"
              value={status}
              onChange={setStatus}
              options={[[undefined, 'Tất cả'], ...(Object.keys(PAYMENT_STATUS) as PaymentStatus[]).map((k) => [k, PAYMENT_STATUS[k][0]] as [PaymentStatus, string])]}
            />
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Đơn hàng', render: (p) => <OrderLink id={p.orderId} codes={codes} /> },
          { header: 'Phương thức', render: (p) => <span className="chip chip-sm">{p.paymentMethod}</span> },
          { header: 'Số tiền', render: (p) => <strong className="money">{vndOf(p.amount)}</strong>, className: 'num nowrap' },
          { header: 'Trạng thái', render: (p) => <StatePill map={PAYMENT_STATUS} value={p.status} /> },
          { header: 'Thời điểm thu', render: (p) => whenOf(p.paidAt), className: 'nowrap' },
          { header: 'Ngày tạo', render: (p) => whenOf(p.createdAt), className: 'muted nowrap' },
        ]}
        actions={
          canWrite
            ? (p) =>
                PAYMENT_NEXT[p.status].length > 0 ? (
                  <button type="button" className="btn btn-plain btn-sm" onClick={() => setUpdating(p)}>
                    Cập nhật
                  </button>
                ) : null
            : undefined
        }
      />

      <CreatePaymentDialog open={creating} onClose={() => setCreating(false)} onSaved={done} />
      <StatusDialog<PaymentStatus>
        open={updating !== null}
        title="Cập nhật thanh toán"
        description={updating ? `${vndOf(updating.amount)} qua ${updating.paymentMethod}` : undefined}
        options={updating ? PAYMENT_NEXT[updating.status] : []}
        labels={PAYMENT_STATUS}
        extra={{ label: 'Mã giao dịch ngân hàng / hóa đơn', hint: 'Mã phải là duy nhất.', when: (s) => s === 'PAID' }}
        onClose={() => setUpdating(null)}
        onSubmit={async (next, _note, code) => {
          await payments.setStatus(updating!.id, next, code)
          done(`Thanh toán: ${labelOf(PAYMENT_STATUS, next).toLowerCase()}.`)
        }}
      />
    </div>
  )
}
