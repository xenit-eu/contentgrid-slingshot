package eu.xenit.contentgrid.slingshot;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebConfigProviderResponse.WebConfigProviderStatus;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import reactor.netty.http.client.HttpClient;

// TODO could be renamed to WebhookConfigProvider and webhooks.client should become webhooks.config
public interface WebhookClientsProvider {

    public static enum MANDATORY_HEADERS {
        application_id, deployment_id, trigger, entity
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
     * @return list of webhooks client configuration but should never return null or
     *         throw any exception
     */
    public WebConfigProviderResponse getClients(Map<String, String> headers);

    static class WebConfigProviderResponse {
        private final List<WebClientEndpointsConfig> configList;
        private final WebConfigProviderStatus status;

        static enum WebConfigProviderStatus {
            success, config_location_missing, cached, failure
        }

        WebConfigProviderResponse() {
            this(WebConfigProviderStatus.success);
        }

        WebConfigProviderResponse(WebConfigProviderStatus status) {
            this(status, Collections.emptyList());
        }

        WebConfigProviderResponse(List<WebClientEndpointsConfig> configList) {
            this(WebConfigProviderStatus.success, configList);
        }

        WebConfigProviderResponse(WebConfigProviderStatus status,
                List<WebClientEndpointsConfig> configList) {
            Assert.notNull(configList, "configList cannot be null");
            Assert.notNull(status, "status cannot be null");
            this.configList = configList;
            this.status = status;
        }

        public List<WebClientEndpointsConfig> getConfigList() {
            return configList;
        }

        public WebConfigProviderStatus getStatus() {
            return status;
        }

        public boolean isProviderStatusOk() {
            return WebConfigProviderStatus.success.equals(this.status)
                    || WebConfigProviderStatus.cached.equals(this.status);
        }
    }

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
        final String endpoint;

        WebClientEndpointConfig(URI endpoint, Map<String, String> filters) {
            Assert.notNull(endpoint, "endpoint cannot be null");
            Assert.notNull(filters, "filters cannot be null");

            this.endpoint = endpoint.toString();
            this.filters = filters;
            this.webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(
                            HttpClient.create().followRedirect(false)))
                    .baseUrl(this.endpoint).build();
        }
    }

    static class InMemoryWebhookClientsProvider implements WebhookClientsProvider {
        private final List<WebClientEndpointsConfig> clients;

        public InMemoryWebhookClientsProvider(List<WebhookClientConfig> clients) {
            // Assert.notNull(clients, "clients cannot be null");
            this.clients = clients == null ? Collections.emptyList()
                    : clients.stream().map(client -> new WebClientEndpointsConfig(
                            client.getFilter(),
                            client.getEndpoints().stream()
                                    .map(e -> new WebClientEndpointConfig(e.getUri(),
                                            client.getFilter()))
                                    .collect(Collectors.toList())))
                            .collect(Collectors.toList());
        }

        public WebConfigProviderResponse getClients(Map<String, String> headers) {
            return new WebConfigProviderResponse(clients.stream()
                    .filter(client -> WebhookPublisher.areMatchingHeaders(client.filters, headers))
                    .collect(Collectors.toList()));
        }
    }

    static class ContentGridApiWebhookClientsProvider implements WebhookClientsProvider {
        private final Map<String, List<WebClientEndpointsConfig>> deploymentIdClientsMap = new HashMap<>();

        public WebConfigProviderResponse getClients(Map<String, String> headers) {
            if(headers == null) {
                return new WebConfigProviderResponse(
                        WebConfigProviderStatus.config_location_missing);
            }
            
            String webhookConfigUrl = headers.get("webhookConfigUrl");
            if (!StringUtils.hasText(webhookConfigUrl)) {
                // this is a safety check, we should not even come in here if this header is not
                // present
                return new WebConfigProviderResponse(
                        WebConfigProviderStatus.config_location_missing);
            }

            if (deploymentIdClientsMap.containsKey(webhookConfigUrl)) {
                List<WebClientEndpointsConfig> list = deploymentIdClientsMap.get(webhookConfigUrl);
                return new WebConfigProviderResponse(WebConfigProviderStatus.cached, list);
            }

            try {
                WebClient webClient = WebClient.builder().baseUrl(webhookConfigUrl).build();
                WebhookConfigResponse clients = webClient.get().retrieve()
                        .bodyToMono(new ParameterizedTypeReference<WebhookConfigResponse>() {
                        }).block();

                List<WebClientEndpointsConfig> list = clients.getWebhooks().getClient().stream()
                        .map(client -> new WebClientEndpointsConfig(client.getFilter(),
                                client.getEndpoints().stream()
                                        .map(e -> new WebClientEndpointConfig(e.getUri(),
                                                client.getFilter()))
                                        .collect(Collectors.toList())))
                        .collect(Collectors.toList());

                deploymentIdClientsMap.put(webhookConfigUrl, list);
                return new WebConfigProviderResponse(list);
            } catch (Throwable ex) {
                return new WebConfigProviderResponse(WebConfigProviderStatus.failure);
            }
        }

        static class WebhookClientConfigResponse {
            private List<WebhookClientConfig> client;

            public void setClient(List<WebhookClientConfig> client) {
                this.client = client;
            }

            public List<WebhookClientConfig> getClient() {
                return client != null ? client : Collections.emptyList();
            }
        }

        static class WebhookConfigResponse {
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
