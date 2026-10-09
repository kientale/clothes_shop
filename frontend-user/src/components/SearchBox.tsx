import { MagnifyingGlassIcon, TShirtIcon, XIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { store } from '../api/store'
import type { Suggestions } from '../api/types'
import { formatMoney } from '../format'

interface Option {
  key: string
  to: string
  label: string
}

/**
 * Header search with suggestions as you type: matching products, categories and brands. Arrow keys move through
 * the list, Enter opens the highlighted entry or searches for the words typed.
 */
export function SearchBox({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const [term, setTerm] = useState('')
  const [found, setFound] = useState<Suggestions | null>(null)
  const [active, setActive] = useState(-1)
  const box = useRef<HTMLFormElement>(null)

  useEffect(() => {
    const value = term.trim()
    if (value.length < 2) {
      setFound(null)
      return
    }
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      store.suggest(value, controller.signal).then(setFound).catch(() => undefined)
    }, 220)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [term])

  useEffect(() => setActive(-1), [found])

  useEffect(() => {
    const close = (event: PointerEvent) => !box.current?.contains(event.target as Node) && onClose()
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [onClose])

  const options: Option[] = [
    ...(found?.products ?? []).map((p) => ({ key: p.id, to: `/shop/products/${p.slug}`, label: p.name })),
    ...(found?.categories ?? []).map((c) => ({ key: c.id, to: `/shop/products?category=${c.id}`, label: c.name })),
    ...(found?.brands ?? []).map((b) => ({ key: b.id, to: `/shop/products?brand=${b.id}`, label: b.name })),
  ]

  const go = (to: string) => {
    navigate(to)
    onClose()
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (active >= 0 && options[active]) return go(options[active].to)
    const value = term.trim()
    go(value ? `/shop/products?search=${encodeURIComponent(value)}` : '/shop/products')
  }

  const keys = (event: KeyboardEvent) => {
    if (event.key === 'Escape') onClose()
    if (!options.length) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((i) => (i + 1) % options.length)
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => (i <= 0 ? options.length - 1 : i - 1))
    }
  }

  const index = (key: string) => options.findIndex((o) => o.key === key)
  const empty = found && options.length === 0

  return (
    <form ref={box} className="header-search search-box" onSubmit={submit} role="search">
      <MagnifyingGlassIcon size={18} aria-hidden />
      <label htmlFor="site-search" className="sr-only">
        Tìm sản phẩm
      </label>
      <input
        id="site-search"
        autoFocus
        autoComplete="off"
        placeholder="Tìm áo, quần, váy..."
        value={term}
        onChange={(e) => setTerm(e.target.value)}
        onKeyDown={keys}
        role="combobox"
        aria-expanded={!!found}
        aria-controls="search-suggestions"
        aria-activedescendant={active >= 0 ? `suggestion-${options[active]?.key}` : undefined}
      />
      <button type="button" className="icon-btn" onClick={onClose} aria-label="Đóng tìm kiếm">
        <XIcon size={16} />
      </button>
      {found && (
        <div id="search-suggestions" className="search-suggestions" role="listbox" aria-label="Gợi ý tìm kiếm">
          {empty && <p className="muted small search-empty">Không có gợi ý. Nhấn Enter để tìm "{term.trim()}".</p>}
          {found.products.length > 0 && (
            <ul>
              {found.products.map((p) => (
                <li key={p.id} id={`suggestion-${p.id}`} role="option" aria-selected={index(p.id) === active}>
                  <button type="button" className="suggestion-product" onClick={() => go(`/shop/products/${p.slug}`)}>
                    <span className="suggestion-thumb">{p.imageUrl ? <img src={p.imageUrl} alt="" /> : <TShirtIcon size={18} aria-hidden />}</span>
                    <span className="suggestion-name">{p.name}</span>
                    <span className="muted small">{formatMoney(p.minPrice)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {(found.categories.length > 0 || found.brands.length > 0) && (
            <ul className="suggestion-chips">
              {found.categories.map((c) => (
                <li key={c.id} id={`suggestion-${c.id}`} role="option" aria-selected={index(c.id) === active}>
                  <button type="button" className="category-chip" onClick={() => go(`/shop/products?category=${c.id}`)}>
                    {c.name}
                  </button>
                </li>
              ))}
              {found.brands.map((b) => (
                <li key={b.id} id={`suggestion-${b.id}`} role="option" aria-selected={index(b.id) === active}>
                  <button type="button" className="category-chip" onClick={() => go(`/shop/products?brand=${b.id}`)}>
                    {b.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </form>
  )
}
