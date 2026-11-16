package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.match.entity.QMatchCandidate
import com.ditto.domain.match.entity.QPersonalMatch.personalMatch
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions

/**
 * 시크릿 매칭 라운드 후보 쌍에서 나온 1:1 신청인가. 라운드는 이미 후보였던 쌍을 다시 뽑지 않아 쌍의 출처는 하나다.
 * 신청에는 출처가 없어 같은 셋의 후보 행으로 판단한다. 후보 행은 양방향으로 저장돼 한쪽 방향만 보면 된다.
 */
internal fun isSecretRoundPersonalMatch(): BooleanExpression {
    val secretCandidate = QMatchCandidate("secretCandidate")
    return JPAExpressions.selectOne()
        .from(secretCandidate)
        .where(
            secretCandidate.quizSetId.eq(personalMatch.quizSetId),
            secretCandidate.ownerMemberId.eq(personalMatch.memberId1),
            secretCandidate.otherMemberId.eq(personalMatch.memberId2),
            secretCandidate.candidateOrigin.ne(CandidateOrigin.REGULAR),
        )
        .exists()
}
