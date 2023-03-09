package eu.xenit.contentgrid.slingshot;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;

@ConfigurationProperties("contentgrid.webhooks")
public class WebhookConfigurationProperties {
    
    public static final Long REQUEST_TIMEOUT_DEFAULT = 5L;

    private String queue = "contentgrid.events";
    private Long requestTimeout = REQUEST_TIMEOUT_DEFAULT;
    private List<WebhookClientConfig> client;
    private WebhookSigningConfig signing;
    
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
    
    public WebhookSigningConfig getSigning() {
        return signing;
    }
    
    public void setSigning(WebhookSigningConfig signing) {
        this.signing = signing;
    }
    
    public static class WebhookSigningConfig {
        private WebhookJWKConfig jwt;
        
        public WebhookJWKConfig getJwt() {
            return jwt;
        }
        
        public void setJwt(WebhookJWKConfig jwt) {
            this.jwt = jwt;
        }
    }
    
    public static class WebhookJWKConfig {
        private List<Resource> retiredKeys;
        private Resource signingKey;
        private boolean generateKey = false;
        private URI issuer;
        
        public List<Resource> getRetiredKeys() {
            return retiredKeys;
        }
        
        public void setRetiredKeys(List<String> retiredKeys) {
            Assert.notEmpty(retiredKeys, "retiredKeys cannot be empty");
            
            PathMatchingResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();            
            this.retiredKeys = retiredKeys.stream().map(key -> {
                try {
                    return Arrays.asList(patternResolver.getResources(key));                    
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).flatMap(resources -> resources.stream()).collect(Collectors.toList());
        }
        
        public Resource getSigningKey() {
            return signingKey;
        }
        
        public void setSigningKey(Resource signingKey) {
            Assert.notNull(signingKey, "signingKey cannot be null");
            this.signingKey = signingKey;
        }
        
        public boolean isGenerateKey() {
            return generateKey;
        }
        
        public void setGenerateKey(boolean generateKey) {
            this.generateKey = generateKey;
        }
        
        public URI getIssuer() {
            return issuer;
        }
        
        public void setIssuer(URI issuer) {
            this.issuer = issuer;
        }
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

            public URI getUri() {
                return uri;
            }

            public void setUri(URI uri) {
                Assert.notNull(uri, "uri cannot be null");
                this.uri = uri;
            }
        }

    }
}
