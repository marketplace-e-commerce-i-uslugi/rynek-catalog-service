package pl.rynek.catalog.offers;

import java.util.UUID;

/** Zmieniła się liczba opublikowanych ofert kanału (publikacja, wstrzymanie, usunięcie). */
public record ChannelOffersChanged(UUID channelId) {
}
