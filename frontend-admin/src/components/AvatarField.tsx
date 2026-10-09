import { LinkSimpleIcon, TrashIcon, UploadSimpleIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState, type DragEvent } from 'react'
import { accessErrorMessage } from '../api/access'
import { AVATAR_MAX_BYTES, AVATAR_TYPES, uploadAvatar, type UploadResponse } from '../api/uploads'
import { initials } from './ui'

/**
 * Avatar picker for profile forms: upload (button or drag and drop) or paste a URL.
 * Uploading happens on pick, so the form only ever stores the resulting URL.
 */
export function AvatarField({
  id,
  value,
  name,
  error,
  onChange,
  onUploadingChange,
  shape = 'circle',
  upload = uploadAvatar,
  maxBytes = AVATAR_MAX_BYTES,
}: {
  id: string
  value: string
  /** Used for the initials shown while there is no image. */
  name: string
  error?: string
  onChange: (url: string) => void
  onUploadingChange?: (uploading: boolean) => void
  /** Circle for people, square for logos and covers. */
  shape?: 'circle' | 'square'
  upload?: (file: File) => Promise<UploadResponse>
  maxBytes?: number
}) {
  const maxMb = Math.round(maxBytes / 1024 / 1024)
  const fileRef = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState<string>()
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string>()
  const [dragging, setDragging] = useState(false)
  const [showUrl, setShowUrl] = useState(false)
  const [broken, setBroken] = useState(false)

  useEffect(() => setBroken(false), [value])
  // Release the local preview's object URL when it is replaced or the field unmounts.
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview)
  }, [preview])

  const pick = async (file: File | undefined) => {
    if (!file || uploading) return
    setUploadError(undefined)
    if (!AVATAR_TYPES.includes(file.type)) return setUploadError('Chỉ nhận ảnh JPEG, PNG, WebP hoặc GIF.')
    if (file.size > maxBytes) return setUploadError(`Ảnh tối đa ${maxMb} MB.`)
    setPreview(URL.createObjectURL(file))
    setUploading(true)
    onUploadingChange?.(true)
    try {
      const uploaded = await upload(file)
      onChange(uploaded.url)
    } catch (err) {
      setUploadError(accessErrorMessage(err))
    } finally {
      setPreview(undefined)
      setUploading(false)
      onUploadingChange?.(false)
    }
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    void pick(event.dataTransfer.files[0])
  }

  const shown = preview ?? (value && !broken ? value : undefined)
  const message = uploadError ?? error

  return (
    <div
      className={`avatar-field${dragging ? ' dragging' : ''}`}
      onDragOver={(event) => {
        event.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={onDrop}
    >
      <div className={`avatar-preview${shape === 'square' ? ' square' : ''}${uploading ? ' busy' : ''}`} aria-hidden>
        {shown ? <img src={shown} alt="" onError={() => setBroken(true)} /> : <span>{initials(name || '?')}</span>}
      </div>

      <div className="avatar-body">
        <div className="avatar-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => fileRef.current?.click()} disabled={uploading}>
            <UploadSimpleIcon size={15} /> {uploading ? 'Đang tải lên...' : value ? 'Đổi ảnh' : 'Tải ảnh lên'}
          </button>
          {value && !uploading && (
            <button type="button" className="btn btn-plain btn-sm" onClick={() => onChange('')}>
              <TrashIcon size={15} /> Xóa ảnh
            </button>
          )}
          <button type="button" className="link-btn avatar-url-toggle" onClick={() => setShowUrl((open) => !open)} aria-expanded={showUrl}>
            <LinkSimpleIcon size={14} /> {showUrl ? 'Ẩn đường dẫn' : 'Dán đường dẫn ảnh'}
          </button>
        </div>
        <input
          ref={fileRef}
          id={`${id}-file`}
          type="file"
          accept={AVATAR_TYPES.join(',')}
          className="sr-only"
          tabIndex={-1}
          aria-label="Chọn ảnh đại diện"
          onChange={(event) => {
            void pick(event.target.files?.[0])
            event.target.value = ''
          }}
        />
        {showUrl && (
          <input
            id={id}
            type="url"
            className="avatar-url"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder="https://..."
            aria-label="Đường dẫn ảnh đại diện"
            aria-invalid={error ? true : undefined}
          />
        )}
        {message ? (
          <span className="field-error" role={uploadError ? 'alert' : undefined}>
            {message}
          </span>
        ) : (
          <span className="field-hint">JPEG, PNG, WebP hoặc GIF, tối đa {maxMb} MB. Có thể kéo thả ảnh vào khung này.</span>
        )}
        {broken && value && !uploading && <span className="field-hint">Không hiển thị được ảnh từ đường dẫn này.</span>}
      </div>
    </div>
  )
}
