package pl.rynek.catalog.api.dto;

import java.util.UUID;

import pl.rynek.catalog.offers.OfferType;

/**
 * Dla koszyka i zamówień: {@code available} = opublikowana i (usługa albo zapas większy od zera). {@code stock} to
 * sztuki dostępne (rezerwacje są już odjęte). Tytuł, miniatura i sprzedawca służą do pozycji koszyka i zamówienia.
 */
public record PriceAndStockResponse(UUID offerId, OfferType type, MoneyDto price, Integer stock, boolean available,
                                    String title, UUID channelId, UUID ownerId, String thumbnailUrl, int tone) {
}
