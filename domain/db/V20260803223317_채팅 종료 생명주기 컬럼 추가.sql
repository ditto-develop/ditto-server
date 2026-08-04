-- chat_room 에 종료 생명주기를 추가한다. 채팅은 금요일 00:00 개방 ~ 월요일 00:00 종료(72시간)이며,
-- 만료 스케줄러와 사용자 종료 두 경로가 하나의 종료 서비스로 모인다.
-- 평가는 채팅이 끝나야 열리므로 기한 없는 방은 허용하지 않는다.
--
-- 종료 행위자는 이 테이블에 두지 않는다. "누가 나갔는지"는 나갈 때 남기는 SYSTEM 메시지의
-- chat_message.sender_id 가 이미 들고 있고, 조회자는 그 값과 자기 ID 를 비교해
-- "상대방이 채팅을 종료했습니다"를 렌더링한다. 그룹의 멤버 이탈도 같은 메커니즘을 쓴다.
--
-- 적용 순서 주의: 여기서는 NOT NULL 로 승격하지 않는다.
-- 구버전 코드의 INSERT 는 이 컬럼들을 채우지 않으므로, 신버전 배포 전에 적용하면 방 생성이 실패한다.
-- opens_at·expires_at·status 의 NOT NULL 승격은 신버전 배포 후 다음 마이그레이션에서 수행한다.
-- (ended_at·end_reason 은 종료된 방만 값을 가지므로 영구히 NULL 허용)
--
-- DEFAULT 를 주는 이유: 마이그레이션 적용과 신버전 배포 사이(롤링 배포 구간)에 구버전이 만드는 방을 막을 수 없다.
-- 그 방이 NULL 을 갖게 두면 ① 신버전이 non-null 로 읽다 NPE 로 방 목록 조회 전체가 깨지고,
-- ② status IS NULL 은 `<> 'ENDED'` 와 `= 'SCHEDULED'` 어느 쪽에도 걸리지 않아(SQL 3값 논리)
-- 열리지도 끝나지도 않는 방으로 영원히 남는다. 주말 창에 정렬되지 않은 값이라도 NULL 보다 낫다.
-- NOT NULL 승격 시 이 DEFAULT 들은 함께 제거한다.
ALTER TABLE chat_room
    ADD COLUMN opens_at   DATETIME(6) NULL DEFAULT (CURRENT_TIMESTAMP(6)) COMMENT '채팅 개방 시각' AFTER source_id,
    ADD COLUMN expires_at DATETIME(6) NULL DEFAULT (CURRENT_TIMESTAMP(6) + INTERVAL 3 DAY) COMMENT '자동 종료 예정 시각 (연장 시 이동)' AFTER opens_at,
    ADD COLUMN status     VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT '방 상태 (SCHEDULED, ACTIVE, ENDED)' AFTER expires_at,
    ADD COLUMN ended_at   DATETIME(6) NULL COMMENT '실제 종료 시각 (종료 전 NULL)' AFTER status,
    ADD COLUMN end_reason VARCHAR(20) NULL COMMENT '종료 사유 (EXPIRED, USER_ENDED)' AFTER ended_at;

-- 기존 방 백필: 생성 시각이 속한 운영 주의 금 00:00 ~ 월 00:00 (WEEKDAY: 월=0 ~ 일=6)
UPDATE chat_room
SET opens_at   = DATE_ADD(DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY), INTERVAL 4 DAY),
    expires_at = DATE_ADD(DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY), INTERVAL 7 DAY)
WHERE opens_at IS NULL;

-- 기한이 이미 지난 방은 만료로 마감된 것으로 본다. 종료 시각·사유를 함께 채워 상태와 어긋나지 않게 한다.
-- NOW() 는 DB 세션 타임존, created_at 은 앱이 Asia/Seoul 로 쓴 값이라 세션이 UTC 면 경계 근처 방이
-- ACTIVE 로 남을 수 있다. 다음 sweep 이 곧바로 EXPIRED 로 정리하므로 그대로 둔다.
UPDATE chat_room
SET status     = 'ENDED',
    ended_at   = expires_at,
    end_reason = 'EXPIRED'
WHERE status IS NULL
  AND expires_at <= NOW();

-- 남은 방은 개방 시각이 아직 미래여도(주중 생성) ACTIVE 로 둔다 — 이미 대화 중인 방을
-- SCHEDULED 로 되돌리면 진행 중인 채팅이 잠기기 때문이다. 신규 방부터 SCHEDULED 가 적용된다.
UPDATE chat_room
SET status = 'ACTIVE'
WHERE status IS NULL;

-- 만료 스케줄러가 끝낼 방을 찾는 경로 (status = 'ACTIVE' AND expires_at <= now)
CREATE INDEX chat_room_index_1 ON chat_room (status, expires_at);
