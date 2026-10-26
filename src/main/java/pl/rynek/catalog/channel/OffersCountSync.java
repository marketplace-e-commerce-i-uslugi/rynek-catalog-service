package pl.rynek.catalog.channel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.rynek.catalog.offers.ChannelOffersChanged;
import pl.rynek.catalog.offers.OfferRepository;

/**
 * Po zatwierdzeniu zmiany przelicza opublikowane oferty kanału i wysyła liczbę do channel-service. Zastępuje
 * zdarzenia {@code offer.published}/{@code offer.unpublished}, dopóki nie ma Kafki.
 */
@Component
class OffersCountSync {

    private final OfferRepository offers;
    private final ChannelClient channels;

    OffersCountSync(OfferRepository offers, ChannelClient channels) {
        this.offers = offers;
        this.channels = channels;
    }

    @TransactionalEventListener
    void on(ChannelOffersChanged event) {
        channels.setOffersCount(event.channelId(), offers.countPublished(event.channelId()));
    }
}
