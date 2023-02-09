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
import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(WebhookConfigurationProperties.class)
@PropertySource(value = "classpath:three-clients.yml", factory = YamlPropertySourceFactory.class)
public class WebhookConfigurationThreeClientsConfigurationTest {

    @Autowired
    WebhookConfigurationProperties webhookProperties;
    @Test
    void when_singleClient_isConfigured_expect_ok() {
        Assertions.assertEquals("content-grid2.events", webhookProperties.getQueue());        
        Assertions.assertEquals(20, webhookProperties.getRequestTimeout());
        
        List<WebhookClientConfig> clients = webhookProperties.getClient();
        Assertions.assertEquals(3, clients.size());
        
        WebhookClientConfig clientOne = clients.get(0);
        List<WebhookClientEndpointConfig> clientOneEndpoints = clientOne.getEndpoints();
        Assertions.assertEquals(1, clientOneEndpoints.size());
        WebhookClientEndpointConfig clientOneEndpointConfig = clientOneEndpoints.get(0);
        Assertions.assertEquals(URI.create("http://localhost:9999/hooksite10"), clientOneEndpointConfig.getUri());
        Assertions.assertEquals("abcde", clientOneEndpointConfig.getSecret());
        Map<String, String> filterOne = clientOne.getFilter();
        Assertions.assertEquals(2, filterOne.size());
        Assertions.assertEquals("created10", filterOne.get("action"));
        Assertions.assertEquals("name10", filterOne.get("application"));
        
        WebhookClientConfig clientTwo = clients.get(1);
        List<WebhookClientEndpointConfig> clientTwoEndpoints = clientTwo.getEndpoints();
        Assertions.assertEquals(1, clientTwoEndpoints.size());
        WebhookClientEndpointConfig clientTwoEndpointConfig = clientTwoEndpoints.get(0);
        Assertions.assertEquals(URI.create("http://localhost:8888/hooksite1"), clientTwoEndpointConfig.getUri());
        Assertions.assertEquals("abcdef", clientTwoEndpointConfig.getSecret());
        Map<String, String> filterTwo = clientTwo.getFilter();
        Assertions.assertEquals(3, filterTwo.size());
        Assertions.assertEquals("created", filterTwo.get("action"));
        Assertions.assertEquals("name1", filterTwo.get("application"));
        Assertions.assertEquals("account-state-zip", filterTwo.get("type"));
        
        WebhookClientConfig clientThree = clients.get(2);
        List<WebhookClientEndpointConfig> clientThreeEndpoints = clientThree.getEndpoints();
        Assertions.assertEquals(1, clientThreeEndpoints.size());
        WebhookClientEndpointConfig clientThreeEndpointConfig = clientThreeEndpoints.get(0);
        Assertions.assertEquals(URI.create("http://localhost:8080/hooksite3"), clientThreeEndpointConfig.getUri());
        Assertions.assertEquals("abcdefg", clientThreeEndpointConfig.getSecret());
        Map<String, String> filterThree = clientThree.getFilter();
        Assertions.assertEquals(3, filterThree.size());
        Assertions.assertEquals("created", filterThree.get("action"));
        Assertions.assertEquals("name1", filterThree.get("application"));
        Assertions.assertEquals("account-state", filterThree.get("type"));
    }
}
