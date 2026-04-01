package com.tanalytics.query.controller;

import com.tanalytics.query.config.SecurityConfig;
import com.tanalytics.query.model.RealtimeStats;
import com.tanalytics.query.security.JwtAuthFilter;
import com.tanalytics.query.security.JwtService;
import com.tanalytics.query.service.AnalyticsQueryService;
import com.tanalytics.query.service.RealtimeStreamService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AnalyticsController.class, RealtimeController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=this-is-a-test-secret-with-at-least-32-bytes!!"
})
class AnalyticsControllerSecurityTest {

    private static final String SECRET = "this-is-a-test-secret-with-at-least-32-bytes!!";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsQueryService analyticsQueryService;

    @MockBean
    private RealtimeStreamService realtimeStreamService;

    @Test
    void allowsAccessForAuthorizedSiteId() throws Exception {
        when(analyticsQueryService.getRealtimeStats("site-a"))
                .thenReturn(new RealtimeStats(1, 2, 3, 0));

        mockMvc.perform(get("/api/v1/sites/site-a/stats/realtime")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of("site-a"))))
                .andExpect(status().isOk());
    }

    @Test
    void deniesAccessForUnauthorizedSiteId() throws Exception {
        mockMvc.perform(get("/api/v1/sites/site-b/stats/realtime")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of("site-a"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRefreshTokenOnQueryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/sites/site-a/stats/realtime")
                        .header("Authorization", "Bearer " + token("refresh", "admin", List.of("site-a"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotImplementedForFunnelsInPhase3() throws Exception {
        mockMvc.perform(get("/api/v1/sites/site-a/stats/funnels")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of("site-a"))))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void returnsNotImplementedForHeatmapsInPhase3() throws Exception {
        mockMvc.perform(get("/api/v1/sites/site-a/stats/heatmaps")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of("site-a"))))
                .andExpect(status().isNotImplemented());
    }

    private String token(String type, String role, List<String> siteIds) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user-1")
                .claim("type", type)
                .claim("role", role)
                .claim("siteIds", siteIds)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
