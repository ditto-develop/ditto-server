package com.ditto.api.chat.dto

/**
 * 투표 생성·마감의 처리 결과. 컨트롤러가 [systemMessage]로 후속 처리를 정한다 —
 * 값이 있으면 방 토픽으로 브로드캐스트한다(실제로 만들었거나 실제로 닫은 요청만, 커밋 뒤에).
 * 마감 멱등 재요청이면 null 이다.
 *
 * 브로드캐스트 페이로드가 저장된 메시지인 이유: FE 소켓 수신부가 프레임을 ChatMessage 로
 * 파싱해 id 기준으로 병합하므로, 저장되지 않은(id 없는) 프레임은 병합·재접속 복구를 깨뜨린다.
 */
data class ChatVoteChangeResult(
    val detail: ChatVoteDetailResponse,
    val systemMessage: ChatMessageResponse?,
)
