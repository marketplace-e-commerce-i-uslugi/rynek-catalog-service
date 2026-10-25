package pl.rynek.catalog.offers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.api.dto.CreateOfferRequest;
import pl.rynek.catalog.api.dto.OfferParamDto;
import pl.rynek.catalog.api.dto.SetMediaRequest;
import pl.rynek.catalog.api.dto.SetServiceDetailsRequest;
import pl.rynek.catalog.api.dto.UpdateOfferRequest;
import pl.rynek.catalog.categories.CategoryService;
import pl.rynek.catalog.categories.CategoryService.CategoryAttribute;
import pl.rynek.catalog.channel.ChannelClient;
import pl.rynek.catalog.channel.ChannelClient.OwnerChannel;
import pl.rynek.catalog.media.MediaClient;
import pl.rynek.catalog.media.MediaClient.MediaFile;
import pl.rynek.catalog.offers.Offer.Param;
import pl.rynek.catalog.offers.Offer.Photo;

/**
 * Oferty sprzedawcy. Szkic może być niepełny; publikacja sprawdza komplet pól, a opublikowana oferta musi go
 * zachować przy każdej zmianie (inaczej 422 i zmiana się nie zapisuje).
 */
@Service
public class OfferService {

    static final Set<String> CONDITIONS = Set.of("Nowy", "Używany", "Poleasingowy", "Uszkodzony");
    static final int MIN_TITLE = 5;

    private final OfferRepository offers;
    private final CategoryService categories;
    private final ChannelClient channels;
    private final MediaClient media;
    private final Clock clock;

    public OfferService(OfferRepository offers, CategoryService categories, ChannelClient channels, MediaClient media, Clock clock) {
        this.offers = offers;
        this.categories = categories;
        this.channels = channels;
        this.media = media;
        this.clock = clock;
    }

    /** Oferty wystawia się z kanału, więc konto bez kanału dostaje 403 z podpowiedzią. */
    @Transactional
    public Offer create(UUID ownerId, CreateOfferRequest request) {
        OwnerChannel channel = channels.byOwner(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Najpierw załóż kanał, aby wystawiać oferty."));
        Instant now = clock.instant();
        OfferType type = request.type() == null ? OfferType.PRODUCT : request.type();
        Offer offer = new Offer(ownerId, channel.id(), type, channel.address() == null ? "" : channel.address(), now);
        apply(offer, request.title(), request.categoryId(), request.price() == null ? null : request.price().amount(),
                request.condition(), request.description(), request.params(), now);
        return offers.save(offer);
    }

    /** Opublikowaną i wstrzymaną widzi każdy (czat, koszyk), szkic tylko właściciel; pozostałym 404. */
    @Transactional(readOnly = true)
    public Offer visible(String offerId, Optional<UUID> viewer) {
        return find(offerId)
                .filter(o -> o.getStatus() != OfferStatus.DRAFT || viewer.map(o::isOwnedBy).orElse(false))
                .orElseThrow(OfferService::notFound);
    }

    @Transactional
    public Offer update(UUID userId, String offerId, UpdateOfferRequest request) {
        Offer offer = owned(userId, offerId);
        Instant now = clock.instant();
        if (request.type() != null && request.type() != offer.getType()) {
            if (offer.getStatus() != OfferStatus.DRAFT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Typ oferty można zmienić tylko w szkicu.");
            }
            offer.changeType(request.type(), now);
        }
        apply(offer, request.title(), request.categoryId(), request.price() == null ? null : request.price().amount(),
                request.condition(), request.description(), request.params(), now);
        return stillComplete(offer);
    }

    /** Każde zdjęcie musi być własnym plikiem {@code OFFER_PHOTO}; inaczej można by pokazać cudze zdjęcia. */
    @Transactional
    public Offer setMedia(UUID userId, String offerId, SetMediaRequest request) {
        Offer offer = owned(userId, offerId);
        List<Photo> photos = new ArrayList<>();
        for (UUID mediaId : request.photoMediaIds().stream().distinct().toList()) {
            MediaFile file = media.get(mediaId);
            if (!file.ownerId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Możesz dodać tylko zdjęcia, które sam wysłałeś.");
            }
            if (!"OFFER_PHOTO".equals(file.purpose())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ten plik wysłano w innym celu. Dodaj zdjęcie ponownie.");
            }
            photos.add(new Photo(mediaId, file.url()));
        }
        offer.changeMedia(photos, request.videoId(), clock.instant());
        return stillComplete(offer);
    }

    @Transactional
    public Offer setServiceDetails(UUID userId, String offerId, SetServiceDetailsRequest request) {
        Offer offer = owned(userId, offerId);
        if (offer.getType() != OfferType.SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cennik usługi można ustawić tylko w ofercie usługi.");
        }
        offer.changeServiceDetails(request.priceFrom().amount(), request.unit().trim(), request.area().trim(),
                request.durationMin(), request.depositPercent(), clock.instant());
        return stillComplete(offer);
    }

    @Transactional
    public void setStock(UUID userId, String offerId, int stock) {
        Offer offer = owned(userId, offerId);
        if (offer.getType() != OfferType.PRODUCT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stan magazynowy mają tylko produkty.");
        }
        offer.changeStock(stock, clock.instant());
    }

    @Transactional(readOnly = true)
    public QualityScore qualityScore(UUID userId, String offerId) {
        Offer offer = owned(userId, offerId);
        return QualityScore.of(offer, offer.getCategoryId() == null ? List.of() : categories.attributes(offer.getCategoryId()));
    }

    /** Publikuje szkic albo wznawia wstrzymaną ofertę; braki wracają jako jedna lista w 422. */
    @Transactional
    public Offer publish(UUID userId, String offerId) {
        Offer offer = owned(userId, offerId);
        requireComplete(offer);
        offer.publish(clock.instant());
        return offer;
    }

    @Transactional
    public Offer unpublish(UUID userId, String offerId) {
        Offer offer = owned(userId, offerId);
        if (!offer.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Wstrzymać można tylko opublikowaną ofertę.");
        }
        offer.pause(clock.instant());
        return offer;
    }

    @Transactional
    public Offer duplicate(UUID userId, String offerId) {
        Offer source = owned(userId, offerId);
        String title = source.getTitle().isBlank() ? "" : truncate(source.getTitle(), 120 - " (kopia)".length()) + " (kopia)";
        return offers.save(source.duplicate(title, clock.instant()));
    }

    @Transactional
    public void delete(UUID userId, String offerId) {
        owned(userId, offerId).delete(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Offer> mine(UUID userId, OfferStatus status, int offset, int limit) {
        return offers.findMine(userId, status, new OffsetPage(offset, limit, Sort.unsorted()));
    }

    @Transactional(readOnly = true)
    public List<Offer> ofChannel(UUID channelId, String sort, int offset, int limit) {
        Sort order = switch (sort == null ? "newest" : sort) {
            case "cheapest" -> Sort.by("priceAmount").ascending().and(Sort.by("id"));
            // Popularność wymaga danych z engagement i analytics; do tego czasu jak „najnowsze”.
            case "newest", "popular" -> Sort.by("publishedAt").descending().and(Sort.by("id"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieznane sortowanie. Wybierz newest, popular albo cheapest.");
        };
        return offers.findPublished(channelId, new OffsetPage(offset, limit, order));
    }

    @Transactional(readOnly = true)
    public List<Offer> batch(List<UUID> ids) {
        return offers.findVisible(ids);
    }

    @Transactional(readOnly = true)
    public Offer active(String offerId) {
        return find(offerId).orElseThrow(OfferService::notFound);
    }

    /** Braki uniemożliwiające publikację, w kolejności pól w kreatorze. */
    List<String> missing(Offer offer) {
        List<String> missing = new ArrayList<>();
        if (offer.getTitle().length() < MIN_TITLE) {
            missing.add("tytuł (co najmniej " + MIN_TITLE + " znaków)");
        }
        if (offer.getCategoryId() == null) {
            missing.add("kategorię");
        }
        if (offer.getType() == OfferType.PRODUCT) {
            if (offer.getPriceAmount() <= 0) {
                missing.add("cenę");
            }
            if (offer.getCondition() == null) {
                missing.add("stan");
            }
        } else if (!offer.hasServiceDetails() || offer.getPriceAmount() <= 0) {
            missing.add("cennik usługi (cena od, jednostka, obszar, czas)");
        }
        if (offer.getPhotos().isEmpty() && offer.getVideoId() == null) {
            missing.add("co najmniej jedno zdjęcie albo film");
        }
        for (CategoryAttribute attribute : requiredAttributes(offer)) {
            if (offer.getParams().stream().noneMatch(p -> p.label().equals(attribute.label()))) {
                missing.add(attribute.label());
            }
        }
        return missing;
    }

    private void apply(Offer offer, String title, String categoryId, Long price, String condition, String description,
                       List<OfferParamDto> params, Instant now) {
        if (title != null) {
            offer.changeTitle(title.trim(), now);
        }
        if (categoryId != null) {
            String category = categoryId.isBlank() ? null : categoryId.trim();
            if (category != null && !categories.exists(category)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz kategorię z listy.");
            }
            offer.changeCategory(category, now);
        }
        if (price != null) {
            offer.changePrice(price, now);
        }
        if (condition != null && offer.getType() == OfferType.PRODUCT) {
            if (!CONDITIONS.contains(condition)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz stan: Nowy, Używany, Poleasingowy albo Uszkodzony.");
            }
            offer.changeCondition(condition, now);
        }
        if (description != null) {
            offer.changeDescription(description.trim(), now);
        }
        if (params != null) {
            offer.changeParams(params.stream().map(p -> new Param(p.label().trim(), p.value().trim())).toList(), now);
        }
    }

    private Offer stillComplete(Offer offer) {
        if (offer.isPublished()) {
            requireComplete(offer);
        }
        return offer;
    }

    private void requireComplete(Offer offer) {
        List<String> missing = missing(offer);
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Uzupełnij przed publikacją: " + String.join(", ", missing) + ".");
        }
    }

    private List<CategoryAttribute> requiredAttributes(Offer offer) {
        return offer.getCategoryId() == null ? List.of()
                : categories.attributes(offer.getCategoryId()).stream().filter(CategoryAttribute::required).toList();
    }

    /** Oferta zalogowanego sprzedawcy; cudza daje 403 (endpointy {@code seller}), szkic innej osoby 404. */
    private Offer owned(UUID userId, String offerId) {
        Offer offer = find(offerId).orElseThrow(OfferService::notFound);
        if (!offer.isOwnedBy(userId)) {
            if (offer.getStatus() == OfferStatus.DRAFT) {
                throw notFound();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Możesz zmieniać tylko własne oferty.");
        }
        return offer;
    }

    private Optional<Offer> find(String offerId) {
        try {
            return offers.findActive(UUID.fromString(offerId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).trim();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ta oferta nie istnieje lub została usunięta.");
    }

    /** Stronicowanie od przesunięcia (kursor to zakodowany offset), a nie od numeru strony. */
    private record OffsetPage(int offset, int limit, Sort sort) implements org.springframework.data.domain.Pageable {

        @Override
        public int getPageNumber() {
            return offset / limit;
        }

        @Override
        public int getPageSize() {
            return limit;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return sort;
        }

        @Override
        public org.springframework.data.domain.Pageable next() {
            return new OffsetPage(offset + limit, limit, sort);
        }

        @Override
        public org.springframework.data.domain.Pageable previousOrFirst() {
            return new OffsetPage(Math.max(0, offset - limit), limit, sort);
        }

        @Override
        public org.springframework.data.domain.Pageable first() {
            return new OffsetPage(0, limit, sort);
        }

        @Override
        public org.springframework.data.domain.Pageable withPage(int pageNumber) {
            return new OffsetPage(pageNumber * limit, limit, sort);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
