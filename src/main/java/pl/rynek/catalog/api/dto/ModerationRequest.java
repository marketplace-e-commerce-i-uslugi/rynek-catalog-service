package pl.rynek.catalog.api.dto;

import jakarta.validation.constraints.NotNull;

/** Blokada oferty przez moderatora ({@code true}) albo jej zdjęcie ({@code false}). */
public record ModerationRequest(@NotNull(message = "Podaj, czy zablokować ofertę (blocked).") Boolean blocked) {
}
