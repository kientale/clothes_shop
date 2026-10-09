import { useState } from 'react'
import { warehouses } from '../../api/commerce'
import { BUCKET_LABEL, type ReportBucket, type ReportPeriod } from '../../api/reports'
import { DateRange, FilterSelect, dayOf, isoDay, rangeQuery, type DateRangeValue } from '../../components/kit'
import { useAsync } from '../../hooks'

const PRESETS: [string, number][] = [
  ['7 ngày', 7],
  ['30 ngày', 30],
  ['90 ngày', 90],
  ['12 tháng', 365],
]

export interface ReportFilterState {
  range: DateRangeValue
  warehouseId: string
  groupBy: ReportBucket
}

/** Filter state for a report page; the query includes the browser's time zone so buckets match local days. */
export function useReportFilters(defaultDays = 30) {
  const [state, setState] = useState<ReportFilterState>({ range: { from: isoDay(-(defaultDays - 1)), to: isoDay(0) }, warehouseId: '', groupBy: 'DAY' })
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Ho_Chi_Minh'
  const query = { ...rangeQuery(state.range), timezone, warehouseId: state.warehouseId || undefined }
  return { state, setState, query, key: JSON.stringify({ ...query, groupBy: state.groupBy }) }
}

export function ReportFilters({
  state,
  onChange,
  grouping,
  noRange,
  children,
}: {
  state: ReportFilterState
  onChange: (state: ReportFilterState) => void
  /** Show the day/week/month switch (revenue and order reports). */
  grouping?: boolean
  /** Snapshot reports (inventory) have no time window. */
  noRange?: boolean
  children?: React.ReactNode
}) {
  const houses = useAsync(() => warehouses.all(), [])
  const days = (n: number) => ({ from: isoDay(-(n - 1)), to: isoDay(0) })
  return (
    <div className="report-filters">
      {!noRange && (
        <>
          <div className="segmented" role="group" aria-label="Khoảng nhanh">
            {PRESETS.map(([label, n]) => {
              const preset = days(n)
              return (
                <button
                  key={label}
                  type="button"
                  aria-pressed={state.range.from === preset.from && state.range.to === preset.to}
                  onClick={() => onChange({ ...state, range: preset, groupBy: n > 120 ? 'MONTH' : n > 31 ? 'WEEK' : state.groupBy === 'MONTH' ? 'DAY' : state.groupBy })}
                >
                  {label}
                </button>
              )
            })}
          </div>
          <DateRange value={state.range} onChange={(range) => onChange({ ...state, range })} />
        </>
      )}
      <FilterSelect label="Kho" value={state.warehouseId} options={(houses.data ?? []).map((w) => [w.id, w.name])} onChange={(warehouseId) => onChange({ ...state, warehouseId })} />
      {grouping && (
        <FilterSelect<ReportBucket>
          label="Nhóm"
          allLabel="Theo ngày"
          value={state.groupBy === 'DAY' ? '' : state.groupBy}
          options={(['WEEK', 'MONTH'] as ReportBucket[]).map((b) => [b, BUCKET_LABEL[b]])}
          onChange={(value) => onChange({ ...state, groupBy: value || 'DAY' })}
        />
      )}
      {children}
    </div>
  )
}

/** "Số liệu từ dd/mm/yyyy đến dd/mm/yyyy (múi giờ)" from the period the backend actually used. */
export function PeriodNote({ period }: { period: ReportPeriod | undefined }) {
  if (!period) return null
  const last = new Date(new Date(period.to).getTime() - 1).toISOString()
  return (
    <p className="report-period">
      Số liệu từ {dayOf(period.from)} đến {dayOf(last)} ({period.timezone})
    </p>
  )
}

const shortDay = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' })
const monthYear = new Intl.DateTimeFormat('vi-VN', { month: '2-digit', year: 'numeric' })

/** Axis label for a bucket start date (yyyy-MM-dd). */
export function bucketLabel(day: string, bucket: ReportBucket) {
  const [y, m, d] = day.split('-').map(Number)
  const date = new Date(y!, m! - 1, d!)
  if (bucket === 'MONTH') return `T${monthYear.format(date)}`
  return bucket === 'WEEK' ? `Tuần ${shortDay.format(date)}` : shortDay.format(date)
}
