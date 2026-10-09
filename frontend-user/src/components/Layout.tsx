import { HandbagIcon, HeartIcon, MagnifyingGlassIcon, MoonIcon, SignOutIcon, SunIcon, UserIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { store } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'
import { useWishlist } from '../wishlist/WishlistContext'
import { ContactButtons } from './ContactButtons'
import { EmailVerifyBanner } from './EmailVerifyBanner'
import { NAV } from './nav'
import { NotificationBell } from './NotificationBell'
import { SearchBox } from './SearchBox'
import { SiteFooter } from './SiteFooter'
import { useAsync } from '../hooks'


function useTheme() {
  const [theme, setTheme] = useState(() => (document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'))
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('lemonadex.theme', theme)
    } catch {
      // The choice simply is not remembered.
    }
  }, [theme])
  return [theme, () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))] as const
}

export default function Layout() {
  const { user, logout } = useAuth()
  const { itemCount, clear: clearCart } = useCart()
  const wishlist = useWishlist()
  const isCustomer = !!user?.roles.includes('CUSTOMER')
  const navigate = useNavigate()
  const location = useLocation()
  const [theme, toggleTheme] = useTheme()
  const [searching, setSearching] = useState(false)
  const closeSearch = useCallback(() => setSearching(false), [])
  const config = useAsync(() => store.configuration(), [])
  const shop = config.data?.store

  // Each route starts at the top; the search box closes on navigation.
  useEffect(() => {
    window.scrollTo({ top: 0 })
    setSearching(false)
  }, [location.pathname])

  const navActive = (search: string) => location.pathname === '/shop/products' && location.search.replace(/^\?/, '') === search

  return (
    <div className="app">
      {config.data?.general?.maintenanceMode && (
        <div className="notice" role="status">
          {config.data.general?.maintenanceMessage ?? 'Cửa hàng đang bảo trì, tạm thời chưa nhận đơn mới.'}
        </div>
      )}
      <EmailVerifyBanner />
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/shop" className="wordmark" aria-label="LemonadeX - Trang chủ cửa hàng">
            LemonadeX
          </Link>

          <nav className="main-nav" aria-label="Danh mục chính">
            <Link to="/">Giới thiệu</Link>
            {NAV.map((item) => (
              <Link key={item.label} to={item.to} className={navActive(item.search) ? 'active' : undefined} aria-current={navActive(item.search) ? 'page' : undefined}>
                {item.label}
              </Link>
            ))}
            <NavLink to="/shop/collections">Bộ sưu tập</NavLink>
            <NavLink to="/shop/articles">Tạp chí</NavLink>
          </nav>

          <div className="header-actions">
            {searching ? (
              <SearchBox onClose={closeSearch} />
            ) : (
              <button type="button" className="icon-btn" onClick={() => setSearching(true)} aria-label="Tìm kiếm">
                <MagnifyingGlassIcon size={20} />
              </button>
            )}
            <button type="button" className="icon-btn" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Giao diện sáng' : 'Giao diện tối'}>
              {theme === 'dark' ? <SunIcon size={20} /> : <MoonIcon size={20} />}
            </button>
            {isCustomer && <NotificationBell />}
            <NavLink to="/shop/wishlist" className="icon-btn wish-link" aria-label={`Yêu thích, ${wishlist.count} sản phẩm`} title="Yêu thích">
              <HeartIcon size={20} />
              {wishlist.count > 0 && <span className="badge-count">{wishlist.count > 99 ? '99+' : wishlist.count}</span>}
            </NavLink>
            {user ? (
              <>
                <NavLink to={isCustomer ? '/shop/account' : '/shop/orders'} className="account-link" title="Tài khoản của tôi">
                  <UserIcon size={18} aria-hidden />
                  <span className="user-name">{user.displayName}</span>
                </NavLink>
                <button
                  type="button"
                  className="icon-btn"
                  aria-label="Đăng xuất"
                  title="Đăng xuất"
                  onClick={() => {
                    // Signed out first, so emptying this device's cart is not saved over the customer's saved cart.
                    logout()
                    clearCart()
                    navigate('/')
                  }}
                >
                  <SignOutIcon size={20} />
                </button>
              </>
            ) : (
              <Link to="/shop/login" className="account-link" state={{ from: location.pathname + location.search }}>
                <UserIcon size={18} aria-hidden />
                <span>Đăng nhập</span>
              </Link>
            )}
            <NavLink to="/shop/cart" className="cart-btn" aria-label={`Giỏ hàng, ${itemCount} sản phẩm`}>
              <HandbagIcon size={20} />
              {itemCount > 0 && <span className="cart-count">{itemCount}</span>}
            </NavLink>
          </div>
        </div>
      </header>

      <main className="site-main">
        <Outlet />
      </main>

      <SiteFooter shop={shop} />
      <ContactButtons shop={shop} />
    </div>
  )
}
