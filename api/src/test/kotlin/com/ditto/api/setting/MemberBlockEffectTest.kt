package com.ditto.api.setting

import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.api.setting.service.MemberBlockService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.service.UserService
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.service.UserReportService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

/**
 * 차단의 두 효력을 검증한다 — 화면(피그마 6.2.2)이 약속한 문구가 곧 명세다.
 * "차단한 사용자는 나의 프로필을 볼 수 없고, 매칭에서 제외돼요."
 */
class MemberBlockEffectTest(
    private val userService: UserService,
    private val memberRepository: MemberRepository,
    private val memberBlockRepository: MemberBlockRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val userReportService: UserReportService,
    private val memberBlockService: MemberBlockService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String, gender: Gender = Gender.FEMALE, age: Int = 27) =
        memberRepository.save(
            Member(nickname = nickname, gender = gender, age = age).apply { activate() },
        )

    "차단은 프로필 조회를 막는다" - {
        "매칭이 성사된 상대라도 차단한 뒤에는 프로필을 조회할 수 없다" {
            val viewer = saveMember("차단한조회자")
            val target = saveMember("차단된대상")
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    requesterId = viewer.id,
                    receiverId = target.id,
                    status = PersonalMatchStatus.ACCEPTED,
                ),
            )
            memberBlockRepository.save(MemberBlock.create(viewer.id, target.id))

            val exception = shouldThrow<WarnException> {
                userService.getPublicProfile(viewer.id, target.id)
            }
            exception.errorCode shouldBe ErrorCode.FORBIDDEN
        }

        "상대가 나를 차단한 경우에도 조회할 수 없다 (방향 무관)" {
            val viewer = saveMember("차단당한조회자")
            val target = saveMember("차단한대상")
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    requesterId = viewer.id,
                    receiverId = target.id,
                    status = PersonalMatchStatus.ACCEPTED,
                ),
            )
            // 차단 방향이 반대다 — target이 viewer를 차단했다.
            memberBlockRepository.save(MemberBlock.create(target.id, viewer.id))

            shouldThrow<WarnException> {
                userService.getPublicProfile(viewer.id, target.id)
            }
        }

        "차단이 없으면 매칭 성사 상대의 프로필을 조회할 수 있다" {
            val viewer = saveMember("정상조회자")
            val target = saveMember("정상대상")
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    requesterId = viewer.id,
                    receiverId = target.id,
                    status = PersonalMatchStatus.ACCEPTED,
                ),
            )

            val result = userService.getPublicProfile(viewer.id, target.id)
            result.userId shouldBe target.id
        }
    }

    "신고와 함께 차단할 수 있다" - {
        "block=true면 신고 접수와 같은 트랜잭션에서 차단이 생성된다" {
            val reporter = saveMember("신고차단자")
            val reported = saveMember("신고차단대상")

            userReportService.submitReport(
                reporterId = reporter.id,
                request = CreateUserReportRequest(
                    reportedMemberId = reported.id,
                    reason = "inappropriate-behavior",
                    source = "chat-room",
                    detail = "폭언을 반복했습니다.",
                    block = true,
                ),
            )

            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(reporter.id, reported.id) shouldBe true
        }

        "block을 생략하면 차단하지 않는다 — 자동 차단이 아니다" {
            val reporter = saveMember("신고만한자")
            val reported = saveMember("신고만당한대상")

            userReportService.submitReport(
                reporterId = reporter.id,
                request = CreateUserReportRequest(
                    reportedMemberId = reported.id,
                    reason = "inappropriate-behavior",
                    source = "profile",
                    detail = "폭언을 반복했습니다.",
                ),
            )

            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(reporter.id, reported.id) shouldBe false
        }
    }

    "차단 대상 검증" - {
        "존재하지 않는 회원은 차단할 수 없다" {
            val member = saveMember("없는사람차단시도")

            val exception = shouldThrow<WarnException> {
                memberBlockService.block(member.id, 999999L)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "차단하지 않은 상대를 해제해도 예외가 나지 않는다" {
            val member = saveMember("해제멱등회원")
            val other = saveMember("차단안된상대")

            memberBlockService.unblock(member.id, other.id)

            memberBlockRepository.findAllByBlockerIdOrderByCreatedAtDesc(member.id).size shouldBe 0
        }
    }

    "차단은 1:1 매칭 후보에서 페어를 제외한다" - {
        val processor = OneToOneMatchingProcessor()

        fun participant(memberId: Long, blocked: Set<Long> = emptySet()) = MatchParticipant(
            memberId = memberId,
            answers = mapOf(1L to 1L, 2L to 1L),
            gender = if (memberId % 2 == 0L) Gender.FEMALE else Gender.MALE,
            age = 27,
            blockedMemberIds = blocked,
        )

        "차단 관계인 두 명은 후보 페어가 되지 않는다" {
            val duos = processor.match(
                listOf(
                    participant(1L, blocked = setOf(2L)),
                    participant(2L, blocked = setOf(1L)),
                ),
            )

            duos.size shouldBe 0
        }

        "한쪽만 차단 정보를 들고 있어도 페어가 깨진다" {
            val duos = processor.match(
                listOf(
                    participant(1L, blocked = setOf(2L)),
                    participant(2L),
                ),
            )

            duos.size shouldBe 0
        }

        "차단이 없으면 페어가 만들어진다" {
            val duos = processor.match(listOf(participant(1L), participant(2L)))

            duos.size shouldBe 1
        }
    }
})
