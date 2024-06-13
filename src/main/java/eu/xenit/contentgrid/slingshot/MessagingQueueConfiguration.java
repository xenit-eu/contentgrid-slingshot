package eu.xenit.contentgrid.slingshot;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;

@Configuration
public class MessagingQueueConfiguration {

    public static final String CHANNEL_NAME = "contentgrid.channel";
    public static final String WEBHOOKS_HEADERNAME = "webhooks_headerName";
    public static final String WEBHOOKS_REQUESTTIMEOUT = "webhooks_requestTimeout";

    @Bean
    public IntegrationFlow routeIncomingAmqpMessagesFlow(ConnectionFactory connectionFactory,
            WebhookConfigurationProperties props) {
        return IntegrationFlow.from(Amqp.inboundAdapter(connectionFactory, props.getQueue()))
                .transform(message -> message)
                .channel(MessagingQueueConfiguration.CHANNEL_NAME).get();
    }
}
