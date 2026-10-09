import { FadersHorizontalIcon, XIcon } from '@phosphor-icons/react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { store, type ProductFilters } from '../api/store'
import type { Gender, StoreCatalog } from '../api/types'
import { ErrorBanner, Pagination, useTitle } from '../components/common'
import { ProductCard, ProductGridSkeleton } from '../components/ProductCard'
import { GENDER_LABEL } from '../format'
import { useAsync } from '../hooks'

const PAGE_SIZE = 24
const SORTS: [NonNullable<ProductFilters['sort']>, string][] = [
  ['newest', 'Mới nhất'],
  ['price_asc', 'Giá thấp đến cao'],
  ['price_desc', 'Giá cao đến thấp'],
  ['name', 'Tên A-Z'],
]
const PRICE_BANDS: [string, string, string][] = [
  ['', '300000', 'Dưới 300.000 ₫'],
  ['300000', '600000', '300.000 - 600.000 ₫'],
  ['600000', '1000000', '600.000 ₫ - 1 triệu'],
  ['1000000', '', 'Trên 1 triệu'],
]

/** Filters live in the URL so a filtered list can be shared, reloaded and navigated back to. */
function useFilters() {
  const [params, setParams] = useSearchParams()
  const get = (key: string) => params.get(key) ?? ''
  const filters: ProductFilters = {
    search: get('search') || undefined,
    categoryId: get('category') || undefined,
    brandId: get('brand') || undefined,
    gender: get('gender') || undefined,
    colorId: get('color') || undefined,
    sizeId: get('size') || undefined,
    minPrice: get('min') ? Number(get('min')) : undefined,
    maxPrice: get('max') ? Number(get('max')) : undefined,
    inStock: get('inStock') === 'true',
    sort: (SORTS.some(([key]) => key === get('sort')) ? get('sort') : 'newest') as ProductFilters['sort'],
    page: Math.max(0, Number(get('page') || 1) - 1),
    size: PAGE_SIZE,
  }
  const set = (changes: Record<string, string | null>) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value)
        else next.delete(key)
      }
      if (!('page' in changes)) next.delete('page')
      return next
    })
  return { filters, params, set }
}

export default function ShopPage() {
  const { filters, params, set } = useFilters()
  const [panelOpen, setPanelOpen] = useState(false)
  const catalog = useAsync(() => store.catalog(), [])
  const key = JSON.stringify(filters)
  const products = useAsync(() => store.products(filters), [key])

  const category = catalog.data?.categories.find((c) => c.id === filters.categoryId)
  const brand = catalog.data?.brands.find((b) => b.id === filters.brandId)
  const title = filters.search
    ? `Kết quả cho "${filters.search}"`
    : category?.name ?? brand?.name ?? (filters.gender ? `Thời trang ${GENDER_LABEL[filters.gender as Gender].toLowerCase()}` : 'Tất cả sản phẩm')
  useTitle(title)

  useEffect(() => {
    if (params.has('page')) window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [params])

  const active = activeChips(filters, catalog.data)

  return (
    <div className="container shop">
      <header className="shop-head">
        <div>
          <h1 className="page-title">{title}</h1>
          <p className="muted" aria-live="polite">
            {products.data ? `${products.data.totalElements} sản phẩm` : 'Đang tải sản phẩm'}
          </p>
        </div>
        <div className="shop-tools">
          <button type="button" className="btn btn-outline only-mobile" onClick={() => setPanelOpen(true)}>
            <FadersHorizontalIcon size={16} /> Bộ lọc{active.length > 0 && ` (${active.length})`}
          </button>
          <label className="select-wrap">
            <span className="sr-only">Sắp xếp</span>
            <select value={filters.sort} onChange={(e) => set({ sort: e.target.value === 'newest' ? null : e.target.value })}>
              {SORTS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </header>

      {active.length > 0 && (
        <div className="active-filters">
          {active.map((chip) => (
            <button key={chip.label} type="button" className="filter-chip" onClick={() => set(chip.clear)}>
              {chip.label} <XIcon size={12} aria-label="Bỏ lọc" />
            </button>
          ))}
          <button
            type="button"
            className="link-btn"
            onClick={() => set({ search: null, category: null, gender: null, color: null, size: null, min: null, max: null, inStock: null })}
          >
            Xóa tất cả
          </button>
        </div>
      )}

      <div className="shop-body">
        <aside className={`filters${panelOpen ? ' open' : ''}`} aria-label="Bộ lọc sản phẩm">
          <div className="filters-head only-mobile">
            <strong>Bộ lọc</strong>
            <button type="button" className="icon-btn" onClick={() => setPanelOpen(false)} aria-label="Đóng bộ lọc">
              <XIcon size={18} />
            </button>
          </div>

          {catalog.data && catalog.data.categories.length > 0 && (
            <fieldset className="filter-group">
              <legend>Danh mục</legend>
              <CategoryTree catalog={catalog.data} selected={filters.categoryId} onSelect={(id) => set({ category: id })} />
            </fieldset>
          )}

          <fieldset className="filter-group">
            <legend>Dành cho</legend>
            <div className="chip-row">
              {(Object.keys(GENDER_LABEL) as Gender[]).map((gender) => (
                <button key={gender} type="button" className="choice-chip" aria-pressed={filters.gender === gender} onClick={() => set({ gender: filters.gender === gender ? null : gender })}>
                  {GENDER_LABEL[gender]}
                </button>
              ))}
            </div>
          </fieldset>

          {catalog.data && catalog.data.sizes.length > 0 && (
            <fieldset className="filter-group">
              <legend>Size</legend>
              <div className="chip-row">
                {catalog.data.sizes.map((size) => (
                  <button key={size.id} type="button" className="choice-chip size" aria-pressed={filters.sizeId === size.id} onClick={() => set({ size: filters.sizeId === size.id ? null : size.id })}>
                    {size.name}
                  </button>
                ))}
              </div>
            </fieldset>
          )}

          {catalog.data && catalog.data.colors.length > 0 && (
            <fieldset className="filter-group">
              <legend>Màu sắc</legend>
              <div className="color-row">
                {catalog.data.colors.map((color) => (
                  <button
                    key={color.id}
                    type="button"
                    className="color-choice"
                    aria-pressed={filters.colorId === color.id}
                    onClick={() => set({ color: filters.colorId === color.id ? null : color.id })}
                    title={color.name}
                  >
                    <span className="swatch swatch-lg" style={{ background: color.hexCode ?? undefined }} aria-hidden />
                    <span className="sr-only">{color.name}</span>
                  </button>
                ))}
              </div>
            </fieldset>
          )}

          <fieldset className="filter-group">
            <legend>Khoảng giá</legend>
            <div className="radio-list">
              {PRICE_BANDS.map(([min, max, label]) => {
                const checked = (params.get('min') ?? '') === min && (params.get('max') ?? '') === max
                return (
                  <label key={label} className="radio-row">
                    <input type="radio" name="price" checked={checked} onChange={() => set({ min: min || null, max: max || null })} />
                    {label}
                  </label>
                )
              })}
            </div>
          </fieldset>

          <label className="toggle-row">
            <input type="checkbox" checked={filters.inStock} onChange={(e) => set({ inStock: e.target.checked ? 'true' : null })} />
            Chỉ hiện sản phẩm còn hàng
          </label>

          <button type="button" className="btn btn-primary btn-block only-mobile" onClick={() => setPanelOpen(false)}>
            Xem {products.data?.totalElements ?? ''} sản phẩm
          </button>
        </aside>
        {panelOpen && <button type="button" className="scrim only-mobile" aria-label="Đóng bộ lọc" onClick={() => setPanelOpen(false)} />}

        <section className="shop-results" aria-busy={products.loading}>
          <ErrorBanner error={products.error} onRetry={products.reload} />
          {products.loading && !products.data ? (
            <ProductGridSkeleton />
          ) : products.data && products.data.content.length > 0 ? (
            <div className={`product-grid${products.loading ? ' is-loading' : ''}`}>
              {products.data.content.map((product, index) => (
                <ProductCard key={product.id} product={product} priority={index < 4} />
              ))}
            </div>
          ) : products.data ? (
            <div className="empty-state">
              {active.length > 0 ? (
                <>
                  <h2>Không tìm thấy sản phẩm phù hợp</h2>
                  <p className="muted">Thử bỏ bớt bộ lọc hoặc tìm với từ khóa khác.</p>
                </>
              ) : (
                <>
                  <h2>Cửa hàng đang cập nhật sản phẩm</h2>
                  <p className="muted">Bộ sưu tập mới sẽ sớm lên kệ. Hãy quay lại sau ít ngày nhé.</p>
                </>
              )}
            </div>
          ) : null}
          {products.data && (
            <Pagination page={products.data.page} totalPages={products.data.totalPages} onChange={(page) => set({ page: page > 0 ? String(page + 1) : null })} />
          )}
        </section>
      </div>
    </div>
  )
}

function CategoryTree({ catalog, selected, onSelect }: { catalog: StoreCatalog; selected?: string; onSelect: (id: string | null) => void }) {
  const roots = catalog.categories.filter((c) => !c.parentId || !catalog.categories.some((p) => p.id === c.parentId))
  const children = (id: string) => catalog.categories.filter((c) => c.parentId === id)
  const render = (id: string, name: string, depth: number) => (
    <li key={id}>
      <button type="button" className="tree-link" aria-pressed={selected === id} style={{ paddingLeft: depth * 14 }} onClick={() => onSelect(selected === id ? null : id)}>
        {name}
      </button>
      {children(id).length > 0 && <ul>{children(id).map((c) => render(c.id, c.name, depth + 1))}</ul>}
    </li>
  )
  return <ul className="category-tree">{roots.map((c) => render(c.id, c.name, 0))}</ul>
}

function activeChips(filters: ProductFilters, catalog: StoreCatalog | undefined) {
  const chips: { label: string; clear: Record<string, null> }[] = []
  if (filters.search) chips.push({ label: `"${filters.search}"`, clear: { search: null } })
  const category = catalog?.categories.find((c) => c.id === filters.categoryId)
  if (category) chips.push({ label: category.name, clear: { category: null } })
  const brand = catalog?.brands.find((b) => b.id === filters.brandId)
  if (brand) chips.push({ label: brand.name, clear: { brand: null } })
  if (filters.gender && filters.gender in GENDER_LABEL) chips.push({ label: GENDER_LABEL[filters.gender as Gender], clear: { gender: null } })
  const size = catalog?.sizes.find((s) => s.id === filters.sizeId)
  if (size) chips.push({ label: `Size ${size.name}`, clear: { size: null } })
  const color = catalog?.colors.find((c) => c.id === filters.colorId)
  if (color) chips.push({ label: color.name, clear: { color: null } })
  const band = PRICE_BANDS.find(([min, max]) => String(filters.minPrice ?? '') === min && String(filters.maxPrice ?? '') === max)
  if (band && (filters.minPrice !== undefined || filters.maxPrice !== undefined)) chips.push({ label: band[2], clear: { min: null, max: null } })
  if (filters.inStock) chips.push({ label: 'Còn hàng', clear: { inStock: null } })
  return chips
}
