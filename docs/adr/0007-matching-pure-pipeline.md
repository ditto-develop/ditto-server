# ADR 0007 — 1:1 매칭을 순수 컴포넌트 파이프라인으로 분해 (대칭 하드필터 + 동점 무작위)

- 상태: Accepted
- 근거: commit-message + code-evidence (HardLimitApplier.kt·OneToOneMatchingProcessor.kt KDoc)

> 사후 기록 — 커밋·코드에서 재구성(2026-06-27).

## Context

1:1 매칭 로직(점수화·상위 비율 선발·1인 노출 제한·자격 필터)을 단일 거대 함수에 두면 규칙이 얽혀 단위 검증과 그룹 매칭 확장이 어렵다. 또 1인 노출 제한(hard limit)에서 동점 페어를 memberId 같은 결정적 키로 자르면 특정 회원이 체계적으로 유리해지는 공정성 문제가 있고, 성별·나이 자격을 점수화 후에 거르면 양방향 노출 원칙이 깨질 수 있다.

## Decision

`MatchingProcessor` 인터페이스로 매칭 타입(1:1/그룹) 구현을 분리하고, 1:1을 순수 컴포넌트 파이프라인으로 조립한다: `MatchScoreCalculator`(답변 일치율 점수화) → `TopRatioSelector`(상위 20%+동점 포함) → `HardLimitApplier`(1인 5명, 양방향 생존).

성별 상호 선호 + 나이차(≤10) 하드 필터는 점수화 전 후보 풀 구성 단계(`scoreAllDuos`의 `isValidPair`)에서 적용한다 — 두 조건 모두 대칭이라 살아남는 페어가 항상 대칭이고 양방향 hard-limit 원칙이 보존된다.

`HardLimitApplier`는 회원별 후보를 shuffle 후 점수 desc로 stable 정렬해 상위 N을 유지한다(점수가 다르면 결정적, 동점만 무작위; comparator 내부 random은 비교 일관성을 깨므로 shuffle로 분리).

## Consequences

- 얻음: 각 단계가 순수 함수라 독립 단위 검증 가능, 그룹 매칭을 별도 Processor로 확장 가능, 동점 공정성 확보, 자격 미달 페어가 아예 후보가 되지 않음.
- 비용: 재계산이 동점 구간에서 비결정적이라 결정성 회귀 테스트로 고정해야 함, 성별·나이 미상 회원은 후보에서 제외됨.

## Links

- commits: 1e4ea6c(파이프라인 분해·동점 무작위), 963747c(성별·나이 대칭 하드필터)
- `api/.../match/matching/{MatchingProcessor,OneToOneMatchingProcessor,TopRatioSelector,HardLimitApplier,MatchScoreCalculator}.kt`(KDoc에 무작위·대칭·양방향 근거), `domain/.../member/entity/GenderPreference.kt`
