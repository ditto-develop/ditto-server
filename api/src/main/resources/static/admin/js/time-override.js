// 시간 조정 화면. 고른 시각이 마감을 넘기거나 지난 시점이면 그때만 확인받는다. 문구는 서버가 data 속성으로 내린다.
(() => {
    const form = document.getElementById('timeOverrideForm');
    form.addEventListener('submit', (event) => {
        const { dataset } = form;
        const warnings = TimeMoveCalc.warningsOf({
            now: dataset.now,
            groupDeadline: dataset.groupDeadline,
            chatEnd: dataset.chatEnd,
            target: form.dateTime.value,
        });
        if (warnings.length === 0) return;

        const messages = warnings.map((warning) => dataset[`${warning}Message`]);
        const irreversibleNotice = TimeMoveCalc.isIrreversible(warnings) ? [dataset.irreversibleNotice] : [];
        if (!window.confirm([...messages, ...irreversibleNotice, dataset.moveQuestion].join(' '))) event.preventDefault();
    });
})();
