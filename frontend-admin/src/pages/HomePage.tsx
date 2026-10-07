import {
  ArrowUpIcon,
  CheckCircleIcon,
  PackageIcon,
  PlusIcon,
  TagIcon,
  TruckIcon,
  type Icon,
} from '@phosphor-icons/react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { CategoryResponse, OrderResponse, Page, ProductResponse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner } from '../components/common'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'

/** How many recent orders the home page inspects for its counters. */
const RECENT_ORDERS = 50
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

interface Overview {
  productCount: number
  /** Real catalog photos for the setup-card artwork; empty until products have images. */
  photos: string[]
  categories: CategoryResponse[]
  orders: Page<OrderResponse>
}

async function loadOverview(): Promise<Overview> {
  const [products, categories, orders] = await Promise.all([
    api.get<Page<ProductResponse>>('/products', { page: 0, size: 8 }),
    api.get<CategoryResponse[]>('/categories'),
    api.get<Page<OrderResponse>>('/admin/orders', { page: 0, size: RECENT_ORDERS }),
  ])
  const photos = products.content.flatMap((product) => product.images.slice(0, 1))
  return { productCount: products.totalElements, photos, categories, orders }
}

function greeting(now = new Date()) {
  const hour = now.getHours()
  if (hour < 11) return 'Chào buổi sáng'
  if (hour < 18) return 'Chào buổi chiều'
  return 'Chào buổi tối'
}

/** Inline skeleton used while counters load, sized like the text it replaces. */
function Skel({ w = '4ch' }: { w?: string }) {
  return <span className="skel" style={{ width: w }} aria-hidden />
}

/**
 * Fanned photo stack with a floating placeholder card, mirroring the setup-card artwork.
 * Uses the store's own product photos when it has them, otherwise seeded placeholders.
 */
function StackArt({ photos, seeds, icon: Glyph }: { photos: (string | undefined)[]; seeds: [string, string]; icon: Icon }) {
  const src = (index: 0 | 1) => photos[index] ?? `https://picsum.photos/seed/${seeds[index]}/240/300`
  return (
    <div className="stack-art" aria-hidden>
      <img className="stack-photo left" src={src(0)} alt="" loading="lazy" />
      <img className="stack-photo right" src={src(1)} alt="" loading="lazy" />
      <div className="stack-card">
        <div className="stack-slot">
          <Glyph size={28} weight="regular" />
        </div>
        <div className="stack-bar" />
      </div>
    </div>
  )
}

function Tile({ title, done, children }: { title: string; done?: boolean; children: ReactNode }) {
  return (
    <div className="tile">
      <h3 className="tile-title">
        {done && <CheckCircleIcon size={18} weight="fill" className="tile-check" aria-label="Đã hoàn tất" />}
        {title}
      </h3>
      {children}
    </div>
  )
}

export default function HomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const overview = useAsync(loadOverview, [])
  const [command, setCommand] = useState('')

  const data = overview.data
  const recent = data?.orders.content ?? []
  const placed = recent.filter((order) => order.status === 'PLACED')
  const shipping = recent.filter((order) => order.status === 'SHIPPED')
  const pendingValue = placed.reduce((sum, order) => sum + order.total, 0)
  const hasProducts = (data?.productCount ?? 0) > 0
  const hasCategories = (data?.categories.length ?? 0) > 0
  const photos = data?.photos ?? []

  const runCommand = (event: FormEvent) => {
    event.preventDefault()
    const term = command.trim()
    if (!term) return
    navigate(UUID.test(term) ? `/orders/${term}` : `/products?search=${encodeURIComponent(term)}`)
  }

  return (
    <div className="page home">
      <div className="home-head">
        <h1>
          {greeting()}
          {user ? `, ${user.displayName}` : ''}. Bắt đầu thôi.
        </h1>
        <Link to="/orders" className="head-link">
          Đơn chờ xử lý: <strong>{data ? placed.length : <Skel w="2ch" />}</strong>
        </Link>
      </div>

      <form className="command" onSubmit={runCommand}>
        <label htmlFor="command" className="sr-only">
          Tìm sản phẩm hoặc mở đơn theo mã
        </label>
        <input
          id="command"
          placeholder="Tìm sản phẩm, hoặc dán mã đơn hàng để mở..."
          value={command}
          onChange={(event) => setCommand(event.target.value)}
          autoComplete="off"
        />
        <div className="command-row">
          <span className="command-mark" aria-hidden>
            L
          </span>
          <div className="command-actions">
            <Link to="/products/new" className="icon-btn ghost" aria-label="Thêm sản phẩm">
              <PlusIcon size={16} />
            </Link>
            <button className="icon-btn round" disabled={!command.trim()} aria-label="Thực hiện">
              <ArrowUpIcon size={14} weight="bold" />
            </button>
          </div>
        </div>
      </form>

      <ErrorBanner error={overview.error} />

      <section className="panel">
        <h2 className="panel-title">LemonadeX Clothes</h2>

        <div className="feature-grid">
          <article className="feature">
            <StackArt photos={[photos[0], photos[1]]} seeds={['lemonadex-linen-shirt', 'lemonadex-denim-rack']} icon={TagIcon} />
            <h3>{hasProducts ? 'Thêm sản phẩm mới' : 'Thêm sản phẩm đầu tiên'}</h3>
            <p>
              {data === undefined ? (
                <Skel w="22ch" />
              ) : hasProducts ? (
                <>Cửa hàng đang bán {data.productCount} sản phẩm. Thêm mẫu mới với size, màu và giá cho từng SKU.</>
              ) : (
                <>
                  Bắt đầu bằng một sản phẩm và vài thông tin chính. Chưa có danh mục?{' '}
                  <Link to="/categories">Tạo danh mục trước</Link>
                </>
              )}
            </p>
            <div className="actions">
              <Link to="/products/new" className="btn btn-primary">
                Thêm sản phẩm
              </Link>
              <Link to="/products" className="btn btn-plain">
                Xem danh sách
              </Link>
            </div>
          </article>

          <article className="feature">
            <StackArt photos={[photos[2], photos[3]]} seeds={['lemonadex-parcel-desk', 'lemonadex-folded-tees']} icon={PackageIcon} />
            <h3>Xử lý đơn hàng mới</h3>
            <p>
              {data === undefined ? (
                <Skel w="26ch" />
              ) : placed.length > 0 ? (
                <>
                  {placed.length} đơn đang chờ xác nhận, tổng {formatMoney(pendingValue)}. Xác nhận sớm để giữ
                  hàng cho khách.
                </>
              ) : (
                <>Không có đơn nào chờ xác nhận. Đơn mới từ khách sẽ hiện ở đây.</>
              )}
            </p>
            <div className="actions">
              <Link to="/orders" className="btn btn-secondary">
                Xem đơn hàng
              </Link>
            </div>
          </article>
        </div>

        <div className="tile-grid">
          <Tile title="Thanh toán khi nhận hàng" done>
            <div className="chips">
              <span className="chip chip-dark">COD</span>
              <span className="chip">VND</span>
            </div>
            <p className="tile-note">Đang bật. Đơn chuyển sang đã thanh toán khi giao thành công.</p>
          </Tile>

          <Tile title="Phí vận chuyển">
            <div className="chips">
              <span className="chip chip-icon">
                <TruckIcon size={16} /> Miễn phí
              </span>
            </div>
            <p className="tile-note">Mọi đơn đang áp dụng phí giao hàng 0đ.</p>
          </Tile>

          <Tile title={hasCategories ? 'Đã có danh mục' : 'Tạo danh mục'} done={hasCategories}>
            <div className="field-box">
              {data === undefined ? (
                <Skel w="12ch" />
              ) : hasCategories ? (
                <span className="truncate">{data.categories.map((category) => category.name).join(', ')}</span>
              ) : (
                <span className="muted">Chưa có danh mục nào</span>
              )}
            </div>
            <Link to="/categories" className="btn btn-secondary btn-sm">
              {hasCategories ? 'Quản lý danh mục' : 'Tạo danh mục'}
            </Link>
          </Tile>
        </div>

        <h2 className="panel-subtitle">Vận hành kho và giao hàng</h2>
        <div className="media-grid">
          <article className="media-card">
            <img src="https://picsum.photos/seed/lemonadex-stockroom/320/240" alt="Kệ hàng trong kho" loading="lazy" />
            <div>
              <h3>Kiểm tra tồn kho</h3>
              <p>Điều chỉnh số lượng theo SKU và ghi lại lý do cho mỗi lần nhập, xuất.</p>
              <Link to="/inventory" className="btn btn-secondary btn-sm">
                Mở tồn kho
              </Link>
            </div>
          </article>
          <article className="media-card">
            <img src="https://picsum.photos/seed/lemonadex-courier/320/240" alt="Đơn hàng đang được giao" loading="lazy" />
            <div>
              <h3>Theo dõi đơn đang giao</h3>
              <p>
                {data === undefined ? (
                  <Skel w="20ch" />
                ) : shipping.length > 0 ? (
                  <>{shipping.length} đơn đang trên đường. Đánh dấu đã giao khi thu tiền COD xong.</>
                ) : (
                  <>Chưa có đơn nào đang giao. Đơn đã xác nhận sẽ chuyển sang bước giao hàng.</>
                )}
              </p>
              <Link to="/orders?status=SHIPPED" className="btn btn-secondary btn-sm">
                Xem đơn giao
              </Link>
            </div>
          </article>
        </div>
      </section>

      {data && data.orders.totalElements > RECENT_ORDERS && (
        <p className="footnote">Số liệu đơn hàng tính trên {RECENT_ORDERS} đơn gần nhất.</p>
      )}
    </div>
  )
}
