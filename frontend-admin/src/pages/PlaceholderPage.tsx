import { Link } from 'react-router-dom'

export default function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="page">
      <h1 className="page-title">{title}</h1>
      <section className="panel empty-panel">
        <h2>Trang này chưa được xây dựng</h2>
        <p>Màn hình {title.toLowerCase()} sẽ dùng các API quản trị hiện có của backend.</p>
        <Link to="/" className="btn btn-secondary">
          Về trang chủ
        </Link>
      </section>
    </div>
  )
}
