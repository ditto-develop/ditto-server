# vote (그룹 만남 투표)

그룹 채팅방에서 만날 장소·시간을 정하는 투표(피그마 `4.2.2~4.2.4`). 방당 하나를 열고, 멤버가 표를 던지고, 마감하면 결과가 확정된다.

## 용어

- `ChatVote` — 투표 한 건. 방(`room_id`)에 종속되고 상태(`OPEN`/`CLOSED`)를 가진다.
- `ChatVoteOption` — 제시된 선택지. `PLACE`(상호명·주소·좌표)와 `TIME`(`meet_at`)이 한 테이블에 산다.
- `ChatVoteChoice` — 제시된 선택지 가운데 회원이 **고른 것** 하나가 한 행. 결과 화면이 "누가 무엇에 투표했는지"를 보여주므로 카운트가 아니라 행으로 남긴다. 서버는 회원 ID(`voterIds`)만 내고 이름 표시는 FE 몫이다.
- `ChatVoteCloseReason` — 마감 사유(`MEMBER`/`ROOM_ENDED`). `closed_by`의 null을 해석하게 두지 않는다.
- cast — 투표하기. 요청에 담긴 집합이 그 회원의 **최종 선택**이다(치환).

## 핵심 규칙·불변식

- **방당 열린 투표는 하나다.** 화면 근거: "투표 생성 후 바텀시트에서 '투표 만들기' 삭제". `open_room_id` 유일키가 DB에서 강제한다(동작 원리는 `ChatVote` KDoc — SSOT). 생성은 멱등이 아니다 — 이미 있으면 8202로 거부한다(돌려주면 FE가 자기가 만든 투표로 착각한다).
- **그룹 방 전용이다**(8208). 1:1·재매칭 방에는 투표가 없다.
- **선택지는 생성 시 확정된다.** 장소·시간 각 2~10개, 이후 추가·삭제 없음. 중복은 PLACE=`(vote, type, label)`, TIME=`(vote, meet_at)` 유일키가 최종으로 막고, 요청 내 중복은 저장 전에 8205로 거른다(UK까지 가면 INTERNAL_ERROR로 샌다). TIME은 분 단위로 절삭해 판정한다.
- **입력 순 = `id` 오름차순.** 동표일 때 "입력 순으로 노출"하는 화면 규칙의 근거라 정렬 컬럼을 따로 두지 않는다.
- **cast는 치환이다.** 재투표가 같은 엔드포인트를 다시 부르므로 append면 표가 늘어난다. 치환은 **차집합**으로 한다 — 전량 삭제 후 재삽입은 Hibernate flush가 INSERT를 DELETE보다 먼저 실행해(`ActionQueue.OrderedActions`) 겹치는 표에서 유일키에 걸리는데, 재투표 화면이 기존 선택을 유지한 채 재제출하므로 겹침이 정상 경로다. 빈 배열 = 해당 유형 표 취소.
- **서버는 승자·비율을 계산하지 않는다.** 화면이 "동수면 모두 노출"이라 확정 항목이 하나로 좁혀지지 않고, 서버가 승자를 고르면 그 규칙이 곧 계약이 된다. `voterIds`와 입력 순 배열만 내고 1위·동표 판정은 클라이언트가 한다.
- **집계는 활성 멤버 기준이다.** `totalMembers`(분모)·`votedCount`·`voterIds` 모두 이탈하지 않은(`left_at IS NULL`) 멤버만 센다 — 이탈로 분자·분모가 함께 줄어 방향이 일관된다. 이탈자의 표는 행으로 남되 응답에서 빠진다. 이탈자·종료된 방의 **조회는 허용**한다(채팅의 읽기 전용 규칙과 동일).
- **마감은 두 경로다.** ① 멤버 누구나 close API(멱등 — 재요청은 성공으로 답하되 SYSTEM 메시지·알림은 실제로 닫은 요청만) ② 방이 끝나면(만료·해체) 열린 투표를 함께 닫는다(`ROOM_ENDED`, 마감자 없음, 메시지·알림 없음). cast별 실시간 브로드캐스트는 없다 — 채팅방에 뜨는 것은 생성 메시지와 마감 결과뿐이고, 집계는 화면 재진입·cast 응답으로 따라잡는다.
- **생성·마감은 SYSTEM 메시지를 남긴다** — `VOTE_CREATED:{voteId}`·`VOTE_CLOSED:{voteId}`. 투표 코드만 `:voteId` 접미가 붙는 이유는 배너·카드가 상세를 재조회하는 키라서다. 브로드캐스트 페이로드가 저장된 메시지인 이유: FE 소켓 수신부가 프레임을 `ChatMessage`로 파싱해 `id` 기준 병합하므로, 저장되지 않은 프레임은 병합·재접속 복구를 깨뜨린다.
- **시간 옵션은 `meetAt: LocalDateTime` 단일 필드다.** `date`·`time` 분리는 전역 `LocalTime` 포맷(`HH:mm:ss`)과 충돌해 FE의 `"19:00"` 요청이 400으로 떨어진다. 표시 문구(`dateLabel`)는 저장하지 않는다 — 로케일 문자열을 저장값으로 삼으면 나중에 못 바꾼다.

## 동시성 (ADR 0011)

잠금 순서는 **방 → 멤버 → 투표**로 고정한다 — 그룹 이탈 트랜잭션이 방 → 멤버를 이미 고정했고, 뒤집으면 해체 경로와 데드락이 난다.

- 생성: 방 행 잠금이 트랜잭션의 **첫 접근**(규칙 5). `ChatRoomAccessChecker`를 쓰지 않는 이유 — 그 안의 비잠금 `findById`가 낡은 방 인스턴스를 영속성 컨텍스트에 먼저 올린다.
- cast·close: 투표 행 잠금이 첫 접근. 방 행은 잠그지 않는다(수정하지 않고, 상태 판정은 잠금 획득 뒤에 읽어 최신 커밋을 본다).
- 방 종료 동반 마감: 방 행 잠금 보유 중 투표 행을 잠그고 닫는다(방 → 투표 순서 준수).
- 전원 투표 완료 자동 마감은 **도입하지 않았다.** 도입하면 `votedCount >= totalMembers` 판정에 멤버 카운트가 끼어 규칙 7(잠금 판정에 끼는 집계도 잠금 읽기)이 적용된다 — 방 → 멤버 → 투표 3단 잠금이 필요해진다.

## 핵심 파일

- 도메인: `domain/.../chat/entity`(`ChatVote`·`ChatVoteOption`·`ChatVoteChoice`·enum 3종), `repository`(`ChatVoteRepository` 등 3개). 스키마: `domain/db/V20260825175340_그룹 투표 테이블 추가.sql`.
- API: `api/.../chat/controller/ChatVoteController`, `service/ChatVoteService`(생성·조회·cast·close), `dto/ChatVote*`. 방 종료 연동: `service/ChatRoomEndService.closeOpenVoteQuietly`. 알림: `notification/notifier/ChatVoteClosedNotifier`.
- 설계 배경: `docs/plans/group-vote.md`(로컬 계획서 — Figma·FE 실측 근거와 검증 지적 반영 내역).
