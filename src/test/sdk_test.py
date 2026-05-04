from openai import OpenAI

client = OpenAI(
    api_key="test-token",
    base_url="http://localhost:8080/v1"
)
print("=== 测试 models ===")
models = client.models.list()
print(models)

print("\n=== 测试 chat（非流式）===")
response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "user", "content": "你好"}
    ]
)

print(response.choices[0].message.content)


print("\n=== 测试 chat（流式）===")

stream = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[{"role": "user", "content": "你好"}],
    stream=True
)

for chunk in stream:
    delta = chunk.choices[0].delta

    if hasattr(delta, "content") and delta.content:
        print(delta.content, end="", flush=True)

