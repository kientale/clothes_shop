import {
  ChartBarIcon,
  GearIcon,
  HouseIcon,
  MegaphoneIcon,
  NewspaperIcon,
  ShieldCheckIcon,
  TagIcon,
  TrayIcon,
  UsersIcon,
  WarehouseIcon,
  type Icon,
} from '@phosphor-icons/react'

export interface AdminNavItem {
  to: string
  label: string
}

export interface AdminNavGroup {
  id: string
  label: string
  icon: Icon
  items: AdminNavItem[]
}

export const DASHBOARD_NAV = { to: '/', label: 'Dashboard tổng quan', icon: HouseIcon }

// Sidebar links and page routes share this list so every menu item has a destination.
export const ADMIN_NAV_GROUPS: AdminNavGroup[] = [
  {
    id: 'products',
    label: 'Sản phẩm',
    icon: TagIcon,
    items: [
      { to: '/products', label: 'Quản lý sản phẩm' },
      { to: '/categories', label: 'Quản lý danh mục sản phẩm' },
      { to: '/brands', label: 'Quản lý thương hiệu' },
      { to: '/product-variants', label: 'Quản lý biến thể sản phẩm' },
      { to: '/sizes', label: 'Quản lý size' },
      { to: '/colors', label: 'Quản lý màu sắc' },
      { to: '/collections', label: 'Quản lý bộ sưu tập' },
    ],
  },
  {
    id: 'inventory',
    label: 'Kho hàng',
    icon: WarehouseIcon,
    items: [
      { to: '/inventory', label: 'Quản lý tồn kho' },
      { to: '/inventory/transactions', label: 'Quản lý nhập/xuất kho' },
      { to: '/inventory/history', label: 'Lịch sử điều chỉnh tồn kho' },
      { to: '/inventory/stock-alerts', label: 'Yêu cầu báo có hàng' },
    ],
  },
  {
    id: 'orders',
    label: 'Đơn hàng',
    icon: TrayIcon,
    items: [
      { to: '/orders', label: 'Quản lý đơn hàng' },
      { to: '/payments', label: 'Quản lý thanh toán' },
      { to: '/shipments', label: 'Quản lý giao hàng' },
      { to: '/shipments/history', label: 'Quản lý lịch sử giao hàng' },
      { to: '/returns', label: 'Quản lý đổi/trả hàng' },
      { to: '/refunds', label: 'Quản lý hoàn tiền' },
    ],
  },
  {
    id: 'customers',
    label: 'Khách hàng',
    icon: UsersIcon,
    items: [
      { to: '/customers', label: 'Quản lý khách hàng' },
      { to: '/reviews', label: 'Quản lý đánh giá sản phẩm' },
    ],
  },
  {
    id: 'marketing',
    label: 'Marketing & Khuyến mãi',
    icon: MegaphoneIcon,
    items: [
      { to: '/coupons', label: 'Quản lý mã giảm giá' },
      { to: '/promotions', label: 'Quản lý chương trình khuyến mãi' },
      { to: '/flash-sales', label: 'Quản lý Flash Sale' },
      { to: '/banners', label: 'Quản lý banner' },
      { to: '/notifications', label: 'Quản lý thông báo' },
    ],
  },
  {
    id: 'content',
    label: 'Nội dung',
    icon: NewspaperIcon,
    items: [
      { to: '/articles', label: 'Quản lý bài viết / Lookbook' },
      { to: '/policies', label: 'Quản lý chính sách cửa hàng' },
    ],
  },
  {
    id: 'access',
    label: 'Tài khoản & Phân quyền',
    icon: ShieldCheckIcon,
    items: [
      { to: '/admin-accounts', label: 'Quản lý tài khoản Admin' },
      { to: '/roles', label: 'Quản lý vai trò' },
      { to: '/permissions', label: 'Quản lý quyền truy cập' },
    ],
  },
  {
    id: 'reports',
    label: 'Báo cáo & Thống kê',
    icon: ChartBarIcon,
    items: [
      { to: '/reports/revenue', label: 'Báo cáo doanh thu' },
      { to: '/reports/orders', label: 'Báo cáo đơn hàng' },
      { to: '/reports/best-sellers', label: 'Báo cáo sản phẩm bán chạy' },
      { to: '/reports/inventory', label: 'Báo cáo tồn kho' },
      { to: '/reports/customers', label: 'Báo cáo khách hàng' },
      { to: '/reports/returns', label: 'Báo cáo đổi/trả hàng' },
      { to: '/reports/promotions', label: 'Báo cáo hiệu quả khuyến mãi' },
    ],
  },
  {
    id: 'settings',
    label: 'Cài đặt hệ thống',
    icon: GearIcon,
    items: [
      { to: '/settings/store', label: 'Cài đặt thông tin cửa hàng' },
      { to: '/settings/payments', label: 'Cài đặt thanh toán' },
      { to: '/settings/shipping', label: 'Cài đặt giao hàng' },
      { to: '/settings/orders', label: 'Cài đặt đơn hàng' },
      { to: '/settings/notifications', label: 'Cài đặt thông báo' },
      { to: '/settings/general', label: 'Cài đặt hệ thống chung' },
    ],
  },
]

export function getActiveNavGroup(pathname: string) {
  return ADMIN_NAV_GROUPS.find((group) =>
    group.items.some((item) => pathname === item.to || pathname.startsWith(`${item.to}/`)),
  )
}
