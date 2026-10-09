package lemonadex.project.clothes.features.report.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.util.SearchUtils;
import lemonadex.project.clothes.features.order.model.ReturnStatus;
import lemonadex.project.clothes.features.order.model.ReturnType;
import lemonadex.project.clothes.features.report.dto.ReportResponses.*;
import lemonadex.project.clothes.features.report.model.*;
import lemonadex.project.clothes.features.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;

/** Each response reads one repeatable database snapshot, including its totals and paged details. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    private final ReportRepository repository;
    private final Clock clock;
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;

    public RevenueReport revenue(Instant from, Instant to, String timezone, ReportBucket bucket, UUID warehouseId) {
        var period = period(from, to, timezone); var p = parameters(period, bucket, warehouseId, 0, 1);
        Map<LocalDate, Map<String, Object>> found = dated(repository.revenue(p));
        List<RevenuePoint> series = new ArrayList<>(); BigDecimal paid = BigDecimal.ZERO, refunded = BigDecimal.ZERO;
        for (LocalDate date : dates(period, bucket)) {
            var row = found.getOrDefault(date, Map.of()); BigDecimal receipts = money(row, "paid_amount"), refunds = money(row, "refunded_amount");
            paid = paid.add(receipts); refunded = refunded.add(refunds); series.add(new RevenuePoint(date, receipts, refunds, receipts.subtract(refunds)));
        }
        return new RevenueReport(period, bucket, paid, refunded, paid.subtract(refunded), series);
    }
    public OrderReport orders(Instant from, Instant to, String timezone, ReportBucket bucket, UUID warehouseId) {
        var period = period(from, to, timezone); var p = parameters(period, bucket, warehouseId, 0, 1);
        List<StatusCount> statuses = repository.orderStatuses(p).stream().map(this::status).toList();
        long count = 0, cancelled = 0; BigDecimal amount = BigDecimal.ZERO, cancelledAmount = BigDecimal.ZERO;
        for (StatusCount s : statuses) {
            count += s.count(); if (s.status().equals("CANCELLED")) { cancelled += s.count(); cancelledAmount = cancelledAmount.add(s.amount()); }
            else amount = amount.add(s.amount());
        }
        Map<LocalDate, Map<String, Object>> found = dated(repository.orderSeries(p)); List<OrderPoint> series = new ArrayList<>();
        for (LocalDate date : dates(period, bucket)) { var row = found.getOrDefault(date, Map.of()); series.add(new OrderPoint(date, number(row, "order_count"), number(row, "cancelled_count"), money(row, "order_amount"))); }
        return new OrderReport(period, bucket, count, cancelled, amount, cancelledAmount,
                count == cancelled ? BigDecimal.ZERO : amount.divide(BigDecimal.valueOf(count-cancelled), 2, RoundingMode.HALF_UP), statuses, series);
    }
    public BestsellerReport bestsellers(Instant from, Instant to, String timezone, UUID warehouseId, UUID productId, int page, int size) {
        var period = period(from, to, timezone); var p = parameters(period, ReportBucket.DAY, warehouseId, page, size); p.put("product", productId);
        var rows = repository.bestsellers(p); long total = windowTotal(rows, p, page, repository::bestsellers);
        return new BestsellerReport(period, page(rows.stream().map(r -> new BestsellerRow(uuid(r, "product_id"), text(r, "product_name"), flag(r, "deleted"),
                number(r, "order_count"), number(r, "gross_quantity"), number(r, "returned_quantity"), number(r, "net_quantity"), money(r, "merchandise_amount"))).toList(), page, size, total));
    }
    public InventoryReport inventory(UUID warehouseId, UUID productId, String search, boolean lowStock, int threshold, int page, int size) {
        Instant asOf = clock.instant(); Map<String, Object> p = new HashMap<>();
        p.put("warehouse", warehouseId); p.put("product", productId); p.put("search", search == null || search.isBlank() ? null : SearchUtils.contains(search));
        p.put("low_stock", lowStock); p.put("threshold", threshold); pagination(p, page, size);
        var summary = repository.inventorySummary(p); long total = number(summary, "stock_records");
        var rows = repository.inventoryRows(p).stream().map(r -> new StockRow(uuid(r, "inventory_id"), uuid(r, "warehouse_id"), text(r, "warehouse_name"), flag(r, "warehouse_deleted"),
                uuid(r, "variant_id"), uuid(r, "product_id"), text(r, "product_name"), text(r, "sku"), flag(r, "product_deleted"), flag(r, "variant_deleted"),
                number(r, "quantity_on_hand"), number(r, "quantity_reserved"), number(r, "quantity_available"), money(r, "unit_retail_price"), money(r, "retail_value"))).toList();
        return new InventoryReport(asOf, total, number(summary, "on_hand"), number(summary, "reserved"), number(summary, "available"), money(summary, "retail_value"), page(rows, page, size, total));
    }
    public CustomerReport customers(Instant from, Instant to, String timezone, UUID warehouseId, int page, int size) {
        var period = period(from, to, timezone); var p = parameters(period, ReportBucket.DAY, warehouseId, page, size); var s = repository.customerSummary(p);
        long total = number(s, "purchasing_customers");
        var rows = repository.customerRows(p).stream().map(r -> new CustomerReportRow(uuid(r, "customer_id"), text(r, "full_name"), text(r, "status"), flag(r, "deleted"),
                number(r, "delivered_orders"), money(r, "order_amount"), money(r, "refunded_amount"), money(r, "net_order_amount"))).toList();
        return new CustomerReport(period, number(s, "new_customers"), number(s, "total_customers"), number(s, "active_customers"), total, number(s, "repeat_customers"), page(rows, page, size, total));
    }
    public ReturnReport returns(Instant from, Instant to, String timezone, UUID warehouseId, ReturnStatus status, ReturnType type, int page, int size) {
        var period = period(from, to, timezone); var p = parameters(period, ReportBucket.DAY, warehouseId, page, size);
        p.put("status", status == null ? null : status.name()); p.put("type", type == null ? null : type.name()); var s = repository.returnSummary(p);
        long total = number(s, "total_requests");
        var rows = repository.returnRows(p).stream().map(r -> new ReturnReportRow(uuid(r, "return_request_id"), uuid(r, "order_id"), text(r, "order_code"), uuid(r, "customer_id"),
                text(r, "request_type"), text(r, "status"), text(r, "reason"), instant(r, "requested_at"), instant(r, "completed_at"), number(r, "quantity"), money(r, "refunded_amount"))).toList();
        return new ReturnReport(period, total, number(s, "return_requests"), number(s, "exchange_requests"), number(s, "requested_quantity"), number(s, "completed_quantity"),
                money(s, "refunded_amount"), repository.returnStatuses(p).stream().map(this::status).toList(), page(rows, page, size, total));
    }
    public PromotionReport promotions(Instant from, Instant to, String timezone, UUID warehouseId, CampaignType type, int page, int size) {
        var period = period(from, to, timezone); var p = parameters(period, ReportBucket.DAY, warehouseId, page, size); p.put("type", type == null ? null : type.name());
        var rows = repository.campaignRows(p); long total = windowTotal(rows, p, page, repository::campaignRows);
        return new PromotionReport(period, page(rows.stream().map(r -> new CampaignRow(CampaignType.valueOf(text(r, "campaign_type")), uuid(r, "campaign_id"), text(r, "name"), text(r, "code"), flag(r, "deleted"),
                number(r, "order_count"), number(r, "released_order_count"), number(r, "quantity"), money(r, "discount_amount"), money(r, "attributed_order_amount"))).toList(), page, size, total));
    }
    private ReportPeriod period(Instant from, Instant to, String timezone) {
        if (timezone == null) timezone = settings.general().timezone();
        try { if (!ZoneId.getAvailableZoneIds().contains(timezone)) throw new DateTimeException("Unknown timezone"); ZoneId.of(timezone); }
        catch (DateTimeException ex) { throw new BadRequestException("INVALID_TIMEZONE", "Use an IANA timezone such as Asia/Ho_Chi_Minh or UTC"); }
        if (!repository.timezoneExists(timezone)) throw new BadRequestException("INVALID_TIMEZONE", "Timezone is not supported by the database");
        Instant end = to == null ? clock.instant() : to, start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0)
            throw new BadRequestException("INVALID_REPORT_PERIOD", "Report period must be positive and no longer than 366 days");
        return new ReportPeriod(start, end, timezone);
    }
    private Map<String, Object> parameters(ReportPeriod r, ReportBucket bucket, UUID warehouse, int page, int size) {
        Map<String, Object> p = new HashMap<>(); p.put("from", r.from().atOffset(ZoneOffset.UTC)); p.put("to", r.to().atOffset(ZoneOffset.UTC));
        p.put("zone", r.timezone()); p.put("bucket", bucket.name().toLowerCase(Locale.ROOT)); p.put("warehouse", warehouse); pagination(p, page, size); return p;
    }
    private void pagination(Map<String, Object> p, int page, int size) { p.put("size", size); p.put("offset", (long) page * size); }
    private List<LocalDate> dates(ReportPeriod r, ReportBucket bucket) {
        ZoneId zone = ZoneId.of(r.timezone()); LocalDate first = floor(r.from().atZone(zone).toLocalDate(), bucket), last = floor(r.to().minusNanos(1).atZone(zone).toLocalDate(), bucket);
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = switch (bucket) { case DAY -> d.plusDays(1); case WEEK -> d.plusWeeks(1); case MONTH -> d.plusMonths(1); }) result.add(d);
        return result;
    }
    private LocalDate floor(LocalDate d, ReportBucket b) { return switch (b) { case DAY -> d; case WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); case MONTH -> d.withDayOfMonth(1); }; }
    private Map<LocalDate, Map<String, Object>> dated(List<Map<String, Object>> rows) {
        Map<LocalDate, Map<String, Object>> result = new HashMap<>();
        rows.forEach(r -> { Object value = r.get("bucket_start"); LocalDate date = value instanceof LocalDate d ? d : ((java.sql.Date) value).toLocalDate(); result.put(date, r); }); return result;
    }
    private long windowTotal(List<Map<String, Object>> rows, Map<String, Object> p, int page, Function<Map<String, Object>, List<Map<String, Object>>> query) {
        if (!rows.isEmpty()) return number(rows.getFirst(), "total_rows"); if (page == 0) return 0;
        Map<String, Object> first = new HashMap<>(p); first.put("offset", 0L); first.put("size", 1); var result = query.apply(first); return result.isEmpty() ? 0 : number(result.getFirst(), "total_rows");
    }
    private <T> PageResponse<T> page(List<T> content, int page, int size, long total) { return new PageResponse<>(content, page, size, total, (int) Math.min(Integer.MAX_VALUE, (total+size-1)/size)); }
    private StatusCount status(Map<String, Object> r) { return new StatusCount(text(r, "status"), number(r, "total"), money(r, "amount")); }
    private long number(Map<String, Object> r, String key) { Object value = r.get(key); return value == null ? 0 : ((Number) value).longValue(); }
    private BigDecimal money(Map<String, Object> r, String key) { Object value = r.get(key); return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private UUID uuid(Map<String, Object> r, String key) { return (UUID) r.get(key); }
    private boolean flag(Map<String, Object> r, String key) { return Boolean.TRUE.equals(r.get(key)); }
    private String text(Map<String, Object> r, String key) { Object value = r.get(key); return value == null ? null : value.toString(); }
    private Instant instant(Map<String, Object> r, String key) {
        Object value = r.get(key); if (value == null) return null; if (value instanceof OffsetDateTime d) return d.toInstant(); return ((java.sql.Timestamp) value).toInstant();
    }
}
