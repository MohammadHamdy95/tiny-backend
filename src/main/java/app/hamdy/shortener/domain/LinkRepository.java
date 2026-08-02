package app.hamdy.shortener.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

/**
 * links table access via the enhanced client: {@link LinkItem} carries
 * the mapping, the atomic-counter extension owns the click count. In
 * prod the table comes from Terraform; against DynamoDB Local
 * (endpoint override set) it is created on startup so dev needs zero
 * manual setup.
 */
@Repository
public class LinkRepository {

    private static final Expression SLUG_ABSENT =
            Expression.builder().expression("attribute_not_exists(slug)").build();
    private static final Expression SLUG_EXISTS =
            Expression.builder().expression("attribute_exists(slug)").build();

    private final DynamoDbTable<LinkItem> table;
    private final boolean createTableOnStartup;

    public LinkRepository(DynamoDbEnhancedClient client,
            @Value("${shortener.table-name}") String tableName,
            @Value("${aws.dynamodb-endpoint:}") String endpointOverride) {
        this.table = client.table(tableName, TableSchema.fromBean(LinkItem.class));
        this.createTableOnStartup = !endpointOverride.isBlank();
    }

    @PostConstruct
    void ensureTableForLocalDev() {
        if (!createTableOnStartup) {
            return;
        }
        try {
            table.createTable();
        } catch (ResourceInUseException alreadyExists) {
            // fine — table is there
        }
    }

    /** @return false if the slug is already taken. */
    public boolean putIfAbsent(Link link) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(LinkItem.class)
                    .item(LinkItem.from(link))
                    .conditionExpression(SLUG_ABSENT)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException taken) {
            return false;
        }
    }

    public Optional<Link> find(String slug) {
        return Optional.ofNullable(table.getItem(key(slug))).map(LinkItem::toLink);
    }

    /**
     * Atomic click count + last click timestamp: ignoreNulls leaves all
     * other attributes untouched while the counter extension increments
     * clicks. No-op if the link is gone.
     */
    public void recordClick(String slug, Instant when) {
        LinkItem patch = new LinkItem();
        patch.setSlug(slug);
        patch.setLastClickAt(when);
        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(LinkItem.class)
                    .item(patch)
                    .ignoreNulls(true)
                    .conditionExpression(SLUG_EXISTS)
                    .build());
        } catch (ConditionalCheckFailedException gone) {
            // deleted between redirect lookup and click write — nothing to count
        }
    }

    private static Key key(String slug) {
        return Key.builder().partitionValue(slug).build();
    }
}
