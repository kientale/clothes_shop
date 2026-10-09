import { LightningIcon, PlusIcon, TrashIcon } from '@phosphor-icons/react'
import { useId } from 'react'
import { MARKETING_STATUS_LABEL, campaignPhase, marketing, type FlashSale, type MarketingStatus } from '../../api/marketing'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Pill, SearchPicker, countOf, fromLocalInput, toLocalInput, useCan, vndOf, windowOf, type PickOption } from '../../components/kit'
import { searchVariants, useVariantNames, type VariantOption } from '../../components/pickers'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { parseMoney } from '../catalog/shared'
import { MarketingStatusField, PeriodFields, checkPeriod } from './shared'

export default function FlashSalesPage() {
  return (
    <ResourcePage<FlashSale>
      title="Flash Sale"
      lede="Giá sốc theo từng biến thể trong khung giờ ngắn, giới hạn số suất. Giá flash phải thấp hơn giá bán hiện tại."
      addLabel="Tạo Flash Sale"
      itemLabel="chiến dịch"
      searchPlaceholder="Tìm theo tên chiến dịch"
      canWrite={useCan('FLASH_SALE_WRITE')}
      statusLabels={MARKETING_STATUS_LABEL}
      load={(query) => marketing.flashSales.list(query)}
      rowLabel={(f) => f.name}
      columns={[
        { header: 'Chiến dịch', render: (f) => <span className="person-name">{f.name}</span> },
        { header: 'Biến thể', render: (f) => f.items.length, className: 'num' },
        {
          header: 'Đã bán / suất',
          render: (f) => {
            const sold = f.items.reduce((s, i) => s + i.soldQuantity, 0)
            const limit = f.items.reduce((s, i) => s + i.quantityLimit, 0)
            return (
              <span className="nowrap">
                <strong>{countOf(sold)}</strong>
                <span className="muted"> / {countOf(limit)}</span>
              </span>
            )
          },
        },
        { header: 'Thời gian', render: (f) => <span className="small">{windowOf(f.startAt, f.endAt)}</span> },
        {
          header: 'Trạng thái',
          render: (f) => {
            const [label, tone] = campaignPhase(f.startAt, f.endAt, f.status)
            return <Pill tone={tone}>{label}</Pill>
          },
        },
      ]}
      deleteTitle="Xóa Flash Sale"
      deleteMessage={(f) => (
        <>
          Xóa chiến dịch <strong>{f.name}</strong>? Các đơn đã đặt vẫn giữ giá flash đã áp dụng.
        </>
      )}
      remove={(f) => marketing.flashSales.remove(f.id)}
      emptyIcon={<LightningIcon size={36} aria-hidden />}
      renderForm={(slot) => <FlashSaleForm {...slot} />}
    />
  )
}

interface FlashLine {
  key: string
  variant: PickOption | null
  /** Current list price, known for newly picked variants. */
  price: number | null
  flashPrice: string
  quantityLimit: string
  sold: number
}

let lineSeq = 0

function FlashSaleForm({ open, item, onClose, onSaved }: FormSlot<FlashSale>) {
  const id = useId()
  const names = useVariantNames(item?.items.map((i) => i.productVariantId) ?? [])
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      name: item?.name ?? '',
      startAt: toLocalInput(item?.startAt),
      endAt: toLocalInput(item?.endAt),
      status: (item?.status ?? 'ACTIVE') as MarketingStatus,
      lines: (item?.items ?? []).map<FlashLine>((i) => ({
        key: i.id,
        variant: { id: i.productVariantId, title: '' },
        price: null,
        flashPrice: String(i.flashPrice),
        quantityLimit: String(i.quantityLimit),
        sold: i.soldQuantity,
      })),
    }),
    [item?.id],
  )
  const update = (key: string, patch: Partial<FlashLine>) => set('lines', form.lines.map((l) => (l.key === key ? { ...l, ...patch } : l)))
  const addLine = () => set('lines', [...form.lines, { key: `new-${++lineSeq}`, variant: null, price: null, flashPrice: '', quantityLimit: '', sold: 0 }])

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa Flash Sale' : 'Tạo Flash Sale'}
      description="Dòng đã có đơn sử dụng không xóa được và không giảm suất dưới số đã bán."
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Tạo Flash Sale'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.name.trim()) e.name = 'Nhập tên chiến dịch.'
          const lines = f.lines.filter((l) => l.variant)
          if (lines.length === 0) e.items = 'Thêm ít nhất một biến thể.'
          else if (new Set(lines.map((l) => l.variant!.id)).size !== lines.length) e.items = 'Mỗi biến thể chỉ có một dòng.'
          else if (lines.some((l) => Number.isNaN(parseMoney(l.flashPrice)))) e.items = 'Nhập giá flash cho mọi dòng.'
          else if (lines.some((l) => l.price !== null && parseMoney(l.flashPrice) >= l.price)) e.items = 'Giá flash phải thấp hơn giá bán hiện tại.'
          else if (lines.some((l) => !/^[1-9]\d*$/.test(l.quantityLimit.trim()))) e.items = 'Số suất phải là số nguyên dương.'
          else if (lines.some((l) => Number(l.quantityLimit) < l.sold)) e.items = 'Số suất không được thấp hơn số đã bán.'
          return { ...e, ...checkPeriod(f.startAt, f.endAt, true) }
        },
        async (f) => {
          const body = {
            name: f.name.trim(),
            startAt: fromLocalInput(f.startAt)!,
            endAt: fromLocalInput(f.endAt)!,
            status: f.status,
            items: f.lines.filter((l) => l.variant).map((l) => ({ productVariantId: l.variant!.id, flashPrice: parseMoney(l.flashPrice), quantityLimit: Number(l.quantityLimit) })),
          }
          if (item) await marketing.flashSales.update(item.id, body)
          else await marketing.flashSales.create(body)
          onSaved(item ? `Đã cập nhật ${body.name}.` : `Đã tạo Flash Sale ${body.name}.`)
        },
      )}
    >
      <Field label="Tên chiến dịch" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} maxLength={150} value={form.name} onChange={(e) => set('name', e.target.value)} />
      </Field>
      <MarketingStatusField id={id} value={form.status} onChange={(s) => set('status', s)} />
      <PeriodFields id={id} startAt={form.startAt} endAt={form.endAt} errors={errors} onChange={(key, value) => set(key, value)} required />

      <fieldset className="line-editor span-2">
        <legend className="field-label">Biến thể và giá flash</legend>
        <div className="line-head flash" aria-hidden>
          <span>Biến thể</span>
          <span>Giá flash</span>
          <span>Số suất</span>
          <span />
        </div>
        {form.lines.map((line, index) => (
          <div key={line.key} className="line-row flash">
            {line.sold > 0 || (item && !line.key.startsWith('new-')) ? (
              <span className="person-text">
                <span className="person-name truncate">{names[line.variant!.id] ?? '...'}</span>
                <span className="person-sub">Đã bán {countOf(line.sold)}</span>
              </span>
            ) : (
              <SearchPicker
                value={line.variant}
                onChange={(v) => update(line.key, { variant: v, price: (v as VariantOption | null)?.price ?? null })}
                search={searchVariants}
                placeholder={`Chọn biến thể ${index + 1}`}
                searchPlaceholder="Tìm theo SKU hoặc tên sản phẩm"
              />
            )}
            <input
              aria-label={`Giá flash dòng ${index + 1}`}
              inputMode="numeric"
              placeholder={line.price !== null ? `< ${vndOf(line.price)}` : '0'}
              value={line.flashPrice}
              onChange={(e) => update(line.key, { flashPrice: e.target.value })}
            />
            <input aria-label={`Số suất dòng ${index + 1}`} inputMode="numeric" value={line.quantityLimit} onChange={(e) => update(line.key, { quantityLimit: e.target.value })} />
            <button
              type="button"
              className="icon-btn danger"
              disabled={line.sold > 0}
              onClick={() => set('lines', form.lines.filter((l) => l.key !== line.key))}
              aria-label={`Xóa dòng ${index + 1}`}
              title={line.sold > 0 ? 'Dòng đã có đơn, không xóa được' : 'Xóa dòng'}
            >
              <TrashIcon size={15} />
            </button>
          </div>
        ))}
        {errors.items && (
          <span className="field-error" role="alert">
            {errors.items}
          </span>
        )}
        <div className="line-foot">
          <button type="button" className="btn btn-plain btn-sm" onClick={addLine} disabled={form.lines.length >= 100}>
            <PlusIcon size={14} /> Thêm biến thể
          </button>
        </div>
      </fieldset>
    </FormDialog>
  )
}
