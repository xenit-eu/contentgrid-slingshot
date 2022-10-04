package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;

public interface WebhookClientsProvider {

    public static enum MANDATORY_HEADERS {
        application, action, type, version
    }

    /**
     * since prometheus can deal only with a static set of tags this method is used
     * to check what we call static metric tags and the mandatory list is based on
     * <code>MANDATORY_HEADERS</code>
     */
    public static boolean isMandatoryHeader(String headerName) {
        if (!StringUtils.hasText(headerName)) {
            return false;
        }

        return Arrays.stream(MANDATORY_HEADERS.values())
                .anyMatch(e -> e.name().equals(headerName.toLowerCase()));
    }

    public static boolean hasAllMandatoryHeaders(Map<String, ?> filter) {
        int length = MANDATORY_HEADERS.values().length;
        if (filter == null || filter.size() < length) {
            return false;
        }
        return filter.entrySet().stream().filter(e -> isMandatoryHeader(e.getKey()))
                .filter(e -> e.getValue() != null).collect(Collectors.counting()) == length;
    }

    /**
     * @return should never return null
     */
    public List<WebClientEndpointsConfig> getClients(Map<String, String> headers);

    static class WebClientEndpointsConfig {
        final Map<String, String> filters;
        final List<WebClientEndpointConfig> endpoints;

        WebClientEndpointsConfig(Map<String, String> filters,
                List<WebClientEndpointConfig> endpoints) {
            Assert.notNull(filters, "filters cannot be null");
            Assert.notEmpty(endpoints, "endpoints cannot be empty");

            this.filters = filters;
            this.endpoints = endpoints;
        }

        public List<WebClientEndpointConfig> getEndpoints() {
            return endpoints;
        }

        public Map<String, String> getFilters() {
            return filters;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("WebClientEndpointsConfig");
            sb.append(" [filters=").append(this.filters).append(']');
            return sb.toString();
        }
    }

    static class WebClientEndpointConfig {
        final WebClient webClient;
        final Map<String, String> filters;
        final String secret;

        WebClientEndpointConfig(URI endpoint, String secret, Map<String, String> filters) {
            Assert.notNull(endpoint, "endpoint cannot be null");
            Assert.notNull(filters, "filters cannot be null");

            Assert.isTrue(WebhookClientsProvider.hasAllMandatoryHeaders(filters),
                    "static values are not provided");

            this.webClient = WebClient.builder().baseUrl(endpoint.toString()).build();
            this.secret = secret;
            this.filters = filters;
        }

    }

    public static class InMemoryWebhookClientsProvider implements WebhookClientsProvider {
        private final List<WebClientEndpointsConfig> clients;

        public InMemoryWebhookClientsProvider(List<WebhookClientConfig> clients) {
            Assert.notNull(clients, "clients cannot be null");
            this.clients = clients.stream()
                    .map(client -> new WebClientEndpointsConfig(client.getFilter(),
                            client.getEndpoints().stream()
                                    .map(e -> new WebClientEndpointConfig(e.getUri(), e.getSecret(),
                                            client.getFilter()))
                                    .collect(Collectors.toList())))
                    .collect(Collectors.toList());
        }

        public List<WebClientEndpointsConfig> getClients(Map<String, String> headers) {
            return clients.stream()
                    .filter(client -> WebhookPublisher.areMatchingHeaders(client.filters, headers))
                    .collect(Collectors.toList());
        }
    }

    public static class DatabaseWebhookClientsProvider implements WebhookClientsProvider {

        public List<WebClientEndpointsConfig> getClients(Map<String, String> headers) {
            return List.of();
        }
    }
}
