package pl.rynek.catalog.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.catalog.api.dto.CreateOfferRequest;
import pl.rynek.catalog.api.dto.SetMediaRequest;
import pl.rynek.catalog.api.dto.SetServiceDetailsRequest;
import pl.rynek.catalog.api.dto.UpdateOfferRequest;
import pl.rynek.catalog.api.dto.UpdateStockRequest;

import static pl.rynek.catalog.common.NotImplemented.notImplemented;

@RestController
public class OffersController {

    /**
     * Utworzenie szkicu oferty (type: PRODUCT / SERVICE). Dostęp: seller.
     */
    @PostMapping("/api/v1/offers")
    public void createOffer(@RequestBody CreateOfferRequest request) {
        throw notImplemented();
    }

    /**
     * Karta oferty (cena, opis, parametry, sprzedawca, media, dostawa). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/{offerId}")
    public void getOffer(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Zapis zmian / autozapis szkicu. Dostęp: seller.
     */
    @PatchMapping("/api/v1/offers/{offerId}")
    public void updateOffer(@PathVariable String offerId, @RequestBody UpdateOfferRequest request) {
        throw notImplemented();
    }

    /**
     * Kolejność mediów: zdjęcia (mediaId) + film (videoId). Dostęp: seller.
     */
    @PutMapping("/api/v1/offers/{offerId}/media")
    public void setMedia(@PathVariable String offerId, @RequestBody SetMediaRequest request) {
        throw notImplemented();
    }

    /**
     * Dla usług: cennik („od…”, jednostka), obszar działania, czas trwania, zaliczka. Dostęp: seller.
     */
    @PutMapping("/api/v1/offers/{offerId}/service-details")
    public void setServiceDetails(@PathVariable String offerId, @RequestBody SetServiceDetailsRequest request) {
        throw notImplemented();
    }

    /**
     * Stan magazynowy (produkty). Dostęp: seller.
     */
    @PatchMapping("/api/v1/offers/{offerId}/stock")
    public void updateStock(@PathVariable String offerId, @RequestBody UpdateStockRequest request) {
        throw notImplemented();
    }

    /**
     * Wskaźnik jakości oferty (podpowiedzi z kreatora). Dostęp: seller.
     */
    @GetMapping("/api/v1/offers/{offerId}/quality-score")
    public void getQualityScore(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Publikacja (walidacja kompletności). Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/publish")
    public void publish(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Wstrzymanie oferty. Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/unpublish")
    public void unpublish(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Duplikuj ofertę. Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/duplicate")
    public void duplicate(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Usunięcie. Dostęp: seller.
     */
    @DeleteMapping("/api/v1/offers/{offerId}")
    public void deleteOffer(@PathVariable String offerId) {
        throw notImplemented();
    }

    /**
     * Moje oferty (panel sprzedawcy), filtr statusu. Dostęp: seller.
     */
    @GetMapping("/api/v1/offers/mine")
    public void listMyOffers(@RequestParam(required = false) String status) {
        throw notImplemented();
    }

    /**
     * Oferty kanału (zakładka „Oferty”). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers")
    public void listOffers(@RequestParam(required = false) String channelId, @RequestParam(required = false) String sort) {
        throw notImplemented();
    }

    /**
     * Wsadowe pobranie kart (feed, koszyk, czat). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/batch")
    public void getOffersBatch(@RequestParam(required = false) String ids) {
        throw notImplemented();
    }
}
