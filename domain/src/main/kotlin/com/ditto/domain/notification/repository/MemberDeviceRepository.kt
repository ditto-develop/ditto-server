package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.MemberDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface MemberDeviceRepository : JpaRepository<MemberDevice, Long> {

    fun findByMemberIdIn(memberIds: Collection<Long>): List<MemberDevice>

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

    /**
     * 회원의 토큰을 모두 지운다 — 탈퇴용. 탈퇴 뒤에는 인증 필터가 LEFT 를 막아 앱이 해제 API 를 부를 수 없으므로
     * 서버가 지워야 탈퇴한 폰에 푸시가 가지 않는다.
     *
     * @return 지운 행 수
     */
    @Modifying
    @Query("DELETE FROM MemberDevice d WHERE d.memberId = :memberId")
    fun deleteAllByMemberId(memberId: Long): Int
}
