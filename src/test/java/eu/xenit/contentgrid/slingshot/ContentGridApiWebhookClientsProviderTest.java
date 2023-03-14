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
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookClientConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebClientEndpointsConfig;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ConfigProviderStatus;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ContentGridApiWebhookClientsProviderTest {
    

    @Test
    void when_provider_receivesEmptyHeaders_expect_configListToBeEmpty() {
        List<WebClientEndpointsConfig> configList = new ContentGridApiWebhookClientsProvider(new SimpleMeterRegistry()).getClients(null);
        Assertions.assertEquals(0, configList.size());
    }
    
    @Test
    void when_provider_receivesUnaccessibleWebhookConfigUrl_expect_configListToBeEmpty() {
        List<WebClientEndpointsConfig> configList = new ContentGridApiWebhookClientsProvider(new SimpleMeterRegistry())
                .getClients(Map.of("webhookConfigUrl", "http://fail"));
        Assertions.assertEquals(0, configList.size());
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
            
            ContentGridApiWebhookClientsProvider provider = new ContentGridApiWebhookClientsProvider(new SimpleMeterRegistry());
            
            // first call => api config is retrieved
            List<WebClientEndpointsConfig> configList = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, configList.size());
            
            // second call => api config is cached
            List<WebClientEndpointsConfig> configList2 = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, configList2.size());
            
            // third call => api config is cached
            List<WebClientEndpointsConfig> configList3 = provider.getClients(Map.of("webhookConfigUrl", baseUrl+"/actuator/webhooks"));            
            Assertions.assertEquals(1, configList3.size());
            
            // TODO add ContentGridApiWebhookClientsProvider method execution checks or metrics?
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
