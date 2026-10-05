package com.ditto.api.admin.quiz.dto

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizSet

class QuizSetParticipation(val quizSet: QuizSet, val progress: QuizProgress)
