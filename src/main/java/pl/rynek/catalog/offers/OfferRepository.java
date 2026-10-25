package pl.rynek.catalog.offers;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Wszystkie zapytania pomijają oferty usunięte ({@code deleted_at}). */
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @Query("select o from Offer o where o.id = :id and o.deletedAt is null")
    Optional<Offer> findActive(UUID id);

    @Query("select o from Offer o where o.id in :ids and o.deletedAt is null and o.status <> pl.rynek.catalog.offers.OfferStatus.DRAFT")
    List<Offer> findVisible(Collection<UUID> ids);

    @Query("select o from Offer o where o.ownerId = :ownerId and o.deletedAt is null and (:status is null or o.status = :status) order by o.updatedAt desc, o.id")
    List<Offer> findMine(UUID ownerId, OfferStatus status, Pageable page);

    @Query("select o from Offer o where o.channelId = :channelId and o.deletedAt is null and o.status = pl.rynek.catalog.offers.OfferStatus.PUBLISHED")
    List<Offer> findPublished(UUID channelId, Pageable page);
}
