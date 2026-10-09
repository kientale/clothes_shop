import { ArrowRightIcon, ArrowsClockwiseIcon, CreditCardIcon, TruckIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { store } from '../api/store'
import type { ProductCard as Card } from '../api/types'
import { ErrorBanner, Reveal, useTitle } from '../components/common'
import { ProductCard, ProductGridSkeleton } from '../components/ProductCard'
import { RecentlyViewed } from '../components/RecentlyViewed'
import { formatDay, formatMoney } from '../format'
import { useAsync } from '../hooks'

/** Admins manage the hero photo in Marketing > Banner, with this position code. */
const HERO_POSITION = 'HOME_HERO'

export default function HomePage() {
  useTitle('Thời trang')
  const home = useAsync(async () => {
    const [banners, latest, women, men, catalog, lookbooks, config] = await Promise.all([
      store.banners(HERO_POSITION).catch(() => []),
      store.products({ sort: 'newest', size: 8 }),
      store.products({ gender: 'WOMEN', sort: 'newest', size: 1 }).catch(() => null),
      store.products({ gender: 'MEN', sort: 'newest', size: 1 }).catch(() => null),
      store.catalog().catch(() => null),
      store.articles('LOOKBOOK', 3).catch(() => null),
      store.configuration().catch(() => null),
    ])
    return { banner: banners[0], latest: latest.content, total: latest.totalElements, women: women?.content[0], men: men?.content[0], catalog, lookbooks: lookbooks?.content ?? [], config }
  }, [])

  const d = home.data
  const heroImage = d?.banner?.imageUrl ?? d?.latest.find((p) => p.imageUrl)?.imageUrl ?? null
  const heroLink = d?.banner?.linkUrl && d.banner.linkUrl.startsWith('/') ? d.banner.linkUrl : '/shop/products'
  const topCategories = d?.catalog?.categories.filter((c) => !c.parentId) ?? []

  return (
    <div className="home">
      <section className={`hero${heroImage ? '' : ' hero-plain'}`} aria-labelledby="hero-title">
        {heroImage && <img className="hero-image" src={heroImage} alt={d?.banner?.title ?? ''} fetchPriority="high" decoding="async" />}
        <div className="hero-shade" aria-hidden />
        <div className="container hero-content">
          <h1 id="hero-title" className="hero-title">
            Mặc đẹp mỗi ngày, <em>theo cách của bạn</em>
          </h1>
          <p className="hero-lede">Áo, quần, váy và phụ kiện cho nam, nữ và trẻ em. Chọn size dễ dàng, giao tận nơi.</p>
          <div className="hero-actions">
            <Link to={heroLink} className="btn btn-light btn-lg">
              Mua ngay <ArrowRightIcon size={16} weight="bold" />
            </Link>
            <Link to="/shop/products?gender=WOMEN" className="btn btn-glass btn-lg">
              Thời trang nữ
            </Link>
          </div>
        </div>
      </section>

      {topCategories.length > 0 && (
        <nav className="container category-rail" aria-label="Danh mục">
          {topCategories.map((category) => (
            <Link key={category.id} to={`/shop/products?category=${category.id}`} className="category-chip">
              {category.name}
            </Link>
          ))}
        </nav>
      )}

      <section className="container section" aria-labelledby="latest-title">
        <div className="section-head">
          <h2 id="latest-title" className="section-title">
            Hàng mới về
          </h2>
          {d && d.total > d.latest.length && (
            <Link to="/shop/products?sort=newest" className="text-link">
              Xem tất cả {d.total} sản phẩm <ArrowRightIcon size={14} />
            </Link>
          )}
        </div>
        <ErrorBanner error={home.error} onRetry={home.reload} />
        {!d && !home.error ? (
          <ProductGridSkeleton count={4} />
        ) : d && d.latest.length > 0 ? (
          <div className="product-grid">
            {d.latest.map((product, index) => (
              <Reveal key={product.id} delay={(index % 4) * 70}>
                <ProductCard product={product} priority={index < 4} />
              </Reveal>
            ))}
          </div>
        ) : d ? (
          <div className="empty-state">
            <h3>Cửa hàng đang cập nhật sản phẩm</h3>
            <p className="muted">Bộ sưu tập mới sẽ sớm lên kệ. Hãy quay lại sau ít ngày nhé.</p>
          </div>
        ) : null}
      </section>

      {(d?.women || d?.men) && (
        <section className="container section gender-split" aria-label="Mua theo đối tượng">
          {[
            { product: d.women, label: 'Nữ', to: '/shop/products?gender=WOMEN', copy: 'Váy, áo kiểu và đồ công sở' },
            { product: d.men, label: 'Nam', to: '/shop/products?gender=MEN', copy: 'Sơ mi, quần và đồ mặc hằng ngày' },
          ]
            .filter((tile): tile is { product: Card; label: string; to: string; copy: string } => Boolean(tile.product))
            .map((tile, index) => (
              <Reveal key={tile.label} delay={index * 90}>
                <Link to={tile.to} className="gender-tile">
                  {tile.product.imageUrl && <img src={tile.product.imageUrl} alt="" loading="lazy" />}
                  <span className="gender-tile-text">
                    <span className="gender-tile-title">{tile.label}</span>
                    <span>{tile.copy}</span>
                  </span>
                  <span className="gender-tile-arrow" aria-hidden>
                    <ArrowRightIcon size={18} />
                  </span>
                </Link>
              </Reveal>
            ))}
        </section>
      )}

      {d && d.lookbooks.length > 0 && (
        <section className="container section" aria-labelledby="lookbook-title">
          <div className="section-head">
            <h2 id="lookbook-title" className="section-title">
              Lookbook
            </h2>
            <Link to="/shop/articles?type=LOOKBOOK" className="text-link">
              Xem tất cả <ArrowRightIcon size={14} />
            </Link>
          </div>
          <div className="lookbook-row">
            {d.lookbooks.map((article, index) => (
              <Reveal key={article.id} delay={index * 80} className={index === 0 ? 'lookbook-lead' : undefined}>
                <Link to={`/shop/articles/${article.slug}`} className="lookbook-card">
                  {article.thumbnailUrl ? <img src={article.thumbnailUrl} alt="" loading="lazy" /> : <span className="lookbook-empty" aria-hidden />}
                  <h3>{article.title}</h3>
                  <span className="muted small">{formatDay(article.publishedAt)}</span>
                </Link>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      <div className="container">
        <RecentlyViewed limit={4} />
      </div>

      {d?.config && (
        <Reveal as="section" className="container service-band">
          <div className="service-item">
            <TruckIcon size={26} weight="light" aria-hidden />
            <div>
              <strong>Giao hàng toàn quốc</strong>
              <span className="muted">
                {d.config.shippingMethods.length > 0
                  ? `Từ ${formatMoney(Math.min(...d.config.shippingMethods.map((m) => m.baseFee)))}, ${d.config.shippingMethods.map((m) => m.name).join(', ')}`
                  : 'Phí giao hàng báo khi đặt đơn'}
              </span>
            </div>
          </div>
          <div className="service-item">
            <CreditCardIcon size={26} weight="light" aria-hidden />
            <div>
              <strong>Thanh toán linh hoạt</strong>
              <span className="muted">{d.config.paymentMethods.length > 0 ? d.config.paymentMethods.map((m) => m.name).join(', ') : 'Thanh toán khi nhận hàng'}</span>
            </div>
          </div>
          <div className="service-item">
            <ArrowsClockwiseIcon size={26} weight="light" aria-hidden />
            <div>
              <strong>Đổi trả dễ dàng</strong>
              <Link to="/policies/RETURN_EXCHANGE" className="text-link">
                Xem chính sách đổi trả
              </Link>
            </div>
          </div>
        </Reveal>
      )}
    </div>
  )
}
