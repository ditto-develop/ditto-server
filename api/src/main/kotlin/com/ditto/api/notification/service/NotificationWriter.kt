package com.ditto.api.notification.service

import com.ditto.api.notification.message.NotificationContent
import com.ditto.domain.notification.entity.DuplicatePolicy
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 한 건을 **자기 트랜잭션에서** 적재한다. 부르는 쪽은 [NotificationAppender] 하나다.
 *
 * `REQUIRES_NEW` 인 이유는 알림이 비즈니스 트랜잭션을 되돌리지 못하게 하는 것이다. 호출자 트랜잭션에
 * 참여하면 적재 실패가 그 트랜잭션을 rollback-only 로 표시해, 예외를 잡아도 커밋이 깨진다.
 *
 * **트랜잭션 안에서 부르는 곳은 `GroupMatchService.joinGroupMatch` 하나다.** 나머지 다섯 곳은 전이가
 * 커밋된 뒤에 부르므로 전파 설정이 결과를 바꾸지 않는다(트랜잭션이 없으면 `REQUIRED` 도 새로 연다).
 * 그 한 곳이 사용자가 결과를 기다리는 그룹 참여 경로라, "알림을 못 남겼다"는 이유로 실패해선 안 된다.
 *
 * 반대급부로 **적재 뒤에 호출자가 롤백하면 알림만 남는다**(적재가 먼저 커밋되므로). 알림은 읽기 전용
 * 통지라 그 방향의 오류를 감수한다 — 반대 방향(알림 때문에 매칭이 취소됨)이 훨씬 나쁘다.
 */
@Component
class NotificationWriter(
    private val notificationRepository: NotificationRepository,
) {

    /**
     * 중복 정책에 따라 적재한다.
     *
     * @return 실제로 생긴 행. 이미 알린 사건이라 건너뛰었으면 `null` — 푸시도 함께 건너뛴다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(memberId: Long, content: NotificationContent, targetId: Long?): Notification? {
        val type = content.type
        when (type.duplicatePolicy) {
            DuplicatePolicy.ALLOW -> Unit

            DuplicatePolicy.ONCE_PER_TARGET -> {
                val target = requireTargetId(type, targetId)
                if (notificationRepository.existsByMemberIdAndTypeAndTargetId(memberId, type, target)) {
                    return null
                }
            }

            // 안읽은 같은 대상의 알림을 걷어내고 새로 남긴다 — 목록에 한 줄만 보이게 한다.
            DuplicatePolicy.COLLAPSE_UNREAD ->
                notificationRepository.deleteUnread(memberId, type, requireTargetId(type, targetId))
        }

        return notificationRepository.save(
            Notification.create(
                memberId = memberId,
                type = type,
                title = content.title,
                body = content.body,
                targetId = targetId,
            ),
        )
    }

    private fun requireTargetId(type: NotificationType, targetId: Long?): Long =
        requireNotNull(targetId) { "중복 정책 ${type.duplicatePolicy} 는 targetId 가 필요합니다: type=$type" }
}
