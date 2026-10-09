import { CaretDownIcon, MagnifyingGlassIcon, TShirtIcon, XIcon } from '@phosphor-icons/react'
import { useEffect, useId, useRef, useState } from 'react'
import { catalog } from '../../api/catalog'
import { useDebounced } from '../../components/ui'
import { useAsync } from '../../hooks'

export interface ProductPick {
  id: string
  name: string
  productCode: string
  imageUrl?: string | null
}

/**
 * Searchable product picker (combobox): type to search by name or code, arrow keys to move,
 * Enter to pick, Escape to close. `clearable` shows an "all products" reset for filters.
 */
export function ProductCombobox({
  value,
  onChange,
  placeholder = 'Chọn sản phẩm',
  clearable,
  invalid,
  inputId,
  exclude = [],
}: {
  value: ProductPick | null
  onChange: (product: ProductPick | null) => void
  placeholder?: string
  clearable?: boolean
  invalid?: boolean
  inputId?: string
  /** Product ids to leave out of the results (e.g. already in a collection). */
  exclude?: string[]
}) {
  const listId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const term = useDebounced(query.trim(), 250)
  const results = useAsync(() => (open ? catalog.products.list({ search: term, page: 0, size: 8 }) : Promise.resolve(null)), [open, term])
  const items = (results.data?.content ?? []).filter((product) => !exclude.includes(product.id))

  useEffect(() => setActive(0), [term, open])
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const pick = (index: number) => {
    const product = items[index]
    if (!product) return
    onChange({ id: product.id, name: product.name, productCode: product.productCode, imageUrl: product.images[0]?.url ?? null })
    setOpen(false)
    setQuery('')
  }

  return (
    <div className={`combo${invalid ? ' invalid' : ''}`} ref={rootRef}>
      {open ? (
        <label className="combo-input">
          <MagnifyingGlassIcon size={15} aria-hidden />
          <input
            id={inputId}
            autoFocus
            role="combobox"
            aria-expanded
            aria-controls={listId}
            aria-activedescendant={items[active] ? `${listId}-${active}` : undefined}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') {
                e.preventDefault()
                setActive((i) => Math.min(i + 1, items.length - 1))
              } else if (e.key === 'ArrowUp') {
                e.preventDefault()
                setActive((i) => Math.max(i - 1, 0))
              } else if (e.key === 'Enter') {
                e.preventDefault()
                pick(active)
              } else if (e.key === 'Escape') {
                // Close only the list: preventDefault stops a surrounding <dialog> from closing too.
                e.preventDefault()
                e.stopPropagation()
                setOpen(false)
              }
            }}
            placeholder="Tìm theo tên hoặc mã sản phẩm"
          />
        </label>
      ) : (
        <button type="button" id={inputId} className={clearable && value ? 'combo-trigger has-clear' : 'combo-trigger'} onClick={() => setOpen(true)} aria-haspopup="listbox">
          {value ? (
            <span className="combo-value">
              <span className="truncate">{value.name}</span>
              <span className="combo-code mono muted small">{value.productCode}</span>
            </span>
          ) : (
            <span className="muted">{placeholder}</span>
          )}
          <CaretDownIcon size={14} aria-hidden />
        </button>
      )}
      {clearable && value && !open && (
        <button type="button" className="combo-clear" onClick={() => onChange(null)} aria-label="Bỏ chọn sản phẩm" title="Bỏ chọn">
          <XIcon size={13} />
        </button>
      )}
      {open && (
        <ul className="combo-list" id={listId} role="listbox">
          {results.loading && !results.data ? (
            <li className="combo-empty">Đang tìm...</li>
          ) : items.length === 0 ? (
            <li className="combo-empty">Không có sản phẩm phù hợp.</li>
          ) : (
            items.map((product, index) => (
              <li
                key={product.id}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={index === active}
                className={index === active ? 'active' : undefined}
                onMouseEnter={() => setActive(index)}
                onMouseDown={(e) => {
                  e.preventDefault()
                  pick(index)
                }}
              >
                {product.images[0] ? <img src={product.images[0].url} alt="" /> : <span className="combo-thumb"><TShirtIcon size={14} /></span>}
                <span className="combo-text">
                  <span className="truncate">{product.name}</span>
                  <span className="mono muted small">{product.productCode}</span>
                </span>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
