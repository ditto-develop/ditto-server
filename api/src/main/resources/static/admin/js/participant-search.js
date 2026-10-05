// 퀴즈셋 참여 현황의 참여자 행을 거른다. '#'을 붙이면 회원 ID 정확 일치, 아니면 회원 ID·닉네임 부분 일치로 본다.
// 새로고침이나 다른 화면에서 돌아와도 이어지게 검색어를 ?q= 에 남긴다.
(() => {
    const input = document.getElementById('participantSearch');
    const noMatchMessage = document.getElementById('participantSearchNoMatch');
    const table = document.getElementById('participantTable');
    const count = document.getElementById('participantCount');
    if (!input || !noMatchMessage || !table || !count) return;

    const QUERY_PARAM = 'q';
    const rows = Array.from(document.querySelectorAll('tr[data-participant]'));

    const containsMemberId = (row, text) => row.dataset.memberId.includes(text);
    // 삭제된 회원 행에는 data-nickname 이 없다.
    const containsNickname = (row, text) => (row.dataset.nickname || '').toLowerCase().includes(text.toLowerCase());

    const matchesQuery = (row, query) => {
        if (query.startsWith('#')) {
            const memberId = query.slice(1);
            return memberId === '' || row.dataset.memberId === memberId;
        }
        return containsMemberId(row, query) || containsNickname(row, query);
    };

    const showMatchingRows = (query) => {
        let visibleCount = 0;
        rows.forEach((row) => {
            const isVisible = query === '' || matchesQuery(row, query);
            row.hidden = !isVisible;
            if (isVisible) visibleCount += 1;
        });
        count.textContent = query === '' ? `${rows.length}` : `${visibleCount} / ${rows.length}`;
        noMatchMessage.hidden = visibleCount > 0;
        table.hidden = visibleCount === 0;
    };

    const keepQueryInUrl = (query) => {
        const url = new URL(window.location.href);
        if (query === '') url.searchParams.delete(QUERY_PARAM);
        else url.searchParams.set(QUERY_PARAM, query);
        window.history.replaceState(null, '', url);
    };

    input.addEventListener('input', () => {
        const query = input.value.trim();
        showMatchingRows(query);
        keepQueryInUrl(query);
    });

    const initialQuery = (new URLSearchParams(window.location.search).get(QUERY_PARAM) || '').trim();
    if (initialQuery === '') return;
    input.value = initialQuery;
    showMatchingRows(initialQuery);
})();
