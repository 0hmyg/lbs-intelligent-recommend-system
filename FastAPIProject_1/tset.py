print("=" * 60)
print("测试模块导入和数据库连接")
print("=" * 60)

# 1. 测试 db_helper
try:
    print("\n1. 导入 db_helper...")
    from utils.db_helper import db_helper
    print("   ✅ db_helper 导入成功")
except Exception as e:
    print(f"   ❌ db_helper 导入失败: {e}")
    import traceback
    traceback.print_exc()

# 2. 测试 SensitiveFilter
try:
    print("\n2. 导入 SensitiveFilter...")
    from modules.sensitive_filter import SensitiveFilter
    print("   ✅ SensitiveFilter 导入成功")
except Exception as e:
    print(f"   ❌ SensitiveFilter 导入失败: {e}")
    import traceback
    traceback.print_exc()

# 3. 测试 TagExtractor
try:
    print("\n3. 导入 TagExtractor...")
    from modules.tag_extractor import TagExtractor
    print("   ✅ TagExtractor 导入成功")
except Exception as e:
    print(f"   ❌ TagExtractor 导入失败: {e}")
    import traceback
    traceback.print_exc()

# 4. 测试数据库连接
try:
    print("\n4. 测试数据库连接...")
    result = db_helper.execute_query("SELECT 1 as test", fetch_all=False)
    if result and result['test'] == 1:
        print("   ✅ 数据库连接成功")
    else:
        print("   ❌ 数据库连接失败")
except Exception as e:
    print(f"   ❌ 数据库连接失败: {e}")

# 5. 测试初始化 TagExtractor
try:
    print("\n5. 初始化 TagExtractor...")
    te = TagExtractor()
    print(f"   ✅ 成功，停用词: {len(te.get_all_stopwords())} 个，标签: {len(te.get_all_tags())} 个")
except Exception as e:
    print(f"   ❌ 初始化失败: {e}")
    import traceback
    traceback.print_exc()

# 6. 测试初始化 SensitiveFilter
try:
    print("\n6. 初始化 SensitiveFilter...")
    sf = SensitiveFilter()
    words = sf.get_all_words()
    print(f"   ✅ 成功，敏感词: block={len(words['block'])}, review={len(words['review'])}")
except Exception as e:
    print(f"   ❌ 初始化失败: {e}")
    import traceback
    traceback.print_exc()

# 7. 测试标签提取
try:
    print("\n7. 测试标签提取...")
    text = "出闲置鱼缸60cm超白缸，中原区自提，价格300元"
    tags = te.extract(text, top_k=5)
    print(f"   原文: {text}")
    print(f"   提取的标签:")
    for tag, weight in tags:
        print(f"     - {tag}: {weight}")
except Exception as e:
    print(f"   ❌ 提取失败: {e}")

# 8. 测试敏感词过滤
try:
    print("\n8. 测试敏感词过滤...")
    test_texts = [
        "这是一条正常的测试文本",
        "这是一条包含毒品的违规内容"
    ]
    for text in test_texts:
        result = sf.filter(text)
        status = "✅ 通过" if result['is_valid'] else "❌ 拦截"
        print(f"   {status}: \"{text}\"")
        if result['hit_words']:
            print(f"      命中词: {result['hit_words']}")
except Exception as e:
    print(f"   ❌ 过滤失败: {e}")

print("\n" + "=" * 60)
print("测试完成")
print("=" * 60)
