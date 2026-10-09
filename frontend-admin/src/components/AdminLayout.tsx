import {
  BellIcon,
  CaretDownIcon,
  ListIcon,
  SignOutIcon,
  XIcon,
} from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ADMIN_NAV_GROUPS, DASHBOARD_NAV, getActiveNavGroup } from '../navigation'
import ThemeToggle from './ThemeToggle'

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0]!.toUpperCase())
    .join('')
}

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [isMobile, setIsMobile] = useState(() => window.matchMedia('(max-width: 767px)').matches)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    try {
      return localStorage.getItem('lemonadex.admin.sidebarCollapsed') === 'true'
    } catch {
      return false
    }
  })
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const sidebarRef = useRef<HTMLElement>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [avatarFailed, setAvatarFailed] = useState(false)

  useEffect(() => {
    setAvatarFailed(false)
  }, [user?.avatarUrl])
  const activeGroup = getActiveNavGroup(location.pathname)
  const [expandedGroups, setExpandedGroups] = useState<string[]>(() => [activeGroup?.id ?? 'products'])
  const DashboardIcon = DASHBOARD_NAV.icon
  const sidebarVisible = isMobile ? drawerOpen : !sidebarCollapsed
  // On desktop a collapsed sidebar becomes a narrow icon rail instead of disappearing.
  const rail = !isMobile && sidebarCollapsed
  const menuLabel = isMobile ? 'Mở menu' : sidebarCollapsed ? 'Mở rộng sidebar' : 'Thu gọn sidebar'
  // Rail mode: hovering (or focusing) a group icon pops its sub-links out beside the rail.
  const [flyout, setFlyout] = useState<{ id: string; top: number; left: number } | null>(null)
  const flyoutTimer = useRef<number>(undefined)

  const openFlyout = (id: string, anchor: HTMLElement) => {
    if (!rail) return
    window.clearTimeout(flyoutTimer.current)
    const rect = anchor.getBoundingClientRect()
    const items = ADMIN_NAV_GROUPS.find((group) => group.id === id)?.items.length ?? 0
    // Keep the panel inside the viewport: header + one row per link + padding.
    const height = 56 + items * 38 + 16
    setFlyout({ id, left: rect.right + 10, top: Math.max(8, Math.min(rect.top - 6, window.innerHeight - height - 8)) })
  }
  const closeFlyout = (delay = 160) => {
    window.clearTimeout(flyoutTimer.current)
    flyoutTimer.current = window.setTimeout(() => setFlyout(null), delay)
  }

  useEffect(() => () => window.clearTimeout(flyoutTimer.current), [])
  useEffect(() => {
    if (!rail) setFlyout(null)
  }, [rail])

  useEffect(() => {
    const media = window.matchMedia('(max-width: 767px)')
    const onChange = (event: MediaQueryListEvent) => {
      setIsMobile(event.matches)
      setDrawerOpen(false)
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    try {
      localStorage.setItem('lemonadex.admin.sidebarCollapsed', String(sidebarCollapsed))
    } catch {
      // The sidebar remains usable when storage is unavailable.
    }
  }, [sidebarCollapsed])

  const closeDrawer = () => {
    setDrawerOpen(false)
    menuButtonRef.current?.focus()
  }

  useEffect(() => {
    if (!isMobile || !drawerOpen) return
    const sidebar = sidebarRef.current
    sidebar?.querySelector<HTMLButtonElement>('.drawer-close')?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setDrawerOpen(false)
        menuButtonRef.current?.focus()
      } else if (event.key === 'Tab' && sidebar) {
        const focusable = Array.from(sidebar.querySelectorAll<HTMLElement>('button, a[href]'))
          .filter((element) => element.getClientRects().length > 0)
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [drawerOpen, isMobile])

  // Close the mobile drawer and account menu whenever the route changes.
  useEffect(() => {
    setDrawerOpen(false)
    setMenuOpen(false)
    setFlyout(null)
    const group = getActiveNavGroup(location.pathname)
    if (group) {
      setExpandedGroups((expanded) => expanded.includes(group.id) ? expanded : [...expanded, group.id])
    }
  }, [location.pathname])

  const toggleGroup = (id: string) => {
    setExpandedGroups((expanded) =>
      expanded.includes(id) ? expanded.filter((groupId) => groupId !== id) : [...expanded, id],
    )
  }

  return (
    <div className="shell">
      <header className="topbar">
        <button
          ref={menuButtonRef}
          type="button"
          className="icon-btn sidebar-toggle"
          onClick={() => isMobile ? setDrawerOpen((open) => !open) : setSidebarCollapsed((collapsed) => !collapsed)}
          aria-label={menuLabel}
          title={menuLabel}
          aria-expanded={sidebarVisible}
          aria-controls="admin-sidebar"
        >
          <ListIcon size={20} />
        </button>
        <Link to="/" className="brand">
          <span className="brand-name">LemonadeX</span>
          <span className="brand-tag">Admin</span>
        </Link>

        <div className="top-actions">
          <ThemeToggle />
          <button className="icon-btn" aria-label="Thông báo">
            <BellIcon size={18} />
          </button>
          <div className="account">
            <button className="account-btn" onClick={() => setMenuOpen((open) => !open)} aria-expanded={menuOpen}>
              {user?.avatarUrl && !avatarFailed ? (
                <img
                  className="avatar"
                  src={user.avatarUrl}
                  alt=""
                  onError={() => setAvatarFailed(true)}
                />
              ) : (
                <span className="avatar">{user ? initials(user.displayName) : ''}</span>
              )}
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
        {isMobile && drawerOpen && <div className="scrim" onClick={closeDrawer} aria-hidden />}
        <aside
          ref={sidebarRef}
          id="admin-sidebar"
          className={`sidebar${drawerOpen ? ' open' : ''}${rail ? ' rail' : ''}`}
          role={isMobile && drawerOpen ? 'dialog' : undefined}
          aria-modal={isMobile && drawerOpen ? true : undefined}
          aria-label="Menu quản trị"
        >
          <button type="button" className="icon-btn only-mobile drawer-close" onClick={closeDrawer} aria-label="Đóng menu">
            <XIcon size={18} />
          </button>
          <nav className="side-nav" aria-label="Điều hướng chính">
            <NavLink
              to={DASHBOARD_NAV.to}
              end
              className="side-link"
              onClick={() => setDrawerOpen(false)}
              aria-label={rail ? DASHBOARD_NAV.label : undefined}
              title={rail ? DASHBOARD_NAV.label : undefined}
            >
              {({ isActive }) => (
                <>
                  <DashboardIcon size={18} weight={isActive ? 'fill' : 'regular'} aria-hidden />
                  <span>{DASHBOARD_NAV.label}</span>
                </>
              )}
            </NavLink>
            {ADMIN_NAV_GROUPS.map((group) => {
              const expanded = expandedGroups.includes(group.id)
              const active = activeGroup?.id === group.id
              const Glyph = group.icon
              const panelId = `nav-group-${group.id}`
              const flyoutOpen = rail && flyout?.id === group.id
              return (
                <div
                  className="side-group"
                  key={group.id}
                  onMouseEnter={(event) => openFlyout(group.id, event.currentTarget)}
                  onMouseLeave={() => closeFlyout()}
                  onFocus={(event) => openFlyout(group.id, event.currentTarget)}
                  onBlur={(event) => {
                    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) closeFlyout(0)
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'Escape' && flyoutOpen) {
                      event.stopPropagation()
                      setFlyout(null)
                      event.currentTarget.querySelector<HTMLButtonElement>('.side-group-toggle')?.focus()
                    }
                  }}
                >
                  <button
                    type="button"
                    className={`side-link side-group-toggle${active ? ' current' : ''}${flyoutOpen ? ' hovered' : ''}`}
                    aria-expanded={rail ? flyoutOpen : expanded}
                    aria-controls={rail ? `${panelId}-flyout` : panelId}
                    aria-label={rail ? group.label : undefined}
                    onClick={() => {
                      // In the icon rail, a group icon reopens the sidebar with that group expanded.
                      if (rail) {
                        setSidebarCollapsed(false)
                        setExpandedGroups((ids) => ids.includes(group.id) ? ids : [...ids, group.id])
                      } else {
                        toggleGroup(group.id)
                      }
                    }}
                  >
                    <Glyph size={18} weight={active ? 'fill' : 'regular'} aria-hidden />
                    <span>{group.label}</span>
                    <CaretDownIcon size={14} className={`side-caret${expanded ? ' expanded' : ''}`} aria-hidden />
                  </button>
                  <div id={panelId} className="side-subnav" hidden={!expanded || rail}>
                    {group.items.map((item) => (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        end
                        className="side-link side-sub-link"
                        onClick={() => setDrawerOpen(false)}
                      >
                        {item.label}
                      </NavLink>
                    ))}
                  </div>
                  {flyoutOpen && (
                    <div
                      id={`${panelId}-flyout`}
                      className="side-flyout"
                      style={{ top: flyout.top, left: flyout.left }}
                      aria-label={group.label}
                      role="group"
                    >
                      <p className="side-flyout-title">{group.label}</p>
                      {group.items.map((item) => (
                        <NavLink
                          key={item.to}
                          to={item.to}
                          end
                          className="side-link side-sub-link"
                          onClick={() => setFlyout(null)}
                        >
                          {item.label}
                        </NavLink>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
          </nav>
        </aside>

        <main className="canvas">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
