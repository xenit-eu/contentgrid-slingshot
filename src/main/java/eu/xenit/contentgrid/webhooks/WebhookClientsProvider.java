package eu.xenit.contentgrid.webhooks;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;

public interface WebhookClientsProvider {

    /**
     * @return should never return null
     */
    public List<WebClientEndpointConfig> getClients();

    static class WebClientEndpointConfig {
        final WebClient webClient;
        final Map<String, String> filters;
        final String secret;

        WebClientEndpointConfig(WebClient webClient, Map<String, String> filters, String secret) {
            this.webClient = webClient;
            this.filters = filters;
            this.secret = secret;
        }
    }

    public static class InMemoryWebhookClientsProvider implements WebhookClientsProvider {
        private final List<WebClientEndpointConfig> clients;

        public InMemoryWebhookClientsProvider(List<WebhookClientConfig> clients) {
            Assert.notNull(clients, "clients cannot be null");
            this.clients = clients.stream()
                    .map(client -> new WebClientEndpointConfig(
                            WebClient.builder().baseUrl(client.getEndpoint().toString()).build(),
                            client.getFilter(), client.getSecret()))
                    .collect(Collectors.toList());
        }

        public List<WebClientEndpointConfig> getClients() {
            return Collections.unmodifiableList(clients);
        }
    }

    public static class DatabaseWebhookClientsProvider implements WebhookClientsProvider {

        public List<WebClientEndpointConfig> getClients() {
            return List.of();
        }
    }

}
