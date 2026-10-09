import { Component, type ErrorInfo, type ReactNode } from 'react'
import { reportError } from '../monitoring'

/** Last line of defence: a render error shows a friendly page (and is reported) instead of a blank screen. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    reportError(error, { componentStack: info.componentStack })
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="container narrow">
        <div className="empty-state large">
          <h1>Đã có lỗi xảy ra</h1>
          <p className="muted">Trang không hiển thị được. Bạn thử tải lại, nếu vẫn lỗi hãy quay lại sau ít phút.</p>
          <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>
            Tải lại trang
          </button>
        </div>
      </div>
    )
  }
}
