package com.ditto.api.match.dto

import com.ditto.domain.match.entity.InvitationStatus

/**
 * 그룹 매칭 후보 목록 응답.
 * 회원이 최근 완료한 그룹 퀴즈셋에서 배정받은 후보 그룹을 그룹 점수 내림차순으로 담는다.
 * 이미 거절한 그룹은 담지 않는다.
 */
data class GroupCandidateResponse(
    val quizSetId: Long,
    val groups: List<CandidateGroup>,
)

/**
 * 후보 그룹 하나.
 *
 * 화면에 보이는 "12개중 평균 8개 일치"는 그룹 전체 페어 평균이 아니라 **나와 각 구성원의 일치 수 평균**이다.
 * 그룹을 뽑을 때 쓰는 점수(`group_match.score`, 모든 페어 평균)와 다른 값이라 조회 시점에 따로 계산한다.
 */
data class CandidateGroup(
    val groupMatchId: Long,
    /** 이 그룹에 대한 내 응답 상태. 거절한 그룹은 목록에 없으므로 PENDING 또는 ACCEPTED */
    val myStatus: InvitationStatus,
    /** 성사 여부(수락 인원이 최소 인원에 도달). 성사되면 금요일에 채팅방이 열린다. */
    val isFormed: Boolean,
    /** 나와 각 구성원의 일치 문항 수 평균(반올림) */
    val averageMatchedQuestions: Int,
    val totalQuestions: Int,
    /** 나를 제외한 구성원. 나와의 일치 문항 수 내림차순 */
    val members: List<Candidate>,
)
