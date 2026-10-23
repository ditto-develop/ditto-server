package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import java.time.LocalDate

interface MatchCandidateRepositoryCustom {

    /**
     * 해당 퀴즈셋에서 후보를 **받은** 회원 ID 목록(중복 제거).
     *
     * 후보 행은 (owner → other) 두 방향으로 저장되므로 owner 쪽만 모으면 "이번 주 볼 후보가 생긴 사람"이
     * 된다. 알림 적재가 이 목록을 쓴다 — 참여자 전원이 아니라 후보가 실제로 생긴 사람에게만 알린다.
     */
    fun findOwnerMemberIdsByQuizSetId(quizSetId: Long): List<Long>

    /**
     * 해당 퀴즈셋에서 두 회원이 **서로의 후보로 노출됐는지** 여부.
     *
     * 후보 행은 (A→B), (B→A) 두 방향으로 저장되므로 한쪽만 봐도 충분하지만,
     * 한 방향 행이 유실된 데이터에서도 판정이 흔들리지 않게 양방향을 함께 본다.
     * 성사 전 소개노트 열람 권한이 이 판정을 쓴다.
     */
    fun existsPairByQuizSetId(oneMemberId: Long, otherMemberId: Long, quizSetId: Long): Boolean

    fun countCandidateOwnersInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<MatchCandidateCount>

    /** 그 주 활성 셋의 후보 쌍마다 점수 하나. 후보는 늘 두 방향으로 생겨서 id 가 작은 쪽 행만 센다. */
    fun findPairScoresInWeek(weekStartedOn: LocalDate): List<Double>

    /** 그 주 1:1 성사(ACCEPTED) 쌍마다 후보 점수. 후보를 다시 만들어 후보 행이 없는 쌍은 null 이다. */
    fun findAcceptedPairScoresInWeek(weekStartedOn: LocalDate): List<Double?>
}
