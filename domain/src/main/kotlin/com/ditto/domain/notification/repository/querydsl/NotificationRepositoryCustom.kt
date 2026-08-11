package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import java.time.LocalDateTime

interface NotificationRepositoryCustom {

    /**
     * 내 알림을 최신순(id DESC)으로 [size] 개 조회한다.
     *
     * [cursor]가 주어지면 그 id 미만(더 과거)만 — 아래로 스크롤 페이징용. [category]가 주어지면
     * 그 카테고리의 유형만(생략 = 전체 칩). [from] 이전에 생성된 알림은 보이지 않는다(보관 30일).
     *
     * 새 메시지 알림은 접힐 때 행을 갱신하지 않고 다시 삽입하므로, `id` 정렬이 곧 시간 정렬이다.
     */
    fun findByMemberIdWithCursor(
        memberId: Long,
        category: NotificationCategory?,
        cursor: Long?,
        size: Int,
        from: LocalDateTime,
    ): List<Notification>

    /**
     * 안읽은 알림을 모두 읽음으로 표시한다 — 화면 우상단 "모두 읽음".
     *
     * 벌크 UPDATE 로 처리한다. 회원의 안읽은 알림을 전부 로드해 하나씩 바꾸면 30일치를 메모리에
     * 올리게 되고, 목적이 "read_at 하나를 채우는 것"이라 엔티티를 거칠 이유가 없다.
     * 호출자는 이후 같은 트랜잭션에서 알림 엔티티를 읽지 않아야 한다(영속성 컨텍스트와 어긋난다).
     *
     * @return 이번 호출로 읽음이 된 건수
     */
    fun markAllRead(memberId: Long, at: LocalDateTime): Long

    /**
     * 같은 `(회원, 유형, 대상)`의 **안읽은** 알림을 지운다 — 새 메시지 알림 접기용.
     *
     * 갱신이 아니라 삭제+재삽입인 이유는 정렬이다. 기존 행의 문구만 갱신하면 오래된 `id`에 최신
     * 시각이 붙어 `id` 정렬(= 시간 정렬)이 깨지고, 커서 페이징이 두 키를 다뤄야 한다.
     *
     * @return 지운 건수
     */
    fun deleteUnread(memberId: Long, type: NotificationType, targetId: Long): Long

    /**
     * 회원의 알림을 모두 지운다 — 탈퇴 완전 삭제용.
     *
     * 본문에 닉네임·메시지 미리보기가 들어 있어 회원과 함께 지운다. 파생 삭제 쿼리(`deleteAllByMemberId`)를
     * 쓰지 않는 이유는 그쪽이 행을 로드해 하나씩 remove 하기 때문이다 — 단일 DELETE 로 끝낸다.
     *
     * @return 지운 건수
     */
    fun deleteAllByMemberId(memberId: Long): Long

    /**
     * [threshold] 이전에 생성된 알림을 지운다 — 보관 기간 경과분 정리.
     *
     * 조회가 30일로 자르므로 지워지는 행은 이미 아무에게도 보이지 않는다.
     *
     * @return 지운 건수
     */
    fun deleteCreatedBefore(threshold: LocalDateTime): Long
}
