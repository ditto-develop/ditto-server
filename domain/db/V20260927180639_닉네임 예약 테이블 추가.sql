-- 닉네임 중복 확인(v2)을 통과한 회원이 10분 동안 그 닉네임을 붙잡아 둔다.
-- 가입 중인 두 사람이 같은 닉네임을 고르면, 먼저 확인한 쪽이 가입 버튼을 누를 때까지 다른 쪽은 확인 단계에서 막힌다.
-- 회원당 하나만 둔다(member_id 유니크) — 새 닉네임을 확인하면 이전 예약은 풀린다.
CREATE TABLE nickname_reservation
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    nickname   VARCHAR(50) NOT NULL COMMENT '예약한 닉네임 (member.nickname 과 같은 콜레이션)',
    member_id  BIGINT      NOT NULL COMMENT '예약한 회원 ID',
    expires_at DATETIME(6) NOT NULL COMMENT '예약 만료 시각',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY nickname_reservation_uk_1 (nickname),
    UNIQUE KEY nickname_reservation_uk_2 (member_id)
);
