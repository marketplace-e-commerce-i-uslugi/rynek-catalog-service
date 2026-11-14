package pl.rynek.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.TestcontainersConfig;
import pl.rynek.catalog.booking.BookingClient;
import pl.rynek.catalog.video.VideoClient;
import pl.rynek.catalog.channel.ChannelClient;
import pl.rynek.catalog.channel.ChannelClient.ChannelRef;
import pl.rynek.catalog.channel.ChannelClient.OwnerChannel;
import pl.rynek.catalog.media.MediaClient;
import pl.rynek.catalog.media.MediaClient.MediaFile;

/**
 * Kategorie i oferty na prawdziwym PostgreSQL. Tokeny w testach to {@code user-<uuid>}, a channel-service
 * i media-service są zastąpione atrapami: każdy sprzedawca ma kanał o id pochodnym od swojego id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OfferFlowTests {

    static final String PHONE = "{\"type\":\"PRODUCT\",\"title\":\"iPhone 15 128 GB\",\"price\":{\"amount\":349900,\"currency\":\"PLN\"}}";

    @Autowired
    MockMvc mvc;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ChannelClient channels;

    @MockitoBean
    MediaClient media;

    @MockitoBean
    BookingClient booking;

    @MockitoBean
    VideoClient videoClient;

    @BeforeEach
    void stubs() {
        given(jwtDecoder.decode(anyString())).willAnswer(call -> {
            String token = call.getArgument(0);
            if (!token.startsWith("user-")) {
                throw new BadJwtException("Nieważny token");
            }
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(token.substring(5))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        });
        given(channels.byOwner(any())).willAnswer(call -> {
            UUID owner = call.getArgument(0);
            return Optional.of(new OwnerChannel(channelOf(owner), "kanal", "Kanał testowy", null, true, "Warszawa"));
        });
        given(channels.batch(any())).willAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            return ids.stream().distinct().map(id -> new ChannelRef(id, "kanal", "Kanał testowy", null, true))
                    .collect(Collectors.toMap(ChannelRef::id, Function.identity()));
        });
    }

    @Test
    void categoriesFormATreeAndInheritAttributes() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("elektronika"))
                .andExpect(jsonPath("$[0].children[0].name").value("Telefony"))
                .andExpect(jsonPath("$[0].children[0].children").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == 'sprzatanie')].children").isEmpty());
        mvc.perform(get("/api/v1/categories/elektronika-telefony/attributes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label").value(contains("Marka", "Model", "Gwarancja", "Pamięć")))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].options").doesNotExist())
                .andExpect(jsonPath("$[2].options", hasSize(3)));
        mvc.perform(get("/api/v1/categories/nie-ma/attributes")).andExpect(status().isNotFound());
    }

    @Test
    void accountWithoutChannelCannotCreateOffers() throws Exception {
        UUID buyer = UUID.randomUUID();
        given(channels.byOwner(buyer)).willReturn(Optional.empty());
        create(buyer, PHONE)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Najpierw załóż kanał, aby wystawiać oferty."));
        mvc.perform(post("/api/v1/offers").contentType(MediaType.APPLICATION_JSON).content(PHONE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productGoesFromDraftToPublishedAndPaused() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, PHONE);
        mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stock").value(1))
                .andExpect(jsonPath("$.location").value("Warszawa"))
                .andExpect(jsonPath("$.channel.id").value(channelOf(seller).toString()));
        // Szkic jest prywatny.
        mvc.perform(get("/api/v1/offers/" + id)).andExpect(status().isNotFound());

        publish(seller, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Uzupełnij przed publikacją: kategorię, stan, co najmniej jedno zdjęcie albo film."));
        update(seller, id, "{\"categoryId\":\"elektronika-telefony\",\"condition\":\"Nowy\"}").andExpect(status().isOk());
        UUID photo = photo(seller);
        setMedia(seller, id, "[\"" + photo + "\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[*].mediaId", contains(photo.toString())))
                .andExpect(jsonPath("$.thumbnailUrl").value("http://localhost:8333/rynek-media-public/offer_photo/" + photo));
        publish(seller, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Uzupełnij przed publikacją: Marka."));
        update(seller, id, "{\"params\":[{\"label\":\"Marka\",\"value\":\" Apple \"},{\"label\":\"Pamięć\",\"value\":\"128 GB\"}]}")
                .andExpect(jsonPath("$.params[0].value").value("Apple"));
        publish(seller, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.categoryPath", contains("Elektronika", "Telefony")));

        mvc.perform(get("/api/v1/offers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(349900))
                .andExpect(jsonPath("$.channel.name").value("Kanał testowy"))
                .andExpect(jsonPath("$.rating.count").value(0));
        mvc.perform(get("/api/v1/offers").param("channelId", channelOf(seller).toString()))
                .andExpect(jsonPath("$.items[*].id", contains(id)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.stock").value(1));

        // Opublikowana oferta nie może stracić kompletu pól; zmiana się nie zapisuje.
        update(seller, id, "{\"title\":\"\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("tytuł")));
        setMedia(seller, id, "[]").andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/offers/" + id)).andExpect(jsonPath("$.title").value("iPhone 15 128 GB"))
                .andExpect(jsonPath("$.photoUrls", hasSize(1)));
        // Typ zmienia się tylko w szkicu.
        update(seller, id, "{\"type\":\"SERVICE\"}").andExpect(status().isConflict());

        stock(seller, id, 0).andExpect(status().isNoContent());
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock")).andExpect(jsonPath("$.available").value(false));

        mvc.perform(post("/api/v1/offers/" + id + "/unpublish").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(get("/api/v1/offers").param("channelId", channelOf(seller).toString()))
                .andExpect(jsonPath("$.items").isEmpty());
        // Wstrzymana zostaje w kartach wsadowych (czat, koszyk), szkice i nieznane id nie.
        String draft = created(seller, PHONE);
        mvc.perform(get("/api/v1/offers/batch").param("ids", draft + "," + id + ",nie-uuid," + UUID.randomUUID()))
                .andExpect(jsonPath("$[*].id", contains(id)));
        mvc.perform(get("/api/v1/offers/mine").param("status", "PAUSED").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items[*].id", contains(id)))
                .andExpect(jsonPath("$.items[0].views").value(0));
    }

    /** Kreator wysyła PATCH /stock i PUT /media równolegle; żaden z zapisów nie może cofnąć drugiego. */
    @Test
    void parallelStockAndMediaUpdatesKeepBothChanges() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, PHONE);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int stock = 2; stock <= 6; stock++) {
                UUID photo = photo(seller);
                int expected = stock;
                CountDownLatch start = new CountDownLatch(1);
                Future<?> stockCall = pool.submit(() -> {
                    start.await();
                    return stock(seller, id, expected).andExpect(status().isNoContent());
                });
                Future<?> mediaCall = pool.submit(() -> {
                    start.await();
                    return setMedia(seller, id, "[\"" + photo + "\"]").andExpect(status().isOk());
                });
                start.countDown();
                stockCall.get();
                mediaCall.get();
                mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                        .andExpect(jsonPath("$.stock").value(expected))
                        .andExpect(jsonPath("$.photos[*].mediaId", contains(photo.toString())));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Kreator zapisuje pola, stan, zdjęcia i cennik jednym żądaniem: całość albo nic. */
    @Test
    void wizardSavesEverythingInOneRequestOrNothing() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID first = photo(seller);
        UUID second = photo(seller);
        String id = created(seller, "{\"type\":\"PRODUCT\",\"title\":\"iPhone 15 128 GB\",\"categoryId\":\"elektronika-telefony\","
                + "\"condition\":\"Nowy\",\"price\":{\"amount\":349900,\"currency\":\"PLN\"},\"params\":[{\"label\":\"Marka\",\"value\":\"Apple\"}],"
                + "\"stock\":5,\"media\":{\"photoMediaIds\":[\"" + second + "\",\"" + first + "\"],\"videoId\":null}}");
        mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.photos[*].mediaId", contains(second.toString(), first.toString())))
                .andExpect(jsonPath("$.photos[0].altText").value("Zdjęcie " + second));
        publish(seller, id).andExpect(status().isOk());

        // Cudze zdjęcie odrzuca całą zmianę, łącznie z tytułem i stanem.
        update(seller, id, "{\"title\":\"Nowy tytuł oferty\",\"stock\":9,\"media\":{\"photoMediaIds\":[\"" + photo(UUID.randomUUID()) + "\"]}}")
                .andExpect(status().isForbidden());
        // Opublikowana oferta bez zdjęć nie przechodzi, więc tytuł i stan też zostają.
        update(seller, id, "{\"title\":\"Nowy tytuł oferty\",\"stock\":9,\"media\":{\"photoMediaIds\":[]}}")
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/offers/" + id))
                .andExpect(jsonPath("$.title").value("iPhone 15 128 GB"))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.photos", hasSize(2)));

        update(seller, id, "{\"title\":\"Nowy tytuł oferty\",\"stock\":9,\"media\":{\"photoMediaIds\":[\"" + first + "\"]}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nowy tytuł oferty"))
                .andExpect(jsonPath("$.stock").value(9))
                .andExpect(jsonPath("$.photos[*].mediaId", contains(first.toString())));
        update(seller, id, "{\"stock\":-1}").andExpect(status().isBadRequest());
        update(seller, id, "{\"service\":{\"priceFrom\":{\"amount\":2500,\"currency\":\"PLN\"},\"unit\":\"m²\",\"area\":\"Kraków\","
                + "\"durationMin\":60,\"depositPercent\":0}}").andExpect(status().isConflict());
    }

    @Test
    void serviceIsCreatedWithPriceListInOneRequest() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, "{\"type\":\"SERVICE\",\"title\":\"Malowanie mieszkań\",\"categoryId\":\"remonty-malowanie\","
                + "\"description\":\"Ściany i sufity, z gruntowaniem.\",\"media\":{\"photoMediaIds\":[\"" + photo(seller) + "\"]},"
                + "\"service\":{\"priceFrom\":{\"amount\":2500,\"currency\":\"PLN\"},\"unit\":\"m²\",\"area\":\"Kraków\",\"durationMin\":240,\"depositPercent\":20}}");
        publish(seller, id).andExpect(status().isOk()).andExpect(jsonPath("$.priceUnit").value("m²"));
        create(seller, "{\"type\":\"SERVICE\",\"stock\":3}").andExpect(status().isConflict());
    }

    /** Licznik w nagłówku kanału: catalog wysyła liczbę opublikowanych ofert po każdej zmianie widoczności. */
    @Test
    void channelGetsPublishedOffersCount() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID channel = channelOf(seller);
        String a = publishedPhone(seller);
        verify(channels).setOffersCount(channel, 1);
        String b = publishedPhone(seller);
        verify(channels).setOffersCount(channel, 2);
        mvc.perform(post("/api/v1/offers/" + a + "/unpublish").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/offers/" + b).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNoContent());
        verify(channels, times(2)).setOffersCount(channel, 1);
        verify(channels).setOffersCount(channel, 0);
        // Odrzucona publikacja nic nie wysyła.
        publish(seller, created(seller, PHONE)).andExpect(status().isUnprocessableContent());
        verify(channels, times(4)).setOffersCount(eq(channel), anyInt());
    }

    @Test
    void serviceOffersCarryNextSlotAndProductsDoNot() throws Exception {
        UUID seller = UUID.randomUUID();
        String service = created(seller, "{\"type\":\"SERVICE\",\"title\":\"Malowanie mieszkań\",\"categoryId\":\"remonty-malowanie\","
                + "\"description\":\"Ściany i sufity, z gruntowaniem.\",\"media\":{\"photoMediaIds\":[\"" + photo(seller) + "\"]},"
                + "\"service\":{\"priceFrom\":{\"amount\":2500,\"currency\":\"PLN\"},\"unit\":\"m²\",\"area\":\"Kraków\",\"durationMin\":240,\"depositPercent\":20}}");
        publish(seller, service).andExpect(status().isOk());
        String product = publishedPhone(seller);
        stock(seller, product, 2).andExpect(status().isNoContent());

        Instant slot = Instant.parse("2026-12-01T09:00:00Z");
        given(booking.nextSlots(any())).willAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            return ids.contains(UUID.fromString(service)) ? java.util.Map.of(UUID.fromString(service), slot) : java.util.Map.of();
        });
        mvc.perform(get("/api/v1/offers/" + service)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextSlot").value("2026-12-01T09:00:00Z"));
        mvc.perform(get("/api/v1/offers/" + product)).andExpect(jsonPath("$.nextSlot").value(nullValue()));
        org.mockito.Mockito.clearInvocations(booking);
        String list = mvc.perform(get("/api/v1/offers").param("channelId", channelOf(seller).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(list, "$.items[?(@.id=='" + service + "')].nextSlot")).containsExactly("2026-12-01T09:00:00Z");
        assertThat((java.util.List<?>) JsonPath.read(list, "$.items[?(@.id=='" + product + "')].nextSlot")).containsOnlyNulls();
        // Booking dostaje tylko usługi, jednym zapytaniem na listę.
        verify(booking, times(1)).nextSlots(java.util.List.of(UUID.fromString(service)));

        // Awaria booking (pusta mapa) nie psuje listy ani szczegółów.
        org.mockito.Mockito.doReturn(java.util.Map.of()).when(booking).nextSlots(any());
        mvc.perform(get("/api/v1/offers/" + service)).andExpect(status().isOk()).andExpect(jsonPath("$.nextSlot").value(nullValue()));
        mvc.perform(get("/api/v1/offers").param("channelId", channelOf(seller).toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void serviceUsesServiceDetailsForPriceAndArea() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, "{\"type\":\"SERVICE\",\"title\":\"Malowanie mieszkań\",\"categoryId\":\"remonty-malowanie\",\"condition\":\"Nowy\"}");
        mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.condition").value(nullValue()))
                .andExpect(jsonPath("$.stock").value(nullValue()));
        stock(seller, id, 3).andExpect(status().isConflict());
        publish(seller, id)
                .andExpect(jsonPath("$.detail").value("Uzupełnij przed publikacją: cennik usługi (cena od, jednostka, obszar, czas), opis usługi (co najmniej 20 znaków), co najmniej jedno zdjęcie albo film."));

        mvc.perform(put("/api/v1/offers/" + id + "/service-details").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceFrom\":{\"amount\":2500,\"currency\":\"PLN\"},\"unit\":\"m²\",\"area\":\"Kraków i okolice\",\"durationMin\":240,\"depositPercent\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(2500))
                .andExpect(jsonPath("$.priceUnit").value("m²"))
                .andExpect(jsonPath("$.location").value("Kraków i okolice"))
                .andExpect(jsonPath("$.service.depositPercent").value(20));
        mvc.perform(put("/api/v1/offers/" + id + "/service-details").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priceFrom\":{\"amount\":2500,\"currency\":\"EUR\"},\"unit\":\"m²\",\"area\":\"Kraków\",\"durationMin\":240,\"depositPercent\":120}"))
                .andExpect(status().isBadRequest());
        setMedia(seller, id, "[\"" + photo(seller) + "\"]").andExpect(status().isOk());
        publish(seller, id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Uzupełnij przed publikacją: opis usługi (co najmniej 20 znaków)."));
        update(seller, id, "{\"description\":\"Ściany i sufity, z gruntowaniem.\"}").andExpect(status().isOk());
        publish(seller, id).andExpect(status().isOk());
        // Cennik usługi dla booking-service.
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock"))
                .andExpect(jsonPath("$.type").value("SERVICE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.service.unit").value("m²"))
                .andExpect(jsonPath("$.service.durationMin").value(240))
                .andExpect(jsonPath("$.service.depositPercent").value(20));

        mvc.perform(get("/api/v1/offers/" + id + "/quality-score").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("słaba"))
                .andExpect(jsonPath("$.hints", hasSize(4)));
    }

    @Test
    void onlyOwnerChangesOfferAndOnlyWithOwnPhotos() throws Exception {
        UUID seller = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        String id = created(seller, PHONE);

        update(stranger, id, "{\"title\":\"Cudza zmiana\"}").andExpect(status().isNotFound());
        setMedia(seller, id, "[\"" + photo(stranger) + "\"]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Możesz dodać tylko zdjęcia, które sam wysłałeś."));
        UUID avatar = UUID.randomUUID();
        given(media.get(avatar)).willReturn(new MediaFile(avatar, seller, "AVATAR", "image/png", "http://x/a", null));
        setMedia(seller, id, "[\"" + avatar + "\"]").andExpect(status().isBadRequest());
        UUID missing = UUID.randomUUID();
        given(media.get(missing)).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nie znaleźliśmy jednego ze zdjęć. Wyślij je ponownie."));
        setMedia(seller, id, "[\"" + missing + "\"]").andExpect(status().isBadRequest());

        update(seller, id, "{\"categoryId\":\"nie-ma\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Wybierz kategorię z listy."));
        update(seller, id, "{\"condition\":\"Jak nowy\"}").andExpect(status().isBadRequest());
        update(seller, id, "{\"price\":{\"amount\":-1,\"currency\":\"PLN\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cena nie może być ujemna."));

        update(seller, id, "{\"categoryId\":\"elektronika\",\"condition\":\"Nowy\",\"params\":[{\"label\":\"Marka\",\"value\":\"Apple\"}]}");
        setMedia(seller, id, "[\"" + photo(seller) + "\"]");
        publish(seller, id).andExpect(status().isOk());
        update(stranger, id, "{\"title\":\"Cudza zmiana\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Możesz zmieniać tylko własne oferty."));
    }

    @Test
    void duplicateDeleteAndPagination() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, PHONE);
        UUID photo = photo(seller);
        setMedia(seller, id, "[\"" + photo + "\"]");

        String copy = JsonPath.read(mvc.perform(post("/api/v1/offers/" + id + "/duplicate").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("iPhone 15 128 GB (kopia)"))
                .andExpect(jsonPath("$.photos[*].mediaId", contains(photo.toString())))
                .andReturn().getResponse().getContentAsString(), "$.id");
        created(seller, "{}");

        String first = mvc.perform(get("/api/v1/offers/mine").param("limit", "2").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].title").value("Szkic bez tytułu"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/offers/mine").param("limit", "2").param("cursor", (String) JsonPath.read(first, "$.nextCursor"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/api/v1/offers/mine").param("cursor", "zly").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/offers/mine").param("status", "SOLD").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/offers/mine")).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/offers/" + copy).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/offers/" + copy).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/mine").header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].id", not(contains(copy))));
        mvc.perform(get("/api/v1/offers")).andExpect(status().isBadRequest());
    }

    @Test
    void reservationTakesStockOnceAndReleaseReturnsItOnce() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = publishedPhone(seller);
        stock(seller, id, 3).andExpect(status().isNoContent());
        UUID first = UUID.randomUUID();
        reserve(id, first, 2).andExpect(status().isNoContent());
        // Ponowienie tej samej rezerwacji nie zdejmuje zapasu drugi raz.
        reserve(id, first, 2).andExpect(status().isNoContent());
        mvc.perform(get("/internal/offers/price-and-stock").param("ids", id + "," + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stock").value(1))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].title").value("iPhone 15 128 GB"))
                .andExpect(jsonPath("$[0].ownerId").value(seller.toString()))
                .andExpect(jsonPath("$[0].channelId").value(channelOf(seller).toString()))
                .andExpect(jsonPath("$[0].thumbnailUrl").value(containsString("offer_photo")));
        reserve(id, UUID.randomUUID(), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Brak wystarczającej liczby sztuk albo oferta nie jest już dostępna."));
        reserve(id, first, 1).andExpect(status().isConflict());

        release(id, first).andExpect(status().isNoContent());
        release(id, first).andExpect(status().isNoContent());
        release(id, UUID.randomUUID()).andExpect(status().isNoContent());
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock")).andExpect(jsonPath("$.stock").value(3));
        reserve(id, first, 2).andExpect(status().isConflict());
    }

    @Test
    void returnRestoresStockOnceAndNeverAfterRelease() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = publishedPhone(seller);
        stock(seller, id, 5).andExpect(status().isNoContent());
        UUID line = UUID.randomUUID();
        reserve(id, line, 2).andExpect(status().isNoContent());
        stockIs(id, 3);

        giveBack(id, line, 1).andExpect(status().isConflict());
        giveBack(UUID.randomUUID().toString(), line, 2).andExpect(status().isConflict());
        giveBack(id, UUID.randomUUID(), 2).andExpect(status().isNotFound());
        giveBack(id, line, 0).andExpect(status().isBadRequest());
        stockIs(id, 3);

        giveBack(id, line, 2).andExpect(status().isNoContent());
        giveBack(id, line, 2).andExpect(status().isNoContent());
        stockIs(id, 5);
        // Po zwrocie późniejsze zwolnienie tej samej rezerwacji nie oddaje sztuk drugi raz.
        release(id, line).andExpect(status().isNoContent());
        stockIs(id, 5);

        // Zwolniona rezerwacja (anulowanie) nie wraca jako zwrot.
        UUID cancelled = UUID.randomUUID();
        reserve(id, cancelled, 1).andExpect(status().isNoContent());
        release(id, cancelled).andExpect(status().isNoContent());
        giveBack(id, cancelled, 1).andExpect(status().isNoContent());
        stockIs(id, 5);
    }

    @Test
    void returnLeavesOfferWithoutStockTrackingUntouched() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = publishedPhone(seller);
        stock(seller, id, 2).andExpect(status().isNoContent());
        UUID line = UUID.randomUUID();
        reserve(id, line, 1).andExpect(status().isNoContent());
        jdbc.update("update offers set stock = null where id = ?", UUID.fromString(id));
        giveBack(id, line, 1).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("select stock from offers where id = ?", Integer.class, UUID.fromString(id)));
    }

    private void stockIs(String id, int expected) throws Exception {
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock")).andExpect(jsonPath("$.stock").value(expected));
    }

    private ResultActions giveBack(String offerId, UUID reservationId, int quantity) throws Exception {
        return mvc.perform(post("/internal/offers/" + offerId + "/stock/return").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationId\":\"" + reservationId + "\",\"quantity\":" + quantity + "}"));
    }

    @Test
    void parallelReservationsNeverSellMoreThanStock() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = publishedPhone(seller);
        stock(seller, id, 3).andExpect(status().isNoContent());
        int buyers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        try {
            java.util.List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return reserve(id, UUID.randomUUID(), 1).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Integer> result : results) {
                ok += result.get() == HttpStatus.NO_CONTENT.value() ? 1 : 0;
            }
            org.junit.jupiter.api.Assertions.assertEquals(3, ok);
        } finally {
            pool.shutdown();
        }
        mvc.perform(get("/internal/offers/" + id + "/price-and-stock"))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.available").value(false));
    }

    /** Zmiany ofert dla search-service od kursora; test filtruje po id, bo baza jest wspólna dla testów. */
    @Test
    void searchSyncReportsChangesTombstonesAndAvailability() throws Exception {
        Instant start = Instant.now();
        UUID seller = UUID.randomUUID();
        String id = publishedPhone(seller);
        stock(seller, id, 1).andExpect(status().isNoContent());

        String first = sync(start.toString(), null, 200).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].active").value(true))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].available").value(true))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].hasVideo").value(false))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].card.title").value("iPhone 15 128 GB"))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].card.channel.name").value("Kanał testowy"))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].categoryId").value("elektronika-telefony"))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].params[0].label").value("Marka"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        String since = JsonPath.read(first, "$.nextSince");
        String afterId = JsonPath.read(first, "$.nextAfterId");
        assertThat((java.util.List<?>) JsonPath.read(first, "$.items[?(@.id=='" + id + "')].categoryPath[*]")).hasSize(2);
        assertThat((java.util.List<?>) JsonPath.read(first, "$.items[?(@.id=='" + id + "')].categoryIds[*]")).hasSize(2)
                .last().isEqualTo("elektronika-telefony");
        // Bez nowych zmian kolejna strona jest pusta, a kursor zostaje u klienta.
        sync(since, afterId, 200).andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.nextSince").doesNotExist());

        // Sprzedaż ostatniej sztuki zmienia dostępność: oferta wraca w synchronizacji jako niedostępna.
        reserve(id, UUID.randomUUID(), 1).andExpect(status().isNoContent());
        sync(since, afterId, 200).andExpect(jsonPath("$.items[?(@.id=='" + id + "')].active").value(true))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].available").value(false));
        // Zwykła sprzedaż (zapas nie spada do zera) nie przestawia oferty w synchronizacji.
        String other = publishedPhone(seller);
        stock(seller, other, 5).andExpect(status().isNoContent());
        String afterOther = sync(since, afterId, 200).andReturn().getResponse().getContentAsString();
        String sinceOther = JsonPath.read(afterOther, "$.nextSince");
        String afterIdOther = JsonPath.read(afterOther, "$.nextAfterId");
        reserve(other, UUID.randomUUID(), 2).andExpect(status().isNoContent());
        sync(sinceOther, afterIdOther, 200).andExpect(jsonPath("$.items", hasSize(0)));

        // Wstrzymana i usunięta oferta to „nagrobek” bez karty.
        mvc.perform(post("/api/v1/offers/" + id + "/unpublish").header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isOk());
        sync(sinceOther, afterIdOther, 200).andExpect(jsonPath("$.items[?(@.id=='" + id + "')].active").value(false))
                .andExpect(jsonPath("$.items[?(@.id=='" + id + "')].card").value(org.hamcrest.Matchers.hasItem(nullValue())));
        mvc.perform(delete("/api/v1/offers/" + other).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(status().isNoContent());
        sync(sinceOther, afterIdOther, 200).andExpect(jsonPath("$.items[?(@.id=='" + other + "')].active").value(false));

        // Paginacja: limit 1 oddaje po jednej zmianie i mówi, że jest więcej; kursor prowadzi do następnej.
        String page = sync(start.toString(), null, 1).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        String second = sync(JsonPath.read(page, "$.nextSince"), JsonPath.read(page, "$.nextAfterId"), 1).andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(second, "$.items[0].id")).isNotEqualTo(JsonPath.read(page, "$.items[0].id"));
        sync("to-nie-data", null, 10).andExpect(status().isBadRequest());
        sync(start.toString(), "to-nie-uuid", 10).andExpect(status().isBadRequest());
    }

    @Test
    void kanalOfertyDlaTiktokIntegration_takzeDlaSzkicu_a404DlaNieistniejacej() throws Exception {
        UUID seller = UUID.randomUUID();
        String draft = created(seller, PHONE);
        mvc.perform(get("/internal/offers/" + draft + "/channel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.channelId", is(channelOf(seller).toString())));
        mvc.perform(get("/internal/offers/" + UUID.randomUUID() + "/channel")).andExpect(status().isNotFound());
    }

    @Test
    void filmOfertyZapisujeSieZPotwierdzeniemTiktokIntegration_tylkoGdyFilmSieZmienil() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, PHONE);
        UUID video = UUID.randomUUID();
        mvc.perform(put("/api/v1/offers/" + id + "/media").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaIds\":[],\"videoId\":\"" + video + "\"}")).andExpect(status().isOk());
        verify(videoClient).setOfferVideo(UUID.fromString(id), channelOf(seller), video);
        // Ten sam film ponownie: bez wywołania. Odpięcie: wywołanie z pustym filmem.
        mvc.perform(put("/api/v1/offers/" + id + "/media").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaIds\":[],\"videoId\":\"" + video + "\"}")).andExpect(status().isOk());
        verify(videoClient, times(1)).setOfferVideo(UUID.fromString(id), channelOf(seller), video);
        setMedia(seller, id, "[]").andExpect(status().isOk());
        verify(videoClient).setOfferVideo(UUID.fromString(id), channelOf(seller), null);
    }

    @Test
    void cudzyFilmOdrzucaTiktokIntegration_awOfercieNicSieNieZmienia() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, PHONE);
        UUID video = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Możesz dodać tylko film swojego kanału."))
                .when(videoClient).setOfferVideo(any(), any(), eq(video));
        mvc.perform(put("/api/v1/offers/" + id + "/media").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaIds\":[],\"videoId\":\"" + video + "\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller))).andExpect(jsonPath("$.videoId").value(org.hamcrest.Matchers.nullValue()));
    }

    private ResultActions sync(String since, String afterId, int limit) throws Exception {
        var request = get("/internal/offers/search-sync").param("limit", String.valueOf(limit));
        if (since != null) {
            request = request.param("since", since);
        }
        if (afterId != null) {
            request = request.param("afterId", afterId);
        }
        return mvc.perform(request);
    }

    private ResultActions reserve(String offerId, UUID reservationId, int quantity) throws Exception {
        return mvc.perform(post("/internal/offers/" + offerId + "/stock/reserve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationId\":\"" + reservationId + "\",\"quantity\":" + quantity + "}"));
    }

    private ResultActions release(String offerId, UUID reservationId) throws Exception {
        return mvc.perform(post("/internal/offers/" + offerId + "/stock/release").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationId\":\"" + reservationId + "\"}"));
    }

    private String publishedPhone(UUID seller) throws Exception {
        String id = created(seller, "{\"type\":\"PRODUCT\",\"title\":\"iPhone 15 128 GB\",\"categoryId\":\"elektronika-telefony\","
                + "\"condition\":\"Nowy\",\"price\":{\"amount\":349900,\"currency\":\"PLN\"},\"params\":[{\"label\":\"Marka\",\"value\":\"Apple\"}],"
                + "\"media\":{\"photoMediaIds\":[\"" + photo(seller) + "\"]}}");
        publish(seller, id).andExpect(status().isOk());
        return id;
    }

    private UUID photo(UUID owner) {
        UUID mediaId = UUID.randomUUID();
        given(media.get(mediaId)).willReturn(new MediaFile(mediaId, owner, "OFFER_PHOTO", "image/jpeg",
                "http://localhost:8333/rynek-media-public/offer_photo/" + mediaId, "Zdjęcie " + mediaId));
        return mediaId;
    }

    private String created(UUID seller, String json) throws Exception {
        return JsonPath.read(create(seller, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions create(UUID seller, String json) throws Exception {
        return mvc.perform(post("/api/v1/offers").header(HttpHeaders.AUTHORIZATION, bearer(seller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(UUID user, String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions setMedia(UUID user, String id, String photoIds) throws Exception {
        return mvc.perform(put("/api/v1/offers/" + id + "/media").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"photoMediaIds\":" + photoIds + ",\"videoId\":null}"));
    }

    private ResultActions stock(UUID user, String id, int stock) throws Exception {
        return mvc.perform(patch("/api/v1/offers/" + id + "/stock").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"stock\":" + stock + "}"));
    }

    private ResultActions publish(UUID user, String id) throws Exception {
        return mvc.perform(post("/api/v1/offers/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private static UUID channelOf(UUID owner) {
        return UUID.nameUUIDFromBytes(("kanal-" + owner).getBytes());
    }

    private static String bearer(UUID userId) {
        return "Bearer user-" + userId;
    }
}
