import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

// Per-page meta tags for search engines and link previews (Facebook, Zalo, X). The shop is a single-page
// app, so crawlers that run JavaScript (Google) read these; for crawlers that do not, see README (prerendering).

const DEFAULT_DESCRIPTION = 'LemonadeX: thời trang hằng ngày cho nam, nữ và trẻ em. Đặt online, giao tận nơi.'

interface Seo {
  description?: string
  image?: string
  type?: 'website' | 'product' | 'article'
  /** Structured data (schema.org) as a plain object; rendered as application/ld+json. */
  jsonLd?: object
  /** Keep the page out of search results (cart, checkout, account). */
  noIndex?: boolean
}

export function useSeo({ description, image, type = 'website', jsonLd, noIndex }: Seo) {
  const ld = jsonLd ? JSON.stringify(jsonLd) : null
  useEffect(() => {
    // Waits a tick so the page title set by useTitle is already in place.
    const timer = window.setTimeout(() => {
      const text = (description ?? DEFAULT_DESCRIPTION).replace(/\s+/g, ' ').trim().slice(0, 300)
      meta('name', 'description', text)
      meta('property', 'og:title', document.title)
      meta('property', 'og:description', text)
      meta('property', 'og:type', type)
      meta('property', 'og:url', window.location.origin + window.location.pathname)
      meta('property', 'og:image', image ?? '')
      meta('name', 'twitter:card', image ? 'summary_large_image' : 'summary')
      meta('name', 'robots', noIndex ? 'noindex, nofollow' : 'index, follow')
      link('canonical', window.location.origin + window.location.pathname)
      const script = document.getElementById('page-jsonld')
      if (ld) {
        const element = script ?? Object.assign(document.createElement('script'), { id: 'page-jsonld', type: 'application/ld+json' })
        element.textContent = ld
        if (!script) document.head.appendChild(element)
      } else {
        script?.remove()
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [description, image, type, ld, noIndex])
}

function meta(attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!content) {
    element?.remove()
    return
  }
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.appendChild(element)
  }
  element.content = content
}

function link(rel: string, href: string) {
  let element = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`)
  if (!element) {
    element = document.createElement('link')
    element.rel = rel
    document.head.appendChild(element)
  }
  element.href = href
}

/**
 * Puts the default tags back on every navigation; a page's own useSeo runs a tick later and overrides them,
 * so pages without useSeo never keep the previous page's description, image or noindex.
 */
export function useSeoReset() {
  const { pathname } = useLocation()
  useEffect(() => {
    meta('name', 'description', DEFAULT_DESCRIPTION)
    meta('property', 'og:title', document.title)
    meta('property', 'og:description', DEFAULT_DESCRIPTION)
    meta('property', 'og:type', 'website')
    meta('property', 'og:url', window.location.origin + pathname)
    meta('property', 'og:image', '')
    meta('name', 'robots', PRIVATE.some((prefix) => pathname.startsWith(prefix)) ? 'noindex, nofollow' : 'index, follow')
    link('canonical', window.location.origin + pathname)
    document.getElementById('page-jsonld')?.remove()
  }, [pathname])
}

/** Personal pages that search engines should never list. */
const PRIVATE = ['/shop/cart', '/shop/checkout', '/shop/account', '/shop/orders', '/shop/notifications', '/shop/wishlist',
  '/shop/login', '/shop/register', '/shop/forgot-password', '/shop/reset-password', '/shop/track']
