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

        assert.deepEqual(totals, { joined: 8, current: 6, left: 2 });
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
    test('행은 늘 연령대 뒤에 그 외 나이·생년월일 없음이 오고 칸은 genderColumnLabels 순서다', () => {
        const genderBirthDateCounts = [{ gender: 'FEMALE', birthDate: '2004-01-01', count: 2 }];

        assert.deepEqual(StatsCalc.ageGenderRows(genderBirthDateCounts, GENDER_LABELS, AGE_BAND_RANGES, '2026-10-22'), [
            { label: '20대 초반', countsInColumnOrder: [0, 2, 0], totalCount: 2 },
            { label: '20대 중반', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
            { label: '그 외 나이', countsInColumnOrder: [0, 0, 0], totalCount: 0 },
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

const MATCHING_TYPE_LABELS = { ONE_TO_ONE: '1:1', GROUP: '그룹' };
const PROGRESS_COUNTS = [
    { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'MALE', count: 3 },
    { matchingType: 'ONE_TO_ONE', status: 'COMPLETED', gender: 'FEMALE', count: 2 },
    { matchingType: 'ONE_TO_ONE', status: 'IN_PROGRESS', gender: 'FEMALE', count: 4 },
    { matchingType: 'GROUP', status: 'COMPLETED', gender: null, count: 1 },
];

describe('quizParticipation', () => {
    test('참여자는 완주만, 시작은 진행 중까지 센다', () => {
        const participation = StatsCalc.quizParticipation(PROGRESS_COUNTS, MATCHING_TYPE_LABELS);

        assert.equal(participation.participantCount, 6);
        assert.equal(participation.startedCount, 10);
    });

    test('매칭 유형별 참여자는 matchingTypeLabels 순서로 0이어도 나온다', () => {
        const participation = StatsCalc.quizParticipation([], MATCHING_TYPE_LABELS);

        assert.deepEqual(participation.participantsByType, [
            { label: '1:1', count: 0 },
            { label: '그룹', count: 0 },
        ]);
    });
});

describe('participantGenderRows', () => {
    test('매칭 유형마다 완주한 회원만 성별 칸으로 나눈다', () => {
        assert.deepEqual(StatsCalc.participantGenderRows(PROGRESS_COUNTS, MATCHING_TYPE_LABELS, GENDER_LABELS), [
            { label: '1:1', countsInColumnOrder: [3, 2, 0], totalCount: 5 },
            { label: '그룹', countsInColumnOrder: [0, 0, 1], totalCount: 1 },
        ]);
    });
});

describe('questionDistributions', () => {
    const choiceRow = (quizSetId, quizId, quizDisplayOrder, choiceContent, answerCount) => ({
        quizSetId,
        matchingType: quizSetId === 1 ? 'ONE_TO_ONE' : 'GROUP',
        quizId,
        quizDisplayOrder,
        question: `질문${quizId}`,
        choiceId: quizId * 10 + choiceContent.length,
        choiceDisplayOrder: 1,
        choiceContent,
        answerCount,
    });

    test('퀴즈셋·문항 순서를 지키며 묶고 문항마다 응답 수를 더한다', () => {
        const distributions = StatsCalc.questionDistributions([
            choiceRow(1, 11, 1, 'A', 3),
            choiceRow(1, 11, 1, 'BB', 1),
            choiceRow(1, 12, 2, 'A', 0),
            choiceRow(1, 12, 2, 'BB', 0),
            choiceRow(2, 21, 1, 'A', 2),
        ]);

        assert.deepEqual(distributions.map((quizSet) => quizSet.matchingType), ['ONE_TO_ONE', 'GROUP']);
        assert.deepEqual(distributions[0].questions[0], {
            displayOrder: 1,
            question: '질문11',
            answerCount: 4,
            choices: [{ label: 'A', count: 3 }, { label: 'BB', count: 1 }],
        });
        assert.equal(distributions[0].questions[1].answerCount, 0);
    });

    test('문항이 없으면 빈 목록이다', () => {
        assert.deepEqual(StatsCalc.questionDistributions([]), []);
    });
});
