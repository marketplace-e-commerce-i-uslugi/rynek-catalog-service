# rynek-catalog-service

`catalog-service` — oferty (produkty i usługi), kategorie

> Status: **szkielet**. Wszystkie endpointy z tabeli istnieją w kontrolerach (`src/main/java/.../api/`) i zwracają `501 Not Implemented` (RFC 7807). Ciała żądań to puste rekordy w `api/dto/`.

## Uruchomienie

```bash
./mvnw spring-boot:run   # port 8083, zmienna PORT
./mvnw verify            # testy
```

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

## Zdarzenia Kafka

Wiersze z §17, w których występuje `catalog` (publikuje lub słucha).

| Zdarzenie | Publikuje | Słuchają |
|---|---|---|
| `offer.published`, `offer.updated`, `offer.unpublished` | catalog | search, feed, cart, analytics |
| `video.synced`, `video.attached`, `video.removed` | tiktok-integration | catalog, feed, search |
| `order.created`, `order.paid`, `order.shipped`, `order.completed`, `order.cancelled` | order | catalog (zapas), notification, analytics, engagement |

## Dane i zależności

- **Magazyn danych:** PostgreSQL
- **Zależności:** media-service, tiktok-integration-service, channel-service
- **Kontrakty:** `rynek-contracts/openapi/` i `rynek-contracts/events/`
- **Lokalne uruchomienie:** `rynek-infra/local/`

## Kolejność budowy

Etap z §19: 2. `media-service`, `catalog-service` — oferty ze zdjęciami (bez wideo).

## Wyjątki routingu

Gateway (§2) kieruje ruch po prefiksie ścieżki, a poniższe endpointy leżą pod prefiksem innego serwisu. Gateway obsługuje je trasami szczegółowymi (`order: -1`), które mają pierwszeństwo przed trasami po prefiksie. Ścieżki pozostają bez zmian (decyzja z 2026-10-24). Test `RoutingTests` w `rynek-api-gateway` sprawdza, czy każda z nich trafia do właściwego serwisu.

- `/api/v1/offers/{offerId}/video`: należy do `tiktok-integration-service` (prefiks wskazuje `catalog-service`).
- `/api/v1/offers/{offerId}/questions`, `/api/v1/offers/{offerId}/like`: należy do `engagement-service` (prefiks wskazuje `catalog-service`).
