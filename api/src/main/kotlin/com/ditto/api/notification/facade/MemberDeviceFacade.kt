package com.ditto.api.notification.facade

import com.ditto.api.notification.service.MemberDeviceService
import com.ditto.domain.notification.entity.DevicePlatform
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

/**
 * 디바이스 토큰 등록·해제 진입점. 발송과 죽은 토큰 정리는 발송 인프라에서 잇는다(#152 후속).
 *
 * 트랜잭션을 열지 않는다 — 유일 제약에 걸린 트랜잭션은 rollback-only 라, 그 예외를
 * 트랜잭션 밖에서 잡아야 정상 응답으로 바꿀 수 있다.
 */
@Component
class MemberDeviceFacade(
    private val memberDeviceService: MemberDeviceService,
) {

    /**
     * 앱이 실행·토큰 갱신 때마다 다시 부른다(멱등).
     *
     * 같은 토큰의 동시 최초 등록(따닥)은 유일 제약이 한쪽을 막는다. 등록은 인증 필수라
     * 경쟁 상대는 사실상 같은 회원 자신이고, 첫 요청이 이미 행을 만들었으니 진 쪽에게는
     * "이번 호출로 새로 된 것 없음" = `false`가 사실 그대로의 답이다. 오류로 흘리면
     * 정상 따닥이 9999 응답과 ERROR 로그가 된다.
     */
    fun register(memberId: Long, token: String, platform: DevicePlatform): Boolean =
        runCatching { memberDeviceService.register(memberId, token, platform) }
            .getOrElse { failure ->
                if (failure !is DataIntegrityViolationException) throw failure
                false
            }

    /** 로그아웃·탈퇴 직전에 앱이 부른다. 규칙은 [MemberDeviceService.unregister]에 있다. */
    fun unregister(memberId: Long, token: String) = memberDeviceService.unregister(memberId, token)
}
