import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

// Error reporting (Sentry) and visit statistics (Google Analytics 4). Both are off unless their key is set:
//   VITE_SENTRY_DSN            -> uncaught errors and failed renders are sent to Sentry
//   VITE_GA_MEASUREMENT_ID     -> page views are sent to GA4 (G-XXXXXXX)
// Neither script is downloaded when its key is empty, so local development stays clean.

const SENTRY_DSN = (import.meta.env.VITE_SENTRY_DSN ?? '').trim()
const GA_ID = (import.meta.env.VITE_GA_MEASUREMENT_ID ?? '').trim()

type Gtag = (...args: unknown[]) => void
declare global {
  interface Window {
    dataLayer?: unknown[]
    gtag?: Gtag
  }
}

let sentry: Promise<typeof import('@sentry/react') | null> = Promise.resolve(null)

export function initMonitoring() {
  if (SENTRY_DSN) {
    // Loaded on demand so the SDK never weighs on the first page load.
    sentry = import('@sentry/react').then((Sentry) => {
      Sentry.init({
        dsn: SENTRY_DSN,
        environment: import.meta.env.MODE,
        // Errors only: no session replay or tracing integrations are added.
      })
      return Sentry
    })
  }
  if (GA_ID && /^G-[A-Z0-9]+$/.test(GA_ID)) {
    window.dataLayer = window.dataLayer ?? []
    window.gtag = function gtag() {
      // GA expects the arguments object itself.
      // eslint-disable-next-line prefer-rest-params
      window.dataLayer!.push(arguments)
    }
    window.gtag('js', new Date())
    // Page views are sent by usePageViews on every route change, not automatically.
    window.gtag('config', GA_ID, { send_page_view: false, anonymize_ip: true })
    const script = document.createElement('script')
    script.async = true
    script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(GA_ID)}`
    document.head.appendChild(script)
  }
}

/** Reports an error caught by an error boundary. */
export function reportError(error: unknown, context?: Record<string, unknown>) {
  console.error(error)
  void sentry.then((Sentry) => Sentry?.captureException(error, context ? { extra: context } : undefined))
}

/** Sends a GA4 page view when the route changes. */
export function usePageViews() {
  const location = useLocation()
  useEffect(() => {
    if (!window.gtag || !GA_ID) return
    const timer = window.setTimeout(() => {
      window.gtag!('event', 'page_view', { page_location: window.location.href, page_title: document.title })
    }, 0)
    return () => window.clearTimeout(timer)
  }, [location.pathname, location.search])
}

/** Tells GA4 about a purchase (used after checkout). */
export function trackPurchase(orderCode: string, value: number, items: { id: string; name: string; quantity: number; price: number }[]) {
  window.gtag?.('event', 'purchase', {
    transaction_id: orderCode,
    currency: 'VND',
    value,
    items: items.map((i) => ({ item_id: i.id, item_name: i.name, quantity: i.quantity, price: i.price })),
  })
}
