package pl.rynek.catalog.video;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Filmy ofert z tiktok-integration-service. Czas trwania filmu na kartach to dodatek ({@link #durations}): bez tiktok-integration oferty
 * pokazują się bez niego. Zapis filmu oferty ({@link #setOfferVideo}) jest ścisły: tiktok sprawdza, czy film należy do kanału oferty.
 */
@Component
public class VideoClient {

    private static final Logger log = LoggerFactory.getLogger(VideoClient.class);

    private final RestClient http;

    public VideoClient(@Value("${rynek.tiktok.url}") String tiktokUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().baseUrl(tiktokUrl).requestFactory(requests).build();
    }

    /** Czas trwania filmu (s) po id oferty; oferta bez filmu albo niedostępny tiktok-integration nie występuje w mapie. */
    public Map<UUID, Integer> durations(Collection<UUID> offerIds) {
        if (offerIds.isEmpty()) {
            return Map.of();
        }
        String ids = offerIds.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<OfferVideo> found = http.get().uri(uri -> uri.path("/internal/videos").queryParam("offerIds", ids).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            Map<UUID, Integer> result = new HashMap<>();
            if (found != null) {
                // Film z linku nie ma znanej długości (0): karta wtedy nie pokazuje czasu zamiast „0:00”.
                found.stream().filter(v -> v.durationSec() > 0).forEach(v -> result.put(v.offerId(), v.durationSec()));
            }
            return result;
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Brak czasów trwania filmów ofert: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Film oferty (albo jego odpięcie, gdy {@code videoId} puste); cudzy film albo nieistniejący daje 403/404 z komunikatem tiktok. */
    public void setOfferVideo(UUID offerId, UUID channelId, UUID videoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("channelId", channelId);
        body.put("videoId", videoId);
        try {
            http.put().uri("/internal/offers/{offerId}/video", offerId).body(body).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Możesz dodać tylko film swojego kanału.");
            }
            if (e.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono filmu. Dodaj go ponownie.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się zapisać filmu oferty.");
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Filmy są chwilowo niedostępne. Spróbuj ponownie za chwilę.");
        }
    }

    record OfferVideo(UUID offerId, UUID videoId, int durationSec, String coverUrl, String embedUrl) {
    }
}
