package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.Assert;

@ConfigurationProperties("contentgrid.webhooks")
@RefreshScope
public class WebhookConfigurationProperties {

    public static final Long REQUEST_TIMEOUT_DEFAULT = 5L;

    private String queue = "contentgrid.events"; // queue is not refreshable
    private Long requestTimeout = REQUEST_TIMEOUT_DEFAULT;
    private List<WebhookClientConfig> client;

    public List<WebhookClientConfig> getClient() {
        return client;
    }

    public void setClient(List<WebhookClientConfig> client) {
        this.client = client;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        Assert.hasText(queue, "queue cannot be empty or null");
        this.queue = queue;
    }

    public Long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout) {
        Assert.notNull(requestTimeout, "requestTimeout cannot be null");
        this.requestTimeout = requestTimeout;
    }

    public static class WebhookClientConfig {

        private Map<String, String> filter = new HashMap<>();
        private List<WebhookClientEndpointConfig> endpoints;

        public Map<String, String> getFilter() {
            return Collections.unmodifiableMap(filter);
        }

        public void setFilter(Map<String, String> filter) {
            if (filter != null) {
                this.filter.putAll(filter);
            }
        }

        public List<WebhookClientEndpointConfig> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<WebhookClientEndpointConfig> endpoints) {
            Assert.notNull(endpoints, "endpoints cannot be null");
            
            Optional<WebhookClientEndpointConfig> endpointConfig = endpoints.stream()
                    .filter(endpoint -> endpoint.getUri() == null).findAny();

            endpointConfig.ifPresent(e -> {
                throw new IllegalArgumentException("client must have endpoints configured!");
            });

            this.endpoints = endpoints;
        }

        public static class WebhookClientEndpointConfig {
            private URI uri;
            private String secret;

            public URI getUri() {
                return uri;
            }

            public void setUri(URI uri) {
                Assert.notNull(uri, "uri cannot be null");
                this.uri = uri;
            }

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }
        }

    }
}
