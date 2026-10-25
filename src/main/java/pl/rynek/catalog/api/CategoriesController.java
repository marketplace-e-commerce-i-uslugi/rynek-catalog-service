package pl.rynek.catalog.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.rynek.catalog.categories.CategoryService;
import pl.rynek.catalog.categories.CategoryService.CategoryAttribute;
import pl.rynek.catalog.categories.CategoryService.CategoryNode;

@RestController
public class CategoriesController {

    private final CategoryService categories;

    public CategoriesController(CategoryService categories) {
        this.categories = categories;
    }

    /**
     * Drzewo kategorii (chipy, filtry). Dostęp: pub.
     */
    @GetMapping("/api/v1/categories")
    public List<CategoryNode> listCategories() {
        return categories.tree();
    }

    /**
     * Atrybuty kategorii (parametry w formularzu). Dostęp: pub.
     */
    @GetMapping("/api/v1/categories/{categoryId}/attributes")
    public List<CategoryAttribute> getAttributes(@PathVariable String categoryId) {
        return categories.attributes(categoryId);
    }
}
