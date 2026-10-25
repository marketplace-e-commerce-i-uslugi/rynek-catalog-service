package pl.rynek.catalog.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ChannelClient channels;

    @MockitoBean
    MediaClient media;

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
                .andExpect(jsonPath("$.photoMediaIds", contains(photo.toString())))
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

    @Test
    void serviceUsesServiceDetailsForPriceAndArea() throws Exception {
        UUID seller = UUID.randomUUID();
        String id = created(seller, "{\"type\":\"SERVICE\",\"title\":\"Malowanie mieszkań\",\"categoryId\":\"remonty-malowanie\",\"condition\":\"Nowy\"}");
        mvc.perform(get("/api/v1/offers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(seller)))
                .andExpect(jsonPath("$.condition").value(nullValue()))
                .andExpect(jsonPath("$.stock").value(nullValue()));
        stock(seller, id, 3).andExpect(status().isConflict());
        publish(seller, id)
                .andExpect(jsonPath("$.detail").value("Uzupełnij przed publikacją: cennik usługi (cena od, jednostka, obszar, czas), co najmniej jedno zdjęcie albo film."));

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
        publish(seller, id).andExpect(status().isOk());

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
        given(media.get(avatar)).willReturn(new MediaFile(avatar, seller, "AVATAR", "image/png", "http://x/a"));
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
                .andExpect(jsonPath("$.photoMediaIds", contains(photo.toString())))
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

    private UUID photo(UUID owner) {
        UUID mediaId = UUID.randomUUID();
        given(media.get(mediaId)).willReturn(new MediaFile(mediaId, owner, "OFFER_PHOTO", "image/jpeg",
                "http://localhost:8333/rynek-media-public/offer_photo/" + mediaId));
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
