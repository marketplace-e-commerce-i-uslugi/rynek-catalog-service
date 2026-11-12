package pl.rynek.catalog.engagement;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Oceny kanałów z engagement-service ({@code GET /internal/ratings}) do kart ofert. Bez engagement oferty pokazują się
 * bez oceny (0 / 0), bo ocena to dodatek, a nie powód, żeby karta się nie wyświetliła.
 */
@Component
public class RatingClient {

    private static final Logger log = LoggerFactory.getLogger(RatingClient.class);

    private final RestClient http;

    public RatingClient(@Value("${rynek.engagement.url}") String engagementUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().baseUrl(engagementUrl).requestFactory(requests).build();
    }

    /** Oceny po id kanału; kanał bez opinii albo przy niedostępnym engagement nie występuje w mapie. */
    public Map<UUID, Rating> batch(Collection<UUID> channelIds) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        String ids = channelIds.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<Rating> found = http.get().uri(uri -> uri.path("/internal/ratings").queryParam("channelIds", ids).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return found == null ? Map.of() : found.stream().filter(r -> r.count() > 0)
                    .collect(Collectors.toMap(Rating::channelId, Function.identity(), (a, b) -> a));
        } catch (RestClientResponseException | ResourceAccessException e) {
            log.warn("Brak ocen kanałów {}: {}", ids, e.getMessage());
            return Map.of();
        }
    }

    public record Rating(UUID channelId, double average, int count) {
    }
}
