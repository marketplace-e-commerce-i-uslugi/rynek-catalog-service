package pl.rynek.catalog.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pełna lista zdjęć w docelowej kolejności (pierwsze jest miniaturą) i opcjonalny film. Pliki z media-service
 * o przeznaczeniu {@code OFFER_PHOTO}; {@code videoId} sprawdzi tiktok-integration, gdy powstanie.
 */
public record SetMediaRequest(
        @NotNull(message = "Podaj listę zdjęć.")
        @Size(max = 8, message = "Oferta może mieć najwyżej 8 zdjęć.")
        List<@NotNull UUID> photoMediaIds,
        UUID videoId) {
}
