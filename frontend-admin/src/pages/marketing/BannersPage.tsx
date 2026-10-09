import { ImageIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { MARKETING_STATUS_LABEL, campaignPhase, marketing, type Banner, type MarketingStatus } from '../../api/marketing'
import { IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'
import { AvatarField } from '../../components/AvatarField'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Pill, fromLocalInput, toLocalInput, useCan, windowOf } from '../../components/kit'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useDebounced } from '../../components/ui'
import { MarketingStatusField, PeriodFields, checkPeriod } from './shared'

const URL = /^https?:\/\/\S+$/

export default function BannersPage() {
  const [position, setPosition] = useState('')
  const term = useDebounced(position.trim().toUpperCase())
  return (
    <ResourcePage<Banner>
      title="Banner"
      lede="Ảnh quảng bá trên cửa hàng, nhóm theo vị trí hiển thị và sắp theo thứ tự. Có thể hẹn giờ bật và tắt."
      addLabel="Thêm banner"
      itemLabel="banner"
      searchPlaceholder="Tìm theo tiêu đề"
      canWrite={useCan('BANNER_WRITE')}
      statusLabels={MARKETING_STATUS_LABEL}
      filters={
        <label className="filter-input">
          <span className="sr-only">Vị trí</span>
          <input placeholder="Vị trí, ví dụ HOME_HERO" value={position} onChange={(e) => setPosition(e.target.value)} />
        </label>
      }
      filterKey={term}
      load={(query) => marketing.banners.list({ ...query, position: term || undefined })}
      rowLabel={(b) => b.title}
      columns={[
        {
          header: 'Banner',
          render: (b) => (
            <div className="person">
              <img className="banner-thumb" src={b.imageUrl} alt="" loading="lazy" />
              <div className="person-text">
                <span className="person-name">{b.title}</span>
                <span className="person-sub truncate">{b.linkUrl ?? 'Không có liên kết'}</span>
              </div>
            </div>
          ),
        },
        { header: 'Vị trí', render: (b) => <span className="chip chip-sm mono">{b.position}</span> },
        { header: 'Thứ tự', render: (b) => b.sortOrder, className: 'num' },
        { header: 'Thời gian', render: (b) => <span className="small">{windowOf(b.startAt, b.endAt)}</span> },
        {
          header: 'Trạng thái',
          render: (b) => {
            const [label, tone] = campaignPhase(b.startAt, b.endAt, b.status)
            return <Pill tone={tone}>{label}</Pill>
          },
        },
      ]}
      deleteTitle="Xóa banner"
      deleteMessage={(b) => (
        <>
          Xóa banner <strong>{b.title}</strong> khỏi cửa hàng?
        </>
      )}
      remove={(b) => marketing.banners.remove(b.id)}
      emptyIcon={<ImageIcon size={36} aria-hidden />}
      renderForm={(slot) => <BannerForm {...slot} />}
    />
  )
}

function BannerForm({ open, item, onClose, onSaved }: FormSlot<Banner>) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      title: item?.title ?? '',
      imageUrl: item?.imageUrl ?? '',
      linkUrl: item?.linkUrl ?? '',
      position: item?.position ?? 'HOME_HERO',
      sortOrder: item ? String(item.sortOrder) : '0',
      startAt: toLocalInput(item?.startAt),
      endAt: toLocalInput(item?.endAt),
      status: (item?.status ?? 'ACTIVE') as MarketingStatus,
    }),
    [item?.id],
  )
  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa banner' : 'Thêm banner'}
      busy={busy}
      disabled={uploading}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm banner'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.title.trim()) e.title = 'Nhập tiêu đề.'
          if (!URL.test(f.imageUrl.trim())) e.imageUrl = 'Tải ảnh lên hoặc dán đường dẫn http(s).'
          if (f.linkUrl.trim() && !URL.test(f.linkUrl.trim()) && !f.linkUrl.trim().startsWith('/')) e.linkUrl = 'Liên kết bắt đầu bằng http(s):// hoặc /.'
          if (!/^[A-Za-z0-9_-]{1,50}$/.test(f.position.trim())) e.position = 'Mã vị trí gồm chữ, số, gạch ngang hoặc gạch dưới.'
          if (!/^\d+$/.test(f.sortOrder.trim())) e.sortOrder = 'Nhập số nguyên từ 0.'
          return { ...e, ...checkPeriod(f.startAt, f.endAt, false) }
        },
        async (f) => {
          const body = {
            title: f.title.trim(),
            imageUrl: f.imageUrl.trim(),
            linkUrl: f.linkUrl.trim() || null,
            position: f.position.trim().toUpperCase(),
            sortOrder: Number(f.sortOrder),
            startAt: fromLocalInput(f.startAt),
            endAt: fromLocalInput(f.endAt),
            status: f.status,
          }
          if (item) await marketing.banners.update(item.id, body)
          else await marketing.banners.create(body)
          onSaved(item ? `Đã cập nhật banner ${body.title}.` : `Đã thêm banner ${body.title}.`)
        },
      )}
    >
      <Field label="Tiêu đề" htmlFor={`${id}-title`} error={errors.title} wide>
        <input {...bind(id, 'title', errors)} maxLength={200} value={form.title} onChange={(e) => set('title', e.target.value)} />
      </Field>
      <Field label="Ảnh banner" htmlFor={`${id}-imageUrl-file`} wide>
        <AvatarField
          id={`${id}-imageUrl`}
          shape="square"
          upload={uploadImage}
          maxBytes={IMAGE_MAX_BYTES}
          value={form.imageUrl}
          name={form.title}
          error={errors.imageUrl}
          onChange={(url) => set('imageUrl', url)}
          onUploadingChange={setUploading}
        />
      </Field>
      <Field label="Liên kết khi bấm" htmlFor={`${id}-linkUrl`} error={errors.linkUrl} optional wide hint="Ví dụ: /shop?category=ao-thun">
        <input {...bind(id, 'linkUrl', errors)} maxLength={2048} value={form.linkUrl} onChange={(e) => set('linkUrl', e.target.value)} />
      </Field>
      <Field label="Vị trí" htmlFor={`${id}-position`} error={errors.position}>
        <input {...bind(id, 'position', errors)} className="mono-input" maxLength={50} value={form.position} onChange={(e) => set('position', e.target.value.toUpperCase())} />
      </Field>
      <Field label="Thứ tự" htmlFor={`${id}-sortOrder`} error={errors.sortOrder} hint="Số nhỏ hiện trước.">
        <input {...bind(id, 'sortOrder', errors)} inputMode="numeric" value={form.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} />
      </Field>
      <PeriodFields id={id} startAt={form.startAt} endAt={form.endAt} errors={errors} onChange={(key, value) => set(key, value)} />
      <MarketingStatusField id={id} value={form.status} onChange={(s) => set('status', s)} />
    </FormDialog>
  )
}
