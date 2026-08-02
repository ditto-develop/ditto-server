# ADR 0014 — ECS 태스크 메모리 예산 재배분: Metaspace 128m→256m, 힙 비율 45%→35%

- 상태: Accepted (2026-08-02)
- 근거: CloudWatch Logs 분석(`/ecs/ditto-api`, 로그 스트림 `84f9181f36a24c5490c60450a4b1c9b6`), code-evidence(PR #105)

## Context

[PR #105](https://github.com/ditto-develop/ditto-server/pull/105)(커밋 `492d9eb`, 2026-07-26 병합)가 실시간 채팅(STOMP) 기능과 함께 `.aws/task-definition.json`에 `JAVA_TOOL_OPTIONS`를 처음 추가했다:

```
-XX:+UseG1GC -XX:MaxRAMPercentage=45.0 -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=96m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp
```

느린 WebSocket 구독자로 인한 다이렉트 메모리 무한 증가를 막으려는 백프레셔 하드닝의 일부였다. 이 값들은 측정 없이 정해졌고, Fargate task 메모리(1024MB) 안에서 힙 45% + Metaspace 128m + Direct 96m = 최대 684MB로 예산을 나눴다.

같은 커밋에 `ChatRoom.roomType → sourceType` 컬럼명 변경도 포함됐는데(별도로 원복 처리, [ADR 미작성 — 오기재 정정으로 처리]), 이 스키마 불일치 때문에 배포된 서버가 부팅 시 `EntityManagerFactory` 초기화에 매번 실패해 7/26~7/29 동안 크래시 루프 상태였다. 이 기간 동안 `-XX:MaxMetaspaceSize=128m` 캡은 한 번도 "정상 가동 + 트래픽" 조합으로 시험대에 오르지 못했다.

2026-08-02, `source_type` 버그를 고쳐 서버가 처음으로 안정적으로 기동했다. 부팅(14:29) 후 37분간 무요청 상태였다가, 15:06:42에 첫 요청이 `/docs/openapi.yaml`(Swagger 문서)을 호출했다. springdoc-openapi는 스펙을 부팅 시 미리 만들지 않고 최초 호출 시점에 지연 생성하는데, 이 생성 과정이 컨트롤러 28개 + 그 안의 모든 DTO(제네릭 `ApiResponse<T>`, Kotlin data class 다수)를 리플렉션으로 한 번에 훑는다. `Init duration for springdoc-openapi is: 8890 ms`로 이례적으로 오래 걸렸고, 45초 뒤(15:07:27) `OutOfMemoryError: Metaspace`가 발생해 이후 거의 모든 요청 스레드가 연쇄로 실패했다(ECS가 15:12경 태스크를 자동 교체해 복구).

즉 Metaspace 캡은 7/26부터 잠재해 있던 문제였고, 앞선 스키마 버그가 이를 가려왔을 뿐이다. 두 버그는 같은 커밋에서 함께 들어왔지만 서로 독립적인 원인이다.

## Decision

Fargate task 메모리(1024MB, cpu 512)는 그대로 두고, 그 안에서 예산만 재배분한다.

- `-XX:MaxMetaspaceSize`: 128m → **256m** — springdoc-openapi의 최초 1회 전수 리플렉션 스캔(추정 필요량이 128m를 초과) 여유 확보.
- `-XX:MaxRAMPercentage`: 45.0 → **35.0** — 힙 상한을 460MB→358MB로 줄여, Metaspace 증가분(+128MB)만큼 이론상 최대 합계(684MB→710MB)가 크게 늘지 않도록 상쇄. `MaxRAMPercentage`는 컨테이너 전체 메모리 대비 비율만 계산할 뿐 Metaspace 사용량을 인지하지 않으므로(두 설정은 서로 독립적인 예산), 이 상쇄는 JVM이 자동으로 해주는 게 아니라 수동으로 맞춘 것이다.
- `-XX:MaxDirectMemorySize=96m`, GC(G1), 힙 덤프 옵션은 변경하지 않는다 — WebSocket 백프레셔 목적과 무관한 축이다.

Task 메모리 자체를 올리는 대안(1024→1536MB)도 검토했으나, 지금까지 로그에 heap OOM이나 컨테이너 레벨 OOM은 없었고 순수 Metaspace 문제였으므로 추가 비용 없이 기존 예산 재배분으로 해결한다.

## Consequences

- 얻음: springdoc-openapi 최초 스캔(및 유사한 일회성 리플렉션 스파이크)을 128MB보다 넉넉한 256MB 여유로 흡수. 재배포 후 첫 `/docs` 열람에도 서버가 죽지 않는다.
- 비용: 힙 상한이 22% 줄었다(460MB→358MB). Hibernate 1차 캐시·요청/응답 버퍼·커서 페이징 결과셋이 담기는 공간이 줄어, 트래픽이 늘거나 대용량 응답이 잦아지면 `OutOfMemoryError: Java heap space` 위험이 그만큼 커진다. GC(특히 Young/Full)도 더 잦아질 수 있다.
- 이론상 최대 합계(힙+Metaspace+Direct)가 684MB→710MB로 26MB 늘어, Alloy 사이드카 등 나머지가 나눠 쓸 여유가 미세하게 줄었다 — task 메모리 자체가 늘지 않았기 때문이다.
- 후속: 실제 트래픽이 늘어 힙 쪽 압박이 관측되면(GC 로그·heap OOM), task 메모리 증설(1024→1536)을 재검토한다. Metaspace 사용량도 정기적으로 확인해 256m가 여전히 충분한지 재점검한다.

## Links

- commits: `492d9eb`(PR #105, JVM 하드닝 최초 도입), 이 ADR과 함께 커밋되는 `.aws/task-definition.json` 변경(128m/45% → 256m/35%)
- 핵심 파일: `.aws/task-definition.json`(`JAVA_TOOL_OPTIONS`)
- CloudWatch: 로그 그룹 `/ecs/ditto-api`, 스트림 `84f9181f36a24c5490c60450a4b1c9b6`(2026-08-02 장애 원본 로그)
