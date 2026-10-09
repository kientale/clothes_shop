import { ArrowsDownUpIcon, PackageIcon, PlusIcon, WarehouseIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { WAREHOUSE_STATUS_LABEL, inventory, warehouses, type Stock, type Warehouse, type WarehouseStatus } from '../../api/commerce'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { FilterSelect, ListPanel, PageHead, Pill, SearchBox, SearchPicker, countOf, useCan, whenOf, type PickOption } from '../../components/kit'
import { searchVariants } from '../../components/pickers'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'
import { useDebounced } from '../../components/ui'
import { useAsync } from '../../hooks'

/** Below this many available units a stock line is flagged, matching the API's lowStock filter. */
const LOW_STOCK = 5

function InventoryTabs() {
  const [params] = useSearchParams()
  const warehousesView = params.get('view') === 'warehouses'
  return (
    <nav className="subtabs" aria-label="Mục kho hàng">
      <Link to="/inventory" aria-current={warehousesView ? undefined : 'page'}>
        Tồn kho theo SKU
      </Link>
      <Link to="/inventory?view=warehouses" aria-current={warehousesView ? 'page' : undefined}>
        Danh sách kho
      </Link>
    </nav>
  )
}

export default function InventoryPage() {
  const [params] = useSearchParams()
  return params.get('view') === 'warehouses' ? <WarehousesView /> : <StockView />
}

function StockView() {
  const canWrite = useCan('INVENTORY_WRITE')
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [warehouseId, setWarehouseId] = useState('')
  const [lowStock, setLowStock] = useState(false)
  const [reload, setReload] = useState(0)
  const [creating, setCreating] = useState(false)
  const [adjusting, setAdjusting] = useState<Stock | null>(null)
  const term = useDebounced(search.trim())
  const houses = useAsync(() => warehouses.all(), [])

  const saved = (message: string) => {
    setCreating(false)
    setAdjusting(null)
    toast.success(message)
    setReload((n) => n + 1)
  }

  return (
    <div className="page page-wide">
      <PageHead
        title="Tồn kho"
        lede="Số lượng theo từng SKU và kho. Hàng giữ là phần đã đặt cho đơn chưa xuất; khả dụng là phần còn bán được."
        actions={
          canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              <PlusIcon size={16} weight="bold" /> Thêm SKU vào kho
            </button>
          )
        }
      />
      <InventoryTabs />

      <ListPanel<Stock>
        filterKey={`${term}|${warehouseId}|${lowStock}`}
        reloadToken={reload}
        load={(paging) => inventory.list({ ...paging, search: term, warehouseId: warehouseId || undefined, lowStock: lowStock || undefined })}
        rowKey={(row) => row.id}
        itemLabel="dòng tồn kho"
        emptyIcon={<PackageIcon size={36} aria-hidden />}
        toolbar={
          <>
            <SearchBox value={search} onChange={setSearch} placeholder="Tìm theo SKU hoặc tên sản phẩm" />
            <FilterSelect
              label="Kho"
              value={warehouseId}
              options={(houses.data ?? []).map((w) => [w.id, w.name])}
              onChange={setWarehouseId}
            />
            <label className="toggle-chip">
              <input type="checkbox" checked={lowStock} onChange={(e) => setLowStock(e.target.checked)} />
              <span>Sắp hết hàng (≤ {LOW_STOCK})</span>
            </label>
          </>
        }
        columns={[
          {
            header: 'Sản phẩm',
            render: (row) => (
              <div className="person-text">
                <span className="person-name">{row.productName}</span>
                <span className="person-sub mono">{row.sku}</span>
              </div>
            ),
          },
          { header: 'Kho', render: (row) => row.warehouseName },
          { header: 'Tồn thực tế', render: (row) => countOf(row.quantityOnHand), className: 'num' },
          { header: 'Đang giữ', render: (row) => countOf(row.quantityReserved), className: 'num muted' },
          {
            header: 'Khả dụng',
            render: (row) =>
              row.quantityAvailable <= LOW_STOCK ? <Pill tone={row.quantityAvailable === 0 ? 'blocked' : 'warn'}>{countOf(row.quantityAvailable)}</Pill> : countOf(row.quantityAvailable),
            className: 'num',
          },
          { header: 'Cập nhật', render: (row) => whenOf(row.updatedAt), className: 'muted nowrap' },
        ]}
        actions={
          canWrite
            ? (row) => (
                <button type="button" className="btn btn-plain btn-sm" onClick={() => setAdjusting(row)}>
                  <ArrowsDownUpIcon size={14} /> Kiểm kê
                </button>
              )
            : undefined
        }
      />

      <CreateStockDialog open={creating} warehouses={houses.data ?? []} onClose={() => setCreating(false)} onSaved={saved} />
      <AdjustDialog stock={adjusting} onClose={() => setAdjusting(null)} onSaved={saved} />
    </div>
  )
}

function CreateStockDialog({ open, warehouses: houses, onClose, onSaved }: { open: boolean; warehouses: Warehouse[]; onClose: () => void; onSaved: (m: string) => void }) {
  const id = useId()
  const active = houses.filter((w) => w.status === 'ACTIVE')
  const { form, set, errors, error, busy, submit } = useDialogForm<{ warehouseId: string; variant: PickOption | null }>(open, () => ({
    warehouseId: active[0]?.id ?? '',
    variant: null,
  }))
  return (
    <FormDialog
      open={open}
      title="Thêm SKU vào kho"
      description="Tạo dòng tồn kho với số lượng 0. Sau đó dùng Nhập kho để ghi nhận hàng về."
      busy={busy}
      submitLabel="Thêm vào kho"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => ({ ...(f.warehouseId ? {} : { warehouseId: 'Chọn kho.' }), ...(f.variant ? {} : { productVariantId: 'Chọn biến thể.' }) }),
        async (f) => {
          await inventory.create({ warehouseId: f.warehouseId, productVariantId: f.variant!.id })
          onSaved(`Đã thêm ${f.variant!.title} vào kho.`)
        },
      )}
    >
      <Field label="Kho" htmlFor={`${id}-warehouseId`} error={errors.warehouseId} wide>
        <select {...bind(id, 'warehouseId', errors)} value={form.warehouseId} onChange={(e) => set('warehouseId', e.target.value)}>
          <option value="">Chọn kho</option>
          {active.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
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
    </FormDialog>
  )
}

function AdjustDialog({ stock, onClose, onSaved }: { stock: Stock | null; onClose: () => void; onSaved: (m: string) => void }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(stock !== null, () => ({ quantity: String(stock?.quantityOnHand ?? ''), reason: '' }), [stock?.id])
  const next = Number(form.quantity)
  const delta = stock && Number.isInteger(next) ? next - stock.quantityOnHand : 0
  return (
    <FormDialog
      open={stock !== null}
      title="Kiểm kê tồn kho"
      description={stock && `${stock.productName} (${stock.sku}) tại ${stock.warehouseName}`}
      busy={busy}
      submitLabel="Ghi nhận"
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => {
          const n = Number(f.quantity)
          const e: Record<string, string> = {}
          if (!/^\d+$/.test(f.quantity.trim())) e.quantityOnHand = 'Nhập số nguyên từ 0.'
          else if (stock && n < stock.quantityReserved) e.quantityOnHand = `Không thấp hơn số đang giữ (${stock.quantityReserved}).`
          else if (stock && n === stock.quantityOnHand) e.quantityOnHand = 'Số lượng chưa thay đổi.'
          if (!f.reason.trim()) e.reason = 'Nhập lý do điều chỉnh.'
          return e
        },
        async (f) => {
          await inventory.adjust(stock!.id, { quantityOnHand: Number(f.quantity), reason: f.reason.trim() })
          onSaved(`Đã cập nhật tồn kho ${stock!.sku}.`)
        },
      )}
    >
      {stock && (
        <div className="mini-stats span-2">
          <div>
            <span>Tồn hiện tại</span>
            <strong>{countOf(stock.quantityOnHand)}</strong>
          </div>
          <div>
            <span>Đang giữ</span>
            <strong>{countOf(stock.quantityReserved)}</strong>
          </div>
          <div>
            <span>Chênh lệch</span>
            <strong className={delta > 0 ? 'up' : delta < 0 ? 'down' : undefined}>{delta > 0 ? `+${delta}` : delta}</strong>
          </div>
        </div>
      )}
      <Field label="Số lượng đếm thực tế" htmlFor={`${id}-quantityOnHand`} error={errors.quantityOnHand}>
        <input {...bind(id, 'quantityOnHand', errors)} inputMode="numeric" value={form.quantity} onChange={(e) => set('quantity', e.target.value)} />
      </Field>
      <Field label="Lý do" htmlFor={`${id}-reason`} error={errors.reason} wide>
        <textarea {...bind(id, 'reason', errors)} rows={3} maxLength={500} value={form.reason} onChange={(e) => set('reason', e.target.value)} placeholder="Ví dụ: kiểm kê cuối tháng, hàng lỗi" />
      </Field>
    </FormDialog>
  )
}

function WarehousesView() {
  const canWrite = useCan('INVENTORY_WRITE')
  return (
    <ResourcePage<Warehouse>
      title="Danh sách kho"
      lede="Kho đang giữ hàng cho đơn không thể ngừng dùng; kho đã có dữ liệu tồn kho không thể xóa."
      addLabel="Thêm kho"
      itemLabel="kho"
      searchPlaceholder="Tìm theo tên hoặc địa chỉ"
      canWrite={canWrite}
      statusLabels={WAREHOUSE_STATUS_LABEL}
      belowHead={<InventoryTabs />}
      load={(query) => warehouses.list(query)}
      rowLabel={(w) => w.name}
      columns={[
        {
          header: 'Kho',
          render: (w) => (
            <div className="person">
              <span className="thumb" aria-hidden>
                <WarehouseIcon size={16} />
              </span>
              <div className="person-text">
                <span className="person-name">{w.name}</span>
                <span className="person-sub">{w.address}</span>
              </div>
            </div>
          ),
        },
        { header: 'Trạng thái', render: (w) => <Pill tone={w.status === 'ACTIVE' ? 'active' : 'inactive'}>{WAREHOUSE_STATUS_LABEL[w.status]}</Pill> },
        { header: 'Cập nhật', render: (w) => whenOf(w.updatedAt), className: 'muted nowrap' },
      ]}
      deleteTitle="Xóa kho"
      deleteMessage={(w) => (
        <>
          Xóa kho <strong>{w.name}</strong>? Chỉ xóa được kho chưa từng có dữ liệu tồn kho.
        </>
      )}
      remove={(w) => warehouses.remove(w.id)}
      emptyIcon={<WarehouseIcon size={36} aria-hidden />}
      renderForm={(slot) => <WarehouseForm {...slot} />}
    />
  )
}

function WarehouseForm({ open, item, onClose, onSaved }: FormSlot<Warehouse>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(open, () => ({
    name: item?.name ?? '',
    address: item?.address ?? '',
    status: (item?.status ?? 'ACTIVE') as WarehouseStatus,
  }), [item?.id])
  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa kho' : 'Thêm kho'}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm kho'}
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => ({ ...(f.name.trim() ? {} : { name: 'Nhập tên kho.' }), ...(f.address.trim() ? {} : { address: 'Nhập địa chỉ kho.' }) }),
        async (f) => {
          const body = { name: f.name.trim(), address: f.address.trim(), status: f.status }
          if (item) await warehouses.update(item.id, body)
          else await warehouses.create(body)
          onSaved(item ? `Đã cập nhật kho ${body.name}.` : `Đã thêm kho ${body.name}.`)
        },
      )}
    >
      <Field label="Tên kho" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} maxLength={150} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Trạng thái" htmlFor={`${id}-status`}>
        <select id={`${id}-status`} value={form.status} onChange={(e) => set('status', e.target.value as WarehouseStatus)}>
          {(Object.keys(WAREHOUSE_STATUS_LABEL) as WarehouseStatus[]).map((s) => (
            <option key={s} value={s}>
              {WAREHOUSE_STATUS_LABEL[s]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Địa chỉ" htmlFor={`${id}-address`} error={errors.address} wide>
        <input {...bind(id, 'address', errors)} value={form.address} maxLength={500} onChange={(e) => set('address', e.target.value)} />
      </Field>
    </FormDialog>
  )
}
