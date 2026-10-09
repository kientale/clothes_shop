import { ArrowDownLeftIcon, ArrowUpRightIcon, ClockCounterClockwiseIcon, ListChecksIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { MOVEMENT_LABEL, inventory, warehouses, type Movement, type MovementType } from '../../api/commerce'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import {
  DateRange,
  FilterSelect,
  ListPanel,
  PageHead,
  Pill,
  SearchPicker,
  countOf,
  rangeQuery,
  useCan,
  whenOf,
  type DateRangeValue,
  type PickOption,
} from '../../components/kit'
import { searchVariants, useVariantNames } from '../../components/pickers'
import { useToast } from '../../components/Toast'
import { useAsync } from '../../hooks'

const INBOUND: MovementType[] = ['RECEIPT', 'RETURN', 'RELEASE']

/**
 * Stock ledger. `mode="transactions"` lists every movement and records manual receipts/issues;
 * `mode="history"` shows only the stock-count adjustments.
 */
export default function MovementsPage({ mode }: { mode: 'transactions' | 'history' }) {
  const canWrite = useCan('INVENTORY_WRITE') && mode === 'transactions'
  const toast = useToast()
  const [warehouseId, setWarehouseId] = useState('')
  const [type, setType] = useState<MovementType | ''>('')
  const [variant, setVariant] = useState<PickOption | null>(null)
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' })
  const [moving, setMoving] = useState<'RECEIPT' | 'ISSUE' | null>(null)
  const [reload, setReload] = useState(0)
  const [rows, setRows] = useState<Movement[]>([])
  const houses = useAsync(() => warehouses.all(), [])
  const houseName = (id: string) => houses.data?.find((w) => w.id === id)?.name ?? '-'
  const names = useVariantNames(rows.map((r) => r.productVariantId))
  const history = mode === 'history'

  const query = {
    warehouseId: warehouseId || undefined,
    productVariantId: variant?.id,
    transactionType: history ? undefined : type || undefined,
    ...rangeQuery(range),
  }

  return (
    <div className="page page-wide">
      <PageHead
        title={history ? 'Lịch sử điều chỉnh tồn kho' : 'Nhập/xuất kho'}
        lede={
          history
            ? 'Các lần kiểm kê đã ghi nhận: số trước và sau, người thực hiện và lý do. Lịch sử chỉ đọc.'
            : 'Sổ kho ghi mọi biến động. Nhập và xuất thủ công tạo tại đây; giữ hàng, xuất giao và nhập trả do đơn hàng sinh ra.'
        }
        actions={
          canWrite && (
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setMoving('ISSUE')}>
                <ArrowUpRightIcon size={16} /> Xuất kho
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setMoving('RECEIPT')}>
                <ArrowDownLeftIcon size={16} weight="bold" /> Nhập kho
              </button>
            </>
          )
        }
      />

      <ListPanel<Movement>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => (history ? inventory.history({ ...paging, ...query }) : inventory.transactions({ ...paging, ...query }))}
        onLoaded={(page) => setRows(page.content)}
        rowKey={(row) => row.id}
        itemLabel={history ? 'lần điều chỉnh' : 'giao dịch kho'}
        emptyIcon={history ? <ClockCounterClockwiseIcon size={36} aria-hidden /> : <ListChecksIcon size={36} aria-hidden />}
        toolbar={
          <>
            <div className="toolbar-picker">
              <SearchPicker value={variant} onChange={setVariant} search={searchVariants} placeholder="Tất cả biến thể" searchPlaceholder="Tìm SKU hoặc sản phẩm" clearable />
            </div>
            <FilterSelect label="Kho" value={warehouseId} options={(houses.data ?? []).map((w) => [w.id, w.name])} onChange={setWarehouseId} />
            {!history && <FilterSelect label="Loại" value={type} options={Object.entries(MOVEMENT_LABEL) as [MovementType, string][]} onChange={setType} />}
            <DateRange value={range} onChange={setRange} />
          </>
        }
        columns={[
          { header: 'Thời gian', render: (m) => whenOf(m.createdAt), className: 'nowrap' },
          ...(history
            ? []
            : [
                {
                  header: 'Loại',
                  render: (m: Movement) => <Pill tone={INBOUND.includes(m.transactionType) ? 'active' : m.transactionType === 'ADJUSTMENT' ? 'info' : 'warn'}>{MOVEMENT_LABEL[m.transactionType]}</Pill>,
                },
              ]),
          { header: 'Biến thể', render: (m) => <span className="cell-wrap">{names[m.productVariantId] ?? '...'}</span> },
          { header: 'Kho', render: (m) => houseName(m.warehouseId) },
          {
            header: 'Thay đổi',
            render: (m) => {
              const diff = m.quantityAfter - m.quantityBefore
              return <span className={diff > 0 ? 'delta up' : diff < 0 ? 'delta down' : 'delta'}>{diff > 0 ? `+${countOf(diff)}` : countOf(diff)}</span>
            },
            className: 'num',
          },
          { header: 'Tồn trước → sau', render: (m) => `${countOf(m.quantityBefore)} → ${countOf(m.quantityAfter)}`, className: 'num nowrap muted' },
          { header: 'Giữ trước → sau', render: (m) => `${countOf(m.reservedBefore)} → ${countOf(m.reservedAfter)}`, className: 'num nowrap muted' },
          { header: history ? 'Lý do' : 'Ghi chú', render: (m) => <span className="cell-wrap">{m.note ?? <span className="muted">-</span>}</span> },
        ]}
      />

      <MovementDialog
        type={moving}
        warehouses={(houses.data ?? []).filter((w) => w.status === 'ACTIVE').map((w) => [w.id, w.name])}
        onClose={() => setMoving(null)}
        onSaved={(message) => {
          setMoving(null)
          toast.success(message)
          setReload((n) => n + 1)
        }}
      />
    </div>
  )
}

function MovementDialog({
  type,
  warehouses: houses,
  onClose,
  onSaved,
}: {
  type: 'RECEIPT' | 'ISSUE' | null
  warehouses: [string, string][]
  onClose: () => void
  onSaved: (message: string) => void
}) {
  const id = useId()
  const receipt = type === 'RECEIPT'
  const { form, set, errors, error, busy, submit } = useDialogForm(
    type !== null,
    () => ({ warehouseId: houses[0]?.[0] ?? '', variant: null as PickOption | null, quantity: '', note: '' }),
    [type],
  )
  return (
    <FormDialog
      open={type !== null}
      title={receipt ? 'Nhập kho' : 'Xuất kho'}
      description={receipt ? 'Cộng số lượng vào tồn thực tế của SKU tại kho.' : 'Trừ từ phần khả dụng; không lấy được hàng đang giữ cho đơn.'}
      busy={busy}
      submitLabel={receipt ? 'Nhập kho' : 'Xuất kho'}
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.warehouseId) e.warehouseId = 'Chọn kho.'
          if (!f.variant) e.productVariantId = 'Chọn biến thể.'
          if (!/^[1-9]\d*$/.test(f.quantity.trim())) e.quantity = 'Nhập số nguyên dương.'
          return e
        },
        async (f) => {
          await inventory.move({
            warehouseId: f.warehouseId,
            productVariantId: f.variant!.id,
            transactionType: type!,
            quantity: Number(f.quantity),
            note: f.note.trim() || null,
          })
          onSaved(`${receipt ? 'Đã nhập' : 'Đã xuất'} ${f.quantity} ${f.variant!.title}.`)
        },
      )}
    >
      <Field label="Kho" htmlFor={`${id}-warehouseId`} error={errors.warehouseId}>
        <select {...bind(id, 'warehouseId', errors)} value={form.warehouseId} onChange={(e) => set('warehouseId', e.target.value)}>
          <option value="">Chọn kho</option>
          {houses.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Số lượng" htmlFor={`${id}-quantity`} error={errors.quantity}>
        <input {...bind(id, 'quantity', errors)} inputMode="numeric" value={form.quantity} onChange={(e) => set('quantity', e.target.value)} />
      </Field>
      <Field label="Biến thể sản phẩm" htmlFor={`${id}-variant`} error={errors.productVariantId} wide>
        <SearchPicker
          inputId={`${id}-variant`}
          value={form.variant}
          onChange={(v) => set('variant', v)}
          search={searchVariants}
          placeholder="Chọn biến thể"
          searchPlaceholder="Tìm theo SKU hoặc tên sản phẩm"
          invalid={Boolean(errors.productVariantId)}
        />
      </Field>
      <Field label="Ghi chú" htmlFor={`${id}-note`} optional wide>
        <textarea id={`${id}-note`} rows={2} maxLength={500} value={form.note} onChange={(e) => set('note', e.target.value)} placeholder={receipt ? 'Ví dụ: nhập từ nhà cung cấp' : 'Ví dụ: xuất mẫu chụp hình'} />
      </Field>
    </FormDialog>
  )
}
