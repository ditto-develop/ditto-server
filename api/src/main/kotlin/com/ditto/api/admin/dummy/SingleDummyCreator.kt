package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.dto.SingleDummyForm
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 어드민이 프로필과 문항별 답을 지정해 더미를 한 명 만든다. 지정하지 않은 값은 무작위로 채운다. */
@Component
class SingleDummyCreator(
    private val memberRepository: MemberRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val dummyAnswerRecorder: DummyAnswerRecorder,
) {

    @Transactional
    fun create(form: SingleDummyForm): CreatedDummy {
        val questions = dummyAnswerRecorder.findQuestionsOf(form.quizSetId)
        val profile = profileOf(form)
        val pickedChoices = questions.pickChoices(answeredCountOf(form, questions)) { quiz ->
            chosenOrRandom(form, quiz, questions.choicesOf(quiz))
        }
        val member = save(profile)
        dummyAnswerRecorder.recordAnswers(member.id, questions, pickedChoices)
        return CreatedDummy(member, answeredCount = pickedChoices.size, quizCount = questions.quizzes.size)
    }

    /**
     * [memberId] 회원과 점수를 맞춘 더미를 만들 폼. 그 회원의 답을 그대로 채우고 성별은 반대, 나이는 같게 둔다.
     * 1:1 은 이성끼리, 나이 차가 작은 회원만 후보가 되기 때문이다.
     */
    @Transactional(readOnly = true)
    fun formMatching(memberId: Long, questions: QuizQuestions): SingleDummyForm {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw WarnException(ErrorCode.NOT_FOUND, "없는 회원입니다: #$memberId")
        val choiceIdByQuizId = quizAnswerRepository
            .findByMemberIdAndQuizIdIn(memberId, questions.quizzes.map { it.id })
            .associateTo(mutableMapOf<Long, Long?>()) { it.quizId to it.choiceId }
        if (choiceIdByQuizId.isEmpty()) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이 퀴즈셋에 답한 문항이 없는 회원입니다: #$memberId")
        }
        return SingleDummyForm.withRandomProfile(questions.quizSetId).also { form ->
            form.gender = member.gender?.opposite() ?: form.gender
            form.age = member.age ?: form.age
            form.choiceIdByQuizId = choiceIdByQuizId
        }
    }

    // 존재 확인과 저장 사이에 같은 닉네임이 먼저 들어오면 유일 제약에 걸린다. 같은 안내로 돌려준다.
    private fun save(profile: DummyProfile): Member =
        runCatching { memberRepository.save(DummyMemberFactory.create(profile)) }
            .getOrElse { e ->
                if (e !is DataIntegrityViolationException) throw e
                throw nicknameTaken(profile.nickname)
            }

    private fun profileOf(form: SingleDummyForm): DummyProfile = DummyProfile(
        nickname = nicknameOf(form),
        gender = form.gender,
        age = ageOf(form),
        interests = interestsOf(form),
        location = form.location,
        job = form.job,
        caricature = caricatureOf(form),
    )

    private fun ageOf(form: SingleDummyForm): Int {
        val age = form.age ?: throw WarnException(ErrorCode.BAD_REQUEST, "나이를 입력해 주세요.")
        val ageRange = DummyMemberFactory.AGE_RANGE
        if (age !in ageRange) {
            throw WarnException(ErrorCode.BAD_REQUEST, "나이는 ${ageRange.first}~${ageRange.last} 사이여야 합니다.")
        }
        return age
    }

    private fun interestsOf(form: SingleDummyForm): Set<Interest> {
        val countRange = DummyMemberFactory.INTEREST_COUNT_RANGE
        if (form.interests.size !in countRange) {
            throw WarnException(ErrorCode.BAD_REQUEST, "관심사는 ${countRange.first}~${countRange.last}개를 골라 주세요.")
        }
        return form.interests.toSet()
    }

    private fun nicknameOf(form: SingleDummyForm): String {
        val suffix = form.nicknameSuffix.trim()
        if (suffix.isEmpty()) return DummyMemberFactory.autoNickname(form.gender)
        if (!DummyMemberFactory.NICKNAME_SUFFIX_PATTERN.matches(suffix)) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "닉네임 뒷부분은 한글·영문·숫자·'-'로 1~${DummyMemberFactory.NICKNAME_SUFFIX_MAX_LENGTH}자까지 쓸 수 있습니다.",
            )
        }
        val nickname = DummyMemberFactory.nicknameOf(suffix)
        if (memberRepository.existsByNickname(nickname)) throw nicknameTaken(nickname)
        return nickname
    }

    private fun nicknameTaken(nickname: String) =
        WarnException(ErrorCode.BAD_REQUEST, "이미 있는 닉네임입니다: $nickname")

    private fun caricatureOf(form: SingleDummyForm): String {
        val avatarNumber = form.avatarNumber ?: return DummyMemberFactory.randomCaricatureOf(form.gender)
        val avatarCount = DummyMemberFactory.CARICATURE_COUNT_PER_GENDER
        if (avatarNumber !in 1..avatarCount) {
            throw WarnException(ErrorCode.BAD_REQUEST, "캐리커처 번호는 1~$avatarCount 사이여야 합니다.")
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
            ?: throw WarnException(ErrorCode.BAD_REQUEST, "문항 ID #${quiz.id}에 없는 선택지입니다: #$chosenId")
    }
}

class CreatedDummy(val member: Member, val answeredCount: Int, val quizCount: Int) {
    /** 매칭 재생성 결과가 구성원을 ID로만 보여 줘서 ID를 함께 적는다. */
    fun toDisplayText(): String =
        "${member.nickname} (#${member.id} · ${member.gender?.description} · $answeredCount/$quizCount 풀이)"
}
