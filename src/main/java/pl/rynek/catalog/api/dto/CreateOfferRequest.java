package pl.rynek.catalog.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import pl.rynek.catalog.offers.OfferType;

/**
 * Szkic oferty; wszystkie pola opcjonalne, bez {@code type} powstaje produkt. Kształt {@code OfferDraftRequest}.
 * {@code stock}, {@code media} i {@code service} jak w {@link UpdateOfferRequest}.
 */
public record CreateOfferRequest(
        OfferType type,
        @Size(max = 120, message = "Tytuł może mieć najwyżej 120 znaków.")
        String title,
        String categoryId,
        @Valid
        MoneyDto price,
        String condition,
        @Size(max = 5000, message = "Opis może mieć najwyżej 5000 znaków.")
        String description,
        @Size(max = 20, message = "Oferta może mieć najwyżej 20 parametrów.")
        List<@Valid OfferParamDto> params,
        @Min(value = 0, message = "Liczba sztuk nie może być ujemna.")
        @Max(value = 100000, message = "Liczba sztuk może wynosić najwyżej 100 000.")
        Integer stock,
        @Valid
        SetMediaRequest media,
        @Valid
        SetServiceDetailsRequest service) {
}
