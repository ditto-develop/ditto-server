package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.MemberDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface MemberDeviceRepository : JpaRepository<MemberDevice, Long> {

    /** token 은 단독 유일이라 결과가 최대 1건이다. */
    fun findByToken(token: String): MemberDevice?

    fun findAllByMemberId(memberId: Long): List<MemberDevice>

    /**
     * 죽은 토큰 정리 — 방치하면 FCM 이 발송량을 제한한다 (`PushSender` 참고).
     *
     * @return 지운 행 수
     */
    @Modifying
    @Query("DELETE FROM MemberDevice d WHERE d.token IN :tokens")
    fun deleteAllByTokenIn(tokens: List<String>): Int

    /**
     * 소유자 조건까지 DELETE 한 문장으로 — 조회 후 지우면 그 사이 소유권이 넘어간 행을 지울 수 있다.
     * 0 이면 없거나 남의 토큰. 구분은 호출부가 조회로 한다.
     */
    @Modifying
    @Query("DELETE FROM MemberDevice d WHERE d.token = :token AND d.memberId = :memberId")
    fun deleteByTokenAndMemberId(token: String, memberId: Long): Int
}
