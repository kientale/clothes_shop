import { ArrowLeftIcon, ArrowRightIcon, StackIcon } from '@phosphor-icons/react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { store } from '../api/store'
import { ErrorBanner, Pagination, Reveal, useTitle } from '../components/common'
import { ProductCard, ProductGridSkeleton } from '../components/ProductCard'
import { formatDay } from '../format'
import { useAsync } from '../hooks'
import { useSeo } from '../seo'

/** Every visible collection as large photo tiles. */
export function CollectionsPage() {
  useTitle('Bộ sưu tập')
  useSeo({ description: 'Các bộ sưu tập theo mùa và theo phong cách của LemonadeX.' })
  const data = useAsync(() => store.collections(), [])

  return (
    <div className="container">
      <h1 className="page-title">Bộ sưu tập</h1>
      <p className="muted page-lede">Những nhóm sản phẩm được phối sẵn theo mùa và theo dịp.</p>
      <ErrorBanner error={data.error} onRetry={data.reload} />
      {!data.data && !data.error ? (
        <div className="collection-grid" aria-hidden>
          {Array.from({ length: 3 }, (_, i) => (
            <span key={i} className="skel collection-skel" />
          ))}
        </div>
      ) : data.data && data.data.length > 0 ? (
        <div className={`collection-grid ${data.data.length <= 4 ? `n${data.data.length}` : 'many'}`}>
          {data.data.map((c, index, all) => (
            <Reveal key={c.id} delay={Math.min(index, 4) * 70} className={index === 0 && all.length <= 4 ? 'collection-lead' : undefined}>
              <Link to={`/shop/collections/${c.slug}`} className="collection-tile">
                {c.imageUrl ? <img src={c.imageUrl} alt="" loading={index < 2 ? 'eager' : 'lazy'} /> : <span className="collection-empty" aria-hidden />}
                <span className="collection-text">
                  <span className="collection-name">{c.name}</span>
                  <span className="collection-count">
                    {c.productCount} sản phẩm <ArrowRightIcon size={14} aria-hidden />
                  </span>
                </span>
              </Link>
            </Reveal>
          ))}
        </div>
      ) : data.data ? (
        <div className="empty-state">
          <StackIcon size={44} weight="thin" aria-hidden />
          <h2>Chưa có bộ sưu tập nào</h2>
          <Link to="/shop/products" className="btn btn-primary">
            Xem tất cả sản phẩm
          </Link>
        </div>
      ) : null}
    </div>
  )
}

export function CollectionPage() {
  const { slug = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 1) - 1)
  const data = useAsync(() => store.collection(slug, page, 24), [slug, page])
  const c = data.data?.collection
  useTitle(c?.name ?? 'Bộ sưu tập')
  useSeo({ description: c?.description ?? undefined, image: c?.imageUrl ?? undefined })

  return (
    <div className="container">
      <Link to="/shop/collections" className="back-link">
        <ArrowLeftIcon size={14} /> Tất cả bộ sưu tập
      </Link>
      {data.error ? (
        <ErrorBanner error={data.error} onRetry={data.reload} />
      ) : (
        <>
          <header className={c?.imageUrl ? 'collection-hero has-image' : 'collection-hero'}>
            {c?.imageUrl && <img src={c.imageUrl} alt="" />}
            <div className="collection-hero-text">
              {c ? <h1>{c.name}</h1> : <span className="skel" style={{ width: '12ch', height: 40 }} />}
              {c?.description && <p>{c.description}</p>}
              {c?.endAt && <span className="collection-until">Đến hết {formatDay(c.endAt)}</span>}
            </div>
          </header>
          {!data.data ? (
            <ProductGridSkeleton count={4} />
          ) : data.data.products.content.length > 0 ? (
            <>
              <div className="product-grid">
                {data.data.products.content.map((product, index) => (
                  <ProductCard key={product.id} product={product} priority={index < 4} />
                ))}
              </div>
              <Pagination page={data.data.products.page} totalPages={data.data.products.totalPages}
                onChange={(next) => setParams(next > 0 ? { page: String(next + 1) } : {})} />
            </>
          ) : (
            <div className="empty-state">
              <h2>Bộ sưu tập đang được cập nhật</h2>
              <Link to="/shop/products" className="btn btn-primary">
                Xem tất cả sản phẩm
              </Link>
            </div>
          )}
        </>
      )}
    </div>
  )
}
