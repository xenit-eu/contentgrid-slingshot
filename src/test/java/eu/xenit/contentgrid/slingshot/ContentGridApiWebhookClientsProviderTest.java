package eu.xenit.contentgrid.slingshot;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookClientConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebConfigProviderResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebConfigProviderResponse.WebConfigProviderStatus;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;

public class ContentGridApiWebhookClientsProviderTest {

    @Test
    void when_provider_receivesEmptyHeaders_expect_configListToBeEmpty() {
        WebConfigProviderResponse providerResponse = new WebhookClientsProvider.ContentGridApiWebhookClientsProvider().getClients(null);
        
        Assertions.assertEquals(0, providerResponse.getConfigList().size());
    }
    
    @Test
    void when_provider_receivesUnaccessibleWebhookConfigUrl_expect_configListToBeEmpty() {
        WebConfigProviderResponse providerResponse = new WebhookClientsProvider.ContentGridApiWebhookClientsProvider()
                .getClients(Map.of("webhookConfigUrl", "http://fail"));
        
        Assertions.assertEquals(0, providerResponse.getConfigList().size());
        Assertions.assertEquals(WebConfigProviderStatus.failure, providerResponse.getStatus());
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @AutoConfigureWireMock(port = 0)
    public class ApiLookupTest {

        @Autowired
        WireMockServer wireMock;
        
        @Test
        void when_provider_receivesValidWebhookConfigUrl_expect_retrievedConfigList() {
            
            stubFor(get(urlEqualTo("/actuator/webhooks")).willReturn(okForJson(prepareResponse(""))));
            String baseUrl = wireMock.baseUrl();
            
            ContentGridApiWebhookClientsProvider provider = new WebhookClientsProvider.ContentGridApiWebhookClientsProvider();
            
            // first call => api config is retrieved
            WebConfigProviderResponse providerResponse = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, providerResponse.getConfigList().size());
            Assertions.assertEquals(WebConfigProviderStatus.success, providerResponse.getStatus());
            
            // second call => api config is cached
            WebConfigProviderResponse providerResponse2 = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, providerResponse2.getConfigList().size());
            Assertions.assertEquals(WebConfigProviderStatus.cached, providerResponse2.getStatus());
            
            // third call => api config is cached
            WebConfigProviderResponse providerResponse3 = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, providerResponse3.getConfigList().size());
            Assertions.assertEquals(WebConfigProviderStatus.cached, providerResponse3.getStatus());
        }
        
        WebhookConfigResponse prepareResponse(String baseUrl) {
            WebhookClientConfig webhookClientConfig = new WebhookClientConfig();
            webhookClientConfig.setFilter(Map.of());
            
            WebhookClientEndpointConfig webhookClientEndpointConfig = new WebhookClientEndpointConfig();
            webhookClientEndpointConfig.setUri(URI.create(baseUrl+ "/endpoint"));
            webhookClientConfig.setEndpoints(List.of(webhookClientEndpointConfig));
            
            WebhookClientConfigResponse clientConfigResponse = new WebhookClientConfigResponse();
            clientConfigResponse.setClient(List.of(webhookClientConfig));
            
            WebhookConfigResponse webhookConfigResponse = new WebhookConfigResponse();
            webhookConfigResponse.setWebhooks(clientConfigResponse);
            
            return webhookConfigResponse;
        }

    }

}
