import { api } from './client'

export interface UploadResponse {
  id: string
  /** Absolute URL to store in avatarUrl. */
  url: string
  contentType: string
  size: number
}

/** Same limits as the backend (app.uploads.max-avatar-size and the signature check). */
export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024

export const IMAGE_MAX_BYTES = 5 * 1024 * 1024

/** Product photos, brand logos and collection covers (needs PRODUCT_WRITE). */
export function uploadImage(file: File) {
  const body = new FormData()
  body.append('file', file)
  return api.post<UploadResponse>('/admin/uploads/images', body)
}

export function uploadAvatar(file: File) {
  const body = new FormData()
  body.append('file', file)
  return api.post<UploadResponse>('/admin/uploads/avatars', body)
}
