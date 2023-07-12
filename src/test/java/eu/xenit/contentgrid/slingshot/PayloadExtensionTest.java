package eu.xenit.contentgrid.slingshot;

import eu.xenit.contentgrid.slingshot.service.JwtService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class PayloadExtensionTest {

    JwtService jwtService = new JwtService(new SigningPrivateKey(new WebhookConfigurationProperties.WebhookJWKConfig(true)), URI.create("https://aaa"));
    BuildProperties buildProperties = new BuildProperties(new Properties()) {
        @Override
        public String getVersion() {
            return "1.0.0";
        }
    };

    @Test
    public void testPayloadExtension() {
        String payload = "{\n" +
                "  \"name\": \"Matthias\"\n" +
                "}";
        Map<String, String> headers = new HashMap<>() {{
            put("application_id", UUID.randomUUID().toString());
            put("deployment_id", UUID.randomUUID().toString());
            put("trigger", "");
            put("entity", "");
        }};
        WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(), new SimpleMeterRegistry(), buildProperties.getVersion());
        
        publisher.modifyPayload(headers, payload);
    }

}
