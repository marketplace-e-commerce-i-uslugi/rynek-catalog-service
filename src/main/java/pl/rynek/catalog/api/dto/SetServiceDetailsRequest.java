package pl.rynek.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cennik usługi: cena „od…” za jednostkę (np. „m²”, „godz.”), obszar działania, czas trwania i zaliczka. */
public record SetServiceDetailsRequest(
        @NotNull(message = "Podaj cenę od.")
        @Valid
        MoneyDto priceFrom,
        @NotBlank(message = "Podaj jednostkę ceny, np. godz. albo m².")
        @Size(max = 20, message = "Jednostka może mieć najwyżej 20 znaków.")
        String unit,
        @NotBlank(message = "Podaj obszar działania, np. Warszawa i okolice.")
        @Size(max = 100, message = "Obszar działania może mieć najwyżej 100 znaków.")
        String area,
        @NotNull(message = "Podaj czas trwania usługi.")
        @Min(value = 15, message = "Usługa trwa co najmniej 15 minut.")
        @Max(value = 1440, message = "Usługa może trwać najwyżej 24 godziny.")
        Integer durationMin,
        @NotNull(message = "Podaj wysokość zaliczki (0, jeśli bez zaliczki).")
        @Min(value = 0, message = "Zaliczka nie może być ujemna.")
        @Max(value = 100, message = "Zaliczka może wynosić najwyżej 100%.")
        Integer depositPercent) {
}
