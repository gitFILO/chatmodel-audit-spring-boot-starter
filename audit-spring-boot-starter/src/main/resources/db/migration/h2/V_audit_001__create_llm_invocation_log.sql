CREATE TABLE llm_invocation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    invoked_at      TIMESTAMP    NOT NULL,
    trace_id        VARCHAR(64)  NOT NULL,
    span_id         VARCHAR(64),

    model_provider  VARCHAR(32)  NOT NULL,
    model_name      VARCHAR(128) NOT NULL,
    user_id         VARCHAR(128),
    team_id         VARCHAR(64),
    prompt          CLOB         NOT NULL,
    response        CLOB,
    token_in        INT,
    token_out       INT,
    latency_ms      INT          NOT NULL,
    cost_micro_krw  BIGINT,

    status          VARCHAR(16)  NOT NULL,
    error_class     VARCHAR(256),
    error_message   CLOB,
    finish_reason   VARCHAR(32),
    tool_calls_json JSON,

    metadata_json   JSON,

    pii_masked         BOOLEAN   NOT NULL DEFAULT FALSE,
    masked_pii_count   INT       NOT NULL DEFAULT 0,
    external_sent      BOOLEAN   NOT NULL DEFAULT FALSE,
    flagged            BOOLEAN   NOT NULL DEFAULT FALSE,

    compliance_profile VARCHAR(32) NOT NULL DEFAULT 'default'
);

-- H2는 DESC/partial 인덱스 미지원 → 일반 인덱스 5종으로 단순화
CREATE INDEX idx_lil_invoked_desc ON llm_invocation_log (invoked_at);
CREATE INDEX idx_lil_trace        ON llm_invocation_log (trace_id);
CREATE INDEX idx_lil_user_time    ON llm_invocation_log (user_id, invoked_at);
CREATE INDEX idx_lil_team_time    ON llm_invocation_log (team_id, invoked_at);
CREATE INDEX idx_lil_model_time   ON llm_invocation_log (model_provider, model_name, invoked_at);
