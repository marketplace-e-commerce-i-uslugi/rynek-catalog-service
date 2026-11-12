package pl.rynek.catalog.api.dto;

import java.util.UUID;

import pl.rynek.catalog.offers.OfferType;

/**
 * Dla koszyka, zamówień i rezerwacji: {@code available} = opublikowana i (usługa albo zapas większy od zera).
 * {@code stock} to sztuki dostępne (rezerwacje są już odjęte). Tytuł, miniatura i sprzedawca służą do pozycji koszyka
 * i zamówienia. {@code service}: cennik usługi dla booking-service ({@code null} dla produktu i usługi bez cennika).
 */
public record PriceAndStockResponse(UUID offerId, OfferType type, MoneyDto price, Integer stock, boolean available,
                                    String title, UUID channelId, UUID ownerId, String thumbnailUrl, int tone, Service service) {

    /** Cena „od” jest w {@code price}; {@code unit} np. „godz.”, „m²”, „usługa”. */
    public record Service(String unit, int durationMin, int depositPercent) {
    }
}
