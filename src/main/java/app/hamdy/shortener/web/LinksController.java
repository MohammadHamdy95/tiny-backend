package app.hamdy.shortener.web;

import java.net.URI;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.hamdy.shortener.service.LinkService;
import app.hamdy.shortener.web.dto.CreateLinkRequest;
import app.hamdy.shortener.web.dto.LinkResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class LinksController {

    private static final String SLUG = "{slug:[A-Za-z0-9_-]{1,64}}";

    private final LinkService service;
    private final LinkResponse.Factory responses;

    public LinksController(LinkService service, LinkResponse.Factory responses) {
        this.service = service;
        this.responses = responses;
    }

    @PostMapping("/links")
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        var link = service.create(request.url(), request.slug(), request.expiresAt());
        return ResponseEntity.created(URI.create("/api/v1/links/" + link.slug()))
                .body(responses.from(link));
    }

    @GetMapping("/links/" + SLUG)
    public LinkResponse get(@PathVariable String slug) {
        return responses.from(service.get(slug));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
