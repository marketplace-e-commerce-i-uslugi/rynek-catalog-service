package pl.rynek.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pl.rynek.catalog.api.dto.OfferCardResponse.RatingRef;
import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.offers.OfferStatus;
import pl.rynek.catalog.offers.OfferType;

/**
 * Pełna oferta. Kształt {@code Offer} z rynek-frontend; {@code photoMediaIds} idą w tej samej kolejności co
 * {@code photoUrls}, żeby kreator mógł edytować zdjęcia. Dostawa i liczba pytań przyjdą z shipping i engagement.
 */
public record OfferResponse(UUID id, OfferType type, String title, MoneyDto price, String priceUnit, String condition,
                            String thumbnailUrl, int tone, String placeholder, Integer videoDurationSec,
                            ChannelRef channel, RatingRef rating, String location, Instant nextSlot,
                            OfferStatus status, String description, String categoryId, List<String> categoryPath,
                            List<OfferParamDto> params, List<String> tags, Integer stock, List<String> photoUrls,
                            List<UUID> photoMediaIds, UUID videoId, Object delivery, ServiceDetailsResponse service,
                            int questionsCount, Instant createdAt) {

    public record ServiceDetailsResponse(MoneyDto priceFrom, String unit, String area, int durationMin, int depositPercent) {
    }
}
