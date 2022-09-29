package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(WebhookConfigurationProperties.class)
@PropertySource(value = "classpath:single-client.yml", factory = YamlPropertySourceFactory.class)
public class WebhookConfigurationSingleClientConfigurationTest {

    @Autowired
    WebhookConfigurationProperties webhookProperties;

    @Test
    void when_singleClient_isConfigured_expect_configurationOk() {
        Assertions.assertEquals("content-grid.events2", webhookProperties.getQueue());
        Assertions.assertEquals("content-grid.hash2", webhookProperties.getHeaderName());
        Assertions.assertEquals(10, webhookProperties.getRequestTimeout());

        List<WebhookClientConfig> clients = webhookProperties.getClient();
        Assertions.assertEquals(1, clients.size());

        WebhookClientConfig client = clients.get(0);
        Assertions.assertEquals(URI.create("http://localhost:9999/hooksite1"),
                client.getEndpoint());
        Assertions.assertEquals("abcd", client.getSecret());

        Map<String, String> filter = client.getFilter();
        Assertions.assertEquals(2, filter.size());
        Assertions.assertEquals("created", filter.get("action"));
        Assertions.assertEquals("name1", filter.get("application"));
    }
}
