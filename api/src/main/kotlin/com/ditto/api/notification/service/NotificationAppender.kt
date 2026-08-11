package com.ditto.api.notification.service

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.support.runCatchingExceptions
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 알림을 남기는 유일한 입구. 적재 지점 여섯 곳이 이것만 부른다.
 *
 * **실패를 삼킨다.** 알림을 못 남긴 것이 매칭·그룹 참여·채팅 전송·평가 열기를 되돌리는 것보다 낫다.
 * 트랜잭션을 여기서 열지 않는 것이 핵심이다 — [NotificationWriter]가 다른 빈이라 호출마다 자기
 * 트랜잭션(`REQUIRES_NEW`)을 얻으므로, 한 건이 실패해도 그 건만 롤백되고 예외는 이 층에서 멈춘다.
 * 같은 메서드 안에서 잡으면 호출자 트랜잭션이 이미 rollback-only 로 표시돼 있어 소용이 없다.
 *
 * 여러 수신자에게 남길 때도 사람마다 격리된다([appendAll]) — 한 사람의 적재 실패가 나머지를 막지 않는다.
 */
@Component
class NotificationAppender(
    private val notificationWriter: NotificationWriter,
) {

    /**
     * 한 사람에게 알림을 남긴다. 중복 처리는 유형의 정책이 정한다(`NotificationType.duplicatePolicy`).
     *
     * @return 실제로 행이 생겼으면 `true`. 이미 알린 사건이거나 적재가 실패했으면 `false`
     */
    fun append(memberId: Long, content: NotificationContent, targetId: Long? = null): Boolean =
        runCatchingExceptions { notificationWriter.write(memberId, content, targetId) }
            .onFailure {
                logger.warn(it) { "알림 적재 실패 — 무시한다: memberId=$memberId, type=${content.type}, targetId=$targetId" }
            }
            .getOrDefault(false)

    /**
     * 여러 사람에게 같은 내용을 남긴다. 문구가 사람마다 달라야 하면([memberId]별 닉네임 등)
     * 이걸 쓰지 말고 [append]를 사람마다 부른다.
     *
     * @return 실제로 행이 생긴 건수
     */
    fun appendAll(memberIds: Collection<Long>, content: NotificationContent, targetId: Long? = null): Int =
        memberIds.count { append(it, content, targetId) }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
