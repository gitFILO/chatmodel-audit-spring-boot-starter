CREATE TABLE llm_invocation_log (
    id              BIGSERIAL PRIMARY KEY,

    invoked_at      TIMESTAMP    NOT NULL,
    trace_id        VARCHAR(64)  NOT NULL,
    span_id         VARCHAR(64),

    model_provider  VARCHAR(32)  NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    user_id         VARCHAR(128),
    team_id         VARCHAR(64),
    prompt          TEXT         NOT NULL,
    response        TEXT,
    token_in        INTEGER,
    token_out       INTEGER,
    latency_ms      INTEGER      NOT NULL,
    cost_micro_krw  BIGINT,

    status          VARCHAR(16)  NOT NULL,
    error_class     VARCHAR(256),
    error_message   TEXT,
    finish_reason   VARCHAR(32),
    tool_calls_json JSONB,

    metadata_json   JSONB,

    pii_masked         BOOLEAN   NOT NULL DEFAULT FALSE,
    masked_pii_count   INTEGER   NOT NULL DEFAULT 0,
    external_sent      BOOLEAN   NOT NULL DEFAULT FALSE,
    flagged            BOOLEAN   NOT NULL DEFAULT FALSE,

    compliance_profile VARCHAR(32) NOT NULL DEFAULT 'default'
);

CREATE INDEX idx_lil_invoked_desc
    ON llm_invocation_log (invoked_at DESC);

CREATE INDEX idx_lil_trace
    ON llm_invocation_log (trace_id);

CREATE INDEX idx_lil_user_time
    ON llm_invocation_log (user_id, invoked_at DESC);

CREATE INDEX idx_lil_team_time
    ON llm_invocation_log (team_id, invoked_at DESC);

CREATE INDEX idx_lil_model_time
    ON llm_invocation_log (model_provider, model_name, invoked_at DESC);

-- 5% 미만 행만 인덱싱: flagged 신고 건만 빠르게 조회
CREATE INDEX idx_lil_flagged
    ON llm_invocation_log (flagged) WHERE flagged = TRUE;

-- 외부 송신 행만 인덱싱: 금융위 보조수단성 매핑
CREATE INDEX idx_lil_external
    ON llm_invocation_log (external_sent, invoked_at DESC) WHERE external_sent = TRUE;

-- 실패 호출만 인덱싱: 디버그용
CREATE INDEX idx_lil_failed
    ON llm_invocation_log (status, invoked_at DESC) WHERE status <> 'SUCCESS';
