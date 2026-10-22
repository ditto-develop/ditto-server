// 운영 지표 화면의 계산. DOM 을 건드리지 않아 node --test 로 검증한다(api/src/test/js).
const StatsCalc = (() => {
    const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];
    const GENDER_UNKNOWN_LABEL = '미입력';
    const AGE_OUTSIDE_BANDS_LABEL = '그 외 나이';
    const BIRTH_DATE_UNKNOWN_LABEL = '생년월일 없음';

    // 'yyyy-MM-dd' 를 브라우저 시간대와 상관없이 다루려고 UTC 로 계산한다.
    const toUtcDate = (isoDate) => new Date(`${isoDate}T00:00:00Z`);
    const addDays = (isoDate, days) => {
        const date = toUtcDate(isoDate);
        date.setUTCDate(date.getUTCDate() + days);
        return date.toISOString().slice(0, 10);
    };
    const mondayOf = (isoDate) => addDays(isoDate, -((toUtcDate(isoDate).getUTCDay() + 6) % 7));
    // 서버는 일시를 'yyyy-MM-dd HH:mm:ss' 로 준다.
    const dateOf = (dateTime) => dateTime.slice(0, 10);

    const countBy = (items, keyOf) => items.reduce((counts, item) => {
        const key = keyOf(item);
        return counts.set(key, (counts.get(key) || 0) + 1);
    }, new Map());
    const sumCountsBy = (countRows, keyOf) => countRows.reduce((counts, countRow) => {
        const key = keyOf(countRow);
        return counts.set(key, (counts.get(key) || 0) + countRow.count);
    }, new Map());
    const groupBy = (items, keyOf) => items.reduce((groups, item) => {
        const key = keyOf(item);
        return groups.set(key, [...(groups.get(key) || []), item]);
    }, new Map());

    const sumCounts = (countRows) => countRows.reduce((sum, countRow) => sum + countRow.count, 0);
    const completedOnly = (progressCounts) => progressCounts.filter((countRow) => countRow.status === 'COMPLETED');

    const formatCount = (count) => count.toLocaleString('ko-KR');
    const percent = (part, whole) => (whole === 0 ? 0 : (part * 100) / whole);
    const percentText = (part, whole) => (whole === 0 ? '-' : `${percent(part, whole).toFixed(1)}%`);

    const memberTotals = (countsByStatus) => {
        const joined = Object.values(countsByStatus).reduce((sum, count) => sum + count, 0);
        const left = countsByStatus.LEFT || 0;
        return { joined, current: joined - left, left };
    };

    // 오늘 이후 날짜는 아직 오지 않아 count 가 null 이다.
    const dailyJoins = (weekStartedOn, today, joinedAts) => {
        const countsByDate = countBy(joinedAts, dateOf);
        return DAY_LABELS.map((dayLabel, index) => {
            const date = addDays(weekStartedOn, index);
            const count = date > today ? null : countsByDate.get(date) || 0;
            return { date, dayLabel, isToday: date === today, count };
        });
    };

    const weekStartsUntil = (lastWeekStartedOn, weekCount) =>
        Array.from({ length: weekCount }, (_, index) => addDays(lastWeekStartedOn, -7 * (weekCount - 1 - index)));

    const weeklyJoins = (lastWeekStartedOn, weekCount, joinedAts) => {
        const countsByWeek = countBy(joinedAts, (joinedAt) => mondayOf(dateOf(joinedAt)));
        return weekStartsUntil(lastWeekStartedOn, weekCount)
            .map((weekStartedOn) => ({ weekStartedOn, count: countsByWeek.get(weekStartedOn) || 0 }));
    };

    // 만 나이. 서버 AgeBand.birthDateRange 와 경계가 같다.
    const ageOn = (birthDate, today) => {
        const [birthYear, birthMonth, birthDay] = birthDate.split('-').map(Number);
        const [year, month, day] = today.split('-').map(Number);
        const hadBirthday = month > birthMonth || (month === birthMonth && day >= birthDay);
        return year - birthYear - (hadBirthday ? 0 : 1);
    };

    const genderCounts = (genderBirthDateCounts, genderLabels) => {
        const countsByGender = sumCountsBy(genderBirthDateCounts, (countRow) => countRow.gender);
        return [
            ...Object.entries(genderLabels)
                .map(([gender, label]) => ({ label, count: countsByGender.get(gender) || 0 })),
            { label: GENDER_UNKNOWN_LABEL, count: countsByGender.get(null) || 0 },
        ];
    };

    // 성별 칸은 genderLabels 순서이고 마지막 칸이 미입력이다.
    const genderRow = (label, countRows, genderLabels) => {
        const countsByGender = sumCountsBy(countRows, (countRow) => countRow.gender);
        const countsInColumnOrder = [...Object.keys(genderLabels), null]
            .map((gender) => countsByGender.get(gender) || 0);
        const totalCount = countsInColumnOrder.reduce((sum, count) => sum + count, 0);
        return { label, countsInColumnOrder, totalCount };
    };

    const ageGenderRows = (genderBirthDateCounts, genderLabels, ageBandRanges, today) => {
        const ageLabelOf = (birthDate) => {
            if (!birthDate) return BIRTH_DATE_UNKNOWN_LABEL;
            const age = ageOn(birthDate, today);
            const band = ageBandRanges.find(({ minAge, maxAge }) => age >= minAge && age <= maxAge);
            return band ? band.label : AGE_OUTSIDE_BANDS_LABEL;
        };
        const countRowsByAgeLabel = groupBy(genderBirthDateCounts, (countRow) => ageLabelOf(countRow.birthDate));
        const labels = [...ageBandRanges.map((band) => band.label), AGE_OUTSIDE_BANDS_LABEL, BIRTH_DATE_UNKNOWN_LABEL];
        return labels.map((label) => genderRow(label, countRowsByAgeLabel.get(label) || [], genderLabels));
    };

    // 참여는 그 회차 퀴즈를 끝까지 낸 것, 시작은 진행 행이 있는 것(첫 답을 낸 것)이다.
    const quizParticipation = (progressCounts, matchingTypeLabels) => {
        const completedCounts = completedOnly(progressCounts);
        const participantCountsByType = sumCountsBy(completedCounts, (countRow) => countRow.matchingType);
        return {
            participantCount: sumCounts(completedCounts),
            startedCount: sumCounts(progressCounts),
            participantsByType: Object.entries(matchingTypeLabels)
                .map(([matchingType, label]) => ({ label, count: participantCountsByType.get(matchingType) || 0 })),
        };
    };

    const participantGenderRows = (progressCounts, matchingTypeLabels, genderLabels) => {
        const completedCounts = completedOnly(progressCounts);
        return Object.entries(matchingTypeLabels).map(([matchingType, label]) => genderRow(
            label,
            completedCounts.filter((countRow) => countRow.matchingType === matchingType),
            genderLabels,
        ));
    };

    const genderColumnLabels = (genderLabels) => [...Object.values(genderLabels), GENDER_UNKNOWN_LABEL];

    // 서버가 퀴즈셋·문항·선택지 순서로 준 행을 그 순서 그대로 묶는다.
    const questionDistributions = (choiceAnswerCounts) =>
        [...groupBy(choiceAnswerCounts, (countRow) => countRow.quizSetId).values()].map((quizSetRows) => ({
            matchingType: quizSetRows[0].matchingType,
            questions: [...groupBy(quizSetRows, (countRow) => countRow.quizId).values()].map((choiceRows) => ({
                displayOrder: choiceRows[0].quizDisplayOrder,
                question: choiceRows[0].question,
                answerCount: choiceRows.reduce((sum, choiceRow) => sum + choiceRow.answerCount, 0),
                choices: choiceRows
                    .map((choiceRow) => ({ label: choiceRow.choiceContent, count: choiceRow.answerCount })),
            })),
        }));

    // 평가지는 한 사람 × 한 채팅방이다. 대상 일부만 답한 진행 중은 제출로 치지 않는다.
    const reviewSubmission = (memberReviewCounts) => {
        const countsByStatus = sumCountsBy(memberReviewCounts, (countRow) => countRow.status);
        return {
            total: sumCounts(memberReviewCounts),
            completed: countsByStatus.get('COMPLETED') || 0,
            inProgress: countsByStatus.get('IN_PROGRESS') || 0,
            notStarted: countsByStatus.get('NOT_STARTED') || 0,
        };
    };

    const reviewSubmissionByType = (memberReviewCounts, chatRoomTypeLabels) =>
        Object.entries(chatRoomTypeLabels).map(([chatRoomType, label]) => {
            const typeCounts = memberReviewCounts.filter((countRow) => countRow.matchType === chatRoomType);
            const { total, completed } = reviewSubmission(typeCounts);
            return { label, total, completed };
        });

    const meetingStatusCounts = (answerCounts, meetingStatusLabels) => {
        const countsByMeetingStatus = sumCountsBy(answerCounts, (countRow) => countRow.meetingStatus);
        return Object.entries(meetingStatusLabels)
            .map(([meetingStatus, label]) => ({ label, count: countsByMeetingStatus.get(meetingStatus) || 0 }));
    };

    const ratingCounts = (answerCounts, ratingScores) => {
        const countsByRating = sumCountsBy(answerCounts, (countRow) => countRow.rating);
        return ratingScores.map((rating) => ({ label: `${rating}점`, count: countsByRating.get(rating) || 0 }));
    };

    // 매칭 유형은 지난 회차에 푼 유형이다. 이번 회차엔 어느 유형이든 완주하면 다시 참여한 것이다.
    const retention = (previousProgressCounts, completedAgainCounts, matchingTypeLabels) => {
        const previousCompletedCounts = completedOnly(previousProgressCounts);
        const previousCountsByType = sumCountsBy(previousCompletedCounts, (countRow) => countRow.matchingType);
        const againCountsByType = sumCountsBy(completedAgainCounts, (countRow) => countRow.matchingType);
        const byType = Object.entries(matchingTypeLabels).map(([matchingType, label]) => ({
            label,
            previousCount: previousCountsByType.get(matchingType) || 0,
            againCount: againCountsByType.get(matchingType) || 0,
        }));
        return {
            previousCount: byType.reduce((sum, row) => sum + row.previousCount, 0),
            againCount: byType.reduce((sum, row) => sum + row.againCount, 0),
            byType,
        };
    };

    // 사유는 여러 개 고를 수 있어 사유별 수를 더하면 신고 건수보다 클 수 있다.
    const memberReportSummary = (memberReportCounts, reasonLabels, statusLabels) => {
        const countsByStatus = sumCountsBy(memberReportCounts, (countRow) => countRow.status);
        const countWithReason = (reason) =>
            sumCounts(memberReportCounts.filter((countRow) => countRow.reasons.includes(reason)));
        return {
            total: sumCounts(memberReportCounts),
            reasonCounts: Object.entries(reasonLabels)
                .map(([reason, label]) => ({ label, count: countWithReason(reason) })),
            statusCounts: Object.entries(statusLabels)
                .map(([status, label]) => ({ label, count: countsByStatus.get(status) || 0 })),
        };
    };

    return {
        formatCount,
        percent,
        percentText,
        memberTotals,
        dailyJoins,
        weeklyJoins,
        ageOn,
        genderCounts,
        genderColumnLabels,
        ageGenderRows,
        quizParticipation,
        participantGenderRows,
        questionDistributions,
        reviewSubmission,
        reviewSubmissionByType,
        meetingStatusCounts,
        ratingCounts,
        retention,
        memberReportSummary,
    };
})();

if (typeof module !== 'undefined') module.exports = StatsCalc;
