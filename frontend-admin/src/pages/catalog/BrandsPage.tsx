import { StorefrontIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { CATALOG_STATUS_LABEL, catalog, slugify, type Brand, type CatalogStatus } from '../../api/catalog'
import { IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'
import { useAuth } from '../../auth/AuthContext'
import { AvatarField } from '../../components/AvatarField'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { initials } from '../../components/ui'
import { formatDate } from '../../format'
import { StatusSelect, checkUrl } from './shared'

export default function BrandsPage() {
  const { user } = useAuth()
  return (
    <ResourcePage<Brand>
      title="Thương hiệu"
      lede="Nhãn hàng gắn với từng sản phẩm. Chỉ xóa được thương hiệu không còn sản phẩm."
      addLabel="Thêm thương hiệu"
      itemLabel="thương hiệu"
      searchPlaceholder="Tìm theo tên hoặc slug"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={CATALOG_STATUS_LABEL}
      load={(query) => catalog.brands.list(query)}
      rowLabel={(brand) => brand.name}
      columns={[
        {
          header: 'Thương hiệu',
          render: (brand) => (
            <div className="person">
              {brand.logoUrl ? (
                <img className="thumb" src={brand.logoUrl} alt="" loading="lazy" />
              ) : (
                <span className="thumb" aria-hidden>
                  {initials(brand.name)}
                </span>
              )}
              <div className="person-text">
                <span className="person-name">{brand.name}</span>
                <span className="person-sub mono">{brand.slug}</span>
              </div>
            </div>
          ),
        },
        { header: 'Sản phẩm', render: (brand) => brand.productCount, className: 'num' },
        { header: 'Trạng thái', render: (brand) => <StatusPill value={brand.status} label={CATALOG_STATUS_LABEL[brand.status]} /> },
        { header: 'Cập nhật', render: (brand) => formatDate(brand.updatedAt), className: 'muted nowrap' },
      ]}
      deleteTitle="Xóa thương hiệu"
      deleteMessage={(brand) => (
        <>
          Xóa thương hiệu <strong>{brand.name}</strong>?
          {brand.productCount > 0 && ` Thương hiệu đang có ${brand.productCount} sản phẩm nên sẽ không xóa được.`}
        </>
      )}
      remove={(brand) => catalog.brands.remove(brand.id)}
      emptyIcon={<StorefrontIcon size={36} aria-hidden />}
      renderForm={(slot) => <BrandForm {...slot} />}
    />
  )
}

interface BrandValues {
  name: string
  slug: string
  logoUrl: string
  description: string
  status: CatalogStatus
}

function BrandForm({ open, item, onClose, onSaved }: FormSlot<Brand>) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const { form, set, errors, error, busy, submit } = useDialogForm<BrandValues>(
    open,
    () => ({
      name: item?.name ?? '',
      slug: item?.slug ?? '',
      logoUrl: item?.logoUrl ?? '',
      description: item?.description ?? '',
      status: item?.status ?? 'ACTIVE',
    }),
    [item],
  )

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa thương hiệu' : 'Thêm thương hiệu'}
      description={item?.name}
      busy={busy}
      disabled={uploading}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm thương hiệu'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => ({
          ...(f.name.trim() ? {} : { name: 'Nhập tên thương hiệu.' }),
          ...checkUrl('logoUrl', f.logoUrl),
        }),
        async (f) => {
          const body = {
            name: f.name.trim(),
            slug: f.slug.trim() || null,
            logoUrl: f.logoUrl.trim() || null,
            description: f.description.trim() || null,
            status: f.status,
          }
          const saved = item ? await catalog.brands.update(item.id, body) : await catalog.brands.create(body)
          onSaved(item ? `Đã cập nhật thương hiệu ${saved.name}.` : `Đã thêm thương hiệu ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên thương hiệu" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={150} />
      </Field>
      <Field label="Slug" htmlFor={`${id}-slug`} optional error={errors.slug} hint={`Để trống sẽ dùng: ${slugify(form.name) || '...'}`}>
        <input {...bind(id, 'slug', errors)} className="mono" value={form.slug} onChange={(e) => set('slug', slugify(e.target.value))} maxLength={200} />
      </Field>
      <Field label="Logo" htmlFor={`${id}-logoUrl-file`} optional wide>
        <AvatarField
          id={`${id}-logoUrl`}
          shape="square"
          upload={uploadImage}
          maxBytes={IMAGE_MAX_BYTES}
          value={form.logoUrl}
          name={form.name}
          error={errors.logoUrl}
          onChange={(url) => set('logoUrl', url)}
          onUploadingChange={setUploading}
        />
      </Field>
      <Field label="Mô tả" htmlFor={`${id}-description`} optional wide error={errors.description}>
        <textarea {...bind(id, 'description', errors)} rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} maxLength={5000} />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} />
    </FormDialog>
  )
}
