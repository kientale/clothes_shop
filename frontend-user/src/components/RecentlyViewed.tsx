import { useState } from 'react'
import { recentProducts } from '../recent'
import { ProductCard } from './ProductCard'

/** "Bạn đã xem gần đây": products opened in this browser, newest first, without the one on screen. */
export function RecentlyViewed({ exclude, limit = 4 }: { exclude?: string; limit?: number }) {
  // Read once per mount, so the list does not reshuffle while the shopper looks at it.
  const [products] = useState(() => recentProducts().filter((p) => p.id !== exclude).slice(0, limit))
  if (products.length === 0) return null
  return (
    <section className="section" aria-labelledby="recent-title">
      <h2 id="recent-title" className="section-title">
        Bạn đã xem gần đây
      </h2>
      <div className="product-grid">
        {products.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>
    </section>
  )
}
