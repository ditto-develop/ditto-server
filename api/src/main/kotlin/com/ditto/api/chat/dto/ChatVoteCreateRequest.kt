package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatVoteOption
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 투표 생성 요청. 화면(피그마 4.2.2)이 장소 → 시간 2단계로 받아 한 번에 제출한다.
 *
 * 장소·시간 **둘 다** [MIN_OPTION_COUNT]~[ChatVoteOption.MAX_COUNT_PER_TYPE]개다 — 생성 모달이 두 스텝을 모두
 * 강제하고 유효성 문구도 "최소 2개"다. `title`은 받지 않는다(생성 화면에 제목 입력이 없다 —
 * 배너 문구는 FE 기본값이다).
 */
data class ChatVoteCreateRequest(
    val allowMultiple: Boolean = false,

    @field:Valid
    @field:Size(min = MIN_OPTION_COUNT, max = ChatVoteOption.MAX_COUNT_PER_TYPE, message = "장소 선택지는 ${MIN_OPTION_COUNT}~${ChatVoteOption.MAX_COUNT_PER_TYPE}개여야 합니다.")
    val placeOptions: List<PlaceOptionRequest>,

    @field:Valid
    @field:Size(min = MIN_OPTION_COUNT, max = ChatVoteOption.MAX_COUNT_PER_TYPE, message = "시간 선택지는 ${MIN_OPTION_COUNT}~${ChatVoteOption.MAX_COUNT_PER_TYPE}개여야 합니다.")
    val timeOptions: List<TimeOptionRequest>,
) {
    data class PlaceOptionRequest(
        @field:NotBlank
        @field:Size(max = ChatVoteOption.LABEL_MAX_LENGTH)
        val label: String,

        @field:Size(max = ChatVoteOption.ADDRESS_MAX_LENGTH)
        val address: String? = null,

        @field:Size(max = ChatVoteOption.MAP_LINK_MAX_LENGTH)
        val mapLink: String? = null,

        val latitude: Double? = null,
        val longitude: Double? = null,
    )

    data class TimeOptionRequest(
        // 전역 직렬화 포맷(yyyy-MM-dd HH:mm:ss)을 그대로 쓴다 — date·time 분리나 HH:mm 국소 포맷을
        // 만들지 않는 이유는 설계서 §3-0 참조. FE 는 "${date} ${time}:00" 조립 한 줄이다.
        val meetAt: LocalDateTime,
    )

    companion object {
        const val MIN_OPTION_COUNT = 2
    }
}
