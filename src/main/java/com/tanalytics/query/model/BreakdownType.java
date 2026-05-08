package com.tanalytics.query.model;

import java.util.Locale;

/** Supported breakdown materialized-view families. */
public enum BreakdownType {
    COUNTRY("country", "country_breakdown_hourly", "country_breakdown_daily", "country", "region", null),
    DEVICE("device", "device_breakdown_hourly", "device_breakdown_daily", "device_type", "browser", "os"),
    CAMPAIGN("campaign", "campaigns_hourly", "campaigns_daily", "utm_source", "utm_medium", "utm_campaign");

    private final String value;
    private final String hourlyTable;
    private final String dailyTable;
    private final String dimension1Column;
    private final String dimension2Column;
    private final String dimension3Column;

    BreakdownType(String value,
                  String hourlyTable,
                  String dailyTable,
                  String dimension1Column,
                  String dimension2Column,
                  String dimension3Column) {
        this.value = value;
        this.hourlyTable = hourlyTable;
        this.dailyTable = dailyTable;
        this.dimension1Column = dimension1Column;
        this.dimension2Column = dimension2Column;
        this.dimension3Column = dimension3Column;
    }

    public String value() {
        return value;
    }

    public String hourlyTable() {
        return hourlyTable;
    }

    public String dailyTable() {
        return dailyTable;
    }

    public String dimension1Column() {
        return dimension1Column;
    }

    public String dimension2Column() {
        return dimension2Column;
    }

    public String dimension3Column() {
        return dimension3Column;
    }

    public boolean hasThirdDimension() {
        return dimension3Column != null;
    }

    public static BreakdownType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("breakdownType is required");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "country", "country_breakdown" -> COUNTRY;
            case "device", "device_breakdown" -> DEVICE;
            case "campaign", "campaigns" -> CAMPAIGN;
            default -> {
                for (BreakdownType type : values()) {
                    if (type.value.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                        yield type;
                    }
                }
                throw new IllegalArgumentException("Unsupported breakdown type: " + value);
            }
        };
    }
}

