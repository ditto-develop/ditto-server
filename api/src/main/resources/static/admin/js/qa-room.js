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
    // 주기 갱신은 사용자가 고르던 영역(투표 체크 등)을 건너뛴다. 제출하면 다시 갱신된다.
    function swapLiveRegions(doc, { fromPolling = false } = {}) {
        const current = timeline();
        const stickToBottom = !current || isNearBottom(current);
        document.querySelectorAll('[data-qa-live]').forEach((region) => {
            if (fromPolling && region.dataset.editing === 'true') return;
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
            if (response.ok && !response.redirected) {
                swapLiveRegions(parse(await response.text()), { fromPolling: true });
            }
        } catch (ignored) {
            // 다음 주기에 다시 시도한다.
        } finally {
            busy = false;
        }
    }

    function setButtonsDisabled(form, disabled) {
        form.querySelectorAll('button').forEach((button) => { button.disabled = disabled; });
    }

    document.addEventListener('change', (event) => {
        const region = event.target.closest('[data-qa-live]');
        if (region) region.dataset.editing = 'true';
    });

    // 버튼마다 다른 엔드포인트(formaction)와 확인 문구(data-confirm)를 둘 수 있다.
    document.addEventListener('submit', async (event) => {
        const form = event.target;
        if (!form.matches('form[data-qa-async]')) return;
        event.preventDefault();
        const submitter = event.submitter;
        const confirmMessage = submitter?.dataset.confirm || form.dataset.confirm;
        if (confirmMessage && !window.confirm(confirmMessage)) return;

        const body = new FormData(form, submitter);
        const action = submitter?.hasAttribute('formaction') ? submitter.formAction : form.action;
        busy = true;
        setButtonsDisabled(form, true);
        try {
            const response = await fetch(action, { method: 'POST', body, credentials: 'same-origin' });
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
