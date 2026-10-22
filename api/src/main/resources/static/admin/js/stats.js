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

    const countText = (count, unit) => `${StatsCalc.formatCount(count)}${unit}`;

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

    const shareRow = ({ label, count }, whole, { className = 'share-row', unit = '명' } = {}) => {
        const fill = element('span');
        fill.style.width = `${StatsCalc.percent(count, whole)}%`;
        const bar = element('span', 'share-bar');
        bar.append(fill);
        const valueText = whole === 0
            ? countText(count, unit)
            : `${countText(count, unit)} · ${StatsCalc.percentText(count, whole)}`;
        const value = element('span', count === 0 ? 'share-value empty' : 'share-value', valueText);
        const row = element('div', className);
        row.append(element('span', null, label), bar, value);
        return row;
    };

    const fillShares = (containerId, rows, options) => {
        const whole = StatsCalc.sumCounts(rows);
        byId(containerId).replaceChildren(...rows.map((row) => shareRow(row, whole, options)));
    };

    const fillRateCard = (id, { part, whole, unit, formula }) => {
        byId(id).textContent = StatsCalc.percentText(part, whole);
        byId(`${id}Basis`).textContent = `${StatsCalc.formatCount(part)} / ${countText(whole, unit)} · ${formula}`;
    };

    // 분모가 0인 장은 숫자 대신 안내만 보여 준다.
    const showSectionIfAny = (sectionName, hasData) => {
        byId(`${sectionName}EmptyNotice`).hidden = hasData;
        byId(`${sectionName}SectionBody`).hidden = !hasData;
    };

    const renderMemberTotals = (memberStats) => {
        const totals = StatsCalc.memberTotals(memberStats.countsByStatus);
        showSectionIfAny('member', totals.joined > 0);
        byId('memberJoinedCount').textContent = countText(totals.joined, '명');
        byId('memberCurrentCount').textContent = countText(totals.current, '명');
        byId('memberLeftCount').textContent = countText(totals.left, '명');
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
        fillShares('genderShares', StatsCalc.genderCounts(memberStats.genderBirthDateCounts, genderLabels));
        renderAgeGenderTable(memberStats, genderLabels, ageBandRanges, today);
        renderJoinTables(memberStats, weekStartedOn, today);
    };

    const renderQuizRates = ({ participantCount, startedCount }, activeMemberCount) => {
        byId('quizParticipantCount').textContent = countText(participantCount, '명');
        byId('quizParticipantCountBasis').textContent = `시작 ${countText(startedCount, '명')}`;
        fillRateCard('quizParticipationRate', {
            part: participantCount, whole: activeMemberCount, unit: '명', formula: '참여 ÷ 활성',
        });
        fillRateCard('quizCompletionRate', {
            part: participantCount, whole: startedCount, unit: '명', formula: '완주 ÷ 시작',
        });
    };

    const renderQuizSection = ({ genderLabels, quizMatchingTypeLabels, quizStats }) => {
        const { progressCounts, activeRoundCount, activeRoundWeeks, activeMemberCount } = quizStats;
        const participation = StatsCalc.quizParticipation(progressCounts, quizMatchingTypeLabels);
        showSectionIfAny('quiz', participation.startedCount > 0);

        byId('quizActiveRounds').textContent = `최근 ${activeRoundCount}회차(${activeRoundWeeks.join(', ')})`;
        renderQuizRates(participation, activeMemberCount);
        fillShares('quizMatchingTypeShares', participation.participantCountsByType);
        fillGenderTable({
            headId: 'quizParticipantGenderHead',
            bodyId: 'quizParticipantGenderRows',
            firstColumnLabel: '매칭 유형',
            genderLabels,
            rows: StatsCalc.participantGenderRows(progressCounts, quizMatchingTypeLabels, genderLabels),
        });
    };

    const questionBlock = ({ displayOrder, question, answerCount, choices }) => {
        const block = element('div', 'question-block');
        const title = element('p', 'question-title', `${displayOrder}. ${question}`);
        title.append(element('span', 'question-count', `응답 ${countText(answerCount, '명')}`));
        const choiceRows = choices
            .map((choice) => shareRow(choice, answerCount, { className: 'share-row wide-label-row' }));
        block.append(title, ...choiceRows);
        return block;
    };

    const renderQuestionSection = ({ quizMatchingTypeLabels, quizStats }) => {
        const distributions = StatsCalc.questionDistributions(quizStats.choiceAnswerCounts);
        byId('questionEmptyNotice').textContent = distributions.length === 0
            ? '이 회차에 활성 퀴즈셋이 없습니다.'
            : '이 회차에 조건에 맞는 답이 없습니다.';
        showSectionIfAny('question', StatsCalc.sumCounts(quizStats.choiceAnswerCounts) > 0);
        byId('questionDistributions').replaceChildren(...distributions.map(({ matchingType, questions }) => {
            const card = element('div', 'card');
            card.append(
                element('h3', null, `${quizMatchingTypeLabels[matchingType]} 퀴즈셋 · 문항 ${questions.length}개`),
                ...questions.map(questionBlock),
            );
            return card;
        }));
    };

    const renderMemberReviewSubmission = (memberReviewCounts, memberReviewMatchTypeLabels) => {
        const submission = StatsCalc.memberReviewSubmission(memberReviewCounts);
        showSectionIfAny('memberReview', submission.memberReviewCount > 0);
        fillRateCard('memberReviewSubmissionRate', {
            part: submission.completedCount,
            whole: submission.memberReviewCount,
            unit: '건',
            formula: '완료 ÷ 평가지',
        });
        byId('memberReviewInProgressCount').textContent = countText(submission.inProgressCount, '건');
        byId('memberReviewNotStartedCount').textContent = countText(submission.notStartedCount, '건');

        const submissionByType =
            StatsCalc.memberReviewSubmissionByType(memberReviewCounts, memberReviewMatchTypeLabels);
        fillRows('memberReviewSubmissionByTypeRows', submissionByType, (row) => [
            cell(row.label),
            countCell(row.memberReviewCount),
            countCell(row.completedCount),
            cell(StatsCalc.percentText(row.completedCount, row.memberReviewCount), 'num'),
        ]);
    };

    const renderMemberReviewAnswers = (reviewAnswerCounts, meetingStatusLabels, ratingScores) => {
        const answerTitle = `확정된 답 ${countText(StatsCalc.sumCounts(reviewAnswerCounts), '건')}`;
        byId('memberReviewMeetingStatusTitle').textContent = `만남 상태 · ${answerTitle}`;
        byId('memberReviewRatingTitle').textContent = `별점 · ${answerTitle}`;
        const shareOptions = { className: 'share-row wide-label-row', unit: '건' };
        fillShares(
            'memberReviewMeetingStatusShares',
            StatsCalc.meetingStatusCounts(reviewAnswerCounts, meetingStatusLabels),
            shareOptions,
        );
        fillShares('memberReviewRatingShares', StatsCalc.ratingCounts(reviewAnswerCounts, ratingScores), shareOptions);
    };

    const renderMemberReviewSection = (pageData) => {
        const { memberReviewMatchTypeLabels, meetingStatusLabels, ratingScores, memberReviewStats } = pageData;
        renderMemberReviewSubmission(memberReviewStats.memberReviewCounts, memberReviewMatchTypeLabels);
        renderMemberReviewAnswers(memberReviewStats.reviewAnswerCounts, meetingStatusLabels, ratingScores);
    };

    const renderRetentionSection = ({ weekStartedOn, quizMatchingTypeLabels, retentionStats }) => {
        const { previousRoundWeek, previousProgressCounts, completedAgainCounts } = retentionStats;
        const { previousCount, completedAgainCount, byType } =
            StatsCalc.retentionCounts(previousProgressCounts, completedAgainCounts, quizMatchingTypeLabels);
        byId('retentionEmptyNotice').textContent = previousRoundWeek === null
            ? '고른 회차 앞에 회차가 없습니다.'
            : '지난 회차 참여자가 없습니다.';
        showSectionIfAny('retention', previousCount > 0);
        if (previousRoundWeek === null) return;

        byId('retentionRounds').textContent =
            `지난 회차 ${shortDate(previousRoundWeek)} 주 → 고른 회차 ${shortDate(weekStartedOn)} 주`;
        fillRateCard('retentionRate', {
            part: completedAgainCount, whole: previousCount, unit: '명', formula: '다시 참여 ÷ 지난 회차',
        });
        byId('retentionPreviousCount').textContent = countText(previousCount, '명');
        byId('retentionCompletedAgainCount').textContent = countText(completedAgainCount, '명');
        fillRows('retentionByTypeRows', byType, (row) => [
            cell(row.label),
            countCell(row.previousCount),
            countCell(row.completedAgainCount),
            cell(StatsCalc.percentText(row.completedAgainCount, row.previousCount), 'num'),
        ]);
    };

    const renderMemberReportSection = (pageData) => {
        const { memberReportReasonLabels, memberReportStatusLabels, memberReportStats } = pageData;
        const counts = StatsCalc.memberReportCounts(
            memberReportStats.memberReportCounts,
            memberReportReasonLabels,
            memberReportStatusLabels,
        );
        showSectionIfAny('memberReport', counts.memberReportCount > 0);
        byId('memberReportCount').textContent = countText(counts.memberReportCount, '건');
        byId('memberReportReceivedCount').textContent = countText(counts.receivedCount, '건');
        byId('memberReportReviewedCount').textContent = countText(counts.reviewedCount, '건');

        // 사유는 여러 개 고를 수 있어 비율의 분모가 사유 합이 아니라 신고 건수다.
        byId('memberReportReasonTitle').textContent =
            `사유별 · 신고 ${countText(counts.memberReportCount, '건')} 중 (여러 개 선택)`;
        const shareOptions = { className: 'share-row wide-label-row', unit: '건' };
        byId('memberReportReasonShares').replaceChildren(
            ...counts.reasonCounts.map((row) => shareRow(row, counts.memberReportCount, shareOptions)),
        );
        fillShares('memberReportStatusShares', counts.statusCounts, shareOptions);
    };

    renderMemberSection(pageData);
    renderQuizSection(pageData);
    renderQuestionSection(pageData);
    renderMemberReviewSection(pageData);
    renderRetentionSection(pageData);
    renderMemberReportSection(pageData);
})();
