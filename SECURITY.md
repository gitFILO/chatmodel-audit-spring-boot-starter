# Security Policy

## Supported Versions

While this project is in pre-1.0 development, only the latest minor release receives security patches.

| Version | Supported |
|---|---|
| 0.x   | The latest minor only |
| 1.x   | The latest minor (when released) |

## Reporting a Vulnerability

Do not file a public GitHub issue for vulnerabilities.

Report security issues via one of the following channels:

- Email: `security@example.com` (PGP key to be published with v0.2)
- GitHub Security Advisory: open a private advisory at <https://github.com/<owner>/chatmodel-audit-spring-boot-starter/security/advisories/new>

Please include:
- A description of the vulnerability and its impact
- Steps to reproduce (proof-of-concept code if possible)
- The affected version(s)
- Your contact information for follow-up

We aim to acknowledge reports within 72 hours and to provide an initial assessment within 7 days.

## Scope

The starter persists prompt and response bodies to the application's own datasource. Consider the following in your threat model:

- Prompt and response bodies may contain confidential information. Encrypt the underlying volume and restrict Actuator endpoints with Spring Security (`ROLE_AUDIT_VIEWER`).
- PII redaction (`kr-financial` mode) uses regular expression matching. It catches the documented eight Korean PII categories at high but not perfect accuracy. Operators are responsible for additional redaction rules specific to their domain.
- Search-time PII re-masking (`actuator.search.mask-output`) limits exposure when search results are returned over HTTP. It does not protect against direct database access.
- The starter does not send any prompt or response data outside the configured datasource. An optional OpenTelemetry exporter is opt-in from v0.5 and disabled by default.

## Korean financial compliance

The `kr-financial` mode is a good-faith mapping of public guidelines from the Korea Financial Services Commission, the Financial Supervisory Service, and related authorities as of 2026-05-27. It is not a substitute for formal compliance review. For audit-related questions, consult your information security officer and the Korea Financial Security Institute (FSI).
