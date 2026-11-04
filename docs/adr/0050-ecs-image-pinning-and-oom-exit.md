# ADR 0050 — 태스크 정의는 커밋 이미지를 가리키고, OOM 이 나면 JVM 을 바로 끝낸다

- 상태: Accepted (2026-11-04)
- 근거: incident (#336, #321)

## Context

**이미지 태그.** CD 는 이미지를 커밋 SHA 와 `latest` 두 태그로 올리지만 `.aws/task-definition.json` 에는 `latest` 만 들어갔다. 배포가 실패해 ECS 가 이전 태스크 정의로 되돌려도 그 정의 역시 `latest` 를 가리킨다. 2026-10-22 #330 배포가 실패했을 때 실제로 이 상태였다 — 떠 있던 태스크는 이전 이미지였지만 `latest` 는 이미 실패한 이미지였고, 태스크가 다시 뜨는 순간(Spot 회수·OOM·헬스 체크 실패) 고장 난 이미지를 받을 수 있었다.

수동 배포(`cd-manual.yml`)에는 다른 문제도 있었다. `workflow_dispatch` 의 `github.sha` 는 입력한 브랜치가 아니라 기본 브랜치 HEAD 라, 다른 브랜치를 배포하면 그 이미지가 main 커밋 태그를 덮어썼다.

**OOM.** 2026-10-18 QA 더미 1만 5천 명으로 1:1 재생성을 돌리다 힙 OOM 이 났다. JVM 은 기본값으로 OOM 에도 끝나지 않는다. 요청 스레드의 OOM 은 그 요청만 실패시켰지만 같은 순간 Tomcat 연결 수락 스레드와 MySQL 연결 정리 스레드도 죽어, 프로세스는 살아 있는데 새 연결을 못 받는 태스크가 14분쯤 남았다(헬스 체크가 실패할 때까지).

**ADR 0014 와 실제 설정의 차이.** 0014 는 `MaxRAMPercentage=35` 를 적었지만 #117 이 미검증 튜닝값이라며 지웠다. 지금 힙 상한은 JVM 기본값(컨테이너 메모리 1024MB 의 25%, 약 256MB)이다.

## Decision

- CD 두 곳 모두 `aws-actions/amazon-ecs-render-task-definition` 으로 API·Alloy 이미지를 **이번 커밋 SHA 태그**로 바꿔 등록한다. 저장소의 `task-definition.json` 은 `latest` 를 그대로 두고 렌더 입력으로만 쓴다. `latest` 태그도 계속 올린다(수동 확인용).
- 수동 배포는 `git rev-parse HEAD`(실제로 체크아웃한 커밋)로 태그한다.
- `JAVA_TOOL_OPTIONS` 에 `-XX:+ExitOnOutOfMemoryError` 를 더한다. 첫 OOM 에서 JVM 이 끝나고 ECS 가 새 태스크를 띄운다.
- 지금 JVM 옵션은 `-XX:+UseG1GC -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError` 다. 힙 크기는 이 ADR 에서 정하지 않는다 — 측정 없이 정했다가 지운 전례(0014→#117)가 있다.

## Consequences

- 되돌린 태스크 정의는 그때의 이미지를 그대로 띄운다. 콘솔에서 이전 리비전으로 서비스를 갱신하는 것만으로 이미지까지 롤백된다.
- 롤백 가능한 범위는 ECR 수명 주기 정책(최근 아티팩트 90개 ≈ 빌드 30개)에 묶인다. 그보다 오래된 리비전은 이미지가 없어 뜨지 않는다.
- 태스크가 1대라 OOM 뒤 새 태스크가 뜨는 동안(기동 약 100초, #335) API 가 끊긴다. 이전에는 헬스 체크가 실패할 때까지 반쯤 고장 난 채로 끊겨 있었으므로 끊기는 시간은 줄어든다(추정 — OOM 이 다시 나면 교체 시간을 잰다).
- GC 과부하로 오래 버티는 상태(힙이 꽉 찬 채 GC 만 도는 상태)는 이 옵션으로 막히지 않는다. OOM 까지 가지 않기 때문이다.

## Links

- 이슈: #336, #321 · 관련: #330(실패 배포), #117(JVM 옵션 정리), #335(기동 시간)
- 핵심 파일: `.github/workflows/cd.yml`, `.github/workflows/cd-manual.yml`, `.aws/task-definition.json`
- 이전 결정: [ADR 0014](0014-ecs-metaspace-heap-rebalance.md) — 힙 비율 부분은 #117 로 이미 무효
