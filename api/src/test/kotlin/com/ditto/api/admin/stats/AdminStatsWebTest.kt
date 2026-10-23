package com.ditto.api.admin.stats

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.entity.PushSkipReason
import com.ditto.domain.notification.repository.PushSendHistoryRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.string.shouldContain
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

private val ANSWERED_AT = LocalDateTime.of(2026, 4, 13, 10, 0)

@AutoConfigureMockMvc
class AdminStatsWebTest(
    private val mockMvc: MockMvc,
    private val quizSetRepository: QuizSetRepository,
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberReviewRepository: MemberReviewRepository,
    private val reviewAnswerRepository: ReviewAnswerRepository,
    private val memberReportRepository: MemberReportRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val pushSendHistoryRepository: PushSendHistoryRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    // 필드 이름은 stats-calc.js 가 읽는다. JSON 이 JS 문자열 안에 들어가 따옴표는 \" 로, 한글은 \uXXXX 로 나온다.
    fun pageJsonContaining(json: String) = containsString(json.replace("\"", "\\\""))

    "운영 지표 화면" - {
        "퀴즈셋이 있는 주를 회차로 보여 주고 회원 집계를 페이지 JSON 에 넣는다" {
            quizSetRepository.save(QuizSetFixture.create())
            memberRepository.save(
                MemberFixture.create(status = MemberStatus.ACTIVE, joinedAt = LocalDateTime.of(2026, 4, 7, 10, 0)),
            )

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-08").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("1회차 · 4/6 주")))
                .andExpect(
                    content().string(
                        pageJsonContaining(""""countsByStatus":{"ACTIVE":1},"currentActiveRoundWeeks":["2026-04-06"]"""),
                    ),
                )
                .andExpect(content().string(pageJsonContaining(""""recentJoinedAts":["2026-04-07 10:00:00"]""")))
                .andExpect(
                    content().string(
                        pageJsonContaining(""""genderBirthDateCounts":[{"gender":null,"birthDate":null,"count":1}]"""),
                    ),
                )
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"locationCounts":[{"location":null,"count":1}],"leaveReasonCounts":[],""" +
                                """"adminMemberCount":0""",
                        ),
                    ),
                )
        }

        "퀴즈 참여·문항 분포 집계를 페이지 JSON 에 넣는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val member = memberRepository.save(MemberFixture.create(status = MemberStatus.ACTIVE))
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = member.id, quizSetId = quizSet.id, totalCount = 1)
                    .apply { recordAnswer() },
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "Q1"))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A"))
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = member.id, quizId = quiz.id, choiceId = choice.id),
            )

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-08").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"progressCounts":[{"matchingType":"ONE_TO_ONE","status":"COMPLETED",""" +
                                """"gender":null,"count":1}],""" +
                                """"activeRoundCount":4,"activeRoundWeeks":["2026-04-06"],"activeMemberCount":1""",
                        ),
                    ),
                )
                .andExpect(content().string(pageJsonContaining(""""completedAts":["""")))
                .andExpect(content().string(pageJsonContaining(""""adminCompletedCount":0""")))
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"choiceAnswerCounts":[{"quizSetId":${quizSet.id},"matchingType":"ONE_TO_ONE",""" +
                                """"quizId":${quiz.id},"quizDisplayOrder":1,"question":"Q1",""" +
                                """"choiceId":${choice.id},""" +
                                """"choiceDisplayOrder":1,"choiceContent":"A","gender":null,"count":1}]""",
                        ),
                    ),
                )
        }

        "매칭 집계를 페이지 JSON 에 넣는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val (oneMember, otherMember) = listOf("one", "other").map { nickname ->
                memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics"))
            }
            matchCandidateRepository.save(MatchCandidateFixture.create(oneMember.id, otherMember.id, quizSet.id))
            personalMatchRepository.save(
                PersonalMatchFixture.create(oneMember.id, otherMember.id, quizSet.id, PersonalMatchStatus.ACCEPTED),
            )
            val groupMatch = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(groupMatch.id, oneMember.id))

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-08").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"matchStats":{"matchCandidateCounts":[{"gender":null,"count":1}],""" +
                                """"candidateCountsPerOwner":[1],""" +
                                """"personalMatchMemberCounts":""" +
                                """[{"gender":null,"count":1},{"gender":null,"count":1}],""" +
                                """"groupMatchMemberCounts":""" +
                                """[{"isGroupActive":false,"status":"PENDING","gender":null,"count":1}],""" +
                                """"pairScores":[80.0],"acceptedPairScores":[80.0],""" +
                                """"groupScores":[{"isActive":false,"score":80.0}],""" +
                                """"activeGroupMemberCounts":[],""" +
                                """"unmatchedRoundWeeks":[],"membersUnmatchedInEveryRound":[]}""",
                        ),
                    ),
                )
        }

        "채팅 집계를 페이지 JSON 에 넣는다" {
            // ChatRoomFixture 기본 방은 이 퀴즈셋 주의 금요일(3/13)에 열린다.
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = LocalDateTime.of(2026, 3, 9, 0, 0),
                    endDate = LocalDateTime.of(2026, 3, 11, 23, 59, 59),
                ),
            )
            val personalMatch = personalMatchRepository.save(
                PersonalMatchFixture.create(1L, 2L, quizSet.id, PersonalMatchStatus.ACCEPTED),
            )
            val room = chatRoomRepository.save(ChatRoomFixture.personal(personalMatch.id))
            listOf(1L, 2L).forEach { chatRoomMemberRepository.save(ChatRoomMemberFixture.create(room.id, it)) }
            chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id, senderId = 1L))

            mockMvc.perform(get("/admin/stats").param("week", "2026-03-11").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"chatStats":{"chatRoomMemberCounts":""" +
                                """[{"roomId":${room.id},"roomType":"PERSONAL","count":2}],""" +
                                """"chatRoomSenderCounts":[{"roomId":${room.id},"count":1}],""" +
                                """"messageSummaries":[{"roomId":${room.id},"opensAt":""",
                        ),
                    ),
                )
                .andExpect(content().string(pageJsonContaining(""""senderGender":null,"messageCount":1,""")))
        }

        "평가 집계를 페이지 JSON 에 넣는다" {
            val member = memberRepository.save(MemberFixture.create(status = MemberStatus.ACTIVE))
            val memberReview = memberReviewRepository.save(MemberReviewFixture.create(authorMemberId = member.id))
            reviewAnswerRepository.save(
                ReviewAnswerFixture.pending(memberReviewId = memberReview.id)
                    .apply { answer(ReviewAnswerContent.of(MeetingStatus.MET, 5, null), ANSWERED_AT) },
            )
            memberReviewRepository.save(memberReview.apply { recordAnswer(false, ANSWERED_AT) })

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-08").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"memberReviewStats":{"memberReviewCounts":""" +
                                """[{"matchType":"PERSONAL","status":"COMPLETED","count":1}],"reviewAnswerCounts":""" +
                                """[{"matchType":"PERSONAL","meetingStatus":"MET","rating":5,"count":1}],""" +
                                """"mutualRatingCounts":[],""" +
                                """"ratingsWithMatchScore":[{"matchType":"PERSONAL","rating":5,"score":null}]}""",
                        ),
                    ),
                )
        }

        "지난 회차가 있으면 재참여 집계를 페이지 JSON 에 넣는다" {
            val previous = quizSetRepository.save(QuizSetFixture.create())
            val current = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = LocalDateTime.of(2026, 4, 13, 0, 0),
                    endDate = LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                ),
            )
            val member = memberRepository.save(MemberFixture.create(status = MemberStatus.ACTIVE))
            listOf(previous, current).forEach { quizSet ->
                quizProgressRepository.save(
                    QuizProgressFixture.create(memberId = member.id, quizSetId = quizSet.id, totalCount = 1)
                        .apply { recordAnswer() },
                )
            }

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-13").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"retentionStats":{"previousRoundWeek":"2026-04-06","previousProgressCounts":""" +
                                """[{"matchingType":"ONE_TO_ONE","status":"COMPLETED","gender":null,"count":1}],""" +
                                """"completedAgainCounts":""" +
                                """[{"previousMatchingType":"ONE_TO_ONE",""" +
                                """"againMatchingType":"ONE_TO_ONE","count":1}],""" +
                                """"matchedPreviousCompletionCounts":[],"matchedCompletedAgainCounts":[],""" +
                                """"cohortRoundWeeks":["2026-04-13","2026-04-06"],"completionsWithJoinedAt":[],""" +
                                """"averageGivenRatings":[],"averageGivenRatingsOfMembersCompletedAgain":[]}""",
                        ),
                    ),
                )
        }

        "이번 회차에 접수된 신고를 페이지 JSON 에 넣는다" {
            val (reporter, reported) = listOf("reporter", "reported").map { nickname ->
                memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics"))
            }
            memberReportRepository.save(
                MemberReportFixture.create(
                    reporterId = reporter.id,
                    reportedMemberId = reported.id,
                    reason = MemberReportReason.MONEY_DEMAND,
                ),
            )
            val mostReportedFirstWeek = OperationWeek.containing(LocalDate.now()).minusWeeks(3).startedOn

            mockMvc.perform(get("/admin/stats").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"memberReportStats":{"memberReportCounts":""" +
                                """[{"reasons":["MONEY_DEMAND"],"status":"RECEIVED","count":1}],""" +
                                """"mostReportedMembers":""" +
                                """[{"memberId":${reported.id},"nickname":"reported","count":1}],""" +
                                """"mostReportedFirstWeek":"$mostReportedFirstWeek"}""",
                        ),
                    ),
                )
        }

        "차단 수를 페이지 JSON 에 넣는다" {
            val (blocker, blocked) = listOf("blocker", "blocked").map { nickname ->
                memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics"))
            }
            memberBlockRepository.save(MemberBlock.create(blockerId = blocker.id, blockedMemberId = blocked.id))

            mockMvc.perform(get("/admin/stats").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(pageJsonContaining(""""memberBlockCount":1""")))
        }

        "푸시 발송 이력을 페이지 JSON 에 넣는다" {
            val member = memberRepository.save(MemberFixture.create())
            pushSendHistoryRepository.save(
                PushSendHistory.skipped(NotificationFixture.create(memberId = member.id), PushSkipReason.NO_DEVICE),
            )

            mockMvc.perform(get("/admin/stats").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"pushSendHistoryCounts":[{"notificationType":"MATCH_RESULT",""" +
                                """"skipReason":"NO_DEVICE","failedDeviceCount":0,"count":1}]""",
                        ),
                    ),
                )
        }

        "비활성 퀴즈셋만 있는 주는 회차 번호가 없고 회차가 아니라고 알린다" {
            quizSetRepository.save(QuizSetFixture.create(isActive = false))

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-06").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("회차 없음 · 4/6 주")))
                .andExpect(content().string(containsString("이 주는 활성 퀴즈셋이 없어 회차로 치지 않습니다")))
        }

        "회차가 아닌 주는 앞 회차에 참여자가 있어도 리텐션을 비운다" {
            val previous = quizSetRepository.save(QuizSetFixture.create())
            val member = memberRepository.save(MemberFixture.create(status = MemberStatus.ACTIVE))
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = member.id, quizSetId = previous.id, totalCount = 1)
                    .apply { recordAnswer() },
            )

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-13").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"retentionStats":{"previousRoundWeek":null,"previousProgressCounts":[],""" +
                                """"completedAgainCounts":[],"matchedPreviousCompletionCounts":[],""" +
                                """"matchedCompletedAgainCounts":[],"cohortRoundWeeks":[],""" +
                                """"completionsWithJoinedAt":[],"averageGivenRatings":[],""" +
                                """"averageGivenRatingsOfMembersCompletedAgain":[]}""",
                        ),
                    ),
                )
                .andExpect(content().string(containsString("회차가 아닌 주입니다.")))
        }

        "매칭 유형을 고르면 그 유형 라벨만 페이지 JSON 에 넣는다" {
            mockMvc.perform(get("/admin/stats").param("matchingType", "GROUP").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(pageJsonContaining(""""quizMatchingTypeLabels":{"GROUP":""")))
                .andExpect(content().string(pageJsonContaining(""""memberReviewMatchTypeLabels":{"GROUP":""")))
        }

        "빈 필터 값을 넘겨도 오류 없이 매칭 유형 전체로 열린다" {
            val html = mockMvc.perform(
                get("/admin/stats")
                    .param("matchingType", "")
                    .param("gender", "")
                    .param("ageBand", "")
                    .param("joinedWeek", "")
                    .with(authentication(admin)),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

            html shouldContain Regex("""value="ALL"\s+selected="selected"""")
        }
    }
})
