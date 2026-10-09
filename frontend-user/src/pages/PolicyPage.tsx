import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { store } from '../api/store'
import { ErrorBanner, Spinner, useTitle } from '../components/common'
import { POLICY_LABEL, formatDay } from '../format'
import { useAsync } from '../hooks'

/** The store policy currently in effect for one type, as published by the admins. */
export default function PolicyPage() {
  const { type = '' } = useParams()
  const policy = useAsync(() => store.policy(type), [type])
  useTitle(policy.data?.title ?? POLICY_LABEL[type] ?? 'Chính sách')
  const missing = policy.error instanceof ApiError && policy.error.status === 404

  return (
    <div className="container narrow policy-page">
      <nav className="policy-nav" aria-label="Chính sách cửa hàng">
        {Object.entries(POLICY_LABEL).map(([key, label]) => (
          <Link key={key} to={`/policies/${key}`} aria-current={key === type ? 'page' : undefined}>
            {label}
          </Link>
        ))}
      </nav>
      {policy.loading && !policy.data ? (
        <Spinner />
      ) : missing ? (
        <div className="empty-state">
          <h1>{POLICY_LABEL[type] ?? 'Chính sách'}</h1>
          <p className="muted">Cửa hàng chưa công bố nội dung này.</p>
        </div>
      ) : policy.error ? (
        <ErrorBanner error={policy.error} onRetry={policy.reload} />
      ) : policy.data ? (
        <article>
          <h1 className="page-title">{policy.data.title}</h1>
          <p className="muted small">
            Phiên bản {policy.data.version}, áp dụng từ {formatDay(policy.data.activatedAt)}
          </p>
          <div className="prose policy-body">{policy.data.content}</div>
        </article>
      ) : null}
    </div>
  )
}
