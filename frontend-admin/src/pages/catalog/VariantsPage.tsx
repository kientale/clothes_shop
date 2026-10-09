import { CaretDownIcon, PaletteIcon, RulerIcon, SquaresFourIcon, StackIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CATALOG_STATUS_LABEL, catalog, type CatalogOptions, type CatalogStatus, type Variant } from '../../api/catalog'
import { useAuth } from '../../auth/AuthContext'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'
import { useAsync } from '../../hooks'
import { ProductCombobox, type ProductPick } from './ProductCombobox'
import { StatusSelect, Swatch, money } from './shared'

const SKU = /^[A-Za-z0-9_-]{0,100}$/
const PRICE = /^\d+(\.\d{1,2})?$/

export default function VariantsPage() {
  const { user } = useAuth()
  const toast = useToast()
  const canWrite = user?.permissions.includes('PRODUCT_WRITE') ?? false
  const options = useAsync(() => catalog.options(), [])
  const [params, setParams] = useSearchParams()
  const productId = params.get('productId') ?? ''
  const [colorId, setColorId] = useState('')
  const [sizeId, setSizeId] = useState('')
  const [bulkOpen, setBulkOpen] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)
  // The product filter lives in the URL (?productId=), so links from the product list land filtered.
  const product = useAsync(() => (productId ? catalog.products.get(productId) : Promise.resolve(null)), [productId])
  const productPick: ProductPick | null = product.data
    ? { id: product.data.id, name: product.data.name, productCode: product.data.productCode }
    : null

  const setProduct = (pick: ProductPick | null) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      next.delete('page')
      if (pick) next.set('productId', pick.id)
      else next.delete('productId')
      return next
    })

  return (
    <>
      <ResourcePage<Variant>
        title="Biến thể sản phẩm"
        lede="Mỗi biến thể là một tổ hợp màu và size của sản phẩm, có SKU và giá bán riêng."
        addLabel="Thêm biến thể"
        itemLabel="biến thể"
        searchPlaceholder="Tìm theo SKU, tên hoặc mã sản phẩm"
        canWrite={canWrite}
        statusLabels={CATALOG_STATUS_LABEL}
        reloadToken={reloadToken}
        headerActions={
          <button type="button" className="btn btn-secondary" onClick={() => setBulkOpen(true)}>
            <SquaresFourIcon size={16} /> Tạo hàng loạt
          </button>
        }
        filterKey={`${productId}|${colorId}|${sizeId}`}
        filters={
          <div className="toolbar-selects product-filters variant-filters">
            <div className="toolbar-picker">
              <ProductCombobox value={productPick} onChange={setProduct} placeholder="Mọi sản phẩm" clearable />
            </div>
            <label className={`filter-select product-filter${colorId ? ' is-active' : ''}`}>
              <PaletteIcon className="product-filter-icon" size={16} aria-hidden />
              <select value={colorId} onChange={(e) => setColorId(e.target.value)} aria-label="Lọc theo màu">
                <option value="">Mọi màu</option>
                {options.data?.colors.map((color) => (
                  <option key={color.id} value={color.id}>
                    {color.name}
                  </option>
                ))}
              </select>
              <CaretDownIcon className="product-filter-caret" size={14} aria-hidden />
            </label>
            <label className={`filter-select product-filter${sizeId ? ' is-active' : ''}`}>
              <RulerIcon className="product-filter-icon" size={16} aria-hidden />
              <select value={sizeId} onChange={(e) => setSizeId(e.target.value)} aria-label="Lọc theo size">
                <option value="">Mọi size</option>
                {options.data?.sizes.map((size) => (
                  <option key={size.id} value={size.id}>
                    {size.name}
                  </option>
                ))}
              </select>
              <CaretDownIcon className="product-filter-caret" size={14} aria-hidden />
            </label>
          </div>
        }
        load={(query) =>
          catalog.variants.list({ ...query, productId: productId || undefined, colorId: colorId || undefined, sizeId: sizeId || undefined })
        }
        rowLabel={(variant) => `biến thể ${variant.sku}`}
        columns={[
          {
            header: 'Sản phẩm',
            render: (variant) => (
              <div className="role-cell">
                <span className="role-name">{variant.product.name}</span>
                <code className="role-code">{variant.product.productCode}</code>
              </div>
            ),
          },
          {
            header: 'Màu',
            render: (variant) => (
              <span className="inline-cell">
                <Swatch hex={variant.color.hexCode} />
                {variant.color.name}
              </span>
            ),
          },
          { header: 'Size', render: (variant) => <span className="size-chip">{variant.size.name}</span> },
          { header: 'SKU', render: (variant) => <code className="role-code">{variant.sku}</code> },
          {
            header: 'Giá bán',
            render: (variant) => (
              <span className="price-cell">
                <span>{money(variant.price)}</span>
                {variant.compareAtPrice !== null && variant.compareAtPrice > variant.price && <s className="muted small">{money(variant.compareAtPrice)}</s>}
              </span>
            ),
            className: 'nowrap',
          },
          { header: 'Trạng thái', render: (variant) => <StatusPill value={variant.status} label={CATALOG_STATUS_LABEL[variant.status]} /> },
        ]}
        deleteTitle="Xóa biến thể"
        deleteMessage={(variant) => (
          <>
            Xóa biến thể <strong>{variant.sku}</strong> ({variant.color.name} / {variant.size.name}) của {variant.product.name}? Tạo lại đúng màu và size này
            sau sẽ khôi phục biến thể.
          </>
        )}
        remove={(variant) => catalog.variants.remove(variant.id)}
        emptyIcon={<StackIcon size={36} aria-hidden />}
        renderForm={(slot) => <VariantForm {...slot} options={options.data} defaultProduct={productPick} />}
      />
      {canWrite && (
        <BulkDialog
          open={bulkOpen}
          options={options.data}
          defaultProduct={productPick}
          onClose={() => setBulkOpen(false)}
          onDone={(count) => {
            setBulkOpen(false)
            setReloadToken((n) => n + 1)
            toast.success(count ? `Đã tạo ${count} biến thể mới.` : 'Các tổ hợp đã chọn đều đã có biến thể, không tạo thêm.')
          }}
        />
      )}
    </>
  )
}

interface VariantValues {
  product: ProductPick | null
  colorId: string
  sizeId: string
  sku: string
  price: string
  compareAtPrice: string
  status: CatalogStatus
}

function checkPrices(price: string, compareAtPrice: string) {
  const next: Record<string, string> = {}
  if (!PRICE.test(price.trim())) next.price = 'Nhập giá là số từ 0.'
  if (compareAtPrice.trim()) {
    if (!PRICE.test(compareAtPrice.trim())) next.compareAtPrice = 'Nhập giá là số từ 0.'
    else if (!next.price && Number(compareAtPrice) < Number(price)) next.compareAtPrice = 'Giá so sánh không được thấp hơn giá bán.'
  }
  return next
}

function VariantForm({
  open,
  item,
  onClose,
  onSaved,
  options,
  defaultProduct,
}: FormSlot<Variant> & { options?: CatalogOptions; defaultProduct: ProductPick | null }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm<VariantValues>(
    open,
    () => ({
      product: item ? { id: item.product.id, name: item.product.name, productCode: item.product.productCode } : defaultProduct,
      colorId: item?.color.id ?? '',
      sizeId: item?.size.id ?? '',
      sku: item?.sku ?? '',
      price: item ? String(item.price) : '',
      compareAtPrice: item?.compareAtPrice === null || item?.compareAtPrice === undefined ? '' : String(item.compareAtPrice),
      status: item?.status ?? 'ACTIVE',
    }),
    [item],
  )
  const color = options?.colors.find((c) => c.id === form.colorId)
  const size = options?.sizes.find((s) => s.id === form.sizeId)
  const autoSku = form.product && color && size ? `${form.product.productCode}-${color.code}-${size.code}` : 'MÃSP-MÃMÀU-MÃSIZE'

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa biến thể' : 'Thêm biến thể'}
      description={item ? `${item.product.name}: ${item.color.name} / ${item.size.name}` : undefined}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm biến thể'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => ({
          ...(item || f.product ? {} : { productId: 'Chọn sản phẩm.' }),
          ...(item || f.colorId ? {} : { colorId: 'Chọn màu.' }),
          ...(item || f.sizeId ? {} : { sizeId: 'Chọn size.' }),
          ...(SKU.test(f.sku.trim()) && (!item || f.sku.trim()) ? {} : { sku: 'SKU gồm chữ, số, gạch ngang hoặc gạch dưới, tối đa 100 ký tự.' }),
          ...checkPrices(f.price, f.compareAtPrice),
        }),
        async (f) => {
          const prices = {
            price: Number(f.price),
            compareAtPrice: f.compareAtPrice.trim() ? Number(f.compareAtPrice) : null,
            status: f.status,
          }
          const saved = item
            ? await catalog.variants.update(item.id, { sku: f.sku.trim(), ...prices })
            : await catalog.variants.create({ productId: f.product!.id, colorId: f.colorId, sizeId: f.sizeId, sku: f.sku.trim() || null, ...prices })
          onSaved(item ? `Đã cập nhật biến thể ${saved.sku}.` : `Đã thêm biến thể ${saved.sku}.`)
        },
      )}
    >
      {item ? (
        <div className="field span-2">
          <span className="field-label">Biến thể</span>
          <div className="field-box variant-summary">
            <span className="truncate">{item.product.name}</span>
            <span className="inline-cell">
              <Swatch hex={item.color.hexCode} /> {item.color.name}
            </span>
            <span className="size-chip">{item.size.name}</span>
          </div>
          <span className="field-hint">Sản phẩm, màu và size không đổi được. Cần tổ hợp khác thì tạo biến thể mới.</span>
        </div>
      ) : (
        <>
          <Field label="Sản phẩm" htmlFor={`${id}-product`} error={errors.productId} wide>
            <ProductCombobox inputId={`${id}-product`} value={form.product} onChange={(pick) => set('product', pick)} invalid={Boolean(errors.productId)} />
          </Field>
          <Field label="Màu" htmlFor={`${id}-colorId`} error={errors.colorId}>
            <select {...bind(id, 'colorId', errors)} value={form.colorId} onChange={(e) => set('colorId', e.target.value)}>
              <option value="">Chọn màu</option>
              {options?.colors.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.code})
                </option>
              ))}
            </select>
          </Field>
          <Field label="Size" htmlFor={`${id}-sizeId`} error={errors.sizeId}>
            <select {...bind(id, 'sizeId', errors)} value={form.sizeId} onChange={(e) => set('sizeId', e.target.value)}>
              <option value="">Chọn size</option>
              {options?.sizes.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
        </>
      )}
      <Field
        label="SKU"
        htmlFor={`${id}-sku`}
        optional={!item}
        error={errors.sku}
        hint={item ? 'Mã quản lý kho, lưu thành chữ hoa.' : `Để trống sẽ dùng: ${autoSku.toUpperCase()}`}
        wide
      >
        <input {...bind(id, 'sku', errors)} className="mono" value={form.sku} onChange={(e) => set('sku', e.target.value.toUpperCase())} maxLength={100} />
      </Field>
      <Field label="Giá bán (VND)" htmlFor={`${id}-price`} error={errors.price}>
        <input {...bind(id, 'price', errors)} type="number" min={0} step={1000} inputMode="numeric" value={form.price} onChange={(e) => set('price', e.target.value)} placeholder="199000" />
      </Field>
      <Field label="Giá so sánh (VND)" htmlFor={`${id}-compareAtPrice`} optional error={errors.compareAtPrice} hint="Giá gốc gạch ngang khi đang giảm giá.">
        <input
          {...bind(id, 'compareAtPrice', errors)}
          type="number"
          min={0}
          step={1000}
          inputMode="numeric"
          value={form.compareAtPrice}
          onChange={(e) => set('compareAtPrice', e.target.value)}
          placeholder="249000"
        />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} hint="Biến thể đang ẩn không bán được." />
    </FormDialog>
  )
}

interface BulkValues {
  product: ProductPick | null
  colorIds: string[]
  sizeIds: string[]
  price: string
  compareAtPrice: string
  status: CatalogStatus
}

/** Creates every chosen color x size combination of one product that does not exist yet. */
function BulkDialog({
  open,
  options,
  defaultProduct,
  onClose,
  onDone,
}: {
  open: boolean
  options?: CatalogOptions
  defaultProduct: ProductPick | null
  onClose: () => void
  onDone: (created: number) => void
}) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm<BulkValues>(
    open,
    () => ({ product: defaultProduct, colorIds: [], sizeIds: [], price: '', compareAtPrice: '', status: 'ACTIVE' }),
    [],
  )
  // Show which combinations already exist for the chosen product.
  const existing = useAsync(
    () => (open && form.product ? catalog.variants.list({ productId: form.product.id, page: 0, size: 50 }) : Promise.resolve(null)),
    [open, form.product?.id],
  )
  const taken = new Set((existing.data?.content ?? []).map((v) => `${v.color.id}|${v.size.id}`))
  const combos = form.colorIds.flatMap((c) => form.sizeIds.map((s) => `${c}|${s}`))
  const fresh = combos.filter((combo) => !taken.has(combo)).length
  const toggle = (key: 'colorIds' | 'sizeIds', value: string) =>
    set(key, form[key].includes(value) ? form[key].filter((v) => v !== value) : [...form[key], value])

  return (
    <FormDialog
      open={open}
      title="Tạo biến thể hàng loạt"
      description="Chọn nhiều màu và size, hệ thống tạo mọi tổ hợp còn thiếu với cùng một giá. Tổ hợp đã có được giữ nguyên."
      busy={busy}
      disabled={combos.length > 0 && fresh === 0}
      submitLabel={fresh ? `Tạo ${fresh} biến thể` : 'Tạo biến thể'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => ({
          ...(f.product ? {} : { productId: 'Chọn sản phẩm.' }),
          ...(f.colorIds.length ? {} : { colorIds: 'Chọn ít nhất một màu.' }),
          ...(f.sizeIds.length ? {} : { sizeIds: 'Chọn ít nhất một size.' }),
          ...checkPrices(f.price, f.compareAtPrice),
        }),
        async (f) => {
          const created = await catalog.variants.bulk({
            productId: f.product!.id,
            colorIds: f.colorIds,
            sizeIds: f.sizeIds,
            price: Number(f.price),
            compareAtPrice: f.compareAtPrice.trim() ? Number(f.compareAtPrice) : null,
            status: f.status,
          })
          onDone(created.length)
        },
      )}
    >
      <Field label="Sản phẩm" htmlFor={`${id}-product`} error={errors.productId} wide>
        <ProductCombobox inputId={`${id}-product`} value={form.product} onChange={(pick) => set('product', pick)} invalid={Boolean(errors.productId)} />
      </Field>
      <fieldset className="field span-2 check-group">
        <legend className="field-label">Màu ({form.colorIds.length} đã chọn)</legend>
        <div className="pick-chips">
          {options?.colors.map((color) => (
            <label key={color.id} className="pick-chip">
              <input type="checkbox" checked={form.colorIds.includes(color.id)} onChange={() => toggle('colorIds', color.id)} />
              <Swatch hex={color.hexCode} size={14} />
              {color.name}
            </label>
          ))}
        </div>
        {errors.colorIds && <span className="field-error">{errors.colorIds}</span>}
      </fieldset>
      <fieldset className="field span-2 check-group">
        <legend className="field-label">Size ({form.sizeIds.length} đã chọn)</legend>
        <div className="pick-chips">
          {options?.sizes.map((size) => (
            <label key={size.id} className="pick-chip">
              <input type="checkbox" checked={form.sizeIds.includes(size.id)} onChange={() => toggle('sizeIds', size.id)} />
              {size.name}
            </label>
          ))}
        </div>
        {errors.sizeIds && <span className="field-error">{errors.sizeIds}</span>}
      </fieldset>
      {combos.length > 0 && (
        <p className="bulk-summary span-2">
          {combos.length} tổ hợp đã chọn: <strong>{fresh} sẽ được tạo</strong>
          {combos.length - fresh > 0 && `, ${combos.length - fresh} đã có sẵn và được giữ nguyên`}.
        </p>
      )}
      <Field label="Giá bán (VND)" htmlFor={`${id}-price`} error={errors.price}>
        <input {...bind(id, 'price', errors)} type="number" min={0} step={1000} inputMode="numeric" value={form.price} onChange={(e) => set('price', e.target.value)} placeholder="199000" />
      </Field>
      <Field label="Giá so sánh (VND)" htmlFor={`${id}-compareAtPrice`} optional error={errors.compareAtPrice}>
        <input {...bind(id, 'compareAtPrice', errors)} type="number" min={0} step={1000} inputMode="numeric" value={form.compareAtPrice} onChange={(e) => set('compareAtPrice', e.target.value)} />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} hint="Áp dụng cho mọi biến thể được tạo." />
    </FormDialog>
  )
}
