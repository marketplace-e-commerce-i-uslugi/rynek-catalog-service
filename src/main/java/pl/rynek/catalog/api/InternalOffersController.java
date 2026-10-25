package pl.rynek.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.catalog.api.dto.MoneyDto;
import pl.rynek.catalog.api.dto.PriceAndStockResponse;
import pl.rynek.catalog.api.dto.ReleaseStockRequest;
import pl.rynek.catalog.api.dto.ReserveStockRequest;
import pl.rynek.catalog.offers.Offer;
import pl.rynek.catalog.offers.OfferService;
import pl.rynek.catalog.offers.OfferType;

import static pl.rynek.catalog.common.NotImplemented.notImplemented;

@RestController
public class InternalOffersController {

    private final OfferService offers;

    public InternalOffersController(OfferService offers) {
        this.offers = offers;
    }

    /**
     * Aktualna cena i dostępność dla koszyka/zamówienia. Dostęp: int.
     */
    @GetMapping("/internal/offers/{offerId}/price-and-stock")
    public PriceAndStockResponse getPriceAndStock(@PathVariable String offerId) {
        Offer offer = offers.active(offerId);
        boolean available = offer.isPublished() && (offer.getType() == OfferType.SERVICE || offer.getStock() > 0);
        return new PriceAndStockResponse(offer.getId(), offer.getType(), MoneyDto.pln(offer.getPriceAmount()), offer.getStock(), available);
    }

    /**
     * Rezerwacja zapasu na czas płatności. Dostęp: int. Czeka na order-service (kontrakt rezerwacji i jej wygasania).
     */
    @PostMapping("/internal/offers/{offerId}/stock/reserve")
    public void reserveStock(@PathVariable String offerId, @RequestBody ReserveStockRequest request) {
        throw notImplemented();
    }

    /**
     * Zwolnienie rezerwacji. Dostęp: int.
     */
    @PostMapping("/internal/offers/{offerId}/stock/release")
    public void releaseStock(@PathVariable String offerId, @RequestBody ReleaseStockRequest request) {
        throw notImplemented();
    }
}
