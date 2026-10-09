import { ArrowRightIcon, ArrowUpIcon, CheckCircleIcon, PlusIcon, TruckIcon } from '@phosphor-icons/react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { accessErrorMessage } from '../api/access'
import { api } from '../api/client'
import { catalog } from '../api/catalog'
import { ORDER_STATUS, inventory, orders } from '../api/commerce'
import { reviews } from '../api/marketing'
import { reports } from '../api/reports'
import { BarChart, StatePill, countOf, isoDay, rangeQuery, useCan, vndOf, whenOf } from '../components/kit'
import { useAsync } from '../hooks'
import { bucketLabel } from './reports/ReportFilters'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
// Same photo set as the storefront homepage, so the admin opens on the brand's own imagery.
const HERO_IMAGE = 'https://api.getlayers.ai/storage/v1/object/public/public/assets/baseline-88535e4000/4.webp'

interface StoreConfiguration {
  ordersEnabled: boolean
  paymentMethods: { code: string; name: string }[]
  shippingMethods: { code: string; name: string; baseFee: number }[]
}


/** Inline skeleton used while counters load, sized like the text it replaces. */
function Skel({ w = '4ch' }: { w?: string }) {
  return <span className="skel" style={{ width: w }} aria-hidden />
}

/**
 * Two fanned photos, like the tilted court cards on the storefront.
 * Uses the store's own product photos when it has them, otherwise seeded placeholders.
 */
function StackArt({ photos, seeds }: { photos: (string | undefined)[]; seeds: [string, string] }) {
  const src = (index: 0 | 1) => photos[index] ?? `https://picsum.photos/seed/${seeds[index]}/240/300`
  return (
    <div className="stack-art" aria-hidden>
      <img className="stack-photo left" src={src(0)} alt="" loading="lazy" />
      <img className="stack-photo right" src={src(1)} alt="" loading="lazy" />
    </div>
  )
}

function Tile({ title, done, wide, children }: { title: string; done?: boolean; wide?: boolean; children: ReactNode }) {
  return (
    <div className={wide ? 'tile tile-wide' : 'tile'}>
      <h3 className="tile-title">
        {done && <CheckCircleIcon size={18} weight="fill" className="tile-check" aria-label="Đã hoàn tất" />}
        {title}
      </h3>
      {children}
    </div>
  )
}

function Stat({ to, label, value, note }: { to: string; label: string; value: ReactNode; note?: ReactNode }) {
  return (
    <Link to={to} className="kpi kpi-link">
      <span className="kpi-label">
        {label} <ArrowRightIcon size={12} weight="bold" aria-hidden />
      </span>
      <span className="kpi-value">{value}</span>
      {note && <span className="kpi-note">{note}</span>}
    </Link>
  )
}

export default function HomePage() {
  const navigate = useNavigate()
  const [command, setCommand] = useState('')
  const canOrders = useCan('ORDER_READ')
  const canProducts = useCan('PRODUCT_READ')
  const canInventory = useCan('INVENTORY_READ')
  const canReviews = useCan('REVIEW_READ')
  const canRevenue = useCan('REPORT_REVENUE_READ')

  const data = useAsync(async () => {
    // Each block loads on its own: a failure leaves that block empty and surfaces the first error
    // in a banner, instead of blanking the whole dashboard.
    const errors: unknown[] = []
    const soft = <T,>(enabled: boolean, load: () => Promise<T>) =>
      enabled
        ? load().catch((error: unknown) => {
            errors.push(error)
            return undefined
          })
        : Promise.resolve(undefined)
    const last30 = rangeQuery({ from: isoDay(-29), to: isoDay(0) })
    const [placed, recent, products, options, lowStock, pendingReviews, revenue, config] = await Promise.all([
      soft(canOrders, () => orders.list({ status: 'PLACED', page: 0, size: 1 })),
      soft(canOrders, () => orders.list({ page: 0, size: 6 })),
      soft(canProducts, () => catalog.products.list({ page: 0, size: 4 })),
      soft(canProducts, () => catalog.options()),
      soft(canInventory, () => inventory.list({ lowStock: true, page: 0, size: 1 })),
      soft(canReviews, () => reviews.list({ status: 'PENDING', page: 0, size: 1 })),
      soft(canRevenue, () => reports.revenue({ ...last30, groupBy: 'DAY', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone })),
      soft(true, () => api.get<StoreConfiguration>('/store/configuration')),
    ])
    return { placed, recent, products, options, lowStock, pendingReviews, revenue, config, error: errors[0] }
  }, [canOrders, canProducts, canInventory, canReviews, canRevenue])

  const d = data.data
  const loading = !d
  const photos = d?.products?.content.flatMap((p) => p.images.slice(0, 1).map((i) => i.url)) ?? []
  const productCount = d?.products?.totalElements ?? 0
  const categories = d?.options?.categories ?? []
  const placedCount = d?.placed?.totalElements ?? 0

  const runCommand = (event: FormEvent) => {
    event.preventDefault()
    const term = command.trim()
    if (!term) return
    if (UUID.test(term)) navigate(`/orders/${term}`)
    else if (/^[A-Z]{1,10}-/i.test(term) || /^\+?\d{8,}$/.test(term)) navigate(`/orders?search=${encodeURIComponent(term)}`)
    else navigate(`/products?search=${encodeURIComponent(term)}`)
  }

  return (
    <div className="page home">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero-plate" aria-hidden>
          <img src={HERO_IMAGE} alt="" decoding="async" />
        </div>

        <div className="home-hero-foot">
          <div className="home-search">
            <h1 id="home-title" className="home-title">
              Chúc một ngày đắt hàng
            </h1>
            <form className="command" onSubmit={runCommand}>
              <label htmlFor="command" className="sr-only">
                Tìm sản phẩm, mã đơn hoặc số điện thoại
              </label>
              <input
                id="command"
                placeholder="Tìm sản phẩm, mã đơn hoặc số điện thoại khách..."
                value={command}
                onChange={(event) => setCommand(event.target.value)}
                autoComplete="off"
              />
              <Link to="/products" className="icon-btn ghost" aria-label="Quản lý sản phẩm" title="Quản lý sản phẩm">
                <PlusIcon size={16} />
              </Link>
              <button className="icon-btn round" disabled={!command.trim()} aria-label="Tìm">
                <ArrowUpIcon size={16} weight="bold" />
              </button>
            </form>
          </div>

          {canOrders && (
            <Link to="/orders?status=PLACED" className="home-stat">
              <span className="home-stat-value">{loading ? <Skel w="2ch" /> : placedCount}</span>
              <span className="home-stat-label">
                Đơn chờ xác nhận <ArrowRightIcon size={12} weight="bold" aria-hidden />
              </span>
            </Link>
          )}
        </div>
      </section>

      {d?.error ? (
        <div className="banner banner-error" role="alert">
          {accessErrorMessage(d.error)}{' '}
          <button type="button" className="link-btn" onClick={data.reload}>
            Thử lại
          </button>
        </div>
      ) : null}

      <div className="kpis home-kpis">
        {canRevenue && (
          <Stat to="/reports/revenue" label="Thực nhận 30 ngày" value={loading ? <Skel w="8ch" /> : vndOf(d?.revenue?.netReceivedAmount)} note={d?.revenue && `Đã thu ${vndOf(d.revenue.paidAmount)}`} />
        )}
        {canProducts && <Stat to="/products" label="Sản phẩm" value={loading ? <Skel /> : countOf(productCount)} note={`${categories.length} danh mục`} />}
        {canInventory && (
          <Stat to="/inventory" label="SKU sắp hết hàng" value={loading ? <Skel w="2ch" /> : countOf(d?.lowStock?.totalElements ?? 0)} note="Còn 5 sản phẩm trở xuống" />
        )}
        {canReviews && <Stat to="/reviews" label="Đánh giá chờ duyệt" value={loading ? <Skel w="2ch" /> : countOf(d?.pendingReviews?.totalElements ?? 0)} />}
      </div>

      <div className="home-grid">
        <section className="panel">
          <div className="section-head">
            <h2 className="panel-title flush">Đơn hàng mới</h2>
            <Link to="/orders" className="btn btn-plain btn-sm">
              Tất cả đơn <ArrowRightIcon size={13} />
            </Link>
          </div>
          {loading ? (
            Array.from({ length: 4 }, (_, i) => <span key={i} className="skel skel-row" />)
          ) : d?.recent?.content.length ? (
            <ul className="recent-orders">
              {d.recent.content.map((o) => (
                <li key={o.id}>
                  <Link to={`/orders/${o.id}`}>
                    <span className="person-text">
                      <span className="mono-strong">{o.orderCode}</span>
                      <span className="person-sub">
                        {o.recipientName} | {whenOf(o.placedAt)}
                      </span>
                    </span>
                    <strong className="money">{vndOf(o.totalAmount)}</strong>
                    <StatePill map={ORDER_STATUS} value={o.orderStatus} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small">{canOrders ? 'Chưa có đơn hàng nào.' : 'Bạn chưa có quyền xem đơn hàng.'}</p>
          )}
        </section>

        <section className="panel">
          <div className="section-head">
            <h2 className="panel-title flush">Thực nhận 30 ngày</h2>
            {canRevenue && (
              <Link to="/reports/revenue" className="btn btn-plain btn-sm">
                Báo cáo <ArrowRightIcon size={13} />
              </Link>
            )}
          </div>
          {!canRevenue ? (
            <p className="muted small">Bạn chưa có quyền xem báo cáo doanh thu.</p>
          ) : d?.revenue ? (
            <BarChart
              label="Tiền thực nhận theo ngày trong 30 ngày qua"
              format={vndOf}
              points={(d.revenue.series ?? []).map((p) => ({ key: p.bucketStart, label: bucketLabel(p.bucketStart, 'DAY'), value: p.netReceivedAmount }))}
            />
          ) : (
            <div className="chart-skel skel" />
          )}
        </section>
      </div>

      <section className="panel">
        <h2 className="panel-title">LemonadeX Clothes</h2>

        <div className="feature-grid">
          <article className="feature">
            <StackArt photos={[photos[0], photos[1]]} seeds={['lemonadex-linen-shirt', 'lemonadex-denim-rack']} />
            <h3>{productCount > 0 ? 'Thêm sản phẩm mới' : 'Thêm sản phẩm đầu tiên'}</h3>
            <p>
              {loading ? (
                <Skel w="22ch" />
              ) : productCount > 0 ? (
                <>Cửa hàng có {productCount} sản phẩm. Thêm mẫu mới, rồi tạo biến thể size và màu cho từng SKU.</>
              ) : (
                <>
                  Bắt đầu bằng một sản phẩm và vài thông tin chính. Chưa có danh mục? <Link to="/categories">Tạo danh mục trước</Link>
                </>
              )}
            </p>
            <div className="actions">
              <Link to="/products" className="btn btn-primary">
                Quản lý sản phẩm
              </Link>
              <Link to="/product-variants" className="btn btn-plain">
                Biến thể
              </Link>
            </div>
          </article>

          <article className="feature">
            <StackArt photos={[photos[2], photos[3]]} seeds={['lemonadex-parcel-desk', 'lemonadex-folded-tees']} />
            <h3>Xử lý đơn hàng mới</h3>
            <p>
              {loading ? (
                <Skel w="26ch" />
              ) : placedCount > 0 ? (
                <>{placedCount} đơn đang chờ xác nhận. Xác nhận sớm để giữ hàng và tạo vận đơn cho khách.</>
              ) : (
                <>Không có đơn nào chờ xác nhận. Đơn mới từ khách sẽ hiện ở đây.</>
              )}
            </p>
            <div className="actions">
              <Link to="/orders?status=PLACED" className="btn btn-secondary">
                Xem đơn chờ xác nhận
              </Link>
            </div>
          </article>
        </div>

        <div className="tile-grid">
          <Tile title="Thanh toán" done={(d?.config?.paymentMethods?.length ?? 0) > 0}>
            <div className="chips">
              {loading ? (
                <Skel w="10ch" />
              ) : d?.config?.paymentMethods?.length ? (
                d.config.paymentMethods.map((m, i) => (
                  <span key={m.code} className={`chip${i === 0 ? ' chip-dark' : ''}`}>
                    {m.name}
                  </span>
                ))
              ) : (
                <span className="chip chip-muted">Chưa bật phương thức nào</span>
              )}
            </div>
            <Link to="/settings/payments" className="btn btn-secondary btn-sm">
              Cài đặt thanh toán
            </Link>
          </Tile>

          <Tile title="Giao hàng" done={(d?.config?.shippingMethods?.length ?? 0) > 0}>
            <div className="chips">
              {loading ? (
                <Skel w="10ch" />
              ) : d?.config?.shippingMethods?.length ? (
                d.config.shippingMethods.slice(0, 2).map((m) => (
                  <span key={m.code} className="chip chip-icon">
                    <TruckIcon size={16} /> {m.name}
                  </span>
                ))
              ) : (
                <span className="chip chip-muted">Phí nhập tay theo đơn</span>
              )}
            </div>
            <Link to="/settings/shipping" className="btn btn-secondary btn-sm">
              Cài đặt giao hàng
            </Link>
          </Tile>

          <Tile title={categories.length > 0 ? 'Danh mục' : 'Tạo danh mục'} done={categories.length > 0} wide>
            <div className="chips">
              {loading ? (
                <Skel w="12ch" />
              ) : categories.length > 0 ? (
                <>
                  {categories.slice(0, 8).map((c) => (
                    <span key={c.id} className="chip">
                      {c.name}
                    </span>
                  ))}
                  {categories.length > 8 && <span className="chip chip-muted">+{categories.length - 8} danh mục</span>}
                </>
              ) : (
                <span className="chip chip-muted">Chưa có danh mục nào</span>
              )}
            </div>
            <Link to="/categories" className="btn btn-secondary btn-sm">
              {categories.length > 0 ? 'Quản lý danh mục' : 'Tạo danh mục'}
            </Link>
          </Tile>
        </div>

        <h2 className="panel-subtitle">Vận hành kho và giao hàng</h2>
        <div className="media-grid">
          <article className="media-card">
            <img src="https://picsum.photos/seed/lemonadex-stockroom/320/240" alt="Kệ hàng trong kho" loading="lazy" />
            <div>
              <h3>Kiểm tra tồn kho</h3>
              <p>Nhập, xuất và kiểm kê theo SKU. Mỗi lần thay đổi được ghi vào sổ kho kèm lý do.</p>
              <Link to="/inventory" className="btn btn-secondary btn-sm">
                Mở tồn kho
              </Link>
            </div>
          </article>
          <article className="media-card">
            <img src="https://picsum.photos/seed/lemonadex-courier/320/240" alt="Đơn hàng đang được giao" loading="lazy" />
            <div>
              <h3>Theo dõi vận đơn</h3>
              <p>Cập nhật trạng thái theo hãng vận chuyển. Đơn tự chuyển sang đã giao khi vận đơn giao thành công.</p>
              <Link to="/shipments" className="btn btn-secondary btn-sm">
                Xem giao hàng
              </Link>
            </div>
          </article>
        </div>
      </section>
    </div>
  )
}
