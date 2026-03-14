# Ditto Server

Kotlin + Spring Boot 기반 백엔드 서버

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 21 (LTS) |
| Kotlin | 1.9.25 |
| Spring Boot | 3.5.11 |
| Gradle | 8.14 |

---

## 프로젝트 구조

```
ditto-server/
├── api/                         # Spring Boot 실행 모듈
│   └── src/main/kotlin/com/ditto/api/
│       ├── DittoApplication.kt
│       ├── HealthCheckController.kt
│       └── config/
│           ├── auth/
│           │   ├── ApiKeyAuthFilter.kt
│           │   └── ApiKeyProperties.kt
│           ├── exception/
│           │   └── GlobalExceptionHandler.kt
│           ├── logging/
│           │   ├── LoggingAspect.kt
│           │   └── RequestIdFilter.kt
│           ├── JacksonConfig.kt
│           ├── OpenApiConfig.kt
│           └── SecurityConfig.kt
├── common/                      # 순수 Kotlin 공통 유틸
│   └── src/main/kotlin/com/ditto/common/
│       ├── exception/           # WarnException, ErrorException, ErrorCode
│       ├── logging/             # @Loggable
│       ├── response/            # ApiResponse
│       └── serialization/       # ObjectMapperFactory, DateTimeFormats
├── domain/                      # 엔티티, VO, 리포지토리
├── infrastructure/              # 외부 인프라 (Redis 등)
├── buildSrc/                    # Convention Plugins
│   └── src/main/kotlin/
│       ├── DependencyVersions.kt
│       ├── kotlin-convention.gradle.kts
│       ├── spring-convention.gradle.kts
│       ├── restdocs-convention.gradle.kts
│       └── sonar-convention.gradle.kts
├── .aws/
│   ├── task-definition.json     # ECS Task Definition
│   └── alloy/
│       ├── config.alloy         # Grafana Alloy 설정
│       └── Dockerfile
├── .github/workflows/
│   ├── ci.yml                   # PR: 빌드 + 테스트 + SonarCloud
│   └── cd.yml                   # main push: ECR → ECS 배포
└── Dockerfile                   # API 이미지 (multi-stage)
```

---

## 인프라 아키텍처

```
                         ┌─────────────────┐
                         │   Route 53      │
                         └────┬───────┬────┘
                              │       │
                   ┌──────────┘       └──────────┐
                   ▼                              ▼
          ┌────────────────┐            ┌────────────────┐
          │  CloudFront    │            │  CloudFront    │
          │  ditto.pics    │            │api.ditto.pics  │
          └───────┬────────┘            └───────┬────────┘
                  │                             │
                  ▼                             ▼
          ┌────────────────┐     ┌──────────────────────────┐
          │  S3 Bucket     │     │  VPC - Public Subnet      │
          │  (프론트엔드)    │     │                          │
          └────────────────┘     │  ┌──────────────────────┐│
                                 │  │ ECS Fargate Task      ││
                                 │  │ (Public IP)           ││
                                 │  │                      ││
                                 │  │  ┌────────────────┐  ││
                                 │  │  │ ditto-api :8080 │  ││
                                 │  │  └──┬─────────────┘  ││
                                 │  │     │localhost        ││
                                 │  │  ┌──▼─────────────┐  ││
                                 │  │  │ ditto-db :3306  │  ││
                                 │  │  └────────────────┘  ││
                                 │  │                      ││
                                 │  │  ┌────────────────┐  ││
                                 │  │  │ alloy          │──── remote_write ──→ Grafana Cloud
                                 │  │  │ (메트릭 수집)    │  ││
                                 │  │  └────────────────┘  ││
                                 │  └──────────────────────┘│
                                 │                          │
                                 │  [Security Group]        │
                                 │  - 8080: CloudFront only │
                                 │  - 3306: 내부만           │
                                 └──────────────────────────┘

          monitor.ditto.pics → CNAME → Grafana Cloud URL
```

### 도메인

| 도메인 | 용도 | 연결 대상 |
|---|---|---|
| `ditto.pics` | 프론트엔드 | CloudFront → S3 |
| `api.ditto.pics` | 백엔드 API | CloudFront → Fargate Public IP |
| `monitor.ditto.pics` | Grafana 대시보드 | CNAME → Grafana Cloud |

### ECS Task 구성

| 컨테이너 | 이미지 | 포트 | essential | 비고 |
|---|---|---|---|---|
| `ditto-api` | ECR `ditto-server` | 8080 | ✅ | Spring Boot |
| `ditto-db` | mysql:8.4 | 3306 | ✅ | MVP용, localhost 통신 |
| `alloy` | ECR `ditto-alloy` | - | ❌ | 메트릭 → Grafana Cloud |

### 네트워크

- VPC (10.0.0.0/16) + Public Subnet + Internet Gateway
- **퍼블릭 서브넷만 사용** (NAT Gateway 불필요)
- Fargate Task에 `assignPublicIp: ENABLED`
- Security Group: 8080 inbound는 CloudFront Managed Prefix List만 허용

---

## 보안

### API Key 인증

Spring Security + API Key 헤더 방식으로 경로별 접근 제어를 적용한다.

| 경로 | 인증 | 설명 |
|---|---|---|
| `/health` | 불필요 | CloudFront 헬스체크용 |
| `/actuator/**` | 불필요 (네트워크 보호) | Alloy sidecar가 localhost로 접근 |
| `/docs/**`, `/swagger-ui/**` | 불필요 | API 문서 (Swagger UI) |
| `/api/**` | `X-API-Key` 헤더 필수 | 비즈니스 API |
| 그 외 모든 경로 | 차단 (403) | 허용되지 않은 경로 |

- API Key는 AWS Secrets Manager(`ditto/api-key`)에 저장하고, ECS Task Definition에서 환경변수 `API_KEY`로 주입
- 로컬 개발 시 기본값: `local-dev-key` (`application.yml`의 `${API_KEY:local-dev-key}`)
- 프론트엔드는 모든 API 요청에 `X-API-Key` 헤더를 포함해야 함

### 네트워크 레벨 보호

| 레이어 | 보호 대상 | 방법 |
|---|---|---|
| Security Group | 8080 포트 | CloudFront Managed Prefix List만 inbound 허용 |
| CloudFront Behavior | `/actuator/*` | 백엔드 CloudFront에서 해당 경로 Behavior를 생성하지 않음 → 외부 접근 불가 |
| Spring Security | `/actuator/**` | 허용하되 네트워크 보호에 의존 (Alloy가 localhost로 접근) |

### CloudFront 백엔드 Behavior 설정

백엔드 CloudFront(`api.ditto.pics`)에서 `/actuator/*` 경로가 외부에 노출되지 않도록, Behavior를 다음과 같이 구성한다:

| 순서 | Path Pattern | Origin | Cache Policy | 비고 |
|---|---|---|---|---|
| 0 (Default) | `*` | Fargate | CachingDisabled | API 요청 전달 |

> `/actuator/*` Behavior를 별도로 만들지 않는다.
> Default Behavior(`*`)가 모든 요청을 Fargate로 전달하지만, Security Group이 CloudFront만 허용하므로 외부에서 직접 접근은 불가능하다.
> 추가 보호가 필요하면 CloudFront Functions로 `/actuator/*` 요청을 403으로 응답하도록 설정할 수 있다.

---

## 모니터링

```
Spring Boot (Actuator + Micrometer)
    │ /actuator/prometheus (15초 scrape)
    ▼
Grafana Alloy (sidecar, 환경변수로 시크릿 참조)
    │ remote_write (HTTPS)
    ▼
Grafana Cloud (무료티어: 10K series, 14일 보존, 3유저)
    │
    ▼
monitor.ditto.pics
```

- Prometheus 대신 **Grafana Alloy** 사용 (환경변수 참조 지원 → 퍼블릭 레포에 시크릿 없이 커밋 가능)
- 설정: `.aws/alloy/config.alloy`
- Alloy → Spring Boot Actuator는 같은 Task 내 **localhost** 통신 (인증 불필요)

---

## CI/CD

### CI (PR → main)
```
PR → Build & Test → SonarCloud 분석 → 테스트 리포트
```

### CD (push → main)
```
main push → API Docker Build → Alloy Docker Build → ECR Push
  → ECS force-new-deployment → 새 Task IP 추출 → CloudFront Origin 업데이트
```

---

## 월간 예상 비용 (서울 리전, MVP)

| 서비스 | 월 예상 비용 | 비고 |
|---|---|---|
| Fargate (0.5 vCPU, 1GB) | ~$15~20 | api + db + alloy 24/7 |
| CloudFront | ~$0~3 | 트래픽 적으면 거의 무료 |
| S3 | ~$0.1 | 정적 파일 |
| ECR | ~$1 | 이미지 2개 |
| CloudWatch Logs | ~$1~2 | 로그 양 따라 |
| Secrets Manager | ~$2.40 | 시크릿 6개 |
| Grafana Cloud | **$0** | 무료티어 |
| **합계** | **~$22~30** | |

---

## 세팅 체크리스트

### GitHub 레포 설정
- [ ] Branch protection: `main` → Require PR + 1 Approve + Status checks
- [ ] Merge 방식: **Squash and merge만 허용** (Settings → General → Pull Requests)
- [ ] Secrets 등록:

| Secret | 값 | 용도 |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | IAM Access Key | AWS 인증 |
| `AWS_SECRET_ACCESS_KEY` | IAM Secret Key | AWS 인증 |
| `CLOUDFRONT_DISTRIBUTION_ID` | CloudFront ID | CD에서 Origin 업데이트 |
| `SONAR_TOKEN` | SonarCloud 토큰 | 정적분석 |

### AWS - Secrets Manager
- [ ] `ditto/db-password` — MySQL 유저 비밀번호
- [ ] `ditto/db-root-password` — MySQL root 비밀번호
- [ ] `ditto/api-key` — API 인증 키 (프론트엔드 → 백엔드 요청 시 `X-API-Key` 헤더에 사용)
- [ ] `ditto/grafana-cloud-remote-write-url` — Grafana Cloud URL
- [ ] `ditto/grafana-cloud-username` — Grafana Cloud 숫자 ID
- [ ] `ditto/grafana-cloud-api-key` — Grafana Cloud API Key

### AWS - ACM (SSL 인증서)
- [ ] `*.ditto.pics` 와일드카드 인증서 요청 (**리전: us-east-1** 필수)
- [ ] DNS 검증

### DNS (호스팅케이알)
- [ ] `ditto.pics` → CNAME → CloudFront (프론트)
- [ ] `api.ditto.pics` → CNAME → CloudFront (백엔드)
- [ ] `monitor.ditto.pics` → CNAME → `<your-stack>.grafana.net`
