// 운영 지표를 그린다. 원본은 서버가 넣은 statsPageJson, 계산은 StatsCalc(stats-calc.js) 가 한다.
(() => {
    const pageData = JSON.parse(window.statsPageJson || 'null');
    if (!pageData) return;

    const shortDate = (isoDate) => `${Number(isoDate.slice(5, 7))}/${Number(isoDate.slice(8, 10))}`;

    const byId = (id) => document.getElementById(id);

    const element = (tagName, className, text) => {
        const created = document.createElement(tagName);
        if (className) created.className = className;
        if (text !== undefined) created.textContent = text;
        return created;
    };

    const cell = (text, className) => element('td', className, text);

    // 0 은 흐리게, 아직 오지 않은 날(null)은 '-' 로 그린다.
    const countCell = (count) => {
        if (count === null) return cell('-', 'num empty');
        return cell(StatsCalc.formatCount(count), count === 0 ? 'num empty' : 'num');
    };

    const headerCell = (text, className) => element('th', className, text);

    const fillRows = (tbodyId, rows, cellsOf, classNameOf = () => '') => {
        const rowElements = rows.map((row) => {
            const tr = document.createElement('tr');
            tr.className = classNameOf(row);
            tr.append(...cellsOf(row));
            return tr;
        });
        byId(tbodyId).replaceChildren(...rowElements);
    };

    const shareRow = ({ label, count }, whole, { className = 'share-row', unit = '명' } = {}) => {
        const fill = element('span');
        fill.style.width = `${StatsCalc.percent(count, whole)}%`;
        const bar = element('span', 'share-bar');
        bar.append(fill);
        const countText = `${StatsCalc.formatCount(count)}${unit}`;
        const valueText = whole === 0 ? countText : `${countText} · ${StatsCalc.percentText(count, whole)}`;
        const row = element('div', className);
        row.append(element('span', null, label), bar, element('span', 'share-value', valueText));
        return row;
    };

    const renderMemberTotals = (memberStats) => {
        const totals = StatsCalc.memberTotals(memberStats.countsByStatus);
        byId('memberJoinedCount').textContent = `${StatsCalc.formatCount(totals.joined)}명`;
        byId('memberCurrentCount').textContent = `${StatsCalc.formatCount(totals.current)}명`;
        byId('memberLeftCount').textContent = `${StatsCalc.formatCount(totals.left)}명`;
        byId('memberEmptyNotice').hidden = totals.joined > 0;
    };

    const renderGenderShares = (memberStats, genderLabels) => {
        const genderCounts = StatsCalc.genderCounts(memberStats.genderBirthDateCounts, genderLabels);
        const currentTotal = genderCounts.reduce((sum, { count }) => sum + count, 0);
        byId('genderShares').replaceChildren(...genderCounts.map((gender) => shareRow(gender, currentTotal)));
    };

    const fillGenderTable = ({ headId, bodyId, firstColumnLabel, genderLabels, rows }) => {
        const headRow = document.createElement('tr');
        headRow.append(
            headerCell(firstColumnLabel),
            ...StatsCalc.genderColumnLabels(genderLabels).map((label) => headerCell(label, 'num')),
            headerCell('합계', 'num'),
        );
        byId(headId).replaceChildren(headRow);
        fillRows(bodyId, rows, (row) => [
            cell(row.label),
            ...row.countsInColumnOrder.map(countCell),
            countCell(row.totalCount),
        ]);
    };

    const renderAgeGenderTable = (memberStats, genderLabels, ageBandRanges, today) => {
        fillGenderTable({
            headId: 'ageGenderHead',
            bodyId: 'ageGenderRows',
            firstColumnLabel: '연령대',
            genderLabels,
            rows: StatsCalc.ageGenderRows(memberStats.genderBirthDateCounts, genderLabels, ageBandRanges, today),
        });
    };

    const renderJoinTables = (memberStats, weekStartedOn, today) => {
        const days = StatsCalc.dailyJoins(weekStartedOn, today, memberStats.recentJoinedAts);
        fillRows(
            'dailyJoinRows',
            days,
            (day) => [cell(`${shortDate(day.date)} (${day.dayLabel})`), countCell(day.count)],
            (day) => (day.isToday ? 'today' : ''),
        );

        byId('weeklyJoinTitle').textContent = `고른 회차까지 ${memberStats.recentWeekCount}주 신규 가입`;
        const weeks = StatsCalc.weeklyJoins(weekStartedOn, memberStats.recentWeekCount, memberStats.recentJoinedAts);
        fillRows('weeklyJoinRows', weeks, (week) => [
            cell(`${shortDate(week.weekStartedOn)} 주`),
            countCell(week.count),
        ]);
    };

    const renderMemberSection = ({ weekStartedOn, today, genderLabels, ageBandRanges, memberStats }) => {
        byId('statsToday').textContent = today;
        renderMemberTotals(memberStats);
        renderGenderShares(memberStats, genderLabels);
        renderAgeGenderTable(memberStats, genderLabels, ageBandRanges, today);
        renderJoinTables(memberStats, weekStartedOn, today);
    };

    const renderQuizSection = ({ genderLabels, quizMatchingTypeLabels, quizStats }) => {
        const participation = StatsCalc.quizParticipation(quizStats.progressCounts, quizMatchingTypeLabels);
        const { participantCount, startedCount } = participation;
        const { activeMemberCount, activeRoundWeeks } = quizStats;

        byId('quizActiveRounds').textContent = activeRoundWeeks.length === 0
            ? '회차 없음'
            : activeRoundWeeks.map((week) => `${shortDate(week)} 주`).join(', ');
        byId('quizParticipantCount').textContent = `${StatsCalc.formatCount(participantCount)}명`;
        byId('quizParticipationRate').textContent = StatsCalc.percentText(participantCount, activeMemberCount);
        byId('quizParticipationRateBasis').textContent =
            `${StatsCalc.formatCount(participantCount)} / ${StatsCalc.formatCount(activeMemberCount)}명`;
        byId('quizCompletionRate').textContent = StatsCalc.percentText(participantCount, startedCount);
        byId('quizCompletionRateBasis').textContent =
            `${StatsCalc.formatCount(participantCount)} / ${StatsCalc.formatCount(startedCount)}명`;

        byId('quizMatchingTypeShares').replaceChildren(
            ...participation.participantsByType.map((typeCount) => shareRow(typeCount, participantCount)),
        );
        fillGenderTable({
            headId: 'quizParticipantGenderHead',
            bodyId: 'quizParticipantGenderRows',
            firstColumnLabel: '매칭 유형',
            genderLabels,
            rows: StatsCalc.participantGenderRows(quizStats.progressCounts, quizMatchingTypeLabels, genderLabels),
        });
    };

    const questionBlock = ({ displayOrder, question, answerCount, choices }) => {
        const block = element('div', 'question-block');
        const title = element('p', 'question-title', `${displayOrder}. ${question}`);
        title.append(element('span', 'question-count', `응답 ${StatsCalc.formatCount(answerCount)}`));
        const choiceRows = choices
            .map((choice) => shareRow(choice, answerCount, { className: 'share-row choice-row' }));
        block.append(title, ...choiceRows);
        return block;
    };

    const renderQuestionSection = ({ quizMatchingTypeLabels, quizStats }) => {
        const distributions = StatsCalc.questionDistributions(quizStats.choiceAnswerCounts);
        byId('questionEmptyNotice').hidden = distributions.length > 0;
        byId('questionDistributions').replaceChildren(...distributions.map(({ matchingType, questions }) => {
            const card = element('div', 'card');
            card.append(
                element('h3', null, `${quizMatchingTypeLabels[matchingType]} 퀴즈셋 · 문항 ${questions.length}개`),
                ...questions.map(questionBlock),
            );
            return card;
        }));
    };

    const renderMemberReviewSection = (pageData) => {
        const { memberReviewMatchTypeLabels, meetingStatusLabels, ratingScores, memberReviewStats } = pageData;
        const { memberReviewCounts, reviewAnswerCounts } = memberReviewStats;
        const submission = StatsCalc.reviewSubmission(memberReviewCounts);
        byId('reviewSubmissionRate').textContent = StatsCalc.percentText(submission.completed, submission.total);
        byId('reviewSubmissionRateBasis').textContent =
            `${StatsCalc.formatCount(submission.completed)} / ${StatsCalc.formatCount(submission.total)}건`;
        byId('reviewInProgressCount').textContent = `${StatsCalc.formatCount(submission.inProgress)}건`;
        byId('reviewNotStartedCount').textContent = `${StatsCalc.formatCount(submission.notStarted)}건`;

        const submissionByType = StatsCalc.reviewSubmissionByType(memberReviewCounts, memberReviewMatchTypeLabels);
        fillRows('reviewSubmissionByTypeRows', submissionByType, (row) => [
            cell(row.label),
            countCell(row.total),
            countCell(row.completed),
            cell(StatsCalc.percentText(row.completed, row.total), 'num'),
        ]);

        const answerTotal = reviewAnswerCounts.reduce((sum, { count }) => sum + count, 0);
        byId('reviewMeetingStatusShares').replaceChildren(
            ...StatsCalc.meetingStatusCounts(reviewAnswerCounts, meetingStatusLabels)
                .map((row) => shareRow(row, answerTotal, { className: 'share-row choice-row', unit: '건' })),
        );
        byId('reviewRatingShares').replaceChildren(
            ...StatsCalc.ratingCounts(reviewAnswerCounts, ratingScores)
                .map((row) => shareRow(row, answerTotal, { unit: '건' })),
        );
    };

    const renderRetentionSection = ({ weekStartedOn, quizMatchingTypeLabels, retentionStats }) => {
        const { previousRoundWeek, previousProgressCounts, completedAgainCounts } = retentionStats;
        byId('retentionEmptyNotice').hidden = previousRoundWeek !== null;
        byId('retentionRounds').textContent = previousRoundWeek === null
            ? '-'
            : `${shortDate(previousRoundWeek)} 주 → ${shortDate(weekStartedOn)} 주`;

        const { previousCount, againCount, byType } =
            StatsCalc.retention(previousProgressCounts, completedAgainCounts, quizMatchingTypeLabels);
        byId('retentionRate').textContent = StatsCalc.percentText(againCount, previousCount);
        byId('retentionRateBasis').textContent =
            `${StatsCalc.formatCount(againCount)} / ${StatsCalc.formatCount(previousCount)}명`;
        fillRows('retentionByTypeRows', byType, (row) => [
            cell(row.label),
            countCell(row.previousCount),
            countCell(row.againCount),
            cell(StatsCalc.percentText(row.againCount, row.previousCount), 'num'),
        ]);
    };

    const renderMemberReportSection = ({ memberReportReasonLabels, memberReportStatusLabels, memberReportStats }) => {
        const { total, reasonCounts, statusCounts } = StatsCalc.memberReportSummary(
            memberReportStats.memberReportCounts,
            memberReportReasonLabels,
            memberReportStatusLabels,
        );
        byId('memberReportCount').textContent = `${StatsCalc.formatCount(total)}건`;
        const shareOptions = { className: 'share-row choice-row', unit: '건' };
        const sharesOf = (rows) => rows.map((row) => shareRow(row, total, shareOptions));
        byId('memberReportReasonShares').replaceChildren(...sharesOf(reasonCounts));
        byId('memberReportStatusShares').replaceChildren(...sharesOf(statusCounts));
    };

    renderMemberSection(pageData);
    renderQuizSection(pageData);
    renderQuestionSection(pageData);
    renderMemberReviewSection(pageData);
    renderRetentionSection(pageData);
    renderMemberReportSection(pageData);
})();
