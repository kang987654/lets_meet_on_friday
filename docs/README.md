# 📚 Docs-as-Code 센터 & 문서 색인 (Documentation Index)
> **문서 버전**: v1.0 | **최종 수정일**: 2026-07-31 | **상태**: Approved (승인)
> **관련 모듈**: `lets_meet_on_friday` 프로젝트 전 영역

---

## 📌 문서 가이드 및 탐색 (Navigation)

`lets_meet_on_friday` 프로젝트의 모든 기술 문서는 Docs-as-Code 철학에 따라 마크다운 및 YAML 포맷으로 관리됩니다.

프로젝트 문서를 새로 작성하거나 정돈할 때에는 **[DOCUMENTATION_GUIDE.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/DOCUMENTATION_GUIDE.md)** 지침서를 준수합니다.

---

## 🗺️ 문서 색인 (Sitemap)

| 구분 | 문서 파일 | 주요 내용 및 목적 |
| :--- | :--- | :--- |
| 📖 **지침서** | [DOCUMENTATION_GUIDE.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/DOCUMENTATION_GUIDE.md) | Docs-as-Code 포맷팅 규칙 및 문서 작성 표준 지침 |
| 📋 **기획/요구사항** | [PRD.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/PRD.md) | 제품 요구사항 문서 (Product Requirement Document) |
| 🏗️ **아키텍처** | [architecture.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/architecture.md) | Clean Architecture 멀티모듈 시스템 구조 & 데이터 흐름 |
| 🎨 **UI/UX 디자인** | [DESIGN.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/DESIGN.md) | Glassmorphism 테마, 애니메이션 및 UI 스펙 디자인 가이드 |
| 📝 **태스크 분해** | [tasks.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/tasks.md) | AI Agent 1회 작업 단위의 74개 세부 태스크 분해목록 |
| 🔌 **내부 계약 사양** | [api_spec.yaml](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/api_spec.yaml) | 앱 내부 UseCase/도구 계약을 OpenAPI 문법으로 기술한 문서 — 오프라인 앱이므로 REST 서버는 없다 |
| 🪵 **변경 이력** | [CHANGELOG.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/CHANGELOG.md) | 프로젝트 버전별 세부 리팩터링 및 수정 내역 기록 |
| 🛠️ **트러블슈팅** | [trouble_shooting.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/trouble_shooting.md) | 16KB 메모리 정렬, Robolectric, Native crash 문제 해결 가이드 |
| 📊 **인증 체크리스트** | [checklists/v0_acceptance.md](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/docs/checklists/v0_acceptance.md) | v0 MVP 인수 테스트 기준 및 체크리스트 |

---

## 🔄 문서 수명주기 관리 규칙

1. 모든 기능 추가 및 리팩터링 완료 시 `CHANGELOG.md` 최상단에 버전과 내역을 기록합니다.
2. 모듈 의존성 변경 시 `architecture.md` 다이어그램을 최신 상태로 업데이트합니다.
