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
@PropertySource(value = "classpath:three-clients.yml", factory = YamlPropertySourceFactory.class)
public class WebhookConfigurationThreeClientsConfigurationTest {

    @Autowired
    WebhookConfigurationProperties webhookProperties;
    @Test
    void when_singleClient_isConfigured_expect_ok() {
        Assertions.assertEquals("content-grid2.events", webhookProperties.getQueue());
        Assertions.assertEquals("content-grid2.hash", webhookProperties.getHeaderName());
        Assertions.assertEquals(20, webhookProperties.getRequestTimeout());
        
        List<WebhookClientConfig> clients = webhookProperties.getClient();
        Assertions.assertEquals(3, clients.size());
        
        WebhookClientConfig clientOne = clients.get(0);
        Assertions.assertEquals(URI.create("http://localhost:9999/hooksite10"), clientOne.getEndpoint());
        Assertions.assertEquals("abcde", clientOne.getSecret());
        Map<String, String> filterOne = clientOne.getFilter();
        Assertions.assertEquals(2, filterOne.size());
        Assertions.assertEquals("created10", filterOne.get("action"));
        Assertions.assertEquals("name10", filterOne.get("application"));
        
        WebhookClientConfig clientTwo = clients.get(1);
        Assertions.assertEquals(URI.create("http://localhost:8888/hooksite1"), clientTwo.getEndpoint());
        Assertions.assertEquals("abcdef", clientTwo.getSecret());
        Map<String, String> filterTwo = clientTwo.getFilter();
        Assertions.assertEquals(3, filterTwo.size());
        Assertions.assertEquals("created", filterTwo.get("action"));
        Assertions.assertEquals("name1", filterTwo.get("application"));
        Assertions.assertEquals("account-state-zip", filterTwo.get("type"));
        
        WebhookClientConfig clientThree = clients.get(2);
        Assertions.assertEquals(URI.create("http://localhost:8080/hooksite3"), clientThree.getEndpoint());
        Assertions.assertEquals("abcdefg", clientThree.getSecret());
        Map<String, String> filterThree = clientThree.getFilter();
        Assertions.assertEquals(3, filterThree.size());
        Assertions.assertEquals("created", filterThree.get("action"));
        Assertions.assertEquals("name1", filterThree.get("application"));
        Assertions.assertEquals("account-state", filterThree.get("type"));
    }
}
