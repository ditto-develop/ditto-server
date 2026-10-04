package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 화면과 결과 메시지에 쓸 회원 이름표를 읽는다. 더미가 아니거나 없는 회원이어도 준다(막는 일은 [QaDummies]). */
@Component
@Transactional(readOnly = true)
class QaMemberLabels(
    private val memberRepository: MemberRepository,
) {
    fun load(memberIds: Collection<Long>): QaMembers = QaMembers(memberRepository.findAllById(memberIds.distinct()))

    fun one(memberId: Long): QaMember = load(listOf(memberId)).of(memberId)
}
