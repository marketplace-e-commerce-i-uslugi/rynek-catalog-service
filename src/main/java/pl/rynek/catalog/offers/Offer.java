package pl.rynek.catalog.offers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * {@code @DynamicUpdate}: UPDATE obejmuje tylko zmienione kolumny. Kreator wysyła równolegle np. {@code PUT /media}
 * i {@code PATCH /stock}; przy pełnym UPDATE drugi zapis przywracałby pozostałe pola sprzed pierwszego.
 */
@Entity
@Table(name = "offers")
@DynamicUpdate
public class Offer {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Enumerated(EnumType.STRING)
    private OfferType type;

    @Enumerated(EnumType.STRING)
    private OfferStatus status;

    private String title;

    private String description;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "price_amount")
    private long priceAmount;

    @Column(name = "price_currency")
    private String priceCurrency;

    private String condition;

    private Integer stock;

    private String location;

    @Column(name = "video_id")
    private UUID videoId;

    @Column(name = "service_unit")
    private String serviceUnit;

    @Column(name = "service_area")
    private String serviceArea;

    @Column(name = "service_duration_min")
    private Integer serviceDurationMin;

    @Column(name = "service_deposit_percent")
    private Integer serviceDepositPercent;

    // Odpowiedzi składa się po transakcji, a kolekcje są małe (do 8 zdjęć, 20 parametrów); listy ofert ładują je
    // jednym zapytaniem na kolekcję (SUBSELECT), a nie osobno dla każdej oferty.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "offer_photos", joinColumns = @JoinColumn(name = "offer_id"))
    @OrderColumn(name = "position")
    private List<Photo> photos = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "offer_params", joinColumns = @JoinColumn(name = "offer_id"))
    @OrderColumn(name = "position")
    private List<Param> params = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Offer() {
    }

    /** Nowy szkic. Produkt startuje z jedną sztuką na stanie, bo kreator ustawia zapas dopiero przy kolejnym zapisie. */
    public Offer(UUID ownerId, UUID channelId, OfferType type, String location, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.channelId = channelId;
        this.type = type;
        this.status = OfferStatus.DRAFT;
        this.title = "";
        this.description = "";
        this.priceCurrency = "PLN";
        this.stock = type == OfferType.PRODUCT ? 1 : null;
        this.location = location;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Kopia jako szkic, z tymi samymi zdjęciami (te same pliki w media-service). */
    public Offer duplicate(String title, Instant now) {
        Offer copy = new Offer(ownerId, channelId, type, location, now);
        copy.title = title;
        copy.description = description;
        copy.categoryId = categoryId;
        copy.priceAmount = priceAmount;
        copy.condition = condition;
        copy.stock = stock;
        copy.serviceUnit = serviceUnit;
        copy.serviceArea = serviceArea;
        copy.serviceDurationMin = serviceDurationMin;
        copy.serviceDepositPercent = serviceDepositPercent;
        copy.photos = new ArrayList<>(photos);
        copy.params = new ArrayList<>(params);
        return copy;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean isPublished() {
        return status == OfferStatus.PUBLISHED;
    }

    /** Zmiana typu czyści pola, które drugi typ nie ma (stan i zapas produktu, cennik usługi). */
    public void changeType(OfferType newType, Instant now) {
        if (newType == type) {
            return;
        }
        this.type = newType;
        if (newType == OfferType.SERVICE) {
            this.condition = null;
            this.stock = null;
        } else {
            this.stock = 1;
            this.serviceUnit = null;
            this.serviceArea = null;
            this.serviceDurationMin = null;
            this.serviceDepositPercent = null;
        }
        touch(now);
    }

    public void changeTitle(String title, Instant now) {
        this.title = title;
        touch(now);
    }

    public void changeDescription(String description, Instant now) {
        this.description = description;
        touch(now);
    }

    public void changeCategory(String categoryId, Instant now) {
        this.categoryId = categoryId;
        touch(now);
    }

    public void changePrice(long amount, Instant now) {
        this.priceAmount = amount;
        touch(now);
    }

    public void changeCondition(String condition, Instant now) {
        this.condition = condition;
        touch(now);
    }

    public void changeParams(List<Param> params, Instant now) {
        this.params.clear();
        this.params.addAll(params);
        touch(now);
    }

    public void changeMedia(List<Photo> photos, UUID videoId, Instant now) {
        this.photos.clear();
        this.photos.addAll(photos);
        this.videoId = videoId;
        touch(now);
    }

    /** Cennik usługi; cena „od…” staje się ceną oferty, a obszar działania jej lokalizacją. */
    public void changeServiceDetails(long priceFrom, String unit, String area, int durationMin, int depositPercent, Instant now) {
        this.priceAmount = priceFrom;
        this.serviceUnit = unit;
        this.serviceArea = area;
        this.location = area;
        this.serviceDurationMin = durationMin;
        this.serviceDepositPercent = depositPercent;
        touch(now);
    }

    public void changeStock(int stock, Instant now) {
        this.stock = stock;
        touch(now);
    }

    public void publish(Instant now) {
        this.status = OfferStatus.PUBLISHED;
        if (publishedAt == null) {
            this.publishedAt = now;
        }
        touch(now);
    }

    public void pause(Instant now) {
        this.status = OfferStatus.PAUSED;
        touch(now);
    }

    public void delete(Instant now) {
        this.deletedAt = now;
        touch(now);
    }

    public boolean hasServiceDetails() {
        return serviceUnit != null;
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public OfferType getType() {
        return type;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public long getPriceAmount() {
        return priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public String getCondition() {
        return condition;
    }

    public Integer getStock() {
        return stock;
    }

    public String getLocation() {
        return location;
    }

    public UUID getVideoId() {
        return videoId;
    }

    public String getServiceUnit() {
        return serviceUnit;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public Integer getServiceDurationMin() {
        return serviceDurationMin;
    }

    public Integer getServiceDepositPercent() {
        return serviceDepositPercent;
    }

    public List<Photo> getPhotos() {
        return List.copyOf(photos);
    }

    public List<Param> getParams() {
        return List.copyOf(params);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Embeddable
    public record Photo(@Column(name = "media_id") UUID mediaId, String url) {
    }

    @Embeddable
    public record Param(String label, String value) {
    }
}
