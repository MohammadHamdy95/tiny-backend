package app.hamdy.shortener.domain;

import java.time.Instant;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAtomicCounter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Enhanced-client persistence shape of a {@link Link}. Kept separate
 * from the immutable domain record: this class carries the mutable
 * bean contract and mapping annotations the mapper needs.
 */
@DynamoDbBean
public class LinkItem {

    private String slug;
    private String url;
    private Instant createdAt;
    private Instant expiresAt;
    private Long expiresAtEpoch;
    private Long clicks;
    private Instant lastClickAt;

    public static LinkItem from(Link link) {
        LinkItem item = new LinkItem();
        item.setSlug(link.slug());
        item.setUrl(link.url());
        item.setCreatedAt(link.createdAt());
        item.setExpiresAt(link.expiresAt());
        item.setExpiresAtEpoch(link.expiresAt().getEpochSecond());
        return item;
    }

    public Link toLink() {
        return new Link(slug, url, createdAt, expiresAt,
                clicks == null ? 0 : clicks, lastClickAt);
    }

    @DynamoDbPartitionKey
    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @DynamoDbAttribute("created_at")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("expires_at")
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** Epoch seconds for the table's DynamoDB TTL. */
    @DynamoDbAttribute("expires_at_epoch")
    public Long getExpiresAtEpoch() {
        return expiresAtEpoch;
    }

    public void setExpiresAtEpoch(Long expiresAtEpoch) {
        this.expiresAtEpoch = expiresAtEpoch;
    }

    /** Incremented by the atomic-counter extension on every update. */
    @DynamoDbAtomicCounter
    public Long getClicks() {
        return clicks;
    }

    public void setClicks(Long clicks) {
        this.clicks = clicks;
    }

    @DynamoDbAttribute("last_click_at")
    public Instant getLastClickAt() {
        return lastClickAt;
    }

    public void setLastClickAt(Instant lastClickAt) {
        this.lastClickAt = lastClickAt;
    }
}
