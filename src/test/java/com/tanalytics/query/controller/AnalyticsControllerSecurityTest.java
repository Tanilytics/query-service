package com.tanalytics.query.controller;

import com.tanalytics.query.auth.InternalAuthClient;
import com.tanalytics.query.auth.InternalAuthUnavailableException;
import com.tanalytics.query.config.SecurityConfig;
import com.tanalytics.query.model.AggregateStats;
import com.tanalytics.query.model.BreakdownStats;
import com.tanalytics.query.model.RealtimeStats;
import com.tanalytics.query.model.TimeRange;
import com.tanalytics.query.security.JwtAuthFilter;
import com.tanalytics.query.security.JwtService;
import com.tanalytics.query.service.AnalyticsQueryService;
import com.tanalytics.query.service.RealtimeStreamService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockBean
    private InternalAuthClient internalAuthClient;

    private static final String SITE_A = "11111111-1111-1111-1111-111111111111";
    private static final String SITE_B = "22222222-2222-2222-2222-222222222222";
    private static final String USER_ID = "33333333-3333-3333-3333-333333333333";

    @Test
    void allowsAccessForAuthorizedSiteId() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);
        when(analyticsQueryService.getRealtimeStats(SITE_A))
                .thenReturn(new RealtimeStats(1, 2, 3, 0));

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/realtime")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isOk());
    }

    @Test
    void deniesAccessForUnauthorizedSiteId() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_B), UUID.fromString(USER_ID))).thenReturn(false);

        mockMvc.perform(get("/api/v1/sites/" + SITE_B + "/stats/realtime")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isForbidden());
    }

            @Test
            void allowsAccessWhenMembershipIsTrueEvenIfTokenClaimsStaleSiteIds() throws Exception {
            when(internalAuthClient.isMember(UUID.fromString(SITE_B), UUID.fromString(USER_ID))).thenReturn(true);
            when(analyticsQueryService.getRealtimeStats(SITE_B))
                .thenReturn(new RealtimeStats(1, 2, 3, 0));

            mockMvc.perform(get("/api/v1/sites/" + SITE_B + "/stats/realtime")
                    .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isOk());
            }

    @Test
    void rejectsRefreshTokenOnQueryEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/realtime")
                        .header("Authorization", "Bearer " + token("refresh", "admin", List.of(SITE_A))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsServiceUnavailableWhenMembershipCheckFails() throws Exception {
        when(internalAuthClient.isMember(any(UUID.class), any(UUID.class)))
                                .thenThrow(new InternalAuthUnavailableException("auth dependency unavailable", null));

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/realtime")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void returnsNotImplementedForFunnelsInPhase3() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/funnels")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void returnsNotImplementedForHeatmapsInPhase3() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/heatmaps")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void returnsBreakdownStatsForAuthorizedSiteId() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);
        when(analyticsQueryService.getBreakdownStats(eq(SITE_A), any(TimeRange.class), eq("country"), eq(10)))
                .thenReturn(List.of(new BreakdownStats("US", "CA", null, 12, 10, 8)));

        String from = Instant.now().minusSeconds(3600).toString();
        String to = Instant.now().toString();

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/breakdown")
                        .param("breakdownType", "country")
                        .param("from", from)
                        .param("to", to)
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dimension1").value("US"))
                .andExpect(jsonPath("$[0].dimension2").value("CA"))
                .andExpect(jsonPath("$[0].pageViews").value(12));
    }

    @Test
    void returnsAggregateStatsForAuthorizedSiteId() throws Exception {
        when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);
        when(analyticsQueryService.getAggregateStats(eq(SITE_A), any(TimeRange.class)))
                .thenReturn(new AggregateStats(42, 21, 11, 0.25, 123.4));

        String from = Instant.now().minusSeconds(3600).toString();
        String to = Instant.now().toString();

        mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/aggregate")
                        .param("from", from)
                        .param("to", to)
                        .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageViews").value(42))
                .andExpect(jsonPath("$.uniqueVisitors").value(21))
                .andExpect(jsonPath("$.avgSessionDurationSeconds").value(123.4));
    }

            @Test
            void returnsServiceUnavailableWhenAnalyticsStoreFails() throws Exception {
            when(internalAuthClient.isMember(UUID.fromString(SITE_A), UUID.fromString(USER_ID))).thenReturn(true);
            when(analyticsQueryService.getAggregateStats(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("clickhouse unavailable"));

            String from = Instant.now().minusSeconds(3600).toString();
            String to = Instant.now().toString();

            mockMvc.perform(get("/api/v1/sites/" + SITE_A + "/stats/aggregate")
                    .param("from", from)
                    .param("to", to)
                    .header("Authorization", "Bearer " + token("access", "admin", List.of(SITE_A))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Analytics query dependency unavailable"))
                .andExpect(jsonPath("$.errorCode").value("QUERY_DATASTORE_UNAVAILABLE"));
            }

    private String token(String type, String role, List<String> siteIds) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(USER_ID)
                .claim("type", type)
                .claim("role", role)
                .claim("siteIds", siteIds)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
