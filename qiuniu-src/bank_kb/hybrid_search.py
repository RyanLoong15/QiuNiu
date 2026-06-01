# -*- coding: utf-8 -*-
"""
Hybrid Search Engine - Milvus 向量检索 + BM25 + RRF 融合
结合语义检索和关键词检索的优势，提供更准确的召回
"""

import json
import time
import threading
from typing import List, Dict, Any, Optional, Tuple

from config import (
    VECTOR_TOP_K,
    BM25_TOP_K,
    RERANK_TOP_K,
    RRF_K,
    SIMILARITY_THRESHOLD,
    USE_MILVUS,
    USE_HYBRID,
)

# 延迟导入，避免循环依赖
_milvus_client = None
_embedding_client = None
_bm25_retriever = None


def _get_milvus():
    global _milvus_client
    if _milvus_client is None and USE_MILVUS:
        try:
            from milvus_client import MilvusClient, get_milvus_client
            _milvus_client = get_milvus_client()
        except Exception as e:
            print(f"[HybridSearch] Milvus not available: {e}")
            _milvus_client = None
    return _milvus_client


def _get_embedding():
    global _embedding_client
    if _embedding_client is None:
        try:
            from embedding_client import EmbeddingClient, get_embedding_client
            _embedding_client = get_embedding_client()
        except Exception as e:
            print(f"[HybridSearch] Embedding client not available: {e}")
            _embedding_client = None
    return _embedding_client


def _get_bm25():
    global _bm25_retriever
    if _bm25_retriever is None:
        try:
            from retriever import HybridRetriever
            _bm25_retriever = HybridRetriever()
        except Exception as e:
            print(f"[HybridSearch] BM25 retriever not available: {e}")
            _bm25_retriever = None
    return _bm25_retriever


class SearchResult:
    """搜索结果对象"""

    def __init__(
        self,
        kb_id: int,
        question: str = None,
        answer: str = None,
        category: str = None,
        score: float = 0.0,
        vector_score: float = 0.0,
        bm25_score: float = 0.0,
        rrf_score: float = 0.0,
        source: str = "hybrid",
        metadata: dict = None,
    ):
        self.kb_id = kb_id
        self.question = question
        self.answer = answer
        self.category = category or "通用"
        self.score = score
        self.vector_score = vector_score
        self.bm25_score = bm25_score
        self.rrf_score = rrf_score
        self.source = source  # "vector" | "bm25" | "hybrid"
        self.metadata = metadata or {}

    def to_dict(self) -> dict:
        return {
            "id": self.kb_id,
            "question": self.question,
            "answer": self.answer,
            "category": self.category,
            "score": round(self.score, 4),
            "vector_score": round(self.vector_score, 4),
            "bm25_score": round(self.bm25_score, 4),
            "source": self.source,
            "metadata": self.metadata,
        }

    @classmethod
    def from_vector_hit(cls, hit: dict) -> "SearchResult":
        """从 Milvus 搜索结果创建"""
        metadata = {}
        if hit.get("metadata"):
            try:
                metadata = json.loads(hit["metadata"])
            except:
                pass

        return cls(
            kb_id=hit.get("kb_id", 0),
            question=metadata.get("question"),
            answer=metadata.get("answer"),
            category=hit.get("category"),
            vector_score=hit.get("distance", 0.0),
            source="vector",
            metadata=metadata,
        )

    @classmethod
    def from_bm25_entry(cls, entry: dict, score: float) -> "SearchResult":
        """从 BM25 结果创建"""
        kb_entry = entry.get("entry", {})
        return cls(
            kb_id=kb_entry.get("id", 0),
            question=kb_entry.get("question"),
            answer=kb_entry.get("answer"),
            category=kb_entry.get("category"),
            bm25_score=score,
            source="bm25",
        )


class HybridSearchEngine:
    """
    混合检索引擎

    策略:
    1. 如果 USE_MILVUS=True: 使用 Milvus 向量检索 + BM25，然后 RRF 融合
    2. 如果 USE_MILVUS=False: 仅使用 BM25 (降级模式)

    RRF (Reciprocal Rank Fusion) 融合公式:
        score(d) = Σ 1/(k + rank_i(d))

    优点:
    - 无需训练，自动平衡不同检索器
    - 对排名顺序敏感，而非绝对分数
    - 对检索器质量差异有鲁棒性
    """

    def __init__(
        self,
        vector_top_k: int = None,
        bm25_top_k: int = None,
        rerank_top_k: int = None,
        use_milvus: bool = None,
    ):
        self.vector_top_k = vector_top_k or VECTOR_TOP_K
        self.bm25_top_k = bm25_top_k or BM25_TOP_K
        self.rerank_top_k = rerank_top_k or RERANK_TOP_K
        self.use_milvus = use_milvus if use_milvus is not None else USE_MILVUS
        self.rrf_k = RRF_K

        self._milvus = None
        self._embedding = None
        self._bm25 = None
        self._entries_cache = []
        self._lock = threading.Lock()

    def _init_clients(self):
        """初始化各检索器客户端"""
        if self.use_milvus:
            self._milvus = _get_milvus()
            self._embedding = _get_embedding()

            if self._milvus is None or self._embedding is None:
                print("[HybridSearch] Milvus/Embedding unavailable, falling back to BM25")
                self.use_milvus = False

        if not self.use_milvus:
            self._bm25 = _get_bm25()
            if self._bm25 is None:
                print("[HybridSearch] BM25 unavailable!")

    def build_index(self, entries: List[dict]):
        """
        构建检索索引

        Args:
            entries: 知识库条目列表
                [{
                    "id": 1,
                    "question": "问题",
                    "answer": "回答",
                    "category": "分类"
                }, ...]
        """
        self._init_clients()
        self._entries_cache = entries or []

        with self._lock:
            # 初始化 BM25 (用于降级和混合)
            if self._bm25 is None:
                self._bm25 = _get_bm25()
            if self._bm25:
                self._bm25.build_index(entries)
                print(f"[HybridSearch] BM25 index built: {len(entries)} entries")

            # Milvus 索引已由 migrator 构建
            if self.use_milvus and self._milvus:
                if self._milvus.has_collection():
                    self._milvus.load_collection()
                    print(f"[HybridSearch] Milvus collection loaded")
                else:
                    print(f"[HybridSearch] Milvus collection not found, using BM25 only")
                    self.use_milvus = False

    def search(
        self,
        query: str,
        category: str = None,
        top_k: int = None,
        min_score: float = None,
    ) -> List[SearchResult]:
        """
        执行混合检索

        Args:
            query: 查询文本
            category: 分类过滤 (可选)
            top_k: 返回数量 (覆盖默认值)
            min_score: 最低分数阈值 (可选)

        Returns:
            SearchResult 列表，按融合分数降序排列
        """
        self._init_clients()

        top_k = top_k or self.rerank_top_K
        min_score = min_score or SIMILARITY_THRESHOLD

        if self.use_milvus and self._milvus and self._embedding:
            # 混合检索: Milvus + BM25 + RRF
            return self._hybrid_search(query, category, top_k, min_score)
        else:
            # 降级模式: 仅 BM25
            return self._bm25_search(query, category, top_k, min_score)

    def _hybrid_search(
        self,
        query: str,
        category: str = None,
        top_k: int = None,
        min_score: float = 0.0,
    ) -> List[SearchResult]:
        """混合检索: 向量 + BM25 + RRF 融合"""

        # Step 1: 并行执行向量检索和 BM25
        vector_results = []
        bm25_results = []

        def do_vector_search():
            nonlocal vector_results
            try:
                # 生成查询向量
                query_vec = self._embedding.encode(query)
                if query_vec is None:
                    return

                # Milvus 搜索
                hits = self._milvus.search_question(
                    query_vector=query_vec,
                    top_k=self.vector_top_k,
                    category=category,
                )

                vector_results = [
                    SearchResult.from_vector_hit(hit)
                    for hit in hits
                ]
            except Exception as e:
                print(f"[HybridSearch] Vector search error: {e}")

        def do_bm25_search():
            nonlocal bm25_results
            try:
                if self._bm25 is None:
                    return

                raw_results = self._bm25.retrieve(query, top_k=self.bm25_top_k)

                # 分类过滤
                for entry in raw_results:
                    kb_entry = entry.get("entry", {})
                    if category is None or kb_entry.get("category") == category:
                        bm25_results.append(
                            SearchResult.from_bm25_entry(entry, entry.get("score", 0.0))
                        )
            except Exception as e:
                print(f"[HybridSearch] BM25 search error: {e}")

        # 并行执行
        t1 = threading.Thread(target=do_vector_search)
        t2 = threading.Thread(target=do_bm25_search)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        print(f"[HybridSearch] Vector hits: {len(vector_results)}, BM25 hits: {len(bm25_results)}")

        # Step 2: RRF 融合
        if not vector_results and not bm25_results:
            return []

        fused = self._rrf_fusion(vector_results, bm25_results)

        # Step 3: 补充原始数据
        results = self._enrich_results(fused)

        # Step 4: 过滤和截断
        results = [r for r in results if r.score >= min_score]

        return results[:top_k]

    def _bm25_search(
        self,
        query: str,
        category: str = None,
        top_k: int = None,
        min_score: float = 0.0,
    ) -> List[SearchResult]:
        """仅使用 BM25 检索 (降级模式)"""

        if self._bm25 is None:
            return []

        try:
            raw_results = self._bm25.retrieve(query, top_k=top_k or self.bm25_top_k)

            results = []
            for entry in raw_results:
                kb_entry = entry.get("entry", {})
                # 分类过滤
                if category and kb_entry.get("category") != category:
                    continue

                result = SearchResult.from_bm25_entry(entry, entry.get("score", 0.0))
                results.append(result)

            # 补充原始数据
            results = self._enrich_results(results)

            # 过滤和截断
            results = [r for r in results if r.score >= min_score]
            return results[:top_k or self.rerank_top_k]

        except Exception as e:
            print(f"[HybridSearch] BM25 search error: {e}")
            return []

    def _rrf_fusion(
        self,
        vector_results: List[SearchResult],
        bm25_results: List[SearchResult],
        k: int = None,
    ) -> List[SearchResult]:
        """
        RRF (Reciprocal Rank Fusion) 融合

        公式: score(d) = Σ 1/(k + rank_i(d))

        Args:
            vector_results: 向量检索结果 (已按 distance 降序)
            bm25_results: BM25 结果 (已按 score 降序)
            k: RRF 参数，默认 60

        Returns:
            融合后的 SearchResult 列表
        """
        k = k or self.rrf_k

        scores = {}  # kb_id -> (rrf_score, best_result)

        # 处理向量检索结果 (distance 越大越好)
        for rank, result in enumerate(sorted(vector_results, key=lambda x: x.vector_score, reverse=True)):
            kb_id = result.kb_id
            rrf_score = 1.0 / (k + rank + 1)
            if kb_id not in scores:
                scores[kb_id] = {"rrf": 0.0, "best": result}
            scores[kb_id]["rrf"] += rrf_score
            scores[kb_id]["best"].vector_score = result.vector_score

        # 处理 BM25 结果
        for rank, result in enumerate(sorted(bm25_results, key=lambda x: x.bm25_score, reverse=True)):
            kb_id = result.kb_id
            rrf_score = 1.0 / (k + rank + 1)
            if kb_id not in scores:
                scores[kb_id] = {"rrf": 0.0, "best": result}
            scores[kb_id]["rrf"] += rrf_score
            scores[kb_id]["best"].bm25_score = result.bm25_score

        # 按 RRF 分数排序
        sorted_results = sorted(
            scores.values(),
            key=lambda x: x["rrf"],
            reverse=True,
        )

        # 更新最终分数
        results = []
        for item in sorted_results:
            result = item["best"]
            result.rrf_score = item["rrf"]
            result.score = item["rrf"]  # 最终分数使用 RRF 分数
            result.source = "hybrid" if (result.vector_score > 0 and result.bm25_score > 0) else result.source
            results.append(result)

        return results

    def _enrich_results(self, results: List[SearchResult]) -> List[SearchResult]:
        """
        从缓存中补充原始数据

        如果 Milvus/BM25 返回的结果缺少 question/answer，
        从 _entries_cache 中查找补充
        """
        if not results:
            return results

        # 构建 ID -> entry 映射
        cache_map = {entry.get("id"): entry for entry in self._entries_cache}

        enriched = []
        for result in results:
            # 如果缺少 question/answer，尝试从缓存补充
            if not result.question or not result.answer:
                cached = cache_map.get(result.kb_id)
                if cached:
                    result.question = result.question or cached.get("question")
                    result.answer = result.answer or cached.get("answer")
                    result.category = result.category or cached.get("category")

            if result.question:  # 只保留有问题的结果
                enriched.append(result)

        return enriched

    def get_stats(self) -> dict:
        """获取检索引擎状态"""
        stats = {
            "mode": "hybrid" if self.use_milvus else "bm25_only",
            "vector_top_k": self.vector_top_k,
            "bm25_top_k": self.bm25_top_k,
            "rerank_top_k": self.rerank_top_k,
            "entries_cache": len(self._entries_cache),
        }

        if self.use_milvus and self._milvus:
            try:
                stats["milvus_entities"] = self._milvus.get_collection_stats()
                stats["milvus_connected"] = self._milvus.is_connected()
            except:
                stats["milvus_connected"] = False

        return stats


# ── 全局单例 ─────────────────────────────────────────────────────────
_hybrid_engine = None


def get_hybrid_engine() -> HybridSearchEngine:
    global _hybrid_engine
    if _hybrid_engine is None:
        _hybrid_engine = HybridSearchEngine()
    return _hybrid_engine


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("Testing HybridSearchEngine...")

    from db_loader import load_knowledge_entries

    # 加载知识库
    entries = load_knowledge_entries()
    print(f"Loaded {len(entries)} entries")

    # 创建引擎
    engine = HybridSearchEngine()

    # 构建索引
    engine.build_index(entries)

    # 测试查询
    queries = [
        "银行活期存款利率是多少？",
        "定期存款和活期有什么区别？",
        "如何计算贷款利息？",
    ]

    for q in queries:
        print(f"\n{'='*60}")
        print(f"Query: {q}")
        print(f"{'='*60}")

        start = time.time()
        results = engine.search(q, top_k=5)
        elapsed = time.time() - start

        print(f"Found {len(results)} results in {elapsed*1000:.1f}ms")
        for i, r in enumerate(results, 1):
            print(f"\n  [{i}] KB_ID={r.kb_id} Score={r.score:.4f} (vec={r.vector_score:.3f}, bm25={r.bm25_score:.3f})")
            print(f"      Q: {r.question[:60]}...")
            print(f"      A: {r.answer[:80]}...")

    print(f"\n{'='*60}")
    print("Engine stats:", engine.get_stats())
