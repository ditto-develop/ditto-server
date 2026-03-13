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
│       └── config/
│           ├── GlobalExceptionHandler.kt
│           ├── JacksonConfig.kt
│           ├── LoggingAspect.kt
│           └── RequestIdFilter.kt
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
          │  ditto.com     │            │  api.ditto.com │
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

          monitor.ditto.com → Route 53 CNAME → Grafana Cloud URL
```

### 도메인

| 도메인 | 용도 | 연결 대상 |
|---|---|---|
| `ditto.com` | 프론트엔드 | CloudFront → S3 |
| `api.ditto.com` | 백엔드 API | CloudFront → Fargate Public IP |
| `monitor.ditto.com` | Grafana 대시보드 | Route 53 CNAME → Grafana Cloud |

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
monitor.ditto.com
```

- Prometheus 대신 **Grafana Alloy** 사용 (환경변수 참조 지원 → 퍼블릭 레포에 시크릿 없이 커밋 가능)
- 설정: `.aws/alloy/config.alloy`

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
| Route 53 | ~$1.50 | 호스팅 존 |
| Secrets Manager | ~$2 | 시크릿 5개 |
| Grafana Cloud | **$0** | 무료티어 |
| **합계** | **~$22~30** | |

---

## 스케일업 마이그레이션 경로

```
현재 (MVP)                    →  Phase 1                →  Phase 2
CloudFront → Fargate 직접     →  CloudFront → ALB → ECS  →  + RDS
DB: ECS 컨테이너              →  DB: ECS 컨테이너        →  DB: RDS
비용: ~$25/월                 →  비용: ~$55/월           →  비용: ~$75+/월
```

> ⚠️ 현재 DB는 ECS 컨테이너로 운영 (개발/MVP용). Task 재시작 시 데이터 유실. 프로덕션 전환 시 RDS로 마이그레이션 필요.

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
- [ ] Public Subnet 생성 (`ditto-public-subnet`, 10.0.1.0/24, ap-northeast-2a)
- [ ] Internet Gateway 생성 + VPC 연결
- [ ] 라우팅 테이블: 0.0.0.0/0 → IGW
- [ ] Security Group 생성 (`ditto-ecs-sg`)
  - Inbound: 8080 TCP ← CloudFront Managed Prefix List
  - Outbound: All traffic → 0.0.0.0/0

### AWS - ECR
- [ ] `ditto-server` 리포지토리 생성 (private)
- [ ] `ditto-alloy` 리포지토리 생성 (private)

### AWS - ECS
- [ ] `ditto-cluster` 클러스터 생성 (Fargate)
- [ ] Task Definition 등록 (`.aws/task-definition.json`의 `<ACCOUNT_ID>` 치환)
- [ ] `ditto-api-service` 서비스 생성
  - 퍼블릭 서브넷, `assignPublicIp: ENABLED`, Task 수: 1

### AWS - IAM
- [ ] `ecsTaskExecutionRole` 생성
  - Trust: `ecs-tasks.amazonaws.com`
  - Policies: `AmazonECSTaskExecutionRolePolicy`, `SecretsManagerReadWrite`, `CloudWatchLogsFullAccess`
- [ ] `ecsTaskRole` 생성
- [ ] CI/CD용 IAM User (Access Key 발급)
  - Policies: `AmazonECS_FullAccess`, `AmazonEC2ContainerRegistryFullAccess`, `CloudFrontFullAccess`, `AmazonEC2ReadOnlyAccess`

### AWS - Secrets Manager
- [ ] `ditto/db-password` — MySQL 유저 비밀번호
- [ ] `ditto/db-root-password` — MySQL root 비밀번호
- [ ] `ditto/grafana-cloud-remote-write-url` — Grafana Cloud URL
- [ ] `ditto/grafana-cloud-username` — Grafana Cloud 숫자 ID
- [ ] `ditto/grafana-cloud-api-key` — Grafana Cloud API Key

### AWS - CloudWatch Logs
- [ ] `/ecs/ditto-api` 로그 그룹
- [ ] `/ecs/ditto-db` 로그 그룹
- [ ] `/ecs/ditto-alloy` 로그 그룹

### AWS - S3 + CloudFront (프론트엔드)
- [ ] `ditto-frontend` S3 버킷 (Block all public access: ON)
- [ ] CloudFront Distribution → S3 Origin (OAC 설정)
- [ ] Custom error response: 403/404 → `/index.html` (SPA 라우팅)

### AWS - CloudFront (백엔드 API)
- [ ] CloudFront Distribution 생성
  - Origin: Fargate Public IP, HTTP Only, port 8080
  - Cache Policy: `CachingDisabled`
  - Origin Request Policy: `AllViewer`
  - Alternate domain: `api.ditto.com`
  - SSL: ACM 인증서 연결

### AWS - ACM (SSL 인증서)
- [ ] `*.ditto.com` 와일드카드 인증서 요청 (**리전: us-east-1** 필수)
- [ ] DNS 검증 (Route 53 자동 검증)

### AWS - Route 53
- [ ] `ditto.com` 호스팅 존 생성
- [ ] `ditto.com` → A (Alias) → CloudFront (프론트)
- [ ] `api.ditto.com` → A (Alias) → CloudFront (백엔드)
- [ ] `monitor.ditto.com` → CNAME → `<your-stack>.grafana.net`

---

## 첫 배포 순서

1. 위 체크리스트의 AWS 리소스 전부 생성
2. `.aws/task-definition.json`에서 `<ACCOUNT_ID>` 치환
3. GitHub Secrets 등록
4. ECR에 첫 이미지 수동 push:
   ```bash
   aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com

   docker build -t <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-server:latest .
   docker push <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-server:latest

   docker build -t <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-alloy:latest .aws/alloy
   docker push <ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/ditto-alloy:latest
   ```
5. ECS Task Definition 등록 + Service 생성
6. Task RUNNING 후 Public IP 확인
7. CloudFront 백엔드 Distribution Origin을 해당 IP로 설정
8. Route 53 레코드 연결
9. `monitor.ditto.com` 접속 → Grafana Cloud 대시보드 확인
10. 이후 main push 시 CD 파이프라인이 자동으로 전부 처리
