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
│           ├── ApiKeyAuthFilter.kt
│           ├── ApiKeyProperties.kt
│           ├── GlobalExceptionHandler.kt
│           ├── JacksonConfig.kt
│           ├── LoggingAspect.kt
│           ├── OpenApiConfig.kt
│           ├── RequestIdFilter.kt
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
Internet
   │
   ▼
┌──────────────────────────────────────────────────────┐
│  VPC (ditto-vpc, 10.0.0.0/16)                        │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Public Subnet A (10.0.1.0/24, ap-northeast-2a) │ │
│  │  Public Subnet C (10.0.2.0/24, ap-northeast-2c) │ │
│  │                                                 │ │
│  │  ┌───────────────────────────────┐              │ │
│  │  │  ALB (ditto-alb)              │              │ │
│  │  │  HTTPS 443 (ACM *.ditto.pics) │              │ │
│  │  │  HTTP 80 → 443 리다이렉트      │              │ │
│  │  └──────────────┬────────────────┘              │ │
│  │                 │ :8080                          │ │
│  │  ┌──────────────▼────────────────┐              │ │
│  │  │  ECS Fargate (ditto-api-service)│             │ │
│  │  │                               │              │ │
│  │  │  ┌─────────────────┐          │              │ │
│  │  │  │ ditto-api :8080  │          │              │ │
│  │  │  └─────────────────┘          │              │ │
│  │  │  ┌─────────────────┐          │              │ │
│  │  │  │ alloy           │── remote_write ──→ Grafana Cloud
│  │  │  │ (메트릭 수집)     │          │              │ │
│  │  │  └─────────────────┘          │              │ │
│  │  └───────────────┬───────────────┘              │ │
│  │                  │ :3306                         │ │
│  │  ┌───────────────▼───────────────┐              │ │
│  │  │  RDS MySQL (ditto-db)          │              │ │
│  │  │  db.t3.micro, MySQL 8.4       │              │ │
│  │  └───────────────────────────────┘              │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Private Subnet A (10.0.10.0/24)                │ │
│  │  Private Subnet C (10.0.20.0/24)                │ │
│  │  (현재 미사용, 향후 확장용)                        │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘

          api.ditto.pics → CNAME → ALB DNS name
          monitor.ditto.pics → CNAME → Grafana Cloud URL
```

### 도메인

| 도메인 | 용도 | 연결 대상 |
|---|---|---|
| `api.ditto.pics` | 백엔드 API | CNAME → ALB |
| `monitor.ditto.pics` | Grafana 대시보드 | CNAME → Grafana Cloud |

### ECS Task 구성

| 컨테이너 | 이미지 | 포트 | essential | 비고 |
|---|---|---|---|---|
| `ditto-api` | ECR `ditto-api` | 8080 | ✅ | Spring Boot |
| `alloy` | ECR `ditto-alloy` | - | ❌ | 메트릭 → Grafana Cloud |

### 네트워크

- VPC (10.0.0.0/16) + Public/Private Subnet + Internet Gateway
- ECS, ALB, RDS는 **퍼블릭 서브넷** 사용 (NAT Gateway 불필요)
- Fargate Task에 `assignPublicIp: ENABLED`
- 보안 그룹 체이닝: ALB(80/443) → ECS(8080) → RDS(3306)

### 보안 그룹

| 보안 그룹 | 인바운드 | 용도 |
|---|---|---|
| `ditto-alb-sg` | 80, 443 from 0.0.0.0/0 | ALB: 인터넷 트래픽 수신 |
| `ditto-ecs-sg` | 8080 from `ditto-alb-sg` | ECS: ALB에서만 접근 허용 |
| `ditto-rds-sg` | 3306 from `ditto-ecs-sg` + My IP | RDS: ECS + 개발자 접근 |

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
| Security Group | 8080 포트 | ALB 보안 그룹만 inbound 허용 |
| ALB | HTTPS | ACM 인증서 (`*.ditto.pics`) |
| Spring Security | `/actuator/**` | 허용하되 네트워크 보호에 의존 (Alloy가 localhost로 접근) |

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
main push → API Docker Build → Alloy Docker Build → Private ECR Push
  → ECS Task Definition 배포 → force-new-deployment
```

---

## 월간 예상 비용 (서울 리전)

| 서비스 | 월 예상 비용 | 비고 |
|---|---|---|
| ECS Fargate (0.5 vCPU, 1GB) | ~$18 | api + alloy 24/7 |
| ALB | ~$18 | 로드밸런서 |
| RDS (db.t3.micro) | $0 | 프리티어 (12개월 후 ~$15) |
| ECR (Private) | ~$1 | 이미지 2개 |
| Secrets Manager | ~$3.6 | 시크릿 9개 |
| CloudWatch Logs | ~$1 | 로그 양 따라 |
| Grafana Cloud | **$0** | 무료티어 |
| **합계** | **~$42/월** | 프리티어 종료 후 ~$57/월 |

---

## 스케일업 마이그레이션 경로

```
현재                              →  향후
ALB → ECS Fargate                →  + Auto Scaling
RDS (Public Subnet)              →  RDS (Private Subnet + Bastion)
비용: ~$42/월                    →  비용: ~$60+/월
```

---

## 세팅 체크리스트

### GitHub 레포 설정
- [ ] Branch protection: `main` → Require PR + 1 Approve + Status checks
- [ ] Merge 방식: **Squash and merge만 허용** (Settings → General → Pull Requests)
- [ ] Secrets 등록:

| Secret | 값 | 용도 |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | IAM Access Key (`github-actions-deployer`) | AWS 인증 |
| `AWS_SECRET_ACCESS_KEY` | IAM Secret Key | AWS 인증 |
| `SONAR_TOKEN` | SonarCloud 토큰 | 정적분석 |

### SonarCloud
- [ ] [sonarcloud.io](https://sonarcloud.io) 가입 → `ditto-develop` org
- [ ] `ditto-server` 프로젝트 추가 (Key: `ditto-develop_ditto-server`)
- [ ] Token 발급 → GitHub Secrets `SONAR_TOKEN`

### Grafana Cloud
- [ ] [grafana.com](https://grafana.com) 가입 (무료)
- [ ] Stack에서 Remote Write URL, Username, API Key 확인
- [ ] AWS Secrets Manager에 등록 (아래 참조)
- [ ] Spring Boot 대시보드 import (ID: 12900)

### AWS - VPC & 네트워킹
- [ ] VPC 생성 (`ditto-vpc`, 10.0.0.0/16)
- [ ] Public Subnet A (10.0.1.0/24, ap-northeast-2a)
- [ ] Public Subnet C (10.0.2.0/24, ap-northeast-2c)
- [ ] Private Subnet A (10.0.10.0/24, ap-northeast-2a)
- [ ] Private Subnet C (10.0.20.0/24, ap-northeast-2c)
- [ ] Internet Gateway 생성 + VPC 연결
- [ ] Public 라우팅 테이블: 0.0.0.0/0 → IGW
- [ ] Security Group 생성
  - `ditto-alb-sg`: 80, 443 from 0.0.0.0/0
  - `ditto-ecs-sg`: 8080 from `ditto-alb-sg`
  - `ditto-rds-sg`: 3306 from `ditto-ecs-sg` + My IP

### AWS - RDS
- [ ] DB Subnet Group 생성 (Public Subnet A, C)
- [ ] RDS 생성: MySQL 8.4, db.t3.micro, Public Access: Yes
- [ ] Initial database: `ditto`

### AWS - ALB
- [ ] Target Group: IP 타입, 포트 8080, Health check `/health`
- [ ] ALB 생성: Public Subnet A/C, `ditto-alb-sg`
- [ ] HTTPS 443 리스너: ACM 인증서 → Target Group
- [ ] HTTP 80 리스너: → HTTPS 리다이렉트

### AWS - ECR
- [ ] `ditto-api` 리포지토리 생성 (private)
- [ ] `ditto-alloy` 리포지토리 생성 (private)
- [ ] 수명 주기 정책 설정 (untagged 이미지 자동 삭제)

### AWS - ECS
- [ ] `ditto-cluster` 클러스터 생성 (Fargate)
- [ ] Task Definition 등록 (`.aws/task-definition.json`)
- [ ] `ditto-api-service` 서비스 생성
  - 퍼블릭 서브넷, `assignPublicIp: ENABLED`, ALB 연결, Task 수: 1

### AWS - IAM
- [ ] `ecsTaskExecutionRole` 생성
  - Trust: `ecs-tasks.amazonaws.com`
  - Policies: `AmazonECSTaskExecutionRolePolicy`, `SecretsManagerReadWrite`, `CloudWatchLogsFullAccess`
- [ ] `ecsTaskRole` 생성
  - Policies: `CloudWatchLogsFullAccess`
- [ ] `github-actions-deployer` IAM User (Access Key 발급)
  - Policies: `AmazonECS_FullAccess`, `AmazonEC2ContainerRegistryFullAccess`, `IAMReadOnlyAccess`

### AWS - Secrets Manager
- [ ] `ditto/api-key` — API 인증 키
- [ ] `ditto/db-url` — RDS 엔드포인트 (`jdbc:mysql://...`)
- [ ] `ditto/db-username` — DB 사용자
- [ ] `ditto/db-password` — DB 비밀번호
- [ ] `ditto/jwt-secret` — JWT 서명키
- [ ] `ditto/kakao-oauth-client-secret` — 카카오 OAuth
- [ ] `ditto/grafana-cloud-api-key` — Grafana Cloud API Key
- [ ] `ditto/grafana-cloud-remote-write-url` — Grafana Cloud URL
- [ ] `ditto/grafana-cloud-username` — Grafana Cloud 숫자 ID

### AWS - CloudWatch Logs
- [ ] `/ecs/ditto-api` 로그 그룹
- [ ] `/ecs/ditto-alloy` 로그 그룹

### AWS - ACM (SSL 인증서)
- [ ] `*.ditto.pics` 와일드카드 인증서 요청 (**리전: ap-northeast-2**)
- [ ] DNS 검증 (호스팅케이알에서 CNAME 추가)

### DNS (호스팅케이알)
- [ ] `api.ditto.pics` → CNAME → ALB DNS name
- [ ] `monitor.ditto.pics` → CNAME → Grafana Cloud URL

---

## 첫 배포 순서

1. 위 체크리스트의 AWS 리소스 전부 생성
2. `.aws/task-definition.json`에 실제 ARN 반영
3. GitHub Secrets 등록 (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
4. ECR에 첫 이미지 수동 push:
   ```bash
   aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com

   docker build --platform linux/arm64 -t <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-api:latest .
   docker push <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-api:latest

   docker build --platform linux/arm64 -t <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-alloy:latest .aws/alloy
   docker push <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-alloy:latest
   ```
5. ECS Task Definition 등록 + Service 생성 (ALB Target Group 연결)
6. Task RUNNING 확인
7. ACM 인증서 Issued 확인 → ALB HTTPS 리스너 추가
8. DNS 레코드 연결 (호스팅케이알: `api.ditto.pics` → ALB DNS name)
9. `https://api.ditto.pics/health` 접속 확인
10. 이후 main push 시 CD 파이프라인이 자동으로 전부 처리
