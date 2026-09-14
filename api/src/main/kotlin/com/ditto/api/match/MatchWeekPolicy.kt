package com.ditto.api.match

import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Component

/**
 * 매칭이 다루는 퀴즈셋을 **이번 운영 주**로 제한한다.
 *
 * 후보 행(`match_candidate`·`group_match`)은 지난 주 것도 남기 때문에, 기준을 "가장 최근에 완주한 셋"으로
 * 두면 이번 주에 그 타입을 풀지 않은 회원에게 지난 사이클 후보가 계속 내려간다. 주차로 좁히는 판정이
 * 후보 조회·성사 전 열람 권한·응답 경로 세 곳에 필요해 여기 한 곳에 모은다 — 어긋나면
 * "후보 카드는 보이는데 소개노트는 403" 같은 불일치가 생긴다.
 *
 * 후보 생성(목 05:00)도 채팅 개방(금 00:00)도 퀴즈 마감과 같은 운영 주(월~일) 안이라,
 * "이번 주 퀴즈셋"으로 좁혀도 매칭·채팅 기간이 잘리지 않는다. 월요일 00:00 에 지난 주 후보가 닫힌다.
 */
@Component
class MatchWeekPolicy(
    private val quizSetRepository: QuizSetRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {

    /** 이번 운영 주에 회원이 완주한 [matchingType] 퀴즈셋. 참여하지 않았으면 null. */
    fun findCompletedQuizSet(memberId: Long, matchingType: MatchingType): QuizSet? =
        quizSetRepository.findCompletedQuizSetInWeek(memberId, matchingType, currentWeek().startedOn)

    /**
     * 응답(수락·거절·요청) 대상이 이번 주 퀴즈셋인지 확인한다.
     *
     * 화면에서 지난 주 후보를 감추는 것만으로는 부족하다 — 다른 탭·기기나 직접 호출로 지난 사이클
     * 그룹이 오늘 성사돼 채팅방이 열릴 수 있다. 쓰기 경로에서 막는다.
     */
    fun validateCurrentWeek(quizSetId: Long) {
        val quizSet = quizSetRepository.findById(quizSetId)
            .orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

        if (quizSet.weekStartedOn != currentWeek().startedOn) {
            throw WarnException(ErrorCode.NOT_MATCHING_PERIOD)
        }
    }

    fun currentWeek(): OperationWeek = OperationWeek.containing(serverTimeProvider.now().toLocalDate())
}
