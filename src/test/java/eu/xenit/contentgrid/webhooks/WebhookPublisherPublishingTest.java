package eu.xenit.contentgrid.webhooks;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig;
import eu.xenit.contentgrid.webhooks.WebhookConfigurationProperties.WebhookClientConfig.WebhookClientEndpointConfig;
import eu.xenit.contentgrid.webhooks.WebhookPublisher.FluxData;
import eu.xenit.contentgrid.webhooks.WebhookPublisher.WebhookDeliveryException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@WebMvcTest(TestController.class)
public class WebhookPublisherPublishingTest {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    WebhookConfigurationProperties props = new WebhookConfigurationProperties();

//    @Test
//    void when_publisherHandlesMessage_expect_reactiveSubscribeOk() {
//        WebhookClientConfig clientConfig = new WebhookConfigurationProperties.WebhookClientConfig();
//        clientConfig.setEndpoint(URI.create("http://mockserver/hook1"));
//        clientConfig.setFilter(Map.of());
//
//        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
//                List.of(clientConfig));
//        Assertions.assertEquals(1, inMemoryProvider.getClients().size());
//
//        WebhookPublisher publisher = new WebhookPublisher(List.of(inMemoryProvider));
//        publisher.handleEvent(new GenericMessage<String>("payload_test", Map.of()));
//    }

    @Test
    void when_endpointIsNotReachable_expect_WebhookDeliveryException() {
        WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
        WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
        clientConfig1EndpointConfig.setUri(URI.create("http://mockserver/hook1"));
        clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
        clientConfig1.setFilter(
                Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"));

        WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                List.of(clientConfig1));
        Assertions.assertEquals(1, inMemoryProvider.getClients(
                Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"))
                .size());

        WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider), meterRegistry);
        FluxData fluxData = publisher.fluxData(
                Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"),
                "payload_test", null, null);

        Assertions.assertEquals(1, fluxData.getSize());

        StepVerifier.create(fluxData.getFlux()).expectError(WebhookDeliveryException.class)
                .verifyThenAssertThat();
    }

    @Test
    void when_endpointIsAvailableWithoutHMAC_expect_ok()
            throws IOException, InterruptedException, URISyntaxException {
        try (MockWebServer mockBackEnd = new MockWebServer()) {
            mockBackEnd.enqueue(new MockResponse().setResponseCode(200));

            WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
            WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
            clientConfig1EndpointConfig.setUri(mockBackEnd.url("/").url().toURI());
            clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
            clientConfig1.setFilter(Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type",
                    "version", "v1"));

            WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                    List.of(clientConfig1));
            Assertions.assertEquals(1, inMemoryProvider.getClients(
                    Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"))
                    .size());

            WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider),
                    meterRegistry);
            FluxData fluxData = publisher.fluxData(
                    Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"),
                    "payload_test",
                    WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME, null);
            Assertions.assertEquals(1, fluxData.getSize());

            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();

            RecordedRequest recordedRequest = mockBackEnd.takeRequest();
            Assertions.assertEquals("-none-", recordedRequest
                    .getHeader(WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME));
        }
    }

    @Test
    void when_endpointIsAvailableWithHMAC_expect_ok()
            throws IOException, InterruptedException, URISyntaxException {
        try (MockWebServer mockBackEnd = new MockWebServer()) {
            mockBackEnd.enqueue(new MockResponse().setResponseCode(200));

            WebhookClientConfig clientConfig1 = new WebhookConfigurationProperties.WebhookClientConfig();
            WebhookClientEndpointConfig clientConfig1EndpointConfig = new WebhookClientEndpointConfig();
            clientConfig1EndpointConfig.setUri(mockBackEnd.url("/").url().toURI());
            clientConfig1EndpointConfig.setSecret("secretKey");
            clientConfig1.setEndpoints(List.of(clientConfig1EndpointConfig));
            clientConfig1.setFilter(Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type",
                    "version", "v1"));

            WebhookClientsProvider inMemoryProvider = new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                    List.of(clientConfig1));
            Assertions.assertEquals(1, inMemoryProvider.getClients(
                    Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"))
                    .size());

            WebhookPublisher publisher = new WebhookPublisher(props, List.of(inMemoryProvider),
                    meterRegistry);
            FluxData fluxData = publisher.fluxData(
                    Map.of("applicationId", "app1", "deploymentId", "abcd", "action", "act", "type", "type", "version", "v1"),
                    "payload_test", WebhookConfigurationProperties.HEADERNAME_DEFAULT,
                    null);
            Assertions.assertEquals(1, fluxData.getSize());

            StepVerifier.create(fluxData.getFlux())
                    .expectNextMatches(result -> result.getStatusCode() == HttpStatus.OK)
                    .verifyComplete();

            RecordedRequest recordedRequest = mockBackEnd.takeRequest();

            byte[] bytes = clientConfig1EndpointConfig.getSecret().getBytes(StandardCharsets.UTF_8);
            String hash = WebhookPublisher.hmac(bytes, "payload_test");
            Assertions.assertEquals(hash,
                    recordedRequest.getHeader(WebhookConfigurationProperties.HEADERNAME_DEFAULT));
        }
    }

    // TODO test how many retries have been executed
}
