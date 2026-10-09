import { ArrowDownIcon, ArrowUpIcon, ImageIcon, LinkSimpleIcon, StarIcon, TrashIcon, UploadSimpleIcon } from '@phosphor-icons/react'
import { useRef, useState, type DragEvent } from 'react'
import { accessErrorMessage } from '../../api/access'
import { AVATAR_TYPES, IMAGE_MAX_BYTES, uploadImage } from '../../api/uploads'

export interface ImageValue {
  url: string
  altText: string
}

const MAX_IMAGES = 10
const URL_PATTERN = /^https?:\/\/\S+$/

/**
 * Ordered product images: upload several at once (button or drop), or add by URL, then
 * reorder or remove. The first image is the primary one, as on the backend.
 */
export function ProductImagesField({
  id,
  images,
  onChange,
  onUploadingChange,
  error,
}: {
  id: string
  images: ImageValue[]
  onChange: (images: ImageValue[]) => void
  onUploadingChange: (uploading: boolean) => void
  error?: string
}) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [pending, setPending] = useState(0)
  const [problem, setProblem] = useState<string>()
  const [dragging, setDragging] = useState(false)
  const [url, setUrl] = useState('')
  const latest = useRef(images)
  latest.current = images
  const inFlight = useRef(0)

  const add = async (files: File[]) => {
    setProblem(undefined)
    const room = MAX_IMAGES - latest.current.length - inFlight.current
    const accepted = files.filter((file) => AVATAR_TYPES.includes(file.type) && file.size <= IMAGE_MAX_BYTES).slice(0, Math.max(room, 0))
    if (accepted.length < files.length) {
      setProblem(room <= 0 || files.length > room ? `Tối đa ${MAX_IMAGES} ảnh cho một sản phẩm.` : 'Bỏ qua file không phải ảnh JPEG, PNG, WebP, GIF hoặc lớn hơn 5 MB.')
    }
    if (!accepted.length) return
    inFlight.current += accepted.length
    setPending(inFlight.current)
    onUploadingChange(true)
    // Upload in parallel but append in the order the files were chosen.
    const results = await Promise.allSettled(accepted.map((file) => uploadImage(file)))
    const uploaded = results.flatMap((result) => (result.status === 'fulfilled' ? [{ url: result.value.url, altText: '' }] : []))
    const failed = results.find((result) => result.status === 'rejected')
    if (failed && failed.status === 'rejected') setProblem(accessErrorMessage(failed.reason))
    onChange([...latest.current, ...uploaded].slice(0, MAX_IMAGES))
    inFlight.current -= accepted.length
    setPending(inFlight.current)
    if (inFlight.current === 0) onUploadingChange(false)
  }

  const move = (from: number, to: number) => {
    const next = [...images]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item!)
    onChange(next)
  }

  const addUrl = () => {
    const value = url.trim()
    if (!URL_PATTERN.test(value)) return setProblem('Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.')
    if (images.length >= MAX_IMAGES) return setProblem(`Tối đa ${MAX_IMAGES} ảnh cho một sản phẩm.`)
    onChange([...images, { url: value, altText: '' }])
    setUrl('')
    setProblem(undefined)
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    void add(Array.from(event.dataTransfer.files))
  }

  return (
    <div className="images-field">
      {images.length > 0 && (
        <ol className="image-list">
          {images.map((image, index) => (
            <li key={`${image.url}-${index}`} className="image-item">
              <img src={image.url} alt="" loading="lazy" />
              <div className="image-meta">
                {index === 0 ? (
                  <span className="tag">
                    <StarIcon size={11} weight="fill" aria-hidden /> Ảnh chính
                  </span>
                ) : (
                  <span className="muted small">Ảnh {index + 1}</span>
                )}
                <input
                  value={image.altText}
                  onChange={(e) => onChange(images.map((item, i) => (i === index ? { ...item, altText: e.target.value } : item)))}
                  placeholder="Mô tả ảnh (alt)"
                  aria-label={`Mô tả ảnh ${index + 1}`}
                  maxLength={255}
                />
              </div>
              <div className="image-actions">
                <button type="button" className="icon-btn" disabled={index === 0} onClick={() => move(index, index - 1)} aria-label={`Đưa ảnh ${index + 1} lên`} title="Lên">
                  <ArrowUpIcon size={15} />
                </button>
                <button
                  type="button"
                  className="icon-btn"
                  disabled={index === images.length - 1}
                  onClick={() => move(index, index + 1)}
                  aria-label={`Đưa ảnh ${index + 1} xuống`}
                  title="Xuống"
                >
                  <ArrowDownIcon size={15} />
                </button>
                <button type="button" className="icon-btn danger" onClick={() => onChange(images.filter((_, i) => i !== index))} aria-label={`Xóa ảnh ${index + 1}`} title="Xóa">
                  <TrashIcon size={15} />
                </button>
              </div>
            </li>
          ))}
          {Array.from({ length: pending }, (_, i) => (
            <li key={`pending-${i}`} className="image-item pending" aria-hidden>
              <span className="image-placeholder" />
              <span className="muted small">Đang tải lên...</span>
            </li>
          ))}
        </ol>
      )}

      <div
        className={`image-drop${dragging ? ' dragging' : ''}`}
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <ImageIcon size={26} aria-hidden />
        <div>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => fileRef.current?.click()} disabled={images.length + pending >= MAX_IMAGES}>
            <UploadSimpleIcon size={15} /> {pending > 0 ? `Đang tải ${pending} ảnh...` : 'Tải ảnh lên'}
          </button>
          <span className="field-hint"> hoặc kéo thả nhiều ảnh vào đây. Ảnh đầu tiên là ảnh chính.</span>
        </div>
        <input
          ref={fileRef}
          id={`${id}-files`}
          type="file"
          multiple
          accept={AVATAR_TYPES.join(',')}
          className="sr-only"
          tabIndex={-1}
          aria-label="Chọn ảnh sản phẩm"
          onChange={(event) => {
            void add(Array.from(event.target.files ?? []))
            event.target.value = ''
          }}
        />
      </div>

      <div className="image-url-row">
        <LinkSimpleIcon size={15} aria-hidden />
        <input
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              addUrl()
            }
          }}
          placeholder="Hoặc dán đường dẫn ảnh https://..."
          aria-label="Đường dẫn ảnh"
        />
        <button type="button" className="btn btn-plain btn-sm" onClick={addUrl} disabled={!url.trim()}>
          Thêm
        </button>
      </div>

      {(problem || error) && (
        <span className="field-error" role="alert">
          {problem ?? error}
        </span>
      )}
    </div>
  )
}
