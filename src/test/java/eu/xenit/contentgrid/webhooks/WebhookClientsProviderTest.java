package eu.xenit.contentgrid.webhooks;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WebhookClientsProviderTest {

    @Test
    void when_webhookClientConfig_isEmptyAndHeadersNullOrEmpty_expect_ok() {
        Assertions.assertEquals(0,
                new WebhookClientsProvider.InMemoryWebhookClientsProvider(List.of()).getClients(null)
                        .size());
        
        Assertions.assertEquals(0,
                new WebhookClientsProvider.InMemoryWebhookClientsProvider(List.of()).getClients(Map.of())
                        .size());
    }
    
    @Test
    void when_webhookClientConfig_isNull_expect_IllegalArgumentException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new WebhookClientsProvider.InMemoryWebhookClientsProvider(null);
        });
    }

    @Test
    void when_webhookClientConfig_isNotConfigured_expect_NullPointerException() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                    List.of(new WebhookConfigurationProperties.WebhookClientConfig()));
        });
    }
}
