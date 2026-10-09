import { StarIcon } from '@phosphor-icons/react'
import { useState, type FormEvent } from 'react'
import { me } from '../api/store'
import type { MyReview } from '../api/types'
import { ErrorBanner } from './common'
import { ImageUploader } from './ImageUploader'

const LABEL = ['', 'Rất tệ', 'Chưa hài lòng', 'Bình thường', 'Hài lòng', 'Rất hài lòng']

/** Star rating plus an optional comment and photos for one delivered order item. Reviews are shown after the shop approves them. */
export function ReviewForm({ orderItemId, productName, onDone, onCancel }: {
  orderItemId: string
  productName: string
  onDone: (review: MyReview) => void
  onCancel: () => void
}) {
  const [rating, setRating] = useState(5)
  const [hover, setHover] = useState(0)
  const [comment, setComment] = useState('')
  const [images, setImages] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const shown = hover || rating

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      onDone(await me.submitReview({ orderItemId, rating, comment: comment.trim() || null, images }))
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <form className="review-form" onSubmit={submit}>
      <fieldset className="stars-input" onPointerLeave={() => setHover(0)}>
        <legend className="sr-only">Số sao cho {productName}</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} onPointerEnter={() => setHover(n)}>
            <input type="radio" name={`rating-${orderItemId}`} value={n} checked={rating === n} onChange={() => setRating(n)} className="sr-only" />
            <StarIcon size={26} weight={n <= shown ? 'fill' : 'regular'} aria-hidden />
            <span className="sr-only">{n} sao</span>
          </label>
        ))}
        <span className="stars-label">{LABEL[shown]}</span>
      </fieldset>
      <label className="sr-only" htmlFor={`comment-${orderItemId}`}>
        Nhận xét
      </label>
      <textarea
        id={`comment-${orderItemId}`}
        rows={3}
        maxLength={2000}
        placeholder="Chất liệu, size, màu sắc có đúng như mong đợi không?"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
      />
      <ImageUploader value={images} onChange={setImages} max={5} label="Thêm ảnh thực tế" />
      <ErrorBanner error={error} />
      <div className="row-gap">
        <button className="btn btn-primary btn-sm" disabled={busy}>
          {busy ? 'Đang gửi...' : 'Gửi đánh giá'}
        </button>
        <button type="button" className="btn btn-outline btn-sm" onClick={onCancel} disabled={busy}>
          Để sau
        </button>
      </div>
    </form>
  )
}
