import { ListBulletsIcon, TicketIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import {
  DISCOUNT_TYPE_LABEL,
  MARKETING_STATUS_LABEL,
  campaignPhase,
  marketing,
  type Coupon,
  type CouponUsage,
  type DiscountType,
  type MarketingStatus,
} from '../../api/marketing'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ListPanel, Pill, Ref, countOf, fromLocalInput, toLocalInput, useCan, vndOf, whenOf, windowOf } from '../../components/kit'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { Modal } from '../../components/ui'
import { parseMoney } from '../catalog/shared'
import { MarketingStatusField, PeriodFields, checkPeriod, discountText } from './shared'

export default function CouponsPage() {
  const canWrite = useCan('COUPON_WRITE')
  const [usages, setUsages] = useState<Coupon | null>(null)
  return (
    <>
      <ResourcePage<Coupon>
        title="Mã giảm giá"
        lede="Mã khách nhập khi đặt hàng. Áp dụng sau giảm giá sản phẩm, không giảm phí vận chuyển."
        addLabel="Tạo mã giảm giá"
        itemLabel="mã giảm giá"
        searchPlaceholder="Tìm theo mã hoặc tên"
        canWrite={canWrite}
        statusLabels={MARKETING_STATUS_LABEL}
        load={(query) => marketing.coupons.list(query)}
        rowLabel={(c) => c.code}
        columns={[
          {
            header: 'Mã',
            render: (c) => (
              <div className="person-text">
                <span className="coupon-code">{c.code}</span>
                <span className="person-sub">{c.name}</span>
              </div>
            ),
          },
          {
            header: 'Mức giảm',
            render: (c) => (
              <div className="person-text">
                <strong>{discountText(c.discountType, c.discountValue)}</strong>
                <span className="person-sub">
                  {c.minimumOrderValue ? `Đơn từ ${vndOf(c.minimumOrderValue)}` : 'Mọi đơn'}
                  {c.maxDiscount ? `, tối đa ${vndOf(c.maxDiscount)}` : ''}
                </span>
              </div>
            ),
          },
          {
            header: 'Lượt dùng',
            render: (c) => (
              <span className="nowrap">
                <strong>{countOf(c.usedCount)}</strong>
                <span className="muted"> / {c.usageLimit === null ? 'không giới hạn' : countOf(c.usageLimit)}</span>
              </span>
            ),
          },
          { header: 'Thời gian', render: (c) => <span className="small">{windowOf(c.startAt, c.endAt)}</span> },
          {
            header: 'Trạng thái',
            render: (c) => {
              const [label, tone] = campaignPhase(c.startAt, c.endAt, c.status)
              return <Pill tone={tone}>{label}</Pill>
            },
          },
        ]}
        rowActions={(c) => (
          <button type="button" className="icon-btn" onClick={() => setUsages(c)} aria-label={`Lượt dùng của ${c.code}`} title="Xem lượt dùng">
            <ListBulletsIcon size={16} />
          </button>
        )}
        deleteTitle="Xóa mã giảm giá"
        deleteMessage={(c) => (
          <>
            Xóa mã <strong>{c.code}</strong>? Mã đã xóa không dùng lại được; các đơn đã áp dụng giữ nguyên mức giảm.
          </>
        )}
        remove={(c) => marketing.coupons.remove(c.id)}
        emptyIcon={<TicketIcon size={36} aria-hidden />}
        renderForm={(slot) => <CouponForm {...slot} />}
      />
      <Modal open={usages !== null} title={`Lượt dùng mã ${usages?.code ?? ''}`} onClose={() => setUsages(null)} size="lg">
        {usages && (
          <ListPanel<CouponUsage>
            local
            filterKey={usages.id}
            load={(paging) => marketing.coupons.usages(usages.id, paging)}
            rowKey={(u) => u.id}
            itemLabel="lượt dùng"
            emptyIcon={<TicketIcon size={32} aria-hidden />}
            columns={[
              { header: 'Đơn hàng', render: (u) => <Ref id={u.orderId} /> },
              { header: 'Khách hàng', render: (u) => <Ref id={u.customerId} /> },
              { header: 'Đã giảm', render: (u) => vndOf(u.discountAmount), className: 'num nowrap' },
              { header: 'Thời gian', render: (u) => whenOf(u.usedAt), className: 'nowrap' },
              { header: 'Hoàn lượt', render: (u) => (u.released ? <Pill tone="inactive">Đã hoàn {whenOf(u.releasedAt)}</Pill> : <Pill tone="active">Hiệu lực</Pill>) },
            ]}
          />
        )}
      </Modal>
    </>
  )
}

function CouponForm({ open, item, onClose, onSaved }: FormSlot<Coupon>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      code: item?.code ?? '',
      name: item?.name ?? '',
      discountType: (item?.discountType ?? 'PERCENTAGE') as DiscountType,
      discountValue: item ? String(item.discountValue) : '',
      maxDiscount: item?.maxDiscount != null ? String(item.maxDiscount) : '',
      minimumOrderValue: item?.minimumOrderValue != null ? String(item.minimumOrderValue) : '',
      usageLimit: item?.usageLimit != null ? String(item.usageLimit) : '',
      usageLimitPerCustomer: item ? String(item.usageLimitPerCustomer) : '1',
      startAt: toLocalInput(item?.startAt),
      endAt: toLocalInput(item?.endAt),
      status: (item?.status ?? 'ACTIVE') as MarketingStatus,
    }),
    [item?.id],
  )
  const optionalMoney = (value: string) => (value.trim() ? parseMoney(value) : null)
  const optionalInt = (value: string) => (value.trim() ? Number(value) : null)

  return (
    <FormDialog
      open={open}
      title={item ? `Sửa mã ${item.code}` : 'Tạo mã giảm giá'}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Tạo mã'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!/^[A-Za-z0-9_-]{1,80}$/.test(f.code.trim())) e.code = 'Mã gồm chữ, số, gạch ngang hoặc gạch dưới, tối đa 80 ký tự.'
          if (!f.name.trim()) e.name = 'Nhập tên hiển thị.'
          const value = parseMoney(f.discountValue)
          if (Number.isNaN(value) || value <= 0) e.discountValue = 'Nhập mức giảm lớn hơn 0.'
          else if (f.discountType === 'PERCENTAGE' && value > 100) e.discountValue = 'Phần trăm tối đa 100.'
          if (f.maxDiscount.trim() && Number.isNaN(parseMoney(f.maxDiscount))) e.maxDiscount = 'Số tiền không hợp lệ.'
          if (f.minimumOrderValue.trim() && Number.isNaN(parseMoney(f.minimumOrderValue))) e.minimumOrderValue = 'Số tiền không hợp lệ.'
          if (f.usageLimit.trim() && !/^[1-9]\d*$/.test(f.usageLimit.trim())) e.usageLimit = 'Nhập số nguyên dương hoặc để trống.'
          if (!/^[1-9]\d*$/.test(f.usageLimitPerCustomer.trim())) e.usageLimitPerCustomer = 'Nhập số nguyên dương.'
          return { ...e, ...checkPeriod(f.startAt, f.endAt, true) }
        },
        async (f) => {
          const body = {
            code: f.code.trim().toUpperCase(),
            name: f.name.trim(),
            discountType: f.discountType,
            discountValue: parseMoney(f.discountValue),
            maxDiscount: f.discountType === 'PERCENTAGE' ? optionalMoney(f.maxDiscount) : null,
            minimumOrderValue: optionalMoney(f.minimumOrderValue),
            usageLimit: optionalInt(f.usageLimit),
            usageLimitPerCustomer: optionalInt(f.usageLimitPerCustomer),
            startAt: fromLocalInput(f.startAt)!,
            endAt: fromLocalInput(f.endAt)!,
            status: f.status,
          }
          if (item) await marketing.coupons.update(item.id, body)
          else await marketing.coupons.create(body)
          onSaved(item ? `Đã cập nhật mã ${body.code}.` : `Đã tạo mã ${body.code}.`)
        },
      )}
    >
      <Field label="Mã" htmlFor={`${id}-code`} error={errors.code} hint="Tự chuyển thành chữ in hoa.">
        <input {...bind(id, 'code', errors)} className="mono-input" maxLength={80} value={form.code} onChange={(e) => set('code', e.target.value.toUpperCase())} />
      </Field>
      <Field label="Tên hiển thị" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} maxLength={150} value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Ví dụ: Chào bạn mới" />
      </Field>
      <Field label="Kiểu giảm" htmlFor={`${id}-discountType`}>
        <select id={`${id}-discountType`} value={form.discountType} onChange={(e) => set('discountType', e.target.value as DiscountType)}>
          {(Object.keys(DISCOUNT_TYPE_LABEL) as DiscountType[]).map((t) => (
            <option key={t} value={t}>
              {DISCOUNT_TYPE_LABEL[t]}
            </option>
          ))}
        </select>
      </Field>
      <Field label={form.discountType === 'PERCENTAGE' ? 'Phần trăm giảm' : 'Số tiền giảm'} htmlFor={`${id}-discountValue`} error={errors.discountValue}>
        <input {...bind(id, 'discountValue', errors)} inputMode="decimal" value={form.discountValue} onChange={(e) => set('discountValue', e.target.value)} />
      </Field>
      {form.discountType === 'PERCENTAGE' && (
        <Field label="Giảm tối đa" htmlFor={`${id}-maxDiscount`} error={errors.maxDiscount} optional>
          <input {...bind(id, 'maxDiscount', errors)} inputMode="numeric" value={form.maxDiscount} onChange={(e) => set('maxDiscount', e.target.value)} />
        </Field>
      )}
      <Field label="Giá trị đơn tối thiểu" htmlFor={`${id}-minimumOrderValue`} error={errors.minimumOrderValue} optional>
        <input {...bind(id, 'minimumOrderValue', errors)} inputMode="numeric" value={form.minimumOrderValue} onChange={(e) => set('minimumOrderValue', e.target.value)} />
      </Field>
      <Field label="Tổng lượt dùng" htmlFor={`${id}-usageLimit`} error={errors.usageLimit} optional hint="Để trống nếu không giới hạn.">
        <input {...bind(id, 'usageLimit', errors)} inputMode="numeric" value={form.usageLimit} onChange={(e) => set('usageLimit', e.target.value)} />
      </Field>
      <Field label="Lượt mỗi khách" htmlFor={`${id}-usageLimitPerCustomer`} error={errors.usageLimitPerCustomer}>
        <input {...bind(id, 'usageLimitPerCustomer', errors)} inputMode="numeric" value={form.usageLimitPerCustomer} onChange={(e) => set('usageLimitPerCustomer', e.target.value)} />
      </Field>
      <PeriodFields id={id} startAt={form.startAt} endAt={form.endAt} errors={errors} onChange={(key, value) => set(key, value)} required />
      <MarketingStatusField id={id} value={form.status} onChange={(s) => set('status', s)} />
    </FormDialog>
  )
}
