package lemonadex.project.clothes.features.report.dto;
import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.features.report.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class ReportResponses {
    private ReportResponses() {}
    public record ReportPeriod(Instant from, Instant to, String timezone) {}
    public record RevenuePoint(LocalDate bucketStart, BigDecimal paidAmount, BigDecimal refundedAmount, BigDecimal netReceivedAmount) {}
    public record RevenueReport(ReportPeriod period, ReportBucket groupBy, BigDecimal paidAmount, BigDecimal refundedAmount,
            BigDecimal netReceivedAmount, List<RevenuePoint> series) {}
    public record StatusCount(String status, long count, BigDecimal amount) {}
    public record OrderPoint(LocalDate bucketStart, long orderCount, long cancelledCount, BigDecimal orderAmount) {}
    public record OrderReport(ReportPeriod period, ReportBucket groupBy, long totalOrders, long cancelledOrders,
            BigDecimal orderAmount, BigDecimal cancelledAmount, BigDecimal averageOrderValue,
            List<StatusCount> statuses, List<OrderPoint> series) {}
    public record BestsellerRow(UUID productId, String productName, boolean deleted, long orderCount, long grossQuantity,
            long returnedQuantity, long netQuantity, BigDecimal merchandiseAmount) {}
    public record BestsellerReport(ReportPeriod period, PageResponse<BestsellerRow> products) {}
    public record StockRow(UUID inventoryId, UUID warehouseId, String warehouseName, boolean warehouseDeleted,
            UUID productVariantId, UUID productId, String productName, String sku, boolean productDeleted, boolean variantDeleted,
            long quantityOnHand, long quantityReserved, long quantityAvailable, BigDecimal unitRetailPrice, BigDecimal retailValue) {}
    public record InventoryReport(Instant asOf, long stockRecords, long quantityOnHand, long quantityReserved, long quantityAvailable,
            BigDecimal retailValue, PageResponse<StockRow> stocks) {}
    public record CustomerReportRow(UUID customerId, String fullName, String status, boolean deleted, long deliveredOrders,
            BigDecimal orderAmount, BigDecimal refundedAmount, BigDecimal netOrderAmount) {}
    public record CustomerReport(ReportPeriod period, long newCustomers, long totalCustomers, long activeCustomers,
            long purchasingCustomers, long repeatCustomers, PageResponse<CustomerReportRow> customers) {}
    public record ReturnReportRow(UUID returnRequestId, UUID orderId, String orderCode, UUID customerId, String requestType,
            String status, String reason, Instant requestedAt, Instant completedAt, long quantity, BigDecimal refundedAmount) {}
    public record ReturnReport(ReportPeriod period, long totalRequests, long returnRequests, long exchangeRequests,
            long requestedQuantity, long completedQuantity, BigDecimal refundedAmount, List<StatusCount> statuses,
            PageResponse<ReturnReportRow> requests) {}
    public record CampaignRow(CampaignType campaignType, UUID campaignId, String name, String code, boolean deleted,
            long orderCount, long releasedOrderCount, long quantity, BigDecimal discountAmount, BigDecimal attributedOrderAmount) {}
    public record PromotionReport(ReportPeriod period, PageResponse<CampaignRow> campaigns) {}
}
