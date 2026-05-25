# -*- coding: utf-8 -*-
"""
Vector Retriever - Milvus 向量检索器
调用 milvus_client.py 操作 Milvus，调用 embedding_client.py 生成向量

[OK] 不依赖 SiliconFlow，支持内网大模型
[OK] 支持 Zilliz Cloud 和自建 Milvus
"""

import time
import json
import threading
from typing import List, Dict, Optional

from config import (
    MILVUS_COLLECTION,
    VECTOR_TOP_K,
    SIMILARITY_THRESHOLD,
)
from milvus_client import get_milvus_client
from embedding_client import get_embedding_client


class VectorRetriever:
    """
    Milvus 向量检索器

    工作流程:
    1. 用 EmbeddingClient 将查询文本转为向量
    2. 在 Milvus 中做 ANN 向量检索
    3. 返回相似度最高的 Top-K 结果
    """

    def __init__(self, collection_name: str = None):
        self.collection_name = collection_name or MILVUS_COLLECTION
        self.embedding = get_embedding_client()
        self.milvus = get_milvus_client()
        self._lock = threading.Lock()

        # 确保 Collection 存在
        if self.milvus.is_connected() and not self.milvus.has_collection(self.collection_name):
            print(f"[VectorRetriever] Collection '{self.collection_name}' not found")
            print(f"[VectorRetriever] Run migrate_to_milvus.py --mode full to create it")

    def is_ready(self) -> bool:
        """检查是否就绪（Milvus 连接 + Collection 存在）"""
        if not self.milvus.is_connected():
            return False
        return self.milvus.has_collection(self.collection_name)

    def retrieve(
        self,
        query: str,
        top_k: int = None,
        category: str = None,
        categories: List[str] = None,
    ) -> List[Dict]:
        """
        向量检索

        Args:
            query: 查询文本
            top_k: 返回数量
            category: 分类过滤（单值，兼容旧接口）
            categories: 分类过滤（列表，多分类）

        Returns:
            [{"entry": {...}, "score": float, "distance": float, "vector_score": float}]
        """
        if not self.is_ready():
            print("[VectorRetriever] Not ready (Milvus not connected or collection not found)")
            return []

        top_k = top_k or VECTOR_TOP_K

        # 1. 生成查询向量
        try:
            query_vec = self.embedding.encode_single(query)
        except Exception as e:
            print(f"[VectorRetriever] Embedding failed: {e}")
            return []

        # 2. Milvus ANN 检索
        try:
            hits = self.milvus.search_single(
                query_vector=query_vec,
                top_k=top_k,
                category=category,
                categories=categories,
            )
        except Exception as e:
            print(f"[VectorRetriever] Search failed: {e}")
            return []

        # 3. 格式化结果
        results = []
        for hit in hits:
            # Milvus COSINE 距离: 1.0 = 完全相同, 0 = 不相关
            # 转换为相似度分数
            distance = hit["distance"]
            score = max(0.0, min(1.0, distance))  # clamp to [0, 1]

            if score >= SIMILARITY_THRESHOLD:
                results.append({
                    "entry": {
                        "id": hit["kb_id"],
                        "question": hit["question"],
                        "answer": hit["answer"],
                        "category": hit["category"],
                    },
                    "score": round(score, 4),
                    "distance": round(distance, 4),
                    "vector_score": round(score, 4),
                    "keyword_score": None,
                    "bm25_score": None,
                })

        return results

    def insert_entries(self, entries: List[Dict]) -> int:
        """
        批量插入知识库条目到 Milvus

        Args:
            entries: [{"id": int, "question": str, "answer": str, "category": str}]

        Returns:
            成功插入的条数
        """
        if not entries:
            return 0
        if not self.milvus.is_connected():
            print("[VectorRetriever] Milvus not connected")
            return 0

        # 确保 Collection 存在
        if not self.milvus.has_collection(self.collection_name):
            dim = self.embedding.dim
            print(f"[VectorRetriever] Creating collection with dim={dim}")
            self.milvus.create_collection(dimension=dim, drop_existing=False)

        # 准备数据
        kb_ids = [e["id"] for e in entries]
        questions = [e.get("question", "") for e in entries]
        answers = [e.get("answer", "") for e in entries]
        categories = [e.get("category", "未分类") for e in entries]

        # 生成向量（批量）
        print(f"[VectorRetriever] Generating embeddings for {len(entries)} entries...")
        start = time.time()
        try:
            vectors = self.embedding.encode(questions).tolist()
        except Exception as e:
            print(f"[VectorRetriever] Embedding failed: {e}")
            return 0
        elapsed = time.time() - start
        print(f"[VectorRetriever] Embeddings generated in {elapsed:.1f}s")

        # 插入 Milvus
        try:
            ids = self.milvus.insert(
                kb_ids=kb_ids,
                questions=questions,
                answers=answers,
                categories=categories,
                vectors=vectors,
            )
            return len(ids)
        except Exception as e:
            print(f"[VectorRetriever] Insert failed: {e}")
            return 0

    def upsert_entry(self, entry: Dict) -> bool:
        """插入或更新单条（先删后插）"""
        kb_id = entry["id"]
        self.milvus.delete_by_kb_id(kb_id)
        count = self.insert_entries([entry])
        return count > 0

    def delete_by_kb_ids(self, kb_ids: List[int]) -> bool:
        return self.milvus.delete_by_kb_ids(kb_ids)

    def get_stats(self) -> Dict:
        if not self.milvus.is_connected():
            return {"ready": False, "connected": False}
        if not self.milvus.has_collection():
            return {"ready": False, "connected": True, "collection_exists": False}

        count = self.milvus.get_collection_stats()
        return {
            "ready": True,
            "connected": True,
            "collection_exists": True,
            "entities": count,
            "embedding_method": self.embedding.get_status()["method"],
            "embedding_dim": self.embedding.dim,
        }

    def close(self):
        self.milvus.close()


# ── 全局单例 ─────────────────────────────────────────────────────────
_vector_retriever = None


def get_vector_retriever() -> VectorRetriever:
    global _vector_retriever
    if _vector_retriever is None:
        _vector_retriever = VectorRetriever()
    return _vector_retriever


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("="*60)
    print("Testing VectorRetriever...")
    print("="*60)

    retriever = get_vector_retriever()
    stats = retriever.get_stats()
    print(f"Stats: {stats}")

    if not retriever.is_ready():
        print("\n[WARN]  VectorRetriever not ready!")
        print("   Possible reasons:")
        print("   1. Milvus not running (start: milvus run)")
        print("   2. Collection not created (run: python migrate_to_milvus.py --mode full)")
        print("   3. Embedding service not available")
    else:
        # 测试检索
        print(f"\n[Test] Retrieval...")
        results = retriever.retrieve("银行活期存款利率", top_k=3)
        print(f"Found {len(results)} results:")
        for i, r in enumerate(results, 1):
            e = r["entry"]
            print(f"  {i}. [{e['category']}] {e['question'][:40]}... (score={r['score']:.4f})")

    print("="*60)
