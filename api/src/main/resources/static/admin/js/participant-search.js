// 퀴즈셋 참여 현황에서 회원 열(닉네임·회원 ID)로 참여자 행을 거른다. '#'으로 시작하면 회원 ID만 비교한다.
(() => {
    const input = document.getElementById('participantSearch');
    const emptyMessage = document.getElementById('participantSearchEmpty');
    if (!input) return;
    const rows = Array.from(document.querySelectorAll('tr[data-participant]'));

    const matches = (row, query) => {
        if (query.startsWith('#')) return row.dataset.memberId.includes(query.slice(1));
        const nickname = (row.dataset.nickname || '').toLowerCase();
        return row.dataset.memberId.includes(query) || nickname.includes(query.toLowerCase());
    };

    input.addEventListener('input', () => {
        const query = input.value.trim();
        let shownCount = 0;
        rows.forEach((row) => {
            const shown = query === '' || matches(row, query);
            row.hidden = !shown;
            if (shown) shownCount += 1;
        });
        emptyMessage.hidden = shownCount > 0;
    });
})();
