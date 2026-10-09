import { ORDER_STATUS, labelOf, type OrderStatus } from '../../api/commerce'
import { accessErrorMessage } from '../../api/access'
import { reports } from '../../api/reports'
import { BarChart, Kpi, Kpis, PageHead, ShareList, StatePill, countOf, vndOf } from '../../components/kit'
import { useAsync } from '../../hooks'
import { PeriodNote, ReportFilters, bucketLabel, useReportFilters } from './ReportFilters'

const compact = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 })
const compactMoney = (value: number) => `${compact.format(value)} ₫`

export function RevenueReportPage() {
  const filters = useReportFilters()
  const report = useAsync(() => reports.revenue({ ...filters.query, groupBy: filters.state.groupBy }), [filters.key])
  const r = report.data
  const loading = report.loading && !r

  return (
    <div className="page page-wide">
      <PageHead title="Báo cáo doanh thu" lede="Dòng tiền đã ghi nhận: tiền thu theo thời điểm xác nhận, trừ tiền hoàn theo thời điểm xử lý. Không phải doanh thu kế toán." />
      <ReportFilters state={filters.state} onChange={filters.setState} grouping />
      {report.error ? <ReportError error={report.error} /> : null}
      <Kpis>
        <Kpi label="Đã thu" value={vndOf(r?.paidAmount)} loading={loading} />
        <Kpi label="Đã hoàn" value={vndOf(r?.refundedAmount)} loading={loading} />
        <Kpi label="Thực nhận" value={vndOf(r?.netReceivedAmount)} loading={loading} note="Đã thu trừ đã hoàn" />
      </Kpis>
      <section className="panel report-panel">
        <h2 className="section-title">Thực nhận theo kỳ</h2>
        <PeriodNote period={r?.period} />
        {r ? (
          <BarChart
            label="Biểu đồ tiền thực nhận theo kỳ"
            format={compactMoney}
            points={r.series.map((p) => ({ key: p.bucketStart, label: bucketLabel(p.bucketStart, r.groupBy), value: p.netReceivedAmount }))}
          />
        ) : (
          <div className="chart-skel skel" />
        )}
        {r && r.series.length > 0 && (
          <details className="report-table">
            <summary>Xem bảng số liệu</summary>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Kỳ</th>
                    <th scope="col" className="num">
                      Đã thu
                    </th>
                    <th scope="col" className="num">
                      Đã hoàn
                    </th>
                    <th scope="col" className="num">
                      Thực nhận
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {r.series.map((p) => (
                    <tr key={p.bucketStart}>
                      <td>{bucketLabel(p.bucketStart, r.groupBy)}</td>
                      <td className="num">{vndOf(p.paidAmount)}</td>
                      <td className="num">{vndOf(p.refundedAmount)}</td>
                      <td className="num">
                        <strong>{vndOf(p.netReceivedAmount)}</strong>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        )}
      </section>
    </div>
  )
}

export function OrdersReportPage() {
  const filters = useReportFilters()
  const report = useAsync(() => reports.orders({ ...filters.query, groupBy: filters.state.groupBy }), [filters.key])
  const r = report.data
  const loading = report.loading && !r

  return (
    <div className="page page-wide">
      <PageHead title="Báo cáo đơn hàng" lede="Đơn theo thời điểm đặt. Giá trị đơn tính trên đơn chưa hủy, gồm phí giao hàng; đơn hủy báo riêng." />
      <ReportFilters state={filters.state} onChange={filters.setState} grouping />
      {report.error ? <ReportError error={report.error} /> : null}
      <Kpis>
        <Kpi label="Tổng đơn" value={countOf(r?.totalOrders)} loading={loading} />
        <Kpi label="Giá trị đơn" value={vndOf(r?.orderAmount)} loading={loading} note="Không gồm đơn hủy" />
        <Kpi label="Giá trị trung bình" value={vndOf(r?.averageOrderValue)} loading={loading} />
        <Kpi label="Đơn hủy" value={countOf(r?.cancelledOrders)} loading={loading} note={r ? vndOf(r.cancelledAmount) : undefined} />
      </Kpis>
      <div className="report-split">
        <section className="panel report-panel">
          <h2 className="section-title">Số đơn theo kỳ</h2>
          <PeriodNote period={r?.period} />
          {r ? (
            <BarChart
              label="Biểu đồ số đơn theo kỳ"
              format={(v) => `${countOf(v)} đơn`}
              points={r.series.map((p) => ({ key: p.bucketStart, label: bucketLabel(p.bucketStart, r.groupBy), value: p.orderCount }))}
            />
          ) : (
            <div className="chart-skel skel" />
          )}
        </section>
        <section className="panel report-panel">
          <h2 className="section-title">Phân bố trạng thái</h2>
          {r ? (
            <ShareList
              rows={r.statuses.map((s) => ({
                key: s.status,
                label: <StatePill map={ORDER_STATUS} value={s.status as OrderStatus} />,
                count: s.count,
                extra: vndOf(s.amount),
              }))}
            />
          ) : (
            <span className="skel skel-row" />
          )}
          {r && <span className="sr-only">{r.statuses.map((s) => `${labelOf(ORDER_STATUS, s.status as OrderStatus)}: ${s.count}`).join(', ')}</span>}
        </section>
      </div>
    </div>
  )
}

export function ReportError({ error }: { error: unknown }) {
  return (
    <div className="banner banner-error" role="alert">
      {accessErrorMessage(error)}
    </div>
  )
}
