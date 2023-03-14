package eu.xenit.contentgrid.slingshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookJWKConfig;
import eu.xenit.contentgrid.slingshot.service.JwkService;

public class SigningPrivateKey {
    
    private static Logger LOG = LoggerFactory.getLogger(SigningPrivateKey.class);

    private final RSAKey rsaKey;

    public SigningPrivateKey(WebhookJWKConfig jwtConfig) throws JOSEException {
        Assert.notNull(jwtConfig, "jwtConfig cannot be null");
        
        if (jwtConfig.isGenerateKey()) {
            if (jwtConfig.getSigningKey() != null) {
                LOG.warn("a signing key is configured but a generated key is used");
            }

            rsaKey = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } else {
            Assert.notNull(jwtConfig.getSigningKey(), "jwt.signing-key must be provided when generated key is not used");

            rsaKey = JwkService.jwk(jwtConfig.getSigningKey());
        }
    }

    public RSAKey getRSAKey() {
        return rsaKey;
    }
}
