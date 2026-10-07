import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { CartProvider } from './cart/CartContext'
import { RequireAuth } from './components/common'
import Layout from './components/Layout'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'
import OrderDetailPage from './pages/OrderDetailPage'
import OrdersPage from './pages/OrdersPage'
import ProductPage from './pages/ProductPage'
import ShopPage from './pages/ShopPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<ShopPage />} />
              <Route path="products/:id" element={<ProductPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />
              <Route path="cart" element={<RequireAuth><CartPage /></RequireAuth>} />
              <Route path="checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
              <Route path="orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
              <Route path="orders/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
              <Route
                path="*"
                element={
                  <div className="empty">
                    <h1>Không tìm thấy trang</h1>
                    <Link to="/">Về cửa hàng</Link>
                  </div>
                }
              />
            </Route>
          </Routes>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
