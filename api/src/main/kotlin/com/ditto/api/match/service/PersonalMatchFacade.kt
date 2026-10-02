package com.ditto.api.match.service

import com.ditto.api.match.dto.PersonalMatchResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Component

/**
 * 1:1 매칭 흐름 중 트랜잭션을 나눠야 하는 것을 모은다. [PersonalMatchService]의 트랜잭션을 프록시로 부른다.
 *
 * 이 클래스에 `@Transactional`을 붙이지 않는다. 붙이면 여기서 한 조회가 서비스 트랜잭션과 합쳐진다.
 */
@Component
class PersonalMatchFacade(
    private val personalMatchRepository: PersonalMatchRepository,
    private val personalMatchService: PersonalMatchService,
) {

    /**
     * 잠글 두 회원 ID를 먼저 읽고, 수락 트랜잭션은 회원 잠금부터 시작한다(ADR 0035).
     * 같은 트랜잭션에서 읽으면 REPEATABLE READ 스냅샷이 잠금을 기다리기 전에 고정된다.
     *
     * ID는 엔티티가 아니라 값으로 읽는다. 요청 동안 영속성 컨텍스트가 유지되므로(OSIV)
     * 엔티티로 읽으면 수락 트랜잭션이 그 낡은 인스턴스를 다시 쓴다.
     */
    fun acceptMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val pairMemberIds = personalMatchRepository.findPairMemberIdsById(matchId)
            ?: throw WarnException(ErrorCode.NOT_FOUND)
        return personalMatchService.acceptMatch(memberId, matchId, pairMemberIds)
    }
}
