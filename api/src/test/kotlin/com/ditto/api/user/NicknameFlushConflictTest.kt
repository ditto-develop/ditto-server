package com.ditto.api.user

import com.ditto.api.user.service.NicknameService
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.NicknameReservationRepository
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

/** 닉네임 저장 시점의 무결성 위반 중 닉네임 유일 제약이 아닌 것. 실제 입력으로는 만들 수 없어 mock 을 쓴다. */
class NicknameFlushConflictTest {

    private val memberRepository = mockk<MemberRepository>()
    private val nicknameService = NicknameService(memberRepository, mockk<NicknameReservationRepository>())

    @Test
    @DisplayName("닉네임 유일 제약이 아닌 무결성 위반은 바꾸지 않고 그대로 던진다")
    fun otherIntegrityViolationPropagates() {
        every { memberRepository.flush() } throws DataIntegrityViolationException("Duplicate entry for key 'other_unique'")

        shouldThrow<DataIntegrityViolationException> {
            nicknameService.flushAssigned()
        }
    }
}
