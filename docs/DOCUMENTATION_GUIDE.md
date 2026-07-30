# 📝 Docs-as-Code 문서 작성 표준 지침서 (DOCUMENTATION_GUIDE)
> **문서 버전**: v1.0 | **최종 수정일**: 2026-07-31 | **상태**: Approved (승인)
> **적용 범위**: `lets_meet_on_friday` 프로젝트 내 모든 마크다운 및 기술 문서 (`docs/` 포함)

---

## 📌 1. 개요 (Overview)

본 지침서는 `lets_meet_on_friday` 프로젝트의 모든 기술 문서(Docs-as-Code)가 **통일된 포맷팅, 높은 가독성, 그리고 검증 가능한 무결성**을 유지하도록 작성 가이드라인을 정의합니다. 

개발자 및 AI 코딩 에이전트는 새로운 문서를 작성하거나 기존 문서를 수정할 때 본 지침서의 규칙을 준수해야 합니다.

---

## 📋 2. 표준 문서 헤더 (Header Metadata Standard)

모든 마크다운(`.md`) 파일은 최상단 1~3번째 줄에 아래 포맷의 표준 메타데이터 헤더를 반드시 작성해야 합니다:

```markdown
# [문서 제목]
> **문서 버전**: v1.X | **최종 수정일**: YYYY-MM-DD | **상태**: Draft / Review / Approved / Deprecated
> **관련 모듈**: `:app`, `:core`, `:domain`, `:data`
```

---

## 📑 3. 문서 유형별 표준 템플릿 (Document Templates)

### ① 기획 / 요구사항 문서 (`PRD.md`, `tasks.md`)
1. **개요 및 비전 (Overview)**
2. **사용자 유즈케이스 (User Stories)**
3. **기능적/비기능적 요구사항 (Functional Requirements)**
4. **예외 처리 및 제약 조건 (Edge Cases & Constraints)**

### ② 아키텍처 / 설계 문서 (`architecture.md`, `DESIGN.md`)
1. **시스템 개요 및 레이어 구조 (System Overview)**
2. **모듈 간 의존성 및 데이터 흐름 (Mermaid Diagram)**
3. **핵심 인터페이스 / 클래스 설계 (Class / Interface Contracts)**
4. **아키텍처 결정 기록 (ADR: Architecture Decision Records)**

### ③ 변경 / 이력 문서 (`CHANGELOG.md`)
1. **버전 타이틀 & 날짜**: `## [X.Y.Z] - YYYY-MM-DD`
2. **태그 기반 분류**: `[Feature]`, `[Refactoring]`, `[Fix]`, `[UI/UX]`, `[QA/Test]`
3. **모듈별 세부 내역**: 변경된 주요 파일 및 클래스 명시

---

## 🎨 4. 서식 및 작성 수칙 (Formatting Rules)

1. **GitHub Alert 강조문 사용**:
   - 중요 정보: `> [!NOTE]`
   - 핵심 요구사항: `> [!IMPORTANT]`
   - 경고/위험: `> [!WARNING]`
   - 팁/최적화: `> [!TIP]`

2. **코드 블록 구문 강조 (Syntax Highlighting)**:
   - 언어를 명시하여 시각적 가독성 확보 (`kotlin`, `yaml`, `mermaid`, `json`, `bash`)

3. **Mermaid 다이어그램 작성 규칙**:
   - 특수문자나 괄호가 들어간 노드 라벨은 반드시 큰따옴표로 감싸 구문 에러 방지 (예: `id["Label (Extra)"]`)
   - 계층 다이어그램은 `graph TD`, 시퀀스는 `sequenceDiagram`을 기본으로 사용

4. **소스 코드 파일 링크**:
   - 관련된 파일 참조 시 GitHub 스타일 마크다운 및 forward slash 경로 사용 (예: `[Constants.kt](file:///c:/Users/J/Desktop/J-Dev/lets_meet_on_friday/core/src/main/java/com/kosmos/app/core/common/Constants.kt)`)

---

## 🔄 5. 문서 수명주기 및 업데이트 트리거 (Lifecycle)

- **코드 변경 동기화**: UseCase, Repository, ViewModel 등의 시그니처나 모듈 구조 변경 시 `architecture.md` 및 `CHANGELOG.md`를 함께 업데이트합니다.
- **버전 릴리즈**: 작업 완결 시 `docs/CHANGELOG.md` 최상단에 새 버전을 기록하고 `README.md` 버전을 동기화합니다.
