// QA 콘솔의 일반 폼(페이지가 새로 그려지는 POST). 비동기 폼([data-qa-async])은 qa-live.js 가 맡는다.
// 되돌릴 수 없는 응답은 data-confirm 으로 확인받고, 두 번 눌려 두 번째 요청의 거부가 첫 성공을 가리지 않게 버튼을 잠근다.
(() => {
    document.addEventListener('submit', (event) => {
        const form = event.target;
        if (form.matches('[data-qa-async]')) return;
        const confirmMessage = event.submitter?.dataset.confirm || form.dataset.confirm;
        if (confirmMessage && !window.confirm(confirmMessage)) {
            event.preventDefault();
            return;
        }
        // 누른 버튼의 name/value 가 함께 가도록 제출이 시작된 뒤에 잠근다.
        window.setTimeout(() => form.querySelectorAll('button').forEach((button) => { button.disabled = true; }));
    });
})();
