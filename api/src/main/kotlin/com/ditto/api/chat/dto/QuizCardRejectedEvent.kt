package com.ditto.api.chat.dto

import com.ditto.common.exception.ErrorCode

/**
 * 질문 카드를 버렸음을 알리는 STOMP 프레임. 같은 방에 이미 올라온 문항이면 카드는 저장하지 않는다(한마디는 저장된다).
 *
 * 개인 큐가 없어 방 토픽으로 보내고, 수신 측은 [memberId]가 자기일 때만 토스트를 띄운다. 담긴 정보가 "누가 이미 올라온
 * 질문을 다시 눌렀다"뿐이라 방 멤버에게 보여도 문제가 없다. 메시지·READ 프레임과 [type]으로 구분한다.
 */
data class QuizCardRejectedEvent(
    val roomId: Long,
    val memberId: Long,
    val quizId: Long,
) {
    val type: String
        get() = TYPE

    val code: String
        get() = ErrorCode.QUIZ_CARD_ALREADY_SENT.code

    val message: String
        get() = ErrorCode.QUIZ_CARD_ALREADY_SENT.message

    companion object {
        const val TYPE = "QUIZ_CARD_REJECTED"
    }
}
