# -*- coding: utf-8 -*-
"""
Hybrid Search - 混合检索引擎
整合 Milvus 向量检索 + BM25 关键词检索，RRF 融合排序

[OK] 不依赖 SiliconFlow，支持内网大模型
[OK] 向量检索不可用 时自动降级到 BM25-only
"""

import time
import threading
from typing import List, Dict, Optional

from config import (
    RETRIEVAL_MODE,
    VECTOR_TOP_K,
    BM25_TOP_K,
    RERANK_TOP_K,
    RRF_K,
    VECTOR_WEIGHT,
    BM25_WEIGHT,
    SIMILARITY_THRESHOLD,
)
from vector_retriever import get_vector_retriever
from retriever import HybridRetriever as BM25Retriever


class HybridSearchEngine:
    """
    混合检索引擎

    检索模式（由 RETRIEVAL_MODE 控制）:
    - "vector": 纯向量检索（需要 Milvus + Embedding）
    - "bm25": 纯 BM25 关键词检索
    - "hybrid": 向量 + BM25 混合（RRF 融合，推荐）

    自动降级:
    - 向量检索不可用 → 自动切换到 BM25-only
    - BM25 索引未构建 → 尝试构建
    """

    def __init__(self):
        self._vector_retriever = None
        self._bm25_retriever = None
        self._lock = threading.Lock()
        self._mode = RETRIEVAL_MODE
        self._vector_available = None  # 延迟检测
        self._entries_cache = []

        print(f"[HybridSearch] Mode: {self._mode}")
        print(f"[HybridSearch] RRF_k={RRF_K}, weights=vector:{VECTOR_WEIGHT}, bm25:{BM25_WEIGHT}")

    # ── 延迟初始化 ─────────────────────────────────────────────────

    @property
    def vector_retriever(self):
        if self._vector_retriever is None:
            self._vector_retriever = get_vector_retriever()
        return self._vector_retriever

    @property
    def bm25_retriever(self):
        if self._bm25_retriever is None:
            self._bm25_retriever = BM25Retriever()
        return self._bm25_retriever

    def _check_vector_available(self) -> bool:
        """检查向量检索是否可用（延迟检测，缓存结果）"""
        if self._vector_available is not None:
            return self._vector_available

        try:
            if self.vector_retriever.is_ready():
                self._vector_available = True
                print("[HybridSearch] [OK] Vector retrieval available")
            else:
                self._vector_available = False
                print("[HybridSearch] [WARN]  Vector retrieval NOT available (Milvus not ready)")
        except Exception as e:
            self._vector_available = False
            print(f"[HybridSearch] [WARN]  Vector retrieval check failed: {e}")

        return self._vector_available

    # ── 公共接口 ─────────────────────────────────────────────────

    def search(
        self,
        query: str,
        top_k: int = None,
        categories: List[str] = None,
        mode: str = None,
    ) -> List[Dict]:
        """
        混合检索

        Args:
            query: 查询文本
            top_k: 返回数量（默认 RERANK_TOP_K）
            categories: 分类过滤（list 或 None/空字符串表示不限）
            mode: 强制指定模式（vector/bm25/hybrid）

        Returns:
            [{"entry": {...}, "score": float, "vector_score": float, "keyword_score": float}]
        """
        mode = mode or self._mode
        top_k = top_k or RERANK_TOP_K

        # 根据模式选择检索策略
        if mode == "vector":
            return self._vector_only(query, top_k, categories)
        elif mode == "bm25":
            return self._bm25_only(query, top_k, categories)
        else:  # hybrid
            return self._hybrid_search(query, top_k, categories)

    def _vector_only(
        self,
        query: str,
        top_k: int,
        categories: List[str] = None,
    ) -> List[Dict]:
        """纯向量检索"""
        if not self._check_vector_available():
            print("[HybridSearch] Vector not available, falling back to BM25")
            return self._bm25_only(query, top_k, categories)

        results = self.vector_retriever.retrieve(query, top_k=top_k, categories=categories)
        return results

    def _bm25_only(
        self,
        query: str,
        top_k: int,
        categories: List[str] = None,
    ) -> List[Dict]:
        """纯 BM25 检索"""
        try:
            raw = self.bm25_retriever.retrieve(query, top_k=top_k)

            results = []
            for r in raw:
                entry = r.get("entry", {})
                # 多分类过滤
                if categories and len(categories) > 0:
                    entry_category = entry.get("category", "")
                    if entry_category not in categories:
                        continue
                results.append({
                    "entry": entry,
                    "score": r.get("score", 0),
                    "vector_score": None,
                    "keyword_score": r.get("score", 0),
                    "bm25_score": r.get("score", 0),  # 保留原有 key
                })

            return results[:top_k]
        except Exception as e:
            print(f"[HybridSearch] BM25 search failed: {e}")
            return []

    def _hybrid_search(
        self,
        query: str,
        top_k: int,
        categories: List[str] = None,
    ) -> List[Dict]:
        """
        混合检索：向量 + BM25，RRF 融合

        流程:
        1. 并行执行向量检索和 BM25 检索
        2. RRF (Reciprocal Rank Fusion) 融合排序
        3. 过滤低分结果
        4. 返回 Top-K
        """
        vector_available = self._check_vector_available()

        vector_results = []
        bm25_results = []

        if vector_available:
            # 并行执行
            def do_vector():
                nonlocal vector_results
                try:
                    vector_results = self.vector_retriever.retrieve(
                        query, top_k=VECTOR_TOP_K, categories=categories
                    )
                except Exception as e:
                    print(f"[HybridSearch] Vector search error: {e}")

            def do_bm25():
                nonlocal bm25_results
                try:
                    raw = self.bm25_retriever.retrieve(query, top_k=BM25_TOP_K)
                    bm25_results = raw
                except Exception as e:
                    print(f"[HybridSearch] BM25 search error: {e}")

            t1 = threading.Thread(target=do_vector)
            t2 = threading.Thread(target=do_bm25)
            t1.start()
            t2.start()
            t1.join()
            t2.join()
        else:
            # 只用 BM25
            print("[HybridSearch] Vector unavailable, using BM25-only")
            bm25_results = self._bm25_only(query, top_k, categories)
            # 格式化输出
            return bm25_results

        # RRF 融合
        merged = self._rrf_fusion(vector_results, bm25_results, categories)

        # 补充 entry 数据
        merged = self._enrich(merged)

        # 过滤低分
        merged = [r for r in merged if r["score"] >= SIMILARITY_THRESHOLD]

        # 排序，返回 Top-K
        merged.sort(key=lambda x: x["score"], reverse=True)
        return merged[:top_k]

    def _rrf_fusion(
        self,
        vector_results: List[Dict],
        bm25_results: List[Dict],
        categories: List[str] = None,
    ) -> List[Dict]:
        """
        RRF (Reciprocal Rank Fusion) 融合

        公式: score(d) = Σ weight_i / (k + rank_i(d))

        优点: 不需要归一化分数，对排名敏感
        
        Args:
            vector_results: 向量检索结果
            bm25_results: BM25 检索结果
            categories: 分类过滤（list 或 None）
        """
        id_map = {}  # id -> result

        # 向量结果（按 score 排序）
        for rank, r in enumerate(vector_results, 1):
            entry_id = r["entry"].get("id")
            if entry_id is None:
                continue
            if entry_id not in id_map:
                id_map[entry_id] = {
                    "entry": r["entry"],
                    "vector_score": r.get("score", 0),
                    "keyword_score": 0,  # 别名
                    "bm25_score": 0,
                    "vector_rank": rank,
                    "bm25_rank": 9999,
                }
            else:
                id_map[entry_id]["vector_score"] = r.get("score", 0)
                id_map[entry_id]["vector_rank"] = rank

        # BM25 结果（按 score 排序）
        for rank, r in enumerate(bm25_results, 1):
            entry = r.get("entry", {})
            entry_id = entry.get("id")
            if entry_id is None:
                continue
            if entry_id in id_map:
                id_map[entry_id]["keyword_score"] = r.get("score", 0)
                id_map[entry_id]["bm25_score"] = r.get("score", 0)
                id_map[entry_id]["bm25_rank"] = rank
                # 用 BM25 的 entry 补充数据（可能更完整）
                if not id_map[entry_id]["entry"].get("answer"):
                    id_map[entry_id]["entry"] = entry
            else:
                id_map[entry_id] = {
                    "entry": entry,
                    "vector_score": 0,
                    "keyword_score": r.get("score", 0),
                    "bm25_score": r.get("score", 0),
                    "vector_rank": 9999,
                    "bm25_rank": rank,
                }

        # 计算 RRF 分数 + 分类过滤
        results = []
        for item in id_map.values():
            entry = item.get("entry", {})
            
            # 多分类过滤
            if categories and len(categories) > 0:
                entry_category = entry.get("category", "")
                if entry_category not in categories:
                    continue
            
            rrf_vector = VECTOR_WEIGHT / (RRF_K + item["vector_rank"])
            rrf_bm25 = BM25_WEIGHT / (RRF_K + item["bm25_rank"])
            rrf_score = rrf_vector + rrf_bm25

            item["score"] = round(rrf_score, 4)
            item["vector_score"] = round(item["vector_score"], 4)
            item["keyword_score"] = round(item["keyword_score"], 4)
            item["bm25_score"] = round(item["bm25_score"], 4)
            results.append(item)

        return results

    def _enrich(self, results: List[Dict]) -> List[Dict]:
        """用缓存数据补充 entry 字段"""
        if not self._entries_cache:
            return results

        cache_map = {e.get("id"): e for e in self._entries_cache}
        enriched = []

        for r in results:
            entry = r.get("entry", {})
            entry_id = entry.get("id")
            if entry_id in cache_map:
                cached = cache_map[entry_id]
                # 补充缺失字段
                if not entry.get("question") and cached.get("question"):
                    entry["question"] = cached["question"]
                if not entry.get("answer") and cached.get("answer"):
                    entry["answer"] = cached["answer"]
                if not entry.get("category") and cached.get("category"):
                    entry["category"] = cached["category"]
                if not entry.get("source_type") and cached.get("source_type"):
                    entry["source_type"] = cached["source_type"]
                if not entry.get("source_question_id") and cached.get("source_question_id"):
                    entry["source_question_id"] = cached["source_question_id"]
                r["entry"] = entry
                enriched.append(r)
            elif entry.get("question"):  # 至少有问题
                enriched.append(r)

        return enriched

    # ── 索引管理 ─────────────────────────────────────────────────

    def build_index(self, entries: List[Dict]):
        """构建 BM25 索引 + 初始化向量索引"""
        with self._lock:
            self._entries_cache = entries or []

            # 构建 BM25 索引
            try:
                self.bm25_retriever.build_index(entries)
                print(f"[HybridSearch] BM25 index built: {len(entries)} entries")
            except Exception as e:
                print(f"[HybridSearch] BM25 build failed: {e}")

            # 初始化向量索引（如果可用）
            if self._check_vector_available():
                try:
                    count = self.vector_retriever.insert_entries(entries)
                    print(f"[HybridSearch] Vector index updated: {count} entries")
                except Exception as e:
                    print(f"[HybridSearch] Vector index update failed: {e}")

    def reload(self):
        """重新加载索引"""
        try:
            self.bm25_retriever.reload()
        except Exception as e:
            print(f"[HybridSearch] BM25 reload failed: {e}")

        try:
            if self.vector_retriever and self.vector_retriever.is_ready():
                self.vector_retriever.milvus.load_collection()
                print("[HybridSearch] Vector index reloaded")
        except Exception as e:
            print(f"[HybridSearch] Vector reload failed: {e}")

    def get_status(self) -> Dict:
        """获取状态"""
        return {
            "mode": self._mode,
            "vector_available": self._check_vector_available(),
            "bm25_available": hasattr(self, '_bm25_retriever') and self._bm25_retriever is not None,
            "vector_stats": self.vector_retriever.get_stats() if self._check_vector_available() else None,
        }


# ── 全局单例 ─────────────────────────────────────────────────────────
_hybrid_search = None


def get_hybrid_search() -> HybridSearchEngine:
    global _hybrid_search
    if _hybrid_search is None:
        _hybrid_search = HybridSearchEngine()
    return _hybrid_search


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("="*60)
    print("Testing HybridSearchEngine...")
    print("="*60)

    from db_loader import load_knowledge_entries

    # 加载知识库
    print("[Test] Loading knowledge entries...")
    entries = load_knowledge_entries()
    print(f"[Test] Loaded {len(entries)} entries")

    # 创建搜索引擎
    engine = get_hybrid_search()

    # 构建索引
    print("[Test] Building index...")
    engine.build_index(entries)

    # 测试查询
    queries = [
        "银行活期存款利率是多少？",
        "定期存款和活期有什么区别？",
        "如何计算贷款利息？",
    ]

    for q in queries:
        print(f"\n{'='*60}")
        print(f"[Query] {q}")
        print(f"{'='*60}")

        start = time.time()
        results = engine.search(q, top_k=5)
        elapsed = time.time() - start

        print(f"Found {len(results)} results in {elapsed*1000:.1f}ms")
        for i, r in enumerate(results, 1):
            e = r["entry"]
            print(f"  [{i}] score={r['score']:.4f} "
                  f"(vec={r.get('vector_score', '-'):.3f}, "
                  f"bm25={r.get('bm25_score', '-'):.3f})")
            print(f"      Q: {e.get('question', '')[:60]}")
            print(f"      A: {e.get('answer', '')[:80]}")

    print("\n" + "="*60)
    print("Status:", engine.get_status())
    print("="*60)
