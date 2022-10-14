package eu.xenit.contentgrid.webhooks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

import com.google.common.hash.Hashing;

import eu.xenit.contentgrid.webhooks.WebhookClientsProvider.MANDATORY_HEADERS;
import eu.xenit.contentgrid.webhooks.WebhookClientsProvider.WebClientEndpointsConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class WebhookPublisher {

    private static Logger LOG = LoggerFactory.getLogger(WebhookPublisher.class);

    private static List<Tag> EMPTY_MANDATORY_TAGS = Stream.of(MANDATORY_HEADERS.values())
            .map(e -> Tag.of(e.name(), "")).collect(Collectors.toList());

    private final List<WebhookClientsProvider> providers;
    final MeterRegistry meterRegistry;

    public WebhookPublisher(List<WebhookClientsProvider> providers, MeterRegistry meterRegistry) {
        Assert.notNull(providers, "provider cannot be null");

        this.providers = providers;
        this.meterRegistry = meterRegistry;
    }

    @ServiceActivator(inputChannel = WebhookMessageConsumerConfiguration.CHANNEL_NAME)
    public void handleEvent(Message<byte[]> message) {
        LOG.debug("message received: {}", message);

        MessageHeaders messageHeaders = message.getHeaders();

        Map<String, String> headersAsStringValues = messageHeaders.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));

        byte[] payload = message.getPayload();
        if (payload != null) {
            publishAndSubscribe(headersAsStringValues, payload,
                    messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_HEADERNAME,
                            String.class),
                    messageHeaders.get(WebhookMessageConsumerConfiguration.WEBHOOKS_REQUESTTIMEOUT,
                            Long.class));
        } else {
            LOG.warn("message received: {} with null payload", message);
        }
    }

    void publishAndSubscribe(final Map<String, String> headers, final byte[] payload,
            final String headerName, final Long requestTimeoutInseconds) {

        FluxData fluxData = fluxData(headers, payload, headerName, requestTimeoutInseconds);
        int size = fluxData.size;

        if (size > 0) {
            fluxData.flux.subscribeOn(Schedulers.newParallel("contentgrid", size)).parallel(size)
                    .subscribe();
        }
    }

    FluxData fluxData(final Map<String, String> headers, final byte[] payload,
            final String headerName, final Long requestTimeoutInseconds) {

        if (!WebhookClientsProvider.hasAllMandatoryHeaders(headers)) {
            Tags tags = Tags.of(headers.entrySet().stream()
                    .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                    .map(e -> Tag.of(e.getKey(), e.getValue().toString()))
                    .collect(Collectors.toList()));

            Counter counter = Counter.builder("webhooks_missing_headers").tags(EMPTY_MANDATORY_TAGS)
                    .tags(tags).description("Number of webhook messages without mandatory headers")
                    .register(meterRegistry);
            counter.increment();
            return new FluxData(Flux.empty(), 0);
        }

        List<WebClientEndpointsConfig> matchedClients = findMatchingClients(headers);
        if (matchedClients.isEmpty()) {
            Tags tags = Tags.of(headers.entrySet().stream()
                    .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                    .map(e -> Tag.of(e.getKey(), e.getValue().toString()))
                    .collect(Collectors.toList()));

            Counter counter = Counter.builder("webhooks_no_matching_clients")
                    .tags(EMPTY_MANDATORY_TAGS).tags(tags)
                    .description("Number of webhook messages without matching clients")
                    .register(meterRegistry);
            counter.increment();
            return new FluxData(Flux.empty(), 0);
        }
        LOG.debug("clients matched: {} for headers: {}", matchedClients.size(), headers);

        Flux<ResponseEntity<Void>> flux = Flux
                .fromStream(matchedClients.stream().flatMap(c -> c.getEndpoints().stream()))
                .flatMap(c -> {

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
                    Timer.Sample sample = Timer.start(meterRegistry);
                    Tags staticMetricsHeaders = Tags.of(headers.entrySet().stream()
                            .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                            .map(e -> Tag.of(e.getKey(), e.getValue()))
                            .collect(Collectors.toList()));

                    return c.webClient.post().contentType(contentType == null ? MediaType.TEXT_PLAIN
                            : MediaType.valueOf(contentType)).headers(h -> {
                                String headerHashValue = hashValue.orElse("-none-");
                                h.add(headerName != null ? headerName
                                        : WebhookConfigurationProperties.HEADERNAME_DEFAULT,
                                        headerHashValue);
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
                                    }))
                            .doOnError(ex -> sample.stop(meterRegistry.timer("webhooks_calls",
                                    staticMetricsHeaders.and("status", "failure"))))
                            .doOnSuccess(r -> sample.stop(meterRegistry.timer("webhooks_calls",
                                    staticMetricsHeaders.and("status", "success"))));
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

    List<WebClientEndpointsConfig> findMatchingClients(Map<String, String> headers) {
        // is it required to double check, since each provider can just provide a
        // verified list?

        return providers.stream().flatMap(provider -> provider.getClients(headers).stream())
                .filter(client -> areMatchingHeaders(client.filters, headers))
                .collect(Collectors.toList());
    }

    public static boolean areMatchingHeaders(Map<String, String> first,
            Map<String, String> second) {

        if (first == null || first.isEmpty()) {
            return true;
        } else if ((second == null || second.isEmpty()) && !first.isEmpty()) {
            return false;
        }

        return first.entrySet().stream().allMatch(e -> e.getValue().equals(second.get(e.getKey())));
    }

    static String hmac(byte[] secretBytes, byte[] payload) {
        return Hashing.hmacSha256(secretBytes).hashBytes(payload).toString();
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
