const { describe, test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const StatsCalc = require(path.join(__dirname, '../../main/resources/static/admin/js/stats-calc.js'));

const GENDER_LABELS = { MALE: '남성', FEMALE: '여성' };
const AGE_BAND_RANGES = [
    { label: '20대 초반', minAge: 20, maxAge: 23 },
    { label: '20대 중반', minAge: 24, maxAge: 26 },
];

describe('formatCount', () => {
    test('천 단위마다 쉼표를 넣는다', () => {
        assert.equal(StatsCalc.formatCount(12345), '12,345');
    });
});

describe('percent', () => {
    test('전체가 0이면 0이다', () => {
        assert.equal(StatsCalc.percent(0, 0), 0);
    });
});

describe('percentText', () => {
    test('소수 첫째 자리까지 보여 준다', () => {
        assert.equal(StatsCalc.percentText(1, 3), '33.3%');
    });

    test('전체가 0이면 비율 대신 - 를 보여 준다', () => {
        assert.equal(StatsCalc.percentText(0, 0), '-');
    });
});

describe('memberTotals', () => {
    test('누적 가입은 탈퇴를 포함하고 현재 회원은 탈퇴를 뺀다', () => {
        const totals = StatsCalc.memberTotals({ ACTIVE: 5, SUSPENDED: 1, LEFT: 2 });

        assert.deepEqual(totals, { joined: 8, current: 6, left: 2, pending: 0 });
    });

    test('가입을 마치지 않은 회원은 누적 가입에서 빼고 따로 센다', () => {
        const totals = StatsCalc.memberTotals({ ACTIVE: 5, LEFT: 2, PENDING: 3 });

        assert.deepEqual(totals, { joined: 7, current: 5, left: 2, pending: 3 });
    });
});

describe('accountStatusCounts', () => {
    test('ACTIVE 를 최근 회차 완주 여부로 활성·휴면으로 나누고 정지·영구 차단은 상태대로 센다', () => {
        const rows = StatsCalc.accountStatusCounts({ ACTIVE: 10, SUSPENDED: 2, BANNED: 1, LEFT: 4 }, 6);

        assert.deepEqual(rows, [
            { label: '활성', count: 6 },
            { label: '휴면', count: 4 },
            { label: '정지', count: 2 },
            { label: '영구 차단', count: 1 },
        ]);
    });
});

describe('locationCounts', () => {
    const LOCATION_LABELS = { SEOUL: '서울', BUSAN: '부산', JEJU: '제주' };

    test('회원이 있는 지역만 많은 순이고 미입력은 맨 뒤다', () => {
        const rows = StatsCalc.locationCounts(
            [{ location: 'BUSAN', count: 1 }, { location: null, count: 2 }, { location: 'SEOUL', count: 3 }],
            LOCATION_LABELS,
        );

        assert.deepEqual(rows, [
            { label: '서울', count: 3 },
            { label: '부산', count: 1 },
            { label: '미입력', count: 2 },
        ]);
    });

    test('같은 수면 라벨 순이다', () => {
        const rows = StatsCalc.locationCounts(
            [{ location: 'JEJU', count: 1 }, { location: 'BUSAN', count: 1 }],
            LOCATION_LABELS,
        );

        assert.deepEqual(rows.map((row) => row.label), ['부산', '제주']);
    });

    test('미입력이 없으면 줄을 만들지 않는다', () => {
        const rows = StatsCalc.locationCounts([{ location: 'JEJU', count: 1 }], LOCATION_LABELS);

        assert.deepEqual(rows, [{ label: '제주', count: 1 }]);
    });
});

describe('leaveReasonCounts', () => {
    test('사유는 탈퇴 화면 순서대로 0이어도 나오고 사유 없음과 모르는 코드는 뒤에 붙는다', () => {
        const counts = StatsCalc.leaveReasonCounts([
            { leaveReason: 'other', count: 1 },
            { leaveReason: 'low_usage', count: 2 },
            { leaveReason: null, count: 1 },
            { leaveReason: 'new_reason', count: 1 },
        ]);

        assert.equal(counts.leftCount, 5);
        assert.deepEqual(counts.reasonCounts, [
            { label: '원하는 매칭이 이루어지지 않아요', count: 0 },
            { label: '사용 빈도가 낮아졌어요', count: 2 },
            { label: '개인정보가 걱정돼요', count: 0 },
            { label: '불편한 경험이 있었어요', count: 0 },
            { label: '기타', count: 1 },
            { label: '애플 계정 삭제', count: 0 },
            { label: '애플 연결 해제', count: 0 },
            { label: '사유 없음', count: 1 },
            { label: 'new_reason', count: 1 },
        ]);
    });
});

describe('dailyJoins', () => {
    test('월요일부터 일요일까지 7일을 채우고 가입이 없는 날은 0이다', () => {
        const joinedAts = ['2026-10-19 09:00:00', '2026-10-19 23:59:59', '2026-10-25 00:00:00'];

        const days = StatsCalc.dailyJoins('2026-10-19', '2026-10-25', joinedAts);

        assert.deepEqual(days.map((day) => day.date), [
            '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24', '2026-10-25',
        ]);
        assert.deepEqual(days.map((day) => day.count), [2, 0, 0, 0, 0, 0, 1]);
        assert.equal(days[0].dayLabel, '월');
        assert.equal(days[6].dayLabel, '일');
    });

    test('오늘 이후 날은 count 가 null 이고 오늘 행에 표시가 붙는다', () => {
        const days = StatsCalc.dailyJoins('2026-10-19', '2026-10-22', []);

        assert.deepEqual(days.map((day) => day.count), [0, 0, 0, 0, null, null, null]);
        assert.deepEqual(days.map((day) => day.isToday), [false, false, false, true, false, false, false]);
    });
});

describe('weeklyJoins', () => {
    test('고른 주에서 끝나는 주들을 오래된 순으로 세고 일요일 가입은 그 주에 넣는다', () => {
        const joinedAts = ['2026-10-11 23:00:00', '2026-10-12 00:00:00', '2026-10-21 10:00:00'];

        const weeks = StatsCalc.weeklyJoins('2026-10-19', 3, joinedAts);

        assert.deepEqual(weeks, [
            { weekStartedOn: '2026-10-05', count: 1 },
            { weekStartedOn: '2026-10-12', count: 1 },
            { weekStartedOn: '2026-10-19', count: 1 },
        ]);
    });

    test('연 경계를 넘어 앞 주로 간다', () => {
        const weeks = StatsCalc.weeklyJoins('2027-01-04', 2, []);

        assert.deepEqual(weeks.map((week) => week.weekStartedOn), ['2026-12-28', '2027-01-04']);
    });
});

describe('ageOn', () => {
    test('생일이 지나야 한 살 올라간다', () => {
        assert.equal(StatsCalc.ageOn('2002-10-22', '2026-10-21'), 23);
        assert.equal(StatsCalc.ageOn('2002-10-22', '2026-10-22'), 24);
    });

    test('2월 29일생은 평년 2월 28일에 아직 생일 전이다', () => {
        assert.equal(StatsCalc.ageOn('2004-02-29', '2025-02-28'), 20);
        assert.equal(StatsCalc.ageOn('2004-02-29', '2025-03-01'), 21);
    });
});

describe('genderCounts', () => {
    test('생년월일별로 나뉜 행을 성별로 합치고 미입력 행은 0이어도 둔다', () => {
        const genderBirthDateCounts = [
            { gender: 'MALE', birthDate: '2000-01-01', count: 2 },
            { gender: 'MALE', birthDate: '2001-01-01', count: 1 },
            { gender: 'FEMALE', birthDate: '2001-01-01', count: 4 },
        ];

        assert.deepEqual(StatsCalc.genderCounts(genderBirthDateCounts, GENDER_LABELS), [
            { label: '남성', count: 3 },
            { label: '여성', count: 4 },
            { label: '미입력', count: 0 },
        ]);
    });

    test('성별이 없는 회원은 미입력으로 센다', () => {
        const genderBirthDateCounts = [{ gender: null, birthDate: null, count: 1 }];

        const unknown = StatsCalc.genderCounts(genderBirthDateCounts, GENDER_LABELS).at(-1);

        assert.deepEqual(unknown, { label: '미입력', count: 1 });
    });
});

describe('genderColumnLabels', () => {
    test('genderLabels 순서 뒤에 미입력을 붙인다', () => {
        assert.deepEqual(StatsCalc.genderColumnLabels(GENDER_LABELS), ['남성', '여성', '미입력']);
    });
});

describe('ageGenderRows', () => {
    test('행은 늘 연령대 뒤에 그 외·생년월일 없음이 오고 칸은 genderColumnLabels 순서다', () => {
        const genderBirthDateCounts = [{ gender: 'FEMALE', birthDate: '2004-01-01', count: 2 }];

        assert.deepEqual(StatsCalc.ageGenderRows(genderBirthDateCounts, GENDER_LABELS, AGE_BAND_RANGES, '2026-10-22'), [
            { label: '20대 초반', countsInColumnOrder: [0, 2, 0], totalCount: 2 },
            { label: '20대 중반', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
            { label: '그 외', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
            { label: '생년월일 없음', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
        ]);
    });

    test('구간 밖 나이와 생년월일 없는 회원은 각자 행에 들어간다', () => {
        const genderBirthDateCounts = [
            { gender: 'MALE', birthDate: '1980-01-01', count: 1 },
            { gender: null, birthDate: null, count: 3 },
        ];

        const rows = StatsCalc.ageGenderRows(genderBirthDateCounts, GENDER_LABELS, AGE_BAND_RANGES, '2026-10-22');

        assert.deepEqual(rows.map((row) => row.totalCount), [0, 0, 1, 3]);
        assert.deepEqual(rows[3].countsInColumnOrder, [0, 0, 3]);
    });

    test('구간 경계 나이는 그 구간에 들어간다', () => {
        const genderBirthDateCounts = [
            { gender: 'MALE', birthDate: '2006-10-22', count: 1 },
            { gender: 'MALE', birthDate: '2002-10-23', count: 1 },
        ];

        const [earlyTwenties] =
            StatsCalc.ageGenderRows(genderBirthDateCounts, GENDER_LABELS, AGE_BAND_RANGES, '2026-10-22');

        assert.equal(earlyTwenties.totalCount, 2);
    });
});

const QUIZ_MATCHING_TYPE_LABELS = { ONE_TO_ONE: '1:1', GROUP: '그룹' };
const PROGRESS_COUNTS = [
    { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'MALE', count: 3 },
    { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'FEMALE', count: 2 },
    { matchingType: 'ONE_TO_ONE', status: 'IN_PROGRESS', gender: 'FEMALE', count: 4 },
    { matchingType: 'GROUP', status: 'COMPLETED', gender: null, count: 1 },
];

describe('quizParticipation', () => {
    test('참여자는 완주만, 시작은 진행 중까지 센다', () => {
        const participation = StatsCalc.quizParticipation(PROGRESS_COUNTS, QUIZ_MATCHING_TYPE_LABELS);

        assert.equal(participation.participantCount, 6);
        assert.equal(participation.startedCount, 10);
    });

    test('매칭 유형별 참여자는 quizMatchingTypeLabels 순서로 0이어도 나온다', () => {
        const participation = StatsCalc.quizParticipation([], QUIZ_MATCHING_TYPE_LABELS);

        assert.deepEqual(participation.participantCountsByType, [
            { label: '1:1', count: 0 },
            { label: '그룹', count: 0 },
        ]);
    });
});

describe('participantGenderRows', () => {
    test('매칭 유형마다 완주한 회원만 성별 칸으로 나눈다', () => {
        assert.deepEqual(StatsCalc.participantGenderRows(PROGRESS_COUNTS, QUIZ_MATCHING_TYPE_LABELS, GENDER_LABELS), [
            { label: '1:1', countsInColumnOrder: [3, 2, 0], totalCount: 5 },
            { label: '그룹', countsInColumnOrder: [0, 0, 1], totalCount: 1 },
        ]);
    });
});

// 1:1 참여 남 3·여 2, 그룹 참여 미입력 1 (PROGRESS_COUNTS)
const MATCH_STATS = {
    matchCandidateCounts: [
        { gender: 'MALE', count: 3 },
        { gender: 'FEMALE', count: 2 },
    ],
    // 같은 성별이 두 행으로 온다.
    personalMatchMemberCounts: [
        { gender: 'MALE', count: 1 },
        { gender: 'FEMALE', count: 1 },
        { gender: 'MALE', count: 1 },
    ],
    groupMatchMemberCounts: [
        { isGroupActive: true, status: 'ACCEPTED', gender: null, count: 1 },
        { isGroupActive: true, status: 'DECLINED', gender: 'MALE', count: 1 },
        { isGroupActive: false, status: 'ACCEPTED', gender: 'FEMALE', count: 1 },
    ],
};

describe('matchFunnel', () => {
    test('유형별 참여·후보·성사를 세고 그룹은 성사된 그룹 수락자만 성사로 친다', () => {
        const funnel = StatsCalc.matchFunnel(PROGRESS_COUNTS, MATCH_STATS, QUIZ_MATCHING_TYPE_LABELS, GENDER_LABELS);

        assert.deepEqual(funnel.countsByType, [
            { label: '1:1', completedCount: 5, candidateCount: 5, matchedCount: 3 },
            { label: '그룹', completedCount: 1, candidateCount: 3, matchedCount: 1 },
        ]);
    });

    test('합계는 유형별 줄을 더한 값이다', () => {
        const funnel = StatsCalc.matchFunnel(PROGRESS_COUNTS, MATCH_STATS, QUIZ_MATCHING_TYPE_LABELS, GENDER_LABELS);

        assert.equal(funnel.completedCount, 6);
        assert.equal(funnel.candidateCount, 8);
        assert.equal(funnel.matchedCount, 4);
    });

    test('미매칭은 참여에서 성사를 성별마다 빼고 카드는 그 표의 합계다', () => {
        const funnel = StatsCalc.matchFunnel(PROGRESS_COUNTS, MATCH_STATS, QUIZ_MATCHING_TYPE_LABELS, GENDER_LABELS);

        assert.deepEqual(funnel.unmatchedGenderRows, [
            { label: '1:1', countsInColumnOrder: [1, 1, 0], totalCount: 2 },
            { label: '그룹', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
        ]);
        assert.equal(funnel.unmatchedCount, 2);
    });

    test('성사가 참여보다 많은 성별 칸은 0으로 자르고 카드도 같은 값을 쓴다', () => {
        const progressCounts = [{ matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'MALE', count: 2 }];
        const matchStats = {
            matchCandidateCounts: [],
            personalMatchMemberCounts: [{ gender: 'MALE', count: 1 }, { gender: 'FEMALE', count: 1 }],
            groupMatchMemberCounts: [],
        };

        const funnel = StatsCalc.matchFunnel(progressCounts, matchStats, { ONE_TO_ONE: '1:1' }, GENDER_LABELS);

        assert.deepEqual(funnel.unmatchedGenderRows[0].countsInColumnOrder, [1, 0, 0]);
        assert.equal(funnel.unmatchedCount, 1);
    });

    test('매칭 유형을 고르면 그 유형 줄만 나온다', () => {
        const funnel = StatsCalc.matchFunnel(PROGRESS_COUNTS, MATCH_STATS, { GROUP: '그룹' }, GENDER_LABELS);

        assert.deepEqual(funnel.countsByType.map((row) => row.label), ['그룹']);
        assert.equal(funnel.completedCount, 1);
        assert.equal(funnel.matchedCount, 1);
    });
});

describe('isBeforeMatchingBatch', () => {
    test('그 주 목요일 전이면 참이다', () => {
        assert.equal(StatsCalc.isBeforeMatchingBatch('2026-10-19', '2026-10-21'), true);
        assert.equal(StatsCalc.isBeforeMatchingBatch('2026-10-19', '2026-10-22'), false);
    });
});

describe('chatActivity', () => {
    const ROOM_TYPE_LABELS = { PERSONAL: '1:1', GROUP: '그룹' };
    const memberCount = (roomId, roomType, count) => ({ roomId, roomType, count });
    const CHAT_STATS = {
        chatRoomMemberCounts: [
            memberCount(1, 'PERSONAL', 2),
            memberCount(2, 'PERSONAL', 2),
            memberCount(3, 'GROUP', 4),
            memberCount(4, 'GROUP', 3),
            memberCount(5, 'REMATCH', 2),
        ],
        // 메시지가 없는 방(2)은 행이 없다.
        chatRoomSenderCounts: [
            { roomId: 1, count: 1 },
            { roomId: 3, count: 2 },
            { roomId: 4, count: 3 },
            { roomId: 5, count: 2 },
        ],
    };

    test('열린 방을 분모로 시작·2명 이상·전원 발신 방을 세고 재매칭은 마지막 줄이다', () => {
        const activity = StatsCalc.chatActivity(CHAT_STATS, ROOM_TYPE_LABELS);

        assert.deepEqual(activity.rows, [
            {
                label: '1:1', isGroup: false, isRematch: false,
                openedCount: 2, startedCount: 1, twoWayCount: 0, allSentCount: 0,
            },
            {
                label: '그룹', isGroup: true, isRematch: false,
                openedCount: 2, startedCount: 2, twoWayCount: 2, allSentCount: 1,
            },
            {
                label: '재매칭',
                isGroup: false,
                isRematch: true,
                openedCount: 1,
                startedCount: 1,
                twoWayCount: 1,
                allSentCount: 1,
            },
        ]);
    });

    test('합계에는 재매칭을 넣지 않는다', () => {
        const activity = StatsCalc.chatActivity(CHAT_STATS, ROOM_TYPE_LABELS);

        assert.equal(activity.openedCount, 4);
        assert.equal(activity.startedCount, 3);
    });

    test('1:1 만 고르면 재매칭 줄이 없다', () => {
        const activity = StatsCalc.chatActivity(CHAT_STATS, { PERSONAL: '1:1' });

        assert.deepEqual(activity.rows.map((row) => row.label), ['1:1']);
        assert.equal(activity.openedCount, 2);
    });
});

describe('questionDistributions', () => {
    const GENDER_LABELS = { MALE: '남성', FEMALE: '여성' };
    const choiceRow = (quizSetId, quizId, quizDisplayOrder, choiceContent, count, gender = 'MALE') => ({
        quizSetId,
        matchingType: quizSetId === 1 ? 'ONE_TO_ONE' : 'GROUP',
        quizId,
        quizDisplayOrder,
        question: `질문${quizId}`,
        choiceId: quizId * 10 + choiceContent.length,
        choiceDisplayOrder: 1,
        choiceContent,
        gender,
        count,
    });

    test('퀴즈셋·문항 순서를 지키며 묶고 선택지는 성별 행을 합친다', () => {
        const distributions = StatsCalc.questionDistributions([
            choiceRow(1, 11, 1, 'A', 2, 'MALE'),
            choiceRow(1, 11, 1, 'A', 1, 'FEMALE'),
            choiceRow(1, 11, 1, 'BB', 1, 'FEMALE'),
            choiceRow(1, 12, 2, 'A', 0, null),
            choiceRow(1, 12, 2, 'BB', 0, null),
            choiceRow(2, 21, 1, 'A', 2),
        ], GENDER_LABELS);

        assert.deepEqual(distributions.map((quizSet) => quizSet.matchingType), ['ONE_TO_ONE', 'GROUP']);
        const [first, second] = distributions[0].questions;
        assert.equal(first.answerCount, 4);
        assert.deepEqual(first.choices, [{ label: 'A', count: 3 }, { label: 'BB', count: 1 }]);
        assert.equal(second.answerCount, 0);
    });

    test('쏠림은 첫 선택지 비율이 50%에서 벗어난 %p 이고 퀴즈셋 점수는 답이 있는 문항의 평균이다', () => {
        const [quizSet] = StatsCalc.questionDistributions([
            choiceRow(1, 11, 1, 'A', 3),
            choiceRow(1, 11, 1, 'BB', 1),
            choiceRow(1, 12, 2, 'A', 1),
            choiceRow(1, 12, 2, 'BB', 1),
            choiceRow(1, 13, 3, 'A', 0, null),
            choiceRow(1, 13, 3, 'BB', 0, null),
        ], GENDER_LABELS);

        assert.deepEqual(quizSet.questions.map((question) => question.skew), [25, 0, null]);
        assert.equal(quizSet.skewScore, 12.5);
    });

    test('성별마다 첫 선택지 비율을 내고 답이 없는 성별은 빠진다', () => {
        const [quizSet] = StatsCalc.questionDistributions([
            choiceRow(1, 11, 1, 'A', 3, 'MALE'),
            choiceRow(1, 11, 1, 'A', 1, 'FEMALE'),
            choiceRow(1, 11, 1, 'BB', 1, 'MALE'),
            choiceRow(1, 11, 1, 'BB', 3, 'FEMALE'),
            choiceRow(1, 12, 2, 'A', 2, 'FEMALE'),
            choiceRow(1, 12, 2, 'BB', 0, null),
        ], GENDER_LABELS);

        assert.deepEqual(quizSet.questions[0].genderShares, [
            { label: '남성', answerCount: 4, share: 75 },
            { label: '여성', answerCount: 4, share: 25 },
        ]);
        assert.deepEqual(quizSet.questions[1].genderShares, [{ label: '여성', answerCount: 2, share: 100 }]);
    });

    test('문항이 없으면 빈 목록이다', () => {
        assert.deepEqual(StatsCalc.questionDistributions([], GENDER_LABELS), []);
    });
});

const MEMBER_REVIEW_MATCH_TYPE_LABELS = { PERSONAL: '1:1', GROUP: '그룹' };
const MEMBER_REVIEW_COUNTS = [
    { matchType: 'PERSONAL', status: 'COMPLETED', count: 6 },
    { matchType: 'PERSONAL', status: 'NOT_STARTED', count: 2 },
    { matchType: 'GROUP', status: 'COMPLETED', count: 3 },
    { matchType: 'GROUP', status: 'IN_PROGRESS', count: 1 },
];
const REVIEW_ANSWER_COUNTS = [
    { matchType: 'PERSONAL', meetingStatus: 'MET', rating: 5, count: 4 },
    { matchType: 'GROUP', meetingStatus: 'MET', rating: 4, count: 2 },
    { matchType: 'GROUP', meetingStatus: 'NO_SHOW', rating: 1, count: 1 },
];

describe('memberReviewSubmission', () => {
    test('진행 중은 제출로 치지 않고 따로 센다', () => {
        assert.deepEqual(StatsCalc.memberReviewSubmission(MEMBER_REVIEW_COUNTS), {
            memberReviewCount: 12,
            completedCount: 9,
            inProgressCount: 1,
            notStartedCount: 2,
        });
    });
});

describe('memberReviewSubmissionByType', () => {
    test('매칭 유형마다 평가지와 완료 수를 낸다', () => {
        const rows = StatsCalc.memberReviewSubmissionByType(MEMBER_REVIEW_COUNTS, MEMBER_REVIEW_MATCH_TYPE_LABELS);

        assert.deepEqual(rows, [
            { label: '1:1', memberReviewCount: 8, completedCount: 6 },
            { label: '그룹', memberReviewCount: 4, completedCount: 3 },
        ]);
    });
});

describe('meetingStatusCounts', () => {
    test('만남 상태는 라벨 순서대로 0이어도 나온다', () => {
        const labels = { MET: '만났어요', APPOINTMENT_MADE: '약속 잡았어요', CHAT_ONLY: '채팅만 했어요', NO_SHOW: '노쇼 당했어요' };

        const counts = StatsCalc.meetingStatusCounts(REVIEW_ANSWER_COUNTS, labels).map((row) => row.count);

        assert.deepEqual(counts, [6, 0, 0, 1]);
    });
});

describe('ratingCounts', () => {
    test('별점은 ratingScores 순서대로 0이어도 나온다', () => {
        assert.deepEqual(StatsCalc.ratingCounts(REVIEW_ANSWER_COUNTS, [5, 4, 3, 2, 1]), [
            { label: '5점', count: 4 },
            { label: '4점', count: 2 },
            { label: '3점', count: 0 },
            { label: '2점', count: 0 },
            { label: '1점', count: 1 },
        ]);
    });
});

describe('retentionCounts', () => {
    test('지난 회차 완주자만 분모로 세고 매칭 유형별로 나눈다', () => {
        const previousProgressCounts = [
            { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'MALE', count: 6 },
            { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'FEMALE', count: 4 },
            { matchingType: 'ONE_TO_ONE', status: 'IN_PROGRESS', gender: 'FEMALE', count: 5 },
            { matchingType: 'GROUP', status: 'COMPLETED', gender: null, count: 5 },
        ];
        const completedAgainCounts = [{ previousMatchingType: 'ONE_TO_ONE', count: 7 }];

        const counts =
            StatsCalc.retentionCounts(previousProgressCounts, completedAgainCounts, QUIZ_MATCHING_TYPE_LABELS);

        assert.deepEqual(counts, {
            previousCount: 15,
            completedAgainCount: 7,
            countsByType: [
                { label: '1:1', previousCount: 10, completedAgainCount: 7 },
                { label: '그룹', previousCount: 5, completedAgainCount: 0 },
            ],
        });
    });
});

describe('pushSendCounts', () => {
    const SKIP_REASON_LABELS = { MUTED: '채팅방 알림 꺼짐', TOGGLE_OFF: '알림 설정 꺼짐', NO_DEVICE: '등록된 기기 없음' };
    const historyCount = (notificationType, skipReason, failedDeviceCount, count) =>
        ({ notificationType, skipReason, failedDeviceCount, count });
    const HISTORY_COUNTS = [
        historyCount('CHAT_MESSAGE', null, 0, 5),
        historyCount('CHAT_MESSAGE', null, 1, 1),
        historyCount('CHAT_MESSAGE', 'MUTED', 0, 2),
        historyCount('MATCH_RESULT', null, 1, 1),
        historyCount('MATCH_RESULT', 'NO_DEVICE', 0, 3),
    ];

    test('건너뛴 이유가 없으면 보냄이고 실패 기기가 하나라도 있으면 실패다', () => {
        const counts = StatsCalc.pushSendCounts(HISTORY_COUNTS, SKIP_REASON_LABELS);

        assert.equal(counts.notificationCount, 12);
        assert.equal(counts.sentCount, 7);
        assert.equal(counts.failedCount, 2);
        assert.equal(counts.skippedCount, 5);
    });

    test('유형별 줄은 보낸 알림이 많은 순이고 라벨이 붙는다', () => {
        const counts = StatsCalc.pushSendCounts(HISTORY_COUNTS, SKIP_REASON_LABELS);

        assert.deepEqual(counts.typeRows, [
            { label: '새 메시지', notificationCount: 8, sentCount: 6, failedCount: 1, skippedCount: 2 },
            { label: '매칭 결과', notificationCount: 4, sentCount: 1, failedCount: 1, skippedCount: 3 },
        ]);
    });

    test('건너뛴 알림이 많아도 보낸 알림이 적으면 아래로 간다', () => {
        const counts = StatsCalc.pushSendCounts(
            [historyCount('QUIZ_OPENED', 'NO_DEVICE', 0, 10), historyCount('CHAT_MESSAGE', null, 0, 2)],
            SKIP_REASON_LABELS,
        );

        assert.deepEqual(counts.typeRows.map((row) => row.label), ['새 메시지', '퀴즈 열림']);
    });

    test('건너뛴 이유는 라벨 순서대로 0이어도 나온다', () => {
        const counts = StatsCalc.pushSendCounts(HISTORY_COUNTS, SKIP_REASON_LABELS);

        assert.deepEqual(counts.skipReasonCounts, [
            { label: '채팅방 알림 꺼짐', count: 2 },
            { label: '알림 설정 꺼짐', count: 0 },
            { label: '등록된 기기 없음', count: 3 },
        ]);
    });

    test('라벨이 없는 유형은 enum 이름 그대로 보인다', () => {
        const counts = StatsCalc.pushSendCounts([historyCount('NEW_TYPE', null, 0, 1)], SKIP_REASON_LABELS);

        assert.equal(counts.typeRows[0].label, 'NEW_TYPE');
    });
});

describe('memberReportCounts', () => {
    const reasonLabels = { MONEY_DEMAND: '금전 요구', ETC: '기타' };
    const statusLabels = { RECEIVED: '검토 대기', REJECTED: '기각' };
    const memberReportCountRows = [
        { reasons: ['MONEY_DEMAND', 'ETC'], status: 'RECEIVED', count: 2 },
        { reasons: ['ETC'], status: 'REJECTED', count: 1 },
    ];

    test('사유는 고른 것마다 세서 합이 건수보다 클 수 있다', () => {
        const counts = StatsCalc.memberReportCounts(memberReportCountRows, reasonLabels, statusLabels);

        assert.equal(counts.memberReportCount, 3);
        assert.deepEqual(counts.reasonCounts, [{ label: '금전 요구', count: 2 }, { label: '기타', count: 3 }]);
    });

    test('처리 상태는 라벨 순서대로 0이어도 나온다', () => {
        const counts = StatsCalc.memberReportCounts([], reasonLabels, statusLabels);

        assert.deepEqual(counts.statusCounts, [{ label: '검토 대기', count: 0 }, { label: '기각', count: 0 }]);
    });

    test('검토 대기 밖의 상태는 모두 처리 완료로 센다', () => {
        const counts = StatsCalc.memberReportCounts(
            [
                { reasons: ['ETC'], status: 'RECEIVED', count: 2 },
                { reasons: ['ETC'], status: 'ACTIONED', count: 1 },
                { reasons: ['ETC'], status: 'REJECTED_ABUSIVE', count: 1 },
            ],
            { ETC: '기타' },
            { RECEIVED: '검토 대기' },
        );

        assert.equal(counts.receivedCount, 2);
        assert.equal(counts.processedCount, 2);
    });
});

describe('sumCounts', () => {
    test('행의 count 를 더한다', () => {
        assert.equal(StatsCalc.sumCounts([{ count: 2 }, { count: 3 }]), 5);
    });
});

describe('completionDayCounts', () => {
    test('응답 기간인 월~수는 0이어도 나오고 그 밖의 요일은 완주가 있을 때만 붙는다', () => {
        const days = StatsCalc.completionDayCounts(
            ['2026-10-19 00:00:00', '2026-10-19 23:59:59', '2026-10-21 12:00:00', '2026-10-25 09:00:00'],
        );

        assert.deepEqual(days, [
            { label: '월', count: 2 },
            { label: '화', count: 0 },
            { label: '수', count: 1 },
            { label: '일', count: 1 },
        ]);
    });
});

describe('trackSwitchCounts', () => {
    const ALL_LABELS = { ONE_TO_ONE: '1:1', GROUP: '그룹' };
    const COMPLETED_AGAIN_COUNTS = [
        { previousMatchingType: 'ONE_TO_ONE', againMatchingType: 'ONE_TO_ONE', count: 3 },
        { previousMatchingType: 'ONE_TO_ONE', againMatchingType: 'GROUP', count: 1 },
        { previousMatchingType: 'GROUP', againMatchingType: 'ONE_TO_ONE', count: 2 },
    ];

    test('지난 회차 유형 → 고른 회차 유형 네 줄을 0이어도 낸다', () => {
        const rows = StatsCalc.trackSwitchCounts(COMPLETED_AGAIN_COUNTS, ALL_LABELS, ALL_LABELS);

        assert.deepEqual(rows, [
            { label: '1:1 → 1:1', count: 3 },
            { label: '1:1 → 그룹', count: 1 },
            { label: '그룹 → 1:1', count: 2 },
            { label: '그룹 → 그룹', count: 0 },
        ]);
    });

    test('매칭 유형을 고르면 지난 회차 쪽만 걸러지고 고른 회차 쪽은 두 유형이 다 나온다', () => {
        const rows = StatsCalc.trackSwitchCounts(COMPLETED_AGAIN_COUNTS, { GROUP: '그룹' }, ALL_LABELS);

        assert.deepEqual(rows, [
            { label: '그룹 → 1:1', count: 2 },
            { label: '그룹 → 그룹', count: 0 },
        ]);
    });
});

describe('scoreSummary', () => {
    test('평균·중앙값을 내고 10점 구간은 값이 있는 구간 사이만 0이어도 채운다', () => {
        const summary = StatsCalc.scoreSummary([62, 81.5, 65, 100]);

        assert.equal(summary.count, 4);
        assert.equal(summary.average, 77.125);
        assert.equal(summary.median, 73.25);
        assert.deepEqual(summary.bins, [
            { label: '60점대', count: 2 },
            { label: '70점대', count: 0 },
            { label: '80점대', count: 1 },
            { label: '90점 이상', count: 1 },
        ]);
    });

    test('점수가 없으면 평균·중앙값이 null 이고 구간도 없다', () => {
        assert.deepEqual(StatsCalc.scoreSummary([]), { count: 0, average: null, median: null, bins: [] });
    });
});

describe('matchScores', () => {
    test('그룹은 전체 후보와 성사된 그룹을 따로 요약한다', () => {
        const scores = StatsCalc.matchScores({
            pairScores: [70],
            acceptedPairScores: [],
            groupScores: [{ isActive: true, score: 66 }, { isActive: false, score: 60 }],
        });

        assert.equal(scores.pair.count, 1);
        assert.equal(scores.matchedPair.count, 0);
        assert.equal(scores.unscoredMatchedPairCount, 0);
        assert.equal(scores.group.average, 63);
        assert.equal(scores.matchedGroup.average, 66);
    });
});

describe('matchScores 의 점수 없는 성사 쌍', () => {
    test('후보 행이 사라져 점수가 null 인 성사 쌍은 평균에서 빼고 따로 센다', () => {
        const scores = StatsCalc.matchScores({ pairScores: [70], acceptedPairScores: [80, null], groupScores: [] });

        assert.equal(scores.matchedPair.count, 1);
        assert.equal(scores.matchedPair.average, 80);
        assert.equal(scores.unscoredMatchedPairCount, 1);
    });
});

describe('activeGroupSizeRows', () => {
    const GENDER_LABELS = { MALE: '남성', FEMALE: '여성' };

    test('성사 그룹을 인원수로 묶고 인원수마다 성별 구성을 더한다', () => {
        const rows = StatsCalc.activeGroupSizeRows([
            { groupMatchId: 1, gender: 'MALE', count: 2 },
            { groupMatchId: 1, gender: 'FEMALE', count: 2 },
            { groupMatchId: 2, gender: 'MALE', count: 1 },
            { groupMatchId: 2, gender: 'FEMALE', count: 2 },
            { groupMatchId: 3, gender: 'FEMALE', count: 3 },
        ], GENDER_LABELS);

        assert.deepEqual(rows, [
            { label: '3명 · 2개', countsInColumnOrder: [1, 5, 0], totalCount: 6 },
            { label: '4명 · 1개', countsInColumnOrder: [2, 2, 0], totalCount: 4 },
        ]);
    });
});

describe('chatMessageStats', () => {
    const ROOM_TYPE_LABELS = { PERSONAL: '1:1', GROUP: '그룹' };
    const GENDER_LABELS = { MALE: '남성', FEMALE: '여성' };
    const OPENS_AT = '2026-10-23 00:00:00';
    const summary = (roomId, senderGender, messageCount, firstMessageAt, lastMessageAt) =>
        ({ roomId, opensAt: OPENS_AT, senderGender, messageCount, firstMessageAt, lastMessageAt });
    const chatStats = {
        chatRoomMemberCounts: [
            { roomId: 1, roomType: 'PERSONAL', count: 2 },
            { roomId: 2, roomType: 'GROUP', count: 4 },
            { roomId: 3, roomType: 'PERSONAL', count: 2 },
            { roomId: 9, roomType: 'REMATCH', count: 2 },
        ],
        messageSummaries: [
            summary(1, 'MALE', 8, '2026-10-23 03:00:00', '2026-10-24 10:00:00'),
            summary(1, 'FEMALE', 4, '2026-10-23 00:30:00', '2026-10-25 01:00:00'),
            summary(2, 'MALE', 60, '2026-10-23 07:00:00', '2026-10-23 20:00:00'),
            summary(9, 'MALE', 3, '2026-10-23 01:00:00', '2026-10-23 02:00:00'),
        ],
    };

    test('메시지 없는 방은 0개로 넣고 재매칭 방은 빼서 방당 메시지 수를 낸다', () => {
        const stats = StatsCalc.chatMessageStats(chatStats, ROOM_TYPE_LABELS, GENDER_LABELS);

        assert.equal(stats.roomCount, 3);
        assert.equal(stats.messagedRoomCount, 2);
        assert.equal(stats.averageMessageCount, 24);
        assert.equal(stats.medianMessageCount, 12);
        assert.deepEqual(stats.messageCountBins.map((bin) => bin.count), [1, 0, 1, 1, 0]);
    });

    test('첫 발신자는 가장 이른 성별이고 첫·마지막 메시지는 방이 열린 때부터 잰다', () => {
        const stats = StatsCalc.chatMessageStats(chatStats, ROOM_TYPE_LABELS, GENDER_LABELS);

        assert.equal(stats.medianFirstMessageHours, 3.75);
        assert.deepEqual(stats.firstMessageBins.map((bin) => bin.count), [1, 0, 1, 0, 0]);
        assert.deepEqual(stats.firstSenderGenderCounts, [
            { label: '남성', count: 1 },
            { label: '여성', count: 1 },
            { label: '미입력', count: 0 },
        ]);
        assert.deepEqual(stats.lastMessageBins.map((bin) => bin.count), [1, 0, 1, 0]);
    });

    test('메시지가 방 열린 시각보다 이르면 맨 앞 칸에 따로 센다', () => {
        const shifted = {
            chatRoomMemberCounts: [{ roomId: 1, roomType: 'PERSONAL', count: 2 }],
            messageSummaries: [summary(1, 'MALE', 1, '2026-10-22 23:00:00', '2026-10-22 23:00:00')],
        };

        const stats = StatsCalc.chatMessageStats(shifted, ROOM_TYPE_LABELS, GENDER_LABELS);

        assert.deepEqual(stats.firstMessageBins[0], { label: '열린 시각 전(시간 조정)', count: 1 });
        assert.equal(stats.beforeOpenRoomCount, 1);
    });
});

describe('matchResultRetention', () => {
    test('성사 안 됨은 전체 재참여에서 성사를 뺀다', () => {
        const rows = StatsCalc.matchResultRetention(
            { previousCount: 10, completedAgainCount: 6 },
            [{ matchingType: 'ONE_TO_ONE', count: 3 }, { matchingType: 'GROUP', count: 1 }],
            [{ previousMatchingType: 'ONE_TO_ONE', againMatchingType: 'GROUP', count: 3 }],
        );

        assert.deepEqual(rows, [
            { label: '성사됨', previousCount: 4, completedAgainCount: 3 },
            { label: '성사 안 됨', previousCount: 6, completedAgainCount: 3 },
        ]);
    });
});

describe('cohortRetention', () => {
    test('가입한 주로 묶고 가입한 주 회차와 가입 뒤 N번째 회차의 완주 수를 낸다', () => {
        const memberStats = {
            recentWeekCount: 3,
            recentJoinedAts: ['2026-10-06 10:00:00', '2026-10-08 22:00:00', '2026-10-14 09:00:00'],
        };
        const retentionStats = {
            cohortRoundWeeks: ['2026-10-19', '2026-10-05'],
            completionsWithJoinedAt: [
                { joinedAt: '2026-10-06 10:00:00', weekStartedOn: '2026-10-05' },
                { joinedAt: '2026-10-08 22:00:00', weekStartedOn: '2026-10-05' },
                { joinedAt: '2026-10-06 10:00:00', weekStartedOn: '2026-10-19' },
                { joinedAt: '2026-10-14 09:00:00', weekStartedOn: '2026-10-19' },
            ],
        };

        const { columnCount, rows } = StatsCalc.cohortRetention(memberStats, retentionStats, '2026-10-19');

        assert.equal(columnCount, 1);
        assert.deepEqual(rows, [
            {
                cohortWeek: '2026-10-05',
                joinedCount: 2,
                joinWeekCell: { completedCount: 2 },
                afterCells: [{ completedCount: 1 }],
            },
            {
                cohortWeek: '2026-10-12',
                joinedCount: 1,
                joinWeekCell: null,
                afterCells: [{ completedCount: 1 }],
            },
        ]);
    });

    test('아직 오지 않은 회차 칸은 null 이고 가입자가 없는 주는 줄이 없다', () => {
        const { columnCount, rows } = StatsCalc.cohortRetention(
            { recentWeekCount: 2, recentJoinedAts: ['2026-10-19 10:00:00'] },
            { cohortRoundWeeks: ['2026-10-12', '2026-10-19'], completionsWithJoinedAt: [] },
            '2026-10-19',
        );

        assert.equal(columnCount, 1);
        assert.deepEqual(rows, [
            { cohortWeek: '2026-10-19', joinedCount: 1, joinWeekCell: { completedCount: 0 }, afterCells: [null] },
        ]);
    });
});

describe('mutualPositiveRows', () => {
    test('유형마다 서로 평가한 쌍과 둘 다 4점 이상인 쌍을 센다', () => {
        const rows = StatsCalc.mutualPositiveRows([
            { matchType: 'PERSONAL', rating: 5, counterpartRating: 4, count: 2 },
            { matchType: 'PERSONAL', rating: 5, counterpartRating: 3, count: 1 },
            { matchType: 'GROUP', rating: 4, counterpartRating: 4, count: 1 },
        ], { PERSONAL: '1:1', GROUP: '그룹' });

        assert.deepEqual(rows, [
            { label: '1:1', pairCount: 3, bothPositivePairCount: 2 },
            { label: '그룹', pairCount: 1, bothPositivePairCount: 1 },
        ]);
    });
});

describe('ratingsByScoreBin', () => {
    test('점수 구간마다 평가 수와 긍정 수를 내고 점수 없는 평가는 맨 뒤에 둔다', () => {
        const rows = StatsCalc.ratingsByScoreBin([
            { matchType: 'PERSONAL', rating: 5, score: 61 },
            { matchType: 'PERSONAL', rating: 3, score: 64 },
            { matchType: 'GROUP', rating: 4, score: 82 },
            { matchType: 'PERSONAL', rating: 2, score: null },
        ]);

        assert.deepEqual(rows, [
            { label: '60점대', ratingCount: 2, positiveCount: 1 },
            { label: '70점대', ratingCount: 0, positiveCount: 0 },
            { label: '80점대', ratingCount: 1, positiveCount: 1 },
            { label: '점수 없음', ratingCount: 1, positiveCount: 0 },
        ]);
    });

    test('평가가 없으면 빈 목록이다', () => {
        assert.deepEqual(StatsCalc.ratingsByScoreBin([]), []);
    });
});

describe('givenRatingRetention', () => {
    test('준 별점 평균 4점을 기준으로 긍정·부정으로 나눠 재참여를 센다', () => {
        const rows = StatsCalc.givenRatingRetention([4, 4.5, 3.9, 2], [4.5, 2]);

        assert.deepEqual(rows, [
            { label: '평균 4점 이상', previousCount: 2, completedAgainCount: 1 },
            { label: '평균 4점 미만', previousCount: 2, completedAgainCount: 1 },
        ]);
    });
});

describe('candidateExposure', () => {
    test('받은 후보 수를 구간으로 묶고 5명을 넘게 받은 회원을 센다', () => {
        const exposure = StatsCalc.candidateExposure([1, 5, 6, 10, 11, 20]);

        assert.deepEqual(exposure, {
            memberCount: 6,
            overBaseMinimumCount: 6,
            overBaseMemberCount: 4,
            bins: [
                { label: '1~5명', count: 2 },
                { label: '6~10명', count: 2 },
                { label: '11명 이상', count: 2 },
            ],
        });
    });
});

describe('memberSearchPath', () => {
    test('회원 관리 검색에 #id 로 넘긴다', () => {
        assert.equal(StatsCalc.memberSearchPath(42), '/admin/members?q=%2342');
    });
});

describe('repeatedlyReportedMembers', () => {
    test('신고를 2건 이상 받은 회원만 남긴다', () => {
        const members = [
            { memberId: 1, nickname: 'a', count: 3 },
            { memberId: 2, nickname: 'b', count: 2 },
            { memberId: 3, nickname: 'c', count: 1 },
        ];

        assert.deepEqual(StatsCalc.repeatedlyReportedMembers(members).map((member) => member.memberId), [1, 2]);
    });
});
