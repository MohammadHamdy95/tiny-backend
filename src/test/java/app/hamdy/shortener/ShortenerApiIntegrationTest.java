package app.hamdy.shortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Full-stack test against DynamoDB Local: table bootstrap, anonymous
 * create, redirect + click counting, expiry rules, slug conflicts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShortenerApiIntegrationTest {

    @Container
    static final GenericContainer<?> dynamo =
            new GenericContainer<>("amazon/dynamodb-local:latest").withExposedPorts(8000);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb-endpoint",
                () -> "http://" + dynamo.getHost() + ":" + dynamo.getMappedPort(8000));
        registry.add("shortener.public-url", () -> "http://s.localhost");
    }

    @Autowired
    private MockMvc mvc;

    private String create(String body) throws Exception {
        var result = mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slug");
    }

    @Test
    void createRedirectAndCountClicks() throws Exception {
        String slug = create("{\"url\":\"https://example.com/target\"}");

        mvc.perform(get("/" + slug))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"));
        mvc.perform(get("/" + slug))
                .andExpect(status().isFound());

        mvc.perform(get("/api/v1/links/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.clicks").value(2))
                .andExpect(jsonPath("$.stats.lastClickAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void customSlugConflictIs409() throws Exception {
        create("{\"url\":\"https://example.com\",\"slug\":\"taken-slug\"}");
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/other\",\"slug\":\"taken-slug\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reservedSlugIs409() throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"slug\":\"api\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void pastExpiryIs400() throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void expiryBeyondDefaultTtlIs400() throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonHttpUrlIs400() throws Exception {
        mvc.perform(post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://example.com/file\"}"))
                .andExpect(status().isBadRequest());
    }
}
