package pl.rynek.catalog.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code reservationId} to id pozycji zamówienia, tak jak przy rezerwacji; {@code quantity} musi zgadzać się z rezerwacją. */
public record ReturnStockRequest(
        @NotNull(message = "Podaj id rezerwacji.") UUID reservationId,
        @NotNull(message = "Podaj liczbę sztuk.")
        @Positive(message = "Liczba sztuk musi być większa od zera.") Integer quantity) {
}
