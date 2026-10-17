package com.ditto.api.admin.feedback

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.FeedbackFixture
import com.ditto.domain.feedback.entity.FeedbackDeviceInfo
import com.ditto.domain.feedback.entity.FeedbackImage
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackImageRepository
import com.ditto.domain.feedback.repository.FeedbackRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDateTime
import javax.sql.DataSource
import org.springframework.data.repository.findByIdOrNull

class AdminFeedbackServiceTest(
    private val adminFeedbackService: AdminFeedbackService,
    private val feedbackRepository: FeedbackRepository,
    private val feedbackImageRepository: FeedbackImageRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = AdminPrincipal(memberId = 1L, name = "운영자", email = "admin@ditto.pics")

    fun saveMember(nickname: String): Member =
        memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE))

    "의견 목록" - {

        "고른 상태의 의견을 최신순으로 보여 주고 스크린샷 수를 센다" {
            val memberId = saveMember("제보자").id
            val older = feedbackRepository.save(FeedbackFixture.create(memberId = memberId, content = "먼저 보낸 의견"))
            val newer = feedbackRepository.save(FeedbackFixture.create(memberId = memberId, content = "나중에 보낸 의견"))
            feedbackImageRepository.saveAll(
                FeedbackImage.attachAll(feedbackId = newer.id, objectKeys = listOf("feedbacks/1/a", "feedbacks/1/b")),
            )

            val rows = adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, type = null)

            rows.map { it.id } shouldBe listOf(newer.id, older.id)
            rows.map { it.imageCount } shouldBe listOf(2, 0)
            rows[0].memberNickname shouldBe "제보자"
        }

        "유형을 고르면 그 유형만 보여 준다" {
            val memberId = saveMember("제보자").id
            val bug = feedbackRepository.save(FeedbackFixture.create(memberId = memberId, type = FeedbackType.BUG))
            feedbackRepository.save(FeedbackFixture.create(memberId = memberId, type = FeedbackType.SUGGESTION))

            val rows = adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, FeedbackType.BUG)

            rows.map { it.id } shouldBe listOf(bug.id)
        }

        "긴 내용은 한 줄로 줄여 앞부분만 보여 준다" {
            val memberId = saveMember("제보자").id
            feedbackRepository.save(FeedbackFixture.create(memberId = memberId, content = "첫 줄\n" + "가".repeat(100)))

            val preview = adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, type = null).single().contentPreview

            preview shouldStartWith "첫 줄 가"
            preview shouldEndWith "…"
            preview.length shouldBe 60 + "…".length
        }

        "회원 행이 지워졌으면 탈퇴한 회원으로 보여 준다" {
            feedbackRepository.save(FeedbackFixture.create(memberId = 99999L))

            val row = adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, type = null).single()

            row.memberNickname shouldBe "(탈퇴한 회원 #99999)"
        }
    }

    "의견 상세" - {

        "회원 상태·기기 정보와 스크린샷 열람 URL을 첨부 순서대로 보여 준다" {
            val memberId = saveMember("제보자").id
            val feedback = feedbackRepository.save(
                FeedbackFixture.create(
                    memberId = memberId,
                    deviceInfo = FeedbackDeviceInfo(appVersion = "1.4.0", os = "iOS 18.2", deviceModel = "iPhone15,2"),
                ),
            )
            feedbackImageRepository.saveAll(
                FeedbackImage.attachAll(feedbackId = feedback.id, objectKeys = listOf("feedbacks/1/a", "feedbacks/1/b")),
            )

            val detail = adminFeedbackService.getFeedbackDetail(feedback.id)

            detail.memberNickname shouldBe "제보자"
            detail.memberStatus shouldBe MemberStatus.ACTIVE
            detail.appVersion shouldBe "1.4.0"
            detail.hasDeviceInfo shouldBe true
            detail.awaitingCompletion shouldBe true
            detail.imageUrls shouldBe listOf(
                "https://fake-storage.local/view/feedbacks/1/a",
                "https://fake-storage.local/view/feedbacks/1/b",
            )
        }

        "회원 행이 지워졌으면 상태 없이 탈퇴한 회원으로 보여 준다" {
            val feedback = feedbackRepository.save(FeedbackFixture.create(memberId = 99999L))

            val detail = adminFeedbackService.getFeedbackDetail(feedback.id)

            detail.memberNickname shouldBe "(탈퇴한 회원 #99999)"
            detail.memberStatus shouldBe null
            detail.hasDeviceInfo shouldBe false
        }

        "없는 의견이면 거부한다" {
            shouldThrow<WarnException> {
                adminFeedbackService.getFeedbackDetail(12345L)
            }.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "처리 완료" - {

        "처리 완료하면 관리자 표시명이 저장되고 확인 대기 목록에서 처리 완료 목록으로 옮겨 간다" {
            val feedback = feedbackRepository.save(FeedbackFixture.create(memberId = saveMember("제보자").id))

            adminFeedbackService.completeFeedback(feedback.id, admin, LocalDateTime.of(2026, 10, 17, 15, 0))

            val saved = feedbackRepository.findByIdOrNull(feedback.id) ?: error("의견이 없다")
            saved.completedByName shouldBe admin.displayName
            adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, type = null).size shouldBe 0
            val completedRow = adminFeedbackService.listFeedbacks(FeedbackStatus.COMPLETED, type = null).single()
            completedRow.completedByName shouldBe admin.displayName
        }

        "이름과 이메일이 긴 관리자도 처리 완료할 수 있다" {
            val feedback = feedbackRepository.save(FeedbackFixture.create(memberId = saveMember("제보자").id))
            val longNameAdmin = AdminPrincipal(memberId = 2L, name = "가".repeat(50), email = "a".repeat(88) + "@ditto.pics")

            adminFeedbackService.completeFeedback(feedback.id, longNameAdmin, LocalDateTime.of(2026, 10, 17, 15, 0))

            val saved = feedbackRepository.findByIdOrNull(feedback.id) ?: error("의견이 없다")
            saved.completedByName shouldBe longNameAdmin.displayName
        }
    }
})
