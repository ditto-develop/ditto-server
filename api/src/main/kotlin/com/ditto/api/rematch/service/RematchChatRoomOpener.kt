package com.ditto.api.rematch.service

import com.ditto.api.chat.service.ChatService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 성사된 재매칭에 채팅방을 예약한다 — 재매칭 트랙과 채팅 트랙을 잇는 어댑터.
 *
 * **성사 트랜잭션은 방을 만들지 않는다.** 성사는 평가 제출 중에 확정되므로(`RematchSubmitter`), 방 생성을
 * 그 트랜잭션에 묶으면 방 생성 실패가 평가 제출을 되돌린다. 대신 "성사됐는데 방이 없다"를 정상 상태로
 * 두고 이 어댑터가 맞춘다. 방이 곧 처리 완료 기록이라 별도 표시나 이벤트 전달 장치가 필요 없다.
 *
 * 성사 직후 즉시 만드는 경로는 두지 않는다. 재매칭 방은 금요일에 열려 즉시 만들 이유가 없고,
 * 늦어도 스케줄러 한 주기 안에 예약된다.
 *
 * 성사된 쌍마다 방을 하나씩 만들며 **"같은 쌍의 방이 이미 있는지"는 보지 않는다**(ADR 0017).
 * 같은 두 사람이 서로 다른 주의 그룹에서 각각 성사되고 두 제출이 같은 주에 몰리면 방이 둘 열린다 —
 * 막지 않는 쪽을 택했고, 막으면 건너뛴 쌍에 처리 기록이 남지 않아 예약 조회에 영구 잔류한다.
 * 같은 `rematch` 하나에 방이 둘 생기는 것은 `chat_room_uk_1`이 막는다.
 */
@Component
class RematchChatRoomOpener(
    private val rematchRepository: RematchRepository,
    private val chatService: ChatService,
    private val memberRepository: MemberRepository,
) {
    /**
     * 방이 없는 성사분에 방을 예약한다. 스케줄러가 주기적으로 부른다.
     *
     * 쌍마다 격리한다 — `createRematchRoom`이 다른 빈이라 호출마다 자기 트랜잭션을 얻으므로 한 쌍이
     * 실패해도 그 쌍만 롤백된다. 배치 전체를 한 트랜잭션으로 묶으면 뒤쪽 한 건 때문에 앞서 성공한
     * 것까지 폐기되고, 조회가 그 쌍을 매 주기 다시 집어오므로 한 쌍이 예약을 영구히 막는다.
     *
     * @return 이번 호출로 새로 만들어진 방 수
     */
    fun openMissing(now: LocalDateTime): Int {
        val matched = rematchRepository.findMatchedWithoutChatRoom(RESERVE_BATCH_SIZE)
        if (matched.isEmpty()) {
            return 0
        }

        val opened = matched.count { rematch ->
            runCatchingExceptions { reserve(rematch, now) }
                .onFailure { logger.warn(it) { "재매칭 방 예약 실패 — 다음 주기로 넘긴다: rematchId=${rematch.id}" } }
                .getOrDefault(false)
        }
        logger.info { "재매칭 방 예약: 대상 ${matched.size}건 중 ${opened}건 생성" }
        return opened
    }

    /**
     * **성사 이후 처음 오는 금요일**에 열도록 예약한다(기획: "금요일 00:00 채팅방 오픈").
     *
     * 기획은 특정 주말을 약속하지 않으므로 진행 중인 주말에 합류시키지 않는다 — 주말 도중에 성사돼도
     * 남은 몇 시간에 급히 열지 않고 온전한 72시간을 받는다. 그 덕에 **이미 닫힌 구간이 계산될 수 없다.**
     * 합류시키면 일요일 늦은 밤 성사(성사를 확정하는 것이 평가 제출이다)에서 방이 몇 분짜리로 열리거나,
     * 예약이 도는 순간 이미 닫힌 창이 되어 `ACTIVE`로 태어난 뒤 곧바로 만료된다. 후자는 방이 존재하는
     * 탓에 예약 조회가 완료로 판정해 다시 고치지 않아 조용한 유실이 된다.
     *
     * 기준을 `max(성사 시각, 예약 시점)`으로 두는 이유는 예약이 밀린 경우다 — 성사 다음 금요일이 이미
     * 지났다면 그 금요일에는 열 수 없고, 열 수 있는 가장 이른 금요일이 답이다.
     *
     * @return 방을 예약했으면 `true`. 건너뛴 것을 성공으로 세지 않으려고 돌려준다.
     */
    private fun reserve(rematch: Rematch, now: LocalDateTime): Boolean {
        val matchedAt = rematch.matchedAt()
            ?: error("성사되지 않은 재매칭에는 방을 만들 수 없습니다: rematchId=${rematch.id}")

        // 탈퇴자가 섞인 쌍은 건너뛴다. 탈퇴는 미성사 쌍을 취소해 이 경로를 막지만
        // (`LeftMemberRematchCanceller`), 탈퇴 가드가 읽은 뒤 상대가 제출해 성사시키는 좁은 창이 남는다.
        // 방을 만들면 남은 한쪽이 아무도 없는 방에 들어가므로, 여기서 한 번 더 확인한다.
        if (hasLeftMember(rematch)) {
            logger.warn { "탈퇴자가 섞인 재매칭은 방을 만들지 않는다: rematchId=${rematch.id}" }
            return false
        }

        chatService.createRematchRoom(
            rematchId = rematch.id,
            memberIds = listOf(rematch.memberId1, rematch.memberId2),
            weekend = ChatPeriod.upcomingWeekendFrom(maxOf(matchedAt, now)),
        )
        return true
    }

    private fun hasLeftMember(rematch: Rematch): Boolean =
        memberRepository.countByIdInAndStatus(
            listOf(rematch.memberId1, rematch.memberId2),
            MemberStatus.LEFT,
        ) > 0

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 한 번의 예약이 떠안을 최대 쌍 수. 장애 후 밀린 물량이 한 호출을 오래 잡지 않게 끊는다. */
        private const val RESERVE_BATCH_SIZE = 100
    }
}
