package app.hamdy.shortener.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * links table access via the plain SDK client — explicit attribute maps
 * over the enhanced-client mapper, chosen deliberately: the item ops and
 * expressions are the thing worth learning here. In prod the table comes
 * from Terraform; against DynamoDB Local (endpoint override set) it is
 * created on startup so dev needs zero manual setup.
 */
@Repository
public class LinkRepository {

    private final DynamoDbClient client;
    private final String table;
    private final boolean createTableOnStartup;

    public LinkRepository(DynamoDbClient client,
            @Value("${shortener.table-name}") String table,
            @Value("${aws.dynamodb-endpoint:}") String endpointOverride) {
        this.client = client;
        this.table = table;
        this.createTableOnStartup = !endpointOverride.isBlank();
    }

    @PostConstruct
    void ensureTableForLocalDev() {
        if (!createTableOnStartup) {
            return;
        }
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName(table)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .keySchema(KeySchemaElement.builder()
                            .attributeName("slug").keyType(KeyType.HASH).build())
                    .attributeDefinitions(AttributeDefinition.builder()
                            .attributeName("slug").attributeType(ScalarAttributeType.S).build())
                    .build());
        } catch (ResourceInUseException alreadyExists) {
            // fine — table is there
        }
    }

    /** @return false if the slug is already taken. */
    public boolean putIfAbsent(Link link) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("slug", s(link.slug()));
        item.put("url", s(link.url()));
        item.put("created_at", s(link.createdAt().toString()));
        item.put("expires_at", s(link.expiresAt().toString()));
        item.put("expires_at_epoch", n(link.expiresAt().getEpochSecond()));
        item.put("clicks", n(link.clicks()));
        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(item)
                    .conditionExpression("attribute_not_exists(slug)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException taken) {
            return false;
        }
    }

    public Optional<Link> find(String slug) {
        var response = client.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("slug", s(slug)))
                .build());
        return response.hasItem() ? Optional.of(fromItem(response.item())) : Optional.empty();
    }

    /** Atomic click count + last click timestamp; no-op if the link is gone. */
    public void recordClick(String slug, Instant when) {
        try {
            client.updateItem(UpdateItemRequest.builder()
                    .tableName(table)
                    .key(Map.of("slug", s(slug)))
                    .updateExpression("ADD clicks :one SET last_click_at = :when")
                    .conditionExpression("attribute_exists(slug)")
                    .expressionAttributeValues(Map.of(
                            ":one", n(1),
                            ":when", s(when.toString())))
                    .build());
        } catch (ConditionalCheckFailedException gone) {
            // deleted between redirect lookup and click write — nothing to count
        }
    }

    /** Idempotent. */
    public void delete(String slug) {
        client.deleteItem(DeleteItemRequest.builder()
                .tableName(table)
                .key(Map.of("slug", s(slug)))
                .build());
    }

    public record LinkPage(List<Link> links, String lastSlug) {
    }

    /** Scan page; cursor is the last slug of the previous page. */
    public LinkPage page(int limit, String afterSlug) {
        var scan = ScanRequest.builder().tableName(table).limit(limit);
        if (afterSlug != null) {
            scan.exclusiveStartKey(Map.of("slug", s(afterSlug)));
        }
        var response = client.scan(scan.build());
        List<Link> links = response.items().stream().map(LinkRepository::fromItem).toList();
        String lastSlug = response.hasLastEvaluatedKey()
                ? response.lastEvaluatedKey().get("slug").s()
                : null;
        return new LinkPage(links, lastSlug);
    }

    private static Link fromItem(Map<String, AttributeValue> item) {
        return new Link(
                item.get("slug").s(),
                item.get("url").s(),
                Instant.parse(item.get("created_at").s()),
                Instant.parse(item.get("expires_at").s()),
                item.containsKey("clicks") ? Long.parseLong(item.get("clicks").n()) : 0,
                item.containsKey("last_click_at")
                        ? Instant.parse(item.get("last_click_at").s())
                        : null);
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue n(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }
}
