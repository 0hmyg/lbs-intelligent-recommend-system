"""
默认画像初始化脚本（适配 user_profiles + user_actions 表）
为没有任何行为记录的用户分配温和的默认标签画像
"""

import sys
import json
import logging
from typing import Dict, List, Optional
from datetime import datetime

# 导入你的模块
from modules.recommender import ContentRecommender
from utils.db_helper import db_helper

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('default_profile_init.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class DefaultProfileInitializer:
    """
    默认用户画像初始化器
    为没有任何行为数据的用户分配温和的默认标签画像
    适配表：user_profiles (JSONB), user_actions (行为日志)
    """

    def __init__(self, recommender: ContentRecommender):
        """
        初始化

        Args:
            recommender: ContentRecommender 实例
        """
        self.recommender = recommender

        # 定义默认标签方案（权重很低，不影响后续真实行为建立的画像）
        self.default_profiles = {
            # 方案1：通用大众标签（推荐）
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

            # 方案2：更中性的标签
            'neutral': {
                "综合": 0.30,
                "资讯": 0.20,
                "生活": 0.20,
                "科普": 0.15,
                "娱乐": 0.15,
            },

            # 方案3：极简标签（影响最小）
            'minimal': {
                "综合": 0.10,
                "通用": 0.10,
            }
        }

        self.stats = {
            'total_users': 0,
            'no_profile_users': 0,
            'initialized': 0,
            'failed': 0,
            'already_has_profile': 0,
            'tags_created': 0,
            'tags_existing': 0,
        }

    def ensure_tags_exist(self, profile_type: str = 'general') -> Dict[str, int]:
        """
        确保默认标签方案中的所有标签在 tags 表中存在

        Args:
            profile_type: 画像方案类型

        Returns:
            标签ID映射 {tag_name: tag_id}
        """
        tags = self.default_profiles.get(profile_type, self.default_profiles['general'])
        tag_name_to_id = {}

        logger.info(f"检查并确保 {len(tags)} 个默认标签存在...")

        for tag_name in tags.keys():
            tag_id = self._get_or_create_tag(tag_name)
            if tag_id:
                tag_name_to_id[tag_name] = tag_id

        logger.info(f"标签检查完成: 已有 {self.stats['tags_existing']}, 新建 {self.stats['tags_created']}")
        return tag_name_to_id

    def _get_or_create_tag(self, tag_name: str, tag_type: str = 'auto') -> Optional[int]:
        """
        查找标签，不存在则创建（PostgreSQL 兼容）

        Args:
            tag_name: 标签名
            tag_type: 标签类型

        Returns:
            标签ID
        """
        try:
            # 先查找是否存在
            result = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            if result:
                self.stats['tags_existing'] += 1
                return result['id']

            # 不存在则创建
            logger.info(f"创建新标签: '{tag_name}'")

            try:
                db_helper.execute_update(
                    "INSERT INTO tags (name, type) VALUES (%s, %s)",
                    (tag_name, tag_type)
                )
                self.stats['tags_created'] += 1

            except Exception as insert_error:
                # 可能是并发插入导致的唯一约束冲突
                error_msg = str(insert_error).lower()
                if 'unique' in error_msg or 'duplicate' in error_msg:
                    logger.warning(f"标签 '{tag_name}' 已被并发创建，重新查询")
                else:
                    raise insert_error

            # 获取标签ID
            new_tag = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            if new_tag:
                logger.info(f"标签就绪: '{tag_name}' (ID: {new_tag['id']})")
                return new_tag['id']

            logger.error(f"无法获取标签 '{tag_name}' 的ID")
            return None

        except Exception as e:
            logger.error(f"处理标签 '{tag_name}' 失败: {e}")
            return None

    def get_users_without_profile(self,
                                  limit: int = None,
                                  min_register_hours: int = 0) -> List[Dict]:
        """
        查询 user_profiles 表中没有记录的用户

        Args:
            limit: 限制数量
            min_register_hours: 最小注册小时数

        Returns:
            用户列表 [{'id': ..., 'username': ...}, ...]
        """
        query = """
            SELECT u.id, u.username
            FROM users u
            WHERE NOT EXISTS (
                SELECT 1 
                FROM user_profiles up 
                WHERE up.user_id = u.id
            )
        """

        params = []

        # 只处理注册超过一定时间的用户
        if min_register_hours > 0:
            query += " AND u.created_at <= NOW() - INTERVAL '%s hours'"
            params.append(min_register_hours)

        # 排除被删除/禁用的用户
        if self._column_exists('users', 'is_active'):
            query += " AND u.is_active = TRUE"

        query += " ORDER BY u.id"

        if limit:
            query += " LIMIT %s"
            params.append(limit)

        try:
            results = db_helper.execute_query(query, tuple(params) if params else None)

            logger.info(f"找到 {len(results)} 个无画像用户" +
                       (f"（注册>{min_register_hours}小时）" if min_register_hours > 0 else ""))

            return results

        except Exception as e:
            logger.error(f"查询无画像用户失败: {e}")
            return []

    def get_users_without_behavior(self,
                                   limit: int = None,
                                   min_register_hours: int = 24) -> List[Dict]:
        """
        查询 user_actions 表中没有任何行为记录且 user_profiles 表中无画像的用户

        Args:
            limit: 限制数量
            min_register_hours: 最小注册小时数

        Returns:
            用户列表 [{'id': ..., 'username': ...}, ...]
        """
        query = """
            SELECT u.id, u.username
            FROM users u
            WHERE NOT EXISTS (
                SELECT 1 FROM user_profiles up WHERE up.user_id = u.id
            )
            AND NOT EXISTS (
                SELECT 1 FROM user_actions ua WHERE ua.user_id = u.id
            )
        """

        params = []

        if min_register_hours > 0:
            query += " AND u.created_at <= NOW() - INTERVAL '%s hours'"
            params.append(min_register_hours)

        query += " ORDER BY u.id"

        if limit:
            query += " LIMIT %s"
            params.append(limit)

        try:
            results = db_helper.execute_query(query, tuple(params) if params else None)

            logger.info(f"找到 {len(results)} 个无行为且无画像的用户")
            return results

        except Exception as e:
            logger.error(f"查询失败: {e}")
            return []

    def get_total_users_count(self) -> int:
        """获取总用户数"""
        try:
            result = db_helper.execute_query(
                "SELECT COUNT(*) as count FROM users",
                fetch_all=False
            )
            return result['count'] if result else 0
        except:
            return 0

    def get_users_with_profile_count(self) -> int:
        """获取已有画像的用户数"""
        try:
            result = db_helper.execute_query(
                "SELECT COUNT(*) as count FROM user_profiles",
                fetch_all=False
            )
            return result['count'] if result else 0
        except:
            return 0

    def get_users_with_actions_count(self) -> int:
        """获取有行为记录的用户数"""
        try:
            result = db_helper.execute_query(
                "SELECT COUNT(DISTINCT user_id) as count FROM user_actions",
                fetch_all=False
            )
            return result['count'] if result else 0
        except:
            return 0

    def init_default_profile(self,
                            user_id: int,
                            username: str = None,
                            profile_type: str = 'general') -> bool:
        """
        为单个用户初始化默认画像（直接写入 user_profiles 表）

        Args:
            user_id: 用户ID
            username: 用户名（用于日志）
            profile_type: 画像方案类型

        Returns:
            是否成功
        """
        try:
            # 检查是否已有画像
            existing = db_helper.execute_query(
                "SELECT id FROM user_profiles WHERE user_id = %s",
                (user_id,),
                fetch_all=False
            )

            if existing:
                logger.debug(f"用户 {user_id} 已有画像，跳过")
                self.stats['already_has_profile'] += 1
                return False

            # 获取标签方案
            tags = self.default_profiles.get(profile_type, self.default_profiles['general'])

            # 转换为 JSONB 格式
            tag_vector = json.dumps(tags, ensure_ascii=False)

            # 保存到 user_profiles 表
            db_helper.execute_update(
                """INSERT INTO user_profiles (user_id, tag_vector, updated_at) 
                   VALUES (%s, %s::jsonb, NOW())
                   ON CONFLICT (user_id) DO NOTHING""",  # 防止并发冲突
                (user_id, tag_vector)
            )

            tag_names = list(tags.keys())
            user_info = f"用户 {user_id}"
            if username:
                user_info += f" ({username})"
            logger.info(f"✓ {user_info}: 已初始化默认画像 {tag_names}")
            return True

        except Exception as e:
            logger.error(f"初始化用户 {user_id} 画像失败: {e}")
            self.stats['failed'] += 1
            return False

    def batch_init_profiles(self,
                           profile_type: str = 'general',
                           max_users: int = None,
                           min_register_hours: int = 24,
                           only_no_behavior: bool = True,
                           batch_size: int = 50) -> Dict:
        """
        批量初始化无画像用户的默认画像

        Args:
            profile_type: 画像方案 ('general', 'neutral', 'minimal')
            max_users: 最多处理用户数
            min_register_hours: 最小注册小时数
            only_no_behavior: 是否只处理完全无行为的用户
            batch_size: 每批处理数量

        Returns:
            处理统计
        """
        start_time = datetime.now()

        logger.info(f"{'='*60}")
        logger.info(f"开始批量初始化默认用户画像")
        logger.info(f"画像方案: {profile_type}")
        logger.info(f"最大处理数: {max_users or '无限制'}")
        logger.info(f"最小注册时长: {min_register_hours} 小时")
        logger.info(f"仅无行为用户: {only_no_behavior}")
        logger.info(f"{'='*60}")

        # 第一步：确保所需标签在 tags 表中存在
        logger.info("\n【步骤1】检查并创建所需标签...")
        self.ensure_tags_exist(profile_type)

        # 第二步：获取目标用户
        logger.info(f"\n【步骤2】查询目标用户...")
        if only_no_behavior:
            users = self.get_users_without_behavior(
                limit=max_users,
                min_register_hours=min_register_hours
            )
        else:
            users = self.get_users_without_profile(
                limit=max_users,
                min_register_hours=min_register_hours
            )

        self.stats['total_users'] = self.get_total_users_count()
        self.stats['no_profile_users'] = len(users)

        if not users:
            logger.info("没有需要处理的用户")
            return self._build_result()

        # 第三步：批量初始化画像
        logger.info(f"\n【步骤3】批量初始化画像（共 {len(users)} 个用户）...")

        total = len(users)
        for i in range(0, total, batch_size):
            batch = users[i:i + batch_size]
            batch_num = (i // batch_size) + 1

            logger.info(f"\n--- 处理第 {batch_num} 批 ({len(batch)} 个用户) ---")

            for j, user in enumerate(batch, 1):
                user_id = user['id']
                username = user.get('username', None)

                self.init_default_profile(user_id, username, profile_type)

                if self.stats['initialized'] > 0 and self.stats['initialized'] % 20 == 0:
                    logger.info(
                        f"已初始化: {self.stats['initialized']}/{total} | "
                        f"已跳过: {self.stats['already_has_profile']} | "
                        f"失败: {self.stats['failed']}"
                    )

        # 输出结果
        elapsed = (datetime.now() - start_time).total_seconds()
        users_with_profile = self.get_users_with_profile_count()

        logger.info(f"\n{'='*60}")
        logger.info(f"初始化完成！耗时: {elapsed:.1f} 秒")
        logger.info(f"标签统计: 已存在 {self.stats['tags_existing']}, 新创建 {self.stats['tags_created']}")
        logger.info(f"总用户数: {self.stats['total_users']}")
        logger.info(f"已有画像用户: {users_with_profile}")
        logger.info(f"有行为记录用户: {self.get_users_with_actions_count()}")
        logger.info(f"本次处理无画像用户: {self.stats['no_profile_users']}")
        logger.info(f"成功初始化: {self.stats['initialized']}")
        logger.info(f"跳过(已有画像): {self.stats['already_has_profile']}")
        logger.info(f"失败: {self.stats['failed']}")

        if self.stats['total_users'] > 0:
            coverage = users_with_profile / self.stats['total_users'] * 100
            logger.info(f"画像覆盖率: {users_with_profile}/{self.stats['total_users']} ({coverage:.1f}%)")

        logger.info(f"{'='*60}")

        return self._build_result()

    def _build_result(self) -> Dict:
        """构建返回结果"""
        return {
            'success': True,
            'total_users': self.stats['total_users'],
            'no_profile_users': self.stats['no_profile_users'],
            'initialized': self.stats['initialized'],
            'already_has_profile': self.stats['already_has_profile'],
            'failed': self.stats['failed'],
            'tags_created': self.stats['tags_created'],
            'tags_existing': self.stats['tags_existing'],
            'message': f"成功初始化 {self.stats['initialized']} 个用户的默认画像，创建了 {self.stats['tags_created']} 个新标签"
        }

    def preview_default_tags(self, profile_type: str = 'general') -> List[Dict]:
        """
        预览默认标签方案（包括数据库中的存在状态）

        Args:
            profile_type: 画像方案类型

        Returns:
            标签详情列表
        """
        tags = self.default_profiles.get(profile_type, self.default_profiles['general'])

        result = []
        for tag_name, weight in tags.items():
            # 检查标签是否存在
            exists = db_helper.execute_query(
                "SELECT id FROM tags WHERE name = %s",
                (tag_name,),
                fetch_all=False
            )

            result.append({
                'tag_name': tag_name,
                'weight': weight,
                'impact': self._assess_impact(weight),
                'exists_in_db': exists is not None,
                'tag_id': exists['id'] if exists else None
            })

        return result

    def show_profile_sample(self, profile_type: str = 'general'):
        """显示画像 JSONB 样例"""
        tags = self.default_profiles.get(profile_type, self.default_profiles['general'])
        tag_vector = json.dumps(tags, ensure_ascii=False, indent=2)

        logger.info(f"\n画像方案 '{profile_type}' 的 JSONB 格式:")
        logger.info(tag_vector)

    def show_statistics(self):
        """显示当前用户画像统计"""
        total = self.get_total_users_count()
        with_profile = self.get_users_with_profile_count()
        with_actions = self.get_users_with_actions_count()

        logger.info(f"\n{'='*40}")
        logger.info(f"用户画像统计:")
        logger.info(f"  总用户数: {total}")
        logger.info(f"  有画像: {with_profile} ({with_profile/total*100:.1f}%)" if total > 0 else "  有画像: 0")
        logger.info(f"  有行为: {with_actions} ({with_actions/total*100:.1f}%)" if total > 0 else "  有行为: 0")
        logger.info(f"  无画像无行为: {total - with_profile - with_actions}" if total > 0 else "  无画像无行为: 0")
        logger.info(f"{'='*40}")

    def _assess_impact(self, weight: float) -> str:
        """评估权重的影响程度"""
        if weight <= 0.1:
            return '极小'
        elif weight <= 0.2:
            return '较小'
        elif weight <= 0.3:
            return '中等'
        else:
            return '较大'

    def _column_exists(self, table: str, column: str) -> bool:
        """检查表的列是否存在"""
        try:
            result = db_helper.execute_query(
                """SELECT column_name 
                   FROM information_schema.columns 
                   WHERE table_name = %s AND column_name = %s""",
                (table, column),
                fetch_all=False
            )
            return result is not None
        except:
            return False


def main():
    """主函数 - 独立运行"""

    import argparse

    parser = argparse.ArgumentParser(description='为用户初始化默认画像标签（user_profiles 表）')
    parser.add_argument('--profile-type', type=str, default='general',
                       choices=['general', 'neutral', 'minimal'],
                       help='画像方案类型（默认: general）')
    parser.add_argument('--max-users', type=int, default=None,
                       help='最多处理用户数')
    parser.add_argument('--min-hours', type=int, default=24,
                       help='最小注册小时数（默认: 24）')
    parser.add_argument('--all-users', action='store_true',
                       help='处理所有无画像用户（不仅无行为用户）')
    parser.add_argument('--preview', action='store_true',
                       help='预览默认标签方案及数据库中存在状态')
    parser.add_argument('--show-json', action='store_true',
                       help='显示画像 JSONB 格式样例')
    parser.add_argument('--stats', action='store_true',
                       help='显示用户画像统计信息')
    parser.add_argument('--check-tags', action='store_true',
                       help='仅检查并创建所需标签，不初始化画像')
    parser.add_argument('--dry-run', action='store_true',
                       help='模拟运行，不实际保存')

    args = parser.parse_args()

    # 初始化 ContentRecommender
    try:
        recommender = ContentRecommender()
    except Exception as e:
        logger.warning(f"ContentRecommender 初始化失败: {e}")
        recommender = None

    # 创建初始化器
    initializer = DefaultProfileInitializer(recommender)

    # 显示统计信息
    if args.stats:
        initializer.show_statistics()
        return

    # 显示 JSON 格式
    if args.show_json:
        initializer.show_profile_sample(args.profile_type)
        return

    # 预览模式
    if args.preview:
        print(f"\n默认标签方案 '{args.profile_type}' 预览:")
        print("-" * 60)
        print(f"{'标签名':<10} {'权重':<8} {'影响':<8} {'数据库':<10} {'标签ID'}")
        print("-" * 60)

        tags = initializer.preview_default_tags(args.profile_type)
        for tag in tags:
            status = "✓ 存在" if tag['exists_in_db'] else "✗ 缺失"
            tag_id = tag['tag_id'] or '-'
            print(f"{tag['tag_name']:<10} {tag['weight']:<8.2f} {tag['impact']:<8} {status:<10} {tag_id}")

        print("-" * 60)

        # 统计缺失的标签
        missing = [t for t in tags if not t['exists_in_db']]
        if missing:
            print(f"\n⚠ 警告: {len(missing)} 个标签不存在于数据库中:")
            for tag in missing:
                print(f"  - {tag['tag_name']}")
            print(f"\n运行脚本时会自动创建这些标签")
        else:
            print(f"\n✓ 所有标签都已存在于数据库中")

        print(f"\nJSONB 格式样例:")
        tags_dict = initializer.default_profiles[args.profile_type]
        print(json.dumps(tags_dict, ensure_ascii=False, indent=2))

        return

    # 仅检查并创建标签
    if args.check_tags:
        logger.info("=== 检查并创建所需标签 ===")
        tag_map = initializer.ensure_tags_exist(args.profile_type)
        logger.info(f"完成！共 {len(tag_map)} 个标签已就绪")
        return

    # 干运行模式
    if args.dry_run:
        logger.info("=== 干运行模式 ===")

        # 显示统计
        initializer.show_statistics()

        # 显示要创建的标签
        logger.info("\n检查所需标签:")
        tags = initializer.preview_default_tags(args.profile_type)
        missing_tags = [t for t in tags if not t['exists_in_db']]
        if missing_tags:
            for tag in missing_tags:
                logger.info(f"  将创建标签: {tag['tag_name']}")
        else:
            logger.info("  所有标签已存在")

        # 查询用户数
        only_no_behavior = not args.all_users
        if only_no_behavior:
            users = initializer.get_users_without_behavior(
                limit=args.max_users,
                min_register_hours=args.min_hours
            )
        else:
            users = initializer.get_users_without_profile(
                limit=args.max_users,
                min_register_hours=args.min_hours
            )

        logger.info(f"\n将处理 {len(users)} 个用户")

        if users:
            logger.info(f"用户列表（前20个）:")
            for user in users[:20]:
                logger.info(f"  ID: {user['id']}, 用户名: {user.get('username', 'N/A')}")
            if len(users) > 20:
                logger.info(f"  ... 还有 {len(users) - 20} 个用户")

        return

    # 实际运行
    result = initializer.batch_init_profiles(
        profile_type=args.profile_type,
        max_users=args.max_users,
        min_register_hours=args.min_hours,
        only_no_behavior=not args.all_users
    )

    print(f"\n执行结果:")
    print(f"  标签: 已存在 {result['tags_existing']}, 新创建 {result['tags_created']}")
    print(f"  总用户数: {result['total_users']}")
    print(f"  无画像用户: {result['no_profile_users']}")
    print(f"  成功初始化: {result['initialized']}")
    print(f"  跳过(已有画像): {result['already_has_profile']}")
    print(f"  失败: {result['failed']}")


if __name__ == '__main__':
    """
    使用方式：
    
    1. 查看统计信息：
       python init_default_profiles.py --stats
    
    2. 预览默认标签方案及 JSON 格式：
       python init_default_profiles.py --preview
       python init_default_profiles.py --preview --profile-type minimal
    
    3. 查看 JSONB 格式样例：
       python init_default_profiles.py --show-json
    
    4. 仅检查并创建所需标签：
       python init_default_profiles.py --check-tags
    
    5. 干运行（查看有多少用户需要处理）：
       python init_default_profiles.py --dry-run
    
    6. 正式运行（处理所有无行为且无画像的用户）：
       python init_default_profiles.py
       
    7. 只处理前50个用户，注册超过48小时：
       python init_default_profiles.py --max-users 50 --min-hours 48
    
    8. 处理所有无画像用户（包括有行为但无画像的）：
       python init_default_profiles.py --all-users
    
    9. 使用最小影响方案：
       python init_default_profiles.py --profile-type minimal
    """
    main()