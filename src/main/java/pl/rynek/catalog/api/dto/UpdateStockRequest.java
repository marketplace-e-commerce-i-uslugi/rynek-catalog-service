package pl.rynek.catalog.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStockRequest(
        @NotNull(message = "Podaj liczbę sztuk.")
        @Min(value = 0, message = "Liczba sztuk nie może być ujemna.")
        @Max(value = 100000, message = "Liczba sztuk może wynosić najwyżej 100 000.")
        Integer stock) {
}
