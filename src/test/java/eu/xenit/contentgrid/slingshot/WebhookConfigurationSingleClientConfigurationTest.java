package eu.xenit.contentgrid.slingshot;

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

import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(WebhookConfigurationProperties.class)
@PropertySource(value = "classpath:single-client.yml", factory = YamlPropertySourceFactory.class)
public class WebhookConfigurationSingleClientConfigurationTest {

    @Autowired
    WebhookConfigurationProperties webhookProperties;

    @Test
    void when_singleClient_isConfigured_expect_configurationOk() {
        Assertions.assertEquals("content-grid.events2", webhookProperties.getQueue());
        Assertions.assertEquals(10, webhookProperties.getRequestTimeout());

        List<WebhookClientConfig> clients = webhookProperties.getClient();
        Assertions.assertEquals(1, clients.size());

        WebhookClientConfig client = clients.get(0);
        Assertions.assertNotNull(client);

        List<WebhookClientEndpointConfig> endpoints = client.getEndpoints();
        Assertions.assertEquals(1, endpoints.size());

        WebhookClientEndpointConfig endpointConfig = endpoints.get(0);

        Assertions.assertEquals(URI.create("http://localhost:9999/hooksite1"),
                endpointConfig.getUri());
        Assertions.assertEquals("abcd", endpointConfig.getSecret());

        Map<String, String> filter = client.getFilter();
        Assertions.assertEquals(2, filter.size());
        Assertions.assertEquals("created", filter.get("action"));
        Assertions.assertEquals("name1", filter.get("application"));

        // WebClientEndpointConfig clientEndpointConfig = new
        // WebClientEndpointConfig(endpointConfig.getUri(), endpointConfig.getSecret(),
        // filter);
    }
}
