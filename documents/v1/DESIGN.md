---
version: v1.2
name: Local Friday
description: 오프라인 우선 개인 AI 비서 앱을 위한 차분하고 신뢰감 있는 모바일 UI 시스템. 메인 컬러는 Sky Blue (#5bc2e7)이며, 이는 친절함·명료함·도움의 느낌을 전달한다. 화면은 밝고 깨끗한 캔버스 위에 얇은 경계선과 절제된 강조를 사용하며, 과도한 그림자보다 정보 구조와 여백으로 계층을 만든다. 대화형 인터페이스는 가볍고 빠르게 느껴져야 하며, 승인·권한·오류와 같은 중요한 순간은 시각적으로 분명하되 위협적이지 않게 표현한다.
colors:
  primary: "#5BC2E7"
  primary-strong: "#39AED8"
  primary-soft: "#EAF8FD"
  ink: "#1F2A33"
  body: "#42515C"
  muted: "#758390"
  canvas: "#F7FBFD"
  surface-card: "#FFFFFF"
  hairline: "#D9E6EC"
  success: "#2FA36B"
  warning: "#F4A62A"
  danger: "#E35D5D"
  info: "#5BC2E7"
  on-primary: "#FFFFFF"
  on-dark: "#FFFFFF"
  overlay: "rgba(20, 30, 38, 0.52)"

typography:
  display-lg:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 32px
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: -0.6px

  heading-lg:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 24px
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: -0.4px

  heading-md:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: -0.3px

  body-lg:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: 0px

  body-md:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: 0px

  body-sm:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0px

  button:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1
    letterSpacing: 0px

  label:
    fontFamily: "'Pretendard', 'Inter', sans-serif"
    fontSize: 12px
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: 0px

rounded:
  none: 0px
  sm: 8px
  md: 12px
  lg: 18px
  xl: 24px
  full: 9999px

spacing:
  xs: 4px
  sm: 8px
  md: 12px
  base: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 72px

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: 12px 18px
    border: "none"

  button-secondary:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: 12px 18px
    border: "1px solid {colors.hairline}"

  input:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.md}"
    padding: 14px 16px
    border: "1px solid {colors.hairline}"

  card:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: 16px
    border: "1px solid {colors.hairline}"

  chat-bubble-user:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 12px 14px
    border: "1px solid transparent"

  chat-bubble-assistant:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    typography: "{typography.body-md}"
    rounded: "{rounded.lg}"
    padding: 12px 14px
    border: "1px solid {colors.hairline}"

  status-card:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.body}"
    rounded: "{rounded.lg}"
    padding: 14px 16px
    border: "1px solid {colors.hairline}"

  approval-sheet:
    backgroundColor: "{colors.surface-card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.xl}"
    padding: 20px
    border: "1px solid {colors.hairline}"

  warning-badge:
    backgroundColor: "#FFF5E6"
    textColor: "{colors.warning}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: 6px 10px
    border: "none"
---

## Overview

Local Friday의 UI는 “개인용 AI 비서”라는 성격에 맞게 **친절하고 조용하며 신뢰감 있는 인터페이스**를 지향한다.  
이 앱은 생산성 도구이면서도 매일 반복적으로 마주하는 개인 공간에 가깝다. 따라서 시각 언어는 과장된 브랜드 쇼케이스가 아니라, 사용자가 오래 봐도 피로하지 않고 중요한 순간에만 적절히 강조가 들어가는 방향이어야 한다.

핵심 브랜드 감정은 다음과 같다:

- **안심감**: 오프라인 우선, 로컬 처리, 승인 기반 실행
- **명료함**: 채팅, 일정, 승인, 검색 같은 상태가 한눈에 구분되어야 함
- **가벼움**: 정보는 많지만 화면은 무겁지 않아야 함
- **친절함**: 경고와 오류는 분명하게, 그러나 위협적이지 않게 표현

메인 컬러인 **Sky Blue** (`{colors.primary}` — `#5BC2E7`)는 기술적인 차가움보다  
“맑고 믿을 수 있는 도우미”의 이미지를 주기 위한 선택이다.  
이 색은 브랜드 전체를 덮는 색이 아니라, **행동을 유도하는 핵심 순간**에만 사용해야 한다.

**Key Characteristics**
- 단일 메인 강조색 `{colors.primary}` 중심. CTA, 활성 상태, 주요 포인트에만 사용한다.
- 기본 캔버스는 `{colors.canvas}` 기반의 밝고 낮은 대비의 배경을 사용한다.
- 깊이는 강한 그림자보다 **얇은 hairline border**와 여백으로 만든다.
- 둥근 모서리는 부드럽지만 과하지 않다. “귀엽다”보다 “편안하다”에 가깝다.
- 채팅 인터페이스는 말풍선보다는 **정돈된 카드형 대화 블록**에 가깝게 느껴져야 한다.

---

## Colors

### Brand & Accent

- **Primary / Sky Blue** (`{colors.primary}` — `#5BC2E7`)
  - 앱의 핵심 브랜드 색상
  - 주요 CTA, 활성 탭, 선택된 상태, 진행 중/정보성 강조에 사용
  - 전체 배경을 파랗게 물들이지 말고, 필요한 순간에만 사용한다

- **Primary Strong** (`{colors.primary-strong}` — `#39AED8`)
  - 눌림 상태, hover 대체 상태, 더 강한 강조가 필요한 경우 사용
  - 모바일에서는 pressed / selected 상태에 적합하다

- **Primary Soft** (`{colors.primary-soft}` — `#EAF8FD`)
  - 사용자 말풍선, 선택 영역, 정보 배경, 비파괴적 강조에 사용
  - 적극적인 브랜드 표현보다는 “안전하고 부드러운 정보 강조” 역할

### Surface

- **Canvas** (`{colors.canvas}` — `#F7FBFD`)
  - 앱의 기본 바탕색
  - 순백보다 눈 피로가 적고, 메인 컬러와도 자연스럽게 조화된다

- **Surface Card** (`{colors.surface-card}` — `#FFFFFF`)
  - 카드, 말풍선, 바텀시트, 입력창, 설정 항목 배경
  - 캔버스 위에서 한 단계 떠 보이는 기본 단위

### Text

- **Ink** (`{colors.ink}` — `#1F2A33`)
  - 가장 중요한 텍스트 색
  - 제목, 주요 수치, 버튼 텍스트, 대화 내용에 사용

- **Body** (`{colors.body}` — `#42515C`)
  - 일반 본문과 설명 텍스트
  - Ink보다는 덜 강하지만 가독성이 충분해야 한다

- **Muted** (`{colors.muted}` — `#758390`)
  - 보조 설명, 메타 정보, timestamp, placeholder
  - 본문과 경쟁하지 않도록 조용해야 한다

### Hairlines & Structure

- **Hairline** (`{colors.hairline}` — `#D9E6EC`)
  - 카드 경계선, 구분선, 입력창 테두리
  - Local Friday는 강한 그림자보다 이 얇은 경계선으로 구조를 만든다

### Semantic

- **Success** (`{colors.success}` — `#2FA36B`)
  - 저장 완료, 승인 성공, 정상 완료 상태

- **Warning** (`{colors.warning}` — `#F4A62A`)
  - 중복 일정 경고, 낮은 confidence, 주의 필요 상태

- **Danger** (`{colors.danger}` — `#E35D5D`)
  - 치명적 오류, 삭제성 위험, 실패 상태

- **Info** (`{colors.info}` — `{colors.primary}`)
  - 네트워크 없이 로컬 응답 유지, 모델 상태, 일반 정보성 알림

### Usage Rule

- 한 화면에서 브랜드 강조색은 제한적으로 사용한다
- 경고와 오류는 강하게 보이되, 전체 화면을 불안하게 만들 정도로 넓게 사용하지 않는다
- 메인 컬러는 “배경색”보다 “행동 유도와 상태 강조”에 더 가깝다

---

## Typography

### Principles

Local Friday의 타이포그래피는 “AI 비서”답게 **대화는 편안하게, 상태는 명확하게, 중요한 순간은 단정하게** 보여야 한다.

- 헤드라인은 기술 제품처럼 너무 차갑지 않아야 한다
- 본문은 긴 대화를 읽어도 피로하지 않아야 한다
- 버튼/라벨은 짧고 명확하게 인지되어야 한다
- 모바일 환경에서 시각 밀도가 높아지지 않도록 line-height를 넉넉히 둔다

### Hierarchy

- **Display Large** → `{typography.display-lg}`
  - 빈 상태의 환영 문구, 주요 섹션 헤드라인
  - 사용 빈도는 낮고, 큰 순간에만 사용한다

- **Heading Large** → `{typography.heading-lg}`
  - 화면 제목, 큰 카드 제목, 승인 바텀시트 타이틀

- **Heading Medium** → `{typography.heading-md}`
  - 섹션 제목, 설정 그룹 제목, 일정 카드 제목

- **Body Large / Medium / Small** → `{typography.body-lg}`, `{typography.body-md}`, `{typography.body-sm}`
  - 대화 본문, 설명, 세부 텍스트에 사용
  - 일반 대화 메시지는 주로 `body-md`

- **Button** → `{typography.button}`
  - 버튼, 토글형 액션, 확인/취소 CTA

- **Label** → `{typography.label}`
  - 배지, 경고 태그, 작은 상태 라벨

### Note on Font Choice

- 기본은 `Pretendard` + `Inter` 조합을 권장한다
- 한국어 가독성과 Android 환경 일관성을 우선한다
- 시스템 폰트 fallback이 필요하더라도 전체 인상은 “정돈되고 현대적인 산세리프”를 유지한다

---

## Layout

### Spacing System

Local Friday는 모바일 중심 앱이므로, spacing은 촘촘하되 숨 막히지 않아야 한다.

- `{spacing.xs}` / `{spacing.sm}`
  - 작은 간격, 라벨과 값 사이, 아이콘과 텍스트 사이

- `{spacing.md}` / `{spacing.base}`
  - 입력창 내부, 카드 내부 기본 간격, 대화 블록 내부 spacing

- `{spacing.lg}` / `{spacing.xl}`
  - 카드 간 간격, 섹션 간 분리, 바텀시트 내부 그룹 분리

- `{spacing.section}`
  - 큰 빈 상태 레이아웃, 화면 전체 주요 섹션 간격

### Layout Philosophy

- 화면은 좌우 꽉 찬 정보판보다 **숨 쉴 수 있는 카드형 구조**를 지향한다
- 한 화면에 너무 많은 강조 요소가 동시에 보이지 않게 한다
- 채팅 화면에서는 입력창, 메시지 리스트, 상태 카드가 서로 경쟁하지 않아야 한다
- 승인/권한/오류 같은 “결정 순간”에는 정보 그룹을 더 크게 띄워서 보여준다

### Container & Grid

- 모바일 우선 단일 컬럼
- 태블릿 이상에서만 일부 카드/설정 목록을 2열까지 고려
- 대화 메시지 영역은 지나치게 넓지 않게 유지해 가독성을 높인다

---

## Elevation

### Surface Strategy

Local Friday는 강한 드롭 섀도우보다 **얇은 경계선과 레이어 간 여백 차이**로 깊이를 만든다.

- 기본 카드, 말풍선, 설정 셀, 상태 카드:
  - `{colors.surface-card}` 배경
  - `1px {colors.hairline}` border
  - 그림자 없음 또는 매우 약한 수준

- 바텀시트/모달:
  - 동일한 카드 배경 사용
  - 모서리는 더 크게 (`{rounded.xl}`)
  - 배경 dim은 `{colors.overlay}` 사용

### Shadow Rule

- 기본적으로 그림자를 사용하지 않는다
- 꼭 필요한 경우에도 **아주 약한 shadow 1단계만** 허용한다
- 구조를 그림자로 해결하려 하지 말고 spacing과 border로 해결한다

---

## Components

**`button-primary`**  
핵심 행동을 유도하는 버튼.  
배경은 `{colors.primary}`, 텍스트는 `{colors.on-primary}`, 타이포는 `{typography.button}`, 모서리는 `{rounded.md}`.  
브랜드 톤을 가장 직접적으로 드러내는 요소이므로 한 화면에 과도하게 많이 배치하지 않는다.

**`button-secondary`**  
보조 행동 버튼.  
배경은 `{colors.surface-card}`, 텍스트는 `{colors.ink}`, `1px {colors.hairline}` border 사용.  
취소, 설정 이동, 세컨더리 액션에 적합하다.

**`input`**  
기본 입력 필드.  
대화 입력창, 설정 입력, 검색 프리뷰 입력 등에 사용한다.  
배경은 `{colors.surface-card}`, border는 `{colors.hairline}`, padding은 충분히 주어 답답하지 않게 만든다.  
포커스 시에는 `{colors.primary}` 계열의 강조를 사용하되, 테두리 두께는 과하게 올리지 않는다.

**`card`**  
앱 전반의 가장 기본적인 정보 단위.  
배경 `{colors.surface-card}`, border `{colors.hairline}`, 모서리 `{rounded.lg}`.  
캘린더 일정, 설정 섹션, 상태 카드, 검색 미리보기 등 거의 모든 구조가 여기서 파생된다.

**`chat-bubble-user`**  
사용자 메시지 블록.  
`{colors.primary-soft}` 배경을 사용하여 assistant 응답과 구분하되, 너무 채도 높지 않게 유지한다.  
“브랜드 과시”보다 “사용자 발화 구분”이 목적이다.

**`chat-bubble-assistant`**  
AI 응답 블록.  
기본 카드형 표현. 흰색 배경 + hairline border를 사용한다.  
AI 응답은 정보량이 많으므로 과한 색보다 가독성과 안정감을 우선한다.

**`status-card`**  
모델 로딩 상태, 오프라인 상태, 네트워크 안내 등 시스템 상태를 보여주는 카드.  
카드와 유사하지만 더 얇고 조용한 표현을 사용한다.  
위험하지 않은 정보는 `{colors.info}` 또는 muted tone으로 안내한다.

**`approval-sheet`**  
일정 저장 승인, 검색 전 확인, 주요 결정 UX에 사용되는 바텀시트.  
기본 카드와 같은 톤을 유지하되, 모서리를 더 크게 하고 패딩을 넉넉하게 준다.  
결정 순간이므로 제목-요약-세부정보-행동 버튼 순으로 정보 구조를 분명히 해야 한다.

**`warning-badge`**  
낮은 confidence, 중복 일정 경고 등 “주의”를 위한 배지.  
강한 경고창보다 먼저 배지/태그 수준으로 보여주는 것이 기본 정책이다.  
Local Friday는 경고를 “위협”이 아니라 “사전 안내”처럼 보여야 한다.

---

## Responsive Behavior

| 이름 | 폭 | 주요 변화 |
|---|---|---|
| Mobile | `< 720px` | 단일 컬럼, 채팅 입력 고정, 바텀시트 중심 인터랙션 |
| Tablet | `720–1024px` | 일부 카드/설정 목록 2열 가능, 여백 증가 |
| Desktop-like | `> 1024px` | Android 태블릿/확장 환경 고려, 본문 폭 제한 유지 |

### Touch Targets

- 주요 버튼/탭/토글은 최소 **44 × 44px**
- 입력창 높이는 최소 **48px**
- FAB는 엄지 접근 영역을 고려해 우하단에 안정적으로 배치

### Collapsing Strategy

- 채팅 화면은 항상 메시지 흐름이 우선
- 작은 화면에서 부가 정보는 카드/시트로 접는다
- 승인 상세 정보는 한 번에 모두 펼치기보다 요약 → 확장 구조를 선호한다

---

## Known Gaps

현재 이 DESIGN.md는 아래를 상세히 정의하지 않는다.

- 다크 모드
- 애니메이션 / 트랜지션 상세 타이밍
- 폼 오류/성공 상태의 세부 시각 패턴
- 아이콘 세트 규칙
- 세부 차트 / 그래프 스타일
- Windows 확장 버전의 UI 변형
- 고급 접근성(예: TalkBack 최적화 문구 수준)의 상세 가이드

이 항목들은 필요 시 후속 버전에서 확장한다.
