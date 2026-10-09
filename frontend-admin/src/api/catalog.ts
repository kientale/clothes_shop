import { api } from './client'
import type { Page } from './types'

// Mirrors /api/v1/admin catalog routes (categories, brands, colors, sizes, collections, products, variants).

export type CatalogStatus = 'ACTIVE' | 'INACTIVE'
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
export type ProductGender = 'MEN' | 'WOMEN' | 'UNISEX' | 'KIDS'

export interface Ref {
  id: string
  name: string
}

interface Timestamps {
  createdAt: string
  updatedAt: string
}

export interface Category extends Timestamps {
  id: string
  parentId: string | null
  parentName: string | null
  name: string
  slug: string
  description: string | null
  status: CatalogStatus
  childCount: number
  productCount: number
}

export interface Brand extends Timestamps {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  description: string | null
  status: CatalogStatus
  productCount: number
}

export interface Color extends Timestamps {
  id: string
  name: string
  code: string
  hexCode: string | null
  status: CatalogStatus
  variantCount: number
}

export interface Size extends Timestamps {
  id: string
  name: string
  code: string
  sortOrder: number
  status: CatalogStatus
  variantCount: number
}

export interface CollectionProduct {
  id: string
  productCode: string
  name: string
  imageUrl: string | null
  status: ProductStatus
}

export interface Collection extends Timestamps {
  id: string
  name: string
  slug: string
  description: string | null
  imageUrl: string | null
  startAt: string | null
  endAt: string | null
  status: CatalogStatus
  productCount: number
  /** Only filled by the detail endpoint. */
  products: CollectionProduct[] | null
}

export interface ProductImage {
  id: string
  url: string
  altText: string | null
  primary: boolean
}

export interface Product extends Timestamps {
  id: string
  productCode: string
  name: string
  slug: string
  description: string | null
  shortDescription: string | null
  brand: Ref
  category: Ref
  material: string | null
  gender: ProductGender
  basePrice: number
  status: ProductStatus
  images: ProductImage[]
  variantCount: number
  minPrice: number | null
  maxPrice: number | null
}

export interface Variant extends Timestamps {
  id: string
  product: { id: string; productCode: string; name: string }
  color: { id: string; name: string; code: string; hexCode: string | null }
  size: { id: string; name: string; code: string }
  sku: string
  price: number
  compareAtPrice: number | null
  status: CatalogStatus
}

export interface CatalogOptions {
  categories: { id: string; name: string; parentId: string | null; status: CatalogStatus }[]
  brands: { id: string; name: string; status: CatalogStatus }[]
  colors: { id: string; name: string; code: string; hexCode: string | null; status: CatalogStatus }[]
  sizes: { id: string; name: string; code: string; status: CatalogStatus }[]
}

export interface CategoryRequest {
  parentId: string | null
  name: string
  slug: string | null
  description: string | null
  status: CatalogStatus
}

export interface BrandRequest {
  name: string
  slug: string | null
  logoUrl: string | null
  description: string | null
  status: CatalogStatus
}

export interface ColorRequest {
  name: string
  code: string
  hexCode: string | null
  status: CatalogStatus
}

export interface SizeRequest {
  name: string
  code: string
  sortOrder: number
  status: CatalogStatus
}

export interface CollectionRequest {
  name: string
  slug: string | null
  description: string | null
  imageUrl: string | null
  startAt: string | null
  endAt: string | null
  status: CatalogStatus
  productIds: string[]
}

export interface ProductRequest {
  productCode: string
  name: string
  slug: string | null
  description: string | null
  shortDescription: string | null
  brandId: string
  categoryId: string
  material: string | null
  gender: ProductGender
  basePrice: number
  status: ProductStatus
  images: { url: string; altText: string | null }[]
}

export interface VariantCreateRequest {
  productId: string
  colorId: string
  sizeId: string
  sku: string | null
  price: number
  compareAtPrice: number | null
  status: CatalogStatus
}

export interface VariantUpdateRequest {
  sku: string
  price: number
  compareAtPrice: number | null
  status: CatalogStatus
}

export interface VariantBulkRequest {
  productId: string
  colorIds: string[]
  sizeIds: string[]
  price: number
  compareAtPrice: number | null
  status: CatalogStatus
}

export type ListQuery = { search?: string; status?: string; page: number; size: number } & Record<string, string | number | undefined>

/** CRUD client for one catalog resource under /api/v1/admin. */
function resource<T, Req, UpdateReq = Req>(path: string) {
  return {
    list: (query: ListQuery) => api.get<Page<T>>(`/admin/${path}`, query),
    get: (id: string) => api.get<T>(`/admin/${path}/${id}`),
    create: (body: Req) => api.post<T>(`/admin/${path}`, body),
    update: (id: string, body: UpdateReq) => api.put<T>(`/admin/${path}/${id}`, body),
    remove: (id: string) => api.delete(`/admin/${path}/${id}`),
  }
}

/** Measurements per size shown to shoppers ("Bảng size"); the first column names the size. */
export interface SizeChart {
  columns: string[]
  rows: string[][]
  note: string | null
}

export const catalog = {
  sizeChart: {
    get: (productId: string) => api.get<SizeChart | null>(`/admin/products/${productId}/size-chart`),
    save: (productId: string, chart: SizeChart) => api.put<SizeChart>(`/admin/products/${productId}/size-chart`, chart),
    remove: (productId: string) => api.delete(`/admin/products/${productId}/size-chart`),
  },
  categories: resource<Category, CategoryRequest>('categories'),
  brands: resource<Brand, BrandRequest>('brands'),
  colors: resource<Color, ColorRequest>('colors'),
  sizes: resource<Size, SizeRequest>('sizes'),
  collections: resource<Collection, CollectionRequest>('collections'),
  products: resource<Product, ProductRequest>('products'),
  variants: {
    ...resource<Variant, VariantCreateRequest, VariantUpdateRequest>('product-variants'),
    bulk: (body: VariantBulkRequest) => api.post<Variant[]>('/admin/product-variants/bulk', body),
  },
  options: () => api.get<CatalogOptions>('/admin/catalog/options'),
}

export const CATALOG_STATUS_LABEL: Record<CatalogStatus, string> = {
  ACTIVE: 'Đang hiển thị',
  INACTIVE: 'Đang ẩn',
}

export const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = {
  DRAFT: 'Nháp',
  ACTIVE: 'Đang bán',
  INACTIVE: 'Ngừng bán',
  ARCHIVED: 'Lưu trữ',
}

export const GENDER_LABEL: Record<ProductGender, string> = {
  MEN: 'Nam',
  WOMEN: 'Nữ',
  UNISEX: 'Unisex',
  KIDS: 'Trẻ em',
}

/** Same rule as the backend: "Áo Thun Nữ" becomes "ao-thun-nu". */
export function slugify(value: string) {
  return value
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** Category names prefixed with their ancestors, e.g. "Nữ / Áo / Áo thun", sorted by that path. */
export function categoryPaths(categories: CatalogOptions['categories']) {
  const byId = new Map(categories.map((category) => [category.id, category]))
  const path = (id: string, seen = new Set<string>()): string => {
    const category = byId.get(id)
    if (!category || seen.has(id)) return ''
    seen.add(id)
    return category.parentId && byId.has(category.parentId) ? `${path(category.parentId, seen)} / ${category.name}` : category.name
  }
  return categories.map((category) => ({ ...category, path: path(category.id) })).sort((a, b) => a.path.localeCompare(b.path, 'vi'))
}
