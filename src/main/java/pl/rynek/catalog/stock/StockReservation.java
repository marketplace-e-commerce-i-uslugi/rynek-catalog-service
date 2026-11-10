package pl.rynek.catalog.stock;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Sztuki zdjęte z zapasu oferty na rzecz pozycji zamówienia. Zwolniona rezerwacja oddaje je do zapasu. */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    private UUID id;

    @Column(name = "offer_id")
    private UUID offerId;

    private int quantity;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected StockReservation() {
    }

    public StockReservation(UUID id, UUID offerId, int quantity, Instant createdAt) {
        this.id = id;
        this.offerId = offerId;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isReleased() {
        return releasedAt != null;
    }

    public void release(Instant now) {
        this.releasedAt = now;
    }
}
