// 운영 지표를 그린다. 원본은 서버가 넣은 statsPageJson, 계산은 StatsCalc(stats-calc.js) 가 한다.
(() => {
    const pageData = JSON.parse(window.statsPageJson || 'null');
    if (!pageData) return;

    const shortDate = (isoDate) => `${Number(isoDate.slice(5, 7))}/${Number(isoDate.slice(8, 10))}`;

    const byId = (id) => document.getElementById(id);

    const cell = (text, className) => {
        const td = document.createElement('td');
        td.textContent = text;
        if (className) td.className = className;
        return td;
    };

    // 0 은 흐리게, 아직 오지 않은 날(null)은 '-' 로 그린다.
    const countCell = (count) => {
        if (count === null) return cell('-', 'num empty');
        return cell(StatsCalc.formatCount(count), count === 0 ? 'num empty' : 'num');
    };

    const headerCell = (text, className) => {
        const th = document.createElement('th');
        th.textContent = text;
        if (className) th.className = className;
        return th;
    };

    const fillRows = (tbodyId, rows, cellsOf, classNameOf = () => '') => {
        const rowElements = rows.map((row) => {
            const tr = document.createElement('tr');
            tr.className = classNameOf(row);
            tr.append(...cellsOf(row));
            return tr;
        });
        byId(tbodyId).replaceChildren(...rowElements);
    };

    const shareRow = ({ label, count }, whole) => {
        const row = document.createElement('div');
        row.className = 'share-row';
        const name = document.createElement('span');
        name.textContent = label;
        const bar = document.createElement('span');
        bar.className = 'share-bar';
        const fill = document.createElement('span');
        fill.style.width = `${StatsCalc.percent(count, whole)}%`;
        bar.append(fill);
        const value = document.createElement('span');
        value.className = 'share-value';
        const countText = `${StatsCalc.formatCount(count)}명`;
        value.textContent = whole === 0 ? countText : `${countText} · ${StatsCalc.percentText(count, whole)}`;
        row.append(name, bar, value);
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

    const renderQuizSection = ({ genderLabels, matchingTypeLabels, quizStats }) => {
        const participation = StatsCalc.quizParticipation(quizStats.progressCounts, matchingTypeLabels);
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
            rows: StatsCalc.participantGenderRows(quizStats.progressCounts, matchingTypeLabels, genderLabels),
        });
    };

    renderMemberSection(pageData);
    renderQuizSection(pageData);
})();
