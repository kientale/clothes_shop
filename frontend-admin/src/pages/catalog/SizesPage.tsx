import { RulerIcon } from '@phosphor-icons/react'
import { useId } from 'react'
import { CATALOG_STATUS_LABEL, catalog, type CatalogStatus, type Size } from '../../api/catalog'
import { useAuth } from '../../auth/AuthContext'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { formatDate } from '../../format'
import { StatusSelect } from './shared'

const CODE = /^[A-Za-z0-9_-]{1,50}$/

export default function SizesPage() {
  const { user } = useAuth()
  return (
    <ResourcePage<Size>
      title="Size"
      lede="Danh sách size theo thứ tự hiển thị, từ nhỏ đến lớn. Thứ tự này dùng khi tạo biến thể và ở cửa hàng."
      addLabel="Thêm size"
      itemLabel="size"
      searchPlaceholder="Tìm theo tên hoặc mã size"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={CATALOG_STATUS_LABEL}
      load={(query) => catalog.sizes.list(query)}
      rowLabel={(size) => `size ${size.name}`}
      columns={[
        { header: 'Size', render: (size) => <span className="size-chip">{size.name}</span> },
        { header: 'Mã', render: (size) => <code className="role-code">{size.code}</code> },
        { header: 'Thứ tự', render: (size) => size.sortOrder, className: 'num' },
        { header: 'Biến thể', render: (size) => size.variantCount, className: 'num' },
        { header: 'Trạng thái', render: (size) => <StatusPill value={size.status} label={CATALOG_STATUS_LABEL[size.status]} /> },
        { header: 'Cập nhật', render: (size) => formatDate(size.updatedAt), className: 'muted nowrap' },
      ]}
      deleteTitle="Xóa size"
      deleteMessage={(size) => (
        <>
          Xóa size <strong>{size.name}</strong>?
          {size.variantCount > 0 && ` Size đang dùng cho ${size.variantCount} biến thể nên sẽ không xóa được.`}
        </>
      )}
      remove={(size) => catalog.sizes.remove(size.id)}
      emptyIcon={<RulerIcon size={36} aria-hidden />}
      renderForm={(slot) => <SizeForm {...slot} />}
    />
  )
}

interface SizeValues {
  name: string
  code: string
  sortOrder: string
  status: CatalogStatus
}

function SizeForm({ open, item, onClose, onSaved }: FormSlot<Size>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm<SizeValues>(
    open,
    () => ({ name: item?.name ?? '', code: item?.code ?? '', sortOrder: String(item?.sortOrder ?? 0), status: item?.status ?? 'ACTIVE' }),
    [item],
  )

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa size' : 'Thêm size'}
      description={item?.name}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm size'}
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => ({
          ...(f.name.trim() ? {} : { name: 'Nhập tên size.' }),
          ...(CODE.test(f.code.trim()) ? {} : { code: 'Mã gồm 1-50 ký tự chữ, số, gạch ngang hoặc gạch dưới.' }),
          ...(/^\d{1,6}$/.test(f.sortOrder) && Number(f.sortOrder) <= 100000 ? {} : { sortOrder: 'Thứ tự là số nguyên từ 0 đến 100000.' }),
        }),
        async (f) => {
          const body = { name: f.name.trim(), code: f.code.trim(), sortOrder: Number(f.sortOrder), status: f.status }
          const saved = item ? await catalog.sizes.update(item.id, body) : await catalog.sizes.create(body)
          onSaved(item ? `Đã cập nhật size ${saved.name}.` : `Đã thêm size ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên size" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={50} placeholder="XL" />
      </Field>
      <Field label="Mã size" htmlFor={`${id}-code`} error={errors.code} hint="Lưu thành chữ hoa, dùng trong SKU.">
        <input {...bind(id, 'code', errors)} className="mono" value={form.code} onChange={(e) => set('code', e.target.value.toUpperCase())} maxLength={50} placeholder="XL" />
      </Field>
      <Field label="Thứ tự hiển thị" htmlFor={`${id}-sortOrder`} error={errors.sortOrder} hint="Số nhỏ hiện trước, ví dụ S = 1, M = 2, L = 3.">
        <input {...bind(id, 'sortOrder', errors)} type="number" min={0} max={100000} value={form.sortOrder} onChange={(e) => set('sortOrder', e.target.value)} />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} />
    </FormDialog>
  )
}
