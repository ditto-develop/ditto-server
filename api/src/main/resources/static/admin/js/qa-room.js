// QA 콘솔 방 화면. 폼은 페이지를 다시 그리지 않고 보내고, [data-qa-live] 영역만 주기적으로 갈아 끼운다.
// 서버는 일반 폼 POST → 리다이렉트로 답하므로 fetch 가 따라간 방 화면 HTML 에서 같은 id 영역을 골라 바꾼다.
(() => {
    const POLL_INTERVAL_MS = 3000;
    const NEAR_BOTTOM_PX = 40;
    let busy = false;

    const timeline = () => document.getElementById('qa-timeline');

    function isNearBottom(element) {
        return element.scrollHeight - element.scrollTop - element.clientHeight < NEAR_BOTTOM_PX;
    }

    function scrollTimelineToBottom() {
        const element = timeline();
        if (element) element.scrollTop = element.scrollHeight;
    }

    // 스크롤을 올려 지난 대화를 보는 중이면 갱신이 바닥으로 끌어내리지 않는다.
    function swapLiveRegions(doc) {
        const current = timeline();
        const stickToBottom = !current || isNearBottom(current);
        document.querySelectorAll('[data-qa-live]').forEach((region) => {
            const next = doc.getElementById(region.id);
            if (next) region.replaceWith(next);
        });
        if (stickToBottom) scrollTimelineToBottom();
    }

    function swapAlerts(doc) {
        const next = doc.getElementById('qa-alerts');
        const current = document.getElementById('qa-alerts');
        if (next && current) current.replaceWith(next);
    }

    function parse(html) {
        return new DOMParser().parseFromString(html, 'text/html');
    }

    async function refresh() {
        if (busy || document.hidden) return;
        busy = true;
        try {
            const response = await fetch(window.location.pathname, { credentials: 'same-origin' });
            // 세션이 끝나 로그인으로 넘어간 응답은 버린다.
            if (response.ok && !response.redirected) swapLiveRegions(parse(await response.text()));
        } catch (ignored) {
            // 다음 주기에 다시 시도한다.
        } finally {
            busy = false;
        }
    }

    function setButtonsDisabled(form, disabled) {
        form.querySelectorAll('button').forEach((button) => { button.disabled = disabled; });
    }

    document.addEventListener('submit', async (event) => {
        const form = event.target;
        if (!form.matches('form[data-qa-async]')) return;
        event.preventDefault();
        if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) return;

        const body = new FormData(form, event.submitter);
        busy = true;
        setButtonsDisabled(form, true);
        try {
            const response = await fetch(form.action, { method: 'POST', body, credentials: 'same-origin' });
            const doc = parse(await response.text());
            swapAlerts(doc);
            swapLiveRegions(doc);
            if (form.id === 'qa-composer') {
                form.elements.content.value = '';
                form.elements.content.focus();
                scrollTimelineToBottom();
            }
        } finally {
            setButtonsDisabled(form, false);
            busy = false;
        }
    });

    scrollTimelineToBottom();
    window.setInterval(refresh, POLL_INTERVAL_MS);
})();
