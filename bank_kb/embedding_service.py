# -*- coding: utf-8 -*-
"""
Embedding Service - 生成文本向量
✅ 优先使用 SiliconFlow API (bge-large-zh-v1.5)
✅ 自动降级到本地 sentence-transformers (paraphrase-multilingual-MiniLM-L12-v2)
"""

import time
import os
import numpy as np
from typing import List, Union

from config import (
    SILICONFLOW_EMBEDDING_API,
    SILICONFLOW_EMBEDDING_MODEL,
    SILICONFLOW_API_KEY,
    EMBEDDING_DIM,
    LOCAL_EMBEDDING_MODEL,
    LOCAL_EMBEDDING_DIM,
    FORCE_LOCAL_EMBEDDING,
)


class EmbeddingService:
    """
    Embedding 服务

    ✅ 优先 SiliconFlow API (bge-large-zh-v1.5, 1024维, 中文优化)
    ✅ 自动降级: API 不可用时使用本地 MiniLM (384维, 多语言)
    
    注意: Milvus Collection 维度必须与实际使用的模型匹配!
    - SiliconFlow bge-large-zh-v1.5 → 1024 维
    - Local MiniLM → 384 维
    """

    def __init__(self):
        self.api_url = f"{SILICONFLOW_EMBEDDING_API}/embeddings"
        self.api_key = SILICONFLOW_API_KEY
        self.api_model = SILICONFLOW_EMBEDDING_MODEL
        self.dim = EMBEDDING_DIM
        self._local_model = None
        self._use_local = FORCE_LOCAL_EMBEDDING
        self._api_failed = False
        self._current_model = self.api_model

        if self._use_local:
            self._load_local_model()

    def _load_local_model(self):
        """加载本地 sentence-transformers 模型"""
        try:
            from sentence_transformers import SentenceTransformer
            print(f"[Embedding] Loading local model: {LOCAL_EMBEDDING_MODEL}")
            self._local_model = SentenceTransformer(LOCAL_EMBEDDING_MODEL)
            self.dim = LOCAL_EMBEDDING_DIM
            self._use_local = True
            self._current_model = f"{LOCAL_EMBEDDING_MODEL} (local)"
            print(f"[Embedding] Local model loaded, dimension={self.dim}")
            return True
        except ImportError as e:
            print(f"[Embedding] sentence-transformers not installed: {e}")
        except Exception as e:
            print(f"[Embedding] Local model load failed: {e}")
        return False

    def encode(self, texts: Union[str, List[str]]) -> np.ndarray:
        """生成文本向量"""
        if isinstance(texts, str):
            texts = [texts]

        if self._use_local or self._api_failed:
            return self._encode_local(texts)
        else:
            try:
                return self._encode_via_api(texts)
            except Exception as e:
                print(f"[Embedding] API failed: {e}, falling back to local model")
                self._api_failed = True
                self._use_local = True
                if self._load_local_model():
                    return self._encode_local(texts)
                raise

    def _encode_via_api(self, texts: List[str]) -> np.ndarray:
        """使用 SiliconFlow API 生成向量"""
        import requests

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        payload = {
            "model": self.api_model,
            "input": texts,
        }

        max_retries = 2
        for attempt in range(max_retries):
            try:
                resp = requests.post(
                    self.api_url,
                    headers=headers,
                    json=payload,
                    timeout=60
                )
                
                if resp.status_code == 403:
                    raise Exception(f"API 403 (账户余额不足): {resp.text[:200]}")
                
                resp.raise_for_status()
                data = resp.json()

                embeddings = [item["embedding"] for item in data["data"]]
                result = np.array(embeddings)
                
                if len(result.shape) == 1:
                    return result.reshape(1, -1)
                return result

            except Exception as e:
                if attempt < max_retries - 1:
                    print(f"[Embedding] API error (attempt {attempt+1}/{max_retries}): {e}")
                    time.sleep(1)
                else:
                    self._api_failed = True
                    self._use_local = True
                    if self._load_local_model():
                        return self._encode_local(texts)
                    raise Exception(f"Embedding service unavailable: {e}")

    def _encode_local(self, texts: List[str]) -> np.ndarray:
        """使用本地模型生成向量"""
        if self._local_model is None:
            if not self._load_local_model():
                raise RuntimeError(
                    "Embedding service unavailable. "
                    "请充值 SiliconFlow 账户或安装 sentence-transformers"
                )

        embeddings = self._local_model.encode(
            texts, 
            show_progress_bar=False,
            convert_to_numpy=True,
            batch_size=32,
        )
        
        if len(embeddings.shape) == 1:
            return embeddings.reshape(1, -1)
        return embeddings

    def encode_single(self, text: str) -> List[float]:
        """生成单个文本的向量，返回 list 格式"""
        vec = self.encode(text)
        if vec.shape[0] == 1:
            return vec[0].tolist()
        return vec.tolist()

    def encode_batch(
        self, 
        texts: List[str], 
        batch_size: int = 20,
        progress_callback=None
    ) -> List[List[float]]:
        """批量编码"""
        all_vectors = []
        total = len(texts)
        actual_batch_size = min(batch_size, 20)

        for i in range(0, total, actual_batch_size):
            batch = texts[i:i + actual_batch_size]
            vecs = self.encode(batch)
            
            if len(vecs.shape) == 2:
                all_vectors.extend(vecs.tolist())
            else:
                all_vectors.append(vecs.tolist())

            if progress_callback:
                progress_callback(min(i + actual_batch_size, total), total)

            if i + actual_batch_size < total and not self._use_local:
                time.sleep(0.1)

        return all_vectors

    def is_local(self) -> bool:
        return self._use_local

    def get_status(self) -> dict:
        return {
            "model": self._current_model,
            "dimension": self.dim,
            "using_local": self._use_local,
            "api_failed": self._api_failed,
        }


# 全局实例
_embedding_service = None


def get_embedding_service() -> "EmbeddingService":
    global _embedding_service
    if _embedding_service is None:
        _embedding_service = EmbeddingService()
    return _embedding_service


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("="*60)
    print("Testing EmbeddingService...")
    print("="*60)

    service = get_embedding_service()
    status = service.get_status()
    print(f"Status: {status}")

    # 测试单条
    print("\n[Test] Single text encoding...")
    try:
        start = time.time()
        vec = service.encode_single("银行活期存款利率是多少？")
        elapsed = time.time() - start
        print(f"  ✅ Success: dim={len(vec)}, time={elapsed:.2f}s")
        print(f"  Sample: {vec[:8]}")
    except Exception as e:
        print(f"  ❌ Error: {e}")

    # 测试批量
    print("\n[Test] Batch encoding...")
    texts = [
        "什么是银行活期存款？",
        "定期存款利率",
        "如何办理贷款？",
        "账户开户流程",
        "理财产品介绍",
    ]
    try:
        start = time.time()
        vecs = service.encode_batch(texts, batch_size=5)
        elapsed = time.time() - start
        print(f"  ✅ Success: {len(vecs)} texts, dim={len(vecs[0])}, time={elapsed:.2f}s")
    except Exception as e:
        print(f"  ❌ Error: {e}")

    print("\n" + "="*60)
    print(f"Final Status: {service.get_status()}")
    print("="*60)
