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

import eu.xenit.contentgrid.slingshot.SigningPrivateKey;

public class JwtService {

    private final SigningPrivateKey signingPrivateKey;
    private final URI issuer;
    private final RSASSASigner signer;    

    public JwtService(SigningPrivateKey signingPrivateKey, URI issuer) {
        Assert.notNull(signingPrivateKey, "signingPrivateKey cannot be null");                
        
        this.signingPrivateKey = signingPrivateKey;
        this.issuer = issuer;
        try {
            this.signer = new RSASSASigner(signingPrivateKey.getRSAKey());
        } catch (JOSEException e) {
            throw new RuntimeException("could not create the RSA Signer", e);
        }
    }

    public SignedJWT generateJwt(Instant instant, String subject, URI audience) {
        Assert.notNull(instant, "instant cannot be null");
        Assert.hasText(subject, "subject cannot be null");
        Assert.notNull(audience, "audience cannot be null");

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(subject)
                .audience(audience.toString())
                .expirationTime(Date.from(instant.plusSeconds(300)))
                .issueTime(Date.from(instant))
                .jwtID(UUID.randomUUID().toString());
        
        if(issuer != null) {
          claimsBuilder.issuer(issuer.toString());
        }
        
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(getKid()).build(),
                claimsBuilder.build());
        try {
            signedJWT.sign(signer);
            return signedJWT;
        } catch (JOSEException e) {
            throw new RuntimeException("Token could not be signed", e);
        }
    }
    
    public String getKid() {
        return signingPrivateKey.getRSAKey().getKeyID();
    }
}
