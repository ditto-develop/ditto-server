package com.ditto.api.chat

import com.ditto.api.chat.controller.ChatController
import com.ditto.api.chat.dto.ChatImageUploadFileRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlResponse
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsResponse
import com.ditto.api.chat.dto.ChatLeaveResult
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatReadRequest
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.chat.service.ChatService
import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.review.service.EndedChatReviewOpener
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
import org.springframework.messaging.simp.SimpMessagingTemplate
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
    private val chatRoomEndService: ChatRoomEndService = mockk()
    private val messagingTemplate: SimpMessagingTemplate = mockk(relaxed = true)
    private val endedChatReviewOpener: EndedChatReviewOpener = mockk(relaxed = true)
    private val reviewRequestNotifier: ReviewRequestNotifier = mockk(relaxed = true)

    override val controller = ChatController(
        chatService,
        chatRoomEndService,
        messagingTemplate,
        endedChatReviewOpener,
        reviewRequestNotifier,
    )

    private fun sampleMessage(id: Long = 3L, imageUrl: String? = null) = ChatMessageResponse(
        id = id,
        roomId = 1L,
        senderId = 2L,
        messageType = ChatMessageType.TEXT,
        content = "안녕하세요",
        imageUrl = imageUrl,
        createdAt = LocalDateTime.of(2026, 7, 25, 12, 0),
    )

    @Test
    @DisplayName("내 채팅방 목록을 조회한다")
    fun getMyRooms() {
        every { chatService.getMyRooms(any()) } returns listOf(
            ChatRoomResponse(
                roomId = 1L,
                sourceType = ChatRoomType.PERSONAL,
                counterpartMemberIds = listOf(2L),
                lastMessage = sampleMessage(),
                unreadCount = 2L,
                createdAt = LocalDateTime.of(2026, 7, 25, 11, 0),
                opensAt = LocalDateTime.of(2026, 7, 24, 0, 0),
                expiresAt = LocalDateTime.of(2026, 7, 27, 0, 0),
                isEnded = false,
                endedAt = null,
                endedReason = null,
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
                                fieldWithPath("data[].sourceType").description("채팅방 유형 (PERSONAL, GROUP)"),
                                fieldWithPath("data[].counterpartMemberIds[]").description("나를 제외한 참여 회원 ID 목록 (1:1이면 1명)"),
                                fieldWithPath("data[].lastMessage").description("마지막 메시지 (없으면 null)").optional(),
                                fieldWithPath("data[].lastMessage.id").description("메시지 ID").optional(),
                                fieldWithPath("data[].lastMessage.roomId").description("채팅방 ID").optional(),
                                fieldWithPath("data[].lastMessage.senderId").description("보낸 회원 ID").optional(),
                                fieldWithPath("data[].lastMessage.messageType").description("메시지 유형").optional(),
                                fieldWithPath("data[].lastMessage.content").description("메시지 내용 (IMAGE 는 S3 key)").optional(),
                                fieldWithPath("data[].lastMessage.imageUrl").description("IMAGE 열람용 presigned URL (아니면 null)").optional(),
                                fieldWithPath("data[].lastMessage.createdAt").description("메시지 생성일시").optional(),
                                fieldWithPath("data[].unreadCount").description("안읽음 수"),
                                fieldWithPath("data[].createdAt").description("채팅방 생성일시"),
                                fieldWithPath("data[].opensAt").description("채팅 개방 시각 (금요일 00:00)"),
                                fieldWithPath("data[].expiresAt").description("자동 종료 예정 시각 (월요일 00:00). 남은 시간 카운트다운 기준"),
                                fieldWithPath("data[].isEnded").description("종료 여부. true 면 전송·구독이 막히고 조회만 가능"),
                                fieldWithPath("data[].endedAt").description("실제 종료 시각 (종료 전 null)").optional(),
                                fieldWithPath("data[].endedReason")
                                    .description("종료 사유 EXPIRED(기한 만료) / USER_ENDED(참여자 종료). 종료 전 null. 누가 종료했는지는 대화의 SYSTEM 메시지(content=USER_LEFT) senderId 로 확인")
                                    .optional(),
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
                                fieldWithPath("data.messages[].messageType").description("메시지 유형 (TEXT, IMAGE, SYSTEM)"),
                                fieldWithPath("data.messages[].content").description("메시지 내용 (IMAGE 는 S3 key)"),
                                fieldWithPath("data.messages[].imageUrl").description("IMAGE 열람용 presigned URL (아니면 null)").optional(),
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

    @Test
    @DisplayName("1:1 채팅을 종료한다")
    fun end() {
        every { chatRoomEndService.endByUser(any(), any(), any()) } returns sampleMessage()

        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/end", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "chat-end",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("채팅 종료")
                            .description(
                                "1:1 채팅을 종료합니다. 종료되면 전송·구독이 막히고 평가가 열립니다. " +
                                    "이미 종료된 방에 다시 요청해도 성공으로 답합니다(재시도 대비). " +
                                    "누가 종료했는지는 대화에 남는 SYSTEM 메시지(content=USER_LEFT)의 senderId 로 확인합니다.",
                            )
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
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

    @Test
    @DisplayName("채팅방에서 나간다")
    fun leave() {
        every { chatRoomEndService.leave(any(), any(), any()) } returns ChatLeaveResult(
            systemMessages = listOf(sampleMessage()),
            roomEnded = false,
        )

        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/leave", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "chat-leave",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("채팅방 나가기")
                            .description(
                                "그룹 방에서 나만 빠지고 방은 남은 인원으로 유지됩니다. " +
                                    "나간 사실은 SYSTEM 메시지(content=MEMBER_LEFT, senderId=나간 회원)로 브로드캐스트됩니다. " +
                                    "이탈로 잔여 인원이 1명이 되면 방이 해체되고(endedReason=INSUFFICIENT_MEMBERS) " +
                                    "SYSTEM 메시지(content=INSUFFICIENT_MEMBERS)가 한 건 더 발행됩니다. " +
                                    "두 사람 방(1:1·재매칭)은 종료(end)와 동일하게 처리됩니다(USER_LEFT). " +
                                    "이미 나갔거나 끝난 방에 다시 요청해도 성공으로 답합니다(멱등).",
                            )
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
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

    @Test
    @DisplayName("이미지 전송용 업로드 URL을 발급한다")
    fun issueImageUploadUrls() {
        every { chatService.issueImageUploadUrls(any(), any(), any()) } returns ChatImageUploadUrlsResponse(
            uploads = listOf(
                ChatImageUploadUrlResponse(
                    objectKey = "chat/2/abc-uuid",
                    uploadUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/chat/2/abc-uuid?sig=...",
                ),
            ),
        )

        mockMvc.perform(
            post("/api/v1/chat/rooms/{roomId}/image-upload-urls", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChatImageUploadUrlsRequest(
                            files = listOf(ChatImageUploadFileRequest(contentType = "image/jpeg", contentLength = 20480)),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.uploads[0].objectKey").value("chat/2/abc-uuid"))
            .andDo(
                document(
                    "chat-image-upload-urls",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("roomId").description("채팅방 ID"),
                    ),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Chat")
                            .summary("이미지 업로드 URL 발급")
                            .description("이미지 전송용 presigned PUT URL을 발급합니다. 업로드 후 messageType=IMAGE, content=objectKey 로 전송합니다. 방 멤버만 호출 가능합니다.")
                            .pathParameters(
                                parameterWithName("roomId").description("채팅방 ID"),
                            )
                            .requestFields(
                                fieldWithPath("files[].contentType").description("이미지 MIME 타입 (image/* 만 허용)"),
                                fieldWithPath("files[].contentLength").description("이미지 바이트 크기 (최대 10MB)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.uploads[].objectKey").description("업로드 후 메시지 content 로 전송할 S3 key"),
                                fieldWithPath("data.uploads[].uploadUrl").description("presigned PUT 업로드 URL"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
