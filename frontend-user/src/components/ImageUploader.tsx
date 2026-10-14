import { ImageIcon, XIcon } from '@phosphor-icons/react'
import { useRef, useState } from 'react'
import { errorMessage } from '../api/client'
import { me } from '../api/store'

/** Photos for a review or a return request: uploaded one by one as they are picked, shown as removable thumbnails. */
export function ImageUploader({ value, onChange, max = 5, label = 'Thêm ảnh' }: {
  value: string[]
  onChange: (urls: string[]) => void
  max?: number
  label?: string
}) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  const pick = async (files: FileList | null) => {
    if (!files?.length || busy) return
    setError(undefined)
    const selected = [...files]
    if (selected.length + value.length > max) {
      setError(`Chỉ được chọn tối đa ${max} ảnh.`)
      if (input.current) input.current.value = ''
      return
    }
    const invalid = selected.find((file) => !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024)
    if (invalid) {
      setError(`${invalid.name}: chọn ảnh JPEG, PNG, WebP hoặc GIF, từ 1 byte đến 5 MB.`)
      if (input.current) input.current.value = ''
      return
    }
    setBusy(true)
    const urls = [...value]
    try {
      for (const file of selected) {
        urls.push((await me.uploadImage(file)).url)
        onChange([...urls])
      }
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="image-uploader">
      <div className="image-uploader-row">
        {value.map((url) => (
          <span key={url} className="image-uploader-thumb">
            <img src={url} alt="" />
            <button type="button" className="image-uploader-remove" onClick={() => onChange(value.filter((u) => u !== url))} aria-label="Bỏ ảnh">
              <XIcon size={12} weight="bold" />
            </button>
          </span>
        ))}
        {value.length < max && (
          <button type="button" className="image-uploader-add" onClick={() => input.current?.click()} disabled={busy}>
            <ImageIcon size={20} aria-hidden />
            <span>{busy ? 'Đang tải...' : label}</span>
          </button>
        )}
      </div>
      <input ref={input} type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden onChange={(e) => void pick(e.target.files)} />
      <span className="field-hint">
        Tối đa {max} ảnh JPEG, PNG, WebP; mỗi ảnh dưới 5 MB.
      </span>
      {error && <span className="field-error" role="alert">{error}</span>}
    </div>
  )
}
