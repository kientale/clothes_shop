import { CheckCircleIcon, ClockIcon, XCircleIcon } from '@phosphor-icons/react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { store } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner, Spinner, useTitle } from '../components/common'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'
import { useSeo } from '../seo'

/**
 * Where VNPay or MoMo sends the shopper after paying (/shop/payment/vnpay, /shop/payment/momo). The backend checks
 * the gateway's signature on the parameters and settles the payment if its IPN has not arrived yet.
 */
export default function PaymentReturnPage() {
  useTitle('Kết quả thanh toán')
  useSeo({ noIndex: true })
  const { gateway = '' } = useParams()
  const [params] = useSearchParams()
  const { user } = useAuth()
  const query = params.toString()
  const result = useAsync(() => store.paymentReturn(gateway, Object.fromEntries(new URLSearchParams(query))), [gateway, query])

  if (result.error) {
    return (
      <div className="container narrow payment-result">
        <XCircleIcon size={56} weight="fill" className="result-icon is-danger" aria-hidden />
        <h1 className="page-title">Không xác minh được giao dịch</h1>
        <ErrorBanner error={result.error} />
        <p className="muted">Nếu tài khoản của bạn đã bị trừ tiền, cửa hàng vẫn nhận được thông báo từ cổng thanh toán và sẽ cập nhật đơn.</p>
        <Link to={user ? '/shop/orders' : '/shop/track'} className="btn btn-primary">
          {user ? 'Xem đơn hàng của tôi' : 'Tra cứu đơn hàng'}
        </Link>
      </div>
    )
  }
  if (!result.data) return <div className="container narrow"><Spinner label="Đang xác minh thanh toán" /></div>

  const { status, orderCode, orderId, amount } = result.data
  const orderLink = user ? `/shop/orders/${orderId}` : `/shop/track?code=${encodeURIComponent(orderCode)}`
  return (
    <div className="container narrow payment-result">
      {status === 'PAID' ? (
        <>
          <CheckCircleIcon size={56} weight="fill" className="result-icon is-success" aria-hidden />
          <h1 className="page-title">Thanh toán thành công</h1>
          <p>
            Đã nhận {formatMoney(amount)} cho đơn <strong>{orderCode}</strong>. Cửa hàng sẽ chuẩn bị hàng ngay.
          </p>
        </>
      ) : status === 'FAILED' ? (
        <>
          <XCircleIcon size={56} weight="fill" className="result-icon is-danger" aria-hidden />
          <h1 className="page-title">Thanh toán chưa thành công</h1>
          <p>
            Giao dịch cho đơn <strong>{orderCode}</strong> bị hủy hoặc không thành công. Đơn vẫn được giữ, bạn có thể thanh toán lại.
          </p>
        </>
      ) : (
        <>
          <ClockIcon size={56} weight="fill" className="result-icon is-warning" aria-hidden />
          <h1 className="page-title">Đang chờ xác nhận</h1>
          <p>Cổng thanh toán chưa báo kết quả cho đơn {orderCode}. Trạng thái sẽ cập nhật trong ít phút.</p>
        </>
      )}
      <Link to={orderLink} className="btn btn-primary">
        Xem đơn hàng
      </Link>
    </div>
  )
}
