"""
配置文件
"""

# PostgreSQL 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'lsp_db',
    'user': 'postgres',
    'password': '123456'
}

# 服务配置
SERVICE_HOST = '0.0.0.0'
SERVICE_PORT = 5000
DEBUG = True