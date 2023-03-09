package eu.xenit.contentgrid.slingshot;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;

import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.MANDATORY_HEADERS;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebClientEndpointsConfig;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebConfigProviderResponse;
import eu.xenit.contentgrid.slingshot.WebhookClientsProvider.WebConfigProviderResponse.WebConfigProviderStatus;
import eu.xenit.contentgrid.slingshot.service.JwtService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;

public class WebhookPublisher {

    private static Logger LOG = LoggerFactory.getLogger(WebhookPublisher.class);

    private static List<Tag> EMPTY_MANDATORY_TAGS = Stream.of(MANDATORY_HEADERS.values())
            .map(e -> Tag.of(e.name(), "")).collect(Collectors.toList());

    public static final String USER_AGENT_HEADER_VALUE = "ContentGrid-Slingshot";
    public static final String CONTENTGRID_APPLICATION_ID_HEADER_NAME = "ContentGrid-Application-Id";
    public static final String CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME = "ContentGrid-Deployment-Id";
    public static final String CONTENTGRID_TOKEN_HEADER_NAME = "ContentGrid-Signature";

    private final List<WebhookClientsProvider> providers;
    private final MeterRegistry meterRegistry;
    private final JwtService jwtService;
    private final Long publishingRequestTimeoutInseconds;

    private final String userAgentHeaderValueWithVersion;

    public WebhookPublisher(JwtService jwtService, List<WebhookClientsProvider> providers,
            MeterRegistry meterRegistry, String slingshotVersion) {
        this(jwtService, providers, meterRegistry,
                WebhookConfigurationProperties.REQUEST_TIMEOUT_DEFAULT, slingshotVersion);
    }

    public WebhookPublisher(JwtService jwtService, List<WebhookClientsProvider> providers,
            MeterRegistry meterRegistry, Long publishingRequestTimeoutInseconds,
            String slingshotVersion) {
        Assert.notNull(providers, "provider cannot be null");
        Assert.notNull(jwtService, "jwtService cannot be null");
        Assert.hasText(slingshotVersion, "slingshotVersion cannot be null");

        this.providers = providers;
        this.meterRegistry = meterRegistry;
        this.jwtService = jwtService;
        this.publishingRequestTimeoutInseconds = publishingRequestTimeoutInseconds;

        userAgentHeaderValueWithVersion = String.format("%s/%s", USER_AGENT_HEADER_VALUE,
                slingshotVersion);
    }

    public String getUserAgentHeaderValueWithVersion() {
        return userAgentHeaderValueWithVersion;
    }

    @ServiceActivator(inputChannel = MessagingQueueConfiguration.CHANNEL_NAME)
    public void handleEvent(Message<String> message) {
        LOG.trace("message received: {}", message);

        MessageHeaders messageHeaders = message.getHeaders();

        Map<String, String> headersAsStringValues = messageHeaders.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));

        String payload = message.getPayload();
        if (payload != null) {
            PublishingFlux fluxData = publishingFlux(headersAsStringValues, payload);
            int size = fluxData.size;
            if (size > 0) {
                fluxData.flux.subscribeOn(Schedulers.newParallel("contentgrid", size)).parallel(size)
                        .subscribe();
            }
        } else {
            LOG.warn("message received: {} with null payload", message);
            recordMessageReceivedMetric(MessageReceivedStatus.null_payload, headersAsStringValues);
        }
    }

    private void recordMessageReceivedMetric(MessageReceivedStatus messageStatus,
            final Map<String, String> headers) {
        Tags overridMandatoryTagsWithValues = Tags.of(headers.entrySet().stream()
                .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                .map(e -> Tag.of(e.getKey(), e.getValue().toString()))
                .collect(Collectors.toList()));

        Counter counter = Counter.builder("messages").tags(EMPTY_MANDATORY_TAGS)
                .tags(overridMandatoryTagsWithValues).tag("status", messageStatus.name())
                .description("Number of webhook messages received").register(meterRegistry);

        counter.increment();
    }

    private void recordWebhookCallMetric(Timer.Sample sample,
            Signal<ResponseEntity<Void>> responseSignal, Map<String, String> headers,
            String endpoint) {

        if (responseSignal.isOnComplete() || responseSignal.isOnSubscribe()) {
            // these signals should not be handled
            return;
        }

        WebhookEndpointInvocationStatus webhookInvocationStatus = responseSignal.isOnError()
                ? WebhookEndpointInvocationStatus.failure
                : WebhookEndpointInvocationStatus.success;

//        TODO check if we need to know each specific exception and create the metric accordingly
//        Throwable throwable = response.getThrowable();
//        Throwable rootCause = throwable;
//        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
//            rootCause = rootCause.getCause();
//        }
        
        ResponseEntity<Void> responseEntity = responseSignal.get();        
        int httpStatusCode = responseEntity != null && responseEntity.getStatusCode() != null ? responseEntity.getStatusCode().value() : 0;
        
        Series series = Series.resolve(httpStatusCode);
        String httpStatusSeries = series != null ? series.name() : "-";
        
        
        if (WebhookEndpointInvocationStatus.failure.equals(webhookInvocationStatus)) { 
          LOG.warn("message could not be delivered to : '{}' received http status code '{}'", endpoint, httpStatusCode);
        } else {
            LOG.debug("message delivered to : '{}' received http status code '{}'", endpoint,
                    httpStatusCode);
        }

        // we are sure that all mandatory tags are present
        Tags mandatoryTagsWithValues = Tags.of(headers.entrySet().stream()
                .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                .map(e -> Tag.of(e.getKey(), e.getValue())).collect(Collectors.toList()));

        Tags t = mandatoryTagsWithValues.and("status", webhookInvocationStatus.name())
                // .and("status", httpStatus.getReasonPhrase())
                .and("http", String.valueOf(httpStatusCode))
                .and("http-series", httpStatusSeries);

        sample.stop(meterRegistry.timer("webhooks", t));
    }

    private void recordApiConfigLookupCallMetric(WebConfigProviderStatus webConfigProviderStatus,
            final Map<String, String> headers) {
        // we are sure that all mandatory tags are present
        Tags mandatoryTagsWithValues = Tags.of(headers.entrySet().stream()
                .filter(e -> WebhookClientsProvider.isMandatoryHeader(e.getKey()))
                .map(e -> Tag.of(e.getKey(), e.getValue())).collect(Collectors.toList()));

        Counter counter = Counter.builder("api_config_lookups").tags(mandatoryTagsWithValues)
                .tag("status", webConfigProviderStatus.name())
                .description("Number of api config lookup calls").register(meterRegistry);
        counter.increment();
    }

    PublishingFlux publishingFlux(final Map<String, String> headers, final String payload) {

        if (!WebhookClientsProvider.hasAllMandatoryHeaders(headers)) {
            // case were the message did not have all the mandatory headers
            LOG.warn("message received does not contain all mandatory headers: {}", headers);
            recordMessageReceivedMetric(MessageReceivedStatus.missing_headers, headers);
            return new PublishingFlux(Flux.empty(), 0);
        }

        List<WebClientEndpointsConfig> matchedClients = findMatchingClients(headers);
        if (matchedClients.isEmpty()) {
            // case where all providers returned an empty list
            LOG.debug("message received does not match any configuration for headers: {}", headers);
            recordMessageReceivedMetric(MessageReceivedStatus.no_matching_config, headers);
            return new PublishingFlux(Flux.empty(), 0);
        }

        // case where we have a valid message
        recordMessageReceivedMetric(MessageReceivedStatus.valid, headers);

        LOG.debug("{} client(s) configuration matched for headers: {}", matchedClients.size(), headers);
        Flux<ResponseEntity<Void>> flux = Flux
                .fromStream(matchedClients.stream().flatMap(c -> c.getEndpoints().stream()))
                .flatMap(c -> {

                    String contentType = (String) headers.get(HttpHeaders.CONTENT_TYPE);
                    if (contentType == null) {
                        contentType = (String) headers.get("contentType");
                    }

                    Timer.Sample sample = Timer.start(meterRegistry);

                    String jwt = jwtService.generateJwt(Instant.now(), USER_AGENT_HEADER_VALUE, URI.create(c.endpoint)).serialize();
                    LOG.debug("sending message to : '{}' with jwt :  '{}' in header : '{}'",
                            c.endpoint, jwt, CONTENTGRID_TOKEN_HEADER_NAME);

                    return c.webClient.post()
                            // .contentType(contentType == null ? MediaType.APPLICATION_JSON :
                            // MediaType.valueOf(contentType))
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(h -> {
                                h.set(CONTENTGRID_DEPLOYMENT_ID_HEADER_NAME,
                                        headers.get(MANDATORY_HEADERS.deployment_id.name()));
                                h.set(CONTENTGRID_APPLICATION_ID_HEADER_NAME,
                                        headers.get(MANDATORY_HEADERS.application_id.name()));
                                h.set(HttpHeaders.USER_AGENT, getUserAgentHeaderValueWithVersion());
                                h.set(CONTENTGRID_TOKEN_HEADER_NAME, jwt);
                            })
                            .body(BodyInserters.fromValue(payload))
                            .retrieve().toBodilessEntity()
                            .timeout(Duration.ofSeconds(publishingRequestTimeoutInseconds != null
                                    ? publishingRequestTimeoutInseconds
                                    : WebhookConfigurationProperties.REQUEST_TIMEOUT_DEFAULT))
                            /*
                             * .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).jitter(0.75)
                             * .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> { throw new
                             * WebhookDeliveryException(
                             * "External Service failed to process after max retries: " +
                             * c.endpoint, HttpStatus.SERVICE_UNAVAILABLE); }))
                             */
                            .doOnEach(r -> recordWebhookCallMetric(sample, r, headers, c.endpoint));
                });

        return new PublishingFlux(flux, matchedClients.size());
    }

    class PublishingFlux {
        private final Flux<ResponseEntity<Void>> flux;
        private final int size;

        public PublishingFlux(Flux<ResponseEntity<Void>> flux, int size) {
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

    /**
     * we skip config providers that fail but this should not occur as documented in
     * <code> {@link  WebhookClientsProvider#getClients(Map)}</<code>
     */
    List<WebClientEndpointsConfig> findMatchingClients(Map<String, String> headers) {
        Map<String, String> headersLocal = headers != null ? headers : Collections.emptyMap();

        // is it required to double check ( areMatchingHeaders(client.filters, headers))
        // since each provider can just provide a verified list?
        return providers.stream().flatMap(provider -> {
            try {
                WebConfigProviderResponse providerResponse = provider.getClients(headersLocal);
                if (providerResponse == null) {
                    // This is a safety check
                    recordApiConfigLookupCallMetric(WebConfigProviderStatus.failure, headersLocal);
                    return Stream.empty();
                }

                recordApiConfigLookupCallMetric(providerResponse.getStatus(), headersLocal);

                if (providerResponse.isProviderStatusOk()) {
                    return provider.getClients(headersLocal).getConfigList().stream();
                }
                return Stream.empty();
            } catch (Throwable e) {
                // This is a safety check
                recordApiConfigLookupCallMetric(WebConfigProviderStatus.failure, headersLocal);
                return Stream.empty();
            }
        }).filter(client -> areMatchingHeaders(client.filters, headersLocal))
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

    public static enum MessageReceivedStatus {
        no_matching_config, valid, missing_headers, null_payload
    }

    public static enum WebhookEndpointInvocationStatus {
        success, failure
    }
}
