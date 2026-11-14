package pl.rynek.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strona zmian ofert dla search-service ({@code GET /internal/offers/search-sync}). Kursor {@code (nextSince, nextAfterId)}
 * to klucz ostatniej oferty strony (puste przy pustej stronie: klient zostaje przy swoim kursorze); {@code hasMore} mówi,
 * że warto od razu pobrać następną stronę.
 */
public record SearchSyncPage(List<SearchSyncItem> items, Instant nextSince, UUID nextAfterId, boolean hasMore) {

    /**
     * Jedna oferta w indeksie. {@code active} = opublikowana i nieusunięta; tylko wtedy {@code card} i reszta pól są
     * wypełnione, w pozostałych przypadkach to „nagrobek” (id i czas zmiany), po którym wyszukiwarka usuwa ofertę.
     * {@code categoryPath}: nazwy kategorii od głównej, {@code categoryIds}: ich identyfikatory (filtr po kategorii nadrzędnej).
     * {@code available}: usługa albo produkt z zapasem większym od zera (bez śledzonego zapasu też dostępny).
     */
    public record SearchSyncItem(UUID id, boolean active, Instant updatedAt, OfferCardResponse card, String description,
                                 String categoryId, List<String> categoryPath, List<String> categoryIds, boolean hasVideo, boolean available,
                                 Instant createdAt, Instant publishedAt, List<OfferParamDto> params) {

        public static SearchSyncItem tombstone(UUID id, Instant updatedAt) {
            return new SearchSyncItem(id, false, updatedAt, null, null, null, List.of(), List.of(), false, false, null, null, List.of());
        }
    }
}
