import { ArrowLeftIcon, ImageIcon, PencilSimpleIcon, StackIcon, StarIcon } from '@phosphor-icons/react'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { accessErrorMessage } from '../../api/access'
import { CATALOG_STATUS_LABEL, GENDER_LABEL, PRODUCT_STATUS_LABEL, catalog, type Variant } from '../../api/catalog'
import { inventory, type Stock } from '../../api/commerce'
import { REVIEW_STATUS, reviews } from '../../api/marketing'
import type { Page } from '../../api/types'
import { StatusPill } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'
import { Details, Kpi, Kpis, StatePill, countOf, useCan, whenOf } from '../../components/kit'
import { useAsync } from '../../hooks'
import { ProductForm } from './ProductsPage'
import { SizeChartPanel } from './SizeChartPanel'
import { Swatch, money, priceRange } from './shared'

/** Reads every page of a list endpoint (max 50 per page) so a product's variants or stock fit on one screen. */
async function allPages<T>(load: (page: number) => Promise<Page<T>>) {
  const first = await load(0)
  const rest = await Promise.all(Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) => load(i + 1)))
  return [first, ...rest].flatMap((page) => page.content)
}

interface StockTotals {
  onHand: number
  reserved: number
  available: number
  warehouses: Stock[]
}

/**
 * One product in full: photos, attributes, descriptions, a colour x size grid of its variants with
 * price and available stock, the variant list with SKUs, and its latest reviews.
 */
export default function ProductDetailPage() {
  const { id = '' } = useParams()
  const toast = useToast()
  const canWrite = useCan('PRODUCT_WRITE')
  const [editing, setEditing] = useState(false)
  const [photo, setPhoto] = useState(0)

  const product = useAsync(() => catalog.products.get(id), [id])
  const options = useAsync(() => catalog.options(), [])
  const variants = useAsync(() => allPages((page) => catalog.variants.list({ productId: id, page, size: 50 })), [id])
  // Stock has no product filter: search by product code, then keep only this product's variants.
  const stock = useAsync(async () => {
    const code = product.data?.productCode
    if (!code) return null
    try {
      return await allPages((page) => inventory.list({ search: code, page, size: 50 }))
    } catch {
      return null
    }
  }, [product.data?.productCode])
  const productReviews = useAsync(() => reviews.list({ productId: id, page: 0, size: 5 }).catch(() => null), [id])

  const stockByVariant = useMemo(() => {
    const map = new Map<string, StockTotals>()
    const ids = new Set((variants.data ?? []).map((v) => v.id))
    for (const row of stock.data ?? []) {
      if (!ids.has(row.productVariantId)) continue
      const totals = map.get(row.productVariantId) ?? { onHand: 0, reserved: 0, available: 0, warehouses: [] }
      totals.onHand += row.quantityOnHand
      totals.reserved += row.quantityReserved
      totals.available += row.quantityAvailable
      totals.warehouses.push(row)
      map.set(row.productVariantId, totals)
    }
    return map
  }, [stock.data, variants.data])

  if (product.error) {
    return (
      <div className="page page-wide">
        <BackLink />
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(product.error)}{' '}
          <button type="button" className="link-btn" onClick={product.reload}>
            Thử lại
          </button>
        </div>
      </div>
    )
  }

  const p = product.data
  if (!p) {
    return (
      <div className="page page-wide" aria-busy="true">
        <BackLink />
        <span className="skel" style={{ width: '24ch', height: 32 }} />
        <div className="detail-grid">
          <section className="panel">
            <span className="skel skel-row" />
            <span className="skel skel-row" />
            <span className="skel skel-row" />
          </section>
          <section className="panel">
            <span className="skel" style={{ width: '100%', aspectRatio: '4 / 5' }} />
          </section>
        </div>
      </div>
    )
  }

  const list = variants.data ?? []
  const images = p.images.length ? [...p.images].sort((a, b) => Number(b.primary) - Number(a.primary)) : []
  const shown = images[Math.min(photo, images.length - 1)]
  const totals = [...stockByVariant.values()]
  const available = totals.reduce((sum, t) => sum + t.available, 0)
  const onHand = totals.reduce((sum, t) => sum + t.onHand, 0)
  const soldOut = list.filter((v) => v.status === 'ACTIVE' && (stockByVariant.get(v.id)?.available ?? 0) <= 0).length
  const approved = productReviews.data?.content.filter((r) => r.status === 'APPROVED') ?? []
  const rating = approved.length ? approved.reduce((sum, r) => sum + r.rating, 0) / approved.length : null

  return (
    <div className="page page-wide">
      <BackLink />
      <header className="page-head">
        <div>
          <div className="title-row">
            <h1 className="page-title">{p.name}</h1>
            <StatusPill value={p.status} label={PRODUCT_STATUS_LABEL[p.status]} />
          </div>
          <p className="page-lede">
            <span className="mono">{p.productCode}</span> | tạo {whenOf(p.createdAt)} | cập nhật {whenOf(p.updatedAt)}
          </p>
        </div>
        <div className="page-head-actions">
          <Link className="btn btn-secondary" to={`/product-variants?productId=${p.id}`}>
            <StackIcon size={16} /> Quản lý biến thể
          </Link>
          {canWrite && (
            <button type="button" className="btn btn-primary" onClick={() => setEditing(true)}>
              <PencilSimpleIcon size={16} /> Sửa sản phẩm
            </button>
          )}
        </div>
      </header>

      <Kpis>
        <Kpi label="Giá bán" value={priceRange(p.basePrice, p.minPrice, p.maxPrice)} note={`Giá gốc ${money(p.basePrice)}`} />
        <Kpi
          label="Biến thể"
          loading={variants.loading && !variants.data}
          value={countOf(list.length)}
          note={list.length ? `${new Set(list.map((v) => v.color.id)).size} màu, ${new Set(list.map((v) => v.size.id)).size} size` : 'Chưa có biến thể'}
        />
        <Kpi
          label="Tồn khả dụng"
          loading={stock.loading && !stock.data}
          value={stock.data ? countOf(available) : '-'}
          note={stock.data ? `${countOf(onHand)} trong kho${soldOut ? `, ${soldOut} biến thể hết hàng` : ''}` : 'Không đọc được tồn kho'}
        />
        <Kpi
          label="Đánh giá"
          loading={productReviews.loading && !productReviews.data}
          value={rating === null ? '-' : `${rating.toFixed(1)} / 5`}
          note={productReviews.data ? `${countOf(productReviews.data.totalElements)} đánh giá` : 'Không đọc được đánh giá'}
        />
      </Kpis>

      <div className="detail-grid">
        <div className="detail-main">
          <section className="panel">
            <div className="section-head">
              <h2 className="section-title">Biến thể theo màu và size</h2>
            </div>
            {variants.error ? (
              <div className="banner banner-error" role="alert">
                {accessErrorMessage(variants.error)}
              </div>
            ) : !variants.data ? (
              <span className="skel skel-row" />
            ) : list.length === 0 ? (
              <div className="empty-state">
                <StackIcon size={32} aria-hidden />
                <p>Sản phẩm chưa có biến thể nào.</p>
                <Link className="btn btn-secondary btn-sm" to={`/product-variants?productId=${p.id}`}>
                  Tạo biến thể
                </Link>
              </div>
            ) : (
              <VariantMatrix variants={list} stock={stockByVariant} hasStock={stock.data !== null} sizeOrder={options.data?.sizes.map((s) => s.id) ?? []} />
            )}
          </section>

          {list.length > 0 && (
            <section className="panel">
              <h2 className="section-title">Danh sách biến thể ({list.length})</h2>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">SKU</th>
                      <th scope="col" className="hide-sm">Màu</th>
                      <th scope="col" className="hide-sm">Size</th>
                      <th scope="col">Giá bán</th>
                      <th scope="col" className="num">
                        Khả dụng
                      </th>
                      <th scope="col" className="hide-sm">Theo kho</th>
                      <th scope="col" className="hide-sm">Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortVariants(list, options.data?.sizes.map((s) => s.id) ?? []).map((v) => {
                      const t = stockByVariant.get(v.id)
                      return (
                        <tr key={v.id}>
                          <td className="nowrap">
                            <code className="role-code">{v.sku}</code>
                          </td>
                          <td className="hide-sm">
                            <span className="inline-cell">
                              <Swatch hex={v.color.hexCode} size={14} />
                              {v.color.name}
                            </span>
                          </td>
                          <td className="hide-sm">
                            <span className="size-chip">{v.size.name}</span>
                          </td>
                          <td className="nowrap">
                            <span className="price-cell">
                              <span>{money(v.price)}</span>
                              {v.compareAtPrice !== null && v.compareAtPrice > v.price && <s className="muted small">{money(v.compareAtPrice)}</s>}
                            </span>
                          </td>
                          <td className="num">
                            {stock.data === null ? '-' : <span className={t && t.available > 0 ? 'money' : 'stock-out'}>{countOf(t?.available ?? 0)}</span>}
                          </td>
                          <td className="small muted hide-sm">
                            {t?.warehouses.length
                              ? t.warehouses.map((w) => (
                                  <span key={w.id} className="stock-line">
                                    {w.warehouseName}: {countOf(w.quantityAvailable)}
                                    {w.quantityReserved > 0 && ` (giữ ${countOf(w.quantityReserved)})`}
                                  </span>
                                ))
                              : stock.data === null
                                ? '-'
                                : 'Chưa nhập kho'}
                          </td>
                          <td className="hide-sm">
                            <StatusPill value={v.status} label={CATALOG_STATUS_LABEL[v.status]} />
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          <SizeChartPanel
            productId={p.id}
            sizeNames={sortVariants(list, options.data?.sizes.map((s) => s.id) ?? [])
              .map((v) => v.size.name)
              .filter((name, index, all) => all.indexOf(name) === index)}
          />

          {(p.shortDescription || p.description) && (
            <section className="panel">
              <h2 className="section-title">Mô tả</h2>
              {p.shortDescription && <p className="product-lead">{p.shortDescription}</p>}
              {p.description && <p className="product-description">{p.description}</p>}
            </section>
          )}

          <section className="panel">
            <div className="section-head">
              <h2 className="section-title">Đánh giá mới nhất</h2>
              {productReviews.data && productReviews.data.totalElements > 0 && (
                <Link className="text-link small" to="/reviews">
                  Quản lý đánh giá
                </Link>
              )}
            </div>
            {!productReviews.data ? (
              productReviews.loading ? (
                <span className="skel skel-row" />
              ) : (
                <p className="muted">Không đọc được đánh giá của sản phẩm này.</p>
              )
            ) : productReviews.data.content.length === 0 ? (
              <p className="muted">Chưa có đánh giá nào.</p>
            ) : (
              <ul className="review-list">
                {productReviews.data.content.map((r) => (
                  <li key={r.id}>
                    <div className="review-head">
                      <span className="stars" aria-label={`${r.rating} trên 5 sao`}>
                        {Array.from({ length: 5 }, (_, i) => (
                          <StarIcon key={i} size={14} weight={i < r.rating ? 'fill' : 'regular'} />
                        ))}
                      </span>
                      <StatePill map={REVIEW_STATUS} value={r.status} />
                      <span className="muted small">{whenOf(r.createdAt)}</span>
                    </div>
                    {r.comment ? <p>{r.comment}</p> : <p className="muted">Không có nội dung.</p>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="detail-side">
          <section className="panel">
            {shown ? (
              <>
                <img className="product-photo" src={shown.url} alt={shown.altText ?? p.name} />
                {images.length > 1 && (
                  <div className="product-thumbs" role="group" aria-label="Ảnh sản phẩm">
                    {images.map((image, index) => (
                      <button
                        key={image.id}
                        type="button"
                        aria-pressed={image === shown}
                        aria-label={`Ảnh ${index + 1}`}
                        onClick={() => setPhoto(index)}
                      >
                        <img src={image.url} alt="" loading="lazy" />
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <div className="product-photo product-photo-empty">
                <ImageIcon size={32} aria-hidden />
                <span>Chưa có ảnh</span>
              </div>
            )}
          </section>

          <section className="panel">
            <h2 className="section-title">Thông tin</h2>
            <Details
              items={[
                ['Danh mục', p.category.name],
                ['Thương hiệu', p.brand.name],
                ['Đối tượng', GENDER_LABEL[p.gender]],
                ['Chất liệu', p.material ?? <span className="muted">Chưa nhập</span>],
                ['Giá gốc', money(p.basePrice)],
                ['Đường dẫn', <span className="mono small">{p.slug}</span>],
              ]}
            />
          </section>
        </aside>
      </div>

      {canWrite && (
        <ProductForm
          open={editing}
          item={p}
          options={options.data ?? undefined}
          onClose={() => setEditing(false)}
          onSaved={(message) => {
            setEditing(false)
            toast.success(message)
            product.reload()
            variants.reload()
          }}
        />
      )}
    </div>
  )
}

/** Colours down, sizes across; each cell is one variant with its price and available stock. */
function VariantMatrix({
  variants,
  stock,
  hasStock,
  sizeOrder,
}: {
  variants: Variant[]
  stock: Map<string, StockTotals>
  hasStock: boolean
  sizeOrder: string[]
}) {
  const colors = [...new Map(variants.map((v) => [v.color.id, v.color])).values()]
  const sizes = sortVariants(variants, sizeOrder)
    .map((v) => v.size)
    .filter((size, index, all) => all.findIndex((other) => other.id === size.id) === index)
  const cell = new Map(variants.map((v) => [`${v.color.id}|${v.size.id}`, v]))

  return (
    <div className="table-wrap">
      <table className="variant-matrix">
        <thead>
          <tr>
            <th scope="col">Màu</th>
            {sizes.map((size) => (
              <th key={size.id} scope="col">
                {size.name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {colors.map((color) => (
            <tr key={color.id}>
              <th scope="row">
                <span className="inline-cell">
                  <Swatch hex={color.hexCode} size={16} />
                  {color.name}
                </span>
              </th>
              {sizes.map((size) => {
                const v = cell.get(`${color.id}|${size.id}`)
                if (!v) {
                  return (
                    <td key={size.id} className="matrix-none" aria-label="Không có biến thể">
                      -
                    </td>
                  )
                }
                const left = stock.get(v.id)?.available ?? 0
                const tone = v.status === 'INACTIVE' ? 'is-hidden' : hasStock && left <= 0 ? 'is-out' : hasStock && left <= 5 ? 'is-low' : ''
                return (
                  <td key={size.id} className={tone} title={v.sku}>
                    <span className="matrix-price">
                      <span className="price-full">{money(v.price)}</span>
                      <span className="price-short" aria-hidden>
                        {shortMoney(v.price)}
                      </span>
                    </span>
                    <span className="matrix-stock">
                      {v.status === 'INACTIVE' ? 'Đang ẩn' : !hasStock ? v.sku : left <= 0 ? 'Hết hàng' : `Còn ${countOf(left)}`}
                    </span>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="matrix-legend small muted">
        <span className="legend-low">Sắp hết: còn 5 trở xuống</span>
        <span className="legend-out">Hết hàng</span>
        <span className="legend-hidden">Đang ẩn</span>
      </p>
    </div>
  )
}

/** Colour name, then the size order configured in the admin (S, M, L...), falling back to the name. */
function sortVariants(variants: Variant[], sizeOrder: string[]) {
  const rank = (sizeId: string) => {
    const index = sizeOrder.indexOf(sizeId)
    return index === -1 ? Number.MAX_SAFE_INTEGER : index
  }
  return [...variants].sort(
    (a, b) => a.color.name.localeCompare(b.color.name, 'vi') || rank(a.size.id) - rank(b.size.id) || a.size.name.localeCompare(b.size.name),
  )
}

/** 459000 -> "459k", 1250000 -> "1,25tr": fits a grid cell on a phone. */
function shortMoney(value: number) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 2 })}tr`
  return `${Math.round(value / 1000)}k`
}

function BackLink() {
  return (
    <Link to="/products" className="back-link">
      <ArrowLeftIcon size={14} /> Tất cả sản phẩm
    </Link>
  )
}
