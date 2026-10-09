package lemonadex.project.clothes.features.storefront.service;

import lemonadex.project.clothes.common.dto.PageResponse;
import lemonadex.project.clothes.common.exception.BadRequestException;
import lemonadex.project.clothes.common.exception.ConflictException;
import lemonadex.project.clothes.common.exception.ResourceNotFoundException;
import lemonadex.project.clothes.common.util.PasswordPolicy;
import lemonadex.project.clothes.features.storefront.dto.StorefrontRequests.*;
import lemonadex.project.clothes.features.storefront.dto.StorefrontResponses.*;
import lemonadex.project.clothes.features.storefront.model.StorefrontRows.*;
import lemonadex.project.clothes.features.storefront.repository.CustomerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** Profile, password, saved addresses and wishlist of the signed-in customer. The account always comes from the JWT. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CustomerAccountService {
    static final int MAX_ADDRESSES = 10;
    static final int MAX_WISHLIST = 200;

    private final CustomerAccountRepository repository;
    private final StorefrontService storefront;
    private final PasswordEncoder passwords;

    public Profile profile(UUID accountId) {
        return view(row(accountId));
    }

    @Transactional
    public Profile updateProfile(UUID accountId, ProfileRequest request) {
        ProfileRow row = row(accountId);
        repository.updateProfile(row.customerId(), request.fullName().strip(), blank(request.phone()) ? null : request.phone().strip(),
                request.gender(), request.dateOfBirth());
        return profile(accountId);
    }

    @Transactional
    public void changePassword(UUID accountId, PasswordChangeRequest request) {
        ProfileRow row = row(accountId);
        if (!passwords.matches(request.currentPassword(), row.passwordHash()))
            throw new BadRequestException("CURRENT_PASSWORD_INCORRECT", "The current password is incorrect");
        PasswordPolicy.validate(request.newPassword());
        repository.updatePassword(accountId, passwords.encode(request.newPassword()));
    }

    public List<SavedAddress> addresses(UUID accountId) {
        return repository.addresses(customer(accountId)).stream().map(CustomerAccountService::view).toList();
    }

    /** The first address becomes the default automatically. */
    @Transactional
    public SavedAddress addAddress(UUID accountId, AddressRequest request) {
        UUID customer = customer(accountId);
        int count = repository.countAddresses(customer);
        if (count >= MAX_ADDRESSES) throw new ConflictException("ADDRESS_LIMIT_REACHED", "At most " + MAX_ADDRESSES + " addresses can be saved");
        UUID id = repository.insertAddress(customer, row(request));
        if (count == 0 || request.makeDefault()) repository.makeDefault(customer, id);
        return view(repository.address(customer, id).orElseThrow());
    }

    @Transactional
    public SavedAddress updateAddress(UUID accountId, UUID id, AddressRequest request) {
        UUID customer = customer(accountId);
        owned(customer, id);
        repository.updateAddress(customer, id, row(request));
        if (request.makeDefault()) repository.makeDefault(customer, id);
        return view(repository.address(customer, id).orElseThrow());
    }

    @Transactional
    public SavedAddress makeDefault(UUID accountId, UUID id) {
        UUID customer = customer(accountId);
        owned(customer, id);
        repository.makeDefault(customer, id);
        return view(repository.address(customer, id).orElseThrow());
    }

    /** Deleting the default address hands the default to the oldest remaining one. */
    @Transactional
    public void deleteAddress(UUID accountId, UUID id) {
        UUID customer = customer(accountId);
        AddressRow address = owned(customer, id);
        repository.deleteAddress(customer, id);
        if (address.isDefault()) repository.firstAddress(customer).ifPresent(next -> repository.makeDefault(customer, next));
    }

    public PageResponse<ProductCard> wishlist(UUID accountId, int page, int size) {
        return storefront.cards(ProductQuery.wishlist(customer(accountId), page, size));
    }

    public List<UUID> wishlistIds(UUID accountId) {
        return repository.wishlistIds(customer(accountId));
    }

    @Transactional
    public List<UUID> addToWishlist(UUID accountId, UUID productId) {
        UUID customer = customer(accountId);
        if (!repository.productOnSale(productId)) throw new ResourceNotFoundException("Product");
        if (repository.countWishlist(customer) >= MAX_WISHLIST)
            throw new ConflictException("WISHLIST_LIMIT_REACHED", "At most " + MAX_WISHLIST + " products can be saved");
        repository.addToWishlist(customer, productId);
        return repository.wishlistIds(customer);
    }

    @Transactional
    public List<UUID> removeFromWishlist(UUID accountId, UUID productId) {
        UUID customer = customer(accountId);
        repository.removeFromWishlist(customer, productId);
        return repository.wishlistIds(customer);
    }

    private ProfileRow row(UUID accountId) {
        return repository.profile(accountId).orElseThrow(() -> new ResourceNotFoundException("Active customer profile"));
    }

    private UUID customer(UUID accountId) {
        return row(accountId).customerId();
    }

    private AddressRow owned(UUID customer, UUID id) {
        // Another customer's address answers 404, so ids cannot be probed.
        return repository.address(customer, id).orElseThrow(() -> new ResourceNotFoundException("Address"));
    }

    private static AddressRow row(AddressRequest r) {
        return new AddressRow(null, r.recipientName().strip(), r.phone().strip(), r.addressLine().strip(), r.ward().strip(),
                blank(r.district()) ? null : r.district().strip(), r.province().strip(), false);
    }

    private static Profile view(ProfileRow r) {
        return new Profile(r.accountId(), r.email(), r.fullName(), r.phone(), r.gender(), r.dateOfBirth());
    }

    private static SavedAddress view(AddressRow r) {
        return new SavedAddress(r.id(), r.recipientName(), r.phone(), r.addressLine(), r.ward(), r.district(), r.province(), r.isDefault());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
