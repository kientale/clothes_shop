import { BellIcon } from '@phosphor-icons/react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { me } from '../api/store'
import type { InboxItem } from '../api/types'
import { formatDate } from '../format'

/** Bell with the unread count and the five latest notifications; checks again every minute while the tab is open. */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<InboxItem[]>([])
  const [unread, setUnread] = useState(0)
  const box = useRef<HTMLDivElement>(null)
  const location = useLocation()

  const load = useCallback(async () => {
    try {
      const [latest, unreadPage] = await Promise.all([me.inbox({ page: 0, size: 5 }), me.inbox({ read: false, page: 0, size: 1 })])
      setItems(latest.content)
      setUnread(unreadPage.totalElements)
    } catch {
      // The bell stays as it was; the inbox page shows errors.
    }
  }, [])

  useEffect(() => {
    void load()
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load()
    }, 60_000)
    return () => window.clearInterval(timer)
  }, [load])

  useEffect(() => setOpen(false), [location.pathname])

  useEffect(() => {
    if (!open) return
    const close = (event: PointerEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  const read = async (item: InboxItem) => {
    if (item.read) return
    setItems((list) => list.map((i) => (i.id === item.id ? { ...i, read: true } : i)))
    setUnread((n) => Math.max(0, n - 1))
    try {
      await me.markRead(item.id)
    } catch {
      void load()
    }
  }

  return (
    <div className="bell" ref={box}>
      <button
        type="button"
        className="icon-btn"
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'}
        onClick={() => {
          setOpen((v) => !v)
          if (!open) void load()
        }}
      >
        <BellIcon size={20} />
        {unread > 0 && <span className="badge-count">{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className="bell-panel" role="dialog" aria-label="Thông báo">
          <div className="bell-head">
            <strong>Thông báo</strong>
            {unread > 0 && <span className="muted small">{unread} chưa đọc</span>}
          </div>
          {items.length === 0 ? (
            <p className="bell-empty muted small">Chưa có thông báo nào. Khuyến mãi và cập nhật đơn hàng sẽ hiện ở đây.</p>
          ) : (
            <ul className="bell-list">
              {items.map((item) => (
                <li key={item.id}>
                  <button type="button" className={item.read ? 'bell-item' : 'bell-item is-unread'} onClick={() => void read(item)}>
                    <span className="bell-title">{item.title}</span>
                    <span className="bell-text">{item.content}</span>
                    <span className="bell-time">{formatDate(item.createdAt)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <Link to="/shop/notifications" className="bell-all">
            Xem tất cả
          </Link>
        </div>
      )}
    </div>
  )
}
