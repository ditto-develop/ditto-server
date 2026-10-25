// 폰 폭의 위쪽 메뉴는 한 줄 가로 스크롤이다. 지금 화면의 메뉴를 보이는 곳으로 밀고, 더 밀 수 있는 쪽 끝만 흐리게 한다.
(() => {
    const nav = document.querySelector('.sidebar .nav');
    if (!nav) return;

    const markScrollEdges = () => {
        nav.classList.toggle('at-start', nav.scrollLeft <= 0);
        nav.classList.toggle('at-end', nav.scrollLeft + nav.clientWidth >= nav.scrollWidth - 1);
    };

    if (nav.scrollWidth > nav.clientWidth) {
        nav.querySelector('a.active')?.scrollIntoView({ inline: 'center', block: 'nearest' });
    }
    markScrollEdges();
    nav.addEventListener('scroll', markScrollEdges, { passive: true });
})();
