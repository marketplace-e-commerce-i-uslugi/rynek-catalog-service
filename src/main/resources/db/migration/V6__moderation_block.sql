-- Oferta zablokowana przez moderatora (po rozpatrzeniu zgłoszenia): wstrzymana, a sprzedawca nie może jej ponownie opublikować.
-- Odblokowanie (moderator) zostawia ofertę wstrzymaną; sprzedawca publikuje ją sam.
ALTER TABLE offers ADD COLUMN blocked boolean NOT NULL DEFAULT false;
