# lbs-intelligent-recommend-system
基于DeepSeek API与LBS的智能内容推荐系统(Python微服务+协同过滤)
# 基于位置与智能推荐的社区生活信息分享平台

> **LBS + DeepSeek AI 混合推荐 | Java + Spring Boot + Vue + Python 微服务**

一个面向社区居民的生活信息分享平台。整合 **PostGIS 地理空间检索**与 **DeepSeek 大模型智能标签推荐**，解决本地信息杂乱、查找低效的问题。支持基于位置的附近内容发现、个性化推荐、敏感词自动审核与后台数据看板。

---

## 🧱 技术架构

| 层级 | 技术栈 | 说明 |
|:---|:---|:---|
| **后端** | Java 8 + Spring Boot + MyBatis + PostgreSQL 16 / PostGIS 3.4 | RESTful API，空间数据存储与查询 |
| **AI 服务** | Python 3.13 + DeepSeek API + Jieba + TF-IDF | 独立微服务，负责标签提取与智能推荐 |
| **前端** | Vue 3 + Element Plus | SPA 交互，LBS 地图展示 |
| **数据库** | PostgreSQL + PostGIS 扩展 | 空间索引（GIST），支持附近检索 |
| **安全** | AC 自动机敏感词过滤 + JWT 鉴权 | 双层内容审核 |
| **部署** | Maven（后端），npm（前端），pip（AI 服务） | 模块化启动 |

---

## ✨ 核心功能

- **📍 附近内容检索**  
  基于 PostGIS 的 `ST_DistanceSphere` 实现 500m-2km 半径内精准检索，利用 GIST 索引将查询耗时从秒级压至毫秒级。

- **🧠 AI 智能标签推荐**  
  独立 Python 微服务调用 DeepSeek 大模型，结合 Jieba 分词与 TF-IDF 实现标签提取与协同过滤推荐。点击率提升约 25%，覆盖 80% 活跃用户。

- **🛡️ 敏感词过滤与审核**  
  AC 自动机 + 人工审核双重机制。自动拦截违禁内容，命中敏感词可回溯。

- **📊 运营数据看板**  
  后台输出用户活跃度、热门区域统计等报表，辅助运营决策。

- **👤 用户画像与行为追踪**  
  记录浏览、点赞、评论等行为，生成标签向量，驱动个性化推荐。

---

## 🗺️ 数据库设计

完整建表脚本与模拟数据见 `database/` 目录。核心表包括：

| 表名 | 说明 | 亮点 |
|:---|:---|:---|
| `users` | 用户表 | 含 `location_geom` 空间字段与 GIST 索引 |
| `posts` | 帖子表 | 支持审核状态、敏感词命中记录、软删除 |
| `tags` | 标签库 | 动态扩充，AI 自动打标入库 |
| `post_tags` | 帖子-标签关联 | 存储 TF-IDF 权重 |
| `user_actions` | 用户行为日志 | 含浏览时长、元数据 JSON |
| `user_profiles` | 用户画像 | JSON 向量存储偏好 |
| `sensitive_words` | 敏感词库 | 支持 block / review 分级 |
| `reports` | 举报记录 | 含处理人与结果追踪 |

> 建库时需先执行 `CREATE EXTENSION IF NOT EXISTS postgis;`

---

## 🚀 快速开始

### 环境要求

- Java 8+
- Python 3.13+
- PostgreSQL 16+（已安装 PostGIS 3.4）
- Node.js 18+

### 1. 数据库初始化

```bash
# 创建数据库并启用 PostGIS
psql -U postgres -c "CREATE DATABASE life_share_platform;"
psql -U postgres -d life_share_platform -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# 导入建表与模拟数据
psql -U postgres -d life_share_platform -f database/schema.sql
psql -U postgres -d life_share_platform -f database/seed.sql
