import { CrosshairIcon, MagnifyingGlassIcon, MapPinIcon, SpinnerGapIcon } from '@phosphor-icons/react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { provider, reverseGeocode, searchAddresses, type AddressParts, type AddressSuggestion } from './geocoder'

/**
 * Search box above the checkout address fields: type a street or place and pick a suggestion, or use
 * the device location. Either way it only fills the fields below, which stay editable.
 */
export function AddressSearch({ onPick }: { onPick: (parts: AddressParts) => void }) {
  const id = useId()
  const [query, setQuery] = useState('')
  const [items, setItems] = useState<AddressSuggestion[]>([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [loading, setLoading] = useState(false)
  const [locating, setLocating] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const box = useRef<HTMLDivElement>(null)
  // The text of a picked suggestion: shown in the box, but not searched again.
  const picked = useRef<string | null>(null)

  // Debounced lookup; a newer keystroke aborts the request in flight.
  useEffect(() => {
    if (query.trim().length < 3 || query === picked.current) {
      setItems([])
      setLoading(false)
      return
    }
    const controller = new AbortController()
    setLoading(true)
    const timer = window.setTimeout(() => {
      searchAddresses(query, controller.signal)
        .then((found) => {
          setItems(found)
          setActive(found.length ? 0 : -1)
          setMessage(found.length ? null : 'Không tìm thấy địa chỉ phù hợp. Bạn có thể nhập tay bên dưới.')
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted) return
          console.warn('Address search failed', err)
          setItems([])
          setMessage('Chưa tìm được địa chỉ lúc này. Bạn vẫn có thể nhập tay bên dưới.')
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false)
        })
    }, 350)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
    }
  }, [query])

  // Close the list on a click outside.
  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])

  const choose = async (item: AddressSuggestion) => {
    setOpen(false)
    const label = [item.title, item.detail].filter(Boolean).join(', ')
    picked.current = label
    setQuery(label)
    try {
      onPick(await item.resolve())
      setMessage('Đã điền địa chỉ bên dưới, bạn kiểm tra lại số nhà nhé.')
    } catch {
      setMessage('Không lấy được chi tiết địa chỉ này. Bạn vui lòng nhập tay bên dưới.')
    }
  }

  const locate = () => {
    if (!('geolocation' in navigator)) {
      setMessage('Trình duyệt không hỗ trợ lấy vị trí.')
      return
    }
    setLocating(true)
    setMessage(null)
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const parts = await reverseGeocode(coords.latitude, coords.longitude)
          if (parts) {
            onPick(parts)
            setMessage('Đã điền theo vị trí hiện tại, bạn kiểm tra lại số nhà nhé.')
          } else {
            setMessage('Không xác định được địa chỉ tại vị trí này.')
          }
        } catch {
          setMessage('Không xác định được địa chỉ tại vị trí này.')
        } finally {
          setLocating(false)
        }
      },
      (err) => {
        setLocating(false)
        setMessage(err.code === err.PERMISSION_DENIED ? 'Bạn chưa cho phép truy cập vị trí.' : 'Không lấy được vị trí hiện tại.')
      },
      { enableHighAccuracy: true, timeout: 10000 },
    )
  }

  const onKey = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      if (items.length) setActive((i) => (i + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length)
    } else if (event.key === 'Enter' && open && items[active]) {
      event.preventDefault()
      void choose(items[active])
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const listId = `${id}-list`
  const showList = open && items.length > 0

  return (
    <div className="address-search" ref={box}>
      <label className="field-label" htmlFor={`${id}-input`}>
        Tìm địa chỉ nhanh
      </label>
      <div className="address-search-row">
        <div className="address-search-box">
          <MagnifyingGlassIcon size={18} aria-hidden className="address-search-icon" />
          <input
            id={`${id}-input`}
            type="search"
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && active >= 0 ? `${id}-opt-${active}` : undefined}
            autoComplete="off"
            placeholder="Nhập số nhà, tên đường, tòa nhà..."
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              setOpen(true)
              setMessage(null)
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={onKey}
          />
          {loading && <SpinnerGapIcon size={18} className="address-search-spin" aria-label="Đang tìm" />}
        </div>
        <button type="button" className="btn btn-outline address-locate" onClick={locate} disabled={locating}>
          {locating ? <SpinnerGapIcon size={16} className="address-search-spin" aria-hidden /> : <CrosshairIcon size={16} aria-hidden />}
          Vị trí của tôi
        </button>
        {showList && (
          <ul className="address-options" id={listId} role="listbox">
            {items.map((item, index) => (
              <li
                key={item.id}
                id={`${id}-opt-${index}`}
                role="option"
                aria-selected={index === active}
                onPointerEnter={() => setActive(index)}
                onPointerDown={(e) => e.preventDefault()}
                onClick={() => void choose(item)}
              >
                <MapPinIcon size={16} aria-hidden />
                <span>
                  <strong>{item.title}</strong>
                  {item.detail && <small>{item.detail}</small>}
                </span>
              </li>
            ))}
            <li className="address-options-credit" aria-hidden>
              {provider === 'google' ? 'Gợi ý từ Google Maps' : 'Dữ liệu bản đồ © OpenStreetMap'}
            </li>
          </ul>
        )}
      </div>
      {message && (
        <p className="field-hint" role="status">
          {message}
        </p>
      )}
    </div>
  )
}
