package lemonadex.project.clothes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The abuse-prone endpoints answer 429 with Retry-After once a client goes over its limit. */
@SpringBootTest(properties = "app.rate-limit.enabled=true") @ActiveProfiles("test") @AutoConfigureMockMvc
class RateLimitIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;

    @Test
    void forgotPasswordIsLimitedPerClient() throws Exception {
        String body = "{\"email\":\"someone@example.com\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/password/forgot").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/v1/auth/password/forgot").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        // Another client is not affected.
        mvc.perform(post("/api/v1/auth/password/forgot").with(r -> { r.setRemoteAddr("203.0.113.8"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
    }
}
