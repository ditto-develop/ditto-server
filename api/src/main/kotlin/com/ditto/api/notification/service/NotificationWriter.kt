package com.ditto.api.notification.service

import com.ditto.api.notification.message.NotificationContent
import com.ditto.domain.notification.entity.DuplicatePolicy
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.repository.NotificationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 한 건을 **자기 트랜잭션에서** 적재한다. 부르는 쪽은 [NotificationAppender] 하나다.
 *
 * `REQUIRES_NEW` 인 이유는 알림이 비즈니스 트랜잭션을 되돌리지 못하게 하는 것이다. 적재 지점 여섯 곳
 * 중 넷이 매칭·그룹 참여·채팅 전송처럼 사용자가 결과를 기다리는 경로이고, 그중 어느 것도 "알림을 못
 * 남겼다"는 이유로 실패해서는 안 된다. 호출자 트랜잭션에 참여하면 적재 실패가 그 트랜잭션을
 * rollback-only 로 표시해, 예외를 잡아도 커밋이 깨진다.
 *
 * 반대급부로 **롤백된 작업의 알림이 드물게 남을 수 있다**(적재는 이미 커밋됐으므로). 알림은 읽기 전용
 * 통지라 그 방향의 오류를 감수한다 — 반대 방향(알림 때문에 매칭이 취소됨)이 훨씬 나쁘다.
 */
@Component
class NotificationWriter(
    private val notificationRepository: NotificationRepository,
) {

    /**
     * 중복 정책에 따라 적재한다.
     *
     * @return 실제로 행이 생겼으면 `true`. 이미 알린 사건이라 건너뛰었으면 `false`
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(memberId: Long, content: NotificationContent, targetId: Long?): Boolean {
        val type = content.type
        if (type.duplicatePolicy.requiresTarget) {
            requireNotNull(targetId) { "중복 정책 ${type.duplicatePolicy} 는 targetId 가 필요합니다: type=$type" }

            when (type.duplicatePolicy) {
                DuplicatePolicy.ONCE_PER_TARGET ->
                    if (notificationRepository.existsByMemberIdAndTypeAndTargetId(memberId, type, targetId)) {
                        return false
                    }
                // 안읽은 같은 대상의 알림을 걷어내고 새로 남긴다 — 목록에 한 줄만 보이게 한다.
                DuplicatePolicy.COLLAPSE_UNREAD ->
                    notificationRepository.deleteUnread(memberId, type, targetId)

                DuplicatePolicy.ALLOW -> Unit
            }
        }

        notificationRepository.save(
            Notification.create(
                memberId = memberId,
                type = type,
                title = content.title,
                body = content.body,
                targetId = targetId,
            ),
        )
        return true
    }
}
