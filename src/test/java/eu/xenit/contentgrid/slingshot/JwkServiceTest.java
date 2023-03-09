package eu.xenit.contentgrid.slingshot;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import eu.xenit.contentgrid.slingshot.service.JwkService;

public class JwkServiceTest {

    @Test
    public void when_generatedJWKWithKid_jwtSetShouldContainKid()
            throws NoSuchAlgorithmException, JOSEException {

        RSAKey generated = new RSAKeyGenerator(2048).keyID("test").generate();
        JwkService jwkService = new JwkService(List.of(generated));

        JWKSet jwkSet = jwkService.jwkSet();

        JWK keyByKeyId = jwkSet.getKeyByKeyId("test");

        assertNotNull(keyByKeyId, "kid cannot be null");
    }

    @Test
    public void when_loadedFromPrivateKey_jwtSetShouldContainKid()
            throws NoSuchAlgorithmException, JOSEException {

        RSAKey loaded = JwkService.jwk(new ClassPathResource("keys/test-private.key"));
        JwkService jwkService = new JwkService(List.of(loaded));

        JWKSet jwkSet = jwkService.jwkSet();

        JWK keyId = jwkSet.getKeyByKeyId("B37ZnA6E0xRNbICvkDOGUlVCSUdRvDtYxCDc3_g6V8Y");

        assertNotNull(keyId, "kid cannot be null");
    }

    @Test
    public void when_multipleKeysLoaded_jwtSetShouldContainAllKids()
            throws NoSuchAlgorithmException, JOSEException {

        RSAKey generated = new RSAKeyGenerator(2048).keyID("test").generate();
        RSAKey loaded = JwkService.jwk(new ClassPathResource("keys/test-private.key"));
        JwkService jwkService = new JwkService(List.of(generated, loaded));

        JWKSet jwkSet = jwkService.jwkSet();

        JWK generatedKeyId = jwkSet.getKeyByKeyId("B37ZnA6E0xRNbICvkDOGUlVCSUdRvDtYxCDc3_g6V8Y");
        JWK loadedKeyId = jwkSet.getKeyByKeyId("test");

        assertNotNull(generatedKeyId, "generated kid cannot be null");
        assertNotNull(loadedKeyId, "loaded kid cannot be null");
    }
}
