# -*- coding: utf-8 -*-
"""最简测试：不触发大模型加载"""
import sys
sys.path.insert(0, '.')

print("=" * 60)
print("Simple Test (no large model loading)")
print("=" * 60)

# 1. config
print("\n[1] Config...")
try:
    from config import RETRIEVAL_MODE, EMBEDDING_DIM
    print(f"  OK: mode={RETRIEVAL_MODE}, dim={EMBEDDING_DIM}")
except Exception as e:
    print(f"  FAIL: {e}")

# 2. db_loader
print("\n[2] DB Loader...")
try:
    from db_loader import get_entries_count
    count = get_entries_count()
    print(f"  OK: MySQL entries={count}")
except Exception as e:
    print(f"  FAIL: {e}")

# 3. embedding_client (应该延迟加载，不触发 sentence-transformers)
print("\n[3] EmbeddingClient (deferred init)...")
try:
    from embedding_client import get_embedding_client
    ec = get_embedding_client()
    s = ec.get_status()
    print(f"  OK: method={s['method']}, dim={s['dimension']}")
except Exception as e:
    print(f"  FAIL: {e}")

# 4. BM25 retriever
print("\n[4] BM25 Retriever...")
try:
    from retriever import HybridRetriever as BM25Retriever
    bm25 = BM25Retriever()
    print(f"  OK: doc_count={bm25.doc_count}")
    # 测试一次检索
    results = bm25.retrieve("银行", top_k=2)
    print(f"  OK: retrieve test -> {len(results)} results")
except Exception as e:
    print(f"  FAIL: {e}")

# 5. HybridSearchEngine (不强制加载向量)
print("\n[5] HybridSearchEngine...")
try:
    from hybrid_search import get_hybrid_search
    engine = get_hybrid_search()
    s = engine.get_status()
    print(f"  OK: mode={s['mode']}, vector_avail={s['vector_available']}")
except Exception as e:
    print(f"  FAIL: {e}")

print("\n" + "=" * 60)
print("Tests completed. Check results above.")
print("=" * 60)
