// 운영 지표 화면의 계산. DOM 을 건드리지 않아 node --test 로 검증한다(api/src/test/js).
const StatsCalc = (() => {
    const DAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];
    const GENDER_UNKNOWN_LABEL = '미입력';
    const AGE_OUTSIDE_BANDS_LABEL = '그 외';
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
    // labels 순서대로 0이어도 한 행씩 낸다.
    const labeledCounts = (countsByKey, labels) =>
        Object.entries(labels).map(([key, label]) => ({ label, count: countsByKey.get(key) || 0 }));

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
            ...labeledCounts(countsByGender, genderLabels),
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

    const genderColumnLabels = (genderLabels) => [...Object.values(genderLabels), GENDER_UNKNOWN_LABEL];

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

    const quizParticipation = (progressCounts, quizMatchingTypeLabels) => {
        const completedCounts = completedOnly(progressCounts);
        const participantCountsByType = sumCountsBy(completedCounts, (countRow) => countRow.matchingType);
        return {
            participantCount: sumCounts(completedCounts),
            startedCount: sumCounts(progressCounts),
            participantCountsByType: labeledCounts(participantCountsByType, quizMatchingTypeLabels),
        };
    };

    const participantGenderRows = (progressCounts, quizMatchingTypeLabels, genderLabels) => {
        const completedCounts = completedOnly(progressCounts);
        return Object.entries(quizMatchingTypeLabels).map(([matchingType, label]) => genderRow(
            label,
            completedCounts.filter((countRow) => countRow.matchingType === matchingType),
            genderLabels,
        ));
    };

    // 서버가 퀴즈셋·문항·선택지 순서로 준 행을 그 순서 그대로 묶는다.
    const questionDistributions = (choiceAnswerCounts) =>
        [...groupBy(choiceAnswerCounts, (countRow) => countRow.quizSetId).values()].map((quizSetRows) => ({
            matchingType: quizSetRows[0].matchingType,
            questions: [...groupBy(quizSetRows, (countRow) => countRow.quizId).values()].map((choiceRows) => ({
                displayOrder: choiceRows[0].quizDisplayOrder,
                question: choiceRows[0].question,
                answerCount: sumCounts(choiceRows),
                choices: choiceRows
                    .map((choiceRow) => ({ label: choiceRow.choiceContent, count: choiceRow.count })),
            })),
        }));

    const memberReviewSubmission = (memberReviewCounts) => {
        const countsByStatus = sumCountsBy(memberReviewCounts, (countRow) => countRow.status);
        return {
            memberReviewCount: sumCounts(memberReviewCounts),
            completedCount: countsByStatus.get('COMPLETED') || 0,
            inProgressCount: countsByStatus.get('IN_PROGRESS') || 0,
            notStartedCount: countsByStatus.get('NOT_STARTED') || 0,
        };
    };

    const memberReviewSubmissionByType = (memberReviewCounts, memberReviewMatchTypeLabels) =>
        Object.entries(memberReviewMatchTypeLabels).map(([matchType, label]) => {
            const typeCounts = memberReviewCounts.filter((countRow) => countRow.matchType === matchType);
            const { memberReviewCount, completedCount } = memberReviewSubmission(typeCounts);
            return { label, memberReviewCount, completedCount };
        });

    const meetingStatusCounts = (reviewAnswerCounts, meetingStatusLabels) =>
        labeledCounts(sumCountsBy(reviewAnswerCounts, (countRow) => countRow.meetingStatus), meetingStatusLabels);

    const ratingCounts = (reviewAnswerCounts, ratingScores) => {
        const countsByRating = sumCountsBy(reviewAnswerCounts, (countRow) => countRow.rating);
        return ratingScores.map((rating) => ({ label: `${rating}점`, count: countsByRating.get(rating) || 0 }));
    };

    const retentionCounts = (previousProgressCounts, completedAgainCounts, quizMatchingTypeLabels) => {
        const previousCountsByType =
            sumCountsBy(completedOnly(previousProgressCounts), (countRow) => countRow.matchingType);
        const completedAgainCountsByType =
            sumCountsBy(completedAgainCounts, (countRow) => countRow.previousMatchingType);
        const byType = Object.entries(quizMatchingTypeLabels).map(([matchingType, label]) => ({
            label,
            previousCount: previousCountsByType.get(matchingType) || 0,
            completedAgainCount: completedAgainCountsByType.get(matchingType) || 0,
        }));
        return {
            previousCount: byType.reduce((sum, row) => sum + row.previousCount, 0),
            completedAgainCount: byType.reduce((sum, row) => sum + row.completedAgainCount, 0),
            byType,
        };
    };

    const memberReportCounts = (memberReportCountRows, reasonLabels, statusLabels) => {
        const countsByStatus = sumCountsBy(memberReportCountRows, (countRow) => countRow.status);
        const countsByReason = new Map(Object.keys(reasonLabels).map((reason) => [
            reason,
            sumCounts(memberReportCountRows.filter((countRow) => countRow.reasons.includes(reason))),
        ]));
        const memberReportCount = sumCounts(memberReportCountRows);
        const receivedCount = countsByStatus.get('RECEIVED') || 0;
        return {
            memberReportCount,
            receivedCount,
            reviewedCount: memberReportCount - receivedCount,
            reasonCounts: labeledCounts(countsByReason, reasonLabels),
            statusCounts: labeledCounts(countsByStatus, statusLabels),
        };
    };

    return {
        sumCounts,
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
        memberReviewSubmission,
        memberReviewSubmissionByType,
        meetingStatusCounts,
        ratingCounts,
        retentionCounts,
        memberReportCounts,
    };
})();

if (typeof module !== 'undefined') module.exports = StatsCalc;
