import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { CategoryResponse, Page, ProductResponse } from '../api/types'
import { ErrorBanner, Pagination, Spinner } from '../components/common'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'

const PAGE_SIZE = 12

export function priceRange(product: ProductResponse) {
  const prices = product.variants.map((variant) => variant.price)
  if (prices.length === 0) return ''
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  return min === max ? formatMoney(min) : `${formatMoney(min)} – ${formatMoney(max)}`
}

export default function ShopPage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? 0)
  const categoryId = params.get('categoryId') ?? ''
  const search = params.get('search') ?? ''
  const [searchInput, setSearchInput] = useState(search)

  const categories = useAsync(() => api.get<CategoryResponse[]>('/categories'), [])
  const products = useAsync(
    () => api.get<Page<ProductResponse>>('/products', { page, size: PAGE_SIZE, categoryId, search }),
    [page, categoryId, search],
  )

  const update = (changes: Record<string, string | number>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '' || value === 0) next.delete(key)
      else next.set(key, String(value))
    }
    setParams(next)
  }

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    update({ search: searchInput.trim(), page: 0 })
  }

  return (
    <>
      <section className="hero">
        <h1>Bộ sưu tập mới</h1>
        <p className="muted">Chọn size, màu và đặt hàng — thanh toán khi nhận hàng.</p>
      </section>

      <div className="toolbar">
        <form onSubmit={onSearch} className="search">
          <input
            type="search"
            placeholder="Tìm sản phẩm…"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            maxLength={100}
          />
          <button className="btn btn-primary">Tìm</button>
        </form>
        <div className="chips">
          <button className={`chip ${categoryId === '' ? 'active' : ''}`} onClick={() => update({ categoryId: '', page: 0 })}>
            Tất cả
          </button>
          {categories.data?.map((category) => (
            <button
              key={category.id}
              className={`chip ${categoryId === category.id ? 'active' : ''}`}
              onClick={() => update({ categoryId: category.id, page: 0 })}
            >
              {category.name}
            </button>
          ))}
        </div>
      </div>

      <ErrorBanner error={products.error} />
      {products.loading && !products.data ? (
        <Spinner />
      ) : products.data && products.data.content.length === 0 ? (
        <p className="empty">Không tìm thấy sản phẩm phù hợp.</p>
      ) : (
        <div className="grid">
          {products.data?.content.map((product) => {
            const soldOut = product.variants.every((variant) => variant.availableQuantity === 0)
            return (
              <Link key={product.id} to={`/products/${product.id}`} className="card product-card">
                <div className="thumb">
                  {product.images[0] ? <img src={product.images[0]} alt={product.name} loading="lazy" /> : <span>{product.brand}</span>}
                  {soldOut && <span className="sold-out">Hết hàng</span>}
                </div>
                <div className="card-body">
                  <span className="muted small">{product.brand}</span>
                  <strong>{product.name}</strong>
                  <span className="price">{priceRange(product)}</span>
                </div>
              </Link>
            )
          })}
        </div>
      )}

      {products.data && (
        <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(next) => update({ page: next })} />
      )}
    </>
  )
}
