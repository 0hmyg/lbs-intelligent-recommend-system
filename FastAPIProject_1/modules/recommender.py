"""
内容推荐模块
基于用户画像与内容标签的余弦相似度计算
"""

import json
import numpy as np
from typing import List, Dict, Optional
import logging
from datetime import datetime, timedelta
from collections import defaultdict
from functools import lru_cache
from utils.db_helper import db_helper

logger = logging.getLogger(__name__)


class ContentRecommender:
    """内容推荐器"""

    # 行为权重配置
    ACTION_WEIGHTS = {
        'view': 0.3,
        'like': 1.0,
        'comment': 0.8,
        'share': 1.2
    }

    # 时间衰减半衰期（7天）
    DECAY_HALF_LIFE = 7 * 24 * 60 * 60

    def __init__(self):
        """初始化，加载标签缓存"""
        self._tag_cache = {}
        self._refresh_tag_cache()

    def _refresh_tag_cache(self):
        """刷新标签缓存（id->name映射）"""
        try:
            results = db_helper.execute_query(
                "SELECT id, name, type, weight FROM tags"
            )
            self._tag_cache = {
                row['id']: {
                    'name': row['name'],
                    'type': row['type'],
                    'global_weight': row['weight']
                }
                for row in results
            }
        except Exception as e:
            logger.error(f"刷新标签缓存失败: {e}")

    def _calculate_time_decay(self, action_time: datetime) -> float:
        """计算时间衰减因子"""
        if not action_time:
            return 1.0
        if hasattr(action_time, 'tzinfo') and action_time.tzinfo:
            action_time = action_time.replace(tzinfo=None)
        elapsed = (datetime.now() - action_time).total_seconds()
        return max(0.5 ** (elapsed / self.DECAY_HALF_LIFE), 0.1)

    def _cosine_similarity(self, vec1: Dict[str, float],
                           vec2: Dict[str, float]) -> float:
        """计算余弦相似度"""
        if not vec1 or not vec2:
            return 0.0

        all_keys = set(vec1.keys()) | set(vec2.keys())
        v1 = np.array([vec1.get(k, 0.0) for k in all_keys])
        v2 = np.array([vec2.get(k, 0.0) for k in all_keys])

        dot_product = np.dot(v1, v2)
        norm1 = np.linalg.norm(v1)
        norm2 = np.linalg.norm(v2)

        if norm1 == 0 or norm2 == 0:
            return 0.0

        return float(dot_product / (norm1 * norm2))

    def _normalize_vector(self, tag_scores: Dict[str, float]) -> Dict[str, float]:
        """向量归一化"""
        if not tag_scores:
            return {}
        max_score = max(tag_scores.values())
        if max_score == 0:
            return {}
        return {k: round(v / max_score, 4) for k, v in tag_scores.items()}

    @lru_cache(maxsize=100)
    def get_user_profile(self, user_id: int) -> Dict[str, float]:
        """获取用户画像"""
        try:
            result = db_helper.execute_query(
                "SELECT tag_vector FROM user_profiles WHERE user_id = %s",
                (user_id,),
                fetch_all=False
            )
            if result and result.get('tag_vector'):
                tag_vector = result['tag_vector']
                if isinstance(tag_vector, str):
                    tag_vector = json.loads(tag_vector)
                return tag_vector
        except Exception as e:
            logger.error(f"获取用户画像失败: {e}")
        return {}

    def get_post_tags(self, post_id: int) -> Dict[str, float]:
        """获取帖子标签向量"""
        try:
            results = db_helper.execute_query(
                """
                SELECT pt.weight as post_weight, pt.tag_id
                FROM post_tags pt
                WHERE pt.post_id = %s
                ORDER BY pt.weight DESC
                """,
                (post_id,)
            )

            tag_vector = {}
            for row in results:
                tag_info = self._tag_cache.get(row['tag_id'], {})
                tag_name = tag_info.get('name', f"tag_{row['tag_id']}")
                tag_vector[tag_name] = row['post_weight']

            return tag_vector
        except Exception as e:
            logger.error(f"获取帖子标签失败: {e}")
            return {}

    def get_nearby_posts(self, lat: float, lng: float,
                         radius: int = 5000) -> List[int]:
        """获取附近帖子"""
        try:
            results = db_helper.execute_query(
                """
                SELECT id FROM posts
                WHERE location_geom IS NOT NULL
                  AND ST_DWithin(
                      location_geom,
                      ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography,
                      %s
                  )
                  AND deleted_at IS NULL
                  AND is_audited = 1
                ORDER BY created_at DESC
                """,
                (lng, lat, radius)
            )
            return [row['id'] for row in results]
        except Exception as e:
            logger.error(f"获取附近帖子失败: {e}")
            return []

    def get_all_posts(self, limit: int = 100) -> List[int]:
        """获取所有已审核帖子"""
        try:
            results = db_helper.execute_query(
                """
                SELECT id FROM posts
                WHERE deleted_at IS NULL AND is_audited = 1
                ORDER BY created_at DESC
                LIMIT %s
                """,
                (limit,)
            )
            return [row['id'] for row in results]
        except Exception as e:
            logger.error(f"获取帖子列表失败: {e}")
            return []

    def get_hot_posts(self, limit: int = 20) -> List[Dict]:
        """获取热门帖子（冷启动）"""
        try:
            results = db_helper.execute_query(
                """
                SELECT id, view_count, like_count, comment_count
                FROM posts
                WHERE deleted_at IS NULL AND is_audited = 1
                ORDER BY (view_count * 0.3 + like_count * 0.5 + comment_count * 0.2) DESC
                LIMIT %s
                """,
                (limit,)
            )
            return [{
                'post_id': row['id'],
                'score': 0.5,
                'reason': '热门内容推荐'
            } for row in results]
        except Exception as e:
            logger.error(f"获取热门帖子失败: {e}")
            return []

    def recommend(self, user_id: int,
                  location_lat: Optional[float] = None,
                  location_lng: Optional[float] = None,
                  limit: int = 20) -> List[Dict]:
        """生成个性化推荐"""
        user_profile = self.get_user_profile(user_id)

        if not user_profile:
            logger.info(f"用户 {user_id} 无画像数据，返回热门内容")
            return self.get_hot_posts(limit)

        candidate_posts = []
        if location_lat and location_lng:
            candidate_posts = self.get_nearby_posts(location_lat, location_lng, 5000)

        if len(candidate_posts) < limit * 2:
            all_posts = self.get_all_posts(limit * 3)
            candidate_posts = list(set(candidate_posts + all_posts))

        scores = []
        for post_id in candidate_posts:
            post_tags = self.get_post_tags(post_id)
            if post_tags:
                similarity = self._cosine_similarity(user_profile, post_tags)
                scores.append({
                    'post_id': post_id,
                    'score': round(similarity, 4),
                    'reason': '基于您的兴趣推荐'
                })

        scores.sort(key=lambda x: x['score'], reverse=True)
        return scores[:limit]

    def get_recent_actions(self, user_id: int, limits: Dict[str, int]) -> List[Dict]:
        """
        获取用户最近各类行为日志

        Args:
            user_id: 用户ID
            limits: 各类行为数量限制，如 {'view': 20, 'like': 10, 'comment': 5, 'share': 3}

        Returns:
            行为列表，每条包含 action_type, post_id, action_time, tag_scores
        """
        actions = []

        for action_type, limit in limits.items():
            if limit <= 0:
                continue

            results = db_helper.execute_query(
                """
                SELECT ua.action_type, ua.post_id, ua.action_time,
                       pt.tag_id, pt.weight as post_weight
                FROM user_actions ua
                JOIN post_tags pt ON ua.post_id = pt.post_id
                WHERE ua.user_id = %s AND ua.action_type = %s
                ORDER BY ua.action_time DESC
                LIMIT %s
                """,
                (user_id, action_type, limit)
            )

            # 按post_id分组标签
            post_tags_map = defaultdict(dict)
            for row in results:
                tag_info = self._tag_cache.get(row['tag_id'], {})
                tag_name = tag_info.get('name', f"tag_{row['tag_id']}")
                post_tags_map[row['post_id']][tag_name] = row['post_weight']

            # 获取去重的帖子行为（取最近一次）
            post_actions = {}
            for row in results:
                if row['post_id'] not in post_actions:
                    post_actions[row['post_id']] = {
                        'action_type': row['action_type'],
                        'post_id': row['post_id'],
                        'action_time': row['action_time'],
                        'tag_scores': post_tags_map[row['post_id']]
                    }

            actions.extend(post_actions.values())

        return actions

    def calculate_user_profile(self, user_id: int,
                               view_limit: int = 50,
                               like_limit: int = 20,
                               comment_limit: int = 10,
                               share_limit: int = 5,
                               old_profile_weight: float = 0.3) -> Dict[str, float]:
        """
        基于最近N条行为日志 + 老画像 计算用户画像

        Args:
            user_id: 用户ID
            view_limit: 最近浏览行为条数
            like_limit: 最近点赞行为条数
            comment_limit: 最近评论行为条数
            share_limit: 最近分享行为条数
            old_profile_weight: 老画像权重（0-1），0表示完全不用老画像

        Returns:
            标签权重向量
        """
        try:
            # 1. 获取最近各类行为日志
            limits = {
                'view': view_limit,
                'like': like_limit,
                'comment': comment_limit,
                'share': share_limit
            }
            recent_actions = self.get_recent_actions(user_id, limits)

            if not recent_actions:
                logger.info(f"用户 {user_id} 无最近行为")
                return {}

            # 2. 计算新画像（基于最近行为）
            tag_scores = defaultdict(float)
            for action in recent_actions:
                action_weight = self.ACTION_WEIGHTS.get(action['action_type'], 0.1)
                time_decay = self._calculate_time_decay(action['action_time'])

                for tag, weight in action['tag_scores'].items():
                    tag_scores[tag] += weight * action_weight * time_decay

            # 归一化新画像
            new_profile = self._normalize_vector(dict(tag_scores))

            # 3. 融合老画像
            if old_profile_weight > 0:
                old_profile = self.get_user_profile(user_id)
                if old_profile:
                    # 老画像按时间衰减（假设平均1天）
                    old_decay = self._calculate_time_decay(
                        datetime.now() - timedelta(days=1)
                    )

                    merged_profile = defaultdict(float)
                    # 新画像权重
                    new_weight = 1 - old_profile_weight
                    for tag, score in new_profile.items():
                        merged_profile[tag] += score * new_weight

                    # 老画像权重（带衰减）
                    for tag, score in old_profile.items():
                        merged_profile[tag] += score * old_profile_weight * old_decay

                    return self._normalize_vector(dict(merged_profile))

            return new_profile

        except Exception as e:
            logger.error(f"计算用户画像失败: {e}")
            return {}

    def save_user_profile(self, user_id: int, tag_vector: Dict[str, float]):
        """保存用户画像"""
        try:
            self.get_user_profile.cache_clear()

            db_helper.execute_update(
                """
                INSERT INTO user_profiles (user_id, tag_vector, updated_at)
                VALUES (%s, %s::jsonb, NOW())
                ON CONFLICT (user_id) DO UPDATE
                SET tag_vector = EXCLUDED.tag_vector,
                    updated_at = NOW()
                """,
                (user_id, json.dumps(tag_vector, ensure_ascii=False))
            )
            logger.info(f"用户 {user_id} 画像已更新，标签数: {len(tag_vector)}")
        except Exception as e:
            logger.error(f"保存用户画像失败: {e}")

    def get_users_need_update(self, min_actions: int = 5,
                              since_hours: int = 24) -> List[int]:
        """
        获取需要更新画像的用户ID列表

        Args:
            min_actions: 最近新增行为的最小条数
            since_hours: 检查最近多少小时内的行为

        Returns:
            用户ID列表
        """
        try:
            results = db_helper.execute_query(
                """
                SELECT user_id, COUNT(*) as action_count
                FROM user_actions
                WHERE action_time > NOW() - INTERVAL '%s hours'
                GROUP BY user_id
                HAVING COUNT(*) >= %s
                ORDER BY action_count DESC
                """,
                (since_hours, min_actions)
            )
            return [row['user_id'] for row in results]
        except Exception as e:
            logger.error(f"获取需要更新的用户失败: {e}")
            return []