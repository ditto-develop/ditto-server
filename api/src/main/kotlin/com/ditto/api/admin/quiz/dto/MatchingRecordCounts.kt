package com.ditto.api.admin.quiz.dto

/** QA 도구가 지우는 매칭 기록 수. 누르기 전 미리보기와 지운 뒤 결과가 같은 형식으로 보이게 한다. */
class MatchingRecordCounts(
    val candidateRowCount: Int,
    val personalMatchCount: Int,
    val groupMatchCount: Int,
    val rematchCount: Int,
    val roomCount: Int,
) {
    /** 0인 항목은 뺀다. */
    fun toDisplayText(): String =
        listOfNotNull(
            countText("1:1 후보", candidateRowCount, "행"),
            countText("1:1 신청", personalMatchCount, "건"),
            countText("그룹", groupMatchCount, "개"),
            countText("재매칭", rematchCount, "건"),
            countText("채팅방", roomCount, "개"),
        ).joinToString(" · ")

    private fun countText(label: String, count: Int, unit: String): String? =
        if (count > 0) "$label $count$unit" else null
}

/** 지운 뒤 결과. 강제 삭제 뒤에는 목록으로 가므로 어떤 셋이었는지 제목을 함께 남긴다. */
class MatchingEraseSummary(
    val quizSetId: Long,
    val quizSetTitle: String,
    val counts: MatchingRecordCounts,
    val notificationCount: Int,
) {
    val isNothingErased: Boolean get() = composeErasedText().isEmpty()

    fun toDisplayText(): String = composeErasedText().ifEmpty { "지운 기록 없음" }

    private fun composeErasedText(): String {
        val notificationText = if (notificationCount > 0) "알림 ${notificationCount}개" else null
        return listOfNotNull(counts.toDisplayText().ifEmpty { null }, notificationText).joinToString(" · ")
    }
}

/** 누르기 전 미리보기. 운영에서 QA 중이라 실회원이 낀 방이 몇 개인지를 따로 센다. */
class MatchingErasePreview(
    val counts: MatchingRecordCounts,
    val realMemberRoomCount: Int,
)
