package eu.xenit.contentgrid.webhooks;

import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootApplication
@EnableConfigurationProperties(WebhookConfigurationProperties.class)
public class WebhooksApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhooksApplication.class, args);
    }

    @Bean
    @RefreshScope
    WebhookClientsProvider inMemoryWebhookClientsProvider(
            WebhookConfigurationProperties webhookProperties) {
        return new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                webhookProperties.getClient());
    }

    @Bean
    WebhookClientsProvider dbWebhookClientsProvider() {
        return new WebhookClientsProvider.DatabaseWebhookClientsProvider();
    }
    
    @Bean
    WebhookClientsProvider contentGridApiWebhookClientsProvider() {
        return new WebhookClientsProvider.ContentGridApiWebhookClientsProvider();
    }

    @Bean
    WebhookPublisher webhookPublisher(
            ObjectProvider<WebhookClientsProvider> webhookClientsProviders,
            @Nullable MeterRegistry meterRegistry) {
        return new WebhookPublisher(
                webhookClientsProviders.orderedStream().collect(Collectors.toList()),
                meterRegistry);
    }
}
