package com.ditto.api.notification

import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName as queryParameterWithName
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class NotificationControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Test
    @DisplayName("알림 목록을 최신순으로 조회한다")
    fun getNotifications() {
        val member = saveMember("알림조회회원")
        // 읽은 알림을 하나 섞는다 — readAt 이 전부 null 이면 스키마에서 그 필드가 빠진다.
        val read = save(member.id, NotificationType.MATCH_RESULT, "이번 주 매칭 결과가 나왔어요", targetId = 11L)
        read.markRead(LocalDateTime.of(2026, 8, 21, 9, 0))
        notificationRepository.save(read)
        save(member.id, NotificationType.CHAT_MESSAGE, "산책러버님의 새 메시지", targetId = 22L)

        mockMvc.perform(
            get("/api/v1/notifications")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications.length()").value(2))
            // 최신순 — 나중에 저장한 알림이 앞에 온다.
            .andExpect(jsonPath("$.data.notifications[0].title").value("산책러버님의 새 메시지"))
            .andExpect(jsonPath("$.data.notifications[0].category").value("CHAT"))
            .andExpect(jsonPath("$.data.notifications[0].readAt").isEmpty)
            .andExpect(jsonPath("$.data.nextCursor").isEmpty)
            .andDo(
                document(
                    "notifications-get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("알림 목록 조회")
                            .description(
                                "알림 센터의 목록을 최신순으로 조회합니다. 보관 기간(30일)이 지난 알림은 조회되지 않습니다. " +
                                    "category 를 생략하면 '전체' 탭입니다. " +
                                    "안읽음 여부는 readAt 이 null 인지로 판단하세요. " +
                                    "nextCursor 가 null 이면 마지막 페이지입니다.",
                            )
                            .queryParameters(
                                queryParameterWithName("category")
                                    .description("필터 (MATCHING·CHAT·SYSTEM). 생략 시 전체").optional(),
                                queryParameterWithName("cursor")
                                    .description("이전 응답의 nextCursor. 생략 시 첫 페이지").optional(),
                                queryParameterWithName("size").description("페이지 크기 (기본 20, 최대 100)").optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.notifications[].id").description("알림 ID"),
                                fieldWithPath("data.notifications[].type")
                                    .description(
                                        "알림 유형 (MATCH_RESULT, GROUP_FORMED, REMATCH_MATCHED, " +
                                            "REVIEW_REQUEST, CHAT_MESSAGE, CHAT_ENDING_SOON, SYSTEM_NOTICE)",
                                    ),
                                fieldWithPath("data.notifications[].category")
                                    .description("필터 카테고리 (MATCHING, CHAT, SYSTEM)"),
                                fieldWithPath("data.notifications[].title").description("제목"),
                                fieldWithPath("data.notifications[].body").description("본문").optional(),
                                fieldWithPath("data.notifications[].targetId")
                                    .description("이동 대상 ID. 무엇을 가리키는지는 type 이 정한다").optional(),
                                fieldWithPath("data.notifications[].readAt")
                                    .description("읽은 시각. null 이면 안읽음").optional(),
                                fieldWithPath("data.notifications[].createdAt")
                                    .description("발생 시각. 상대시간 표기는 클라이언트가 만든다"),
                                fieldWithPath("data.nextCursor")
                                    .description("다음 페이지 커서. null 이면 마지막 페이지").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("카테고리 필터는 그 카테고리의 알림만 준다")
    fun getNotificationsByCategory() {
        val member = saveMember("카테고리필터회원")
        save(member.id, NotificationType.MATCH_RESULT, "이번 주 매칭 결과가 나왔어요", targetId = 11L)
        save(member.id, NotificationType.CHAT_ENDING_SOON, "채팅이 6시간 후 종료돼요", targetId = 22L)

        mockMvc.perform(
            get("/api/v1/notifications")
                .withApiKey()
                .withBearerToken(member.id)
                .param("category", NotificationCategory.CHAT.name)
                .param("size", "10"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications.length()").value(1))
            .andExpect(jsonPath("$.data.notifications[0].type").value(NotificationType.CHAT_ENDING_SOON.name))
    }

    @Test
    @DisplayName("페이지를 채우면 다음 커서를 준다")
    fun getNotificationsWithCursor() {
        val member = saveMember("커서회원")
        val older = save(member.id, NotificationType.MATCH_RESULT, "과거 알림", targetId = 1L)
        val newer = save(member.id, NotificationType.MATCH_RESULT, "최신 알림", targetId = 2L)

        mockMvc.perform(
            get("/api/v1/notifications")
                .withApiKey()
                .withBearerToken(member.id)
                .param("size", "1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications[0].id").value(newer.id))
            .andExpect(jsonPath("$.data.nextCursor").value(newer.id))

        mockMvc.perform(
            get("/api/v1/notifications")
                .withApiKey()
                .withBearerToken(member.id)
                .param("cursor", newer.id.toString())
                .param("size", "1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notifications[0].id").value(older.id))
    }

    @Test
    @DisplayName("미읽음 수를 조회한다 — 홈 헤더 배지용")
    fun getUnreadCount() {
        val member = saveMember("배지회원")
        save(member.id, NotificationType.MATCH_RESULT, "안읽은 알림", targetId = 1L)
        val read = save(member.id, NotificationType.MATCH_RESULT, "읽은 알림", targetId = 2L)
        read.markRead(LocalDateTime.now())
        notificationRepository.save(read)

        mockMvc.perform(
            get("/api/v1/notifications/unread-count")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.count").value(1))
            .andDo(
                document(
                    "notifications-unread-count",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("미읽음 알림 수 조회")
                            .description(
                                "홈 헤더 벨 배지용입니다. 목록과 같은 창(최근 30일)의 안읽은 알림만 셉니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.count").description("안읽은 알림 수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("알림을 읽음으로 표시한다 — 두 번 요청해도 성공한다")
    fun readNotification() {
        val member = saveMember("읽음회원")
        val notification = save(member.id, NotificationType.REVIEW_REQUEST, "이번 만남은 어떠셨나요?", targetId = 1L)

        repeat(2) {
            mockMvc.perform(
                put("/api/v1/notifications/{id}/read", notification.id)
                    .withApiKey()
                    .withBearerToken(member.id),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }

        check(notificationRepository.findByIdAndMemberId(notification.id, member.id)!!.isRead) {
            "읽음 처리되지 않았다"
        }

        mockMvc.perform(
            put("/api/v1/notifications/{id}/read", notification.id)
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "notifications-read",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("알림 읽음")
                            .description(
                                "알림 하나를 읽음으로 표시합니다. 이미 읽은 알림에 다시 요청해도 성공합니다(멱등). " +
                                    "내 알림이 아니면 404 로 응답합니다.",
                            )
                            .pathParameters(parameterWithName("id").description("알림 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 본문 없음").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    fun readOthersNotificationRejected() {
        val member = saveMember("요청회원")
        val other = saveMember("남의알림주인")
        val notification = save(other.id, NotificationType.MATCH_RESULT, "남의 알림", targetId = 1L)

        mockMvc.perform(
            put("/api/v1/notifications/{id}/read", notification.id)
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(404))

        check(!notificationRepository.findByIdAndMemberId(notification.id, other.id)!!.isRead) {
            "남의 알림이 읽음 처리됐다"
        }
    }

    @Test
    @DisplayName("모두 읽음 — 읽음 처리된 건수를 준다")
    fun readAllNotifications() {
        val member = saveMember("모두읽음회원")
        save(member.id, NotificationType.MATCH_RESULT, "알림1", targetId = 1L)
        save(member.id, NotificationType.CHAT_MESSAGE, "알림2", targetId = 2L)

        mockMvc.perform(
            put("/api/v1/notifications/read-all")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.readCount").value(2))
            .andDo(
                document(
                    "notifications-read-all",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("알림 모두 읽음")
                            .description(
                                "안읽은 알림을 모두 읽음으로 표시합니다(화면 우상단 '모두 읽음'). " +
                                    "이미 다 읽었으면 readCount 가 0 이며 성공합니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.readCount").description("이번 호출로 읽음이 된 건수"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )

        // 두 번째 호출은 바꿀 것이 없다.
        mockMvc.perform(
            put("/api/v1/notifications/read-all")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.readCount").value(0))
    }

    private fun save(
        memberId: Long,
        type: NotificationType,
        title: String,
        targetId: Long?,
    ): Notification = notificationRepository.save(
        NotificationFixture.create(memberId = memberId, type = type, title = title, targetId = targetId),
    )

    private fun saveMember(nickname: String): Member = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            email = "$nickname@example.com",
            status = MemberStatus.ACTIVE,
        ),
    )
}
