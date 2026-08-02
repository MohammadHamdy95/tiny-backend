package app.hamdy.shortener.web.dto;

import java.util.List;

public record LinkListResponse(List<LinkResponse> items, String nextCursor) {
}
