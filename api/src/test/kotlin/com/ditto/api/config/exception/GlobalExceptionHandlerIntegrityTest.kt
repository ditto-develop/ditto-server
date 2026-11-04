package com.ditto.api.config.exception

import com.ditto.common.exception.ErrorCode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.mock.web.MockHttpServletRequest

/** 무결성 위반 안전망. 가입·프로필 수정은 서비스가 먼저 바꾸므로, 그 밖의 경로가 여기까지 온다. */
class GlobalExceptionHandlerIntegrityTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest("POST", "/api/v1/users/social-login")

    @Test
    @DisplayName("닉네임 유일 제약 위반은 NICKNAME_ALREADY_EXISTS 로 응답한다")
    fun nicknameConflictAnsweredAsDuplicate() {
        val violation = DataIntegrityViolationException("Duplicate entry 'x' for key 'member.member_unique_1'")

        handler.handleDataIntegrityViolation(violation, request).error?.code shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS.code
    }

    @Test
    @DisplayName("그 밖의 무결성 위반은 서버 오류로 응답한다")
    fun otherViolationAnsweredAsInternalError() {
        val violation = DataIntegrityViolationException("Duplicate entry 'x' for key 'other_unique'")

        handler.handleDataIntegrityViolation(violation, request).error?.code shouldBe ErrorCode.INTERNAL_ERROR.code
    }
}
