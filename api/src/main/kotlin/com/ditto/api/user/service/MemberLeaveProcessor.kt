package com.ditto.api.user.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.entity.Member
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 앱 탈퇴와 애플 알림 탈퇴가 함께 쓰는 탈퇴 단계.
 * 진행 중인 매칭·채팅 검사는 부르는 쪽이 먼저 한다. 걸렸을 때 대응이 서로 달라서다.
 */
@Component
class MemberLeaveProcessor(
    private val leftMemberRematchCanceller: LeftMemberRematchCanceller,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @Transactional
    fun leave(member: Member, reason: String?, reasonDetail: String? = null) {
        member.leave(reason = reason, reasonDetail = reasonDetail, now = serverTimeProvider.now())

        leftMemberRematchCanceller.cancelWaitingPairs(member.id)

        // 세션은 즉시 끊는다. SocialAccount는 복구·재가입 식별 근거이므로 남긴다.
        refreshTokenRepository.deleteAllByMemberId(member.id)
        // 푸시 토큰도 지금 지운다. 복구하면 앱이 다시 등록하므로 지워도 안전하다.
        memberDeviceRepository.deleteAllByMemberId(member.id)
    }
}
