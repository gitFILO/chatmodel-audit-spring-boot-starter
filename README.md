# chatmodel-audit-spring-boot-starter

Spring AI ChatModel invocations persisted to your own database, with built-in Korean financial compliance mappings.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.gitfilo/chatmodel-audit-spring-boot-starter.svg)](https://central.sonatype.com/artifact/io.github.gitfilo/chatmodel-audit-spring-boot-starter)
[![Build](https://github.com/gitfilo/chatmodel-audit-spring-boot-starter/actions/workflows/build.yml/badge.svg)](https://github.com/gitfilo/chatmodel-audit-spring-boot-starter/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.x-brightgreen)](https://docs.spring.io/spring-ai/reference/)

A Spring Boot service that calls Spring AI once or twice a day stays inside the framework's built-in observability. At dozens of calls per hour — internal chatbots, copilots, MR review bots, RAG endpoints — the application log scrolls past, monthly cloud LLM invoices arrive without per-team attribution, and a regulator asking "show me the prompt that produced this answer six months ago" has no answer. Spring AI itself avoids persisting prompt and response bodies (the same policy applied to `httpexchanges`); external tools like Langfuse and Phoenix do persist them, but ship the data to a backend outside the corporate network.

`chatmodel-audit-spring-boot-starter` is a Spring Boot 3.x starter that records every Spring AI `ChatModel` invocation — prompt, response, tokens, latency, cost, user, team, and trace identifier — to a relational database the application already owns. It registers a Micrometer `ObservationHandler` at auto-configuration time, so no application code change is required, and ships a Korean financial compliance preset that maps to the AI Basic Act, FSC AI Guidelines, and FSS IT audit guidelines out of the box.

## Table of Contents

- [Why we built this](#why-we-built-this)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Compliance Modes](#compliance-modes)
- [What Gets Captured](#what-gets-captured)
- [What This Starter Never Persists](#what-this-starter-never-persists)
- [Storage Backends](#storage-backends)
- [Privacy and Redaction](#privacy-and-redaction)
- [Architecture](#architecture)
- [Compatibility Matrix](#compatibility-matrix)
- [Actuator Endpoints](#actuator-endpoints)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)

## Why we built this

- Spring AI exposes `spring.ai.chat.observations.log-prompt` and `log-completion`, but it intentionally avoids persisting prompt and response bodies. The framework follows the same policy as `httpexchanges` — body retention is left to the application.
- Langfuse, Phoenix, and Helicone solve audit by shipping data to an external backend. Korean financial institutions cannot send prompt content outside the corporate network without a separate SaaS exemption review.
- Spring AI Session API (`spring-ai-starter-session-jdbc`) persists conversation state by `sessionId`. It does not record `model`, `token usage`, `cost`, `team identifier`, or `trace identifier` required for IT audit.
- This starter sits on top of `ObservationHandler`, persists OpenTelemetry `gen_ai.*` semantic-convention attributes into a nine-column row, and adds Korean financial compliance presets — five-year retention, Korean PII redaction, KRW cost catalog.
- Trade-off: this starter persists prompt and response bodies. Storage cost is non-trivial (about 7 GB per million calls at average sizes). Disable body capture, enable sampling, or set a shorter retention for high-volume workloads.

## Requirements

- Java 17 or later
- Spring Boot 3.2.x or later
- Spring AI 1.1.x or later (2.0.x supported from v0.3)
- A relational datasource: PostgreSQL 14+, MySQL 8+, or H2

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.gitfilo</groupId>
    <artifactId>chatmodel-audit-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("io.github.gitfilo:chatmodel-audit-spring-boot-starter:0.1.0")
```

The starter brings in `spring-boot-starter-jdbc`, `spring-boot-starter-actuator`, `spring-ai-commons`, `micrometer-core`, `flyway-core`, and `caffeine`. The application must provide its own JDBC driver and at least one Spring AI model starter.

## Getting Started

### 1. Add the dependency

Add the Maven or Gradle coordinate shown above to the application.

### 2. Enable the starter

Add the following to `application.yml`:

```yaml
audit:
  compliance:
    enabled: true
```

Flyway runs migration `V_audit_001__create_llm_invocation_log.sql` against the application's `spring.datasource` on the next boot.

### 3. Invoke any Spring AI `ChatClient`

```java
@Service
class LoanAdviceService {

    private final ChatClient chatClient;

    LoanAdviceService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    String advise(String userQuery) {
        return chatClient.prompt().user(userQuery).call().content();
    }
}
```

The starter persists one row to `llm_invocation_log` per invocation. Inspect `/actuator/llm-audit/stats` for aggregate metrics or `/actuator/llm-audit/search?q=...` for full-text retrieval.

## Configuration

Minimum configuration is three lines. The Korean financial preset is one additional line:

```yaml
audit:
  compliance:
    enabled: true
    compliance:
      mode: kr-financial
```

`kr-financial` mode enables Korean PII redaction (eight patterns), sets retention to 1825 days, switches the cost catalog to KRW, and forces `mask-output` on Actuator search responses.

The full property reference is in [docs/configuration.md](docs/configuration.md).

## Compliance Modes

Four presets are available via `compliance.mode`. Each sets retention, PII detection, cost catalog currency, and search masking defaults in one line. Individual properties remain overridable.

| Mode | Retention | PII detection | Cost currency | Search masking | Intended for |
|---|---|---|---|---|---|
| `default` | 365 days | off | USD | off | Generic use |
| `kr-financial` | 1825 days (5 yr) | 8 Korean patterns on | KRW | on | Banks, brokerages, insurance, card issuers under FSC supervision |
| `kr-insurance` | 1095 days (3 yr) | 8 Korean patterns on | KRW | on | Insurers under Insurance Business Act |
| `kr-medical` | 3650 days (10 yr) | 8 Korean + medical | KRW | on | Healthcare under Medical Service Act |

`flagged` rows are preserved indefinitely regardless of retention. The cleanup job (`audit.compliance.retention.cleanup-cron`) deletes only unflagged rows older than `max-age-days`.

## What Gets Captured

| Column | OTel attribute | Description |
|---|---|---|
| `model_provider` | `gen_ai.system` | anthropic, openai, ollama, etc. |
| `model_name` | `gen_ai.request.model` | claude-opus-4-7, qwen2.5-coder:7b |
| `prompt` | `gen_ai.prompt` | Prompt body, after PII masking |
| `response` | `gen_ai.completion` | Response body, NULL on failure |
| `token_in` | `gen_ai.usage.input_tokens` | Prompt token count |
| `token_out` | `gen_ai.usage.output_tokens` | Completion token count |
| `latency_ms` | (derived) | End-to-end call latency |
| `cost_micro_krw` | (computed) | Cost from pricing catalog, micro-KRW |
| `user_id`, `team_id` | (resolved) | Spring Security or MDC |
| `trace_id`, `span_id` | (Micrometer Tracing) | Cross-system correlation |
| `status`, `error_class`, `error_message` | (derived) | SUCCESS, FAILED, CANCELLED |
| `finish_reason`, `tool_calls_json` | (Spring AI Usage) | stop, length, tool_calls |
| `pii_masked`, `masked_pii_count`, `external_sent`, `flagged` | (derived) | Audit and compliance flags |

## What This Starter Never Persists

The starter is deliberately scoped to audit, not to transport or analyze content. The following are never written to disk or sent anywhere outside the application's own JDBC datasource:

- **Raw PII when `kr-financial` mode is active.** Resident registration numbers, account numbers, card numbers, and the other six Korean PII categories are replaced by tokens before the row is written. The token-to-original map is held only in an in-memory Caffeine cache with a 600-second TTL.
- **No traffic to external observability backends.** The starter does not send data to Langfuse, Phoenix, LangSmith, Helicone, or Datadog. Audit rows only land in the JDBC datasource the application already configures. An optional OpenTelemetry exporter is opt-in from v0.5 and disabled by default.
- **No prompt or response content sent over the network by this starter.** Outbound traffic from a Spring AI `ChatModel` to its provider (Anthropic, OpenAI, Ollama) still happens in the application's existing code path; this starter does not add any new outbound connection.
- **No worker telemetry, no usage reporting, no anonymous statistics.** The starter does not phone home.

The trade-off is the inverse: the application's own database now holds prompt and response bodies. Encrypt the underlying volume, restrict the Actuator endpoints with Spring Security (`ROLE_AUDIT_VIEWER`), and rely on search-time PII re-masking (`actuator.search.mask-output: true`, on by default in `kr-financial`) to limit secondary exposure.

## Storage Backends

| Backend | v0.1 | v0.2 | v1.0 | Notes |
|---|---|---|---|---|
| PostgreSQL 14+ | Supported | Supported | Supported | Primary backend, uses JSONB |
| H2 | Supported | Supported | Supported | For tests |
| MySQL 8+ | — | Supported | Supported | JSON column type |
| Oracle 19c+ | — | — | Supported | Enterprise demand |

The starter selects the Flyway dialect directory automatically via `DatabaseDriver` detection. Override the schema or table name with `audit.compliance.schema` and `audit.compliance.table-name`.

## Privacy and Redaction

When `compliance.mode: kr-financial` is active, the starter masks eight categories of Korean PII before persistence and before sending the prompt to an external provider:

- Resident registration number (`주민등록번호`)
- Bank account number
- Card number
- Business registration number (`사업자등록번호`)
- Phone number
- Email address
- Health insurance number
- Foreigner registration number

Masked tokens are kept in an in-memory Caffeine cache with a TTL of 600 seconds. The response can be optionally restored with `pii.restore-in-response: true`. The persisted row contains only masked bodies; PII is never written to disk.

Custom detectors can be added by registering a `PiiDetector` bean:

```java
@Bean
PiiDetector enterpriseDetector() {
    return new ChainPiiDetector(new KoreanPiiDetector(), new EmployeeIdDetector());
}
```

## Architecture

```
ChatClient.prompt().user(...).call()
        |
        v
Spring AI ChatModel + ObservationRegistry
        |
        +-- PiiMaskObservationHandler (kr-financial mode)
        +-- AuditObservationHandler
                  |
                  v
            AsyncBatchWriter (ArrayBlockingQueue, 500 ms or 100-row flush)
                  |
                  v
            JdbcAuditRecordRepository.batchInsert
                  |
                  v
            llm_invocation_log (your datasource)
```

The starter writes asynchronously to keep the application call path unaffected. Default overflow policy is `BLOCK` to preserve every record. A `DROP` mode is available for high-throughput workloads; both modes emit a Micrometer counter.

A detailed component diagram and ADRs are in [docs/architecture.md](docs/architecture.md).

## Compatibility Matrix

| Starter | Spring Boot | Spring AI | Java |
|---|---|---|---|
| 0.1.x | 3.2, 3.3, 3.4 | 1.1.x | 17, 21 |
| 0.2.x | 3.3, 3.4, 3.5 | 1.1.x | 17, 21 |
| 0.3.x | 3.4, 3.5 | 1.1.x, 2.0.x | 17, 21 |
| 1.0.x | 3.5 | 2.0.x | 21 |

## Provider Adapters

This starter is split into a provider-agnostic core and per-provider adapters. A single audit table receives records from any combination of adapters.

| Module | Provider | Hook | Status |
|---|---|---|---|
| `audit-core` | (none, shared) | — | v0.1 |
| `audit-spring-boot-starter` | Spring AI 1.1+ | Micrometer `ObservationHandler` | v0.1 (this artifact) |
| `audit-langchain4j-starter` | LangChain4j 0.36+ | `ChatModelListener` | v0.3 (planned) |

Add both starters to record audits from Spring AI and LangChain4j in the same application. The `model_provider` column distinguishes records.

## Actuator Endpoints

The starter exposes endpoints under `/actuator/llm-audit` when `management.endpoints.web.exposure.include` permits them:

- `stats` — aggregate counts, costs, and latency percentiles by team, user, or model
- `search` — full-text search over prompt and response with filter parameters
- `by-trace/{traceId}` — all invocations belonging to a single trace
- `flag/{id}` — mark an invocation for permanent retention after a post hoc review
- `export` — CSV or JSON export, with a PDF export aligned to the Korean FSC IT audit format from v0.4
- `health` — queue depth, last flush timestamp, database reachability

## Built-in Dashboard UI

Add `spring-boot-starter-thymeleaf` to enable an embedded HTML dashboard at `/actuator/llm-audit/dashboard`. Five pages: overview (KPI charts), search (filters + PII re-masking), trace timeline, flag form, and compliance status. Korean and English i18n.

No separate web app, no Node.js runtime, no Docker required. Pages render with Thymeleaf, partial updates use HTMX (CDN), charts use Chart.js (CDN). All in the same jar as the starter.

Restrict access with Spring Security `ROLE_AUDIT_VIEWER` (read) and `ROLE_AUDIT_OWNER` (flag submission). See `docs/13-dashboard-ui.md` for the full specification.

## Grafana Dashboard (v0.2)

A `grafana-dashboard.json` is bundled in v0.2+ for Prometheus-based SRE workflows. Import once and map your datasource. Twelve panels: invocation count, KRW cost, latency p50/p95/p99, tokens, error rate, queue depth, drops, external send ratio, PII mask pass rate, by-model, by-team.

## Documentation

Detailed documentation lives in the [`docs/`](docs/) directory:

- [Vision and Positioning](docs/01-vision-and-positioning.md)
- [Incumbent Analysis](docs/02-incumbent-analysis.md)
- [Architecture](docs/03-architecture.md)
- [Data Model](docs/04-data-model.md)
- [API and Configuration](docs/05-api-and-configuration.md)
- [Korean Compliance](docs/06-korean-compliance.md)
- [Strengths and Limitations](docs/07-strengths-limitations.md)
- [Roadmap](docs/08-roadmap-and-milestones.md)
- [Testing Strategy](docs/09-testing-strategy.md)
- [Implementation Guide](docs/11-implementation-guide.md)

For Korean readers, see [README_kr.md](README_kr.md). The Korean version adds a Korean regulation mapping table and a terminology glossary not present in this README.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development setup and the commit message convention.

For security reports, please follow [SECURITY.md](SECURITY.md) and do not file public issues for vulnerabilities.

## License

Apache License 2.0. See [LICENSE](LICENSE) for the full text.

## Disclaimer

`chatmodel-audit-spring-boot-starter` is not affiliated with, endorsed by, or sponsored by Anthropic, OpenAI, Google, Alibaba, the Spring team at Broadcom, the Apache Software Foundation, the Korea Financial Services Commission (FSC), the Financial Supervisory Service (FSS), or any other third party. Spring AI, Spring Boot, Micrometer, OpenTelemetry, Claude, GPT, Qwen, Gemini, Ollama, Langfuse, Phoenix, and other product names are trademarks of their respective owners and are used here only to indicate compatibility or to provide context for the starter's positioning.

The Korean regulation mapping in `kr-financial` mode is a good-faith implementation of public guidelines as of 2026-05-27. It is not a substitute for legal counsel, IT audit certification, or formal compliance review by the Korea Financial Security Institute (FSI) or any other authority. Operators are responsible for their own compliance assessment.
