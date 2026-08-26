package com.ditto.api.notification.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.entity.MemberDevice
import com.ditto.domain.notification.repository.MemberDeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 디바이스 토큰 쓰기 계층. 동시 등록의 유일 제약 실패를 정상 응답으로 바꾸는 일은
 * [com.ditto.api.notification.facade.MemberDeviceFacade] 몫이다 — 실패한 트랜잭션은
 * rollback-only 라 여기서 잡아봐야 커밋이 깨진다.
 */
@Service
class MemberDeviceService(
    private val memberDeviceRepository: MemberDeviceRepository,
) {

    /**
     * 없으면 생성, 남의 토큰이면 소유자 갱신([MemberDevice.transferTo]), 내 토큰이면 그대로 둔다.
     *
     * @return 이번 호출로 내 소유가 됐는지. 멱등 재호출이면 `false` (실패 아님)
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
     * 없는 토큰이면 그대로 성공(멱등), 남의 토큰이면 404.
     * 삭제가 소유자 조건부 단문인 이유는 [MemberDeviceRepository.deleteByTokenAndMemberId] 참고.
     */
    @Transactional
    fun unregister(memberId: Long, token: String) {
        val deletedCount = memberDeviceRepository.deleteByTokenAndMemberId(token, memberId)
        if (deletedCount > 0) {
            return
        }
        if (memberDeviceRepository.findByToken(token) != null) {
            throw WarnException(ErrorCode.DEVICE_NOT_FOUND)
        }
    }
}
