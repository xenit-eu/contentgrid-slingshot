package eu.xenit.contentgrid.webhooks;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.Assert;

@ConfigurationProperties("contentgrid.webhooks")
@RefreshScope
public class WebhookConfigurationProperties {

    public static final Long REQUESTTIMEOUT_DEFAULT = 30L;

    private String queue = "content-grid.events"; // queue is not refreshable
    private String headerName = "content-grid.hash";
    private Long requestTimeout = REQUESTTIMEOUT_DEFAULT;
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

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        Assert.hasText(headerName, "headerName cannot be empty or null");
        this.headerName = headerName;
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
        private URI endpoint;
        private String secret;

        public Map<String, String> getFilter() {
            return Collections.unmodifiableMap(filter);
        }

        public void setFilter(Map<String, String> filter) {
            if (filter != null) {
                this.filter.putAll(filter);
            }
        }

        public URI getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(URI endpoint) {
            Assert.notNull(endpoint, "endpoint cannot be null");
            this.endpoint = endpoint;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
