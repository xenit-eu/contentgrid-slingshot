package eu.xenit.contentgrid.webhooks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import eu.xenit.contentgrid.webhooks.WebhookClientsProvider.WebClientEndpointConfig;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class WebhookPublisher {

    private static Logger LOG = LoggerFactory.getLogger(WebhookPublisher.class);

    private final List<WebhookClientsProvider> providers;

    public WebhookPublisher(List<WebhookClientsProvider> providers) {
        Assert.notNull(providers, "provider cannot be null");

        this.providers = providers;
    }

    @ServiceActivator(inputChannel = WebhookMessageConsumerConfiguration.CHANNEL_NAME)
    public void handleEvent(Message<String> message) {
        LOG.debug("message received: {}", message);

        MessageHeaders messageHeaders = message.getHeaders();

        publishAndSubscribe(messageHeaders, message.getPayload(),
                messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME,
                        String.class),
                messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_REQUESTTIMEOUT,
                        Long.class));
    }

    void publishAndSubscribe(final Map<String, Object> headers, final String payload,
            final String headerName, final Long requestTimeoutInseconds) {

        FluxData fluxData = fluxData(headers, payload, headerName, requestTimeoutInseconds);

        int size = fluxData.size;
        fluxData.flux.subscribeOn(Schedulers.newParallel("contentgrid", size)).parallel(size)
                .subscribe();
    }

    FluxData fluxData(final Map<String, Object> headers, final String payload,
            final String headerName, final Long requestTimeoutInseconds) {
        List<WebClientEndpointConfig> matchedClients = findMatchingClients(headers);
        if (matchedClients.isEmpty()) {
            LOG.debug("no clients matched: {}", headers);
            return new FluxData(Flux.empty(), 0);
        }
        LOG.debug("clients matched: {} for headers: {}", matchedClients, headers);

        Flux<ResponseEntity<Void>> flux = Flux.fromStream(matchedClients.stream()).flatMap(c -> {

            String hash = null;
            if (StringUtils.hasText(c.secret)) {
                byte[] bytes = c.secret.getBytes(StandardCharsets.UTF_8);
                hash = hmac(bytes, payload);
            }

            final Optional<String> hashValue = Optional.ofNullable(hash);

            String contentType = (String) headers.get(HttpHeaders.CONTENT_TYPE);
            if (contentType == null) {
                contentType = (String) headers.get("contentType");
            }

//                    MediaType contentType = (MediaType) headers.compute(HttpHeaders.CONTENT_TYPE,
//                            (k, v) -> v == null ? MediaType.TEXT_PLAIN
//                                    : MediaType.valueOf((String) v));

            // String contentType = (String)headers.compute(HttpHeaders.CONTENT_TYPE, k ->
            // MediaType.TEXT_PLAIN_VALUE);

            return c.webClient.post().contentType(
                    contentType == null ? MediaType.TEXT_PLAIN : MediaType.valueOf(contentType))
                    .headers(h -> {
                        String headerHashValue = hashValue.orElse("-none-");
                        h.add(headerName != null ? headerName
                                : WebhookConfigurationProperties.HEADERNAME_DEFAULT,
                                headerHashValue);
                    }).body(BodyInserters.fromValue(payload)).retrieve().toBodilessEntity()
                    .timeout(Duration
                            .ofSeconds(requestTimeoutInseconds != null ? requestTimeoutInseconds
                                    : WebhookConfigurationProperties.REQUESTTIMEOUT_DEFAULT))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).jitter(0.75)
                            /*
                             * .filter(throwable -> !(throwable instanceof
                             * WebhookDeliveryException))
                             */
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                throw new WebhookDeliveryException(
                                        "External Service failed to process after max retries",
                                        HttpStatus.SERVICE_UNAVAILABLE);
                            }));
        });

        return new FluxData(flux, matchedClients.size());
    }

    class FluxData {
        private final Flux<ResponseEntity<Void>> flux;
        private final int size;

        public FluxData(Flux<ResponseEntity<Void>> flux, int size) {
            this.flux = flux;
            this.size = size;
        }

        public Flux<ResponseEntity<Void>> getFlux() {
            return flux;
        }

        public int getSize() {
            return size;
        }
    }

    List<WebClientEndpointConfig> findMatchingClients(Map<String, Object> headers) {
        return providers.stream().flatMap(provider -> provider.getClients().stream())
                .filter(client -> areMatchingHeaders(client.filters, headers))
                .collect(Collectors.toList());
    }

    static boolean areMatchingHeaders(Map<String, String> first, Map<String, Object> second) {

        if (first == null || first.isEmpty()) {
            return true;
        } else if ((second == null || second.isEmpty()) && !first.isEmpty()) {
            return false;
        }

        return first.entrySet().stream().allMatch(e -> e.getValue().equals(second.get(e.getKey())));
    }
    
    static String hmac(byte[] secretBytes, String payload) {
        return Hashing.hmacSha256(secretBytes).hashString(payload, StandardCharsets.UTF_8).toString();
    }

    @SuppressWarnings("serial")
    static class WebhookDeliveryException extends RuntimeException {

        final HttpStatus status;

        public WebhookDeliveryException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
