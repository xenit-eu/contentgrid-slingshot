package eu.xenit.contentgrid.slingshot;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.info.BuildProperties;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties;
import eu.xenit.contentgrid.slingshot.WebhookPublisher;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebClientEndpointsConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;

public class WebhookPublisherWithoutPublishingTest {

    // TODO add test without required provided
    
    WebhookConfigurationProperties props = new WebhookConfigurationProperties();
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    BuildProperties buildProperties = new BuildProperties(new Properties()) {
        @Override
        public String getVersion() {
            return "1.0.0";
        }
    };

    @Test
    void when_singleClient_isConfiguredWithoutFilters_expect_ok() {
        WebhookClientConfig clientConfig = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfigEndpointConfig = new WebhookClientEndpointConfig();
        clientConfigEndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig.setEndpoints(List.of(clientConfigEndpointConfig));
        clientConfig.setFilter(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig));
        Assertions.assertEquals(1, inMemoryProvider.getClients(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"))
                .getConfigList().size());

        WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider), meterRegistry, buildProperties);

        List<WebClientEndpointsConfig> oneClientFound = publisher.findMatchingClients(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "application", "app1",
                "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        // publisher.handleEvent(new GenericMessage<String>("payload_test", Map.of()));
    }

    @Test
    void when_twoClients_areConfiguredWithoutAndWithoutFilters_expect_ok() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig1EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
        clientConfig1.setFilter(Map.of("test2", "test2", "application", "app1", "action", "act",
                "type", "type", "version", "v1"));

        WebhookClientConfig clientConfig2 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig2EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig2EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig2.setEndpoints(List.of(clientConfig2EndpointConfig));
        clientConfig2.setFilter(Map.of("test", "test", "application", "app1", "action", "act",
                "type", "type", "version", "v1"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig1, clientConfig2));
        Assertions
                .assertEquals(2,
                        inMemoryProvider
                                .getClients(Map.of("test2", "test2", "test", "test", "application",
                                        "app1", "action", "act", "type", "type", "version", "v1"))
                                .getConfigList().size());

        WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider), meterRegistry, buildProperties);

        List<WebClientEndpointsConfig> oneClientFound = publisher.findMatchingClients(Map.of("test",
                "test", "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test2", "test2", "test", "test",
                "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "test2", "test2",
                "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test2", "test2", "application",
                "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(0, oneClientFound.size());

        // publisher.handleEvent(new GenericMessage<String>("payload_test", Map.of()));
    }

    @Test
    void when_singleClient_isConfiguredWithFilters_expect_ok() {
        WebhookClientConfig clientConfig = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfigEndpointConfig = new WebhookClientEndpointConfig();
        clientConfigEndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig.setEndpoints(List.of(clientConfigEndpointConfig));
        clientConfig.setFilter(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig));
        Assertions.assertEquals(1, inMemoryProvider.getClients(
                Map.of("application", "app1", "action", "act", "type", "type", "version", "v1"))
                .getConfigList().size());

        WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider), meterRegistry, buildProperties);

        List<WebClientEndpointsConfig> oneClientFound = publisher.findMatchingClients(Map.of("test",
                "test", "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "test2", "test2",
                "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(0, oneClientFound.size());
    }

    @Test
    void when_twoClients_areConfiguredWithFilters_expect_ok() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig1EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
        clientConfig1.setFilter(Map.of("test2", "test2", "application", "app1", "action", "act",
                "type", "type", "version", "v1"));

        WebhookClientConfig clientConfig2 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig2EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig2EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig2.setEndpoints(List.of(clientConfig2EndpointConfig));
        clientConfig2.setFilter(Map.of("test", "test", "application", "app1", "action", "act",
                "type", "type", "version", "v1"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig1, clientConfig2));
        Assertions
                .assertEquals(2,
                        inMemoryProvider
                                .getClients(Map.of("test2", "test2", "test", "test", "application",
                                        "app1", "action", "act", "type", "type", "version", "v1"))
                                .getConfigList().size());

        WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider), meterRegistry, buildProperties);

        List<WebClientEndpointsConfig> oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test", "test", "application", "app1",
                "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test2", "test2", "application",
                "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(1, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("test2", "test2", "test", "test",
                "application", "app1", "action", "act", "type", "type", "version", "v1"));
        Assertions.assertEquals(2, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of("noresult", "noresult"));
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(Map.of());
        Assertions.assertEquals(0, oneClientFound.size());

        oneClientFound = publisher.findMatchingClients(null);
        Assertions.assertEquals(0, oneClientFound.size());
    }
}
