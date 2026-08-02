package app.hamdy.shortener.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import app.hamdy.shortener.service.LinkService;

/**
 * The public redirect surface: s.hamdy.app/{slug}. 302 (not 301) so
 * caches never swallow clicks or keep deleted links alive — see
 * docs/API.md. Single-segment paths only; /api/** never matches here.
 */
@RestController
public class RedirectController {

    private final LinkService service;

    public RedirectController(LinkService service) {
        this.service = service;
    }

    @GetMapping("/{slug:[A-Za-z0-9_-]{1,64}}")
    public ResponseEntity<Void> redirect(@PathVariable String slug) {
        var link = service.resolve(slug);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.url()))
                .header("Cache-Control", "no-store")
                .build();
    }
}
