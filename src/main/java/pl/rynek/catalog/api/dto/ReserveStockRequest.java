package pl.rynek.catalog.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code reservationId} nadaje order-service (id pozycji zamówienia), więc ponowienie żądania jest bezpieczne. */
public record ReserveStockRequest(
        @NotNull(message = "Podaj id rezerwacji.") UUID reservationId,
        @NotNull(message = "Podaj liczbę sztuk.")
        @Positive(message = "Liczba sztuk musi być większa od zera.")
        @Max(value = 999, message = "Za dużo sztuk w jednej pozycji.") Integer quantity) {
}
