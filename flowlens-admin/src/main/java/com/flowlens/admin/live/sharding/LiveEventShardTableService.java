package com.flowlens.admin.live.sharding;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LiveEventShardTableService {

    private final JdbcTemplate jdbcTemplate;

    private final Set<String> ensuredMonths = ConcurrentHashMap.newKeySet();

    public void ensureMonthTables(String monthSuffix) {
        if (!ensuredMonths.add(monthSuffix)) {
            return;
        }
        String eventTable = LiveEventTableRouter.monthlyEventTable(monthSuffix);
        String rawTable = LiveEventTableRouter.monthlyRawTable(monthSuffix);
        try {
            jdbcTemplate.execute("create table if not exists " + eventTable + " like " + LiveEventTableRouter.EVENT_TABLE);
            jdbcTemplate.execute("create table if not exists " + rawTable + " like " + LiveEventTableRouter.RAW_TABLE);
        } catch (RuntimeException ex) {
            ensuredMonths.remove(monthSuffix);
            throw ex;
        }
    }
}
