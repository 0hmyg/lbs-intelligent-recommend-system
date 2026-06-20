"""
敏感词过滤模块（数据库直连版）
从 PostgreSQL 的 sensitive_words 表读取敏感词
"""

from typing import List, Dict, Set
import logging
from utils.db_helper import db_helper

logger = logging.getLogger(__name__)


class SensitiveFilter:
    """敏感词过滤器（从数据库加载）"""

    def __init__(self):
        """初始化过滤器，从数据库加载敏感词"""
        self.block_words: Set[str] = set()
        self.review_words: Set[str] = set()

        # 从数据库加载
        self._load_from_db()

        logger.info(f"敏感词过滤器初始化完成: block={len(self.block_words)}, review={len(self.review_words)}")

    def _load_from_db(self):
        """从数据库加载敏感词"""
        try:
            results = db_helper.execute_query(
                "SELECT word, level FROM sensitive_words"
            )

            self.block_words.clear()
            self.review_words.clear()

            for row in results:
                word = row['word']
                level = row['level']
                if level == 'block':
                    self.block_words.add(word)
                else:
                    self.review_words.add(word)

            logger.info(f"从数据库加载敏感词: block={len(self.block_words)}, review={len(self.review_words)}")

        except Exception as e:
            logger.error(f"加载敏感词失败: {e}")
            self._load_default_words()

    def _load_default_words(self):
        """加载默认敏感词"""
        self.block_words = {'毒品', '色情', '赌博', '枪支', '诈骗', '暴力', '邪教'}
        self.review_words = {'违法', '骂人', '骗子'}

    def reload_from_db(self) -> Dict:
        """重新从数据库加载"""
        self._load_from_db()
        return {
            'block_count': len(self.block_words),
            'review_count': len(self.review_words),
            'total': len(self.block_words) + len(self.review_words)
        }

    def filter(self, text: str) -> Dict:
        """过滤文本中的敏感词"""
        if not text:
            return {'is_valid': True, 'filtered_text': '', 'hit_words': []}

        hit_words = []
        filtered_text = text
        has_block_word = False

        for word in self.block_words:
            if word in text:
                hit_words.append(word)
                has_block_word = True
                filtered_text = filtered_text.replace(word, '*' * len(word))

        for word in self.review_words:
            if word in text and word not in hit_words:
                hit_words.append(word)
                filtered_text = filtered_text.replace(word, '*' * len(word))

        return {
            'is_valid': not has_block_word,
            'filtered_text': filtered_text,
            'hit_words': hit_words
        }

    def check(self, text: str) -> bool:
        """快速检查"""
        if not text:
            return True
        for word in self.block_words:
            if word in text:
                return False
        return True

    def get_all_words(self) -> Dict:
        """获取所有敏感词"""
        try:
            results = db_helper.execute_query(
                "SELECT word, level, created_at FROM sensitive_words ORDER BY level, word"
            )
            block_words = [{'word': r['word'], 'created_at': str(r['created_at'])}
                          for r in results if r['level'] == 'block']
            review_words = [{'word': r['word'], 'created_at': str(r['created_at'])}
                           for r in results if r['level'] == 'review']
            return {
                'block': block_words,
                'review': review_words,
                'total': len(results)
            }
        except Exception as e:
            logger.error(f"获取敏感词失败: {e}")
            return {'block': [], 'review': [], 'total': 0}