package eu.xenit.contentgrid.slingshot.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

import eu.xenit.contentgrid.slingshot.service.JwkService;

@RestController
@RequestMapping("/.well-known/jwks.json")
public class JwkController {

    private final JwkService jwkService;

    public JwkController(JwkService jwkService) {
        this.jwkService = jwkService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> jwkSet() {
        JWKSet jwkSet = jwkService.jwkSet();
        return ResponseEntity.ok(jwkSet.toString(true));
    }
}
