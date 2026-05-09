package com.tanalytics.query.model;

/**
 * Aggregate analytics stats for a site over a given time range.
 */
public final class AggregateStats {

    private final long pageViews;
    private final long uniqueVisitors;
    private final long uniqueSessions;
    private final double bounceRate;
    private final double avgSessionDurationSeconds;

    public AggregateStats(long pageViews,
                          long uniqueVisitors,
                          long uniqueSessions,
                          double bounceRate,
                          double avgSessionDurationSeconds) {
        this.pageViews = pageViews;
        this.uniqueVisitors = uniqueVisitors;
        this.uniqueSessions = uniqueSessions;
        this.bounceRate = bounceRate;
        this.avgSessionDurationSeconds = avgSessionDurationSeconds;
    }

    public long pageViews() {
        return pageViews;
    }

    public long getPageViews() {
        return pageViews;
    }

    public long uniqueVisitors() {
        return uniqueVisitors;
    }

    public long getUniqueVisitors() {
        return uniqueVisitors;
    }

    public long uniqueSessions() {
        return uniqueSessions;
    }

    public long getUniqueSessions() {
        return uniqueSessions;
    }

    public double bounceRate() {
        return bounceRate;
    }

    public double getBounceRate() {
        return bounceRate;
    }

    public double avgSessionDurationSeconds() {
        return avgSessionDurationSeconds;
    }

    public double getAvgSessionDurationSeconds() {
        return avgSessionDurationSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateStats that)) return false;
        return pageViews == that.pageViews
                && uniqueVisitors == that.uniqueVisitors
                && uniqueSessions == that.uniqueSessions
                && Double.compare(bounceRate, that.bounceRate) == 0
                && Double.compare(avgSessionDurationSeconds, that.avgSessionDurationSeconds) == 0;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(pageViews);
        result = 31 * result + Long.hashCode(uniqueVisitors);
        result = 31 * result + Long.hashCode(uniqueSessions);
        long temp = Double.doubleToLongBits(bounceRate);
        result = 31 * result + Long.hashCode(temp);
        temp = Double.doubleToLongBits(avgSessionDurationSeconds);
        result = 31 * result + Long.hashCode(temp);
        return result;
    }

    @Override
    public String toString() {
        return "AggregateStats[pageViews=" + pageViews
                + ", uniqueVisitors=" + uniqueVisitors
                + ", uniqueSessions=" + uniqueSessions
                + ", bounceRate=" + bounceRate
                + ", avgSessionDurationSeconds=" + avgSessionDurationSeconds
                + ']';
    }
}

