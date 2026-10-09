package lemonadex.project.clothes;

import lemonadex.project.clothes.features.auth.dto.*;
import lemonadex.project.clothes.features.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class InventoryOrderIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    private static final String API = "/api/v1/admin";
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String token;
    private UUID customer, warehouse, product, variant, replacement, stock, replacementStock;

    @BeforeEach
    void fixtures() throws Exception {
        token = auth.login(new LoginRequest("admin", "admin123")).accessToken();
        customer = id(create("/customers", map("fullName", "Commerce " + tag, "status", "ACTIVE")));
        UUID category = id(create("/categories", map("name", "Commerce " + tag, "status", "ACTIVE")));
        UUID brand = id(create("/brands", map("name", "Commerce " + tag, "status", "ACTIVE")));
        UUID color = id(create("/colors", map("name", "Commerce " + tag, "code", "C-" + tag, "status", "ACTIVE")));
        UUID small = id(create("/sizes", map("name", "S " + tag, "code", "S-" + tag, "sortOrder", 1, "status", "ACTIVE")));
        UUID medium = id(create("/sizes", map("name", "M " + tag, "code", "M-" + tag, "sortOrder", 2, "status", "ACTIVE")));
        product = id(create("/products", map("productCode", "P-" + tag, "name", "Commerce " + tag, "brandId", brand,
                "categoryId", category, "gender", "UNISEX", "basePrice", 100, "status", "ACTIVE", "images", List.of())));
        variant = id(create("/product-variants", map("productId", product, "colorId", color, "sizeId", small, "price", 100, "status", "ACTIVE")));
        replacement = id(create("/product-variants", map("productId", product, "colorId", color, "sizeId", medium, "price", 100, "status", "ACTIVE")));
        warehouse = id(create("/warehouses", map("name", "Warehouse " + tag, "address", "Hồ Chí Minh", "status", "ACTIVE")));
        stock = id(create("/inventory", map("warehouseId", warehouse, "productVariantId", variant)));
        replacementStock = id(create("/inventory", map("warehouseId", warehouse, "productVariantId", replacement)));
        movement(variant, "RECEIPT", 10).andExpect(status().isCreated());
        movement(replacement, "RECEIPT", 10).andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() {
        // Remove only this test's fixtures, in foreign-key order; API records themselves remain durable.
        String orders = "select id from orders where customer_id in (select id from customers where full_name like ?)";
        String returned = "select id from return_requests where order_id in (" + orders + ")";
        String payments = "select id from payments where order_id in (" + orders + ")";
        String shipments = "select id from shipments where order_id in (" + orders + ")";
        String like = "%" + tag + "%";
        jdbc.update("delete from refunds where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_images where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_request_items where return_request_id in (" + returned + ")", like);
        jdbc.update("delete from return_requests where order_id in (" + orders + ")", like);
        jdbc.update("delete from shipment_status_history where shipment_id in (" + shipments + ")", like);
        jdbc.update("delete from shipments where order_id in (" + orders + ")", like);
        jdbc.update("delete from payment_transactions where payment_id in (" + payments + ")", like);
        jdbc.update("delete from payments where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_status_history where order_id in (" + orders + ")", like);
        jdbc.update("delete from order_items where order_id in (" + orders + ")", like);
        jdbc.update("delete from orders where customer_id in (select id from customers where full_name like ?)", like);
        jdbc.update("delete from inventory_transactions where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from inventories where warehouse_id in (select id from warehouses where name like ?)", like);
        jdbc.update("delete from warehouses where name like ?", like);
        jdbc.update("delete from product_images where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from product_variants where product_id in (select id from products where name like ?)", like);
        jdbc.update("delete from products where name like ?", like);
        jdbc.update("delete from colors where name like ?", like);
        jdbc.update("delete from sizes where name like ?", like);
        jdbc.update("delete from brands where name like ?", like);
        jdbc.update("delete from categories where name like ?", like);
        jdbc.update("delete from customers where full_name like ?", like);
    }

    @Test
    void receiptsIssuesAdjustmentsAndWarehouseLifecyclePreserveAuditTrail() throws Exception {
        movement(variant, "ISSUE", 3).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantity").value(-3)).andExpect(jsonPath("$.data.quantityBefore").value(10))
                .andExpect(jsonPath("$.data.quantityAfter").value(7));
        perform(post(API + "/inventory/" + stock + "/adjustments"), map("quantityOnHand", 5, "reason", "Kiểm kê"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.quantityAvailable").value(5));
        movement(variant, "ISSUE", 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        perform(get(API + "/inventory/history").param("warehouseId", warehouse.toString()), null)
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].note").value("Kiểm kê"));
        perform(get(API + "/inventory").param("search", tag).param("lowStock", "true"), null).andExpect(jsonPath("$.data.totalElements").value(1));
        create("/inventory", map("warehouseId", warehouse, "productVariantId", variant)).andExpect(status().isConflict());
        perform(delete(API + "/warehouses/" + warehouse), null).andExpect(status().isConflict());
        perform(put(API + "/warehouses/" + warehouse), map("name", "Warehouse " + tag, "address", "Hà Nội", "status", "INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.address").value("Hà Nội"));
        movement(variant, "RECEIPT", 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WAREHOUSE_INACTIVE"));
        UUID unused = id(create("/warehouses", map("name", "Empty " + tag, "address", "Hà Nội", "status", "ACTIVE")));
        perform(delete(API + "/warehouses/" + unused), null).andExpect(status().isOk());
        perform(get(API + "/warehouses/" + unused), null).andExpect(status().isNotFound());
    }

    @Test
    void cancelReleasesReservationsAndVoidsPendingPaymentAndShipmentExactlyOnce() throws Exception {
        UUID order = id(create("/orders", order(2)));
        assertStock(stock, 10, 2);
        movement(variant, "ISSUE", 9).andExpect(status().isConflict());
        perform(post(API + "/inventory/" + stock + "/adjustments"), map("quantityOnHand", 1, "reason", "Wrong count"))
                .andExpect(status().isConflict());
        UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 200)));
        changeOrder(order, "CONFIRMED").andExpect(status().isOk());
        UUID shipment = id(create("/shipments", map("orderId", order, "shippingProvider", "Local", "shippingFee", 0)));
        perform(put(API + "/warehouses/" + warehouse), map("name", "Warehouse " + tag, "address", "HCM", "status", "INACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WAREHOUSE_HAS_RESERVATIONS"));
        changeOrder(order, "CANCELLED").andExpect(status().isOk());
        changeOrder(order, "CANCELLED").andExpect(status().isOk());
        assertStock(stock, 10, 0);
        perform(get(API + "/payments/" + payment), null).andExpect(jsonPath("$.data.status").value("VOID"));
        perform(get(API + "/shipments/" + shipment), null).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        perform(get(API + "/orders/" + order + "/history"), null).andExpect(jsonPath("$.data.totalElements").value(3));
        create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 1)).andExpect(status().isConflict());
    }

    @Test
    void fulfillmentPaymentsReturnsAndRefundsEnforceDiscountedLimits() throws Exception {
        Map<String, Object> request = order(2);
        request.put("items", List.of(map("productVariantId", variant, "quantity", 2, "discountAmount", 20)));
        request.put("discountAmount", 10); request.put("shippingFee", 20);
        UUID order = id(create("/orders", request).andExpect(jsonPath("$.data.subtotal").value(200))
                .andExpect(jsonPath("$.data.discountAmount").value(30)).andExpect(jsonPath("$.data.totalAmount").value(190)));
        UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "BANK_TRANSFER", "amount", 190)));
        create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 1)).andExpect(status().isConflict());
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID", "providerTransactionId", "BANK-" + tag)).andExpect(status().isOk());
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        perform(get(API + "/payments/" + payment + "/transactions"), null).andExpect(jsonPath("$.data.totalElements").value(2));
        UUID shipment = dispatch(order);
        assertStock(stock, 8, 0);
        changeShipment(shipment, "SHIPPED").andExpect(status().isOk()); assertStock(stock, 8, 0);
        changeShipment(shipment, "IN_TRANSIT").andExpect(status().isOk());
        changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        changeOrder(order, "COMPLETED").andExpect(status().isOk());
        UUID item = item(order);
        UUID returned = id(create("/returns", returned(order, item, 1, "RETURN", null))
                .andExpect(jsonPath("$.data.refundableAmount").value(85)));
        create("/returns", returned(order, item, 2, "RETURN", null)).andExpect(status().isConflict());
        create("/refunds", refund(returned, payment, 85)).andExpect(status().isConflict());
        changeReturn(returned, "APPROVED").andExpect(status().isOk());
        changeReturn(returned, "COMPLETED").andExpect(status().isOk());
        changeReturn(returned, "COMPLETED").andExpect(status().isOk()); assertStock(stock, 9, 0);
        create("/refunds", refund(returned, payment, 86)).andExpect(status().isConflict());
        UUID refund = id(create("/refunds", refund(returned, payment, 85)));
        create("/refunds", refund(returned, payment, 1)).andExpect(status().isConflict());
        perform(put(API + "/refunds/" + refund + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        perform(put(API + "/refunds/" + refund + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.refundedAmount").value(85))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_REFUNDED"));
        perform(get(API + "/shipments/history").param("shipmentId", shipment.toString()), null).andExpect(jsonPath("$.data.totalElements").value(4));
        perform(put(API + "/refunds/" + refund + "/status"), map("status", "FAILED")).andExpect(status().isConflict());
    }

    @Test
    void exchangesReserveReplacementThenReceiveAndDispatchExactlyOnce() throws Exception {
        UUID order = id(create("/orders", order(2)));
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(order, item(order), 1, "EXCHANGE", replacement)));
        changeReturn(returned, "APPROVED").andExpect(status().isOk()); assertStock(replacementStock, 10, 1);
        changeReturn(returned, "COMPLETED").andExpect(status().isOk());
        assertStock(stock, 9, 0); assertStock(replacementStock, 9, 0);
        changeReturn(returned, "COMPLETED").andExpect(status().isOk()); assertStock(replacementStock, 9, 0);
        perform(get(API + "/returns/" + returned), null).andExpect(jsonPath("$.data.refundableAmount").value(0));
        UUID second = id(create("/returns", returned(order, item(order), 1, "EXCHANGE", replacement)));
        changeReturn(second, "APPROVED").andExpect(status().isOk()); assertStock(replacementStock, 9, 1);
        changeReturn(second, "CANCELLED").andExpect(status().isOk()); assertStock(replacementStock, 9, 0);
    }

    @Test
    void failedCarrierReturnRestocksAndSubsequentReturnNeverReceivesGoodsTwice() throws Exception {
        UUID order = id(create("/orders", order(2)));
        UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 200)));
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        UUID shipment = dispatch(order);
        changeShipment(shipment, "FAILED").andExpect(status().isOk());
        changeShipment(shipment, "RETURNED").andExpect(status().isOk()); assertStock(stock, 10, 0);
        UUID returned = id(create("/returns", returned(order, item(order), 2, "RETURN", null)));
        changeReturn(returned, "APPROVED").andExpect(status().isOk());
        changeReturn(returned, "COMPLETED").andExpect(status().isOk()); assertStock(stock, 10, 0);
        UUID refund = id(create("/refunds", refund(returned, payment, 200)));
        perform(put(API + "/refunds/" + refund + "/status"), map("status", "SUCCEEDED")).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.orderStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("REFUNDED"));
    }

    @Test
    void insufficientStockRollsBackEntireOrderAndConcurrentRequestsCannotOversell() throws Exception {
        Map<String, Object> failed = order(1);
        failed.put("items", List.of(map("productVariantId", variant, "quantity", 2, "discountAmount", 0),
                map("productVariantId", replacement, "quantity", 11, "discountAmount", 0)));
        create("/orders", failed).andExpect(status().isConflict()); assertStock(stock, 10, 0);
        assertThat(jdbc.queryForObject("select count(*) from orders where customer_id = ?", Integer.class, customer)).isZero();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> create = () -> { start.await(); return create("/orders", order(7)).andReturn().getResponse().getStatus(); };
            Future<Integer> one = executor.submit(create), two = executor.submit(create); start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertStock(stock, 10, 7);
            assertThat(jdbc.queryForObject("select count(*) from orders where customer_id = ?", Integer.class, customer)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void invalidTransitionsCrossOrderReturnsAndValidationAreRejected() throws Exception {
        UUID one = id(create("/orders", order(1))), two = id(create("/orders", order(1)));
        changeOrder(one, "SHIPPED").andExpect(status().isConflict());
        changeOrder(one, "COMPLETED").andExpect(status().isConflict());
        UUID shipment = dispatch(one); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        changeOrder(one, "COMPLETED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_NOT_PAID"));
        changeShipment(shipment, "RETURNED").andExpect(status().isConflict());
        create("/returns", returned(one, item(two), 1, "RETURN", null)).andExpect(status().isBadRequest());
        create("/orders", map("customerId", customer)).andExpect(status().isBadRequest());
        create("/payments", map("orderId", one, "paymentMethod", "COD", "amount", -1)).andExpect(status().isBadRequest());
        movement(variant, "RESERVE", 1).andExpect(status().isBadRequest());
        perform(get(API + "/inventory/transactions").param("from", "2026-10-10T00:00:00Z").param("to", "2026-10-01T00:00:00Z"), null).andExpect(status().isBadRequest());
        for (String path : List.of("/inventory", "/orders", "/payments", "/shipments", "/returns", "/refunds")) {
            perform(get(API + path).param("size", "51"), null).andExpect(status().isBadRequest());
            create(path, Map.of()).andExpect(status().isBadRequest());
        }
    }

    @Test
    void snapshotsAndStockRemainReadableAfterCatalogSoftDeletion() throws Exception {
        UUID order = id(create("/orders", order(1)));
        perform(delete(API + "/products/" + product), null).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.items[0].productName").value("Commerce " + tag));
        perform(get(API + "/inventory/" + stock), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityReserved").value(1));
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(order, item(order), 1, "RETURN", null)));
        changeReturn(returned, "APPROVED").andExpect(status().isOk()); changeReturn(returned, "COMPLETED").andExpect(status().isOk());
        assertStock(stock, 10, 0);
    }

    @Test
    void commercePermissionsAndImmutableHistoriesAreEnforced() throws Exception {
        AuthResponse registered = auth.register(new RegisterRequest(UUID.randomUUID() + "@example.com", "Password-1234", "Password-1234", "Customer", null, null));
        try {
            for (String path : List.of("/warehouses", "/inventory", "/inventory/transactions", "/inventory/history", "/orders", "/payments", "/shipments", "/shipments/history", "/returns", "/refunds")) {
                mvc.perform(get(API + path)).andExpect(status().isUnauthorized());
                mvc.perform(get(API + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.accessToken())).andExpect(status().isForbidden());
            }
            Map<String, String> permissions = Map.of("INVENTORY_READ", "/inventory", "ORDER_READ", "/orders", "PAYMENT_READ", "/payments", "SHIPMENT_READ", "/shipments", "RETURN_READ", "/returns", "REFUND_READ", "/refunds");
            for (var permission : permissions.entrySet()) {
                jdbc.update("update permissions set deleted = true where code = ?", permission.getKey());
                try { perform(get(API + permission.getValue()), null).andExpect(status().isForbidden()); }
                finally { jdbc.update("update permissions set deleted = false where code = ?", permission.getKey()); }
            }
            jdbc.update("update permissions set deleted = true where code = 'ORDER_WRITE'");
            try { create("/orders", order(1)).andExpect(status().isForbidden()); }
            finally { jdbc.update("update permissions set deleted = false where code = 'ORDER_WRITE'"); }
            Map<String, String> writePaths = Map.of("INVENTORY_WRITE", "/inventory/transactions", "PAYMENT_WRITE", "/payments",
                    "SHIPMENT_WRITE", "/shipments", "RETURN_WRITE", "/returns", "REFUND_WRITE", "/refunds");
            Map<String, Map<String, Object>> writeBodies = Map.of(
                    "INVENTORY_WRITE", map("warehouseId", warehouse, "productVariantId", variant, "transactionType", "RECEIPT", "quantity", 1, "note", "Denied"),
                    "PAYMENT_WRITE", map("orderId", UUID.randomUUID(), "paymentMethod", "COD", "amount", 1),
                    "SHIPMENT_WRITE", map("orderId", UUID.randomUUID(), "shippingProvider", "Local", "shippingFee", 0),
                    "RETURN_WRITE", returned(UUID.randomUUID(), UUID.randomUUID(), 1, "RETURN", null),
                    "REFUND_WRITE", refund(UUID.randomUUID(), UUID.randomUUID(), 1));
            for (var permission : writePaths.entrySet()) {
                jdbc.update("update permissions set deleted = true where code = ?", permission.getKey());
                try { create(permission.getValue(), writeBodies.get(permission.getKey())).andExpect(status().isForbidden()); }
                finally { jdbc.update("update permissions set deleted = false where code = ?", permission.getKey()); }
            }
            perform(post(API + "/inventory/history"), map("quantity", 100)).andExpect(status().isMethodNotAllowed());
            UUID order = id(create("/orders", order(1)));
            UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)));
            perform(delete(API + "/payments/" + payment), null).andExpect(status().isMethodNotAllowed());
        } finally {
            jdbc.update("delete from customers where account_id = ?", registered.account().id());
            jdbc.update("delete from accounts where id = ?", registered.account().id());
        }
    }

    @Test
    void partialPaymentsRetriesAndDuplicateBankReferencesRemainConsistent() throws Exception {
        UUID order = id(create("/orders", order(2)));
        UUID first = id(create("/payments", map("orderId", order, "paymentMethod", "BANK_TRANSFER", "amount", 50)));
        perform(put(API + "/payments/" + first + "/status"), map("status", "PAID", "providerTransactionId", "BANK-" + tag)).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_PAID"));
        UUID second = id(create("/payments", map("orderId", order, "paymentMethod", "BANK_TRANSFER", "amount", 150)));
        perform(put(API + "/payments/" + second + "/status"), map("status", "PAID", "providerTransactionId", "BANK-" + tag))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_TRANSACTION_EXISTS"));
        perform(get(API + "/payments/" + second), null).andExpect(jsonPath("$.data.status").value("PENDING"));
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.paidAmount").value(50));
        perform(put(API + "/payments/" + second + "/status"), map("status", "FAILED")).andExpect(status().isOk());
        UUID retry = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 150)));
        perform(put(API + "/payments/" + retry + "/status"), map("status", "PAID")).andExpect(status().isOk());
        perform(get(API + "/orders/" + order), null).andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        perform(put(API + "/payments/" + first + "/status"), map("status", "VOID")).andExpect(status().isConflict());
    }

    @Test
    void refundsFromAnotherOrderAreRejectedAndConcurrentRefundsCannotExceedLimits() throws Exception {
        UUID order = id(create("/orders", order(1)));
        UUID payment = id(create("/payments", map("orderId", order, "paymentMethod", "COD", "amount", 100)));
        perform(put(API + "/payments/" + payment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(order, item(order), 1, "RETURN", null)));
        changeReturn(returned, "APPROVED").andExpect(status().isOk()); changeReturn(returned, "COMPLETED").andExpect(status().isOk());
        UUID otherOrder = id(create("/orders", order(1)));
        UUID otherPayment = id(create("/payments", map("orderId", otherOrder, "paymentMethod", "COD", "amount", 100)));
        perform(put(API + "/payments/" + otherPayment + "/status"), map("status", "PAID")).andExpect(status().isOk());
        create("/refunds", refund(returned, otherPayment, 60)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFUND_ORDER_MISMATCH"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> attempt = () -> { start.await(); return create("/refunds", refund(returned, payment, 60)).andReturn().getResponse().getStatus(); };
            Future<Integer> one = executor.submit(attempt), two = executor.submit(attempt); start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            perform(get(API + "/refunds").param("returnRequestId", returned.toString()), null).andExpect(jsonPath("$.data.totalElements").value(1));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void exchangeApprovalRechecksPriceAndPreservesTheRequestOnFailure() throws Exception {
        UUID order = id(create("/orders", order(1)));
        UUID shipment = dispatch(order); changeShipment(shipment, "DELIVERED").andExpect(status().isOk());
        UUID returned = id(create("/returns", returned(order, item(order), 1, "EXCHANGE", replacement)));
        perform(put(API + "/product-variants/" + replacement), map("sku", "NEW-" + tag, "price", 120, "status", "ACTIVE")).andExpect(status().isOk());
        changeReturn(returned, "APPROVED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXCHANGE_PRICE_MISMATCH"));
        perform(get(API + "/returns/" + returned), null).andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.approvedAt").doesNotExist());
        assertStock(replacementStock, 10, 0);
        changeReturn(returned, "REJECTED").andExpect(status().isOk());
        create("/returns", returned(order, item(order), 1, "RETURN", null)).andExpect(status().isCreated());
    }

    private Map<String, Object> order(int quantity) {
        return map("customerId", customer, "warehouseId", warehouse, "recipientName", "Nguyễn An", "recipientPhone", "0901234567",
                "shippingAddress", "Hồ Chí Minh", "discountAmount", 0, "shippingFee", 0,
                "items", List.of(map("productVariantId", variant, "quantity", quantity, "discountAmount", 0)));
    }
    private Map<String, Object> returned(UUID order, UUID item, int quantity, String type, UUID replacement) {
        return map("orderId", order, "requestType", type, "reason", "Đổi size hoặc trả hàng", "images", List.of("https://img.example.com/return.png"),
                "items", List.of(map("orderItemId", item, "quantity", quantity, "replacementVariantId", replacement)));
    }
    private Map<String, Object> refund(UUID returned, UUID payment, int amount) { return map("returnRequestId", returned, "paymentId", payment, "amount", amount, "refundMethod", "BANK_TRANSFER", "reason", "Trả hàng"); }
    private UUID dispatch(UUID order) throws Exception {
        changeOrder(order, "CONFIRMED").andExpect(status().isOk());
        UUID shipment = id(create("/shipments", map("orderId", order, "shippingProvider", "Local " + tag, "trackingCode", UUID.randomUUID().toString(), "shippingFee", 0)));
        changeShipment(shipment, "SHIPPED").andExpect(status().isOk()); return shipment;
    }
    private UUID item(UUID order) throws Exception { return UUID.fromString(body(perform(get(API + "/orders/" + order), null).andExpect(status().isOk())).get("data").get("items").get(0).get("id").asString()); }
    private ResultActions changeOrder(UUID id, String status) throws Exception { return perform(put(API + "/orders/" + id + "/status"), map("status", status)); }
    private ResultActions changeShipment(UUID id, String status) throws Exception { return perform(put(API + "/shipments/" + id + "/status"), map("status", status)); }
    private ResultActions changeReturn(UUID id, String status) throws Exception { return perform(put(API + "/returns/" + id + "/status"), map("status", status)); }
    private ResultActions movement(UUID variant, String type, int quantity) throws Exception { return create("/inventory/transactions", map("warehouseId", warehouse, "productVariantId", variant, "transactionType", type, "quantity", quantity, "note", "Movement " + tag)); }
    private void assertStock(UUID id, int onHand, int reserved) throws Exception {
        perform(get(API + "/inventory/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.quantityOnHand").value(onHand))
                .andExpect(jsonPath("$.data.quantityReserved").value(reserved)).andExpect(jsonPath("$.data.quantityAvailable").value(onHand - reserved));
    }
    private ResultActions create(String path, Map<String, ?> request) throws Exception { return perform(post(API + path), request); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(body(result.andExpect(status().isCreated())).get("data").get("id").asString()); }
    private JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }
}
