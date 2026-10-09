import { ChatCircleTextIcon, StarIcon, TrashIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { accessErrorMessage } from '../../api/access'
import { REVIEW_STATUS, reviews, type Review, type ReviewStatus } from '../../api/marketing'
import { FilterSelect, ListPanel, PageHead, SearchBox, Segmented, StatePill, StatusDialog, useCan, whenOf } from '../../components/kit'
import { useProductNames } from '../../components/pickers'
import { useToast } from '../../components/Toast'
import { ConfirmDialog, useDebounced } from '../../components/ui'

function Stars({ value }: { value: number }) {
  return (
    <span className="stars" aria-label={`${value} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <StarIcon key={n} size={14} weight={n <= value ? 'fill' : 'regular'} className={n <= value ? 'on' : undefined} aria-hidden />
      ))}
    </span>
  )
}

export default function ReviewsPage() {
  const canWrite = useCan('REVIEW_WRITE')
  const toast = useToast()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<ReviewStatus | undefined>('PENDING')
  const [rating, setRating] = useState<'1' | '2' | '3' | '4' | '5' | ''>('')
  const [rows, setRows] = useState<Review[]>([])
  const [moderating, setModerating] = useState<Review | null>(null)
  const [deleting, setDeleting] = useState<Review | null>(null)
  const [reload, setReload] = useState(0)
  const term = useDebounced(search.trim())
  const products = useProductNames(rows.map((r) => r.productId))
  const query = { search: term, status, rating: rating || undefined }

  const done = (message: string) => {
    setModerating(null)
    setDeleting(null)
    toast.success(message)
    setReload((n) => n + 1)
  }

  return (
    <div className="page page-wide">
      <PageHead title="Đánh giá sản phẩm" lede="Đánh giá từ khách đã mua và nhận hàng. Đánh giá mới chờ duyệt trước khi hiện ở cửa hàng." />
      <ListPanel<Review>
        filterKey={JSON.stringify(query)}
        reloadToken={reload}
        load={(paging) => reviews.list({ ...paging, ...query })}
        onLoaded={(page) => setRows(page.content)}
        rowKey={(r) => r.id}
        itemLabel="đánh giá"
        emptyIcon={<ChatCircleTextIcon size={36} aria-hidden />}
        toolbar={
          <>
            <SearchBox value={search} onChange={setSearch} placeholder="Tìm trong nội dung đánh giá" />
            <Segmented
              label="Lọc theo trạng thái"
              value={status}
              onChange={setStatus}
              options={[[undefined, 'Tất cả'], ...(Object.keys(REVIEW_STATUS) as ReviewStatus[]).map((k) => [k, REVIEW_STATUS[k][0]] as [ReviewStatus, string])]}
            />
            <FilterSelect
              label="Số sao"
              value={rating}
              options={(['5', '4', '3', '2', '1'] as const).map((n) => [n, `${n} sao`])}
              onChange={setRating}
            />
          </>
        }
        columns={[
          { header: 'Sản phẩm', render: (r) => <span className="person-name">{products[r.productId] ?? '...'}</span> },
          {
            header: 'Đánh giá',
            render: (r) => (
              <div className="review-cell">
                <Stars value={r.rating} />
                {r.comment ? <p className="cell-wrap">{r.comment}</p> : <span className="muted small">Không có bình luận</span>}
                {r.images.length > 0 && (
                  <div className="review-images">
                    {r.images.slice(0, 4).map((src) => (
                      <a key={src} href={src} target="_blank" rel="noreferrer">
                        <img src={src} alt="Ảnh khách gửi kèm đánh giá" loading="lazy" />
                      </a>
                    ))}
                  </div>
                )}
              </div>
            ),
          },
          {
            header: 'Trạng thái',
            render: (r) => (
              <div className="person-text">
                <StatePill map={REVIEW_STATUS} value={r.status} />
                {r.moderationNote && <span className="person-sub cell-wrap">{r.moderationNote}</span>}
              </div>
            ),
          },
          { header: 'Ngày gửi', render: (r) => whenOf(r.createdAt), className: 'muted nowrap' },
        ]}
        actions={
          canWrite
            ? (r) => (
                <>
                  <button type="button" className="btn btn-plain btn-sm" onClick={() => setModerating(r)}>
                    Duyệt
                  </button>
                  <button type="button" className="icon-btn danger" onClick={() => setDeleting(r)} aria-label="Xóa đánh giá" title="Xóa">
                    <TrashIcon size={16} />
                  </button>
                </>
              )
            : undefined
        }
      />

      <StatusDialog<ReviewStatus>
        open={moderating !== null}
        title="Duyệt đánh giá"
        description="Ghi chú duyệt được lưu cùng người duyệt và thời gian."
        options={moderating ? (Object.keys(REVIEW_STATUS) as ReviewStatus[]).filter((s) => s !== 'PENDING') : []}
        labels={REVIEW_STATUS}
        noteLabel="Ghi chú duyệt"
        noteRequired
        onClose={() => setModerating(null)}
        onSubmit={async (next, note) => {
          await reviews.moderate(moderating!.id, next, note ?? '')
          done(next === 'APPROVED' ? 'Đã duyệt đánh giá.' : 'Đã từ chối đánh giá.')
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title="Xóa đánh giá"
        message="Đánh giá sẽ bị ẩn khỏi cửa hàng. Ảnh đính kèm vẫn được giữ làm bằng chứng."
        confirmLabel="Xóa"
        formatError={accessErrorMessage}
        onClose={() => setDeleting(null)}
        onConfirm={async () => {
          await reviews.remove(deleting!.id)
          done('Đã xóa đánh giá.')
        }}
      />
    </div>
  )
}
