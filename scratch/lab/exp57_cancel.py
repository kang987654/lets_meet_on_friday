"""exp57 — 생성 취소 뒤 같은 대화가 계속 "Task cancelled" 로 끝나는가 (0.38.6 M0).

실기기: 생성 도중 한 번 취소하자 그 뒤 모든 입력이 "Task cancelled"(앱 재시작 전까지). 앱은 채팅 대화를 캐시해 재사용한다.
A: 생성 중 cancel_process → 같은 대화에 다시 보내기 ×2 → 새 대화에 보내기(대조)
B: 쉬는 대화에 cancel_process 만 → 같은 대화에 보내기
"""
import threading
import time

import litert_lm as llm

import kosmos_lab as K


def send(conv, text, label):
    t0 = time.time()
    try:
        r = conv.send_message(text)
        out = r if isinstance(r, str) else str(r)
        print(f"  {label}: 성공 {time.time() - t0:.1f}s {out[:80]!r}", flush=True)
    except Exception as e:
        print(f"  {label}: 실패 {time.time() - t0:.1f}s {type(e).__name__}: {e}", flush=True)


def stream_then_cancel(conv, text, after_chunks=5):
    n = 0
    try:
        for chunk in conv.send_message_async(text):
            n += 1
            if n == after_chunks:
                threading.Thread(target=conv.cancel_process).start()
        print(f"  스트림 끝(조각 {n})", flush=True)
    except Exception as e:
        print(f"  스트림 예외(조각 {n}) {type(e).__name__}: {e}", flush=True)


engine = K.create_engine(llm.Backend.GPU)
with engine:
    print("A: 생성 중 취소", flush=True)
    a = K.create_chat_conversation(engine, tools=[])
    stream_then_cancel(a, "한국의 사계절을 각각 세 문장씩 자세히 설명해 줘.")
    time.sleep(1)
    send(a, "안녕", "같은 대화 1")
    send(a, "오늘 날씨 어때?", "같은 대화 2")
    a2 = K.create_chat_conversation(engine, tools=[])
    send(a2, "안녕", "새 대화")

    print("B: 쉬는 대화 취소", flush=True)
    b = K.create_chat_conversation(engine, tools=[])
    send(b, "안녕", "취소 전")
    b.cancel_process()
    send(b, "고마워", "취소 후 1")
    send(b, "잘 자", "취소 후 2")
