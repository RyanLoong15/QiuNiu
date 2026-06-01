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
LLM_MODEL = "Qwen/Qwen2.5-72B-Instruct"
LLM_TIMEOUT = 60  # seconds
LLM_MAX_TOKENS = 4096

# ─── SiliconFlow Embedding ────────────────────────────────────────
# 支持的模型:
#   BAAI/bge-large-zh-v1.5  (1024 维, 推荐)
#   BAAI/bge-small-zh-v1.5  (512 维)
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-large-zh-v1.5")
EMBEDDING_DIM = 1024  # bge-large-zh-v1.5 维度
EMBEDDING_TIMEOUT = 30  # seconds
EMBEDDING_BATCH_SIZE = 20  # 每批处理数量

# ─── Milvus / Zilliz Cloud ────────────────────────────────────────
# 方式1: Zilliz Cloud (托管版，推荐)
#    ZILLIZ_URI 格式: https://in-xxxxxxxx.gcp-us-west1.cloud.zilliz.com
#    ZILLIZ_TOKEN: 你的 API Key
ZILLIZ_URI = os.getenv("ZILLIZ_URI", "")  # 留空则使用自建 Milvus
ZILLIZ_TOKEN = os.getenv("ZILLIZ_TOKEN", "")

# 方式2: 自建 Milvus (当 ZILLIZ_URI 为空时使用)
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")

# Collection 配置
COLLECTION_NAME = "kb_vectors"  # Collection 名称

# 索引配置
MILVUS_INDEX_TYPE = "HNSW"      # HNSW 图索引 (高召回)
MILVUS_METRIC_TYPE = "COSINE"   # 余弦相似度

# ─── 检索配置 ─────────────────────────────────────────────────────
# 是否启用 Milvus (False 则降级为纯 BM25)
USE_MILVUS = bool(ZILLIZ_URI and ZILLIZ_TOKEN)

# 是否使用混合检索 (False 则仅用向量或仅用 BM25)
USE_HYBRID = True

# 检索参数
TOP_K = 5                      # 最终返回数量
VECTOR_TOP_K = 15             # Milvus 向量检索召回数
BM25_TOP_K = 15               # BM25 召回数
RERANK_TOP_K = 5              # RRF 融合后返回数
RRF_K = 60                    # RRF 融合参数 (越大越依赖 BM25)

# 相似度阈值
SIMILARITY_THRESHOLD = 0.3    # 混合阈值 (低于此分数的结果被过滤)

# 文档分块
CHUNK_SIZE = 2000             # max chars per knowledge entry for prompt

# ─── 调试 ─────────────────────────────────────────────────────────
DEBUG = os.getenv("DEBUG", "false").lower() in ("true", "1", "yes")
