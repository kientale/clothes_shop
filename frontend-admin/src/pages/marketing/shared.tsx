import { MARKETING_STATUS_LABEL, type DiscountType, type MarketingStatus } from '../../api/marketing'
import { Field, bind } from '../../components/forms'
import { vndOf } from '../../components/kit'

export function discountText(type: DiscountType, value: number) {
  return type === 'PERCENTAGE' ? `-${value}%` : `-${vndOf(value)}`
}

/** Start/end errors for two datetime-local values. */
export function checkPeriod(startAt: string, endAt: string, required: boolean): Record<string, string> {
  const e: Record<string, string> = {}
  if (required && !startAt) e.startAt = 'Chọn thời gian bắt đầu.'
  if (required && !endAt) e.endAt = 'Chọn thời gian kết thúc.'
  if (startAt && endAt && new Date(endAt) <= new Date(startAt)) e.endAt = 'Kết thúc phải sau thời gian bắt đầu.'
  return e
}

export function PeriodFields({
  id,
  startAt,
  endAt,
  errors,
  onChange,
  required,
}: {
  id: string
  startAt: string
  endAt: string
  errors: Record<string, string>
  onChange: (key: 'startAt' | 'endAt', value: string) => void
  required?: boolean
}) {
  return (
    <>
      <Field label="Bắt đầu" htmlFor={`${id}-startAt`} error={errors.startAt} optional={!required}>
        <input {...bind(id, 'startAt', errors)} type="datetime-local" value={startAt} onChange={(e) => onChange('startAt', e.target.value)} />
      </Field>
      <Field label="Kết thúc" htmlFor={`${id}-endAt`} error={errors.endAt} optional={!required} hint="Hiệu lực đến trước thời điểm này.">
        <input {...bind(id, 'endAt', errors)} type="datetime-local" value={endAt} min={startAt || undefined} onChange={(e) => onChange('endAt', e.target.value)} />
      </Field>
    </>
  )
}

export function MarketingStatusField({ id, value, onChange }: { id: string; value: MarketingStatus; onChange: (value: MarketingStatus) => void }) {
  return (
    <Field label="Trạng thái" htmlFor={`${id}-status`} hint="Tắt để tạm dừng mà không xóa.">
      <select id={`${id}-status`} value={value} onChange={(e) => onChange(e.target.value as MarketingStatus)}>
        {(Object.keys(MARKETING_STATUS_LABEL) as MarketingStatus[]).map((s) => (
          <option key={s} value={s}>
            {MARKETING_STATUS_LABEL[s]}
          </option>
        ))}
      </select>
    </Field>
  )
}
