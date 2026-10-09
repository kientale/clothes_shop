import { Link } from 'react-router-dom'
import type { StoreConfiguration } from '../api/types'
import { POLICY_LABEL } from '../format'
import { NAV } from './nav'

/** Footer shared by the intro page and the shop; contact details come from the store settings. */
export function SiteFooter({ shop }: { shop: StoreConfiguration['store'] | undefined }) {
  return (
    <footer className="site-footer">
      <div className="container footer-grid">
        <div className="footer-brand">
          <Link to="/" className="wordmark wordmark-lg">
            LemonadeX
          </Link>
          <p className="muted">Thời trang hằng ngày cho nam, nữ và trẻ em. Đặt online, giao tận nơi.</p>
        </div>
        <nav className="footer-col" aria-label="Mua sắm">
          <h2>Mua sắm</h2>
          {NAV.map((item) => (
            <Link key={item.label} to={item.to}>
              {item.label}
            </Link>
          ))}
        </nav>
        <nav className="footer-col" aria-label="Hỗ trợ">
          <h2>Hỗ trợ</h2>
          {(['SHIPPING', 'RETURN_EXCHANGE', 'PAYMENT', 'PRIVACY'] as const).map((type) => (
            <Link key={type} to={`/policies/${type}`}>
              {POLICY_LABEL[type]}
            </Link>
          ))}
          <Link to="/shop/track">Tra cứu đơn hàng</Link>
          <Link to="/shop/articles">Tạp chí & Lookbook</Link>
        </nav>
        <div className="footer-col">
          <h2>Liên hệ</h2>
          {shop?.address && <p>{shop.address}</p>}
          {shop?.supportPhone && <a href={`tel:${shop.supportPhone.replace(/\s/g, '')}`}>{shop.supportPhone}</a>}
          {shop?.supportEmail && <a href={`mailto:${shop.supportEmail}`}>{shop.supportEmail}</a>}
          {shop?.businessHours && <p className="muted">{shop.businessHours}</p>}
          {!shop?.address && !shop?.supportPhone && !shop?.supportEmail && <p className="muted">Thông tin liên hệ đang được cập nhật.</p>}
        </div>
      </div>
      <div className="container footer-base muted">
        © {new Date().getFullYear()} {shop?.storeName ?? 'LemonadeX'}
      </div>
    </footer>
  )
}
