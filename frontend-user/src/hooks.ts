import { useCallback, useEffect, useState, type DependencyList } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  error: unknown
  loading: boolean
  reload: () => void
  setData: (data: T) => void
}

/** Runs `load` whenever `deps` change and ignores results from superseded runs. */
export function useAsync<T>(load: () => Promise<T>, deps: DependencyList): AsyncState<T> {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<unknown>()
  const [loading, setLoading] = useState(true)
  const [nonce, setNonce] = useState(0)

  useEffect(() => {
    let current = true
    setLoading(true)
    setError(undefined)
    load()
      .then((result) => current && setData(result))
      .catch((err) => current && setError(err))
      .finally(() => current && setLoading(false))
    return () => {
      current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  return { data, error, loading, reload, setData }
}
