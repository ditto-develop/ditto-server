package com.ditto.api.admin.featureflag

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminFeatureFlagWebTest(
    private val mockMvc: MockMvc,
    private val featureFlagRepository: FeatureFlagRepository,
    private val featureFlags: FeatureFlags,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(5L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    "기능 플래그 화면에 정의된 플래그와 꺼짐 상태가 보인다" {
        mockMvc.perform(get("/admin/feature-flags").with(authentication(admin)))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("QUIZ_CHAT")))
            .andExpect(content().string(containsString("꺼짐")))
    }

    "켜기를 누르면 켜지고 바꾼 어드민이 남으며, 다른 서버 반영 시간을 안내한다" {
        mockMvc.perform(
            post("/admin/feature-flags/QUIZ_CHAT").param("enabled", "true").with(authentication(admin)).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/feature-flags"))
            .andExpect(flash().attribute("message", containsString("최대 1분 뒤 모든 서버에 적용")))

        val row = featureFlagRepository.findByKey(FeatureFlagKey.QUIZ_CHAT)!!
        row.enabled shouldBe true
        row.updatedByMemberId shouldBe 5L
        featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) shouldBe true
    }

    "켠 뒤에는 켜짐 배지와 바꾼 어드민(이름이 없으면 회원 번호)이 보인다" {
        mockMvc.perform(post("/admin/feature-flags/QUIZ_CHAT").param("enabled", "true").with(authentication(admin)).with(csrf()))

        mockMvc.perform(get("/admin/feature-flags").with(authentication(admin)))
            .andExpect(content().string(containsString("badge ok")))
            .andExpect(content().string(containsString("회원 #5")))
            .andExpect(content().string(containsString("을(를) 끕니다")))
    }

    "로그인하지 않으면 화면을 열 수 없다" {
        mockMvc.perform(get("/admin/feature-flags"))
            .andExpect(status().is3xxRedirection)
    }
})
