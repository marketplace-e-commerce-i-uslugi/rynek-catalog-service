package pl.rynek.catalog.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import pl.rynek.catalog.offers.OfferType;

/** Zmiana szkicu lub oferty; pominięte pola ({@code null}) zostają bez zmian. Kształt {@code OfferDraftRequest}. */
public record UpdateOfferRequest(
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
        List<@Valid OfferParamDto> params) {
}
