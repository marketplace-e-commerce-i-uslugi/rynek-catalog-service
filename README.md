# rynek-catalog-service

`catalog-service` — oferty (produkty i usługi), kategorie

> Status: **kategorie i oferty działają**: drzewo kategorii z atrybutami, szkic, edycja, zdjęcia z media-service, cennik usług, stan magazynowy, wskaźnik jakości, publikacja i wstrzymanie, duplikowanie, usunięcie, listy (moje, kanału, wsadowo) i `price-and-stock`. Rezerwacja i zwolnienie zapasu zwracają `501 Not Implemented`, bo czekają na order-service. Zdarzeń Kafka jeszcze nie ma.

## Uruchomienie

Wymaga PostgreSQL z `rynek-infra/local` (baza `catalog`), identity-service (JWKS), channel-service (kanał sprzedawcy, dane kanału w kartach) i media-service (zdjęcia):

```bash
docker compose -f ../rynek-infra/local/docker-compose.yml up -d
./mvnw spring-boot:run   # port 8083, zmienna PORT
./mvnw verify            # testy; Testcontainers uruchamia własny PostgreSQL, wymaga Dockera
```

| Zmienna | Domyślnie | Znaczenie |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/catalog`, `catalog`, `catalog` | Baza serwisu |
| `CHANNEL_SERVICE_URL` | `http://localhost:8082` | channel-service: `/internal/channels/...` |
| `MEDIA_SERVICE_URL` | `http://localhost:8084` | media-service: `GET /internal/media/{id}` |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | identity-service: JWKS |
| `JWT_ISSUER` | `rynek-identity` | Oczekiwany claim `iss` |

Schemat i słownik kategorii zakłada Flyway (`src/main/resources/db/migration`). Hibernate tylko sprawdza zgodność encji (`ddl-auto: validate`).

- Swagger UI: http://localhost:8083/swagger-ui.html
- Zdrowie: http://localhost:8083/actuator/health

## Zakres i endpointy

_Skopiowane z `rynek-docs/architektura/rynek-endpointy-mikroserwisy.md`. Od teraz to repozytorium jest właścicielem tej specyfikacji._

Ekrany: 4 Karta oferty, 6 Dodawanie oferty, 9 Panel (lista ofert). Serwis jest źródłem prawdy o ofercie; po zmianie publikuje zdarzenia dla wyszukiwarki i feedu.

| Metoda | Ścieżka | Opis | Dostęp |
|---|---|---|---|
| GET | `/api/v1/categories` | Drzewo kategorii (chipy, filtry) | pub |
| GET | `/api/v1/categories/{categoryId}/attributes` | Atrybuty kategorii (parametry w formularzu) | pub |
| POST | `/api/v1/offers` | Utworzenie szkicu oferty (`type`: PRODUCT / SERVICE) | seller |
| GET | `/api/v1/offers/{offerId}` | Karta oferty (cena, opis, parametry, sprzedawca, media, dostawa) | pub |
| PATCH | `/api/v1/offers/{offerId}` | Zapis zmian / autozapis szkicu | seller |
| PUT | `/api/v1/offers/{offerId}/media` | Kolejność mediów: zdjęcia (`mediaId`) + film (`videoId`) | seller |
| PUT | `/api/v1/offers/{offerId}/service-details` | Dla usług: cennik („od…”, jednostka), obszar działania, czas trwania, zaliczka | seller |
| PATCH | `/api/v1/offers/{offerId}/stock` | Stan magazynowy (produkty) | seller |
| GET | `/api/v1/offers/{offerId}/quality-score` | Wskaźnik jakości oferty (podpowiedzi z kreatora) | seller |
| POST | `/api/v1/offers/{offerId}/publish` | Publikacja (walidacja kompletności) | seller |
| POST | `/api/v1/offers/{offerId}/unpublish` | Wstrzymanie oferty | seller |
| POST | `/api/v1/offers/{offerId}/duplicate` | Duplikuj ofertę | seller |
| DELETE | `/api/v1/offers/{offerId}` | Usunięcie | seller |
| GET | `/api/v1/offers/mine?status=` | Moje oferty (panel sprzedawcy), filtr statusu | seller |
| GET | `/api/v1/offers?channelId=&sort=` | Oferty kanału (zakładka „Oferty”) | pub |
| GET | `/api/v1/offers/batch?ids=` | Wsadowe pobranie kart (feed, koszyk, czat) | pub |
| GET | `/internal/offers/{offerId}/price-and-stock` | Aktualna cena i dostępność dla koszyka/zamówienia | int |
| POST | `/internal/offers/{offerId}/stock/reserve` | Rezerwacja zapasu na czas płatności | int |
| POST | `/internal/offers/{offerId}/stock/release` | Zwolnienie rezerwacji | int |

## Zachowanie

- **Kategorie** to dwa poziomy z migracji `V1__categories.sql` (id to slug, np. `elektronika-telefony`). Podkategoria dziedziczy atrybuty kategorii nadrzędnej. Wartości parametrów oferty zapisują się pod etykietą atrybutu (`label`), tak jak wysyła je kreator.
- **Szkic:** `POST /offers` wymaga kanału (channel-service `by-owner`, konto bez kanału dostaje 403). Wszystkie pola są opcjonalne, domyślnie powstaje produkt z jedną sztuką na stanie. Lokalizacja to adres kanału, a w usługach obszar działania z cennika. `PATCH` zmienia tylko podane pola. Typ zmienia się tylko w szkicu (409), a zmiana czyści pola drugiego typu.
- **Publikacja** wymaga tytułu (co najmniej 5 znaków) i kategorii. Produkt potrzebuje ceny większej od zera i stanu, a usługa cennika (`PUT /service-details`). Oferta musi mieć co najmniej jedno zdjęcie albo film i wymagane atrybuty kategorii. Braki wracają jako jedna lista w `422`, np. „Uzupełnij przed publikacją: kategorię, stan, Marka.”. Opublikowana oferta musi zachować komplet przy każdej zmianie, a niepełna zmiana daje 422 i się nie zapisuje. Po `unpublish` (PAUSED) ponowne `publish` wznawia ofertę.
- **Zdjęcia:** `PUT /media` z pełną listą w docelowej kolejności (najwyżej 8, pierwsze to miniatura). Każde musi być plikiem `OFFER_PHOTO` właściciela oferty: cudze daje 403, o innym przeznaczeniu albo nieistniejące 400. `videoId` zapisuje się bez sprawdzania, dopóki nie ma tiktok-integration. Oferta zwraca `photoUrls` i w tej samej kolejności `photoMediaIds`, którego używa kreator przy edycji.
- **Widoczność:** szkic widzi tylko właściciel (innym 404). Karta oferty i `/offers/batch` pokazują oferty opublikowane i wstrzymane (czat, koszyk), a `/offers?channelId=` tylko opublikowane. Cudza opublikowana oferta przy zmianie daje 403. Usunięcie jest miękkie (`deleted_at`), a usunięta oferta znika wszędzie.
- **Karty** biorą nazwę, handle, awatar i weryfikację kanału z channel-service przy każdym odczycie, jednym zapytaniem na listę. Gdy channel-service nie działa, wynikiem jest 503. Pola z innych serwisów są puste albo zerowe: ocena (engagement), długość filmu (tiktok-integration), najbliższy termin (booking), dostawa (shipping), liczba pytań, wyświetlenia (analytics). Sortowanie `popular` działa jak `newest`, dopóki nie ma danych o popularności.
- **Wskaźnik jakości:** zdjęcia (co najmniej 3) 25 pkt, film 25, opis (co najmniej 80 znaków) 20, parametry 15, tytuł (co najmniej 20 znaków) 15. Poziom: od 80 „bardzo dobra”, od 50 „dobra”, poniżej „słaba”. Za brak atrybutów w kategorii parametry dostają komplet punktów.
- **Paginacja:** `?cursor=&limit=` (domyślnie 20, najwyżej 50). Kursor jest nieprzezroczysty i zły daje 400. `/offers/mine` sortuje od ostatnio zmienionej, `/offers` od najnowszej publikacji albo `cheapest`.
- **`/internal/offers/{id}/price-and-stock`:** `{offerId, type, price, stock, available}`, gdzie `available` znaczy: opublikowana i (usługa albo zapas większy od zera).
- **Duplikat** to szkic z tytułem „… (kopia)” i tymi samymi plikami zdjęć.

## Zdarzenia Kafka

Wiersze z §17, w których występuje `catalog` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |
| `order.created`, `order.paid`, `order.shipped`, `order.completed`, `order.cancelled` | order | catalog (zapas), notification, analytics, engagement |

## Dane i zależności

- **Magazyn danych:** PostgreSQL
- **Zależności:** media-service, channel-service; później tiktok-integration-service (film)
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 2. `media-service`, `catalog-service` — oferty ze zdjęciami (bez wideo).

## Wyjątki routingu

Gateway (§2) kieruje ruch po prefiksie ścieżki, a poniższe endpointy leżą pod prefiksem innego serwisu. Gateway obsługuje je trasami szczegółowymi (`order: -1`), które mają pierwszeństwo przed trasami po prefiksie. Ścieżki pozostają bez zmian (decyzja z 2026-10-24). Test `RoutingTests` w `rynek-api-gateway` sprawdza, czy każda z nich trafia do właściwego serwisu.

- `/api/v1/offers/{offerId}/video`: należy do `tiktok-integration-service` (prefiks wskazuje `catalog-service`).
- `/api/v1/offers/{offerId}/questions`, `/api/v1/offers/{offerId}/like`: należy do `engagement-service` (prefiks wskazuje `catalog-service`).
