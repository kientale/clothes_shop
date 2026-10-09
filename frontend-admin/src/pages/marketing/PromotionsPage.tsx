import { PercentIcon, XIcon } from '@phosphor-icons/react'
import { useId } from 'react'
import { catalog, categoryPaths } from '../../api/catalog'
import {
  DISCOUNT_TYPE_LABEL,
  MARKETING_STATUS_LABEL,
  PROMOTION_TYPE_LABEL,
  campaignPhase,
  marketing,
  type DiscountType,
  type MarketingStatus,
  type Promotion,
  type PromotionType,
} from '../../api/marketing'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Pill, fromLocalInput, toLocalInput, useCan, windowOf } from '../../components/kit'
import { useProductNames } from '../../components/pickers'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useAsync } from '../../hooks'
import { ProductCombobox } from '../catalog/ProductCombobox'
import { parseMoney } from '../catalog/shared'
import { MarketingStatusField, PeriodFields, checkPeriod, discountText } from './shared'

export default function PromotionsPage() {
  return (
    <ResourcePage<Promotion>
      title="Chương trình khuyến mãi"
      lede="Giảm giá tự động theo sản phẩm hoặc danh mục. Khi nhiều chương trình cùng áp dụng, chương trình có độ ưu tiên cao hơn được chọn."
      addLabel="Tạo chương trình"
      itemLabel="chương trình"
      searchPlaceholder="Tìm theo tên chương trình"
      canWrite={useCan('PROMOTION_WRITE')}
      statusLabels={MARKETING_STATUS_LABEL}
      load={(query) => marketing.promotions.list(query)}
      rowLabel={(p) => p.name}
      columns={[
        {
          header: 'Chương trình',
          render: (p) => (
            <div className="person-text">
              <span className="person-name">{p.name}</span>
              {p.description && <span className="person-sub truncate">{p.description}</span>}
            </div>
          ),
        },
        {
          header: 'Phạm vi',
          render: (p) => (
            <span className="nowrap">
              {PROMOTION_TYPE_LABEL[p.promotionType]}
              {p.promotionType === 'PRODUCT_DISCOUNT' && <span className="muted"> ({p.productIds.length})</span>}
              {p.promotionType === 'CATEGORY_DISCOUNT' && <span className="muted"> ({p.categoryIds.length})</span>}
            </span>
          ),
        },
        { header: 'Mức giảm', render: (p) => <strong>{discountText(p.discountType, p.discountValue)}</strong>, className: 'nowrap' },
        { header: 'Ưu tiên', render: (p) => p.priority, className: 'num' },
        { header: 'Thời gian', render: (p) => <span className="small">{windowOf(p.startAt, p.endAt)}</span> },
        {
          header: 'Trạng thái',
          render: (p) => {
            const [label, tone] = campaignPhase(p.startAt, p.endAt, p.status)
            return <Pill tone={tone}>{label}</Pill>
          },
        },
      ]}
      deleteTitle="Xóa chương trình"
      deleteMessage={(p) => (
        <>
          Xóa chương trình <strong>{p.name}</strong>? Đơn đã đặt giữ nguyên mức giảm đã áp dụng.
        </>
      )}
      remove={(p) => marketing.promotions.remove(p.id)}
      emptyIcon={<PercentIcon size={36} aria-hidden />}
      renderForm={(slot) => <PromotionForm {...slot} />}
    />
  )
}

function PromotionForm({ open, item, onClose, onSaved }: FormSlot<Promotion>) {
  const id = useId()
  const options = useAsync(() => (open ? catalog.options() : Promise.resolve(null)), [open])
  const categories = options.data ? categoryPaths(options.data.categories) : []
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      name: item?.name ?? '',
      description: item?.description ?? '',
      promotionType: (item?.promotionType ?? 'ALL_PRODUCTS') as PromotionType,
      discountType: (item?.discountType ?? 'PERCENTAGE') as DiscountType,
      discountValue: item ? String(item.discountValue) : '',
      priority: item ? String(item.priority) : '0',
      startAt: toLocalInput(item?.startAt),
      endAt: toLocalInput(item?.endAt),
      status: (item?.status ?? 'ACTIVE') as MarketingStatus,
      productIds: item?.productIds ?? [],
      categoryIds: item?.categoryIds ?? [],
    }),
    [item?.id],
  )
  const productNames = useProductNames(form.productIds)

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa chương trình' : 'Tạo chương trình khuyến mãi'}
      description="Giảm cố định tính trên mỗi đơn vị sản phẩm."
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Tạo chương trình'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.name.trim()) e.name = 'Nhập tên chương trình.'
          const value = parseMoney(f.discountValue)
          if (Number.isNaN(value) || value <= 0) e.discountValue = 'Nhập mức giảm lớn hơn 0.'
          else if (f.discountType === 'PERCENTAGE' && value > 100) e.discountValue = 'Phần trăm tối đa 100.'
          if (!/^-?\d+$/.test(f.priority.trim())) e.priority = 'Nhập số nguyên.'
          if (f.promotionType === 'PRODUCT_DISCOUNT' && f.productIds.length === 0) e.productIds = 'Chọn ít nhất một sản phẩm.'
          if (f.promotionType === 'CATEGORY_DISCOUNT' && f.categoryIds.length === 0) e.categoryIds = 'Chọn ít nhất một danh mục.'
          return { ...e, ...checkPeriod(f.startAt, f.endAt, true) }
        },
        async (f) => {
          const body = {
            name: f.name.trim(),
            description: f.description.trim() || null,
            promotionType: f.promotionType,
            discountType: f.discountType,
            discountValue: parseMoney(f.discountValue),
            startAt: fromLocalInput(f.startAt)!,
            endAt: fromLocalInput(f.endAt)!,
            priority: Number(f.priority),
            status: f.status,
            productIds: f.promotionType === 'PRODUCT_DISCOUNT' ? f.productIds : [],
            categoryIds: f.promotionType === 'CATEGORY_DISCOUNT' ? f.categoryIds : [],
          }
          if (item) await marketing.promotions.update(item.id, body)
          else await marketing.promotions.create(body)
          onSaved(item ? `Đã cập nhật ${body.name}.` : `Đã tạo chương trình ${body.name}.`)
        },
      )}
    >
      <Field label="Tên chương trình" htmlFor={`${id}-name`} error={errors.name} wide>
        <input {...bind(id, 'name', errors)} maxLength={150} value={form.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <Field label="Mô tả" htmlFor={`${id}-description`} optional wide>
        <textarea id={`${id}-description`} rows={2} maxLength={1000} value={form.description} onChange={(e) => set('description', e.target.value)} />
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
      <Field label={form.discountType === 'PERCENTAGE' ? 'Phần trăm giảm' : 'Số tiền giảm mỗi sản phẩm'} htmlFor={`${id}-discountValue`} error={errors.discountValue}>
        <input {...bind(id, 'discountValue', errors)} inputMode="decimal" value={form.discountValue} onChange={(e) => set('discountValue', e.target.value)} />
      </Field>
      <PeriodFields id={id} startAt={form.startAt} endAt={form.endAt} errors={errors} onChange={(key, value) => set(key, value)} required />
      <Field label="Độ ưu tiên" htmlFor={`${id}-priority`} error={errors.priority} hint="Số lớn hơn được chọn trước.">
        <input {...bind(id, 'priority', errors)} inputMode="numeric" value={form.priority} onChange={(e) => set('priority', e.target.value)} />
      </Field>
      <MarketingStatusField id={id} value={form.status} onChange={(s) => set('status', s)} />

      <fieldset className="choice-group span-2">
        <legend className="field-label">Áp dụng cho</legend>
        <div className="choice-list">
          {(Object.keys(PROMOTION_TYPE_LABEL) as PromotionType[]).map((t) => (
            <label key={t} className="choice">
              <input type="radio" name={`${id}-scope`} checked={form.promotionType === t} onChange={() => set('promotionType', t)} />
              <span>{PROMOTION_TYPE_LABEL[t]}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {form.promotionType === 'PRODUCT_DISCOUNT' && (
        <Field label="Sản phẩm áp dụng" htmlFor={`${id}-products`} error={errors.productIds} wide>
          <ProductCombobox
            inputId={`${id}-products`}
            value={null}
            exclude={form.productIds}
            placeholder="Thêm sản phẩm"
            invalid={Boolean(errors.productIds)}
            onChange={(p) => p && set('productIds', [...form.productIds, p.id])}
          />
          {form.productIds.length > 0 && (
            <div className="chips picked">
              {form.productIds.map((pid) => (
                <span key={pid} className="chip chip-sm">
                  {productNames[pid] ?? '...'}
                  <button type="button" className="chip-x" onClick={() => set('productIds', form.productIds.filter((x) => x !== pid))} aria-label="Bỏ sản phẩm">
                    <XIcon size={11} />
                  </button>
                </span>
              ))}
            </div>
          )}
        </Field>
      )}

      {form.promotionType === 'CATEGORY_DISCOUNT' && (
        <fieldset className="check-group span-2">
          <legend className="field-label">Danh mục áp dụng</legend>
          <div className="check-list compact">
            {categories.map((c) => (
              <label key={c.id} className="check">
                <input
                  type="checkbox"
                  checked={form.categoryIds.includes(c.id)}
                  onChange={(e) => set('categoryIds', e.target.checked ? [...form.categoryIds, c.id] : form.categoryIds.filter((x) => x !== c.id))}
                />
                <span className="check-title">{c.path}</span>
              </label>
            ))}
          </div>
          {errors.categoryIds && (
            <span className="field-error" role="alert">
              {errors.categoryIds}
            </span>
          )}
        </fieldset>
      )}
    </FormDialog>
  )
}
