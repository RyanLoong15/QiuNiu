# -*- coding: utf-8 -*-
"""
Embedding Client - 文本向量生成客户端

[WARN] 当前状态：SiliconFlow API 不可用（余额不足）
[WARN] 本地 sentence-transformers 加载时进程被 SIGKILL 杀死（内存不足）

方案：
1. 【推荐】内网大模型就绪后，修改 _encode_via_internal_api() 方法
2. 临时方案：使用简单的 TF-IDF 向量（不依赖外部服务）

接口规范：
    embedder.encode(texts: List[str]) -> List[List[float]]
    embedder.encode_single(text: str) -> List[float]
"""

import os
import sys
import json
import numpy as np
from typing import List, Union, Optional

from config import (
    EMBEDDING_DIM,
    LOCAL_EMBEDDING_DIM,
    FORCE_LOCAL_EMBEDDING,
    SILICONFLOW_API_KEY,
    SILICONFLOW_EMBEDDING_API,
    SILICONFLOW_EMBEDDING_MODEL,
)


class EmbeddingClient:
    """
    文本向量生成客户端

    优先级：
    1. 内网大模型 API（等用户配置 INTERNAL_EMBEDDING_API_URL）
    2. SiliconFlow API（余额不足时会自动跳过）
    3. 本地 sentence-transformers（内存不足时会自动跳过）
    4. 简单 TF-IDF 向量（兜底方案，不依赖任何外部服务）
    """

    def __init__(self):
        self.dim = EMBEDDING_DIM
        self._method = None  # 延迟初始化，避免启动时触发 import
        self._internal_api_url = os.getenv("INTERNAL_EMBEDDING_API_URL", "")
        self._internal_api_key = os.getenv("INTERNAL_EMBEDDING_API_KEY", "")
        self._local_model = None
        self._method_initialized = False

    def _ensure_initialized(self):
        """延迟初始化，避免启动时加载大模型"""
        if self._method_initialized:
            return
        self._init_method()
        self._method_initialized = True

    def _init_method(self):
        """按优先级初始化 Embedding 方法"""
        # 1. 内网大模型 API
        if self._internal_api_url:
            self._method = "internal_api"
            print(f"[Embedding] Using internal API: {self._internal_api_url}")
            return

        # 2. SiliconFlow API
        if not FORCE_LOCAL_EMBEDDING:
            self._method = "siliconflow_api"
            print(f"[Embedding] Using SiliconFlow API: {SILICONFLOW_EMBEDDING_MODEL}")
            return

        # 3. 本地模型
        if self._try_load_local_model():
            self._method = "local_model"
            return

        # 4. 兜底：TF-IDF 向量
        self._method = "tfidf_fallback"
        print("[Embedding] Using TF-IDF fallback (no external service needed)")
        self.dim = LOCAL_EMBEDDING_DIM  # TF-IDF 使用本地维度

    def _is_model_cached(self, model_name: str) -> bool:
        """检查 sentence-transformers 模型是否已缓存到本地"""
        # 缓存位置1: ~/.cache/torch/sentence_transformers/<model_name>/ 
        cache_dir1 = os.path.join(os.path.expanduser("~"), ".cache", "torch", "sentence_transformers")
        model_dir_name = model_name.replace("/", ",")
        if os.path.exists(os.path.join(cache_dir1, model_dir_name)):
            return True
        # 缓存位置2: ~/.cache/huggingface/hub/models--<model_name>/
        cache_dir2 = os.path.join(os.path.expanduser("~"), ".cache", "huggingface", "hub")
        if os.path.exists(cache_dir2):
            import glob
            pattern = os.path.join(cache_dir2, f"models--{model_name.replace('/', '--')}*")
            if glob.glob(pattern):
                return True
        return False

    def _try_load_local_model(self) -> bool:
        try:
            from sentence_transformers import SentenceTransformer
            model_name = os.getenv("LOCAL_EMBEDDING_MODEL", "paraphrase-multilingual-MiniLM-L12-v2")
            # 先检查模型是否已缓存，未缓存则直接跳过（避免网络下载挂起）
            if not self._is_model_cached(model_name):
                print(f"[Embedding] Model {model_name} not cached locally, skipping (network blocked?)")
                return False
            print(f"[Embedding] Loading local model: {model_name}")
            self._local_model = SentenceTransformer(model_name)
            self.dim = self._local_model.get_sentence_embedding_dimension()
            print(f"[Embedding] Local model loaded, dim={self.dim}")
            return True
        except Exception as e:
            print(f"[Embedding] Local model unavailable: {e}")
            return False

    # ── 公共接口 ─────────────────────────────────────────────────────

    def encode(self, texts: Union[str, List[str]]) -> np.ndarray:
        """
        生成文本向量

        Args:
            texts: 单个文本或文本列表

        Returns:
            numpy array, shape=(n, dim)
        """
        self._ensure_initialized()
        if isinstance(texts, str):
            texts = [texts]

        if self._method == "internal_api":
            return self._encode_via_internal_api(texts)
        elif self._method == "siliconflow_api":
            return self._encode_via_siliconflow(texts)
        elif self._method == "local_model":
            return self._encode_local(texts)
        else:
            return self._encode_tfidf(texts)

    def encode_single(self, text: str) -> List[float]:
        """生成单个文本的向量"""
        self._ensure_initialized()
        vec = self.encode(text)
        if vec.shape[0] == 1:
            return vec[0].tolist()
        return vec.tolist()

    def get_status(self) -> dict:
        """获取状态（延迟初始化）"""
        self._ensure_initialized()
        return {
            "method": self._method,
            "dimension": self.dim,
            "internal_api_configured": bool(self._internal_api_url),
        }

    # ── 内网大模型 API ─────────────────────────────────────────────

    def _encode_via_internal_api(self, texts: List[str]) -> np.ndarray:
        """
        调用内网大模型 API 生成向量

        [WARN] 需要用户配置环境变量：
            INTERNAL_EMBEDDING_API_URL=http://内网地址/v1/embeddings
            INTERNAL_EMBEDDING_API_KEY=your_key  (可选)

        API 格式（OpenAI 兼容）：
            POST {INTERNAL_EMBEDDING_API_URL}
            Headers: {"Authorization": "Bearer {key}"}
            Body: {"model": "模型名", "input": ["文本1", "文本2"]}
            Response: {"data": [{"embedding": [0.1, ...]}, ...]}
        """
        import requests

        headers = {"Content-Type": "application/json"}
        if self._internal_api_key:
            headers["Authorization"] = f"Bearer {self._internal_api_key}"

        # 从环境变量读取模型名，默认用 bge-large-zh-v1.5
        model = os.getenv("INTERNAL_EMBEDDING_MODEL", "bge-large-zh-v1.5")

        payload = {
            "model": model,
            "input": texts,
        }

        try:
            resp = requests.post(
                self._internal_api_url,
                headers=headers,
                json=payload,
                timeout=60,
            )
            resp.raise_for_status()
            data = resp.json()
            embeddings = [item["embedding"] for item in data["data"]]
            return np.array(embeddings)
        except Exception as e:
            print(f"[Embedding] Internal API failed: {e}, falling back to TF-IDF")
            self._method = "tfidf_fallback"
            return self._encode_tfidf(texts)

    # ── SiliconFlow API ─────────────────────────────────────────────

    def _encode_via_siliconflow(self, texts: List[str]) -> np.ndarray:
        """调用 SiliconFlow API 生成向量（余额不足时会失败）"""
        import requests

        headers = {
            "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": SILICONFLOW_EMBEDDING_MODEL,
            "input": texts,
        }

        try:
            resp = requests.post(
                SILICONFLOW_EMBEDDING_API,
                headers=headers,
                json=payload,
                timeout=60,
            )
            if resp.status_code == 403:
                raise Exception("SiliconFlow 余额不足")
            resp.raise_for_status()
            data = resp.json()
            embeddings = [item["embedding"] for item in data["data"]]
            return np.array(embeddings)
        except Exception as e:
            print(f"[Embedding] SiliconFlow API failed: {e}")
            print("[Embedding] Falling back to TF-IDF")
            self._method = "tfidf_fallback"
            return self._encode_tfidf(texts)

    # ── 本地模型 ─────────────────────────────────────────────────────

    def _encode_local(self, texts: List[str]) -> np.ndarray:
        if self._local_model is None:
            raise RuntimeError("Local model not loaded")
        embeddings = self._local_model.encode(
            texts,
            show_progress_bar=False,
            convert_to_numpy=True,
        )
        if len(embeddings.shape) == 1:
            return embeddings.reshape(1, -1)
        return embeddings

    # ── TF-IDF 兜底方案 ─────────────────────────────────────────

    def _encode_tfidf(self, texts: List[str]) -> np.ndarray:
        """
        TF-IDF 向量（兜底方案，不依赖任何外部服务）

        将文本转换为 TF-IDF 向量，维度 = 词表大小（截断到 LOCAL_EMBEDDING_DIM）

        [WARN] 这不是真正的语义向量，只是关键词匹配
        [WARN] 仅用于临时测试，正式环境请使用真正的 Embedding 服务
        """
        import jieba
        import math

        # 构建词表（懒加载）
        if not hasattr(self, '_tfidf_vocab'):
            self._tfidf_vocab = {}
            self._tfidf_idf = {}
            self._tfidf_dim = LOCAL_EMBEDDING_DIM

        # 分词
        all_tokens = []
        for text in texts:
            tokens = list(jieba.cut_for_search(text))
            all_tokens.append(tokens)

        # 构建向量（简化版：词频向量，归一化）
        results = []
        for tokens in all_tokens:
            vec = np.zeros(self._tfidf_dim)
            token_counts = {}
            for t in tokens:
                token_counts[t] = token_counts.get(t, 0) + 1

            # 填充向量（用 hash 映射到固定维度）
            for t, cnt in token_counts.items():
                idx = hash(t) % self._tfidf_dim
                vec[idx] += cnt

            # L2 归一化
            norm = np.linalg.norm(vec)
            if norm > 0:
                vec = vec / norm

            results.append(vec)

        return np.array(results)


# ── 全局单例 ─────────────────────────────────────────────────────────
_embedding_client = None


def get_embedding_client() -> EmbeddingClient:
    global _embedding_client
    if _embedding_client is None:
        _embedding_client = EmbeddingClient()
    return _embedding_client


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("="*60)
    print("Testing EmbeddingClient...")
    print("="*60)

    client = get_embedding_client()
    status = client.get_status()
    print(f"Status: {status}")

    texts = [
        "银行活期存款利率是多少？",
        "定期存款和活期有什么区别？",
        "如何计算贷款利息？",
    ]

    print(f"\n[Test] Encoding {len(texts)} texts...")
    start = time.time()
    vecs = client.encode(texts)
    elapsed = time.time() - start
    print(f"  [OK] Dimension: {vecs.shape}")
    print(f"    Time: {elapsed:.2f}s")

    # 计算相似度
    from numpy.linalg import norm
    v1 = np.array(vecs[0])
    v2 = np.array(vecs[1])
    cos_sim = np.dot(v1, v2) / (norm(v1) * norm(v2))
    print(f"\n[Test] Similarity between text 1 & 2: {cos_sim:.4f}")

    print(f"\n[Test] Single encode...")
    vec = client.encode_single("测试查询")
    print(f"  [OK] Vector length: {len(vec)}")

    print("\n" + "="*60)
