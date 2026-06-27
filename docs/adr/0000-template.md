# ADR 0000 — 제목 (결정 한 줄 요약)

- 상태: Proposed | Accepted | Superseded(→ 0000) (YYYY-MM-DD)
- 근거: code-evidence | commit-message | memory | discussion (출처를 구체적으로)

> 사후 기록일 때만 — 커밋·코드에서 재구성(YYYY-MM-DD). 결정 시점에 쓴 ADR이면 이 줄 삭제.

## Context

왜 결정이 필요했는가. 문제·제약·대안의 배경. "나중에 왜 이렇게 했지?"가 궁금할 사람이 읽을 부분이니, 당시 상황과 고려한 대안을 적는다.

## Decision

무엇을 정했는가. 능동·단정형으로. 핵심 선택과 근거를 함께. 다른 ADR과 이어지면 본문에서 [ADR 0000](0000-template.md)로 링크한다.

## Consequences

- 얻음: 이 결정으로 좋아진 점.
- 비용: 감수한 트레이드오프·새로 생긴 부담.
- 후속/연쇄: 이 결정이 부른 다음 변경이나 다른 ADR로의 영향(있으면).

## Links

- commits: 관련 커밋 해시(+ 한 줄 설명)
- memory: 결정 메모리 키(있으면)
- 핵심 파일: `module/.../File.kt`(무엇을 보면 되는지)

---

## 채번 규칙

- 번호는 **다음 빈 번호**(현재 최댓값 + 1). 파일명은 `NNNN-영문-슬러그.md`(제목 슬러그).
- 상태: 제안 중이면 `Proposed`, 채택되면 `Accepted`, 더 이상 유효하지 않으면 `Superseded(→ NNNN)`로 바꾸고 이 ADR은 삭제하지 말고 남긴다(이력 보존).
- 이미 내려져 코드로 굳은 결정을 뒤늦게 적으면 근거에 출처(code-evidence 등)를 명시하고 "사후 기록" 줄을 둔다.
