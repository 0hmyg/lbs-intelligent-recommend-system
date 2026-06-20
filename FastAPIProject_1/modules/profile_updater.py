"""
用户画像管理模块
提供画像创建（默认画像）、单用户更新、批量更新接口
"""

import json
import logging
from typing import Dict, List, Optional
from datetime import datetime
from modules.recommender import ContentRecommender
from utils.db_helper import db_helper

logger = logging.getLogger(__name__)


class ProfileUpdater:
    """用户画像管理器（创建 + 更新）"""

    # ==================== 默认画像方案 ====================
    DEFAULT_PROFILES = {
        'general': {
            "生活": 0.20,
            "科技": 0.15,
            "美食": 0.15,
            "音乐": 0.10,
            "电影": 0.10,
            "读书": 0.10,
            "旅游": 0.10,
            "健康": 0.10,
        },
        'neutral': {
            "综合": 0.30,
            "资讯": 0.20,
            "生活": 0.20,
            "科普": 0.15,
            "娱乐": 0.15,
        },
        'minimal': {
            "综合": 0.10,
            "通用": 0.10,
        }
    }

    def __init__(self, recommender: ContentRecommender):
        """
        初始化
        Args:
            recommender: ContentRecommender 实例
        """
        self.recommender = recommender

    # ==================== 画像创建（注册时调用） ====================

    def create_default_profile(
        self,
        user_id: int,
        profile_type: str = 'general'
    ) -> Dict:
        """
        为新注册用户创建默认画像

        Args:
            user_id: 用户ID
            profile_type: 画像方案（general/neutral/minimal）

        Returns:
            {
                'success': bool,
                'message': str,
                'profile_type': str,
                'tag_count': int,
                'tags': [...]
            }
        """
        try:
            # 1. 检查是否已有画像
            existing = db_helper.execute_query(
                "SELECT id, tag_vector FROM user_profiles WHERE user_id = %s",
                (user_id,),
                fetch_all=False
            )

            if existing:
                existing_tags = existing.get('tag_vector', {})
                if isinstance(existing_tags, str):
                    existing_tags = json.loads(existing_tags)
                return {
                    'success': True,
                    'message': f'用户 {user_id} 画像已存在，跳过创建',
                    'profile_type': 'existing',
                    'tag_count': len(existing_tags),
                    'tags': []
                }

            # 2. 获取默认标签方案
            tags = self.DEFAULT_PROFILES.get(profile_type, self.DEFAULT_PROFILES['general'])

            # 3. 确保所有标签在 tags 表中存在
            tag_list = self._ensure_tags_exist(tags)

            # 4. 保存画像到 user_profiles 表
            tag_vector = json.dumps(tags, ensure_ascii=False)
            db_helper.execute_update(
                """INSERT INTO user_profiles (user_id, tag_vector, updated_at) 
                   VALUES (%s, %s::jsonb, NOW())""",
                (user_id, tag_vector)
            )

            tag_names = list(tags.keys())
            logger.info(f"用户 {user_id} 默认画像创建成功: {tag_names}")

            return {
                'success': True,
                'message': f'用户 {user_id} 默认画像创建成功',
                'profile_type': profile_type,
                'tag_count': len(tags),
                'tags': tag_list
            }

        except Exception as e:
            logger.error(f"创建用户 {user_id} 画像失败: {e}")
            return {
                'success': False,
                'message': f'创建失败: {str(e)}',
                'profile_type': profile_type,
                'tag_count': 0,
                'tags': []
            }

    def _ensure_tags_exist(self, tags: Dict[str, float]) -> List[Dict]:
        """
        确保所有标签在 tags 表中存在，不存在则创建

        Args:
            tags: {标签名: 权重}

        Returns:
            [{'tag_name': ..., 'weight': ..., 'tag_id': ...}]
        """
        tag_list = []

        for tag_name, weight in tags.items():
            # 查找标签
            result = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            if not result:
                # 创建新标签
                try:
                    db_helper.execute_update(
                        "INSERT INTO tags (name, type, weight) VALUES (%s, 'auto', 1.5)",
                        (tag_name,)
                    )
                    logger.info(f"创建新标签: '{tag_name}'")
                except Exception:
                    # 并发冲突，忽略
                    pass

                # 重新查询
                result = db_helper.execute_query(
                    "SELECT id FROM tags WHERE name = %s",
                    (tag_name,),
                    fetch_all=False
                )

            if result:
                tag_list.append({
                    'tag_name': tag_name,
                    'weight': weight,
                    'tag_id': result['id']
                })

        return tag_list

    def batch_create_default_profiles(
        self,
        profile_type: str = 'general',
        max_users: int = None,
        min_register_hours: int = 0
    ) -> Dict:
        """
        批量为无画像用户创建默认画像

        Args:
            profile_type: 画像方案
            max_users: 最多处理用户数
            min_register_hours: 最小注册小时数

        Returns:
            处理统计
        """
        try:
            # 查询无画像用户
            query = """
                SELECT u.id, u.username
                FROM users u
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_profiles up WHERE up.user_id = u.id
                )
            """
            params = []

            if min_register_hours > 0:
                query += " AND u.created_at <= NOW() - INTERVAL '%s hours'"
                params.append(min_register_hours)

            query += " ORDER BY u.id"

            if max_users:
                query += " LIMIT %s"
                params.append(max_users)

            users = db_helper.execute_query(query, tuple(params) if params else None)

            if not users:
                return {
                    'total': 0, 'success': 0, 'failed': 0,
                    'message': '没有需要创建画像的用户'
                }

            # 批量创建
            success = 0
            failed = 0
            skipped = 0

            for user in users:
                result = self.create_default_profile(user['id'], profile_type)
                if result['success']:
                    if result['profile_type'] == 'existing':
                        skipped += 1
                    else:
                        success += 1
                else:
                    failed += 1

            return {
                'total': len(users),
                'success': success,
                'failed': failed,
                'skipped': skipped,
                'message': f'批量创建完成：成功{success}，失败{failed}，跳过{skipped}'
            }

        except Exception as e:
            logger.error(f"批量创建画像失败: {e}")
            return {
                'total': 0, 'success': 0, 'failed': 0,
                'message': f'批量创建失败: {str(e)}'
            }

    # ==================== 画像更新（行为驱动） ====================

    def update_single_user(
        self,
        user_id: int,
        view_limit: int = 50,
        like_limit: int = 20,
        comment_limit: int = 10,
        share_limit: int = 5,
        old_profile_weight: float = 0.3
    ) -> Dict:
        """
        更新指定用户的画像（基于行为日志）

        Args:
            user_id: 用户ID
            view_limit: 取最近N条浏览记录
            like_limit: 取最近N条点赞记录
            comment_limit: 取最近N条评论记录
            share_limit: 取最近N条分享记录
            old_profile_weight: 老画像权重（0-1）

        Returns:
            {'success': bool, 'message': str, 'old_tag_count': int, 'new_tag_count': int}
        """
        try:
            # 获取老画像（如果没有，返回空）
            old_profile = self.recommender.get_user_profile(user_id)
            old_tag_count = len(old_profile) if old_profile else 0

            # 计算新画像
            new_profile = self.recommender.calculate_user_profile(
                user_id=user_id,
                view_limit=view_limit,
                like_limit=like_limit,
                comment_limit=comment_limit,
                share_limit=share_limit,
                old_profile_weight=old_profile_weight
            )

            if not new_profile:
                return {
                    'success': False,
                    'message': f'用户 {user_id} 无最近行为数据',
                    'old_tag_count': old_tag_count,
                    'new_tag_count': 0
                }

            # 保存画像
            self.recommender.save_user_profile(user_id, new_profile)

            logger.info(
                f"用户 {user_id} 画像更新: "
                f"旧标签数={old_tag_count}, 新标签数={len(new_profile)}"
            )

            return {
                'success': True,
                'message': f'用户 {user_id} 画像更新成功',
                'old_tag_count': old_tag_count,
                'new_tag_count': len(new_profile)
            }

        except Exception as e:
            logger.error(f"更新用户 {user_id} 画像失败: {e}")
            return {
                'success': False,
                'message': f'更新失败: {str(e)}',
                'old_tag_count': 0,
                'new_tag_count': 0
            }

    def update_batch_users(
        self,
        min_actions: int = 5,
        since_hours: int = 24,
        view_limit: int = 50,
        like_limit: int = 20,
        comment_limit: int = 10,
        share_limit: int = 5,
        old_profile_weight: float = 0.3
    ) -> Dict:
        """
        批量更新符合条件的用户画像

        Args:
            min_actions: 最近新增行为的最小条数
            since_hours: 检查最近多少小时内的行为
            view_limit: 取最近N条浏览记录
            like_limit: 取最近N条点赞记录
            comment_limit: 取最近N条评论记录
            share_limit: 取最近N条分享记录
            old_profile_weight: 老画像权重（0-1）

        Returns:
            处理统计
        """
        try:
            # 获取需要更新的用户列表
            user_ids = self.recommender.get_users_need_update(
                min_actions=min_actions,
                since_hours=since_hours
            )

            if not user_ids:
                return {
                    'total': 0, 'success': 0, 'failed': 0, 'skipped': 0,
                    'message': '没有需要更新的用户',
                    'details': []
                }

            # 逐个更新
            results = []
            success_count = 0
            failed_count = 0
            skipped_count = 0

            for user_id in user_ids:
                result = self.update_single_user(
                    user_id=user_id,
                    view_limit=view_limit,
                    like_limit=like_limit,
                    comment_limit=comment_limit,
                    share_limit=share_limit,
                    old_profile_weight=old_profile_weight
                )

                result['user_id'] = user_id
                results.append(result)

                if result['success']:
                    success_count += 1
                elif '无最近行为' in result.get('message', ''):
                    skipped_count += 1
                else:
                    failed_count += 1

            return {
                'total': len(user_ids),
                'success': success_count,
                'failed': failed_count,
                'skipped': skipped_count,
                'message': f'批量更新完成：成功{success_count}，失败{failed_count}，跳过{skipped_count}',
                'details': results
            }

        except Exception as e:
            logger.error(f"批量更新用户画像失败: {e}")
            return {
                'total': 0, 'success': 0, 'failed': 0, 'skipped': 0,
                'message': f'批量更新失败: {str(e)}',
                'details': []
            }

    # ==================== 画像查询 ====================

    def get_profile(self, user_id: int) -> Optional[Dict]:
        """获取用户画像"""
        try:
            result = db_helper.execute_query(
                "SELECT user_id, tag_vector, updated_at FROM user_profiles WHERE user_id = %s",
                (user_id,),
                fetch_all=False
            )
            return result
        except Exception as e:
            logger.error(f"查询用户 {user_id} 画像失败: {e}")
            return None

    def get_profile_stats(self) -> Dict:
        """获取画像统计信息"""
        try:
            total_users = db_helper.execute_query(
                "SELECT COUNT(*) as count FROM users", fetch_all=False
            )
            with_profile = db_helper.execute_query(
                "SELECT COUNT(*) as count FROM user_profiles", fetch_all=False
            )
            with_actions = db_helper.execute_query(
                "SELECT COUNT(DISTINCT user_id) as count FROM user_actions", fetch_all=False
            )

            total = total_users['count'] if total_users else 0
            profiled = with_profile['count'] if with_profile else 0
            active = with_actions['count'] if with_actions else 0

            return {
                'total_users': total,
                'users_with_profile': profiled,
                'users_with_actions': active,
                'users_without_profile': total - profiled,
                'coverage': round(profiled / total * 100, 1) if total > 0 else 0
            }
        except Exception as e:
            logger.error(f"获取画像统计失败: {e}")
            return {
                'total_users': 0, 'users_with_profile': 0,
                'users_with_actions': 0, 'users_without_profile': 0, 'coverage': 0
            }