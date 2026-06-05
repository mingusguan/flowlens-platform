package com.flowlens.admin.live.sharding;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;

public final class LiveEventTableRouter {

    public static final String EVENT_TABLE = "live_event";

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private static final ThreadLocal<String> MONTH_SUFFIX = new ThreadLocal<>();

    private LiveEventTableRouter() {
    }

    public static String resolveMonthSuffix(LocalDateTime eventTime) {
        LocalDateTime resolvedTime = eventTime == null ? LocalDateTime.now() : eventTime;
        return resolvedTime.format(MONTH_FORMATTER);
    }

    public static void useMonth(String monthSuffix) {
        if (!StringUtils.hasText(monthSuffix) || !monthSuffix.matches("\\d{6}")) {
            throw new IllegalArgumentException("直播事件分表月份不合法");
        }
        MONTH_SUFFIX.set(monthSuffix);
    }

    public static void clear() {
        MONTH_SUFFIX.remove();
    }

    public static String resolveTableName(String tableName) {
        String monthSuffix = MONTH_SUFFIX.get();
        if (!StringUtils.hasText(monthSuffix)) {
            return tableName;
        }
        if (EVENT_TABLE.equals(tableName)) {
            return tableName + "_" + monthSuffix;
        }
        return tableName;
    }

    public static String monthlyEventTable(String monthSuffix) {
        return EVENT_TABLE + "_" + requireMonthSuffix(monthSuffix);
    }

    private static String requireMonthSuffix(String monthSuffix) {
        if (!StringUtils.hasText(monthSuffix) || !monthSuffix.matches("\\d{6}")) {
            throw new IllegalArgumentException("直播事件分表月份不合法");
        }
        return monthSuffix;
    }
}
