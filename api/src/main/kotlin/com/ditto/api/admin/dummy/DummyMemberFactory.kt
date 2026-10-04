package com.ditto.api.admin.dummy

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import java.util.UUID
import kotlin.random.Random

/** 더미 회원의 프로필 값을 정하고 실회원 가입과 같은 경로(register)로 활성화한다. */
object DummyMemberFactory {
    const val CARICATURE_COUNT_PER_GENDER = 8
    val INTEREST_COUNT_RANGE = 1..5

    private const val EMAIL_DOMAIN = "dummy.local"

    // FE 가입 화면이 고르는 아바타 경로와 같은 형식이다. FE가 이 경로를 바꾸면 함께 고쳐야 한다.
    private const val CARICATURE_PATH_PREFIX = "/onboarding/profileimg/avatar/"

    fun create(profile: DummyProfile): Member =
        // gender·age 가 null 이면 매칭 후보 풀에서 빠지므로 더미는 반드시 채운다.
        Member(nickname = profile.nickname, email = "${profile.nickname}@$EMAIL_DOMAIN").apply {
            register(
                name = null,
                nickname = null,
                phoneNumber = null,
                gender = profile.gender,
                age = profile.age,
                birthDate = null,
                email = null,
                interests = profile.interests,
                location = profile.location,
                job = profile.job,
                caricature = profile.caricature,
            )
        }

    fun autoNickname(gender: Gender): String =
        "${DummyMarker.NICKNAME_PREFIX}${gender.name.lowercase()}-${UUID.randomUUID().toString().take(8)}"

    fun caricatureOf(gender: Gender, avatarNumber: Int): String {
        val genderInitial = when (gender) {
            Gender.MALE -> "m"
            Gender.FEMALE -> "f"
        }
        return "$CARICATURE_PATH_PREFIX$genderInitial$avatarNumber.svg"
    }

    fun randomCaricatureOf(gender: Gender): String =
        caricatureOf(gender, Random.nextInt(1, CARICATURE_COUNT_PER_GENDER + 1))

    fun randomInterests(): Set<Interest> =
        Interest.entries.shuffled().take(INTEREST_COUNT_RANGE.random()).toSet()

    fun randomLocation(): Location = Location.entries.random()

    fun randomJob(): Job = Job.entries.random()
}

class DummyProfile(
    val nickname: String,
    val gender: Gender,
    val age: Int,
    val interests: Set<Interest>,
    val location: Location,
    val job: Job,
    val caricature: String,
)
