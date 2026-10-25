package pl.rynek.catalog.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import pl.rynek.catalog.api.dto.MoneyDto;
import pl.rynek.catalog.api.dto.MyOfferResponse;
import pl.rynek.catalog.api.dto.OfferCardResponse;
import pl.rynek.catalog.api.dto.OfferCardResponse.RatingRef;
import pl.rynek.catalog.api.dto.OfferParamDto;
import pl.rynek.catalog.api.dto.OfferResponse;
import pl.rynek.catalog.api.dto.OfferResponse.ServiceDetailsResponse;
import pl.rynek.catalog.categories.CategoryService;
import pl.rynek.catalog.channel.ChannelClient;
import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.offers.Offer;
import pl.rynek.catalog.offers.Offer.Photo;

/** Składa odpowiedzi z oferty i danych kanału (jedno zapytanie do channel-service na całą listę). */
@Component
class OfferViews {

    private final ChannelClient channels;
    private final CategoryService categories;

    OfferViews(ChannelClient channels, CategoryService categories) {
        this.channels = channels;
        this.categories = categories;
    }

    OfferResponse offer(Offer offer) {
        ChannelRef channel = channelsOf(List.of(offer)).getOrDefault(offer.getChannelId(), missingChannel(offer));
        OfferCardResponse card = card(offer, channel);
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
                offer.getPhotos().stream().map(Photo::mediaId).toList(), offer.getVideoId(), null, service, 0,
                offer.getCreatedAt());
    }

    List<OfferCardResponse> cards(List<Offer> offers) {
        Map<UUID, ChannelRef> found = channelsOf(offers);
        return offers.stream().map(o -> card(o, found.getOrDefault(o.getChannelId(), missingChannel(o)))).toList();
    }

    static MyOfferResponse mine(Offer offer) {
        return new MyOfferResponse(offer.getId(), offer.getTitle().isBlank() ? "Szkic bez tytułu" : offer.getTitle(),
                offer.getStatus(), MoneyDto.pln(offer.getPriceAmount()), offer.getStock(), 0, offer.getType(), tone(offer));
    }

    private OfferCardResponse card(Offer offer, ChannelRef channel) {
        List<Photo> photos = offer.getPhotos();
        return new OfferCardResponse(offer.getId(), offer.getType(), offer.getTitle(), MoneyDto.pln(offer.getPriceAmount()),
                offer.getServiceUnit(), offer.getCondition(), photos.isEmpty() ? null : photos.getFirst().url(), tone(offer),
                offer.getTitle().isBlank() ? "Nowa oferta" : offer.getTitle(), null, channel, RatingRef.NONE,
                offer.getLocation(), null);
    }

    private Map<UUID, ChannelRef> channelsOf(Collection<Offer> offers) {
        return channels.batch(offers.stream().map(Offer::getChannelId).toList());
    }

    /** Kolor zastępczej miniatury (media-1…4) stały dla oferty. */
    private static int tone(Offer offer) {
        return Math.floorMod(offer.getId().hashCode(), 4) + 1;
    }

    private static ChannelRef missingChannel(Offer offer) {
        return new ChannelRef(offer.getChannelId(), "", "Kanał niedostępny", null, false);
    }
}
