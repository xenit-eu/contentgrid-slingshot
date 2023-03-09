package eu.xenit.contentgrid.slingshot.service;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.util.Assert;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JwtService {

    private final RSASSASigner signer;
    private final URI issuer;

    public JwtService(RSASSASigner signer, URI issuer) {
        Assert.notNull(signer, "signer cannot be null");
        Assert.notNull(issuer, "issuer cannot be null");
        this.signer = signer;
        this.issuer = issuer;
    }

    public SignedJWT generateJwt(Instant instant, String subject, URI audience) {
        Assert.notNull(instant, "instant cannot be null");
        Assert.hasText(subject, "subject cannot be null");
        Assert.notNull(audience, "audience cannot be null");

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().subject(subject)
                .issuer(issuer.toString()).audience(audience.toString())
                .expirationTime(Date.from(instant.plusSeconds(300))).issueTime(Date.from(instant))
                .jwtID(UUID.randomUUID().toString());

        JWTClaimsSet claimsSet = builder.build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claimsSet);
        try {
            signedJWT.sign(signer);
            return signedJWT;
        } catch (JOSEException e) {
            throw new RuntimeException("Token could not be signed", e);
        }
    }
}
