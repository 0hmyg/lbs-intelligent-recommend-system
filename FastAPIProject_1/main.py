"""
基于位置与智能推荐的生活信息分享平台
Python 智能服务 - 主入口
"""
from dotenv import load_dotenv
load_dotenv() 
import json
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Dict
import uvicorn

from config import SERVICE_HOST, SERVICE_PORT, DEBUG
from modules.tag_extractor import TagExtractor
from modules.recommender import ContentRecommender
from modules.sensitive_filter import SensitiveFilter
from modules.profile_updater import ProfileUpdater
from utils.db_helper import db_helper

# 创建 FastAPI 应用
app = FastAPI(
    title="生活信息分享平台智能服务",
    description="提供文本标签提取、内容推荐、敏感词过滤、用户画像管理等功能",
    version="1.0.0"
)

# 配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 初始化模块
tag_extractor = TagExtractor()
recommender = ContentRecommender()
sensitive_filter = SensitiveFilter()
profile_updater = ProfileUpdater(recommender)


# ==================== 请求/响应模型 ====================

class TextRequest(BaseModel):
    text: str
    top_k: Optional[int] = 5


class TagResponse(BaseModel):
    tag: str
    weight: float


class RecommendRequest(BaseModel):
    user_id: int
    location_lat: Optional[float] = None
    location_lng: Optional[float] = None
    limit: Optional[int] = 20


class RecommendResponse(BaseModel):
    post_id: int
    score: float
    reason: str


class FilterRequest(BaseModel):
    text: str


class FilterResponse(BaseModel):
    is_valid: bool
    filtered_text: str
    hit_words: List[str]


class UserProfileUpdateRequest(BaseModel):
    user_id: int
    action_type: str  # view, like, comment, share
    post_id: int


# ==================== 标签打标相关模型 ====================

class TaggingRequest(BaseModel):
    """标签生成请求"""
    post_id: int = Field(..., description="帖子ID")
    title: str = Field(..., min_length=1, description="帖子标题")
    content: str = Field(..., min_length=1, description="帖子内容")
    category: str = Field("life", description="帖子分类")
    top_k: Optional[int] = Field(5, ge=1, le=20, description="返回标签数量")
    save_to_db: Optional[bool] = Field(True, description="是否自动保存到数据库")


class BatchTaggingRequest(BaseModel):
    """批量标签生成请求"""
    post_ids: List[int] = Field(..., min_items=1, max_items=100, description="帖子ID列表")
    top_k: int = Field(5, ge=1, le=20, description="返回标签数量")


class TagItem(BaseModel):
    """标签项"""
    tag_name: str
    weight: float


class TaggingResponse(BaseModel):
    """标签生成响应"""
    post_id: int
    tags: List[TagItem]
    source: str  # "ai" | "jieba" | "mixed"
    saved: bool


class BatchResult(BaseModel):
    """批量处理单个结果"""
    post_id: int
    status: str
    message: Optional[str] = None
    tags: Optional[List[TagItem]] = None
    count: Optional[int] = None


class BatchTaggingResponse(BaseModel):
    """批量标签生成响应"""
    status: str
    total: int
    results: List[BatchResult]


# ==================== 用户画像模型 ====================

class ProfileCreateResponse(BaseModel):
    """画像创建响应"""
    success: bool
    message: str
    profile_type: str
    tag_count: int
    tags: List[Dict] = []


class ProfileStatsResponse(BaseModel):
    """画像统计响应"""
    total_users: int
    users_with_profile: int
    users_with_actions: int
    users_without_profile: int
    coverage: float


# ==================== 基础接口 ====================

@app.get("/")
def root():
    return {"status": "running", "service": "生活信息分享平台智能服务"}


@app.get("/health")
def health():
    """健康检查"""
    try:
        result = db_helper.execute_query("SELECT 1 as test", fetch_all=False)
        db_status = "connected" if result and result['test'] == 1 else "error"
    except Exception as e:
        db_status = f"error: {e}"

    return {
        "status": "healthy",
        "database": db_status,
        "modules": {
            "tag_extractor": "ready",
            "recommender": "ready",
            "sensitive_filter": "ready",
            "profile_updater": "ready"
        }
    }


# ==================== 推荐相关接口 ====================

@app.post("/api/recommend", response_model=List[RecommendResponse])
async def get_recommendations(request: RecommendRequest):
    """获取个性化推荐内容"""
    try:
        recommendations = recommender.recommend(
            user_id=request.user_id,
            location_lat=request.location_lat,
            location_lng=request.location_lng,
            limit=request.limit
        )
        return recommendations
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/user_profile/{user_id}")
async def get_user_profile(user_id: int):
    """获取用户画像详情"""
    try:
        profile = recommender.get_user_profile(user_id)
        return {
            "status": "success",
            "user_id": user_id,
            "profile": profile,
            "tag_count": len(profile)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/update_profile")
async def update_user_profile(request: UserProfileUpdateRequest):
    """更新用户画像（用户行为触发）"""
    try:
        recommender.update_profile(
            user_id=request.user_id,
            action_type=request.action_type,
            post_id=request.post_id
        )
        return {"status": "success", "message": "用户画像已更新"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 用户画像管理接口 ====================

@app.post("/api/profile/create/{user_id}", response_model=ProfileCreateResponse)
async def create_user_profile(
    user_id: int,
    profile_type: str = Query("general", description="画像方案: general/neutral/minimal")
):
    """
    为新注册用户创建默认画像（注册时调用）

    画像方案：
    - general: 生活0.2/科技0.15/美食0.15/音乐0.1/电影0.1/读书0.1/旅游0.1/健康0.1
    - neutral: 综合0.3/资讯0.2/生活0.2/科普0.15/娱乐0.15
    - minimal: 综合0.1/通用0.1
    """
    try:
        result = profile_updater.create_default_profile(user_id, profile_type)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/profile/create/batch")
async def batch_create_profiles(
    profile_type: str = Query("general"),
    max_users: Optional[int] = Query(None, description="最多处理用户数"),
    min_register_hours: int = Query(0, description="最小注册小时数")
):
    """批量为无画像用户创建默认画像"""
    try:
        result = profile_updater.batch_create_default_profiles(
            profile_type=profile_type,
            max_users=max_users,
            min_register_hours=min_register_hours
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/profile/{user_id}")
async def get_user_profile_detail(user_id: int):
    """获取用户画像详情"""
    try:
        profile = profile_updater.get_profile(user_id)
        if not profile:
            return {
                "success": False,
                "message": f"用户 {user_id} 画像不存在",
                "profile": None
            }

        tag_vector = profile.get('tag_vector', {})
        if isinstance(tag_vector, str):
            tag_vector = json.loads(tag_vector)

        return {
            "success": True,
            "profile": {
                "user_id": profile['user_id'],
                "tag_vector": tag_vector,
                "tag_count": len(tag_vector),
                "updated_at": str(profile['updated_at'])
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/profile/stats", response_model=ProfileStatsResponse)
async def get_profile_statistics():
    """获取用户画像统计信息"""
    try:
        stats = profile_updater.get_profile_stats()
        return stats
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/profile/update/{user_id}")
async def update_single_profile(
    user_id: int,
    view_limit: int = Query(50, description="取最近N条浏览记录"),
    like_limit: int = Query(20, description="取最近N条点赞记录"),
    comment_limit: int = Query(10, description="取最近N条评论记录"),
    share_limit: int = Query(5, description="取最近N条分享记录"),
    old_profile_weight: float = Query(0.3, description="老画像权重（0-1）")
):
    """
    更新指定用户画像（基于最近行为日志）

    示例：
    POST /api/profile/update/123?view_limit=50&like_limit=20&comment_limit=10&share_limit=5&old_profile_weight=0.3
    """
    try:
        result = profile_updater.update_single_user(
            user_id=user_id,
            view_limit=view_limit,
            like_limit=like_limit,
            comment_limit=comment_limit,
            share_limit=share_limit,
            old_profile_weight=old_profile_weight
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/profile/update_batch")
async def update_batch_profiles(
    min_actions: int = Query(5, description="最小行为条数阈值，低于此数跳过"),
    since_hours: int = Query(24, description="检查最近N小时内的行为"),
    view_limit: int = Query(50, description="取最近N条浏览记录"),
    like_limit: int = Query(20, description="取最近N条点赞记录"),
    comment_limit: int = Query(10, description="取最近N条评论记录"),
    share_limit: int = Query(5, description="取最近N条分享记录"),
    old_profile_weight: float = Query(0.3, description="老画像权重（0-1）")
):
    """
    批量更新用户画像

    自动查找最近{since_hours}小时内新增行为超过{min_actions}条的用户，
    基于最近N条行为日志 + 老画像，逐个更新画像

    示例（Java定时调用）：
    POST /api/profile/update_batch?min_actions=5&since_hours=1&view_limit=50&like_limit=20
    """
    try:
        result = profile_updater.update_batch_users(
            min_actions=min_actions,
            since_hours=since_hours,
            view_limit=view_limit,
            like_limit=like_limit,
            comment_limit=comment_limit,
            share_limit=share_limit,
            old_profile_weight=old_profile_weight
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 标签提取接口 ====================

@app.post("/api/extract_tags", response_model=List[TagResponse])
async def extract_tags(request: TextRequest):
    """提取文本关键词标签"""
    try:
        tags = tag_extractor.extract(request.text, top_k=request.top_k)
        return [{"tag": tag, "weight": weight} for tag, weight in tags]
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/tagging", response_model=TaggingResponse)
async def generate_post_tags(request: TaggingRequest):
    """
    为帖子自动生成标签及权重（AI + jieba 混合）

    流程：
    1. jieba TF-IDF 提取关键词
    2. AI 匹配标签库中的标签
    3. 合并去重，AI 标签优先
    4. 可选：自动保存到 post_tags 表
    """
    try:
        tags = tag_extractor.extract_for_db(
            post_id=request.post_id,
            title=request.title,
            content=request.content,
            category=request.category,
            top_k=request.top_k
        )

        has_ai = any(
            t['tag_name'] in tag_extractor.tag_names
            for t in tags
        )
        source = "mixed" if has_ai else "jieba"

        saved = False
        if request.save_to_db and tags:
            saved = tag_extractor.save_tags_to_db(
                request.post_id,
                [(t['tag_name'], t['weight']) for t in tags]
            )

        return {
            "post_id": request.post_id,
            "tags": tags,
            "source": source,
            "saved": saved
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/tagging/batch", response_model=BatchTaggingResponse)
async def batch_generate_tags(request: BatchTaggingRequest):
    """批量为帖子生成标签（用于历史数据回填）"""
    try:
        results = []
        placeholders = ','.join(['%s'] * len(request.post_ids))
        posts_query = f"SELECT id, title, content, category FROM posts WHERE id IN ({placeholders})"
        posts = db_helper.execute_query(posts_query, tuple(request.post_ids))
        posts_dict = {p['id']: p for p in posts}

        for post_id in request.post_ids:
            if post_id not in posts_dict:
                results.append({
                    "post_id": post_id,
                    "status": "error",
                    "message": "帖子不存在"
                })
                continue

            post = posts_dict[post_id]

            try:
                tags = tag_extractor.extract_for_db(
                    post_id=post_id,
                    title=post['title'],
                    content=post['content'],
                    category=post.get('category', 'life'),
                    top_k=request.top_k
                )

                saved = tag_extractor.save_tags_to_db(
                    post_id,
                    [(t['tag_name'], t['weight']) for t in tags]
                )

                results.append({
                    "post_id": post_id,
                    "status": "success" if saved else "partial",
                    "tags": tags,
                    "count": len(tags)
                })

            except Exception as e:
                results.append({
                    "post_id": post_id,
                    "status": "error",
                    "message": f"处理失败: {str(e)}"
                })

        return {
            "status": "success",
            "total": len(request.post_ids),
            "results": results
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/tagging/posts/{post_id}")
async def get_post_tags(post_id: int):
    """获取帖子的已保存标签"""
    try:
        tags = db_helper.execute_query(
            """
            SELECT pt.id, pt.weight, pt.created_at,
                   t.name as tag_name, t.type as tag_type
            FROM post_tags pt
            JOIN tags t ON pt.tag_id = t.id
            WHERE pt.post_id = %s
            ORDER BY pt.weight DESC
            """,
            (post_id,)
        )

        return {
            "status": "success",
            "post_id": post_id,
            "tags": tags,
            "count": len(tags)
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/api/tagging/posts/{post_id}")
async def delete_post_tags(post_id: int):
    """删除帖子的所有标签（重新打标前调用）"""
    try:
        db_helper.execute_update(
            "DELETE FROM post_tags WHERE post_id = %s",
            (post_id,)
        )

        return {
            "status": "success",
            "message": f"已删除帖子 {post_id} 的所有标签"
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 标签管理接口 ====================

@app.get("/api/tags")
async def get_tags():
    """获取所有标签"""
    try:
        tags = tag_extractor.get_all_tags()
        return {"status": "success", "data": tags, "count": len(tags)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/tags")
async def add_tag(name: str, tag_type: str = "content", weight: float = 1.5):
    """添加标签"""
    try:
        result = tag_extractor.add_tag(name, tag_type, weight)
        if result['success']:
            return result
        else:
            raise HTTPException(status_code=400, detail=result['message'])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 敏感词相关接口 ====================

@app.post("/api/filter", response_model=FilterResponse)
async def filter_sensitive_words(request: FilterRequest):
    """敏感词过滤"""
    try:
        result = sensitive_filter.filter(request.text)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/sensitive_words")
async def get_sensitive_words():
    """获取所有敏感词"""
    try:
        words = sensitive_filter.get_all_words()
        return {"status": "success", "data": words}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/sensitive_words")
async def add_sensitive_word(word: str, level: str = "review"):
    """添加敏感词"""
    try:
        result = sensitive_filter.add_word(word, level)
        if result['success']:
            return result
        else:
            raise HTTPException(status_code=400, detail=result['message'])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/api/sensitive_words")
async def remove_sensitive_word(word: str):
    """删除敏感词"""
    try:
        result = sensitive_filter.remove_word(word)
        if result['success']:
            return result
        else:
            raise HTTPException(status_code=400, detail=result['message'])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/sensitive_words/reload")
async def reload_sensitive_words():
    """重新加载敏感词（从数据库同步）"""
    try:
        result = sensitive_filter.reload_from_db()
        return {"status": "success", "data": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 停用词管理接口 ====================

@app.get("/api/stopwords")
async def get_stopwords():
    """获取所有停用词"""
    try:
        words = tag_extractor.get_all_stopwords()
        return {"status": "success", "data": words, "count": len(words)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/stopwords")
async def add_stopword(word: str):
    """添加停用词"""
    try:
        result = tag_extractor.add_stopword(word)
        if result['success']:
            return result
        else:
            raise HTTPException(status_code=400, detail=result['message'])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 数据重载接口 ====================

@app.post("/api/reload")
async def reload_all():
    """重新加载所有数据（停用词、标签、敏感词）"""
    try:
        tag_result = tag_extractor.reload_from_db()
        sensitive_result = sensitive_filter.reload_from_db()
        return {
            "status": "success",
            "tag_extractor": tag_result,
            "sensitive_filter": sensitive_result
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ==================== 服务启动 ====================

if __name__ == "__main__":
    print("=" * 50)
    print("🚀 启动 Python 智能服务...")
    print(f"📍 地址: http://{SERVICE_HOST}:{SERVICE_PORT}")
    print(f"📚 API 文档: http://localhost:{SERVICE_PORT}/docs")
    print("=" * 50)
    uvicorn.run(
        "main:app",
        host=SERVICE_HOST,
        port=SERVICE_PORT,
        reload=DEBUG
    )