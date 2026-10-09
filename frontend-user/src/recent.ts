import type { ProductCard, ProductDetail } from './api/types'

// Products the shopper opened recently, newest first. Kept in this browser only; nothing is sent to the server.
const KEY = 'lemonadex.recent'
const LIMIT = 12

export function recentProducts(): ProductCard[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((p) => typeof p?.id === 'string' && typeof p?.slug === 'string') : []
  } catch {
    return []
  }
}

export function rememberProduct(product: ProductDetail) {
  const prices = product.variants.map((v) => v.price)
  const card: ProductCard = {
    id: product.id,
    slug: product.slug,
    name: product.name,
    brandName: product.brand.name,
    categoryName: product.category.name,
    gender: product.gender,
    imageUrl: product.images[0]?.url ?? null,
    hoverImageUrl: product.images[1]?.url ?? null,
    minPrice: Math.min(...prices),
    maxPrice: Math.max(...prices),
    compareAtPrice: null,
    colors: [...new Map(product.variants.map((v) => [v.colorId, { name: v.colorName, hexCode: v.hexCode }])).values()],
    inStock: product.variants.some((v) => v.available > 0),
    createdAt: new Date().toISOString(),
  }
  try {
    localStorage.setItem(KEY, JSON.stringify([card, ...recentProducts().filter((p) => p.id !== product.id)].slice(0, LIMIT)))
  } catch {
    // Storage unavailable: the list is simply not kept.
  }
}
