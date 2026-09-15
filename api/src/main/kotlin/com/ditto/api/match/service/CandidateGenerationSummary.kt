package com.ditto.api.match.service

import com.ditto.api.match.matching.ScoredMatch
import com.ditto.domain.quiz.entity.MatchingType

/**
 * 한 퀴즈셋의 매칭 후보 생성 결과 요약. 어드민 화면·REST 응답·서버 로그에 그대로 쓰고 저장하지는 않는다.
 *
 * "성공했는데 후보가 0개"(참여자가 모자란 주)와 "건너뜀"을 구분하기 위해 존재한다 — 건너뜀은
 * 값이 아니라 [com.ditto.common.exception.WarnException]으로 표현하므로 이 타입은 항상 성공이다.
 *
 * @property participantCount 완료자 중 제외 정책을 통과해 실제 후보 풀에 들어간 인원
 * @property rowCounts 후보 테이블에서 지우고 새로 쓴 행 수(1:1은 `match_candidate`, 그룹은 `group_match`+`group_match_member`)
 * @property matches 저장된 매칭(1:1은 페어, 그룹은 방 하나)
 */
data class CandidateGenerationSummary(
    val quizSetId: Long,
    val matchingType: MatchingType,
    val participantCount: Int,
    val rowCounts: CandidateRowCounts,
    val matches: List<ScoredMatch>,
)

/** 후보 대체로 영향 받은 행 수. */
data class CandidateRowCounts(
    val deletedCount: Int,
    val savedCount: Int,
)
