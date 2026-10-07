import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { CartResponse } from '../api/types'
import { useAuth } from '../auth/AuthContext'

export const MAX_LINE_QUANTITY = 99

interface CartState {
  cart: CartResponse | null
  itemCount: number
  refresh: () => Promise<CartResponse | null>
  /** Sets the absolute quantity of a variant (the API replaces, it does not increment). */
  setQuantity: (variantId: string, quantity: number) => Promise<void>
  remove: (variantId: string) => Promise<void>
  clear: () => Promise<void>
  /** Local reset after checkout, which empties the cart server-side. */
  reset: () => void
}

const CartContext = createContext<CartState | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [cart, setCart] = useState<CartResponse | null>(null)

  const refresh = useCallback(async () => {
    if (!user) {
      setCart(null)
      return null
    }
    const next = await api.get<CartResponse>('/cart')
    setCart(next)
    return next
  }, [user])

  useEffect(() => {
    refresh().catch(() => setCart(null))
  }, [refresh])

  const value = useMemo<CartState>(
    () => ({
      cart,
      itemCount: cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0,
      refresh,
      setQuantity: async (variantId, quantity) => {
        setCart(await api.put<CartResponse>(`/cart/items/${variantId}`, { quantity }))
      },
      remove: async (variantId) => {
        await api.delete(`/cart/items/${variantId}`)
        await refresh()
      },
      clear: async () => {
        await api.delete('/cart')
        await refresh()
      },
      reset: () => setCart((current) => (current ? { ...current, items: [], subtotal: 0 } : current)),
    }),
    [cart, refresh],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used inside CartProvider')
  return context
}
