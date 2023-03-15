package eu.xenit.contentgrid.slingshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import eu.xenit.contentgrid.slingshot.WebhookConfigurationProperties.WebhookJWKConfig;
import eu.xenit.contentgrid.slingshot.service.JwtService;

public class JwtServiceTest {

    @Test
    public void when_privateKeyIsValid_jwtShouldBeGeneratedWithClaims()
            throws JOSEException, ParseException {
        
        JwtService jwtService = new JwtService(new SigningPrivateKey(new WebhookJWKConfig(true)), URI.create("https://aaa"));

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
        assertEquals(jwtService.getKid(), jwt.getHeader().getKeyID());
    }    
}
