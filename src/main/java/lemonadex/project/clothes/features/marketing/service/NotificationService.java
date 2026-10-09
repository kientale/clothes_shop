package lemonadex.project.clothes.features.marketing.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.*;
import lemonadex.project.clothes.common.util.ListFilters;
import lemonadex.project.clothes.features.inventory.service.InventoryService;
import lemonadex.project.clothes.features.marketing.dto.MarketingRequests.NotificationRequest;
import lemonadex.project.clothes.features.marketing.dto.MarketingResponses.*;
import lemonadex.project.clothes.features.marketing.model.*;
import lemonadex.project.clothes.features.marketing.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final CustomerNotificationRepository inbox;
    private final MarketingQueryRepository queries;
    private final InventoryService inventory;
    private final Clock clock;
    private final lemonadex.project.clothes.features.settings.service.SettingsService settings;
    public PageResponse<NotificationResponse> list(String search, NotificationStatus status, int page, int size) {
        return PageResponse.from(notifications.findAll(ListFilters.where(search, new String[]{"title", "content"}, ListFilters.values("status", status), null, null),
                PageRequest.of(page, size, ListFilters.NEWEST)).map(this::response));
    }
    public NotificationResponse get(UUID id) { return response(required(id)); }
    @Transactional public NotificationResponse create(NotificationRequest r, UUID actor) {
        inventory.lock(); Notification n = new Notification(); n.setCreatedBy(actor); return save(n, r);
    }
    @Transactional public NotificationResponse update(UUID id, NotificationRequest r) {
        inventory.lock(); Notification n = required(id); draft(n); return save(n, r);
    }
    private NotificationResponse save(Notification n, NotificationRequest r) {
        MarketingValidation.unique(r.customerIds());
        if (r.targetType() == NotificationTarget.ALL && !r.customerIds().isEmpty() || r.targetType() == NotificationTarget.SELECTED && r.customerIds().isEmpty())
            throw new BadRequestException("INVALID_NOTIFICATION_TARGET", "Selected notifications need recipients; ALL must have an empty list");
        r.customerIds().forEach(id -> { if (!queries.exists("customers", id)) throw new ResourceNotFoundException("Customer"); });
        n.setTitle(r.title().strip()); n.setContent(r.content().strip()); n.setNotificationType(r.notificationType()); n.setTargetType(r.targetType());
        n.getCustomerIds().clear(); n.getCustomerIds().addAll(r.customerIds()); n.setUpdatedAt(clock.instant());
        return response(notifications.saveAndFlush(n));
    }
    @Transactional public NotificationResponse publish(UUID id) {
        inventory.lock(); Notification n = required(id);
        if (n.getStatus() == NotificationStatus.PUBLISHED) return response(n);
        var configuration = settings.notification();
        if (!configuration.inAppEnabled()) throw new ConflictException("NOTIFICATIONS_DISABLED", "Publishing in-app notifications is disabled");
        if (n.getTargetType() == NotificationTarget.ALL && !configuration.allowBroadcast())
            throw new ConflictException("BROADCAST_DISABLED", "Broadcast notifications are disabled");
        int recipients = queries.publish(id, n.getTargetType() == NotificationTarget.ALL);
        if (recipients > configuration.maxRecipients()) throw new ConflictException("NOTIFICATION_RECIPIENT_LIMIT", "The notification exceeds the configured recipient limit");
        if (recipients == 0) throw new ConflictException("NO_ACTIVE_RECIPIENTS", "Notification has no active recipients");
        n.setStatus(NotificationStatus.PUBLISHED); n.setPublishedAt(clock.instant()); notifications.flush(); return response(n);
    }
    @Transactional public void delete(UUID id) {
        inventory.lock(); Notification n = required(id); draft(n); notifications.delete(n); notifications.flush();
    }
    public PageResponse<RecipientResponse> recipients(UUID id, int page, int size) {
        required(id);
        return PageResponse.from(inbox.findAll((root, query, cb) -> cb.equal(root.get("notification").get("id"), id),
                PageRequest.of(page, size, ListFilters.NEWEST)).map(i -> new RecipientResponse(i.getId(), i.getCustomerId(), i.isRead(), i.getReadAt(), i.getCreatedAt())));
    }
    public PageResponse<InboxResponse> inbox(UUID accountId, Boolean read, int page, int size) {
        UUID customerId = ownCustomer(accountId);
        return PageResponse.from(inbox.findAll((root, query, cb) -> {
            var visible = root.join("notification");
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("customerId"), customerId)); predicates.add(cb.isFalse(visible.get("deleted")));
            predicates.add(cb.equal(visible.get("status"), NotificationStatus.PUBLISHED));
            if (read != null) predicates.add(cb.equal(root.get("read"), read));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, PageRequest.of(page, size, ListFilters.NEWEST)).map(this::inboxResponse));
    }
    @Transactional public InboxResponse markRead(UUID accountId, UUID id) {
        inventory.lock(); UUID customerId = ownCustomer(accountId);
        CustomerNotification i = inbox.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new ResourceNotFoundException("Notification"));
        if (!i.isRead()) { i.setRead(true); i.setReadAt(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); inbox.flush(); }
        return inboxResponse(i);
    }
    private UUID ownCustomer(UUID accountId) {
        UUID id = queries.ownCustomer(accountId); if (id == null) throw new ResourceNotFoundException("Active customer profile"); return id;
    }
    private void draft(Notification n) { if (n.getStatus() != NotificationStatus.DRAFT) throw new ConflictException("NOTIFICATION_PUBLISHED", "Published content and recipient evidence are immutable"); }
    private Notification required(UUID id) { return notifications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification")); }
    private NotificationResponse response(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getContent(), n.getNotificationType(), n.getTargetType(), n.getStatus(), n.getCustomerIds().stream().sorted().toList(),
                n.getCreatedBy(), n.getPublishedAt(), inbox.countByNotificationId(n.getId()), inbox.countByNotificationIdAndReadTrue(n.getId()), n.getCreatedAt(), n.getUpdatedAt());
    }
    private InboxResponse inboxResponse(CustomerNotification i) {
        Notification n = i.getNotification(); return new InboxResponse(i.getId(), n.getId(), n.getTitle(), n.getContent(), n.getNotificationType(), i.isRead(), i.getReadAt(), i.getCreatedAt());
    }
}
