package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.dto.SingleDummyForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 어드민이 프로필과 문항별 답을 지정해 더미를 한 명 만든다. 지정하지 않은 값은 무작위로 채운다. */
@Component
class SingleDummyCreator(
    private val memberRepository: MemberRepository,
    private val dummyAnswerRecorder: DummyAnswerRecorder,
) {

    @Transactional
    fun create(form: SingleDummyForm): Member {
        val questions = dummyAnswerRecorder.findQuestionsOf(form.quizSetId)
        val profile = profileOf(form)
        val pickedChoices = questions.pickChoices(answeredCountOf(form, questions)) { quiz, choices ->
            chosenOrRandom(form, quiz, choices)
        }
        val member = memberRepository.save(DummyMemberFactory.create(profile))
        dummyAnswerRecorder.recordAnswers(member.id, questions, pickedChoices)
        return member
    }

    private fun profileOf(form: SingleDummyForm): DummyProfile {
        if (form.age !in AGE_RANGE) {
            throw WarnException(ErrorCode.BAD_REQUEST, "나이는 ${AGE_RANGE.first}~${AGE_RANGE.last} 사이여야 합니다.")
        }
        if (form.interests.size !in DummyMemberFactory.INTEREST_COUNT_RANGE) {
            throw WarnException(ErrorCode.BAD_REQUEST, "관심사는 1~5개를 골라 주세요.")
        }
        return DummyProfile(
            nickname = nicknameOf(form),
            gender = form.gender,
            age = form.age,
            interests = form.interests.toSet(),
            location = form.location,
            job = form.job,
            caricature = caricatureOf(form),
        )
    }

    private fun nicknameOf(form: SingleDummyForm): String {
        val suffix = form.nicknameSuffix.trim()
        if (suffix.isEmpty()) return DummyMemberFactory.autoNickname(form.gender)
        if (!NICKNAME_SUFFIX_PATTERN.matches(suffix)) {
            throw WarnException(ErrorCode.BAD_REQUEST, "닉네임 뒷부분은 한글·영문·숫자·'-'로 1~20자까지 쓸 수 있습니다.")
        }
        val nickname = DummyMarker.NICKNAME_PREFIX + suffix
        if (memberRepository.existsByNickname(nickname)) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이미 있는 닉네임입니다: $nickname")
        }
        return nickname
    }

    private fun caricatureOf(form: SingleDummyForm): String {
        val avatarNumber = form.avatarNumber ?: return DummyMemberFactory.randomCaricatureOf(form.gender)
        val avatarCount = DummyMemberFactory.CARICATURE_COUNT_PER_GENDER
        if (avatarNumber !in 1..avatarCount) {
            throw WarnException(ErrorCode.BAD_REQUEST, "캐리커쳐 번호는 1~$avatarCount 사이여야 합니다.")
        }
        return DummyMemberFactory.caricatureOf(form.gender, avatarNumber)
    }

    private fun answeredCountOf(form: SingleDummyForm, questions: QuizQuestions): Int {
        val quizCount = questions.quizzes.size
        val answeredCount = form.answeredCount ?: return quizCount
        if (answeredCount !in 0..quizCount) {
            throw WarnException(ErrorCode.BAD_REQUEST, "푼 문항 수는 0~$quizCount 사이여야 합니다.")
        }
        return answeredCount
    }

    private fun chosenOrRandom(form: SingleDummyForm, quiz: Quiz, choices: List<QuizChoice>): QuizChoice {
        val chosenId = form.choiceIdByQuizId[quiz.id] ?: return choices.random()
        return choices.firstOrNull { it.id == chosenId }
            ?: throw WarnException(ErrorCode.BAD_REQUEST, "문항에 없는 선택지입니다: quizId=${quiz.id}, choiceId=$chosenId")
    }

    companion object {
        // 실회원 가입 검증(CreateUserRequest)과 같은 범위다.
        private val AGE_RANGE = 20..100

        // 실회원 닉네임 규칙보다 넓게 '-'와 20자까지 허용한다. 접두어를 붙여도 컬럼 길이(50) 안이다.
        private val NICKNAME_SUFFIX_PATTERN = Regex("^[a-zA-Z0-9가-힣-]{1,20}$")
    }
}
