package com.ditto.api.chat

import com.ditto.api.chat.controller.ChatQuizController
import com.ditto.api.chat.dto.QuizCompareItem
import com.ditto.api.chat.dto.QuizComparePercentChoice
import com.ditto.api.chat.dto.QuizCompareResponse
import com.ditto.api.chat.dto.QuizDistributionChoice
import com.ditto.api.chat.dto.QuizDistributionItem
import com.ditto.api.chat.dto.QuizDistributionResponse
import com.ditto.api.chat.quiz.ChatQuizAnswerService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class ChatQuizControllerTest : ControllerUnitTest() {

    private val chatQuizAnswerService: ChatQuizAnswerService = mockk()

    override val controller = ChatQuizController(chatQuizAnswerService)

    @Test
    @DisplayName("1:1 방의 문항별 답 비교를 조회한다")
    fun compare() {
        every { chatQuizAnswerService.compare(any(), 1L) } returns QuizCompareResponse(
            quizSetId = 41L,
            totalCount = 2,
            sameCount = 1,
            diffCount = 1,
            highlightQuizId = 301L,
            canSendCard = true,
            chatOpensAt = LocalDateTime.of(2026, 11, 6, 0, 0),
            items = listOf(
                QuizCompareItem(
                    quizId = 301L, order = 1, question = "시간이 멈춘다면 그 시각은?",
                    choices = listOf(QuizComparePercentChoice(1L, "오후 3시", 82), QuizComparePercentChoice(2L, "새벽 2시", 18)),
                    myChoiceId = 2L, peerChoiceId = 2L, same = true, minority = true,
                    hintQuestion = "그 시간엔 주로 뭘 하세요?", sent = false,
                ),
                QuizCompareItem(
                    quizId = 302L, order = 2, question = "커튼을 열면 보이는 색은?",
                    choices = listOf(QuizComparePercentChoice(3L, "하늘빛", 58), QuizComparePercentChoice(4L, "노을빛", 42)),
                    myChoiceId = 3L, peerChoiceId = 4L, same = false, minority = false,
                    hintQuestion = null, sent = true,
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/quiz-compare", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sameCount").value(1))
            .andDo(
                document(
                    "chat-quiz-compare",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("roomId").description("채팅방 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("1:1 답변 비교")
                            .description(
                                "1:1·재매칭 방에서 두 사람의 문항별 답을 비교합니다(재매칭은 원래 그룹 퀴즈 기준). 배너 숫자도 이 응답으로 그립니다. " +
                                    "방이 생긴 뒤부터 끝나기 전까지만 보입니다: 끝난 방 409(7004), 상대 탈퇴·차단 409(7008), 그룹 방 400(7009), 남의 방 403(7002).",
                            )
                            .pathParameters(parameterWithName("roomId").description("채팅방 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("비교 기준 퀴즈셋 ID"),
                                fieldWithPath("data.totalCount").description("문항 수"),
                                fieldWithPath("data.sameCount").description("같은 답 문항 수"),
                                fieldWithPath("data.diffCount").description("다른 답 문항 수"),
                                fieldWithPath("data.highlightQuizId").description("같은 답 중 소수 의견이고 내 선택 비율이 가장 낮은 문항. 없으면 null").optional(),
                                fieldWithPath("data.canSendCard").description("질문 카드를 보낼 수 있는지 (방이 열려 있고 기능이 켜져 있을 때)"),
                                fieldWithPath("data.chatOpensAt").description("방 개방 시각 — 열리기 전 딤드 버튼 문구에 쓴다"),
                                fieldWithPath("data.items[].quizId").description("문항 ID"),
                                fieldWithPath("data.items[].order").description("문항 순서"),
                                fieldWithPath("data.items[].question").description("질문"),
                                fieldWithPath("data.items[].choices[].choiceId").description("선택지 ID"),
                                fieldWithPath("data.items[].choices[].content").description("선택지 문구"),
                                fieldWithPath("data.items[].choices[].percent").description("그 주 전체 완주자 기준 % (마감 스냅샷)"),
                                fieldWithPath("data.items[].myChoiceId").description("내 선택지 ID").optional(),
                                fieldWithPath("data.items[].peerChoiceId").description("상대 선택지 ID").optional(),
                                fieldWithPath("data.items[].same").description("같은 답인지"),
                                fieldWithPath("data.items[].minority").description("내 선택이 소수 의견인지"),
                                fieldWithPath("data.items[].hintQuestion").description("대화 힌트 질문. 없으면 null → FE 공통 문구").optional(),
                                fieldWithPath("data.items[].sent").description("이 방에 이미 카드로 올린 문항인지"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("그룹 방의 문항별 답 분포를 조회한다")
    fun distribution() {
        every { chatQuizAnswerService.distribution(any(), 2L) } returns QuizDistributionResponse(
            quizSetId = 42L,
            memberCount = 4,
            totalCount = 2,
            unanimousCount = 1,
            splitCount = 1,
            splitQuizIds = listOf(402L),
            canSendCard = false,
            chatOpensAt = LocalDateTime.of(2026, 11, 6, 0, 0),
            items = listOf(
                QuizDistributionItem(
                    quizId = 401L, order = 1, question = "시간이 멈춘다면 그 시각은?",
                    choices = listOf(QuizDistributionChoice(11L, "오후 3시", 0), QuizDistributionChoice(12L, "새벽 2시", 4)),
                    myChoiceId = 12L, unanimous = true, hintQuestion = "그 시간엔 주로 뭘 하세요?", sent = true,
                ),
                QuizDistributionItem(
                    quizId = 402L, order = 2, question = "커튼을 열면 보이는 색은?",
                    choices = listOf(QuizDistributionChoice(13L, "하늘빛", 2), QuizDistributionChoice(14L, "노을빛", 2)),
                    myChoiceId = 13L, unanimous = false, hintQuestion = null, sent = false,
                ),
            ),
        )

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/quiz-distribution", 2L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.splitCount").value(1))
            .andDo(
                document(
                    "chat-quiz-distribution",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("roomId").description("채팅방 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("그룹 답변 분포")
                            .description(
                                "그룹 방에 남아 있는 멤버 기준 실시간 분포입니다. 누가 무엇을 골랐는지 잇는 필드는 없습니다(인원 수만). " +
                                    "끝난 방 409(7004), 1:1 방 400(7009), 남의 방 403(7002).",
                            )
                            .pathParameters(parameterWithName("roomId").description("채팅방 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").description("그룹 퀴즈셋 ID"),
                                fieldWithPath("data.memberCount").description("방에 남아 있는 멤버 수"),
                                fieldWithPath("data.totalCount").description("문항 수"),
                                fieldWithPath("data.unanimousCount").description("배너 '다 같은 답 N'"),
                                fieldWithPath("data.splitCount").description("배너 '반반 M' = totalCount − unanimousCount"),
                                fieldWithPath("data.splitQuizIds").description("'반반 갈린 질문' — 만장일치 제외, 인원 차 작은 순, 최대 3개"),
                                fieldWithPath("data.canSendCard").description("질문 카드를 올릴 수 있는지"),
                                fieldWithPath("data.chatOpensAt").description("방 개방 시각"),
                                fieldWithPath("data.items[].quizId").description("문항 ID"),
                                fieldWithPath("data.items[].order").description("문항 순서"),
                                fieldWithPath("data.items[].question").description("질문"),
                                fieldWithPath("data.items[].choices[].choiceId").description("선택지 ID"),
                                fieldWithPath("data.items[].choices[].content").description("선택지 문구"),
                                fieldWithPath("data.items[].choices[].count").description("이 선택지를 고른 멤버 수"),
                                fieldWithPath("data.items[].myChoiceId").description("내 선택지 ID").optional(),
                                fieldWithPath("data.items[].unanimous").description("답한 멤버가 모두 같은 답인지"),
                                fieldWithPath("data.items[].hintQuestion").description("대화 힌트 질문. 없으면 null").optional(),
                                fieldWithPath("data.items[].sent").description("이 방에 이미 카드로 올린 문항인지"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("끝난 방이면 7004 로 답해 FE 가 배너를 숨긴다")
    fun compareEndedRoom() {
        every { chatQuizAnswerService.compare(any(), 3L) } throws WarnException(ErrorCode.CHAT_ROOM_ENDED)

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/quiz-compare", 3L))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("7004"))
    }
}
