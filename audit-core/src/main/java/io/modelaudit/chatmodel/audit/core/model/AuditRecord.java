package io.modelaudit.chatmodel.audit.core.model;

import java.time.Instant;

// 9 OTel GenAI semconv compatible columns + audit/security flag extensions — vault 04 §3-1
public record AuditRecord(
    Instant invokedAt,
    String traceId,
    String spanId,
    String modelProvider,
    String modelName,
    String userId,
    String teamId,
    String prompt,
    String response,
    Integer tokenIn,
    Integer tokenOut,
    int latencyMs,
    Long costMicroKrw,
    Status status,
    String errorClass,
    String errorMessage,
    String finishReason,
    String toolCallsJson,
    String metadataJson,
    boolean piiMasked,
    int maskedPiiCount,
    boolean externalSent,
    boolean flagged,
    String complianceProfile
) {
    public enum Status { SUCCESS, FAILED, CANCELLED }
}
