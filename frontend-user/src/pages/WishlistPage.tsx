import { HeartIcon } from '@phosphor-icons/react'
import { Link, useSearchParams } from 'react-router-dom'
import { me } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner, Pagination, Spinner, useTitle } from '../components/common'
import { ProductCard, ProductGridSkeleton } from '../components/ProductCard'
import { useAsync } from '../hooks'
import { useWishlist } from '../wishlist/WishlistContext'

/** Saved products. Signed-out shoppers are invited to sign in, since the list is kept on their account. */
export default function WishlistPage() {
  useTitle('Yêu thích')
  const { user, loading } = useAuth()
  if (loading) return <Spinner />
  if (!user?.roles.includes('CUSTOMER')) {
    return (
      <div className="container narrow">
        <div className="empty-state large">
          <HeartIcon size={44} weight="thin" aria-hidden />
          <h1>Lưu sản phẩm bạn thích</h1>
          <p className="muted">Đăng nhập để lưu sản phẩm và xem lại trên mọi thiết bị.</p>
          <Link to="/shop/login" state={{ from: '/shop/wishlist' }} className="btn btn-primary">
            Đăng nhập
          </Link>
        </div>
      </div>
    )
  }
  return <SavedProducts />
}

function SavedProducts() {
  const wishlist = useWishlist()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 1) - 1)
  // Reload when hearts change elsewhere, so a removed product leaves the grid.
  const data = useAsync(() => me.wishlist(page, 24), [page, wishlist.count])

  return (
    <div className="container">
      <h1 className="page-title">Yêu thích</h1>
      <p className="muted page-lede">{wishlist.count > 0 ? `${wishlist.count} sản phẩm đã lưu` : 'Chưa có sản phẩm nào.'}</p>
      <ErrorBanner error={data.error} onRetry={data.reload} />
      {!data.data && !data.error ? (
        <ProductGridSkeleton count={4} />
      ) : data.data && data.data.content.length > 0 ? (
        <>
          <div className="product-grid">
            {data.data.content.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
          <Pagination page={data.data.page} totalPages={data.data.totalPages} onChange={(next) => setParams(next > 0 ? { page: String(next + 1) } : {})} />
        </>
      ) : data.data ? (
        <div className="empty-state">
          <HeartIcon size={44} weight="thin" aria-hidden />
          <h2>Danh sách yêu thích đang trống</h2>
          <p className="muted">Bấm vào biểu tượng trái tim trên sản phẩm để lưu lại.</p>
          <Link to="/shop/products" className="btn btn-primary">
            Xem sản phẩm
          </Link>
        </div>
      ) : null}
    </div>
  )
}
