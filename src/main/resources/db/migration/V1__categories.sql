-- Drzewo kategorii (dwa poziomy). Id to czytelny slug, bo trafia do adresów i filtrów wyszukiwarki.
CREATE TABLE categories (
    id        varchar(60) PRIMARY KEY,
    parent_id varchar(60) REFERENCES categories (id),
    name      varchar(80) NOT NULL,
    position  int         NOT NULL
);

-- Parametry w formularzu oferty. Podkategoria dziedziczy atrybuty kategorii nadrzędnej.
-- Wartości ofert zapisują się pod etykietą (label), tak jak wysyła je kreator.
CREATE TABLE category_attributes (
    category_id varchar(60) NOT NULL REFERENCES categories (id),
    key         varchar(40) NOT NULL,
    label       varchar(40) NOT NULL,
    type        varchar(10) NOT NULL CHECK (type IN ('text', 'number', 'select')),
    options     text[],
    required    boolean     NOT NULL DEFAULT false,
    position    int         NOT NULL,
    PRIMARY KEY (category_id, key)
);

INSERT INTO categories (id, parent_id, name, position) VALUES
    ('elektronika', NULL, 'Elektronika', 1),
    ('elektronika-telefony', 'elektronika', 'Telefony', 1),
    ('elektronika-komputery', 'elektronika', 'Laptopy i komputery', 2),
    ('elektronika-rtv', 'elektronika', 'RTV i audio', 3),
    ('elektronika-foto', 'elektronika', 'Fotografia', 4),
    ('elektronika-konsole', 'elektronika', 'Konsole i gry', 5),
    ('dom-ogrod', NULL, 'Dom i ogród', 2),
    ('dom-ogrod-meble', 'dom-ogrod', 'Meble', 1),
    ('dom-ogrod-agd', 'dom-ogrod', 'AGD', 2),
    ('dom-ogrod-narzedzia', 'dom-ogrod', 'Narzędzia', 3),
    ('dom-ogrod-ogrod', 'dom-ogrod', 'Ogród', 4),
    ('motoryzacja', NULL, 'Motoryzacja', 3),
    ('motoryzacja-czesci', 'motoryzacja', 'Części samochodowe', 1),
    ('motoryzacja-opony', 'motoryzacja', 'Opony i felgi', 2),
    ('moda', NULL, 'Moda', 4),
    ('moda-damska', 'moda', 'Odzież damska', 1),
    ('moda-meska', 'moda', 'Odzież męska', 2),
    ('moda-obuwie', 'moda', 'Obuwie', 3),
    ('sport', NULL, 'Sport', 5),
    ('sport-rowery', 'sport', 'Rowery', 1),
    ('sport-fitness', 'sport', 'Fitness i siłownia', 2),
    ('dzieci', NULL, 'Dla dzieci', 6),
    ('dzieci-zabawki', 'dzieci', 'Zabawki', 1),
    ('dzieci-wozki', 'dzieci', 'Wózki i foteliki', 2),
    ('remonty', NULL, 'Remonty i wykończenia', 7),
    ('remonty-malowanie', 'remonty', 'Malowanie', 1),
    ('remonty-hydraulika', 'remonty', 'Hydraulika', 2),
    ('remonty-elektryka', 'remonty', 'Elektryka', 3),
    ('sprzatanie', NULL, 'Sprzątanie', 8),
    ('uslugi-it', NULL, 'Usługi IT', 9),
    ('uslugi-it-naprawa', 'uslugi-it', 'Naprawa komputerów', 1),
    ('uslugi-it-strony', 'uslugi-it', 'Strony internetowe', 2),
    ('zdrowie-uroda', NULL, 'Zdrowie i uroda', 10);

INSERT INTO category_attributes (category_id, key, label, type, options, required, position) VALUES
    ('elektronika', 'brand', 'Marka', 'text', NULL, true, 1),
    ('elektronika', 'model', 'Model', 'text', NULL, false, 2),
    ('elektronika', 'warranty', 'Gwarancja', 'select', ARRAY['Brak', '12 miesięcy', '24 miesiące'], false, 3),
    ('elektronika-telefony', 'storage', 'Pamięć', 'select', ARRAY['64 GB', '128 GB', '256 GB', '512 GB', '1 TB'], false, 1),
    ('elektronika-komputery', 'ram', 'Pamięć RAM', 'select', ARRAY['8 GB', '16 GB', '32 GB', '64 GB'], false, 1),
    ('elektronika-komputery', 'screen', 'Przekątna ekranu (cale)', 'number', NULL, false, 2),
    ('dom-ogrod-agd', 'brand', 'Marka', 'text', NULL, true, 1),
    ('dom-ogrod-narzedzia', 'brand', 'Marka', 'text', NULL, true, 1),
    ('dom-ogrod-narzedzia', 'power', 'Zasilanie', 'select', ARRAY['Sieciowe', 'Akumulatorowe', 'Spalinowe', 'Ręczne'], false, 2),
    ('motoryzacja-czesci', 'make', 'Marka pojazdu', 'text', NULL, true, 1),
    ('motoryzacja-opony', 'size', 'Rozmiar', 'text', NULL, true, 1),
    ('motoryzacja-opony', 'season', 'Sezon', 'select', ARRAY['Letnie', 'Zimowe', 'Całoroczne'], false, 2),
    ('moda-damska', 'size', 'Rozmiar', 'select', ARRAY['XS', 'S', 'M', 'L', 'XL', 'XXL'], true, 1),
    ('moda-meska', 'size', 'Rozmiar', 'select', ARRAY['XS', 'S', 'M', 'L', 'XL', 'XXL'], true, 1),
    ('moda-obuwie', 'size', 'Rozmiar', 'number', NULL, true, 1),
    ('sport-rowery', 'frame', 'Rozmiar ramy', 'select', ARRAY['XS', 'S', 'M', 'L', 'XL'], false, 1),
    ('sport-rowery', 'wheel', 'Rozmiar koła (cale)', 'select', ARRAY['20', '24', '26', '27,5', '28', '29'], false, 2);
