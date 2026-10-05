package com.ditto.api.admin.member

import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.Limit
import java.util.Optional

class AdminMemberServiceTest : FreeSpec({
    "searchByEmail 은 공백을 제거하고 이메일 정확 일치로 조회한다" {
        val repository = mockk<MemberRepository>()
        every { repository.findByEmailOrderByIdAsc("a@b.com") } returns
            listOf(MemberFixture.create(email = "a@b.com", id = 1L))

        val result = AdminMemberService(repository).searchByEmail("  a@b.com ")

        result shouldHaveSize 1
        verify { repository.findByEmailOrderByIdAsc("a@b.com") }
    }

    "searchByEmail 은 공백뿐이면 조회 없이 빈 목록" {
        val repository = mockk<MemberRepository>()

        AdminMemberService(repository).searchByEmail("   ") shouldBe emptyList()

        verify(exactly = 0) { repository.findByEmailOrderByIdAsc(any()) }
    }

    "searchByNicknameOrId 는 #을 붙이면 회원 ID 정확 일치로 찾는다" {
        val repository = mockk<MemberRepository>()
        every { repository.findById(7L) } returns Optional.of(MemberFixture.create(nickname = "dummy-a", id = 7L))

        val result = AdminMemberService(repository).searchByNicknameOrId(" #7 ")

        result.map { it.memberId } shouldBe listOf(7L)
        result.single().isDummy shouldBe true
        verify(exactly = 0) { repository.findByNicknameContainingOrderByIdAsc(any(), any()) }
    }

    "searchByNicknameOrId 는 # 뒤가 숫자가 아니거나 없는 ID면 빈 목록" {
        val repository = mockk<MemberRepository>()
        every { repository.findById(99L) } returns Optional.empty()
        val service = AdminMemberService(repository)

        service.searchByNicknameOrId("#abc") shouldBe emptyList()
        service.searchByNicknameOrId("#99") shouldBe emptyList()
    }

    "searchByNicknameOrId 는 #이 없으면 닉네임 부분 일치를 정해진 수까지만 찾는다" {
        val repository = mockk<MemberRepository>()
        every { repository.findByNicknameContainingOrderByIdAsc("홍", Limit.of(AdminMemberService.SEARCH_LIMIT)) } returns
            listOf(MemberFixture.create(nickname = "홍길동", id = 1L))

        val result = AdminMemberService(repository).searchByNicknameOrId("홍")

        result.map { it.nickname } shouldBe listOf("홍길동")
        result.single().isDummy shouldBe false
    }

    "searchByNicknameOrId 는 공백뿐이면 조회 없이 빈 목록" {
        val repository = mockk<MemberRepository>()

        AdminMemberService(repository).searchByNicknameOrId("  ") shouldBe emptyList()

        verify(exactly = 0) { repository.findByNicknameContainingOrderByIdAsc(any(), any()) }
    }

    "changeRole 은 회원 권한을 변경한다" {
        val repository = mockk<MemberRepository>()
        val member = MemberFixture.create(role = MemberRole.USER, id = 1L)
        every { repository.findById(1L) } returns Optional.of(member)

        AdminMemberService(repository).changeRole(1L, MemberRole.ADMIN)

        member.role shouldBe MemberRole.ADMIN
    }

    "changeRole 은 없는 회원이면 예외" {
        val repository = mockk<MemberRepository>()
        every { repository.findById(99L) } returns Optional.empty()

        shouldThrow<WarnException> { AdminMemberService(repository).changeRole(99L, MemberRole.ADMIN) }
    }

    "listAdmins 는 ADMIN 권한 회원을 조회한다" {
        val repository = mockk<MemberRepository>()
        every { repository.findByRoleOrderByIdAsc(MemberRole.ADMIN) } returns
            listOf(MemberFixture.create(role = MemberRole.ADMIN, id = 1L))

        AdminMemberService(repository).listAdmins() shouldHaveSize 1
        verify { repository.findByRoleOrderByIdAsc(MemberRole.ADMIN) }
    }
})
