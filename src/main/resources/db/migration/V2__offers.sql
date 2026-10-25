-- Oferty. owner_id = sub z JWT, channel_id = kanał właściciela z channel-service (dane kanału pobierane przy odczycie).
-- Szkic może być niepełny; komplet pól sprawdza publikacja. Usunięcie jest miękkie (deleted_at), bo do oferty
-- odwołują się czaty, koszyki i zamówienia.
CREATE TABLE offers (
    id                      uuid PRIMARY KEY,
    owner_id                uuid          NOT NULL,
    channel_id              uuid          NOT NULL,
    type                    varchar(10)   NOT NULL CHECK (type IN ('PRODUCT', 'SERVICE')),
    status                  varchar(10)   NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED')),
    title                   varchar(120)  NOT NULL DEFAULT '',
    description             varchar(5000) NOT NULL DEFAULT '',
    category_id             varchar(60) REFERENCES categories (id),
    -- Kwota w groszach; dla usług cena „od…” z service_unit.
    price_amount            bigint        NOT NULL DEFAULT 0 CHECK (price_amount >= 0),
    price_currency          varchar(3)    NOT NULL DEFAULT 'PLN',
    condition               varchar(20),
    stock                   int CHECK (stock >= 0),
    location                varchar(100)  NOT NULL DEFAULT '',
    video_id                uuid,
    -- Usługi (PUT /service-details): wszystkie albo żadne.
    service_unit            varchar(20),
    service_area            varchar(100),
    service_duration_min    int,
    service_deposit_percent int,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL,
    published_at            timestamptz,
    deleted_at              timestamptz
);

CREATE INDEX offers_owner_idx ON offers (owner_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX offers_channel_idx ON offers (channel_id, published_at DESC) WHERE deleted_at IS NULL AND status = 'PUBLISHED';

-- Zdjęcia w kolejności (mediaId z media-service, przeznaczenie OFFER_PHOTO). URL publiczny jest stały, więc zapisany.
CREATE TABLE offer_photos (
    offer_id uuid         NOT NULL REFERENCES offers (id) ON DELETE CASCADE,
    position int          NOT NULL,
    media_id uuid         NOT NULL,
    url      varchar(500) NOT NULL,
    PRIMARY KEY (offer_id, position)
);

CREATE TABLE offer_params (
    offer_id uuid         NOT NULL REFERENCES offers (id) ON DELETE CASCADE,
    position int          NOT NULL,
    label    varchar(40)  NOT NULL,
    value    varchar(100) NOT NULL,
    PRIMARY KEY (offer_id, position)
);
