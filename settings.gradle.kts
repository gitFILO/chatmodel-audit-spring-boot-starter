rootProject.name = "chatmodel-audit-spring-boot-starter"

include("audit-starter")
include("audit-starter-tests")

// audit-core 모듈 분리는 O3에서 추가 (provider-agnostic core)
// audit-langchain4j-starter는 v0.3 예정 — settings 미포함
