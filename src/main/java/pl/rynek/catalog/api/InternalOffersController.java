package pl.rynek.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.catalog.api.dto.ReleaseStockRequest;
import pl.rynek.catalog.api.dto.ReserveStockRequest;

import static pl.rynek.catalog.common.NotImplemented.notImplemented;

@RestController
public class InternalOffersController {

    /**
     * Aktualna cena i dostępność dla koszyka/zamówienia. Dostęp: int.
     */
    @GetMapping("/internal/offers/{offerId}/price-and-stock")
    public void getPriceAndStock(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Rezerwacja zapasu na czas płatności. Dostęp: int.
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
