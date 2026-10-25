package pl.rynek.catalog.api.dto;

import java.util.UUID;

import pl.rynek.catalog.offers.OfferStatus;
import pl.rynek.catalog.offers.OfferType;

/** Wiersz panelu sprzedawcy. Kształt {@code MyOffer}; wyświetlenia przyjdą z analytics. */
public record MyOfferResponse(UUID id, String title, OfferStatus status, MoneyDto price, String priceUnit, Integer stock,
                              long views, OfferType type, String thumbnailUrl, int tone) {
}
