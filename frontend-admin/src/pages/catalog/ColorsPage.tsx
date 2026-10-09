import { PaletteIcon } from '@phosphor-icons/react'
import { useId } from 'react'
import { CATALOG_STATUS_LABEL, catalog, type CatalogStatus, type Color } from '../../api/catalog'
import { useAuth } from '../../auth/AuthContext'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { formatDate } from '../../format'
import { StatusSelect, Swatch } from './shared'

const HEX = /^#[0-9A-Fa-f]{6}$/
const CODE = /^[A-Za-z0-9_-]{1,50}$/

export default function ColorsPage() {
  const { user } = useAuth()
  return (
    <ResourcePage<Color>
      title="Màu sắc"
      lede="Bảng màu dùng để tạo biến thể sản phẩm. Mã màu là định danh ngắn, ví dụ BLACK hoặc NAVY."
      addLabel="Thêm màu"
      itemLabel="màu"
      searchPlaceholder="Tìm theo tên hoặc mã màu"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={CATALOG_STATUS_LABEL}
      load={(query) => catalog.colors.list(query)}
      rowLabel={(color) => `màu ${color.name}`}
      columns={[
        {
          header: 'Màu',
          render: (color) => (
            <span className="inline-cell">
              <Swatch hex={color.hexCode} size={22} />
              <span className="person-name">{color.name}</span>
            </span>
          ),
        },
        { header: 'Mã', render: (color) => <code className="role-code">{color.code}</code> },
        { header: 'Mã hex', render: (color) => <span className="mono">{color.hexCode ?? '-'}</span> },
        { header: 'Biến thể', render: (color) => color.variantCount, className: 'num' },
        { header: 'Trạng thái', render: (color) => <StatusPill value={color.status} label={CATALOG_STATUS_LABEL[color.status]} /> },
        { header: 'Cập nhật', render: (color) => formatDate(color.updatedAt), className: 'muted nowrap' },
      ]}
      deleteTitle="Xóa màu"
      deleteMessage={(color) => (
        <>
          Xóa màu <strong>{color.name}</strong> ({color.code})?
          {color.variantCount > 0 && ` Màu đang dùng cho ${color.variantCount} biến thể nên sẽ không xóa được.`}
        </>
      )}
      remove={(color) => catalog.colors.remove(color.id)}
      emptyIcon={<PaletteIcon size={36} aria-hidden />}
      renderForm={(slot) => <ColorForm {...slot} />}
    />
  )
}

interface ColorValues {
  name: string
  code: string
  hexCode: string
  status: CatalogStatus
}

function ColorForm({ open, item, onClose, onSaved }: FormSlot<Color>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm<ColorValues>(
    open,
    () => ({ name: item?.name ?? '', code: item?.code ?? '', hexCode: item?.hexCode ?? '', status: item?.status ?? 'ACTIVE' }),
    [item],
  )
  const validHex = HEX.test(form.hexCode)

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa màu' : 'Thêm màu'}
      description={item?.name}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm màu'}
      error={error}
      onClose={onClose}
      size="md"
      onSubmit={submit(
        (f) => ({
          ...(f.name.trim() ? {} : { name: 'Nhập tên màu.' }),
          ...(CODE.test(f.code.trim()) ? {} : { code: 'Mã gồm 1-50 ký tự chữ, số, gạch ngang hoặc gạch dưới.' }),
          ...(f.hexCode && !HEX.test(f.hexCode) ? { hexCode: 'Mã màu dạng #RRGGBB.' } : {}),
        }),
        async (f) => {
          const body = { name: f.name.trim(), code: f.code.trim(), hexCode: f.hexCode || null, status: f.status }
          const saved = item ? await catalog.colors.update(item.id, body) : await catalog.colors.create(body)
          onSaved(item ? `Đã cập nhật màu ${saved.name}.` : `Đã thêm màu ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên màu" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={100} placeholder="Xanh navy" />
      </Field>
      <Field label="Mã màu" htmlFor={`${id}-code`} error={errors.code} hint="Lưu thành chữ hoa, dùng trong SKU.">
        <input {...bind(id, 'code', errors)} className="mono" value={form.code} onChange={(e) => set('code', e.target.value.toUpperCase())} maxLength={50} placeholder="NAVY" />
      </Field>
      <Field label="Mã hex" htmlFor={`${id}-hexCode`} optional error={errors.hexCode}>
        <div className="color-input">
          <input
            type="color"
            aria-label="Chọn màu"
            value={validHex ? form.hexCode : '#000000'}
            onChange={(e) => set('hexCode', e.target.value.toUpperCase())}
          />
          <input
            {...bind(id, 'hexCode', errors)}
            className="mono"
            value={form.hexCode}
            onChange={(e) => set('hexCode', e.target.value.toUpperCase())}
            maxLength={7}
            placeholder="#1F2A44"
          />
        </div>
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} />
    </FormDialog>
  )
}
