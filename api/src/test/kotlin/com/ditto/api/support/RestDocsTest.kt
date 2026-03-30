package com.ditto.api.support

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@ActiveProfiles("test")
abstract class RestDocsTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    protected val objectMapper: ObjectMapper = ObjectMapperFactory.create()

    protected fun MockHttpServletRequestBuilder.withApiKey(): MockHttpServletRequestBuilder {
        return this.header("X-API-Key", TEST_API_KEY)
    }

    protected fun MockHttpServletRequestBuilder.withBearerToken(): MockHttpServletRequestBuilder {
        val token = jwtTokenProvider.generateAccessToken("test-user", SocialProvider.KAKAO)
        return this.header("Authorization", "Bearer $token")
    }

    companion object {
        const val TEST_API_KEY = "test-api-key"
    }
}
