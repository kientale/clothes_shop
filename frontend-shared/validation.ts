/** Constraint validation shared by the storefront and administration forms. */
export type Control = HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement

/** Whole VND amounts, optionally grouped by one consistent thousand separator. */
export function parseWholeMoney(raw: string): number {
  const value = raw.trim().replace(/\s*[₫đ]$/, '').replace(/\s+/g, ' ')
  if (!/^\d+$/.test(value) && !/^\d{1,3}([., ])\d{3}(?:\1\d{3})*$/.test(value)) return Number.NaN
  const amount = Number(value.replace(/[., ]/g, ''))
  return Number.isSafeInteger(amount) ? amount : Number.NaN
}

/** Yesterday in the browser's local timezone, for backend @Past birth dates. */
export function pastDateMax() {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function isControl(element: EventTarget | null): element is Control {
  return element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement || element instanceof HTMLSelectElement
}

export function controlsOf(form: HTMLFormElement): Control[] {
  return Array.from(form.elements).filter(isControl)
}

export function validationMessage(control: Control): string {
  if (control.matches(':disabled') || control instanceof HTMLInputElement && ['hidden', 'file', 'submit', 'button', 'reset'].includes(control.type)) return ''
  if ('readOnly' in control && control.readOnly) return ''
  const input = control instanceof HTMLInputElement ? control : null
  if (input && ['checkbox', 'radio'].includes(input.type)) {
    return input.validity.valueMissing ? 'Vui lòng chọn một lựa chọn.' : ''
  }
  const raw = control.value
  const value = raw.trim()
  if (input?.validity.badInput) return 'Vui lòng nhập giá trị hợp lệ.'
  if (control.required && !value) return control instanceof HTMLSelectElement ? 'Vui lòng chọn một giá trị.' : 'Vui lòng nhập trường này.'
  if (!value) return ''
  if ('maxLength' in control && control.maxLength >= 0 && raw.length > control.maxLength) return `Tối đa ${control.maxLength} ký tự.`
  if ('minLength' in control && control.minLength > 0 && raw.length < control.minLength) return `Nhập ít nhất ${control.minLength} ký tự.`
  if (input?.type === 'password' || input?.autocomplete.includes('password')) {
    if (new TextEncoder().encode(raw).length > 72) return 'Mật khẩu tối đa 72 byte UTF-8.'
  }
  if (input?.type === 'email' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'Email không hợp lệ (ví dụ: ten@example.com).'
  if (input && (input.type === 'tel' || input.inputMode === 'tel')) {
    const phone = input.dataset.normalizePhone === 'true' ? value.replace(/[\s.]/g, '') : value
    if (!/^\+?[0-9]{8,15}$/.test(phone)) return 'Số điện thoại gồm 8–15 chữ số, có thể bắt đầu bằng +.'
  }
  if (input?.type === 'url' || input?.dataset.validation === 'url') {
    try {
      const url = new URL(value)
      if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || /\s/.test(value)) return 'Nhập đường dẫn http:// hoặc https:// hợp lệ.'
    } catch { return 'Nhập đường dẫn http:// hoặc https:// hợp lệ.' }
  }
  if (input?.dataset.validation === 'username' && value.toLowerCase() !== 'admin' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'Nhập email hợp lệ hoặc tên đăng nhập admin.'
  if (input?.pattern && !new RegExp(`^(?:${input.pattern})$`, 'u').test(value)) return input.dataset.patternMessage || 'Định dạng không hợp lệ.'
  if (input && (input.type === 'number' || input.inputMode === 'numeric' || input.inputMode === 'decimal')) {
    // Formatted VND fields use the same normalization as their request serializers.
    const money = input.dataset.validation === 'money'
    const numberText = money ? String(parseWholeMoney(value)) : value
    const decimal = input.inputMode === 'decimal' || input.step === 'any' || Number(input.step) > 0 && Number(input.step) < 1
    if (!(decimal ? /^\d+(?:\.\d{1,2})?$/ : /^\d+$/).test(numberText)) return money ? 'Nhập số tiền nguyên hợp lệ (ví dụ: 100000 hoặc 100.000).' : decimal ? 'Nhập số không âm, tối đa 2 chữ số thập phân.' : 'Nhập số nguyên không âm.'
    const number = Number(numberText)
    if (!Number.isFinite(number) || number > Number.MAX_SAFE_INTEGER) return 'Giá trị vượt giới hạn cho phép.'
    if (input.min && number < Number(input.min)) return `Giá trị phải từ ${input.min} trở lên.`
    if (input.max && number > Number(input.max)) return `Giá trị tối đa là ${input.max}.`
  }
  if (input && ['date', 'datetime-local'].includes(input.type)) {
    if (Number.isNaN(Date.parse(value))) return 'Ngày giờ không hợp lệ.'
    if (input.min && value < input.min) return 'Ngày giờ phải từ mốc bắt đầu trở đi.'
    if (input.max && value > input.max) return 'Ngày giờ vượt quá giới hạn cho phép.'
  }
  if (input?.dataset.match && input.form) {
    const original = controlsOf(input.form).find((field) => field !== input && field instanceof HTMLInputElement && field.autocomplete === input.dataset.match)
    if (original && raw !== original.value) return 'Mật khẩu xác nhận không khớp.'
  }
  if (control.validity.typeMismatch || control.validity.patternMismatch || control.validity.customError) return 'Vui lòng nhập giá trị hợp lệ.'
  return ''
}
