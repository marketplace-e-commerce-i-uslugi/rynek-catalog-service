package pl.rynek.catalog.offers;

import java.util.ArrayList;
import java.util.List;

import pl.rynek.catalog.categories.CategoryService.CategoryAttribute;

/**
 * Wskaźnik jakości dla kreatora: suma punktów za zdjęcia, film, opis, parametry i tytuł (razem 100) oraz
 * podpowiedzi do brakujących elementów. Kształt {@code QualityScore} z rynek-frontend.
 */
public record QualityScore(String level, int score, List<String> hints) {

    static QualityScore of(Offer offer, List<CategoryAttribute> attributes) {
        int score = 0;
        List<String> hints = new ArrayList<>();
        if (offer.getPhotos().size() >= 3) {
            score += 25;
        } else {
            hints.add("Dodaj co najmniej 3 zdjęcia, żeby kupujący zobaczyli ofertę z kilku stron.");
        }
        if (offer.getVideoId() != null) {
            score += 25;
        } else {
            hints.add("Dodaj film: oferty z filmem są lepiej widoczne w feedzie.");
        }
        if (offer.getDescription().length() >= 80) {
            score += 20;
        } else {
            hints.add(offer.getType() == OfferType.SERVICE
                    ? "Rozbuduj opis: zakres usługi, doświadczenie, dojazd."
                    : "Rozbuduj opis: stan, wyposażenie, dostawa.");
        }
        // Kategoria bez atrybutów (np. większość usług) nie ma czego uzupełniać.
        boolean requiredFilled = attributes.stream().filter(CategoryAttribute::required)
                .allMatch(a -> offer.getParams().stream().anyMatch(p -> p.label().equals(a.label())));
        if (attributes.isEmpty() || (requiredFilled && offer.getParams().size() >= Math.min(2, attributes.size()))) {
            score += 15;
        } else {
            hints.add("Uzupełnij parametry, np. markę i model.");
        }
        if (offer.getTitle().length() >= 20) {
            score += 15;
        } else {
            hints.add("Rozbuduj tytuł o markę, model albo najważniejszą cechę.");
        }
        String level = score >= 80 ? "bardzo dobra" : score >= 50 ? "dobra" : "słaba";
        return new QualityScore(level, score, hints);
    }
}
