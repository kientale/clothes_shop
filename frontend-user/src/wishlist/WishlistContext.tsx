import { HeartIcon } from '@phosphor-icons/react'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { me } from '../api/store'
import { useAuth } from '../auth/AuthContext'

interface WishlistState {
  ids: ReadonlySet<string>
  count: number
  has: (productId: string) => boolean
  /** Adds or removes; sends a signed-out shopper to sign in first. */
  toggle: (productId: string) => Promise<void>
}

const WishlistContext = createContext<WishlistState | null>(null)

/** Wishlist ids of the signed-in customer, kept on the server so they follow the customer across devices. */
export function WishlistProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [ids, setIds] = useState<ReadonlySet<string>>(new Set())
  const isCustomer = !!user?.roles.includes('CUSTOMER')

  useEffect(() => {
    if (!isCustomer) {
      setIds(new Set())
      return
    }
    let active = true
    me.wishlistIds()
      .then((list) => active && setIds(new Set(list)))
      .catch(() => active && setIds(new Set()))
    return () => {
      active = false
    }
  }, [isCustomer, user?.id])

  const toggle = useCallback(
    async (productId: string) => {
      if (!isCustomer) {
        navigate('/shop/login', { state: { from: location.pathname + location.search } })
        return
      }
      const liked = ids.has(productId)
      // Optimistic: the heart flips at once and rolls back if the server refuses.
      setIds((current) => {
        const next = new Set(current)
        if (liked) next.delete(productId)
        else next.add(productId)
        return next
      })
      try {
        setIds(new Set(liked ? await me.unlike(productId) : await me.like(productId)))
      } catch (err) {
        setIds((current) => {
          const next = new Set(current)
          if (liked) next.add(productId)
          else next.delete(productId)
          return next
        })
        window.alert(errorMessage(err))
      }
    },
    [ids, isCustomer, location.pathname, location.search, navigate],
  )

  const value = useMemo<WishlistState>(() => ({ ids, count: ids.size, has: (id) => ids.has(id), toggle }), [ids, toggle])
  return <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>
}

export function useWishlist() {
  const context = useContext(WishlistContext)
  if (!context) throw new Error('useWishlist must be used inside WishlistProvider')
  return context
}

/** Heart toggle; filled when the product is saved. */
export function WishButton({ productId, name, className = '', withLabel }: { productId: string; name: string; className?: string; withLabel?: boolean }) {
  const wishlist = useWishlist()
  const liked = wishlist.has(productId)
  return (
    <button
      type="button"
      className={`wish-btn${liked ? ' is-liked' : ''}${withLabel ? ' with-label' : ''} ${className}`}
      aria-pressed={liked}
      aria-label={liked ? `Bỏ ${name} khỏi yêu thích` : `Thêm ${name} vào yêu thích`}
      title={liked ? 'Bỏ khỏi yêu thích' : 'Thêm vào yêu thích'}
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        void wishlist.toggle(productId)
      }}
    >
      <HeartIcon size={withLabel ? 20 : 18} weight={liked ? 'fill' : 'regular'} aria-hidden />
      {withLabel && <span>{liked ? 'Đã yêu thích' : 'Yêu thích'}</span>}
    </button>
  )
}
