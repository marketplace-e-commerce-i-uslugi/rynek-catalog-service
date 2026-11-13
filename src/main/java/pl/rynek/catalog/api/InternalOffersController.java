package pl.rynek.catalog.api;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.api.dto.MoneyDto;
import pl.rynek.catalog.api.dto.PriceAndStockResponse;
import pl.rynek.catalog.api.dto.ReleaseStockRequest;
import pl.rynek.catalog.api.dto.ReserveStockRequest;
import pl.rynek.catalog.api.dto.ReturnStockRequest;
import pl.rynek.catalog.offers.Offer;
import pl.rynek.catalog.offers.OfferRepository;
import pl.rynek.catalog.offers.OfferService;
import pl.rynek.catalog.offers.OfferType;
import pl.rynek.catalog.stock.StockService;

@RestController
public class InternalOffersController {

    private static final int MAX_BATCH = 100;

    private final OfferService offers;
    private final OfferRepository repository;
    private final StockService stock;

    public InternalOffersController(OfferService offers, OfferRepository repository, StockService stock) {
        this.offers = offers;
        this.repository = repository;
        this.stock = stock;
    }

    /**
     * Aktualna cena i dostępność dla koszyka/zamówienia. Dostęp: int.
     */
    @GetMapping("/internal/offers/{offerId}/price-and-stock")
    public PriceAndStockResponse getPriceAndStock(@PathVariable String offerId) {
        return priceAndStock(offers.active(offerId));
    }

    /**
     * To samo dla wielu ofert naraz (koszyk). Usunięte i nieznane id nie występują w odpowiedzi. Dostęp: int.
     */
    @GetMapping("/internal/offers/price-and-stock")
    public List<PriceAndStockResponse> getPriceAndStockBatch(@RequestParam String ids) {
        List<UUID> parsed;
        try {
            parsed = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).distinct().toList();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowe id oferty.");
        }
        if (parsed.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Najwyżej " + MAX_BATCH + " ofert w jednym żądaniu.");
        }
        return parsed.isEmpty() ? List.of() : repository.findAllActive(parsed).stream().map(InternalOffersController::priceAndStock).toList();
    }

    /**
     * Rezerwacja zapasu na czas płatności: zdejmuje sztuki od razu, 409 gdy ich brakuje. Dostęp: int.
     */
    @PostMapping("/internal/offers/{offerId}/stock/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveStock(@PathVariable UUID offerId, @Valid @RequestBody ReserveStockRequest request) {
        stock.reserve(offerId, request.reservationId(), request.quantity());
    }

    /**
     * Zwolnienie rezerwacji (anulowanie zamówienia): sztuki wracają do zapasu. Dostęp: int.
     */
    @PostMapping("/internal/offers/{offerId}/stock/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseStock(@PathVariable UUID offerId, @Valid @RequestBody ReleaseStockRequest request) {
        stock.release(offerId, request.reservationId());
    }

    /**
     * Przywrócenie zapasu po zwrocie przyjętym przez sprzedawcę (order-service): sztuki z rezerwacji wracają raz,
     * ponowienie niczego nie zmienia, oferta bez śledzenia zapasu zostaje bez zmian. Dostęp: int.
     */
    @PostMapping("/internal/offers/{offerId}/stock/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnStock(@PathVariable UUID offerId, @Valid @RequestBody ReturnStockRequest request) {
        stock.restore(offerId, request.reservationId(), request.quantity());
    }

    private static PriceAndStockResponse priceAndStock(Offer offer) {
        boolean available = offer.isPublished() && (offer.getType() == OfferType.SERVICE || offer.getStock() > 0);
        String thumbnail = offer.getPhotos().isEmpty() ? null : offer.getPhotos().getFirst().url();
        PriceAndStockResponse.Service service = offer.getType() == OfferType.SERVICE && offer.hasServiceDetails()
                ? new PriceAndStockResponse.Service(offer.getServiceUnit(), offer.getServiceDurationMin(), offer.getServiceDepositPercent())
                : null;
        return new PriceAndStockResponse(offer.getId(), offer.getType(), MoneyDto.pln(offer.getPriceAmount()), offer.getStock(),
                available, offer.getTitle(), offer.getChannelId(), offer.getOwnerId(), thumbnail, OfferViews.tone(offer), service);
    }
}
