import {
  BellIcon,
  FoldersIcon,
  GearIcon,
  HouseIcon,
  ListIcon,
  MagnifyingGlassIcon,
  SignOutIcon,
  TagIcon,
  TrayIcon,
  WarehouseIcon,
  XIcon,
  type Icon,
} from '@phosphor-icons/react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

interface NavItem {
  to: string
  label: string
  icon: Icon
  end?: boolean
}

const NAV: NavItem[] = [
  { to: '/', label: 'Trang chủ', icon: HouseIcon, end: true },
  { to: '/orders', label: 'Đơn hàng', icon: TrayIcon },
  { to: '/products', label: 'Sản phẩm', icon: TagIcon },
  { to: '/categories', label: 'Danh mục', icon: FoldersIcon },
  { to: '/inventory', label: 'Tồn kho', icon: WarehouseIcon },
]

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

function SideNavLink({ item }: { item: NavItem }) {
  const { icon: Glyph } = item
  return (
    <NavLink to={item.to} end={item.end} className="side-link">
      {({ isActive }) => (
        <>
          <Glyph size={18} weight={isActive ? 'fill' : 'regular'} aria-hidden />
          <span>{item.label}</span>
        </>
      )}
    </NavLink>
  )
}

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [query, setQuery] = useState('')

  // Close the mobile drawer and account menu whenever the route changes.
  useEffect(() => {
    setDrawerOpen(false)
    setMenuOpen(false)
  }, [location.pathname])

  const search = (event: FormEvent) => {
    event.preventDefault()
    const term = query.trim()
    if (term) navigate(`/products?search=${encodeURIComponent(term)}`)
  }

  return (
    <div className="shell">
      <header className="topbar">
        <button className="icon-btn only-mobile" onClick={() => setDrawerOpen(true)} aria-label="Mở menu">
          <ListIcon size={20} />
        </button>
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden>
            L
          </span>
          <span className="brand-name">LemonadeX</span>
          <span className="brand-tag">Admin</span>
        </Link>

        <form className="top-search" onSubmit={search} role="search">
          <MagnifyingGlassIcon size={16} aria-hidden />
          <input
            type="search"
            placeholder="Tìm sản phẩm"
            aria-label="Tìm sản phẩm"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </form>

        <div className="top-actions">
          <button className="icon-btn" aria-label="Thông báo">
            <BellIcon size={18} />
          </button>
          <div className="account">
            <button className="account-btn" onClick={() => setMenuOpen((open) => !open)} aria-expanded={menuOpen}>
              <span className="avatar">{user ? initials(user.displayName) : ''}</span>
              <span className="account-name">{user?.displayName}</span>
            </button>
            {menuOpen && (
              <div className="account-menu" role="menu">
                <div className="account-email">{user?.email}</div>
                <button
                  role="menuitem"
                  onClick={() => {
                    logout()
                    navigate('/login')
                  }}
                >
                  <SignOutIcon size={16} /> Đăng xuất
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className="body">
        {drawerOpen && <div className="scrim" onClick={() => setDrawerOpen(false)} aria-hidden />}
        <aside className={`sidebar ${drawerOpen ? 'open' : ''}`}>
          <button className="icon-btn only-mobile drawer-close" onClick={() => setDrawerOpen(false)} aria-label="Đóng menu">
            <XIcon size={18} />
          </button>
          <nav className="side-nav" aria-label="Điều hướng chính">
            {NAV.map((item) => (
              <SideNavLink key={item.to} item={item} />
            ))}
          </nav>
          <nav className="side-nav side-bottom" aria-label="Cài đặt">
            <SideNavLink item={{ to: '/settings', label: 'Cài đặt', icon: GearIcon }} />
          </nav>
        </aside>

        <main className="canvas">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
