package com.ditto.api.intronote.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.intronote.dto.IntroNotesResponse
import com.ditto.api.intronote.dto.SaveIntroNoteRequest
import com.ditto.api.intronote.service.IntroNoteService
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class IntroNoteController(
    private val introNoteService: IntroNoteService,
) {

    @GetMapping("/api/v1/users/me/intro-notes")
    fun getMyIntroNotes(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<IntroNotesResponse> {
        val result = introNoteService.getMyIntroNotes(principal.memberId)
        return ApiResponse.ok(result)
    }

    @PutMapping("/api/v1/users/me/intro-notes/{questionCode}")
    fun saveIntroNote(
        @PathVariable questionCode: String,
        @Valid @RequestBody request: SaveIntroNoteRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<IntroNotesResponse> {
        val result = introNoteService.saveAnswer(principal.memberId, questionCode, request.answer)
        return ApiResponse.ok(result)
    }

    @GetMapping("/api/v1/users/{id}/intro-notes")
    fun getIntroNotes(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<IntroNotesResponse> {
        val result = introNoteService.getIntroNotes(principal.memberId, id)
        return ApiResponse.ok(result)
    }
}
