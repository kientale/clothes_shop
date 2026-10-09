package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.features.mail.service.MailService;
import lemonadex.project.clothes.features.mail.service.MailTemplates;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.StockAlertRequest;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.StockAlertSummary;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.AlertRow;
import lemonadex.project.clothes.features.storefront.repository.StockAlertRepository;
import lemonadex.project.clothes.features.storefront.repository.StorefrontRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * "Email me when it is back": shoppers leave an email for a sold-out size; a periodic check mails everyone whose
 * size has stock again, once. Checking stock periodically covers every way stock comes back (receipts, returns,
 * cancelled orders) without hooking into each of them.
 */
@Slf4j
@Service @RequiredArgsConstructor
public class StockAlertService {
    static final int MAX_OPEN_PER_EMAIL = 30;
    private static final int BATCH = 200;

    private final StockAlertRepository alerts;
    private final StorefrontRepository store;
    private final MailService mail;

    @Transactional
    public void subscribe(StockAlertRequest request, UUID accountId) {
        long available = alerts.availableIfSold(request.productVariantId()).orElseThrow(() -> new ResourceNotFoundException("Product variant"));
        if (available > 0) throw new ConflictException("VARIANT_IN_STOCK", "This size is in stock; it can be ordered now");
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (alerts.openFor(email) >= MAX_OPEN_PER_EMAIL)
            throw new ConflictException("STOCK_ALERT_LIMIT_REACHED", "Too many open back-in-stock requests for this email");
        UUID customer = accountId == null ? null : store.customerOf(accountId).orElse(null);
        alerts.subscribe(request.productVariantId(), email, customer);
    }

    /** Mails the requests whose size is back; returns how many were sent. Runs every few minutes. */
    @Scheduled(fixedDelayString = "${app.stock-alerts.interval:PT5M}", initialDelayString = "${app.stock-alerts.initial-delay:PT1M}")
    @Transactional
    public int notifyRestocked() {
        List<AlertRow> due = alerts.due(BATCH);
        for (AlertRow alert : due) {
            String link = mail.storefront() + "/shop/products/" + alert.slug();
            mail.send(alert.email(), "Đã có hàng: " + alert.productName(),
                    MailTemplates.backInStock(alert.productName(), alert.colorName() + " / " + alert.sizeName(), link));
        }
        alerts.markNotified(due.stream().map(AlertRow::id).toList());
        if (!due.isEmpty()) log.info("Sent {} back-in-stock emails", due.size());
        return due.size();
    }

    public List<StockAlertSummary> summary(int limit) {
        return alerts.summary(limit).stream().map(r -> new StockAlertSummary(r.productVariantId(), r.productId(), r.productName(), r.sku(),
                r.colorName(), r.sizeName(), r.waiting(), Math.max(0, r.available()), r.latestAt())).toList();
    }
}
