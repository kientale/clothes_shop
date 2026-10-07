import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'

export default function Layout() {
  const { user, logout } = useAuth()
  const { itemCount } = useCart()
  const navigate = useNavigate()

  return (
    <div className="app">
      <header className="topbar">
        <div className="container topbar-inner">
          <Link to="/" className="brand">
            Lemonade<span>X</span>
          </Link>
          <nav className="nav">
            <NavLink to="/" end>
              Cửa hàng
            </NavLink>
            {user && <NavLink to="/orders">Đơn hàng</NavLink>}
            <NavLink to="/cart" className="cart-link">
              Giỏ hàng{itemCount > 0 && <span className="count">{itemCount}</span>}
            </NavLink>
            {user ? (
              <>
                <span className="muted user-name">{user.displayName}</span>
                <button
                  className="btn btn-ghost"
                  onClick={() => {
                    logout()
                    navigate('/')
                  }}
                >
                  Đăng xuất
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login">Đăng nhập</NavLink>
                <Link to="/register" className="btn btn-primary">
                  Đăng ký
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="container main">
        <Outlet />
      </main>
      <footer className="footer">
        <div className="container muted">© LemonadeX Clothes · Thanh toán khi nhận hàng (COD)</div>
      </footer>
    </div>
  )
}
