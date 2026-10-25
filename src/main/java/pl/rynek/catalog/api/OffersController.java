package pl.rynek.catalog.api;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.api.dto.CreateOfferRequest;
import pl.rynek.catalog.api.dto.CursorPage;
import pl.rynek.catalog.api.dto.MyOfferResponse;
import pl.rynek.catalog.api.dto.OfferCardResponse;
import pl.rynek.catalog.api.dto.OfferResponse;
import pl.rynek.catalog.api.dto.SetMediaRequest;
import pl.rynek.catalog.api.dto.SetServiceDetailsRequest;
import pl.rynek.catalog.api.dto.UpdateOfferRequest;
import pl.rynek.catalog.api.dto.UpdateStockRequest;
import pl.rynek.catalog.config.Viewer;
import pl.rynek.catalog.offers.Offer;
import pl.rynek.catalog.offers.OfferService;
import pl.rynek.catalog.offers.OfferStatus;
import pl.rynek.catalog.offers.QualityScore;

@RestController
public class OffersController {

    /** Tyle kart mieści feed lub koszyk z zapasem. */
    private static final int MAX_BATCH = 50;

    private final OfferService offers;
    private final OfferViews views;
    private final Viewer viewer;

    public OffersController(OfferService offers, OfferViews views, Viewer viewer) {
        this.offers = offers;
        this.views = views;
        this.viewer = viewer;
    }

    /**
     * Utworzenie szkicu oferty (type: PRODUCT / SERVICE). Dostęp: seller.
     */
    @PostMapping("/api/v1/offers")
    public ResponseEntity<OfferResponse> createOffer(@AuthenticationPrincipal Jwt token, @Valid @RequestBody CreateOfferRequest request) {
        Offer offer = offers.create(userId(token), request);
        return ResponseEntity.created(URI.create("/api/v1/offers/" + offer.getId())).body(views.offer(offer));
    }

    /**
     * Karta oferty (cena, opis, parametry, sprzedawca, media, dostawa). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers/{offerId}")
    public OfferResponse getOffer(@PathVariable String offerId, HttpServletRequest http) {
        return views.offer(offers.visible(offerId, viewer.of(http)));
    }

    /**
     * Zapis zmian / autozapis szkicu. Dostęp: seller.
     */
    @PatchMapping("/api/v1/offers/{offerId}")
    public OfferResponse updateOffer(@AuthenticationPrincipal Jwt token, @PathVariable String offerId,
                                     @Valid @RequestBody UpdateOfferRequest request) {
        return views.offer(offers.update(userId(token), offerId, request));
    }

    /**
     * Kolejność mediów: zdjęcia (mediaId) + film (videoId). Dostęp: seller.
     */
    @PutMapping("/api/v1/offers/{offerId}/media")
    public OfferResponse setMedia(@AuthenticationPrincipal Jwt token, @PathVariable String offerId,
                                  @Valid @RequestBody SetMediaRequest request) {
        return views.offer(offers.setMedia(userId(token), offerId, request));
    }

    /**
     * Dla usług: cennik („od…”, jednostka), obszar działania, czas trwania, zaliczka. Dostęp: seller.
     */
    @PutMapping("/api/v1/offers/{offerId}/service-details")
    public OfferResponse setServiceDetails(@AuthenticationPrincipal Jwt token, @PathVariable String offerId,
                                           @Valid @RequestBody SetServiceDetailsRequest request) {
        return views.offer(offers.setServiceDetails(userId(token), offerId, request));
    }

    /**
     * Stan magazynowy (produkty). Dostęp: seller.
     */
    @PatchMapping("/api/v1/offers/{offerId}/stock")
    public ResponseEntity<Void> updateStock(@AuthenticationPrincipal Jwt token, @PathVariable String offerId,
                                            @Valid @RequestBody UpdateStockRequest request) {
        offers.setStock(userId(token), offerId, request.stock());
        return ResponseEntity.noContent().build();
    }

    /**
     * Wskaźnik jakości oferty (podpowiedzi z kreatora). Dostęp: seller.
     */
    @GetMapping("/api/v1/offers/{offerId}/quality-score")
    public QualityScore getQualityScore(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        return offers.qualityScore(userId(token), offerId);
    }

    /**
     * Publikacja (walidacja kompletności). Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/publish")
    public OfferResponse publish(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        return views.offer(offers.publish(userId(token), offerId));
    }

    /**
     * Wstrzymanie oferty. Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/unpublish")
    public OfferResponse unpublish(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        return views.offer(offers.unpublish(userId(token), offerId));
    }

    /**
     * Duplikuj ofertę. Dostęp: seller.
     */
    @PostMapping("/api/v1/offers/{offerId}/duplicate")
    public ResponseEntity<OfferResponse> duplicate(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        Offer copy = offers.duplicate(userId(token), offerId);
        return ResponseEntity.created(URI.create("/api/v1/offers/" + copy.getId())).body(views.offer(copy));
    }

    /**
     * Usunięcie. Dostęp: seller.
     */
    @DeleteMapping("/api/v1/offers/{offerId}")
    public ResponseEntity<Void> deleteOffer(@AuthenticationPrincipal Jwt token, @PathVariable String offerId) {
        offers.delete(userId(token), offerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Moje oferty (panel sprzedawcy), filtr statusu. Dostęp: seller.
     */
    @GetMapping("/api/v1/offers/mine")
    public CursorPage<MyOfferResponse> listMyOffers(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer limit) {
        OfferStatus filter = parseStatus(status);
        int offset = Cursors.offset(cursor);
        return Cursors.page(offset, Cursors.limit(limit), n -> offers.mine(userId(token), filter, offset, n),
                list -> list.stream().map(OfferViews::mine).toList());
    }

    /**
     * Oferty kanału (zakładka „Oferty”). Dostęp: pub.
     */
    @GetMapping("/api/v1/offers")
    public CursorPage<OfferCardResponse> listOffers(@RequestParam(required = false) String channelId,
                                                    @RequestParam(required = false) String sort,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer limit) {
        UUID channel = uuidOrNull(channelId);
        if (channel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj kanał (channelId).");
        }
        int offset = Cursors.offset(cursor);
        return Cursors.page(offset, Cursors.limit(limit), n -> offers.ofChannel(channel, sort, offset, n), views::cards);
    }

    /**
     * Wsadowe pobranie kart (feed, koszyk, czat). Dostęp: pub. {@code ids} rozdzielone przecinkami; nieznane, usunięte
     * i szkice są pomijane, kolejność wyniku jak w {@code ids}.
     */
    @GetMapping("/api/v1/offers/batch")
    public List<OfferCardResponse> getOffersBatch(@RequestParam(required = false) String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        List<UUID> requested = Arrays.stream(ids.split(",")).map(String::trim).map(OffersController::uuidOrNull)
                .filter(Objects::nonNull).distinct().toList();
        if (requested.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Najwyżej " + MAX_BATCH + " ofert w jednym zapytaniu.");
        }
        Map<UUID, Offer> found = offers.batch(requested).stream().collect(Collectors.toMap(Offer::getId, Function.identity()));
        return views.cards(requested.stream().filter(found::containsKey).map(found::get).toList());
    }

    private static OfferStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OfferStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznany status. Wybierz DRAFT, PUBLISHED albo PAUSED.");
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID userId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
