// Address lookup for checkout. With VITE_GOOGLE_MAPS_API_KEY set, suggestions come from Google Places
// (API New, restricted to Vietnam); otherwise from Photon, a free OpenStreetMap geocoder that needs no key.

/** The checkout's three address fields. */
export interface AddressParts {
  line: string
  ward: string
  province: string
}

export interface AddressSuggestion {
  id: string
  title: string
  detail: string
  /** Google needs a second call for the components; Photon results carry them already. */
  resolve: () => Promise<AddressParts>
}

const GOOGLE_KEY = (import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? '').trim()

export const provider: 'google' | 'osm' = GOOGLE_KEY ? 'google' : 'osm'

/** Suggestions for what the customer typed; empty below 3 characters. */
export function searchAddresses(query: string, signal: AbortSignal): Promise<AddressSuggestion[]> {
  const q = query.trim()
  if (q.length < 3) return Promise.resolve([])
  return provider === 'google' ? googleSearch(q, signal) : photonSearch(q, signal)
}

/** Address at a GPS position, for "use my location". */
export async function reverseGeocode(lat: number, lon: number): Promise<AddressParts | null> {
  bias = { lat, lon }
  if (provider === 'google') {
    const response = await fetch(
      `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lon}&language=vi&key=${encodeURIComponent(GOOGLE_KEY)}`,
    )
    const body = (await response.json()) as { results?: { address_components: GoogleComponent[] }[] }
    const first = body.results?.[0]
    return first ? fromGoogle(first.address_components.map((c) => ({ longText: c.long_name, types: c.types }))) : null
  }
  const response = await fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lon}&limit=1`)
  if (!response.ok) throw new Error(`Photon ${response.status}`)
  const body = (await response.json()) as { features: PhotonFeature[] }
  return body.features[0] ? fromPhoton(body.features[0].properties) : null
}

/** Opens the composed address in Google Maps so the customer can check it. Needs no key. */
export function googleMapsLink(address: string) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
}

// Photon (OpenStreetMap) ------------------------------------------------------------------------

interface PhotonProperties {
  osm_id: number
  type?: string
  name?: string
  housenumber?: string
  street?: string
  locality?: string
  district?: string
  county?: string
  city?: string
  state?: string
}

interface PhotonFeature {
  properties: PhotonProperties
}

// Vietnam's bounding box keeps results inside the country.
const VN_BBOX = '102.1,8.2,109.6,23.4'

// Results lean toward this point: the shop's city by default, the customer once "use my location" ran.
let bias = { lat: 10.776, lon: 106.7 }

async function photonSearch(q: string, signal: AbortSignal): Promise<AddressSuggestion[]> {
  // OSM streets rarely carry house numbers; search without it and keep the one the customer typed.
  const typedNumber = /^\s*([0-9][0-9a-zA-Z/-]*)\s+/.exec(q)?.[1]
  // Old district/ward numbers ("Quận 1", "P.5") no longer exist in OSM since the 2025 reform and only add noise.
  const text = stripOldUnits(q.replace(/^\s*[0-9][0-9a-zA-Z/-]*\s+/, ''))
  if (text.length < 2) return []
  const params = new URLSearchParams({ q: text, limit: '12', bbox: VN_BBOX, lat: String(bias.lat), lon: String(bias.lon), location_bias_scale: '0.5' })
  const response = await fetch(`https://photon.komoot.io/api/?${params}`, { signal })
  if (!response.ok) throw new Error(`Photon ${response.status}`)
  const body = (await response.json()) as { features: PhotonFeature[] }
  // Photon matches loosely; keep results that contain every word typed (accents ignored).
  const words = fold(text).split(' ').filter((word) => word.length > 1)
  return body.features
    .filter(({ properties: p }) => {
      const haystack = fold([p.name, p.street, p.locality, p.district, p.county, p.city, p.state].filter(Boolean).join(' '))
      return words.every((word) => haystack.includes(word))
    })
    .map(({ properties: p }) => {
      const parts = fromPhoton(p)
      if (typedNumber && !p.housenumber && !parts.line.startsWith(typedNumber)) parts.line = `${typedNumber} ${parts.line}`
      return {
        id: `${p.osm_id}-${p.name ?? ''}-${p.street ?? ''}`,
        title: parts.line,
        detail: [parts.ward, parts.province].filter(Boolean).join(', '),
        resolve: async () => parts,
      }
    })
    // Several OSM ways often make up one street: keep one suggestion per address.
    .filter((item, index, all) => all.findIndex((other) => other.title === item.title && other.detail === item.detail) === index)
    .slice(0, 6)
}

/** Drops "Quận 1", "Q.1", "Phường 5", "P5": numbered units that no longer exist since the 2025 reform. */
function stripOldUnits(value: string) {
  const words = value.split(/\s+/).filter(Boolean)
  const kept: string[] = []
  for (let i = 0; i < words.length; i++) {
    const word = fold(words[i]).replace(/,$/, '')
    if ((word === 'quan' || word === 'phuong' || word === 'q.' || word === 'p.' || word === 'q' || word === 'p') && /^[0-9]+,?$/.test(words[i + 1] ?? '')) {
      i++
      continue
    }
    if (/^(q|p)\.?[0-9]+,?$/.test(word)) continue
    kept.push(words[i])
  }
  return kept.join(' ').replace(/,\s*$/, '').trim()
}

/** Lower case without Vietnamese accents, so "Nguyen Hue" matches "Nguyễn Huệ". */
function fold(value: string) {
  return value.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'd').toLowerCase()
}

function fromPhoton(p: PhotonProperties): AddressParts {
  const street = p.street ?? (p.type === 'street' ? p.name : undefined)
  const place = p.type === 'street' ? undefined : p.name
  const line = unique([place, [p.housenumber, street].filter(Boolean).join(' ')]).join(', ')
  return {
    line: line || p.name || '',
    // Ward (and district where it still exists); a city inside a province goes here too. The OSM
    // "locality" is usually a neighbourhood ("Khu phố 5") and only clutters the field.
    ward: unique([p.district, p.county, p.state ? p.city : undefined]).join(', '),
    province: p.state ?? p.city ?? '',
  }
}

// Google Places (API New) -----------------------------------------------------------------------

interface GoogleComponent {
  long_name: string
  types: string[]
}

interface PlaceComponent {
  longText: string
  types: string[]
}

// One token per typing session, so Google bills the suggestions and the pick as one session.
let session = crypto.randomUUID()

async function googleSearch(q: string, signal: AbortSignal): Promise<AddressSuggestion[]> {
  const response = await fetch('https://places.googleapis.com/v1/places:autocomplete', {
    method: 'POST',
    signal,
    headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': GOOGLE_KEY },
    body: JSON.stringify({ input: q, includedRegionCodes: ['vn'], languageCode: 'vi', sessionToken: session }),
  })
  if (!response.ok) throw new Error(`Google Places ${response.status}`)
  const body = (await response.json()) as {
    suggestions?: { placePrediction?: { placeId: string; structuredFormat?: { mainText?: { text: string }; secondaryText?: { text: string } } } }[]
  }
  return (body.suggestions ?? []).flatMap(({ placePrediction: p }) =>
    p
      ? [
          {
            id: p.placeId,
            title: p.structuredFormat?.mainText?.text ?? '',
            detail: p.structuredFormat?.secondaryText?.text ?? '',
            resolve: () => googleDetails(p.placeId),
          },
        ]
      : [],
  )
}

async function googleDetails(placeId: string): Promise<AddressParts> {
  const response = await fetch(`https://places.googleapis.com/v1/places/${placeId}?languageCode=vi&sessionToken=${session}`, {
    headers: { 'X-Goog-Api-Key': GOOGLE_KEY, 'X-Goog-FieldMask': 'addressComponents,displayName' },
  })
  session = crypto.randomUUID()
  if (!response.ok) throw new Error(`Google Places ${response.status}`)
  const body = (await response.json()) as { addressComponents?: PlaceComponent[]; displayName?: { text: string } }
  const parts = fromGoogle(body.addressComponents ?? [])
  return { ...parts, line: parts.line || body.displayName?.text || '' }
}

function fromGoogle(components: PlaceComponent[]): AddressParts {
  const pick = (...types: string[]) => components.find((c) => types.some((t) => c.types.includes(t)))?.longText
  const province = pick('administrative_area_level_1') ?? pick('locality') ?? ''
  return {
    line: [pick('street_number'), pick('route')].filter(Boolean).join(' '),
    ward: unique([pick('sublocality_level_1', 'sublocality', 'administrative_area_level_3'), pick('administrative_area_level_2'), pick('locality')])
      .filter((part) => part !== province)
      .join(', '),
    province,
  }
}

function unique(values: (string | undefined)[]) {
  return values.filter((v, i, all): v is string => !!v && all.indexOf(v) === i)
}
