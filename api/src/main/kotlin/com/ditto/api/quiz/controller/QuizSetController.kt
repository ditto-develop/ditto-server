package com.ditto.api.quiz.controller

import com.ditto.api.quiz.dto.CurrentWeekQuizSetsResponse
import com.ditto.api.quiz.dto.QuizSetResponse
import com.ditto.api.quiz.service.QuizSetService
import com.ditto.common.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class QuizSetController(
    private val quizSetService: QuizSetService,
) {
    @GetMapping("/api/v1/quiz-sets/current-week")
    fun getCurrentWeek(): ApiResponse<CurrentWeekQuizSetsResponse> {
        return ApiResponse.ok(quizSetService.getCurrentWeekQuizSets())
    }

    @GetMapping("/api/v1/quiz-sets/{id}")
    fun getQuizSet(@PathVariable id: Long): ApiResponse<QuizSetResponse> {
        return ApiResponse.ok(quizSetService.getQuizSet(id))
    }
}
