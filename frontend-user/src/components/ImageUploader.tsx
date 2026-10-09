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
    if (!files?.length) return
    setError(undefined)
    setBusy(true)
    const urls = [...value]
    try {
      for (const file of [...files].slice(0, max - urls.length)) {
        if (file.size > 5 * 1024 * 1024) throw new Error(`${file.name} lớn hơn 5 MB.`)
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
      {error && <span className="field-error">{error}</span>}
    </div>
  )
}
