package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaRoomView

/** 콘솔에서 방을 열지 못한 이유를 화면에 다르게 보여주려고 나눈다. */
sealed interface QaRoomLookup {
    data class Found(val view: QaRoomView) : QaRoomLookup

    data object NotFound : QaRoomLookup

    // 실회원끼리의 대화는 운영자가 보지 않는다.
    data object WithoutDummy : QaRoomLookup
}
