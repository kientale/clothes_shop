import { useEffect, useState } from 'react'
import { catalog } from '../api/catalog'
import { orders } from '../api/commerce'
import { customers } from '../api/customers'
import type { PickOption } from './kit'
import { vndOf } from './kit'

// Search sources for SearchPicker. Each returns at most 8 options.

export async function searchCustomers(term: string): Promise<PickOption[]> {
  const page = await customers.list({ search: term, status: 'ACTIVE', page: 0, size: 8 })
  return page.content.map((c) => ({ id: c.id, title: c.fullName, sub: [c.phone, c.email].filter(Boolean).join(' | ') || undefined }))
}

export interface VariantOption extends PickOption {
  productId: string
  price: number
}

export async function searchVariants(term: string): Promise<VariantOption[]> {
  const page = await catalog.variants.list({ search: term, status: 'ACTIVE', page: 0, size: 8 })
  return page.content.map((v) => ({
    id: v.id,
    productId: v.product.id,
    price: v.price,
    title: `${v.product.name} - ${v.color.name} / ${v.size.name}`,
    sub: `${v.sku} | ${vndOf(v.price)}`,
  }))
}

export async function searchOrders(term: string): Promise<PickOption[]> {
  const page = await orders.list({ search: term, page: 0, size: 8 })
  return page.content.map((o) => ({ id: o.id, title: o.orderCode, sub: `${o.recipientName} | ${vndOf(o.totalAmount)}` }))
}

/**
 * Hook factory for id -> display name lookups. Each id is fetched once per session and shared
 * by every screen; ids render as their short form until the name arrives.
 */
function lookup(fetchName: (id: string) => Promise<string>) {
  const cache = new Map<string, Promise<string>>()
  const nameOf = (id: string) => {
    let name = cache.get(id)
    if (!name) {
      name = fetchName(id).catch(() => {
        cache.delete(id)
        return id.slice(0, 8).toUpperCase()
      })
      cache.set(id, name)
    }
    return name
  }
  return function useNames(ids: (string | null | undefined)[]) {
    const [names, setNames] = useState<Record<string, string>>({})
    const key = [...new Set(ids.filter((id): id is string => Boolean(id)))].sort().join(',')
    useEffect(() => {
      let current = true
      const unique = key ? key.split(',') : []
      Promise.all(unique.map(async (id) => [id, await nameOf(id)] as const)).then((entries) => {
        if (current) setNames((prev) => ({ ...prev, ...Object.fromEntries(entries) }))
      })
      return () => {
        current = false
      }
    }, [key])
    return names
  }
}

/** "Product - color / size (SKU)" per variant id. */
export const useVariantNames = lookup(async (id) => {
  const v = await catalog.variants.get(id)
  return `${v.product.name} - ${v.color.name} / ${v.size.name} (${v.sku})`
})

/** Order code (LX-...) per order id. */
export const useOrderCodes = lookup(async (id) => (await orders.get(id)).orderCode)

/** Product name per product id. */
export const useProductNames = lookup(async (id) => (await catalog.products.get(id)).name)
