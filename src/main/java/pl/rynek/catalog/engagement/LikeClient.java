package pl.rynek.catalog.engagement;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Czy oglądający polubił ofertę, z engagement-service ({@code GET /internal/offers/{id}/liked}). Bez engagement: nie. */
@Component
public class LikeClient {

    private static final Logger log = LoggerFactory.getLogger(LikeClient.class);

    private final RestClient http;

    public LikeClient(@Value("${rynek.engagement.url}") String engagementUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().baseUrl(engagementUrl).requestFactory(requests).build();
    }

    public boolean liked(UUID offerId, UUID userId) {
        try {
            Liked liked = http.get().uri(uri -> uri.path("/internal/offers/{id}/liked").queryParam("userId", userId).build(offerId))
                    .retrieve().body(Liked.class);
            return liked != null && liked.liked();
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Brak informacji o polubieniu oferty {}: {}", offerId, e.getMessage());
            return false;
        }
    }

    public record Liked(boolean liked) {
    }
}
