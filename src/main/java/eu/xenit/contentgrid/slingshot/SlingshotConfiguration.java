package eu.xenit.contentgrid.slingshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;

import eu.xenit.contentgrid.slingshot.service.JwkService;
import eu.xenit.contentgrid.slingshot.service.JwtService;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@EnableConfigurationProperties(WebhookConfigurationProperties.class)
public class SlingshotConfiguration {
    
    @Bean
    WebhookClientsProvider inMemoryWebhookClientsProvider(
            WebhookConfigurationProperties webhookProperties) {
        return new WebhookClientsProvider.InMemoryWebhookClientsProvider(
                webhookProperties.getClient());
    }

    @Bean
    WebhookClientsProvider contentGridApiWebhookClientsProvider() {
        return new WebhookClientsProvider.ContentGridApiWebhookClientsProvider();
    }

    @Bean
    WebhookPublisher webhookPublisher(JwtService jwkTervice, WebhookConfigurationProperties props,
            ObjectProvider<WebhookClientsProvider> webhookClientsProviders,
            @Nullable MeterRegistry meterRegistry, @Nullable BuildProperties buildInfo,
            ApplicationContext ctx) {

        return new WebhookPublisher(jwkTervice,
                webhookClientsProviders.orderedStream().collect(Collectors.toList()), meterRegistry,
                props.getRequestTimeout(),
                buildInfo != null ? buildInfo.getVersion() : "0.0.1-SNAPSHOT");
    }
    
    @Bean
    SigningPrivateKey signingPrivateKey(WebhookConfigurationProperties props) throws JOSEException {
        return new SigningPrivateKey(props.getSigning().getJwt());
    }

    @Bean
    JwtService jwtService(SigningPrivateKey signingPrivateKey, WebhookConfigurationProperties props) throws JOSEException {
        return new JwtService(new RSASSASigner(signingPrivateKey.getRSAKey()), props.getSigning().getJwt().getIssuer());
    }

    @Bean
    JwkService jwkService(WebhookConfigurationProperties props, SigningPrivateKey signingPrivateKey, ResourcePatternResolver resourcePatternResolver) throws JOSEException {
        List<JWK> jwks = Optional.ofNullable(props.getSigning().getJwt().getRetiredKeys()).map(Collection::stream)
                .orElseGet(Stream::empty).map(resource -> {
                    return JwkService.jwk(resource);
                }).collect(Collectors.toList());
        
        ArrayList<JWK> list = new ArrayList<>(jwks);
        list.add(signingPrivateKey.getRSAKey());
        
        return new JwkService(list);
    }
}
