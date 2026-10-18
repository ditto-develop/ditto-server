package com.ditto.api.admin.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AdminWebMvcConfig(
    private val qaToolsSwitchInterceptor: QaToolsSwitchInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(qaToolsSwitchInterceptor)
            .addPathPatterns(QaToolsSwitchInterceptor.QA_TOOL_PATHS)
    }
}
