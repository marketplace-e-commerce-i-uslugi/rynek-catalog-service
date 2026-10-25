package pl.rynek.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfferParamDto(
        @NotBlank(message = "Podaj nazwę parametru.")
        @Size(max = 40, message = "Nazwa parametru może mieć najwyżej 40 znaków.")
        String label,
        @NotBlank(message = "Podaj wartość parametru.")
        @Size(max = 100, message = "Wartość parametru może mieć najwyżej 100 znaków.")
        String value) {
}
