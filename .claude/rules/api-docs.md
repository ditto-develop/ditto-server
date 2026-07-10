---
paths:
  - "api/**/*Controller.kt"
  - "api/src/test/kotlin/**/*ControllerTest.kt"
---
컨트롤러나 그 문서화 테스트를 수정하기 전에 `docs/testing/rest-docs.md`의 **필수 블록**을 먼저 읽어라 — `RestDocsTest`·`.withApiKey()`·작성 템플릿이 거기 있다.
컨트롤러를 바꾸면 짝 문서화 테스트(REST Docs)도 함께 갱신하라.
