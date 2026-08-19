# create_conversation / send_message 시그니처 확인 (일회성)
import inspect
import litert_lm as llm

print("create_conversation:")
print(inspect.signature(llm.Engine.create_conversation))
doc = inspect.getdoc(llm.Engine.create_conversation)
print((doc or "")[:900])

print()
print("send_message:")
print(inspect.signature(llm.Conversation.send_message))
print((inspect.getdoc(llm.Conversation.send_message) or "")[:600])

print()
print("token_count:")
print(inspect.signature(llm.Conversation.token_count))
print((inspect.getdoc(llm.Conversation.token_count) or "")[:400])

print()
print("Tool:")
print(inspect.signature(llm.Tool))
print((inspect.getdoc(llm.Tool) or "")[:400])

print()
print("Message.tool:")
print(inspect.signature(llm.Message.tool))

print()
print("Content.ToolResponse:")
print(inspect.signature(llm.Content.ToolResponse))
