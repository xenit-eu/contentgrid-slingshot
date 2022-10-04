package eu.xenit.contentgrid.webhooks;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @PostMapping("hooksite3")
    public void test(@RequestHeader("content-grid.hash") String hash, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestBody(required = false) String payload) {
        System.out.println();
    }
}
