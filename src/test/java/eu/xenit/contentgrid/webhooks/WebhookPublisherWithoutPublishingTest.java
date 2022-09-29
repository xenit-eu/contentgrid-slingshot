package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import eu.xenit.contentgrid.webhooks.WebhookClientsProvider.WebClientEndpointConfig;
import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;

public class WebhookPublisherWithoutPublishingTest {

    @Test
    void when_singleClient_isConfiguredWithoutFilters_expect_ok() {
        WebhookClientConfig clientConfig = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig.setFilter(Map.of());

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig));
        Assertions.assertEquals(1, inMemoryProvider.getClients().size());

        WebhookPublisher publisher = new WebhookPublisher(List.of(inMemoryProvider));

        List<WebClientEndpointConfig> oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(1, oneClientFound.size());

        // publisher.handleEvent(new GenericMessage<String>("payload_test", Map.of()));
    }

    @Test
    void when_twoClients_areConfiguredWithoutAndWithoutFilters_expect_ok() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig1.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig1.setFilter(Map.of());

        WebhookClientConfig clientConfig2 = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig2.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig2.setFilter(Map.of("test", "test"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig1, clientConfig2));
        Assertions.assertEquals(2, inMemoryProvider.getClients().size());

        WebhookPublisher publisher = new WebhookPublisher(List.of(inMemoryProvider));

        List<WebClientEndpointConfig> oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "test2", "test2"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(1, oneClientFound.size());

        // publisher.handleEvent(new GenericMessage<String>("payload_test", Map.of()));
    }

    @Test
    void when_singleClient_isConfiguredWithFilters_expect_ok() {
        WebhookClientConfig clientConfig = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig.setFilter(Map.of("test", "test"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig));
        Assertions.assertEquals(1, inMemoryProvider.getClients().size());

        WebhookPublisher publisher = new WebhookPublisher(List.of(inMemoryProvider));

        List<WebClientEndpointConfig> oneClientFound = publisher
                .findMatchingClients(Map.of("test", "test"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "test2", "test2"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(0, oneClientFound.size());
    }

    @Test
    void when_twoClients_areConfiguredWithFilters_expect_ok() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig1.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig1.setFilter(Map.of("test2", "test2"));

        WebhookClientConfig clientConfig2 = new WebhookConfigurationProperties.WebhookClientConfig();
        clientConfig2.setEndpoint(URI.create("http://mockserver/hook1"));
        clientConfig2.setFilter(Map.of("test", "test"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig1, clientConfig2));
        Assertions.assertEquals(2, inMemoryProvider.getClients().size());

        WebhookPublisher publisher = new WebhookPublisher(List.of(inMemoryProvider));

        List<WebClientEndpointConfig> oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test2", "test2"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "test2", "test2"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("noresult", "noresult"));
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(0, oneClientFound.size());
    }
}
