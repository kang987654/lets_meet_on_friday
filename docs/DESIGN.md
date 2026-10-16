---
version: v3.0
name: KOSMOS
description: 기기 안에서만 동작하는 개인 비서 앱의 UI 기준. "덜렁대는 나 대신 꼼꼼히 챙기고, 고민될 땐 먼저 제안해 주는 다정한 비서. 화면은 조용하게, 말은 따뜻하게." 하늘색(#5BC2E7) 하나를 브랜드로, 라이트·다크 두 테마를 같은 무게로 다룬다(ADR-005). 장식(유리·그라데이션·발광)이 아니라 위계와 결과로 말한다.
changelog: "v3.0 (2026-10-16, 0.38.0 M3) — v2 의 Tailwind 계열 다크 팔레트·유리 질감·이모지 아이콘을 폐기하고 글자용 색 단계·하다 라운드·Phosphor 로 다시 정했다. 근거는 docs/plans/0.38.x-ui-ux-overhaul.md(D1~D8)·docs/design/audit-0.38.md."
implementation:
  tokens: "app/src/main/java/com/kosmos/app/ui/theme/Color.kt (KosmosColors) — 0.38.1 M1 에서 아래 이름으로 교체"
  access: "KosmosTheme.colors.<토큰> — 색상 리터럴 직접 사용 금지(AGENTS §4-6)"
  type: "app/src/main/java/com/kosmos/app/ui/theme/Type.kt (AppTypography) — 화면에서 fontSize 직접 지정 금지"
  mode: "ThemeMode(SYSTEM/LIGHT/DARK), DataStore key `theme_mode`, 기본 SYSTEM / 글꼴 모드(앱 글꼴·시스템 글꼴, 기본 앱 글꼴)"

# 색 — 글자로 쓰는 토큰은 모두 surface 위 4.5:1 이상(괄호 안 대비, surface 기준)
colors:
  bg: "#F5FBFE"
  surface: "#FFFFFF"
  surfaceSunk: "#EBF1F4"          # 분할 버튼 바탕·입력 바탕 등 한 단계 가라앉은 면
  border: "#D9E6EC"               # hairline — 하늘빛(회색은 흰 박스 경계가 탁해 보였다, 0.38.1 M3)
  textPrimary: "#222729"          # 15.1
  textSecondary: "#515659"        # 7.4
  textMuted: "#676D6F"            # 5.3 — 날짜·태그·보조 라벨
  brand: "#5BC2E7"                # 면 전용(버튼·선택 표시·진행 바). 글자색 금지(2.0)
  brandContainer: "#D7F4FF"       # 옅은 브랜드 면 — 사용자 말풍선·선택된 칩
  brandContainerEnd: "#BDEAFA"    # 사용자 말풍선 그라데이션 끝(같은 색상, 조금 더 맑게) — textPrimary 11.7
  onBrand: "#222729"              # brand 면 위 글자(7.4) — 흰 글자 금지(2.5)
  brandText: "#006E8B"            # 링크·아이콘·강조 글자(5.8)
  dangerText: "#BA3630"           # 5.7
  successText: "#227C45"          # 5.2
  warningText: "#9D6300"          # 5.0
  scrim: "rgba(17, 22, 24, 0.48)"

colors-dark:
  bg: "#111618"
  surface: "#1A1F21"
  surfaceSunk: "#161A1C"
  border: "#2F3436"
  textPrimary: "#E6ECEF"          # 14.0
  textSecondary: "#BFC5C8"        # 9.5
  textMuted: "#93999C"            # 5.8
  brand: "#5BC2E7"
  brandContainer: "#133B49"
  brandContainerEnd: "#184C5E"    # textPrimary 7.9
  onBrand: "#111618"              # 9.0
  brandText: "#70CCEE"            # 9.2
  dangerText: "#F7857D"           # 6.8
  successText: "#65C98C"          # 8.2
  warningText: "#EBB25F"          # 8.8
  scrim: "rgba(0, 0, 0, 0.56)"

# 글자 — 하다 라운드(Hada Round, OFL) 400/500/700. 시스템 글꼴 모드에서는 같은 크기·굵기로 기기 글꼴. 단위 sp.
typography:
  number:   { fontSize: 28, lineHeight: 32, fontWeight: 700, use: "결과 카드의 시각·값(1:56, 134). tabular 숫자" }
  title:    { fontSize: 22, lineHeight: 30, fontWeight: 700, use: "상단 바 화면 제목" }
  subtitle: { fontSize: 17, lineHeight: 24, fontWeight: 500, use: "카드·시트 제목, 알림·일정 이름" }
  body:     { fontSize: 16, lineHeight: 24, fontWeight: 400, use: "채팅·메모 본문" }
  caption:  { fontSize: 14, lineHeight: 20, fontWeight: 400, use: "설명·도움말" }
  label:    { fontSize: 13, lineHeight: 18, fontWeight: 500, use: "날짜·태그·메타·칩" }
  button:   { fontSize: 15, lineHeight: 20, fontWeight: 500, use: "버튼 (주 버튼은 700)" }

rounded:
  sm: 8px       # 칩, 작은 배지, 분할 버튼 안쪽
  md: 12px      # 버튼, 입력, 카드, 목록 묶음, 분할 버튼 바탕
  lg: 18px      # 말풍선(보낸 쪽 모서리 4px)
  xl: 24px      # 바텀시트 윗모서리
  full: 9999px  # 원형 버튼

spacing:        # 4 기반
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px      # 화면 좌우 여백·카드 안쪽
  lg: 24px      # 묶음 사이
  xl: 32px
  xxl: 48px

icons:
  set: "Phosphor (MIT) — res/drawable/ic_* VectorDrawable, 쓰는 것만"
  sizes: { inline: 20, default: 24, large: 32 }
  weights: "기본 Regular / 선택·켜짐 Fill / 결과 카드 대표 아이콘(알림·일정·기억) Duotone"
  color: "기본 textPrimary, 강조 brandText, 위험 dangerText — 아이콘만 쓰는 버튼은 반드시 contentDescription"
---

## Overview

**성격 한 줄 (D1)**: 덜렁대는 나 대신 꼼꼼히 챙기고, 고민될 땐 먼저 제안해 주는 다정한 비서. **화면은 조용하게, 말은 따뜻하게.**

| 층 | 뜻 | 화면에서 |
|---|---|---|
| 화면 | 조용하고 정돈됨 | 장식 최소, 결과 카드 중심, 강조색은 행동·선택에만 |
| 일 처리 | 꼼꼼함 | 숫자·시각은 항상 정확하게 크게, 놓친 것을 먼저 알림 |
| 고민 | 먼저 제안 | 답 끝에 다음 행동 칩 한두 개, 강요하지 않음 |
| 말투 | 밝고 친근 | 짧게, 감탄은 결과 뒤 한마디(0.38.4) |

**하지 않는 것** (0.38.0 감사의 "AI 티"): 유리·블러 질감, 다른 색상끼리 넘어가는 그라데이션(특히 하늘→보라)·그라데이션 글자, 발광·오로라 배경, 이모지·글자 기호 아이콘, 모든 요소에 같은 무게의 둥근 테두리 카드, 제목 위 작은 회색 라벨(eyebrow), 보라+시안 조합.

**참고 패턴 (D2)** — 겉모습은 빌리지 않고 문제를 푸는 방식만:
- 토스: 화면 하나에 핵심 하나, 큰 숫자·짧은 문장의 위계, 친근하고 짧은 UX 문구
- 카카오톡: 말풍선 밀도·날짜 구분선, 카드형 메시지를 대화 흐름에 끼우기
- Things 3: "오늘" 중심, 완료의 손맛, 조용한 목록
- 클로바노트: 요약과 근거(원문 위치)를 함께

---

## Colors

### 브랜드
- **하늘색 `brand` 는 면에만** — 주 버튼, 선택된 탭·칩 표시, 진행 바, 토글 켜짐. 글자·아이콘으로 쓰지 않는다(흰 배경 위 2.0:1).
- 같은 색상의 **글자 단계 `brandText`** 가 링크·강조 글자·강조 아이콘을 맡는다(라이트는 짙게, 다크는 밝게).
- **`brandContainer`** 는 옅은 브랜드 면 — 사용자 말풍선, 선택된 칩 바탕, 결과 카드의 Duotone 면.
- 주 버튼 글자는 `onBrand`(짙은 글자). 하늘색 위 흰 글자는 대비 미달이라 쓰지 않는다.

### 표면
- `bg` < `surface` 두 단계와 한 단계 낮은 `surfaceSunk`. 위계는 그림자가 아니라 **면의 밝기 차 + hairline `border`** 로 만든다.
- 다크 바탕은 남색이 아니라 하늘색 기운이 아주 약한 회색 — 브랜드 하늘색이 두 테마에서 같은 앱으로 보이게.

### 글자
- `textPrimary` 본문·제목, `textSecondary` 설명, `textMuted` 날짜·태그·메타. 셋 다 라이트·다크 모두 surface 위 4.5:1 이상.
- 상태 글자는 `dangerText`·`successText`·`warningText`. 상태를 색만으로 전하지 않는다 — 아이콘이나 문구를 함께.

### 규칙
- 화면 코드는 토큰만(AGENTS §4-6). 새 색이 필요하면 이 문서와 `Color.kt` 에 토큰으로 추가하고 대비를 잰다.
- 다른 테마(예: 보라 계열)는 토큰 묶음을 하나 더 만드는 방식으로만 — 화면 코드를 바꾸지 않는다(추후 후보).

---

## Typography

- **하다 라운드** 400/500/700 세 굵기(앱에 원본 탑재, 서브셋 없음). Light·Black 은 쓰지 않는다.
- **설정의 "앱 글꼴 / 시스템 글꼴"** — 시스템 글꼴 모드에서는 기기에서 고른 글꼴을 같은 크기·굵기 단계로 따른다. 두 모드 모두 줄 높이·최소 폭을 글꼴 모양에 기대지 않는다.
- 단계는 위 7개뿐. 화면에서 `fontSize` 를 직접 쓰지 않는다. 숫자는 `number` 단계와 tabular 숫자.
  구현: `ui/theme/Type.kt` 가 7단계를 Material3 역할 15개에 모두 싣는다(표는 그 파일 KDoc) — 화면은 `MaterialTheme.typography.<역할>`.
  `BasicTextField` 는 기본 글자 스타일을 물려받지 않으니 `textStyle = MaterialTheme.typography.<역할>.copy(...)` 로 준다.
- 예외: 문서 뷰어 본문(`FlowView`·`SheetView` 의 셀)은 확대 배율을 곱하는 읽기용 글자라 직접 크기를 쓴다(글꼴은 단계와 같다).
- 한자는 글꼴에 없어 시스템 글꼴로 섞여 보인다(알려진 한계).
- 위젯(Glance)은 Compose 단계를 쓸 수 없어 시스템 글꼴 + 직접 크기(같은 수치를 따른다).
- 라이선스: SIL OFL 1.1 — 고지는 `app/src/main/assets/licenses/HadaRound-OFL.txt`(구성 글꼴 저작권 포함).

---

## Layout

- 모바일 단일 컬럼, 좌우 여백 `md`(16). 묶음 사이 `lg`(24), 묶음 안 `xs`~`sm`.
- **목록은 카드 더미가 아니라 행** — 한 묶음(`surface` + `border` + `rounded.md`) 안에 행을 hairline 으로 나눈다. 행 높이는 내용 + 세로 12, 행 끝 동작은 48dp 아이콘 버튼.
- **카드는 결과에만** — 알림·일정·기억처럼 "처리된 결과"가 카드다. 설정 섹션·메모 하나하나를 카드로 감싸지 않는다.
- 제목 위 작은 라벨을 두지 않는다. 섹션이 필요하면 `subtitle` 단계 제목으로.

---

## Elevation

- 그림자를 쓰지 않는다. 깊이는 면 밝기(`bg`→`surface`)와 `border`.
- 바텀시트·대화상자만 위로 뜬다 — `surface` + `rounded.xl` 윗모서리 + `scrim`.
- 유리·블러·반투명 면은 쓰지 않는다(v2 의 `glass*` 토큰 폐기).

---

## 시스템 바와 화면 틀

- **edge-to-edge** — 상태바·내비게이션 바도 앱 색으로 칠해진다(0.38.1 M2, 매니페스트 옛 테마 교체). 바 아이콘 밝기는 테마를 따른다.
- 인셋은 **화면 틀이 맡는다** — 상단 바(TopAppBar)가 상태바, 입력바·하단 버튼이 내비게이션 바·키보드(IME). 평범한 Row/Column 을 슬롯에 넣으면 바에 깔린다(AGENTS §2-③).
- **모든 하위 화면에 같은 상단 바** — 왼쪽 ← 아이콘 버튼(48dp, contentDescription "뒤로") + `title` 제목. 글자 "뒤로" 버튼은 쓰지 않는다. 시스템 뒤로·예측 뒤로는 그대로(`AppNavHost.navigateBack` 루트 가드).
- 채팅(홈)은 상단 바 왼쪽이 메뉴(드로어).

---

## Components

**버튼**
- 주 버튼: `brand` 면 + `onBrand` 글자 700, `rounded.md`, 높이 48. 한 화면(시트)에 하나.
- 보조 버튼: `surface` + `border` + `textPrimary`.
- 글자 버튼(링크형): `brandText`, 위험 동작은 `dangerText`. 터치 영역 48dp.
- 아이콘 버튼: 48dp 영역, 아이콘 24, `contentDescription` 필수. 글자 기호(✕ › ▾ +)를 버튼으로 쓰지 않는다.

**분할 버튼(탭 전환)** — M3 `SingleChoiceSegmentedButtonRow` 를 토큰으로 테마. `surfaceSunk` 바탕 + 선택 칸 `surface`, 글자 `button`. (자체 `GlassSegmentedControl` 대체)

**입력** — `surface`(또는 `surfaceSunk`) + `border` + `rounded.md`, 높이 ≥48, 포커스는 `brand` 테두리 1.5.

**말풍선** — 사용자: `brandContainer`→`brandContainerEnd` 같은 색 그라데이션(대각선) + 옅은 테두리 — 개성 자리(아래 "개성"), `rounded.lg`(보낸 쪽 아래 모서리 4). 비서 답의 모양(맨 문장 / 카드)은 0.38.2 구조 결정(D6)을 따른다.

**결과 카드** — `surface` + `border` + `rounded.md`. 구성: Duotone 아이콘 + `label` 종류(brandText) / `subtitle` 이름 / `number` 값. **보기 전용** — 카드 안에 바로 실행되는 버튼을 두지 않고, 누르면 시트가 열려 그 안에서 바꾼다(D6 ②). 카드는 그 순간의 사진이 아니라 대상의 **현재 상태**(미룸·취소함·울렸음)를 보여 준다.

**칩** — 답 칩(누르면 그 글자로 한 턴)과 행동 칩(앱 기능으로 바로). `rounded.full`, 높이 36, `border`. **마지막 비서 답에만** 보이고, 고르거나 새 턴이 시작되면 사라진다(D6 ③). 칩은 코드 규칙으로 만들고 칩을 위해 추론을 더 하지 않는다.

**승인·수정 시트** — 모든 승인(일정·리마인더·메모·프로필)과 결과 카드의 수정은 **같은 모양의 바텀시트**(D6 ①). 순서: `subtitle` 무엇을 할지 → 구조화된 내용(시각은 `number`, 제목, 메모) → 안내 줄(정확 알람 등, `caption`) → [취소 보조][주 버튼]. 채팅 안 떠 있는 승인 카드는 쓰지 않는다.

**목록 행** — `body` 내용 + `label` 메타(textMuted) + 끝 동작(48dp 아이콘). 할 일 완료는 왼쪽 원형 체크(48dp)만(0.36.2).

**스낵바** — 일시 안내·되돌리기. 결정이 필요한 것만 대화상자.

**빈 화면·오류** — 아이콘(32, Light) + 한 줄 + 행동 버튼. 일러스트 없음.

**개성 (두 곳)** — 화면이 밋밋해지지 않게 정해진 자리에만 둔다(사용자 결정 2026-10-16, 한 곳 제한 해제).
① **사용자 말풍선** — 같은 하늘색 안에서 밝기만 넘어가는 그라데이션(보라 금지). ② **브랜드 마크** — 항성과 그 둘레의 행성·궤도(KOSMOS). 스플래시와 음성 입력 중 상태(0.38.3). 새 자리를 늘릴 때는 이 목록부터 고친다.

---

## Icons

- **Phosphor** 한 세트(MIT). 다른 세트·이모지와 섞지 않는다.
- 크기: 글자 옆 20, 기본 24, 빈 화면 32.
- 굵기: 기본 **Regular**, 선택된 탭·켜진 상태 **Fill**, 결과 카드 대표 아이콘(알림·일정·기억) **Duotone**(면은 `brandContainer` 계열이 되도록 확인). 빈 화면은 Light.
- 색: `textPrimary` 기본, 강조 `brandText`, 위험 `dangerText`.

---

## Motion (방향 — 상세는 0.38.3)

- 짧게(대부분 150–250ms, 시트·화면 전환 ≤300ms), 감속 곡선(ease-out). 값은 `ui/theme/Motion.kt` 토큰.
- 결과가 생길 때(카드 등장, 할 일 완료 체크)만 작게 움직인다. 같은 등장 효과를 모든 요소에 뿌리지 않는다.
- 시스템 "애니메이션 제거"를 존중한다 — 즉시 전환 또는 짧은 페이드.
- 계속 도는 애니메이션은 상태가 진행 중일 때만(응답 대기·음성 입력). 장식용 무한 애니메이션은 쓰지 않는다(발열에 민감한 앱).

---

## Voice (방향 — 상세는 0.38.4)

- **해요체**, 짧은 문장, 결과를 먼저. "1시 56분에 알려 드릴게요."
- 다정한 한마디는 결과 뒤에 한 번. 감탄·느낌표 남발 금지.
- 화면 문구에 이모지를 쓰지 않는다. 내부 이벤트 이름(`APPROVAL_GRANTED` 등)을 그대로 보여 주지 않는다.
- 비서 답의 말투(모델 출력)는 프롬프트 영역 — 실험실 실측 후에만 바꾼다(§2-⑤). 말투 롤모델은 내부 참고로, 실존 인물의 이름·말버릇을 문구나 프롬프트에 쓰지 않는다.

---

## Accessibility

- 글자 대비 4.5:1(큰 글자 3:1) — 위 토큰을 쓰면 자동으로 지켜진다. 새 조합은 재서 넣는다.
- 터치 영역 48×48dp, 사이 8dp.
- 눌리는 것은 버튼 역할(`Button`/`IconButton`/`Role.Button`)과 설명을 가진다. 글자에 `clickable` 만 붙이지 않는다.
- 글자 크기는 sp — 시스템 글자 크기 1.3배에서 잘리지 않게(실기기 확인).
- 색만으로 상태를 전하지 않는다.

---

## Responsive

- 휴대폰 세로가 기준. 가로·태블릿은 잘리지 않는 것까지만 보장(창 크기 클래스 대응은 범위 밖).
- 키보드: 입력바는 키보드 바로 위(`adjustResize` + IME 인셋, 0.36.1).

---

## 유지

- 이 문서가 UI 의 유일한 기준이다. 새 화면은 이를 따르고, 바꿀 때는 이 문서부터 고친다.
- 회귀는 Roborazzi 기준 이미지로 잡는다 — `ScreenCatalogTest` 14장면 × 라이트·다크, 기준은 `app/src/test/screenshots/`(커밋).
  `./gradlew test` 가 비교하고(`gradle.properties` 의 `roborazzi.test.verify`) 다르면 실패, 차이 이미지는 `app/build/outputs/roborazzi/`(`_compare`·`_actual`).
  의도한 변경은 전후 비교를 사용자에게 보이고 확인받은 뒤 `./gradlew :app:recordRoborazziDebug` 로 다시 기록한다. 시각·시간대는 테스트가 고정한다(`LocalClock`).
- 검색으로 지키는 지표: 테마 밖 하드코딩 색 0, 이모지·글자 기호 아이콘 0, 테마 밖 직접 `fontSize` 0.

## Known Gaps

- 비서 답의 모양(맨 문장 / 카드), 드로어·메모·설정·문서 홈의 구조 — 0.38.2 에서 정해 이 문서에 반영
- 움직임 수치, 브랜드 마크 그림 — 0.38.3
- 말투 상세 가이드 — 0.38.4
- 보라 계열 등 추가 테마 — 추후 후보
- 태블릿·가로 전용 배치
