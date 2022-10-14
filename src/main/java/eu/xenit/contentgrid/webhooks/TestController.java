package eu.xenit.contentgrid.webhooks;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("hooksite3")
    public void test(@RequestHeader("content-grid.hash") String hash,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestBody(required = false) String payload) {
        System.out.println();
    }

    @GetMapping("webhooks")
    public ResponseEntity<?> webhooks() {

        return ResponseEntity.ok(Map.of("webhooks",
                List.of(Map.of("filter", Map.of("action", "created", "application", "test"),
                        "endpoints", List.of(Map.of("secret", "secret", "uri", "uri"))))));
    }
}
