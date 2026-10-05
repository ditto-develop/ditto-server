package com.ditto.api.admin.dummy

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.dummy.dto.DummyGenerateForm
import com.ditto.api.admin.dummy.dto.SingleDummyForm
import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 더미 생성(서버 렌더링). 선택한 퀴즈셋을 무작위 답으로 끝까지 푼 더미를 남녀 인원수만큼 만든다.
 * 데이터(회원·진행·답변)만 생성하며, 매칭 후보는 '매칭 실행'에서 별도로 재생성한다.
 */
@Controller
class AdminDummyController(
    private val adminDummyService: AdminDummyService,
    private val singleDummyCreator: SingleDummyCreator,
    private val dummyAnswerRecorder: DummyAnswerRecorder,
    private val quizSetRepository: QuizSetRepository,
) {
    @GetMapping("/admin/dummy")
    fun page(model: Model): String {
        model.addAttribute("form", DummyGenerateForm())
        model.addAttribute("quizSets", quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc())
        model.addAttribute("dummyCount", adminDummyService.countDummies())
        model.addAttribute("active", "dummy")
        return "dummy"
    }

    @PostMapping("/admin/dummy")
    fun generate(
        @ModelAttribute("form") form: DummyGenerateForm,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching { adminDummyService.generate(form) }
            .onSuccess { created ->
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #${form.quizSetId} 에 더미 ${created}명 생성" }
                redirectAttributes.addFlashAttribute("message", "퀴즈셋 #${form.quizSetId}에 더미 ${created}명을 생성했습니다.")
                redirectAttributes.addFlashAttribute("createdQuizSetId", form.quizSetId)
            }
            .onFailure { e -> redirectAttributes.addFlashAttribute("error", warnOrRethrow(e).message) }
        return "redirect:/admin/dummy"
    }

    @GetMapping("/admin/dummy/single")
    fun singleForm(
        @RequestParam(required = false) quizSetId: Long?,
        @RequestParam(required = false) answersFromMemberId: Long?,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (quizSetId == null) {
            redirectAttributes.addFlashAttribute("error", "더미를 만들 퀴즈셋을 골라 주세요.")
            return "redirect:/admin/dummy"
        }
        val formCarriedOver = (model.getAttribute(FORM) as? SingleDummyForm)?.takeIf { it.quizSetId == quizSetId }
        model.addAttribute("answersFromMemberId", answersFromMemberId)
        return showSingleForm(model, redirectAttributes) {
            val questions = dummyAnswerRecorder.findQuestionsOf(quizSetId)
            val form = formCarriedOver
                ?: answersFromMemberId?.let { singleDummyCreator.formMatching(it, questions) }
                ?: SingleDummyForm.withRandomProfile(quizSetId)
            questions to form
        }
    }

    @PostMapping("/admin/dummy/single")
    fun createSingle(
        @ModelAttribute("form") form: SingleDummyForm,
        @AuthenticationPrincipal admin: AdminPrincipal,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { singleDummyCreator.create(form) }
        .fold(
            onSuccess = { created ->
                val summary = created.toDisplayText()
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #${form.quizSetId} 에 더미 생성: $summary" }
                redirectAttributes.addFlashAttribute("message", "퀴즈셋 #${form.quizSetId}에 더미를 만들었습니다: $summary")
                redirectAttributes.addFlashAttribute(FORM, form.forNextDummy())
                "redirect:/admin/dummy/single?quizSetId=${form.quizSetId}"
            },
            onFailure = { e ->
                // 입력한 값을 잃지 않게 리다이렉트하지 않고 같은 폼을 다시 그린다.
                model.addAttribute("error", warnOrRethrow(e).message)
                showSingleForm(model, redirectAttributes) {
                    dummyAnswerRecorder.findQuestionsOf(form.quizSetId) to form
                }
            },
        )

    @PostMapping("/admin/dummy/clear")
    fun clear(
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        val deletedText = adminDummyService.deleteAllDummies().toDisplayText()
        log.info { "어드민[${admin.displayName}] 이 $deletedText 삭제" }
        redirectAttributes.addFlashAttribute("message", "${deletedText}를 삭제했습니다.")
        return "redirect:/admin/dummy"
    }

    /** [questionsAndForm] 이 입력 오류로 실패하면 더미 페이지로 돌려보낸다. */
    private fun showSingleForm(
        model: Model,
        redirectAttributes: RedirectAttributes,
        questionsAndForm: () -> Pair<QuizQuestions, SingleDummyForm>,
    ): String =
        runCatching(questionsAndForm)
            .fold(
                onSuccess = { (questions, form) -> renderSingleForm(model, form, questions) },
                onFailure = { e ->
                    redirectAttributes.addFlashAttribute("error", warnOrRethrow(e).message)
                    "redirect:/admin/dummy"
                },
            )

    private fun renderSingleForm(model: Model, form: SingleDummyForm, questions: QuizQuestions): String {
        model.addAttribute(FORM, form)
        model.addAttribute("questions", questions)
        model.addAttribute("genders", Gender.entries)
        model.addAttribute("locations", Location.entries)
        model.addAttribute("jobs", Job.entries)
        model.addAttribute("interests", Interest.entries)
        model.addAttribute("avatarNumbers", 1..DummyMemberFactory.CARICATURE_COUNT_PER_GENDER)
        model.addAttribute("ageRange", DummyMemberFactory.AGE_RANGE)
        model.addAttribute("interestCountRange", DummyMemberFactory.INTEREST_COUNT_RANGE)
        model.addAttribute("nicknamePrefix", DummyMarker.NICKNAME_PREFIX)
        model.addAttribute("nicknameSuffixMaxLength", DummyMemberFactory.NICKNAME_SUFFIX_MAX_LENGTH)
        model.addAttribute("isOneToOne", questions.quizSet.matchingType == MatchingType.ONE_TO_ONE)
        model.addAttribute("maxAgeGap", OneToOneMatchingProcessor.MAX_AGE_GAP)
        model.addAttribute("active", "dummy")
        return "dummy-single"
    }

    /** 입력 오류(WarnException)는 화면에 안내하고, 예기치 못한 예외는 전역 핸들러로 넘긴다. */
    private fun warnOrRethrow(exception: Throwable): WarnException = exception as? WarnException ?: throw exception

    companion object {
        private const val FORM = "form"
        private val log = KotlinLogging.logger {}
    }
}
