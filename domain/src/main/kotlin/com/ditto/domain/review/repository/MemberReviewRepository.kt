package com.ditto.domain.review.repository

import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.repository.querydsl.MemberReviewRepositoryCustom
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface MemberReviewRepository : JpaRepository<MemberReview, Long>, MemberReviewRepositoryCustom {
    /** 동일 종료 이벤트 재처리 시 기존 진행 단위를 찾아 멱등 처리한다. */
    fun findByChatRoomIdAndAuthorMemberId(chatRoomId: Long, authorMemberId: Long): MemberReview?

    /**
     * 제출 트랜잭션 전용 잠금 조회 — 같은 평가에 겹친 제출을 직렬화해 진행률·완료 전이가 유실되지 않게 한다.
     *
     * 화면은 대상을 한 명씩 제출하지만, 더블 탭·클라이언트 재시도·API 직접 호출로 요청이 겹칠 수 있다.
     * 겹친 요청은 서로의 커밋 전 상태를 보므로 멱등 판정(`ReviewAnswer.content()` 비교)만으로는 막히지 않는다 —
     * 잠금이 순서를 만들어 뒤 요청이 최신 상태를 보고 멱등 경로로 빠지게 한다.
     * 어긋나면 완료한 평가가 미완료 목록에 남고 제출할 대상이 없어 벗어날 수 없다(`status`는 비정규화 값이다).
     *
     * 재매칭까지 잠글 때는 이 평가를 먼저 잠근다 (잠금 순서 고정 — ADR 0011).
     *
     * 호출자 트랜잭션이 없으면 잠금이 즉시 풀리고 엔티티가 준영속이 되어 이후 변경이 조용히 사라지므로
     * MANDATORY로 두어 유실 대신 예외로 드러낸다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): MemberReview?

    fun findAllByChatRoomId(chatRoomId: Long): List<MemberReview>
}
