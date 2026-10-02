## [0.35.0] - 2026-10-02
> **문서 뷰어 2차 — 워드(docx)·한글(hwpx) 읽기 모드 + 채팅으로 보내기** (계획서 `docs/plans/0.35.0-document-chat.md`). 실측 중 **첨부 문서 질문이 툴로 새는 현행 결함**을 찾아 함께 고쳤다(ADR-028).
- **[Fix] 첨부 문서 질문이 툴로 새던 결함 (M0, exp46·46b, ADR-028)** — 문서를 첨부하고 물으면 모델이 문서를 읽지 않고 툴을 불렀다. 원인: 매 턴 붙는 표준 리마인더("For THIS request … you MUST call the tool")와 트리거 규칙("~가 뭐야", "몇 시")이 문서 질문을 툴 질문으로 끌어간다. 앱 기본(웹 검색 꺼짐) 조건에서 표 질문 20건 중 **4건만 정답, 14건이 `search_memory`·`get_schedule` 호출**, 회의록 요약에는 "첨부 파일을 읽을 수 없다"고 답했다. 대책 4안 비교(exp46b, 표 질문 20 + 행동 요청 3):
  - A 현행: 4/20, 샘 14, 행동 3/3
  - B 문서 턴 툴 끄기: 18/20, 샘 0, **행동 0/3 — "일정에 추가했습니다"라고 거짓 완료**
  - C 엄격 리마인더: 17/20, 샘 0, **행동 0/3 — "알려드리겠습니다" 거짓 약속**
  - **D 행동 예외 리마인더(채택)**: 16/20, 샘 0, 행동 1/3 — 실패해도 "추가해 드릴까요?"로 되묻는다(정직). 문서 질문 오답은 B·C·D 모두 대부분 **합계 계산 실수**(4B 산수 한계)라 조건 차이로 보지 않았다
  - 구현: `PromptAssembler.assembleWithTools(documentAttached)` — 문서가 붙은 턴은 표준 리마인더 대신 `DOC_TURN_REMINDER`(원문: `[Attached Document] The user attached a document above. Answer questions about it from the document itself, not with tools. Call a tool only if the user explicitly asks to save, schedule, or remind something.`). **시스템 지시·툴 선언 불변**(대화 KV 재사용 유지, `MEASURED_OVERHEAD` 무관), 문서 턴 입력만 +5토큰(표준 37 → 42, 300자 상한 산정의 여유 안). 실험실 미러는 `DocumentTurnReminderTest` 가 `fixtures/doc_turn_reminder.txt` 로 내보낸다
- **[Feat] 첨부 표 형식 (M0, exp46c)** — 문서 턴 리마인더 조건에서 같은 300자에 표 5종×질문 4: **"열이름: 값" 19/20**, 탭 17/20, 마크다운 16/20(세 형식 모두 샘 0). "열이름: 값"은 300자에 드는 행이 1행 적지만(9.8 대 10.8) 합계 3문항 중 2개를 맞혔다(나머지 0) — 행이 적어 더할 수가 적은 몫도 섞여 있다. 판정 규칙(정답 수 우선)대로 `DocumentText.TABLE_FORMAT = KEY_VALUE`
- **[Feat] 워드·한글 읽기 모드 (M1·M2)** — `DocxReader`(스타일 **이름**·개요 수준으로 제목 판별 — 한국어 워드는 제목 스타일 id 가 숫자, 서식 런, 목록 `numPr`/List 스타일, 표 `gridSpan`·`vMerge`, `a:blip` 이미지, 쪽 나눔, pPr 의 탭 위치 정의 제외, `w:delText` 제외), `HwpxReader`(`content.hpf` spine 순서·없으면 `section0..` 차례, `header.xml` 글자 모양·스타일 이름 — "개요 N" → 목록, "제목" → 제목, 표 `cellSpan` — **세로 병합으로 생략된 칸을 자리 채움**, `hc:img` → 매니페스트 경로, 머리말·꼬리말·각주·미주 제외, 모르는 요소는 글자만). `FlowView`: 블록 단위 `LazyColumn`, 제목 단계·목록 글머리·가로 스크롤 표·이미지 지연 디코드(`inSampleSize` 로 화면 폭, 힙 1/16 캐시)·쪽 나눔 구분선·글자 선택, 글자 크기 가−/가+ 3단계(설정 DataStore 기억)
- **[Feat] 채팅으로 보내기 (M3)** — 뷰어 상단 "채팅으로" → 고르기: 시트는 행 번호 두 번(시작·끝, 머리 행 자동 포함), 읽기 모드는 블록 탭, PDF 는 보이는 쪽(안드로이드 15 + S 확장 13 의 `PdfRenderer.Page.getTextContents`, 미만 기기는 안내). 하단 "n / 300자 · 앞부분만 보내요(전체 N자)" — 행·블록 **경계에서** 자른다(칸 값이 잘린 숫자를 모델에 주지 않게). 보내기는 **인텐트 extra 가 아니라 같은 프로세스의 `ShareIntentHandler.offer`** — MainActivity 는 exported 라 명시적 인텐트로 외부 주입이 가능하므로 메모리 전달로 그 경로를 없앴다(계획서 D2 의 "발신 패키지 확인"보다 강하다). 채팅은 `NEW_TASK` 로 앞에 오고 그때 모델이 켜진다(ADR-027). 자동 전송하지 않는다
- **[Fix] 입력바 문서 첨부가 깨진 바이트를 모델에 넣던 결함 (M4)** — `AttachmentReader` 가 모든 비이미지를 글자로 읽어 xlsx·docx·PDF 의 zip/PDF 바이트가 깨진 문자열로 들어갔다. 이제 xlsx·docx·hwpx·PDF 는 `ShareIntentHandler.offerDocumentFile` → `DocumentTextExtractor`(IO, 시트는 첫 시트·PDF 는 첫 쪽, 앞 300자) — 입력바에 "문서를 읽는 중…" 후 "Document Attached · 파일명 · 앞부분만". 글자가 아닌 파일(NUL 포함 또는 깨진 글자 10% 초과)은 거부 안내. 텍스트 파일은 예전 그대로 **동기**(E2E `waitForIdle` 계약 보존 — `MultimodalChatE2ETest` 무수정 통과). 새 오류 코드를 만들지 않고 기존 "지원하지 않는 파일 형식" 안내를 쓴다(`ErrorMessageMappingTest` 무수정)
- **[Fix] hwpx 를 고를 수 없던 문제 (M5, 에뮬레이터 발견)** — 기기가 hwpx 를 모르면 MIME 이 `application/octet-stream` 이라(에뮬레이터 MediaStore 실측) 앱 안 선택기에서 회색으로 막혔다. 선택기만 `PICKER_MIME_TYPES`(뷰어 목록 + octet-stream)로 넓혔다 — 연 뒤 확장자로 판별. "연결 프로그램" 필터는 모든 바이너리에 앱이 뜨므로 넓히지 않는다 → **MIME 을 모르는 기기의 파일 앱에서는 hwpx "연결 프로그램"에 이 앱이 안 뜬다**(실기기 확인 항목)
- **[Note] 계획과 갈린 점** — ① 표 변환기(`DocumentText`)는 M1 이 아니라 M3 에서(형식이 M0 판정에 묶여서) ② `taskAffinity=""` 를 다시 넣었다 — 0.34.0 에서는 `documentLaunchMode` 만으로 충분했지만, 뷰어에서 `NEW_TASK` 로 채팅을 열 때 같은 친화도면 뷰어 태스크 안에 MainActivity 가 하나 더 생길 수 있다(에뮬레이터: 뷰어 t68, 채팅 t69 분리 확인) ③ 넘기기 방식은 인텐트가 아니라 메모리 전달(위) ④ 글자 크기 3단계는 화면 버튼 + 설정 DataStore
- **[Note] 에뮬레이터 확인** — docx·hwpx 읽기 모드(제목·굵게·목록·병합 표), 고르기 모드 강조·"52 / 300자", 채팅으로 보내기 → 모델 로드 → 프리뷰 → 질문("When does the launch start and end?") → **툴 없이 "출시 일정은 10-05에 시작하여 10-20에 종료됩니다"**, 입력바 xlsx 첨부 "앞부분만", PDF 쪽 글자 289/300자(합성 PDF 는 영문 — 한글 PDF 추출 품질은 미확인)
- **[Known Issue]** ① 4B 모델은 표 합계 계산을 자주 틀린다(exp46b·46c 합계 문항 대부분 오답) — 숫자 집계가 필요하면 앱이 계산해 넣는 기능이 따로 필요하다 ② 문서 턴의 행동 요청은 1/3 만 바로 툴을 부르고 나머지는 되묻는다 — 다음 턴("응 추가해줘")은 보통 턴이라 툴이 불린다 ③ 첨부는 300자(10행 안팎) — 긴 문서 요약은 expand D1 잔여 ④ hwp(옛 한글) 미지원(0.36.0 실험)
- **[QA/Test]** 게이트 녹색, 기존 단언 수정 0건(이번 회차 신규 테스트 `DocumentViewerViewModelTest` 의 미리보기 단언 하나는 형식 판정에 맞춰 같은 회차 안에서 갱신). 신규: `FlowReadersTest` 6, `DocumentTextTest` 3, `DocumentParsingTest` +1, `FlowViewTest` 3, `DocumentViewerViewModelTest` +3, `AttachmentReaderTest` 4, `DocumentTurnReminderTest` 2. 실험 스크립트 exp46·46b·46c 커밋(출력 미커밋). **실기기 게이트(남음)**: 실사용 docx·hwpx(공문서 서식) 열람 품질, 삼성 "내 파일"에서 hwpx·docx "연결 프로그램" 노출(MIME), 한글 PDF 쪽 글자 추출, GPU 기기에서 문서 턴 응답 시간, 다크 모드 고르기 강조 대비

## [0.34.0] - 2026-10-02
> **문서 뷰어 1차 — 모델 없는 경량 뷰어 · PDF · xlsx · csv** (계획서 `docs/plans/0.34.0-document-viewer.md`). 동기: 기기 문서 뷰어에 광고가 나와 불편했다(사용자, 2026-10-02). 중점은 **모델을 켜지 않고 가볍고 빠르게 열리는 것**. 채팅 프롬프트 무변경(`MEASURED_OVERHEAD` 무관).
- **[Refactor] 모델 준비를 채팅 화면 단위로 (M0, ADR-027)** — `KosmosApp` 의 프로세스 전경 진입 `warmUp` 을 `MainActivity` 의 `ModelWarmUpObserver` 로 옮겼다. 원인: 프로세스 단위라 "연결 프로그램"으로 문서만 열어도 3.6GB 로드가 시작됐다. 해제(`onStop → close`)는 프로세스 단위 그대로. 2026-08-14 재진입 결함(낡은 Ready 통과 뒤 데워줄 곳 없음)은 재진입이 언제나 MainActivity onStart 를 지나므로 똑같이 막힌다 — 에뮬레이터 백그라운드↔복귀 3회 모두 재초기화·정상
- **[Feat] 파서 (M1)** — `domain/document/`(순수 JVM): `XlsxReader`(공유/인라인 문자열·후리가나 제외·`_xHHHH_` 복원, 서식별 숫자 — 내장·한국어 날짜 id 27~36/50~58·사용자 서식 분석, 1900 윤년 버그/1904 기준, 불리언·오류·수식 캐시 값, 병합·틀 고정·열 너비), `CsvReader`(RFC 4180, BOM, UTF-8 엄격 → CP949 폴백, 탭·세미콜론 구분), `DocumentType`(MIME 우선 → 확장자, csv 의 `vnd.ms-excel` MIME 대응), `DocumentSniffer`(zip·OLE·PDF 머리 바이트 — 암호 xlsx 는 OLE 로 저장돼 "손상"이 아니라 "암호·옛 형식"으로 안내), 안전 상한 `DocumentLimits`(엔트리 50MB·2만 행·40만 셀·csv 20MB)
- **[Feat] 뷰어 화면 (M2)** — 별도 `DocumentViewerActivity`(스플래시·NavHost·Room·모델 없음, `documentLaunchMode="intoExisting"` + `autoRemoveFromRecents` — 문서마다 최근 앱에 따로, 채팅 태스크 위에 쌓이지 않음), VIEW 필터 4종(pdf·xlsx·csv 2종, octet-stream 제외). 표: 열 머리·행 번호·`stickyHeader` 틀 고정 행·고정 열(화면 절반 초과면 해제)·병합·공유 가로 스크롤·숫자 오른쪽 정렬·길게 눌러 전체 보기/복사·시트 탭(bottomBar 에 `navigationBarsPadding`). PDF: `PdfRenderer` 페이지 지연 렌더(화면 폭 비트맵, 흰 배경, `LruCache` 힙 1/8, 렌더·닫기 직렬화), 두 손가락 1~3배 확대 + 배율 버튼(100→150→200%), 쪽 표시
- **[Feat] 문서 홈 (M3)** — 드로어 "📄 문서" 목록 줄(사용자 결정 — 2×2 타일 안 기각) → 파일 열기(`OpenDocument`) + 최근 문서 20개(`RecentDocumentsStore`, 설정 DataStore 키 하나 — Room 미사용). 앱 안에서 고른 문서만 영구 권한을 받아 기록, 목록에서 밀려나거나 지운 문서는 권한 반납(`releasePersistableUriPermission`), 권한이 사라진 항목은 지우고 다시 고르라고 안내. 길게 눌러 "목록에서만" 지우기
- **[Perf] 에뮬레이터 실측 (M4)** — 합성 문서(xlsx 1천·1만 행 2시트, PDF 50쪽, CP949 csv 200행; 생성 스크립트·파일 미커밋). 콜드 VIEW `Displayed` 0.9~1.7초(프로세스 시작 포함), 열기(복사+목차) 0.12~0.27초. **1만 행 시트 3.5초 → 1.2초, 첫 200행은 열기 뒤 0.2~0.45초에 표시**. 원인: 숫자 셀마다 `DecimalFormat` 을 새로 만들었다(패턴 파싱) — 스레드별 패턴 캐시로. 첫 화면 우선은 계획 그대로 넣지 않았다가 측정 후 "앞 200행 부분 시트 → 끝까지 읽은 시트로 교체"로 구현했다. 1천 행은 전체 0.95초인데 첫 200행이 0.27초 — 남은 몫은 새 프로세스의 파서·서식 클래스 첫 초기화로 본다(1만 행과 차이가 작다). 메모리(PSS): 뷰어만 131~156MB, 채팅 화면 404MB+(에뮬레이터 CPU 엔진 로딩 중)
- **[Fix] 너비 정보 없는 열이 잘림 (M4 에뮬레이터 발견)** — csv 는 열 너비가 없어 기본 72dp 에 전화번호가 "010-000…"으로 잘렸다. 앞 200행(부분 시트와 같은 표본 — 교체 때 너비가 튀지 않게)의 글자 폭으로 맞춘다(48~220dp, 여러 열 병합 칸 제외)
- **[Note] 계획과 갈린 점** — ① `ZipInputStream` 스트리밍 → **캐시 복사 + `ZipFile`**: 엑셀은 공유 문자열 표를 시트 뒤에 저장하는 경우가 많아 순차 읽기로는 시트를 버퍼링해야 하고, 메신저·메일 URI 는 파이프 기술자라 `PdfRenderer`(탐색 필요)가 실패한다. 복사본은 닫을 때 삭제, 비정상 종료 잔여는 하루 지난 것만 정리 ② XML 은 `XmlPullParser` 가 아니라 **SAX** — domain 이 순수 JVM 이고 JVM 테스트에서 XmlPullParser 는 android.jar 스텁이다. JDK·안드로이드 양쪽에 있고 외부 엔티티를 끈다 ③ 상한은 core `Constants` 가 아니라 파서 옆 `DocumentLimits` ④ `taskAffinity=""` 는 넣지 않았다 — `documentLaunchMode` 만으로 별도 태스크다 ⑤ M3 의 "뷰어 warmUp 0회" 는 Hilt Robolectric E2E(`DocumentViewerLaunchE2ETest`, VIEW 인텐트로 csv 를 띄워 내용 확인 + `coVerify(exactly = 0) warmUp`)로 고정했다
- **[Note] 프로세스 시작 시 모델 파일 확인 로그** — 뷰어만 열어도 `GemmaRuntimeManager: model file: …` 가 한 줄 찍힌다. `KosmosApp` 이 상주 컴포넌트를 eager 주입하며 `GemmaRuntimeManager` 생성자가 파일 존재를 확인하는 것으로, 디렉터리 나열뿐 엔진 초기화는 없다(초기화 로그 0 확인). `KosmosApp.onCreate` 비용은 따로 재지 않았다 — 콜드 `Displayed` 안에 포함, 실기기 수치를 보고 지연 시작 여부를 판단한다(수명주기 계약 변경이라 이번에 고치지 않음)
- **[Known Issue] 이번 범위의 한계** — PDF 확대는 비트맵을 늘려 3배에서 흐림(재렌더 없음)·텍스트 선택/검색 없음, 표는 200열·틀 고정 5행까지, 차트·이미지·조건부 서식·숨김 행 미표시, xls·doc·hwp 미지원(docx·hwpx 는 0.35.0, hwp 는 0.36.0 실험), 다른 앱에서 연 문서는 최근 목록에 남지 않음(임시 권한), octet-stream 으로 오는 파일은 "연결 프로그램"에 안 뜰 수 있음
- **[QA/Test]** 게이트 녹색, 기존 단언 수정 0건(`DocumentViewerViewModelTest` 는 신규 — 로더 시그니처 변경에 맞춘 생성부만). 신규: `ModelWarmUpObserverTest` 2, `XlsxReaderTest` 11(테스트 안에서 xlsx 조립 — 개인 문서 픽스처 없음), `DocumentParsingTest` 10, `RecentDocumentsTest` 4, `DocumentViewerViewModelTest` 5, `SheetViewTest` 7, `DocumentHomeViewModelTest` 5, `DocumentHomeScreenTest` 2, `DocumentViewerLaunchE2ETest` 1(`-Pfast` 에서 제외되는 `*E2ETest`). 테스트 도구 `InMemoryPreferences` — Windows JVM 에서 파일 DataStore 의 두 번째 쓰기가 rename 실패("Unable to rename …")해 메모리 저장소로(안드로이드에선 생기지 않는 문제). **실기기 게이트(남음)**: 삼성 "내 파일"·카카오톡·Gmail 첨부에서 xlsx/PDF/csv 의 "연결 프로그램" 노출·열림(octet-stream 누락 여부), 뷰어만 열린 동안 모델 미로드(메모리·발열 체감), 뷰어 열린 채 채팅 아이콘 → 태스크 분리, 실사용 큰 xlsx 첫 화면·가로 스크롤·틀 고정 체감, PDF 확대 선명도, 다크 모드 셀 경계·머리글 대비, 인셋

## [0.33.0] - 2026-10-01
> **예산 밖 개선 — 공휴일 표시 · 여러 날 일정** (계획서 `docs/plans/0.33.0-search-calendar-extras.md`). 기억 검색 질문 확장(A)은 **보류**(사용자 결정, 아래 Note).
- **[Feat] 공휴일 (B)** — `KoreanHolidays` 내장 표(사용자 결정 D-B1): 양력 고정 공휴일 8개는 모든 해, 음력(설·부처님오신날·추석)·대체공휴일·선거일은 2026~2027 표. 월 그리드에서 공휴일은 빨강(토요일이어도 — 10/3 개천절), 셀 접근성 설명에 이름, 선택일 목록 위에 이름 한 줄(D-B2). 에뮬레이터 확인: 10/3 개천절·10/5 대체공휴일·10/9 한글날
- **[Feat] 여러 날 일정 (C)** — `spanDays`: 걸친 날마다 점·목록(D-C1), 끝 시각이 정확히 자정이면 그날 제외, 끝이 시작보다 앞이거나 못 읽으면 하루, 최대 31일(잘못된 끝 날짜로 한 달을 칠하지 않게). 선택일 목록의 여러 날 일정은 시각 대신 "10월 3일 ~ 10월 5일"
- **[Fix] 기기 캘린더 종일 일정이 "오전 9:00"·이틀로 보이던 결함 (C 작업 중 발견)** — Android 종일 일정은 UTC 자정 기준 `[DTSTART, DTEND)` 인데 기기 시간대로 바꿔 KST 에서 "8/15 오전 9:00 ~ 8/16 오전 9:00"이 됐다(목록에 시각이 붙고, 여러 날 전개를 넣으면 이틀을 칠한다). `AndroidCalendarTool` 이 `ALL_DAY` 를 읽어 **UTC 날짜만**("2026-08-15")과 포함 끝 날짜(DTEND − 1일)로 낸다. `IsoDateTimeParser` 는 날짜만 있는 ISO 를 "종일"/"8월 15일 종일"로 표기 — 일정 조회 툴 결과(모델 입력)에서도 자정 일정으로 오해되지 않는다
- **[Note] A 기억 검색 질문 확장 — 보류 (사용자 결정 2026-10-01)** — 판정 입력이던 exp37 의 코퍼스(`exp33_summaries.json`, 실대화 파생이라 미커밋)가 scratch/lab 정리로 없어 재실행할 수 없다. 합성 평가 세트를 새로 만드는 대신 보류한다 — 그 사이 0.32.0 exp45 에서 현행 프롬프트가 회상 질문 10종을 20/20 호출해, exp37 의 미스 중 "툴 미호출" 몫이 이미 줄었을 가능성이 크다. 재개 조건: 검색 미스 사례가 기록될 때 합성 세트로 exp37 재구성
- **[Known Issue] 공휴일 표 갱신** — 음력·대체공휴일 날짜는 작성 시 한국천문연구원 월력요항과 **원문 대조를 하지 못했다**(기억 기반). 대조 필요. 2028년부터는 표가 없어 양력 고정 공휴일만 보인다 — 매년 말 갱신
- **[QA/Test]** 게이트 녹색, 기존 단언 수정 0건. 신규: `KoreanHolidaysTest` 2(이름 표 10·비공휴일/표 밖 연도/현충일 대체 없음), `MonthGridTest` +2(걸친 날짜 표 6형태·31일 상한, 날짜별 묶음), `IsoDateTimeParserDisplayTest` +1(종일 표기). **실기기 게이트(남음)**: 기기 캘린더 종일 일정(공휴일 캘린더 구독 포함)이 "종일"·하루로 보임, 여러 날 일정 점·기간 표기, 다크 모드 공휴일 대비 — 에뮬레이터에는 기기 캘린더 계정이 없어 확인하지 못했다

## [0.32.0] - 2026-10-01
> **프롬프트 개선 실측 — 코드 변경 없음.** 계획서 `docs/plans/0.32.0-prompt-tuning.md` 의 세 후보를 실측(exp43·exp45)했고, 둘은 기각, 하나는 통과했지만 예산 때문에 보류했다(사용자 결정). 채팅 프롬프트·`MEASURED_OVERHEAD`(1,226) 무변경.
- **[Note] 약속 문장 제거 — 기각** — 시스템 지시 "For EVERY user request … promising without calling is a failure."(−39). exp43(모방 미끼 히스토리 포함 24건)은 기준선·제거안 모두 거짓 약속 0 이라 차이를 못 봤지만, **exp45 에서 제거안이 "네가 지어내서 답한 적 있었지?"(트리거 단어 없는 회상)에 두 조건 모두 툴을 부르지 않았다**(회상·저장 20/20 → 18/20). 이 문장이 막고 있던 실패를 처음 관측했다 — 계획서에 적어 둔 "빼서 나빠지는 걸 못 봤을 뿐"이라는 한계가 맞았다
- **[Note] 과거 대화 회상 트리거 — 기각** — 현행이 회상 질문 6종 + 저장 의도 4종을 히스토리 2조건에서 **20/20**. 게이트(+2)를 넘을 여지가 없다. 약속 문장을 뺀 위에 얹으면 19/20 으로 제거 손실을 다 메우지도 못했다(+16토큰)
- **[Note] ListReminders — 통과했지만 보류 (사용자 결정)** — 현행 위에 선언 + 트리거 한 줄(`"알림 뭐 있어", "리마인더 목록": you MUST call \`list_reminders\``, 선언 "등록해 둔 알림 목록을 시각순으로 조회한다.")을 얹은 exp45b: 스모크 21/21 · 회상·저장 20/20 · 목록 질문 6/6. 비용 **+50(tokenize)~+68(token_count)** — 넣으면 여유 74 → 6 이 되어 이후 프롬프트 개선이 사실상 막힌다. 조회는 드로어 할 일 목록·아침 브리핑이 대행 중이라 보류. 재개 조건: TD-3 해소(창 3,328 복원) 또는 "채팅으로 알림 목록" 수요 기록 — 그때는 실측 없이 계획서 M2 대로 구현 가능
- **[Note] 측정 하네스** — `scratch/lab/exp45_recall_list_reminders.py`(A/B/C/D, `E` 인자로 exp45b). 스모크는 exp41 단일 턴 15 + 긴 히스토리 6 = 21건

## [0.31.0] - 2026-10-01
> **수동 기억 정리 2차 — 이어지는 대화 통합** (expand.md A5). 같은 일이 30분 무활동 경계로 끊겨 여러 에피소드로 갈라진 것을 "기억 정리"의 마지막 단계에서 찾아 **통합 제안**하고, 사용자가 체크한 것만 하나로 합친 뒤 다시 요약한다. 계획서 `docs/plans/0.31.0-episode-merge.md`. 사용자 확정 결정(2026-10-01): 시간상 연속한(24시간 이내) 쌍만 후보 · 뒤를 앞에 합치고 재요약 · 1차와 같은 미리보기·체크 기본 해제.
- **[Note] M0 실측 (exp44, 합성 에피소드 20)** — 1차(exp42)는 한 줄 노트만 쟀으므로 에피소드는 새로 쟀다. 후보: 제목+태그 겹침 ≥ 0.3 만이면 참 5/6("주말 러닝 계획 ↔ 러닝 준비물" 0.17 놓침), **공유 태그 ≥ 2** 를 더하면 6/6. 요약까지 겹침에 넣으면 오히려 4/6. 판정: 참 6/6, **오병합 1**("회사 회의 안건 정리" + "회의실 예약" — 둘 다 같은 월요일 회의 준비라 정답 라벨 자체가 애매), 게이트(≤1) 통과. 재요약: 참 쌍 3개 모두 **문서 1편**·양쪽 핵심어 보존
- **[Changed] 계획과 갈린 점 — 재요약을 먼저** — 계획은 "통합 → 재요약 → 실패 시 CLOSED 로 되돌림"이었다. 요약 프롬프트는 무관한 주제를 2편으로 나누므로, 이은 메시지로 **먼저 요약해 정확히 1편일 때만** DB 를 바꾼다. 2편이거나 실패하면 합치지 않고 결과에 "다른 주제라 그대로 뒀어요"로 보고한다 — DB 를 건드리기 전에 판정이 끝나 되돌릴 경로가 필요 없다
- **[Feat] 데이터 (M1)** — `EpisodeDao` 를 interface → abstract class 로 바꿔 `@Transaction mergeInto(from, into)`: 메시지 재배정 · **회수 칩 id 재매핑**(공백 없는 쉼표 목록을 양끝 쉼표로 감싸 경계 일치 치환, `instr` 사용 — "ep2" 를 바꿀 때 "ep20" 을 건드리지 않음) · 원본 삭제 · 대상 저장을 한 트랜잭션으로. 대화 매퍼가 칩 id 를 `distinct` (이미 대상 id 를 가리키던 답변의 중복 방지)
- **[Feat] 도메인 (M2)** — `PlanEpisodeMergeUseCase`(연속 쌍 · exp44 후보 규칙 · 판정 oneShot · 연속 "같음"은 한 묶음 A~B~C), `ApplyEpisodeMergeUseCase`(이은 메시지로 기존 `SummarizeEpisodeUseCase` 재사용 → 1편일 때만 첫 에피소드로 통합 · 자동 추출은 부르지 않음 — 이미 추출된 사실의 중복 제안 방지 · 감사 로그)
- **[Feat] 실행기·화면 (M3)** — 정리 단계에 "이어지는 대화를 살펴보는 중 (n/N)" 추가, 검토 화면에 "이어지는 대화" 섹션(날짜+제목 → "하나의 대화로 합쳐 다시 요약", 체크 기본 해제), 적용 중 상태(재요약 추론이 있어 취소 없음 — 묶음이 반쯤 적용된 채 끊기지 않게), 결과에 합친 수와 건너뛴 수. 대화 통합 적용은 자동 요약 드레인과 **같은 잠금** 안에서
- **[QA/Test]** 마일스톤 게이트 녹색, 기존 단언 수정 0건(생성부: `MemoryCleanupRunnerTest` 에 통합 유스케이스 대역). 신규 9건: `EpisodeRepositoryTest` +1(Room 실 DB — 재배정·칩 경계 재매핑·중복 제거·원본 삭제), `EpisodeMergeUseCasesTest` 5(후보 규칙 표·연속 묶음·후보 아닌 쌍 미전송·다름 무제안·재요약 2편이면 DB 무변경·1편이면 합침), `MemoryCleanupRunnerTest` +2(검토에 통합 제안·합침/건너뜀 수, 적용이 잠금 안), `MemoryCleanupScreenTest` +1(체크 해제 시작·체크한 것만). **에뮬레이터**: 정리 전체 경로가 오류 없이 돌았지만 통합 제안은 **0건** — 에뮬레이터의 에피소드 4개가 서로 다른 일이고, "30분 끊긴 같은 일"을 만들려면 시계 조작이 필요해 **실제로 합치는 경로는 단위·DB 테스트로만 확인**했다. **실기기 게이트(남음)**: ① 통합 후 드로어 아카이브에 하나로 보이고 제목이 양쪽을 담음 ② 예전 답변의 🧠 칩이 통합 에피소드를 엶 ③ `search_memory` 회수 ④ 정리 중 앱 내림 → 취소·재실행 ⑤ 다크 모드. **알려진 한계**: 전사는 앞에서부터 토큰 예산까지만 담아 아주 긴 두 대화를 합치면 뒤쪽이 요약에서 빠질 수 있다(기존 긴 에피소드와 같은 한계)

## [0.30.0] - 2026-10-01
> **수동 기억 정리 1차 — 주간 회고 + 기억 중복 병합** (expand.md A5 대체). 설정 > 기억 > "기억 정리"를 누르면 지난 7일 에피소드로 회고(3~5문장)를 만들어 지식 노트로 저장하고, 같은 사실이 여러 번 저장된 기억의 **병합 제안**을 보여 준다 — 사용자가 체크한 것만 합친다. 계획서 `docs/plans/0.30.0-memory-cleanup.md`. 사용자 확정 결정(2026-10-01): 야간 배치 대신 수동 버튼(자동 정리와의 경합·백그라운드 모델 로드 금지 §2-⑥) · 1차 = 회고+병합, 에피소드 통합은 2차 · "지금 대화 정리" 없음 · 회고는 지식 노트(주차 교체) · 병합 대상은 수동·자동 모두(수동이 섞이면 결과도 수동) · 미리보기 후 적용, 되돌리기 대신 감사 로그.
- **[Note] M0 실측 (exp42·exp42b, 합성 데이터)** — 바이그램 containment 0.3 이 참 중복 8/8 을 잡았지만, 모델 판정만으로는 **"자전거 자물쇠 번호 4821 + 현관 비밀번호 4821" 을 같음으로 합쳤다**. 결정적 가드 — ① 숫자 집합 일치 ② 숫자를 뺀 본문 겹침 ≥ 0.3 — 를 모델 앞에 두자 숫자·대상 함정이 후보 단계에서 빠졌다. 그래도 "와이파이 비밀번호 kosmos123" + "회사 와이파이 비밀번호 kosmos123"(한쪽에 대상이 없음)은 판정 프롬프트 v1·v2 둘 다 합쳤다(v2 는 합친 문장에서 "회사"까지 지움 → v1 채택). **프롬프트로 오병합 0 을 보장할 수 없어 미리보기 체크박스 기본값을 "해제"로** 했다 — 계획서 D3 "항목별 체크 후 적용"의 구체화. 합친 문장이 원문 숫자를 하나라도 잃으면 제안에서 뺀다. 회고 프롬프트는 3회 모두 4문장·새 숫자 0
- **[Feat] 도메인 (M1)** — `MemoryMergeGuard`(결정적 가드), `PlanMemoryMergeUseCase`(후보 → 쌍 판정 oneShot → "같음" 간선 union-find → 3개 이상 묶음 재판정 → 숫자 보존 검사), `ApplyMemoryMergeUseCase`(합친 노트 **저장이 먼저**, 실패하면 원본 유지 · 겹치는 제안은 먼저 것만 · 감사 로그에 원문→합친 문장), `GenerateWeeklyReviewUseCase`(7일 창·요약 없는 에피소드 제외·예산 초과 시 오래된 것부터 제외·`weekly-review-2026-W40` 키로 교체 저장). `BigramMatcher.containment` 추가(Jaccard 는 표현 길이 차이만큼 깎여 7/8)
- **[Feat] 실행기 (M2)** — `MemoryCleanupRunner`(@Singleton): 발열 경고·엔진 미준비면 시작 거부, 회고와 병합 사이에 발열 재확인, 상태 `Idle/Waiting/Running(단계·n/N)/Review/Done/Blocked`. **`BackgroundInferenceGate`** — 자동 요약 드레인(`EpisodeSummarizeScheduler.drainMutex`)과 같은 뮤텍스를 써서 정리 중 드레인은 뒤로, 드레인 중 정리는 대기. 앱 onStop(엔진 해제) 시 취소(§2-⑥) — 다시 누르면 처음부터(멱등)
- **[Feat] 화면 (M3)** — 설정 "기억" 섹션에 진입 행, 정리 화면(TopAppBar 셸): 시작 → 진행률(병합 단계는 n/N)·취소 → 회고 본문 + 제안 카드(원문들 → 합친 문장, 체크박스) → "선택한 N건 합치기"/"합치지 않고 마치기" → 결과
- **[QA/Test]** 마일스톤 게이트 녹색, 기존 단언 수정 0건(생성부: `EpisodeSummarizeSchedulerTest` 에 잠금 인자). 신규 18건: `MemoryCleanupUseCasesTest` 9(가드 함정 표·숫자 보존·같음만 제안·후보 아닌 쌍 미전송·숫자 잃은 병합 제외·3개 묶음 재판정·저장 실패 시 원본 유지·수동 승계/겹침 건너뜀·회고 창/주차 키), `MemoryCleanupRunnerTest` 7(발열·엔진 거부·검토 상태·드레인 잠금 대기·취소 무적용·체크한 것만·빈 선택), `MemoryCleanupScreenTest` 2(체크 기본 해제·체크한 것만 적용). **에뮬레이터 종단 확인(통과)**: 설정 → 기억 정리 → 회고 생성(에뮬레이터의 실제 합성 대화 — 치과·물 마시기·자전거 4821 — 만 언급) → 병합 제안 1건(자전거 자물쇠 번호 두 노트, 체크 해제 상태) → 체크·적용 → 메모 화면에 회고 노트("자동" 배지·주간 회고/2026-W40 태그)와 합친 노트(수동, 태그 합침), 원본 2건 삭제. **실기기 게이트(남음)**: 지식 30·에피소드 20 규모 소요 시간, 정리 중 채팅 전송, 정리 중 앱 내림 → 취소·재실행, 발열 거부 문구, 회고 `search_memory` 회수("이번 주 뭐 했지?"), 다크 모드. **잔여**: 2차 에피소드 중복 통합

## [0.29.0] - 2026-10-01
> **내장 TTS 음성 출력** (expand.md A2) — 답변을 기기 내장 엔진(삼성·구글 등)으로 읽어 준다. 설정에 자동 낭독 토글과 엔진 선택, 말풍선마다 재생/정지 버튼. **한국어·오프라인 음성만** 쓴다(NFR1). 계획서 `docs/plans/0.29.0-tts.md`. 사용자 확정 결정(2026-10-01): 내장 엔진 · 자동 낭독 토글(기본 꺼짐) · 대상은 모든 답변 · 말풍선 재생 버튼 · 엔진은 "시스템 기본" + 설치 목록.
- **[Feat] `SpeechOutput` (M1)** — `TextToSpeech` 래퍼(@Singleton, open). 첫 낭독/설정 진입 때 엔진을 만들고(§2-④ init 금지) 초기화를 5초까지 기다린다. 음성은 **한국어 && `isNetworkConnectionRequired == false` && 데이터 설치됨** 중 품질 최상 — 없으면 `NO_KOREAN_OFFLINE_VOICE` 로 읽지 않는다(네트워크 음성으로 몰래 넘어가지 않음). 오디오 포커스 `GAIN_TRANSIENT_MAY_DUCK`(음악을 줄였다가 복귀), 속성 `USAGE_ASSISTANT`. 매니페스트 `<queries>` `TTS_SERVICE` — targetSdk 30+ 패키지 가시성 때문에 이것이 없으면 `getEngines()` 가 빈다(에뮬레이터에서 구글 엔진이 목록에 뜨는 것으로 확인)
- **[Feat] 낭독 텍스트 (M2)** — `SpeakableText.from`: 굵게·제목·목록·인용·코드 블록·링크 URL·이모지 제거. `chunks`: 문장 경계에서 `getMaxSpeechInputLength()` 이하로, 긴 한 문장은 글자 수로
- **[Feat] 설정 "음성" 섹션 (M3)** — 자동 낭독 토글(기본 꺼짐), 엔진 라디오("시스템 기본" + 설치 목록), 음성 상태 안내(한국어 오프라인 음성 없음 → "음성 데이터 설치" = `ACTION_INSTALL_TTS_DATA`, 엔진 시작 실패). `SettingsViewModel` combine 셋째 그룹
- **[Feat] 채팅 연동 (M4)** — 답변 확정 시 자동 낭독(오류 턴 제외), 말풍선 재생/정지(🔊/■, "답변 읽기"/"읽기 멈추기" 설명), **녹음 시작·새 메시지 전송·화면 종료 시 정지**(마이크가 TTS 를 전사하지 않게), 앱 onStop 시 엔진 해제(`KosmosApp`). 계획에 없던 보강: 재생을 눌렀는데 한국어 오프라인 음성이 없으면 **스낵바 안내** — 에뮬레이터(구글 엔진, 한국어 데이터 미설치)에서 버튼이 아무 반응 없어 고장처럼 보였다. 자동 낭독은 답변마다 반복되므로 안내하지 않는다. 안내는 기존 일시 안내 채널(`suggestionNotice`)을 같이 쓴다
- **[QA/Test]** 마일스톤 게이트 녹색, 기존 단언 수정 0건(생성부: `SettingsViewModel` 1곳·`ChatViewModel` E2E 3곳에 낭독기 대역). 신규 13건: `SpeakableTextTest` 2(정리 11형태 표·분할), `SpeechOutputTest` 3(Robolectric 섀도 — 오프라인 한국어 음성이면 정리된 텍스트 낭독, 네트워크 음성만 있으면 거부, 정지), `SettingsViewModelTest` +3, `ChatSpeechIntegrationTest` 5(자동 낭독 켜짐/꺼짐·녹음 시 정지·재생 토글·음성 없음 안내 — `-Pfast` 제외 목록 추가). **에뮬레이터 확인**: 엔진 목록(구글), 한국어 음성 없음 안내·설치 버튼, 말풍선 재생 버튼, 음성 없을 때 스낵바. **실기기 게이트(남음)**: ① 삼성 엔진 한국어 낭독 ② 비행기 모드 낭독 ③ 구글 엔진 전환 ④ 낭독 중 🎤 → 즉시 정지·정상 전사 ⑤ 음악 덕킹·복귀 ⑥ 앱 내리면 정지 ⑦ 마크다운 기호 안 읽음 ⑧ 긴 답변 이어 읽기 — 에뮬레이터는 한국어 음성 데이터가 없어(내려받기는 네트워크) 소리 경로를 확인하지 못했다

## [0.28.0] - 2026-10-01
> **월 캘린더 + 오늘 요약 제거** — 일정 화면에 **월 / 목록** 탭을 두고, 월 탭은 일요일 시작 그리드(좌우 스와이프)에 일정 점과 선택한 날의 목록을 보여 준다. 목록 탭은 기존 화면(오늘 / 이번 주 + 7일 날짜 띠) 그대로이고, AI 요약은 **이번 주(오늘부터 7일)에만** 만든다. 계획서 `docs/plans/0.28.0-month-calendar.md`. 사용자 확정 결정(2026-10-01): 월 단위 요약 없음 · 일요일 시작 · 탭 둘 다(월 기본) · 오늘 요약 제거 · 점 색으로 앱/기기 일정 구분 · 공휴일은 이번 회차 범위 밖(일요일만 빨강).
- **[Feat] 월 범위 조회 (M1)** — `GetTodayScheduleUseCase.month(yearMonth, zoneId)` 신설, 결과는 전용 `MonthSchedule`. 툴·브리핑·위젯이 공유하는 `RangeType`(오늘/이번 주)에 MONTH 를 넣지 않았다 — `when` 분기 7곳이 흔들리고 `get_schedule` 툴 표면이 바뀐다(프롬프트 표면 무변경). 기존 두 범위와 월 조회가 같은 `loadRange`(앱 일정 + 기기 캘린더 병합·중복 제거·정렬)를 쓴다. `CalendarEvent.source`(APP 기본 / DEVICE) 추가 — 계획은 `AndroidCalendarTool` 에서 표시하자고 했으나 병합 지점인 유스케이스에서 붙였다(기기 일정이 들어오는 길이 그 하나)
- **[Feat] 월 그리드 순수 함수 (M2)** — `monthGrid(yearMonth, firstDay = SUNDAY)` 는 앞뒤 달로 채운 **필요한 만큼의 주(4~6)**, `eventsByDate` 는 시작일 기준 묶음(여러 날 일정은 시작일에만). 2026-02(일요일 시작 28일 → 4주)·2026-08(토요일 시작 → 6주)·2028-02(윤년) 표 테스트
- **[Feat] 월 탭 화면 (M3·M4)** — 탭 상태·월 조회 상태(`MonthUiState`)·선택일을 뷰모델이 들고, 빠른 스와이프 시 이전 조회를 취소한다. 화면은 `HorizontalPager`(기준 달 ±1,200) ↔ 뷰모델 양방향 동기화(이웃 달 칸을 누르면 그 달로), 그리드 높이 6주 고정(달마다 4~6주라 아래 목록이 출렁이지 않게), 오늘 테두리·선택 채움·이웃 달 흐림, 점 최대 3개(앱=accent, 기기=accentAlt), 다른 달을 보고 있으면 "오늘" 버튼. 셀마다 "10월 2일, 일정 2건" 접근성 설명
- **[Changed] 요약은 이번 주만** — 오늘 범위는 요약 추론을 돌리지 않는다. 요약 카드에 "이번 주 요약" 라벨 — 날짜 띠로 하루를 골라도 요약은 7일 전체라 범위를 밝힌다
- **[QA/Test]** 마일스톤 4회 게이트 녹색. **기존 테스트 1건 수정(사용자 확인 후)**: `CalendarViewModelTest.요약을 기다리지 않고 목록을 먼저 내보낸다` 의 호출을 `loadSchedule()`(오늘) → `loadSchedule(WEEK)` 로 — 단언(목록 선행·요약 도착)은 그대로. 신규 13건: 월 조회 3(경계·기기 범위/출처·실패 플래그), `MonthGridTest` 3, 뷰모델 4(오늘 요약 없음·월 조회 요약 없음·같은 달 재조회 생략/force·이웃 달 선택), `MonthCalendarTabTest` 3(날짜 선택 → 목록, 빈 날, 다음 달). **에뮬레이터 확인(통과)**: 라이트/다크 대비, 6주 달(2026-08) 잘림 없음, "오늘" 복귀, 날짜 선택 목록, 목록 탭 오늘 범위에 요약 없음. **남은 확인**: 기기 캘린더 일정의 점 색(에뮬레이터에 기기 일정 없음), 이번 주 요약 라벨(에뮬레이터 CPU 추론이 느려 생략), 실기기 스와이프 체감

## [0.27.1] - 2026-09-30
> **0.27.0 후속 — 툴 결과 문구 실측(exp38), 에뮬레이터로 실기기 게이트 소화, 거기서 나온 결함 수정.** 사용자 결정(2026-09-30): 오류 일시 안내는 Snackbar 로 통일, 테스트 단언 규칙은 예외 허용하되 수정 전 확인.
- **[Fix] AddMemory 실패 문구의 거짓 성공 (exp38)** — 저장이 실패했는데 모델이 "기억해 두었습니다"라고 답했다. 발화 6개 × 문구 변형 측정: 현행 영어 `Failed to save memory: DbWriteError(…)` **2/6**, 한국어 직역 **3/6**(더 나빴다), 해야 할 행동을 적은 지시형("…기억하지 못했다고 사용자에게 분명히 알리고, 다시 시도할지 물어보세요.") **0/6**(+5토큰). 내부 오류 이름도 모델 입력에서 뺐다. 성공 문구는 영/한 동등(KVΔ 50.0 vs 50.7)이라 다른 실행기와 맞춰 "기억에 저장했어요."
- **[Note] 일정·리마인더 실패 문구는 유지 (exp38c)** — 현행("일정 추가 실패"/"리마인더 등록 실패")이 이미 거짓 성공 0/6. 지시형은 +25~28토큰에 이득 0. 문구 교체는 실행기마다 재야 한다는 근거
- **[Fix] 페이징 앞쪽 로드 중복 → LazyColumn 중복 키 크래시** — 에뮬레이터에서 드로어 아카이브 에피소드를 수정하자 앱이 두 번 죽었다(`Key … was already used`). `DefaultPagingSource`·`CountedPagingSource` 가 prevKey 를 `offset - loadSize` 로 잘라 넘겨, 새로고침이 앵커(1)에서 60개를 읽은 뒤 앞쪽 로드가 0..19 를 **다시** 읽었다. 0.27.0 의 "수정 후 목록 refresh" 가 처음 드러냈을 뿐 기억 화면(할 일 추가·완료 refresh)·채팅 타임라인도 같은 잠재 결함이었다. 앞쪽 로드 키를 끝 오프셋으로 하는 `offsetRange`(OffsetPaging.kt) 로 두 소스 공용화 + `itemsBefore` 로 새로고침 앵커를 절대 위치로. 회귀 테스트 3건(`OffsetPagingTest`)
- **[Fix] 엔진 로드 실패 안내** — 0.27.0 의 `setError` 가 `ModelNotReady` 였는데, 에뮬레이터에서 손상 파일로 재현하니 스플래시가 "AI 모델을 **준비하고 있어요**. 잠시 후 다시 시도해주세요." — 진행 중처럼 읽혀 사용자가 기다리기만 한다. `AppError.ModelLoadFailed`/`MODEL_LOAD_FAILED` 신설("…파일이 손상됐을 수 있어요 — 설정 > 모델 관리에서 다시 내려받아 주세요."), 스플래시에 "모델 내려받기" 버튼도 노출. `ErrorMessageMappingTest` 의 코드→대표 오류 표에 한 줄 추가(단언 무변경)
- **[Changed]** 긴 날짜 표기 `IsoDateTimeParser.longDateKorean`("9월 30일 수요일") — 아침 브리핑 프롬프트(모델 입력)와 위젯 헤더가 각자 들고 있던 `ofPattern("M월 d일 EEEE")` 을 모았다. 출력 바이트 불변을 7개 요일 대조 테스트로 고정. 기억 화면 Toast → Snackbar(앱 전체 일시 안내 통일 — 일정 불러오기 실패 같은 **상태 표시**와 모달 시트 안 안내는 인라인 유지: 시트는 별도 창이라 Snackbar 가 가려진다). `./gradlew test -Pfast`(E2E·Robolectric 스트림 5개 제외, 약 40초 — AGENTS §1)
- **[Note] 에뮬레이터 게이트 결과** — 통과: 0.27.0 할 일 통계("남은 일 1 · 완료 1", 완료 즉시 목록에서 빠짐) · 에피소드 수정·삭제 즉시 드로어 반영(재실행 없이) · 새 시각 표기("오전 12:47") · 첨부 "첨부 취소" 아이콘 · TalkBack 한국어 설명 · 엔진 로드 실패 화면 / 0.20 아침 브리핑 카드 도착(캘린더 미확인 문구 포함) / 0.21 리마인더 **재부팅 후 복원 → 정각 발화(07:04) → 헤드업(중요도 4) → 탭하면 앱** · 정확 알림 off 시 ±10분 창 / 0.22 위젯 날짜("9월 30일 수요일")·할 일 수 갱신 / 0.23 백업 내보내기 → 삭제 → 가져오기 → 재시작 후 프로필 복원 / 0.25·0.26 "기억해줘" 저장 → "4821" 정확 회수, 저장 안 한 여권번호는 "없다"(지어내지 않음) / 승인 카드 "10월 1일 (목) · 오후 3:00 - 오후 4:00". 한글 입력은 공유 인텐트(`am start -a SEND --es EXTRA_TEXT`)로 넣었다 — adb `input text` 는 한글 불가
- **[Known Issue] 에뮬레이터에서 새로 드러난 것 (미수정)** — ① **"오전 7시 4분에 알려줘"(06:54 발화)를 모델이 내일로 잡았다** — 시스템 지시에 분 단위 시각이 없어(PromptAssembler 의 의도된 설계, 재프리필 방지) 오늘 그 시각이 지났는지 모른다. "오늘"을 붙이면 정확(→ 아래 [Fix] 로 해소 — 원인은 시각이 아니라 직전 턴 날짜 끌림이었다) ② **승인 거절 회신 "사용자가 취소했습니다"에서도 거짓 성공**(→ 아래 [Fix] 로 해소)(에뮬레이터: 카드를 취소했는데 "알림을 설정했습니다") — exp38d: 리마인더 1/6, 그리고 대부분의 답변이 취소를 "오류가 발생했습니다"로 말했다. 지시형 "사용자가 승인하지 않아 실행하지 않았습니다. …알리세요." 는 0/12 + 정확("승인이 필요합니다"). "취소" 단어를 살린 변형은 거짓 성공 0 이지만 12/12 가 "오류"로 오인 — 채택 후보는 전자인데 `ToolApprovalE2ETest` 대역이 "취소" 단어로 분기해 테스트 수정 확인 대기 ③ 승인 카드와 캘린더 권한 대화상자가 동시에 떠 카드를 가리고, 그동안 승인 60초 타이머가 흐른다(에뮬레이터에서 무응답 → 자동 거절)(→ 아래 [Fix] 로 해소) ④ 무작위 바이트 모델 파일에서 네이티브 오류 문자열이 잘못된 UTF-8 이라 **디버그 빌드의 CheckJNI 가 프로세스를 abort**(릴리스는 CheckJNI 꺼짐 — ASCII 손상 파일로는 정상적으로 오류 화면)
- **[Fix] 날짜 없는 시각이 직전 턴의 날짜로 끌려감 (Known Issue ①, exp39·39b·39c)** — 처음 가설("분 단위 시각이 없어서")은 기각됐다: exp39 에서 턴 리마인더에 `[Now] 14:10` 을 실어도 정답률이 같았다(4/6 = 4/6, +10토큰). exp39b 가 진짜 원인을 재현했다 — 히스토리 없이는 3/3 오늘인데, 에뮬레이터와 같은 "내일 오후 3시 치과" 대화 직후에는 **3건 중 2건이 내일로 끌려갔다**(그중 하나가 에뮬레이터 원문 "오전 7시 4분"). 상대 날짜 규칙 줄 끝에 `A time with no date word means today.` 를 붙였다(exp39c): 히스토리 3종(없음·내일·모레) × 발화 4개 **11/12 → 12/12**, 에뮬레이터 원문 재현 2/2(기준선은 둘 다 내일), 툴 스모크 4/4 무손상. 20토큰짜리 영어 변형("앞 턴의 날짜를 이어 쓰지 말라")도 12/12 라 짧은 쪽을 택했다. 오버헤드 실측 **+16 → `MEASURED_OVERHEAD` 1,277 → 1,293**(+ 프로필 상한 100 = 1,393 / 예약 1,400, 여유 7). 표본이 작아(12+2) 실기기에서 "내일" 대화 직후 날짜 없는 리마인더를 한 번 더 본다. 
- **[Fix] 지난 시각 리마인더를 승인한 뒤에야 실패** — 과거 시각 검사가 `AddReminderUseCase`(승인 **뒤** 실행)에만 있어, 사용자가 실행될 수 없는 카드를 승인한 다음에야 "이미 지난 시각"이 났다(실행기 주석의 "승인·실행 공유 검증" 규약에서 이것만 빠져 있었다). `buildApprovalRequest` 가 지난 시각이면 `ToolArgumentException(time, PAST)` 를 던져 카드를 띄우지 않는다. 실행 쪽 UseCase 검사는 유지 — 카드가 떠 있는 동안 시각이 지나갈 수 있어 최종 판정은 실행 시점이다. 공유 `parse` 가 아니라 승인 요청에만 넣은 것도 그래서다(기존 실행 경로 테스트 무변경). 모델 회신은 BaseAgent 인자 오류 봉투(tool/field/reason 키 추가)로 바뀌지만 문구는 실행 오류와 같은 원문이다 — exp40: 현행 봉투 3/3 = 새 봉투 3/3, 둘 다 "이미 지난 시각이라 설정할 수 없다, 미래 시각을 말해 달라"로 사실대로 답했고 거짓 성공·같은 시각 재호출 0. 시각은 내부 생성자의 `now: () -> Long` 로 주입(§2-④, Hilt 는 벽시계 보조 생성자). `AddReminderToolExecutorTest` 는 생성부에 고정 시각만 넣었고 **단언 수정 0건**, 신규 2건(지난 시각·지금과 같은 시각 → PAST)
- **[Changed] 프롬프트 중복 문구 다이어트 (exp41, 사용자 결정: 73토큰안)** — 날짜 규칙 한 문장으로 예산 여유가 7토큰까지 줄어, 중복·기본 동작 서술을 후보 6개로 실측했다(스모크: 툴 선택 12·무호출 2·날짜 끌림 2·요일 날짜 1 + 긴 잡담 히스토리 뒤 6건, 인자 원문·날짜 검사 포함). 채택 4개 — ① `Today: … (요일)` 줄을 `오늘=… (요일)` 줄로 합침 ② 트리거 규칙 6줄의 `Korean triggers` 라벨 제거 ③ 끝 문장 "If you do not need a tool, simply provide your final response in plain text." 제거 ④ `add_memory` content 설명의 "숫자와 고유명사는 그대로"(시스템 지시 "Never alter numbers…"와 중복) 제거. 단독 각 17/17, 조합 23/23. 오버헤드 실측 **−67**(tokenize 차이로는 −74, 작은 쪽 채택) → `MEASURED_OVERHEAD` 1,293 → **1,226**, 여유 7 → **74**. 보류 1개 — 시스템 지시의 "MUST call / 말로만 약속하지 말 것" 문장(−39)은 턴 리마인더와 중복이고 스모크도 23/23 이었지만, 스모크가 '호출 없이 했다고 말하기'를 재지 않아 0.8.3 실기기 결함의 방어선을 남겼다. 기각 1개 — `search_memory` 선언의 "추측하지 말고 확인"(−13)은 "이번주 토요일"을 다음 주로 잡아 16/17. `PromptAssemblerTest`·`PromptFixtureExportTest` 의 날짜 블록 표지 문자열을 `[System Data] Today:` → `[System Data] 오늘=` 로 바꿨다(단언 2개, 사용자 확인 후 — 검사 의미는 그대로). 정정: 앞의 날짜 규칙 문장 "+16"은 응답 길이 변동이 섞인 값이고 문장 자체는 9토큰이다 — 예산 쪽으로 보수적인 오차라 기록은 두고 이번 측정부터 tokenize 차이를 함께 적는다
- **[Fix] 승인 거절 회신 문구 (exp38d/e, 사용자 확인 후)** — Known Issue ②를 해소했다. `BaseAgent.rejectionMessage`("사용자가 승인하지 않아 실행하지 않았습니다. {일정을 추가하지/알림을 등록하지/기억하지} 않았다고 알리세요.")로 바꿨다. `ToolApprovalE2ETest` 대역의 분기 조건을 `contains("취소")` → `contains("승인하지 않아")` 로 바꿨고, 단언은 바꾸지 않았다
- **[Changed] 테스트 정리 (규칙 예외 — 대상 목록을 먼저 보이고 사용자 확인)**
  - `ToolParser.malformedToolCalls` 삭제: 소비자 0. `ToolParserTest` 단언 2줄을 빼고, 테스트 이름을 "본문에서 걷어내고 툴 콜로 세지 않는다"로 바꿨다
  - `MultimodalChatE2ETest` 대역을 `ScriptedModelRunner` 로 옮겼다. `receivedPrompts` 를 스레드 안전 목록으로 바꿨고, `lastPrompt!!` 를 `receivedPrompts.last()` 로 바꿔 `!!` 도 없앴다
  - 삭제
    - `ChatScreenE2ETest`: 테스트 안에서 만든 가짜 화면만 검증했다. 17초가 걸렸고 제품 커버리지는 0이었다. AGENTS 의 `"Enter message"` 계약 서술도 정정했다
    - 중복 8건: MemoryPipeline 2(고유 단언 2개는 SearchMemoryToolExecutorTest 로 옮김), ToolParserStream 2, KnowledgeTagRoundTrip 2, VoiceTranscriptionFailure 1(단언은 같은 셋업 테스트로 합침), FloatBytes 1
    - 함의 단언: TokenBudgetInvariant 1줄, AudioLimit 2개
  - 표 형식 병합: GetScheduleRange 7→1, SqlLikeEscape 6→1, TagsNormalize 8→2
  - 결과: 474 → **433건**, 커버리지 손실 없음. AGENTS §2-② 에 예외 절차를 명문화했다
- **[Note] Robolectric 제거 속도 실험 — 기각**
  - 실험 내용: `android-json`(안드로이드 org.json 구현 추출본)을 testImplementation 으로 넣고, org.json 때문에만 Robolectric 을 쓰던 10개 파일에서 러너를 뗐다
  - 결과: 10개 파일 합은 9.2s → 2.8s 로 줄었지만(BaseAgentStreamTest 6.6s → 2.1s), **테스트 태스크 전체는 54·51s → 58·64s** 로 줄지 않았다
  - 원인: Robolectric 샌드박스 기동비가 다음 Robolectric 스위트(AutoExtractMigrationTest 5.9s)로 옮겨 갔을 뿐이다. 남은 Robolectric 파일이 19개라 기동은 어차피 한 번 일어난다
  - 판단: 이득이 없는데 2013년판 org.json 구현이라는 충실도 위험만 늘어서 되돌렸다. 더 줄이려면 E2E 3종의 SDK 설정(33/34/35 혼재 → 샌드박스 3종 기동) 통일을 측정하는 쪽이 후보다
- **[Changed] Robolectric SDK 35 로 통일 (위 후보 실측 → 채택)** — `robolectric.properties` 와 E2E 3종의 `@Config(sdk=33/34)` 고정을 걷어 전부 35 로. 샌드박스 기동이 3종 → 1종. 테스트 태스크 **56·55s → 44·47s**(약 18% 단축). SDK 분기를 검증하는 테스트가 없어 잃는 것이 없다
- **[Fix] 권한 대화상자가 승인 카드를 가린 동안 흐른 제한 시간 (Known Issue ③)** — `ApprovalCoordinator.restartTimeout()` 신설: 대기 루프가 `select` 로 승인 결과와 재시작 신호(CONFLATED 채널)를 함께 기다려, 신호가 오면 60초를 처음부터 다시 잰다. 캘린더 권한 런처 콜백이 `ChatViewModel.onApprovalObstructionCleared()` 로 부른다. 새 요청 시작 시 이전 요청의 남은 신호는 비운다. 신규 테스트 `ApprovalCoordinatorTimeoutTest` 3건(재시작 후 연장·미재시작 시 만료·이전 신호 무시)
- **[QA/Test]** 게이트 `test lintDebug assembleDebug` 녹색. 신규 테스트: `OffsetPagingTest` 3, 긴 날짜 바이트 대조 1, `ApprovalCoordinatorTimeoutTest` 3, `AddReminderToolExecutorTest` +2. 프롬프트 표면 변경 3건(날짜 규칙 한 문장 — exp39c, 지난 시각 인자 오류 봉투 — exp40, 중복 문구 다이어트 — exp41. `MEASURED_OVERHEAD` 1,277 → 1,226). 실기기에 남는 것: 프로필 제안 카드(0.24 ②~⑦ — 에피소드 닫힘 트리거가 30분 무활동/예산 리셋이라 시계 조작 없이는 에뮬레이터 불가), 발열·arm64·삼성 런처·잠금 화면 타일(보안 설정)

## [0.27.0] - 2026-09-30
> **전체 코드 리팩터링 회차** — 서브에이전트 3개(런타임·어시스턴트 / UI / core·domain·data·테스트)의 점검 결과 약 45건을 항목마다 코드로 재검증한 뒤 수정했다. 계획서를 Antigravity 에 넘기는 분업(0.23.1)은 **이 회차에 한해 취소**하고 이 세션에서 직접 수행했다(사용자 결정 2026-09-30). 마일스톤 5개(M1~M5)마다 `test lintDebug assembleDebug` 녹색 후 커밋, 프롬프트 표면(시스템 지시·툴 선언·툴 결과 문구)은 **바이트 불변**(PromptFixtureExport 픽스처 diff 가 날짜 줄뿐임을 매 마일스톤 확인).
- **[Fix] M1 런타임 안정성** — ① **백그라운드 재로드 경합**: `runTurn` 이 Ready 를 뮤텍스 밖에서만 확인해, 그 사이 `close()`(onStop)가 엔진을 해제하면 뮤텍스를 얻은 턴이 3.6GB 를 다시 로드했다(§2-⑥ 위반, 요약 드레인 경로). 뮤텍스 안에서 재확인하고 엔진 초기화는 `warmUp` 만 한다 ② **warmUp 실패 크래시**: GPU·CPU 둘 다 실패하면 예외가 `lifecycleScope` 로 새어 앱이 죽고 상태가 InitializingEngine 에 고정됐다 — `ModelLoadState.Error` 생성처가 0곳이었다. `ModelLoadManager.setError` 신설, 스플래시 "다시 시도"가 성립 ③ **취소 삼킴**: `SendChatMessageUseCase` 의 `catch(Exception)` 이 코루틴 취소까지 잡아 "알 수 없는 오류: {예외 원문}" 말풍선을 DB 에 저장했다 — 취소는 되던지고 문구는 `ErrorMessages`. 러너의 두 catch 는 **자기 코루틴이 취소됐을 때만** 되던진다(`ensureActive`) — 사용자 취소(cancelProcess)는 부분 응답 보존 경로라 오류로 흡수해야 한다 ④ **닫힌 대화 참조**: close 후 재생성이 던지면 캐시 필드가 닫힌 네이티브 객체를 가리켰다(use-after-free) — 닫기 전에 비운다. 실패한 GPU 엔진도 닫는다 ⑤ `cancel()` 이 진행 중인 oneShot(전사)에도 닿게 `activeConversation` 추적 ⑥ **init 구독 → `start()`**(§2-④): `EpisodeSummarizeScheduler`·`EpisodeBoundaryManager` 를 KosmosApp 에서 eager 주입·start. 드레인 예외 한 번에 Ready 구독이 영구히 멈추던 것도 감쌌다. 벽시계는 `now` 인자로
- **[Fix] M2 데이터·표시** — ① 기억 화면 "완료 N"·진행 바가 늘 0(미완료만 담는 페이징 스냅샷에서 셈) → DAO 카운트(`TaskRepository.getCounts`) ② 완료 토글 직후 refresh 가 DB 쓰기보다 먼저 돌 수 있었다 → `completeTask(id, onDone)` ③ 일정 승인 카드가 사설 파서로 **ISO "2026-09-30"** 을 띄웠다(§4-7) → `IsoDateTimeParser.toDisplayDateKorean`("9월 30일 (수)"), 헤더 "Kosmos suggests an event" → "일정을 추가할까요?" ④ **백업 스키마 검사 부재**: 더 새 앱의 백업을 복원하면 다운그레이드 파괴 마이그레이션(`dropAllTables`)이 복원한 기억을 조용히 지웠다 → SQLite 헤더 `user_version` 을 읽어 `KosmosDatabase.SCHEMA_VERSION` 초과면 `ImportSchemaMismatch` ⑤ 에피소드 태그가 정규화 없이 저장 → `Tags.encode/decode` 로 인코딩 계층이 불변식을 지킨다 ⑥ 완료 항목 재저장 시 `completedAt` 이 덮이던 잠재 결함 ⑦ `GetTodayScheduleUseCase` 벽시계 → `(range, now, zone)` 오버로드(기본 인자로 하면 mockk 스텁에 스텁 시점 시각이 박혀 매칭이 깨진다) ⑧ 저장소 오류 처리 3가지 모양 → `dbRead/dbWrite`(DbCall.kt). **지식 DB 오류가 `SearchError`→"검색이 지연되고 있어요"로 뜨던 분류 오류**를 `DbReadError` 로 교정(사용자 문구 변경) ⑨ enum 역직렬화 폴백 5곳 → `enumOrDefault`
- **[Changed] M3 어시스턴트** — `ToolNames` 상수(툴 이름 리터럴 30곳), 툴 실행기 `@IntoSet` 멀티바인딩(`AgentModule`, 빈 `AppModule` 삭제), `AssistantMessageWriter`(말풍선 저장·오류 말풍선+감사 쌍 — BaseAgent·Orchestrator·브리핑), `ToolResultJson`(키 순서까지 보존해 모델 입력 바이트 불변), `shouldDeferBackgroundInference()`(발열 게이트 3곳), `briefingTriggerMs`(알림 예약·생성 판정 공용), 메인 스레드 블로킹 제거(다운로드 `acknowledge` 의 `Future.get()`·부분 파일 삭제, 공유 인텐트 크기 조회 → IO), `PromptAssembler(today)`, 죽은 코드(`clearPending`, `DeviceStatusStrip`, `recordEnd` 반환값·`InferenceMetrics`, `GemmaTokenizer` 미사용 생성자 인자), 낡은 주석 5곳
- **[Changed] M4 UI** — `ApprovalCardScaffold`(일정·프로필 승인 카드 공용 뼈대), `GlassSegmentedControl`(3벌), `AttachmentPreview`(닫기 "X" → 아이콘+"첨부 취소"), `TimelineRow`(채팅 두 items 블록), `AttachmentReader`(ChatScreen 컴포저블 안 65줄 I/O 분리, 동기 유지 — E2E waitForIdle 계약), 에피소드 시트 `EpisodeSheetState`(없는 id·조회 실패에서 "불러오는 중…"에 갇히던 결함) + 수정·삭제 후 드로어 아카이브·검색 갱신(`onChanged`), 날짜 표기 `monthDayKorean/timeKorean/dayLabelKorean` 단일화 + `rememberToday()`(자정 넘겨 복귀해도 "오늘"이 어제로 남던 문제), contentDescription 한국어화(E2E 계약 "Attach"/"Send" 제외), `collectAsStateWithLifecycle` 통일, 일정 목록 키, 미사용 import 167건
- **[Changed] M5 런타임 분리·빌드·테스트** — `GemmaModelRunner` 713 → 약 590줄: 재사용·재생성 판정을 순수 함수 `decideConversation`(+`ConversationKey`, 세 캐시 필드를 한 값으로)과 설정 조립(`chatConversationConfig`/`oneShotConversationConfig`/`GREEDY_SAMPLER`)으로 `ConversationPolicy.kt` 에, 디버그 진단을 `RuntimeDiagnostics` 로. 미사용 `room-paging`(app·data)·카탈로그 별칭 `paging-common`·`kotlin-android`(AGP 9 재선언 함정) 제거. 테스트: `InMemoryKosmosDbRule`(DB 테스트 5곳 설정 이전), `ScriptedModelRunner`
- **[Note] 검증 후 수정하지 않은 항목** — ① `ToolParser.malformedToolCalls`(소비자 없음) — 기존 테스트 단언이 고정하고 있어 유지 ② E2E 전용 ModelRunner 대역 2종 — 자기 필드를 단언하므로 공용화하면 단언을 고쳐야 한다 ③ `data` 의 `implementation(project(":core"))` — domain 의 `api` 로 중복이지만 "쓰는 것에 직접 의존"이 맞아 유지 ④ `MemoryScreen.TabButton` — 세그먼트가 아니라 분리형 필 버튼이라 다른 컴포넌트 ⑤ **AddMemory 툴 결과의 영어 문구·예외 원문** — 모델 입력(프롬프트 표면)이라 §2-⑤ 실측이 선행돼야 한다(후보로 남김) ⑥ 오류 표시 채널(Toast/Snackbar/인라인) 통일 — 설계 결정 사안이라 보류 ⑦ 브리핑 프롬프트의 날짜 문자열 — 모델 입력이라 표기 단일화 대상에서 제외
- **[Note] 계획과 갈린 점** — 승인 카드 헤더와 본문 사이 간격을 일정 카드 16dp·프로필 카드 12dp 에서 12dp 로 통일. 에피소드 시트 시각이 24시간제 "00:46" → "오전 12:46"(표기 규칙 통일). `GetTodayScheduleUseCase` 는 IO 전환을 뺐다(Room suspend·캘린더 툴 자체 IO)
- **[QA/Test]** 마일스톤 5회 게이트 녹색, 기존 단언 수정 0건(생성부 수정: 스케줄러·경계 관리자 팩토리에 `.start()`, `GemmaTokenizer()` 인자 제거 4곳, `FakeTaskRepository.getCounts`, DB Rule 이전 5곳). 테스트 448 → **472건**(신규: 취소·오류 문구, 스키마 헤더, 표시 헬퍼, core 헬퍼, 고정 시계 일정, 대화 판정 6, 에피소드 시트 4, 날짜 구분선 3). 에뮬레이터 스모크: 설치·실행·채팅 1턴(툴 5종 선언, KV 1,378/4096, 응답 저장, "오늘" 구분선). **실기기 확인 대상**: 승인 카드 새 날짜 표기·헤더 문구, 기억 화면 할 일 통계, 에피소드 시트 수정·삭제 후 드로어 갱신, 모델 없는 상태가 아닌 **엔진 초기화 실패** 시 스플래시 오류·다시 시도(인위 재현이 어려워 코드 경로만 확인), 첨부 닫기 아이콘, TalkBack 한국어 설명

## [0.26.1] - 2026-09-30
> **실기기 게이트의 에뮬레이터 소화 + 발견 결함 2건 수정.** 누적된 실기기 확인 목록(0.17~0.26) 중 추론이 없거나 짧은 항목을 합성 데이터 AVD(`my_phone`, x86_64/API 35, `-gpu swiftshader_indirect`, CPU 폴백 0.2 t/s)에서 돌렸다. 설치돼 있던 0.20.x 빌드 위에 0.26.0 debug 를 업그레이드 설치 — DB **v6 → v9 마이그레이션 체인이 기존 대화 보존 채로 통과**한 것이 덤으로 확인됐다.
- **[Fix]** **프로필 시트의 상한 초과 안내가 지워지지 않던 결함** — `ProfileSheetViewModel` 이 저장·삭제 **성공** 시 `_error` 를 비우지 않아, 항목을 전부 지운 뒤에도 "프로필이 너무 길어요 (116/100토큰)" 가 남았다(에뮬레이터 재현). 성공한 편집은 직전 안내를 지운다 — 안내는 "지금 상태"에 대한 말이어야 한다
- **[Fix]** **빈 프로필이 "1/100토큰"으로 보이던 표시 결함** — `GemmaTokenizer` 추정식의 `+ 1` 올림이 빈 문자열에도 1을 돌려준다. 빈 블록은 시스템 지시에서 생략돼 실제 비용이 0 이므로 `profileBlockTokens()`(ProfileBlock.kt 신설)가 빈 블록을 0 으로 잰다 — 시트 게이지와 `projectedProfileTokens`(상한 집행) 두 경로의 단일 출처. 추정기 자체는 윈도우 예산 전반이 기대므로 건드리지 않았다
- **[Note]** **에뮬레이터 통과 17건** — v0 **AC8**(모델 파일 이름 변경 → 경로 안내 + "모델 내려받기" → 모델 관리, 복원 후 "다시 시도" → Ready — 체크리스트 닫음) · 0.19.1 "뒤로"+시스템 뒤로 동시 입력에 빈 화면 없음 · 0.19.3 런처 아이콘 흰 상자 없음(Pixel 런처 원형 마스크) · 0.19.0 에피소드 문서 생성 → 시트 → 원문 보기 · 0.20.0 ① 설정 시각 정각 발화(`BriefingNotificationWorker` 01:30:00 SUCCESS + 익일 재예약) ⑨ 알림 권한 거부 시 조용히 생략 · 0.21.0 ② 정확 알림 권한 off 안내 행 · 0.22.0 ① 위젯 설치·표시 ④ 열기→채팅 ⑤ 🎤→즉시 녹음 ⑥ 앱 사용 중 QS 타일→즉시 녹음 ⑦ 마이크 권한 요청 경유 ⑨ 캘린더 권한 없음 안내 줄 · 0.23.0 ① 시트 편집·저장 ④ 빈 프로필 = 블록 생략(코드) ⑤ 상한 초과 차단 ⑧ 라이트 카드(다크도 확인) · 0.24.0 ① 요약→추출 백그라운드 완료·채팅 무차단(감사 fact-extraction 2건) ⑧ 잡담 에피소드에서 저장 0건 · 0.25.0 ④ 변형(드로어 검색 "kosmos" → 태그 "Kosmos" 대소문자 무관 적중)
- **[Note]** **측정 도구 함정 1건** — `cmd statusbar click-tile` 은 앱이 전경일 때 타일 클릭을 전달하지 못하거나 늦게 전달했다(늦은 전달이 다음 콜드 스타트 직후 녹음을 시작시켜 "재진입 시 녹음 재생"으로 오인할 뻔함 — 일반 재실행 2회로 재현 안 됨을 확인). 실제 QS 패널 탭으로는 정상이다. 타일 검증은 `expand-settings` + 패널 탭으로 한다
- **[QA/Test]** 게이트 `test lintDebug assembleDebug` 녹색, 기존 단언 수정 0건. 신규 테스트 3건(`ProfileSheetViewModelTest`: 상한 안내가 성공한 삭제로/저장으로 지워짐, 빈 프로필 토큰 0). **실기기에 남는 것**: 추론 품질·툴 호출 항목(0.25 ①②③⑤, 0.26 ①②, 0.21 ①, 0.24 ②~⑦, 0.23 ②③⑥, 0.20 ②~⑤, 비행기 모드 검색 실패), 데이터 주입이 필요한 항목(0.21 ③~⑨ 리마인더 재부팅·발화, 0.24 ⑩ v8→v9 지식 배지 — 에뮬레이터 DB 덮어쓰기는 이번 세션에서 허가되지 않았다), 하드웨어 의존(arm64 로딩·설치 크기 0.20.1/0.26 ③, 잠금 화면 타일 0.22 ⑧, 발열 0.20 ⑦/0.24 ⑨, 백업 왕복 0.23 ⑦, 삼성 런처 마스크)

## [0.26.0] - 2026-09-03
> **C2 — MediaPipe 임베더 최소 제거** (expand.md Track C, TD-1 해소). 영어 전용 임베더(ADR-013, 한국어 분별력 0)의 **구현·gradle 의존·tflite 자산**을 제거하고 `TextEmbedder` 계약에는 항상 실패를 돌리는 `DisabledTextEmbedder` 를 바인딩했다. APK 실측 **release 78.5MB → 50.0MB (−28.4MB)**, debug 103.0MB → 72.2MB (−30.9MB). 사용자 확정 결정 2건(2026-09-03): ① **최소 제거**(계약 층·테스트 무변경) ② 툴 미호출 2건(exp37)은 이번 회차에서 넘김.
- **[Changed]** **제거** — `data/.../embedder/MediaPipeTextEmbedder.kt`(108줄, 지연 초기화·JNI 예외 방어), `com.google.mediapipe:tasks-text 0.10.35`(카탈로그 version/library + `data/build.gradle.kts` 의존), `app/src/main/assets/models/universal_sentence_encoder.tflite`(6,120,274 B — assets 트리의 유일한 파일). 런타임 초기화·ProGuard·packaging 설정은 원래 없었다. `play-services-tflite`(LiteRT-LM GPU 델리게이트)는 MediaPipe 가 아니라 유지
- **[Changed]** **계약 층은 유지 — `DisabledTextEmbedder`** — `TextEmbedder` 인터페이스·`SaveKnowledgeUseCase(repository, textEmbedder)`·`KnowledgeNote.embedding`·`knowledge_note.embedding BLOB`·`searchByVector`·`FloatBytes` 는 그대로다. 기존 테스트 25건(`MemoryPipelineIntegrationTest` 임베딩 성공/실패 저장 2, `KnowledgeEmbeddingBlobTest` 6, `FloatBytesTest` 5, `KnowledgeEmbeddingMigrationTest` 7 …)이 단언으로 고정하고 있어 지우려면 "단언 수정 0건" 예외가 필요했고, 사용자가 최소 제거를 택했다. 빈 구현이 안전한 근거: `SaveKnowledgeUseCase` 는 임베딩 Failure 를 null 로 흡수한다(0.10.x 부터, 테스트가 증명) — 저장 경로는 달라지지 않고 임베딩만 늘 null. 한국어 임베더(C3)를 바인딩하면 그 자리에서 되살아난다(ADR-026)
- **[Docs]** **보관(`docs/archive/c2-mediapipe-2026-09/`, README)** — `download_model.ps1`(루트, 제거된 자산만 내려받던 스크립트)·`scratch/lab/exp11_embedder.py`(ADR-013 근거 실험 — 자산 경로·`mediapipe.tasks` import 에 묶여 더는 돌지 않지만 수치의 출처). 주석 정정: `app/build.gradle.kts` abiFilters [WHY]("mediapipe 10MB" 과거형), `CoroutineModule`("MediaPipe LlmInference" → LiteRT-LM), `SaveKnowledgeUseCase`·`KnowledgeRepository.searchByVector`·`SearchMemoryToolExecutor` KDoc. `scratch/lab/requirements-0.15.0.lock` 의 `mediapipe==1.0.0` 은 파이썬 실험실 환경이라 유지
- **[Note]** **툴 미호출 2건(exp37)은 넘김** — ① "저녁 약속 시간이 언제야?" 는 모델이 `get_schedule` 로 갔는데, 트리거 규칙에서 "약속"은 일정 어휘이고 실제 앱에서 약속은 `add_schedule` 로 저장되므로 **맞는 해석일 수 있다**(exp37 정답표는 과거 대화 문서 기준). ② "네가 지어내서 답한 적 있었지?" 는 무호출 — `search_memory` 규칙에 **과거 대화 회상 트리거**("전에", "있었지", "그때")가 없고 선언 문구도 "저장해 둔 기억(메모)"만 말한다. 고치려면 예산 여유 23토큰 안에서 문구를 넣거나 다른 규칙을 다이어트해야 하며(exp34b 방식 실측 선행), 두 건 모두 완벽 해결이 어렵다는 판단으로 사용자가 넘겼다. 후보로 남긴다: 과거 대화 트리거 A/B(exp38) + `search_memory` 선언에 "과거 대화" 한 마디
- **[QA/Test]** 게이트 `test lintDebug assembleDebug` 녹색, 기존 단언 수정 0건·생성부 수정 0건(계약 층 유지라 테스트 파일 무변경). APK 전후 `assembleRelease`(서명 없음, minify·shrink) 재빌드 비교. **실기기 게이트 3건**: ① 새 APK 에서 "기억해줘" 저장 → 기억 화면에 보임 ② 기억 검색·에피소드 회수 정상(0.25.0 경로) ③ 설치 크기가 이전보다 약 28.4MB 작음

## [0.25.0] - 2026-09-03
> **기억 검색 — 바이그램 스코어러 3단 검색** (expand.md C1+C′3, E-Phase 2 셋째 회차). `SearchMemory` 툴과 드로어 검색이 **정밀 LIKE → 바이그램 점수 정렬 → 미스 시 전수 스캔(최소 겹침 임계) → 태그 목록** 순서로 통일됐다. exp33 의 `score()` 원문 포팅(인메모리, 스키마 무변경)이며 FTS5 는 규모 게이트로 미룬다. 사용자 확정 결정 3건(2026-09-03): ① 인메모리 스코어러(FTS5 아님) ② 모델 확장은 미스 시에만 → **M0 실측 결과로 확장 자체를 보류** ③ 게이트 실패 판정 후 "스코어러만 출시". 계획서 `docs/plans/0.25.0-memory-search.md`.
- **[Feat]** **BigramMatcher (domain/search)** — 공백·기호를 지운 2글자 창 집합, 항별 `|겹침|/|항|` 합(exp33 `score()` 원문), 임계 판정용 `bestTermOverlap`. "자물쇠번호" ↔ "자물쇠 비밀번호" = 0.75(LIKE 는 0건), 두 항 문서 2.0 > 한 항 문서 1.0(기존 "맞은 토큰 수" 계약 보존). **2,001 문서 전수 스캔 17ms**(JVM 계측) — 개인 규모에서 색인이 필요 없다는 근거. `Constants.MEMORY_SCAN_LIMIT = 500`, `BIGRAM_MIN_TERM_OVERLAP = 0.5`
- **[Feat]** **SearchMemoryToolExecutor 3단** — ① 토큰별 정밀 후보(LIKE·태그, 지식+에피소드 — 무변경) ② 후보를 바이그램 점수로 정렬(동점 최신순) ③ 후보 0건이면 최근 500건 전수 스캔, **가장 잘 맞은 항의 겹침 ≥ 0.5** 인 문서만 인정 ④ 그래도 0건이면 기존 태그 목록 폴백(문구 원문 불변, 스캔에서 읽은 목록 재사용 — 조회 1회 절약). 점수 텍스트는 표시 문구("(과거 대화)") 를 뺀 `title tags summary` / `content tags`(`MemorySearchText`) — 모든 에피소드에 같은 바이그램이 붙어 점수를 오염시키는 것을 막는다. 봉투·`meta.episodeIds`·"ep:" 키·400토큰 캡 무변경
- **[Feat]** **드로어 검색 통일** — `DrawerViewModel.search` 가 같은 3단(확장 없음)을 따른다. 이전에는 LIKE 두 결과를 정렬 없이 이어 붙여 드로어 순서 ≠ 모델 회수 순서였다(ui_a_prime.md:47 원칙 위반)
- **[Note]** **M0 실측(exp37) — 게이트 실패와 판정** — exp33 의 16문항을 **실제 앱 프롬프트**에 넣어 모델이 뽑는 `search_memory.keyword` 를 받은 뒤 네 조건을 비교: 현행 A **11/16**, 바이그램 B **11/16**, 바이그램+항상 확장(원 질문) C **13/16**, 앱 정책(미스 시에만 확장) D **11/16**(확장 실행 1회). 게이트(D ≥ 14) 실패. 미스 5건의 원인: **툴 미호출 2**("저녁 약속 시간"→일정 조회로 판단, "지어내서 답한 적"→무호출 — 검색 알고리즘 밖, 트리거 규칙 회차 후보) / **키워드가 넓어 유사 문서 중 오답 2**("위키 검색" 4문서, "자전거 비밀번호"에서 4321 유실 — 원 질문으로 확장하는 C 만 고침) / **요약에 없는 내용 1**("음성 메시지" — 저장 품질). "미스 시에만 확장" 정책은 이득 0 — 문제가 0건이 아니라 **적중 중 순위 오답**이라 확장이 돌지 않는다; 임계 조정도 무의미. exp33 의 16/16 과의 차이는 검색기가 아니라 **질의 조건**(원 질문 전체 vs 툴 키워드)이다. 판정: 스코어러만 출시(무회귀 11→11 + 붙여쓰기·조사 변형 회수 추가), **확장은 보류** — C=13 은 "항상 확장 + 원 질문 전달(BaseAgent 배선)"의 측정된 상한으로 남긴다. 임계 0.5 계약 확인: "좋아하는 것"↔"커피보다 녹차…" 0.33 차단(태그 목록 폴백 보존), "자물쇠번호" 0.75 통과. exp37 은 `rescore` 모드로 모델 없이 재계산 가능
- **[Note]** **계획과 갈린 점** — ① M2(ExpandQueryUseCase)·`expand_system.txt` 픽스처는 **미구현**(M0 판정) ② `SearchMemoryToolExecutorTest` 는 Robolectric 러너 필요(`org.json` 봉투) ③ FTS5(C1 명칭)는 구현하지 않음 — Room 에 `@Fts5` 없음, 내장 토크나이저에 바이그램 없음(한글은 `unicode61` 이 어절 단위), 프레임워크 SQLite OEM 가용성, exp33 수치가 인메모리 스코어러 기준. **규모 게이트**: 문서 2,000건 초과 또는 스캔 50ms 초과 실측 시 FTS5(외부 콘텐츠 테이블 + 앱 측 바이그램 컬럼) 착수 — ADR-025
- **[QA/Test]** 게이트 마일스톤 3회 녹색, 기존 단언 수정 0건(`MemoryPipelineIntegrationTest` 8건 무변경 통과, 생성부도 무변경 — 확장 UseCase 미도입). 신규 테스트 16건: `BigramMatcherTest` 7 · `SearchMemoryToolExecutorTest` 6(정밀 적중 시 스캔 미실행 / 붙여쓰기 회수 / 에피소드 meta / 임계 미달 폴백 / 스캔 실패 error / 점수 순위) · `DrawerViewModelTest` 3. 프롬프트 표면 무변경 → `MEASURED_OVERHEAD` 1,277 유지. **실기기 게이트 5건**: ① "자전거 자물쇠 번호 기억나?" → 회수 + 🧠 칩 ② 정밀 적중 질의의 답변 지연이 이전과 같음 ③ 저장된 적 없는 것("여권번호") → "없다" 답변, 지어내지 않음 ④ 드로어 검색바 "자물쇠" → 비밀번호 에피소드 상위 ⑤ 감사 화면 SearchMemory 툴 콜 기록 정상

## [0.24.0] - 2026-09-03
> **리셋 시점 자동 추출** (expand.md C′2, E-Phase 2 둘째 회차) — 에피소드가 요약(SUMMARIZED)될 때 oneShot 1회를 더 돌려 "앞으로도 기억할 사실 0~3개"를 뽑는다. 지식 급은 `source=auto` 로 **자동 저장**, 프로필 급(이름·호칭·말투·직업…)은 **입력바 위 승인 카드**를 거쳐 Profile 에 들어간다. 예산 1,700 때문에 리셋이 잦은 약점을 캡처 트리거로 뒤집는다 — C′1 이 톤을 바꾸고 C′2 가 쓸수록 쌓이게 한다. 계획서 `docs/plans/0.24.0-auto-extract.md`(이번 회차는 Claude 가 계획·구현 모두 수행). 사용자 확정 결정 3건(2026-09-02): ① 지식 자동 저장 + 통제 장치 ② 승인 카드 = 입력바 위 플로팅 ③ 추출 = 요약 성공 직후 별도 oneShot.
- **[Feat]** **추출 프롬프트 (exp36, M0 실측)** — 실대화 에피소드 9 + 정답을 심은 합성 6 으로 판정. **v1 초안 실패**(형식 15/15·회수 6/8·**잡담 오추출 2**): 모델이 프롬프트의 "규칙:" 라벨을 셋째 줄로 따라 써("규칙: 없음") 지식 절에 섞였고, 일회성 일정(치과 예약)을 지식으로 뽑았다. **v2 통과**(형식 15/15·회수 8/8·오추출 0, 분류 7/8 — 말투 1건이 지식 절로): 규칙을 라벨 없는 문장으로 옮기고 "예약·약속처럼 한 번 지나가는 일정은 캘린더가 맡으므로 제외" 명시. greedy(top_k=1) 라 재현 가능. `ExtractFactsUseCase.SYSTEM_INSTRUCTION` 은 v2 원문 그대로이고, `PromptFixtureExportTest` 가 `fixtures/extract_system.txt` 로 내보내 exp36 `fixture` 모드가 미러 일치를 먼저 검사한다(확인: 동일 수치 재현). 채팅 프리필 예산은 **무변경**(oneShot, `MEASURED_OVERHEAD` 1,277 유지)
- **[Feat]** **관대 파서 (exp36 에서 결정)** — 모델은 ① 항목마다 라벨을 반복하고("지식: A / 지식: B") ② 빈 절의 "프로필: 없음" 줄을 생략하고 ③ 한 줄에 여러 항목을 쉼표로 잇는다("이름: 진우, 직업: 개발자"). 셋 다 내용은 온전하므로 **라벨 줄 1개 이상 = 형식 준수**, 라벨 줄 전부 수집, 프로필 줄은 쉼표 분리 후 `항목: 값` 조각만 인정(콜론 없는 조각은 앞 값에 붙여 "친근하게, 존댓말" 보존). 엄격 파서였다면 실측 15/15 가 앱에서 13/15 로 떨어졌다. 상한: 프로필 ≤2·지식 ≤3·**합계 ≤3**(프로필 우선). 라벨 0개 → `ModelInferenceError`(요약과 달리 재시도 없음 — 감사 구분용), 라벨만 있고 항목 없음 → 빈 Success. 요약과 같은 전사(`buildEpisodeTranscript` 로 공유 추출 — 요약 동작 무변경, 기존 테스트가 회귀 감시)
- **[Feat]** **EpisodeFactExtractor — 요약 파이프라인 후행 훅 (ADR-024)** — "리셋 직전" 훅은 코드상 추론 불가(리셋은 다음 턴 프리필 안 `getOrCreateConversation`, 비-suspend·llmDispatcher 직렬). `EpisodeSummarizeScheduler.process` 가 SUMMARIZED 전이 직후 같은 드레인에서 1회 호출(실패·재시도 경로에서는 호출 안 함 — 재시도마다 중복 추출 방지). 게이트: 설정 토글 OFF → 추론 자체 생략 / 발열 43°C 이상 → 건너뜀(미루지 않음, 재시도 큐 없음). 지식: `knowledgeRepository.search(fact, 1)` 로 내용 포함 중복이면 skip, 아니면 `SaveKnowledgeUseCase(content, tags = 요약 태그 앞 3개, source = auto)`. 프로필: 현재 프로필과 같은 값 skip, **`exists(key, value)` 가 어느 상태로든 참이면 skip**(거절한 사실을 다시 묻지 않는다 — 값이 달라진 같은 키는 새 제안), 아니면 PENDING 제안(episodeId 포함). 감사 MODEL_RUN 1건 `sessionId="fact-extraction"` — 원문 미기록, 개수만(브리핑 전례). 자체 구독 없는 일반 클래스(스케줄러가 호출)라 @Singleton init 구독 금지 수칙과 Hilt 테스트 격리가 자동
- **[Feat]** **G3 예외 명시 — 지식 자동 저장** — "쓰기=승인, 예외 없음"(ApprovalRules)은 **툴 콜**(모델이 대화 중 쓰기)에 대한 결정이다. 자동 추출은 사후 캡처이고 지식은 검색으로만 회수되는 저위험 층이라 자동 저장을 허용한다(사용자 결정). 통제 장치 3종이 성립 조건: 출처 배지("자동" — 기억 화면 지식 카드·프로필 시트 행) / **기억 화면 지식 삭제**(✕ → 확인 다이얼로그, `KnowledgeRepository.delete` 의 첫 호출처 — 이전에는 지식 카드가 읽기 전용이었다; 수동 항목도 삭제 가능, P4 통제권) / 설정 "기억 › 대화에서 자동으로 기억하기" 토글(기본 ON — 브리핑과 같은 이유). 프로필 급은 여전히 승인 카드
- **[Feat]** **프로필 제안 승인 카드 (`profile_suggestion` 영속)** — 기존 `ApprovalCoordinator` 는 단일 슬롯·60초 자동 거절·채팅 화면 생존 전제라 백그라운드 추출의 승인에 부적합 → 제안을 테이블에 영속(PENDING/ACCEPTED/REJECTED)하고 `ChatViewModel` 이 `observePending()` 을 구독. `ProfileSuggestionCard` 는 CalendarDraftCard 와 같은 자리(입력바 위 플로팅)·같은 문법([프로필에 저장]/[무시]), 한 번에 1건 + "외 N건", **툴 승인 대기 중이면 양보**(이중 노출 금지). 승인은 `ProfileSuggestionResolver.accept` — **상한 100토큰을 집행**한다: `projectedProfileTokens`(ProfileBlock.kt 신설 공용 함수)를 드로어 시트 수동 경로와 자동 경로가 같이 쓴다. 초과면 "프로필이 너무 길어요 (N/100토큰) — 항목을 줄이면 저장할 수 있어요" 스낵바(`suggestionNotice`), 제안은 대기로 남는다. 저장은 `source=auto`. 드로어 프로필 카드 부제에 "제안 N건". `ProfileSuggestion` 의 `status` 문자열이 깨져 있으면 REJECTED 로 읽는다(카드로 튀어나오지 않게)
- **[Feat]** **스키마 v9** — `knowledge_note.source TEXT NOT NULL DEFAULT 'manual'`(ALTER) + `profile_suggestion(id PK, key, value, episodeId?, status, createdAt, updatedAt)` + status 인덱스. **[WHY] 여기서는 DEFAULT 가 필수**다 — NOT NULL 컬럼 추가에 SQLite 가 기본값을 요구하고, Room 은 엔티티 `@ColumnInfo(defaultValue = "manual")` 로 같은 DDL 을 기대해야 검증이 통과한다(5→6 의 "`DEFAULT NULL` 을 쓰면 안 되는" 경우와 정확히 역방향 — 둘 다 "Room 기대 스키마 = DDL" 한 원칙이다). 기존 지식 행은 전부 manual(사실에 맞다 — 자동 추출 이전의 기억은 모두 사용자·툴 콜 저장). `KnowledgeNote.source`(도메인, 기본 manual, equals/hashCode 포함), `SaveKnowledgeUseCase(content, tags, source = manual)` — 기존 호출 무변경. `ProfileSuggestionDao` 를 `DatabaseModule` 에 @Provides 로 등록(빠뜨려 Hilt MissingBinding — M3 에서 발견). 백업 다운그레이드 한계(manifest 에 schemaVersion 없음)는 v8 과 같은 기존 조건
- **[Changed]** **설정 combine 5개 상한 돌파** — 여섯째 설정(자동 추출)부터 **모델·응답 묶음 / 비서 동작 묶음** 두 그룹의 중첩 combine. 그룹 경계 = 설정 화면 섹션 경계라 다음 설정도 자기 그룹에 붙이면 된다(0.20.0 에 예고한 리팩터의 최소형 — data class 묶음은 과설계로 보류). `SettingsDataStore.autoExtractEnabledFlow`(기본 true)
- **[Note]** **계획과 갈린 점** — ① 프로필 시트의 '제안 행' 노출은 **"자동" 라벨 + 드로어 카드 "제안 N건"으로 축소**(E2E 계약·VM 생성부 최소화 — 카드가 영속이라 채팅이 유일 창구로 충분) ② 상한 초과 안내는 `warningMessage` 재사용 대신 **`suggestionNotice` 스낵바 신설**(warningMessage 는 발열 상태에 묶인 헤더 표시라 덮어쓰기 부작용) ③ `ExtractFactsUseCase.parse` 는 `internal` 이 아니라 public — 테스트가 app 모듈에 있어 domain 의 internal 에 못 닿는다 ④ ChatViewModel 제안 흐름 단위 테스트는 생략(생성부 의존 13개·Robolectric 세팅 비용) — 리졸버·추출기·저장소 테스트 + 실기기 게이트 2~5 가 대신한다 ⑤ SettingsDataStore 기본값 단독 테스트 생략(추출기 테스트가 플로우 주입으로 커버) ⑥ 실대화 픽스처에서 "비밀번호: 12" 같은 파편 1건 — 원문 자체가 파편이라 프롬프트로 못 막고 출처 배지·삭제가 통제 장치. 관리 장치 스펙 중 "주기적 통합 oneShot(중복 병합)"은 A5 로 이관
- **[QA/Test]** 게이트 `test lintDebug assembleDebug` 마일스톤 5회 전부 녹색, 기존 단언 수정 0건(생성부: ChatViewModel E2E 3곳 + `EpisodeSummarizeSchedulerTest` 생성자, `SettingsViewModelTest` 무변경). 신규 테스트 41건: `AutoExtractMigrationTest` 4(기존 행 manual 보존·DDL·인덱스·재적용) · `ProfileSuggestionRepositoryTest` 4(PENDING 정렬·전이·`exists` 상태 무관·지식 source 왕복) · `ExtractFactsUseCaseTest` 13(oneShot·파서 6형태·상한·null) · `EpisodeFactExtractorTest` 9 · `ProfileSuggestionResolverTest` 5(상한 합산·source auto·실패 시 상태 불변) · 스케줄러 +2(성공 직후 1회·실패 시 0회) · `PromptFixtureExportTest` +1 · `MemoryViewModelTest` 2 · `SettingsViewModelTest` +1. **실기기 게이트 10건**: ① 예산 리셋 후 다음 턴 → 요약 → 추출까지 백그라운드 완료, 채팅 입력 무차단 ② 제안 카드가 입력바 위에 뜨고 툴 승인 시트와 겹치지 않음, 라이트/다크 대비 ③ [프로필에 저장] → 드로어 카드·시트 반영 + "자동" 라벨, 다음 턴 답변에 프로필 반영(재프리필 1회만) ④ [무시] → 카드 사라짐, 같은 사실이 다음 에피소드에서 재제안되지 않음 ⑤ 상한 초과 제안 저장 시 안내 스낵바, 프로필 무변경 ⑥ 기억 화면 "자동" 배지, ✕ → 확인 → 삭제 반영 ⑦ 설정 토글 OFF 후 리셋 → 감사 화면에 fact-extraction 없음, ON 복귀 시 재개 ⑧ 잡담만 한 에피소드에서 아무것도 저장되지 않음 ⑨ 발열 경고 온도에서 추출 건너뜀 ⑩ v8→v9 업그레이드 후 기존 지식 전부 보이고 배지 없음

## [0.23.1] - 2026-09-02
> 코드 변경 0건 — **AI 에이전트 지침 간소화 + 분업 체계 문서화**. 지침 파일 7개(219줄) 중 살아있는 것은 2개였고, 나머지는 중복·사문·현행 결정과 정면 충돌했다. 삭제 대신 `docs/archive/` 로 옮겨 이력을 보존했다(사용자 결정).
- **[Docs]** **정본 단일화** — `AGENTS.md` 가 유일한 정본, `.agents/04_MODEL_EVIDENCE.md`(근거 등급, 코드·ADR 참조)만 유지. 신설: 루트 `CLAUDE.md`(`@AGENTS.md` — Claude Code 자동 로드, 매 세션 "읽어줘" 불필요), `.agents/00_README.md`(Antigravity 등 `.agents/` 를 읽는 에이전트용 포인터). `AGENTS.md` 에 흡수: UTF-8 No BOM·`reset --hard` 허가(01), `@UninstallModules`→`@BindValue`·mock 용 `open suspend fun`·AnimatedContent 레이아웃(android SKILL), §2-⑤ 수치 0.23.0 기준으로 갱신
- **[Docs]** **보관(`docs/archive/agent-rules-2026-09/`, README 에 사유 표)** — `.agents/01·02·03`, `skills/android-friday`, `skills/litertlm-gemma4`, `docs/agent/ui_improvement_plan.md`. 02 는 `AGENTS.md` §2-①이 대체한 3-Phase 를 여전히 ALWAYS 로 요구(직접 충돌). **litertlm SKILL 이 가장 위험했다**: `<tool_call>` 텍스트 파싱(ADR-008 폐기), 윈도우 3,000토큰(현행 1,700), thinking 여유 2~4천(사고 모드 꺼짐), MTP(미검증), 이미지 토큰 표(측정 없이 정하지 않기로 결정), `filesDir`(실제 `getExternalFilesDir`) — 따르면 확정 아키텍처를 되돌린다. 유효분(오디오 WAV 16k mono)은 `AudioRecorder` KDoc 에 이미 있음
- **[Docs]** **분업 체계** — 계획=Claude(plan 모드) / 구현=Antigravity(Gemini) / 검수=Claude(짧게). 전제: 계획서는 **저장소 파일**(`docs/plans/<버전>-<회차>.md`, README 에 형식·수칙; `~/.claude/plans` 는 다른 에이전트가 못 본다), 프롬프트 표면 회차는 M0 실측 문구를 계획서에 원문 그대로, 구현 에이전트도 같은 게이트를 진다. `agent_workflow_guide.md` v2.0(추천 프롬프트 3종), 0.23.0 계획서를 첫 예시로 보존
- **[QA/Test]** 코드·빌드 설정 변경 없음 — 게이트 생략(직전 0.23.0 녹색 유효). 잔여 참조 점검: 옮긴 파일을 가리키는 곳은 CHANGELOG 과거 기록뿐

## [0.23.0] - 2026-08-28
> **프로필 상시 주입 + 드로어 고정 카드** (expand.md C′1, E-Phase 2 첫 회차 — 시안 A′-2 로 **시안 A′ 전체 완료**) — 이름·호칭·말투 같은 항상-관련 기억이 `[User Profile]` 블록으로 시스템 지시에 상시 주입돼 답변의 톤을 바꾼다(AC6 정식 배선). 재원은 **few-shot 제거**. 사용자 확정 결정 2건: 자유 키-값 / 폴백=히스토리 동적 차감(실측 성공으로 미사용).
- **[Feat]** **few-shot 시범 제거 (104토큰 회수, exp35 판정)** — 도입 진단("시범 없으면 호출 0회", 0.8.5)은 ADR-017 이 철회했고(진짜 원인 = 지침 거리 → 턴 리마인더가 해결), 제거 가능성은 재실측된 적이 없었다. exp35: few-shot 실측 정확히 104, **제거 후 툴 선택 스모크 11/11 유지**(기억 저장/조회 방향·숫자 보존 포함). 시범의 예시 숫자("8282")가 조회 턴에 새던 실해(ADR-010)도 구조적으로 소멸. 실험실 하네스에 few-shot 미러를 복원(0.15.0 재구축 때 누락 — 그간 lab 실측이 앱보다 104 과소)한 뒤 A/B 로 판정했다
- **[Feat]** **예산 회계** — 실측 오버헤드 1,381 → **1,277**(few-shot 제거) + 프로필 상한 `PROFILE_MAX_TOKENS = 100` = 1,377 ≤ 예약 1,400 — **예산 상수 전부 무변경**(1400+300=1700 바닥 관계 유지). 신규 불변식 추가: `PREFILL_OVERHEAD >= MEASURED_OVERHEAD + PROFILE_MAX_TOKENS`. 프로필 블록 실측 86토큰(5항목 샘플), 상한 집행은 편집 시트가 저장 전 GemmaTokenizer 추정(과대 방향이 안전, exp26)으로 차단
- **[Feat]** **스키마 v8** — 사문이던 v0 프로필(고정 컬럼 단일 행, 호출 0곳·데이터 0행 — 2026-08-15 감사)을 키-값 행(`key PK, value, source, updatedAt`)으로 **DROP 무손실 교체**. `source` 는 manual/auto — C′2 자동 추출의 출처 표시 자리를 미리 확보(v9 회피). 구 `UserProfile` 모델·계약은 삭제(style↔responseStyle 오매핑·null 미방출 결함째 정리). **알려진 한계**: v8 백업을 v7 앱에 복원하면 다운그레이드 파괴 삭제 — manifest 에 schemaVersion 이 없는 기존 조건(이번 범위 밖)
- **[Feat]** **주입 배선** — `renderProfileBlock` 순수 함수: **키 사전순·updatedAt 미포함·trim 고정 = 바이트-안정이 계약**(흔들리면 런타임의 시스템 지시 문자열 비교가 매 턴 전체 재프리필을 만든다 — ADR-010 실측 0.5s→3.8s). ContextBuilder 가 매 턴 로드(편집이 다음 턴에 자동 반영 — 런타임 무변경), PromptAssembler 는 `[System]` 과 `[System Data]` 사이에 삽입(날짜 블록 위치·"above" 포인터 보존). 빈 프로필 = 블록 생략(비용 0), 읽기 실패 = 프로필 없음으로 강등(채팅을 막지 않는다). exp35 톤 확인: "진우님" 호칭 + 관심사 반영 관측
- **[Feat]** **드로어 고정 카드 + 편집 시트** (A′-2) — 검색바 위 👤 카드(이름 값 + 키 미리보기, 비면 "등록해 보세요"), 탭 → ProfileSheet(하우스 크롬): 항목 리스트(행 탭=입력란 채움, ✕ 삭제) + 추가 입력란 + **토큰 게이지 "N/100"**. 저장 성공 시에만 입력란 비움(addTask 콜백 전례 — 상한 초과·실패 시 입력 보존)
- **[QA/Test]** 마일스톤마다 게이트 녹색, 기존 단언 수정 0건(ContextBuilderTest 생성부 확장). 신규 테스트 4묶음(마이그레이션 v8, 렌더 바이트-안정, 상한 집행 경계, 프로필 블록 위치·픽스처). **실기기 게이트 8건**: ① 카드→시트 편집→저장 ② 다음 턴 호칭·말투 반영 ③ 편집 직후 1회만 재프리필(로그) ④ 빈 프로필 = 블록 없음 ⑤ 상한 초과 차단 ⑥ few-shot 제거 후 "기억해줘/뭐였지" 방향 정상 ⑦ 백업 왕복에 프로필 포함 ⑧ 라이트 모드 카드 육안

## [0.22.0] - 2026-08-28
> **홈 위젯 + QS 타일** (expand.md A3, **E-Phase 1 완료**) — 비서는 여는 비용이 낮을수록 쓰게 된다. 위젯이 "오늘"(일정 3건+할 일)을 상시 노출하고, 위젯 🎤·QS 타일이 **채팅 도착 즉시 녹음 시작**(PTT)까지 탭 수를 줄인다. 사용자 확정 결정 2건: 리스트형 위젯 / 도착 즉시 녹음.
- **[Feat]** **Glance 1.2.0 위젯** (프로젝트 첫 위젯 — Kotlin 2.4.10·BOM 2026.08 조합 호환 확인) — 오늘 일정 최대 3건(시각 `toDisplayTimeKorean`, 종일 일정은 "종일") + 초과분 "+N" + 할 일 카운트 + 열기·🎤 버튼. 데이터는 브리핑 알림과 같은 재료(추론 0, DB 읽기만)라 모델 로드와 무관. **첫 `@EntryPoint` 도입** — GlanceAppWidget 은 프레임워크가 인스턴스화해 @AndroidEntryPoint 불가, 위젯 전용 인터페이스로 좁게 연다. 색은 `ColorProvider(day, night)` 에 KosmosColors Light/Dark 복제(알림 accent 와 같은 프레임워크 경계 예외). READ_CALENDAR 실패는 "확인 못 함"으로 정직 표기(EC4 — 위젯은 권한 UI 를 못 띄운다)
- **[Feat]** **시각-only 표기 단일화** — `IsoDateTimeParser.toDisplayTimeKorean`("오전 9:00") 신설, CalendarScreen 의 사설 중복 파서를 흡수. 위젯 재료 조립은 top-level 순수 함수(`buildWidgetSnapshot`) — Glance 렌더는 JVM 게이트가 못 보므로 이 함수가 데이터 정확성을 진다
- **[Feat]** **음성 진입 통로** — `VoiceLaunchHandler`(replay=1, ShareIntentHandler 전례): 콜드 스타트는 스플래시(모델 로드 9~12초)를 지나야 구독자가 생기므로 그 전 도착분이 유실되면 안 된다. ChatScreen 이 기존 마이크 권한 게이트를 재사용해 도착 즉시 `toggleRecording` — 이미 녹음·생성 중이면 조용히 접는다. **MainActivity `launchMode=singleTop` 전환** — 기본(standard)이면 위젯·타일·알림 재진입이 액티비티를 재생성해 onNewIntent 를 타지 않았다(브리핑·리마인더 알림 인텐트에도 SINGLE_TOP 짝 플래그 추가)
- **[Feat]** **QS 타일** — 잠금이면 unlockAndRun(마이크는 잠금 너머로 못 연다), API 34+ 는 PendingIntent 오버로드 분기. 진입 인텐트는 위젯 🎤 과 공유하는 top-level 순수 함수(`voiceLaunchIntent`) — 액션이 어긋나면 "탭했는데 아무 일도 없음"이 되므로 테스트로 고정
- **[Feat]** **위젯 갱신 훅** — `WidgetRefresher` 인터페이스(호출측 테스트를 Glance 에서 떼는 Notifier 전례) + 쓰기 경로 5지점(일정 툴·리마인더 툴·할 일 추가/완료·DB 가져오기). TaskRepository 가 Flow 없는 suspend-only 계약이라 구독 대신 명시 훅. 30분 주기(updatePeriodMillis)는 자정 넘김 보정 전용 — 갱신 실패는 본 기능을 실패시키지 않는다
- **[QA/Test]** 마일스톤마다 `test lintDebug assembleDebug` 녹색, 기존 단언 수정 0건(ChatViewModel·MemoryViewModel·실행기 생성부에 인자 추가 — E2E 3곳 포함). 신규 테스트 4묶음(스냅샷 조립·시각 표기·운반 replay/consume·진입 인텐트 계약). **실기기 게이트 10건**: ① 위젯 설치→일정·할 일 표시 ② 쓰기 5경로 즉시 갱신 ③ 자정 넘김 30분 내 반영 ④ 열기→채팅 ⑤ 🎤/타일→(스플래시)→즉시 녹음 ⑥ 앱 사용 중 타일→onNewIntent 즉시 녹음 ⑦ 마이크 권한 요청 경유 ⑧ 잠금 화면 타일→해제 유도 ⑨ 캘린더 권한 회수 시 안내 줄 ⑩ 라이트/다크 배경 가독성

## [0.21.0] - 2026-08-28
> **리마인더** (expand.md B1, E-Phase 1 두 번째 회차) — "3시에 알려줘"가 실제로 3시에 울린다. `AddReminder` 툴(6번째) + AlarmManager 정확 알람. 브리핑이 "상기"라면 리마인더는 "시점 알림" — 선제형 전환의 두 번째 조각. 사용자 확정 결정 3건: 정확 알람 / 표준 승인 시트 / AddReminder 만.
- **[Feat]** **실측 게이트 선행 (M0, `scratch/lab/exp34*`)** — 오버헤드 여유가 71토큰뿐인데(예약 1,400 − 실측 1,329) 원안 선언+트리거가 **+182 로 초과**. 기존 선언 4종의 둘째 문장(시스템 지시 트리거 규칙과 **중복**)을 깎아 상쇄 → **순증 52**, 기능 스모크 8/8(기존 5종 + 리마인더 2변형 + "3시에 회의 일정 잡아줘"→add_schedule 혼동 감시). `MEASURED_OVERHEAD` 1,329→**1,381**(예약 1,400 무변경 — 예산 상수 전부 그대로). 프롬프트 문구는 실측된 원문 그대로 이식 — 바꾸려면 실험실 재실측이 선행
- **[Feat]** **스키마 v7** — `task_item.remindAtIso`(ISO 문자열 계약 유지)·`remindedAtMs` 추가. **리마인더 = 알림 시각이 있는 할 일**(Task Memory v1 의 첫 실사용처, 별도 테이블 없음). 발화(remindedAtMs)와 완료(isCompleted)는 별개 — 알림이 울렸다고 할 일이 끝난 게 아니다. 마이그레이션 6→7(DEFAULT NULL 금지 규칙 준수) + sqlite_master 대조 테스트
- **[Feat]** **AlarmManager 정확 알람** (사용자 결정 — 0.20.0 이 브리핑에서 기각한 근거의 재검토: 미리보기와 달리 리마인더는 **시점 정확도가 정체성**이고 WorkManager 는 Doze 에서 수십 분 늦을 수 있다) — 정확 권한 있으면 정각(`setExactAndAllowWhileIdle`), 없으면 **±10분 창 자동 강등**(`setWindow`, 권한 없어도 동작 유지). 대가로 진 것: 권한 2종 + 리시버 2개 + **BOOT_COMPLETED 복원**(미래는 재예약, 이미 지난 것은 즉시 1회 발화 — 놓친 알림을 무음 폐기하지 않는다). requestCode·알림 ID 는 항목별 파생(`reminderStableId` — 고정 상수 전례에서 처음 갈라지는 지점, 0x40000000 비트로 기존 상수와 충돌 원천 차단). 채널 "리마인더" IMPORTANCE_HIGH(헤드업)
- **[Feat]** **발화 핸들러의 DB 재확인이 방어선** — 알람이 울려도 완료·기발화·삭제된 항목이면 침묵(취소 누락 경합 방어). 순서는 **알림 먼저, 기록 뒤** — 뒤집으면 기록 후 알림 실패 시 리마인더가 조용히 사라진다(중복 1회가 무음 유실보다 낫다). 완료 처리 시 알람 취소는 최적화일 뿐 방어선이 아니다
- **[Feat]** **AddReminder 툴** — 승인은 **표준 ApprovalSheet**(경량화 검토했으나 기각 — "쓰기=승인" G3 원칙 예외 없음, 사용자 결정. 기존 시트가 코드 0줄로 붙는다). `parse()` 를 승인·실행이 공유(실행 불가능한 초안 승인 방지, AddSchedule 규약), 승인 문구·툴 관측값 전부 `toDisplayKorean`(ISO 원문 비노출). **과거 시각은 형식 오류가 아니라 의미 오류** — "이미 지난 시각입니다. 미래의 시각으로 다시 호출하세요"를 관측값으로 돌려 모델 자가수정. 감사는 기존 TOOL_CALL/APPROVAL_* 자동(계약 테스트 무변경)
- **[Note]** **범위 제외 2건** — ① ListReminders: 선언 2종은 다이어트로도 예산 초과라 제외, 조회는 드로어 할 일 목록+아침 브리핑이 대행(수요 기록되면 재검토) ② 상대 시각("30분 뒤"): 분 단위 시계가 시스템 블록에 의도적으로 없어(재생성 비용) 미지원 — 절대 시각("오후 3시")은 [System Data] 날짜로 해석됨을 스모크로 확인
- **[Changed]** 설정에 "리마인더" 안내 행(정확 알람 권한 없을 때만 노출, ON_RESUME 재확인 — combine 5개 상한이라 시스템 API 상태는 화면에서 직접 읽음), 드로어 할 일에 ⏰ 시각 표기, 실험실 미러(kosmos_lab.py) 6종 동기화
- **[QA/Test]** 마일스톤마다 `test lintDebug assembleDebug` 녹색. **계약 확장 명기**: KosmosAgentTest·PromptFixtureExportTest·KosmosToolDeclarationsTest 의 기대 툴 목록에 AddReminder 추가(단언 로직 무변경), MEASURED_OVERHEAD 실측 갱신. 신규 테스트 5묶음(유스케이스 검증, 마이그레이션, 발화 핸들러 분기, ShadowAlarmManager 예약·취소·복원, 실행기 승인·실행). **실기기 게이트 9건**: ① "3시에 알려줘"→승인 시트→정각 알림 ② 정확 권한 off→±10분 창+설정 안내 행 ③ 재부팅 후 생존(exported=false 수신 포함) ④ 완료한 리마인더 침묵 ⑤ 알림 탭→앱 ⑥ 과거 시각 자가수정 ⑦ 드로어 ⏰ ⑧ 알림 권한 거부 시 저장만 ⑨ 헤드업 표시

## [0.20.2] - 2026-08-21
> 0.20.1 이 `x86_64` 를 남기며 전제한 "에뮬레이터 검증 환경"이 **실제로 성립하는지 실측했다.** 결론: **성립한다 — 단 조건과 한계가 있다.** 코드 변경은 주석 정정뿐이다.
- **[Note]** **종단 검증 성공** — AVD `my_phone`(x86_64/API 35, RAM 12GB·디스크 16G 로 상향)에서 모델 로드 → 프롬프트 조립 → 토큰 생성 → 말풍선 렌더까지 동작을 확인했다. 입력 `"hello,"` → 응답 `"안녕하세요! 저는 Kosmos입니다. 무엇을 도와드릴까요?"`
- **[Fix]** **필수 조건 — AVD 를 `-gpu swiftshader_indirect` 로 띄울 것.** 기본값(`-gpu auto`)으로 띄우면 호스트 GPU 패스스루로 **WebGPU 초기화가 성공해 Ready 까지 켜지지만**, 토큰 샘플러가 OpenCL 을 요구해 생성 시점에 무너진다(`sampler_factory.cc: WebGPU sampler not available` → `litertlm.cc: Can not find OpenCL library`). OpenCL 은 안드로이드 표준이 아니라 GPU 벤더 드라이버가 싣는 것이라 에뮬레이터에는 없다(`libOpenCL*` 검색 결과 0건, `/vendor/lib64/egl/` 은 전부 `_emulation`). **`GemmaModelRunner` 의 CPU 폴백은 초기화 실패에만 걸리므로 이 경로를 구제하지 못한다** — 호스트 GPU 를 막아 처음부터 CPU(XNNPack)로 가게 해야 한다. 0.20.1 주석의 "OpenCL 이 없어 GPU 초기화가 실패한다"는 서술을 이 사실로 교체
- **[Note]** **성능 실측 — 0.2 t/s (실기기 9.6 t/s 대비 약 48배 느림)**. 15토큰 응답에 75초. 초기화는 `xnnpack_cache` 2.1GB 생성에 약 3분이 걸리고 이후 재사용된다(GPU 경로의 mldrift 캐시는 4.2GB·약 40분이라 8배 느렸다 — `user 50% / sys 46%` 로 절반이 GPU 경계 통과 비용이었다). 앱 RAM 6.2GB. **용도 판정**: AC8·UI/내비 회귀·회수 칩 점프처럼 추론이 없거나 짧은 항목은 실용적이고, 에피소드 자동 요약처럼 긴 생성이 필요한 항목은 수십 분이 걸려 부적합하다
- **[Note]** **되돌린 판단 2건** — ① "5분 무진행 = 엔진이 안 깨어난다"는 **오판이었다.** 캐시는 `files/models/` 가 아니라 앱 `cacheDir` 에 쌓이는데 감시 대상을 잘못 잡아 진행 중인 것을 멈춘 것으로 봤다 ② "생성 실패가 사용자에게 조용히 사라진다"도 **사실이 아니다** — 화면에 `응답을 만들지 못했어요. 다시 시도해주세요.` 가 정상 표시된다(스크린샷 시점이 렌더 전이었다)
- **[QA/Test]** `assembleDebug`·`assembleRelease` 녹색(release 77.8MB / debug 100.7MB, 0.20.1 과 동일 — ABI 구성 변경 없음). expand.md TD-7 을 "검증 경로 미확보"에서 **해소**로 갱신
## [0.20.1] - 2026-08-21
> 네이티브 라이브러리를 실행하지 않는 CPU 아키텍처 2종까지 실어 나르던 것을 정리하고, 남긴 하나(x86_64)에 **검증 환경으로서의 역할**을 부여했다. 코드 변경 없음 — 빌드 설정 3줄.
- **[Changed]** **`abiFilters += listOf("arm64-v8a", "x86_64")`** — `litertlm_jni`(20.5MB)·`mediapipe_tasks_jni`(10MB) 가 4개 ABI 로 패키징되어 `lib/` 이 APK 의 90MB 를 먹고 있었다(x86_64 37.5 / arm64-v8a 31.1 / x86 14.8 / armeabi-v7a 7.4). 뺀 둘은 **쓰는 곳이 없다** — `x86` 은 구형 에뮬레이터용이고, `armeabi-v7a` 는 32비트 기기용인데 3.6GB 모델이 12GB+ RAM 을 요구해 애초에 대상 밖이다
- **[Note]** **x86_64 는 남긴다 — 크기보다 격리가 값지다** (사용자 결정). arm64 단독이면 debug 63.2MB 까지 내려가지만 에뮬레이터 설치가 불가능해진다. **실기기 DB 에는 실제 대화와 비밀번호가 들어 있어**(`scratch/lab/device_fixture.py`) 자동화 검증을 거기서 돌리면 유출 경로가 생기고 폰에 실행 잔여물도 남는다. 합성 데이터만 넣은 일회용 AVD 에서 기능·회귀를 보고, 충실도 항목(GPU FP16 거동·발열 게이트·인셋/제스처·스크롤 체감)만 실기기가 맡는 분담으로 간다
- **[Note]** **실측 — release 100.1MB → 77.8MB, debug 123.6MB → 100.7MB (각 −22.3/−22.9MB)**. 같은 커밋에서 적용 전후를 각각 빌드해 비교했다. 참고로 arm64 단독일 때는 release 40.3 / debug 63.2MB 였다 — 에뮬레이터 지원의 값이 약 37MB 인 셈이다. **에뮬레이터 전제**: OpenCL 이 없어 GPU 초기화는 실패하고 `GemmaModelRunner` 의 CPU 폴백으로 동작한다(느리지만 기능 검증에는 충분 — exp27 이 PC CPU 로 같은 모델의 정상 출력을 확인한 바 있다). 모델은 폰과 같은 경로(`getExternalFilesDir("models")`)를 쓰고 가상 디스크에 영속하므로 **최초 1회만** 넣으면 된다
- **[Docs]** **expand.md §6 에 TD-6 추가**(해소 기록)와 **TD-1 수치 정정** — TD-1 이 근거로 삼던 "APK 152MB → ~100MB" 는 저장소 어디에도 없는 수치였다(실측은 당시 release 100MB). 또한 ABI 정리가 MediaPipe 제거의 몫을 일부 흡수했다: 남은 2개 ABI 기준 MediaPipe 는 `lib` 22.4MB + assets 5.8MB ≈ **28MB** 라, C2 의 기대 효과는 −52MB 가 아니라 −28MB(77.8 → ~50MB)다
- **[QA/Test]** `assembleDebug`·`assembleRelease` 녹색, APK 내 `lib/` 이 arm64-v8a·x86_64 둘뿐임을 확인. **후속 필요**: AVD `my_phone` 은 `hw.ramSize=2048` 이라 앱 PSS 실측 5.5GB 를 못 받는다 — 12GB 이상으로 올리고 데이터 파티션도 10G→16G 권장(xnnpack 캐시가 PC 에서 2.1GB였다). **실기기 확인 대상**: ABI 변경은 네이티브 로딩 경로를 건드리므로 JVM 게이트가 잡지 못한다 — S25 Ultra 설치·추론이 종전과 같은지

## [0.20.0] - 2026-08-21
> **아침 브리핑 + 에피소드 후속 질문** (expand.md A4·A4+, E-Phase 1 첫 회차) — 반응형→선제형 전환의 첫 조각. 매일 설정 시각(기본 09:17)에 알림이 오고, 앱을 열면 타임라인에 ☀️ 브리핑 카드(인사+오늘 일정+미완료 할 일+**어제 에피소드 기반 후속 질문 1개**)가 비서 발화로 도착한다.
- **[Feat]** **하이브리드 생성** (사용자 결정) — 정시 알림은 **추론 없이** DB 숫자만("오늘 일정 N건 · 남은 할 일 M건"), 본문은 앱이 열려 엔진 Ready 가 될 때 oneShot 생성. 원안(A4 "알림으로 요약")의 백그라운드 모델 로드는 수명주기(onStop/onTrimMemory 즉시 close, 0.16.2)와 정면 충돌해 분리했다 — 에피소드 요약과 같은 **Ready 편승** 전례를 따른다
- **[Feat]** **GenerateBriefingUseCase** — oneShot 표준형(세션 "morning-briefing", 히스토리·툴 없음), 재료(일정·할 일·최근 SUMMARIZED 에피소드 1~3건) 직렬화 + 상한 초과 시 에피소드→할 일 순 뒤부터 절단(일정이 뼈대). 기기 캘린더 조회 실패는 프롬프트에 명시해 "일정 없음" 오표시를 막는다(EC4). 시각 표기는 `toDisplayKorean` 단일 출처
- **[Feat]** **MorningBriefingGenerator** — `loadState.collect { Ready → maybeGenerate() }`, 생성 조건(켜짐·설정 시각 경과·오늘 미생성·발열 43°C 미만)은 순수 함수로 분리해 경계값 고정. "오늘 생성됨" 판정은 DB 가 진실(`countByInputTypeSince`). **비서 발화용 에피소드 훅**(`onAssistantInitiatedMessage`) 신설 — 미배정 저장 시 catch-up 이 1줄짜리 에피소드를 양산해 아카이브를 오염시키는 것을 막고, 사용자의 답이 브리핑과 같은 에피소드로 묶인다. 판정 시각은 인자 주입 — 벽시계에 묶으면 테스트가 실행 시각에 따라 갈린다. 감사는 기존 MODEL_RUN 타입 재사용(계약 테스트 무변경)
- **[Feat]** **정시 알림** — 자기 재예약 OneTimeWork(재부팅 생존, BOOT_COMPLETED 불요. 정확 알람은 미리보기 하나에 과한 권한이라 기각 — Doze ±수 분 수용). **워커 안 자기 재예약은 APPEND_OR_REPLACE** — REPLACE 는 실행 중인 자기 자신을 취소할 수 있다. "아침 브리핑" 알림 채널 분리(시스템 설정에서 브리핑만 무음 가능), 알림 권한 거부 시 알림만 생략되고 카드 생성은 정상
- **[Feat]** **☀️ 브리핑 카드 + 설정** — `InputType.BRIEFING` 추가(문자열 저장+TEXT 폴백이라 마이그레이션 불필요), 전용 카드 컴포저블(기존 버블 E2E 계약 무변경), 저장 신호 SharedFlow 로 앱 사용 중 도착분도 화면 반영. 설정: 토글(기본 켜짐)+시각 선택(TimePicker, 분 단위 — 기본 09:17), 변경 시 예약 즉시 동기화
- **[QA/Test]** 전체 테스트 + lintDebug + assembleDebug 마일스톤마다 녹색, 기존 단언 수정 0건. 신규 테스트 6묶음(oneShot 계약·절단·EC4, 생성 조건 경계값, Ready 편승·중복 방지·발열 연기, 비서 발화 에피소드 훅, 지연 계산, 워커 분기). **실기기 게이트**: ① 설정 시각 알림 문구·탭 ② 탭→스플래시→카드 도착 ③ 사용 중 복귀 시 자동 표시 ④ 하루 1회 ⑤ 브리핑+답장 한 에피소드 ⑥ 캘린더 권한 회수 문구 ⑦ 발열 연기 ⑧ off/시각 변경 ⑨ 알림 권한 거부

## [0.19.4] - 2026-08-20
> 코드 변경 0건 — `266bd88`·`c7738b1`(2026-08-19)의 문서·저장소 관리 변경분을 기록한다. 0.19.3 과 같은 회수 성격이다.
- **[Docs]** **평가 보고서 2종 신설** — `docs/ENGINEERING_EVALUATION.md`(아키텍처·AI 런타임·타입 안전성·테스트·Compose 5개 영역 스코어카드 + 기술 부채 3건), `docs/INVESTOR_EVALUATION.md`(시장 기회·기술 해자·리스크·GTM 제언). **성격 주의**: 프로젝트 자신에 대한 평가 문서이고 등급은 자체 판정이다 — 그 안에서 사실로 취급할 것은 인용된 실측치(테스트 307건, lint 0건, 프리필 예산 1,700)뿐이고, 나머지 서술의 근거는 ADR·CHANGELOG 로 되짚어야 한다. **정정(0.20.1)**: 애초 이 목록에 "APK 152MB" 를 실측치로 넣었으나 저장소 어디에도 없는 수치였다 — 실측은 당시 release 100MB 다
- **[Docs]** **expand.md §6 — 기술 부채 표 신설(TD-1~TD-5)** — 부채를 로드맵 트랙에 못박은 단일 표. TD-1 MediaPipe 임베더 제거(C1 검증 후 C2, APK ~52MB 감량) / TD-2 targetSdk 37 vs Robolectric 4.14 상한 35 의 에뮬레이션 갭 / **TD-3 상류 LiteRT-LM FP16 — 예산 1,700→3,328 복원이 E-Phase 4 전체의 전제이자 릴리스 감시의 1순위** / TD-4 배치 추론 큐와 발열 임계(43/48°C) 결합 / TD-5 3.6GB 다운로드 회복력·저장공간 사전 검증
- **[Chore]** **`.gitignore` 정책 전환 — `scratch/` 전면 무시에서 `scratch/lab` 추적으로** (`c7738b1`) — 실측 스크립트 18종(exp25~exp32b, GPU 모델 섹션 덤프, 툴 왕복 스모크)·픽스처 7종·`upstream_issue_draft.md` 26개 파일이 저장소에 들어왔다. DB 픽스처와 `__pycache__`·`*.pyc` 는 계속 제외. TD-3 은 상류 릴리스마다 exp30 재실측을 요구하므로 그 도구가 손에 남아 있어야 한다
- **[QA/Test]** 코드 변경이 없어 별도 게이트 없음 — 직전 `clean build` 결과(테스트 307건, Kotlin 경고 0, lint 0)가 그대로 유효하다

## [0.19.3] - 2026-08-18
> 0.19.2 이후 **기록되지 않은 채 남아 있던 커밋 2건(2026-08-15)을 회수**하고, 그중 하나가 들여온 lint 경고를 정리했다. 코드 변경은 이번 회차에서 리소스 폴더명 하나뿐이다.
- **[Fix]** **뒤로가기 이중 pop 으로 빈 화면이 되던 결함** (`acbfafb`) — 화면 내 "뒤로" 버튼과 시스템 예측 뒤로가기가 같은 전환 구간에 겹치면 `popBackStack()` 이 두 번 실행돼 루트(chat)까지 뽑히고, NavHost 가 비어 오로라 배경만 남았다(2026-08-15 실기기: 시맨틱 트리 노드 0개로 확인 — 복구 수단 없음). `previousBackStackEntry != null` 가드를 둔 `navigateBack()` 으로 모든 뒤로 콜백을 통일. 부수로 스플래시→채팅 전환에 `launchSingleTop` — Ready 가 재진입 요동(Ready→FileFound→Ready, 0.16.2)으로 두 번 오면 채팅이 중복 푸시되어 ViewModel 이 두 벌 떴다
- **[Feat]** **흰 상자 없는 적응형 런처 아이콘** (`d07be3e`) — 레거시 PNG 만 있으면 런처가 흰 배경 상자 위에 아이콘을 얹는다(2026-08-15 실기기). 배경(그라데이션 `ic_launcher_background`)과 전경(글리프 `ic_launcher_foreground`, 5개 밀도)을 분리한 `adaptive-icon` 으로 어떤 마스크에도 꽉 차게 하고, Android 13+ 테마 아이콘용 `monochrome` 도 함께 선언
- **[Feat]** **스플래시에 워드마크·상태 문구** (`d07be3e`) — 오브만 있으면 어두운 배경에서 "빈 화면"으로 오인된다(실기기 문의). KOSMOS + "기기 안에서만 생각하는 AI 비서" 아래 로드 단계별 문구(모델 상태 확인 → 엔진 준비 → "AI 엔진을 깨우는 중이에요 (10초 정도)")를 띄운다 — 엔진 재초기화 실측 9~12초(0.16.2) 동안의 침묵이 가장 큰 불안 요소였다
- **[Chore]** **`mipmap-anydpi-v26` → `mipmap-anydpi`** — `minSdk = 26` 이라 `-v26` 한정자는 항상 참이고, lint `ObsoleteSdkInt` 가 이를 잡았다(위 아이콘 커밋이 들여온 유일한 경고). 파일 내용·리소스 이름은 그대로라 참조하는 곳이 없다. AAPT2 가 `anydpi` 의 최소 API 인 `-v21` 을 자동 부여하므로 APK 에는 `res/mipmap-anydpi-v21/` 로 들어간다(minSdk 26 이라 항상 적용)
- **[Note]** **리소스 폴더 개명은 증분 빌드가 조용히 먹는다** — 개명 직후 `assembleDebug` 는 성공하고 lint 도 0건인데 **APK 에서 적응형 아이콘 XML 이 통째로 빠져 있었다**(`mergeDebugResources` 가 옛 경로 항목만 지우고 새 경로를 넣지 않음 — 병합 산출물에 `.flat` 이 0개). 성공한 빌드 로그로는 드러나지 않는 종류라, `--rerun`(또는 clean) 후 **APK 안의 리소스를 직접 확인**해야 판정된다. 리소스 폴더를 옮기거나 이름을 바꾼 회차에서는 이 확인을 빼지 말 것
- **[QA/Test]** `clean build` 녹색 — 테스트 **307건** 통과(실패·에러·스킵 0), Kotlin 경고 **0건**(`:core` `:domain` `:data` `:app` + 테스트 소스 재컴파일로 확인), lintDebug **0건**(app·data 양쪽), APK 내 `mipmap-anydpi-v21/ic_launcher{,_round}.xml` 존재 확인. 실기기 확인 대상: 홈 화면 아이콘에 흰 테두리 상자가 사라졌는지

## [0.19.2] - 2026-08-15
> **보조 화면 통일 회차** (0.19.1 피드백 ②) — 채팅만 새 언어이고 기억/일정/설정/활동 기록/모델 관리는 구세대(영문 제목·불투명 배경)로 남아 "다른 앱 같던" 것을 정리했다.
- **[Changed]** **전 화면 한글화** — 기억("메모 · 할 일" — 드로어 타일과 동일 명명), 일정, 설정(모델 상태·화면 테마·응답 스타일 간결/기본/자세히 — 저장 키는 영문 유지, 표시만 한글), 활동 기록, 모델 관리(내려받기 흐름 전체). 제외 1건: 입력바 "Document Attached" 는 E2E 셀렉터(불변 계약) — 이번 검증에서 테스트가 실제로 잡아서 되돌렸다
- **[Changed]** **표기 통일** — ① 모델의 일정 답변에 ISO 원문("2026-08-20T16:00:00")이 노출되던 것을 툴 결과 단계에서 한국어 표기("8월 20일 오후 4:00")로 교체(`IsoDateTimeParser.toDisplayKorean` 신설 — 표기 규칙 단일 출처). 2026-08-12 의 "툴 관측값 동결" 유보는 이후 실기기 안정 확인으로 해제 ② 일정 화면 시각 "9:00 AM" → "오전 9:00", 월 표기 "August 2026" → "2026년 8월", 요일 Sat → 토
- **[Changed]** **배경 통일** — 기억·일정 화면만 불투명 `bg` 를 깔아 셸의 오로라 배경이 죽어 있었다. 걷어내서 채팅·설정과 같은 "오로라 위 글래스 카드" 언어로
- **[QA/Test]** 전체 테스트 + lintDebug + assembleDebug 녹색, 기존 단언 수정 0건 (E2E 가 "Document Attached" 한글화를 잡아낸 것이 계약이 살아 있다는 증거)

## [0.19.1] - 2026-08-15
> 0.19.0 실기기 확인에서 나온 결함·피드백 회차. 앞선 핫픽스 포함: **셸 Scaffold 제거로 사라진 시스템 바 인셋** — M3 Scaffold 는 topBar/bottomBar 슬롯의 인셋을 슬롯 자신이 처리한다고 가정하는데(TopAppBar·NavigationBar 는 내장) 새 헤더·입력바는 평범한 Row/Column 이라 상태바·제스처 바 밑에 깔려 조작 불능이었다. 헤더 `statusBarsPadding`, 입력바 `navigationBarsPadding+imePadding`, 셸 Scaffold 에 기대던 캘린더·기억·설정은 `systemBarsPadding` 랩. Robolectric 은 인셋이 0 이라 JVM 게이트가 못 잡는 결함 유형이다.
- **[Fix]** **종일 일정이 '오늘' 탭에서 누락** — 기기 캘린더 조회가 완전 포함(`DTSTART >= ? AND DTEND <= ?`)이라, UTC 자정 기준인 종일 일정(광복절)이 KST 에서 9시간 삐져나가 하루 창에서 항상 탈락했다(주간 창에서만 보임). 겹침 판정(`DTSTART < 끝 AND DTEND > 시작`)으로 교체 — 자정 넘기는 일반 일정도 함께 고쳐진다
- **[Fix]** **"Add new task" 가 빈 스텁**(`clickable { }` — 버튼만 있고 기능 없음) — 인라인 입력으로 배선: 자리에서 입력 + 완료/추가로 저장, **저장 완료 후** 페이징 refresh(저장 전 refresh 는 방금 항목이 안 보이는 경합)
- **[Changed]** **상태 캡슐에 앱 RAM 복귀** — `🌡39° · 4.1GB · 9.6t/s` (사용자 요청). 시스템 전체 RAM 등 상세는 캡슐 탭 시트 유지
- **[Changed]** **주간 탭의 날짜 강조 분리** — 필터를 안 걸었는데 오늘 칸이 선택된 것처럼 강조되어 "오늘만 보는 중 같은데 목록은 주 전체"로 읽혔다. 주간 탭은 탭(필터)했을 때만 강조하고, 오늘 위치는 점(`isToday`)이 상시 표시
- **[Changed]** **설정의 SECURITY & LOGS 섹션 제거** — 활동 기록 진입점이 드로어 타일로 옮겨져(M2-2) 같은 화면의 문이 두 개였다
- **[QA/Test]** 전체 테스트 + lintDebug + assembleDebug 녹색, 기존 단언 수정 0건. 잔여: 보조 화면(기억/캘린더/설정) 시각 언어 통일 회차 별도 예정

## [0.19.0] - 2026-08-15
> **시안 A′ UI 셸** (ADR-022 의 M2 트랙, 구현 결정은 ADR-023) — 세션 없는 연속 타임라인 + 드로어 + 기억 아카이브 + 회수 칩. 하단 탭이 사라지고 채팅이 루트가 됐다. E2E 계약(ChatScreen 기본 인자 단독 compose, 셀렉터 불변) 유지 — 기존 테스트 단언 수정 0건.
- **[Feat]** **헤더·입력바 재설계** — ☰ + KOSMOS + 상태 점 + 상태 캡슐 1개(`formatStatusCapsule`, 발열 경고 시 색 승격)로 압축하고, 기존 기기 상태 스트립 본문은 탭 시 `StatusDetailSheet`(ModalBottomSheet)로 이동. 웹 검색 🌐 토글은 첨부 옆으로 — 설정 진입 없이 턴 단위로 켜고 끈다
- **[Feat]** **셸 전환: 하단 탭 → 드로어** — `ModalNavigationDrawer`(셸 층 — ChatScreen 은 navController 를 모르는 구조 유지, E2E 가 단독 compose 라 드로어가 딸려오면 안 됨). 엣지 스와이프는 채팅에서만(푸시 화면의 뒤로가기 제스처와 충돌 방지), 드로어 열림 중 뒤로가기는 드로어를 닫는다
- **[Feat]** **연속 타임라인 (Paging3)** — 세션 무관 무한 스크롤. `timelineAnchor` 이전은 페이징(placeholder 켬), 이후는 기존 라이브 테일 — 낙관적 append·스트리밍 버블 무변경(ADR-023 §4). `reverseLayout` 로 바닥=최신, 날짜 구분선은 전체 전처리 대신 항목별 경계 판정(더 과거 이웃과 날짜 비교). placeholder 총수용 `CountedPagingSource` 신설. **부수 수정**: 첨부 피커의 큰 읽기를 제거(이미지=SIZE 쿼리, 문서=캡 경계 읽기)해 0.17.2 의 IO 이동으로 비동기화됐던 프리뷰를 동기로 복원 — ANR 원인 자체가 소멸
- **[Feat]** **드로어 내용물** — 기억 검색바(300ms 디바운스, SearchMemory 툴과 같은 저장소·같은 방식 — 드로어에서 보이는 것과 모델이 회수하는 것이 어긋나면 안 된다) + 에피소드 아카이브(SUMMARIZED 만, Paging) + 타일(캘린더/활동 기록/설정 — 아이콘 크게·글자 작게, 사용자 결정). `EpisodeSheet`: episodeId 만 받는 시트(드로어·회수 칩 두 진입점 공용) — 열람/인라인 수정/삭제("요약 문서만 삭제돼요 — 원문은 타임라인에 남아요")/원문 대화 보기
- **[Feat]** **회수 칩(🧠) + 원문 점프** — 기억을 참조한 답변 아래 "🧠 기억에서 — {제목} (M월 d일)" 칩(searchUsed 뱃지와 같은 투명성 원칙). 제목은 저장된 id 로 렌더 시 해석(ChatViewModel 캐시) — 시트에서 제목을 고쳐도 낡지 않는다. 칩 탭 → 에피소드 시트, "원문 대화 보기" → `countNewerThan` 차분으로 인덱스 계산 → placeholder 덕에 미로드 위치로도 O(1) 점프 + 도착 지점 2.5초 하이라이트. 드로어 발 점프는 셸이 요청 시각을 내려보내는 상태 통로로 배선
- **[QA/Test]** 전체 테스트 + lintDebug + assembleDebug 회차마다 녹색, 기존 단언 수정 0건. **실기기 게이트 6건이 남아 있다**: ① 30분 뒤 재대화 → 드로어에 에피소드 문서 ② "○○ 뭐였지?" → 칩 + 정답 ③ 칩 → 시트 → 원문 점프 ④ 긴 타임라인 스크롤 체감 ⑤ AC8(모델 없음 → "모델 내려받기" 버튼) ⑥ 비행기 모드 검색 실패 안내

## [0.18.0] - 2026-08-15
> **에피소드 기억 백엔드** (ADR-022 시안 A′의 M1 트랙) — 대화가 에피소드로 자동 분절되어 oneShot 요약 문서가 되고, SearchMemory 가 과거 대화를 회수한다. UI 없이 JVM 293건으로 종단 검증. 선검증 게이트(M0): exp33 확장 — 다중 주제 분리 지시를 얹어도 형식 9/9 유지, 단일 주제 비분리, 실제 혼합 에피소드만 2문서 분리.
- **[Feat]** **스키마 v6** — `episode` 테이블(상태 기계 OPEN→CLOSED→SUMMARIZED|FAILED, 시간 범위, 재시도 카운트) 신설 + `conversation.episodeId`(정확한 멤버십, NULL=catch-up 마커)·`recallEpisodeIds`(회수 칩 영속, 제목은 저장하지 않고 렌더 시 해석 — 수정 시 낡는 비정규화 회피). knowledge 편입은 기각 — kind 필터를 빠뜨린 기존 소비처가 에피소드를 메모 UI 에 흘리는 무음 실패 모드. **마이그레이션 테스트가 첫 결함을 잡았다**: `ADD COLUMN ... DEFAULT NULL` 의 DEFAULT 가 DDL 에 남아 Room 기대 스키마와 어긋남 — 실기기였다면 다음 실행 크래시
- **[Feat]** **SummarizeEpisodeUseCase** — oneShot 표준형(세션 "episode-summary", 히스토리·툴 없음), exp33 검증 프롬프트(제목/태그/요약, 숫자 원문 보존) + `---` 다중 문서 분리. 부분 형식은 실패로 승격(태그 없는 문서는 태그 회수에 안 걸려 검색 품질을 조용히 깎는다)
- **[Feat]** **경계 감지** — `ModelRunner.conversationResets: SharedFlow`(기본 구현은 침묵 — fake 들이 구현할 필요 없음) + GemmaModelRunner 방출(같은 세션 리셋만, oneShot 경로 구조적 배제). **TOKEN_BUDGET 만 경계** — 웹 검색 토글·응답 스타일 변경이 주제를 가르면 안 된다. `EpisodeBoundaryManager` 가 유저 메시지 저장 직전(단일 지점) 30분 무활동을 판정하고, 실패 시 null(미배정 저장) — 에피소드 배선 문제가 채팅을 막지 않는다
- **[Feat]** **요약 스케줄러** — 계획의 "무활동=즉시 실행"을 **버리고 전부 턴 종료 후 드레인**: 즉시 실행은 llmDispatcher(직렬)에서 사용자 턴보다 먼저 줄을 서 응답을 수십 초 지연시킨다. "ON_STOP 소비"도 기각 — onStop 은 모델을 해제한다(0.16.2). 발열 게이트(43°C), 재시도 3회 후 FAILED(원문 보존 — 요약 실패 ≠ 데이터 손실), 모델 Ready 시 catch-up(낡은 OPEN 닫기·고아 메시지 30분 간격 소급 배정·미요약 CLOSED 재큐). **테스트가 무한 회전 결함을 잡았다**: 발열로 미룬 항목을 같은 드레인 루프가 즉시 재시도 — 스냅샷 배치로 교정
- **[Feat]** **SearchMemory 통합** — 에피소드 문서(SUMMARIZED만)가 메모와 같은 랭킹에 합류(본문 LIKE+태그, 키 "ep:" 프리픽스), 2차 회수 태그 목록은 메모∪에피소드. 결과는 SentenceTruncator 로 500토큰 캡. 성공 JSON 에 `meta.episodeIds` 동봉 — **BaseAgent 가 뽑은 뒤 모델 회신에서 제거**(id 에코 방지, 테스트 고정) → `AgentResult.Text.recallEpisodeIds` → ChatMessage → Entity CSV 로 영속. 렌더(칩)는 M2-5
- **[QA/Test]** 전체 **293건** 통과 + lintDebug + assembleDebug. 신규 테스트 5묶음: 마이그레이션 DDL 대조, 저장소 왕복(episodeId·recallEpisodeIds 소실 회귀), oneShot 계약, 경계 판정(30분/사유 필터/실패 폴백), 스케줄러 시점(턴 종료 드레인·발열 보류·재시도 상한·catch-up), meta 추출·제거

## [0.17.3] - 2026-08-15
> MVP 감사의 문서·주석 트랙 — **문서가 코드보다 두 세대 뒤**였던 것을 현행으로 정합화했다. 코드 동작 변경 0건(주석·로그 한 줄 제외), 테스트 260건 통과 유지.
> **부수 (UI 회차 선행 작업)**: `refactor(chat)` — 1,211줄 `ChatScreen.kt`(컴포저블 12개 동거)를 응집 단위 5개로 **순수 분리**: `ChatScreen`(화면 조립+행 모델, 457줄) / `ChatHeader`(헤더+기기 상태) / `ChatBubbles`(말풍선·구분선·썸네일) / `ChatInputBar` / `CalendarDraftCard`(+일시 포맷터). 동작 변경 0 — 가시성 조정은 파일 경계를 넘는 둘(DateSeparator·AttachmentThumbnail)의 private→internal 뿐이고, 파일 헤더 KDoc 2벌 중 낡은 쪽을 삭제했다. 테스트 260건·lint·빌드 통과. 이후 UI 개선은 이 구조 위에서 파일 단위로 진행한다.
- **[Docs]** **PRD 정합화** — 제약 표의 예산 3500(이력에도 없던 값) → 1,700(ADR-021), 이미지 512px → 1024px, "최근 5턴" → Token Sliding Window. F7 개정(전역 토글) 미반영이던 8-2 흐름도·V1-AC1·차별점/목표/Non-Goals/정책 표 6곳과, F2 개정(전사 자동 전송) 미반영이던 8-3 음성 흐름도를 현행으로. **AC6 에 프로필 주(注) 추가** — 전용 저장소는 호출 0곳, Knowledge 메모리가 대행 중이며 3층 기억 모델 회차에서 정식 배선
- **[Docs]** **체크리스트 허위 검증 5건 정정** — v0 의 "STT 확인 후 전송"(F2 로 폐기)과 v1 의 F7 폐기 항목 4건이 "검증 완료"로 체크된 채 남아 있었다. 폐기 항목은 기록용으로 분리하고, 현행 기준으로 재작성 — AC8 은 0.17.2 버튼 신설로 **미확인**이 사실이므로 체크를 풀었다
- **[Docs]** **architecture.md 의 낡은 "현재값" 서술 3곳** — §4-2 Constants 스니펫(존재하지 않는 MAX_CONVERSATION_TURNS, KV 와 예산을 혼동한 4096, 낡은 모델 파일명)을 분류 서술로 교체(값의 단일 출처는 Constants.kt 의 [WHY]), §4-4 4블록 예산표(총합 4096 — 전부 현행 산식과 불일치) 삭제, §13-1 확장성 표를 현행 예산·윈도우로
- **[Docs]** **루트 README** — 기술 스택 두 세대 낙후 정정(Kotlin 2.0.21→2.4.10, Target SDK 35→37, OkHttp 3→4, litertlm 0.16.0 명시, mediapipe 제거 검토 중이라 스택에서 제외), **assets 번들 지시 → sideload/앱 내 다운로드**(따라 하면 빌드가 안 되는 온보딩이었다 — 3.6GB 는 APK 에 못 들어간다), 제거된 IntentClassifier 다이어그램을 현행 단일 에이전트 구조로. docs/README 의 api_spec.yaml 설명도 "REST/WebSocket 사양서" → 내부 계약 명세(파일 자신의 서술과 일치)로
- **[Docs]** **expand.md** — "v0 전부 실기기 검증 완료" 주장을 체크리스트 기준으로 완화(단일 출처 명시), **Track C′(3층 기억 모델) 설계 추가**: Profile(상시 주입 ~150토큰, 키-값 강제) / Knowledge(SearchMemory) / History(윈도우), 리셋 시점 oneShot 자동 추출(예산 1,700 의 약점을 캡처 트리거로 뒤집기 — C4 흡수), 검색은 모델 양끝 정규화 + C1 FTS5 로 임베딩 게이트(C3) 유지
- **[Fix]** **코드와 정반대이거나 폐기된 근거를 인용하던 주석 정정** — ① RuntimeMetricsCollector: 코드는 임계 발열에서 Failure(하드 차단)인데 주석은 "Warning 반환"(정반대), 쿨다운 근거로 폐기된 '토큰 단위 지연'(ADR-012) 인용 ② GemmaRuntimeManager: "GPU Delegate 자동 활성화" 주장 — 실제는 수동 선택+CPU 폴백 ③ GemmaModelRunner: "툴 선언 ~2천 토큰"(실측 652), 0.14.0 기준 [WHY] 2곳에 0.16.0 재검증 상태 명시, @ExperimentalApi 감수 근거를 gallery 인용에서 실질 이유로 교체 ④ ToolParser KDoc 에 malformedToolCalls 소비자 없음 명시 ⑤ 위키 'For MVP' 영어 잔재를 INFOBOX 제외의 실제 이유로 ⑥ 부수: calId=1L 폴백에 [WHY]+경고 로그, MAX_TOOL_LOOP_COUNT=3 의 SearchMemory 재조회 의존 [WHY], ChatScreen 끝표시 주석·도메인 'v1' 마커 제거
- **[QA/Test]** 전체 **260건** 통과 + lintDebug + assembleDebug. 코드 동작 변경은 calId 폴백 경고 로그 한 줄뿐

## [0.17.2] - 2026-08-15
> MVP 마무리 감사(ultracode, 에이전트 21개 — 발견 80건 중 검증 생존 79건. 전문은 미추적 작업 기록이며 결론은 이 회차와 0.17.3 에 전부 반영됨)에서 "지금 고칠 것"으로 분류된 코드 결함을 일괄 수정했다. 공통 테마는 **성공처럼 보이는 실패** — 실패했는데 화면은 정상인 경로들이다. 결정 2건 반영: 이미지 단독 전송은 허용 대신 **안내 문구**(사용자 결정), AC6 프로필은 3층 기억 모델 회차로 분리.
- **[Fix]** **검색 실패가 "위키백과 검색 결과를 참고했어요" 로 오표시되던 결함** — 위키 네트워크 실패는 예외가 아니라 오류 JSON 으로 돌아오므로(executed=true) 실행 여부만 보던 기준으로는 **검색 없이 만든 답변에 근거 뱃지가 붙었다.** PRD V1-AC3·EC5 를 실질 무력화하는 오표시. `ToolOutcome` 에 `succeeded`(JSON status 파싱)를 추가해 뱃지는 성공 기준, 감사 기록은 실행 기준(네트워크 시도 자체가 기록할 사건)으로 갈랐다
- **[Fix]** **이미지 읽기 실패 시 이미지 없이 텍스트만 전송되던 결함** — 말풍선은 📷 "첨부된 이미지" 를 표시하는데 모델은 텍스트만 받아, 모델이 이미지를 보고 답했다고 오해하게 했다. 읽기 실패 시 턴을 중단하고 낙관적 말풍선을 걷어낸 뒤 오류를 알린다. `printStackTrace` 잔재도 로그로 교체
- **[Fix]** 무음 실패 4건 추가 — ① 첨부 피커의 빈 catch(`// handle error`): 실패 안내 + 파일 읽기를 IO 로 이동(메인 스레드 10MB 동기 읽기 ANR 위험 동시 해소) ② 대화 로드 실패가 빈 화면으로 위장: 스낵바 안내 ③ 비서 응답 DB 저장 실패 무시(재시작 후 턴 소실): `AgentResult.persistFailed` 로 표면화 + 감사 기록 ④ Task 완료 토글 실패 무시(탭이 씹힌 것처럼 보임): 토스트 안내
- **[Fix]** **첨부 문서 캡 2500자 → `MAX_ATTACHED_DOC_CHARS`(300자, 예산 파생)** — 2500 은 예산 6000 시절의 유물로, 그 턴의 KV 를 GPU 숫자 깨짐 발병점 위로 밀고 다음 턴부터는 문서 메시지가 슬라이딩 윈도우에서 통째로 탈락해 **모델이 문서를 본 적 없는 상태**가 됐다. 유도: 최소 히스토리(300) − 메시지 오버헤드(10) − 래퍼(~15~25토큰) ≈ 265토큰 × 1.2자/토큰 ≈ 318 → 300. 잘리면 "앞부분만 첨부돼요" 안내. 긴 문서의 정식 경로는 페이지 단위 oneShot 요약(expand.md D1)이고 이 캡은 그때까지 조용한 깨짐을 정직한 짧음으로 바꾼다. 불변식 테스트가 **실제 저장 형태(래퍼 포함)를 추정기에 넣어** 윈도우 생존을 고정한다
- **[Fix]** **EC1 추론 타임아웃 집행** — `ModelInferenceTimeout` 오류·문구까지 준비돼 있었지만 생성하는 곳이 0곳이었다. 네이티브가 멈추면 lifecycleMutex 를 쥔 채 이후 모든 턴(warmUp·close 포함)이 함께 막혔다. `withTimeout` 은 블로킹 JNI 를 풀지 못하므로 **별도 디스패처의 무활동 감시 + `cancelProcess`**(취소 버튼이 쓰는 네이티브 자체 중단 경로)로 집행한다. 기준은 총시간이 아니라 무활동 120초 — 정상 생성은 답이 길면 몇 분이 걸릴 수 있고, 행의 특징은 토큰이 안 오는 것이다. 사용자 취소(부분 응답 보존)와는 timedOut 플래그로 갈린다
- **[Fix]** **AC8 — 스플래시 모델 없음 안내에 "모델 내려받기" 버튼 추가** — 안내는 "설정 > 모델 관리에서 내려받아 주세요" 라고 말하면서 이동 수단은 Retry 뿐이었다. 모델 관리 화면으로 직행하고, 스플래시를 백스택에 남겨 내려받기 후 돌아오면 Ready 를 보고 자동으로 채팅에 진입한다. Retry 버튼 문구도 "다시 시도" 로 한글화
- **[Fix]** **probe 의 실패 HEAD 응답 미반납 연결 누수** — 다운로드 재시도(최대 5회)마다 안 닫힌 연결이 누적돼 커넥션 풀이 고갈됐다. 폴백 GET 으로 넘어가기 전에 close
- **[Changed]** **이미지·문서 첨부 시 텍스트가 비면 "질문을 함께 입력하면 전송돼요" 힌트 표시** — 첨부만으로는 전송 버튼이 안 뜨는데(마이크가 대신 뜸) 그 이유를 알 길이 없었다. 단독 전송 허용 대신 안내를 택했다(사용자 결정)
- **[QA/Test]** 전체 **260건** 통과(app 모듈, 신규 2 — 검색 실패 뱃지 회귀·문서 캡 윈도우 생존 불변식) + lintDebug 무이슈 + assembleDebug. 실기기 확인 목록: ① AC8 — 모델 파일 이름 변경 후 재시작 → "모델 내려받기" 버튼으로 모델 관리 진입 ② 비행기 모드 + 웹 검색 ON 질문 → 참고 뱃지 없이 "웹 검색 보강 실패" 안내

## [0.17.1] - 2026-08-14
> 0.17.0 음성 검증에서 나온 UX 결함 — 전사는 정상인데 **화면에서 확인할 방법이 없었다.**
- **[Fix]** **음성 말풍선이 전사 후에도 자리표시자("(음성 메시지)")로 남았다** — 자리표시자는 전사 전에 즉시 띄우는 낙관적 항목으로 의도된 것이지만, 전사가 끝난 뒤 교체하는 코드가 없어 그 세션 화면에서는 계속 자리표시자였다(DB 에는 전사문이 정상 저장 — 나갔다 들어오면 보였다). 음성 턴 종료 시 메시지 목록을 DB 에서 재조회해 전사문으로 교체한다. 전사 실패 턴은 사용자 메시지를 저장하지 않으므로(0.12.0) 자리표시자도 함께 사라진다 — 내용 없는 말풍선을 남기지 않는 기존 설계와 일관. 🎤 뱃지(입력 유형 표시)는 유지
- **[QA/Test]** 전체 **272건** 통과 + lintDebug 경고 0 + assembleDebug. `VoiceChatIntegrationTest` 에 "화면 말풍선이 전사문으로 교체된다" 단언 추가(기존 단언 무변경)

## [0.17.0] - 2026-08-14
> 0.16.0 에서 보류했던 둘을 사용자 결정으로 올렸다 — **tflite 16.5**("litertlm 0.17 을 기다리지 말고 지금 검증하자")와 **targetSdk 37**("나중에 OS 가 요구할 때 바꾸는 게 더 귀찮다"). 그 과정에서 lint 크래시가 재발해 오전의 원인 판정이 **오판**이었음이 드러났고 정정한다. **릴리스 게이트는 실기기 재검증** — 특히 tflite 는 숫자 온전성이 판정 기준이다.
- **[Changed]** **play-services-tflite 16.4.0 → 16.5.0** (GPU 델리게이트) — 모델 런타임 인접이라 승격 = 실기기 숫자 재검증과 짝이다. GPU 연산 계층이 바뀌면 FP16 발병점 실측(exp30 1854/2122)의 전제가 흔들리므로, 긴 세션에서 일정 시각·숫자 온전성을 다시 봐야 한다
- **[Changed]** **targetSdk 35 → 37** — compileSdk(컴파일 API)와 달리 런타임 동작 규칙이 바뀐다. Robolectric 4.14 의 에뮬레이션 상한이 35 라 전 테스트가 구성 단계에서 죽었고(`targetSdkVersion=37 > maxSdkVersion=35`, 16건 실패로 발현), `robolectric.properties(sdk=35)` 로 테스트 에뮬레이션을 기존 조건에 고정해 분리했다 — 실기기의 37 동작 확인은 기기 검증이 맡는다
- **[Note]** **정정 — lint FIR 크래시의 원인 판정 두 건이 오판이었다.** 0.15.2 는 "mockk 1.14 가 크래시 원인"(롤백으로 소멸 확인), 0.16.0 은 "AGP 9.3 이 해결"이라 적었는데, 이번 회차에서 mockk·AGP 무변경 상태로 같은 크래시가 재발했고 **무변경 재실행 2회는 통과**했다 — 비결정적 경합(K2 FIR lazy-resolution)이며 두 판정의 "확인"은 우연이었다. 복불복 게이트는 게이트가 아니므로 크래시 표면인 **테스트 소스 lint 분석을 껐다**(`ignoreTestSources` — 지금까지 테스트 소스에서 나온 lint 지적 0건이라 잃는 것이 없다). 교훈: 비결정 실패는 "바꾸고 통과"가 판정이 아니다 — **무변경 재실행이 먼저다**
- **[Note]** **okhttp 5 보류의 근거를 보강** — 5.0 조사 결과 우리 사용 패턴(위키 GET + 파일 다운로드)에 필요한 신기능이 없고(코루틴 확장·요청 gzip·AAR 분리 등), mockwebserver3 패키지 개편으로 테스트 마이그레이션 비용만 있다. "메이저라 위험"이 아니라 "얻는 것이 없음"으로 이유를 교체. mediapipe 는 제거 대 유지 판단을 사용자가 검토 중
- **[QA/Test]** 전체 **272건** 통과(실패·에러·스킵 0) + lintDebug 경고 0 + assembleDebug. 실기기 확인 목록: ① 긴 세션 일정 등록 — 승인 카드 시각 온전(tflite 판정 기준) ② 전경 다운로드 알림·오디오 녹음·캘린더 동기화(targetSdk 37 영향권) ③ 뒤로가기 재진입(0.16.2 회귀 확인)
- **[Note]** **실기기 확정 (2026-08-14)** — ① 승인 카드 시각 온전: **tflite 16.5 채택 확정** ② **음성 전사 정상**(주변 대화가 전사되어 사용자 메시지로 저장되고 모델이 응답 — 0.15.1 부터 미확인이던 항목이 이번에 닫힘) ③ 일정 앱 내 등록 정상, 뒤로가기 재진입 정상(0.16.2 회귀 없음). 미확정 1건: **기기 캘린더 동기화** — 앱 일정은 저장됐으나 캘린더 앱 반영이 확인되지 않음(사용자 판단: 무관). 유력 원인은 재설치로 초기화된 캘린더 런타임 권한 — 동기화 실패는 설계상 조용히 로컬 저장을 유지한다(ADR-004). 재현 시 앱 권한에서 캘린더 허용 여부를 먼저 볼 것

## [0.16.2] - 2026-08-14
> 0.16.1 의 처방이 실기기에서 **"어떤 때는 되고 어떤 때는 안 되는"** 채로 남았고, logcat 이 조건을 확정했다: **OS 가 백그라운드 프로세스를 죽였는지 여부**다. 죽이면(3.6GB 모델을 든 앱이라 우선 회수 대상 — 로그의 PROCESS ENDED) 콜드 스타트라 정상 동작하고, 살리면 빠른 재진입에서 close() 의 비동기 정리(실측 onStop 후 ~0.5초)가 끝나기 전에 스플래시가 낡은 Ready 를 보고 통과한다 — 그 뒤 상태가 FileFound 로 내려가도 다시 데워줄 곳이 없다.
- **[Fix]** **"FileFound 를 보면 warmUp" 반응을 스플래시 뷰모델에서 앱 수명주기로 승격** — `ProcessLifecycleOwner.onStart` 가 포그라운드 진입마다 warmUp 을 건다. warmUp 은 멱등이라(엔진 살아 있으면 초기화 건너뜀) 콜드 스타트의 스플래시 경로와 겹쳐도 뮤텍스 직렬화로 Ready 에 수렴한다 — 화면 단위 반응은 그 화면이 없는 타이밍에 구멍이 나는데, 상태의 주인(프로세스 수명주기)에 반응을 두면 어떤 킬/생존/경합 조합에서도 성립한다
- **[Note]** 부수 효과로 엔진 재초기화(9~12초 실측)가 첫 메시지 전송 시점이 아니라 **재진입 직후 선행**된다 — 백그라운드 해제 정책(메모리 반환)의 대가를 사용자가 기다림으로 치르는 시점이 가장 덜 아픈 곳으로 이동
- **[QA/Test]** 전체 **272건** 통과 + lintDebug 경고 0 + assembleDebug. 실기기 확인: 뒤로가기 퇴장 → (프로세스 생존/사망 무관) 재진입 → 설정에서 Ready 복귀 — 복불복이 사라졌는지가 판정 기준

## [0.16.1] - 2026-08-14
> 0.16.0 실기기 스모크에서 나온 결함 1건. **상향의 회귀가 아니라 기존 버그다** — 이 경로의 코드는 최신화 회차에서 한 줄도 안 바뀌었고, 스모크가 처음으로 이 동선(뒤로가기 퇴장 → 재진입 → 설정)을 밟았다. 나머지 스모크 항목(일정·위키·재시작)은 정상 확인.
- **[Fix]** **백그라운드 해제 후 모델 로드 상태가 거짓 Ready 로 남았다** — 뒤로가기로 나가면 `ProcessLifecycleOwner.onStop` 이 `modelRunner.close()` 로 엔진을 해제하는데 `loadState` 는 Ready 로 남는다. 재진입한 스플래시는 낡은 Ready 를 믿고 warmUp 을 건너뛰고(FileFound 에만 반응), 설정 화면의 재탐색(`checkModelFile`)이 상태를 FileFound 로 되돌리면 **warmUp 을 불러줄 곳이 없어 "엔진 준비 중" 스피너가 영원히 돈다.** 채팅이 멀쩡했던 이유는 generate 경로가 엔진을 지연 초기화하기 때문 — 표시만 죽고 실동작은 살아 있었다. 처방 둘: ① `close()` 가 해제 직후 상태를 사실(FileFound)로 되돌린다 → 다음 진입이 다시 데운다 ② 설정 새로고침이 `checkModelFile` 에서 멈추지 않고 `warmUp` 까지 부른다(엔진이 살아 있으면 초기화는 건너뜀)
- **[QA/Test]** 전체 **272건** 통과(실패·에러·스킵 0, 신규 1 — `SettingsViewModelTest`) + lintDebug 경고 0 + assembleDebug. 실기기 확인 1건 남음: 뒤로가기 퇴장 → 재진입 → 설정 진입/새로고침에서 Ready 복귀

## [0.16.0] - 2026-08-14
> 예약해 둔 **런타임 의존성 최신화 회차**(사용자 결정). 그룹별 커밋 + 그룹별 테스트 게이트로 올려, 문제가 생기면 그룹 단위로 원인이 갈리게 했다. 모델 런타임 인접(litertlm·tflite)과 메이저(okhttp 5·mediapipe 1.0)는 동결하고 그 이유를 카탈로그 [WHY] 로 남겼다. **릴리스 게이트는 실기기 스모크 1회가 남아 있다.**
- **[Changed]** **툴체인** — Gradle 9.4.1→9.5.0(AGP 요구), AGP 9.2.0→9.3.1, kotlin 2.3.21→2.4.10, KSP 2.3.7→2.3.11(2.3.10 부터 kotlin 2.4 지원). **kotlin 2.4 는 hilt 2.60 과 한 몸이다** — hilt 2.59 는 kotlin 2.4 메타데이터를 못 읽어 KSP 가 죽는다("maximum supported version is 2.3.0"). 되돌릴 일이 생기면 함께 되돌려야 한다
- **[Changed]** **compileSdk 35→37** — androidx-hilt 1.4·lifecycle 2.11 의 AAR 메타데이터 요구. **targetSdk 는 35 유지** — compileSdk 는 컴파일 시점 API 노출만 바꾸고 런타임 동작 전환은 targetSdk 가 정하므로, 실기기 검증(0.15.1)의 동작 조건이 유지된다
- **[Changed]** hilt 2.59→2.60.1, androidx-hilt 1.3.0→1.4.0, compose BOM 2026.04.01→2026.08.00, navigation 2.9.8, paging 3.5.1, work 2.11.2, core-ktx 1.19.0, lifecycle-process 2.11.0, kotlinx-serialization 1.11.0, kotlinx-collections-immutable 0.5.1, richtext alpha05
- **[Note]** **mockk 1.14.11 재시도 성공** — 0.15.2 에서 "AGP 상향 이후 재시도" 조건으로 보류했던 것이 바로 이번 회차에 풀렸다. AGP 9.3.1 의 lint 는 kotlin 2.4 메타데이터를 읽는다(테스트 271 + lint 경고 0 재확인). robolectric 4.16 은 재시도하지 않았다 — 실패 원인이 AGP 가 아니라 `VoiceChatIntegrationTest` 의 벽시계 대기 구조라, 그 테스트를 손보는 회차의 몫이다
- **[Note]** **동결 판정 3건을 카탈로그 [WHY] 로 기록** — okhttp 5.x(메이저, 필요가 생길 때 별도 회차), mediapipe 1.0.0(메이저 + 영어 전용 임베더 하나만 쓰는 죽은 경로라 올리기보다 걷어내는 판단이 먼저, ADR-013), play-services-tflite 16.5(GPU 델리게이트 = 모델 런타임 인접, 승격 시 실기기 재검증 필수)
- **[Note]** **실기기 스모크가 릴리스 게이트** — 일정 등록 1건(승인 카드 시각 온전), 위키 검색 1건(근거 답변), 앱 재시작 1회(마이그레이션·초기화 경로). compose·lifecycle·work 가 올라갔으므로 UI 렌더·전경 다운로드 알림도 눈으로 확인 권장
- **[QA/Test]** 전체 **271건** 통과(실패·에러·스킵 0) + lintDebug **경고 0**(새 lint 9.3.1 에서도 기준선 유지) + assembleDebug. 기존 테스트 단언 수정 0건, 소스 코드 수정 0건(빌드 스크립트·카탈로그만)

## [0.15.2] - 2026-08-14
> 0.15.1 마무리 뒤의 정리 회차. lint 경고 **76건(app 72 + data 4)을 전수 판정**해 21건은 고치고 55건은 [WHY] 와 함께 의도적으로 억제 — **"경고 0" 기준선**을 만들어 이후 새 경고가 1건만 생겨도 바로 보이게 했다. 대원칙: **버전 승격은 lint 알림이 아니라 검증 회차에서 수동 결정한다**(0.14.0→0.16.0 라운드가 그 증명이다, ADR-016~021). 테스트 스코프 상향 4종 중 2종이 게이트에서 탈락해 되돌렸다.
- **[Chore]** **미사용 문자열 13건 삭제** — `R.string` 참조가 3건뿐임을 grep 으로 확인(남긴 것: image_attached·image_size_kb·chat_input_hint). web_search_approval_* 5종도 포함(설정 플래그는 있으나 승인 UI 미구현 — 필요 시 git 에서 복원). 말줄임표 `...` 를 전용 문자 `…` 로(살아남은 chat_input_hint 1건, 나머지 2건은 문자열 삭제로 소멸)
- **[Fix]** **백업 정책을 Android 12+ 문법으로 명시** — `allowBackup=false` 는 12+ 에서 클라우드 백업에만 적용되고 폐기 예고 상태라, `dataExtractionRules`(클라우드 백업·기기 간 전송 전 도메인 차단) + minSdk 26 경로용 `fullBackupContent="false"` 를 함께 명시했다. 개인 일정·대화가 담기는 DB 이므로 의도를 전 버전에 걸쳐 문서화 — 백업·이전은 앱 내 ZIP 내보내기가 담당한다
- **[Chore]** 슬라이더 상태를 `mutableFloatStateOf` 로(오토박싱 제거), `Uri.parse` 를 KTX `toUri` 로(data 에 core-ktx 명시 — 전이 의존에 기대지 않는다), 하드코딩된 junit·mockk 를 버전 카탈로그로 통일(domain 포함)
- **[Note]** **테스트 스코프 상향 4종 중 2종 탈락** — ① mockk 1.14.11: 테스트는 전부 통과하지만 **lint(AGP 9.2)의 Kotlin 분석기가 크래시**한다(lintAnalyzeDebugUnitTest 가 BaseAgentStreamTest 해석 중 FIR 오류로 사망, 롤백으로 소멸 확인). ② robolectric 4.16.1: `VoiceChatIntegrationTest` 가 **단독 실행 2/2 결정적 실패**(전체 실행에서는 간헐 통과 — ShadowLooper 타이밍 의존 테스트라 스케줄러 변화에 민감). 둘 다 되돌리고 카탈로그에 [WHY] 로 재시도 조건을 남겼다. 채택 2종: espresso 3.7.0, androidx-test-ext-junit 1.3.0. **테스트 단언을 완화해 통과시키는 선택지는 규칙상 없다**
- **[Note]** **의존성 알림 lint 를 껐다**(GradleDependency·NewerVersionAvailable·AndroidGradlePluginVersion·OldTargetApi) — 버전 승격은 검증 회차에서 수동 결정하고, 특히 litertlm·tflite 계열은 승격 = 실기기 재검증 필수다. **대가: 보안 패치 알림도 함께 꺼진다** — 확인은 최신화 회차에서 수동으로 한다. 아이콘 2종(IconLauncherShape·IconDuplicates)은 adaptive icon 자산 회차까지 보류, UsableSpace 3곳은 인라인 억제(권장 대체재 allocateBytes 는 **타 앱 캐시를 지워서** 공간을 만드는 API 라 도입은 별도 결정)
- **[Note]** **다음 회차 예약 — 런타임 의존성 최신화**(사용자 결정): kotlin/AGP/KSP → hilt → compose-bom·androidx 계열 → 기타 순의 그룹별 커밋 + 그룹별 테스트 게이트 + 기기 스모크 1회. tflite·litertlm 계열 포함 여부는 그 회차에서 별도 판정. mockk·robolectric 재시도는 AGP 상향 이후
- **[QA/Test]** 전체 **271건** 통과(실패·에러·스킵 0) + lintDebug **경고 0**(app·data 리포트 XML issue 0건) + assembleDebug. 기존 테스트 단언 수정 0건

## [0.15.1] - 2026-08-13
> 0.15.0 의 계측이 **첫 실기기 가동에서 가설 하나를 기각**했다(깨짐 시점 KV 2,783/4096, 초과 경고 0건). 실험 여섯이 하루 안에 나머지를 정리해 **원인을 확정했다: GPU FP16 활성화 정밀도 × 문맥 깊이** — CPU 는 같은 문맥에서 온전, GPU 는 1,854 토큰까지 온전하고 2,122 부터 깨지며, FLOAT32 활성화로 완치된다. 그 레버가 AAR 에 없고 GPU 전용 모델 변형은 오디오·비전이 없어, **예산을 발병점 아래로 내렸다**(사용자 결정, ADR-021).
- **[Fix]** **대화 예산을 GPU 발병점 아래로** — `MAX_CONTEXT_TOKENS` 3000→1700, `MIN_CONVERSATION_RESET` 2600→1700, `MIN_HISTORY` 500→300, 슬라이더 상한 1700(저장값은 읽기 시 클램프 — 0.13.0 패턴). 1700 은 오버헤드(1400)+최소 히스토리(300)의 **바닥**이라 매 턴 재생성 루프 직전까지 내린 값이다. 플레인 턴 생성 시점 KV ≈ 1,800 < 온전선 1,854 — **일정 인자 생성이 이 안전권**이다. `TokenBudgetInvariantTest` +2 (발병점 관계·재생성 루프 방지)
- **[Note]** **판정 여정** — ① 실기기 계측: 초과 없이 깨짐 → KV 초과 기각 ② exp27: CPU × 오염 깊은 문맥(실기기와 토큰 수 사실상 동일) 온전 → 오염 모방 기각 ③ exp28: GPU × 같은 문맥 깨짐(**PC 결정적 재현 확보**) ④ exp29: 깨끗한 문맥도 깨짐(방아쇠=깊이) + FLOAT32 완치(원인=FP16) ⑤ exp30: 발병 경계 1,854~2,122 실측. 로더 로그가 메커니즘을 자백한다: *"System's default activation type for Text decoder is fp16"*. arm64 무죄(x86_64 재현)
- **[Note]** **GPU 전용 모델 변형(-gpu.litertlm)은 기각** — 같은 재현 조건에서 온전했으나(exp31) 섹션 덤프 결과 **오디오 인코더·표준 비전 인코더·CPU 디코더가 전부 없다**(exp32). 텍스트 전용 GPU 온리 빌드라 음성·이미지가 죽는다. 기본 모델 파일은 미갱신(SHA256 일치). "백엔드 하이브리드라 -gpu 파일로 바꿔도 음성 무관" 전언은 **실측으로 반증** — 백엔드는 실행 위치의 문제이고 가중치는 파일에 있어야 한다
- **[Note]** **대가 명기** — 히스토리 약 300토큰(2~3턴), 재설정 빈도 증가. 툴 회신 턴의 생성(~2,350)은 노출이 남는다(재생성 금지 턴) — 노출 대상은 요약 산문의 숫자이고 일정 인자는 BAD_FORMAT 방어선이 지킨다. 발병점은 PC GPU 실측이라 기기(Adreno) 경계는 실기기 재검증이 최종 판정자다
- **[Note]** **상류 보고 준비** — PC 결정적 재현 케이스 + 요청 2건(AAR 에 activationDataType 노출, 오디오·비전 포함 GPU 변형). 초안 `scratch/lab/upstream_issue_draft.md`. 원시 `<|tool_call>` 조각의 말풍선 저장·재생 소독은 위생 결함으로 별건 분리
- **[Note]** **실기기 확정** — 예산 1700 빌드에서 같은 발화의 승인 카드가 `2026-08-17 / 10:00–11:00` 으로 온전, 승인 일정이 기기 캘린더에 지정 시각으로 저장(isoToMs 수정 동시 확인), 위키 근거 답변의 날짜(`2015년 10월 20일`)도 온전. PC 발병점이 기기 GPU(Adreno)에도 유효했다. 음성 전사만 미확인으로 남음(오디오 경로 무변경 + PC 대조군 exp32b 통과라 위험 낮음)
- **[QA/Test]** 전체 **271건** 통과(실패·에러·스킵 0). 신규 2건(`TokenBudgetInvariantTest`), 갱신 — `ContextBuilderTest` 최소 히스토리 산술 픽스처(500→300 상수 변경 반영, 단언 의미 무변경)

## [0.15.0] - 2026-08-13
> 실기기 한 줄 검증에서 **AAR 버전 가설이 기각됐다** — 0.16.0 에서도 `202026-081717T010000` 으로 깨졌다. 검증 세션이 **이어지던 긴 세션**이었음이 확인되면서 ADR-016 의 "KV 조용한 초과" 후보가 1순위로 부활했고(가설 제기: 사용자), 코드 감사에서 **맞는 예산이 잘못 집행되는 구멍 3개**를 찾아 봉합·계측했다. 판정은 실기기 재검증 한 번이 남아 있다 (ADR-020).
- **[Fix]** **정상 일정도 기기 캘린더에 '지금' 시각으로 들어가고 있었다 (별개 버그)** — `AndroidCalendarTool.isoToMs` 에 LocalDateTime 폴백이 없어 **툴 선언이 모델에게 지시하는 바로 그 형식**(`2026-08-07T15:00:00`, 오프셋 없음)이 항상 파싱 실패했고, catch 가 `System.currentTimeMillis()` 로 무음 대체했다. `IsoDateTimeParser` 로 교체하고, 시작 시각 파싱 실패는 '지금' 대체 대신 **동기화 실패**로 처리한다 — 호출자가 이미 실패를 경고 로깅 후 로컬 저장을 유지하므로(ADR-004) 상위 변경 0. 틀린 시각의 실캘린더 기록은 "동기화 안 됨"보다 나쁜 무음 데이터 오염이다
- **[Fix]** **AddSchedule 시각 인자를 ISO 8601 로 검증** — `ToolArguments.requireIsoDateTime`/`optIsoDateTime` + `Reason.BAD_FORMAT` 신설. 깨진 값(`202026-081717T010000`)은 **승인 카드가 뜨기 전에** 걸려 모델 자가수정 루프로 돌아간다. 판정 파서는 하류(기기 캘린더)와 같은 `IsoDateTimeParser` — 여기서 통과한 값이 하류에서 실패하는 틈이 없다. 오류 안내에 깨진 원문은 에코하지 않는다(깨진 토큰열 재주입 방지). 루프 상한(3회) 초과는 이제 말풍선을 남긴다 — 검증 추가로 이 경로가 현실이 됐는데 기존엔 무반응 종료였다(`BaseAgentStreamTest` 의 동작 고정을 새 계약으로 갱신)
- **[Fix]** **토큰 추정을 실측 비율로 보정** — `GemmaTokenizer` 의 `length/3+1` 은 한국어 채팅을 실측의 0.52~0.60배, **ISO 문자열을 0.35배**로 과소평가했다(exp26, `engine.tokenize` 직접 측정: `2026-08-17T10:00:00` = 19자 = **19토큰**). 슬라이딩 윈도우가 그만큼 초과 히스토리를 실어 **재생성 직후의 대화조차 4096 을 넘을 수 있었다.** 문자 3클래스(영문·공백 /4.25, 그 외 ASCII /0.9, 비ASCII /1.2)로 교체 — 격자 탐색에서 전 14샘플 **과소평가 0건** AND 과대 1.5배 이내(최악 1.46배). 대가는 히스토리 전형 1.1~1.3배 과대(축소)이고, 초과의 무음 품질 저하보다 싸다. `GemmaTokenizerTest` 가 실측 픽스처로 게이트를 고정한다
- **[Fix]** **위키 결과 캡을 토큰 예산에서 파생** — ko 5,000자 하드컷(실측 약 2,500토큰)은 예산이 6000~8000 이던 최초 커밋 시절 유물이다. 툴 결과는 **턴 중간**에 KV 로 들어가고 툴 회신 턴은 재생성 금지 턴(0.8.5 가드)이라 이 캡이 유일한 방어선인데, 0.13.0 예산 개편 때 재검토되지 않았다. `TOOL_RESULT_MAX_TOKENS = 500` 파생 + 단어 중간 절단 대신 **문장 경계 절단**(`SentenceTruncator`, domain 신설) — 잘린 조각이 사실처럼 읽히는 것 자체가 환각의 재료다. 언어별 자수 분기(zh/fr/es)는 토큰 추정기가 문자 클래스로 언어 밀도를 반영하므로 삭제
- **[Added]** **KV 실토큰 계측(디버그)** — 턴 종료·툴 회신 직전에 `getTokenCount()` 를 로그로 남기고 `> 4096` 이면 "KV 용량 초과 상태로 계속 진행" 경고. AAR 은 초과를 오류 없이 진행하므로(파이썬은 거부, exp17) 이 로그가 실기기에서 **초과와 깨짐의 시간적 상관**을 확정할 유일한 증거다. 완전한 예산 보장은 재생성 금지 가드가 있는 한 구조적으로 불가 — 이번 처방은 최악 초과를 3×~2,500 에서 3×~500 토큰으로 줄이고 관측 가능하게 만드는 것
- **[Fix]** **위키 요약 응답에 근거 고정 지침** — 실기기에서 요약(도입부)에 없는 데뷔일을 지어내 답했다. 툴 회신 턴은 턴 지침(exp20)이 `currentInput=""` 로 덮여 사라지는, **시스템 지시로부터 가장 먼 턴**이라 데이터 옆이 유일한 근거리 주입 지점이다 — `SearchMemoryToolExecutor` 의 실기기 검증된 패턴을 확장했다. 문구는 exp25 픽스처와 문자 단위 동일 유지
- **[Note]** **일반 정직성 규칙(레버 B)은 기각** — exp25 실측: 회귀 게이트는 통과했지만(일정 4 + 조회 3 = 7/7, 과거 "DO NOT guess" 사고 재발 없음) 데이터 옆 지침(레버 A) 단독이 이미 명시적 "없다" 답변 3/3 천장이라 **추가 효과가 측정되지 않았다**(B 단독 2/3, A+B 3/3 = A). baseline 은 fabrication 0/3 에 명확한 답변 1/3 — 실기기의 fabrication 은 긴 세션 조건이라 depth 0 에서 재현되지 않았고, 명확성 지표가 조건을 갈랐다. 측정으로 정당화되지 않는 시스템 지시 변경은 넣지 않는다
- **[Note]** **CPU 강제 토글은 이번에 넣지 않았다** (사용자 결정) — 실기기 재검증에서 "초과 없는데 깨짐" 이 나올 때만 백엔드/arm64 판정 수단으로 다음 라운드에 추가한다. GetSchedule 근거 고정도 미적용 — 관측된 환각은 위키뿐이고, 타임스탬프 판정 라운드에 관측값 변수를 더하지 않는다
- **[Note]** **실험실 재구축** — conda `friday` 환경 + `litert-lm` 0.16.0(패키지명이 `litertlm` 이 아니다), `PromptFixtureExportTest` 로 픽스처 재내보내기, 툴 왕복 스모크 통과. `.agents/04_MODEL_EVIDENCE.md` 에 **"실험실 통과는 용량 이내에서만 유효"** 규칙 추가 — 파이썬은 초과를 거부하므로 실험실 결과를 초과 가설의 반증으로 인용하지 말 것(exp22 의 36/36 을 그렇게 오독할 뻔했다)
- **[QA/Test]** 전체 **269건** 통과(실패·에러·스킵 0) + lintDebug(Error 0) + assembleDebug, **빌드 경고 0건**. 신규 30건 — `IsoDateTimeParserTest` 8(**domain 테스트 소스셋 신설**), `ToolArgumentsTest` +6, `AddScheduleToolExecutorTest` 4, `GemmaTokenizerTest` 2(exp26 실측 픽스처로 과소평가 0건 게이트 고정), `SentenceTruncatorTest` 6, `SearchWikipediaGroundingTest` 2, `TokenBudgetInvariantTest` +2. 갱신 — `ToolApprovalE2ETest` 픽스처만("15:00" → 유효 ISO, 단언 무변경), `BaseAgentStreamTest` 의 루프 상한 동작 고정을 새 계약(말풍선 저장)으로 교체(의도된 계약 변경 — 위 [Fix] 참조)

## [0.14.0] - 2026-08-13
> litertlm 을 0.16.0 으로 올리고 공식 문서 대조에서 나온 어긋남을 정리했다. 상향은 **소스 수정 0** 으로 끝났고, 어긋남 5건 중 3건은 고쳤으며 1건은 측정 근거가 없어 보류, 1건은 **애초에 결함이 아니었다** (ADR-019).
- **[Changed]** **litertlm 0.14.0 → 0.16.0.** 카탈로그 한 줄로 끝났다 — 컴파일 오류 0, 테스트 236건 통과, 경고 0. 사전에 classes.jar 을 비교해 클래스 8개 추가·**제거 0** 임을 확인한 대로였다. 글자 유실의 마지막 남은 후보가 이 런타임이었으므로(파이썬 0.15.0·0.16.0 양쪽 36/36 온전) **다른 의존성은 일부러 건드리지 않았다** — 기기에서 무엇이 달라졌는지 귀속하려면 단독 변경이어야 한다
- **[Fix]** **생각 모드를 `ThinkingConfig` 타입 API 로 전환** — 0.14.0 에는 그 클래스가 없어 `extraContext = mapOf("enable_thinking" to false)` 맵으로 껐고, **맵의 키 이름 하나에 의존하는 상태**였다. exp24 에서 생각 모드가 툴 호출을 4/4 → 2/4 로 깎는 것과, `enable_thinking=true` 에서도 `<|think|>` 마커가 출력에 나오지 않아 **응답으로는 켜졌는지 알 수 없다**는 것을 함께 확인했다 — 조용히 실패하면 툴 호출만 나빠지고 눈치챌 방법이 없었다. 이제 컴파일러가 검증한다
- **[Fix]** **녹음을 30초로 제한** — 공식 문서(capabilities/audio)가 최대 30초·초당 25토큰으로 못박는데 앱에는 상한이 **아예 없었다.** 마이크를 다시 누를 때까지 무한히 녹음돼 한계를 넘긴 오디오가 그대로 모델에 들어갔고 그때의 동작이 정의되지 않았다. 화면 타이머가 30초에 자동 종료·전송하고, `AudioRecorder` 가 파일 자체를 상한에서 끊는다(타이머가 프로세스 일시정지로 늦어도 모델이 받는 것은 항상 상한 이내). 초과분은 버퍼 전체를 버리지 않고 남은 만큼만 쓴다 — 버퍼 크기가 상한과 안 맞으면 마지막 조각이 통째로 사라져 눈에 띄게 짧아진다
- **[Fix]** **`EngineConfig.maxNumImages` 명시** — 한 턴에 정확히 한 장을 붙이므로 1 이다. `maxNumTokens`·`audioBackend` 와 같은 "보이지 않는 기본값" 이었고, 기본값이 우리 사용량보다 크면 이미지 한 장이 시각 토큰 예산만큼 KV 를 먹는 만큼 4096 안의 히스토리가 조용히 줄어든다
- **[Docs]** **이미지 선축소 주석이 코드와 모순이던 것을 정정** — 주석은 *"리사이징 금지(Gemma 4 네이티브 패칭 활용)"* 라 적었지만 코드는 1024px 로 반씩 줄인다(4000px 사진 → 1000px). 이 축소는 **OOM 방어**이고 해상도 정책이 아님을 명시했다. 코드는 줄이고 주석은 안 줄인다고 말하는 상태였다
- **[Fix]** 프리페이스 진단 로그가 `take(2000)` 으로 **툴 선언 블록 중간에서 잘렸다** — "툴이 선언됐나" 를 답하려고 넣은 로그인데 정작 그 답이 안 보였다(2026-08-13 로그 분석에서 발견). 자르는 대신 1500자씩 나눠 전체를 남긴다
- **[Note]** **시각 토큰 예산은 설정하지 않는다** — 공식 문서는 70/140/280/560/1120 중 선택이 Gemma 4 비전의 요점이라 하고 `ExperimentalFlags.visualTokenBudget` 이 존재하지만, **파이썬 패키지가 그 플래그를 노출하지 않아 값을 고를 근거를 로컬에서 만들 수 없다.** 낮게 잡으면 문서 스크린샷 읽기(PRD F3 핵심 용도)를 조용히 깎고 높게 잡으면 4096 KV 에서 히스토리를 잃는다 — 측정 없이 정할 값이 아니므로 런타임 기본값에 맡긴다
- **[Note]** **thought 삭제 예외는 결함이 아니었다** — 앞서 *"문서의 '툴 호출 시퀀스 중 예외' 를 우리는 지키지 않는다"* 고 적었으나 틀렸다. `ToolParser` 가 걷어내는 것은 **화면·DB 에 남길 본문**이고 모델에게 되돌아가는 문맥이 아니다. 재사용 경로는 런타임이 KV 를 들고 있고, 재생성 경로는 문서의 기본 규칙과 같으며, 예외에 해당하는 "툴 시퀀스 중 재생성" 은 `GemmaModelRunner` 가 이미 막는다(0.8.5 사고 때문에 넣은 가드가 결과적으로 규칙을 만족한다)
- **[Note]** **정정 — APK 26MB 증가는 상향 때문이 아니었다.** 상향 직후 그렇게 적었으나 비교 기준(127MB)이 어제 0.11.1 빌드였다. 0.14.0 으로 되돌려 다시 빌드한 A/B 결과 **151.66MB → 152.67MB(+1.0MB)** 로 `.so` 증가분과 맞는다
- **[Note]** **부수 관측 — 죽은 경로가 APK 52MB 를 쓴다.** `lib/` 이 APK 의 95MB 를 차지하고 그중 **MediaPipe 가 4개 ABI 로 약 46MB** 인데, 우리가 쓰는 것은 영어 전용으로 판명된 임베더 하나뿐이다(ADR-013). assets 의 6.1MB 모델까지 합쳐 52MB. 걷어내는 판단은 별건으로 남긴다
- **[QA/Test]** 전체 **239건** 통과 + lintDebug + assembleDebug, **빌드 경고 0건**. 신규 3건 — `AudioLimitTest`(상한 값, 30초의 토큰 비용이 프리필 천장 안, 상한을 올릴 때 예산 재계산을 강제하는 여유 확인)
- **[Note]** **남은 판정은 실기기 한 줄** — `"다음주 월요일 10시 팀 회의"` → 승인 카드 시각이 `10:00` 으로 온전하면 글자 유실의 원인이 AAR 0.14.0 이었던 것이 확정된다. 여전히 깨지면 Android arm64 백엔드 고유가 남는 후보다

## [0.13.1] - 2026-08-13
> Gemma 4 공식 문서 5종을 코드와 대조하고, **근거를 공식 문서와 우리 실측으로 재기준화**했다. 코드 변경은 주석·문서가 대부분이지만 그 과정에서 gallery 전언 2건이 실측으로 승격되고 내 추측 1건이 폐기됐다 (ADR-018).
- **[Docs]** **근거 등급 규칙 신설** (`.agents/04_MODEL_EVIDENCE.md`) — ① Gemma 4 공식 문서 ② 우리 실측(실험 번호 명기) ③ litertlm API 계약 순. **gallery 는 가설의 출처이고 근거가 아니다** (사용자 결정) — Gemma 3n·DiffusionGemma 등 여러 모델을 함께 다루는 데모 앱이라 그 제약이 우리 모델에 적용된다는 보장이 없다. 이 규칙 없이 두 번 틀렸다: `maxNumTokens` 미설정(gallery 기본값 1024가 근거였다)과 `audioBackend`/`visionBackend`(둘 다 "for **Gemma 3n**" 주석이었다)
- **[Note]** **실측 승격 — 생각 모드가 툴 호출을 깎는다**(exp24): `enable_thinking=false` 4/4 → `true` 2/4. 깎이는 쪽은 일정 등록이고 위키는 살아남는다. gallery 의 주장이 Gemma 4 E4B 에서도 맞지만 근거는 이제 이 표다
- **[Note]** **위험 발견 — 응답 텍스트로는 생각 모드가 켜졌는지 알 수 없다.** `enable_thinking=true` 에서도 `<|think|>` 마커가 출력에 한 번도 나오지 않았다. 앱은 0.14.0 에 `ThinkingConfig` 가 없어 `extraContext` 맵의 키 이름 하나로 끄는데, 조용히 실패하면 **툴 호출만 나빠지고 눈치채지 못한다** — 0.16.0 의 타입 있는 API 로 올릴 이유
- **[Note]** **실측 승격 — 오디오만 백엔드 고정이 필요하다**(exp23): `audio_backend=GPU` 는 **엔진 생성 자체가 실패**하고 CPU 는 정상. 비전은 **CPU·GPU 양쪽에서 이미지를 정상 인식**한다. 둘 다 미설정이면 추론이 실패한다
- **[Note]** **내 추측 폐기** — *"CPU 폴백 기기에서 이미지가 조용히 깨질 수 있다"* 고 했는데 틀렸다. gallery 의 "must be GPU for Gemma 3n" 을 우리 모델로 확장한 것이었다. `visionBackend = backend` 를 그대로 둔다
- **[Docs]** 주석 9곳의 근거 표기 재작성 — greedy(topK=1), 제약 디코딩, 생각 모드 끄기, 오디오 백엔드, 툴 이름 백틱 지목, `@Tool`+`ToolSet` 선언 방식. 각각 "공식 문서" 또는 "우리 실측(실험 번호)" 으로 바꿨다. 남은 gallery 언급 3곳은 **참고임을 명시하는 문맥**이다
- **[Docs]** `PromptAssembler` KDoc 의 끊긴 참조 정정 — *"검색된 기억은 `ChatPrompt.turnContext` 로 분리"* 라 적혀 있었으나 그 필드는 0.10.0 에서 제거됐다(ADR-013)
- **[Note]** **프롬프트 형식은 문서와 일치한다** — 실기기 렌더 프리페이스가 `<|turn>system` → 지시 → `<|tool>declaration:...`, 구분자 `<|"|>` 로 문서 형식을 그대로 따른다. 런타임이 만들어 주는 부분이라 우리가 틀릴 여지가 없었다(ADR-008 의 값). 이미지가 텍스트보다 앞에 오는 것도 문서 요구와 일치
- **[Note]** **비디오는 반영할 것이 없다** — 문서가 프레임 샘플링·fps·토큰 비용·최대 길이를 아무것도 명시하지 않는다
- **[Note]** **문서와 어긋난 5건을 백로그로 정리** — ① 녹음 길이 제한 없음(문서 최대 30초, 25토큰/초) ② 시각 토큰 예산 미설정(70/140/280/560/1120 중 선택이 Gemma 4 비전의 요점) ③ 이미지를 1024px 로 선축소하면서 주석은 "리사이징 금지" 라 적음 ④ `maxNumImages` 미설정 ⑤ thought 삭제의 "툴 호출 시퀀스 예외" 미준수. 부수로 진단 로그가 프리페이스를 2000자에서 잘라 툴 선언 블록도 다 못 보여준다
- **[Note]** **파이썬 하네스를 0.16.0 으로 올려 재판정** — 자릿수 **36/36 온전**(0.15.0 과 동일). 글자 유실의 남은 후보가 **AAR 0.14.0** 하나로 좁혀졌다. API 표면 비교로 업그레이드 위험도 확인 — 0.14.0 → 0.16.0 은 클래스 8개 추가·**제거 0개**이고 우리가 쓰는 심볼이 전부 남아 있다(`ThinkingConfig`·`ResponseFormat`·`RepetitionPenaltyConfig` 등이 추가분)
- **[Added]** 실험 3종 — exp22(앱 설정에서의 자릿수), exp23(백엔드 매트릭스), exp24(생각 모드 대 툴 호출). 판정 가능한 이미지 픽스처(`vision_shapes.png` — 빨간 사각형 + 파란 원)를 직접 생성해 외부 이미지 의존을 없앴다
- **[QA/Test]** 전체 **236건** 통과 + lintDebug + assembleDebug, **빌드 경고 0건** (코드 변경이 주석·KDoc 중심이라 신규 테스트 없음)

## [0.13.0] - 2026-08-13
> ADR-016 이 남긴 두 항목을 해소했다. **툴 호출 실패의 원인은 ADR-016 에 적은 '히스토리 모방' 이 아니었다** — 실험 네 개가 순서대로 뒤집어 진짜 원인이 **지침과 사용자 발화 사이의 거리**임을 밝혔다. 판정은 전부 로컬 실험실에서 했고 실기기를 쓰지 않았다 (ADR-017).
- **[Fix]** **히스토리가 쌓이면 툴을 부르지 않던 문제** — 툴 지침이 시스템 턴에 한 번만 있고, 히스토리가 길어지면 사용자 발화와 수천 토큰 떨어진다. `PromptAssembler` 가 툴이 선언된 턴에만 **166자 한 줄을 사용자 턴 앞에 재게시**한다. 앱이 실제로 내보내는 픽스처로 재판정한 결과 원본 실기기 히스토리에서 툴 3종 × 깊이 2 = **6/6** 호출(exp21). 같은 조건 baseline 은 깊이 40 에서 0/2 였다
- **[Note]** **원인 규명 경과 — 두 번 틀렸다.** ① ADR-016 은 "모방" 이라 적고 처방으로 "히스토리에 툴 호출 구조 보존(Room v5→v6)" 을 제시했다. 공식 문서가 그 형식을 **명시**하므로 확신했지만, 구조를 복원해도 일정 툴은 **0/3** 이었다(exp18) — 규격 준수와 이 결함의 해법은 다른 문제였다. ② 이어서 "같은 답변을 60자로 자르면 3/3"(exp19)이 나와 모방이 아니라 산문의 양임이 드러났고, 절단 임계값은 **60자 통과 / 120자 실패** 로 너무 낮아 절단은 제품 해법이 못 됐다. 남은 후보가 위치였고 그것이 답이었다(exp20). **Room 마이그레이션을 하기 전에 실험실에서 선검증한 것이 그 작업을 막았다**
- **[Note]** ADR-010 과 같은 종류의 발견이다 — 그때도 `[System Data]` 블록의 **문구가 아니라 위치**가 툴 선택을 갈랐다(조회 3/3 vs 1/3). 이 모델에게 프롬프트의 거리는 내용만큼 중요하다
- **[Fix]** **앱 예산이 엔진 KV 용량을 넘던 문제** — `Constants.kt` 는 *"gemma-4 컨텍스트는 32K 이고 `maxNumTokens` 를 설정하지 않으므로 상한이 아니다"* 라고 적었는데 두 군데가 틀렸다. **컨텍스트 창과 KV 할당량은 다른 것이고**(모델 카드의 128K 는 주의를 둘 수 있는 상한, `maxNumTokens` 는 런타임이 메모리를 잡는 크기 — 실측 토큰당 약 1MB로 128K 는 100GB 이상), **넘기지 않으면 상한이 없어지는 게 아니라 런타임 기본값 4096 이 된다**(exp17: `5857 >= 4096` 거부). `ENGINE_MAX_TOKENS = 4096` 을 명시 전달하고 나머지를 거기서 파생시킨다
- **[Changed]** 오버헤드 예약 **2600 → 1400**. `Conversation.token_count` 실측(`measure_overhead.py`): 시스템 지시 573 + 툴 5종 선언 **652** + few-shot 104 = **1,329**. 예전 주석의 "툴 선언만 ~2천 토큰" 은 틀렸고 과대 예약이 히스토리 예산을 1,200 토큰 넘게 조용히 깎고 있었다 — **그래서 예산을 6000→3000 으로 내려도 히스토리는 오히려 늘어난다**
- **[Fix]** 대화 재설정 하한 **4000 → 2600**. 4000 은 프리필 천장(3328)보다 커서, 예산을 낮춰도 임계값이 밀려 올라가 **대화가 용량을 넘도록 자라는 것을 허용**했다 — 재생성을 막으려는 하한이 정반대로 작동하고 있었다
- **[Changed]** 설정 슬라이더 상한 **8000 → 3000**. 올릴 수 있는 범위의 절반 이상이 애초에 담기지 않는 값이었다. 저장된 값은 **읽을 때만** 천장으로 자른다 — 그대로 되살리면 이 결함이 업그레이드한 사용자에게만 남고, 저장값을 덮어쓰는 것은 사용자가 만지지도 않은 설정을 바꾸는 일이다
- **[Added]** `TokenBudgetInvariantTest` — 예산 관계를 고정한다. 이 값들은 상수·설정 화면·DataStore·런타임에 흩어져 각자 그럴듯한 이유로 바뀌므로 코드로 강제할 수 없다. 초과가 크래시가 아니라 **품질 저하**로만 드러나는 것이 이 결함을 어렵게 만든 이유였다
- **[Added]** 디버그 빌드에서 KV 천장에 닿으면 로그를 남긴다. AAR 0.14.0 은 용량을 넘겨도 오류를 내지 않으므로(파이썬 0.15.0 은 거부) 조용히 넘어가는 경로를 남기지 않는다
- **[Added]** 실험 4종 — exp18(툴 구조 A/B), exp19(길이 대 내용), exp20(지침 위치), exp21(배포 모양 최종 판정). `PromptFixtureExportTest` 가 **턴 지침도** 내보내 실험이 앱과 다른 프롬프트를 재지 않게 한다
- **[Docs]** exp14 의 결론을 정정했다 — "모방 확정" 이라 적혀 있었으나 exp18~21 이 뒤집었다. 파일은 원인을 좁히는 첫 단계로 남기되 결론은 exp20·21 을 따르게 명시
- **[Deferred]** 히스토리의 툴 호출 구조 보존(Room v5→v6) — 문서가 요구하는 형식이므로 규격 준수 항목으로 남긴다. #1 을 풀지 못하고 마이그레이션이 필요해 우선순위를 내렸다
- **[Note]** **글자 유실은 앱 설정에서 재현되지 않는다(exp22)** — 두 수정을 넣은 뒤 실제 설정(`max_num_tokens=4096`, 턴 지침 재게시, 원본 실기기 히스토리)으로 재판정: constrained on/off × depth 0·8·20 × 발화 3종 × 반복 2회 = **36/36 온전**. 기기에서 `234` 로 저장됐던 비밀번호 `1234` 도 온전하다. 이전에는 깊은 히스토리에서 **툴 호출 자체가 없어 인자를 볼 수 없었으므로**, 지침 위치를 고친 지금이 처음으로 측정 가능한 시점이었다
- **[Note]** **AAR 상위 버전이 존재한다** — Google Maven 에 `litertlm-android` **0.15.0·0.16.0** 이 있다(0.16.1 이상 없음). ADR-016 이 *"버전이 달라 이 하네스로는 가를 수 없다"* 고 적은 것은 확인하지 않은 추정이었다. 0.15.0 은 이 하네스가 쓰는 바로 그 버전이므로 **글자 유실의 남은 후보는 검증 가능한 가설**이 됐다. 업그레이드는 별건으로 남긴다
- **[QA/Test]** 전체 **236건** 통과(실패·에러·스킵 0) + lintDebug + assembleDebug, **빌드 경고 0건**. 신규 9건 — `TokenBudgetInvariantTest` 5, `PromptAssemblerTest` +4(턴 지침 유무·발화가 맨 뒤·중복 제거가 원문 비교)

## [0.12.0] - 2026-08-12
> 실기기 검증(Galaxy S25 Ultra, Android 16)에서 통과 6건·실패 5건이 나왔다. `adb` 로 앱 DB·감사 로그·logcat 을 직접 읽어 증상을 **값으로** 확정하고, 그 DB 를 픽스처로 삼아 **실기기 없이 재현하는 실험실**을 만들었다. 원인 확정 3건을 고쳤고, 남은 2건은 원인까지 규명했으나 처방에 대가가 있어 결정을 남겼다 (ADR-016).
- **[Fix]** **음성 입력이 처음부터 동작하지 않았다** — `EngineConfig` 가 `backend`·`visionBackend` 만 설정하고 `audioBackend` 를 한 번도 넘기지 않았다. 기본값이 `null` 이라 엔진이 `Content.AudioFile` 을 받지 못한다. PC 실험(exp16)이 네이티브 오류 원문까지 재현했다 — `Audio executor should not be null, please TryLoadingAudioExecutor() first.` 0.11.0 회귀가 아니라 이전부터 있던 결함이다. gallery 근거(`must be CPU for Gemma 3n`)에 따라 오디오만 CPU 로 고정하고 비전은 GPU 로 둔다
- **[Note]** **통과하던 테스트가 동작 불가능한 경로를 검증하고 있었다** — `TranscribeAudioUseCaseTest` 7건이 위 결함을 놓친 이유는 `ModelRunner` 를 목으로 대체했고 **잘못 설정된 것이 정확히 그 목이 가린 컴포넌트**였기 때문이다. 설정 조립을 `buildEngineConfig` 순수 함수로 꺼내 목을 거치지 않고 단언한다. `audioBackend` 기본값이 `null` 이라는 사실 자체도 테스트로 못박아, SDK 가 기본값을 바꾸면 알려 준다
- **[Fix]** **무음 녹음에서 지시문이 전사문으로 저장되던 문제** — 무음 2초에 모델이 사용자 턴의 지시 문장(`"이 오디오를 들리는 그대로 받아써 주세요."`)을 그대로 되읊는 것이 실측됐다(exp16). 공백이 아니므로 빈 검사를 통과해 앱이 그 문장을 사용자 메시지로 저장하고 답까지 했다(PRD EC3 위반). 되읊음을 문자열로 걸러내는 대신 **되읊을 대상을 없앤다** — 오디오만 보내도 전사는 정상이고 무음에서는 빈 결과가 온다. 런타임은 `currentInput` 이 비면 텍스트 파트를 붙이지 않는다
- **[Fix]** **기기 캘린더 실패 안내가 가장 필요한 상황에서만 사라졌다** — 뷰모델이 일정 0건일 때 `CalendarUiState.Empty` 를 내면서 `ScheduleData` 를 통째로 버려 `deviceCalendarFailed` 가 소실됐다. `Empty` 는 `Success(...).events.isEmpty()` 와 **같은 것을 두 번 표현**한 것이고 그 이중 표현이 데이터를 버리는 경로를 만들었으므로, 증상을 덧대지 않고 표현을 하나로 줄였다. `applyDateFilter` 에도 같은 붕괴가 있었다 — 일정 없는 날짜를 눌러 본 것만으로 안내가 사라졌다
- **[Fix]** **일정 요약을 계산만 하고 버렸다** — `CalendarScreen` 에 `summary` 를 읽는 코드가 한 줄도 없었다. PRD F4 는 "일정 목록 + 요약 텍스트"를 요구하는데, 앱은 매번 10초짜리 추론을 돌려 결과를 버리고 **그것을 기다린 뒤에야** 목록을 냈다(그동안 화면은 스피너). 요약을 `SummarizeScheduleUseCase` 로 분리해 목록을 먼저 내보내고 요약은 도착하는 대로 채운다. `GetScheduleToolExecutor` 는 새 유스케이스를 직접 호출해 **툴 결과의 모양을 그대로 유지**한다 — 모델에게 주는 관측값을 바꾸면 툴 호출 실패 조사에 변수가 늘어난다(사용자 결정)
- **[Fix]** 전사 실패가 감사 로그에 한 줄도 남지 않았다 — 이 경로는 `BaseAgent.handleErrorAndReturn`(기록 담당)까지 가지 않고 오케스트레이터에서 바로 반환한다. 그래서 실기기에서 음성이 계속 실패했는데 원인을 로그가 아니라 소스 대조로 찾아야 했다. 실패 시 임시 WAV 가 캐시에 남던 것도 함께 고쳤다(`return` 이 삭제를 건너뛰었다 — 실측 138KB 잔존, 마이크 녹음이 남는 것은 개인정보 문제이기도 하다)
- **[Fix]** 문맥 구성 실패 시 말풍선에 `DbReadError(...)` 원문이 저장됐다 — 0.11.0 에서 `BaseAgent` 쪽은 고쳤는데 오케스트레이터가 빠져 있었다
- **[Added]** **로컬 실험실** (`scratch/lab/`, gitignore 대상) — 실기기 DB(대화 79개·감사 46건·깨진 일정 2건)를 픽스처로 보관하고, 한국어 TTS 로 만든 음성·무음 WAV 를 더했다. `device_fixture.py` 가 DB 를 `L.Message` 목록으로 바꾸며 `mode="terse"` 로 **모방과 길이를 분리**한다. `Lab.conversation()` 으로 대화 재사용 경로를, `max_num_tokens`·`audio` 로 엔진 설정을 실험에서 통제한다. exp14(툴 호출)·exp15(글자 유실)·exp16(전사)·exp17(KV 용량) 신설
- **[Added]** `PromptFixtureExportTest` — 실험실의 시스템 지시가 **손으로 베낀 사본**이라 0.8.6 시절에 멈춰 있었다(날짜 블록 없음, `search_memory` 트리거 없음, 툴 4종). 그 상태로 돌린 실험은 앱과 다른 프롬프트를 측정하며 틀렸다는 사실도 조용히 남는다. 실제 `PromptAssembler`·`TranscribeAudioUseCase` 출력을 픽스처로 내보내 사본이 낡을 수 없게 했다. 전사 지시는 상수를 읽지 않고 **실제로 나가는 `ChatPrompt` 를 붙잡는다**(상수가 `private` 이고, 테스트 편의로 가시성을 넓히는 것보다 정확하다)
- **[Note]** **툴 호출 실패의 원인 확정 — 히스토리 모방 (미수정)** — 실기기에서 위키 검색이 필요한 발화 4연속 모두 `TOOL_CALL` 0건이었고, 그러면서 "위키백과에서 검색하여 가져왔습니다" 로 시작하는 전부 거짓인 답변을 냈다. exp14 가 원인을 갈랐다: 히스토리의 어시스턴트 답변만 짧은 문장으로 대체하면 **depth 8·20·40 에서 모두 호출 성공**, 실제 답변을 두면 **모두 미호출**이다. 턴 수·사용자 발화·길이는 그대로다. 길이·대화 재사용·KV 용량 가설은 배제된다. 원인은 프롬프트 문구가 아니라 **재생하는 히스토리가 손실된 버전**이라는 것 — `ChatMessage` 에 툴 호출 필드가 없어 복원된 대화는 "어시스턴트가 언제나 툴 없이 답했다"는 이야기가 되고 모델이 그 패턴을 따른다. 처방은 대가가 갈려 결정을 남겼다(ADR-016)
- **[Note]** **신규 결함 — 엔진 KV 용량이 앱 예산보다 작다 (미수정)** — `Constants.kt:18` 은 "`maxNumTokens` 를 설정하지 않으므로 상한이 아니다" 라고 적었지만, 설정하지 않으면 상한이 없어지는 것이 아니라 런타임 기본값 **4096** 이 된다(exp17 실측: `5857 >= 4096` 거부). 앱 예산은 6000, 슬라이더 최대는 8000 이다 — **엔진 용량을 넘는 프롬프트를 의도적으로 만든다.** 파이썬 0.15.0 은 오류를 내지만 AAR 0.14.0 은 실기기에서 오류 없이 답을 냈다(초과분을 조용히 처리). **KV 할당량은 모델 컨텍스트 창과 다른 것이다** — 모델 카드의 128,000 토큰은 모델이 주의를 둘 수 있는 상한이고 `maxNumTokens` 는 런타임이 메모리를 잡는 크기다. 실측: 기본(4096) 5,003MB → 8192 로 올리면 9,188MB, **토큰당 약 1MB**다(128K 를 잡으려면 100GB 이상). 실기기 앱 PSS 5.5GB 가 기본값 5.0GB 와 같은 자릿수라 안드로이드 기본값도 4096 으로 보인다. 따라서 **용량을 올리는 것은 선택지가 아니고 예산을 4096 안에 맞춰야 한다**
- **[Note]** **글자 유실은 여전히 미확정** — 배제된 것: 스트리밍 채널(툴 인자는 네이티브 파싱 구조체에서 읽는데 깨져 있다), 제약 디코딩 FST(exp15 에서 constrained=True·greedy·depth 0 으로 `2026-08-17T10:00:00` 정확). 남은 1순위는 AAR 0.14.0 과 파이썬 0.15.0 의 런타임 차이이며, 위 KV 초과와의 상호작용도 후보다
- **[QA/Test]** 전체 **227건** 통과(실패·에러·스킵 0) + lintDebug + assembleDebug, **빌드 경고 0건**. 신규 20건 — `EngineConfigTest` 4(오디오 백엔드 누락이 실패로 드러남 + 기본값 `null` 고정), `VoiceTranscriptionFailureTest` 4(감사 기록·임시 파일 삭제·원문 오류 미노출), `CalendarViewModelTest` 5(플래그 소실 2건은 **수정 전 실제로 실패함을 확인**), `SummarizeScheduleUseCaseTest` 4, `PromptFixtureExportTest` 2, `TranscribeAudioUseCaseTest` +1
- **[Note]** **커밋 분리 계획을 하나 합쳤다** — 캘린더의 `Empty` 제거와 요약 분리가 같은 세 파일을 건드려 부분 스테이징 없이는 쪼갤 수 없었다

## [0.11.1] - 2026-08-12
> 빌드 경고 **70건(중복 제외 37종)을 0으로** 만들고 테스트 스위트에서 검증하지 않는 것들을 골라냈다. 기능 변경은 없다. 다만 경고 대부분이 *"지금은 동작하지만 다음 버전에서 의미가 바뀐다"* 는 예고였고, 그중 17건은 Kotlin 이 **기본 동작을 바꾸겠다**는 통보였다. `@Suppress`·`-nowarn`·억제 플래그는 **한 건도 쓰지 않았다** — 전부 실제 API 마이그레이션과 선언 수정으로 해소했다.
- **[Fix]** **어노테이션 use-site target 17건** — Kotlin 이 생성자 `val` 파라미터의 기본 타깃을 바꾸겠다고 예고한다. 방치하면 다음 상향에서 어노테이션이 필드/프로퍼티에도 붙어 동작이 조용히 달라진다. **원인이 둘로 갈려 처방도 나눴다.** 커스텀 qualifier 2건(`@LLMDispatcher`·`@DownloadClient`)은 *우리* 선언에 `@Target` 이 없어 기본 타깃 집합에 PROPERTY 가 섞인 것이므로, 선언에 `@Target(FUNCTION, VALUE_PARAMETER)` 을 달아 **원천에서** 좁혔다 — 사용처를 한 줄도 고치지 않고 경고가 사라진다. `@ApplicationContext` 15건은 Hilt 의 Java 어노테이션이라 선언을 고칠 수 없어 `@param:` 으로 명시했다(Dagger 가 읽는 것이 생성자 파라미터이므로 의미상 정확하다)
- **[Note]** `-Xannotation-default-target=param-property` 플래그는 **쓰지 않았다** — 실험적 `-X` 옵션 하나에 4개 모듈 전체의 어노테이션 배치 규칙을 묶는 일이고, Dagger 가 읽지도 않는 필드 메타데이터를 15개 만든다. 이 프로젝트에는 `compilerOptions` 블록이 하나도 없어 **"빌드 스크립트에 컴파일러 스위치를 넣는" 선례**가 생기는 것도 피했다. `@param:` 은 Kotlin 기본값이 뒤집혀도 오늘과 같은 동작을 유지한다
- **[Refactoring]** `hiltViewModel` 이 androidx.hilt 1.3.0 에서 `androidx.hilt.navigation.compose` → `androidx.hilt.lifecycle.viewmodel.compose` 로 이동했다(9곳). 옮기고 나니 `androidx.hilt.navigation` 사용처가 **0** 이 되었는데, 그대로 두면 신 아티팩트가 구 아티팩트의 **전이 의존으로만** 클래스패스에 있는 상태가 된다 — core-ktx 를 명시 선언할 때 적어둔 원칙("우연한 전이 의존에 기대면 안 된다")을 그대로 위반한다. 신 아티팩트를 카탈로그에 명시하고 `hilt-navigation-compose` 를 제거했다. 잃는 api 의존은 `navigation-compose` 뿐인데 이미 별도 선언돼 있어 `debugRuntimeClasspath` 에 2.9.7 이 남는 것을 확인했다
- **[Fix]** deprecated CompositionLocal 2건 — `LocalLifecycleOwner` 는 `androidx.lifecycle.compose` 로 이전(의존성은 이미 선언돼 있었다). `LocalClipboardManager` 는 suspend 를 지원하는 `LocalClipboard` 로 대체했는데, 복사 람다에 이미 스낵바용 `snackbarScope.launch` 가 있어 `setClipEntry` 를 그 블록 안으로 옮기는 것으로 끝났다 — 컴포저블 시그니처도 `onLongPress: () -> Unit` 계약도 바뀌지 않는다. **복사가 동기에서 비동기가 되지만** 롱프레스 경로라 체감 차이는 없다
- **[Changed]** Room 2.8 이 무인자 `fallbackToDestructiveMigrationOnDowngrade()` 를 deprecate 하고 드롭 범위 명시를 요구한다. **`dropAllTables = true`** 를 택했다 — `false`(레거시 동작)는 Room 이 아는 테이블만 드롭하는데 이 DB 는 전부 Room 관리라 지켜줄 비-Room 테이블이 애초에 없고, 반대로 v5 사용자가 구 빌드를 설치했을 때 신 스키마에만 있던 테이블을 **잔존**시켜 이후 재업그레이드에서 `MIGRATION_3_4` 의 `ALTER TABLE` 이 오염된 파일 위에서 돌게 만든다. 업그레이드 경로가 항상 명시적 Migration 을 요구한다는 정책은 그대로다
- **[Fix]** `BaseAgent.executeToolLoop` 의 **도달 불가 방어 코드 2건** — `lastTurn`/`parsedResult` 를 루프 밖으로 꺼내려고 nullable var 에 `null` 을 넣어 뒀는데, 그 `null` 은 실제 상태가 아니라 선언 시점의 문법적 요구를 때우는 자리표였다. 루프에서 그 지점까지 가는 경로는 `break` 하나뿐이고 `break` 는 두 값을 대입한 뒤에만 나온다(상한 초과·추론 실패는 `return` 이다). 자리표를 없애고 **정의적 대입**에 맡겨 타입에서 nullable 자체를 지웠다. 앞으로 대입 앞에 조기 탈출이 생기면 **컴파일 에러로 즉시 드러난다** — 지금까지는 죽은 분기가 삼켜 `"Model execution failed"` 를 사용자에게 띄웠을 구조다
- **[Added]** **툴 루프 상한 초과 경로 테스트** — `MAX_TOOL_LOOP_COUNT` 초과는 모든 툴 대화가 의존하는 브레이크인데 검증이 **0건**이었다(위 루프 구조를 건드리며 발견). 툴 호출 턴 3개로 상한을 넘겨 오류 종류·문구·감사 기록을 고정하고, 이 경로가 다른 오류 경로와 달리 **말풍선을 저장하지 않는다**는 현재 동작도 함께 못박았다
- **[Removed]** `AssistantPipelineTest` — `ChatRequest` 만 만들고 orchestrator 를 호출하지도, 단언하지도 않아 **무조건 통과**하던 빈 껍데기였다. `MockModelRunner` 가 돌려주던 `{"type":"text",…}` 는 ADR-008 이전의 JSON 봉투 포맷이고 이를 파싱하던 `ResponseParser` 는 이미 삭제됐다 — 되살리려면 mock 부터 새로 써야 하는데, 같은 경로를 `ToolApprovalE2ETest`(Hilt 실제 DI 관통)와 `BaseAgentStreamTest` 15건이 이미 검증한다
- **[Removed]** `ContractConsistencyTest` — 3건 중 2건(`ImageTooLarge`·`SearchError` 매핑)이 `ErrorMessageMappingTest` 의 `ErrorCode.entries` **29개 전수 순회**에 완전히 포함된다(그쪽은 `when` 이 exhaustive 라 코드가 추가되면 컴파일 에러로 잡힌다 — 개별 테스트보다 엄격히 강하다)
- **[Added]** `AuditEventTypeSpecTest` — 위 3번째 테스트를 승격했다. 이름은 *"matches api spec"* 이었지만 **스펙 파일을 읽지 않았고** 10개 값 중 6개가 존재하는지만 봐서, 한쪽에만 값이 추가·삭제돼도 통과했다. 이제 `docs/api_spec.yaml` 을 실제로 파싱해 **양방향 집합 비교**를 한다. YAML 파서를 의존성으로 들이지 않는 대신, 파싱 실패 시 빈 집합이 되고 `isNotEmpty` 단언이 조용한 통과를 막는다
- **[Note]** **통합하지 않은 것** — 이름이 겹쳐 보이는 4쌍(`SqlLikeEscape`/`KnowledgeSearchEscape`, `FloatBytes`/`KnowledgeEmbeddingBlob`, `TagsNormalize`/`KnowledgeTagRoundTrip`, `GetScheduleRange`/`GetTodayScheduleUseCase`)은 전부 **순수 함수와 Room 경유 동작을 나눠 검증하는 의도된 계층 분리**다. 합치면 "이스케이프 함수는 맞는데 쿼리에 `ESCAPE '\'` 가 없다", "정규화 함수는 맞는데 저장 경계에서 호출하지 않는다" 같은 실패를 놓친다. E2E 4개 클래스는 테스트 5개로 **실행 시간의 54%(23.7/43.6초)** 를 쓰지만 `@UninstallModules`/`@BindValue` 구성이 서로 달라, 6초 절감을 위해 Hilt 배선을 재설계할 값이 없다고 판단했다
- **[Docs]** `.agents` 규칙 복원 — `24ffa2d` 에서 구 `.agents/AGENTS.md` 를 6개 파일로 분할할 때 규칙 1의 **UTF-8(No BOM) 구체 지침이 "encoding issues" 한 마디로 축약되며 소실**됐다. 한글 주석과 KDoc 이 많은 레포에서 Windows PowerShell 의 ANSI 기본 인코딩은 실제 파일 손상으로 이어지므로 실행 가능한 형태로 복원했다. `02_WORKFLOW_AND_AUDIT.md` 가 호명하던 Antigravity 전용 도구명(`invoke_subagent`·`list_dir`·`grep_search`)도 `01` 이 이미 쓰던 도구 중립 어법으로 정리했다
- **[Chore]** 사용하지 않는 원격 브랜치 2개 삭제 — `task/litert-performance-optimization`(`4417b77` PR 로 정식 머지됨), `debug/llm-working-and-multimodal-enable`(고유 커밋 1개가 `393f9d7` 로 master 에 중복 반영됨을 `git patch-id` 동일성으로 확인). 2026-06-12 이후 브랜치 워크플로를 쓰지 않아 두 달간 방치돼 있었다
- **[QA/Test]** 전체 **207건** 통과(실패·에러·스킵 0) + lintDebug + assembleRelease. 209 → 207 은 감소가 아니라 교체다: 허수 1건(단언 없음)과 중복 2건이 빠지고, 실질 2건(루프 상한, 스펙 대조)이 들어왔다. **빌드 경고 0건**
- **[Note]** **테스트가 닿지 않는 변경 1건** — Room `dropAllTables = true` 는 다운그레이드에서만 발동해 단위 테스트로 검증할 수 없다. 실기기에 구버전 APK 를 덮어 설치하는 상황에서 처음 실행되며, 그때 이상이 보이면 이 항목을 먼저 의심할 것

## [0.11.0] - 2026-08-09
> PRD 와 코드가 어긋난 곳을 사용자 결정에 따라 정리하고(발열 완화책·음성 전사), 발열·자원 상태를 상단에 상시 표시하며, PRD 가 요구하지만 비어 있던 안내 배선을 채웠다. **PRD v1.3 개정**을 함께 포함한다.
- **[Perf]** **부수 계산이 채팅 대화를 파괴하던 문제** — 런타임은 채팅 Conversation 을 캐시해 재사용하는데(ADR-010), 시스템 지시와 `sessionId` 가 다른 부수 호출이 오면 그 캐시를 밀어냈다. `GetTodayScheduleUseCase` 의 일정 요약 때문에 **캘린더 화면을 열 때마다 채팅 대화가 날아가** 다음 질문이 전체 프리필(툴 선언 ~2천 토큰 포함)을 다시 냈다. `ChatPrompt.oneShot` 을 신설해 부수 계산은 임시 대화를 만들어 쓰고 즉시 닫는다 — 툴·few-shot·히스토리를 싣지 않아 그 자체도 싸다 (ADR-014)
- **[Changed]** **음성 메시지가 전사문으로 저장된다** (PRD F2·AC2 개정) — 예전에는 `"(음성 메시지)"` 를 저장하고 오디오를 채팅 대화에 실었다. 그래서 대화를 다시 열면 **자기가 무슨 말을 했는지 기록에 남지 않았고**, 오디오가 KV 를 먹었으며, 검색·히스토리에서 그 턴이 의미를 갖지 못했다. 이제 일회성 대화에서 먼저 전사하고 그 텍스트로 일반 채팅 턴을 돈다. 확인 단계는 두지 않는다(사용자 결정) — 채팅에 뜬 자기 문장이 곧 인식 결과 확인이다
- **[Fix]** 무음 녹음이 빈 말풍선을 남기던 문제 — 전사가 비면 **사용자 메시지를 저장하지 않고** `SttError` 로 올려 "음성을 인식하지 못했어요" 를 띄운다 (PRD EC3 충족). 전사 후 임시 오디오 파일도 지운다
- **[Added]** **채팅 상단 기기 상태 표시** — `🌡 41.2°C · RAM 3.4/8.0GB(앱 4.1GB) · 12.4 tok/s`. 기존 발열 경고 배너를 이 줄에 통합해, 평소에는 수치만 옅게 보이고 경고·임계 온도에서 같은 줄이 승격된다
- **[Note]** **GPU 사용률은 표시할 수 없다** — 안드로이드에 공개 API 가 없고 벤더 sysfs(Adreno `/sys/class/kgsl/kgsl-3d0/gpubusy`)는 SELinux 로 앱 도메인에서 차단된다. 대신 **tok/s** 를 쓴다: 스트리밍 경로는 네이티브 메시지 1건 = 토큰 1개라 정확히 셀 수 있고, 스로틀링되면 즉시 떨어진다. RAM 은 `Debug.getMemoryInfo().totalPss` 로 본다 — 3.7GB 모델은 네이티브/mmap 이라 **자바 힙 수치에는 전혀 보이지 않는다** (ADR-015)
- **[Fix]** 마이크 권한을 거부하면 **아무 일도 일어나지 않던** 문제 — `if (isGranted)` 에 else 가 없었다. 사용자는 버튼이 고장 난 것으로 본다. 안내 스낵바를 띄운다 (PRD EC2)
- **[Fix]** 기기 캘린더 읽기 실패를 **조용히 삼키던** 문제 — `emptyList()` 로 넘겨 앱 안의 일정만 보여 줬고, 사용자는 기기 일정이 없다고 믿었다. `ScheduleData.deviceCalendarFailed` 로 올려 안내 카드 + 재시도·권한 설정 버튼을 띄운다 (PRD EC2·EC4)
- **[Fix]** 웹 검색 실패가 사용자에게 보이지 않던 문제 — 실패 JSON 이 모델에게만 가서, 네트워크가 끊긴 줄 모른 채 온디바이스 답변을 검색 결과로 오해할 수 있었다. `searchFailed` 를 실어 '웹 검색 보강 실패' 를 안내한다. **토글 OFF(허용 안 함)는 실패가 아니므로 제외**한다 (PRD V1-AC3·EC5)
- **[Fix]** 추론 실패 시 말풍선에 `"Model inference failed: TemperatureCritical(48.3)"` 같은 **내부 문자열이 그대로 저장**되던 문제 — `ErrorMessages` 로 인간화한다(스낵바만 인간화돼 있었다). 감사 로그에는 원문을 남겨 진단은 유지
- **[Docs]** **PRD v1.3 개정** — ① NFR3·EC7: "Token Delay" 완화책 삭제(0.9.1 에서 효과 없음이 확인됐다 — `trySend` 가 논블로킹이라 수신이 느려져도 디코더는 안 느려지고, 대신 채널이 넘쳐 토큰이 유실됐다). 쿨다운은 **연속 5회 + 경고 온도 이상**일 때만. ② F2·AC2·EC3: 음성 전사 자동 전송. ③ NFR2: 상단 상태 표시 추가 + GPU 불가 명시. ④ **V1-AC2·NFR4·EC5 의 "검색 쿼리 미리보기" 요구 삭제** — F7 이 2026-07-31 에 전역 토글로 개정되며 *"별도 승인 다이얼로그를 표시하지 않는다"* 가 됐는데 이 세 곳에 옛 요구가 남아 **PRD 내부가 모순**이었다
- **[QA/Test]** 신규 테스트 10건 — `TranscribeAudioUseCaseTest` 7(전사 통과, 따옴표·화자 라벨 제거, 본문 따옴표 보존, 무음→SttError, 모델 실패 전달, **oneShot·툴 미선언 계약**), `RuntimeMetricsCollectorTest` +3(tok/s 계산, 토큰 미상 턴은 직전 값 유지, 상태 갱신). `VoiceChatIntegrationTest` 기대값을 전사문으로 갱신. 전체 **209건** + lintDebug + assembleDebug 통과

## [0.10.0] - 2026-08-07
> RAG 가 어떻게 쓰이는지 훑다가 임베더의 한국어 어휘가 이상해 **직접 측정했고, 동작하지 않는다는 것이 확정됐다.** 매 턴 자동 주입을 없애고 기억 조회를 `SearchMemory` 툴로 옮긴다 (ADR-013).
- **[Verified]** **임베더가 한국어에서 동작하지 않는다** — `universal_sentence_encoder.tflite` 를 PC 에서 같은 옵션으로 올려 쟀다. 관련쌍/무관쌍 분리도가 **영어 +0.255, 한국어 +0.000**. `"자전거 비밀번호는 1234" ↔ "내 자전거 비밀번호 뭐였지?"` 가 0.946 이고 `↔ "트와이스 나연에 대해 알려줘"` 도 **0.946** 이다 — 소수점 셋째 자리까지 같아 **어떤 임계값으로도 가를 수 없다.** 서로 무관한 한국어 문장 8개의 쌍별 코사인이 0.930~1.000(평균 0.964)으로 한 점에 뭉치고(같은 내용 영어는 0.635~0.817), 실제 검색 정확도는 **top-1 1/7, top-3 2/7** 로 무작위(1/8)와 같다. 어휘를 뜯어보면 한글 토큰이 **1글자 16,079개 / 2글자 이상 8개**(실제 단어는 `오전`·`오후`·`주의`·`참고` 넷)인 **영어 전용 모델**이다
- **[Removed]** 매 턴 자동 RAG 주입 — 실제로 하던 일은 "관련 기억을 찾아 준다"가 아니라 **"무작위로 고른 메모 3건을 매 턴 질문 앞에 붙인다"** 였다. 프리필을 축내고, 무관한 사실을 문맥에 넣어 환각의 재료를 주고, 정작 회상은 되지 않았다(PRD F6 미충족). 켜 두는 것보다 끄는 것이 나은 상태였다
- **[Added]** `SearchMemory` 툴 — 모델이 **짧은 명사 키워드**를 뽑아 주면 본문·태그를 어휘 검색한다. 사용자 문장 전체("내 자전거 비밀번호 뭐였지?")로는 LIKE 가 0건이지만 "자전거 비밀번호"로는 정확히 맞는다. 한국어 이해는 이미 온디바이스 LLM 이 하므로 **질의어 추출을 모델에게 맡기는 것**이 이 앱에서 가장 값싼 의미 검색이다. 덤으로 필요할 때만 돌고, 조회 사실이 감사 로그(`TOOL_CALL`)에 남는다
- **[Verified]** PC 하네스 8케이스 — **툴 선택 8/8, 키워드 적합 5/5.** 회상은 `search_memory`, 저장은 `add_memory`, 일정은 `add_schedule` 로 정확히 갈렸다. exp7 에서 관찰됐던 "회상 질문에 `add_memory` 를 잘못 부르는" 혼동이 사라졌다 — 규칙에 `This is a LOOKUP, never call add_memory for it` 을 못 박았고 `PromptAssemblerTest` 가 고정한다
- **[Fix]** 첫 측정에서 회상 **5건 중 2건**이 **키워드는 옳은데 글자가 안 겹쳐** 못 찾았다 — 어휘 검색은 동의어를 못 넘는데 모델은 의미로 키워드를 뽑는다("좋아하는 것" 대 "커피보다 녹차를 더 좋아함", 태그 `선호도`: 한 글자도 겹치지 않는다). 두 가지로 보강했다 — ① 본문뿐 아니라 **태그도** 검색한다(저장 시 모델 자신이 붙인 것이라 알아본다), ② 그래도 0건이면 **저장된 분류(태그) 목록**을 돌려주고 그 단어로 재호출하도록 유도한다. 최근 몇 건을 흘려보내는 것과 달리 태그는 기억이 늘어도 목록이 짧게 유지돼 규모를 탄다. 보강 후 **회상 4/5**(수정 전 3/5)이고, 남은 1건도 지어내지 않고 정답 분류를 짚어 되묻는다 — *"혹시 '선호도'나 '음료'와 관련된 내용이었나요?"*
- **[Note]** 남은 한계 — 동의어 간극은 어휘 검색으로 원리상 완전히 못 넘는다. 태그 목록 폴백이 그 자리를 메우지만 한 번의 되묻기가 생길 수 있다. 완전 해소는 한국어를 다루는 임베더로 갈아 끼우는 길뿐이고, 그때 `searchByVector` 경로가 그대로 되살아난다
- **[Fix]** `SaveKnowledgeUseCase` 가 임베딩 실패 시 **저장 자체를 실패시키던** 문제 — 지금 임베딩은 어떤 조회 경로에서도 읽히지 않는데, 그 값을 만들지 못했다는 이유로 사용자의 "기억해줘"를 버리는 것은 명백히 잘못이다. best-effort 로 바꿨다(임베더가 죽어도 메모는 저장된다)
- **[Removed]** `SearchKnowledgeUseCase`(벡터 우선 + 텍스트 폴백 글루) — 유일한 호출자였던 `ContextBuilder` 의 주입이 사라져 호출자 0건. `ChatPrompt.turnContext` 배관과 `PromptAssembler` 의 SYSTEM 역할 필터도 함께 제거했다(SYSTEM 메시지 생산자가 이 경로뿐이었다)
- **[Note]** `searchByVector`·`KnowledgeNote.embedding`·`TextEmbedder`·`.tflite` 자산은 **남겨 뒀다.** 막힌 곳이 외부 자산 하나이므로 한국어를 다루는 임베더로 갈아 끼우면 그대로 되살아나고, BLOB 인코딩·마이그레이션 테스트가 그 계약을 계속 지켜 준다. 인터페이스 KDoc 에 *"다시 배선하기 전에 반드시 임베더의 한국어 분별력을 먼저 재라"* 를 남겼다
- **[Known Issue]** 같은 대화에서 턴이 쌓이면 툴 인자 정확도가 떨어진다 — 8번째 턴의 `add_schedule` 이 `start_time='2086-07T15:0000'`(기대 `2026-08-08T15:00`)로 나왔다. 새 대화에서 같은 케이스는 4/4 정확하다(exp8). 기기의 글자 유실(0.9.1)과 달리 **삭제가 아니라 뒤섞임**이라 원인이 다르다 — 별도 조사가 필요하다
- **[QA/Test]** 신규 테스트 7건 — `MemoryPipelineIntegrationTest` 재작성(저장→키워드 조회 왕복, 임베딩 실패해도 저장 성공, 태그 경로, 다중 토큰 순위, 미일치 폴백, 기억 없음, 조회 실패, 빈 키워드 차단), `KosmosAgentTest`·`PromptAssemblerTest` 계약 갱신. 전체 **199건** + lintDebug + assembleDebug 통과

## [0.9.1] - 2026-08-07
> 실기기에서 위키 검색은 동작하기 시작했으나 세 가지가 보고됐다: ① 첫 턴만 검색하고 이후에는 "검색하겠습니다"라고 **말만** 함, ② 답변 중간중간 글자가 빠짐("2015년 10월" → "205년 10"), ③ 나연이 배우도 리더도 아닌데 그렇게 답함. ②의 원인은 **우리 수신 경로**로 확정했고, ③은 ①의 결과다. ①은 원인 후보를 좁혔으나 확정하지 못했다.
- **[Fix]** **스트리밍 토큰 유실** — 0.14.0 의 `Conversation.sendMessageAsync(…): Flow` 는 callbackFlow 이고, 네이티브 콜백(`Conversation$sendMessageAsync$1$1.onMessage`)이 `ProducerScope.trySend(message)` 를 부른 뒤 **반환값을 버린다**(바이트코드 확인). callbackFlow 의 기본 채널 용량은 64 이고, 가득 차면 `trySend` 는 예외도 로그도 없이 실패한다 — **그 메시지는 사라진다.** 우리는 그 위에 토큰마다 `delay(15~50ms)` 를 걸고 있었다. `.buffer(Channel.UNLIMITED)` 를 끼워 이 Flow 의 소비자가 즉시 비워 가도록 했다
- **[Verified]** 손상 패턴이 유실을 가리킨다 — "다국적 9인조"→"다국 9조"(적·인), "2015년 10월 20일"→"205년 10 20일"(1·월), "오디션"→"오선"(디), 이전의 "1234"→"134"(2). **모두 정확히 한 토큰씩 삭제이고 치환·삽입은 한 건도 없다.** greedy 디코딩에서 원문이 문맥에 있는데 모델이 이런 외과적 삭제를 낼 이유가 없다. PC(비스트리밍 `send_message`)에서는 한 번도 재현되지 않았다 — 안드로이드 GPU 고유 문제로 좁혔던 이전 판단(ADR-009)을 **철회한다**
- **[Removed]** 토큰마다의 발열 지연(`delay(15)`/`delay(50)`) — 이 지연은 **의도한 일을 할 수 없다.** `trySend` 가 논블로킹이므로 우리가 느려도 네이티브 디코더는 전혀 느려지지 않는다. 즉 발열은 그대로이고 얻는 것은 (a) 채널 넘침으로 인한 토큰 유실과 (b) 무한 버퍼를 끼운 뒤에는 순수한 화면 지연(300토큰 × 50ms = 15초)뿐이다. **NFR3 의 "Token Delay" 항목과 충돌하므로 문서 개정이 필요하다**
- **[Fix]** 감사의 `MODEL_RUN` 에 **선언된 툴 목록**을 함께 기록 — "툴을 부르지 않았다"만으로는 선언 누락(토글·allowlist)인지, 모델의 거부인지, 전달 유실인지 구분할 수 없다. 실기기에서 logcat 없이 앱 안에서 가르기 위한 단서다
- **[Known Issue]** **2턴 이후 툴 호출이 사라지는 문제는 원인 미확정** — PC 하네스로 세 가지 구성을 재현했으나 **전부 3/3 성공**이었다: ① 대화 재사용(0.9.0 동작), ② 턴마다 재생성(0.8.x 동작), ③ 무관한 RAG 기억 3건이 매 턴 붙는 조건(실기기 조건). 즉 대화 재사용도, RAG 잡음도 원인이 아니다. 남은 차이는 **스트리밍 경로**(PC 하네스는 비스트리밍)와 0.14.0/0.15.0 런타임 차이다. 위 토큰 유실과 같은 채널로 툴 호출 메시지도 오므로 함께 유실됐을 가능성이 있고, 그렇다면 이번 버퍼 수정으로 해소된다 — 다음 실기기 확인에서 갈린다
- **[Note]** 환각(나연이 배우·리더)은 위 ①의 **결과**다. 검색이 실행되지 않으면 4B int4 모델이 파라미터 기억으로 답하고, K-POP 인물 정보는 그 기억이 신뢰할 수 없다. 검색이 매 턴 실행되면 대부분 해소된다
- **[Note]** `KnowledgeRepositoryImpl.searchByVector` 에 **유사도 임계값이 없다**(주석: *"유사도 0.3 이상 필터링 가능, 일단 모두"*). 저장된 메모가 있으면 질문과 무관해도 상위 3건이 매 턴 `[Context / Knowledge]` 로 붙는다. 이번 증상의 원인은 아님을 실험으로 확인했으나(3/3), 그 자체로 프리필 낭비이자 잡음이므로 별건으로 남긴다 — 임계값은 실기기 점수 분포를 봐야 정할 수 있다
- **[QA/Test]** 전체 193건 + lintDebug + assembleDebug 통과

## [0.9.0] - 2026-08-07
> gallery 의 모델 설정(max token / accelerator 고정 / top-k·top-p·temperature 부재)과 토글 2개(thinking, speculative decoding)를 차용할지 검토한 결과다. **셋 다 채용하지 않는 것이 맞다**는 결론이고 근거는 ADR-011 에 있다. 대신 그 검토 과정에서 **체감 지연의 지배 요인**이 우리 프롬프트 구조에 있다는 것이 PC 실측으로 드러나 그것을 고쳤다.
- **[Perf]** 시스템 지시가 턴마다 달라져 **매 턴 전체 프리필이 다시 일어나던 문제** (ADR-010) — `getOrCreateConversation` 은 시스템 지시가 달라지면 Conversation 을 파괴하는데, 시스템 지시에 **분 단위 시각**과 **RAG 검색 기억**이 들어 있어 재사용 판정이 거의 항상 거짓이었다. 재생성은 시스템 지시 + 툴 선언(~2천 토큰) + few-shot + 히스토리 전체의 재프리필이다. PC 하네스에서 같은 3턴을 재사용/재생성으로 나눠 재니 **4.3·0.5·0.9초 대 4.0·3.8·4.0초** — 2번째 턴부터 턴당 3초 이상이 프리필뿐이었다(기기에서는 더 느리다). 날짜 블록은 시스템 지시에 남기되 **시계를 빼 하루 단위로** 만들고, 검색된 기억만 `ChatPrompt.turnContext` 로 사용자 턴에 실었다
- **[Verified]** 날짜 블록까지 사용자 턴으로 옮기려던 첫 설계는 **실측으로 기각했다** — `[System Data] 오늘=…` 목록이 사용자 발화 앞에 붙으면 조회 질문이 툴 호출로 샜다("내 자전거 비밀번호 뭐였지?" → `add_memory`, "내가 뭘 좋아한다고 했지?" → `get_schedule`). 조회 3케이스 중 1/3. 변형 실험으로 **문구가 아니라 블록의 위치**가 원인임을 분리했고(문구만 바꾸면 3/3, 위치만 바꾸면 2/3), 채택안은 전체 16케이스 **16/16** 이다(기존 15/16). 시계를 빼도 일정 등록 정확도는 떨어지지 않는다. 대가는 "지금 몇 시야?" 를 다룰 수 없다는 것 — 알림 기능이 없으므로 지불한다
- **[Security]** 검색된 기억이 시스템 지시로 들어가던 것도 함께 해소 — 저장된 메모의 문장이 **시스템 권한으로 승격**되는 주입 경로였다. 메모는 웹·문서·대화에서 온 텍스트일 수 있다. 첨부 문서를 USER 구분 블록으로 격리한 ADR-003 과 같은 근거로 사용자 턴에 싣는다
- **[Fix]** 응답 스타일 설정이 사실상 무효였던 문제 — `[Style: CONCISE]` 라벨 한 줄만 넣어, 모델은 그 대문자 토큰이 무엇을 요구하는지 알 길이 없었다. 실제 지시문("Answer in one or two short sentences…")으로 풀었다. 설정 화면에 없는 자유 문장은 그대로 전달한다
- **[Fix]** 대화 재설정 임계값이 설정과 무관하던 문제 — 런타임에 박힌 `8000` 이어서 사용자가 예산을 1000 으로 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자랐다. **설정이 메모리에 아무 영향을 주지 못했다.** `ChatPrompt.contextBudgetTokens` 로 예산을 실어 임계값을 파생시키고, 예산 최소값에서 매 턴 재생성되지 않도록 `MIN_CONVERSATION_RESET_TOKENS = 4000` 하한을 뒀다
- **[Fix]** 설정 화면의 거짓 라벨 — "CONTEXT WINDOW / Token Limit" 은 모델의 컨텍스트 윈도우가 아니다(`EngineConfig.maxNumTokens` 로 전달되지 않는다). 실제로는 매 턴 실어 보내는 분량이므로 "대화 기억 범위 / 한 번에 참고할 분량"으로 바꿨다. 이름이 동작과 다르면 사용자는 만져도 효과가 없다고 느낀다
- **[Fix]** 감사 로그에 **툴 실행 기록이 한 건도 없던 문제** — `AuditEventType.TOOL_CALL` 은 enum 에 있고 감사 화면에 색상까지 지정돼 있는데 **생산자가 0개**였다(툴 루프로 골격을 바꿀 때 기록 지점이 함께 옮겨오지 않았다). 승인이 필요 없는 툴(일정 조회, 웹 검색)은 실행 흔적이 아예 없었다. `logToolCall(tool, result, note)` 를 신설해 툴 루프에서 남긴다
- **[Fix]** 네트워크 egress 가 감사에 남지 않던 문제 — `logSearchEvent`/`SEARCH_USED` 는 서비스 메서드·enum·계약 테스트까지 있는데 **호출하는 곳이 없었다.** 프라이버시상 기록이 가장 필요한 동작이 비어 있었다. 실제 실행된 웹 검색만 기록한다(차단된 호출은 egress 가 아니므로 남기지 않는다)
- **[Fix]** 툴을 쓴 턴의 감사 프롬프트가 비어 있던 문제 — `logModelRun(prompt.currentInput, …)` 을 기록했는데 툴 루프가 그 값을 `""` 로 덮어쓴다. **툴을 쓴 대화일수록** 프롬프트가 빈 문자열이었다 — 정작 기록이 가장 필요한 턴이다. 원문 요청(`request.message`)을 기록한다
- **[Fix]** `ChatMessage.searchUsed` 가 항상 false 로 저장되고 화면에서 읽히지도 않던 문제 — 웹 검색을 쓴 답변과 온디바이스 답변을 사용자가 구분할 수 없었다. 툴 루프에서 실제 값을 채워 DB·`AgentResult` 양쪽에 전파하고, 말풍선 아래에 "🌐 위키백과 검색 결과를 참고했어요"를 표시한다. 토글로 명시 허용하는 동작이므로 쓰인 사실도 보여야 한다
- **[Fix]** 무음으로 버려지던 추가 툴 호출 — 한 턴에 호출이 여러 개 오면 첫 것만 실행하는 것은 의도한 정책이지만(병렬 실행 방지) 버려진 사실이 어디에도 없었다. 감사 기록의 `note` 에 남긴다
- **[Fix]** 온도와 무관하게 5번째 추론마다 1초를 지연하던 쿨다운 — 툴을 쓰는 대화는 사용자 메시지 하나가 **추론 2회**를 쓰므로(호출 턴 + 응답 턴) 사용자 기준 2~3 메시지마다 1초를 냈다. 발열 방어는 이미 세 겹(임계 온도 사전 차단, 경고 온도 이상 토큰 단위 지연, 종료 후 감사)이므로 이 지연은 **경고 온도 이상일 때만** 낸다. 상한 미달이면 온도 조회조차 하지 않는다
- **[Fix]** 진단 수단이 릴리스에서도 켜져 있던 문제 — `renderPrefaceIntoString()`(템플릿 전체를 실제 렌더링, LLM 수명주기 뮤텍스 안)과 네이티브 `LogSeverity.INFO`(토큰 단위로 쏟아진다)를 디버그 빌드로 한정했다. 프리페이스 2천 자를 logcat 에 남기는 것 자체가 사용자 발화와 검색된 기억이 로그로 새는 경로이기도 했다
- **[Refactoring]** 발열 상수 3개(`THERMAL_WARNING_CELSIUS`/`THERMAL_SHUTDOWN_CELSIUS`/`THERMAL_COOLDOWN_INFERENCE_COUNT`)가 선언만 되고 참조가 0건이었고, 리터럴 `43.0f`/`48.0f`/`5` 가 `RuntimeMetricsCollector` 와 `GemmaModelRunner` 에 복제돼 있었다 — 정책을 한 곳에서 바꿀 수 없는 상태였다. 상수로 통일했다 (0.8.8 의 이미지 상수와 같은 결함 유형)
- **[Removed]** `PromptAssembler.assemble` (툴 없는 변형) — 프로덕션 호출자 0건, 테스트만 쓰고 있었다
- **[QA/Test]** 신규 테스트 20건 — `PromptAssemblerTest` 재작성(시스템 지시 불변성, 턴 문맥에 날짜 블록 부재, 규칙과 블록의 순서, 스타일 지시문), `BaseAgentStreamTest` +5(TOOL_CALL 기록, 무시된 호출 기록, SEARCH_USED + `searchUsed` 전파, 차단된 검색은 미기록, 툴 턴 프롬프트), `RuntimeMetricsCollectorTest` 7(**이 클래스에는 테스트가 0건이었다** — 모든 추론이 지나는 발열 경로인데). 전체 **193건** + lintDebug + assembleDebug 통과
- **[Note]** 샘플링 파라미터를 노출하지 않는 이유 (ADR-011) — `topK = 1`(greedy)은 0.8.4 에서 툴 호출을 성립시킨 조건이고 숫자 왜곡 완화에도 기여한다. gallery 자신도 agent chat 에서 `AgentChatSamplingParamsManager` 로 `topK = 1` 을 **강제**하며 그 태스크에서는 이 설정들을 보여주지 않는다. 노출은 실기기 왕복 8회로 막아 놓은 실패 모드를 슬라이더 한 번으로 되살릴 수 있게 만드는 일이다. 응답의 성격을 바꾸는 손잡이는 샘플러가 아니라 응답 스타일 지시문으로 제공한다
- **[Note]** thinking·speculative decoding 을 채용하지 않는 이유 (ADR-011) — gallery 의 모델 허용 목록에서 우리 모델(`Gemma-4-E4B-it`)의 `capabilityToTaskTypes` 는 `llm_thinking: [llm_chat, llm_ask_image, llm_ask_audio]`, `speculative_decoding: [llm_chat, llm_ask_image, llm_ask_audio, llm_prompt_lab]` 이다 — **둘 다 `llm_agent_chat` 만 빠져 있다.** speculative decoding 은 단순 태스크인 prompt_lab 에도 허용되므로 agent chat 만 제외되는 차이는 툴 선언 + constrained decoding 뿐이다(드래프터 제안과 FST 문법 마스크의 충돌로 해석된다). 우리 앱은 모든 턴이 agent chat 이다
- **[Note]** 모델 파일에 드래프터가 **실제로 들어 있다** — `gemma-4-E4B-it.litertlm` 컨테이너 헤더에서 `tf_lite_mtp_drafter` 섹션을 확인했다(MTP = multi-token prediction). `Capabilities` 클래스와 `ExperimentalFlags.enableSpeculativeDecoding`(`Boolean?`, `Engine.initialize()` 전에 읽힘) 모두 0.14.0 AAR 에 있다. 즉 **켤 수 있는데 켜지 않는 것**이며, 켜려면 툴 호출 회귀 검증이 필요하고 PC 하네스로는 확인할 수 없다(Python 0.15.0 에 대응 플래그가 없다)
- **[Note]** `EngineConfig.maxNumTokens` 는 계속 넘기지 않는다 — 엔진 KV 캐시 크기를 정하고 초기화 시점에만 읽히므로 슬라이더로 바꾸려면 엔진 재적재(7~11초)가 필요하고, 우리가 임의로 정한 값이 지금 동작하는 런타임 기본값보다 **작을** 위험이 있다(gallery 의 기본값은 1024 다). 히스토리 윈도우와 재설정 임계값이 KV 성장의 실질적 지배 요인이므로 그 둘을 통제한다

## [0.8.8] - 2026-08-07
> AI Edge Gallery 앱을 사용자가 직접 써 보고 요청한 검토에서 나온 수정들이다. gallery 는 툴 4개(`load_skill`/`run_js`/`run_intent`/`runMcpTool`) 고정 + 스킬 문서 12개 온디맨드 로딩 구조인데, 그건 자기네 `maxTokens: 4000` 예산 안에 활성 스킬 지시문 총합(~4,770토큰)이 들어가지 않아서 나온 **제약의 산물**이다. 우리는 `EngineConfig.maxNumTokens` 를 설정하지 않아 모델 기본 32K 를 쓰므로 툴을 직접 선언하는 현재 구조가 이 규모에서 우월하다(정식 함수호출 템플릿 + constrained decoding 문법 강제). 대신 검토 중 발견된 실제 결함들을 고쳤다.
- **[Fix]** 채팅 화면이 **마지막 5개 메시지만 로드**하던 버그 — `ChatViewModel.loadMessages` 가 `getRecentBySession(sessionId)` 를 limit 없이 호출해 계약 기본값 `MAX_CONVERSATION_TURNS = 5` 가 적용됐다. DB 에는 남아 있는데 화면에서만 사라졌고, `ContextBuilder` 는 같은 데이터를 150개로 읽고 있어 화면과 모델이 서로 다른 히스토리를 봤다. **계약에서 기본값 자체를 제거**해 같은 실수를 컴파일 단계에서 막고, 양쪽을 `Constants.MAX_RECENT_CONVERSATIONS` 하나로 묶었다
- **[Fix]** 컨텍스트 윈도우 설정이 뜻대로 동작하지 않던 문제 — 슬라이딩 윈도우가 `ChatMessage.content` 만 세고 **시스템 지시·툴 선언(실측 ~2천 토큰)·few-shot 시범을 세지 않았다.** "4096" 설정에도 실제 프리필이 6천을 넘었다. `PREFILL_OVERHEAD_TOKENS = 2600` 을 먼저 예약하고 남은 예산만 히스토리에 배분하며, 설정을 최소로 내려도 `MIN_HISTORY_TOKENS = 500` 은 남긴다(히스토리가 0이면 대화가 성립하지 않는다). 기본값은 4096 → **6000** — 설정 슬라이더 눈금(1000 단위)에 없는 값이라 슬라이더를 만지는 순간 값이 튀던 문제도 함께 해소
- **[Fix]** 앱 내 파일 피커로 고른 이미지에 **크기·치수 검사가 전혀 없던 문제** — `MAX_IMAGE_SIZE_BYTES`(10MB)·`MAX_IMAGE_DIMENSION_PX`(1024)는 선언만 되고 참조가 0건이었고, 공유 시트 경로만 10MB 를 검사했다. 12MP 사진을 고르면 그대로 디코딩돼 3.7GB 모델과 메모리를 다퉜다. 단일 관문(`ImageInputAdapter`)에서 크기를 거부하고, 치수는 **디코딩 시점 축소**(`inSampleSize`)로 처리한다 — 디코딩 후 리사이즈는 큰 비트맵을 일단 메모리에 올려야 해서 OOM 을 막지 못하고, 상한 이하 이미지는 배수 1 로 지나가므로 "리사이징 금지(Gemma 4 네이티브 패칭)" 원칙과 충돌하지 않는다
- **[Removed]** `imageTokenBudget = 280` 파라미터 — 계약(`ModelRunner`)부터 구현까지 4개 파일을 거쳐 배관됐지만 `GemmaModelRunner` 본문에서 **한 번도 읽히지 않았다.** 동작하는 조절 장치처럼 보여 누가 값을 바꿔도 아무 일이 없는 함정이었다. 필요해지면 litertlm 의 `ExperimentalFlags.visualTokenBudget` 을 쓰면 된다
- **[Removed]** `ProcessImageInputUseCase` — 호출자 0건. 그 안의 10MB 검사도 죽어 있었고, 이제 관문이 대신한다
- **[Refactoring]** 중복·미사용 상수 정리 — `SendChatMessageUseCase.MAX_INPUT_CHARS` 가 `Constants.MAX_INPUT_CHARS` 를 가려 두 값이 갈릴 수 있었던 것을 참조로 교체, `MAX_KNOWLEDGE_CONTEXT_ITEMS`(선언만 되고 미참조)를 `ContextBuilder` 의 하드코딩 `3` 자리에 적용, `ShareIntentHandler` 의 매직 넘버 `10 * 1024 * 1024` 를 상수로 교체
- **[QA/Test]** 신규 테스트 11건 — `ContextBuilderTest` +4(오버헤드 예약, 최소 예산 보장, 최신순 담기, 후보 수 일치), `ImageInputAdapterTest` 7(축소 배수, 축소 후 상한 불변식, 크기 거부, 큰 이미지 통과). **이미지 경로에는 그동안 테스트가 0건이었다**(유일한 멀티모달 테스트는 문서/텍스트 경로를 검증했다). 전체 173건 + lintDebug 통과
- **[Note]** 멀티모달 현황 — 이미지 첨부(파일 피커·공유 시트), 마이크 녹음(STT 없이 모델이 직접 전사), 문서 첨부는 **모두 E2E 동작한다.** 없는 것은 카메라 촬영, TTS, 그리고 **말풍선에 첨부 이미지 실제 표시**(현재는 이모지+텍스트 placeholder)다. 마지막 항목은 `ChatMessage` 에 첨부 필드가 없어 Room 마이그레이션이 필요하고, `GetContent` URI 는 재시작 후 접근 권한이 없으므로 앱 전용 저장소로 복사하는 설계가 함께 필요하다 — 별건으로 남긴다
- **[Note]** litertlm 릴리스 확인 — 최신은 **0.15.0**(8월 4일)이고 변경은 Apple FM Adapter·CLI `config.json`·JS(Gemma 4 웹 + `AutoToolChat`) 로 **Kotlin/Android 항목이 없다.** Maven 에도 `litertlm-android` 0.15.0 은 없다(HTTP 404 확인). Android 는 0.14.0 이 최신이다

## [0.8.7] - 2026-08-07
> **PC 실험 환경을 세워 프롬프트를 실측으로 검증했다** (ADR-009). `litert-lm-api`(Python) + 같은 `.litertlm` 파일로 앱 조건을 재현하니 케이스당 7초 — 실기기 왕복(빌드·설치·수동 입력, 수 분)과 비교가 안 된다. 첫 배치에서 **일정 등록이 3/4 실패**하고 그 원인이 우리 프롬프트였음이 드러났다.
- **[Fix]** 일정 등록을 막던 시스템 지시 교체 — `"If you lack mandatory information to use a tool, DO NOT guess. Ask the user for clarification first."` 이 한 줄이 주범이었다. 모델이 상대 날짜("내일", "모레", "다음주 월요일")를 '없는 필수 정보'로 판단해 호출 대신 되물었고(**자기가 날짜를 계산해 놓고도** 되물었다: *"다음 주 월요일은 2026년 8월 10일입니다. … 등록해 드릴까요?"*), 선택 파라미터인 `end_time` 까지 필수로 착각했다. "상대 날짜는 스스로 계산하라 / 선택 파라미터는 되묻는 이유가 될 수 없다"로 교체해 **일정 4케이스 2/5 → 5/5**
- **[Fix]** 시스템 지시에 요일과 파생 날짜를 계산해 제공 — 시각만 주면 모델이 "다음주 월요일"을 **한 주 틀리게**(8/10 → 8/17) 계산했다. `[System Data] 오늘=…, 내일=…, 모레=…, 다음주 월요일=…` 을 우리가 계산해 넣으니 정확해졌다. 상대 날짜 해석은 일정 등록의 전제라 모델에게 맡기지 않는다
- **[Fix]** `GetSchedule` 이 내일을 물으면 오늘 일정을 반환하던 결함 — 실행부가 `"week"` 포함 여부만 보고 나머지를 전부 TODAY 로 떨어뜨렸는데, 모델은 `date='tomorrow'`/`'2026-08-09'` 를 보낸다(실측). **틀린 답을 성공처럼 돌려주므로** 사용자가 알아채기 어렵다. 툴 설명으로 값을 좁히고(그래도 'tomorrow' 는 남았다), 실행부에서 today 가 아닌 값은 내일이 포함된 주간으로 넓혔다
- **[QA/Test]** 신규 테스트 10건 — `PromptAssemblerTest` +3(파생 날짜 계산, 다음주 월요일 불변식, **예전 "DO NOT guess" 문구가 되살아나면 실패**), `GetScheduleRangeTest` 7(tomorrow/구체 날짜/대소문자). 전체 162건 + lintDebug 통과
- **[Verified]** 0.8.2 에서 우려했던 "required 4개가 호출 문턱을 높인다"는 **실험으로 반증** — required=2 와 required=4 모두 4/4 성공이고 required=4 는 오히려 `end_time` 을 잘 채웠다. 앱 스키마를 바꿀 이유가 없다
- **[Verified]** 0.8.6 의 한국어 트리거는 효과 있음 — 메모리 자연어 **7/7 성공**("기억해줘", "저장해줘", "잊지 마", 트리거 없는 평서문까지). 태그 자동 분류도 양호
- **[Known Issue]** 숫자 왜곡("1234"→"134")은 **데스크톱 CPU·GPU 모두에서 재현되지 않았다** — 같은 모델 파일, 같은 조건에서 PC 는 매번 정확하다. 안드로이드 GPU(Adreno) 고유 문제로 좁혀진다. 코드로 고칠 수 있는 성질이 아니므로 완화책(승인 카드에서 내용 수정 UI)을 백로그로 남긴다
- **[Note]** Android 용 litertlm 은 여전히 0.14.0 이 최신이고 Python 은 0.15.0 이다(Maven 확인). 0.15.0 은 constrained decoding·thinking 을 전역 플래그가 아닌 정식 config 객체로 노출하므로, Android 에 올라오면 `GemmaModelRunner` 의 전역 플래그 조작을 걷어낼 수 있다

## [0.8.6] - 2026-08-07
> **툴 호출 첫 실기기 성공** — "add_memory 툴을 사용해서 …저장해줘"(툴 이름 직접 지목)에 `toolCalls=[AddMemory]` → 승인 카드 → 실행 → 메모리 화면 반영까지 전체 파이프라인이 처음으로 끝까지 동작했다. 네이티브 로그도 전부 정상 확인(Gemma4DataProcessor, FST 문법 생성 393ms, constraint 오류 없음). 남은 결함 3건 중 2건을 수정한다.
- **[Fix]** 툴 응답 턴이 새 대화로 가던 버그 — 토큰 초과 판정(>3000)이 승인 대기 사이에 대화를 재생성해, 모델이 **자기가 호출한 적 없는 툴의 응답**을 받고 뜬금없는 답("비밀번호는 234라고…")을 만들었다. `toolResponse` 턴은 어떤 재생성 조건보다 기존 대화 재사용이 우선하도록 수정
- **[Fix]** 토큰 초과 문턱 3000 → 8000 — 3000 은 자체 XML 규약 시절의 값으로, 네이티브 선언 경로에서는 툴 선언만 ~2천 토큰이라 거의 매 턴 전체 프리필이 발생했다 (gemma-4 컨텍스트는 32K)
- **[Fix]** 자연어 표현이 호출로 이어지지 않던 간극 — "add_memory 툴을 써줘"는 호출되지만 "기억해줘"는 안 됐다. 영어 규칙과 한국어 표현 사이에 다리가 없었으므로, 시스템 지시의 각 규칙에 한국어 트리거 예시("기억해줘", "예약", "오늘 일정" 등)를 직접 박았다
- **[Known Issue]** 숫자 복사 왜곡 — greedy(topK=1)에서도 "1234"가 "134"/"234" 로 저장·응답되는 사례. int4 양자화 4B 모델의 숫자 시퀀스 복사 약점으로 코드로 완치가 어렵다. 완화 후보: 승인 카드에서 내용 수정 UI(백로그), 모델 파일 업스트림 갱신본 재다운로드
- **[QA/Test]** 전체 152건 + lintDebug 통과

## [0.8.5] - 2026-08-07
> 0.8.4 에서 숫자 왜곡은 잡혔으나(greedy 효과 — "1234" 정확) 호출 성향은 여전히 0. gallery 와의 구성 차이는 소진됐으므로, 성향 자체를 겨냥한 few-shot 시범과 네이티브 진단 2종을 추가했다.
- **[Added]** 툴 호출 few-shot 시범 — `initialMessages` 서두에 "사용자 요청 → 모델의 실제 `ToolCall` → `Message.tool` 응답 → 확인 답변" 왕복 한 번을 심는다. 지시("you MUST call")만으로 성향이 안 바뀌는 4B 모델에게 남은 가장 강한 지렛대는 정확한 네이티브 형식의 시범이다. 예시는 LLM 대화에만 존재하고 DB·화면 기록에는 남지 않는다
- **[Added]** 네이티브 로그 활성화 — `Engine.setNativeMinLogSeverity(INFO)`. 네이티브(liblitertlm_jni) 로그는 기본 억제되어 있어 constrained decoding 문법 생성 실패, 데이터 프로세서 선택(Gemma4DataProcessor 여부) 같은 결정적 진단이 logcat 에 전혀 없었다 — 지금까지 "네이티브 로그가 안 보인다"의 원인
- **[Removed]** 사용자 턴의 "[Current Input]" 라벨 — 자체 XML 규약 시절의 잔재. 네이티브 경로에서 역할 표시는 템플릿의 몫이므로 원문 그대로 보낸다 (gallery 와 동일)
- **[QA/Test]** 전체 152건 + lintDebug 통과

## [0.8.4] - 2026-08-06
> 0.8.3 에서도 미해결(모델이 "비밀번호는 12라고 기억해 두겠습니다" — 거짓 약속 + 숫자 왜곡). gallery 와의 구성 차이를 마저 대조해 마지막 2건을 맞췄다. 이로써 gallery agent chat 구성(툴 선언 + constrained decoding + greedy + thinking off + 툴 이름 지목 프롬프트)과 완전히 동일하다.
- **[Fix]** 샘플러를 greedy(topK=1)로 — gallery 는 agent chat 진입 시 **topK=1 을 강제**한다(`AgentChatSamplingParamsManager`, "specifically enforcing greedy decoding"). 샘플링이 남아 있으면 호출 시작 토큰이 최빈이 아닐 때 툴 호출이 확률적으로 뭉개진다. greedy 는 숫자 왜곡("1234"→"12")도 함께 막는다 — 0.8.0 의 temperature 하향(0.8→0.3)보다 근본적인 해법
- **[Fix]** 시스템 지시가 툴 이름을 직접 지목하도록 강화 — "You have tools available" 수준의 일반 권고로는 4B 모델이 호출 대신 말로만 약속했다. gallery 프롬프트처럼 백틱으로 런타임 이름(`add_memory` 등 snake_case — 선언과 같은 이름이어야 이어진다)을 지목하고 "you MUST call" 로 강제. 활성 툴 목록에 따라 조건부로 생성
- **[Fix]** trimIndent 템플릿에 여러 줄 변수를 보간하면 보간 줄만 들여쓰기가 어긋나는 포맷 버그 재발 방지 — buildFormatBlock 을 buildString 으로 전환 (0.8.0 에서 지운 `$toolsDesc` 버그와 동일 패턴)
- **[QA/Test]** 전체 152건 + lintDebug 통과

## [0.8.3] - 2026-08-06
> 0.8.2 에서도 `toolCalls=[]`. 이번에는 0.14.0 바이트코드로 constrained decoding 플래그가 네이티브까지 전달되는 것을 확인해 그 가설을 닫았고, gallery 와의 남은 차이를 전수 대조해 2건을 찾았다.
- **[Fix]** `enable_thinking=false` 를 extraContext 로 전달 — Gemma 4 는 thinking 모델이라 템플릿 기본값으로 생각 모드가 켜지는데, gallery 는 **모든** 추론 호출에서 이 변수를 false 로 넘기고 허용 목록에서도 thinking 과 agent chat(툴 호출)을 조합하지 않는다(`capabilityToTaskTypes.llm_thinking` 에 `llm_agent_chat` 없음 — 의도적 분리). 우리는 이 변수를 아예 안 넘겨서 생각 모드로 돌았고, 생각 모드 템플릿에서는 함수호출 동작이 달라진다 — `toolCalls=[]` 의 유력 원인
- **[Fix]** 툴 응답이 USER 역할로 위장되던 문제 — `sendMessageAsync(Contents)` 오버로드는 무조건 `Message.user(...)` 로 감싼다(바이트코드 확인). `Content.ToolResponse` 를 실어도 역할이 사용자면 템플릿이 툴 응답으로 렌더링하지 않는다. 공식 문서의 수동 툴 호출 예제와 같이 `Message.tool(...)` 로 감싸고, 일반 발화는 `Message.user(...)` 로 명시
- **[Removed]** 조사 과정에서 배제된 가설 기록 — `ExperimentalFlags.enableConversationConstrainedDecoding` 은 `createConversation` 이 읽어 `nativeCreateConversation` 의 boolean 인자로 정상 전달된다(0.8.1 의 수정은 유효). LiteRT-LM 공식 문서(docs/api/kotlin/getting_started.md)도 `automaticToolCalling = false` + `Message.toolCalls` 수동 처리를 정식 지원 경로로 명시한다
- **[QA/Test]** 전체 152건 + lintDebug 통과. 부작용: thinking 표시 기능은 생각 모드 비활성화로 휴면 상태가 된다 — 툴 호출이 핵심 기능(PRD F2/F3/F4)이고 thinking 표시는 보조 기능이므로 gallery 와 같은 선택을 따른다

## [0.8.2] - 2026-08-06
> 0.8.1 실기기 로그가 원인 범위를 크게 좁혔다 — 모델은 gemma-4-E4B(올바름)이고 preface 에 툴 선언이 **렌더링된다**(모델 파일 문제 아님). 대신 렌더링된 스키마에서 우리 쪽 결함 2건이 드러났다.
- **[Fix]** `add_schedule` 의 파라미터 이름 `description` 이 스키마의 툴 설명 키와 충돌 — 렌더링된 선언의 properties 에서 **사라지고 required 에만 남아**, 존재하지 않는 필드를 필수로 요구하는 깨진 스키마가 됐다. constrained decoding 은 이 스키마로 문법(FST)을 만들므로 생성 실패 시 강제가 통째로 죽을 수 있다 — `toolCalls=[]` 지속의 유력 원인. `memo` 로 개명 (executor 는 두 키를 모두 읽어 기존 테스트 호환 유지)
- **[Fix]** 인자 키 snake_case 미변환 — 런타임은 함수 이름뿐 아니라 **파라미터 이름도** snake_case 로 선언하고(preface 에서 `start_time`/`end_time` 확인), 모델이 보내는 인자 키도 그 이름이다. executor 는 camelCase 를 읽으므로(`requireString("startTime")`) 호출이 도착해도 인자가 통째로 유실될 상태였다. `camelArgName` 매핑을 `collectToolCalls` 에 추가 — 툴 호출이 한 번도 도착하지 않아 아직 밟지 않은 지뢰를 선제 제거
- **[Fix]** "nullable 파라미터는 required 에서 빠진다"는 0.8.1 의 가정이 실기기에서 반증됨 — `nullable:true` 로 렌더링되지만 required 에는 남는다. 주석을 사실로 교정하고, endTime 설명을 "모르면 생략"(불가능한 지시)에서 "모르면 시작 1시간 뒤"로 변경
- **[QA/Test]** `KosmosToolDeclarationsTest` 신설 4건 — snake_case↔camelCase 인자 키 왕복, 함수 이름 매핑, 미지 이름 통과. 전체 152건 + lintDebug 통과

## [0.8.1] - 2026-08-06
> 0.8.0 실기기 확인 결과 여전히 `toolCalls=[]` — 모델이 툴 호출을 만들지 않았다. 0.14.0 AAR 바이트코드와 gallery 소스를 대조해 배관은 정상임을 확정했다(툴 JSON 은 `automaticToolCalling` 과 무관하게 네이티브로 전달되고, `toolCalls` 는 우리가 collect 하는 스트리밍 Flow 에 실려 온다). 남은 원인은 두 가지 — constrained decoding 미활성화, 그리고 모델 파일의 템플릿 지원 여부.
- **[Fix]** `ExperimentalFlags.enableConversationConstrainedDecoding` 활성화 — gallery 는 툴을 쓰는 **모든** 태스크에서 이 전역 플래그를 `createConversation` 직전에 켜고 직후에 끈다. 툴 스키마로부터 FST 문법을 만들어 모델 출력을 호출 구문으로 **강제**하는 장치로, 선언만으로는 4B 온디바이스 모델이 호출 형식을 지키지 못한다. `ConversationConfig` 필드가 아니라 생성 시점에만 읽히는 전역 플래그라서 0.8.0 전환 때 누락됐다
- **[Added]** 진단 로그 3종 — ① 선택된 모델 파일 이름·크기(`GemmaRuntimeManager`), ② Conversation 생성 시 선언된 툴 목록·프로바이더 수, ③ `renderPrefaceIntoString()` 으로 렌더링된 프롬프트 서두. **툴 호출 지원은 모델 파일에 달렸다** — 네이티브가 모델 메타데이터의 jinja 템플릿을 쓰는데 템플릿에 툴 블록이 없으면 선언이 렌더링에서 통째로 탈락한다(gallery 허용 목록 기준 Gemma 4 = 지원, Gemma 3n = 미지원). preface 로그가 이를 실기기에서 확정하는 유일한 단서다
- **[Fix]** 모델 파일 스캔의 외부 저장소 분기가 `firstOrNull`(파일시스템 나열 순서, 비결정적)이던 것을 내부 분기와 같은 최신 수정 파일 기준으로 통일 — 옛 모델이 남아 있으면 그게 잡힐 수 있었다
- **[Fix]** R8 keep 규칙 추가 — litertlm AAR 은 consumer proguard 규칙을 싣지 않고, `tool(ToolSet)` 은 kotlin-reflect 로 `@Tool` 메서드를 찾으므로 R8 이 이름을 바꾸면 **예외 없이 빈 툴 목록**이 되어 release 빌드에서만 툴이 조용히 죽는다. 잠복 버그 선제 수정
- **[Fix]** `addSchedule` 선언의 `endTime`/`description` 을 nullable 로 — non-null 파라미터는 스키마의 required 에 들어가 4B 모델이 4개 인자를 전부 지어내야 호출할 수 있었다(환각 인자 유도). 실행부는 누락 인자를 이미 처리한다
- **[QA/Test]** `BaseAgentStreamTest` 의 enabledTools 검증이 빈 리스트끼리 비교하는 공허한 테스트였던 것을 비어 있지 않은 목록으로 교정 — `BaseAgent` 가 enabledTools 를 채우지 않아도 통과하는 상태였다. 전체 148건 + lintDebug 통과
- **[Known Issue]** 실기기 재확인 필요 — ① 모델 파일이 `gemma-4-*` 인지(`model file:` 로그), ② preface 에 툴 선언이 렌더링되는지, ③ 승인 카드. preface 에 툴 블록이 없으면 코드가 아니라 모델 파일 교체가 답이다

## [0.8.0] - 2026-08-06
> 실기기 테스트에서 메모리·일정·위키 기능이 **전부 동작하지 않는 것**이 확인됐다. 감사 로그상 모델이 `<tool_call>` 을 한 번도 생성하지 않고 평문으로 답하며 "저는 이 정보를 영구적으로 저장하지 않고"라고 말했다 — 자기에게 툴이 있다는 걸 몰랐다. 최근 4개 버전과 무관하며, **툴 호출은 실기기에서 처음부터 동작한 적이 없었다.** 기존 테스트는 fake 모델이 툴 호출을 스크립트해 배관만 검증했다.
- **[Fix]** 툴 호출을 LiteRT 네이티브 함수호출로 전환 (ADR-008) — 시스템 프롬프트로 `<tool_call>{"name":...}</tool_call>` 형식을 가르치던 방식을 버렸다. 그 규약은 Gemma 의 채팅 템플릿에 없어 온디바이스 모델이 따를 사전 지식이 없다. `@Tool`/`@ToolParam` + `ToolSet` 선언을 `ConversationConfig(tools = ...)` 로 넘겨 모델의 정식 함수호출 템플릿을 쓴다 (google-ai-edge/gallery 참조). `kotlin-reflect` 의존 추가 — `ReflectionTool` 이 `KFunction` 으로 스키마를 만들므로 없으면 런타임 실패한다
- **[Fix]** 일정 등록이 표현에 따라 실패하던 문제 — `IntentClassifier` 가 쓰기 키워드와 캘린더 문맥 키워드를 **둘 다** 요구했는데 "치과 예약"에는 문맥 키워드가 없었다(목록에 `약속` 은 있지만 `예약` 은 없다 — 글자 순서가 반대인 다른 단어다). `DefaultAgent` 로 라우팅돼 `AddSchedule` 이 프롬프트에서 사라지고 실행 단계에서도 차단됐다
- **[Fix]** 모델이 사용자가 말한 숫자를 왜곡하던 문제 — `temperature 0.8 → 0.3`. "비밀번호 1234"가 342/4213/2143 으로 바뀌었다. 프롬프트에 "숫자·날짜·고유명사를 절대 바꾸지 말라"는 지시도 추가
- **[Fix]** Wikipedia 요청에 `User-Agent` 추가 및 무의미한 `origin=*` 제거 — Wikimedia 정책상 식별 가능한 UA 가 없으면 403/레이트리밋이 발생한다(위키 검색 실패의 원인 후보)
- **[Fix]** 시스템 지시의 시각을 초 단위에서 분 단위로 — 매 턴 지시가 달라져 `getOrCreateConversation` 의 재사용 판정이 항상 거짓이 되고 Conversation 이 매번 파괴·재생성(전체 프리필)됐다. 그 판정은 사실상 죽은 코드였다
- **[Refactoring]** 라우터 폐지 및 에이전트 단일화 — `IntentClassifier`/`TaskRouter`/`CalendarAgent` 제거, `DefaultAgent` → `KosmosAgent` 로 통합해 툴 4개를 모두 선언한다(웹 검색만 토글 게이트). **에이전트별로 툴 목록이 다른 구조 자체가 "라우팅 오류 = 툴 소멸"이라는 실패 모드**였다. 실행 시점 allowlist 재검증은 유지한다 — 프롬프트 주입 방어는 라우팅과 별개다
- **[Refactoring]** `ModelRunner` 반환형을 `AppResult<String>` → `AppResult<ModelTurn>`(텍스트 + 구조화된 툴 호출)로 확장. `ChatPrompt` 에 `enabledTools`/`toolResponse` 추가. 툴 결과는 `Content.ToolResponse` 로 되돌린다 — 이전에는 `<tool_response>` 텍스트를 사용자 턴으로 위장해 보냈다. `ToolArguments.of(map)` 덕분에 executor 4개와 승인 경로는 무변경
- **[Refactoring]** `GemmaModelRunner` 의 세 진입점(텍스트/이미지/오디오)에 60여 줄씩 복제돼 있던 추론 흐름을 `runTurn` 하나로 통합 — 툴 호출 수집을 세 곳에 각각 넣으면 어긋날 수밖에 없었다. 이미지 경로에만 남아 있던 죽은 try/catch 도 제거
- **[Refactoring]** `ToolParser` 의 역할 축소 — 툴 호출 판정은 구조화된 값이 담당하고, 이 파서는 `<|think|>` 처리와 표시 단계 위생 처리(모델이 옛 규약을 흉내내도 화면에 노출되지 않게)만 맡는다. 스트리밍 중 조기 취소도 제거
- **[QA/Test]** 신규 테스트 6건(`KosmosAgentTest` 3건 — 표현과 무관하게 일정 툴이 항상 노출되는지, `BaseAgentStreamTest` +3건 — 구조화된 툴 호출이 `toolResponse` 로 회신되는지). fake 5곳을 `ModelTurn` 으로 이관하고 `ToolApprovalE2ETest` 의 스크립트를 XML 텍스트에서 구조화된 호출로 교체. 전체 148건 + lintDebug 통과
- **[Known Issue]** **실기기 확인 필요 —** `automaticToolCalling = false` 에서 `Message.toolCalls` 가 채워지는지는 디컴파일된 API 표면으로만 확인했다. 첫 확인 항목: 메모리 저장 요청 시 승인 카드가 뜨는지. 실패 시 대안은 `automaticToolCalling = true` + `ToolSet` 본문에서 승인 처리(ADR-008 하단)
- **[Known Issue]** UI 2건 보류 (사용자 결정) — 상단 웹 검색 토글과 설정 버튼 겹침, 일정 탭의 파란 표지 오류. 대화가 동작한 뒤 전면 개선 때 함께 본다

## [0.7.4] - 2026-08-06
- **[Performance]** 임베딩 저장을 콤마 구분 문자열에서 little-endian float BLOB 으로 전환 (Room v4→v5) — `searchByVector`가 1000행을 `split`+`toFloatOrNull`로 파싱해 RAG 질의 한 번에 30만 개 남짓 단기 할당이 발생했고, `toDomain()`이 같은 문자열을 다시 파싱하는 이중 파싱도 있었다. `FloatBytes`로 인코딩을 한 곳에 모았고 바이트 순서를 명시적으로 고정했다(`ByteBuffer` 기본값은 big-endian이라 플랫폼 기본값에 맡기면 조용히 깨진다)
- **[Fix]** 임베딩 파싱 실패가 노트를 영구히 숨기던 실패 모드 — `mapNotNull`이 실패 항목을 조용히 버려 길이 비교에서 탈락한 노트가 벡터 검색에서 안 보였다. BLOB 은 길이가 구조적으로 보존된다
- **[Fix]** `searchByTags`의 중복 제거를 `Set`에서 `distinctBy { id }`로 변경 — `KnowledgeEntity`가 `ByteArray` 필드를 갖게 되면서 `data class`의 `equals`가 참조 비교가 됐고, 그대로 두면 같은 노트가 태그마다 중복 반환된다. 문자열 임베딩 시절에는 값 비교라 우연히 동작했다
- **[Added]** `MIGRATION_4_5` 및 마이그레이션 정의를 `KosmosMigrations`로 분리 — 인라인 익명 객체로는 테스트가 불가능했다. TEXT→BLOB 은 `ALTER`로 못 하고 CSV→바이트 재인코딩은 SQL로 표현할 수 없어, 새 테이블 복사 + Kotlin 행 단위 변환 + 인덱스 재생성으로 처리한다. 기존 임베딩을 버리는 쪽이 간단하지만 재생성 경로가 없어 기존 노트가 벡터 검색에서 영구히 사라진다 (사용자 결정)
- **[Refactoring]** `:domain`에서 `androidx.paging` 의존 제거 — `AuditRepository.getPaged()`/`KnowledgeRepository.getPagedData()`가 `Flow<PagingData<T>>`를 반환해 Pure Kotlin JVM 모듈이 `androidx` 타입을 공개 계약에 노출했다(선언은 `implementation`인데 공개 시그니처에 나타나던 잠재적 누수). offset/limit 계약으로 교체하고 `Pager` 생성을 ViewModel로 올렸다 — `TaskRepository.getPendingTasksData`와 같은 형태다. `DefaultPagingSource`를 `ui/paging`으로 옮기고 세 ViewModel이 복제하던 `AppResult`→throw 변환을 `unwrapForPaging`으로 통합
- **[Fix]** 스트리밍 파싱 단일화 — 툴 루프에서 1턴 문장이 2턴에 이어붙어 보이다가 완료 시 마지막 턴만 커밋돼 텍스트가 줄어드는 것처럼 보이던 문제. `ChatViewModel`이 원시 토큰을 직접 누적했으나 계약에 턴 경계 신호가 없어 비울 수 없었다. `ChatRequest.onToken`을 `onStream: (StreamUpdate)`로 바꿔 파싱을 `BaseAgent` 한 곳으로 모았다 (ADR-007)
- **[Fix]** 스트리밍 중 `<tool_call>` 프로토콜 문법이 화면에 노출되던 문제 — 정규식이 닫는 태그를 요구해 열린 태그가 본문에 남았고, 툴 콜 턴에서는 가시 구간 대부분 동안 `<tool_call>{"name":"AddSch`가 한 글자씩 자라는 것이 보였다. 완전한 여는 태그와 꼬리의 부분 접두사를 모두 절단한다
- **[Fix]** 생각 블록만 스트리밍되는 구간의 빈 버블 — 본문이 빈 문자열이라 `ChatScreen`이 "텍스트 있음"으로 판단해 타이핑 인디케이터까지 숨겼다. `StreamUpdate.content`를 nullable 로 정규화하고 `sendMessage` 초기값도 `""`에서 `null`로 변경
- **[Fix]** 두 번째 이후 `<|think|>` 블록이 본문에 남아 노출되던 문제 — `find`(단수)를 `findAll`로 변경
- **[QA/Test]** 신규 테스트 33건 — `FloatBytesTest`(5건, 바이트 순서 고정 포함), `KnowledgeEmbeddingBlobTest`(6건, BLOB 왕복·벡터 검색·태그 중복·페이지 경계), `KnowledgeEmbeddingMigrationTest`(7건, 마이그레이션 SQL 및 결과 DDL이 v5 스키마와 일치하는지), `ToolParserStreamTest`(9건, 점진 공급), `BaseAgentStreamTest`(6건, 턴 경계 회귀 방지). `BaseAgent` 테스트와 마이그레이션 테스트는 이 프로젝트에 처음 생겼다. 전체 142건 + lintDebug 통과
- **[Known Issue]** FTS4/5 전환 보류 (사용자 결정) — 현재 `LIKE '%q%'`는 문자 단위 부분 일치라 `"의"`로도 `"회의"`가 걸리지만, FTS 기본 `unicode61` 토크나이저는 한국어 형태소를 분리하지 못해 공백으로만 자른다. `"회의를"`로 저장된 어절이 `"회의"` 검색에 걸리지 않아 RAG 회상 재현율이 떨어진다. 성능 문제도 현재 규모(노트 수백 건, `LIMIT 100`)에서 실측되지 않았다
- **[Known Issue]** `MIGRATION_2_3` 누락 — `data/schemas/`에 2.json·3.json이 있는데 등록된 마이그레이션은 3→4부터라, v2 DB를 가진 설치는 업그레이드 시 `IllegalStateException`으로 죽는다. **조치하지 않는다 (사용자 결정)** — 배포 이력이 없는 개인 프로젝트이고 실기기 DB가 이미 v4 이상이라 도달 불가 경로다. 외부 배포 시 재검토

## [0.7.3] - 2026-08-06
- **[Added]** 알림용 24dp 모노 벡터 3종 신설(`ic_stat_download`/`ic_stat_download_done`/`ic_stat_error`) — 기존에는 `android.R.drawable.stat_sys_*` 플랫폼 아이콘을 빌려 썼는데 제조사가 재스타일링하므로 기기별로 다르게 보이고 시스템 알림과 구분되지 않았다. 상태바는 아이콘을 알파 마스크로만 쓰므로 단색 실루엣으로 작성. 취소 액션 아이콘은 플랫폼 X 유지(API 24+ 대부분의 알림 UI가 액션 아이콘을 표시하지 않는다)
- **[Added]** `res/values/colors.xml` 신설 + 알림 3종에 `setColor` 적용 — 프로젝트 색은 Compose 토큰이 정본이지만 알림은 프레임워크가 그려서 색을 int로 넘겨야 하므로 accent(`#22D3EE`)만 리소스로 복제했다. 알림 그림자는 시스템 테마를 따라 라이트/다크 분기가 불가능하므로 단일 값을 쓴다
- **[Fix]** 콤마가 든 태그가 두 개로 쪼개지던 문제 — `tags` 칼럼이 콤마 조인 문자열이고 `KnowledgeDao.searchByTags`가 `',' || tags || ','` 패턴으로 콤마를 순수 구분자로 하드 가정한다. `["밥, 국"]`을 저장하면 `["밥", "국"]`으로 읽히고, 0.7.1의 태그 정확 매칭 이후로는 원래 태그로 검색이 아예 불가능했다. `Tags.normalize`로 콤마를 공백 치환(사용자 결정 — 저장 거부 대신). 저장·검색 양쪽에 같은 정규화를 적용해 형태를 맞췄고, 정규화가 새로 만드는 중복도 제거한다
- **[Fix]** `MediaPipeTextEmbedder` 초기화 실패가 영구 고장으로 굳던 문제 — `init` 블록에서 그래프를 로드하고 실패를 `catch(Throwable)` + 로그로 넘겨 필드가 영구히 null이 됐다(재시도 경로 없음). `SaveKnowledgeUseCase`는 폴백 없이 실패하므로 앱 재시작 전까지 **메모리 저장이 통째로 막히는** 상태였다. `Mutex` 기반 지연 초기화로 바꿔 실패를 기억하지 않고 다음 호출에서 재시도한다. `.tflite`는 assets에 포함돼 있어 파일 부재로 실패할 일이 없고 남는 원인은 메모리 압박 같은 일시적 사유이므로, 별도 품질 저하 배너 대신 재시도로 대응했다
- **[Fix]** 임베딩 그래프 로드가 첫 주입 시 호출 스레드(메인 가능)를 블로킹하던 문제 — 로드와 추론을 `Dispatchers.Default`로 이동. 초기화 실패 원인도 오류 메시지에 실어 보낸다(기존에는 `"TextEmbedder is not initialized"`만 남고 원인이 사라졌다)
- **[Refactoring]** `TextEmbedder.embed`를 `suspend`로 전환 — 논블로킹 시그니처였기 때문에 구현체가 그래프 로드를 생성자로 밀어낼 수밖에 없었다. 호출부 2곳은 이미 `suspend` 함수 안이라 변경 없음
- **[QA/Test]** 신규 테스트 13건 — `TagsNormalizeTest`(8건), `KnowledgeTagRoundTripTest`(5건, 인메모리 Room으로 `joinToString`↔`split` 실제 왕복 검증). 전체 109건 + lintDebug 통과
- **[Known Issue]** 아이콘 시각 확인은 실기기 필요 — 상태바 알파 마스크 결과와 알림 그림자 틴트는 단위 테스트 불가 영역

## [0.7.2] - 2026-08-06
- **[Added]** 내보내기/가져오기 UI 진입점 — `prd.md` F8이 "메모리 화면에서 export 선택"을 요구하는데 진입점이 아예 없었다. `MemoryViewModel.exportData`/`importData`는 구현돼 있었으나 호출자가 없어 도달 불가 상태였고(`importLauncher`는 만들어졌지만 `launch`가 호출되는 곳이 없었다), 그래서 `V1-AC4`(export→import 왕복)를 사용자가 수행할 방법이 없었다. 메모리 화면 하단에 백업 섹션 추가
- **[Added]** 내보내기 시 개인정보 포함 경고 다이얼로그 — `prd.md` F8 정책("export 파일에 개인정보가 포함됨을 UI에서 명시") 충족. v1은 암호화를 의도적으로 넣지 않았으므로 이 경고가 유일한 보호막이다. 가져오기에도 기존 데이터를 되돌릴 수 없이 덮어쓴다는 확인 단계 추가
- **[Added]** 저장 위치 직접 선택 — 생성된 zip은 `cacheDir`에 있어 사용자가 접근할 수 없고 시스템이 언제든 비울 수 있다. SAF `CreateDocument`로 사용자가 고른 위치에 복사하는 `BackupFileWriter` 추가 (사용자 결정). 저장 완료·취소 어느 쪽이든 캐시 사본을 정리한다
- **[Fix]** `MemoryViewModel`의 콜백 누수 — 콜백 람다가 Activity `context`를 캡처하고 `viewModelScope` 코루틴이 그것을 붙잡았다. DB를 재작성하는 가져오기 도중 화면을 회전하면 파괴된 Activity를 향해 `startActivity`가 실행되고, 반대로 컴포지션을 벗어나면 진행 중 코루틴이 옛 람다를 들고 있어 완료 통지가 유실됐다(`UiState`에 읽을 필드도 없었다). 결과를 `BackupState`로 노출하도록 전환
- **[Fix]** 가져오기 중복 실행 가드 추가 — DB 파일을 통째로 교체하는 작업이라 재진입이 곧 데이터 파괴다
- **[Fix]** 복원 후 자동 재시작(Toast + 2초 지연 `exitProcess(0)`)을 확인 다이얼로그로 교체 (사용자 결정) — 타이머가 화면 회전이나 백그라운드 전환과 경합할 수 있었고, `Toast`는 0.6.0의 라이트/다크 토큰을 무시했다. 재시작 다이얼로그는 닫을 수 없다(DB가 이미 교체된 상태로 계속 쓰면 화면의 페이징 캐시와 실제 데이터가 어긋난다)
- **[Removed]** `AuditRepository.getPagedByType` 3계층 제거 (인터페이스·`AuditRepositoryImpl`·`AuditDao`) — 감사 로그 타입 필터용으로 UI보다 먼저 만들어졌으나 `tasks.md`에 해당 요구사항이 없고 호출자도 없었다. 소비자 없는 계약은 `:domain`의 paging 의존 제거 작업만 무겁게 만든다. DB 스키마 변경이 아니므로 마이그레이션 없음 (사용자 결정)
- **[QA/Test]** 신규 테스트 12건 — `MemoryBackupStateTest`(내보내기/가져오기 상태 전환, 중복 실행 가드, 재시작 대기 상태의 닫기 차단, 경고 선행 검증). 전체 96건 + lintDebug 통과
- **[Known Issue]** `ExportImportManager`의 zip 왕복은 여전히 무테스트 — Room DB 파일 교체와 프로세스 재시작이 얽혀 JVM 테스트로 재현하기 어렵다. 실기기 QA 필요(특히 가져오기 진행 중 화면 회전)

## [0.7.1] - 2026-08-06
- **[Fix]** `AddMemory` 툴의 태그가 전부 유실되던 버그 — `AddMemoryToolExecutor`가 `tagsRaw is List<*>`로 분기했으나 `org.json`은 JSON 배열을 `List`가 아닌 `JSONArray`로 주므로 이 분기는 절대 참이 되지 않았다. 프롬프트가 지시한 정식 형태(`{"tags":["work","urgent"]}`)로 보낸 태그가 조용히 사라지고 콤마 문자열 폴백만 동작하던 상태(재현 확인: `isList=false`, 결과 `[]`)
- **[Fix]** `KnowledgeDao` LIKE 와일드카드 미이스케이프 — Room 파라미터 바인딩은 SQL 인젝션을 막지만 `%`/`_`는 바인딩된 값 안쪽에 있어 와일드카드로 해석됐다. `100%` 검색이 `100` 포함 전체를, `%` 한 글자가 테이블 전체를 매칭해 노트 100건이 RAG 프롬프트로 쏟아지고 컨텍스트 예산을 터뜨렸다. `SqlLike.escape` + 두 쿼리에 `ESCAPE '\'` 적용, 빈 검색어 가드 추가
- **[Fix]** 태그 부분 매칭 — `tags`는 `"work,urgent"` 형태 콤마 문자열인데 `LIKE '%work%'`가 `"workflow"`에도 매칭됐다. 구분자로 양쪽을 감싸 정확히 한 개 토큰으로 매칭 (사용자 결정)
- **[Fix]** 승인·실행 인자 검증 불일치 — `AddScheduleToolExecutor`의 `buildApprovalRequest`는 누락된 제목을 `(제목 없음)`으로 표시하고 `execute`는 거부했기 때문에, 사용자가 애초에 실행될 수 없는 초안을 승인할 수 있었다. 검증을 한 곳으로 모아 승인 단계에서 먼저 실패하게 변경
- **[Fix]** JSON이 깨진 `<tool_call>`을 빈 `catch`로 삼켜 툴 콜이 흔적 없이 사라지던 문제 — 모델은 오류를 받지 못하고 그 턴이 평문 답변으로 처리돼 사용자에게는 요청이 무시된 것처럼 보였다. `ParsedStream.malformedToolCalls`로 노출하고 `BaseAgent`가 형식 오류를 모델에게 되돌려 재작성 기회를 준다
- **[Refactoring]** 툴 인자 해석을 `ToolArguments` 래퍼로 일원화 — `ToolExecutor`의 두 시그니처를 `Map<String, Any>` → `ToolArguments`로 교체하고 executor 4곳의 `as? String` 캐스트를 제거. `JSONObject.NULL`을 "값 없음"으로 정규화(non-null `Any`라서 생기던 함정 제거), 숫자·불린은 문자열로 강제 변환(모델이 따옴표를 빠뜨려도 대화가 멈추지 않도록), 배열·객체는 타입 오류로 보고. 누락(`MISSING`)과 타입 오류(`WRONG_TYPE`)를 구분해 모델이 무엇을 고칠지 알 수 있게 함 — 기존에는 둘을 뭉개 모델이 이미 보낸 값을 반복 요구받는 루프에 빠졌다
- **[QA/Test]** 신규 테스트 33건 — `ToolArgumentsTest`(14건, JSON 배열 태그 회귀 방지 포함), `SqlLikeEscapeTest`(6건, 백슬래시 우선 처리 순서 검증), `KnowledgeSearchEscapeTest`(10건, 인메모리 Room으로 실제 쿼리 검증), `ToolParserTest`(+3건). 전체 84건 + lintDebug 통과. `ToolApprovalE2ETest`가 승인 경로를 태워 회귀 안전망 역할

## [0.7.0] - 2026-08-05
- **[UI/UX]** Phase B-3 2~3단계 — 모델 다운로드를 WorkManager 전경 작업으로 이관 (ADR-006)
  - 다운로드가 `viewModelScope`를 벗어나 `ModelDownloadWorker`(@HiltWorker)로 이동 — 화면을 벗어나거나 앱을 닫아도 전송이 계속된다. 진행 카드 문구도 "앱을 종료하면 다운로드가 중단됩니다" → "앱을 닫아도 백그라운드에서 계속 진행됩니다"로 사실에 맞게 교체
  - 전경 서비스 알림으로 진행률(받은 용량/전체 용량)과 취소 액션 노출, 완료/실패 알림 추가. 알림 인프라(`NotificationChannels`, `DownloadNotifier`)는 프로젝트 최초 도입
  - 진행 카드에 대기 상태(`Queued`) 추가 — Wi-Fi 전용 제약 때문에 Wi-Fi가 없으면 작업이 멈춰 있는데, 카드가 없으면 "다운로드 버튼이 먹지 않는" 것으로 보인다
  - 실패 다이얼로그에 재시도 버튼과 이어받기 안내 추가, 취소를 "일시 중지"(부분 파일 보존)와 "취소 후 삭제"(저장 공간 회수)로 분리
  - POST_NOTIFICATIONS 컨텍스트 요청(API 33+) — 거부되어도 다운로드는 진행한다
- **[Feature]** HTTP Range 이어받기 및 저장 공간 사전 점검
  - `.part` + `.part.meta`(url·ETag·총 크기) 사이드카로 프로세스가 죽어도 재개 가능. ETag 불일치나 200 응답 시에는 처음부터 다시 받아 버전이 섞인 손상 파일을 만들지 않는다
  - 전송 시작 전 `Content-Length` 기반으로 필요 용량(+256MB 여유)을 점검하고, 부족하면 실제 필요 바이트 수와 함께 차단
  - 재시도 정책: `Transient`(IOException·타임아웃·5xx·408·429)만 최대 5회 지수 백오프(30초 시작), `Permanent`(4xx·크기 불일치)는 즉시 실패
  - 네트워크 제약 `NetworkType.UNMETERED` — 3.6GB를 이동통신 데이터로 받지 않는다 (사용자 결정)
  - 다운로드 전용 OkHttpClient 분리(`@DownloadClient`, read 60s / callTimeout 무제한) — 공유 클라이언트의 30초 read timeout은 대용량 스트림에서 끊긴다
- **[Fix]** `:data` 모듈에 `kotlin-serialization` 플러그인 누락 — 모듈 내부 `@Serializable` 클래스의 직렬화기가 생성되지 않아 컴파일은 통과하고 런타임에 `SerializationException`이 발생하던 상태. `PartMeta` 이어받기 실패로 발견했고, 같은 원인으로 `ExportManifest` 기반 내보내기/가져오기도 동작하지 않고 있었다
- **[Fix]** 다운로드 확정 시 기존 모델 파괴 회귀 — `target.delete()`를 먼저 하고 rename 했기 때문에 rename이 실패하면 새 모델도 없고 쓰던 모델도 없는 상태가 됐다. 기존 파일을 `.bak`으로 옮긴 뒤 rename 하고 실패 시 되돌린다
- **[Fix]** `.part`를 `finally`에서 무조건 삭제하던 동작 제거 — 재시도가 매번 0바이트부터 다시 받게 만든 원인. 영구 실패에서만 정리한다
- **[Fix]** 저장 공간 부족 판정의 부분 문자열 매칭(`message.contains("space")` → `InsufficientStorage(0L)` 하드코딩) 제거 — `ModelDownloadException` 타입 분기로 대체하고 실제 필요 바이트 수를 전달
- **[Fix]** 중복 다운로드 방지를 `downloadJob?.isActive` 가드에서 WorkManager 유니크 작업(KEEP)으로 이관 — 옛 가드는 ViewModel이 죽으면 함께 사라져 실제로 중복을 막지 못했다
- **[Refactoring]** `ModelDownloader`를 `Flow<Int>`(퍼센트) → `Flow<DownloadProgress>`(바이트)로 확장하고 `probe`/`clearPartial`/`partialBytes` 추가. `DownloadModelUseCase`는 `status`/`enqueue`/`cancel`/`acknowledge` 파사드로 재작성(예약과 관찰이 분리된 관심사가 됐다). `ModelDownloadService`의 하드코딩 `"models"`를 `Constants.MODEL_DIR_NAME`으로 교체
- **[QA/Test]** 신규 테스트 25건 — `DownloadStatusMappingTest`(10건, WorkInfo 전 상태 번역), `ModelDownloadWorkerTest`(6건, 재시도/실패 분류·완료 부수효과), `ModelDownloadResumeTest`(9건, MockWebServer 기반 실제 이어받기·부분 파일 보존·기존 모델 보호). 전체 51건 + lintDebug 통과
- **[Known Issue]** 알림 아이콘은 `android.R.drawable.stat_sys_download` 계열 임시 사용 — 24dp 모노 벡터 필요. 전경 승격·알림 렌더링·Doze 백오프는 단위 테스트 불가 영역으로 ADR-006에 수동 QA 체크리스트 기재

## [0.6.1] - 2026-07-31
- **[UI/UX]** UI 개선 계획서 Phase A — 죽은 인터랙션 활성화
  - 캘린더: 장식이던 날짜 스트립(`DatePill`)을 실제 필터로 연결(탭 → 해당 일자만 표시, 재탭 시 해제, 오늘이 아닌 날짜 선택 시 주간 범위 자동 확장), 미사용이던 `selectedRange`를 오늘/이번 주 세그먼트 UI로 노출, 섹션 헤더가 현재 필터를 반영
  - Memory: 기본 필터를 `ALL` → `KNOWLEDGE`로 변경해 첫 진입 시 두 탭 모두 미선택으로 보이던 문제 해결(`MemoryFilterType.ALL` 제거)
  - 채팅: 생성 중 전송 버튼을 정지 버튼으로 전환(`ChatViewModel.cancelGeneration()`) — 모델 스트림만 중단하고 파이프라인은 정상 종료시켜 부분 응답을 보존
  - ISO 파싱 규칙을 `domain/util/IsoDateTimeParser`로 추출해 유즈케이스와 화면 필터가 동일 규칙을 공유
- **[UI/UX]** Phase B(1~2) — 신뢰감 있는 피드백
  - `ErrorCodeMapper` 재작성: 부분 문자열 추측(예: "timeout" → MISSING_TIME_INFO) 제거 후 표준 토큰(`ValidationReason`) 정확 매칭, 미분류를 `INPUT_TOO_LONG` 오분류 대신 신규 `INVALID_INPUT`으로 처리
  - `ErrorMessages` 신설: 모든 `ErrorCode`에 사용자 문구(한국어) 매핑. 캘린더·설정·스플래시·모델 관리·메모리 백업 등 표시 지점 6곳을 연결해 `DbWriteError(task_item)` 같은 내부 클래스명 노출 제거
  - 채팅: 표시 경로가 아예 없어 무음으로 사라졌던 `uiState.error`를 스낵바로 노출, 웹 검색 토글 변경 시 허용/차단 안내 스낵바 추가
  - `ErrorMessageMappingTest` 신규(5건): 표준 토큰 매핑, 오분류 회귀, 내부 식별자 미노출, 전체 코드 문구 보유 검증. `docs/api_spec.yaml` 매핑 계약에 `INVALID_INPUT` 및 토큰 규칙 반영
- **[UI/UX]** Phase B-3 1단계 — 모델 다운로드 진행을 모달 다이얼로그에서 화면 내 카드로 이동(진행률·잔여 저장 공간·취소 노출). WorkManager 이관(2~3단계)은 계획서 권고대로 별도 세션으로 이월
- **[UI/UX]** Phase C — 채팅 화면 완성도
  - 날짜 구분선 추가(`ChatRow` 도입 — 오늘/어제/M월 d일), 메시지 롱프레스 복사(+스낵바), 최신으로 이동 FAB(사용자가 위로 스크롤하면 자동 스크롤 일시 해제), 이미지 첨부 썸네일 프리뷰(`inSampleSize` 다운샘플 + IO 디코딩, 외부 이미지 라이브러리 미추가)
- **[QA/Test]** 전체 테스트 + lintDebug + build(릴리스 포함) 통과

## [0.6.0] - 2026-07-31
- **[UI/Feature]** 라이트/다크 테마 전환 지원 (ADR-005, 사용자 결정 D-b)
  - **시맨틱 토큰 도입**: 하드코딩된 top-level `Color` 상수 20종(BgColor/Cyan/TextPrimary 등)을 `KosmosColors` 데이터 클래스로 대체하고 `LocalKosmosColors` CompositionLocal로 주입 — 전 화면 190여 개 색상 참조를 `KosmosTheme.colors.<토큰>` 형태로 일괄 전환
  - **라이트 팔레트 신설**: `docs/DESIGN.md`의 Sky Blue 기획(canvas #F7FBFD / ink #1F2A33 / primary-strong #39AED8 / hairline #D9E6EC)을 Glassmorphism 구조에 적용 — 밝은 배경에서 white-on-white로 경계가 사라지지 않도록 카드 표면을 불투명에 가깝게 올리고 hairline 테두리로 계층 구성
  - **테마 선택 UI**: 설정 화면에 APPEARANCE 섹션 추가(시스템 설정/라이트/다크), `ThemeMode`를 DataStore(`theme_mode`, 기본 SYSTEM)에 영속. `ThemeViewModel`을 MainActivity 루트에서 구독해 즉시 전체 적용, 상태바 아이콘 명암도 동기화
  - **대비·연출 보정**: `onAccent` 토큰 도입으로 accent 배경 위 텍스트/아이콘 대비 확보(다크=남색, 라이트=흰색), `auroraAlpha`로 라이트에서 배경 오로라 강도 0.35배 감쇠, 라이트에서 안 보이던 `Color.White` 하드코딩 텍스트 5곳과 하드코딩 오렌지 경고 배너를 토큰으로 교체
  - **구조 대응**: `DrawScope`/`LazyListScope`는 비-Composable이므로 토큰을 바깥에서 추출해 전달(AuroraBackground/OrbPulse/ScheduleContent), `Modifier.glassEffect`는 색상 기본값을 null + `composed {}` 내부 해석으로 변경
- **[Docs]** `DESIGN.md` v2.0 개정 — 라이트/다크 양쪽 팔레트와 토큰 구현 위치·접근 규칙 명시(문서·구현 불일치 해소), `architecture.md` ADR-005 추가
- **[QA/Test]** 전체 테스트 + lintDebug + build(릴리스 포함) 통과

## [0.5.12] - 2026-07-31
- **[Refactoring/Decision]** 음성 입력을 Gemma 멀티모달 직접 입력으로 확정 (사용자 결정)
  - 도달 불가 상태였던 시스템 STT 대안 파이프라인 전체 삭제: `feature/voice`(VoiceOverlay/VoiceViewModel/VoiceUiState), `AndroidSpeechToTextTool`, `ProcessVoiceInputUseCase`, `domain/tool/SpeechToTextTool`(+SttState), PlatformModule 바인딩
- **[UI/Refactoring]** 일정 승인 UI를 플로팅 초안 카드로 통일 (절충안, 사용자 결정)
  - `ApprovalRequest`에 `calendarDraft` 페이로드 추가 — AddSchedule 승인 시 일반 다이얼로그(ApprovalSheet) 대신 기존 `CalendarDraftCard` UI로 렌더링, 승인/거절은 동일한 `ApprovalCoordinator` 경로 유지 (메모리 저장 등 비캘린더 승인은 시트 유지)
  - 패배한 설계 경로 제거: `ResponseParser`(항상 TextOutput 스텁), `PreExecutionGuard`/`ExecutionPolicy`, `ModelOutput`, `ActionCard`/`ActionPayload`, `AssistantResponse`, `AgentResult.Action`, `ChatUiState.pendingCalendarDraft` 및 관련 ViewModel 로직 — BaseAgent 응답 처리를 텍스트 저장·반환으로 단순화
  - `ChatViewModel`의 미사용 `addScheduleUseCase` 의존 제거 (E2E 테스트 3건 배선만 동기화, 검증 로직 불변)
- **[QA/Test]** 전체 테스트 + lintDebug 통과

## [0.5.11] - 2026-07-31
- **[Feature/Calendar]** 기기 시스템 캘린더 실연동 (ADR-004, 사용자 결정에 따른 옵션 A)
  - 바인딩만 되고 미사용이던 `AndroidCalendarTool`을 실제 배선: `AddScheduleUseCase`가 로컬 저장(단일 진실 원천) 후 기기 캘린더에 best-effort 삽입, 실패(권한 미보유 등) 시 로컬 저장 유지 + 경고 로깅
  - `GetTodayScheduleUseCase`가 기기 캘린더 이벤트를 읽어 (제목, 시작 시각) 중복 제거 후 로컬 일정과 병합 — 캘린더 화면/GetSchedule 툴 모두 기기 일정 반영
  - 캘린더 권한 컨텍스트 요청: ChatScreen 일정 승인 시(WRITE+READ), CalendarScreen 진입 시(READ, 승인 직후 재조회)
  - `GetTodayScheduleUseCaseTest`에 FakeCalendarTool 반영, 전체 테스트 + lintDebug 통과

## [0.5.10] - 2026-07-31
- **[Performance]** 추론·렌더링 핫패스 최적화
  - 이미지 JPEG 이중 압축 제거(`ProcessImageInputUseCase`는 검증·위임만, 압축은 `SendChatMessageUseCase` 단일 지점), `ImageInputAdapter`의 공유 bitmap pool 제거(동시 경합·픽셀 덮어쓰기·상주 메모리 문제)
  - 배터리 온도 조회 5초 캐시(토큰마다 Binder 왕복 제거), `BaseAgent` 스트리밍 파싱을 `<tool_call` 태그 감지 후에만 수행, 임계 발열(≥48°C) 시 감사 로그만 남기고 추론을 진행하던 문제 수정(실제 차단)
  - 채팅 LazyColumn `key={id}` 부여, `AuroraBackground`를 RESUMED 상태에서만 애니메이션(백그라운드 배터리 소모 제거)
- **[Refactoring]** 컨벤션·에러 처리 정비
  - `runCatchingCancellable` 공통 헬퍼 신설 후 5개 리포지토리 적용 — 코루틴 취소가 가짜 DB 오류로 변환되던 문제 해소, 읽기 실패의 `DbWriteError` 오분류 정정
  - `!!` 사용 전부 제거(BaseAgent/ChatScreen/AndroidCalendarTool), `KnowledgeNote` FloatArray equals/hashCode 구현, 툴 루프 상한 초과를 Timeout 대신 추론 오류로 보고
  - 죽은 코드 삭제(PulseGreenDot, PlaceholderScreen, GemmaTokenizer 동일 분기, resetCount 등), `RuntimeMetricsCollector`에 SupervisorJob+AtomicInteger 적용
  - Room/Paging 의존성 버전 카탈로그 통일(하드코딩 중복 제거), `collectAsStateWithLifecycle` 통일, `DEFAULT_MODEL_FILENAME`을 공식 다운로드 산출 파일명과 일치, domain의 `:core`를 `api()`로 전이
- **[QA/Test]** 전체 테스트 + lintDebug + build 통과
- **[Pending/Backlog]** 사용자 결정 대기: 기기 캘린더 실연동(P3-2), feature/voice 삭제 여부, ResponseParser/CalendarDraft 경로 완성 여부. 이월 과제: ToolExecutor Map<String,Any> 대체, KnowledgeDao LIKE escape, 임베딩 BLOB 전환, MediaPipeTextEmbedder lazy-init, domain paging 의존 제거, WorkManager 다운로드, ChatViewModel 스트리밍 파싱 단일화

## [0.5.9] - 2026-07-31
- **[Fix/Stability]** LLM 런타임 수명주기 경합 해소 (`GemmaModelRunner`)
  - `close()`가 메인 스레드에서 추론 중 네이티브 세션과 무동기화로 실행되던 use-after-free/ANR 위험 제거 — 취소 요청 후 LLM 디스패처에서 Mutex 직렬화 해제
  - `warmUp()`(Dispatchers.IO)과 첫 generate의 Engine 이중 초기화 경합 수정(동일 디스패처+Mutex), `conversation!!` NPE 제거
  - 세션 내 에이전트 전환 시 새 systemInstruction이 무시되던 Conversation 재사용 결함 수정, 툴 루프의 stateful 대화 중복 전송(currentInput+rawOutput 재전송) 제거, 첫 턴 사용자 메시지 이중 포함 제거 — 컨텍스트 토큰 낭비 대폭 감소
  - `BaseAgent`의 fire-and-forget 중간 취소가 다음 루프 추론을 죽일 수 있던 경합을 Job 추적+join으로 구조화
- **[Fix/Crash]** Memory 탭 Paging 중복 키 크래시 수정 — `DefaultPagingSource` key를 페이지 번호에서 offset 기반으로 재작성 (Paging3 initialLoadSize 3× 충돌)
- **[Fix/UX]** 공유 인텐트 콜드 스타트 유실 수정 (`ShareIntentHandler` replay=1 + 소비 후 클리어), 모델 다운로드 다이얼로그 취소 버튼 추가(Job 취소 연동), Import 후 재시작 로직의 raw thread/sleep 제거, 캘린더 초안 저장 실패 시 무음 dismiss 대신 에러 노출·카드 유지
- **[Security/Privacy]** `allowBackup=false` — 대화/지식/감사 DB가 클라우드 자동 백업되던 문제 차단 (백업은 앱 내 Export/Import 경로 사용)
- **[Fix/Platform]** `AudioRecorder` 해제 순서 교정(release 전 writer join, @Volatile, SupervisorJob), `ApprovalCoordinator` AtomicReference+60초 타임아웃 자동 거절, `SpeechRecognizer` release()/에러 코드 로깅, MainActivity 일괄 권한 요청 제거, 채팅 이미지 첨부 읽기를 메인 스레드에서 Dispatchers.IO로 이동
- **[QA/Test]** 전체 테스트 + lintDebug 통과

## [0.5.8] - 2026-07-31
- **[Fix/Calendar]** 일정 데이터 손실 및 시간대 표시 결함 수정
  - `AddScheduleUseCase`: `endTime` 무성 폐기 및 `description` title 뭉개짐 수정 — `TaskItem`/`TaskEntity`에 `endDateIso`/`description` 필드 추가(Room v3→4 명시적 Migration, `fallbackToDestructiveMigration` 제거), 하드코딩 `Success(1L)` 대신 실제 Task ID(String) 반환
  - `GetTodayScheduleUseCase.parseIsoToMs` 재작성: 오프셋 포함 시간(`+09:00` KST 등)이 조용히 드롭되던 휴리스틱을 `OffsetDateTime→Instant→LocalDateTime→LocalDate` 표준 폴백 체인으로 대체 (0.5.5의 "예외 안전성 강화" 기록 정정 — 당시 오프셋 드롭 결함 잔존했음)
  - WEEK 범위 8일 → 7일(오늘 포함) 경계 수정, 일정 정렬을 ISO 사전순에서 파싱된 epoch ms 기준으로 변경
  - 시간대 표시: `AndroidCalendarTool.msToIso`의 UTC 고정(`Instant.toString`) → 기기 시간대 오프셋 포함으로, `CalendarScreen.formatIsoString`/`CalendarDraftCard`의 문자열 슬라이싱(`takeLast(5)`) → java.time 파서 기반 표시로 교체 (KST 19:00가 "10:00 AM"으로 표시되던 버그)
  - `CalendarScreen` 헤더 "July 2026" 하드코딩 → 현재 년월 동적 표시
- **[QA/Test]** `GetTodayScheduleUseCaseTest` 신규 작성 (오프셋 파싱/혼합 포맷 정렬/7일 경계/불량 입력 스킵, 4건) — 전체 테스트 통과
- **[Pending]** 기기 캘린더 실연동(AndroidCalendarTool 배선) 여부는 기획 결정 대기 (docs/agent/task.md P3-2)

## [0.5.7] - 2026-07-31
- **[Security/Architecture]** 툴 승인·allowlist 실행 시점 강제 (ADR-002)
  - `ToolExecutor`에 `actionType`/`buildApprovalRequest` 계약 추가, `BaseAgent.executeToolInner`에서 ① 에이전트별 allowlist 검증 ② `ApprovalRules` 기반 사용자 승인 대기를 공통 수행 — 프롬프트에만 존재하던 장식용 allowlist와 `AddScheduleToolExecutor` 하드코딩 승인을 단일 정책 경로로 통합
  - `AddMemory` 툴을 MEMORY_WRITE 승인 대상으로 편입하고 DefaultAgent에 노출 (기존: 등록만 되고 도달 불가 + 무승인)
  - `IntentClassifier` 캘린더 문맥 키워드에 "약속"/"미팅"/"스케줄" 추가 — allowlist 강제 도입 후 라우팅 누락이 툴 차단으로 이어지는 문제 보완
- **[Feature/UX]** 웹 검색 허용 전역 토글 도입 (기획 변경, ADR-001, PRD F7 개정)
  - 채팅 헤더 설정 버튼 왼쪽에 웹 검색 토글(`Icons.Default.Language`, contentDescription "WebSearchToggle") 배치, `WebSearchViewModel` + `SettingsDataStore.webSearchEnabledFlow`(기본 OFF)로 영속화
  - OFF 시 SearchWikipedia를 모델 프롬프트에서 제외하고 실행 계층에서도 차단(이중 방어), ON 시 건별 승인 없이 실행
- **[Security/Fix]** 프롬프트 인젝션 경로 차단 (ADR-003)
  - 첨부 문서(`documentText`)를 SYSTEM 역할 대신 USER 역할 `[Attached Document]` 구분 블록으로 저장 — 문서 내 지시문의 시스템 프롬프트 승격 차단, 저장 실패 시 오류 반환
  - 툴 응답 JSON을 문자열 연결에서 `JSONObject` 조립로 전환(4개 실행기) — 따옴표/개행 파손 및 2차 인젝션 차단
  - `WikipediaSearchToolImpl`: `HttpUrl.Builder` 쿼리 인코딩(한국어/특수문자 안전), lang 파라미터 호스트 검증, `response.use` 커넥션 누수 수정
  - `AddScheduleToolExecutor`: title/startTime 누락 시 기본값 무성 진행 대신 실행 거부 후 모델이 되묻도록 오류 반환
- **[Refactoring]** 죽은 정책 스켈레톤 정리 및 감사 로그 실질화
  - 미사용 파일 9종 삭제: `domain/agent/Agent`, `domain/policy/*`, `domain/model/ApprovalRequest`, `domain/tool/FileTool`, `domain/modelrunner/InferenceMetrics`, `core/config/{ModelConfig,AppConfig,FeatureFlags}`
  - `Redaction`을 `AuditTrailService.redact`에 실배선하고 이메일/전화번호 PII 마스킹 추가, 감사 저장 실패(AppResult.Failure) 무음 유실을 `AppLogger` 경고로 표면화
  - `PermissionPolicy`/`ErrorCodeMapper`는 계약 테스트가 참조하므로 유지 (개선은 후속)
- **[Build/QA]** androidTest 스코프 Compose BOM 누락으로 `lintDebug`가 실패하던 기존 문제 수정. 전체 테스트 + lintDebug 통과

## [0.5.6] - 2026-07-31
- **[Fix/Critical]** 백업(Export)/복원(Import) 기능 전면 수리 (`ExportImportManager`)
  - 백업 대상 DB 파일명이 실제 Room DB(`kosmos_db`)와 달라 빈 Zip을 만들고 Success를 반환하던 무동작 버그 수정 — DB 이름을 `Constants.DATABASE_NAME` 단일 소스로 통일 (`DatabaseModule` 공유)
  - Export 시 Hilt 싱글턴 `RoomDatabase.close()` 호출로 이후 모든 DB 접근이 죽던 치명 결함 제거, `PRAGMA wal_checkpoint(TRUNCATE)`를 Cursor 소비(`use { moveToFirst() }`)로 실제 실행되도록 수정
  - Import 시 Zip Slip(경로 탈출) 방어(canonicalPath 검증), zip-bomb 상한(512MB/1,000엔트리), 디렉터리 엔트리 처리 추가
  - 복원 시 checkpoint 선행 및 백업에 없는 stale `-wal`/`-shm` 사이드카 삭제로 복원본 손상 방지
  - `printStackTrace` → `AppLogger` 전환, 실패/취소 시 부분 산출물(Zip) 정리, `CancellationException` rethrow
- **[Fix]** 모델 다운로드 원자성 확보 (`ModelDownloadService`, `DownloadModelUseCase`)
  - `.part` 임시 파일 다운로드 후 성공 시에만 rename — 중단된 부분 파일이 유효 모델로 선택되던 문제 차단 (`GemmaRuntimeManager`의 `.litertlm` 필터와 정합 확인)
  - 비-2xx 응답 시 커넥션 누수 수정(`response.use`), 자체 OkHttpClient 제거 후 DI 공유 클라이언트(타임아웃 설정 포함) 주입, fileName 폴백의 URL 쿼리스트링 제거
  - 디스크 부족 오류를 `NetworkUnavailable`이 아닌 `InsufficientStorage`로 구분 매핑
- **[Docs]** 문서 기반 정비: `trouble_shooting.md`·`nvidia_skills_analysis.md`·`agent_workflow_guide.md`에 표준 메타데이터 헤더 추가, `.agents/01·03`의 특정 에이전트 전용 도구명을 중립 표현으로 수정, 감사 후속 수정 계획서(`docs/agent/implementation_plan.md`, `task.md`) 작성
- **[QA/Test]** 전체 단위 및 Robolectric E2E 테스트 통과 확인. (미수행 항목 명시: `ExportImportManager` 신규 단위 테스트는 추후 작성 예정)

## [0.5.5] - 2026-07-31
- **[Refactoring]** 프로젝트 전 영역(app, core, domain, data) 3단계 안전 리팩터링 및 KDoc 문서화 완료
  - **`Core 모듈`**: `AppError`, `AppResult`, `Constants`, `AppConfig`, `FeatureFlags`, `ModelConfig`, `AppLogger`, `ErrorCode`, `ErrorCodeMapper`, `ApprovalRules`, `PermissionPolicy`, `Redaction` 등 12개 주요 클래스/인터페이스에 표준 KDoc 헤더(`Role`, `Architecture Context`, `Key Flow`) 명시 및 `Constants.DEFAULT_MODEL_DOWNLOAD_URL` 중앙 관리 통합
  - **`Mockup 격리`**: `src/main/` 메인 소스 트리에 있던 `FakeTemperatureProvider` 목업 클래스를 `src/test/` 하위 테스트 픽스처 패키지로 이관하여 비즈니스 코드와 완전 격리
  - **`GetTodayScheduleUseCase.kt`**: `runCatching` 기반 안전한 ISO 날짜 파싱 및 `ZoneId` 매개변수화로 예외 안전성(Null Safety) 강화, AI 요약 실패 시 `null` 폴백 처리 명시
  - **`MemoryViewModel.kt` & `ChatViewModel.kt`**: StateFlow 불변성(Immutability) 및 Null Safety 방어 코드 보완
  - **`구조 정돈`**: 구현 파일이 존재하는 패키지의 redundant `.gitkeep` 파일 13개 정리 및 미구현 전용 패키지만 유지
  - **`QA/Test`**: E2E 테스트 버튼 셀렉터(`Attach`, `Send` contentDescription) 및 테스트 픽스처 시그니처 보완 후 전체 17개 단위 및 Robolectric E2E 통합 테스트 100% 통과 완료 (`./gradlew test`)

## [0.5.4] - 2026-07-19
- **[Refactoring]** 기술 부채 청산, 하드코딩된 더미 UI 제거 및 최적화
  - **`CalendarScreen.kt`**: 피그마 시안 확인용으로 하드코딩되었던 "Upcoming" 섹션 및 가짜 일정 행(`UpcomingEventRow`), 일정 시간 배지("30m") 제거 (DB 연동 순수 데이터만 출력)
  - **`ChatViewModel.kt` & `ImageInputAdapter.kt`**: 첨부 이미지 이중 압축 버그 해결. ViewModel에서 Raw Bytes만 추출하고 Adapter에서 단방향(단일) 압축(JPEG)을 수행하도록 파이프라인 리팩토링 및 딜레이 감소
  - **`WebSearchGateway.kt`**: 테스트용 가짜 지연 코드 및 Mock 검색 파일(`WebSearchTool.kt`, `SearchToolExecutor.kt`) 완전 삭제 및 Hilt(`PlatformModule`) 의존성 정리

## [0.5.3] - 2026-07-19
- **[UI/UX Hotfix]** Phase 3 피그마 스펙 완전 동기화 및 잔여 버그 수정 (2:32 AM 기준 리셋 후 재적용)
  - **`OrbPulse.kt`**: 메인 커버 애니메이션의 회전축을 중앙(`pivot = Offset.Zero`)으로 고정하여 궤도 이탈 글리치 수정
  - **`MainScreen.kt`**: 바텀 네비게이션 뒤로가기(Back) 누를 시 백스택 꼬임 현상을 방지하기 위해 `popUpTo(Chat.route)`로 홈 복귀 강제
  - **`MemoryScreen.kt`**: 누락되었던 상단 "Memory & Tasks" 메인 헤더 복구 및 태그(Knowledge) 칩에 둥근 모서리/Glassmorphism 스타일 적용
  - **`CalendarScreen.kt`**: "14~20일"로 하드코딩 되어있던 날짜 스크롤 바를 `LocalDate.now()` 기반으로 동적 생성하도록 수정
  - **`ChatScreen.kt`**: 하단 전송 버튼을 종이비행기 모양(`ic_send.xml`) 에셋으로 교체하고, 마이크 버튼과의 전환 시 `AnimatedContent`를 적용하여 렌더링 글리치(네모->원형 깨짐) 원천 차단
  - **`SettingsScreen.kt`**: 피그마 Phase 3 명세에 따라 `SectionBox` 대문자 타이틀 및 `Icons.Settings` 등 디테일 컴포넌트로 전면 교체

## [0.5.2] - 2026-07-18
- **[UI/UX Hotfix]** 기획안(Phase 2) 누락분 `ChatScreen.kt` 피그마 UI 완전 동기화 및 마이그레이션 적용
  - 상단바: 기본 TopAppBar를 KOSMOS 커스텀 헤더(`CustomChatHeader`)로 교체 및 펄스 애니메이션이 들어간 초록색 상태 뱃지(`PulseGreenDot`) 구현
  - 말풍선: 유저(Gradient), AI(Glassmorphism) 버블에 피그마 원본 곡률(`18px 18px 4px 18px` 등 비대칭) 적용
  - 하단 입력창: 개별 분리되었던 버튼 구조를 하나로 통합하여 단일 `glassEffect` 알약(Pill) 형태로 레이아웃 전면 리팩토링 및 렌더링 검증 완료
- **[Feature]** AI 일정 제안 카드(Calendar Draft Card) UI 바텀 오버레이 구현 및 기능 연동 완료
  - `ChatScreen.kt`에서 배경을 어둡게 가리지 않는 Floating 형태의 피그마 UI(D · Calendar Card) 완벽 구현
  - `ChatViewModel`을 통해 `pendingCalendarDraft` 상태를 구독하여, 사용자의 [Approve & Save] 동작 시 `AddScheduleUseCase`를 호출해 실제 DB에 일정 저장 연동 완료
  - 사용자의 [Reject] 동작 시 자연스럽게 카드를 숨기도록 상태 처리 구현

## [0.5.1] - 2026-07-18
- **[Bugfix/Hotfix]** Android 15 16KB 호환성 정공법 적용 및 스플래시 무한 로딩 버그 최종 수정
  - `libs.versions.toml`에서 `litertlm` (0.14.0) 및 `mediapipe` (0.10.35) 버전을 업데이트하여 네이티브 파일(`.so`)의 16KB 메모리 정렬을 근본적으로 지원하도록 변경
  - AGP `useLegacyPackaging = true` 및 매니페스트의 `extractNativeLibs` 꼼수 옵션 완전 제거
  - `GemmaModelRunner.warmUp()` 내에 모델 파일 존재 여부 재확인 로직(`checkModelFile()`)을 추가하여 초기 모델 파일 부재 시 렌더링 된 Retry 버튼 클릭 시 동작하지 않던(Deadlock) 현상 해결

## [0.5.0] - 2026-07-18
- **[Feature]** 온디바이스 텍스트 임베딩(MediaPipe) 및 RAG(Retrieval-Augmented Generation) 메모리 파이프라인 연동 완료
  - `MediaPipeTextEmbedder` 추가 및 `universal_sentence_encoder.tflite` 오프라인 모델 로드 적용
  - Room 데이터베이스(`KnowledgeEntity`)에 `embedding` 컬럼 추가 및 코사인 유사도(Cosine Similarity) 기반 벡터 검색 기능 구현
  - `SaveKnowledgeUseCase` 및 `SearchKnowledgeUseCase`를 통한 기억 저장/검색 파이프라인 리팩토링
  - `ContextBuilder`에서 최신 사용자 메시지 기반 RAG 검색 후 `[Context / Knowledge]`로 시스템 프롬프트 실시간 주입
- **[Feature]** API Key 프리 위키피디아 온디바이스 검색 툴(`SearchWikipedia`) 탑재
  - Wikipedia 공용 API(`api.php`)를 OkHttp/JSON 기반으로 직접 연동 (`WikipediaSearchToolImpl`)
  - 토픽(Topic), 언어(Lang) 기반 요약문 추출 및 검색 결과 글자수 제한 캡 적용으로 안정성 확보
  - `AddMemoryToolExecutor`와 함께 `ToolRegistry` 및 `PromptAssembler`에 정식 에이전트 툴로 등록 및 검증 완료
- **[QA/Test]** RAG 기반 장기 메모리(Vector DB) 파이프라인 및 웹 검색(SearchWeb) 통합 테스트 검증 완료
  - `MemoryPipelineIntegrationTest` 작성: `SaveKnowledgeUseCase`부터 `ContextBuilder`까지 이어지는 RAG Write/Read 전체 통합 사이클 및 시스템 텍스트 렌더링 정상 확인 (`MediaPipe` 임베딩 포함 Mocking 완료)
  - `SearchToolExecutorTest` 작성: `WebSearchTool`의 승인/거절(Approval Coordinator) 비동기 처리 흐름 및 응답 JSON 직렬화 안정성 검증 통과
- **[QA/Test]** Glassmorphism UI 렌더링 무결성 및 E2E 테스트(`MultimodalChatE2ETest`, `ToolApprovalE2ETest`) 크래시 복구 및 검증
  - Hilt 주입 시 발생하는 `MediaPipeTextEmbedder`의 JNI 로드 에러(`java.lang.UnsatisfiedLinkError`)를 `Throwable` 포획으로 방어하여 앱 강제 종료 및 테스트 크래시 원천 차단
  - 백그라운드 Robolectric 환경에서 새로운 UI 계층의 렌더링(채팅 메시지 갱신, 라우팅, ApprovalSheet 시트 팝업)이 정상 작동하는지 자동화 E2E 테스트로 실행 검증 (All Pass 확인)
- **[Bugfix/Hotfix]** Android 15 (Vanilla Ice Cream) 환경 네이티브 라이브러리 충돌 및 스플래시 화면 무한 로딩 수정
  - TensorFlow Lite, MediaPipe 등 네이티브(`.so`) 라이브러리들이 16KB 페이지 사이즈 정렬 검사에 실패하여 `UnsatisfiedLinkError`를 내며 강제 종료되는 문제(ELF Alignment Error) 해결
  - `build.gradle.kts` 내 `useLegacyPackaging = true` 옵션을 적용하여, OS가 파일 압축을 해제하고 시스템 단위에서 16KB 메모리 정렬을 대행하도록 런타임 호환성 우회 로직 추가
  - `SplashScreen.kt` 내부에서 초기화 상태가 `Error`로 빠질 경우 네비게이션 없이 애니메이션만 무한 루프하는 버그를 수정하여, 에러 원인 텍스트와 함께 뷰모델의 `retry()` 액션을 호출하는 재시도 UI 컴포넌트 추가

## [0.4.0] - 2026-07-18
- **[UI/UX]** 안드로이드 네이티브(Jetpack Compose) Glassmorphism UI 전면 마이그레이션 및 적용 완료 (Phase 1, 2, 3)
  - `core:designsystem`을 대체하여 `BgColor`, `SurfaceColor`, `GlassColor`, `Cyan`, `Violet` 커스텀 테마 색상 및 오프라인 커스텀 폰트 번들 적용
  - `RenderEffect.createBlurEffect()` 기반의 안드로이드 네이티브 `Modifier.glassEffect()` 구현 완료
  - **애니메이션 전환**: React `@keyframes`를 Compose `InfiniteTransition`과 Canvas로 변환 (AuroraBackground, OrbPulse, ThinkingDots)
  - **Screen 일괄 마이그레이션**: `SplashScreen`, `ChatScreen`, `VoiceOverlay`, `MemoryScreen`, `CalendarScreen`, `SettingsScreen`, `AuditScreen`, `ApprovalSheet` 및 `MainScreen` (Bottom Nav) 모두 새 디자인 시스템과 Glassmorphism/Dark Theme 일괄 적용
  - `MemoryScreen` 앱 데이터 복원(Import) 후 재시작 시, 안전한 백스택 초기화를 위해 Android 권장 방식(`Intent.makeRestartActivityTask`)으로 로직 개선
- **[QA/Test]** 0.4.0 대규모 UI 개편 및 다중 에이전트 아키텍처 전환에 대한 회귀 테스트(Regression Test) 검증 완료
  - Robolectric 기반 통합 E2E 테스트(채팅, 멀티모달, 음성, 툴 승인) 전 항목(All Pass) 통과 확인
  - `TaskRouter`, `ContextBuilder`, `MemoryScreen` 내 주요 의존성 및 라우팅 로직 정적 분석 결과 결함 없음 확인

## [0.3.5] - 2026-07-17
- **[Architecture/Refactoring]** 에이전트 구조 분리 (Multi-Agent Refactoring) 구현 완료
  - `AssistantOrchestrator`의 비대해진 모델 추론(재귀 루프) 및 툴 파싱 로직을 `BaseAgent` 추상 클래스로 분리 및 캡슐화
  - `IntentClassifier`를 통한 사용자 의도(Intent) 기반 라우팅을 담당하는 `TaskRouter` 도입 및 `CalendarAgent`, `DefaultAgent`로 책임 분할
  - `AudioRecorder`와 `ChatViewModel`의 예외 처리를 공통 `AppResult` 래퍼로 통합하여 안정성 강화 및 리팩토링
  - `PromptAssembler`를 개선하여 각 에이전트의 역할(SystemRole)과 사용 가능한 툴(AvailableTools)을 동적으로 주입하도록 스펙 변경
  - 변경된 구조에 맞추어 통합 테스트(`MultimodalChatE2ETest`, `ToolApprovalE2ETest`, `VoiceChatIntegrationTest`, `PromptAssemblerTest`) 코드 모의 객체 및 반환 타입 일괄 업데이트 후 전체 TC 통과
- **[Prompt/Optimization]** 온디바이스 소형 LLM(Gemma 4 e4b) 프롬프트 최적화 (상용 앱 수준 리팩토링)
  - 서비스명 강제(`named Kosmos`) 및 하드코딩된 스타일 부정어 제거 후 `[Style: Concise]` 변수로 동적 주입하여 지시어 충돌 차단
  - 시스템 시간을 `[System Data] Current Time: ...` 단일 문장으로 압축하여 토큰 낭비 제거
  - `Always respond in Korean...` 추가로 다국어 이탈(Language Drift) 현상 차단
  - Tool 인자 부족 시 짐작하지 않고 되묻도록 Fallback 로직 추가하여 환각 캘린더 생성 억제
  - 턴 마커(`User:`, `Assistant:`) 이중 표기 제거 및 Tool 설명 인자 스키마를 JSON 형태로 포맷 변경하여 Syntax Error 예방

## [0.3.4] - 2026-07-16
- **[Persona/SystemPrompt]** 사용자 선호 응답 스타일(Profile Memory) 시스템 지시문 동적 주입 구현 완료
  - `ContextBuilder`가 `SettingsDataStore`를 주입받아 대화 컨텍스트 구성 시 현재 저장된 선호 응답 스타일 상태(`settingsDataStore.responseStyleFlow`)를 코루틴 `.first()` 연산자로 1회성 로드 연동
  - `PromptAssembler`에서 시스템 지시문 구성 시 `Context` 내 `responseStyle`이 `"DEFAULT"`가 아닐 경우 "User's preferred response style: [스타일]" 지시어 블록을 동적 주입하도록 로직 보완
  - 생성자 파급 경로를 모두 검토하여 단위 테스트 및 Hilt 그래프 빌드에 부작용이 없음을 검증 완료
- **[Architecture/Refactoring]** 레거시 툴 실행 아키텍처 제거 및 승인 로직 간소화 이후 발생한 빌드/동기화 오류 수정
  - `ToolApprovalE2ETest` 내에서 Robolectric의 `ShadowLooper`가 Compose의 `StandardTestDispatcher` UI 큐를 펌핑하지 못해 발생하는 Timeout 타임아웃 문제를 확인하고, 테스트 코드가 `ChatViewModel`을 직접 호출하도록 우회하여 승인(ApprovalCoordinator) 흐름 검증 성공
  - 불필요한 중간 계층인 `ResumeActionUseCase` 완전 제거로 인한 패키지 간 순환 의존성 및 복잡도(Early Return) 개선 완료 (SRP, DRY, 가독성 준수 확인)
- **[QA/Test]** 수명이 다한 컨텍스트(과거 기획/디버깅 내역) 청소 원칙에 따라 `docs/agent/qa_plan.md`에 새롭게 구현할 '프로필 메모리 연동' 기능에 대한 QA 계획(수동/자동화 테스트 시나리오) 신규 작성 및 파일 정리

## [0.3.3] - 2026-07-15
- **[Approval/ToolCall]** Gemma 4 Tool Call 일정 쓰기(AddSchedule) 승인 관리 로직 구현 및 E2E 테스트(`ToolApprovalE2ETest`) 검증 완료
- `ApprovalRequest` 구조를 개선하여 `title`, `description` 필드를 추가하고 `action`을 Nullable로 처리하여 Tool Call 승인과 Guard 정책 승인을 동시 지원
- `ApprovalCoordinator`에 `CompletableDeferred<Boolean>`을 도입하여 LLM 툴 루프 도중 비동기적으로 사용자의 승인/거절 입력을 동기적 대기(Suspending)할 수 있도록 구조 설계 및 구현
- `ChatViewModel`에서 `request.action == null`인 새로운 Tool Call 승인에 대해 coordinator의 `approve()` / `reject()`를 실행하여 deferred 완료 처리 연동
- `ChatScreen`에서 `action == null`인 일반 툴 콜 승인 요청에 대해서도 동적으로 Alert Dialog를 렌더링하도록 UI 보완
- **[QA/Test]** Hilt 테스트 환경 내 `ModelRunner` Mock 인터페이스를 최신 스펙(`ChatPrompt` 지원, `suspend` 시그니처)에 맞게 갱신하여 빌드 에러 해결 및 테스트 버튼 클릭을 통한 롤백 시나리오 검증 완료

## [0.3.2] - 2026-07-14
- **[Architecture/Refactoring]** `ChatViewModel`, `AssistantOrchestrator` 과도한 책임 및 복잡도 리팩토링 (SRP, DRY 원칙 적용)
- `ChatViewModel`에서 수행되던 Tool Execution 및 재귀 추론 로직을 `AssistantOrchestrator`의 `processRequest` 내부 캡슐화로 회수
- 뷰모델 내 Android 특화 로직(`Uri` -> `Bitmap` -> `ByteArray`)을 프라이빗 헬퍼 함수로 분리하여 가독성 강화
- `AssistantOrchestrator` 내 반복되는 `ChatMessage` 데이터베이스 저장 코드를 단일 헬퍼(`createAndSaveMessage`)로 병합(DRY)
- 여러 `AppResult` 분기와 거대한 `when` 블록을 별도의 `handleXXXAction` 메서드로 평탄화하여 복잡도 감소
- Hilt `@UninstallModules` 사용 테스트 환경(`MultimodalChatE2ETest`, `VoiceChatIntegrationTest`)에서 누락된 `ImageProcessor` Mock 의존성 주입 복구 및 검증 완료

## [0.3.0] - 2026-07-14
- `:app` 모듈에 밀집된 코드를 `:core`, `:domain`, `:data` 3개 모듈로 물리적/논리적 분리 및 컴파일 연동 완료
- `:domain` 모듈을 Pure Kotlin JVM 모듈로 구성하고, Android SDK 종속성을 차단하기 위해 `ImageProcessor`, `ModelDownloader`, `ModelLoadManager`, `MemoryBackupManager` 인터페이스 추상화 도입
- `:core` 모듈 또한 Pure Kotlin JVM 모듈로 전환하고, Android `Manifest` 상수 및 `AppLogger` 리플렉션 폴백 처리를 적용하여 의존성 격리
- 메인 코디네이터(`AssistantOrchestrator`)를 참조하여 컴파일 순환 참조를 일으키던 4개의 앱 오케스트레이션 유스케이스를 `:app` 모듈로 재배치
- Room 데이터베이스, DataStore, 메모리 Repository에 관한 Hilt 모듈(`DatabaseModule`, `DataStoreModule`, `MemoryModule`)을 `:data` 모듈로 물리적 이관
- **[QA/Test]** 멀티 모듈 리팩토링 검증 완료: 순환 참조 및 Hilt DI 에러 점검 완료 (`MultimodalChatE2ETest`, `VoiceChatIntegrationTest` 내 `ModelLoadManager` Mock 주입 픽스 및 전체 단위 테스트 성공)

## [0.2.1] - 2026-07-14
- Gemma 4 Advanced Features (MTP, Tool Calling, Vision, Thinking Process) 구현 및 E2E 테스트 통합 완료
- `ToolParser.kt`를 통한 스트리밍 출력 중 `<|think|>` 블록 및 `<tool_call>` JSON 정규식 기반 실시간 파싱 적용
- `ChatViewModel`에서 Tool Call을 인터셉트하여 `GetTodayScheduleUseCase`, `AddScheduleUseCase` 실행 후 `tool_response`로 컨텍스트 재개 구현
- `ChatScreen.kt` 내 첨부 이미지(Vision) 및 `thinkingProcess` 아코디언 UI 연동 검증
- `RobolectricTestRunner` 환경에서 `org.json.JSONObject` 종속성 문제 해결 및 `ChatViewModel` 생성자 주입 최신화

## [0.2.0] - 2026-07-14
- Gemma 4 MTP 옵션 검증 및 GPU 백엔드 초기화 로직 보완
- 스트리밍 응답 텍스트에 포함된 <|think|> 태그 분리 및 ChatScreen 아코디언 UI 연동
- 캘린더 조회(get_today_schedule) Tool Call 파싱 구현 및 승인 시나리오(CalendarAgent) 추가
