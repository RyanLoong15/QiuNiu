# -*- coding: utf-8 -*-
"""Bank KB - Global configuration"""

import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# ─── MySQL ──────────────────────────────────────────────────────
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "NewPassword123!")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "qiuniu_db")

# ─── SiliconFlow LLM ────────────────────────────────────────────
SILICONFLOW_API_KEY = os.getenv(
    "SILICONFLOW_API_KEY",
    "sk-lsxvyhxtokkhuatgilwkgmmopjrndvewufogywslwtaxfimo"
)
SILICONFLOW_API_URL = "https://api.siliconflow.cn/v1"

# LLM Fallback 链配置（模型级别 fallback）
# 主模型：DeepSeek-V3（性价比高，推理能力强）
# 备用模型：Qwen2.5-72B-Instruct（稳定可靠）
DEEPSEEK_MODEL = "deepseek-ai/DeepSeek-V3"
QWEN_MODEL = "Qwen/Qwen2.5-72B-Instruct"
LLM_MODEL = DEEPSEEK_MODEL  # 默认使用 DeepSeek
LLM_FALLBACK_CHAIN = [DEEPSEEK_MODEL, QWEN_MODEL]  # fallback 顺序

LLM_TIMEOUT = 60  # seconds（增大到 60s，避免生成长回答时超时）
LLM_MAX_TOKENS = 8192  # 增大到 8192，避免长回答被截断
LLM_MAX_RETRIES = 1  # 每个 provider 最大重试次数
LLM_RETRY_DELAY = 1  # 重试间隔秒数
LLM_RATE_LIMIT_DELAY = 3  # 429 限流等待秒数
LLM_FALLBACK_REPLY = "抱歉，当前服务响应较慢，请稍后再试或联系相关负责人。"  # 全链 fallback 回复

# ─── Embedding Model ─────────────────────────────────────────────
# SiliconFlow API (推荐，但如果账户余额不足会降级到本地模型)
# 模型选项:
#   BAAI/bge-large-zh-v1.5  (1024 维, 中文优化, 需要 API)
#   paraphrase-multilingual-MiniLM-L12-v2  (384 维, 多语言, 本地缓存)
SILICONFLOW_EMBEDDING_API = "https://api.siliconflow.cn/v1"
SILICONFLOW_EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-large-zh-v1.5")
EMBEDDING_DIM = 1024  # bge-large-zh-v1.5 维度 (如果降级到本地模型会自动调整)

# 本地模型 (当 API 不可用时使用)
# paraphrase-multilingual-MiniLM-L12-v2: 384维, 已缓存, 多语言
LOCAL_EMBEDDING_MODEL = os.getenv("LOCAL_EMBEDDING_MODEL", "paraphrase-multilingual-MiniLM-L12-v2")
LOCAL_EMBEDDING_DIM = 384  # MiniLM 维度

# 强制使用本地模型 (设为 true 跳过 API)
# 当前 SiliconFlow API 不可用，强制使用本地 384 维模型
FORCE_LOCAL_EMBEDDING = True

# ─── Milvus / Zilliz Cloud ────────────────────────────────────────
# 方式1: Zilliz Cloud (托管版，推荐)
#    ZILLIZ_URI 格式: https://in-xxxxxxxx.gcp-us-west1.cloud.zilliz.com
#    ZILLIZ_TOKEN: 你的 API Key
ZILLIZ_URI = os.getenv("ZILLIZ_URI", "")
ZILLIZ_TOKEN = os.getenv("ZILLIZ_TOKEN", "")

# 方式2: 自建 Milvus (当 ZILLIZ_URI 为空时使用)
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))

# Collection 配置
MILVUS_COLLECTION = os.getenv("MILVUS_COLLECTION", "qiuniu_knowledge_base")

# 索引配置
MILVUS_INDEX_TYPE = "HNSW"
MILVUS_METRIC_TYPE = "COSINE"

# ─── Retrieval Mode ──────────────────────────────────────────────
# 可选: "vector" (纯向量), "bm25" (纯关键词), "hybrid" (混合, 推荐)
RETRIEVAL_MODE = os.getenv("RETRIEVAL_MODE", "hybrid")

# ─── Hybrid Retrieval ─────────────────────────────────────────────
VECTOR_TOP_K = 15      # Milvus 向量检索召回数
BM25_TOP_K = 15        # BM25 召回数
RERANK_TOP_K = 5       # RRF 融合后返回数
RRF_K = 60             # RRF 融合参数

# 相似度阈值
SIMILARITY_THRESHOLD = 0.25

# 混合检索权重
VECTOR_WEIGHT = 0.5
BM25_WEIGHT = 0.5

# ─── 基础参数 ─────────────────────────────────────────────────────
TOP_K = 5
CHUNK_SIZE = 2000

# ─── 调试 ─────────────────────────────────────────────────────────
DEBUG = os.getenv("DEBUG", "false").lower() in ("true", "1", "yes")

# ─── 文档上传存储目录 ───────────────────────────────────────────
# 文档导入上传文件存储目录（可通过环境变量 UPLOAD_DIR 覆盖）
# 默认：BASE_DIR/uploads/documents/
UPLOAD_DIR = os.getenv("UPLOAD_DIR", os.path.join(BASE_DIR, 'uploads', 'documents'))

# ─── 员工门户配置 ─────────────────────────────────────────────
# JWT 认证
JWT_SECRET = os.getenv("EMPLOYEE_JWT_SECRET", "change-me-in-production")
JWT_ALGORITHM = "HS256"
JWT_EXPIRY_HOURS = 8

# 员工门户静态根目录（用于拼接头像保存路径）
WEBAPP_ROOT = os.getenv("WEBAPP_ROOT", r"D:\qiuniu20260506\qiuniu-src\src\main\webapp")

# 头像上传限制
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif"}
MAX_FILE_SIZE = 5 * 1024 * 1024  # 5MB

# 登录速率限制（内存存储，单进程有效）
_LOGIN_ATTEMPTS = {}
MAX_ATTEMPTS = 5
WINDOW_SECONDS = 300

# JWT 注销黑名单（内存存储，单进程有效）
_TOKEN_BLACKLIST = {}
