package com.ditto.api.admin.dummy.dto

import com.ditto.api.admin.dummy.DummyMemberFactory
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import java.io.Serializable

/** 더미 직접 만들기 폼(스프링 폼 바인딩). 비운 값은 무작위나 자동으로 채운다. */
class SingleDummyForm(
    var quizSetId: Long = 0L,
    /** `dummy-` 뒤에 붙일 부분. 비우면 자동으로 만든다. */
    var nicknameSuffix: String = "",
    var gender: Gender = Gender.MALE,
    /** 비어 오면 바인딩이 실패하지 않게 null 로 받아 서비스가 안내한다. */
    var age: Int? = DEFAULT_AGE,
    var location: Location = Location.SEOUL,
    var job: Job = Job.IT_TECH,
    var interests: MutableSet<Interest> = mutableSetOf(),
    /** 성별 아바타 번호(1~8). 비우면 무작위. */
    var avatarNumber: Int? = null,
    /** 문항 ID별로 지정한 선택지 ID. 값이 없는 문항은 무작위로 고른다. */
    var choiceIdByQuizId: MutableMap<Long, Long?> = mutableMapOf(),
    /** 문항 순서대로 앞에서 몇 개를 풀지. 비우면 전부 푼다. */
    var answeredCount: Int? = null,
) : Serializable {

    /**
     * 연달아 만들 때 다음 폼 값. 성별·나이·문항별 답·푼 문항 수는 이어 쓰고,
     * 닉네임은 비우고 사는 곳·직업·관심사·캐리커처는 다시 무작위로 채운다.
     */
    fun forNextDummy() = withRandomProfile(quizSetId).also {
        it.gender = gender
        it.age = age
        it.choiceIdByQuizId = choiceIdByQuizId.toMutableMap()
        it.answeredCount = answeredCount
    }

    companion object {
        private const val DEFAULT_AGE = 27

        fun withRandomProfile(quizSetId: Long) = SingleDummyForm(
            quizSetId = quizSetId,
            location = DummyMemberFactory.randomLocation(),
            job = DummyMemberFactory.randomJob(),
            interests = DummyMemberFactory.randomInterests().toMutableSet(),
        )
    }
}
