package app.hamdy.shortener.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Real AWS by default (credentials from the default provider chain —
 * env vars in compose). When aws.dynamodb-endpoint is set (DynamoDB
 * Local in dev/tests), the endpoint is overridden and dummy static
 * credentials are used, since DynamoDB Local accepts anything.
 */
@Configuration
public class DynamoConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${aws.region}") String region,
            @Value("${aws.dynamodb-endpoint:}") String endpointOverride) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }
}
