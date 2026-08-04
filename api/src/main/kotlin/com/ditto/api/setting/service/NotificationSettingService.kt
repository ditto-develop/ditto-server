package com.ditto.api.setting.service

import com.ditto.api.setting.dto.NotificationSettingsResponse
import com.ditto.api.setting.dto.UpdateNotificationSettingsRequest
import com.ditto.api.setting.dto.toResponse
import com.ditto.domain.member.entity.MemberNotificationSetting
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 설정 화면의 알림 토글. 행이 없는 회원은 기본값으로 응답하며, 조회만으로 행을 만들지 않는다
 * — 읽기 트랜잭션에서 쓰기를 하지 않아야 읽기 전용 복제로 옮겨도 안전하다.
 */
@Service
class NotificationSettingService(
    private val repository: MemberNotificationSettingRepository,
) {

    @Transactional(readOnly = true)
    fun getSettings(memberId: Long): NotificationSettingsResponse =
        (repository.findByMemberId(memberId) ?: MemberNotificationSetting.defaultOf(memberId)).toResponse()

    /** 부분 패치. 아직 행이 없으면 기본값에서 시작해 만든다. */
    @Transactional
    fun updateSettings(
        memberId: Long,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse {
        val setting = repository.findByMemberId(memberId)
            ?: repository.save(MemberNotificationSetting.defaultOf(memberId))

        setting.update(
            matching = request.matching,
            chat = request.chat,
            marketing = request.marketing,
        )
        return setting.toResponse()
    }
}
