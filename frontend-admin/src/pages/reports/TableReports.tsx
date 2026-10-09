import { ArrowsClockwiseIcon, ChartBarIcon, PackageIcon, TagIcon, UsersIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RETURN_STATUS, RETURN_TYPE_LABEL, type ReturnStatus, type ReturnType } from '../../api/commerce'
import {
  CAMPAIGN_TYPE_LABEL,
  reports,
  type BestsellerRow,
  type CampaignRow,
  type CampaignType,
  type CustomerReport,
  type CustomerReportRow,
  type InventoryReport,
  type ReturnReport,
  type ReturnReportRow,
  type StockRow,
} from '../../api/reports'
import { FilterSelect, Kpi, Kpis, ListPanel, PageHead, Pill, SearchBox, ShareList, StatePill, countOf, vndOf, whenOf } from '../../components/kit'
import { useDebounced } from '../../components/ui'
import { PeriodNote, ReportFilters, useReportFilters } from './ReportFilters'

function Deleted() {
  return <Pill tone="inactive">Đã xóa</Pill>
}

export function BestsellersReportPage() {
  const filters = useReportFilters()
  const [period, setPeriod] = useState<Parameters<typeof PeriodNote>[0]['period']>()
  return (
    <div className="page page-wide">
      <PageHead
        title="Sản phẩm bán chạy"
        lede="Tính trên đơn đã giao hoặc hoàn tất. Lượng ròng trừ hàng trả đã hoàn tất; giá trị hàng đã phân bổ giảm giá, không gồm phí giao hàng."
      />
      <ReportFilters state={filters.state} onChange={filters.setState} />
      <PeriodNote period={period} />
      <ListPanel<BestsellerRow>
        filterKey={filters.key}
        load={async (paging) => {
          const r = await reports.bestsellers({ ...filters.query, ...paging })
          setPeriod(r.period)
          return r.products
        }}
        rowKey={(r) => r.productId}
        itemLabel="sản phẩm"
        emptyIcon={<ChartBarIcon size={36} aria-hidden />}
        columns={[
          {
            header: 'Sản phẩm',
            render: (r) => (
              <span className="person-name">
                {r.productName} {r.deleted && <Deleted />}
              </span>
            ),
          },
          { header: 'Số đơn', render: (r) => countOf(r.orderCount), className: 'num' },
          { header: 'Đã giao', render: (r) => countOf(r.grossQuantity), className: 'num muted' },
          { header: 'Trả lại', render: (r) => countOf(r.returnedQuantity), className: 'num muted' },
          { header: 'Bán ròng', render: (r) => <strong>{countOf(r.netQuantity)}</strong>, className: 'num' },
          { header: 'Giá trị hàng', render: (r) => <strong className="money">{vndOf(r.merchandiseAmount)}</strong>, className: 'num nowrap' },
        ]}
      />
    </div>
  )
}

export function InventoryReportPage() {
  const filters = useReportFilters()
  const [search, setSearch] = useState('')
  const [lowStock, setLowStock] = useState(false)
  const [report, setReport] = useState<InventoryReport>()
  const term = useDebounced(search.trim())
  const query = { warehouseId: filters.query.warehouseId, search: term, lowStock: lowStock || undefined }
  return (
    <div className="page page-wide">
      <PageHead
        title="Báo cáo tồn kho"
        lede="Ảnh chụp tồn kho hiện tại. Giá trị tính theo giá bán hiện tại của biến thể, chưa phải giá vốn."
      />
      <ReportFilters state={filters.state} onChange={filters.setState} noRange />
      <Kpis>
        <Kpi label="Dòng tồn kho" value={countOf(report?.stockRecords)} loading={!report} />
        <Kpi label="Tồn thực tế" value={countOf(report?.quantityOnHand)} loading={!report} />
        <Kpi label="Đang giữ" value={countOf(report?.quantityReserved)} loading={!report} />
        <Kpi label="Giá trị theo giá bán" value={vndOf(report?.retailValue)} loading={!report} note={report ? `Tại ${whenOf(report.asOf)}` : undefined} />
      </Kpis>
      <ListPanel<StockRow>
        filterKey={JSON.stringify(query)}
        load={async (paging) => {
          const r = await reports.inventory({ ...query, ...paging })
          setReport(r)
          return r.stocks
        }}
        rowKey={(r) => r.inventoryId}
        itemLabel="dòng tồn kho"
        emptyIcon={<PackageIcon size={36} aria-hidden />}
        toolbar={
          <>
            <SearchBox value={search} onChange={setSearch} placeholder="Tìm theo tên sản phẩm hoặc SKU" />
            <label className="toggle-chip">
              <input type="checkbox" checked={lowStock} onChange={(e) => setLowStock(e.target.checked)} />
              <span>Chỉ hàng sắp hết (≤ 5)</span>
            </label>
          </>
        }
        columns={[
          {
            header: 'Sản phẩm',
            render: (r) => (
              <div className="person-text">
                <span className="person-name">
                  {r.productName} {(r.productDeleted || r.variantDeleted) && <Deleted />}
                </span>
                <span className="person-sub mono">{r.sku}</span>
              </div>
            ),
          },
          { header: 'Kho', render: (r) => <>{r.warehouseName} {r.warehouseDeleted && <Deleted />}</> },
          { header: 'Tồn', render: (r) => countOf(r.quantityOnHand), className: 'num' },
          { header: 'Giữ', render: (r) => countOf(r.quantityReserved), className: 'num muted' },
          {
            header: 'Khả dụng',
            render: (r) => (r.quantityAvailable <= 5 ? <Pill tone={r.quantityAvailable === 0 ? 'blocked' : 'warn'}>{countOf(r.quantityAvailable)}</Pill> : countOf(r.quantityAvailable)),
            className: 'num',
          },
          { header: 'Giá bán', render: (r) => vndOf(r.unitRetailPrice), className: 'num nowrap muted' },
          { header: 'Giá trị', render: (r) => <strong className="money">{vndOf(r.retailValue)}</strong>, className: 'num nowrap' },
        ]}
      />
    </div>
  )
}

export function CustomersReportPage() {
  const filters = useReportFilters()
  const [report, setReport] = useState<CustomerReport>()
  return (
    <div className="page page-wide">
      <PageHead title="Báo cáo khách hàng" lede="Khách mới theo ngày tạo hồ sơ; khách mua tính trên đơn đã giao hoặc hoàn tất trong kỳ. Mua lại nghĩa là từ hai đơn." />
      <ReportFilters state={filters.state} onChange={filters.setState} />
      <PeriodNote period={report?.period} />
      <Kpis>
        <Kpi label="Khách mới" value={countOf(report?.newCustomers)} loading={!report} />
        <Kpi label="Khách đã mua" value={countOf(report?.purchasingCustomers)} loading={!report} />
        <Kpi
          label="Mua lại"
          value={countOf(report?.repeatCustomers)}
          loading={!report}
          note={report && report.purchasingCustomers > 0 ? `${Math.round((report.repeatCustomers / report.purchasingCustomers) * 100)}% khách đã mua` : undefined}
        />
        <Kpi label="Đang hoạt động" value={countOf(report?.activeCustomers)} loading={!report} note={report ? `Trên ${countOf(report.totalCustomers)} hồ sơ` : undefined} />
      </Kpis>
      <ListPanel<CustomerReportRow>
        filterKey={filters.key}
        load={async (paging) => {
          const r = await reports.customers({ ...filters.query, ...paging })
          setReport(r)
          return r.customers
        }}
        rowKey={(r) => r.customerId}
        itemLabel="khách hàng"
        emptyIcon={<UsersIcon size={36} aria-hidden />}
        columns={[
          {
            header: 'Khách hàng',
            render: (r) => (
              <span className="person-name">
                {r.fullName} {r.deleted && <Deleted />}
              </span>
            ),
          },
          { header: 'Đơn đã giao', render: (r) => countOf(r.deliveredOrders), className: 'num' },
          { header: 'Giá trị đơn', render: (r) => vndOf(r.orderAmount), className: 'num nowrap muted' },
          { header: 'Đã hoàn', render: (r) => vndOf(r.refundedAmount), className: 'num nowrap muted' },
          { header: 'Giá trị ròng', render: (r) => <strong className="money">{vndOf(r.netOrderAmount)}</strong>, className: 'num nowrap' },
        ]}
      />
    </div>
  )
}

export function ReturnsReportPage() {
  const filters = useReportFilters()
  const [type, setType] = useState<ReturnType | ''>('')
  const [report, setReport] = useState<ReturnReport>()
  const query = { ...filters.query, requestType: type || undefined }
  return (
    <div className="page page-wide">
      <PageHead title="Báo cáo đổi/trả hàng" lede="Yêu cầu theo ngày tạo. Tiền hoàn gồm các khoản xử lý sau kỳ, tính đến hiện tại." />
      <ReportFilters state={filters.state} onChange={filters.setState}>
        <FilterSelect label="Loại" value={type} options={Object.entries(RETURN_TYPE_LABEL) as [ReturnType, string][]} onChange={setType} />
      </ReportFilters>
      <PeriodNote period={report?.period} />
      <Kpis>
        <Kpi label="Yêu cầu" value={countOf(report?.totalRequests)} loading={!report} note={report ? `${report.returnRequests} trả, ${report.exchangeRequests} đổi` : undefined} />
        <Kpi label="Số lượng yêu cầu" value={countOf(report?.requestedQuantity)} loading={!report} />
        <Kpi label="Đã hoàn tất" value={countOf(report?.completedQuantity)} loading={!report} note="Sản phẩm" />
        <Kpi label="Đã hoàn tiền" value={vndOf(report?.refundedAmount)} loading={!report} />
      </Kpis>
      {report && report.statuses.length > 0 && (
        <section className="panel report-panel">
          <h2 className="section-title">Phân bố trạng thái</h2>
          <ShareList rows={report.statuses.map((s) => ({ key: s.status, label: <StatePill map={RETURN_STATUS} value={s.status as ReturnStatus} />, count: s.count }))} />
        </section>
      )}
      <ListPanel<ReturnReportRow>
        filterKey={JSON.stringify(query)}
        load={async (paging) => {
          const r = await reports.returns({ ...query, ...paging })
          setReport(r)
          return r.requests
        }}
        rowKey={(r) => r.returnRequestId}
        itemLabel="yêu cầu"
        emptyIcon={<ArrowsClockwiseIcon size={36} aria-hidden />}
        columns={[
          {
            header: 'Đơn hàng',
            render: (r) => (
              <Link to={`/orders/${r.orderId}`} className="row-link mono-strong">
                {r.orderCode}
              </Link>
            ),
          },
          { header: 'Loại', render: (r) => RETURN_TYPE_LABEL[r.requestType as ReturnType] ?? r.requestType },
          { header: 'Lý do', render: (r) => <span className="cell-wrap">{r.reason}</span> },
          { header: 'SL', render: (r) => countOf(r.quantity), className: 'num' },
          { header: 'Đã hoàn', render: (r) => vndOf(r.refundedAmount), className: 'num nowrap' },
          { header: 'Trạng thái', render: (r) => <StatePill map={RETURN_STATUS} value={r.status as ReturnStatus} /> },
          { header: 'Ngày yêu cầu', render: (r) => whenOf(r.requestedAt), className: 'muted nowrap' },
        ]}
      />
    </div>
  )
}

export function PromotionsReportPage() {
  const filters = useReportFilters()
  const [type, setType] = useState<CampaignType | ''>('')
  const [period, setPeriod] = useState<Parameters<typeof PeriodNote>[0]['period']>()
  const query = { ...filters.query, campaignType: type || undefined }
  return (
    <div className="page page-wide">
      <PageHead
        title="Hiệu quả khuyến mãi"
        lede="Mỗi chiến dịch tính trên đơn đặt trong kỳ. Một đơn có thể dùng cả mã giảm giá và khuyến mãi, nên không cộng giá trị đơn giữa các dòng."
      />
      <ReportFilters state={filters.state} onChange={filters.setState}>
        <FilterSelect label="Loại" value={type} options={Object.entries(CAMPAIGN_TYPE_LABEL) as [CampaignType, string][]} onChange={setType} />
      </ReportFilters>
      <PeriodNote period={period} />
      <ListPanel<CampaignRow>
        filterKey={JSON.stringify(query)}
        load={async (paging) => {
          const r = await reports.promotions({ ...query, ...paging })
          setPeriod(r.period)
          return r.campaigns
        }}
        rowKey={(r) => `${r.campaignType}-${r.campaignId}`}
        itemLabel="chiến dịch"
        emptyIcon={<TagIcon size={36} aria-hidden />}
        columns={[
          {
            header: 'Chiến dịch',
            render: (r) => (
              <div className="person-text">
                <span className="person-name">
                  {r.name} {r.deleted && <Deleted />}
                </span>
                {r.code && <span className="person-sub mono">{r.code}</span>}
              </div>
            ),
          },
          { header: 'Loại', render: (r) => <span className="chip chip-sm">{CAMPAIGN_TYPE_LABEL[r.campaignType]}</span> },
          { header: 'Số đơn', render: (r) => countOf(r.orderCount), className: 'num' },
          { header: 'Đơn hoàn lượt', render: (r) => countOf(r.releasedOrderCount), className: 'num muted' },
          { header: 'Số lượng', render: (r) => (r.campaignType === 'COUPON' ? <span className="muted">-</span> : countOf(r.quantity)), className: 'num' },
          { header: 'Tiền giảm', render: (r) => vndOf(r.discountAmount), className: 'num nowrap' },
          { header: 'Giá trị đơn', render: (r) => <strong className="money">{vndOf(r.attributedOrderAmount)}</strong>, className: 'num nowrap' },
        ]}
      />
    </div>
  )
}
