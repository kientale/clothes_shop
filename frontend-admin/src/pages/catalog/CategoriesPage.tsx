import { TreeStructureIcon } from '@phosphor-icons/react'
import { useId, useMemo } from 'react'
import { CATALOG_STATUS_LABEL, catalog, categoryPaths, slugify, type CatalogOptions, type CatalogStatus, type Category } from '../../api/catalog'
import { useAuth } from '../../auth/AuthContext'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { ResourcePage, StatusPill, type FormSlot } from '../../components/ResourcePage'
import { formatDate } from '../../format'
import { useAsync } from '../../hooks'
import { StatusSelect } from './shared'

export default function CategoriesPage() {
  const { user } = useAuth()
  const options = useAsync(() => catalog.options(), [])
  return (
    <ResourcePage<Category>
      title="Danh mục sản phẩm"
      lede="Cây danh mục để phân loại sản phẩm. Danh mục có thể nằm trong danh mục cha, ví dụ Nữ / Áo / Áo thun."
      addLabel="Thêm danh mục"
      itemLabel="danh mục"
      searchPlaceholder="Tìm theo tên hoặc slug"
      canWrite={user?.permissions.includes('PRODUCT_WRITE') ?? false}
      statusLabels={CATALOG_STATUS_LABEL}
      load={(query) => catalog.categories.list(query)}
      rowLabel={(category) => `danh mục ${category.name}`}
      columns={[
        {
          header: 'Danh mục',
          render: (category) => (
            <div className="role-cell">
              <span className="role-name">{category.name}</span>
              <code className="role-code">{category.slug}</code>
            </div>
          ),
        },
        { header: 'Danh mục cha', render: (category) => category.parentName ?? <span className="muted">Gốc</span> },
        { header: 'Danh mục con', render: (category) => category.childCount, className: 'num' },
        { header: 'Sản phẩm', render: (category) => category.productCount, className: 'num' },
        { header: 'Trạng thái', render: (category) => <StatusPill value={category.status} label={CATALOG_STATUS_LABEL[category.status]} /> },
        { header: 'Cập nhật', render: (category) => formatDate(category.updatedAt), className: 'muted nowrap' },
      ]}
      deleteTitle="Xóa danh mục"
      deleteMessage={(category) => (
        <>
          Xóa danh mục <strong>{category.name}</strong>?
          {category.childCount > 0 && ` Danh mục còn ${category.childCount} danh mục con.`}
          {category.productCount > 0 && ` Danh mục còn ${category.productCount} sản phẩm.`}
          {(category.childCount > 0 || category.productCount > 0) && ' Cần chuyển chúng đi trước khi xóa.'}
        </>
      )}
      remove={(category) => catalog.categories.remove(category.id)}
      emptyIcon={<TreeStructureIcon size={36} aria-hidden />}
      onChanged={options.reload}
      renderForm={(slot) => <CategoryForm {...slot} options={options.data} />}
    />
  )
}

interface CategoryValues {
  parentId: string
  name: string
  slug: string
  description: string
  status: CatalogStatus
}

function CategoryForm({ open, item, onClose, onSaved, options }: FormSlot<Category> & { options?: CatalogOptions }) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm<CategoryValues>(
    open,
    () => ({
      parentId: item?.parentId ?? '',
      name: item?.name ?? '',
      slug: item?.slug ?? '',
      description: item?.description ?? '',
      status: item?.status ?? 'ACTIVE',
    }),
    [item],
  )

  // A category cannot move under itself or any of its descendants.
  const parents = useMemo(() => {
    const all = options?.categories ?? []
    const blocked = new Set<string>()
    if (item) {
      blocked.add(item.id)
      let grew = true
      while (grew) {
        grew = false
        for (const category of all) {
          if (category.parentId && blocked.has(category.parentId) && !blocked.has(category.id)) {
            blocked.add(category.id)
            grew = true
          }
        }
      }
    }
    return categoryPaths(all).filter((category) => !blocked.has(category.id))
  }, [options, item])

  return (
    <FormDialog
      open={open}
      title={item ? 'Sửa danh mục' : 'Thêm danh mục'}
      description={item?.name}
      busy={busy}
      submitLabel={item ? 'Lưu thay đổi' : 'Thêm danh mục'}
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f): Record<string, string> => (f.name.trim() ? {} : { name: 'Nhập tên danh mục.' }),
        async (f) => {
          const body = {
            parentId: f.parentId || null,
            name: f.name.trim(),
            slug: f.slug.trim() || null,
            description: f.description.trim() || null,
            status: f.status,
          }
          const saved = item ? await catalog.categories.update(item.id, body) : await catalog.categories.create(body)
          onSaved(item ? `Đã cập nhật danh mục ${saved.name}.` : `Đã thêm danh mục ${saved.name}.`)
        },
      )}
    >
      <Field label="Tên danh mục" htmlFor={`${id}-name`} error={errors.name}>
        <input {...bind(id, 'name', errors)} value={form.name} onChange={(e) => set('name', e.target.value)} maxLength={150} placeholder="Áo thun" />
      </Field>
      <Field label="Slug" htmlFor={`${id}-slug`} optional error={errors.slug} hint={`Để trống sẽ dùng: ${slugify(form.name) || '...'}`}>
        <input {...bind(id, 'slug', errors)} className="mono" value={form.slug} onChange={(e) => set('slug', slugify(e.target.value))} maxLength={200} />
      </Field>
      <Field label="Danh mục cha" htmlFor={`${id}-parentId`} optional error={errors.parentId} wide>
        <select {...bind(id, 'parentId', errors)} value={form.parentId} onChange={(e) => set('parentId', e.target.value)}>
          <option value="">Không có (danh mục gốc)</option>
          {parents.map((category) => (
            <option key={category.id} value={category.id}>
              {category.path}
              {category.status === 'INACTIVE' ? ' (đang ẩn)' : ''}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Mô tả" htmlFor={`${id}-description`} optional wide error={errors.description}>
        <textarea {...bind(id, 'description', errors)} rows={3} value={form.description} onChange={(e) => set('description', e.target.value)} maxLength={5000} />
      </Field>
      <StatusSelect id={id} value={form.status} onChange={(status) => set('status', status)} />
    </FormDialog>
  )
}
