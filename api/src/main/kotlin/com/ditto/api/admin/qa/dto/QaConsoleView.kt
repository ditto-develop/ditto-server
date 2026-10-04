package com.ditto.api.admin.qa.dto

import java.time.LocalDate
import java.time.LocalDateTime

class QaConsoleView(
    val weekStartedOn: LocalDate,
    val dummyCount: Int,
    val personal: QaPersonalSection,
)

class QaPersonalSection(
    val receivedRequests: List<DummyReceivedPersonalRequest>,
    val requestOptions: List<DummyPersonalRequestOption>,
) {
    companion object {
        val EMPTY = QaPersonalSection(receivedRequests = emptyList(), requestOptions = emptyList())
    }
}

class QaMember(
    val id: Long,
    val nickname: String,
)

class DummyReceivedPersonalRequest(
    val matchId: Long,
    val dummy: QaMember,
    val requester: QaMember,
    val quizSetTitle: String,
    val requestedAt: LocalDateTime,
)

/** 더미가 신청을 보낼 수 있는 실회원. 더미의 이번 주 후보 중 아직 신청이 오가지 않은 사람이다. */
class DummyPersonalRequestOption(
    val dummy: QaMember,
    val receiver: QaMember,
    val quizSetId: Long,
    val quizSetTitle: String,
)
