import { useId, useLayoutEffect, useState, type ComponentProps, type SubmitEvent } from 'react'
import { createPortal } from 'react-dom'
import { controlsOf, isControl, validationMessage, type Control } from '../../../frontend-shared/validation'

type Issue = { control: Control; message: string; id: string }

/** Validate on blur and submit, then keep visible errors current as the user edits. */
export function ValidatedForm({ onSubmit, onBlurCapture, onInputCapture, children, ...props }: ComponentProps<'form'>) {
  const prefix = useId()
  const [issues, setIssues] = useState<Issue[]>([])
  const issueFor = (control: Control, form: HTMLFormElement): Issue | undefined => {
    const message = validationMessage(control)
    return message ? { control, message, id: `${prefix}-error-${controlsOf(form).indexOf(control)}` } : undefined
  }
  // Reconcile errors after resets, address autofill, and conditional fields change.
  useLayoutEffect(() => {
    if (!issues.some(({ control, message }) => !control.isConnected || validationMessage(control) !== message)) return
    setIssues((current) => current.flatMap((issue) => {
      const message = issue.control.isConnected ? validationMessage(issue.control) : ''
      return message ? [{ ...issue, message }] : []
    }))
  })
  useLayoutEffect(() => {
    const restore = issues.map(({ control, id }) => {
      const invalid = control.getAttribute('aria-invalid')
      const description = control.getAttribute('aria-describedby')
      control.setAttribute('aria-invalid', 'true')
      control.setAttribute('aria-describedby', [description, id].filter(Boolean).join(' '))
      return () => {
        if (invalid === null) control.removeAttribute('aria-invalid')
        else control.setAttribute('aria-invalid', invalid)
        if (description === null) control.removeAttribute('aria-describedby')
        else control.setAttribute('aria-describedby', description)
      }
    })
    return () => restore.forEach((reset) => reset())
  }, [issues])

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    const next = controlsOf(event.currentTarget).flatMap((control) => {
      const issue = issueFor(control, event.currentTarget)
      return issue ? [issue] : []
    })
    setIssues(next)
    if (next.length) {
      event.preventDefault()
      next[0].control.focus()
      return
    }
    onSubmit?.(event)
  }

  return (
    <form
      {...props}
      noValidate
      onSubmit={submit}
      onBlurCapture={(event) => {
        onBlurCapture?.(event)
        // The submit handler validates all controls. Avoid moving the button between
        // pointerdown and click when a blur error adds a line above it.
        const next = event.relatedTarget
        if (next instanceof HTMLButtonElement && next.type === 'submit' && next.form === event.currentTarget) return
        if (!isControl(event.target)) return
        const control = event.target
        const issue = issueFor(control, event.currentTarget)
        setIssues((current) => [...current.filter((entry) => entry.control !== control && entry.control.isConnected), ...(issue ? [issue] : [])])
      }}
      onInputCapture={(event) => {
        onInputCapture?.(event)
        if (!issues.length) return
        const form = event.currentTarget
        setIssues((current) => current.flatMap(({ control }) => {
          if (!control.isConnected) return []
          const issue = issueFor(control, form)
          return issue ? [issue] : []
        }))
      }}
    >
      {children}
      {issues.filter(({ control }) => control.isConnected).map(({ control, message, id }) => {
        const container = control.closest('.field, .signin-field') ?? control.parentElement
        return container ? createPortal(<span id={id} className="field-error client-field-error" role="alert">{message}</span>, container, id) : null
      })}
    </form>
  )
}
