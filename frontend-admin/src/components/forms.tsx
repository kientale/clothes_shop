import { useEffect, useId, useState, type DependencyList, type FormEvent, type ReactNode } from 'react'
import { accessErrorMessage, fieldErrors } from '../api/access'
import { Modal } from './ui'

/**
 * Form state for a dialog: values, per-field errors (cleared when that field is edited),
 * a server error and the busy flag. Values reset whenever `open` or `deps` change.
 */
export function useDialogForm<T extends object>(open: boolean, initial: () => T, deps: DependencyList = []) {
  const [form, setForm] = useState<T>(initial)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!open) return
    setForm(initial())
    setErrors({})
    setError(undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, ...deps])

  const set = <K extends keyof T>(key: K, value: T[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => {
      if (!(key as string in current)) return current
      const { [key as string]: _cleared, ...rest } = current
      return rest
    })
  }

  /** Runs client validation, then `action`; server field errors land on the matching fields. */
  const submit = (validate: (form: T) => Record<string, string>, action: (form: T) => Promise<void>) => async (event: FormEvent) => {
    event.preventDefault()
    const next = validate(form)
    setErrors(next)
    setError(undefined)
    if (Object.keys(next).length) return
    setBusy(true)
    try {
      await action(form)
    } catch (err) {
      setErrors(fieldErrors(err))
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return { form, set, setForm, errors, error, busy, submit }
}

export function FormDialog({
  open,
  title,
  description,
  busy,
  disabled,
  submitLabel,
  error,
  onClose,
  onSubmit,
  size = 'lg',
  children,
}: {
  open: boolean
  title: string
  description?: ReactNode
  busy: boolean
  /** Extra reason to block saving, e.g. an image still uploading. */
  disabled?: boolean
  submitLabel: string
  error?: unknown
  onClose: () => void
  onSubmit: (event: FormEvent) => void
  size?: 'sm' | 'md' | 'lg'
  children: ReactNode
}) {
  const id = useId()
  return (
    <Modal
      open={open}
      title={title}
      description={description}
      onClose={busy ? () => {} : onClose}
      size={size}
      footer={
        <>
          <button type="button" className="btn btn-plain" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="submit" form={`${id}-form`} className="btn btn-primary" disabled={busy || disabled}>
            {busy ? 'Đang lưu...' : submitLabel}
          </button>
        </>
      }
    >
      <form id={`${id}-form`} className="form-grid" onSubmit={onSubmit} noValidate>
        {error ? (
          <div className="banner banner-error span-2" role="alert">
            {accessErrorMessage(error)}
          </div>
        ) : null}
        {children}
      </form>
    </Modal>
  )
}

/** Label above the control, then the error or the hint below it. */
export function Field({
  label,
  htmlFor,
  optional,
  error,
  hint,
  wide,
  children,
}: {
  label: string
  htmlFor?: string
  optional?: boolean
  error?: string
  hint?: ReactNode
  /** Span both form columns. */
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className={`field${wide ? ' span-2' : ''}`}>
      <label className="field-label" htmlFor={htmlFor}>
        {label} {optional && <span className="optional">(không bắt buộc)</span>}
      </label>
      {children}
      {error ? (
        <span className="field-error" role="alert">
          {error}
        </span>
      ) : hint ? (
        <span className="field-hint">{hint}</span>
      ) : null}
    </div>
  )
}

/** Props for an input bound to a form field: id plus the invalid state. */
export function bind(id: string, key: string, errors: Record<string, string>) {
  return { id: `${id}-${key}`, 'aria-invalid': errors[key] ? true : undefined }
}
