package pl.rynek.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static pl.rynek.catalog.common.NotImplemented.notImplemented;

@RestController
public class CategoriesController {

    /**
     * Drzewo kategorii (chipy, filtry). Dostęp: pub.
     */
    @GetMapping("/api/v1/categories")
    public void listCategories() {
        throw notImplemented();
    }

    /**
     * Atrybuty kategorii (parametry w formularzu). Dostęp: pub.
     */
    @GetMapping("/api/v1/categories/{categoryId}/attributes")
    public void getAttributes(@PathVariable String categoryId) {
        throw notImplemented();
    }
}
