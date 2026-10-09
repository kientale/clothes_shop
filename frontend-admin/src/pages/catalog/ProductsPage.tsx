import { CaretDownIcon, EyeIcon, StackIcon, TShirtIcon, TagIcon } from '@phosphor-icons/react'
import { useId, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  GENDER_LABEL,
  PRODUCT_STATUS_LABEL,
  catalog,
  categoryPaths,
  slugify,
  type CatalogOptions,
  type Product,
  type ProductGender,
  type ProductStatus,
} from '../../api/catalog'
import { useAuth } from '../../auth/AuthContext'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { useAsync } from '../../hooks'
import { ProductImagesField, type ImageValue } from './ProductImagesField'
import { priceRange } from './shared'

const CODE = /^[A-Za-z0-9_-]{1,80}$/

export default function ProductsPage() {
  const { user } = useAuth()
  const options = useAsync(() => catalog.options(), [])
  const [brandId, setBrandId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const categories = useMemo(() => categoryPaths(options.data?.categories ?? []), [options.data])

  return (
    <ResourcePage<Product>
      title="Sản phẩm"
      lede="Thông tin chung của sản phẩm. Màu, size, SKU và giá bán từng loại quản lý ở trang Biến thể."
      addLabel="Thêm sản phẩm"
      itemLabel="sản phẩm"
      searchPlaceholder="Tìm theo tên, mã hoặc slug"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={PRODUCT_STATUS_LABEL}
      filterKey={`${brandId}|${categoryId}`}
      filters={
        <div className="toolbar-selects product-filters">
          <label className={`filter-select product-filter${brandId ? ' is-active' : ''}`}>
            <TagIcon className="product-filter-icon" size={16} aria-hidden />
            <select value={brandId} onChange={(e) => setBrandId(e.target.value)} aria-label="Lọc theo thương hiệu">
              <option value="">Mọi thương hiệu</option>
              {options.data?.brands.map((brand) => (
                <option key={brand.id} value={brand.id}>
                  {brand.name}
                </option>
              ))}
            </select>
            <CaretDownIcon className="product-filter-caret" size={14} aria-hidden />
          </label>
          <label className={`filter-select product-filter${categoryId ? ' is-active' : ''}`}>
            <StackIcon className="product-filter-icon" size={16} aria-hidden />
            <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} aria-label="Lọc theo danh mục">
              <option value="">Mọi danh mục</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.path}
                </option>
              ))}
            </select>
            <CaretDownIcon className="product-filter-caret" size={14} aria-hidden />
          </label>
        </div>
      }
      load={(query) => catalog.products.list({ ...query, brandId: brandId || undefined, categoryId: categoryId || undefined })}
      rowLabel={(product) => product.name}
      columns={[
        {
          header: 'Sản phẩm',
          render: (product) => (
            <div className="person">
              {product.images[0] ? (
                <img className="thumb thumb-lg" src={product.images[0].url} alt="" loading="lazy" />
              ) : (
                <span className="thumb thumb-lg" aria-hidden>
                  <TShirtIcon size={20} />
                </span>
              )}
              <div className="person-text">
                <Link className="person-name row-link" to={`/products/${product.id}`}>
                  {product.name}
                </Link>
                <span className="person-sub mono">{product.productCode}</span>
              </div>
            </div>
          ),
        },
        { header: 'Danh mục', render: (product) => product.category.name },
        { header: 'Thương hiệu', render: (product) => product.brand.name },
        { header: 'Giá', render: (product) => priceRange(product.basePrice, product.minPrice, product.maxPrice), className: 'nowrap' },
        {
          header: 'Biến thể',
          render: (product) => (
            <Link className="count-link" to={`/product-variants?productId=${product.id}`}>
              {product.variantCount}
            </Link>
          ),
          className: 'num',
        },
        { header: 'Trạng thái', render: (product) => <StatusPill value={product.status} label={PRODUCT_STATUS_LABEL[product.status]} /> },
      ]}
      rowActions={(product) => (
        <>
        <Link className="icon-btn" to={`/products/${product.id}`} aria-label={`Xem chi tiết ${product.name}`} title="Xem chi tiết">
          <EyeIcon size={16} />
        </Link>
        <Link className="icon-btn" to={`/product-variants?productId=${product.id}`} aria-label={`Biến thể của ${product.name}`} title="Biến thể">
          <StackIcon size={16} />
        </Link>
        </>
      )}
      deleteTitle="Xóa sản phẩm"
      deleteMessage={(product) => (
        <>
          Xóa sản phẩm <strong>{product.name}</strong> ({product.productCode})?
          {product.variantCount > 0 && ` ${product.variantCount} biến thể của sản phẩm cũng bị xóa.`}
        </>
      )}
      remove={(product) => catalog.products.remove(product.id)}
      emptyIcon={<TShirtIcon size={36} aria-hidden />}
      renderForm={(slot) => <ProductForm {...slot} options={options.data} />}
    />
  )
}

interface ProductValues {
  productCode: string
  name: string
  slug: string
  brandId: string
  categoryId: string
  gender: ProductGender
  basePrice: string
  material: string
  status: ProductStatus
  shortDescription: string
  description: string
  images: ImageValue[]
}

export function ProductForm({ open, item, onClose, onSaved, options }: FormSlot<Product> & { options?: CatalogOptions }) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const categories = useMemo(() => categoryPaths(options?.categories ?? []), [options])
  const { form, set, errors, error, busy, submit } = useDialogForm<ProductValues>(
    open,
    () => ({
      productCode: item?.productCode ?? '',
      name: item?.name ?? '',
      slug: item?.slug ?? '',
      brandId: item?.brand.id ?? '',
      categoryId: item?.category.id ?? '',
      gender: item?.gender ?? 'UNISEX',
      basePrice: item ? String(item.basePrice) : '',
      material: item?.material ?? '',
      status: item?.status ?? 'DRAFT',
      shortDescription: item?.shortDescription ?? '',
      description: item?.description ?? '',
      images: item?.images.map((image) => ({ url: image.url, altText: image.altText ?? '' })) ?? [],
    }),
    [item],
  )

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
      description={item ? `${item.name} (${item.productCode})` : 'Tạo sản phẩm trước, sau đó thêm biến thể màu và size ở trang Biến thể.'}
      busy={busy}
      disabled={uploading}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm sản phẩm'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const next: Record<string, string> = {}
          if (!CODE.test(f.productCode.trim())) next.productCode = 'Mã gồm 1-80 ký tự chữ, số, gạch ngang hoặc gạch dưới.'
          if (!f.name.trim()) next.name = 'Nhập tên sản phẩm.'
          if (!f.brandId) next.brandId = 'Chọn thương hiệu.'
          if (!f.categoryId) next.categoryId = 'Chọn danh mục.'
          if (!/^\d+(\.\d{1,2})?$/.test(f.basePrice.trim())) next.basePrice = 'Nhập giá là số từ 0.'
          return next
        },
        async (f) => {
          const body = {
            productCode: f.productCode.trim(),
            name: f.name.trim(),
            slug: f.slug.trim() || null,
            description: f.description.trim() || null,
            shortDescription: f.shortDescription.trim() || null,
            brandId: f.brandId,
            categoryId: f.categoryId,
            material: f.material.trim() || null,
            gender: f.gender,
            basePrice: Number(f.basePrice),
            status: f.status,
            images: f.images.map((image) => ({ url: image.url, altText: image.altText.trim() || null })),
          }
          const saved = item ? await catalog.products.update(item.id, body) : await catalog.products.create(body)
          onSaved(item ? `Đã cập nhật sản phẩm ${saved.name}.` : `Đã thêm sản phẩm ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên sản phẩm" htmlFor={`${id}-name`} error={errors.name} wide>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={255} placeholder="Áo thun cotton cổ tròn" />
      </Field>
      <Field label="Mã sản phẩm" htmlFor={`${id}-productCode`} error={errors.productCode} hint="Lưu thành chữ hoa, dùng làm tiền tố SKU.">
        <input {...bind(id, 'productCode', errors)} className="mono" value={form.productCode} onChange={(e) => set('productCode', e.target.value.toUpperCase())} maxLength={80} placeholder="TEE-001" />
      </Field>
      <Field label="Slug" htmlFor={`${id}-slug`} optional error={errors.slug} hint={`Để trống sẽ dùng: ${slugify(form.name) || '...'}`}>
        <input {...bind(id, 'slug', errors)} className="mono" value={form.slug} onChange={(e) => set('slug', slugify(e.target.value))} maxLength={300} />
      </Field>
      <Field label="Thương hiệu" htmlFor={`${id}-brandId`} error={errors.brandId}>
        <select {...bind(id, 'brandId', errors)} value={form.brandId} onChange={(e) => set('brandId', e.target.value)}>
          <option value="">Chọn thương hiệu</option>
          {options?.brands.map((brand) => (
            <option key={brand.id} value={brand.id}>
              {brand.name}
              {brand.status === 'INACTIVE' ? ' (đang ẩn)' : ''}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Danh mục" htmlFor={`${id}-categoryId`} error={errors.categoryId}>
        <select {...bind(id, 'categoryId', errors)} value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
          <option value="">Chọn danh mục</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.path}
              {category.status === 'INACTIVE' ? ' (đang ẩn)' : ''}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Giá cơ bản (VND)" htmlFor={`${id}-basePrice`} error={errors.basePrice} hint="Giá tham khảo. Giá bán thực tế đặt ở từng biến thể.">
        <input {...bind(id, 'basePrice', errors)} type="number" min={0} step={1000} inputMode="numeric" value={form.basePrice} onChange={(e) => set('basePrice', e.target.value)} placeholder="199000" />
      </Field>
      <Field label="Giới tính" htmlFor={`${id}-gender`}>
        <select id={`${id}-gender`} value={form.gender} onChange={(e) => set('gender', e.target.value as ProductGender)}>
          {(Object.keys(GENDER_LABEL) as ProductGender[]).map((gender) => (
            <option key={gender} value={gender}>
              {GENDER_LABEL[gender]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Chất liệu" htmlFor={`${id}-material`} optional error={errors.material}>
        <input {...bind(id, 'material', errors)} value={form.material} onChange={(e) => set('material', e.target.value)} maxLength={255} placeholder="100% cotton" />
      </Field>
      <Field label="Trạng thái" htmlFor={`${id}-status`} hint="Chỉ sản phẩm Đang bán hiện ở cửa hàng.">
        <select id={`${id}-status`} value={form.status} onChange={(e) => set('status', e.target.value as ProductStatus)}>
          {(Object.keys(PRODUCT_STATUS_LABEL) as ProductStatus[]).map((status) => (
            <option key={status} value={status}>
              {PRODUCT_STATUS_LABEL[status]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Ảnh sản phẩm" htmlFor={`${id}-images-files`} optional wide>
        <ProductImagesField id={`${id}-images`} images={form.images} onChange={(images) => set('images', images)} onUploadingChange={setUploading} error={errors.images} />
      </Field>
      <Field label="Mô tả ngắn" htmlFor={`${id}-shortDescription`} optional wide error={errors.shortDescription}>
        <textarea {...bind(id, 'shortDescription', errors)} rows={2} value={form.shortDescription} onChange={(e) => set('shortDescription', e.target.value)} maxLength={1000} />
      </Field>
      <Field label="Mô tả chi tiết" htmlFor={`${id}-description`} optional wide error={errors.description}>
        <textarea {...bind(id, 'description', errors)} rows={5} value={form.description} onChange={(e) => set('description', e.target.value)} maxLength={20000} />
      </Field>
    </FormDialog>
  )
}
