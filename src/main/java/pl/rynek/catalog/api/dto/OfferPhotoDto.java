package pl.rynek.catalog.api.dto;

import java.util.UUID;

/** Zdjęcie oferty w kolejności wyświetlania; {@code altText} z media-service z chwili przypięcia zdjęcia. */
public record OfferPhotoDto(UUID mediaId, String url, String altText) {
}
