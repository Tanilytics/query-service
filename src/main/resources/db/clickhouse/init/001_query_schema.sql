CREATE TABLE IF NOT EXISTS analytics.events (
    site_id String,
    timestamp DateTime64(3),
    visitor_id String,
    session_id String,
    event_type String,
    url String,
    referrer String,
    media_url String,
    media_type String
)
ENGINE = MergeTree
ORDER BY (site_id, timestamp, visitor_id, session_id);

CREATE TABLE IF NOT EXISTS analytics.page_views_hourly (
    site_id String,
    hour DateTime,
    url String,
    referrer String,
    views UInt64,
    unique_visitors UInt64,
    unique_sessions UInt64
)
ENGINE = MergeTree
ORDER BY (site_id, hour, url, referrer);

CREATE TABLE IF NOT EXISTS analytics.media_engagement_hourly (
    site_id String,
    hour DateTime,
    media_url String,
    media_type String,
    plays UInt64,
    completions UInt64,
    unique_viewers UInt64,
    avg_completion_rate Float64
)
ENGINE = MergeTree
ORDER BY (site_id, hour, media_url, media_type);
