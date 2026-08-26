package com.ditto.api.notification.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.entity.MemberDevice
import com.ditto.domain.notification.repository.MemberDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시 발송(FCM)의 주소록 관리 — 디바이스 토큰 등록·해제.
 * 발송과 죽은 토큰 정리(FCM `Unregistered` 응답 시 삭제)는 발송 인프라가 붙을 때 온다(#152 후속).
 */
@Service
class MemberDeviceService(
    private val memberDeviceRepository: MemberDeviceRepository,
) {

    /**
     * 토큰을 등록한다. 앱이 실행·토큰 갱신 때마다 다시 부르므로 멱등해야 한다.
     *
     * 이미 남의 토큰으로 등록돼 있으면 소유자를 요청 회원으로 갱신한다 — 토큰은 기기의 것이라
     * 로그아웃해도 그대로이므로, 갱신하지 않으면 이전 회원의 알림이 남의 폰에 뜬다.
     *
     * 같은 토큰의 동시 최초 등록은 유일 제약이 한쪽을 막는다. 실패한 쪽은 앱 재실행 때
     * 재등록되므로 별도 재시도를 두지 않는다.
     *
     * @return 이번 호출로 이 회원 소유가 됐으면 `true`(신규·소유권 이전).
     *         이미 이 회원의 토큰이었으면 `false` — 어느 쪽이든 등록된 상태로 끝난다
     */
    @Transactional
    fun register(memberId: Long, token: String, platform: DevicePlatform): Boolean {
        val device = memberDeviceRepository.findByToken(token)
        if (device == null) {
            memberDeviceRepository.save(MemberDevice(memberId = memberId, token = token, platform = platform))
            return true
        }
        if (device.isOwnedBy(memberId)) {
            return false
        }
        device.transferTo(memberId)
        return true
    }

    /**
     * 토큰을 해제한다. 로그아웃·탈퇴 직전에 앱이 부른다.
     *
     * 이미 없는 토큰이면 그대로 성공한다(멱등 — 재시도·중복 호출 대비).
     * 남의 토큰이면 404 — 다른 회원의 푸시 등록을 끊을 수 없어야 한다.
     */
    @Transactional
    fun unregister(memberId: Long, token: String) {
        val device = memberDeviceRepository.findByToken(token) ?: return
        if (!device.isOwnedBy(memberId)) {
            throw WarnException(ErrorCode.DEVICE_NOT_FOUND)
        }
        memberDeviceRepository.delete(device)
    }
}
