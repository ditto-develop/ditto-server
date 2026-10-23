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

    const fillRateCard = (id, { part, whole, unit, wholeLabel }) => {
        byId(id).textContent = StatsCalc.percentText(part, whole);
        byId(`${id}Basis`).textContent = `${wholeLabel} ${countText(whole, unit)} 중 ${countText(part, unit)}`;
    };

    // 분모가 0인 장은 숫자 대신 안내만 보여 준다.
    const showSectionIfAny = (sectionName, hasData) => {
        byId(`${sectionName}EmptyNotice`).hidden = hasData;
        byId(`${sectionName}SectionBody`).hidden = !hasData;
    };

    const renderMemberTotals = (totals) => {
        byId('memberJoinedCount').textContent = countText(totals.joined, '명');
        const pendingBasis = byId('memberJoinedCountBasis');
        pendingBasis.hidden = totals.pending === 0;
        pendingBasis.textContent = `가입 미완료 ${countText(totals.pending, '명')} 제외`;
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

        byId('weeklyJoinTitle').textContent = `주별 신규 가입 · 고른 주까지 ${memberStats.recentWeekCount}주`;
        const weeks = StatsCalc.weeklyJoins(weekStartedOn, memberStats.recentWeekCount, memberStats.recentJoinedAts);
        fillRows('weeklyJoinRows', weeks, (week) => [
            cell(`${shortDate(week.weekStartedOn)} 주`),
            countCell(week.count),
        ]);
    };

    const renderLeaveReasons = ({ leaveReasonCounts }) => {
        const { leftCount, reasonCounts } = StatsCalc.leaveReasonCounts(leaveReasonCounts);
        byId('leaveReasonTitle').textContent = `탈퇴 사유 · ${countText(leftCount, '명')}`;
        byId('leaveReasonEmpty').hidden = leftCount > 0;
        if (leftCount === 0) {
            byId('leaveReasonShares').replaceChildren();
            return;
        }
        const options = { className: 'share-row wide-label-row stack-on-phone', unit: '명' };
        byId('leaveReasonShares').replaceChildren(...reasonCounts.map((row) => shareRow(row, leftCount, options)));
    };

    const activeRoundWeeksText = (roundWeeks) => {
        if (roundWeeks.length === 0) return '최근 회차 없음';
        return `최근 ${roundWeeks.length}회차(${roundWeeks.map(shortDate).reverse().join('·')} 주)`;
    };

    const renderAccountStatus = ({ countsByStatus, currentActiveRoundWeeks, recentlyCompletedActiveStatusCount }) => {
        fillShares(
            'accountStatusShares',
            StatsCalc.accountStatusCounts(countsByStatus, recentlyCompletedActiveStatusCount),
        );
        byId('accountStatusNote').textContent =
            `활성 = ${activeRoundWeeksText(currentActiveRoundWeeks)} 완주자. ` +
            '퀴즈 참여 장의 활성 회원은 고른 회차 기준이고 정지·탈퇴 회원도 세며 ' +
            '매칭 유형 조건도 걸려서 이 숫자와 다를 수 있습니다.';
    };

    // 팀원이 관리자 계정으로 실제 참여하면 숫자가 작게 보여서 빠진 인원을 알린다.
    const renderAdminExcludedNotice = ({ memberStats, quizStats }) => {
        const { adminMemberCount } = memberStats;
        const { adminCompletedCount } = quizStats;
        const notice = byId('adminExcludedNotice');
        notice.hidden = adminMemberCount === 0;
        const completedText = adminCompletedCount === 0
            ? ''
            : ` 이 회차에는 그중 ${countText(adminCompletedCount, '명')}이 퀴즈를 끝까지 풀었습니다.`;
        notice.textContent =
            `관리자 계정 ${countText(adminMemberCount, '명')}은 채팅 장을 뺀 모든 지표에서 빠집니다.${completedText}`;
    };

    const renderMemberSection = (
        { weekStartedOn, today, genderLabels, locationLabels, ageBandRanges, memberStats },
    ) => {
        const totals = StatsCalc.memberTotals(memberStats.countsByStatus);
        showSectionIfAny('member', totals.joined > 0);
        byId('statsToday').textContent = today;
        renderMemberTotals(totals);
        renderAccountStatus(memberStats);
        fillShares('locationShares', StatsCalc.locationCounts(memberStats.locationCounts, locationLabels));
        fillShares('genderShares', StatsCalc.genderCounts(memberStats.genderBirthDateCounts, genderLabels));
        renderAgeGenderTable(memberStats, genderLabels, ageBandRanges, today);
        renderJoinTables(memberStats, weekStartedOn, today);
        renderLeaveReasons(memberStats);
    };

    const renderQuizRates = ({ participantCount, startedCount }, activeMemberCount) => {
        byId('quizParticipantCount').textContent = countText(participantCount, '명');
        byId('quizParticipantCountBasis').textContent = `시작 ${countText(startedCount, '명')}`;
        fillRateCard('quizParticipationRate', {
            part: participantCount, whole: activeMemberCount, unit: '명', wholeLabel: '활성',
        });
        fillRateCard('quizCompletionRate', {
            part: participantCount, whole: startedCount, unit: '명', wholeLabel: '시작',
        });
    };

    const renderQuizSection = ({ genderLabels, quizMatchingTypeLabels, quizStats }) => {
        const { progressCounts, activeRoundCount, activeRoundWeeks, activeMemberCount } = quizStats;
        const participation = StatsCalc.quizParticipation(progressCounts, quizMatchingTypeLabels);
        showSectionIfAny('quiz', participation.startedCount > 0);

        byId('quizActiveRoundCount').textContent = activeRoundCount;
        byId('quizActiveRounds').textContent = activeRoundWeeksText(activeRoundWeeks);
        renderQuizRates(participation, activeMemberCount);
        // 매칭 유형을 하나만 고르면 막대가 늘 100% 하나라 숨긴다.
        byId('quizMatchingTypeSharesCard').hidden = Object.keys(quizMatchingTypeLabels).length < 2;
        fillShares('quizMatchingTypeShares', participation.participantCountsByType);
        fillGenderTable({
            headId: 'quizParticipantGenderHead',
            bodyId: 'quizParticipantGenderRows',
            firstColumnLabel: '매칭 유형',
            genderLabels,
            rows: StatsCalc.participantGenderRows(progressCounts, quizMatchingTypeLabels, genderLabels),
        });
        fillShares('quizCompletionDayShares', StatsCalc.completionDayCounts(quizStats.completedAts));
    };

    const renderMatchSection = (
        { weekStartedOn, today, genderLabels, quizMatchingTypeLabels, quizStats, matchStats },
    ) => {
        const funnel =
            StatsCalc.matchFunnel(quizStats.progressCounts, matchStats, quizMatchingTypeLabels, genderLabels);
        showSectionIfAny('match', funnel.completedCount > 0);
        byId('matchBeforeBatchNotice').hidden = !StatsCalc.isBeforeMatchingBatch(weekStartedOn, today);

        byId('matchMatchedCount').textContent = countText(funnel.matchedCount, '명');
        byId('matchMatchedCountBasis').textContent = `후보 ${countText(funnel.candidateCount, '명')}`;
        fillRateCard('matchRate', {
            part: funnel.matchedCount, whole: funnel.completedCount, unit: '명', wholeLabel: '참여',
        });
        byId('matchUnmatchedCount').textContent = countText(funnel.unmatchedCount, '명');
        // 매칭 유형을 하나만 고르면 표가 위 카드와 같은 숫자라 숨긴다.
        byId('matchFunnelCard').hidden = funnel.countsByType.length < 2;
        fillRows('matchFunnelRows', funnel.countsByType, (row) => [
            cell(row.label),
            countCell(row.completedCount),
            countCell(row.candidateCount),
            countCell(row.matchedCount),
            cell(StatsCalc.percentText(row.matchedCount, row.completedCount), 'num'),
        ]);
        fillGenderTable({
            headId: 'matchUnmatchedGenderHead',
            bodyId: 'matchUnmatchedGenderRows',
            firstColumnLabel: '매칭 유형',
            genderLabels,
            rows: funnel.unmatchedGenderRows,
        });
        renderMatchScores(matchStats, quizMatchingTypeLabels, genderLabels);
    };

    const scoreText = (score) => `${score.toFixed(1)}점`;

    // 성사 쪽 평균을 옆에 둬서 점수가 높을수록 성사되는지 본다.
    const scoreSummaryText = (summary, matchedSummary, unitLabel) => {
        if (summary.count === 0) return '이 회차 후보가 없습니다.';
        const matchedText = matchedSummary.count === 0
            ? '성사 없음'
            : `성사 ${matchedSummary.count}${unitLabel} 평균 ${scoreText(matchedSummary.average)}`;
        return `평균 ${scoreText(summary.average)} · 중앙값 ${scoreText(summary.median)} · ${matchedText}`;
    };

    const fillScoreCard = (idPrefix, { title, summary, matchedSummary, unitLabel }) => {
        byId(`${idPrefix}Title`).textContent = `${title} · ${summary.count}${unitLabel}`;
        byId(`${idPrefix}Summary`).textContent = scoreSummaryText(summary, matchedSummary, unitLabel);
        fillShares(`${idPrefix}Shares`, summary.bins, { unit: unitLabel });
    };

    const renderMatchScores = (matchStats, quizMatchingTypeLabels, genderLabels) => {
        const scores = StatsCalc.matchScores(matchStats);
        const showsOneToOne = Object.hasOwn(quizMatchingTypeLabels, 'ONE_TO_ONE');
        const showsGroup = Object.hasOwn(quizMatchingTypeLabels, 'GROUP');
        byId('matchPairScoreCard').hidden = !showsOneToOne;
        byId('matchGroupScoreCard').hidden = !showsGroup;
        byId('matchGroupSizeCard').hidden = !showsGroup;
        fillScoreCard('matchPairScore', {
            title: '1:1 후보 점수', summary: scores.pair, matchedSummary: scores.acceptedPair, unitLabel: '쌍',
        });
        fillScoreCard('matchGroupScore', {
            title: '그룹 후보 점수', summary: scores.group, matchedSummary: scores.activeGroup, unitLabel: '개',
        });
        fillGenderTable({
            headId: 'matchGroupSizeHead',
            bodyId: 'matchGroupSizeRows',
            firstColumnLabel: '인원 · 그룹 수',
            genderLabels,
            rows: StatsCalc.activeGroupSizeRows(matchStats.activeGroupMemberCounts, genderLabels),
        });
    };

    const rateCell = (part, whole) => cell(StatsCalc.percentText(part, whole), 'num');

    // 두 사람 방은 전원이 곧 양방향이라 그룹만 채운다.
    const allSentCell = (row) =>
        (row.isGroup ? rateCell(row.allSentCount, row.openedCount) : cell('-', 'num empty'));

    const chatTypeCells = (row, showsAllSent) => [
        cell(row.label),
        countCell(row.openedCount),
        rateCell(row.startedCount, row.openedCount),
        rateCell(row.twoWayCount, row.openedCount),
        ...(showsAllSent ? [allSentCell(row)] : []),
    ];

    // 평가의 매칭 유형 키와 채팅방 유형 키(PERSONAL·GROUP)가 같아 그 라벨을 쓴다.
    const hourText = (hours) => `${hours.toFixed(1)}시간`;

    const renderChatMessages = (chatStats, roomTypeLabels, genderLabels) => {
        const messages = StatsCalc.chatMessageStats(chatStats, roomTypeLabels, genderLabels);
        const options = { className: 'share-row wide-label-row', unit: '개' };
        byId('chatMessageCountTitle').textContent = `방당 메시지 수 · 방 ${countText(messages.roomCount, '개')}`;
        byId('chatMessageCountSummary').textContent = messages.averageMessageCount === null
            ? ''
            : `평균 ${messages.averageMessageCount.toFixed(1)}개 · 중앙값 ${messages.medianMessageCount.toFixed(1)}개`;
        fillShares('chatMessageCountShares', messages.messageCountBins, options);
        byId('chatFirstMessageTitle').textContent =
            `첫 메시지까지 · 대화가 있는 방 ${countText(messages.messagedRoomCount, '개')}`;
        byId('chatFirstMessageSummary').textContent = messages.medianFirstMessageHours === null
            ? '대화가 있는 방이 없습니다.'
            : `중앙값 ${hourText(messages.medianFirstMessageHours)}`;
        fillShares('chatFirstMessageShares', messages.firstMessageBins, options);
        fillShares('chatFirstSenderShares', messages.firstSenderGenderCounts, options);
        fillShares('chatLastMessageShares', messages.lastMessageBins, options);
    };

    const renderChatSection = ({ genderLabels, memberReviewMatchTypeLabels, chatStats }) => {
        const activity = StatsCalc.chatActivity(chatStats, memberReviewMatchTypeLabels);
        showSectionIfAny('chat', activity.rows.some((row) => row.openedCount > 0));

        byId('chatOpenedCount').textContent = countText(activity.openedCount, '개');
        fillRateCard('chatStartedRate', {
            part: activity.startedCount, whole: activity.openedCount, unit: '개', wholeLabel: '열린 방',
        });
        fillRateCard('chatTwoWayRate', {
            part: activity.twoWayCount, whole: activity.openedCount, unit: '개', wholeLabel: '열린 방',
        });
        const showsAllSent = activity.rows.some((row) => row.isGroup);
        byId('chatAllSentHead').hidden = !showsAllSent;
        byId('chatRematchNote').hidden = !activity.rows.some((row) => row.isRematch);
        fillRows('chatByTypeRows', activity.rows, (row) => chatTypeCells(row, showsAllSent));
        renderChatMessages(chatStats, memberReviewMatchTypeLabels, genderLabels);
    };

    const percentPointText = (value) => `${value.toFixed(1)}%p`;

    // 응답이 적어도 차이가 커 보여서 성별마다 응답자 수를 붙인다. 조각마다 줄이 갈리지 않게 묶는다.
    const genderGapLine = (choices, genderShares) => {
        const shares = genderShares.map((genderShare) => genderShare.share);
        const pieces = [
            `'${choices[0].label}' 선택`,
            ...genderShares.map(({ label, share, answerCount }) =>
                `${label} ${countText(answerCount, '명')} 중 ${share.toFixed(1)}%`),
            `차이 ${percentPointText(Math.max(...shares) - Math.min(...shares))}`,
        ];
        const line = element('p', 'muted');
        pieces.forEach((piece, index) => {
            if (index > 0) line.append(' · ');
            line.append(element('span', 'nowrap', piece));
        });
        return line;
    };

    const questionCountText = (answerCount, skew) => {
        const skewText = skew === null ? '' : ` · 쏠림 ${percentPointText(skew)}`;
        return `응답 ${countText(answerCount, '명')}${skewText}`;
    };

    const questionBlock = ({ displayOrder, question, answerCount, choices, skew, genderShares }) => {
        const block = element('div', 'question-block');
        const title = element('p', 'question-title', `${displayOrder}. ${question}`);
        title.append(element('span', 'question-count', questionCountText(answerCount, skew)));
        const choiceRows = choices
            .map((choice) => shareRow(choice, answerCount, { className: 'share-row wide-label-row stack-on-phone' }));
        block.append(title, ...choiceRows);
        if (genderShares.length >= 2) block.append(genderGapLine(choices, genderShares));
        return block;
    };

    const quizSetTitle = (matchingTypeLabel, questions, skewScore) => {
        const skewText = skewScore === null ? '' : ` · 쏠림 점수 ${percentPointText(skewScore)}`;
        return `${matchingTypeLabel} 퀴즈셋 · 문항 ${questions.length}개${skewText}`;
    };

    const renderQuestionSection = ({ genderLabels, quizMatchingTypeLabels, quizStats }) => {
        const distributions = StatsCalc.questionDistributions(quizStats.choiceAnswerCounts, genderLabels);
        showSectionIfAny('question', StatsCalc.sumCounts(quizStats.choiceAnswerCounts) > 0);
        byId('questionDistributions').replaceChildren(...distributions.map(({ matchingType, questions, skewScore }) => {
            const card = element('div', 'card');
            card.append(
                element('h3', null, quizSetTitle(quizMatchingTypeLabels[matchingType], questions, skewScore)),
                ...questions.map(questionBlock),
            );
            return card;
        }));
    };

    const renderMemberReviewSubmission = (submission, memberReviewCounts, memberReviewMatchTypeLabels) => {
        fillRateCard('memberReviewSubmissionRate', {
            part: submission.completedCount,
            whole: submission.memberReviewCount,
            unit: '건',
            wholeLabel: '평가지',
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
        const answerTitle = `응답 ${countText(StatsCalc.sumCounts(reviewAnswerCounts), '건')}`;
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

    const renderMemberReviewSection = (
        { memberReviewMatchTypeLabels, meetingStatusLabels, ratingScores, memberReviewStats },
    ) => {
        const { memberReviewCounts, reviewAnswerCounts } = memberReviewStats;
        const submission = StatsCalc.memberReviewSubmission(memberReviewCounts);
        showSectionIfAny('memberReview', submission.memberReviewCount > 0);
        renderMemberReviewSubmission(submission, memberReviewCounts, memberReviewMatchTypeLabels);
        renderMemberReviewAnswers(reviewAnswerCounts, meetingStatusLabels, ratingScores);
    };

    const retentionRateCells = (row) => [
        cell(row.label),
        countCell(row.previousCount),
        countCell(row.completedAgainCount),
        cell(StatsCalc.percentText(row.completedAgainCount, row.previousCount), 'num'),
    ];

    // 아직 오지 않은 회차는 비우고, 비율 옆에 완주자 수를 붙인다.
    const cohortCell = (roundCell, joinedCount) => {
        if (roundCell === null) return cell('-', 'num empty');
        const { completedCount } = roundCell;
        return cell(`${StatsCalc.percentText(completedCount, joinedCount)}(${completedCount})`, 'num');
    };

    const renderCohortRetention = (memberStats, retentionStats, weekStartedOn) => {
        const { columnCount, rows } = StatsCalc.cohortRetention(memberStats, retentionStats, weekStartedOn);
        const headRow = document.createElement('tr');
        headRow.append(
            headerCell('가입 주차'),
            headerCell('가입자(명)', 'num'),
            headerCell('가입한 주', 'num'),
            ...Array.from({ length: columnCount }, (_, index) => headerCell(`${index + 1}회차 뒤`, 'num')),
        );
        byId('retentionCohortHead').replaceChildren(headRow);
        fillRows('retentionCohortRows', rows, (row) => [
            cell(`${shortDate(row.cohortWeek)} 주`),
            countCell(row.joinedCount),
            cohortCell(row.joinWeekCell, row.joinedCount),
            ...row.afterCells.map((afterCell) => cohortCell(afterCell, row.joinedCount)),
        ]);
    };

    const renderRetentionSection = (
        { weekStartedOn, quizMatchingTypeLabels, allQuizMatchingTypeLabels, memberStats, retentionStats },
    ) => {
        const { previousRoundWeek, previousProgressCounts, completedAgainCounts } = retentionStats;
        const { previousCount, completedAgainCount, countsByType } =
            StatsCalc.retentionCounts(previousProgressCounts, completedAgainCounts, quizMatchingTypeLabels);
        showSectionIfAny('retention', previousCount > 0);
        if (previousRoundWeek === null) return;

        byId('retentionRounds').textContent =
            `지난 회차 ${shortDate(previousRoundWeek)} 주 → 고른 회차 ${shortDate(weekStartedOn)} 주`;
        fillRateCard('retentionRate', {
            part: completedAgainCount, whole: previousCount, unit: '명', wholeLabel: '지난 회차',
        });
        byId('retentionPreviousCount').textContent = countText(previousCount, '명');
        byId('retentionCompletedAgainCount').textContent = countText(completedAgainCount, '명');
        fillRows('retentionByTypeRows', countsByType, retentionRateCells);
        fillRows(
            'retentionMatchResultRows',
            StatsCalc.matchResultRetention(
                { previousCount, completedAgainCount },
                retentionStats.matchedPreviousCounts,
                retentionStats.matchedCompletedAgainCounts,
            ),
            retentionRateCells,
        );
        renderCohortRetention(memberStats, retentionStats, weekStartedOn);
        fillShares(
            'retentionTrackSwitchShares',
            StatsCalc.trackSwitchCounts(completedAgainCounts, quizMatchingTypeLabels, allQuizMatchingTypeLabels),
            { className: 'share-row wide-label-row' },
        );
    };

    const renderMemberReportSection = ({ memberReportReasonLabels, memberReportStatusLabels, memberReportStats }) => {
        const counts = StatsCalc.memberReportCounts(
            memberReportStats.memberReportCounts,
            memberReportReasonLabels,
            memberReportStatusLabels,
        );
        showSectionIfAny('memberReport', counts.memberReportCount > 0);
        byId('memberReportCount').textContent = countText(counts.memberReportCount, '건');
        byId('memberReportReceivedCount').textContent = countText(counts.receivedCount, '건');
        byId('memberReportProcessedCount').textContent = countText(counts.processedCount, '건');

        // 사유는 여러 개 고를 수 있어 비율의 분모가 사유 합이 아니라 신고 건수다.
        byId('memberReportReasonTitle').textContent =
            `사유별 · 신고 ${countText(counts.memberReportCount, '건')} 중 · 복수 선택`;
        const reasonOptions = { className: 'share-row wide-label-row stack-on-phone', unit: '건' };
        byId('memberReportReasonShares').replaceChildren(
            ...counts.reasonCounts.map((row) => shareRow(row, counts.memberReportCount, reasonOptions)),
        );
        const statusOptions = { className: 'share-row wide-label-row', unit: '건' };
        fillShares('memberReportStatusShares', counts.statusCounts, statusOptions);
    };

    const renderMemberBlockSection = ({ memberBlockCount }) => {
        showSectionIfAny('memberBlock', memberBlockCount > 0);
        byId('memberBlockCount').textContent = countText(memberBlockCount, '건');
    };

    // 이력 영역 밖이라 빈 상태 안내를 받지 않아서 분모가 0이면 카드에 직접 적는다.
    const renderPushDeviceRate = ({ activeMemberCount, activeMemberWithPushDeviceCount }) => {
        if (activeMemberCount === 0) {
            byId('pushDeviceRate').textContent = '활성 회원 없음';
            byId('pushDeviceRateBasis').textContent = '';
            return;
        }
        fillRateCard('pushDeviceRate', {
            part: activeMemberWithPushDeviceCount, whole: activeMemberCount, unit: '명', wholeLabel: '활성',
        });
    };

    const renderPushSendSection = ({ quizStats, pushSendHistoryCounts, pushSkipReasonLabels }) => {
        renderPushDeviceRate(quizStats);
        const counts = StatsCalc.pushSendCounts(pushSendHistoryCounts, pushSkipReasonLabels);
        showSectionIfAny('pushSend', counts.notificationCount > 0);

        byId('pushNotificationCount').textContent = countText(counts.notificationCount, '건');
        byId('pushNotificationCountBasis').textContent =
            `보냄 ${countText(counts.sentCount, '건')} · 건너뜀 ${countText(counts.skippedCount, '건')}`;
        fillRateCard('pushFailureRate', {
            part: counts.failedCount, whole: counts.sentCount, unit: '건', wholeLabel: '보냄',
        });
        const skipReasonOptions = { className: 'share-row wide-label-row', unit: '건' };
        fillShares('pushSkipReasonShares', counts.skipReasonCounts, skipReasonOptions);
        fillRows('pushSendByTypeRows', counts.typeRows, (row) => [
            cell(row.label),
            countCell(row.sentCount),
            countCell(row.failedCount),
            rateCell(row.failedCount, row.sentCount),
            countCell(row.skippedCount),
        ]);
    };

    renderAdminExcludedNotice(pageData);
    renderMemberSection(pageData);
    renderQuizSection(pageData);
    renderQuestionSection(pageData);
    renderMatchSection(pageData);
    renderChatSection(pageData);
    renderMemberReviewSection(pageData);
    renderRetentionSection(pageData);
    renderMemberReportSection(pageData);
    renderMemberBlockSection(pageData);
    renderPushSendSection(pageData);
})();
