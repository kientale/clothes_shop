import { CheckCircleIcon, ScrollIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { accessErrorMessage } from '../../api/access'
import { POLICY_STATUS, POLICY_TYPE_LABEL, content, type Policy, type PolicyType } from '../../api/marketing'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { FilterSelect, StatePill, useCan, whenOf } from '../../components/kit'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'
import { ConfirmDialog } from '../../components/ui'

export default function PoliciesPage() {
  const canWrite = useCan('POLICY_WRITE')
  const toast = useToast()
  const [type, setType] = useState<PolicyType | ''>('')
  const [activating, setActivating] = useState<Policy | null>(null)
  const [reload, setReload] = useState(0)

  return (
    <>
      <ResourcePage<Policy>
        title="Chính sách cửa hàng"
        lede="Mỗi loại chính sách có nhiều phiên bản; chỉ một phiên bản được áp dụng. Muốn thay nội dung, tạo phiên bản mới rồi áp dụng."
        addLabel="Tạo phiên bản"
        itemLabel="phiên bản"
        canWrite={canWrite}
        statusLabels={Object.fromEntries(Object.entries(POLICY_STATUS).map(([k, v]) => [k, v[0]]))}
        filters={<FilterSelect label="Loại" value={type} options={Object.entries(POLICY_TYPE_LABEL) as [PolicyType, string][]} onChange={setType} />}
        filterKey={type}
        reloadToken={reload}
        load={({ search: _search, ...query }) => content.policies.list({ ...query, policyType: type || undefined })}
        rowLabel={(p) => `${p.title} v${p.version}`}
        columns={[
          {
            header: 'Chính sách',
            render: (p) => (
              <div className="person-text">
                <span className="person-name">{p.title}</span>
                <span className="person-sub clamp-2">{p.content}</span>
              </div>
            ),
          },
          { header: 'Loại', render: (p) => <span className="chip chip-sm">{POLICY_TYPE_LABEL[p.policyType]}</span> },
          { header: 'Phiên bản', render: (p) => <span className="mono-strong">v{p.version}</span>, className: 'num' },
          { header: 'Trạng thái', render: (p) => <StatePill map={POLICY_STATUS} value={p.status} /> },
          { header: 'Áp dụng từ', render: (p) => whenOf(p.activatedAt), className: 'muted nowrap' },
        ]}
        rowActions={(p) =>
          canWrite && p.status === 'DRAFT' ? (
            <button type="button" className="icon-btn" onClick={() => setActivating(p)} aria-label="Áp dụng phiên bản" title="Áp dụng">
              <CheckCircleIcon size={16} />
            </button>
          ) : null
        }
        deleteTitle="Xóa bản nháp"
        deleteMessage={(p) => (
          <>
            Xóa bản nháp <strong>{p.title}</strong> v{p.version}? Phiên bản đã áp dụng được giữ làm lịch sử và không xóa được.
          </>
        )}
        remove={(p) => content.policies.remove(p.id)}
        emptyIcon={<ScrollIcon size={36} aria-hidden />}
        renderForm={(slot) => <PolicyForm {...slot} />}
      />
      <ConfirmDialog
        open={activating !== null}
        title="Áp dụng chính sách"
        message={
          activating && (
            <>
              Áp dụng <strong>{activating.title}</strong> v{activating.version}? Phiên bản {POLICY_TYPE_LABEL[activating.policyType].toLowerCase()} đang áp dụng sẽ chuyển sang
              đã thay thế.
            </>
          )
        }
        confirmLabel="Áp dụng"
        formatError={accessErrorMessage}
        onClose={() => setActivating(null)}
        onConfirm={async () => {
          await content.policies.setStatus(activating!.id, 'ACTIVE')
          toast.success(`Đã áp dụng ${activating!.title} v${activating!.version}.`)
          setReload((n) => n + 1)
        }}
      />
    </>
  )
}

function PolicyForm({ open, item, onClose, onSaved }: FormSlot<Policy>) {
  const id = useId()
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({ policyType: (item?.policyType ?? 'SHIPPING') as PolicyType, title: item?.title ?? '', content: item?.content ?? '' }),
    [item?.id],
  )
  const locked = Boolean(item && item.status !== 'DRAFT')
  return (
    <FormDialog
      open={open}
      title={locked ? `${item!.title} v${item!.version}` : item ? 'Sửa bản nháp' : 'Tạo phiên bản chính sách'}
      description={locked ? 'Phiên bản đã áp dụng không sửa được.' : 'Số phiên bản tự tăng theo loại.'}
      busy={busy}
      disabled={locked}
      submitLabel="Lưu nháp"
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => ({ ...(f.title.trim() ? {} : { title: 'Nhập tiêu đề.' }), ...(f.content.trim() ? {} : { content: 'Nhập nội dung.' }) }),
        async (f) => {
          const body = { policyType: f.policyType, title: f.title.trim(), content: f.content }
          if (item) await content.policies.update(item.id, body)
          else await content.policies.create(body)
          onSaved(item ? 'Đã lưu bản nháp.' : `Đã tạo phiên bản mới cho chính sách ${POLICY_TYPE_LABEL[f.policyType].toLowerCase()}.`)
        },
      )}
    >
      <Field label="Loại chính sách" htmlFor={`${id}-type`} hint={item ? 'Không đổi được loại sau khi tạo.' : undefined}>
        <select id={`${id}-type`} disabled={Boolean(item)} value={form.policyType} onChange={(e) => set('policyType', e.target.value as PolicyType)}>
          {(Object.keys(POLICY_TYPE_LABEL) as PolicyType[]).map((t) => (
            <option key={t} value={t}>
              {POLICY_TYPE_LABEL[t]}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Tiêu đề" htmlFor={`${id}-title`} error={errors.title}>
        <input {...bind(id, 'title', errors)} disabled={locked} maxLength={255} value={form.title} onChange={(e) => set('title', e.target.value)} />
      </Field>
      <Field label="Nội dung" htmlFor={`${id}-content`} error={errors.content} wide>
        <textarea {...bind(id, 'content', errors)} disabled={locked} rows={12} maxLength={100000} value={form.content} onChange={(e) => set('content', e.target.value)} />
      </Field>
    </FormDialog>
  )
}
