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

import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
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
    public List<WebClientEndpointsConfig> getClients(Map<String, String> headers);
        
    class ConfigProviderStatus {
        static final ConfigProviderStatus SUCCESS = new ConfigProviderStatus("success"); 
        static final ConfigProviderStatus CACHED = new ConfigProviderStatus("cached");
        static final ConfigProviderStatus FAILURE = new ConfigProviderStatus("failure");
        
        private final String value;
        
        ConfigProviderStatus(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
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

        public List<WebClientEndpointsConfig> getClients(Map<String, String> headers) {
            if(headers == null || headers.isEmpty()) {
                return Collections.emptyList();
            }
            
            return clients.stream()
                    .filter(client -> WebhookPublisher.areMatchingHeaders(client.filters, headers))
                    .collect(Collectors.toList());
        }
    }

    static class ContentGridApiWebhookClientsProvider implements WebhookClientsProvider {
        static final ConfigProviderStatus CONFIG_LOCATION_MISSING = new ConfigProviderStatus("config_location_missing"); 
        
        private final Map<String, List<WebClientEndpointsConfig>> deploymentIdClientsMap = new HashMap<>();
        
        private final MeterRegistry meterRegistry;

        public ContentGridApiWebhookClientsProvider(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        public List<WebClientEndpointsConfig> getClients(Map<String, String> headers) {
            if(headers == null || headers.isEmpty()) {
                recordApiConfigLookupCallMetric(CONFIG_LOCATION_MISSING, headers != null ? headers : Collections.emptyMap());
                return Collections.emptyList();
            }
            
            String webhookConfigUrl = headers.get("webhookConfigUrl");
            if (!StringUtils.hasText(webhookConfigUrl)) {
                // this is a safety check, we should not even come in here if this header is not present
                recordApiConfigLookupCallMetric(CONFIG_LOCATION_MISSING, headers);
                return Collections.emptyList();
            }

            if (deploymentIdClientsMap.containsKey(webhookConfigUrl)) {
                List<WebClientEndpointsConfig> list = deploymentIdClientsMap.get(webhookConfigUrl);
                recordApiConfigLookupCallMetric(ConfigProviderStatus.CACHED, headers);  
                return list != null ? list : Collections.emptyList();
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
                
                recordApiConfigLookupCallMetric(ConfigProviderStatus.SUCCESS, headers);               
                return list != null ? list : Collections.emptyList();
            } catch (Throwable ex) {
                recordApiConfigLookupCallMetric(ConfigProviderStatus.FAILURE, headers);
                return Collections.emptyList();
            }
        }
        
        private void recordApiConfigLookupCallMetric(ConfigProviderStatus webConfigProviderStatus,
                final Map<String, String> headers) {
            // we are sure that all mandatory tags are present
            Tags mandatoryTagsWithValues = Tags.of(headers.entrySet().stream()
                    .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                    .map(e -> Tag.of(e.getKey(), e.getValue())).collect(Collectors.toList()));

            Counter counter = Counter.builder("api_config_lookups").tags(mandatoryTagsWithValues)
                    .tag("status", webConfigProviderStatus.getValue())
                    .description("Number of api config lookup calls").register(meterRegistry);
            counter.increment();
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
