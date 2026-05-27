# Contributing

Issues and pull requests are welcome. This project is in early development (v0.1 MVP), so the contribution model will tighten once a stable release is published.

## Development Setup

### Prerequisites
- Java 17 or later (21 recommended)
- Gradle 8.10 or later (or use the wrapper)
- PostgreSQL 14+ for integration tests (or rely on H2 for fast iteration)
- Git

### Local build
```bash
git clone https://github.com/<owner>/chatmodel-audit-spring-boot-starter.git
cd chatmodel-audit-spring-boot-starter
./gradlew build
```

### Running tests
```bash
./gradlew test                        # all unit + integration (H2)
./gradlew test --tests "*PiiDetector*"   # focused
```

### Code style
- Java code style follows the Spring Framework conventions (see `eclipse-formatter.xml` once added in v0.2).
- Comments: Korean single-line `//` only, on non-obvious WHY. No Javadoc HTML tags, no `{@link}`. See `CLAUDE.md` for the full rule.

## Commit message
- One line, English or Korean, present-tense.
- Format: `feat(scope): description` (Conventional Commits).
- Do not include `Co-Authored-By` lines.
- Do not reference task tracker IDs in the message body (use PR description instead).

## Pull request
- Open against `main`.
- Include a one-paragraph description of what changes and why.
- Add tests for new behavior. Target ≥ 70% line coverage on touched files.
- Do not introduce new dependencies without prior discussion in an issue (build files are locked by convention).
- Do not introduce code that sends data to external SaaS (Langfuse, Phoenix, Helicone, etc.). The starter only writes to the user's own JDBC datasource.
- Do not implement `ChatMemoryRepository` or `MessageChatMemoryAdvisor`. These conflict with Spring AI Session API.

## Issue
Bug reports and feature requests are accepted in either English or Korean. Korean issues receive a first response within Korean business hours.

For security issues, see [SECURITY.md](SECURITY.md). Do not file public issues for vulnerabilities.

## Architecture changes
For non-trivial architecture changes (new module, new public API), open a discussion or an issue first with a one-page proposal. The starter has a deliberately narrow scope (audit only, not transport or analysis); see `docs/07-strengths-limitations.md` for the explicit non-goals.

## License
By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
