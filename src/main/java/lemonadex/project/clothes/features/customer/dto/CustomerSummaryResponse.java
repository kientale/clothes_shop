package lemonadex.project.clothes.features.customer.dto;

/** Number of visible (not deleted) customer profiles in total and per status. */
public record CustomerSummaryResponse(long total, long active, long inactive, long blocked) {}
