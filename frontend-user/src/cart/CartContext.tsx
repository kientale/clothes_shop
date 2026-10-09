import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { me } from '../api/store'
import type { ServerCartLine } from '../api/types'
import { useAuth } from '../auth/AuthContext'

/** Per-line cap; the backend also enforces the configured limits at checkout. */
export const MAX_LINE_QUANTITY = 20
const STORAGE_KEY = 'lemonadex.cart'

/**
 * A cart line keeps a snapshot for display only. Prices, promotions and stock are always
 * recomputed by the backend at checkout, and the cart page re-reads current prices.
 */
export interface CartLine {
  variantId: string
  productId: string
  slug: string
  name: string
  imageUrl: string | null
  colorName: string
  sizeName: string
  sku: string
  price: number
  quantity: number
}

interface CartState {
  lines: CartLine[]
  itemCount: number
  subtotal: number
  add: (line: Omit<CartLine, 'quantity'>, quantity: number) => void
  setQuantity: (variantId: string, quantity: number) => void
  /** Refreshes the display snapshot (e.g. a new price) without touching the quantity. */
  update: (variantId: string, patch: Partial<Omit<CartLine, 'variantId' | 'quantity'>>) => void
  remove: (variantId: string) => void
  clear: () => void
}

const CartContext = createContext<CartState | null>(null)

function read(): CartLine[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((line) => typeof line?.variantId === 'string' && line.quantity > 0) : []
  } catch {
    return []
  }
}

/** Lines from both carts; a variant in both keeps the larger quantity. Lines no longer on sale are dropped from the server copy. */
function merge(local: CartLine[], server: ServerCartLine[]): CartLine[] {
  const merged = new Map(local.map((line) => [line.variantId, line]))
  for (const line of server) {
    if (!line.sellable) continue
    const { available: _available, sellable: _sellable, ...snapshot } = line
    const existing = merged.get(line.variantId)
    merged.set(line.variantId, { ...snapshot, quantity: Math.min(MAX_LINE_QUANTITY, Math.max(existing?.quantity ?? 0, line.quantity)) })
  }
  return [...merged.values()]
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>(read)
  const { user } = useAuth()
  const customer = user?.roles.includes('CUSTOMER') ? user.id : null
  // Account whose saved cart has been merged into this browser's cart; from then on every change is saved.
  const synced = useRef<string | null>(null)

  // Signing in merges the saved cart with this browser's. An expired session keeps the cart here; the explicit
  // "Đăng xuất" button empties it (see Layout) without touching the saved copy.
  useEffect(() => {
    if (!customer) {
      synced.current = null
      return
    }
    if (synced.current === customer) return
    let active = true
    me.cart()
      .then((server) => {
        if (!active) return
        synced.current = customer
        setLines((current) => merge(current, server))
      })
      .catch(() => {
        // Offline or no customer profile: the cart simply stays in this browser.
      })
    return () => {
      active = false
    }
  }, [customer])

  // Save the whole cart shortly after each change, so it follows the customer to other devices.
  useEffect(() => {
    if (!customer || synced.current !== customer) return
    const timer = window.setTimeout(() => {
      me.saveCart(lines.map((line) => ({ productVariantId: line.variantId, quantity: line.quantity }))).catch(() => undefined)
    }, 600)
    return () => window.clearTimeout(timer)
  }, [lines, customer])

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(lines))
    } catch {
      // Storage unavailable (private mode): the cart lives for this tab only.
    }
  }, [lines])

  // Keep several open tabs in step.
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) setLines(read())
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [])

  const clamp = (quantity: number) => Math.max(1, Math.min(MAX_LINE_QUANTITY, Math.floor(quantity)))

  const add = useCallback<CartState['add']>((line, quantity) => {
    setLines((current) => {
      const existing = current.find((item) => item.variantId === line.variantId)
      if (existing) return current.map((item) => (item === existing ? { ...item, ...line, quantity: clamp(item.quantity + quantity) } : item))
      return [...current, { ...line, quantity: clamp(quantity) }]
    })
  }, [])

  const value = useMemo<CartState>(
    () => ({
      lines,
      itemCount: lines.reduce((sum, line) => sum + line.quantity, 0),
      subtotal: lines.reduce((sum, line) => sum + line.price * line.quantity, 0),
      add,
      setQuantity: (variantId, quantity) =>
        setLines((current) => current.map((line) => (line.variantId === variantId ? { ...line, quantity: clamp(quantity) } : line))),
      update: (variantId, patch) => setLines((current) => current.map((line) => (line.variantId === variantId ? { ...line, ...patch } : line))),
      remove: (variantId) => setLines((current) => current.filter((line) => line.variantId !== variantId)),
      clear: () => setLines([]),
    }),
    [lines, add],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
