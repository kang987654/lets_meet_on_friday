"""exp14 — 히스토리가 쌓이면 툴을 안 부르는지 재현한다 (#1).

## 무엇을 재현하는가

2026-08-12 실기기(0.11.1)에서 위키 검색이 필요한 발화 **4연속 모두** `TOOL_CALL` 0건이었다.
`Tools declared:` 에는 `search_wikipedia` 가 매 턴 있었고, 명시 지시("위키에서 에스파 검색해줘")
조차 호출되지 않았다. 그런데 답변은 이렇게 시작했다:

    "에스파(aespa)에 대해 위키백과에서 검색하여 요약 정보를 가져왔습니다."

그리고 내용은 전부 거짓이었다(2020→2023, SM→JYP, 멤버 구성). **검색하지 않고 검색 결과처럼
생긴 답변을 위조한 것이다.** 같은 날 새벽 첫 턴에서는 정상 호출됐다(감사 로그 03:37).

## 가설

  H1 모방  — 히스토리에 "검색 결과를 보고한 어시스턴트 메시지"가 있으면 모델은 툴을 부르는
            대신 그 **겉모양**을 재현한다. 툴 호출은 문맥에 남지 않으니 모방 대상이 답변
            형식뿐이다.
  H2 길이  — 문맥이 길어지면 시스템 지시의 트리거 규칙이 상대적으로 묻힌다.
  H3 재사용 — 툴 선언은 대화 생성 시 한 번만 프리필된다. KV 가 자라면 선언이 멀어진다.

## 어떻게 가른다

  깊이 N × 모드(재사용 R / 재생성 N) × 히스토리 형태(verbatim / terse)

  - verbatim 은 실제 답변 그대로, terse 는 어시스턴트 답변을 "네, 알겠습니다."로 대체한다.
    길이는 거의 그대로 두고 **모방 대상만** 없애는 변형이다.
  - terse 에서 호출률이 회복되면 → H1 확정. 처방은 프롬프트가 아니라 **히스토리 적재 방식**이다.
  - verbatim·terse 가 같이 떨어지면 → H2. 처방은 예산·요약이다.
  - R 과 N 이 다르면 → H3. 처방은 재설정 주기다.

앱과 같은 조건: 툴 5종 선언, greedy, constrained on, thinking off, few-shot 1건.

## 실측 결과와 **정정** (2026-08-12~13, cpu, max_num_tokens=8192)

    형태        depth=8   depth=20  depth=40
    verbatim    미호출     미호출     미호출
    terse       호출 OK    호출 OK    호출 OK

여기까지 보고 H1(모방)을 확정했는데 **그 결론은 틀렸다.** 후속 실험이 뒤집었다:

  exp18  문서 형식대로 툴 호출 구조를 히스토리에 복원해도 일정 툴은 **0/3** — 구조 누락이 아니다
  exp19  같은 답변을 60자로 자르면 3/3, 120자면 실패 — 모방이 아니라 **어시스턴트 산문의 양**이다
  exp20  지침을 사용자 턴에 다시 붙이면 **원본 히스토리 그대로** 호출된다 — 원인은 **지침과 사용자
         발화 사이의 거리**다. 히스토리를 어떻게 담느냐의 문제가 아니었다
  exp21  앱이 실제로 보내는 모양으로 재판정 — depth 20·40 에서 툴 3종 **6/6**

그래서 처방은 히스토리 적재 방식이 아니라 프롬프트 배치였다(ADR-017). terse 가 이긴 이유도
"모방 대상 제거"가 아니라 "지침과 발화 사이의 산문이 줄어든 것"으로 설명된다.

이 파일은 **원인을 좁히는 첫 단계**로 남긴다 — verbatim/terse 대비가 여전히 그 효과를 가장
크게 보여 준다. 다만 결론은 exp20·21 을 따를 것.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

# 위키 검색이 필요한 발화만 본다. 툴 이름을 직접 지목하지 않는 평문 질문과, 명시 지시를 섞었다.
PROBES = F.WIKI_PROBES

DEPTHS = F.depth_options()
MODES = ("reuse", "recreate")
SHAPES = ("verbatim", "terse")


def called_search(result):
    return any("wikipedia" in (c.get("name") or "") for c in result.tool_calls)


def run_reuse(lab, cfg, history):
    """한 대화로 4턴을 연속 보낸다 (앱 기본 경로 — 대화 재사용)."""
    hits = []
    with lab.conversation(cfg, history) as send:
        for probe in PROBES:
            r = send(F.as_app_sends(probe))
            hits.append(called_search(r))
    return hits


def run_recreate(lab, cfg, history):
    """턴마다 대화를 새로 만든다 (히스토리를 initialMessages 로 재주입)."""
    hits = []
    grown = list(history)
    for probe in PROBES:
        r = lab.run(F.as_app_sends(probe), cfg, grown)
        hits.append(called_search(r))
        # 앱이 재생성할 때와 같이, 방금 턴을 히스토리에 더한다.
        grown = grown + [
            K.L.Message.user(probe),
            K.L.Message.model(K.L.Contents.of(r.text or " ")),
        ]
    return hits


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] fixtures/system_instruction.txt 가 없다. 먼저:")
        print("    ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1
    print(f"시스템 지시: 앱 픽스처 {len(si)}자 / 선언 툴 {len(K.ALL_TOOLS)}종")
    print(f"깊이: {DEPTHS}  (히스토리 전체 {len(F.rows())}개)")
    print(f"발화 {len(PROBES)}개, '{F.WIKI_HISTORY_CUT}' 직전까지를 히스토리로 씀\n")

    cfg = K.Config(label="app-parity", system=si)
    lab = K.Lab(backend=BACKEND)
    rows = []
    try:
        for shape in SHAPES:
            full = F.messages(mode=shape, before=F.WIKI_HISTORY_CUT)
            for depth in DEPTHS:
                history = full[-depth:] if depth else []
                for mode in MODES:
                    runner = run_reuse if mode == "reuse" else run_recreate
                    hits = runner(lab, cfg, history)
                    rows.append((shape, depth, mode, hits))
                    marks = "".join("O" if h else "." for h in hits)
                    print(f"  {shape:9} depth={depth:<3} {mode:9} {marks}  {sum(hits)}/{len(hits)}")
    finally:
        lab.close()

    print("\n=== 요약 (O = search_wikipedia 호출) ===")
    print(f"{'shape':10}{'depth':>6}{'reuse':>8}{'recreate':>10}")
    for shape in SHAPES:
        for depth in DEPTHS:
            got = {m: h for s, d, m, h in rows if s == shape and d == depth for m, h in [(m, h)]}
            r = sum(got.get("reuse", []))
            n = sum(got.get("recreate", []))
            print(f"{shape:10}{depth:>6}{r:>8}{n:>10}")

    print("\n판정 지침:")
    print("  깊이 0 에서 호출되고 깊어질수록 떨어지면      → 문맥이 원인")
    print("  terse 에서 회복되면                          → **모방 가설 확정**")
    print("  verbatim·terse 가 같이 떨어지면              → 길이 자체가 원인")
    print("  reuse 와 recreate 가 다르면                  → 선언 프리필 위치 문제")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
