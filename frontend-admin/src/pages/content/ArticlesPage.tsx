import { ArchiveIcon, ArrowCounterClockwiseIcon, NewspaperIcon, RocketLaunchIcon, TrashIcon } from '@phosphor-icons/react'
import { useId, useState } from 'react'
import { accessErrorMessage } from '../../api/access'
import { slugify } from '../../api/catalog'
import { ARTICLE_STATUS, ARTICLE_TYPE_LABEL, content, type Article, type ArticleStatus, type ArticleType } from '../../api/marketing'
import { IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'
import { AvatarField } from '../../components/AvatarField'
import { Field, FormDialog, bind, useDialogForm } from '../../components/forms'
import { FilterSelect, StatePill, useCan, whenOf } from '../../components/kit'
import { ResourcePage, type FormSlot } from '../../components/ResourcePage'
import { useToast } from '../../components/Toast'

const URL = /^https?:\/\/\S+$/

export default function ArticlesPage() {
  const canWrite = useCan('ARTICLE_WRITE')
  const toast = useToast()
  const [type, setType] = useState<ArticleType | ''>('')
  const [reload, setReload] = useState(0)
  const move = async (article: Article, status: ArticleStatus, message: string) => {
    try {
      await content.articles.setStatus(article.id, status)
      toast.success(message)
      setReload((n) => n + 1)
    } catch (err) {
      toast.error(accessErrorMessage(err))
    }
  }

  return (
    <ResourcePage<Article>
      title="Bài viết / Lookbook"
      lede="Bài viết và bộ ảnh phối đồ cho cửa hàng. Chỉ sửa được bản nháp; muốn sửa bài đã xuất bản, đưa về nháp trước."
      addLabel="Viết bài"
      itemLabel="bài viết"
      searchPlaceholder="Tìm theo tiêu đề hoặc slug"
      canWrite={canWrite}
      statusLabels={Object.fromEntries(Object.entries(ARTICLE_STATUS).map(([k, v]) => [k, v[0]]))}
      filters={<FilterSelect label="Loại" value={type} options={Object.entries(ARTICLE_TYPE_LABEL) as [ArticleType, string][]} onChange={setType} />}
      filterKey={type}
      reloadToken={reload}
      load={(query) => content.articles.list({ ...query, articleType: type || undefined })}
      rowLabel={(a) => a.title}
      columns={[
        {
          header: 'Bài viết',
          render: (a) => (
            <div className="person">
              {a.thumbnailUrl ? (
                <img className="banner-thumb" src={a.thumbnailUrl} alt="" loading="lazy" />
              ) : (
                <span className="banner-thumb empty" aria-hidden>
                  <NewspaperIcon size={18} />
                </span>
              )}
              <div className="person-text">
                <span className="person-name">{a.title}</span>
                <span className="person-sub mono">/{a.slug}</span>
              </div>
            </div>
          ),
        },
        { header: 'Loại', render: (a) => <span className="chip chip-sm">{ARTICLE_TYPE_LABEL[a.articleType]}</span> },
        { header: 'Ảnh', render: (a) => a.images.length, className: 'num' },
        { header: 'Trạng thái', render: (a) => <StatePill map={ARTICLE_STATUS} value={a.status} /> },
        { header: 'Xuất bản', render: (a) => whenOf(a.publishedAt), className: 'muted nowrap' },
      ]}
      rowActions={(a) =>
        canWrite && (
          <>
            {a.status === 'DRAFT' && (
              <button type="button" className="icon-btn" onClick={() => move(a, 'PUBLISHED', `Đã xuất bản ${a.title}.`)} aria-label="Xuất bản" title="Xuất bản">
                <RocketLaunchIcon size={16} />
              </button>
            )}
            {a.status === 'PUBLISHED' && (
              <button type="button" className="icon-btn" onClick={() => move(a, 'ARCHIVED', `Đã lưu trữ ${a.title}.`)} aria-label="Lưu trữ" title="Lưu trữ">
                <ArchiveIcon size={16} />
              </button>
            )}
            {a.status !== 'DRAFT' && (
              <button type="button" className="icon-btn" onClick={() => move(a, 'DRAFT', `Đã đưa ${a.title} về nháp.`)} aria-label="Đưa về nháp" title="Đưa về nháp">
                <ArrowCounterClockwiseIcon size={16} />
              </button>
            )}
          </>
        )
      }
      deleteTitle="Xóa bài viết"
      deleteMessage={(a) => (
        <>
          Xóa bài <strong>{a.title}</strong>? Slug của bài được giữ để không trỏ nhầm sang bài khác.
        </>
      )}
      remove={(a) => content.articles.remove(a.id)}
      emptyIcon={<NewspaperIcon size={36} aria-hidden />}
      renderForm={(slot) => <ArticleForm {...slot} />}
    />
  )
}

function ArticleForm({ open, item, onClose, onSaved }: FormSlot<Article>) {
  const id = useId()
  const [uploading, setUploading] = useState(false)
  const { form, set, errors, error, busy, submit } = useDialogForm(
    open,
    () => ({
      title: item?.title ?? '',
      slug: item?.slug ?? '',
      thumbnailUrl: item?.thumbnailUrl ?? '',
      content: item?.content ?? '',
      articleType: (item?.articleType ?? 'ARTICLE') as ArticleType,
      images: item?.images ?? [],
      newImage: '',
    }),
    [item?.id],
  )
  const locked = Boolean(item && item.status !== 'DRAFT')
  const addImage = (url: string) => {
    if (!url || form.images.includes(url) || form.images.length >= 50) return
    set('images', [...form.images, url])
  }

  return (
    <FormDialog
      open={open}
      title={item ? (locked ? 'Xem bài viết' : 'Sửa bản nháp') : 'Viết bài mới'}
      description={locked ? 'Bài đã xuất bản hoặc lưu trữ. Đưa về nháp để sửa.' : 'Bài mới lưu ở dạng nháp. Lookbook cần ít nhất một ảnh để xuất bản.'}
      busy={busy}
      disabled={uploading || locked}
      submitLabel="Lưu nháp"
      error={error}
      onClose={onClose}
      onSubmit={submit(
        (f) => {
          const e: Record<string, string> = {}
          if (!f.title.trim()) e.title = 'Nhập tiêu đề.'
          if (!f.content.trim()) e.content = 'Nhập nội dung.'
          if (f.thumbnailUrl.trim() && !URL.test(f.thumbnailUrl.trim())) e.thumbnailUrl = 'Đường dẫn ảnh phải bắt đầu bằng http(s)://.'
          return e
        },
        async (f) => {
          const body = {
            title: f.title.trim(),
            slug: f.slug.trim() || null,
            thumbnailUrl: f.thumbnailUrl.trim() || null,
            content: f.content,
            articleType: f.articleType,
            images: f.images,
          }
          if (item) await content.articles.update(item.id, body)
          else await content.articles.create(body)
          onSaved(item ? `Đã lưu ${body.title}.` : `Đã tạo bản nháp ${body.title}.`)
        },
      )}
    >
      <fieldset className="contents" disabled={locked}>
        <Field label="Tiêu đề" htmlFor={`${id}-title`} error={errors.title} wide>
          <input {...bind(id, 'title', errors)} maxLength={255} value={form.title} onChange={(e) => set('title', e.target.value)} />
        </Field>
        <Field label="Slug" htmlFor={`${id}-slug`} optional error={errors.slug} hint={`Để trống sẽ dùng: ${slugify(form.title) || '...'}`}>
          <input {...bind(id, 'slug', errors)} className="mono-input" maxLength={255} value={form.slug} onChange={(e) => set('slug', slugify(e.target.value))} />
        </Field>
        <Field label="Loại" htmlFor={`${id}-type`}>
          <select id={`${id}-type`} value={form.articleType} onChange={(e) => set('articleType', e.target.value as ArticleType)}>
            {(Object.keys(ARTICLE_TYPE_LABEL) as ArticleType[]).map((t) => (
              <option key={t} value={t}>
                {ARTICLE_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Ảnh đại diện" htmlFor={`${id}-thumbnailUrl-file`} optional wide>
          <AvatarField
            id={`${id}-thumbnailUrl`}
            shape="square"
            upload={uploadImage}
            maxBytes={IMAGE_MAX_BYTES}
            value={form.thumbnailUrl}
            name={form.title}
            error={errors.thumbnailUrl}
            onChange={(url) => set('thumbnailUrl', url)}
            onUploadingChange={setUploading}
          />
        </Field>
        <Field label="Nội dung" htmlFor={`${id}-content`} error={errors.content} wide hint={`${form.content.length.toLocaleString('vi-VN')} / 100.000 ký tự`}>
          <textarea {...bind(id, 'content', errors)} rows={8} maxLength={100000} value={form.content} onChange={(e) => set('content', e.target.value)} />
        </Field>
        <Field label={`Bộ ảnh (${form.images.length}/50)`} htmlFor={`${id}-newImage-file`} optional wide>
          {form.images.length > 0 && (
            <div className="gallery">
              {form.images.map((src) => (
                <figure key={src}>
                  <img src={src} alt="" loading="lazy" />
                  {!locked && (
                    <button type="button" className="icon-btn danger" onClick={() => set('images', form.images.filter((x) => x !== src))} aria-label="Bỏ ảnh" title="Bỏ ảnh">
                      <TrashIcon size={14} />
                    </button>
                  )}
                </figure>
              ))}
            </div>
          )}
          {!locked && (
            <AvatarField
              id={`${id}-newImage`}
              shape="square"
              upload={uploadImage}
              maxBytes={IMAGE_MAX_BYTES}
              value=""
              name="+"
              onChange={addImage}
              onUploadingChange={setUploading}
            />
          )}
        </Field>
      </fieldset>
    </FormDialog>
  )
}
