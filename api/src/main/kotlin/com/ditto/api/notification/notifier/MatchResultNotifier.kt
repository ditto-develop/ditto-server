package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.match.repository.MatchCandidateRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 주간 매칭 후보가 생긴 회원에게 알린다 — 매칭 배치와 알림을 잇는 어댑터.
 *
 * **배치 트랜잭션 밖에서 부른다.** 적재는 자기 트랜잭션에서 커밋되므로(`NotificationWriter`), 배치
 * 트랜잭션 안에서 부르면 배치가 롤백돼도 알림만 남는다. 그래서 스케줄러·어드민이 배치를 마친 뒤 부른다.
 *
 * 대상은 참여자 전원이 아니라 **후보가 실제로 생긴 회원**이다. 인원이 모자라 후보가 안 만들어진 주에
 * "결과가 나왔어요"를 보내면 빈 화면으로 보낸다.
 *
 * 알림은 퀴즈셋당 한 번이다(`MATCH_RESULT`의 `target_id` = 퀴즈셋 ID). 어드민이 같은 주의 후보를
 * 여러 번 재생성해도 알림은 하나다 — 다시 알릴 이유가 없고, 재생성은 대개 운영상의 수정이다.
 */
@Component
class MatchResultNotifier(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 후보가 생성된 퀴즈셋들에 대해 알린다.
     *
     * @return 실제로 남긴 알림 수
     */
    fun notifyFor(quizSetIds: Collection<Long>): Int {
        if (quizSetIds.isEmpty()) {
            return 0
        }

        val appended = quizSetIds.sumOf { quizSetId ->
            val memberIds = matchCandidateRepository.findOwnerMemberIdsByQuizSetId(quizSetId)
            notificationAppender.appendAll(
                memberIds = memberIds,
                content = NotificationMessages.matchResult(),
                targetId = quizSetId,
            )
        }

        if (appended > 0) {
            logger.info { "매칭 결과 알림: ${appended}건 (퀴즈셋 ${quizSetIds.size}개)" }
        }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
