package io.modelaudit.chatmodel.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "audit.compliance")
public class ComplianceAuditProperties {

    private boolean enabled = false;

    private String tableName = "llm_invocation_log";

    private String schema;

    // spring-security / mdc / header
    private String userIdResolver = "spring-security";

    private String userIdMdcKey = "userId";

    private String userIdHeaderName = "X-User-Id";

    // mdc
    private String teamIdResolver = "mdc";

    private String teamIdMdcKey = "teamId";

    private final Compliance compliance = new Compliance();

    private final Async async = new Async();

    private final Pii pii = new Pii();

    private final Cost cost = new Cost();

    private final Retention retention = new Retention();

    private final Actuator actuator = new Actuator();

    private final Metrics metrics = new Metrics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getUserIdResolver() {
        return userIdResolver;
    }

    public void setUserIdResolver(String userIdResolver) {
        this.userIdResolver = userIdResolver;
    }

    public String getUserIdMdcKey() {
        return userIdMdcKey;
    }

    public void setUserIdMdcKey(String userIdMdcKey) {
        this.userIdMdcKey = userIdMdcKey;
    }

    public String getUserIdHeaderName() {
        return userIdHeaderName;
    }

    public void setUserIdHeaderName(String userIdHeaderName) {
        this.userIdHeaderName = userIdHeaderName;
    }

    public String getTeamIdResolver() {
        return teamIdResolver;
    }

    public void setTeamIdResolver(String teamIdResolver) {
        this.teamIdResolver = teamIdResolver;
    }

    public String getTeamIdMdcKey() {
        return teamIdMdcKey;
    }

    public void setTeamIdMdcKey(String teamIdMdcKey) {
        this.teamIdMdcKey = teamIdMdcKey;
    }

    public Compliance getCompliance() {
        return compliance;
    }

    public Async getAsync() {
        return async;
    }

    public Pii getPii() {
        return pii;
    }

    public Cost getCost() {
        return cost;
    }

    public Retention getRetention() {
        return retention;
    }

    public Actuator getActuator() {
        return actuator;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static class Compliance {

        // default / kr-financial
        private String mode = "default";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public static class Async {

        private boolean enabled = true;

        private int queueCapacity = 10000;

        private int flushBatchSize = 100;

        private long flushIntervalMs = 500L;

        // block / drop
        private String overflowPolicy = "block";

        private boolean dropMetricEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getFlushBatchSize() {
            return flushBatchSize;
        }

        public void setFlushBatchSize(int flushBatchSize) {
            this.flushBatchSize = flushBatchSize;
        }

        public long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public String getOverflowPolicy() {
            return overflowPolicy;
        }

        public void setOverflowPolicy(String overflowPolicy) {
            this.overflowPolicy = overflowPolicy;
        }

        public boolean isDropMetricEnabled() {
            return dropMetricEnabled;
        }

        public void setDropMetricEnabled(boolean dropMetricEnabled) {
            this.dropMetricEnabled = dropMetricEnabled;
        }
    }

    public static class Pii {

        private boolean maskEnabled = false;

        // true 시 검출 실패한 prompt는 호출 차단
        private boolean required = false;

        private List<String> providers = new ArrayList<>();

        private boolean restoreInResponse = true;

        private long tokenTtlSec = 600L;

        public boolean isMaskEnabled() {
            return maskEnabled;
        }

        public void setMaskEnabled(boolean maskEnabled) {
            this.maskEnabled = maskEnabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public List<String> getProviders() {
            return providers;
        }

        public void setProviders(List<String> providers) {
            this.providers = providers;
        }

        public boolean isRestoreInResponse() {
            return restoreInResponse;
        }

        public void setRestoreInResponse(boolean restoreInResponse) {
            this.restoreInResponse = restoreInResponse;
        }

        public long getTokenTtlSec() {
            return tokenTtlSec;
        }

        public void setTokenTtlSec(long tokenTtlSec) {
            this.tokenTtlSec = tokenTtlSec;
        }
    }

    public static class Cost {

        private String table = "classpath:/llm-cost-table.yml";

        // static / api
        private String exchangeRateProvider = "static";

        private long staticUsdToKrw = 1380L;

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getExchangeRateProvider() {
            return exchangeRateProvider;
        }

        public void setExchangeRateProvider(String exchangeRateProvider) {
            this.exchangeRateProvider = exchangeRateProvider;
        }

        public long getStaticUsdToKrw() {
            return staticUsdToKrw;
        }

        public void setStaticUsdToKrw(long staticUsdToKrw) {
            this.staticUsdToKrw = staticUsdToKrw;
        }
    }

    public static class Retention {

        private int maxAgeDays = 365;

        private String cleanupCron;

        private boolean archiveBeforeDelete = false;

        private boolean preserveFlagged = true;

        public int getMaxAgeDays() {
            return maxAgeDays;
        }

        public void setMaxAgeDays(int maxAgeDays) {
            this.maxAgeDays = maxAgeDays;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }

        public boolean isArchiveBeforeDelete() {
            return archiveBeforeDelete;
        }

        public void setArchiveBeforeDelete(boolean archiveBeforeDelete) {
            this.archiveBeforeDelete = archiveBeforeDelete;
        }

        public boolean isPreserveFlagged() {
            return preserveFlagged;
        }

        public void setPreserveFlagged(boolean preserveFlagged) {
            this.preserveFlagged = preserveFlagged;
        }
    }

    public static class Actuator {

        private boolean statsEnabled = true;

        private boolean searchEnabled = true;

        private boolean exportEnabled = true;

        private int maxSearchResults = 1000;

        private final Search search = new Search();

        public boolean isStatsEnabled() {
            return statsEnabled;
        }

        public void setStatsEnabled(boolean statsEnabled) {
            this.statsEnabled = statsEnabled;
        }

        public boolean isSearchEnabled() {
            return searchEnabled;
        }

        public void setSearchEnabled(boolean searchEnabled) {
            this.searchEnabled = searchEnabled;
        }

        public boolean isExportEnabled() {
            return exportEnabled;
        }

        public void setExportEnabled(boolean exportEnabled) {
            this.exportEnabled = exportEnabled;
        }

        public int getMaxSearchResults() {
            return maxSearchResults;
        }

        public void setMaxSearchResults(int maxSearchResults) {
            this.maxSearchResults = maxSearchResults;
        }

        public Search getSearch() {
            return search;
        }

        public static class Search {

            private boolean maskOutput = false;

            public boolean isMaskOutput() {
                return maskOutput;
            }

            public void setMaskOutput(boolean maskOutput) {
                this.maskOutput = maskOutput;
            }
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        private boolean teamIdRequired = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isTeamIdRequired() {
            return teamIdRequired;
        }

        public void setTeamIdRequired(boolean teamIdRequired) {
            this.teamIdRequired = teamIdRequired;
        }
    }
}
