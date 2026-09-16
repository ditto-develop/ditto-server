package com.ditto.api.user.service

/**
 * 타인 프로필(과 평점·답변 비교 같은 보조 정보)을 어디까지 볼 수 있는지.
 *
 * 소개노트가 후보에게 "10문항 중 3문항"만 여는 것과 같은 원칙이다
 * ([com.ditto.api.intronote.service.IntroNoteService.getIntroNotes]) — 성사 전 구간은
 * "대화 신청 여부를 정하는 화면"(피그마 3.2)이 쓸 만큼만 열고, 나머지는 성사 후로 미룬다.
 */
enum class ProfileAccessLevel {
    /** 매칭 성사·같은 그룹 채팅 참여·본인. 받은 평가의 코멘트와 노쇼 횟수까지 본다. */
    FULL,

    /** 이번 주 매칭 후보(성사 전). 평균 점수와 평가 건수까지만 본다. */
    SUMMARY,
    ;

    val isFull: Boolean get() = this == FULL
}
