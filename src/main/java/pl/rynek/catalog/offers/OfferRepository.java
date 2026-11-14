package pl.rynek.catalog.offers;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Wszystkie zapytania pomijają oferty usunięte ({@code deleted_at}). */
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @Query("select o from Offer o where o.id = :id and o.deletedAt is null")
    Optional<Offer> findActive(UUID id);

    @Query("select o from Offer o where o.id in :ids and o.deletedAt is null and o.status <> pl.rynek.catalog.offers.OfferStatus.DRAFT")
    List<Offer> findVisible(Collection<UUID> ids);

    @Query("select o from Offer o where o.ownerId = :ownerId and o.deletedAt is null and (:status is null or o.status = :status) order by o.updatedAt desc, o.id")
    List<Offer> findMine(UUID ownerId, OfferStatus status, Pageable page);

    @Query("select count(o) from Offer o where o.channelId = :channelId and o.deletedAt is null and o.status = pl.rynek.catalog.offers.OfferStatus.PUBLISHED")
    int countPublished(UUID channelId);

    @Query("select o from Offer o where o.channelId = :channelId and o.deletedAt is null and o.status = pl.rynek.catalog.offers.OfferStatus.PUBLISHED")
    List<Offer> findPublished(UUID channelId, Pageable page);

    @Query("select o from Offer o where o.id in :ids and o.deletedAt is null")
    List<Offer> findAllActive(Collection<UUID> ids);

    /**
     * Zmiany dla search-service od kursora {@code (since, afterId)}, od najstarszej: także oferty szkicowe, wstrzymane i usunięte,
     * bo wyszukiwarka musi je usunąć z indeksu.
     */
    @Query("select o from Offer o where o.updatedAt > :since or (o.updatedAt = :since and o.id > :afterId) order by o.updatedAt, o.id")
    List<Offer> syncPage(Instant since, UUID afterId, Pageable page);

    /**
     * Zdejmuje sztuki z zapasu opublikowanego produktu; 0 = za mało sztuk albo oferta niedostępna. Gdy zapas spada do zera,
     * {@code updated_at} się zmienia, żeby wyszukiwarka dowiedziała się, że oferta zniknęła z dostępnych (zwykła sprzedaż
     * nie przestawia kolejności listy „Moje oferty”).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update offers set stock = stock - :quantity, updated_at = case when stock - :quantity = 0 then now() else updated_at end where id = :id and deleted_at is null and status = 'PUBLISHED'"
            + " and type = 'PRODUCT' and stock >= :quantity", nativeQuery = true)
    int takeStock(UUID id, int quantity);

    /** Oddaje sztuki ze zwolnionej rezerwacji, także do oferty wstrzymanej lub usuniętej (zapas wraca do stanu sprzed zamówienia). */
    /** Zwrot przyjęty przez sprzedawcę: tylko oferty ze śledzonym zapasem (stock nie jest NULL). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update offers set stock = stock + :quantity, updated_at = case when stock = 0 then now() else updated_at end where id = :id and stock is not null", nativeQuery = true)
    int restoreTrackedStock(UUID id, int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update offers set stock = coalesce(stock, 0) + :quantity, updated_at = case when coalesce(stock, 0) = 0 then now() else updated_at end where id = :id", nativeQuery = true)
    int returnStock(UUID id, int quantity);
}
