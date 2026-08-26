package com.ditto.domain.notification.repository

import com.ditto.domain.notification.entity.MemberDevice
import org.springframework.data.jpa.repository.JpaRepository

interface MemberDeviceRepository : JpaRepository<MemberDevice, Long> {

    /** 토큰은 단독 유일이라 결과가 하나다. 등록(멱등·소유권 이전)과 해제가 이 조회로 갈라진다. */
    fun findByToken(token: String): MemberDevice?
}
