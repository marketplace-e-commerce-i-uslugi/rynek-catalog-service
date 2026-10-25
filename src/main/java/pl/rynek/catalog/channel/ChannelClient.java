package pl.rynek.catalog.channel;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * Dane kanałów z channel-service ({@code /internal/channels/...}). Karty ofert pobierają je przy odczycie, więc
 * zmiana nazwy, awatara czy weryfikacji kanału od razu widać we wszystkich ofertach.
 */
@Component
public class ChannelClient {

    private final RestClient http;

    public ChannelClient(@Value("${rynek.channel.url}") String channelUrl) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(channelUrl).requestFactory(requests).build();
    }

    /** Kanał konta albo pusty, gdy konto go nie ma. */
    public Optional<OwnerChannel> byOwner(UUID userId) {
        try {
            return Optional.ofNullable(http.get().uri("/internal/channels/by-owner/{userId}", userId).retrieve().body(OwnerChannel.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw unavailable();
        }
    }

    /** Kanały po id; brakujące (np. usunięte) nie występują w mapie. */
    public Map<UUID, ChannelRef> batch(Collection<UUID> channelIds) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        String ids = channelIds.stream().distinct().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<ChannelRef> found = http.get().uri(uri -> uri.path("/internal/channels").queryParam("ids", ids).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return found == null ? Map.of() : found.stream().collect(Collectors.toMap(ChannelRef::id, Function.identity()));
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Serwis kanałów jest niedostępny. Spróbuj ponownie za chwilę.");
    }

    /** Kształt {@code ChannelRef} z rynek-frontend (common.ts). */
    public record ChannelRef(UUID id, String handle, String name, String avatarUrl, boolean verified) {
    }

    public record OwnerChannel(UUID id, String handle, String name, String avatarUrl, boolean verified, String address) {
    }
}
