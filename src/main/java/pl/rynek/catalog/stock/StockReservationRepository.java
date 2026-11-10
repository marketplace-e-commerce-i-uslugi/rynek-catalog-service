package pl.rynek.catalog.stock;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /** Blokada wiersza: równoległe zwolnienia tej samej rezerwacji oddają zapas tylko raz. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id = :id")
    Optional<StockReservation> findForUpdate(UUID id);
}
