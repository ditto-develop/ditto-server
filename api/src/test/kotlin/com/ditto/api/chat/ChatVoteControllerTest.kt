package com.ditto.api.chat

import com.ditto.api.chat.controller.ChatVoteController
import com.ditto.api.chat.dto.ChatVoteCastRequest
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatVoteChangeResult
import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.PlaceOptionRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.TimeOptionRequest
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.api.chat.dto.ChatVoteDetailResponse.MyVoteResponse
import com.ditto.api.chat.dto.ChatVoteDetailResponse.PlaceOptionResponse
import com.ditto.api.chat.dto.ChatVoteDetailResponse.TimeOptionResponse
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.api.notification.notifier.ChatVoteClosedNotifier
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatVoteStatus
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class ChatVoteControllerTest : ControllerUnitTest() {

    private val chatVoteService: ChatVoteService = mockk()
    private val messagingTemplate: SimpMessagingTemplate = mockk(relaxed = true)
    private val chatVoteClosedNotifier: ChatVoteClosedNotifier = mockk(relaxed = true)

    override val controller = ChatVoteController(chatVoteService, messagingTemplate, chatVoteClosedNotifier)

    /** nullable 필드도 전부 non-null 샘플 — 전부 null 이면 openapi.yaml 스키마에서 그 필드가 빠진다(#140). */
    private fun sampleDetail(
        voteId: Long = 41L,
        status: ChatVoteStatus = ChatVoteStatus.OPEN,
        closedAt: LocalDateTime? = null,
        myVote: MyVoteResponse? = MyVoteResponse(placeIds = listOf(301L), timeIds = listOf(311L)),
    ) = ChatVoteDetailResponse(
        voteId = voteId,
        roomId = 87L,
        status = status,
        allowMultiple = false,
        createdBy = 12L,
        createdAt = LocalDateTime.of(2026, 3, 24, 21, 3, 11),
        closedAt = closedAt,
        totalMembers = 4,
        votedCount = 2,
        placeOptions = listOf(
            PlaceOptionResponse(
                optionId = 301L,
                label = "강남역 스타벅스",
                address = "서울 강남구 테헤란로 231",
                mapLink = "http://place.map.kakao.com/26338954",
                latitude = 37.4979,
                longitude = 127.0276,
                voterIds = listOf(12L, 33L),
            ),
            PlaceOptionResponse(
                optionId = 302L, label = "홍대 카페거리", address = null, mapLink = null,
                latitude = null, longitude = null, voterIds = emptyList(),
            ),
        ),
        timeOptions = listOf(
            TimeOptionResponse(optionId = 311L, meetAt = LocalDateTime.of(2026, 3, 26, 19, 0), voterIds = listOf(12L)),
        ),
        myVote = myVote,
    )

    private fun sampleSystemMessage(content: String) = ChatMessageResponse(
        id = 900L,
        roomId = 87L,
        senderId = 12L,
        messageType = ChatMessageType.SYSTEM,
        content = content,
        imageUrl = null,
        createdAt = LocalDateTime.of(2026, 3, 24, 21, 3, 12),
    )

    private fun detailResponseFields(prefix: String) = arrayOf(
        fieldWithPath("${prefix}voteId").description("투표 ID"),
        fieldWithPath("${prefix}roomId").description("채팅방 ID"),
        fieldWithPath("${prefix}status").description("상태 (OPEN, CLOSED)"),
        fieldWithPath("${prefix}allowMultiple").description("복수 선택 허용 여부 (장소·시간 공통)"),
        fieldWithPath("${prefix}createdBy").description("투표를 만든 회원 ID"),
        fieldWithPath("${prefix}createdAt").description("생성일시"),
        fieldWithPath("${prefix}closedAt").description("마감 시각 (진행 중이면 null)").optional(),
        fieldWithPath("${prefix}totalMembers").description("방의 활성(이탈하지 않은) 멤버 수 — 진행 카운터의 분모"),
        fieldWithPath("${prefix}votedCount").description("장소·시간 중 하나라도 표를 던진 활성 멤버 수"),
        fieldWithPath("${prefix}placeOptions[]").description("장소 선택지 (입력 순 — 동표 노출 순서)"),
        fieldWithPath("${prefix}placeOptions[].optionId").description("선택지 ID"),
        fieldWithPath("${prefix}placeOptions[].label").description("상호명"),
        fieldWithPath("${prefix}placeOptions[].address").description("도로명 주소 (직접 입력이면 null)").optional(),
        fieldWithPath("${prefix}placeOptions[].mapLink").description("카카오맵 URL (직접 입력이면 null)").optional(),
        fieldWithPath("${prefix}placeOptions[].latitude").description("위도 (직접 입력이면 null)").optional(),
        fieldWithPath("${prefix}placeOptions[].longitude").description("경도 (직접 입력이면 null)").optional(),
        fieldWithPath("${prefix}placeOptions[].voterIds[]").description("이 선택지에 투표한 회원 ID 목록 (실명 공개)"),
        fieldWithPath("${prefix}timeOptions[]").description("시간 선택지 (입력 순)"),
        fieldWithPath("${prefix}timeOptions[].optionId").description("선택지 ID"),
        fieldWithPath("${prefix}timeOptions[].meetAt").description("만날 일시 (표시 문구는 클라이언트가 만든다)"),
        fieldWithPath("${prefix}timeOptions[].voterIds[]").description("이 선택지에 투표한 회원 ID 목록"),
        fieldWithPath("${prefix}myVote").description("내 표 (한 표도 없으면 null)").optional(),
        // 생성 응답은 myVote 가 항상 null 이라 타입을 명시해야 스키마에 실린다(#140 규칙).
        fieldWithPath("${prefix}myVote.placeIds[]").type(JsonFieldType.ARRAY)
            .description("내가 고른 장소 선택지 ID").optional(),
        fieldWithPath("${prefix}myVote.timeIds[]").type(JsonFieldType.ARRAY)
            .description("내가 고른 시간 선택지 ID").optional(),
    )

    @Test
    @DisplayName("그룹 방에 만남 투표를 만든다")
    fun createVote() {
        every { chatVoteService.createVote(any(), any(), any()) } returns ChatVoteChangeResult(
            detail = sampleDetail(myVote = null).copy(votedCount = 0),
            systemMessage = sampleSystemMessage("VOTE_CREATED:41"),
        )

        val request = ChatVoteCreateRequest(
            allowMultiple = false,
            placeOptions = listOf(
                PlaceOptionRequest(
                    label = "강남역 스타벅스", address = "서울 강남구 테헤란로 231",
                    mapLink = "http://place.map.kakao.com/26338954", latitude = 37.4979, longitude = 127.0276,
                ),
                PlaceOptionRequest(label = "홍대 카페거리"),
            ),
            timeOptions = listOf(
                TimeOptionRequest(meetAt = LocalDateTime.of(2026, 3, 26, 19, 0)),
                TimeOptionRequest(meetAt = LocalDateTime.of(2026, 3, 27, 18, 0)),
            ),
        )

        mockMvc.perform(
            post("/api/v1/chat/rooms/{roomId}/votes", 87L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.voteId").value(41L))
            .andDo { verify(exactly = 1) { messagingTemplate.convertAndSend("/sub/chat/rooms/87", any<Any>()) } }
            .andDo(
                document(
                    "chat-vote-create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("roomId").description("채팅방 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("만남 투표 생성")
                            .description(
                                "그룹 방에서 만날 장소·시간을 정하는 투표를 만듭니다. 방당 진행 중인 투표는 하나이며 " +
                                    "이미 있으면 8202 로 거부합니다. 장소·시간 선택지는 각각 2~10개이고, " +
                                    "같은 상호명·같은 일시(분 단위)는 한 투표 안에서 중복될 수 없습니다(8205). " +
                                    "그룹이 아닌 방은 8208, 나간 방은 7002 로 거부합니다.",
                            )
                            .pathParameters(parameterWithName("roomId").description("채팅방 ID"))
                            .requestFields(
                                fieldWithPath("allowMultiple").description("복수 선택 허용 여부 (기본 false, 생성 후 변경 불가)"),
                                fieldWithPath("placeOptions[]").description("장소 선택지 (2~10개)"),
                                fieldWithPath("placeOptions[].label").description("상호명 (최대 100자)"),
                                fieldWithPath("placeOptions[].address").description("도로명 주소 (선택)").optional(),
                                fieldWithPath("placeOptions[].mapLink").description("카카오맵 URL (선택)").optional(),
                                fieldWithPath("placeOptions[].latitude").description("위도 (선택)").optional(),
                                fieldWithPath("placeOptions[].longitude").description("경도 (선택)").optional(),
                                fieldWithPath("timeOptions[]").description("시간 선택지 (2~10개)"),
                                fieldWithPath("timeOptions[].meetAt").description("만날 일시 (yyyy-MM-dd HH:mm:ss, 초 이하는 버린다)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                *detailResponseFields("data."),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("방의 투표 목록을 최신순으로 조회한다")
    fun getVotes() {
        every { chatVoteService.getVotes(any(), any()) } returns listOf(
            sampleDetail(),
            // 마감된 투표를 하나 섞는다 — closedAt 이 전부 null 이면 스키마에서 그 필드가 빠진다(#140).
            sampleDetail(voteId = 40L, status = ChatVoteStatus.CLOSED, closedAt = LocalDateTime.of(2026, 3, 20, 21, 0)),
        )

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/votes", 87L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andDo(
                document(
                    "chat-vote-list",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("roomId").description("채팅방 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("방의 투표 목록")
                            .description(
                                "방의 투표를 최신순으로 조회합니다. 재접속 후 배너의 열린 투표를 되찾는 복구 경로입니다. " +
                                    "종료된 방·이미 나간 방에서도 조회할 수 있습니다(읽기 전용).",
                            )
                            .pathParameters(parameterWithName("roomId").description("채팅방 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                *detailResponseFields("data[]."),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("투표에 표를 던진다 — 재투표도 같은 경로다")
    fun cast() {
        every { chatVoteService.cast(any(), any(), any(), any()) } returns sampleDetail()

        val request = ChatVoteCastRequest(placeIds = listOf(301L), timeIds = listOf(311L))

        mockMvc.perform(
            post("/api/v1/chat/rooms/{roomId}/votes/{voteId}/cast", 87L, 41L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.myVote.placeIds[0]").value(301L))
            .andDo(
                document(
                    "chat-vote-cast",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                        parameterWithName("voteId").description("투표 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("투표하기")
                            .description(
                                "요청에 담긴 집합이 내 최종 선택입니다(치환) — 재투표도 같은 경로이며 " +
                                    "빈 배열은 해당 유형의 표 취소입니다. 응답은 갱신된 상세라 재조회가 필요 없습니다. " +
                                    "복수 선택이 꺼진 투표에 유형별 2개 이상이면 8207, 이 투표의 선택지가 아니면 8206, " +
                                    "마감된 투표면 8203 으로 거부합니다.",
                            )
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                                parameterWithName("voteId").description("투표 ID"),
                            )
                            .requestFields(
                                fieldWithPath("placeIds[]").description("선택한 장소 선택지 ID 목록 (빈 배열 = 장소 표 취소)"),
                                fieldWithPath("timeIds[]").description("선택한 시간 선택지 ID 목록 (빈 배열 = 시간 표 취소)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                *detailResponseFields("data."),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("투표를 마감한다 — 멱등, 실제로 닫은 요청만 브로드캐스트")
    fun close() {
        every { chatVoteService.close(any(), any(), any(), any()) } returns ChatVoteChangeResult(
            detail = sampleDetail(status = ChatVoteStatus.CLOSED, closedAt = LocalDateTime.of(2026, 3, 26, 21, 0)),
            systemMessage = sampleSystemMessage("VOTE_CLOSED:41"),
        )

        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/votes/{voteId}/close", 87L, 41L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CLOSED"))
            .andDo(
                document(
                    "chat-vote-close",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                        parameterWithName("voteId").description("투표 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("투표 마감")
                            .description(
                                "투표를 마감합니다. 권한은 방 멤버 누구나이며, 마감하면 표를 던질 수 없습니다. " +
                                    "이미 마감된 투표에 다시 요청해도 성공으로 답합니다(멱등). " +
                                    "마감 SYSTEM 메시지(content=VOTE_CLOSED:{voteId})는 실제로 닫은 요청만 발행합니다.",
                            )
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                                parameterWithName("voteId").description("투표 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                *detailResponseFields("data."),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("투표 상세를 조회한다")
    fun getVote() {
        every { chatVoteService.getVote(any(), any(), any()) } returns sampleDetail()

        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/votes/{voteId}", 87L, 41L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.voteId").value(41L))
            .andDo(
                document(
                    "chat-vote-detail",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                        parameterWithName("voteId").description("투표 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("투표 상세")
                            .description(
                                "선택지별 투표자(실명 공개)와 내 표를 담은 상세입니다. " +
                                    "서버는 승자·비율을 계산하지 않습니다 — 1위·동표 판정은 voterIds 와 " +
                                    "입력 순 배열로 클라이언트가 합니다. 이탈한 멤버의 표는 집계에서 빠집니다.",
                            )
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                                parameterWithName("voteId").description("투표 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                *detailResponseFields("data."),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
