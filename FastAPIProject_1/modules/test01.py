"""
临时脚本：为已审核帖子批量打标签
功能：找出 posts 表中 is_audited=1 但 post_tags 表中没有关联标签的帖子，自动提取并保存标签
独立运行（除数据库连接外）
"""

import sys
import time
import logging
from typing import List, Dict, Tuple
from datetime import datetime

# 导入你的 TagExtractor 和 db_helper
from tag_extractor import TagExtractor
from utils.db_helper import db_helper

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('batch_tagging.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class BatchTagProcessor:
    """批量标签处理器"""

    def __init__(self, use_ai: bool = True, batch_size: int = 50, delay: float = 0.5):
        """
        初始化批量处理器

        Args:
            use_ai: 是否使用 AI（DeepSeek）
            batch_size: 每批处理数量
            delay: API 调用间隔（秒），避免频率限制
        """
        self.extractor = TagExtractor(use_ai=use_ai)
        self.batch_size = batch_size
        self.delay = delay
        self.stats = {
            'total': 0,
            'success': 0,
            'failed': 0,
            'no_tags': 0,  # 无法提取标签的帖子
            'skipped': 0  # 跳过的帖子（如内容为空）
        }

    def get_untagged_posts(self, limit: int = None) -> List[Dict]:
        """
        查询已审核但未打标签的帖子

        Args:
            limit: 限制查询数量，None 表示全部

        Returns:
            帖子列表
        """
        query = """
                SELECT p.id, p.title, p.content, p.category, p.created_at
                FROM posts p
                WHERE p.is_audited = 1
                  AND NOT EXISTS (SELECT 1 \
                                  FROM post_tags pt \
                                  WHERE pt.post_id = p.id)
                ORDER BY p.id \
                """

        if limit:
            query += f" LIMIT {limit}"

        try:
            results = db_helper.execute_query(query)
            logger.info(f"找到 {len(results)} 个待打标签的帖子")
            return results
        except Exception as e:
            logger.error(f"查询帖子失败: {e}")
            return []

    def process_single_post(self, post: Dict) -> Tuple[bool, List[Dict]]:
        """
        处理单个帖子

        Args:
            post: 帖子数据

        Returns:
            (是否成功, 标签列表)
        """
        post_id = post['id']
        title = post.get('title', '')
        content = post.get('content', '')
        category = post.get('category', 'life')

        # 跳过空内容
        if not title and not content:
            logger.warning(f"帖子 {post_id}: 标题和内容均为空，跳过")
            self.stats['skipped'] += 1
            return False, []

        # 提取标签
        try:
            tags = self.extractor.extract_for_db(
                post_id=post_id,
                title=title,
                content=content,
                category=category,
                top_k=5
            )

            if not tags:
                logger.warning(f"帖子 {post_id}: 未能提取到标签")
                self.stats['no_tags'] += 1
                return False, []

            # 保存到数据库
            tag_tuples = [(t['tag_name'], t['weight']) for t in tags]
            saved = self.extractor.save_tags_to_db(post_id, tag_tuples)

            if saved:
                tag_names = [t['tag_name'] for t in tags]
                logger.info(f"✓ 帖子 {post_id}: 成功添加标签 {tag_names}")
                return True, tags
            else:
                logger.error(f"✗ 帖子 {post_id}: 标签保存失败")
                self.stats['failed'] += 1
                return False, []

        except Exception as e:
            logger.error(f"✗ 帖子 {post_id}: 处理异常 - {e}")
            self.stats['failed'] += 1
            return False, []

    def process_batch(self, posts: List[Dict]) -> None:
        """
        批量处理帖子

        Args:
            posts: 帖子列表
        """
        total_in_batch = len(posts)
        logger.info(f"开始批量处理 {total_in_batch} 个帖子...")

        for i, post in enumerate(posts, 1):
            self.stats['total'] += 1

            success, tags = self.process_single_post(post)
            if success:
                self.stats['success'] += 1

            # 进度显示
            if i % 10 == 0 or i == total_in_batch:
                progress = (i / total_in_batch) * 100
                logger.info(
                    f"进度: {i}/{total_in_batch} ({progress:.1f}%) - "
                    f"成功: {self.stats['success']}, "
                    f"失败: {self.stats['failed']}, "
                    f"无标签: {self.stats['no_tags']}"
                )

            # API 调用间隔（避免频率限制）
            if self.extractor.use_ai and i < total_in_batch:
                time.sleep(self.delay)

    def run(self, max_posts: int = None, batch_size: int = None) -> Dict:
        """
        运行批量处理

        Args:
            max_posts: 最多处理帖子数
            batch_size: 每批数量（覆盖初始化时的设置）

        Returns:
            处理统计
        """
        if batch_size:
            self.batch_size = batch_size

        start_time = datetime.now()
        logger.info(f"{'=' * 60}")
        logger.info(f"开始执行批量标签任务")
        logger.info(f"AI 模式: {'启用' if self.extractor.use_ai else '禁用'}")
        logger.info(f"批次大小: {self.batch_size}")
        logger.info(f"最大处理数: {max_posts or '无限制'}")
        logger.info(f"{'=' * 60}")

        # 获取待处理帖子
        posts = self.get_untagged_posts(limit=max_posts)

        if not posts:
            logger.info("没有需要处理的帖子")
            return self.stats

        # 分批处理
        for i in range(0, len(posts), self.batch_size):
            batch = posts[i:i + self.batch_size]
            batch_num = (i // self.batch_size) + 1
            logger.info(f"\n--- 处理第 {batch_num} 批 ({len(batch)} 个帖子) ---")

            self.process_batch(batch)

        # 统计结果
        elapsed = (datetime.now() - start_time).total_seconds()
        logger.info(f"\n{'=' * 60}")
        logger.info(f"批量处理完成！")
        logger.info(f"总耗时: {elapsed:.1f} 秒")
        logger.info(f"总计: {self.stats['total']} 个帖子")
        logger.info(f"成功: {self.stats['success']} 个")
        logger.info(f"失败: {self.stats['failed']} 个")
        logger.info(f"无标签: {self.stats['no_tags']} 个")
        logger.info(f"跳过: {self.stats['skipped']} 个")
        logger.info(f"{'=' * 60}")

        return self.stats


def main():
    """主函数"""
    import argparse

    parser = argparse.ArgumentParser(description='为已审核帖子批量打标签')
    parser.add_argument('--max', type=int, default=None,
                        help='最多处理帖子数（默认：全部）')
    parser.add_argument('--batch', type=int, default=50,
                        help='每批处理数量（默认：50）')
    parser.add_argument('--delay', type=float, default=0.5,
                        help='API调用间隔秒数（默认：0.5）')
    parser.add_argument('--no-ai', action='store_true',
                        help='禁用AI，仅使用jieba')
    parser.add_argument('--dry-run', action='store_true',
                        help='模拟运行，不实际保存到数据库')

    args = parser.parse_args()

    # 干运行模式
    if args.dry_run:
        logger.info("=== 干运行模式：不实际保存标签 ===")
        # 这里需要修改 process_single_post，跳过保存步骤
        # 为简单起见，直接提示用户
        logger.warning("干运行模式暂未实现完整逻辑，请去掉 --dry-run 实际执行")
        return

    # 创建处理器并运行
    processor = BatchTagProcessor(
        use_ai=not args.no_ai,
        batch_size=args.batch,
        delay=args.delay
    )

    stats = processor.run(
        max_posts=args.max,
        batch_size=args.batch
    )

    # 输出最终统计
    print(f"\n最终统计：")
    print(f"  总帖子数: {stats['total']}")
    print(f"  成功: {stats['success']}")
    print(f"  失败: {stats['failed']}")
    print(f"  无标签: {stats['no_tags']}")
    print(f"  跳过: {stats['skipped']}")

    return stats


if __name__ == '__main__':
    """
    使用方式：

    1. 处理所有未打标签的帖子（默认）：
       python batch_tag_posts.py

    2. 只处理前 100 个帖子：
       python batch_tag_posts.py --max 100

    3. 调整批次大小和延迟：
       python batch_tag_posts.py --batch 30 --delay 1.0

    4. 仅使用 jieba，不用 AI：
       python batch_tag_posts.py --no-ai

    5. 快速测试（处理 10 个）：
       python batch_tag_posts.py --max 10 --batch 5
    """
    main()