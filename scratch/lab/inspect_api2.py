# Engine/Conversation 시그니처 확인용 (일회성)
import inspect
import litert_lm as llm

for name in ["Engine", "Conversation", "SamplerConfig", "ThinkingConfig", "ConstrainedDecodingConfig", "Content", "Message", "tool_from_function"]:
    obj = getattr(llm, name)
    print("=" * 20, name)
    try:
        print(inspect.signature(obj))
    except (ValueError, TypeError):
        pass
    doc = inspect.getdoc(obj)
    if doc:
        print(doc[:600])
    members = [m for m in dir(obj) if not m.startswith("_")]
    print("members:", members[:40])
