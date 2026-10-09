import { BellIcon, PaperPlaneTiltIcon, XIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { accessErrorMessage } from '../../api/access'
import {
  NOTIFICATION_STATUS_LABEL,
  NOTIFICATION_TYPE_LABEL,
  marketing,
  type Notification,
  type NotificationTarget,
  type NotificationType,
} from '../../api/marketing'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { Pill, SearchPicker, countOf, useCan, whenOf, type PickOption } from '../../components/kit'
import { searchCustomers } from '../../components/pickers'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'
import { ConfirmDialog } from '../../components/ui'

export default function NotificationsPage() {
  const canWrite = useCan('NOTIFICATION_WRITE')
  const toast = useToast()
  const [publishing, setPublishing] = useState<Notification | null>(null)
  const [reload, setReload] = useState(0)
  return (
    <>
      <ResourcePage<Notification>
        title="Thông báo"
        lede="Thông báo trong ứng dụng cho khách hàng. Soạn bản nháp, kiểm tra rồi phát hành; bản đã phát hành không sửa được."
        addLabel="Soạn thông báo"
        itemLabel="thông báo"
        searchPlaceholder="Tìm theo tiêu đề"
        canWrite={canWrite}
        statusLabels={NOTIFICATION_STATUS_LABEL}
        reloadToken={reload}
        load={(query) => marketing.notifications.list(query)}
        rowLabel={(n) => n.title}
        columns={[
          {
            header: 'Thông báo',
            render: (n) => (
              <div className="person-text">
                <span className="person-name">{n.title}</span>
                <span className="person-sub clamp-2">{n.content}</span>
              </div>
            ),
          },
          { header: 'Loại', render: (n) => <span className="chip chip-sm">{NOTIFICATION_TYPE_LABEL[n.notificationType]}</span> },
          { header: 'Người nhận', render: (n) => (n.targetType === 'ALL' ? 'Tất cả khách' : `${countOf(n.customerIds.length)} khách chọn lọc`), className: 'nowrap' },
          {
            header: 'Đã đọc',
            render: (n) => (n.status === 'PUBLISHED' ? `${countOf(n.readCount)} / ${countOf(n.recipientCount)}` : <span className="muted">-</span>),
            className: 'num nowrap',
          },
          {
            header: 'Trạng thái',
            render: (n) => (
              <div className="person-text">
                <Pill tone={n.status === 'PUBLISHED' ? 'active' : 'inactive'}>{NOTIFICATION_STATUS_LABEL[n.status]}</Pill>
                {n.publishedAt && <span className="person-sub">{whenOf(n.publishedAt)}</span>}
              </div>
            ),
          },
        ]}
        rowActions={(n) =>
          canWrite && n.status === 'DRAFT' ? (
            <button type="button" className="icon-btn" onClick={() => setPublishing(n)} aria-label={`Phát hành ${n.title}`} title="Phát hành">
              <PaperPlaneTiltIcon size={16} />
            </button>
          ) : null
        }
        deleteTitle="Xóa thông báo"
        deleteMessage={(n) => (
          <>
            Xóa bản nháp <strong>{n.title}</strong>? Chỉ xóa được thông báo chưa phát hành.
          </>
        )}
        remove={(n) => marketing.notifications.remove(n.id)}
        emptyIcon={<BellIcon size={36} aria-hidden />}
        renderForm={(slot) => <NotificationForm {...slot} />}
      />
      <ConfirmDialog
        open={publishing !== null}
        title="Phát hành thông báo"
        message={
          publishing && (
            <>
              Gửi <strong>{publishing.title}</strong> tới {publishing.targetType === 'ALL' ? 'mọi khách hàng đang hoạt động' : `${publishing.customerIds.length} khách đã chọn`}? Sau khi phát hành
              không sửa hoặc thu hồi được.
            </>
          )
        }
        confirmLabel="Phát hành"
        formatError={accessErrorMessage}
        onClose={() => setPublishing(null)}
        onConfirm={async () => {
          const result = await marketing.notifications.publish(publishing!.id)
          toast.success(`Đã phát hành tới ${countOf(result.recipientCount)} khách hàng.`)
          setReload((n) => n + 1)
        }}
      />
    </>
  )
}

function NotificationForm({ open, item, onClose, onSaved }: FormSlot<Notification>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      title: item?.title ?? '',
      content: item?.content ?? '',
      notificationType: (item?.notificationType ?? 'GENERAL') as NotificationType,
      targetType: (item?.targetType ?? 'ALL') as NotificationTarget,
      customers: (item?.customerIds ?? []).map<PickOption>((cid) => ({ id: cid, title: `Khách ${cid.slice(0, 8).toUpperCase()}` })),
    }),
    [item?.id],
  )
  const readOnly = item?.status === 'PUBLISHED'
  return (
    <FormDialog
      open={open}
      title={readOnly ? 'Thông báo đã phát hành' : item ? 'Sửa bản nháp' : 'Soạn thông báo'}
      description={readOnly ? 'Nội dung đã phát hành được giữ nguyên.' : 'Lưu ở dạng nháp. Phát hành từ danh sách khi đã sẵn sàng.'}
      busy={busy}
      disabled={readOnly}
      submitLabel="Lưu nháp"
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.title.trim()) e.title = 'Nhập tiêu đề.'
          if (!f.content.trim()) e.content = 'Nhập nội dung.'
          if (f.targetType === 'SELECTED' && f.customers.length === 0) e.customerIds = 'Chọn ít nhất một khách hàng.'
          return e
        },
        async (f) => {
          const body = {
            title: f.title.trim(),
            content: f.content.trim(),
            notificationType: f.notificationType,
            targetType: f.targetType,
            customerIds: f.targetType === 'SELECTED' ? f.customers.map((c) => c.id) : [],
          }
          if (item) await marketing.notifications.update(item.id, body)
          else await marketing.notifications.create(body)
          onSaved(item ? 'Đã lưu bản nháp.' : 'Đã tạo bản nháp thông báo.')
        },
      )}
    >
      <Field label="Tiêu đề" htmlFor={`${id}-title`} error={errors.title}>
        <input {...bind(id, 'title', errors)} disabled={readOnly} maxLength={200} value={form.title} onChange={(e) => set('title', e.target.value)} />
      </Field>
      <Field label="Loại" htmlFor={`${id}-type`}>
        <select id={`${id}-type`} disabled={readOnly} value={form.notificationType} onChange={(e) => set('notificationType', e.target.value as NotificationType)}>
          {(Object.keys(NOTIFICATION_TYPE_LABEL) as NotificationType[]).map((t) => (
            <option key={t} value={t}>
              {NOTIFICATION_TYPE_LABEL[t]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Nội dung" htmlFor={`${id}-content`} error={errors.content} wide>
        <textarea {...bind(id, 'content', errors)} disabled={readOnly} rows={4} maxLength={5000} value={form.content} onChange={(e) => set('content', e.target.value)} />
      </Field>
      <fieldset className="choice-group span-2" disabled={readOnly}>
        <legend className="field-label">Người nhận</legend>
        <div className="choice-list">
          <label className="choice">
            <input type="radio" name={`${id}-target`} checked={form.targetType === 'ALL'} onChange={() => set('targetType', 'ALL')} />
            <span>Mọi khách hàng đang hoạt động</span>
          </label>
          <label className="choice">
            <input type="radio" name={`${id}-target`} checked={form.targetType === 'SELECTED'} onChange={() => set('targetType', 'SELECTED')} />
            <span>Chọn khách hàng</span>
          </label>
        </div>
      </fieldset>
      {form.targetType === 'SELECTED' && (
        <Field label="Khách hàng nhận" htmlFor={`${id}-customers`} error={errors.customerIds} wide hint="Tối đa 1000 khách.">
          {!readOnly && (
            <SearchPicker
              inputId={`${id}-customers`}
              value={null}
              onChange={(c) => c && !form.customers.some((x) => x.id === c.id) && set('customers', [...form.customers, c])}
              search={searchCustomers}
              placeholder="Thêm khách hàng"
              searchPlaceholder="Tìm theo tên, điện thoại, email"
              invalid={Boolean(errors.customerIds)}
            />
          )}
          {form.customers.length > 0 && (
            <div className="chips picked">
              {form.customers.map((c) => (
                <span key={c.id} className="chip chip-sm">
                  {c.title}
                  {!readOnly && (
                    <button type="button" className="chip-x" onClick={() => set('customers', form.customers.filter((x) => x.id !== c.id))} aria-label={`Bỏ ${c.title}`}>
                      <XIcon size={11} />
                    </button>
                  )}
                </span>
              ))}
            </div>
          )}
        </Field>
      )}
    </FormDialog>
  )
}
