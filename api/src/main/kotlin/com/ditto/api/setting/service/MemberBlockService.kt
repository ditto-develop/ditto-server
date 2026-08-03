package com.ditto.api.setting.service

import com.ditto.api.setting.dto.BlockedMemberResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 간 차단. 화면(피그마 6.2.2)이 약속한 효력은 두 가지다 —
 * 상대가 내 프로필을 볼 수 없고, 매칭에서 서로 제외된다.
 * 집행 지점은 각각 `UserService.getPublicProfile`과 매칭 후보 생성이며, 판정은 [isBlockedBetween]을 쓴다.
 */
@Service
class MemberBlockService(
    private val memberBlockRepository: MemberBlockRepository,
    private val memberRepository: MemberRepository,
) {

    /**
     * 차단을 만든다. 이미 차단한 상대면 아무 것도 하지 않는다(멱등) —
     * 신고와 함께 오는 경로가 있어 재시도·중복 요청이 실패로 보이면 안 된다.
     */
    @Transactional
    fun block(blockerId: Long, blockedMemberId: Long) {
        if (blockerId == blockedMemberId) {
            throw WarnException(ErrorCode.BAD_REQUEST, "자기 자신을 차단할 수 없습니다.")
        }
        if (!memberRepository.existsById(blockedMemberId)) {
            throw WarnException(ErrorCode.NOT_FOUND)
        }
        if (memberBlockRepository.existsByBlockerIdAndBlockedMemberId(blockerId, blockedMemberId)) {
            return
        }
        memberBlockRepository.save(MemberBlock.create(blockerId, blockedMemberId))
    }

    @Transactional(readOnly = true)
    fun getMyBlocks(blockerId: Long): List<BlockedMemberResponse> {
        val blocks = memberBlockRepository.findAllByBlockerIdOrderByCreatedAtDesc(blockerId)
        if (blocks.isEmpty()) {
            return emptyList()
        }

        // 닉네임·캐리커쳐를 한 번에 읽어 N+1을 피한다.
        val membersById = memberRepository.findAllById(blocks.map { it.blockedMemberId })
            .associateBy { it.id }

        return blocks.mapNotNull { block ->
            val member = membersById[block.blockedMemberId] ?: return@mapNotNull null
            BlockedMemberResponse(
                id = member.id,
                nickname = member.nickname,
                profileImageUrl = member.caricature,
                blockedAt = block.createdAt,
            )
        }
    }

    /** 차단 해제. 차단하지 않은 상대를 해제해도 성공으로 둔다 — 목록에서 지우는 동작이 멱등해야 한다. */
    @Transactional
    fun unblock(blockerId: Long, blockedMemberId: Long) {
        val block = memberBlockRepository.findByBlockerIdAndBlockedMemberId(blockerId, blockedMemberId)
            ?: return
        memberBlockRepository.delete(block)
    }

    /** 두 회원 사이에 방향 무관하게 차단이 있는지 — 프로필 조회·매칭 제외의 공통 판정. */
    @Transactional(readOnly = true)
    fun isBlockedBetween(oneId: Long, otherId: Long): Boolean =
        memberBlockRepository.existsBetween(oneId, otherId)
}
