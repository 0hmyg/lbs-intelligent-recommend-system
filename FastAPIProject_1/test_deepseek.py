"""测试 DeepSeek API 连接"""
from openai import OpenAI

api_key = "sk-dc06b98fea084b969ba609c7f834f9d1"

try:
    client = OpenAI(
        api_key=api_key,
        base_url="https://api.deepseek.com"
    )

    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=[
            {"role": "user", "content": "你好，请简单介绍一下自己"}
        ],
        max_tokens=100
    )

    print("✅ DeepSeek 连接成功！")
    print(f"回复: {response.choices[0].message.content}")

except Exception as e:
    print(f"❌ DeepSeek 连接失败: {e}")