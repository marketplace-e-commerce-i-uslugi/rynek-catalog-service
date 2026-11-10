package pl.rynek.catalog.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReleaseStockRequest(@NotNull(message = "Podaj id rezerwacji.") UUID reservationId) {
}
