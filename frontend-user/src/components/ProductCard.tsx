import { TShirtIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import type { ProductCard as Card } from '../api/types'
import { formatMoney } from '../format'
import { WishButton } from '../wishlist/WishlistContext'

/** Product tile: photo (second photo on hover), name, price with markdown, and color swatches. */
export function ProductCard({ product, priority }: { product: Card; priority?: boolean }) {
  const sale = product.compareAtPrice !== null
  return (
    <div className="product-tile">
      <Link to={`/shop/products/${product.slug}`} className="product-card">
        <div className="product-media">
          {product.imageUrl ? (
            <>
              <img src={product.imageUrl} alt={product.name} loading={priority ? 'eager' : 'lazy'} decoding="async" />
              {product.hoverImageUrl && <img className="product-media-alt" src={product.hoverImageUrl} alt="" loading="lazy" decoding="async" />}
            </>
          ) : (
            <span className="product-media-empty" aria-hidden>
              <TShirtIcon size={40} weight="thin" />
            </span>
          )}
          {!product.inStock && <span className="product-flag">Hết hàng</span>}
          {product.inStock && sale && <span className="product-flag sale">Giảm giá</span>}
        </div>
        <div className="product-info">
          <span className="product-brand">{product.brandName}</span>
          <span className="product-name">{product.name}</span>
          <span className="product-price">
            <span className={sale ? 'price-sale' : undefined}>
              {product.minPrice === product.maxPrice ? formatMoney(product.minPrice) : `Từ ${formatMoney(product.minPrice)}`}
            </span>
            {sale && <s className="price-was">{formatMoney(product.compareAtPrice!)}</s>}
          </span>
          {product.colors.length > 1 && (
            <span className="product-swatches" aria-label={`${product.colors.length} màu: ${product.colors.map((c) => c.name).join(', ')}`}>
              {product.colors.slice(0, 5).map((color) => (
                <span key={color.name} className="swatch" style={{ background: color.hexCode ?? undefined }} title={color.name} />
              ))}
              {product.colors.length > 5 && <span className="muted small">+{product.colors.length - 5}</span>}
            </span>
          )}
        </div>
      </Link>
      {/* A sibling of the link, not inside it: a button inside an <a> is invalid and confuses screen readers. */}
      <WishButton productId={product.id} name={product.name} className="product-wish" />
    </div>
  )
}

/** Grid placeholder with the same shape as the product tiles. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="product-grid" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="product-card">
          <div className="product-media skel" />
          <div className="product-info">
            <span className="skel" style={{ width: '40%' }} />
            <span className="skel" style={{ width: '80%' }} />
            <span className="skel" style={{ width: '30%' }} />
          </div>
        </div>
      ))}
    </div>
  )
}
