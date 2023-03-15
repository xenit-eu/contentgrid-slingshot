package eu.xenit.contentgrid.slingshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.security.PrivateKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import eu.xenit.contentgrid.slingshot.service.JwtService;

public class JwtServiceTest {

    @Test
    public void when_privateKeyIsValid_jwtShouldBeGeneratedWithClaims()
            throws JOSEException, ParseException {

        PrivateKey privateKey2 = new RSAKeyGenerator(2048).generate().toPrivateKey();

        RSASSASigner signer = new RSASSASigner(privateKey2);
        JwtService jwtService = new JwtService(signer, URI.create("https://aaa"), "kid123");

        Instant now = Instant.now();
        SignedJWT jwt = jwtService.generateJwt(now, "ContentGrid", URI.create("https://endpoint"));

        assertNotNull(jwt, "jwt cannot be null");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertEquals("ContentGrid", claims.getSubject());
        assertEquals("https://aaa", claims.getIssuer());
        assertTrue(claims.getAudience().contains("https://endpoint"));
        assertEquals(Date.from(now), claims.getIssueTime());
        assertEquals(Date.from(now.plusSeconds(300)), claims.getExpirationTime());        
        assertNotNull(claims.getJWTID(), "jid cannot be null");
        assertEquals("kid123", jwt.getHeader().getKeyID());
    }    
}
