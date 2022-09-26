package eu.xenit.contentgrid.webhooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;

import com.google.common.hash.Hashing;

import eu.xenit.contentgrid.webhooks.WebhookClientsProvider.WebClientEndpointConfig;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class WebhookPublisher {

    private final List<WebhookClientsProvider> providers;

    public WebhookPublisher(List<WebhookClientsProvider> providers) {
        Assert.notNull(providers, "provider cannot be null");

        this.providers = providers;
    }

    @ServiceActivator(inputChannel = WebhookMessageConsumerConfiguration.CHANNEL_NAME)
    public void handleEvent(Message<String> message) throws IOException {
        MessageHeaders messageHeaders = message.getHeaders();

        publish(messageHeaders, message.getPayload(),
                messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME,
                        String.class),
                messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME,
                        Long.class));
    }

    void publish(final Map<String, Object> headers, final String payload, final String headerName,
            final Long requestTimeoutInseconds) {
        List<WebClientEndpointConfig> matchedClients = findMatchingClients(headers);
        if (matchedClients.isEmpty()) {
            return;
        }

        Flux.fromStream(matchedClients.stream())
                .subscribeOn(Schedulers.newParallel("contentgrid", matchedClients.size()))
                .parallel(matchedClients.size()).flatMap(c -> {

                    String hash = null;
                    if (StringUtils.hasText(c.secret)) {
                        byte[] bytes = c.secret.getBytes(StandardCharsets.UTF_8);
                        hash = Hashing.hmacSha256(bytes).hashString(payload, StandardCharsets.UTF_8)
                                .toString();
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

                    return c.webClient.post().contentType(contentType == null ? MediaType.TEXT_PLAIN
                            : MediaType.valueOf(contentType)).headers(h -> {
                                String headerHashValue = hashValue.orElse("-none-");
                                h.add(headerName, headerHashValue);
                            }).body(BodyInserters.fromValue(payload)).retrieve().toBodilessEntity()
                            .timeout(Duration.ofSeconds(requestTimeoutInseconds != null
                                    ? requestTimeoutInseconds
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
                }).subscribe();
    }

    List<WebClientEndpointConfig> findMatchingClients(Map<String, Object> headers) {
        return providers.stream().flatMap(provider -> provider.getClients().stream())
                .filter(client -> areMatchingHeaders(client.filters, headers))
                .collect(Collectors.toList());
    }

    static boolean areMatchingHeaders(Map<String, String> first, Map<String, Object> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return true;
        } else if (first.isEmpty() && !second.isEmpty()) {
            return false;
        }

        return first.entrySet().stream().allMatch(e -> e.getValue().equals(second.get(e.getKey())));
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
