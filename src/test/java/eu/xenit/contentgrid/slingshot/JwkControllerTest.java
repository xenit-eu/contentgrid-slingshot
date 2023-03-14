package eu.xenit.contentgrid.slingshot;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import eu.xenit.contentgrid.slingshot.controller.JwkController;
import eu.xenit.contentgrid.slingshot.service.JwkService;

@ExtendWith(SpringExtension.class)
@WebMvcTest(JwkController.class)
public class JwkControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private JwkService jwkService;
    
    @Test
    void when_jwkSet_hasSingleJwk_expect_singleKeyWithKid() throws Exception {
        
        RSAKey loaded = JwkService.jwk(new ClassPathResource("keys/test-private.key"));
        doReturn(new JWKSet(loaded)).when(jwkService).jwkSet();

        mockMvc.perform(get("/.well-known/jwks.json").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kid").value(loaded.getKeyID()));
    }
    
    @Test
    void when_jwkSet_hasTwoSameJwk_expect_twoKeysWithSameKid() throws Exception {
        
        RSAKey loaded = JwkService.jwk(new ClassPathResource("keys/test-private.key"));
        doReturn(new JWKSet(List.of(loaded, loaded))).when(jwkService).jwkSet();

        mockMvc.perform(get("/.well-known/jwks.json").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(2))
                .andExpect(jsonPath("$.keys[0].kid").value(loaded.getKeyID()))
                .andExpect(jsonPath("$.keys[1].kid").value(loaded.getKeyID()));
    }
    
    @Test
    void when_jwkSet_hasTwoJwk_expect_twoKeysWithDifferentKid() throws Exception {
        
        RSAKey loaded = JwkService.jwk(new ClassPathResource("keys/test-private.key"));
        RSAKey generated = new RSAKeyGenerator(2048).keyID("test").generate();
        doReturn(new JWKSet(List.of(loaded, generated))).when(jwkService).jwkSet();

        mockMvc.perform(get("/.well-known/jwks.json").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(2))
                .andExpect(jsonPath("$.keys[0].kid").value(loaded.getKeyID()))
                .andExpect(jsonPath("$.keys[1].kid").value(generated.getKeyID()));
    }
}
