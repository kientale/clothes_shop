import { ArrowRightIcon, ClockIcon, MapPinIcon, PhoneIcon, StarIcon } from '@phosphor-icons/react'
import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { store } from '../api/store'
import type { ProductCard, ProductDetail } from '../api/types'
import { Reveal, useTitle } from '../components/common'
import { SiteFooter } from '../components/SiteFooter'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'

const HEADLINE = ['Thời', 'trang', 'mặc', 'mỗi', 'ngày']

/**
 * The shop's introduction page ("/"), in the style of the original LemonadeX homepage: a full-bleed
 * photo card with the navigation on top, then the story, categories, numbers, reviews and the store
 * address. Photos are banners managed in the admin (INTRO_HERO, INTRO_STORY).
 */
export default function IntroPage() {
  useTitle('Giới thiệu')
  const data = useAsync(async () => {
    const [config, hero, story, products, catalog] = await Promise.all([
      store.configuration().catch(() => null),
      store.banners('INTRO_HERO').catch(() => []),
      store.banners('INTRO_STORY').catch(() => []),
      store.products({ sort: 'newest', size: 48 }).catch(() => null),
      store.catalog().catch(() => null),
    ])
    // Reviews are published per product; read the first few products that have any.
    const details = await Promise.all((products?.content ?? []).slice(0, 8).map((p) => store.product(p.slug).catch(() => null)))
    return { config, hero: hero[0], story, products, catalog, details: details.filter((d): d is ProductDetail => d !== null) }
  }, [])

  const d = data.data
  const shop = d?.config?.store
  const products = d?.products?.content ?? []
  const featured = products.find((p) => p.imageUrl && p.inStock) ?? products.find((p) => p.imageUrl)
  const heroImage = d?.hero?.imageUrl ?? featured?.imageUrl ?? null
  const categories = categoryTiles(d?.catalog?.categories ?? [], Array.isArray(products) ? products : [])
  // Five tiles fill the mosaic exactly (one large, four small); the rest become links below it.
  const mosaic = categories.slice(0, 5)
  const moreCategories = categories.slice(5)
  const reviews = (d?.details ?? [])
    .flatMap((p) => p.reviews.filter((r) => r.comment && r.rating >= 4).map((r) => ({ ...r, productName: p.name, slug: p.slug })))
    .filter((review, index, all) => all.findIndex((other) => other.id === review.id) === index)
    .slice(0, 3)
  const stats = [
    { value: d?.products?.totalElements ?? 0, label: 'sản phẩm đang bán' },
    { value: d?.catalog?.categories?.length ?? 0, label: 'danh mục' },
    { value: d?.catalog?.brands?.length ?? 0, label: 'thương hiệu' },
    { value: d?.config?.shippingMethods?.length ?? 0, label: 'cách giao hàng' },
  ].filter((stat) => stat.value > 0)

  return (
    <div className="intro">
      <section className={`intro-hero${heroImage ? '' : ' plain'}`} aria-labelledby="intro-title">
        {heroImage && <img className="intro-hero-image" src={heroImage} alt="" fetchPriority="high" decoding="async" />}
        <div className="intro-hero-shade" aria-hidden />

        <header className="intro-header">
          <nav className="intro-nav" aria-label="Trang giới thiệu">
            <a href="#story">Câu chuyện</a>
            <a href="#categories">Danh mục</a>
            <a href="#visit">Cửa hàng</a>
          </nav>
          <Link to="/" className="wordmark intro-wordmark">
            LemonadeX
          </Link>
          <div className="intro-header-end">
            <Link to="/shop" className="pill pill-light">
              Vào cửa hàng
            </Link>
          </div>
        </header>

        <div className="intro-hero-foot">
          <div className="intro-hero-copy">
            <h1 id="intro-title" className="intro-title">
              {HEADLINE.map((word, index) => (
                <span key={word} className="word-clip">
                  <span className="word" style={{ animationDelay: `${150 + index * 110}ms` }}>
                    {word}
                  </span>
                </span>
              ))}
            </h1>
            <p className="intro-lede">Áo, quần, váy và phụ kiện chọn lọc cho nam, nữ và trẻ em. Mua online hoặc ghé cửa hàng ở Quận 5.</p>
            <div className="intro-actions">
              <Link to="/shop" className="pill pill-light pill-lg">
                Vào cửa hàng <ArrowRightIcon size={16} weight="bold" />
              </Link>
              <Link to="/shop/products?sort=newest" className="pill pill-glass pill-lg">
                Hàng mới về
              </Link>
            </div>
          </div>

          {featured && (
            <Link to={`/shop/products/${featured.slug}`} className="intro-product glass">
              {featured.imageUrl && <img src={featured.imageUrl} alt="" />}
              <span className="intro-product-text">
                <span className="intro-product-label">Mới lên kệ</span>
                <strong>{featured.name}</strong>
                <span>{formatMoney(featured.minPrice)}</span>
              </span>
              <span className="intro-product-arrow" aria-hidden>
                <ArrowRightIcon size={16} />
              </span>
            </Link>
          )}
        </div>
      </section>

      <section id="story" className="container intro-story" aria-labelledby="story-title">
        <Reveal className="intro-story-text">
          <h2 id="story-title" className="intro-statement">
            Bắt đầu từ một cửa hàng nhỏ ở Quận 5, với một ý tưởng: <em>quần áo đẹp phải mặc được mỗi ngày.</em>
          </h2>
          <p>
            Chúng tôi chọn chất liệu tự nhiên như cotton, linen và lụa, giữ phom dáng đơn giản để dễ phối, và may vừa vặn với vóc người Việt. Mỗi tuần
            đều có hàng mới, đổi size dễ dàng trong 7 ngày.
          </p>
          <Link to="/policies/RETURN_EXCHANGE" className="text-link">
            Chính sách đổi trả <ArrowRightIcon size={14} />
          </Link>
        </Reveal>
        {d && d.story.length > 0 && (
          <Reveal className="intro-story-photos" delay={120}>
            {d.story.slice(0, 3).map((banner, index) => (
              <figure key={banner.id} className={`story-photo p${index}`}>
                <img src={banner.imageUrl} alt={banner.title} loading="lazy" />
              </figure>
            ))}
          </Reveal>
        )}
      </section>

      {categories.length > 0 && (
        <section id="categories" className="intro-categories" aria-labelledby="categories-title">
          <div className="container intro-section-head">
            <h2 id="categories-title" className="intro-heading">
              Mua theo danh mục
            </h2>
            <Link to="/shop/products" className="text-link">
              Xem tất cả sản phẩm <ArrowRightIcon size={14} />
            </Link>
          </div>
          <div className={`container category-mosaic n${mosaic.length}`}>
            {mosaic.map((tile, index) => (
              <Reveal key={tile.id} delay={index * 70} className={index === 0 ? 'category-tile lead' : 'category-tile'}>
                <Link to={`/shop/products?category=${tile.id}`} className="category-card">
                  {tile.imageUrl ? <img src={tile.imageUrl} alt="" loading="lazy" /> : <span className="category-card-empty" aria-hidden />}
                  <span className="category-card-text">
                    <span className="category-card-name">{tile.name}</span>
                    <span className="category-card-count">{tile.count > 0 ? `${tile.count} sản phẩm` : 'Sắp có hàng'}</span>
                  </span>
                </Link>
              </Reveal>
            ))}
          </div>
          {moreCategories.length > 0 && (
            <div className="container category-more">
              <span>Danh mục khác</span>
              {moreCategories.map((tile) => (
                <Link key={tile.id} to={`/shop/products?category=${tile.id}`} className="category-pill">
                  {tile.name}
                </Link>
              ))}
            </div>
          )}
        </section>
      )}

      {d?.products && d.products.totalElements > 0 && (
        <Reveal as="section" className="container">
          <div className={`intro-numbers n${stats.length}`} aria-label="LemonadeX bằng con số">
            {stats.map((stat) => (
              <Stat key={stat.label} value={stat.value} label={stat.label} />
            ))}
          </div>
        </Reveal>
      )}

      {reviews.length > 0 && (
        <section className="container intro-reviews" aria-labelledby="reviews-title">
          <h2 id="reviews-title" className="intro-heading">
            Khách hàng nói gì
          </h2>
          <div className={`review-wall n${reviews.length}`}>
            {reviews.map((review, index) => (
              <Reveal key={review.id} delay={index * 90} className={index === 0 ? 'review-lead' : undefined}>
                <figure className="quote">
                  <span className="stars" aria-label={`${review.rating} trên 5 sao`}>
                    {Array.from({ length: review.rating }, (_, i) => (
                      <StarIcon key={i} size={14} weight="fill" aria-hidden />
                    ))}
                  </span>
                  <blockquote>“{review.comment}”</blockquote>
                  <figcaption>
                    <strong>{review.customerName}</strong>
                    <Link to={`/shop/products/${review.slug}`}>{review.productName}</Link>
                  </figcaption>
                </figure>
              </Reveal>
            ))}
          </div>
        </section>
      )}

      <section id="visit" className="container intro-visit" aria-labelledby="visit-title">
        {d && d.story[0] && (
          <Reveal className="visit-photo">
            <img src={d.story[0].imageUrl} alt={d.story[0].title} loading="lazy" />
          </Reveal>
        )}
        <Reveal className="visit-card" delay={100}>
          <h2 id="visit-title" className="intro-heading">
            Ghé cửa hàng
          </h2>
          <ul className="visit-list">
            {shop?.address && (
              <li>
                <MapPinIcon size={20} aria-hidden /> {shop.address}
              </li>
            )}
            {shop?.businessHours && (
              <li>
                <ClockIcon size={20} aria-hidden /> {shop.businessHours}
              </li>
            )}
            {shop?.supportPhone && (
              <li>
                <PhoneIcon size={20} aria-hidden /> <a href={`tel:${shop.supportPhone.replace(/\s/g, '')}`}>{shop.supportPhone}</a>
              </li>
            )}
            {!shop?.address && !shop?.businessHours && <li className="muted">Địa chỉ cửa hàng đang được cập nhật.</li>}
          </ul>
          <p className="muted">Không tiện ghé? Đặt online, cửa hàng giao tận nơi trên toàn quốc.</p>
          <Link to="/shop" className="pill pill-dark pill-lg">
            Vào cửa hàng <ArrowRightIcon size={16} weight="bold" />
          </Link>
        </Reveal>
      </section>

      <SiteFooter shop={shop} />
    </div>
  )
}

/** Number that counts up once when it scrolls into view; it writes to the DOM directly, not to React state. */
function Stat({ value, label }: { value: number; label: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
      element.textContent = String(value)
      return
    }
    let frame = 0
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry?.isIntersecting) return
      observer.disconnect()
      const start = performance.now()
      const tick = (now: number) => {
        const progress = Math.min(1, (now - start) / 1100)
        element.textContent = String(Math.round(value * (1 - Math.pow(1 - progress, 3))))
        if (progress < 1) frame = requestAnimationFrame(tick)
      }
      frame = requestAnimationFrame(tick)
    })
    observer.observe(element)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [value])
  return (
    <div className="stat">
      <span ref={ref} className="stat-value">
        0
      </span>
      <span className="stat-label">{label}</span>
    </div>
  )
}

/** Top-level categories with the photo of their newest product and how many of the loaded products they hold. */
function categoryTiles(categories: { id: string; parentId: string | null; name: string }[], products: ProductCard[]) {
  return categories
    .filter((c) => !c.parentId)
    .map((c) => {
      const inCategory = products.filter((p) => p.categoryName === c.name)
      return { id: c.id, name: c.name, count: inCategory.length, imageUrl: inCategory.find((p) => p.imageUrl)?.imageUrl ?? null }
    })
    .sort((a, b) => Number(Boolean(b.imageUrl)) - Number(Boolean(a.imageUrl)) || b.count - a.count)
}
