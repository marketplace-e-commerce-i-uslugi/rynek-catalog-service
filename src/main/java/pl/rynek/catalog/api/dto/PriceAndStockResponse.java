package pl.rynek.catalog.api.dto;

import java.util.UUID;

import pl.rynek.catalog.offers.OfferType;

/** Dla koszyka i zamówień: {@code available} = opublikowana i (usługa albo zapas większy od zera). */
public record PriceAndStockResponse(UUID offerId, OfferType type, MoneyDto price, Integer stock, boolean available) {
}
