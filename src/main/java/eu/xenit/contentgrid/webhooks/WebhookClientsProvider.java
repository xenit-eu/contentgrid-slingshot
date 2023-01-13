package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;

public interface WebhookClientsProvider {

    public static enum MANDATORY_HEADERS {
        applicationId, deploymentId, type/* , version */
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
                .anyMatch(e -> e.name().toLowerCase().equals(headerName.toLowerCase()));
    }

    public static boolean hasAllMandatoryHeaders(Map<String, ?> filter) {
        int length = MANDATORY_HEADERS.values().length;
        if (filter == null || filter.size() < length) {
            return false;
        }
        return filter.entrySet().stream().filter(e -> isMandatoryHeader(e.getKey()))
                .filter(k -> k != null).collect(Collectors.counting()) == length;
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
        final String endpoint;

        WebClientEndpointConfig(URI endpoint, String secret, Map<String, String> filters) {
            Assert.notNull(endpoint, "endpoint cannot be null");
            Assert.notNull(filters, "filters cannot be null");

            this.endpoint = endpoint.toString();
            this.secret = secret;
            this.filters = filters;
            this.webClient = WebClient.builder().baseUrl(this.endpoint).build();
        }

    }

    public static class InMemoryWebhookClientsProvider implements WebhookClientsProvider {
        private final List<WebClientEndpointsConfig> clients;

        public InMemoryWebhookClientsProvider(List<WebhookClientConfig> clients) {
            // Assert.notNull(clients, "clients cannot be null");
            this.clients = clients == null ? Collections.emptyList()
                    : clients.stream().map(client -> new WebClientEndpointsConfig(
                            client.getFilter(),
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

    public static class ContentGridApiWebhookClientsProvider implements WebhookClientsProvider {
        private final Map<String, List<WebClientEndpointsConfig>> deploymentIdClientsMap = new HashMap<>();

        public List<WebClientEndpointsConfig> getClients(Map<String, String> headers) {
            String webhookConfigUrl = headers.get("webhookConfigUrl");
            //String deployment = headers.get("deploymentId");
            if (!StringUtils.hasText(webhookConfigUrl)/* || !StringUtils.hasText(deployment)*/) {
                // TODO add log
                return Collections.emptyList();
            }

            if (deploymentIdClientsMap.containsKey(webhookConfigUrl)) {
                List<WebClientEndpointsConfig> list = deploymentIdClientsMap.get(webhookConfigUrl);
                return list;
            } else {
                WebClient webClient = WebClient.builder().baseUrl(webhookConfigUrl).build();
                try {
                    WebhookConfigResponse clients = webClient.get().retrieve().bodyToMono(
                            new ParameterizedTypeReference<WebhookConfigResponse>() {
                            }).block();

                    List<WebClientEndpointsConfig> collect = clients.getWebhooks().getClient().stream()
                            .map(client -> new WebClientEndpointsConfig(client.getFilter(),
                                    client.getEndpoints().stream()
                                            .map(e -> new WebClientEndpointConfig(e.getUri(),
                                                    e.getSecret(), client.getFilter()))
                                            .collect(Collectors.toList())))
                            .collect(Collectors.toList());

                    deploymentIdClientsMap.put(webhookConfigUrl, collect);
                    return collect;
                } catch (Throwable e) {
                    // TODO add log
                    e.printStackTrace();
                }
            }

            return Collections.emptyList();
        }

        public static class WebhookClientConfigResponse {
            private List<WebhookClientConfig> client;
            
            public void setClient(List<WebhookClientConfig> client) {
                this.client = client;
            }

            public List<WebhookClientConfig> getClient() {
                return client != null ? client : Collections.emptyList();
            }
        }
        
        public static class WebhookConfigResponse {
            private WebhookClientConfigResponse webhooks;

            public WebhookClientConfigResponse getWebhooks() {
                return webhooks != null ? webhooks : new WebhookClientConfigResponse();
            }
            
            public void setWebhooks(WebhookClientConfigResponse webhooks) {
                this.webhooks = webhooks;
            }
        }
    }
}
