# chatmodel-audit-spring-boot-starter

Spring AI 호출의 prompt, response, 토큰, 비용, trace 식별자를 사용자의 기존 데이터베이스에 영속화하는 Spring Boot 스타터입니다. 한국 금융권 컴플라이언스 매핑이 기본 포함되어 있습니다.

[![Maven Central|154](https://img.shields.io/maven-central/v/io.modelaudit/chatmodel-audit-spring-boot-starter.svg)](https://central.sonatype.com/artifact/io.modelaudit/chatmodel-audit-spring-boot-starter)
[![Build](https://github.com/gitFILO/chatmodel-audit-spring-boot-starter/actions/workflows/build.yml/badge.svg)](https://github.com/gitFILO/chatmodel-audit-spring-boot-starter/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.x-brightgreen)](https://docs.spring.io/spring-ai/reference/)

하루 한두 번 Spring AI를 호출하는 서비스는 프레임워크 내장 옵저베이션만으로도 충분합니다. 그러나 사내 챗봇, 코파일럿, MR 리뷰 봇, RAG 엔드포인트가 시간당 수십 건씩 호출하는 환경이 되면, 애플리케이션 로그는 흘러가버리고, 월말 외부 LLM 청구서는 부서별 책임 추적 없이 도착하며, "6개월 전 그 답변을 만든 prompt를 보여달라"는 감독기관 요청에 답할 수 없습니다. Spring AI는 prompt와 response 본문 영속화를 의도적으로 제공하지 않습니다(`httpexchanges`와 동일한 정책). Langfuse, Phoenix 같은 외부 도구는 본문을 영속화하지만 데이터를 사내망 밖 백엔드로 전송합니다.

`chatmodel-audit-spring-boot-starter`는 모든 Spring AI `ChatModel` 호출의 prompt, response, 토큰, 지연 시간, 비용, 사용자, 팀, trace 식별자를 애플리케이션 기존 데이터베이스에 9개 컬럼으로 기록하는 Spring Boot 3.x 스타터입니다. 자동 설정 시점에 Micrometer `ObservationHandler`를 등록하므로 애플리케이션 코드는 한 줄도 수정하지 않으며, 인공지능기본법·금융위 통합 AI 가이드라인·금감원 IT 감사 가이드라인에 매핑된 한국 금융권 프리셋이 기본 포함되어 있습니다.

## 목차

- [왜 만들었나](#왜-만들었나)
- [한국 규제 매핑](#한국-규제-매핑)
- [요구 사항](#요구-사항)
- [설치](#설치)
- [시작하기](#시작하기)
- [설정](#설정)
- [컴플라이언스 모드](#컴플라이언스-모드)
- [무엇을 기록하나](#무엇을-기록하나)
- [무엇을 기록하지 않나](#무엇을-기록하지-않나)
- [저장소 백엔드](#저장소-백엔드)
- [한국 PII와 마스킹](#한국-pii와-마스킹)
- [아키텍처](#아키텍처)
- [호환성 매트릭스](#호환성-매트릭스)
- [Actuator 엔드포인트](#actuator-엔드포인트)
- [용어표](#용어표)
- [문서](#문서)
- [기여](#기여)
- [라이선스](#라이선스)
- [면책 조항](#면책-조항)

## 왜 만들었나

한국 금융권에 사내 LLM 도입이 늘면서, AI 호출 본문을 사내 데이터베이스에 5년 이상 보관해야 한다는 요구가 빠르게 표준이 되고 있습니다. 그러나 Spring AI 자체는 본문 영속화를 제공하지 않고, 외부 도구는 사내망 정책과 맞지 않거나 한국 컴플라이언스를 모릅니다. 이 스타터는 그 빈자리를 채웁니다.

핵심 사실 다섯 가지:

- Spring AI는 `spring.ai.chat.observations.log-prompt`, `log-completion` 옵션을 제공하지만, prompt/response 본문 영속화는 의도적으로 제공하지 않습니다. 프레임워크는 `httpexchanges`와 동일한 정책을 따르며, 본문 보존은 애플리케이션 책임으로 둡니다.
- Langfuse, Phoenix, Helicone은 외부 백엔드로 데이터를 전송하는 구조입니다. 한국 금융권 망분리 환경에서는 별도 SaaS 예외 인증이 필요하며, 데이터 반출 정책상 도입이 어렵습니다.
- Spring AI Session API(`spring-ai-starter-session-jdbc`)는 대화 상태를 `sessionId` 기준으로 영속화합니다. 컴플라이언스 감사에 필요한 `model`, `token usage`, `cost`, `team`, `trace` 식별자는 기록하지 않습니다.
- 이 스타터는 `ObservationHandler` 위에서 동작하며, OpenTelemetry `gen_ai.*` 시맨틱 컨벤션 속성을 9개 컬럼으로 영속화합니다. 한국 금융권 프리셋(5년 보관, 한국 PII 마스킹, KRW 단가 카탈로그)은 한 줄 설정으로 활성화됩니다.
- 트레이드오프: 이 스타터는 prompt와 response 본문을 영속화합니다. 저장 공간 비용이 작지 않으며(평균 호출 100만 건당 약 7 GB), 호출량이 많은 환경에서는 본문 캡처를 끄거나 샘플링을 활성화하거나 보관 기간을 짧게 설정해야 합니다.

## 한국 규제 매핑

| 규제 | 시행 시점 | 자동 충족 항목 |
|---|---|---|
| 인공지능기본법 | 2026-01-22 | 고영향 AI 운영자 생성 로그 기록 — 9개 컬럼 영속화 + 5년 보관 |
| 금융위 「금융분야 통합 AI 가이드라인」 | 2026.1Q | 7대 원칙(보조수단성·보안성·신뢰성 등) — `external_sent` 추적, PII 마스킹 |
| 금감원 IT 감사 가이드라인 | 2025-02 | 추적성·증적·SoD — `trace_id`, `user_id`, `team_id`, 감사 보고서 PDF(v0.4) |
| 전자금융감독규정 제13조 3항 | 시행 중 | 1년 이상 보관 — `kr-financial` 프리셋이 5년 기본 |
| 개인정보보호법 + ISMS-P | 시행 중 | 한국 PII 8종 정규식 가명처리, `masked_pii_count` 컬럼 |

## 요구 사항

- Java 17 이상
- Spring Boot 3.2.x 이상
- Spring AI 1.1.x 이상 (2.0.x는 v0.3부터)
- 관계형 데이터소스: PostgreSQL 14+, MySQL 8+, H2 중 하나

## 설치

### Maven

```xml
<dependency>
    <groupId>io.modelaudit</groupId>
    <artifactId>chatmodel-audit-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("io.modelaudit:chatmodel-audit-spring-boot-starter:0.1.0")
```

스타터는 `spring-boot-starter-jdbc`, `spring-boot-starter-actuator`, `spring-ai-commons`, `micrometer-core`, `flyway-core`, `caffeine`을 전이 의존성으로 가져옵니다. 애플리케이션은 별도로 JDBC 드라이버와 Spring AI 모델 스타터(anthropic, openai, ollama 등) 하나 이상을 추가해야 합니다.

## 시작하기

### 1. 의존성 추가

위의 Maven 또는 Gradle 좌표를 애플리케이션에 추가합니다.

### 2. 스타터 활성화

`application.yml`에 다음을 추가합니다.

```yaml
audit:
  compliance:
    enabled: true
```

다음 부팅 시 Flyway가 `V_audit_001__create_llm_invocation_log.sql` 마이그레이션을 애플리케이션의 `spring.datasource`에 적용합니다.

### 3. Spring AI `ChatClient` 호출

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

스타터는 호출마다 `llm_invocation_log`에 한 행을 영속화합니다. `/actuator/llm-audit/stats`에서 집계 메트릭을, `/actuator/llm-audit/search?q=...`에서 전문 검색을 확인할 수 있습니다.

## 설정

최소 설정은 세 줄이며, 한국 금융 프리셋은 한 줄을 추가합니다.

```yaml
audit:
  compliance:
    enabled: true
    compliance:
      mode: kr-financial
```

`kr-financial` 모드는 한국 PII 마스킹(8종), 1825일(5년) 보관, KRW 단가 카탈로그, Actuator 검색 응답의 `mask-output`을 한 번에 활성화합니다.

전체 설정 키 목록은 v0.1 동봉되는 `samples/kr-financial-sample` 의 `application.yml` 에 `audit.compliance.*` 형식으로 추가될 예정입니다.

## 컴플라이언스 모드

`compliance.mode`로 다음 4가지 프리셋을 사용할 수 있습니다. 보관 기간, PII 감지, 단가 카탈로그 통화, 검색 마스킹 기본값이 한 줄로 설정되며, 개별 항목은 모두 오버라이드 가능합니다.

| 모드 | 보관 기간 | PII 감지 | 단가 통화 | 검색 마스킹 | 대상 |
|---|---|---|---|---|---|
| `default` | 365일 | off | USD | off | 일반 용도 |
| `kr-financial` | 1825일 (5년) | 한국 PII 8종 on | KRW | on | 금융위 감독 대상 은행·증권·보험·카드 |
| `kr-insurance` | 1095일 (3년) | 한국 PII 8종 on | KRW | on | 보험업법 적용 보험사 |
| `kr-medical` | 3650일 (10년) | 한국 PII 8종 + 의료 | KRW | on | 의료법 적용 의료기관 |

`flagged` 행은 보관 기간과 무관하게 영구 보존됩니다. cleanup 잡(`audit.compliance.retention.cleanup-cron`)은 `max-age-days`를 넘긴 비-flagged 행만 삭제합니다.

## 무엇을 기록하나

| 컬럼 | OTel 속성 | 설명 |
|---|---|---|
| `model_provider` | `gen_ai.system` | anthropic, openai, ollama 등 |
| `model_name` | `gen_ai.request.model` | claude-opus-4-7, qwen2.5-coder:7b |
| `prompt` | `gen_ai.prompt` | prompt 본문 (PII 마스킹 후) |
| `response` | `gen_ai.completion` | response 본문 (실패 시 NULL) |
| `token_in` | `gen_ai.usage.input_tokens` | 입력 토큰 수 |
| `token_out` | `gen_ai.usage.output_tokens` | 출력 토큰 수 |
| `latency_ms` | (계산) | 호출 종단 지연 시간 |
| `cost_micro_krw` | (계산) | 단가 카탈로그 기반 비용, 마이크로 원 |
| `user_id`, `team_id` | (resolve) | Spring Security 또는 MDC |
| `trace_id`, `span_id` | (Micrometer Tracing) | 시스템 간 상관 |
| `status`, `error_class`, `error_message` | (계산) | SUCCESS, FAILED, CANCELLED |
| `finish_reason`, `tool_calls_json` | (Spring AI Usage) | stop, length, tool_calls |
| `pii_masked`, `masked_pii_count`, `external_sent`, `flagged` | (계산) | 감사 및 컴플라이언스 플래그 |

## 무엇을 기록하지 않나

이 스타터는 의도적으로 감사에 범위를 한정하며, 본문 운반이나 분석은 다루지 않습니다. 다음 항목은 디스크에 기록되지 않으며 애플리케이션의 JDBC 데이터소스 외 어디로도 전송되지 않습니다.

- **`kr-financial` 모드에서 원본 PII.** 주민등록번호, 계좌번호, 카드번호 등 한국 PII 8종은 행이 기록되기 전에 토큰으로 대체됩니다. 토큰-원본 매핑은 TTL 600초의 in-memory Caffeine 캐시에만 보관됩니다.
- **외부 옵저버빌리티 백엔드로의 트래픽.** 스타터는 Langfuse, Phoenix, LangSmith, Helicone, Datadog 어디로도 데이터를 전송하지 않습니다. 감사 행은 애플리케이션이 이미 설정한 JDBC 데이터소스에만 기록됩니다. OpenTelemetry exporter는 v0.5부터 opt-in이며 기본 비활성입니다.
- **스타터에 의한 prompt/response 외부 송신.** Spring AI `ChatModel`이 제공자(Anthropic, OpenAI, Ollama)에 보내는 외부 트래픽은 애플리케이션 기존 코드 경로에서 그대로 발생하며, 본 스타터는 새로운 외부 연결을 추가하지 않습니다.
- **워커 텔레메트리, 사용 보고, 익명 통계.** 스타터는 phone home하지 않습니다.

대신 트레이드오프는 명확합니다. 애플리케이션 자체 데이터베이스가 이제 prompt/response 본문을 보관하게 됩니다. 기반 볼륨을 암호화하고, Spring Security(`ROLE_AUDIT_VIEWER`)로 Actuator 엔드포인트를 제한하며, 검색 시점 PII 재마스킹(`actuator.search.mask-output: true`, `kr-financial` 모드에서 기본 on)으로 2차 노출을 제한합니다.

## 저장소 백엔드

| 백엔드 | v0.1 | v0.2 | v1.0 | 비고 |
|---|---|---|---|---|
| PostgreSQL 14+ | 지원 | 지원 | 지원 | 1순위 백엔드, JSONB 사용 |
| H2 | 지원 | 지원 | 지원 | 테스트용 |
| MySQL 8+ | — | 지원 | 지원 | JSON 컬럼 타입 |
| Oracle 19c+ | — | — | 지원 | 엔터프라이즈 수요 |

스타터는 `DatabaseDriver` 감지로 Flyway dialect 디렉토리를 자동 선택합니다. `audit.compliance.schema`, `audit.compliance.table-name`으로 스키마와 테이블 이름을 변경할 수 있습니다.

## 한국 PII와 마스킹

`compliance.mode: kr-financial` 모드에서는 다음 8종의 한국 PII가 영속화 직전과 외부 LLM 송신 직전에 마스킹됩니다.

- 주민등록번호
- 계좌번호
- 카드번호
- 사업자등록번호
- 전화번호
- 이메일 주소
- 건강보험번호
- 외국인등록번호

마스킹된 토큰은 in-memory Caffeine 캐시에 TTL 600초로 보관됩니다. `pii.restore-in-response: true`로 설정하면 response에서 자동 복원됩니다. 영속화되는 행에는 마스킹된 본문만 저장되며, 원본 PII는 디스크에 기록되지 않습니다.

사내 PII 패턴은 `PiiDetector` 빈을 등록하여 확장할 수 있습니다.

```java
@Bean
PiiDetector enterpriseDetector() {
    return new ChainPiiDetector(new KoreanPiiDetector(), new EmployeeIdDetector());
}
```

## 아키텍처

```
ChatClient.prompt().user(...).call()
        |
        v
Spring AI ChatModel + ObservationRegistry
        |
        +-- PiiMaskObservationHandler (kr-financial 모드)
        +-- AuditObservationHandler
                  |
                  v
            AsyncBatchWriter (ArrayBlockingQueue, 500ms 또는 100행 flush)
                  |
                  v
            JdbcAuditRecordRepository.batchInsert
                  |
                  v
            llm_invocation_log (사용자 datasource)
```

스타터는 비동기로 영속화하므로 애플리케이션의 호출 응답 경로에는 영향을 주지 않습니다. 큐 오버플로 기본 정책은 `BLOCK`이며 모든 행을 보존합니다. 호출량이 많은 환경에서는 `DROP` 모드를 사용할 수 있고, 두 모드 모두 Micrometer counter를 노출합니다.

자세한 컴포넌트 다이어그램과 아키텍처 의사결정(ADR-007 — ComplianceProfile + PiiDetector SPI 포함)은 v0.1 동안 내부 설계 노트에 보관되며, v0.2에 선별 공개될 예정입니다.

## 호환성 매트릭스

| 스타터 | Spring Boot | Spring AI | Java |
|---|---|---|---|
| 0.1.x | 3.2, 3.3, 3.4 | 1.1.x | 17, 21 |
| 0.2.x | 3.3, 3.4, 3.5 | 1.1.x | 17, 21 |
| 0.3.x | 3.4, 3.5 | 1.1.x, 2.0.x | 17, 21 |
| 1.0.x | 3.5 | 2.0.x | 21 |

## 프로바이더 어댑터

이 스타터는 provider-agnostic core와 각 프로바이더 어댑터로 분리됩니다. 단일 audit 테이블이 모든 어댑터의 레코드를 받습니다.

| 모듈 | 프로바이더 | hook | 상태 |
|---|---|---|---|
| `audit-core` | (없음, 공유) | — | v0.1 |
| `audit-spring-boot-starter` | Spring AI 1.1+ | Micrometer `ObservationHandler` | v0.1 (현재 artifact) |
| `audit-langchain4j-starter` | LangChain4j 0.36+ | `ChatModelListener` | v0.3 (예정) |

두 스타터를 동시에 추가하면 Spring AI와 LangChain4j의 audit이 같은 테이블에 기록됩니다. `model_provider` 컬럼이 레코드를 구분합니다.

## Actuator 엔드포인트

`management.endpoints.web.exposure.include`가 허용하면 `/actuator/llm-audit` 아래에 다음 엔드포인트가 노출됩니다.

- `stats` — 팀, 사용자, 모델별 호출 수, 비용, 지연 시간 백분위 집계
- `search` — prompt/response 전문 검색 + 필터 파라미터
- `by-trace/{traceId}` — 단일 trace에 속한 모든 호출
- `flag/{id}` — 사후 검토 후 영구 보관으로 표시
- `export` — CSV 또는 JSON 내보내기. v0.4부터 금감원 IT 감사 양식에 맞춘 PDF 내보내기 지원
- `health` — 큐 깊이, 마지막 flush 시점, DB 도달 여부

## 내장 대시보드 UI

`spring-boot-starter-thymeleaf` 의존성을 추가하면 `/actuator/llm-audit/dashboard`에서 HTML 대시보드가 활성화됩니다. 5 페이지: 개요(KPI 차트), 검색(필터 + PII 재마스킹), trace timeline, flag 폼, 컴플라이언스 상태. 한국어/영문 i18n.

별도 웹 앱 없음, Node.js runtime 없음, Docker 없음. Thymeleaf 렌더, HTMX(CDN)로 부분 갱신, Chart.js(CDN) 차트. 모두 스타터와 같은 jar 안에 있습니다.

접근 제어는 Spring Security `ROLE_AUDIT_VIEWER`(읽기) / `ROLE_AUDIT_OWNER`(flag 등록)로 제한합니다.

## Grafana 대시보드 (v0.2)

v0.2+에서 `grafana-dashboard.json`이 동봉됩니다. Prometheus 기반 SRE 워크플로용. Grafana에서 한 번 import + datasource 매핑이면 12 패널 작동: 호출수·KRW 비용·지연 P50/P95/P99·토큰·에러율·queue depth·drops·외부 송신 비율·PII 마스킹 통과율·모델별·팀별.

## 용어표

영문 README와의 용어 매핑입니다. 한국 Spring 개발자의 음역/번역 관행을 따랐습니다.

| 영문 | 한글 | 비고 |
|---|---|---|
| starter | 스타터 | 음역 |
| compliance | 컴플라이언스 | 금융권 표준 |
| audit | 감사 로그 | 번역 |
| dependency | 의존성 | 번역 |
| observation handler | 옵저베이션 핸들러 | 음역 |
| webhook | 웹훅 | 음역 |
| guardrail | 가드레일 | 음역 |
| prompt injection | 프롬프트 인젝션 | 음역 |
| redaction | 가명처리 / 마스킹 | 혼용 |
| sampling | 샘플링 | 음역 |
| retention | 보관 기간 | 번역 |
| spool | 스풀 | 음역 |

## 다른 국가로 확장 — SPI

이 스타터는 두 개의 SPI(ADR-007) 위에 얹혀 있으며, `kr-financial` 모드는 **레퍼런스 구현**일 뿐 고정된 천장이 아닙니다.

- `ComplianceProfile` — 보관 일수, PII 종류 목록, 비용 통화, 검색 시 마스킹 기본값. `audit.compliance.mode` 와 `name()` 매칭으로 lookup.
- `PiiDetector` — `id()` + `mask(input)`. `List<PiiDetector>` 자동 주입으로 수집되며 `ComplianceProfile.piiProviders()` 의 id 와 일치하는 detector 만 활성.

새 관할권(예: US, EU, JP) 추가 시 작은 동반 스타터 모듈을 만들면 됩니다.

```java
@AutoConfiguration
@AutoConfigureAfter(ComplianceAuditAutoConfiguration.class)
public class UsFinancialAutoConfiguration {
    @Bean UsFinancialProfile usFinancialProfile() { return new UsFinancialProfile(); }
    @Bean UsSsnDetector usSsn() { return new UsSsnDetector(); }
    @Bean UsItinDetector usItin() { return new UsItinDetector(); }
}
```

사용자는 한 줄로 전환합니다.

```yaml
audit:
  compliance:
    mode: us-financial
```

이 코어 저장소에 fork 나 PR 없이 가능합니다. 사용자 정의 PII detector 도 동일한 방식 — `@Bean` 으로 등록 + 활성 프로파일의 `piiProviders()` 에 자기 `id()` 가 포함되는 순간 활성화됩니다.

## 문서

공개 문서는 이 README, 동봉되는 `samples/`, 그리고 소스 레벨 JavaDoc 으로 통합 중입니다. 내부 설계 노트 (비전, ADR, 로드맵, 한국 컴플라이언스 매핑 디테일) 는 v0.1 동안 비공개 vault 에 보관되며, v0.2 에 선별 공개될 예정입니다.

영문 README는 [README_eng.md](README_eng.md)에 있습니다.

## 기여

이슈와 PR을 환영합니다. 개발 환경 설정과 커밋 메시지 컨벤션은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

**이슈는 한국어로 작성해도 됩니다.** 한국어 이슈는 한국 시각 기준 영업일 안에 1차 응답을 드립니다.

보안 취약점은 공개 이슈가 아닌 [SECURITY.md](SECURITY.md)에 명시된 경로로 신고합니다.

## 라이선스

Apache License 2.0. 전체 라이선스 본문은 [LICENSE](LICENSE) 파일에 있습니다.

## 면책 조항

`chatmodel-audit-spring-boot-starter`는 Anthropic, OpenAI, Google, Alibaba, Broadcom의 Spring 팀, Apache Software Foundation, 금융위원회, 금융감독원, 그 외 어떤 제3자와도 제휴·후원 관계가 없습니다. Spring AI, Spring Boot, Micrometer, OpenTelemetry, Claude, GPT, Qwen, Gemini, Ollama, Langfuse, Phoenix 등 제품명은 각 권리자의 상표이며, 호환성을 나타내거나 스타터의 자리매김 맥락을 제공하기 위해서만 사용됩니다.

`kr-financial` 모드의 한국 규제 매핑은 2026-05-27 기준 공개된 가이드라인의 선의(good-faith) 구현입니다. 법률 자문, IT 감사 인증, 금융보안원(FSI) 또는 그 외 기관의 공식 컴플라이언스 검토를 대체하지 않습니다. 컴플라이언스 적합성 평가는 운영자의 책임입니다.
