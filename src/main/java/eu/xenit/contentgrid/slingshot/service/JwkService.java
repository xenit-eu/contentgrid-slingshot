package eu.xenit.contentgrid.slingshot.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

public class JwkService {

    private final JWKSet jwks;

    public JwkService(List<JWK> jwks) {
        Assert.notEmpty(jwks, "jwks cannot be empty");        

        this.jwks =  new JWKSet(jwks);
    }

    public JWKSet jwkSet() {
        return jwks;
    }
    
    public static RSAKey jwk(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(),
                StandardCharsets.UTF_8)) {
            RSAKey loadedKey = RSAKey.parseFromPEMEncodedObjects(FileCopyUtils.copyToString(reader)).toRSAKey();
            
            return new RSAKey.Builder(loadedKey.toRSAPublicKey()).privateKey(loadedKey.toPrivateKey())
                    .keyIDFromThumbprint().build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
