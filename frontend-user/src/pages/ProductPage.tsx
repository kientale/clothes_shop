import { CheckIcon, MinusIcon, PlusIcon, StarIcon, TShirtIcon } from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { store } from '../api/store'
import type { ProductDetail, VariantOption } from '../api/types'
import { MAX_LINE_QUANTITY, useCart } from '../cart/CartContext'
import { ErrorBanner, Spinner, useTitle } from '../components/common'
import { ProductCard } from '../components/ProductCard'
import { RecentlyViewed } from '../components/RecentlyViewed'
import { SizeChartButton } from '../components/SizeChartDialog'
import { StockAlertForm } from '../components/StockAlertForm'
import { GENDER_LABEL, formatDay, formatMoney, priceRange } from '../format'
import { useAsync } from '../hooks'
import { rememberProduct } from '../recent'
import { useSeo } from '../seo'
import { WishButton } from '../wishlist/WishlistContext'

/** Below this many units the page tells the shopper stock is running low. */
const LOW_STOCK = 5

export default function ProductPage() {
  const { id = '' } = useParams()
  const product = useAsync(() => store.product(id), [id])
  useTitle(product.data?.name)

  if (product.error) {
    return (
      <div className="container narrow">
        <ErrorBanner error={product.error} onRetry={product.reload} />
        <Link to="/shop/products" className="text-link">
          Quay lại cửa hàng
        </Link>
      </div>
    )
  }
  if (!product.data) return <div className="container"><Spinner /></div>
  return <ProductView key={product.data.id} product={product.data} />
}

function ProductView({ product }: { product: ProductDetail }) {
  const { add } = useCart()
  useSeo({
    description: product.shortDescription ?? product.description ?? undefined,
    image: product.images[0]?.url,
    type: 'product',
    jsonLd: productJsonLd(product),
  })
  const colors = useMemo(() => {
    const seen = new Map<string, VariantOption>()
    product.variants.forEach((v) => seen.has(v.colorId) || seen.set(v.colorId, v))
    return [...seen.values()]
  }, [product])
  // Start on the first color that has stock.
  const [colorId, setColorId] = useState(() => (product.variants.find((v) => v.available > 0) ?? product.variants[0])!.colorId)
  const sizes = product.variants.filter((v) => v.colorId === colorId).sort((a, b) => a.sizeSortOrder - b.sizeSortOrder)
  const [variantId, setVariantId] = useState<string | null>(null)
  const variant = sizes.find((v) => v.id === variantId) ?? null
  const [quantity, setQuantity] = useState(1)
  const [added, setAdded] = useState(false)
  const [image, setImage] = useState(0)
  const [hint, setHint] = useState<string>()
  const related = useAsync(() => store.products({ categoryId: product.category.id, size: 5 }), [product.category.id])

  useEffect(() => rememberProduct(product), [product])
  useEffect(() => setVariantId(null), [colorId])
  useEffect(() => setQuantity((q) => Math.min(q, Math.max(1, variant?.available ?? 1))), [variant])

  const prices = product.variants.map((v) => v.price)
  const price = variant ? variant.price : null
  const compare = variant?.compareAtPrice && variant.compareAtPrice > variant.price ? variant.compareAtPrice : null
  const maxQuantity = Math.min(MAX_LINE_QUANTITY, variant?.available ?? MAX_LINE_QUANTITY)
  const soldOut = product.variants.every((v) => v.available === 0)
  const variantOut = variant !== null && variant.available === 0
  const images = product.images

  const addToCart = () => {
    if (!variant) {
      setHint('Vui lòng chọn size.')
      return
    }
    if (variant.available === 0) return
    add(
      {
        variantId: variant.id,
        productId: product.id,
        slug: product.slug,
        name: product.name,
        imageUrl: images[0]?.url ?? null,
        colorName: variant.colorName,
        sizeName: variant.sizeName,
        sku: variant.sku,
        price: variant.price,
      },
      quantity,
    )
    setAdded(true)
    window.setTimeout(() => setAdded(false), 2200)
  }

  return (
    <div className="container product-page">
      <nav className="breadcrumbs" aria-label="Đường dẫn">
        <Link to="/shop">Cửa hàng</Link>
        <span aria-hidden>/</span>
        <Link to={`/shop/products?category=${product.category.id}`}>{product.category.name}</Link>
        <span aria-hidden>/</span>
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="product-layout">
        <div className="gallery">
          <div className="gallery-main">
            {images[image] ? (
              <img src={images[image].url} alt={images[image].altText ?? product.name} />
            ) : (
              <span className="product-media-empty" aria-hidden>
                <TShirtIcon size={64} weight="thin" />
              </span>
            )}
          </div>
          {images.length > 1 && (
            <div className="gallery-thumbs" role="group" aria-label="Ảnh sản phẩm">
              {images.map((img, index) => (
                <button key={img.url} type="button" aria-pressed={index === image} onClick={() => setImage(index)} aria-label={`Ảnh ${index + 1}`}>
                  <img src={img.url} alt="" loading="lazy" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="buy-box">
          <span className="product-brand">{product.brand.name}</span>
          <h1 className="product-title">{product.name}</h1>
          {product.ratingCount > 0 && (
            <a href="#reviews" className="rating-line">
              <Stars value={product.ratingAverage} /> {product.ratingAverage.toFixed(1)} <span className="muted">({product.ratingCount} đánh giá)</span>
            </a>
          )}
          <p className="buy-price">
            <span className={compare ? 'price-sale' : undefined}>{price !== null ? formatMoney(price) : priceRange(Math.min(...prices), Math.max(...prices))}</span>
            {compare && <s className="price-was">{formatMoney(compare)}</s>}
          </p>
          {product.shortDescription && <p className="buy-lede">{product.shortDescription}</p>}

          <fieldset className="option-group">
            <legend>
              Màu sắc: <strong>{colors.find((c) => c.colorId === colorId)?.colorName}</strong>
            </legend>
            <div className="color-row">
              {colors.map((c) => {
                const out = product.variants.filter((v) => v.colorId === c.colorId).every((v) => v.available === 0)
                return (
                  <button key={c.colorId} type="button" className={`color-choice${out ? ' out' : ''}`} aria-pressed={c.colorId === colorId} onClick={() => setColorId(c.colorId)} title={c.colorName}>
                    <span className="swatch swatch-lg" style={{ background: c.hexCode ?? undefined }} aria-hidden />
                    <span className="sr-only">
                      {c.colorName}
                      {out && ' (hết hàng)'}
                    </span>
                  </button>
                )
              })}
            </div>
          </fieldset>

          <fieldset className="option-group">
            <legend className="legend-row">
              <span>Size</span>
              {product.sizeChart && <SizeChartButton chart={product.sizeChart} productName={product.name} />}
            </legend>
            <div className="size-grid">
              {sizes.map((v) => (
                <button
                  key={v.id}
                  type="button"
                  className={`size-choice${v.available === 0 ? ' out' : ''}`}
                  aria-pressed={v.id === variantId}
                  aria-label={v.available === 0 ? `${v.sizeName}, hết hàng` : undefined}
                  title={v.available === 0 ? 'Hết hàng, bấm để nhận thông báo khi có lại' : undefined}
                  onClick={() => {
                    setVariantId(v.id)
                    setHint(undefined)
                  }}
                >
                  {v.sizeName}
                </button>
              ))}
            </div>
            {hint && <p className="field-error">{hint}</p>}
            {variant && variant.available > 0 && variant.available <= LOW_STOCK && <p className="stock-note">Chỉ còn {variant.available} sản phẩm</p>}
            {variantOut && <StockAlertForm key={variant.id} variantId={variant.id} label={`${variant.colorName} / size ${variant.sizeName}`} />}
          </fieldset>

          <div className="buy-actions">
            <div className="stepper" role="group" aria-label="Số lượng">
              <button type="button" onClick={() => setQuantity((q) => Math.max(1, q - 1))} disabled={quantity <= 1} aria-label="Giảm số lượng">
                <MinusIcon size={14} />
              </button>
              <output aria-live="polite">{quantity}</output>
              <button type="button" onClick={() => setQuantity((q) => Math.min(maxQuantity, q + 1))} disabled={quantity >= maxQuantity} aria-label="Tăng số lượng">
                <PlusIcon size={14} />
              </button>
            </div>
            <button type="button" className="btn btn-primary btn-lg btn-grow" onClick={addToCart} disabled={soldOut || variantOut}>
              {soldOut || variantOut ? 'Hết hàng' : added ? (
                <>
                  <CheckIcon size={16} weight="bold" /> Đã thêm vào giỏ
                </>
              ) : (
                'Thêm vào giỏ'
              )}
            </button>
            <WishButton productId={product.id} name={product.name} className="product-page-wish" withLabel />
          </div>
          {added && (
            <Link to="/shop/cart" className="text-link">
              Xem giỏ hàng và thanh toán
            </Link>
          )}

          <dl className="spec-list">
            <div>
              <dt>Dành cho</dt>
              <dd>{GENDER_LABEL[product.gender]}</dd>
            </div>
            {product.material && (
              <div>
                <dt>Chất liệu</dt>
                <dd>{product.material}</dd>
              </div>
            )}
            <div>
              <dt>Mã sản phẩm</dt>
              <dd>{variant?.sku ?? product.productCode}</dd>
            </div>
          </dl>

          {product.description && (
            <details className="disclosure" open>
              <summary>Mô tả sản phẩm</summary>
              <p className="prose">{product.description}</p>
            </details>
          )}
          <details className="disclosure">
            <summary>Giao hàng và đổi trả</summary>
            <p className="prose">
              Phí giao hàng được tính khi thanh toán. Xem chi tiết tại <Link to="/policies/SHIPPING">chính sách giao hàng</Link> và{' '}
              <Link to="/policies/RETURN_EXCHANGE">chính sách đổi trả</Link>.
            </p>
          </details>
        </div>
      </div>

      <section id="reviews" className="section" aria-labelledby="reviews-title">
        <h2 id="reviews-title" className="section-title">
          Đánh giá {product.ratingCount > 0 && <span className="muted">({product.ratingCount})</span>}
        </h2>
        {product.reviews.length === 0 ? (
          <p className="muted">Chưa có đánh giá. Khách đã mua và nhận hàng có thể đánh giá sản phẩm này.</p>
        ) : (
          <div className="review-list">
            {product.reviews.map((review) => (
              <article key={review.id} className="review">
                <Stars value={review.rating} />
                {review.comment && <p>{review.comment}</p>}
                {review.images.length > 0 && (
                  <div className="review-images">
                    {review.images.map((src) => (
                      <img key={src} src={src} alt="Ảnh khách gửi kèm đánh giá" loading="lazy" />
                    ))}
                  </div>
                )}
                <span className="muted small">
                  {review.customerName}, {formatDay(review.createdAt)}
                </span>
              </article>
            ))}
          </div>
        )}
      </section>

      {related.data && related.data.content.filter((p) => p.id !== product.id).length > 0 && (
        <section className="section" aria-labelledby="related-title">
          <h2 id="related-title" className="section-title">
            Có thể bạn cũng thích
          </h2>
          <div className="product-grid">
            {related.data.content
              .filter((p) => p.id !== product.id)
              .slice(0, 4)
              .map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
          </div>
        </section>
      )}

      <RecentlyViewed exclude={product.id} />
    </div>
  )
}

function Stars({ value }: { value: number }) {
  return (
    <span className="stars" aria-label={`${value.toFixed(1)} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <StarIcon key={n} size={14} weight={n <= Math.round(value) ? 'fill' : 'regular'} aria-hidden />
      ))}
    </span>
  )
}

/** schema.org Product, so search results can show price, availability and rating. */
function productJsonLd(product: ProductDetail) {
  const prices = product.variants.map((v) => v.price)
  const inStock = product.variants.some((v) => v.available > 0)
  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    sku: product.productCode,
    description: product.shortDescription ?? product.description ?? undefined,
    image: product.images.map((i) => i.url),
    brand: { '@type': 'Brand', name: product.brand.name },
    category: product.category.name,
    offers: {
      '@type': 'AggregateOffer',
      priceCurrency: 'VND',
      lowPrice: Math.min(...prices),
      highPrice: Math.max(...prices),
      offerCount: product.variants.length,
      availability: inStock ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
      url: window.location.origin + '/shop/products/' + product.slug,
    },
    ...(product.ratingCount > 0
      ? { aggregateRating: { '@type': 'AggregateRating', ratingValue: product.ratingAverage, reviewCount: product.ratingCount } }
      : {}),
  }
}
