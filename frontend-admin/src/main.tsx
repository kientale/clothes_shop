import '@fontsource-variable/geist'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import AdminLayout from './components/AdminLayout'
import { RequireAdmin } from './components/common'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import PlaceholderPage from './pages/PlaceholderPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="login" element={<LoginPage />} />
          <Route
            element={
              <RequireAdmin>
                <AdminLayout />
              </RequireAdmin>
            }
          >
            <Route index element={<HomePage />} />
            <Route path="orders/*" element={<PlaceholderPage title="Đơn hàng" />} />
            <Route path="products/*" element={<PlaceholderPage title="Sản phẩm" />} />
            <Route path="categories" element={<PlaceholderPage title="Danh mục" />} />
            <Route path="inventory" element={<PlaceholderPage title="Tồn kho" />} />
            <Route path="settings" element={<PlaceholderPage title="Cài đặt" />} />
            <Route path="*" element={<PlaceholderPage title="Không tìm thấy trang" />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
