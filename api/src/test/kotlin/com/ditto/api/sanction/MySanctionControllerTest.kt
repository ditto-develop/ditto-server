package com.ditto.api.sanction

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private val SANCTION_LEVELS = SanctionLevel.entries.joinToString(", ") { "${it.name}(${it.description})" }
private val REPORT_REASON_CODES = MemberReportReason.entries.joinToString(", ") { "${it.code}(${it.description})" }

class MySanctionControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var sanctionRepository: SanctionRepository

    @Test
    @DisplayName("이용 정지 중인 회원도 자신의 제재를 조회할 수 있다")
    fun getMySanction() {
        val until = LocalDateTime.now().plusDays(7)
        val member = memberRepository.save(
            Member(nickname = "정지회원").apply {
                activate()
                suspendUntil(until)
            },
        )
        sanctionRepository.save(
            SanctionFixture.create(
                memberId = member.id,
                origin = SanctionOrigin.MANUAL,
                level = SanctionLevel.SUSPENSION,
                startsAt = until.minusDays(14),
                endsAt = until,
            ),
        )

        mockMvc.perform(
            get("/api/v1/users/me/sanction")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sanction.level").value("SUSPENSION"))
            .andDo(
                document(
                    "my-sanction",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("내 제재 조회")
                            .description(
                                "현재 유효한 내 제재를 조회합니다. 제재가 없으면 sanction이 null입니다. " +
                                    "이용 정지 중에도 호출할 수 있는 유일한 API로, 정지 안내 화면이 사용합니다. " +
                                    "신고자·신고 시점·상세 내용은 노출하지 않습니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.sanction").description("유효한 제재 (없으면 null)").optional(),
                                fieldWithPath("data.sanction.level").description("제재 수위. 가능한 값: $SANCTION_LEVELS"),
                                fieldWithPath("data.sanction.levelDescription").description("제재 수위 설명"),
                                fieldWithPath("data.sanction.reason")
                                    .description("신고 사유 code (직권 제재는 null). 가능한 값: $REPORT_REASON_CODES")
                                    .optional(),
                                fieldWithPath("data.sanction.reasonDescription").description("신고 사유 설명 (직권 제재는 null)").optional(),
                                fieldWithPath("data.sanction.startsAt").description("제재 시작 일시"),
                                fieldWithPath("data.sanction.endsAt").description("제재 종료 일시 (영구 차단은 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("유효한 제재가 없으면 sanction이 null이다")
    fun getMySanctionEmpty() {
        val member = memberRepository.save(Member(nickname = "무제재회원").apply { activate() })

        mockMvc.perform(
            get("/api/v1/users/me/sanction")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sanction").isEmpty)
    }
}
