package pl.rynek.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pl.rynek.catalog.api.dto.OfferCardResponse.RatingRef;
import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.offers.OfferStatus;
import pl.rynek.catalog.offers.OfferType;

/**
 * Pełna oferta. Kształt {@code Offer} z rynek-frontend; {@code photos} idą w tej samej kolejności co
 * {@code photoUrls} i niosą {@code mediaId} (kreator) oraz tekst alternatywny. Dostawa przyjdzie z shipping, liczba pytań i {@code liked} (puste dla gościa) z engagement.
 */
public record OfferResponse(UUID id, OfferType type, String title, MoneyDto price, String priceUnit, String condition,
                            String thumbnailUrl, int tone, String placeholder, Integer videoDurationSec,
                            ChannelRef channel, RatingRef rating, String location, Instant nextSlot,
                            OfferStatus status, String description, String categoryId, List<String> categoryPath,
                            List<OfferParamDto> params, List<String> tags, Integer stock, List<String> photoUrls,
                            List<OfferPhotoDto> photos, UUID videoId, Object delivery, ServiceDetailsResponse service,
                            int questionsCount, Boolean liked, Instant createdAt) {

    public record ServiceDetailsResponse(MoneyDto priceFrom, String unit, String area, int durationMin, int depositPercent) {
    }
}
