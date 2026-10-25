package pl.rynek.catalog.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.rynek.catalog.api.dto.CursorPage;

/** Kursor to zakodowane przesunięcie; klient traktuje go jak nieprzezroczysty napis. */
final class Cursors {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private Cursors() {
    }

    static int offset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int offset = decoded.startsWith("o:") ? Integer.parseInt(decoded.substring(2)) : -1;
            if (offset < 0) {
                throw new IllegalArgumentException();
            }
            return offset;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowy kursor. Zacznij od pierwszej strony.");
        }
    }

    static int limit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit musi wynosić od 1 do " + MAX_LIMIT + ".");
        }
        return limit;
    }

    /** Pobiera o jeden element więcej, żeby wiedzieć, czy jest następna strona. */
    static <S, T> CursorPage<T> page(int offset, int limit, Function<Integer, List<S>> fetch, Function<List<S>, List<T>> map) {
        List<S> rows = fetch.apply(limit + 1);
        boolean more = rows.size() > limit;
        List<S> items = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + (offset + limit)).getBytes(StandardCharsets.UTF_8)) : null;
        return new CursorPage<>(map.apply(items), next);
    }
}
