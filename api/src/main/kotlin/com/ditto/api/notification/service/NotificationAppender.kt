package com.ditto.api.notification.service

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.notification.push.PushNotifier
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.notification.entity.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 알림을 남기는 유일한 입구. 적재 지점 여섯 곳이 이것만 부르고, 행이 생긴 알림만 푸시가 나간다 —
 * 중복 정책으로 걸러진 사건은 푸시도 없다.
 *
 * 실패는 삼킨다. 알림을 못 남긴 것이 매칭·그룹 참여·채팅 전송을 되돌리는 것보다 낫다.
 * 트랜잭션을 여기서 열지 않는 것이 핵심이다 — [NotificationWriter]가 별도 빈이라 호출마다
 * `REQUIRES_NEW` 트랜잭션을 얻으므로 실패해도 그 건만 롤백된다. 같은 메서드 안에서 잡으면
 * 호출자 트랜잭션이 이미 rollback-only 라 소용이 없다.
 */
@Component
class NotificationAppender(
    private val notificationWriter: NotificationWriter,
    private val pushNotifier: PushNotifier,
) {

    /**
     * 한 사람에게 알림을 남기고 푸시를 보낸다. 중복 처리는 `NotificationType.duplicatePolicy`가 정한다.
     *
     * @return 실제로 행이 생겼으면 `true`
     */
    fun append(memberId: Long, content: NotificationContent, targetId: Long? = null): Boolean {
        val notification = writeQuietly(memberId, content, targetId) ?: return false
        pushNotifier.pushAll(listOf(notification))
        return true
    }

    /**
     * 여러 사람에게 같은 내용을 남긴다. 문구가 사람마다 달라야 하면 [append]를 사람마다 부른다.
     * 사람마다 격리된다 — 한 사람의 적재 실패가 나머지를 막지 않는다.
     * 푸시는 모아서 한 번에 넘긴다(방 조회 같은 공통 준비를 [PushNotifier]가 한 번만 하게).
     *
     * @return 실제로 행이 생긴 건수
     */
    fun appendAll(memberIds: Collection<Long>, content: NotificationContent, targetId: Long? = null): Int {
        val appended = memberIds.mapNotNull { writeQuietly(it, content, targetId) }
        pushNotifier.pushAll(appended)
        return appended.size
    }

    private fun writeQuietly(memberId: Long, content: NotificationContent, targetId: Long?): Notification? =
        runCatchingExceptions { notificationWriter.write(memberId, content, targetId) }
            .onFailure {
                logger.warn(it) { "알림 적재 실패 — 무시한다: memberId=$memberId, type=${content.type}, targetId=$targetId" }
            }
            .getOrNull()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
