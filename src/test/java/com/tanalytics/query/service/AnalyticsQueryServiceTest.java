package com.tanalytics.query.service;

import com.tanalytics.query.model.BreakdownStats;
import com.tanalytics.query.model.BreakdownType;
import com.tanalytics.query.model.TimeRange;
import com.tanalytics.query.repository.ClickHouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    @Mock
    private ClickHouseRepository clickHouseRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private AnalyticsQueryService service;

    @Test
    void routesRecentBreakdownQueriesToHourlyMv() {
        List<BreakdownStats> expected = List.of(new BreakdownStats("US", "CA", null, 12, 10, 8));
        when(clickHouseRepository.queryBreakdownFromHourlyMV(eq("site-a"), any(), any(), eq(20), eq(BreakdownType.COUNTRY)))
                .thenReturn(expected);

        List<BreakdownStats> result = service.getBreakdownStats(
                "site-a",
                new TimeRange(Instant.now().minusSeconds(3600), Instant.now()),
                "country",
                20
        );

        assertEquals(expected, result);
        verify(clickHouseRepository).queryBreakdownFromHourlyMV(eq("site-a"), any(), any(), eq(20), eq(BreakdownType.COUNTRY));
        verify(clickHouseRepository, never()).queryBreakdownFromDailyMV(any(), any(), any(), anyInt(), any());
    }

    @Test
    void routesOlderBreakdownQueriesToDailyMv() {
        List<BreakdownStats> expected = List.of(new BreakdownStats("organic", "email", "spring", 24, 12, 9));
        when(clickHouseRepository.queryBreakdownFromDailyMV(eq("site-a"), any(), any(), eq(15), eq(BreakdownType.CAMPAIGN)))
                .thenReturn(expected);

        List<BreakdownStats> result = service.getBreakdownStats(
                "site-a",
                new TimeRange(Instant.now().minusSeconds(172800), Instant.now().minusSeconds(90000)),
                "campaigns",
                15
        );

        assertEquals(expected, result);
        verify(clickHouseRepository).queryBreakdownFromDailyMV(eq("site-a"), any(), any(), eq(15), eq(BreakdownType.CAMPAIGN));
        verify(clickHouseRepository, never()).queryBreakdownFromHourlyMV(any(), any(), any(), anyInt(), any());
    }
}


