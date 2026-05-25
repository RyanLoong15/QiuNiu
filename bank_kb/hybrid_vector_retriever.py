# -*- coding: utf-8 -*-
"""
Hybrid Retriever (Vector + BM25) - 混合检索器
[OK] 优化: 改进 RRF 融合算法，支持分类过滤，并行检索
"""

from typing import List, Dict, Optional
import threading
import time

from config import (
    TOP_K, SIMILARITY_THRESHOLD,
    VECTOR_WEIGHT, BM25_WEIGHT,
    VECTOR_TOP_K, BM25_TOP_K, RERANK_TOP_K,
    RRF_K,
)
from vector_retriever import get_vector_retriever
from retriever import HybridRetriever as BM25Retriever


class HybridVectorRetriever:
    """
    混合检索器：向量检索 + BM25 关键词检索

    [OK] 优化特性:
    - 并行执行向量检索和 BM25 (多线程)
    - RRF (Reciprocal Rank Fusion) 融合算法
    - 支持分类过滤
    - 独立的召回数和最终返回数
    """

    def __init__(
        self,
        vector_weight: float = None,
        bm25_weight: float = None,
        use_rrf: bool = True,
    ):
        """
        初始化混合检索器

        Args:
            vector_weight: 向量检索权重 (默认从配置读取)
            bm25_weight: BM25 检索权重 (默认从配置读取)
            use_rrf: 是否使用 RRF 融合 (True) 或简单加权 (False)
        """
        self.vector_weight = vector_weight if vector_weight is not None else VECTOR_WEIGHT
        self.bm25_weight = bm25_weight if bm25_weight is not None else BM25_WEIGHT
        self.use_rrf = use_rrf
        self.rrf_k = RRF_K
        self._lock = threading.Lock()
        self._initialized = False
        self._entries_cache = []  # 缓存条目用于补充数据

        # 初始化两个检索器 (懒加载)
        self._vector_retriever = None
        self._bm25_retriever = None

        print(f"[HybridRetriever] Initialized (RRF={use_rrf}), "
              f"weights: vector={self.vector_weight}, bm25={self.bm25_weight}")

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

    def _init_clients(self):
        """懒初始化客户端"""
        if not self._initialized:
            # 尝试初始化向量检索器
            if self._vector_retriever is None:
                try:
                    self._vector_retriever = get_vector_retriever()
                except Exception as e:
                    print(f"[HybridRetriever] Vector retriever init failed: {e}")
                    self._vector_retriever = None

            # BM25 总是可用
            if self._bm25_retriever is None:
                self._bm25_retriever = BM25Retriever()

            self._initialized = True

    def retrieve(
        self,
        query: str,
        top_k: int = None,
        category: str = None,
    ) -> List[Dict]:
        """
        混合检索：融合向量检索和 BM25 结果

        Args:
            query: 用户问题
            top_k: 返回结果数量
            category: 分类过滤 (可选)

        Returns:
            检索结果列表，格式: [{"entry": {...}, "score": float, "vector_score": float, "bm25_score": float}]
        """
        self._init_clients()

        top_k = top_k or RERANK_TOP_K
        vector_top = VECTOR_TOP_K
        bm25_top = BM25_TOP_K

        # 并行执行两个检索
        vector_results = []
        bm25_results = []
        vector_error = None
        bm25_error = None

        def do_vector_search():
            nonlocal vector_results, vector_error
            try:
                if self.vector_retriever and self.vector_retriever.is_connected():
                    vector_results = self.vector_retriever.retrieve(
                        query, 
                        top_k=vector_top,
                        category=category,
                    )
                else:
                    print("[HybridRetriever] Vector retriever not available")
            except Exception as e:
                vector_error = e
                print(f"[HybridRetriever] Vector search error: {e}")

        def do_bm25_search():
            nonlocal bm25_results, bm25_error
            try:
                raw = self.bm25_retriever.retrieve(query, top_k=bm25_top)
                
                # 分类过滤
                for r in raw:
                    entry = r.get("entry", {})
                    if category is None or entry.get("category") == category:
                        bm25_results.append(r)
            except Exception as e:
                bm25_error = e
                print(f"[HybridRetriever] BM25 search error: {e}")

        # 并行执行
        t1 = threading.Thread(target=do_vector_search)
        t2 = threading.Thread(target=do_bm25_search)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        print(f"[HybridRetriever] Query='{query[:30]}...', "
              f"vector={len(vector_results)}, bm25={len(bm25_results)}")

        # 如果两个都没结果，降级到 BM25-only
        if not vector_results and not bm25_results:
            if self.bm25_retriever.doc_count > 0:
                # BM25 作为兜底
                return self._bm25_only_fallback(query, top_k, category)
            return []

        # 融合结果
        if self.use_rrf:
            merged = self._rrf_fusion(vector_results, bm25_results)
        else:
            merged = self._weighted_fusion(vector_results, bm25_results)

        # 补充原始数据
        merged = self._enrich_entries(merged)

        # 过滤低分结果
        merged = [r for r in merged if r["score"] >= SIMILARITY_THRESHOLD]

        # 排序并返回 top_k
        merged.sort(key=lambda x: x["score"], reverse=True)
        return merged[:top_k]

    def _rrf_fusion(
        self,
        vector_results: List[Dict],
        bm25_results: List[Dict],
    ) -> List[Dict]:
        """
        RRF (Reciprocal Rank Fusion) 融合算法

        公式: score(d) = Σ 1/(k + rank_i(d))

        优点:
        - 无需训练，自动平衡不同检索器
        - 对排名顺序敏感，而非绝对分数
        - 对检索器质量差异有鲁棒性
        """
        # 按ID聚合结果
        id_to_result = {}

        # 处理向量检索结果 (按 score 降序排列)
        sorted_vector = sorted(vector_results, key=lambda x: x.get("score", 0), reverse=True)
        for rank, result in enumerate(sorted_vector, 1):
            entry_id = result.get("entry", {}).get("id")
            if entry_id is None:
                continue

            if entry_id not in id_to_result:
                id_to_result[entry_id] = {
                    "entry": result["entry"],
                    "vector_score": result.get("score", 0),
                    "bm25_score": 0,
                    "vector_rank": rank,
                    "bm25_rank": 9999,
                }
            else:
                id_to_result[entry_id]["vector_score"] = result.get("score", 0)
                id_to_result[entry_id]["vector_rank"] = rank

        # 处理 BM25 结果
        sorted_bm25 = sorted(bm25_results, key=lambda x: x.get("score", 0), reverse=True)
        for rank, result in enumerate(sorted_bm25, 1):
            entry_id = result.get("entry", {}).get("id")
            if entry_id is None:
                continue

            if entry_id in id_to_result:
                id_to_result[entry_id]["bm25_score"] = result.get("score", 0)
                id_to_result[entry_id]["bm25_rank"] = rank
                # 更新 entry (BM25 可能包含更完整的数据)
                if not id_to_result[entry_id]["entry"].get("answer"):
                    id_to_result[entry_id]["entry"] = result["entry"]
            else:
                id_to_result[entry_id] = {
                    "entry": result["entry"],
                    "vector_score": 0,
                    "bm25_score": result.get("score", 0),
                    "vector_rank": 9999,
                    "bm25_rank": rank,
                }

        # 计算 RRF 分数
        results = []
        for item in id_to_result.values():
            rrf_vector = 1.0 / (self.rrf_k + item["vector_rank"])
            rrf_bm25 = 1.0 / (self.rrf_k + item["bm25_rank"])

            # 加权 RRF (可选)
            rrf_score = (
                self.vector_weight * rrf_vector + 
                self.bm25_weight * rrf_bm25
            )

            # 也可以使用简单 RRF (不加权)
            # rrf_score = rrf_vector + rrf_bm25

            item["score"] = round(rrf_score, 4)
            item["vector_score"] = round(item["vector_score"], 4)
            item["bm25_score"] = round(item["bm25_score"], 4)

            results.append(item)

        return results

    def _weighted_fusion(
        self,
        vector_results: List[Dict],
        bm25_results: List[Dict],
    ) -> List[Dict]:
        """
        简单加权融合 (非 RRF)

        使用归一化的分数进行加权平均
        """
        # 归一化向量分数
        max_vector = max((r.get("score", 0) for r in vector_results), default=1)
        if max_vector == 0:
            max_vector = 1

        # 归一化 BM25 分数
        max_bm25 = max((r.get("score", 0) for r in bm25_results), default=1)
        if max_bm25 == 0:
            max_bm25 = 1

        # 按ID聚合
        id_to_result = {}

        for result in vector_results:
            entry = result.get("entry", {})
            entry_id = entry.get("id")
            if entry_id is None:
                continue

            norm_score = result.get("score", 0) / max_vector
            id_to_result[entry_id] = {
                "entry": entry,
                "vector_score": result.get("score", 0),
                "bm25_score": 0,
                "norm_vector": norm_score,
                "norm_bm25": 0,
            }

        for result in bm25_results:
            entry = result.get("entry", {})
            entry_id = entry.get("id")
            if entry_id is None:
                continue

            norm_score = result.get("score", 0) / max_bm25
            if entry_id in id_to_result:
                id_to_result[entry_id]["bm25_score"] = result.get("score", 0)
                id_to_result[entry_id]["norm_bm25"] = norm_score
                # 补充数据
                if not id_to_result[entry_id]["entry"].get("answer"):
                    id_to_result[entry_id]["entry"] = entry
            else:
                id_to_result[entry_id] = {
                    "entry": entry,
                    "vector_score": 0,
                    "bm25_score": result.get("score", 0),
                    "norm_vector": 0,
                    "norm_bm25": norm_score,
                }

        # 加权融合
        for item in id_to_result.values():
            item["score"] = round(
                self.vector_weight * item["norm_vector"] + 
                self.bm25_weight * item["norm_bm25"],
                4
            )

        return list(id_to_result.values())

    def _bm25_only_fallback(
        self,
        query: str,
        top_k: int,
        category: str = None,
    ) -> List[Dict]:
        """BM25 降级模式"""
        print("[HybridRetriever] Falling back to BM25-only mode")

        raw = self.bm25_retriever.retrieve(query, top_k=top_k)
        results = []

        for r in raw:
            entry = r.get("entry", {})
            if category and entry.get("category") != category:
                continue
            results.append({
                "entry": entry,
                "score": r.get("score", 0),
                "vector_score": 0,
                "bm25_score": r.get("score", 0),
            })

        return results

    def _enrich_entries(self, results: List[Dict]) -> List[Dict]:
        """从缓存中补充条目数据"""
        if not results:
            return results

        # 构建 ID -> entry 映射
        cache_map = {entry.get("id"): entry for entry in self._entries_cache}

        enriched = []
        for r in results:
            entry = r.get("entry", {})
            entry_id = entry.get("id")

            # 补充缺失字段
            if not entry.get("question") or not entry.get("answer"):
                cached = cache_map.get(entry_id)
                if cached:
                    entry = {
                        **cached,
                        **entry,  # 已有的字段优先
                    }

            if entry.get("question"):  # 只保留有问题的结果
                r["entry"] = entry
                enriched.append(r)

        return enriched

    def build_index(self, entries: List[Dict]):
        """
        构建索引（同时构建向量索引和 BM25 索引）

        Args:
            entries: 知识库条目列表
        """
        self._init_clients()

        with self._lock:
            # 缓存条目
            self._entries_cache = entries or []

            # 构建 BM25 索引
            if self._bm25_retriever:
                self._bm25_retriever.build_index(entries)
                print(f"[HybridRetriever] BM25 index built: {len(entries)} entries")

            # 向量索引需要通过迁移脚本单独构建
            # 如果 Milvus 可用，打印提示
            if self._vector_retriever and self._vector_retriever.is_connected():
                stats = self._vector_retriever.get_stats()
                print(f"[HybridRetriever] Milvus collection: {stats.get('entities', 0)} entities")
                if stats.get('entities', 0) == 0:
                    print(f"[HybridRetriever] [WARN] Milvus is empty. Run 'python migrate_to_milvus.py --mode full'")
            else:
                print(f"[HybridRetriever] [WARN] Milvus not connected. Vector search unavailable.")
                print(f"[HybridRetriever]   Set ZILLIZ_URI/ZILLIZ_TOKEN or MILVUS_HOST/MILVUS_PORT")

    def reload(self):
        """重新加载索引"""
        if self._vector_retriever:
            self._vector_retriever.reload()
        if self._bm25_retriever:
            self._bm25_retriever.reload()
        print("[HybridRetriever] Both indexes reloaded")


# 全局实例
_hybrid_retriever = None


def get_hybrid_retriever() -> HybridVectorRetriever:
    """获取全局混合检索器实例"""
    global _hybrid_retriever
    if _hybrid_retriever is None:
        _hybrid_retriever = HybridVectorRetriever()
    return _hybrid_retriever


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("Testing HybridVectorRetriever...")

    from db_loader import load_knowledge_entries

    # 加载知识库
    entries = load_knowledge_entries()
    print(f"Loaded {len(entries)} entries")

    # 创建检索器
    retriever = HybridVectorRetriever()

    # 构建索引
    retriever.build_index(entries)

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
        results = retriever.retrieve(q, top_k=5)
        elapsed = time.time() - start

        print(f"Found {len(results)} results in {elapsed*1000:.1f}ms")
        for i, r in enumerate(results, 1):
            entry = r.get("entry", {})
            print(f"\n  [{i}] ID={entry.get('id')} Score={r['score']:.4f} "
                  f"(vec={r.get('vector_score', 0):.3f}, bm25={r.get('bm25_score', 0):.3f})")
            print(f"      Q: {entry.get('question', '')[:60]}...")
            print(f"      A: {entry.get('answer', '')[:80]}...")
