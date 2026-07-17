package com.ditto.api.admin.auth

import com.ditto.api.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * dev 로그인이 local 프로파일에만 존재하는지 검증하는 회귀 테스트.
 * [AdminDevLoginController]의 `@Profile("local")`이 실수로 지워지면 여기서 잡힌다.
 * 부모의 local 프로파일을 [ActiveProfiles.inheritProfiles] = false 로 걷어내고 test 단독으로 부트한다.
 */
@AutoConfigureMockMvc
@ActiveProfiles("test", inheritProfiles = false)
class AdminDevLoginProfileGateTest(
    private val mockMvc: MockMvc,
    private val applicationContext: ApplicationContext,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {
    "local 프로파일이 아니면" - {
        "dev 로그인 빈이 등록되지 않는다" {
            applicationContext.getBeanNamesForType(AdminDevLoginController::class.java).size shouldBe 0
        }

        "로그인 페이지에 로컬 개발 로그인 버튼이 노출되지 않는다" {
            mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("로컬 개발 로그인"))))
        }

        "dev 로그인 경로로 어드민 세션을 얻을 수 없다" {
            // 핸들러가 없어도 GlobalExceptionHandler 가 200 + ApiResponse 실패 바디로 바꾸므로, 404 대신 "세션 인증이 생기지 않음"을 검증한다.
            val result = mockMvc.perform(get("/admin/oauth/dev")).andReturn()
            result.response.redirectedUrl shouldBe null

            val session = result.request.session as MockHttpSession
            mockMvc.perform(get("/admin").session(session))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrlPattern("**/admin/login"))
        }
    }
})
