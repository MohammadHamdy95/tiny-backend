package app.hamdy.shortener.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import app.hamdy.shortener.domain.Link;
import app.hamdy.shortener.service.LinkNotFoundException;
import app.hamdy.shortener.service.LinkService;
import app.hamdy.shortener.service.SlugTakenException;
import app.hamdy.shortener.web.dto.LinkResponse;

@WebMvcTest(controllers = { RedirectController.class, LinksController.class },
        properties = {
                "shortener.admin-token=test-token",
                "shortener.public-url=http://s.localhost"
        })
@Import(LinkResponse.Factory.class)
class WebLayerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkService service;

    private Link link() {
        return new Link("abc1234", "https://example.com/target",
                Instant.parse("2026-08-02T12:00:00Z"),
                Instant.parse("2031-08-01T12:00:00Z"), 3,
                Instant.parse("2026-08-02T13:00:00Z"));
    }

    @Test
    void redirectIs302WithLocationAndNoStore() throws Exception {
        when(service.resolve("abc1234")).thenReturn(link());
        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/target"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unknownSlugIs404Problem() throws Exception {
        when(service.resolve("missing")).thenThrow(new LinkNotFoundException("missing"));
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithTokenReturns201() throws Exception {
        when(service.create(any(), isNull(), isNull())).thenReturn(link());
        mvc.perform(post("/api/v1/links")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/target\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/links/abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://s.localhost/abc1234"))
                .andExpect(jsonPath("$.stats.clicks").value(3));
    }

    @Test
    void takenSlugIs409Problem() throws Exception {
        when(service.create(any(), any(), isNull())).thenThrow(new SlugTakenException("demo"));
        mvc.perform(post("/api/v1/links")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"slug\":\"demo\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidSlugPatternIs400() throws Exception {
        mvc.perform(post("/api/v1/links")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"slug\":\"bad slug!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
