package pl.rynek.catalog.stock;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.offers.OfferRepository;

/**
 * Rezerwacja zapasu dla order-service. Zapas zmniejsza jedno warunkowe UPDATE ({@code stock >= quantity}), więc dwa
 * równoległe zamówienia ostatniej sztuki nie zejdą poniżej zera: jedno dostaje 409.
 */
@Service
public class StockService {

    private final StockReservationRepository reservations;
    private final OfferRepository offers;
    private final Clock clock;

    public StockService(StockReservationRepository reservations, OfferRepository offers, Clock clock) {
        this.reservations = reservations;
        this.offers = offers;
        this.clock = clock;
    }

    /**
     * Ponowienie z tym samym id rezerwacji niczego nie zmienia (order-service może ponawiać po błędzie sieci).
     * Dwa równoległe ponowienia rozstrzyga klucz główny: drugie wycofuje swoją transakcję razem ze zdjęciem zapasu.
     */
    @Transactional
    public void reserve(UUID offerId, UUID reservationId, int quantity) {
        Optional<StockReservation> existing = reservations.findById(reservationId);
        if (existing.isPresent()) {
            requireSame(existing.get(), offerId, quantity);
            return;
        }
        if (offers.findActive(offerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej oferty.");
        }
        if (offers.takeStock(offerId, quantity) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Brak wystarczającej liczby sztuk albo oferta nie jest już dostępna.");
        }
        try {
            reservations.saveAndFlush(new StockReservation(reservationId, offerId, quantity, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ta rezerwacja jest właśnie zapisywana. Spróbuj ponownie.");
        }
    }

    /** Nieznana albo już zwolniona rezerwacja to sukces: skutek (zapas oddany) jest ten sam. */
    @Transactional
    public void release(UUID offerId, UUID reservationId) {
        reservations.findForUpdate(reservationId)
                .filter(r -> r.getOfferId().equals(offerId) && !r.isReleased() && !r.isReturned())
                .ifPresent(r -> {
                    // Najpierw oznaczenie: returnStock czyści kontekst JPA, ale wcześniej zapisuje (flush) zmienione encje.
                    r.release(clock.instant());
                    offers.returnStock(offerId, r.getQuantity());
                });
    }

    /**
     * Przywrócenie sztuk po przyjętym zwrocie (rezerwacja ze zrealizowanego zamówienia nie była zwolniona). Raz: ponowienie,
     * rezerwacja już zwolniona albo już zwrócona to sukces bez zmiany zapasu. Oferta bez śledzenia zapasu nie zmienia stanu.
     * Nieznana rezerwacja to 404, inna oferta lub ilość to 409.
     */
    @Transactional
    public void restore(UUID offerId, UUID reservationId, int quantity) {
        StockReservation reservation = reservations.findForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej rezerwacji."));
        if (!reservation.getOfferId().equals(offerId) || reservation.getQuantity() != quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rezerwacja o tym id dotyczy innej oferty lub ilości.");
        }
        if (reservation.isReleased() || reservation.isReturned()) {
            return;
        }
        // Najpierw oznaczenie: restoreTrackedStock czyści kontekst JPA, ale wcześniej zapisuje (flush) zmienione encje.
        reservation.markReturned(clock.instant());
        offers.restoreTrackedStock(offerId, quantity);
    }

    private static void requireSame(StockReservation reservation, UUID offerId, int quantity) {
        if (!reservation.getOfferId().equals(offerId) || reservation.getQuantity() != quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rezerwacja o tym id dotyczy innej oferty lub ilości.");
        }
        if (reservation.isReleased()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ta rezerwacja została już zwolniona.");
        }
    }
}
