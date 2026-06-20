"""
标签提取器模块（修复版 - PostgreSQL 兼容）
基于 jieba + DeepSeek AI 的混合标签提取
"""

import jieba
import jieba.analyse
import json
import logging
from typing import List, Dict, Tuple, Optional, Set
from openai import OpenAI

from utils.db_helper import db_helper

logger = logging.getLogger(__name__)


class TagExtractor:
    """标签提取器（jieba + DeepSeek AI混合）"""

    def __init__(self, use_ai: bool = True):
        """初始化标签提取器"""
        # 标签库和停用词
        self.tag_names: Set[str] = set()
        self.stop_words: Set[str] = set()

        # DeepSeek 配置
        self.use_ai = use_ai
        self.deepseek_api_key = os.environ.get("DEEPSEEK_API_KEY")

        # 初始化 DeepSeek 客户端
        if self.use_ai and self.deepseek_api_key:
            self.ai_client = OpenAI(
                api_key=self.deepseek_api_key,
                base_url="https://api.deepseek.com"
            )
            logger.info("DeepSeek AI 客户端初始化成功")
        else:
            self.ai_client = None
            logger.warning("未配置 DeepSeek API Key，AI 功能禁用")

        # 添加自定义词典，防止分词碎片化
        self._add_custom_words()

        # 从数据库加载数据
        self._load_tags_from_db()
        self._load_stopwords_from_db()

        logger.info(
            f"标签提取器初始化完成: 标签={len(self.tag_names)}, 停用词={len(self.stop_words)}, AI={'启用' if self.ai_client else '禁用'}")

    def _add_custom_words(self):
        """添加自定义词典，提高分词准确度"""
        custom_words = [
            "夫妻肺片", "水煮鱼", "麻辣烫", "酸菜鱼", "火锅",
            "人工智能", "机器学习", "深度学习", "自然语言处理",
            "比特币", "区块链", "元宇宙", "电动车", "自动驾驶"
        ]
        for word in custom_words:
            jieba.add_word(word)

    def _load_tags_from_db(self):
        """从数据库加载标签库"""
        try:
            results = db_helper.execute_query(
                "SELECT name FROM tags WHERE type IN ('content', 'auto')"
            )
            self.tag_names = {row['name'] for row in results}
        except Exception as e:
            logger.error(f"加载标签库失败: {e}")
            self.tag_names = {
                "科技", "生活", "美食", "旅游", "音乐", "电影",
                "读书", "运动", "健康", "教育", "职场", "情感",
                "摄影", "游戏", "时尚", "汽车", "房产", "财经"
            }

    def _load_stopwords_from_db(self):
        """从数据库加载停用词"""
        try:
            results = db_helper.execute_query("SELECT word FROM stopwords")
            self.stop_words = {row['word'] for row in results}
        except Exception as e:
            logger.error(f"加载停用词失败: {e}")
            self.stop_words = {
                '的', '了', '在', '是', '我', '有', '和', '就',
                '不', '人', '都', '一', '一个', '上', '也', '很',
                '到', '说', '要', '去', '你', '会', '着', '没有',
                '看', '好', '自己', '这', '那', '他', '她', '它',
                '们', '什么', '怎么', '哪', '吗', '啊', '吧', '呢'
            }

    def _extract_keywords_jieba(self, text: str, top_k: int = 10) -> List[Tuple[str, float]]:
        """使用 jieba 提取关键词（内部方法）"""
        if not text:
            return []

        keywords = jieba.analyse.extract_tags(text, topK=top_k, withWeight=True)

        # 过滤
        filtered = []
        for word, weight in keywords:
            # 过滤停用词
            if word in self.stop_words:
                continue
            # 过滤短词
            if len(word) < 2:
                continue
            # 过滤纯数字
            if word.isdigit():
                continue
            # 过滤纯标点
            if all(c in '，。！？、；：""''（）【】《》' for c in word):
                continue

            filtered.append((word, weight))

        return filtered

    def _extract_with_deepseek(self, text: str, top_k: int = 5) -> Optional[List[Tuple[str, float]]]:
        """使用 DeepSeek AI 提取标签"""
        if not self.ai_client:
            return None

        tag_library = ", ".join(sorted(self.tag_names)[:40])

        prompt = f"""你是一个专业的标签提取助手。请从以下文本中提取最相关的标签。

【文本内容】
{text[:600]}

【可用标签库】
{tag_library}

【任务要求】
1. 从标签库中选 2-3 个最匹配的标签
2. 如果文本有明显主题但标签库没有，可以创建 1-2 个新标签（2-4个字）
3. 总共返回 {top_k} 个标签
4. 每个标签给出 0-1 之间的权重，越高越相关
5. 标签要简洁准确，避免碎片化词汇

【返回格式】只返回JSON数组：
[{{"tag": "标签名", "weight": 0.95}}, {{"tag": "标签名", "weight": 0.85}}]

不要包含任何其他文字，只返回JSON数组。"""

        try:
            response = self.ai_client.chat.completions.create(
                model="deepseek-chat",
                messages=[
                    {"role": "system", "content": "你是专业的标签提取助手。只返回JSON格式结果，不要任何解释。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,
                max_tokens=300
            )

            result_text = response.choices[0].message.content.strip()

            # 清理可能的 markdown 格式
            if "```json" in result_text:
                result_text = result_text.split("```json")[1].split("```")[0].strip()
            elif "```" in result_text:
                result_text = result_text.split("```")[1].split("```")[0].strip()

            # 解析 JSON
            ai_tags = json.loads(result_text)

            # 转换为标准格式
            result = []
            for tag in ai_tags:
                if isinstance(tag, dict):
                    tag_name = tag.get("tag", tag.get("tag_name", ""))
                    weight = float(tag.get("weight", 0.5))
                    if tag_name and len(tag_name) >= 2 and 0 < weight <= 1:
                        result.append((tag_name, round(weight, 3)))

            if result:
                logger.info(f"DeepSeek AI 提取成功: {result}")
                return result[:top_k]

        except json.JSONDecodeError as e:
            logger.warning(f"AI 返回的JSON解析失败: {result_text[:200]}, 错误: {e}")
        except Exception as e:
            logger.error(f"DeepSeek AI 调用失败: {e}")

        return None

    def extract(self, text: str, top_k: int = 5) -> List[Tuple[str, float]]:
        """
        提取文本关键词（AI 优先，jieba 兜底）
        这是 /api/extract_tags 调用的主要方法
        """
        if not text:
            return []

        # 1. 优先尝试 DeepSeek AI
        if self.ai_client:
            ai_result = self._extract_with_deepseek(text, top_k)
            if ai_result:
                return ai_result

        # 2. AI 失败则降级到 jieba
        logger.info("AI 未返回结果，降级使用 jieba")
        jieba_result = self._extract_keywords_jieba(text, top_k * 2)

        # 提高标签库匹配词的权重
        result = []
        for word, weight in jieba_result:
            if word in self.tag_names:
                weight = min(weight * 1.3, 1.0)
            result.append((word, round(weight, 3)))

        result.sort(key=lambda x: x[1], reverse=True)
        return result[:top_k]

    def extract_for_db(self, post_id: int, title: str, content: str,
                       category: str = "life", top_k: int = 5) -> List[Dict]:
        """
        为数据库提取标签（完整版，包含保存逻辑）
        用于帖子打标接口
        """
        try:
            # 组合文本，标题权重更高
            full_text = f"{title}。{content[:500]}"

            # 提取标签
            tags = self.extract(full_text, top_k)

            # 转换为字典格式
            result = []
            for tag_name, weight in tags:
                result.append({
                    "tag_name": tag_name,
                    "weight": weight
                })

            logger.info(f"标签提取完成: post_id={post_id}, 标签数={len(result)}")
            return result

        except Exception as e:
            logger.error(f"标签提取失败: {e}")
            return []

    def save_tags_to_db(self, post_id: int, tags: List[Tuple[str, float]]) -> bool:
        """保存标签到数据库（PostgreSQL 兼容版本）"""
        try:
            # 先删除该帖子的旧标签（如果有）
            db_helper.execute_update(
                "DELETE FROM post_tags WHERE post_id = %s",
                (post_id,)
            )

            # 插入新标签
            for tag_name, weight in tags:
                tag_id = self._get_or_create_tag(tag_name)
                if tag_id:
                    db_helper.execute_update(
                        """INSERT INTO post_tags (post_id, tag_id, weight)
                           VALUES (%s, %s, %s)
                           ON CONFLICT (post_id, tag_id) 
                           DO UPDATE SET weight = EXCLUDED.weight""",
                        (post_id, tag_id, weight)
                    )

            logger.info(f"Post {post_id}: 成功保存 {len(tags)} 个标签")
            return True
        except Exception as e:
            logger.error(f"保存标签失败: {e}")
            return False

    def _get_or_create_tag(self, tag_name: str) -> Optional[int]:
        """查找或创建标签（PostgreSQL 兼容版本）"""
        try:
            # 先查找是否存在
            result = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            if result:
                return result['id']

            # 不存在则创建
            try:
                db_helper.execute_update(
                    "INSERT INTO tags (name, type) VALUES (%s, %s)",
                    (tag_name, 'auto')
                )
            except Exception as insert_error:
                # 可能是并发插入导致的唯一约束冲突
                logger.warning(f"插入标签时发生冲突，尝试重新查询: {tag_name}")
                result = db_helper.execute_query(
                    "SELECT id FROM tags WHERE name = %s",
                    (tag_name,),
                    fetch_all=False
                )
                if result:
                    return result['id']
                raise insert_error

            # 获取新插入的标签ID
            new_tag = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            if new_tag:
                self.tag_names.add(tag_name)
                return new_tag['id']

            return None

        except Exception as e:
            logger.error(f"创建标签失败 '{tag_name}': {e}")
            return None

    def get_all_tags(self) -> List[Dict]:
        """获取所有标签"""
        try:
            return db_helper.execute_query(
                "SELECT id, name, type, created_at FROM tags ORDER BY name"
            )
        except Exception as e:
            logger.error(f"获取标签列表失败: {e}")
            return []

    def add_tag(self, name: str, tag_type: str = "content", weight: float = 1.5) -> Dict:
        """添加标签"""
        try:
            existing = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (name,),
                fetch_all=False
            )

            if existing:
                return {"success": False, "message": f"标签 '{name}' 已存在"}

            db_helper.execute_update(
                "INSERT INTO tags (name, type) VALUES (%s, %s)",
                (name, tag_type)
            )
            self.tag_names.add(name)

            return {"success": True, "message": f"标签 '{name}' 添加成功"}
        except Exception as e:
            return {"success": False, "message": str(e)}

    def get_all_stopwords(self) -> List[str]:
        """获取所有停用词"""
        try:
            results = db_helper.execute_query("SELECT word FROM stopwords ORDER BY word")
            return [row['word'] for row in results]
        except Exception as e:
            return list(self.stop_words)

    def add_stopword(self, word: str) -> Dict:
        """添加停用词"""
        try:
            existing = db_helper.execute_query(
                "SELECT id FROM stopwords WHERE word = %s",
                (word,),
                fetch_all=False
            )

            if existing:
                return {"success": False, "message": f"停用词 '{word}' 已存在"}

            db_helper.execute_update("INSERT INTO stopwords (word) VALUES (%s)", (word,))
            self.stop_words.add(word)

            return {"success": True, "message": f"停用词 '{word}' 添加成功"}
        except Exception as e:
            return {"success": False, "message": str(e)}

    def reload_from_db(self) -> Dict:
        """重新从数据库加载数据"""
        self._load_tags_from_db()
        self._load_stopwords_from_db()
        return {
            "success": True,
            "tags_count": len(self.tag_names),
            "stopwords_count": len(self.stop_words)
        }