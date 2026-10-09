import { BellIcon } from '@phosphor-icons/react'
import { useSearchParams } from 'react-router-dom'
import { me } from '../api/store'
import type { InboxItem } from '../api/types'
import { ErrorBanner, Pagination, useTitle } from '../components/common'
import { formatDate } from '../format'
import { useAsync } from '../hooks'

const TYPE_LABEL: Record<InboxItem['notificationType'], string> = { GENERAL: 'Tin chung', ORDER: 'Đơn hàng', PROMOTION: 'Khuyến mãi' }

export default function NotificationsPage() {
  useTitle('Thông báo')
  const [params, setParams] = useSearchParams()
  const unreadOnly = params.get('filter') === 'unread'
  const page = Math.max(0, Number(params.get('page') ?? 1) - 1)
  const inbox = useAsync(() => me.inbox({ read: unreadOnly ? false : undefined, page, size: 15 }), [unreadOnly, page])

  const open = async (item: InboxItem) => {
    if (item.read || !inbox.data) return
    inbox.setData({ ...inbox.data, content: inbox.data.content.map((i) => (i.id === item.id ? { ...i, read: true } : i)) })
    try {
      await me.markRead(item.id)
    } catch {
      inbox.reload()
    }
  }

  return (
    <div className="container narrow">
      <h1 className="page-title">Thông báo</h1>
      <div className="tabs" role="group" aria-label="Lọc thông báo">
        <button type="button" aria-pressed={!unreadOnly} onClick={() => setParams({})}>
          Tất cả
        </button>
        <button type="button" aria-pressed={unreadOnly} onClick={() => setParams({ filter: 'unread' })}>
          Chưa đọc
        </button>
      </div>
      <ErrorBanner error={inbox.error} onRetry={inbox.reload} />
      {inbox.loading && !inbox.data ? (
        <div className="order-list">
          {Array.from({ length: 3 }, (_, i) => (
            <span key={i} className="skel order-skel" />
          ))}
        </div>
      ) : inbox.data && inbox.data.content.length > 0 ? (
        <ul className="inbox-list">
          {inbox.data.content.map((item) => (
            <li key={item.id}>
              <button type="button" className={item.read ? 'inbox-item' : 'inbox-item is-unread'} onClick={() => void open(item)}>
                <span className="inbox-meta">
                  <span className="tag-pill">{TYPE_LABEL[item.notificationType]}</span>
                  <span className="muted small">{formatDate(item.createdAt)}</span>
                </span>
                <strong>{item.title}</strong>
                <span className="inbox-text">{item.content}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : inbox.data ? (
        <div className="empty-state">
          <BellIcon size={44} weight="thin" aria-hidden />
          <h2>{unreadOnly ? 'Bạn đã đọc hết thông báo' : 'Chưa có thông báo nào'}</h2>
          <p className="muted">Khuyến mãi và cập nhật về đơn hàng sẽ được gửi tới đây.</p>
        </div>
      ) : null}
      {inbox.data && (
        <Pagination page={inbox.data.page} totalPages={inbox.data.totalPages} onChange={(next) => setParams((current) => {
          const p = new URLSearchParams(current)
          if (next > 0) p.set('page', String(next + 1))
          else p.delete('page')
          return p
        })} />
      )}
    </div>
  )
}
