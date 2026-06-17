# -*- coding: utf-8 -*-
"""
Migrate to Milvus - 将知识库数据从 MySQL 迁移到 Milvus
支持 Zilliz Cloud (托管版) 和自建 Milvus
支持全量迁移和增量迁移
"""

import sys
import time
import json
import argparse
from typing import List, Dict

# 确保能导入本地模块
sys.path.insert(0, '.')

from db_loader import load_knowledge_entries, get_connection
from vector_retriever import VectorRetriever
from embedding_service import get_embedding_service
from config import (
    MILVUS_COLLECTION, 
    EMBEDDING_DIM,
    ZILLIZ_URI, ZILLIZ_TOKEN,
)


def get_last_migrated_id() -> int:
    """获取已迁移的最后一条记录 ID（从本地文件读取）"""
    import os
    marker_file = ".milvus_migration_marker"
    if os.path.exists(marker_file):
        with open(marker_file, 'r', encoding='utf-8') as f:
            return int(f.read().strip())
    return 0


def save_migration_marker(last_id: int):
    """保存迁移进度标记"""
    with open(".milvus_migration_marker", 'w', encoding='utf-8') as f:
        f.write(str(last_id))


def clear_migration_marker():
    """清除迁移进度标记"""
    import os
    marker_file = ".milvus_migration_marker"
    if os.path.exists(marker_file):
        os.remove(marker_file)


def fetch_entries_from_mysql(last_id: int = 0, limit: int = None) -> List[Dict]:
    """
    从 MySQL 读取知识库数据

    Args:
        last_id: 上次迁移的最后 ID（用于增量迁移）
        limit: 限制读取数量（用于测试）

    Returns:
        知识库条目列表
    """
    conn = get_connection()
    cursor = conn.cursor()

    query = "SELECT id, question, answer, category FROM knowledge_base WHERE enabled = 1"
    if last_id > 0:
        query += f" AND id > {last_id}"
    query += " ORDER BY id ASC"

    if limit:
        query += f" LIMIT {limit}"

    cursor.execute(query)
    rows = cursor.fetchall()

    entries = []
    for row in rows:
        entries.append({
            "id": row[0],
            "question": row[1] or "",
            "answer": row[2] or "",
            "category": row[3] or "未分类"
        })

    cursor.close()
    conn.close()

    return entries


def migrate_full(retriever: VectorRetriever, batch_size: int = 50):
    """
    全量迁移：删除现有集合并重新导入所有数据

    Args:
        retriever: VectorRetriever 实例
        batch_size: 批量处理大小 (API 最大 20)
    """
    print("\n" + "="*60)
    print("[Migration] Starting FULL migration")
    print("="*60)

    start_time = time.time()

    # 1. 创建新集合
    print("[Migration] Creating collection...")
    retriever.create_collection(dim=EMBEDDING_DIM, drop_existing=True)

    # 2. 读取所有数据
    print("[Migration] Fetching entries from MySQL...")
    entries = fetch_entries_from_mysql()

    if not entries:
        print("[Migration] No entries to migrate")
        return

    print(f"[Migration] Total entries to migrate: {len(entries)}")

    # 3. 批量插入
    total = len(entries)
    imported = 0
    
    # 由于 Embedding API 最大批量 20，这里限制 batch_size
    actual_batch_size = min(batch_size, 20)
    
    for i in range(0, total, actual_batch_size):
        batch = entries[i:i + actual_batch_size]
        count = retriever.insert(batch)
        imported += count
        
        progress = min(i + actual_batch_size, total)
        pct = progress / total * 100
        print(f"[Migration] Progress: {progress}/{total} ({pct:.1f}%)")

    # 4. 加载到内存
    retriever.collection.load()

    # 5. 保存标记
    if entries:
        save_migration_marker(entries[-1]["id"])

    elapsed = int(time.time() - start_time)
    print("\n" + "="*60)
    print(f"[Migration] ✅ FULL migration completed!")
    print(f"   Total entries: {imported}")
    print(f"   Time: {elapsed}s")
    print(f"   Speed: {imported/max(elapsed, 1):.1f} entries/s")
    print("="*60 + "\n")


def migrate_incremental(retriever: VectorRetriever, batch_size: int = 50):
    """
    增量迁移：只迁移新增的数据

    Args:
        retriever: VectorRetriever 实例
        batch_size: 批量处理大小
    """
    print("\n" + "="*60)
    print("[Migration] Starting INCREMENTAL migration")
    print("="*60)

    start_time = time.time()

    # 1. 检查集合是否存在
    if retriever.collection is None:
        print("[Migration] Collection not found, creating...")
        retriever.create_collection(dim=EMBEDDING_DIM)

    # 2. 获取上次迁移的位置
    last_id = get_last_migrated_id()
    print(f"[Migration] Last migrated ID: {last_id}")

    # 3. 读取新增数据
    entries = fetch_entries_from_mysql(last_id=last_id)

    if not entries:
        print("[Migration] No new entries to migrate")
        return

    print(f"[Migration] New entries: {len(entries)}")

    # 4. 批量插入
    total = len(entries)
    actual_batch_size = min(batch_size, 20)
    
    for i in range(0, total, actual_batch_size):
        batch = entries[i:i + actual_batch_size]
        count = retriever.insert(batch)
        
        progress = min(i + actual_batch_size, total)
        print(f"[Migration] Progress: {progress}/{total}")

    # 5. 保存标记
    save_migration_marker(entries[-1]["id"])

    elapsed = int(time.time() - start_time)
    print("\n" + "="*60)
    print(f"[Migration] ✅ INCREMENTAL migration completed!")
    print(f"   New entries: {total}")
    print(f"   Time: {elapsed}s")
    print("="*60 + "\n")


def reset_migration():
    """重置迁移状态（删除 Milvus 集合和标记文件）"""
    print("\n" + "="*60)
    print("[Migration] RESET - Deleting Milvus collection and marker")
    print("="*60)

    from pymilvus import connections, utility

    # 连接
    if ZILLIZ_URI and ZILLIZ_TOKEN:
        connections.connect(alias="default", uri=ZILLIZ_URI, token=ZILLIZ_TOKEN)
    else:
        from config import MILVUS_HOST, MILVUS_PORT
        connections.connect(alias="default", host=MILVUS_HOST, port=str(MILVUS_PORT))

    # 删除集合
    if utility.has_collection(MILVUS_COLLECTION):
        utility.drop_collection(MILVUS_COLLECTION)
        print(f"[Migration] Dropped collection: {MILVUS_COLLECTION}")
    else:
        print(f"[Migration] Collection not found: {MILVUS_COLLECTION}")

    # 删除标记文件
    clear_migration_marker()
    print("[Migration] Cleared migration marker")

    connections.disconnect("default")
    print("="*60 + "\n")


def show_status():
    """显示迁移状态"""
    print("\n" + "="*60)
    print("[Migration] Status Check")
    print("="*60)

    # MySQL 数量
    entries = fetch_entries_from_mysql()
    mysql_count = len(entries)
    print(f"MySQL entries: {mysql_count}")

    # Milvus 数量
    try:
        from pymilvus import connections, utility, Collection

        if ZILLIZ_URI and ZILLIZ_TOKEN:
            connections.connect(alias="default", uri=ZILLIZ_URI, token=ZILLIZ_TOKEN)
            print(f"Milvus: Zilliz Cloud ({ZILLIZ_URI[:30]}...)")
        else:
            from config import MILVUS_HOST, MILVUS_PORT
            connections.connect(alias="default", host=MILVUS_HOST, port=str(MILVUS_PORT))
            print(f"Milvus: Self-hosted ({MILVUS_HOST}:{MILVUS_PORT})")

        if utility.has_collection(MILVUS_COLLECTION):
            collection = Collection(MILVUS_COLLECTION)
            collection.load()
            milvus_count = collection.num_entities
            print(f"Milvus collection: {MILVUS_COLLECTION}")
            print(f"Milvus entities: {milvus_count}")

            if milvus_count < mysql_count:
                print(f"⚠️  Missing: {mysql_count - milvus_count} entries (run incremental migration)")
            elif milvus_count > mysql_count:
                print(f"⚠️  Extra: {milvus_count - mysql_count} entries (MySQL may have deletions)")
            else:
                print("✅ Synced!")
        else:
            print(f"Milvus collection: NOT FOUND")
            print(f"⚠️  Run 'python migrate_to_milvus.py --mode full' to create")

        connections.disconnect("default")

    except Exception as e:
        print(f"Milvus: ERROR - {e}")
        print(f"⚠️  Check ZILLIZ_URI/ZILLIZ_TOKEN or MILVUS_HOST/MILVUS_PORT")

    # 迁移标记
    last_id = get_last_migrated_id()
    if last_id > 0:
        print(f"Migration marker: last_id={last_id}")
    else:
        print("Migration marker: none")

    print("="*60 + "\n")


def test_retrieval(retriever: VectorRetriever, queries: List[str] = None):
    """
    测试检索功能

    Args:
        retriever: VectorRetriever 实例
        queries: 测试查询列表
    """
    if queries is None:
        queries = [
            "如何查询账户余额",
            "核心系统是什么",
            "贷款利率是多少",
            "银行活期存款利率",
            "定期存款和活期存款区别",
        ]

    print("\n" + "="*60)
    print("[Test] Testing Vector Retrieval")
    print("="*60)

    for q in queries:
        print(f"\n[Query] {q}")
        results = retriever.retrieve(q, top_k=3)
        print(f"[Results] {len(results)} found:")
        for i, r in enumerate(results, 1):
            entry = r.get("entry", {})
            print(f"  {i}. [{entry.get('category')}] "
                  f"{entry.get('question', '')[:50]}... "
                  f"(score={r['score']:.4f})")

    print("\n" + "="*60)


def main():
    parser = argparse.ArgumentParser(
        description="Migrate knowledge base to Milvus",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python migrate_to_milvus.py --mode full           # 全量迁移
  python migrate_to_milvus.py --mode incremental     # 增量迁移
  python migrate_to_milvus.py --mode status           # 查看状态
  python migrate_to_milvus.py --mode reset           # 重置迁移
  python migrate_to_milvus.py --mode test             # 测试检索
  python migrate_to_milvus.py --mode full --batch 10  # 小批量测试
        """
    )
    parser.add_argument(
        "--mode",
        choices=["full", "incremental", "status", "reset", "test"],
        default="status",
        help="Migration mode (default: status)"
    )
    parser.add_argument(
        "--batch-size", 
        type=int, 
        default=20,
        help="Batch size for migration (default: 20, max recommended: 20)"
    )
    parser.add_argument(
        "--collection", 
        type=str, 
        default=None,
        help="Milvus collection name (default: from config)"
    )

    args = parser.parse_args()

    if args.mode == "status":
        show_status()
        return

    if args.mode == "reset":
        reset_migration()
        return

    # 初始化检索器
    retriever = VectorRetriever(collection_name=args.collection)

    if args.mode == "full":
        migrate_full(retriever, batch_size=args.batch_size)
        test_retrieval(retriever)
    elif args.mode == "incremental":
        migrate_incremental(retriever, batch_size=args.batch_size)
    elif args.mode == "test":
        if not retriever.is_connected():
            print("ERROR: Not connected to Milvus")
            return
        test_retrieval(retriever)


if __name__ == "__main__":
    main()
