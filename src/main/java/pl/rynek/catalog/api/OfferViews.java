package pl.rynek.catalog.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import pl.rynek.catalog.api.dto.MoneyDto;
import pl.rynek.catalog.api.dto.MyOfferResponse;
import pl.rynek.catalog.api.dto.OfferCardResponse;
import pl.rynek.catalog.api.dto.OfferCardResponse.RatingRef;
import pl.rynek.catalog.api.dto.OfferParamDto;
import pl.rynek.catalog.api.dto.SearchSyncPage;
import pl.rynek.catalog.api.dto.OfferPhotoDto;
import pl.rynek.catalog.api.dto.OfferResponse;
import pl.rynek.catalog.api.dto.OfferResponse.ServiceDetailsResponse;
import pl.rynek.catalog.booking.BookingClient;
import pl.rynek.catalog.categories.CategoryService;
import pl.rynek.catalog.channel.ChannelClient;
import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.engagement.LikeClient;
import pl.rynek.catalog.engagement.QuestionCountClient;
import pl.rynek.catalog.engagement.RatingClient;
import pl.rynek.catalog.offers.Offer;
import pl.rynek.catalog.offers.Offer.Photo;
import pl.rynek.catalog.offers.OfferStatus;
import pl.rynek.catalog.offers.OfferType;

/** Składa odpowiedzi z oferty i danych kanału (jedno zapytanie do channel-service na całą listę). */
@Component
class OfferViews {

    private final ChannelClient channels;
    private final RatingClient ratings;
    private final QuestionCountClient questionCounts;
    private final LikeClient likes;
    private final CategoryService categories;
    private final BookingClient booking;

    OfferViews(ChannelClient channels, RatingClient ratings, QuestionCountClient questionCounts, LikeClient likes, CategoryService categories,
               BookingClient booking) {
        this.booking = booking;
        this.channels = channels;
        this.ratings = ratings;
        this.questionCounts = questionCounts;
        this.likes = likes;
        this.categories = categories;
    }

    OfferResponse offer(Offer offer) {
        return offer(offer, Optional.empty());
    }

    /** Z oglądającym karta oferty niesie też {@code liked} (czy polubił); dla gościa pole jest puste. */
    OfferResponse offer(Offer offer, Optional<UUID> viewer) {
        ChannelRef channel = channelsOf(List.of(offer)).getOrDefault(offer.getChannelId(), missingChannel(offer));
        OfferCardResponse card = card(offer, channel, ratingsOf(List.of(offer)).get(offer.getChannelId()), nextSlotsOf(List.of(offer)));
        ServiceDetailsResponse service = offer.hasServiceDetails()
                ? new ServiceDetailsResponse(MoneyDto.pln(offer.getPriceAmount()), offer.getServiceUnit(), offer.getServiceArea(),
                offer.getServiceDurationMin(), offer.getServiceDepositPercent())
                : null;
        return new OfferResponse(card.id(), card.type(), card.title(), card.price(), card.priceUnit(), card.condition(),
                card.thumbnailUrl(), card.tone(), card.placeholder(), card.videoDurationSec(), card.channel(), card.rating(),
                card.location(), card.nextSlot(), offer.getStatus(), offer.getDescription(), offer.getCategoryId(),
                offer.getCategoryId() == null ? List.of() : categories.path(offer.getCategoryId()),
                offer.getParams().stream().map(p -> new OfferParamDto(p.label(), p.value())).toList(), List.of(),
                offer.getStock(), offer.getPhotos().stream().map(Photo::url).toList(),
                offer.getPhotos().stream().map(p -> new OfferPhotoDto(p.mediaId(), p.url(), p.altText())).toList(), offer.getVideoId(), null, service, questionCounts.of(offer.getId()),
                viewer.map(user -> likes.liked(offer.getId(), user)).orElse(null), offer.getCreatedAt());
    }

    /**
     * Oferty dla indeksu wyszukiwarki, w kolejności wejściowej. Opublikowane dostają pełną kartę (kanał, ocena i termin pobrane
     * wsadowo, jak na liście), pozostałe tylko „nagrobek”.
     */
    List<SearchSyncPage.SearchSyncItem> searchItems(List<Offer> offers) {
        List<Offer> live = offers.stream().filter(o -> o.getStatus() == OfferStatus.PUBLISHED && !o.isDeleted()).toList();
        Map<UUID, OfferCardResponse> cards = cards(live).stream().collect(java.util.stream.Collectors.toMap(OfferCardResponse::id, c -> c));
        return offers.stream().map(o -> {
            OfferCardResponse card = cards.get(o.getId());
            if (card == null) {
                return SearchSyncPage.SearchSyncItem.tombstone(o.getId(), o.getUpdatedAt());
            }
            boolean available = o.getType() == OfferType.SERVICE || o.getStock() == null || o.getStock() > 0;
            return new SearchSyncPage.SearchSyncItem(o.getId(), true, o.getUpdatedAt(), card, o.getDescription(), o.getCategoryId(),
                    o.getCategoryId() == null ? List.of() : categories.path(o.getCategoryId()),
                    o.getCategoryId() == null ? List.of() : categories.pathIds(o.getCategoryId()), o.getVideoId() != null, available,
                    o.getCreatedAt(), o.getPublishedAt(), o.getParams().stream().map(p -> new OfferParamDto(p.label(), p.value())).toList());
        }).toList();
    }

    List<OfferCardResponse> cards(List<Offer> offers) {
        Map<UUID, ChannelRef> found = channelsOf(offers);
        Map<UUID, RatingClient.Rating> rated = ratingsOf(offers);
        Map<UUID, Instant> slots = nextSlotsOf(offers);
        return offers.stream().map(o -> card(o, found.getOrDefault(o.getChannelId(), missingChannel(o)), rated.get(o.getChannelId()), slots)).toList();
    }

    static MyOfferResponse mine(Offer offer) {
        return new MyOfferResponse(offer.getId(), offer.getTitle().isBlank() ? "Szkic bez tytułu" : offer.getTitle(),
                offer.getStatus(), MoneyDto.pln(offer.getPriceAmount()), offer.getServiceUnit(), offer.getStock(), 0,
                offer.getType(), offer.getPhotos().isEmpty() ? null : offer.getPhotos().getFirst().url(), tone(offer));
    }

    private OfferCardResponse card(Offer offer, ChannelRef channel, RatingClient.Rating rating, Map<UUID, Instant> nextSlots) {
        List<Photo> photos = offer.getPhotos();
        return new OfferCardResponse(offer.getId(), offer.getType(), offer.getTitle(), MoneyDto.pln(offer.getPriceAmount()),
                offer.getServiceUnit(), offer.getCondition(), photos.isEmpty() ? null : photos.getFirst().url(), tone(offer),
                offer.getTitle().isBlank() ? "Nowa oferta" : offer.getTitle(), null, channel,
                rating == null ? RatingRef.NONE : new RatingRef(rating.average(), rating.count()),
                offer.getLocation(), nextSlots.get(offer.getId()));
    }

    /** Najbliższe terminy usług z listy jednym zapytaniem do booking-service; produkty nie pytają. */
    private Map<UUID, Instant> nextSlotsOf(Collection<Offer> offers) {
        return booking.nextSlots(offers.stream().filter(o -> o.getType() == OfferType.SERVICE).map(Offer::getId).toList());
    }

    private Map<UUID, ChannelRef> channelsOf(Collection<Offer> offers) {
        return channels.batch(offers.stream().map(Offer::getChannelId).toList());
    }

    private Map<UUID, RatingClient.Rating> ratingsOf(Collection<Offer> offers) {
        return ratings.batch(offers.stream().map(Offer::getChannelId).toList());
    }

    /** Kolor zastępczej miniatury (media-1…4) stały dla oferty. */
    static int tone(Offer offer) {
        return Math.floorMod(offer.getId().hashCode(), 4) + 1;
    }

    private static ChannelRef missingChannel(Offer offer) {
        return new ChannelRef(offer.getChannelId(), "", "Kanał niedostępny", null, false);
    }
}
