package eu.xenit.contentgrid.slingshot;

import eu.xenit.contentgrid.slingshot.service.JwtService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        String applicationId = UUID.randomUUID().toString();
        String deploymentId = UUID.randomUUID().toString();
        String payload = "{" +
                "  \"name\":\"Matthias\"" +
                "}";
        String expectedPayload = "{" +
                "\"name\":\"Matthias\"," +
                "\"applicationId\":\"" + applicationId + "\"," +
                "\"deploymentId\":\"" + deploymentId + "\"" +
                "}";
        Map<String, String> headers = new HashMap<>() {{
            put("application_id", applicationId);
            put("deployment_id", deploymentId);
            put("trigger", "");
            put("entity", "");
        }};
        WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(), new SimpleMeterRegistry(), buildProperties.getVersion());
        String modifiedPayload = publisher.modifyPayload(headers, payload);

        assertEquals(expectedPayload, modifiedPayload);
    }

}
