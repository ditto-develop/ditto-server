# ADR 0049 — 크론은 ShedLock(만료 시각 기록형 DB 락)으로 한 인스턴스에서만 돌린다

- 상태: Accepted (2026-11-04)
- 근거: discussion (#361)

## Context

API 서버는 태스크 1대로 돌지만 배포 때는 새 태스크를 먼저 띄우고 기존 태스크를 나중에 내린다(최소 100%·최대 200%). 그 사이 두 태스크가 함께 떠 있고, ALB 연결 드레이닝(300초) 동안 겹친다. 크론에는 중복 방지가 없어 이 구간에 걸린 작업이 두 번 돈다.

- 매분 도는 채팅방 생명주기 처리는 멱등이라 두 번 돌아도 결과가 같다.
- 주 1회 작업은 그렇지 않다. 월 00:00 퀴즈 오픈 푸시·월 09:00 평가 리마인드·수 18:00 마감 임박 푸시가 두 번 나가고, 목 00:01 매칭이 겹쳐 돈다.

대안:

- `SELECT … FOR UPDATE` 행 잠금: 작업 내내 트랜잭션을 열어 둬야 한다. 매칭은 퀴즈셋마다 트랜잭션을 나누는데([ADR 0027](0027-matching-batch-per-quiz-set-transaction.md)) 그 구조와 맞지 않는다.
- MySQL `GET_LOCK()`: 작업 내내 DB 연결 하나를 붙잡는다. 연결이 끊기면 실행 도중 락이 풀린다.
- 배포 시각 회피(운영 규칙): 사람이 지켜야 하고, 이후 스케일아웃 때 다시 문제가 된다.

## Decision

ShedLock(JDBC)을 쓴다. `shedlock` 테이블에 작업 이름마다 한 행씩 만료 시각을 적고, 조건부 UPDATE로 잡는다. 작업 동안 연결·트랜잭션을 붙잡지 않는다.

- 시각 비교는 DB 시각으로 한다(`usingDbTime`). 인스턴스 간 시계 차이를 타지 않는다.
- 모든 `@Scheduled` 메서드에 `@SchedulerLock`을 단다. 이름은 작업마다 다르게 한다(테스트로 검사).
- 보유 시간은 `SchedulerLockDurations` 한 곳에 묶는다.
- 최대 보유 시간(`lockAtMostFor`)은 실제 실행보다 넉넉하게: 매분 작업 5분, 주 1회 작업(푸시·매칭) 30분, 그 외(매시 지연 탈퇴·매일 정리) 10분.
- 최소 보유 시간(`lockAtLeastFor`)은 회차 간격보다 짧고, 인스턴스마다 크론이 발화하는 시각 차이(로컬 시계 기준)보다 길게: 매분 작업 10초, 나머지 30초. 작업이 빨리 끝나 락을 풀어도 다른 인스턴스가 같은 회차를 다시 잡지 못하고, QA가 크론을 매분으로 바꿔도 회차를 건너뛰지 않는다.
- 테이블은 JPA 엔티티로도 둔다. 로컬·테스트(H2)에 테이블이 생기고, 운영(`validate`)에서 마이그레이션을 빠뜨리면 기동이 실패한다. 테이블이 없을 때 ShedLock 은 락을 못 잡아 스케줄러를 조용히 건너뛰므로, 배포 롤백이 그보다 낫다.

## Consequences

- 얻음: 배포 중 겹치는 구간과 이후 태스크 2대 이상에서 크론이 한 번만 돈다.
- 비용: 락을 쥔 인스턴스가 실행 도중 비정상 종료되면 최대 보유 시간 동안 같은 작업이 막히고, 놓친 회차를 대신 돌려 주지 않는다. 정상 종료(배포)에서는 실행 중인 작업을 최대 20초 기다리므로(`spring.task.scheduling.shutdown.await-termination`, ECS `stopTimeout` 60초) 20초 안에 끝나는 작업은 끊기지 않는다.
- 비용: 테스트에서는 `@EnableSchedulerLock`을 켜지 않아(스케줄러 메서드를 직접 부르는 테스트가 최소 보유 시간에 막힌다) 프록시가 실제로 막는지는 자동 테스트가 보지 않는다. 테스트는 저장소 동작과 "모든 크론에 서로 다른 이름의 락"만 검사한다.
- 어드민 "정기 매칭 지금 돌리기"(`AdminMatchController`)는 스케줄러 메서드가 아니라 배치를 직접 불러 락을 거치지 않는다. 락이 남아 있어도 수동 실행은 되지만, 목 00:01 크론과 겹치는 것은 막지 않는다(기존과 같음).
- 후속/연쇄: 태스크를 2대 이상으로 늘리려면 STOMP 외부 브로커가 남은 조건이다.

## 운영

시각 컬럼은 **UTC**다(DB 시각). 앱·어드민은 KST라 그대로 보면 9시간 차이가 난다.

```sql
-- 현재 락 상태
SELECT name, locked_by,
       CONVERT_TZ(locked_at, '+00:00', '+09:00')  AS locked_at_kst,
       CONVERT_TZ(lock_until, '+00:00', '+09:00') AS lock_until_kst,
       lock_until > UTC_TIMESTAMP(3)             AS held
FROM shedlock
ORDER BY lock_until DESC;

-- 걸린 채 남은 락 강제 해제 (그 작업을 돌리는 인스턴스가 없음을 확인한 뒤)
UPDATE shedlock SET lock_until = UTC_TIMESTAMP(3) WHERE name = 'matching';
```

- 락에 막혀 건너뛴 회차는 `Not executing '<이름>'. It's locked.` 로그(DEBUG, `DefaultLockingTaskExecutor`)로 남는다.
- 마이그레이션을 빠뜨리고 배포하면 새 태스크가 `Schema-validation: missing table [shedlock]`으로 기동에 실패하고 배포가 롤백된다.

## Links

- 이슈: #361
- 핵심 파일: `api/.../config/SchedulerLockConfig.kt`(락 저장소), `api/.../config/SchedulerLockDurations.kt`(보유 시간), `api/.../config/SchedulingConfig.kt`(`@EnableSchedulerLock`, 종료 시 대기), `domain/.../system/entity/ShedLock.kt`, `domain/db/V20261104085543_shedlock 테이블 추가.sql`
- 같은 이슈의 종료 처리: `api/src/main/resources/application.yml`(종료 대기), `.aws/task-definition.json`(`stopTimeout`), `api/.../HealthCheckController.kt`(준비 상태 503)
