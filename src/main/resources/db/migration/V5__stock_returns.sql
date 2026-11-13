-- Zwrot przyjęty przez sprzedawcę oddaje sztuki z rezerwacji do zapasu; returned_at pilnuje, żeby tylko raz.
ALTER TABLE stock_reservations ADD COLUMN returned_at timestamptz;
