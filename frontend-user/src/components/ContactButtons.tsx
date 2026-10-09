import { ChatCircleDotsIcon, MessengerLogoIcon, PhoneIcon, XIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import type { StoreConfiguration } from '../api/types'

/**
 * Floating "Chat với shop" button: opens Zalo, Messenger or a phone call, whichever the shop has set in the
 * store settings. Hidden when none is set.
 */
export function ContactButtons({ shop }: { shop: StoreConfiguration['store'] | undefined }) {
  const [open, setOpen] = useState(false)
  const zalo = shop?.zaloPhone?.replace(/[^0-9]/g, '')
  const messenger = shop?.messengerUrl
  const phone = shop?.supportPhone?.replace(/\s/g, '')
  if (!zalo && !messenger && !phone) return null

  return (
    <div className={`contact-fab${open ? ' open' : ''}`}>
      {open && (
        <ul className="contact-fab-menu" aria-label="Liên hệ cửa hàng">
          {zalo && (
            <li>
              <a href={`https://zalo.me/${zalo}`} target="_blank" rel="noreferrer" className="contact-fab-item zalo">
                <span className="contact-fab-badge" aria-hidden>
                  Zalo
                </span>
                Chat Zalo
              </a>
            </li>
          )}
          {messenger && (
            <li>
              <a href={messenger} target="_blank" rel="noreferrer" className="contact-fab-item messenger">
                <MessengerLogoIcon size={22} weight="fill" aria-hidden /> Messenger
              </a>
            </li>
          )}
          {phone && (
            <li>
              <a href={`tel:${phone}`} className="contact-fab-item">
                <PhoneIcon size={20} aria-hidden /> Gọi {shop?.supportPhone}
              </a>
            </li>
          )}
        </ul>
      )}
      <button type="button" className="contact-fab-toggle" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        aria-label={open ? 'Đóng liên hệ' : 'Chat với cửa hàng'}>
        {open ? <XIcon size={22} /> : <ChatCircleDotsIcon size={26} weight="fill" />}
      </button>
    </div>
  )
}
