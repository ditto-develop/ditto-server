package com.ditto.api.support

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@ActiveProfiles("test")
@ExtendWith(JunitDatabaseCleanExtension::class)
abstract class RestDocsTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    lateinit var memberRepository: MemberRepository

    protected val objectMapper: ObjectMapper = ObjectMapperFactory.create()

    protected fun MockHttpServletRequestBuilder.withApiKey(): MockHttpServletRequestBuilder {
        return this.header("X-API-Key", TEST_API_KEY)
    }

    /**
     * ACTIVE 회원을 새로 만들어 그 회원의 Bearer 토큰을 헤더에 추가한다.
     * JWT 필터가 회원 status를 조회하므로, 인증이 필요한 테스트는 ACTIVE 회원이 있어야 한다.
     */
    protected fun MockHttpServletRequestBuilder.withBearerToken(): MockHttpServletRequestBuilder {
        val member = memberRepository.save(Member(nickname = "auth-${memberSeq.incrementAndGet()}").apply { activate() })
        return withBearerToken(member.id)
    }

    /** 지정한 회원 id의 Bearer 토큰을 헤더에 추가한다. */
    protected fun MockHttpServletRequestBuilder.withBearerToken(memberId: Long): MockHttpServletRequestBuilder {
        return this.header("Authorization", "Bearer ${jwtTokenProvider.generateAccessToken(memberId)}")
    }

    companion object {
        const val TEST_API_KEY = "test-api-key"
        private val memberSeq = AtomicLong(0)
    }
}
