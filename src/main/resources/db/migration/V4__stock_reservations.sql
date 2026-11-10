-- Rezerwacje zapasu na zamówienia (order-service). Rezerwacja od razu zmniejsza offers.stock, więc stan widoczny
-- w ofercie to sztuki dostępne. Id rezerwacji nadaje order-service (pozycja zamówienia), dzięki czemu ponowione
-- żądanie rezerwacji albo zwolnienia nie zmienia zapasu drugi raz.
CREATE TABLE stock_reservations (
    id          uuid PRIMARY KEY,
    offer_id    uuid        NOT NULL REFERENCES offers (id),
    quantity    int         NOT NULL CHECK (quantity > 0),
    created_at  timestamptz NOT NULL,
    released_at timestamptz
);

CREATE INDEX stock_reservations_offer_idx ON stock_reservations (offer_id);
