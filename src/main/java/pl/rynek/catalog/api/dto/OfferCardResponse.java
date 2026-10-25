package pl.rynek.catalog.api.dto;

import java.time.Instant;
import java.util.UUID;

import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.offers.OfferType;

/**
 * Karta oferty (feed, wyniki, kanał, koszyk). Kształt {@code OfferCard} z rynek-frontend. Ocena, długość filmu
 * i najbliższy termin przyjdą z engagement, tiktok-integration i booking; do tego czasu są puste.
 */
public record OfferCardResponse(UUID id, OfferType type, String title, MoneyDto price, String priceUnit, String condition,
                                String thumbnailUrl, int tone, String placeholder, Integer videoDurationSec,
                                ChannelRef channel, RatingRef rating, String location, Instant nextSlot) {

    public record RatingRef(double average, int count) {
        public static final RatingRef NONE = new RatingRef(0, 0);
    }
}
