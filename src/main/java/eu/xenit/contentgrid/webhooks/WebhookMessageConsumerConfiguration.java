package eu.xenit.contentgrid.webhooks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;

@Configuration
public class WebhookMessageConsumerConfiguration {

    private static Logger LOG = LoggerFactory.getLogger(WebhookMessageConsumerConfiguration.class);
    public static final String CHANNEL_NAME = "content-grid.events";
    public static final String WEBHOOKS_HEADERNAME = "webhooks_headerName";
    public static final String WEBHOOKS_REQUESTTIMEOUT = "webhooks_requestTimeout";

    @Bean
    Queue queue(WebhookConfigurationProperties props) {
        return new Queue(props.getQueue(), false);
    }

    @Bean
    public IntegrationFlow routeIncomingAmqpMessagesFlow(ConnectionFactory connectionFactory,
            WebhookConfigurationProperties props) {
        return IntegrationFlows.from(Amqp.inboundAdapter(connectionFactory, props.getQueue()))
                .enrichHeaders(headers -> {
                    headers.header(WEBHOOKS_HEADERNAME, props.getHeaderName(), true);
                    headers.header(WEBHOOKS_REQUESTTIMEOUT, props.getRequestTimeout(), true);
                }).channel(WebhookMessageConsumerConfiguration.CHANNEL_NAME).get();
    }
}
