package eu.xenit.contentgrid.slingshot;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForEmptyJson;
import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.nimbusds.jose.crypto.RSASSASigner;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookClientConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.ContentGridApiWebhookClientsProvider.WebhookConfigResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.InMemoryWebhookClientsProvider;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;
import eu.xenit.contentgrid.slingshot.WebhookPublisher.PublishingFlux;
import eu.xenit.contentgrid.slingshot.service.JwtService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.test.StepVerifier;

public final class WebhookPublisherPublishingTest {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    

    Supplier<PrivateKey> privateKey = () -> {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            return kp.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    RSASSASigner signer = new RSASSASigner(privateKey.get());
    JwtService jwtService = new JwtService(signer, URI.create("https://aaa"));

    BuildProperties buildProperties = new BuildProperties(new Properties()) {
        @Override
        public String getVersion() {
            return "1.0.0";
        }
    };
    
    @BeforeEach
    public void clear() {
        meterRegistry.clear();
    }

    @Test
    void when_endpointIsNotReachable_expect_ok() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig1EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
        clientConfig1.setFilter(Map.of("application_id", "app1", "deployment_id", "abcd", "action",
                "act", "trigger", "type", "version", "v1", "entity", "case"));

        WebhookClientsProvider inMemoryProvider = new InMemoryWebhookClientsProvider(List.of(clientConfig1));

        WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(inMemoryProvider), meterRegistry, buildProperties.getVersion());
        PublishingFlux fluxData = publisher.publishingFlux(Map.of("application_id", "app1", "deployment_id",
                "abcd", "action", "act", "trigger", "type", "version", "v1", "webhookConfigUrl",
                "http://test", "entity", "case"), "payload_test");

        Assertions.assertEquals(1, fluxData.getSize());
        
        StepVerifier.create(fluxData.getFlux()).verifyError();
    }

    @Nested
    @ExtendWith(SpringExtension.class)
    @AutoConfigureWireMock(port = 0)
    public class InMemoryConfigProviderWithCustomerEndpointMockTest {

        @Autowired
        WireMockServer wireMock;

        @Test
        void when_webhookEndpointExists_expect_okAndAllCGHeaders() {
            
            String baseUrl = wireMock.baseUrl();
            
            WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
            WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
            clientConfig1EndpointConfig.setUri(URI.create(baseUrl + "/endpoint"));
            clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
            clientConfig1.setFilter(Map.of("application_id", "app1", "deployment_id", "abcd",
                    "action", "act", "trigger", "type", "version", "v1", "entity", "case"));
            
            WebhookClientsProvider inMemoryProvider = new InMemoryWebhookClientsProvider(List.of(clientConfig1));
            
            WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(inMemoryProvider), meterRegistry, buildProperties.getVersion());
            PublishingFlux fluxData = publisher.publishingFlux(Map.of("application_id", "app1", "deployment_id",
                    "abcd", "action", "act", "trigger", "type", "version", "v1", "webhookConfigUrl",                    
                    "http://test", "entity", "case"), "payload_test");
            Assertions.assertEquals(1, fluxData.getSize());
            
            stubFor(post(urlEqualTo("/endpoint"))
                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
                    .withHeader(HttpHeaders.USER_AGENT, new EqualToPattern(publisher.getUserAgentHeaderValueWithVersion()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(new AnythingPattern())
                    .willReturn(okForEmptyJson()));

            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
        
        @Test
        void when_webhookEndpointsExists_expect_okAndAllCGHeaders() {
            
            String baseUrl = wireMock.baseUrl();
            
            WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
            WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
            clientConfig1EndpointConfig.setUri(URI.create(baseUrl + "/endpoint"));
            clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
            clientConfig1.setFilter(Map.of("application_id", "app1", "deployment_id", "abcd",
                    "action", "act", "trigger", "type", "version", "v1", "entity", "case"));
            
            WebhookClientConfig clientConfig2 = new WebhookConfigurationProperties.WebhookClientConfig();
            WebhookClientEndpointConfig clientConfig1EndpointConfig2 = new WebhookClientEndpointConfig();
            clientConfig1EndpointConfig2.setUri(URI.create(baseUrl + "/endpoint"));
            clientConfig2.setEndpoints(List.of(clientConfig1EndpointConfig2));
            clientConfig2.setFilter(Map.of("application_id", "app1", "deployment_id", "abcd",
                    "action", "act", "trigger", "type", "version", "v1", "entity", "case"));
            
            WebhookClientsProvider inMemoryProvider = new InMemoryWebhookClientsProvider(List.of(clientConfig1, clientConfig2));
            
            WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(inMemoryProvider), meterRegistry, buildProperties.getVersion());
            PublishingFlux fluxData = publisher.publishingFlux(Map.of("application_id", "app1", "deployment_id",
                    "abcd", "action", "act", "trigger", "type", "version", "v1", "webhookConfigUrl",
                    baseUrl + "/endpoint", "entity", "case"), "payload_test");
            Assertions.assertEquals(2, fluxData.getSize());
            
            stubFor(post(urlEqualTo("/endpoint"))
                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
                    .withHeader(HttpHeaders.USER_AGENT, new EqualToPattern(publisher.getUserAgentHeaderValueWithVersion()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(new AnythingPattern())
                    .willReturn(okForEmptyJson()));

            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
        }
    }
    
    @Nested
    @ExtendWith(SpringExtension.class)
    @AutoConfigureWireMock(port = 0)
    public class ApiConfigProviderWithCustomerEndpointMockTest {

        @Autowired
        WireMockServer wireMock;
        
        @BeforeEach
        public void clear() {
            meterRegistry.clear();
        }
        
        @Test
        void when_webhookEndpointExistsAndWebhookConfigHasUnkownField_expect_correctResponseDeserialization() {
            
            ContentGridApiWebhookClientsProvider provider = new ContentGridApiWebhookClientsProvider();            
            WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(provider), meterRegistry, buildProperties.getVersion());
            
            String baseUrl = wireMock.baseUrl();
            
            stubFor(get(urlEqualTo("/actuator/webhooks")).willReturn(
                    responseDefinition().withHeader("Content-Type", "application/json").withBody(prepareUnknownFieldResponse(baseUrl))));
            
            stubFor(post(urlEqualTo("/endpoint"))
                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
                    .withHeader(HttpHeaders.USER_AGENT, new EqualToPattern(publisher.getUserAgentHeaderValueWithVersion()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(new EqualToPattern("payload_test"))
                    .willReturn(okForEmptyJson()));
            
            PublishingFlux fluxData = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooks"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            //TODO check metrics
        }
        
        @Test
        void when_webhookEndpointExistsAndWebhookConfigHasMissingField_expect_correctResponseDeserialization() {
            
            ContentGridApiWebhookClientsProvider provider = new ContentGridApiWebhookClientsProvider();            
            WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(provider), meterRegistry, buildProperties.getVersion());
            
            String baseUrl = wireMock.baseUrl();
            
            stubFor(get(urlEqualTo("/actuator/webhooks")).willReturn(
                    responseDefinition().withHeader("Content-Type", "application/json").withBody(prepareMissingFieldResponse())));
            
            stubFor(post(urlEqualTo("/endpoint"))
                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
                    .withHeader(HttpHeaders.USER_AGENT, new EqualToPattern(publisher.getUserAgentHeaderValueWithVersion()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(new EqualToPattern("payload_test"))
                    .willReturn(okForEmptyJson()));
            
            PublishingFlux fluxData = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooks"),
                    "payload_test");
            Assertions.assertEquals(0, fluxData.getSize());
            
            StepVerifier.create(fluxData.getFlux()).verifyComplete();
            
            //TODO check metrics
        }

        @Test
        void when_webhookEndpointExists_expect_okAndAllCGHeaders() {
            
            ContentGridApiWebhookClientsProvider provider = new ContentGridApiWebhookClientsProvider();            
            WebhookPublisher publisher = new WebhookPublisher(jwtService, List.of(provider), meterRegistry, buildProperties.getVersion());
            
            String baseUrl = wireMock.baseUrl();
            
            stubFor(get(urlEqualTo("/actuator/webhooks")).willReturn(okForJson(prepareResponse(baseUrl))));
            stubFor(get(urlEqualTo("/actuator/webhooksOther")).willReturn(okForJson(prepareResponse(baseUrl))));
            
            stubFor(post(urlEqualTo("/endpoint"))
                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
                    .withHeader(HttpHeaders.USER_AGENT, new EqualToPattern(publisher.getUserAgentHeaderValueWithVersion()))
                    .withHeader(HttpHeaders.CONTENT_TYPE, new EqualToPattern(MediaType.APPLICATION_JSON_VALUE))
                    .withRequestBody(new EqualToPattern("payload_test"))
                    .willReturn(okForEmptyJson()));
            
            PublishingFlux fluxData = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooks"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            PublishingFlux fluxData2 = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooks"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData2.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData2.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            PublishingFlux fluxData3 = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooksOther"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData3.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData3.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            PublishingFlux fluxData4 = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooks"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData4.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData4.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            PublishingFlux fluxData5 = publisher.publishingFlux(
                    Map.of("deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                            "version", "v1", "webhookConfigUrl", baseUrl+"/actuator/webhooksOther"),
                    "payload_test");
            Assertions.assertEquals(0, fluxData5.getSize());
            
            StepVerifier.create(fluxData5.getFlux()).verifyComplete();
            
            PublishingFlux fluxData6 = publisher.publishingFlux(
                    Map.of("application_id", "app1", "deployment_id", "abcd",  "entity", "case", "trigger", "create", 
                           "webhookConfigUrl", baseUrl+"/actuator/webhooksOther"),
                    "payload_test");
            Assertions.assertEquals(2, fluxData6.getSize());
            
            // verify that we received back HttpStatus 200
            StepVerifier.create(fluxData6.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();
            
            Counter metricMessages4 = meterRegistry.find("messages").counter();
            Counter metricLookups4 = meterRegistry.find("api_config_lookups").counter();
            Timer metricWebhooks4 = meterRegistry.find("webhooks").timer();
            
            meterRegistry.forEachMeter(m -> {
                System.out.println(m.getId() + " - " +m.measure());
            });
            
            System.out.println();
            
//            // verify that we sent the correct headers
//            verify(postRequestedFor(urlEqualTo("/test"))
//                    .withHeader(WebhookPublisher.CONTENTGRID_APPLICATION_ID_HEADER_NAME, new EqualToPattern("app1")) 
//                    .withHeader(WebhookPublisher.CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME, new EqualToPattern("abcd"))
//                    .withHeader(WebhookPublisher.CONTENTGRID_TOKEN_HEADER_NAME, new AnythingPattern())
//                    .withHeader(HttpHeaders.USER_AGENT, new AnythingPattern()));

        }
    }
    
    WebhookConfigResponse prepareResponse(String baseUrl) {
        WebhookClientConfig webhookClientConfig = new WebhookClientConfig();
        webhookClientConfig.setFilter((Map.of("application_id", "app1", "deployment_id", "abcd", "entity", "case", "trigger", "create")));
        
        WebhookClientEndpointConfig webhookClientEndpointConfig = new WebhookClientEndpointConfig();
        webhookClientEndpointConfig.setUri(URI.create(baseUrl+ "/endpoint"));
        webhookClientConfig.setEndpoints(List.of(webhookClientEndpointConfig));
        
        WebhookClientConfig webhookClientConfig2 = new WebhookClientConfig();
        webhookClientConfig2.setFilter((Map.of("application_id", "app1", "deployment_id", "abcd", "entity", "case", "trigger", "create")));
        
        WebhookClientEndpointConfig webhookClientEndpointConfig2 = new WebhookClientEndpointConfig();
        webhookClientEndpointConfig2.setUri(URI.create(baseUrl+ "/endpoint"));
        webhookClientConfig2.setEndpoints(List.of(webhookClientEndpointConfig2));
        
        WebhookClientConfigResponse clientConfigResponse = new WebhookClientConfigResponse();
        clientConfigResponse.setClient(List.of(webhookClientConfig, webhookClientConfig2));
        
        WebhookConfigResponse webhookConfigResponse = new WebhookConfigResponse();
        webhookConfigResponse.setWebhooks(clientConfigResponse);
        
        return webhookConfigResponse;
    }
    
    String prepareUnknownFieldResponse(String baseUrl) {
        return String.format("""
            {
             "webhooks" : {
               "client" : [ {
                 "filter" : {
                   "trigger" : "create",
                   "deployment_id" : "abcd",
                   "application_id" : "app1",
                   "entity" : "case"
                 },
                 "endpoints" : [ {
                   "uri" : "%1$s/endpoint",
                   "secret": ""
                 } ]
               }, {
                 "filter" : {
                   "trigger" : "create",
                   "deployment_id" : "abcd",
                   "application_id" : "app1",
                   "entity" : "case"
                 },
                 "endpoints" : [ {
                   "uri" : "%1$s/endpoint",
                   "secret": "abcd"
                 } ]
               } ]
             }
           }          
          """, baseUrl);
    }
    
    String prepareMissingFieldResponse() {
        return """
            {
             "webhooks": {
              "client": [{
               "filter": {
                "trigger": "create",
                "deployment_id": "abcd",
                "application_id": "app1",
                "entity": "case"
               }
              }]
             }
            }   
          """;
    }
}
