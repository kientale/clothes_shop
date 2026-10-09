import '@fontsource-variable/onest'
import { StrictMode, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import AdminLayout from './components/AdminLayout'
import { RequireAdmin } from './components/common'
import { ADMIN_NAV_GROUPS } from './navigation'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import PlaceholderPage from './pages/PlaceholderPage'
import AdminAccountsPage from './pages/access/AdminAccountsPage'
import RolesPage from './pages/access/RolesPage'
import CustomersPage from './pages/customers/CustomersPage'
import ProductDetailPage from './pages/catalog/ProductDetailPage'
import ProductsPage from './pages/catalog/ProductsPage'
import CategoriesPage from './pages/catalog/CategoriesPage'
import BrandsPage from './pages/catalog/BrandsPage'
import VariantsPage from './pages/catalog/VariantsPage'
import SizesPage from './pages/catalog/SizesPage'
import ColorsPage from './pages/catalog/ColorsPage'
import CollectionsPage from './pages/catalog/CollectionsPage'
import InventoryPage from './pages/inventory/InventoryPage'
import StockAlertsPage from './pages/inventory/StockAlertsPage'
import MovementsPage from './pages/inventory/MovementsPage'
import OrdersPage from './pages/orders/OrdersPage'
import OrderDetailPage from './pages/orders/OrderDetailPage'
import PaymentsPage from './pages/orders/PaymentsPage'
import ShipmentsPage, { ShipmentHistoryPage } from './pages/orders/ShipmentsPage'
import ReturnsPage, { RefundsPage } from './pages/orders/ReturnsPage'
import ReviewsPage from './pages/customers/ReviewsPage'
import CouponsPage from './pages/marketing/CouponsPage'
import PromotionsPage from './pages/marketing/PromotionsPage'
import FlashSalesPage from './pages/marketing/FlashSalesPage'
import BannersPage from './pages/marketing/BannersPage'
import NotificationsPage from './pages/marketing/NotificationsPage'
import ArticlesPage from './pages/content/ArticlesPage'
import PoliciesPage from './pages/content/PoliciesPage'
import PermissionsPage from './pages/access/PermissionsPage'
import { OrdersReportPage, RevenueReportPage } from './pages/reports/RevenueReportPage'
import { BestsellersReportPage, CustomersReportPage, InventoryReportPage, PromotionsReportPage, ReturnsReportPage } from './pages/reports/TableReports'
import {
  GeneralSettingsPage,
  NotificationSettingsPage,
  OrderSettingsPage,
  PaymentSettingsPage,
  ShippingSettingsPage,
  StoreSettingsPage,
} from './pages/settings/SettingsPages'
import { ToastProvider } from './components/Toast'
import { ThemeProvider } from './theme/ThemeContext'
import './styles.css'
import './operations.css'

// One screen per sidebar entry (see navigation.ts); unknown paths fall through to the not-found page.
const PAGES: Record<string, ReactNode> = {
  '/products': <ProductsPage />,
  '/categories': <CategoriesPage />,
  '/brands': <BrandsPage />,
  '/product-variants': <VariantsPage />,
  '/sizes': <SizesPage />,
  '/colors': <ColorsPage />,
  '/collections': <CollectionsPage />,
  '/inventory': <InventoryPage />,
  '/inventory/transactions': <MovementsPage mode="transactions" />,
  '/inventory/history': <MovementsPage mode="history" />,
  '/inventory/stock-alerts': <StockAlertsPage />,
  '/orders': <OrdersPage />,
  '/payments': <PaymentsPage />,
  '/shipments': <ShipmentsPage />,
  '/shipments/history': <ShipmentHistoryPage />,
  '/returns': <ReturnsPage />,
  '/refunds': <RefundsPage />,
  '/customers': <CustomersPage />,
  '/reviews': <ReviewsPage />,
  '/coupons': <CouponsPage />,
  '/promotions': <PromotionsPage />,
  '/flash-sales': <FlashSalesPage />,
  '/banners': <BannersPage />,
  '/notifications': <NotificationsPage />,
  '/articles': <ArticlesPage />,
  '/policies': <PoliciesPage />,
  '/admin-accounts': <AdminAccountsPage />,
  '/roles': <RolesPage />,
  '/permissions': <PermissionsPage />,
  '/reports/revenue': <RevenueReportPage />,
  '/reports/orders': <OrdersReportPage />,
  '/reports/best-sellers': <BestsellersReportPage />,
  '/reports/inventory': <InventoryReportPage />,
  '/reports/customers': <CustomersReportPage />,
  '/reports/returns': <ReturnsReportPage />,
  '/reports/promotions': <PromotionsReportPage />,
  '/settings/store': <StoreSettingsPage />,
  '/settings/payments': <PaymentSettingsPage />,
  '/settings/shipping': <ShippingSettingsPage />,
  '/settings/orders': <OrderSettingsPage />,
  '/settings/notifications': <NotificationSettingsPage />,
  '/settings/general': <GeneralSettingsPage />,
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider>
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
                {ADMIN_NAV_GROUPS.flatMap((group) => group.items).map((item) => (
                  <Route key={item.to} path={item.to.slice(1)} element={PAGES[item.to] ?? <PlaceholderPage title={item.label} />} />
                ))}
                <Route path="orders/:id" element={<OrderDetailPage />} />
                <Route path="products/:id" element={<ProductDetailPage />} />
                <Route path="settings" element={<Navigate to="/settings/store" replace />} />
                <Route path="*" element={<PlaceholderPage title="Không tìm thấy trang" />} />
              </Route>
            </Routes>
          </ToastProvider>
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
)
