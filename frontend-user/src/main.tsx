import '@fontsource-variable/onest'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { initMonitoring, usePageViews } from './monitoring'
import { useSeoReset } from './seo'
import { ErrorBoundary } from './components/ErrorBoundary'
import { WishlistProvider } from './wishlist/WishlistContext'
import AccountPage from './pages/AccountPage'
import { ArticlePage, ArticlesPage } from './pages/ArticlesPages'
import PaymentReturnPage from './pages/PaymentReturnPage'
import { CollectionPage, CollectionsPage } from './pages/CollectionsPages'
import NotificationsPage from './pages/NotificationsPage'
import TrackOrderPage from './pages/TrackOrderPage'
import WishlistPage from './pages/WishlistPage'
import { AuthProvider } from './auth/AuthContext'
import { CartProvider } from './cart/CartContext'
import { RequireAuth } from './components/common'
import Layout from './components/Layout'
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from './pages/AuthPages'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import HomePage from './pages/HomePage'
import IntroPage from './pages/IntroPage'
import OrderDetailPage from './pages/OrderDetailPage'
import OrdersPage from './pages/OrdersPage'
import PolicyPage from './pages/PolicyPage'
import ProductPage from './pages/ProductPage'
import ShopPage from './pages/ShopPage'
import './styles.css'
import './intro.css'
import './extensions.css'

initMonitoring()

/** Per-navigation effects: GA page views and resetting the meta tags. */
function PageViews() {
  usePageViews()
  useSeoReset()
  return null
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* "/" introduces the shop; the store itself lives under /shop/... */}
    <BrowserRouter>
      <ErrorBoundary>
      <PageViews />
      <AuthProvider>
        <CartProvider>
          <WishlistProvider>
          <Routes>
            <Route index element={<IntroPage />} />
            <Route element={<Layout />}>
              <Route path="policies/:type" element={<PolicyPage />} />
              <Route path="shop">
                <Route index element={<HomePage />} />
                <Route path="products" element={<ShopPage />} />
                <Route path="products/:id" element={<ProductPage />} />
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />
                <Route path="forgot-password" element={<ForgotPasswordPage />} />
                <Route path="reset-password" element={<ResetPasswordPage />} />
                <Route path="verify-email" element={<VerifyEmailPage />} />
                <Route path="articles" element={<ArticlesPage />} />
                <Route path="articles/:slug" element={<ArticlePage />} />
                <Route path="payment/:gateway" element={<PaymentReturnPage />} />
                <Route path="collections" element={<CollectionsPage />} />
                <Route path="collections/:slug" element={<CollectionPage />} />
                <Route path="track" element={<TrackOrderPage />} />
                <Route path="wishlist" element={<WishlistPage />} />
                <Route path="account" element={<RequireAuth><AccountPage /></RequireAuth>} />
                <Route path="notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
                <Route path="cart" element={<CartPage />} />
                {/* Guests may check out too; signed-in customers get their saved addresses. */}
                <Route path="checkout" element={<CheckoutPage />} />
                <Route path="orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
                <Route path="orders/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
              </Route>
              <Route
                path="*"
                element={
                  <div className="container narrow">
                    <div className="empty-state large">
                      <h1>Không tìm thấy trang</h1>
                      <p className="muted">Đường dẫn có thể đã thay đổi hoặc sản phẩm không còn bán.</p>
                      <Link to="/shop" className="btn btn-primary">
                        Về cửa hàng
                      </Link>
                    </div>
                  </div>
                }
              />
            </Route>
          </Routes>
          </WishlistProvider>
        </CartProvider>
      </AuthProvider>
      </ErrorBoundary>
    </BrowserRouter>
  </StrictMode>,
)
