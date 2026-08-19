# 📊 [투자 심사 보고서] KOSMOS (Local Friday)

- **평가 대상**: On-Device AI 기반 개인 비서 & 지능형 일정 관리 솔루션
- **핵심 기술**: Google Gemma 4 E4B-it (INT4) + LiteRT-LM + Clean Multi-Module
- **타깃 세그먼트**: Privacy-First Edge AI, 엔터프라이즈/전문직 개인 비서, 온디바이스 에이전트

---

## 1. 투자 하이라이트 & 총평 (Executive Summary)

> **"클라우드 AI의 비용 폭증과 프라이버시 한계를 정면으로 돌파하는 고난도 온디바이스 에이전트 기술. 엔지니어링 완성도는 최상급이나, 하드웨어 파편화 극복과 B2B/B2C 비즈니스 모델 정립이 기업가치 스케일업의 핵심 열쇠."**

| 평가 항목 | 등급 | 핵심 요약 |
| :--- | :---: | :--- |
| **시장 기회 (TAM/SAM)** | **A-** | 클라우드 LLM 토큰 비용 절감 및 프라이버시 규제 강화에 따른 온디바이스 AI 시장의 급성장 |
| **기술적 완성도 & 해자 (Moat)** | **A+** | 단순 래퍼가 아닌 GPU FP16 정밀도 보정, 에피소드 장기 기억 백엔드, 툴 오케스트레이션 내재화 |
| **엔지니어링 실행력** | **S** | 307개 E2E/단위 테스트 100% 통과, Lint 경고 0건, 철저한 ADR 및 실측 기반 아키텍처 구축 |
| **수익화 및 GTM 전략** | **B** | 현재 단일 기기 Sideload 수준으로, 명확한 과금 모델(B2B/B2C) 및 배포 파이프라인 수립 필요 |
| **빅테크 경쟁 위험** | **B-** | Apple Intelligence, Samsung Galaxy AI 등 OS 네이티브 기능과의 차별화 포지셔닝 요구 |

---

## 2. 시장 기회 및 경제성 분석 (Unit Economics & Market Opportunity)

### ① 단위 경제성(Unit Economics)의 혁신: **한계 비용 0원 ($0 Marginal Cost)**
- **클라우드 AI 비서의 딜레마**: 사용자가 질문/음성/이미지를 전송할 때마다 클라우드 API(OpenAI, Anthropic 등) 호출 비용이 선형적으로 증가하여 MAU가 늘어날수록 마진이 압박받음.
- **KOSMOS의 경쟁 우위**: 기기 자체 연산(NPU/GPU)을 사용하므로 **유저당 추론 인프라 비용(Serving Cost)이 $0**입니다. 구독형(SaaS) 모델 도입 시 **GPM(Gross Profit Margin) 90% 이상**의 압도적인 마진 구조 확보가 가능합니다.

### ② 규제 및 프라이버시 프리미엄 (Privacy Premium)
- GDPR, HIPAA, 금융보안 규제 등으로 인해 **의료인, 변호사, 금융권 임원, C-Level 경영진**은 업무 데이터(일정, 메모, 녹음)를 ChatGPT 등 외부 클라우드로 전송할 수 없습니다.
- 로컬에서 완결되는 보안 격리형 비서는 B2B 엔터프라이즈 시장에서 높은 가격 지불 용의성(WTP, Willingness to Pay)을 가집니다.

---

## 3. 기술적 해자 및 강점 (Competitive Advantage & Moats)

```text
[KOSMOS의 4중 기술 방어선]
1. Runtime Engineering  : 모바일 GPU FP16 한계 실측 및 1,700 토큰 최적화 (ADR-021)
2. Agentic Pipeline     : 멀티턴 도구 호출(Calendar/Wikipedia) + Human-in-the-Loop 승인 가드
3. Long-term Memory     : 30분 무활동 자동 분절 → 백그라운드 요약 → FTS 검색 회수 (에피소드 기억)
4. Full Multi-modal     : 16kHz WAV 직접 녹음/전사 파이프라인 (별도 무거운 STT 엔진 불요)
```

1. **상용화 수준의 런타임 제약 해결 능력**:
   - 단순히 라이브러리를 붙인 것이 아니라, GPU 1,854 토큰 이상에서 발생하는 FP16 숫자 깨짐 발병점을 직접 계측하여 예산 산식(1,700 토큰)으로 방어하고, 토크나이저 추정 오차를 보정한 엔지니어링 뎁스가 돋보입니다.
2. **신뢰 기반의 Human-in-the-Loop UX**:
   - AI 에이전트의 최대 약점인 '통제 불가능한 환각/오작동'을 승인 시트(ApprovalSheet)와 Audit Trail을 통해 제도적으로 차단하여 기업용 도입 장벽을 낮췄습니다.
3. **독보적인 코드베이스 신뢰도**:
   - Clean Multi-Module 구조, 307건의 무결점 테스트 슈트, ADR 기반의 의사결정 기록은 팀의 확장(Scaling) 및 투자 후 기술 실사(Technical Due Diligence)에서 최고 수준의 평가를 받을 수 있는 자산입니다.

---

## 4. 주요 리스크 및 약점 (Key Investment Risks)

```text
       [Risk Matrix]
Impact
  ▲
  │   (R1) Big Tech OS 통합 ──┐
  │                           │   (R2) 하드웨어 제약
  │                           │
  │                     (R3) GTM/수익 모델 부재
  │
  └──────────────────────────────────────────► Probability
```

1. **빅테크(Apple/Google/Samsung)의 네이티브 OS AI 침투 (High Impact / High Probability)**:
   - 삼성 Galaxy AI, 구글 Gemini Nano, 애플 Apple Intelligence가 시스템 수준에서 일정/메모/요약을 무료 기본 탑재하고 있습니다.
   - *위험 완화 전략*: 범용 비서 대신 **특화 영역(전문직 데이터베이스 연동, 사내망 폐쇄형 ERP/CRM 연동 에이전트)**으로 피봇하여 차별화해야 합니다.
2. **하드웨어 요구사양과 타깃 시장 규모(TAM)의 제약**:
   - 3.6GB 모델 구동을 위해 최소 12GB+ RAM 및 플래그십 칩셋(S25 Ultra급)이 필요하여, 초기 진입 가능한 안드로이드 기기 모수가 전체의 5~10% 수준으로 제한됩니다.
3. **배포 및 업데이트 파이프라인 (Sideload 한계)**:
   - 3.6GB 대용량 모델 파일은 Google Play Store APK 크기 제한에 걸려 인앱 분할 다운로드나 외부 스토리지 관리가 필수적이며, 일반 대중 사용자(Mass Market)의 온보딩 마찰이 큽니다.

---

## 5. 밸류에이션 극대화를 위한 전략적 제언 (Growth Strategy)

투자자 관점에서 기업가치(Valuation)를 10배 이상 스케일업하기 위한 3단계 전략입니다:

### Step 1. 수직형 B2B/엔터프라이즈 특화 (Vertical AI Pivot)
- 일반 B2C 비서 앱에서 **"전문직용 폐쇄형 기밀 비서"** (예: 로펌 변호사용 상담 녹음/일정 관리, 병원 의사용 온디바이스 차트 요약)로 포지셔닝.
- 1인당 월 $30~$50의 고단가 B2B 구독 모델(SaaS) 도입.

### Step 2. 'On-Device Agent Framework' 기술 라이선싱 (SDK B2B)
- KOSMOS의 코어 엔진(에피소드 메모리 시스템, 1,700 토큰 윈도우 관리자, 네이티브 툴 오케스트레이터)을 모듈화하여 타 앱 개발사나 디바이스 제조사에 **B2B 온디바이스 에이전트 SDK** 형태로 공급.

### Step 3. 하이브리드 확장 (Privacy-Preserving Hybrid)
- 민감 정보(일정/메모/음성)는 로컬 Gemma 4가 100% 처리하고, 고난도 추론이나 대용량 문서 분석에 한해 사용자가 선택적으로 익명화된 클라우드 LLM을 호출할 수 있는 하이브리드 티어(Tier) 구성.

---

## 6. 투자 심사 결론 (Final Verdict)

- **투자 추천 의견**: **Conditional BUY (조건부 투자 긍정)**
- **추천 단계**: Seed ~ Pre-Series A
- **핵심 투자 논거**:
  1. On-Device AI 런타임 최적화와 실기기 발열/메모리/정밀도 이슈를 실질적으로 해결한 **극소수의 실력 있는 엔지니어링 자산**.
  2. 추론 서버 비용 제로($0) 기반의 **압도적인 마진 구조 잠재력**.
  3. 보안/프라이버시 규제가 강화되는 글로벌 AI 시장 트렌드와의 완벽한 부합.
- **투자 집행 선결 조건(Milestones for Funding)**:
  - B2C 앱스토어 정식 온보딩 플로우 검증 (모델 다운로드 UX 마찰 해소).
  - 특정 버티컬(전문직/기업용) 타깃 고객군 1곳 이상의 유료 전환 PoC(Proof of Concept) 확보.
