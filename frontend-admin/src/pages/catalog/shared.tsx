import { CATALOG_STATUS_LABEL, type CatalogStatus } from '../../api/catalog'
import { formatMoney } from '../../format'
import { Field } from '../../components/forms'

const URL_PATTERN = /^https?:\/\/\S+$/

/** Error entry when `value` is set but not an http(s) URL. */
export function checkUrl(key: string, value: string): Record<string, string> {
  return value.trim() && !URL_PATTERN.test(value.trim()) ? { [key]: 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.' } : {}
}

export function StatusSelect({
  id,
  value,
  onChange,
  hint = 'Mục đang ẩn không hiện ở cửa hàng nhưng vẫn giữ dữ liệu.',
}: {
  id: string
  value: CatalogStatus
  onChange: (status: CatalogStatus) => void
  hint?: string
}) {
  return (
    <Field label="Trạng thái" htmlFor={`${id}-status`} hint={hint}>
      <select id={`${id}-status`} value={value} onChange={(e) => onChange(e.target.value as CatalogStatus)}>
        {(Object.keys(CATALOG_STATUS_LABEL) as CatalogStatus[]).map((status) => (
          <option key={status} value={status}>
            {CATALOG_STATUS_LABEL[status]}
          </option>
        ))}
      </select>
    </Field>
  )
}

/** Color dot; a dashed ring when the color has no hex code. */
export function Swatch({ hex, size = 18 }: { hex: string | null; size?: number }) {
  return (
    <span
      className={`swatch${hex ? '' : ' empty'}`}
      style={{ width: size, height: size, background: hex ?? undefined }}
      aria-hidden
    />
  )
}

/** Parses a VND amount typed with or without thousand separators; NaN when invalid. */
export function parseMoney(value: string) {
  const digits = value.replace(/[.\s,₫đ]/g, '')
  return /^\d+$/.test(digits) ? Number(digits) : Number.NaN
}

export function money(value: number | null | undefined) {
  return value === null || value === undefined ? '-' : formatMoney(value)
}

/** Product price range from its variants, falling back to the base price. */
export function priceRange(base: number, min: number | null, max: number | null) {
  if (min === null || max === null) return money(base)
  return min === max ? money(min) : `${money(min)} - ${money(max)}`
}
