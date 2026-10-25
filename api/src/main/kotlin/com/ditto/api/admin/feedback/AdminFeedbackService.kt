package com.ditto.api.admin.feedback

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.feedback.dto.FeedbackDetailView
import com.ditto.api.admin.feedback.dto.FeedbackListItem
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackImageRepository
import com.ditto.domain.feedback.repository.FeedbackRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.infrastructure.storage.ObjectStorage
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminFeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackImageRepository: FeedbackImageRepository,
    private val memberRepository: MemberRepository,
    private val objectStorage: ObjectStorage,
) {

    @Transactional(readOnly = true)
    fun countFeedbacks(status: FeedbackStatus): Long = feedbackRepository.countByStatus(status)

    @Transactional(readOnly = true)
    fun listFeedbacks(status: FeedbackStatus, type: FeedbackType?): List<FeedbackListItem> {
        val feedbacks = findFeedbacks(status, type)
        val nicknames = nicknamesOf(feedbacks.map { it.memberId })
        val imageCounts = feedbackImageRepository.findAllByFeedbackIdIn(feedbacks.map { it.id })
            .groupingBy { it.feedbackId }
            .eachCount()

        return feedbacks.map { feedback ->
            FeedbackListItem(
                id = feedback.id,
                typeDescription = feedback.type.description,
                contentPreview = previewOf(feedback.content),
                memberNickname = nicknames.getValue(feedback.memberId),
                imageCount = imageCounts[feedback.id] ?: 0,
                createdAt = feedback.createdAt,
                completedByName = feedback.completedByName,
                completedAt = feedback.completedAt,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getFeedbackDetail(id: Long): FeedbackDetailView {
        val feedback = findFeedback(id)
        val member = memberRepository.findById(feedback.memberId).getOrNull()
        val imageUrls = feedbackImageRepository.findAllByFeedbackIdOrderByDisplayOrder(id)
            .map { objectStorage.issueViewUrl(it.objectKey) }

        return FeedbackDetailView(
            id = feedback.id,
            typeDescription = feedback.type.description,
            statusDescription = feedback.status.description,
            awaitingCompletion = !feedback.isCompleted,
            content = feedback.content,
            appVersion = feedback.appVersion,
            os = feedback.os,
            deviceModel = feedback.deviceModel,
            memberId = feedback.memberId,
            memberNickname = member?.nickname ?: withdrawnMemberLabel(feedback.memberId),
            memberStatus = member?.status,
            createdAt = feedback.createdAt,
            imageUrls = imageUrls,
            completedByName = feedback.completedByName,
            completedAt = feedback.completedAt,
        )
    }

    @Transactional
    fun completeFeedback(id: Long, admin: AdminPrincipal, now: LocalDateTime) {
        findFeedback(id).complete(adminName = admin.displayName, now = now)
    }

    private fun findFeedbacks(status: FeedbackStatus, type: FeedbackType?): List<Feedback> {
        if (type == null) return feedbackRepository.findAllByStatusOrderByIdDesc(status)
        return feedbackRepository.findAllByStatusAndTypeOrderByIdDesc(status, type)
    }

    private fun findFeedback(id: Long): Feedback =
        feedbackRepository.findById(id).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND, "없는 의견입니다: #$id")

    private fun previewOf(content: String): String {
        val singleLine = content.replace(WHITESPACES, " ").trim()
        if (singleLine.length <= PREVIEW_LENGTH) return singleLine
        return singleLine.take(PREVIEW_LENGTH) + "…"
    }

    private fun nicknamesOf(memberIds: List<Long>): Map<Long, String> {
        val found = memberRepository.findAllById(memberIds.distinct()).associate { it.id to it.nickname }
        return memberIds.associateWith { found[it] ?: withdrawnMemberLabel(it) }
    }

    private fun withdrawnMemberLabel(memberId: Long): String = "(탈퇴한 회원 #$memberId)"

    companion object {
        private const val PREVIEW_LENGTH = 60
        private val WHITESPACES = Regex("\\s+")
    }
}
