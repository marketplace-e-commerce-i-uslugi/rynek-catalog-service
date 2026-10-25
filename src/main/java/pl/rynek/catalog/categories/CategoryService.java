package pl.rynek.catalog.categories;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Kategorie są danymi słownikowymi z migracji Flyway; serwis ich nie zmienia. */
@Service
public class CategoryService {

    private final JdbcClient jdbc;

    public CategoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Kategorie główne z podkategoriami, w kolejności {@code position}. */
    public List<CategoryNode> tree() {
        List<Row> rows = rows();
        Map<String, List<CategoryNode>> children = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.parentId() != null) {
                children.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(new CategoryNode(row.id(), row.name(), null));
            }
        }
        return rows.stream()
                .filter(row -> row.parentId() == null)
                .map(row -> new CategoryNode(row.id(), row.name(), children.get(row.id())))
                .toList();
    }

    public boolean exists(String categoryId) {
        return find(categoryId).isPresent();
    }

    /** Nazwy od kategorii głównej do podanej, np. {@code [Elektronika, Telefony]}. */
    public List<String> path(String categoryId) {
        List<String> names = new ArrayList<>();
        Optional<Row> current = find(categoryId);
        while (current.isPresent()) {
            names.addFirst(current.get().name());
            current = current.get().parentId() == null ? Optional.empty() : find(current.get().parentId());
        }
        return names;
    }

    /** Atrybuty kategorii nadrzędnej, a po nich własne podkategorii. */
    public List<CategoryAttribute> attributes(String categoryId) {
        Row category = find(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleźliśmy tej kategorii."));
        List<CategoryAttribute> attributes = new ArrayList<>();
        if (category.parentId() != null) {
            attributes.addAll(ownAttributes(category.parentId()));
        }
        for (CategoryAttribute own : ownAttributes(category.id())) {
            attributes.removeIf(inherited -> inherited.key().equals(own.key()));
            attributes.add(own);
        }
        return attributes;
    }

    private List<CategoryAttribute> ownAttributes(String categoryId) {
        return jdbc.sql("SELECT key, label, type, options, required FROM category_attributes WHERE category_id = ? ORDER BY position")
                .param(categoryId)
                .query((rs, n) -> new CategoryAttribute(rs.getString("key"), rs.getString("label"), rs.getString("type"),
                        options(rs.getArray("options")), rs.getBoolean("required")))
                .list();
    }

    private Optional<Row> find(String categoryId) {
        return jdbc.sql("SELECT id, parent_id, name FROM categories WHERE id = ?").param(categoryId)
                .query((rs, n) -> new Row(rs.getString("id"), rs.getString("parent_id"), rs.getString("name")))
                .optional();
    }

    private List<Row> rows() {
        return jdbc.sql("SELECT id, parent_id, name FROM categories ORDER BY position, name")
                .query((rs, n) -> new Row(rs.getString("id"), rs.getString("parent_id"), rs.getString("name")))
                .list();
    }

    private static List<String> options(Array array) throws SQLException {
        return array == null ? null : List.of((String[]) array.getArray());
    }

    private record Row(String id, String parentId, String name) {
    }

    /** Kształt {@code Category} z rynek-frontend; {@code children} pomijane u liści. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoryNode(String id, String name, List<CategoryNode> children) {
    }

    /** Kształt {@code CategoryAttribute}; {@code options} tylko dla {@code select}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoryAttribute(String key, String label, String type, List<String> options, boolean required) {
    }
}
