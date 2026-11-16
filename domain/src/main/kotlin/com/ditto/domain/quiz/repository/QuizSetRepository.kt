package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.querydsl.QuizSetRepositoryCustom
import jakarta.persistence.LockModeType
import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface QuizSetRepository :
    JpaRepository<QuizSet, Long>,
    QuizSetRepositoryCustom {
    /** 어드민 목록용 — 최신 운영 주부터 정렬해 전체 조회. */
    fun findAllByOrderByWeekStartedOnDescIdDesc(): List<QuizSet>

    fun findByWeekStartedOn(weekStartedOn: LocalDate): List<QuizSet>

    /**
     * 한 셋의 후보를 만들거나 더하는 작업끼리 줄을 세우는 잠금 조회. 시크릿 매칭 cron 과 수동 실행이 겹쳐도
     * 뒤에 온 쪽이 앞쪽 결과를 보고 건너뛴다. 호출자 트랜잭션이 없으면 잠금이 바로 풀려서 MANDATORY 로 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): QuizSet?

    @Query("select distinct q.weekStartedOn from QuizSet q where q.isActive = true order by q.weekStartedOn desc")
    fun findActiveQuizSetWeeksNewestFirst(): List<LocalDate>

    /**
     * 같은 주차·타입에 자기 말고 활성 셋이 또 있는지. 아직 저장 전인 셋은 [id]가 0이라 자기 자신이 걸릴 일이 없다.
     */
    fun existsByWeekStartedOnAndMatchingTypeAndIsActiveTrueAndIdNot(
        weekStartedOn: LocalDate,
        matchingType: MatchingType,
        id: Long,
    ): Boolean
}
