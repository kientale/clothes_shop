import { CopyIcon, QrCodeIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import type { PaymentOption } from '../api/types'
import { formatMoney } from '../format'

// NAPAS bank codes (BIN) used by VietQR, keyed by the names shops usually type. Banking apps in Vietnam scan
// these codes and pre-fill the account, the amount and the transfer note.
const BANK_BIN: [RegExp, string][] = [
  [/vietcom|vcb/, '970436'],
  [/vietin|ctg/, '970415'],
  [/bidv/, '970418'],
  [/agri/, '970405'],
  [/techcom|tcb/, '970407'],
  [/^mb|mbbank|quan doi/, '970422'],
  [/acb|a chau/, '970416'],
  [/vpbank|vp bank|viet nam thinh vuong/, '970432'],
  [/tpbank|tp bank|tien phong/, '970423'],
  [/sacom|stb/, '970403'],
  [/vib|quoc te/, '970441'],
  [/shb|sai gon ha noi/, '970443'],
  [/hdbank|hd bank/, '970437'],
  [/ocb|phuong dong/, '970448'],
  [/msb|hang hai/, '970426'],
  [/seabank|dong nam a/, '970440'],
  [/exim/, '970431'],
  [/lpbank|lienviet|loc phat/, '970449'],
  [/vietbank/, '970433'],
  [/nam a|namabank/, '970428'],
  [/bac a|bacabank/, '970409'],
  [/abbank|an binh/, '970425'],
  [/scb|sai gon/, '970429'],
  [/pvcom/, '970412'],
  [/cake/, '546034'],
  [/timo/, '963388'],
]

function bankBin(bankName: string) {
  const name = bankName.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/gi, 'd').toLowerCase()
  return BANK_BIN.find(([pattern]) => pattern.test(name))?.[1] ?? null
}

/**
 * Transfer details for an unpaid bank-transfer order, with a VietQR code when the bank is recognised.
 * The order code is the transfer note, so staff can match the money to the order.
 */
export function BankTransferQr({ method, amount, orderCode }: { method: PaymentOption; amount: number; orderCode: string }) {
  const bank = method.bankDetails!
  const bin = bankBin(bank.bankName)
  const [copied, setCopied] = useState<string | null>(null)
  const qr = bin
    ? `https://img.vietqr.io/image/${bin}-${encodeURIComponent(bank.accountNumber)}-compact2.png?amount=${Math.round(amount)}` +
      `&addInfo=${encodeURIComponent(orderCode)}&accountName=${encodeURIComponent(bank.accountHolder)}`
    : null

  const copy = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(label)
      window.setTimeout(() => setCopied(null), 1500)
    } catch {
      // Copying is a convenience; the value stays visible to copy by hand.
    }
  }

  return (
    <section className="panel transfer-panel">
      <h2>
        <QrCodeIcon size={20} aria-hidden /> Chuyển khoản để hoàn tất đơn
      </h2>
      <div className="transfer-grid">
        {qr && (
          <figure className="transfer-qr">
            <img src={qr} alt={`Mã VietQR chuyển ${formatMoney(amount)} tới ${bank.bankName}`} width={240} height={240} loading="lazy" />
            <figcaption className="muted small">Quét bằng ứng dụng ngân hàng, số tiền và nội dung đã điền sẵn.</figcaption>
          </figure>
        )}
        <dl className="transfer-facts">
          <div>
            <dt>Ngân hàng</dt>
            <dd>{bank.bankName}</dd>
          </div>
          <div>
            <dt>Số tài khoản</dt>
            <dd>
              <span className="mono">{bank.accountNumber}</span>
              <button type="button" className="icon-btn" aria-label="Sao chép số tài khoản" onClick={() => void copy('account', bank.accountNumber)}>
                <CopyIcon size={16} />
              </button>
              {copied === 'account' && <span className="muted small">Đã chép</span>}
            </dd>
          </div>
          <div>
            <dt>Chủ tài khoản</dt>
            <dd>{bank.accountHolder}</dd>
          </div>
          <div>
            <dt>Số tiền</dt>
            <dd>
              <strong>{formatMoney(amount)}</strong>
            </dd>
          </div>
          <div>
            <dt>Nội dung</dt>
            <dd>
              <span className="mono">{orderCode}</span>
              <button type="button" className="icon-btn" aria-label="Sao chép nội dung chuyển khoản" onClick={() => void copy('note', orderCode)}>
                <CopyIcon size={16} />
              </button>
              {copied === 'note' && <span className="muted small">Đã chép</span>}
            </dd>
          </div>
        </dl>
      </div>
      {method.instructions && <p className="muted small">{method.instructions}</p>}
      <p className="muted small">Cửa hàng xác nhận thanh toán khi nhận được tiền; trạng thái đơn sẽ tự cập nhật.</p>
    </section>
  )
}
