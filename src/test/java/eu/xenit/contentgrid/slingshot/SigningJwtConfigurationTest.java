package eu.xenit.contentgrid.slingshot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookJWKConfig;

public class SigningJwtConfigurationTest {

    @Test
    void when_generateKey_isTrue_expect_ok() throws JOSEException {

        WebhookJWKConfig jwtConfig = new WebhookJWKConfig();
        jwtConfig.setGenerateKey(true);

        SigningPrivateKey signingPrivateKey = new SigningPrivateKey(jwtConfig);
        RSAKey rsaKey = signingPrivateKey.getRSAKey();

        assertNotNull(rsaKey);
    }

    @Test
    void when_generateKeyIsFalseAndSigningKeyNotSet_expect_IllegalArgumentException() throws JOSEException {

        WebhookJWKConfig jwtConfig = new WebhookJWKConfig();
        jwtConfig.setGenerateKey(false);

        assertThrows(IllegalArgumentException.class, () -> {
            new SigningPrivateKey(jwtConfig);
        });
    }
    
    @Test
    void when_generateKeyIsFalseAndSigningKeySet_expect_ok() throws JOSEException {

        WebhookJWKConfig jwtConfig = new WebhookJWKConfig();
        jwtConfig.setGenerateKey(false);
        jwtConfig.setSigningKey(new ClassPathResource("keys/test-private.key"));

        SigningPrivateKey signingPrivateKey = new SigningPrivateKey(jwtConfig);
        RSAKey rsaKey = signingPrivateKey.getRSAKey();
        
        assertNotNull(rsaKey);
    }
}
