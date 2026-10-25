package pl.rynek.catalog.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** Kwota w groszach: {@code {"amount": 38900, "currency": "PLN"}} = 389,00 zł. */
public record MoneyDto(
        @NotNull(message = "Podaj cenę.")
        @PositiveOrZero(message = "Cena nie może być ujemna.")
        Long amount,
        @NotNull(message = "Podaj walutę.")
        @Pattern(regexp = "PLN", message = "Ceny podajemy w złotych (PLN).")
        String currency) {

    public static MoneyDto pln(long amount) {
        return new MoneyDto(amount, "PLN");
    }
}
