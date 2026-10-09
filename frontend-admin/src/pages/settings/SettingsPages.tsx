import { useId, useState } from 'react'
import { IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'
import type { GeneralSettings, NotificationSettings, OrderSettings, PaymentSettings, ShippingSettings, StoreSettings } from '../../api/settings'
import { AvatarField } from '../../components/AvatarField'
import { Field, bind } from '../../components/forms'
import { vndOf } from '../../components/kit'
import { PaymentMethodsPanel, ShippingMethodsPanel } from './MethodsPanels'
import { SettingsSection, SettingsShell, SwitchRow, badNumber, numOrNull, numText } from './SettingsShell'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const URL = /^https?:\/\/\S+$/
const PHONE = /^\+?[0-9 .-]{8,20}$/

type Text<T> = { [K in keyof T]: T[K] extends boolean ? boolean : string }

export function StoreSettingsPage() {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  return (
    <SettingsShell<'store', Text<StoreSettings>>
      group="store"
      title="Thông tin cửa hàng"
      lede="Tên, liên hệ và giờ mở cửa hiển thị cho khách ở cửa hàng và trên chứng từ."
      toForm={(c) => ({
        storeName: c.storeName ?? '',
        legalName: c.legalName ?? '',
        taxCode: c.taxCode ?? '',
        supportEmail: c.supportEmail ?? '',
        supportPhone: c.supportPhone ?? '',
        address: c.address ?? '',
        logoUrl: c.logoUrl ?? '',
        websiteUrl: c.websiteUrl ?? '',
        businessHours: c.businessHours ?? '',
        zaloPhone: c.zaloPhone ?? '',
        messengerUrl: c.messengerUrl ?? '',
      })}
      toConfig={(f) => ({
        storeName: f.storeName.trim(),
        legalName: f.legalName.trim() || null,
        taxCode: f.taxCode.trim() || null,
        supportEmail: f.supportEmail.trim() || null,
        supportPhone: f.supportPhone.trim() || null,
        address: f.address.trim() || null,
        logoUrl: f.logoUrl.trim() || null,
        websiteUrl: f.websiteUrl.trim() || null,
        businessHours: f.businessHours.trim() || null,
        zaloPhone: f.zaloPhone.replace(/[\s.]/g, '') || null,
        messengerUrl: f.messengerUrl.trim() || null,
      })}
      validate={(f) => {
        const e: Record<string, string> = {}
        if (uploading) e.logoUrl = 'Đợi ảnh tải lên xong.'
        if (!f.storeName.trim()) e.storeName = 'Nhập tên cửa hàng.'
        if (f.supportEmail.trim() && !EMAIL.test(f.supportEmail.trim())) e.supportEmail = 'Email không hợp lệ.'
        if (f.supportPhone.trim() && !PHONE.test(f.supportPhone.trim())) e.supportPhone = 'Số điện thoại không hợp lệ.'
        if (f.websiteUrl.trim() && !URL.test(f.websiteUrl.trim())) e.websiteUrl = 'Website bắt đầu bằng http(s)://.'
        if (f.zaloPhone.trim() && !PHONE.test(f.zaloPhone.replace(/[\s.]/g, ''))) e.zaloPhone = 'Số Zalo gồm 8-15 chữ số.'
        if (f.messengerUrl.trim() && !/^https:\/\/(m\.me|(www\.)?messenger\.com)\/\S+$/.test(f.messengerUrl.trim()))
          e.messengerUrl = 'Dùng link dạng https://m.me/ten-trang.'
        return e
      }}
    >
      {({ form, set, errors }) => (
        <>
          <SettingsSection title="Nhận diện">
            <div className="form-grid">
              <Field label="Tên cửa hàng" htmlFor={`${id}-storeName`} error={errors.storeName}>
                <input {...bind(id, 'storeName', errors)} maxLength={150} value={form.storeName} onChange={(e) => set('storeName', e.target.value)} />
              </Field>
              <Field label="Website" htmlFor={`${id}-websiteUrl`} error={errors.websiteUrl} optional>
                <input {...bind(id, 'websiteUrl', errors)} maxLength={2048} placeholder="https://" value={form.websiteUrl} onChange={(e) => set('websiteUrl', e.target.value)} />
              </Field>
              <Field label="Logo" htmlFor={`${id}-logoUrl-file`} optional wide>
                <AvatarField
                  id={`${id}-logoUrl`}
                  shape="square"
                  upload={uploadImage}
                  maxBytes={IMAGE_MAX_BYTES}
                  value={form.logoUrl}
                  name={form.storeName}
                  error={errors.logoUrl}
                  onChange={(url) => set('logoUrl', url)}
                  onUploadingChange={setUploading}
                />
              </Field>
            </div>
          </SettingsSection>
          <SettingsSection title="Pháp lý">
            <div className="form-grid">
              <Field label="Tên pháp lý" htmlFor={`${id}-legalName`} optional>
                <input id={`${id}-legalName`} maxLength={255} value={form.legalName} onChange={(e) => set('legalName', e.target.value)} />
              </Field>
              <Field label="Mã số thuế" htmlFor={`${id}-taxCode`} optional>
                <input id={`${id}-taxCode`} className="mono-input" maxLength={50} value={form.taxCode} onChange={(e) => set('taxCode', e.target.value)} />
              </Field>
            </div>
          </SettingsSection>
          <SettingsSection title="Liên hệ">
            <div className="form-grid">
              <Field label="Email hỗ trợ" htmlFor={`${id}-supportEmail`} error={errors.supportEmail} optional>
                <input {...bind(id, 'supportEmail', errors)} type="email" maxLength={254} value={form.supportEmail} onChange={(e) => set('supportEmail', e.target.value)} />
              </Field>
              <Field label="Điện thoại hỗ trợ" htmlFor={`${id}-supportPhone`} error={errors.supportPhone} optional>
                <input {...bind(id, 'supportPhone', errors)} inputMode="tel" maxLength={20} value={form.supportPhone} onChange={(e) => set('supportPhone', e.target.value)} />
              </Field>
              <Field label="Địa chỉ" htmlFor={`${id}-address`} optional wide>
                <input id={`${id}-address`} maxLength={500} value={form.address} onChange={(e) => set('address', e.target.value)} />
              </Field>
              <Field label="Giờ mở cửa" htmlFor={`${id}-businessHours`} optional wide>
                <input id={`${id}-businessHours`} maxLength={255} placeholder="Ví dụ: 9:00 - 21:00 hằng ngày" value={form.businessHours} onChange={(e) => set('businessHours', e.target.value)} />
              </Field>
            </div>
          </SettingsSection>
          <SettingsSection title="Nút chat trên cửa hàng">
            <p className="muted small">Khách thấy nút chat ở góc màn hình, mở Zalo, Messenger hoặc gọi điện hỗ trợ. Bỏ trống để ẩn từng kênh.</p>
            <div className="form-grid">
              <Field label="Số Zalo" htmlFor={`${id}-zaloPhone`} error={errors.zaloPhone} optional>
                <input {...bind(id, 'zaloPhone', errors)} inputMode="tel" maxLength={20} placeholder="0901234567" value={form.zaloPhone} onChange={(e) => set('zaloPhone', e.target.value)} />
              </Field>
              <Field label="Link Messenger" htmlFor={`${id}-messengerUrl`} error={errors.messengerUrl} optional>
                <input {...bind(id, 'messengerUrl', errors)} maxLength={2048} placeholder="https://m.me/lemonadex" value={form.messengerUrl} onChange={(e) => set('messengerUrl', e.target.value)} />
              </Field>
            </div>
          </SettingsSection>
        </>
      )}
    </SettingsShell>
  )
}

export function PaymentSettingsPage() {
  const id = useId()
  return (
    <SettingsShell<'payment', Text<PaymentSettings>>
      group="payment"
      title="Cài đặt thanh toán"
      lede="Quy tắc nhận thanh toán và các phương thức khách được chọn khi đặt hàng."
      toForm={(c) => ({ paymentsEnabled: c.paymentsEnabled, allowPartialPayments: c.allowPartialPayments, minimumPaymentAmount: numText(c.minimumPaymentAmount) })}
      toConfig={(f) => ({ paymentsEnabled: f.paymentsEnabled, allowPartialPayments: f.allowPartialPayments, minimumPaymentAmount: numOrNull(f.minimumPaymentAmount) })}
      validate={(f) => (badNumber(f.minimumPaymentAmount) ? { minimumPaymentAmount: 'Nhập số tiền hợp lệ.' } : {})}
      aside={<PaymentMethodsPanel />}
    >
      {({ form, set, errors }) => (
        <SettingsSection title="Quy tắc">
          <SwitchRow id={`${id}-enabled`} label="Nhận thanh toán mới" hint="Tắt sẽ chặn tạo thanh toán mới; khoản đang chờ vẫn xác nhận được." checked={form.paymentsEnabled} onChange={(v) => set('paymentsEnabled', v)} />
          <SwitchRow id={`${id}-partial`} label="Cho phép thanh toán từng phần" hint="Khi tắt, mỗi khoản phải bằng số tiền còn lại của đơn." checked={form.allowPartialPayments} onChange={(v) => set('allowPartialPayments', v)} />
          <div className="form-grid">
            <Field label="Số tiền tối thiểu mỗi lần" htmlFor={`${id}-minimumPaymentAmount`} error={errors.minimumPaymentAmount} optional>
              <input {...bind(id, 'minimumPaymentAmount', errors)} inputMode="numeric" value={form.minimumPaymentAmount} onChange={(e) => set('minimumPaymentAmount', e.target.value)} />
            </Field>
          </div>
        </SettingsSection>
      )}
    </SettingsShell>
  )
}

export function ShippingSettingsPage() {
  const id = useId()
  return (
    <SettingsShell<'shipping', Text<ShippingSettings>>
      group="shipping"
      title="Cài đặt giao hàng"
      lede="Phí giao hàng mặc định, ngưỡng miễn phí và biểu phí theo phương thức."
      toForm={(c) => ({
        shippingEnabled: c.shippingEnabled,
        useConfiguredFees: c.useConfiguredFees,
        defaultBaseFee: numText(c.defaultBaseFee),
        freeShippingThreshold: numText(c.freeShippingThreshold),
      })}
      toConfig={(f) => ({
        shippingEnabled: f.shippingEnabled,
        useConfiguredFees: f.useConfiguredFees,
        defaultBaseFee: numOrNull(f.defaultBaseFee),
        freeShippingThreshold: numOrNull(f.freeShippingThreshold),
      })}
      validate={(f) => ({
        ...(badNumber(f.defaultBaseFee) ? { defaultBaseFee: 'Nhập số tiền hợp lệ.' } : {}),
        ...(badNumber(f.freeShippingThreshold) ? { freeShippingThreshold: 'Nhập số tiền hợp lệ.' } : {}),
      })}
      aside={<ShippingMethodsPanel />}
    >
      {({ form, set, errors }) => (
        <SettingsSection title="Quy tắc">
          <SwitchRow id={`${id}-enabled`} label="Nhận giao hàng" hint="Tắt sẽ chặn đơn và vận đơn mới; vận đơn đang chạy vẫn cập nhật được." checked={form.shippingEnabled} onChange={(v) => set('shippingEnabled', v)} />
          <SwitchRow id={`${id}-configured`} label="Tự tính phí theo cấu hình" hint="Đơn mới dùng phí mặc định hoặc phí của phương thức, thay vì nhập tay." checked={form.useConfiguredFees} onChange={(v) => set('useConfiguredFees', v)} />
          <div className="form-grid">
            <Field label="Phí mặc định" htmlFor={`${id}-defaultBaseFee`} error={errors.defaultBaseFee} optional>
              <input {...bind(id, 'defaultBaseFee', errors)} inputMode="numeric" value={form.defaultBaseFee} onChange={(e) => set('defaultBaseFee', e.target.value)} />
            </Field>
            <Field
              label="Miễn phí cho đơn từ"
              htmlFor={`${id}-freeShippingThreshold`}
              error={errors.freeShippingThreshold}
              optional
              hint={form.freeShippingThreshold && !badNumber(form.freeShippingThreshold) ? `Tiền hàng sau giảm giá từ ${vndOf(numOrNull(form.freeShippingThreshold))}.` : 'Để trống nếu không áp dụng.'}
            >
              <input {...bind(id, 'freeShippingThreshold', errors)} inputMode="numeric" value={form.freeShippingThreshold} onChange={(e) => set('freeShippingThreshold', e.target.value)} />
            </Field>
          </div>
        </SettingsSection>
      )}
    </SettingsShell>
  )
}

export function OrderSettingsPage() {
  const id = useId()
  return (
    <SettingsShell<'order', Text<OrderSettings>>
      group="order"
      title="Cài đặt đơn hàng"
      lede="Quy tắc nhận đơn, giới hạn số lượng và chính sách hủy, đổi trả."
      toForm={(c) => ({
        ordersEnabled: c.ordersEnabled,
        autoConfirm: c.autoConfirm,
        orderCodePrefix: c.orderCodePrefix ?? 'LX',
        minimumOrderAmount: numText(c.minimumOrderAmount),
        maxItems: numText(c.maxItems),
        maxQuantityPerItem: numText(c.maxQuantityPerItem),
        enableMarketingByDefault: c.enableMarketingByDefault,
        allowCancellation: c.allowCancellation,
        allowReturns: c.allowReturns,
        returnWindowDays: numText(c.returnWindowDays),
      })}
      toConfig={(f) => ({
        ordersEnabled: f.ordersEnabled,
        autoConfirm: f.autoConfirm,
        orderCodePrefix: f.orderCodePrefix.trim().toUpperCase(),
        minimumOrderAmount: numOrNull(f.minimumOrderAmount),
        maxItems: numOrNull(f.maxItems),
        maxQuantityPerItem: numOrNull(f.maxQuantityPerItem),
        enableMarketingByDefault: f.enableMarketingByDefault,
        allowCancellation: f.allowCancellation,
        allowReturns: f.allowReturns,
        returnWindowDays: numOrNull(f.returnWindowDays),
      })}
      validate={(f) => {
        const e: Record<string, string> = {}
        if (!/^[A-Za-z0-9]{1,10}$/.test(f.orderCodePrefix.trim())) e.orderCodePrefix = 'Tiền tố gồm chữ và số, tối đa 10 ký tự.'
        for (const key of ['minimumOrderAmount', 'maxItems', 'maxQuantityPerItem', 'returnWindowDays'] as const) if (badNumber(f[key])) e[key] = 'Nhập số nguyên hợp lệ.'
        return e
      }}
    >
      {({ form, set, errors }) => (
        <>
          <SettingsSection title="Nhận đơn">
            <SwitchRow id={`${id}-enabled`} label="Nhận đơn mới" hint="Tắt để tạm ngừng bán; đơn hiện có vẫn xử lý bình thường." checked={form.ordersEnabled} onChange={(v) => set('ordersEnabled', v)} />
            <SwitchRow id={`${id}-auto`} label="Tự động xác nhận đơn" hint="Đơn mới chuyển thẳng sang đã xác nhận khi giữ hàng thành công." checked={form.autoConfirm} onChange={(v) => set('autoConfirm', v)} />
            <SwitchRow id={`${id}-marketing`} label="Áp dụng khuyến mãi mặc định" hint="Đơn tạo mới tự chọn khuyến mãi hoặc Flash Sale tốt nhất." checked={form.enableMarketingByDefault} onChange={(v) => set('enableMarketingByDefault', v)} />
            <div className="form-grid">
              <Field label="Tiền tố mã đơn" htmlFor={`${id}-orderCodePrefix`} error={errors.orderCodePrefix} hint={`Mã đơn có dạng ${form.orderCodePrefix.trim().toUpperCase() || 'LX'}-...`}>
                <input {...bind(id, 'orderCodePrefix', errors)} className="mono-input" maxLength={10} value={form.orderCodePrefix} onChange={(e) => set('orderCodePrefix', e.target.value.toUpperCase())} />
              </Field>
              <Field label="Giá trị đơn tối thiểu" htmlFor={`${id}-minimumOrderAmount`} error={errors.minimumOrderAmount} optional>
                <input {...bind(id, 'minimumOrderAmount', errors)} inputMode="numeric" value={form.minimumOrderAmount} onChange={(e) => set('minimumOrderAmount', e.target.value)} />
              </Field>
              <Field label="Số dòng tối đa mỗi đơn" htmlFor={`${id}-maxItems`} error={errors.maxItems} optional>
                <input {...bind(id, 'maxItems', errors)} inputMode="numeric" value={form.maxItems} onChange={(e) => set('maxItems', e.target.value)} />
              </Field>
              <Field label="Số lượng tối đa mỗi dòng" htmlFor={`${id}-maxQuantityPerItem`} error={errors.maxQuantityPerItem} optional>
                <input {...bind(id, 'maxQuantityPerItem', errors)} inputMode="numeric" value={form.maxQuantityPerItem} onChange={(e) => set('maxQuantityPerItem', e.target.value)} />
              </Field>
            </div>
          </SettingsSection>
          <SettingsSection title="Hủy và đổi trả">
            <SwitchRow id={`${id}-cancel`} label="Cho phép hủy đơn" checked={form.allowCancellation} onChange={(v) => set('allowCancellation', v)} />
            <SwitchRow id={`${id}-returns`} label="Nhận yêu cầu đổi/trả" checked={form.allowReturns} onChange={(v) => set('allowReturns', v)} />
            {form.allowReturns && (
              <div className="form-grid">
                <Field label="Thời hạn đổi/trả (ngày)" htmlFor={`${id}-returnWindowDays`} error={errors.returnWindowDays} optional hint="Tính từ ngày giao. Để trống nếu không giới hạn.">
                  <input {...bind(id, 'returnWindowDays', errors)} inputMode="numeric" value={form.returnWindowDays} onChange={(e) => set('returnWindowDays', e.target.value)} />
                </Field>
              </div>
            )}
          </SettingsSection>
        </>
      )}
    </SettingsShell>
  )
}

export function NotificationSettingsPage() {
  const id = useId()
  return (
    <SettingsShell<'notification', Text<NotificationSettings>>
      group="notification"
      title="Cài đặt thông báo"
      lede="Thông báo trong ứng dụng cho khách hàng. Chưa gửi qua email, SMS hay push."
      toForm={(c) => ({ inAppEnabled: c.inAppEnabled, allowBroadcast: c.allowBroadcast, maxRecipients: numText(c.maxRecipients) })}
      toConfig={(f) => ({ inAppEnabled: f.inAppEnabled, allowBroadcast: f.allowBroadcast, maxRecipients: numOrNull(f.maxRecipients) })}
      validate={(f) => (badNumber(f.maxRecipients) ? { maxRecipients: 'Nhập số nguyên hợp lệ.' } : {})}
    >
      {({ form, set, errors }) => (
        <SettingsSection title="Phát hành">
          <SwitchRow id={`${id}-inapp`} label="Bật thông báo trong ứng dụng" checked={form.inAppEnabled} onChange={(v) => set('inAppEnabled', v)} />
          <SwitchRow id={`${id}-broadcast`} label="Cho phép gửi tới toàn bộ khách" hint="Tắt để chỉ gửi danh sách khách chọn lọc." checked={form.allowBroadcast} onChange={(v) => set('allowBroadcast', v)} />
          <div className="form-grid">
            <Field label="Số người nhận tối đa mỗi lần" htmlFor={`${id}-maxRecipients`} error={errors.maxRecipients} optional hint="Vượt giới hạn thì lần phát bị hủy và thông báo giữ ở dạng nháp.">
              <input {...bind(id, 'maxRecipients', errors)} inputMode="numeric" value={form.maxRecipients} onChange={(e) => set('maxRecipients', e.target.value)} />
            </Field>
          </div>
        </SettingsSection>
      )}
    </SettingsShell>
  )
}

const TIMEZONES = ['Asia/Ho_Chi_Minh', 'Asia/Bangkok', 'Asia/Singapore', 'Asia/Tokyo', 'Europe/London', 'America/New_York', 'UTC']

export function GeneralSettingsPage() {
  const id = useId()
  return (
    <SettingsShell<'general', Text<GeneralSettings>>
      group="general"
      title="Cài đặt hệ thống chung"
      lede="Múi giờ mặc định cho báo cáo, ngôn ngữ hiển thị và chế độ bảo trì cửa hàng."
      toForm={(c) => ({ timezone: c.timezone, language: c.language, maintenanceMode: c.maintenanceMode, maintenanceMessage: c.maintenanceMessage ?? '' })}
      toConfig={(f) => ({ timezone: f.timezone, language: f.language as GeneralSettings['language'], maintenanceMode: f.maintenanceMode, maintenanceMessage: f.maintenanceMessage.trim() || null })}
      validate={(f) => (f.maintenanceMode && !f.maintenanceMessage.trim() ? { maintenanceMessage: 'Nhập thông điệp hiển thị cho khách khi bảo trì.' } : {})}
    >
      {({ form, set, errors }) => (
        <>
          <SettingsSection title="Khu vực">
            <div className="form-grid">
              <Field label="Múi giờ" htmlFor={`${id}-timezone`}>
                <select id={`${id}-timezone`} value={form.timezone} onChange={(e) => set('timezone', e.target.value)}>
                  {[...new Set([form.timezone, ...TIMEZONES])].map((tz) => (
                    <option key={tz} value={tz}>
                      {tz}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Ngôn ngữ" htmlFor={`${id}-language`}>
                <select id={`${id}-language`} value={form.language} onChange={(e) => set('language', e.target.value)}>
                  <option value="VI">Tiếng Việt</option>
                  <option value="EN">English</option>
                </select>
              </Field>
            </div>
          </SettingsSection>
          <SettingsSection title="Bảo trì">
            <SwitchRow
              id={`${id}-maintenance`}
              label="Bật chế độ bảo trì"
              hint="Chặn đặt đơn mới trên cửa hàng. Trang quản trị và đăng nhập vẫn hoạt động."
              checked={form.maintenanceMode}
              onChange={(v) => set('maintenanceMode', v)}
            />
            {form.maintenanceMode && (
              <div className="form-grid">
                <Field label="Thông điệp cho khách" htmlFor={`${id}-maintenanceMessage`} error={errors.maintenanceMessage} wide>
                  <textarea {...bind(id, 'maintenanceMessage', errors)} rows={3} maxLength={500} value={form.maintenanceMessage} onChange={(e) => set('maintenanceMessage', e.target.value)} />
                </Field>
              </div>
            )}
          </SettingsSection>
        </>
      )}
    </SettingsShell>
  )
}
