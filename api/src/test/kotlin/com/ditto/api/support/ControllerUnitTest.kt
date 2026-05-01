package com.ditto.api.support

import com.ditto.api.config.exception.GlobalExceptionHandler
import com.ditto.common.serialization.ObjectMapperFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

/**
 * 컨트롤러 단위 테스트 베이스. Spring 컨텍스트를 띄우지 않고 standalone MockMvc 로
 * 컨트롤러만 격리해 검증한다. 하위 레이어(service 등)는 자식 클래스에서 mockk 으로 모킹한다.
 *
 * 사용 예:
 * ```
 * class FooControllerTest : ControllerUnitTest() {
 *     private val fooService: FooService = mockk()
 *     override val controller = FooController(fooService)
 * }
 * ```
 */
@ExtendWith(RestDocumentationExtension::class)
abstract class ControllerUnitTest {

    protected abstract val controller: Any

    protected val objectMapper: ObjectMapper = ObjectMapperFactory.create()

    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc(restDocumentation: RestDocumentationContextProvider) {
        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(FakeMemberPrincipalResolver())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .apply<StandaloneMockMvcBuilder>(documentationConfiguration(restDocumentation))
            .build()
    }
}
