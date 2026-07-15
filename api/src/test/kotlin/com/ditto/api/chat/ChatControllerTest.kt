package com.ditto.api.chat

import com.ditto.api.chat.controller.ChatController
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatReadRequest
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.api.chat.service.ChatService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomType
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class ChatControllerTest : ControllerUnitTest() {

    private val chatService: ChatService = mockk()

    override val controller = ChatController(chatService)

    private fun sampleMessage(id: Long = 3L) = ChatMessageResponse(
        id = id,
        roomId = 1L,
        senderId = 2L,
        messageType = ChatMessageType.TEXT,
        content = "안녕하세요",
        createdAt = LocalDateTime.of(2026, 7, 15, 12, 0),
    )

    @Test
    @DisplayName("내 채팅방 목록을 조회한다")
    fun getMyRooms() {
        every { chatService.getMyRooms(any()) } returns listOf(
            ChatRoomResponse(
                roomId = 1L,
                roomType = ChatRoomType.PERSONAL,
                counterpartMemberId = 2L,
                lastMessage = sampleMessage(),
                unreadCount = 2L,
                createdAt = LocalDateTime.of(2026, 7, 15, 11, 0),
            ),
        )

        mockMvc.perform(get("/api/v1/chat/rooms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].roomId").value(1L))
            .andExpect(jsonPath("$.data[0].unreadCount").value(2L))
            .andDo(
                document(
                    "chat-rooms",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("내 채팅방 목록")
                            .description("내가 참여한 채팅방 목록을 최근 대화순으로 조회합니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data[].roomId").description("채팅방 ID"),
                                fieldWithPath("data[].roomType").description("채팅방 유형 (PERSONAL, GROUP)"),
                                fieldWithPath("data[].counterpartMemberId").description("상대 회원 ID (그룹이면 null)").optional(),
                                fieldWithPath("data[].lastMessage").description("마지막 메시지 (없으면 null)").optional(),
                                fieldWithPath("data[].lastMessage.id").description("메시지 ID").optional(),
                                fieldWithPath("data[].lastMessage.roomId").description("채팅방 ID").optional(),
                                fieldWithPath("data[].lastMessage.senderId").description("보낸 회원 ID").optional(),
                                fieldWithPath("data[].lastMessage.messageType").description("메시지 유형").optional(),
                                fieldWithPath("data[].lastMessage.content").description("메시지 내용").optional(),
                                fieldWithPath("data[].lastMessage.createdAt").description("메시지 생성일시").optional(),
                                fieldWithPath("data[].unreadCount").description("안읽음 수"),
                                fieldWithPath("data[].createdAt").description("채팅방 생성일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("채팅방의 과거 메시지를 커서로 조회한다")
    fun getMessages() {
        every { chatService.getMessages(any(), any(), any(), any()) } returns ChatMessagesResponse(
            messages = listOf(sampleMessage(id = 3L), sampleMessage(id = 2L)),
            nextCursor = 2L,
        )

        mockMvc.perform(
            get("/api/v1/chat/rooms/{roomId}/messages", 1L)
                .param("cursor", "5")
                .param("size", "2"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.nextCursor").value(2L))
            .andDo(
                document(
                    "chat-messages",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                    ),
                    queryParameters(
                        parameterWithName("cursor").description("이 메시지 ID 미만(더 과거)을 조회. 최초 조회 시 생략").optional(),
                        parameterWithName("size").description("조회 개수 (기본 30, 최대 100)").optional(),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("과거 메시지 조회")
                            .description("채팅방 메시지를 최신순으로 조회합니다. cursor 미만(더 과거)으로 size 개. 응답의 nextCursor 로 위로 스크롤 페이징합니다.")
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                            )
                            .queryParameters(
                                parameterWithName("cursor").description("이 메시지 ID 미만(더 과거)을 조회. 최초 조회 시 생략").optional(),
                                parameterWithName("size").description("조회 개수 (기본 30, 최대 100)").optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.messages[].id").description("메시지 ID"),
                                fieldWithPath("data.messages[].roomId").description("채팅방 ID"),
                                fieldWithPath("data.messages[].senderId").description("보낸 회원 ID"),
                                fieldWithPath("data.messages[].messageType").description("메시지 유형"),
                                fieldWithPath("data.messages[].content").description("메시지 내용"),
                                fieldWithPath("data.messages[].createdAt").description("메시지 생성일시"),
                                fieldWithPath("data.nextCursor").description("다음 페이지 커서 (더 없으면 null)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("채팅방을 읽음 처리한다")
    fun read() {
        justRun { chatService.markAsRead(any(), any(), any()) }

        mockMvc.perform(
            post("/api/v1/chat/rooms/{roomId}/read", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChatReadRequest(lastReadMessageId = 10L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "chat-read",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("읽음 처리")
                            .description("채팅방의 읽음 위치를 lastReadMessageId 까지 전진시킵니다.")
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                            )
                            .requestFields(
                                fieldWithPath("lastReadMessageId").description("마지막으로 읽은 메시지 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 데이터 (없음)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
