"""
PostgreSQL 数据库连接辅助类
"""

import psycopg2
from psycopg2.extras import RealDictCursor
from contextlib import contextmanager
import logging
from config import DB_CONFIG

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class DBHelper:
    """数据库连接助手"""

    def __init__(self):
        self.config = DB_CONFIG

    @contextmanager
    def get_connection(self):
        """获取数据库连接"""
        conn = psycopg2.connect(**self.config)
        try:
            yield conn
            conn.commit()
        except Exception as e:
            conn.rollback()
            logger.error(f"数据库操作失败: {e}")
            raise
        finally:
            conn.close()

    @contextmanager
    def get_cursor(self, cursor_factory=None):
        """获取游标"""
        with self.get_connection() as conn:
            with conn.cursor(cursor_factory=cursor_factory) as cur:
                yield cur

    def execute_query(self, sql, params=None, fetch_all=True):
        """执行查询"""
        with self.get_cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(sql, params)
            if fetch_all:
                return cur.fetchall()
            return cur.fetchone()

    def execute_update(self, sql, params=None):
        """执行更新"""
        with self.get_cursor() as cur:
            cur.execute(sql, params)
            return cur.rowcount

    def execute_batch(self, sql, params_list):
        """批量执行"""
        with self.get_cursor() as cur:
            cur.executemany(sql, params_list)
            return cur.rowcount


# 全局实例
db_helper = DBHelper()