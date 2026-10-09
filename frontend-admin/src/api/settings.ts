import { api } from './client'
import type { Page } from './types'

// Mirrors /api/v1/admin/settings (docs/system-settings.md). PUT replaces a whole group and needs
// the revision read by GET; a stale revision answers 409 SETTINGS_REVISION_CONFLICT.

export interface StoreSettings {
  storeName: string
  legalName: string | null
  taxCode: string | null
  supportEmail: string | null
  supportPhone: string | null
  address: string | null
  logoUrl: string | null
  websiteUrl: string | null
  businessHours: string | null
  /** Zalo number for the shop's chat button. */
  zaloPhone: string | null
  /** m.me link of the Facebook page for the Messenger button. */
  messengerUrl: string | null
}

export interface PaymentSettings {
  paymentsEnabled: boolean
  allowPartialPayments: boolean
  minimumPaymentAmount: number | null
}

export interface ShippingSettings {
  shippingEnabled: boolean
  useConfiguredFees: boolean
  defaultBaseFee: number | null
  freeShippingThreshold: number | null
}

export interface OrderSettings {
  ordersEnabled: boolean
  autoConfirm: boolean
  orderCodePrefix: string
  minimumOrderAmount: number | null
  maxItems: number | null
  maxQuantityPerItem: number | null
  enableMarketingByDefault: boolean
  allowCancellation: boolean
  allowReturns: boolean
  returnWindowDays: number | null
}

export interface NotificationSettings {
  inAppEnabled: boolean
  allowBroadcast: boolean
  maxRecipients: number | null
}

export interface GeneralSettings {
  timezone: string
  language: 'VI' | 'EN'
  maintenanceMode: boolean
  maintenanceMessage: string | null
}

export interface SettingsGroups {
  store: StoreSettings
  payment: PaymentSettings
  shipping: ShippingSettings
  order: OrderSettings
  notification: NotificationSettings
  general: GeneralSettings
}

export type SettingsGroup = keyof SettingsGroups

export interface Versioned<T> {
  revision: number
  updatedAt: string | null
  updatedBy: string | null
  configuration: T
}

export interface SettingsHistory {
  id: string
  action: string
  entityType: string
  entityId: string | null
  accountId: string | null
  oldData: string | null
  newData: string | null
  createdAt: string
}

export interface PaymentMethodConfiguration {
  /** COD and BANK_TRANSFER are confirmed by staff; VNPAY and MOMO settle automatically through the gateway. */
  kind: 'COD' | 'BANK_TRANSFER' | 'VNPAY' | 'MOMO'
  bankDetails: { bankName: string; accountNumber: string; accountHolder: string } | null
  instructions: string | null
}

export interface PaymentMethod {
  id: string
  code: string
  name: string
  provider: string
  enabled: boolean
  configuration: PaymentMethodConfiguration | null
  revision: number
  createdAt: string
  updatedAt: string
}

export interface ShippingMethod {
  id: string
  code: string
  name: string
  provider: string
  baseFee: number
  estimatedDays: number | null
  enabled: boolean
  revision: number
  createdAt: string
  updatedAt: string
}

/** Whether an online gateway has its keys, and the IPN address to register with it. */
export interface GatewayStatus {
  gateway: 'VNPAY' | 'MOMO'
  configured: boolean
  ipnUrl: string | null
}

export const settings = {
  gateways: () => api.get<GatewayStatus[]>('/payments/gateways'),
  get: <G extends SettingsGroup>(group: G) => api.get<Versioned<SettingsGroups[G]>>(`/admin/settings/${group}`),
  save: <G extends SettingsGroup>(group: G, expectedRevision: number, configuration: SettingsGroups[G]) =>
    api.put<Versioned<SettingsGroups[G]>>(`/admin/settings/${group}`, { expectedRevision, configuration }),
  history: (group: SettingsGroup, query: { page: number; size: number }) =>
    api.get<Page<SettingsHistory>>(`/admin/settings/${group}/history`, query),
  paymentMethods: {
    list: () => api.get<PaymentMethod[]>('/admin/settings/payment-methods'),
    create: (body: { code: string; name: string; enabled: boolean; configuration: PaymentMethodConfiguration }) =>
      api.post<PaymentMethod>('/admin/settings/payment-methods', body),
    update: (id: string, body: { expectedRevision: number; name: string; enabled: boolean; configuration: PaymentMethodConfiguration }) =>
      api.put<PaymentMethod>(`/admin/settings/payment-methods/${id}`, body),
    remove: (id: string, expectedRevision: number) =>
      api.delete(`/admin/settings/payment-methods/${id}?expectedRevision=${expectedRevision}`),
  },
  shippingMethods: {
    list: () => api.get<ShippingMethod[]>('/admin/settings/shipping-methods'),
    create: (body: { code: string; name: string; provider: string; baseFee: number; estimatedDays: number | null; enabled: boolean }) =>
      api.post<ShippingMethod>('/admin/settings/shipping-methods', body),
    update: (id: string, body: { expectedRevision: number; name: string; provider: string; baseFee: number; estimatedDays: number | null; enabled: boolean }) =>
      api.put<ShippingMethod>(`/admin/settings/shipping-methods/${id}`, body),
    remove: (id: string, expectedRevision: number) =>
      api.delete(`/admin/settings/shipping-methods/${id}?expectedRevision=${expectedRevision}`),
  },
}
