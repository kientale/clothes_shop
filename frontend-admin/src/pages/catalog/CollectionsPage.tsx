import { ArrowDownIcon, ArrowUpIcon, SquaresFourIcon, TShirtIcon, XIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { CATALOG_STATUS_LABEL, catalog, slugify, type CatalogStatus, type Collection } from '../../api/catalog'
import { IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'
import { useAuth } from '../../auth/AuthContext'
import { AvatarField } from '../../components/AvatarField'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { useAsync } from '../../hooks'
import { ProductCombobox, type ProductPick } from './ProductCombobox'
import { StatusSelect, checkUrl } from './shared'

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

function period(collection: Collection) {
  if (!collection.startAt && !collection.endAt) return <span className="muted">Không giới hạn</span>
  const from = collection.startAt ? dateTime.format(new Date(collection.startAt)) : '...'
  const to = collection.endAt ? dateTime.format(new Date(collection.endAt)) : '...'
  return `${from} - ${to}`
}

/** ISO instant to the value of a datetime-local input (local time, minutes). */
function toLocalInput(iso: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function CollectionsPage() {
  const { user } = useAuth()
  return (
    <ResourcePage<Collection>
      title="Bộ sưu tập"
      lede="Nhóm sản phẩm theo chủ đề hoặc mùa, có thứ tự hiển thị và thời gian áp dụng."
      addLabel="Thêm bộ sưu tập"
      itemLabel="bộ sưu tập"
      searchPlaceholder="Tìm theo tên hoặc slug"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={CATALOG_STATUS_LABEL}
      load={(query) => catalog.collections.list(query)}
      rowLabel={(collection) => `bộ sưu tập ${collection.name}`}
      columns={[
        {
          header: 'Bộ sưu tập',
          render: (collection) => (
            <div className="person">
              {collection.imageUrl ? (
                <img className="thumb thumb-wide" src={collection.imageUrl} alt="" loading="lazy" />
              ) : (
                <span className="thumb thumb-wide" aria-hidden>
                  <SquaresFourIcon size={18} />
                </span>
              )}
              <div className="person-text">
                <span className="person-name">{collection.name}</span>
                <span className="person-sub mono">{collection.slug}</span>
              </div>
            </div>
          ),
        },
        { header: 'Thời gian', render: period, className: 'nowrap' },
        { header: 'Sản phẩm', render: (collection) => collection.productCount, className: 'num' },
        { header: 'Trạng thái', render: (collection) => <StatusPill value={collection.status} label={CATALOG_STATUS_LABEL[collection.status]} /> },
      ]}
      deleteTitle="Xóa bộ sưu tập"
      deleteMessage={(collection) => (
        <>
          Xóa bộ sưu tập <strong>{collection.name}</strong>? Các sản phẩm trong bộ sưu tập không bị ảnh hưởng.
        </>
      )}
      remove={(collection) => catalog.collections.remove(collection.id)}
      emptyIcon={<SquaresFourIcon size={36} aria-hidden />}
      renderForm={(slot) => <CollectionForm {...slot} />}
    />
  )
}

interface CollectionValues {
  name: string
  slug: string
  description: string
  imageUrl: string
  startAt: string
  endAt: string
  status: CatalogStatus
  products: ProductPick[]
}

function CollectionForm({ open, item, onClose, onSaved }: FormSlot<Collection>) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  // The list endpoint carries only counts; load the ordered products when editing.
  const detail = useAsync(() => (open && item ? catalog.collections.get(item.id) : Promise.resolve(null)), [open, item?.id])
  const ready = !item || detail.data !== undefined
  const { form, set, errors, error, busy, submit } = useDialogForm<CollectionValues>(
    open,
    () => ({
      name: item?.name ?? '',
      slug: item?.slug ?? '',
      description: item?.description ?? '',
      imageUrl: item?.imageUrl ?? '',
      startAt: toLocalInput(item?.startAt ?? null),
      endAt: toLocalInput(item?.endAt ?? null),
      status: item?.status ?? 'ACTIVE',
      products: (detail.data?.products ?? []).map((p) => ({ id: p.id, name: p.name, productCode: p.productCode, imageUrl: p.imageUrl })),
    }),
    [item, detail.data],
  )

  const move = (from: number, to: number) => {
    const next = [...form.products]
    const [moved] = next.splice(from, 1)
    next.splice(to, 0, moved!)
    set('products', next)
  }

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa bộ sưu tập' : 'Thêm bộ sưu tập'}
      description={item?.name}
      busy={busy}
      disabled={uploading || !ready}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm bộ sưu tập'}
      error={error ?? detail.error}
      onClose={onClose}
      onSubmit={submit(
        (f) => ({
          ...(f.name.trim() ? {} : { name: 'Nhập tên bộ sưu tập.' }),
          ...checkUrl('imageUrl', f.imageUrl),
          ...(f.startAt && f.endAt && new Date(f.endAt) <= new Date(f.startAt) ? { endAt: 'Thời gian kết thúc phải sau thời gian bắt đầu.' } : {}),
        }),
        async (f) => {
          const body = {
            name: f.name.trim(),
            slug: f.slug.trim() || null,
            description: f.description.trim() || null,
            imageUrl: f.imageUrl.trim() || null,
            startAt: f.startAt ? new Date(f.startAt).toISOString() : null,
            endAt: f.endAt ? new Date(f.endAt).toISOString() : null,
            status: f.status,
            productIds: f.products.map((p) => p.id),
          }
          const saved = item ? await catalog.collections.update(item.id, body) : await catalog.collections.create(body)
          onSaved(item ? `Đã cập nhật bộ sưu tập ${saved.name}.` : `Đã thêm bộ sưu tập ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên bộ sưu tập" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={150} placeholder="Hè 2026" />
      </Field>
      <Field label="Slug" htmlFor={`${id}-slug`} optional error={errors.slug} hint={`Để trống sẽ dùng: ${slugify(form.name) || '...'}`}>
        <input {...bind(id, 'slug', errors)} className="mono" value={form.slug} onChange={(e) => set('slug', slugify(e.target.value))} maxLength={200} />
      </Field>
      <Field label="Bắt đầu" htmlFor={`${id}-startAt`} optional error={errors.startAt}>
        <input {...bind(id, 'startAt', errors)} type="datetime-local" value={form.startAt} onChange={(e) => set('startAt', e.target.value)} />
      </Field>
      <Field label="Kết thúc" htmlFor={`${id}-endAt`} optional error={errors.endAt}>
        <input {...bind(id, 'endAt', errors)} type="datetime-local" value={form.endAt} min={form.startAt || undefined} onChange={(e) => set('endAt', e.target.value)} />
      </Field>
      <Field label="Ảnh bìa" htmlFor={`${id}-imageUrl-file`} optional wide>
        <AvatarField
          id={`${id}-imageUrl`}
          shape="square"
          upload={uploadImage}
          maxBytes={IMAGE_MAX_BYTES}
          value={form.imageUrl}
          name={form.name}
          error={errors.imageUrl}
          onChange={(url) => set('imageUrl', url)}
          onUploadingChange={setUploading}
        />
      </Field>
      <div className="field span-2">
        <span className="field-label">Sản phẩm ({form.products.length})</span>
        <ProductCombobox
          value={null}
          placeholder="Thêm sản phẩm vào bộ sưu tập"
          exclude={form.products.map((p) => p.id)}
          onChange={(pick) => pick && set('products', [...form.products, pick])}
        />
        {!ready ? (
          <span className="skel skel-row" aria-hidden />
        ) : form.products.length === 0 ? (
          <span className="field-hint">Chưa có sản phẩm. Tìm và chọn sản phẩm ở ô trên; thứ tự trong danh sách là thứ tự hiển thị.</span>
        ) : (
          <ol className="collection-items">
            {form.products.map((product, index) => (
              <li key={product.id}>
                <span className="collection-index">{index + 1}</span>
                {product.imageUrl ? <img src={product.imageUrl} alt="" /> : <span className="combo-thumb"><TShirtIcon size={14} /></span>}
                <span className="combo-text">
                  <span className="truncate">{product.name}</span>
                  <span className="mono muted small">{product.productCode}</span>
                </span>
                <button type="button" className="icon-btn" disabled={index === 0} onClick={() => move(index, index - 1)} aria-label={`Đưa ${product.name} lên`} title="Lên">
                  <ArrowUpIcon size={14} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  disabled={index === form.products.length - 1}
                  onClick={() => move(index, index + 1)}
                  aria-label={`Đưa ${product.name} xuống`}
                  title="Xuống"
                >
                  <ArrowDownIcon size={14} />
                </button>
                <button
                  type="button"
                  className="icon-btn danger"
                  onClick={() => set('products', form.products.filter((p) => p.id !== product.id))}
                  aria-label={`Bỏ ${product.name} khỏi bộ sưu tập`}
                  title="Bỏ ra"
                >
                  <XIcon size={14} />
                </button>
              </li>
            ))}
          </ol>
        )}
      </div>
      <Field label="Mô tả" htmlFor={`${id}-description`} optional wide error={errors.description}>
        <textarea {...bind(id, 'description', errors)} rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} maxLength={5000} />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} />
    </FormDialog>
  )
}
