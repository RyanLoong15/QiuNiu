# -*- coding: utf-8 -*-
"""
Reindex Worker - 向量重索引 + 重试队列管理

功能：
1. trigger_vector_reindex(): KB 条目 → Embedding → Milvus
2. enqueue_retry(): 失败时写入 JSON 重试队列
3. process_retry_queue(): 定时处理重试队列（最多 3 次）

注意：
- Milvus 使用 Zilliz Cloud 云端托管，数据已持久化，不需要本地 dump
- 重试队列使用本地 JSON 文件存储，防止索引失败导致数据丢失

使用方式：
    from reindex_worker import trigger_vector_reindex, enqueue_retry, process_retry_queue
    
    # 对 KB 条目进行向量索引
    result = trigger_vector_reindex(kb_entry_id=123)
    
    # 失败时写入重试队列
    if not result["success"]:
        enqueue_retry(kb_entry_id=123, unanswered_id=45)
    
    # 定时处理重试队列（Flask 启动时启动后台线程）
    start_retry_queue_processor(interval_seconds=60)
"""

import os
import json
import time
import threading
from typing import Dict, Any, Optional

from config import (
    BASE_DIR,
    DEBUG,
)

# 全局 EmbeddingClient / MilvusClient 实例（复用，避免重复初始化）
_global_embedder = None
_global_milvus = None


def _get_embedder():
    global _global_embedder
    if _global_embedder is None:
        from embedding_client import EmbeddingClient
        _global_embedder = EmbeddingClient()
    return _global_embedder


def _get_milvus():
    global _global_milvus
    if _global_milvus is None:
        from milvus_client import MilvusClient
        _global_milvus = MilvusClient()
    return _global_milvus

# 常量
RETRY_QUEUE_FILE = os.path.join(BASE_DIR, "data", "reindex_retry_queue.json")
MAX_RETRY_COUNT = 3
RETRY_INTERVAL_SECONDS = 60  # 重试队列处理间隔


# ── KB 条目查询（临时实现，Step-20 后可改用 db_loader）─────────────────────────────

def _get_kb_entry_by_id(kb_entry_id: int) -> Optional[Dict[str, Any]]:
    """
    根据 ID 获取单条 KB 条目。
    
    临时实现，直接查询 MySQL。
    Step-20 完成后可改用 db_loader.get_kb_entry_by_id()。
    
    Args:
        kb_entry_id: KB 条目 ID
    
    Returns:
        {"id": int, "question": str, "answer": str, "category": str} 或 None
    """
    import mysql.connector
    from config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE
    
    try:
        conn = mysql.connector.connect(
            host=MYSQL_HOST,
            port=MYSQL_PORT,
            user=MYSQL_USER,
            password=MYSQL_PASSWORD,
            database=MYSQL_DATABASE,
            charset="utf8mb4",
        )
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, question, answer, category "
            "FROM knowledge_base "
            "WHERE id = %s AND enabled = 1",
            (kb_entry_id,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        if DEBUG:
            print(f"[ReindexWorker] Failed to get KB entry {kb_entry_id}: {e}")
        return None


# ── 核心功能 ───────────────────────────────────────────────────────────────────────

def trigger_vector_reindex(
    kb_entry_id: int,
    unanswered_id: Optional[int] = None,
) -> Dict[str, Any]:
    """
    对 KB 条目进行向量重索引。
    
    流程：
    1. 从 MySQL 获取 KB 条目
    2. 使用 EmbeddingClient 生成 embedding
    3. 插入到 Milvus
    
    Args:
        kb_entry_id: KB 条目 ID（MySQL knowledge_base.id）
        unanswered_id: 来源的 unanswered_questions ID（可选，用于追踪）
    
    Returns:
        {
            "success": bool,
            "kb_entry_id": int,
            "milvus_ids": List[int],  # 插入的 Milvus primary keys
            "unanswered_id": Optional[int],
            "error": Optional[str]
        }
    """
   
    start = time.time()
    
    # 1. 获取 KB 条目
    kb_entry = _get_kb_entry_by_id(kb_entry_id)
    if not kb_entry:
        return {
            "success": False,
            "kb_entry_id": kb_entry_id,
            "milvus_ids": [],
            "unanswered_id": unanswered_id,
            "error": f"KB entry {kb_entry_id} not found or disabled",
        }
    
    question = kb_entry.get("question", "")
    answer = kb_entry.get("answer", "")
    category = kb_entry.get("category", "")
    
    if not question:
        return {
            "success": False,
            "kb_entry_id": kb_entry_id,
            "milvus_ids": [],
            "unanswered_id": unanswered_id,
            "error": "KB entry has empty question",
        }
    
    # 2. 生成 Embedding
    try:
        embedder = _get_embedder()
        vector = embedder.encode_single(question)
        
        if not vector or len(vector) == 0:
            return {
                "success": False,
                "kb_entry_id": kb_entry_id,
                "milvus_ids": [],
                "unanswered_id": unanswered_id,
                "error": "Embedding generation failed",
            }
    
    except Exception as e:
        return {
            "success": False,
            "kb_entry_id": kb_entry_id,
            "milvus_ids": [],
            "unanswered_id": unanswered_id,
            "error": f"Embedding error: {str(e)[:100]}",
        }
    
    # 3. 插入到 Milvus
    try:
        milvus = _get_milvus()
        
        if not milvus.is_connected():
            # 尝试重新连接
            milvus._connect()
        
        if not milvus.is_connected():
            return {
                "success": False,
                "kb_entry_id": kb_entry_id,
                "milvus_ids": [],
                "unanswered_id": unanswered_id,
                "error": "Milvus not connected",
            }
        
        milvus_ids = milvus.insert(
            kb_ids=[kb_entry_id],
            questions=[question],
            answers=[answer],
            categories=[category],
            vectors=[vector],
        )
        
        if not milvus_ids:
            return {
                "success": False,
                "kb_entry_id": kb_entry_id,
                "milvus_ids": [],
                "unanswered_id": unanswered_id,
                "error": "Milvus insert returned empty",
            }
        
        latency_ms = int((time.time() - start) * 1000)
        
        if DEBUG:
            print(f"[ReindexWorker] Reindexed kb_id={kb_entry_id} to Milvus id={milvus_ids[0]}, latency={latency_ms}ms")
        
        return {
            "success": True,
            "kb_entry_id": kb_entry_id,
            "milvus_ids": milvus_ids,
            "unanswered_id": unanswered_id,
            "error": None,
        }
    
    except Exception as e:
        return {
            "success": False,
            "kb_entry_id": kb_entry_id,
            "milvus_ids": [],
            "unanswered_id": unanswered_id,
            "error": f"Milvus error: {str(e)[:100]}",
        }


# ── 重试队列 ────────────────────────────────────────────────────────────────────────

def _ensure_retry_queue_file() -> None:
    """确保重试队列文件存在。"""
    data_dir = os.path.dirname(RETRY_QUEUE_FILE)
    if not os.path.exists(data_dir):
        os.makedirs(data_dir, exist_ok=True)
    
    if not os.path.exists(RETRY_QUEUE_FILE):
        with open(RETRY_QUEUE_FILE, "w", encoding="utf-8") as f:
            json.dump({"pending": [], "failed": []}, f, ensure_ascii=False)


def enqueue_retry(
    kb_entry_id: int,
    unanswered_id: Optional[int] = None,
    error: Optional[str] = None,
) -> bool:
    """
    将失败的 KB 条目写入重试队列。
    
    Args:
        kb_entry_id: KB 条目 ID
        unanswered_id: 来源的 unanswered_questions ID
        error: 错误信息
    
    Returns:
        是否写入成功
    """
    _ensure_retry_queue_file()
    
    try:
        with open(RETRY_QUEUE_FILE, "r", encoding="utf-8") as f:
            queue_data = json.load(f)
        
        # 检查是否已在队列中
        pending = queue_data.get("pending", [])
        for item in pending:
            if item.get("kb_entry_id") == kb_entry_id:
                if DEBUG:
                    print(f"[ReindexWorker] kb_id={kb_entry_id} already in retry queue")
                return True
        
        # 添加到队列
        pending.append({
            "kb_entry_id": kb_entry_id,
            "unanswered_id": unanswered_id,
            "error": error,
            "retry_count": 0,
            "created_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        })
        
        queue_data["pending"] = pending
        
        with open(RETRY_QUEUE_FILE, "w", encoding="utf-8") as f:
            json.dump(queue_data, f, ensure_ascii=False, indent=2)
        
        if DEBUG:
            print(f"[ReindexWorker] Enqueued kb_id={kb_entry_id} for retry")
        
        return True
    
    except Exception as e:
        if DEBUG:
            print(f"[ReindexWorker] Failed to enqueue: {e}")
        return False


def _update_retry_queue(queue_data: Dict[str, Any]) -> bool:
    """更新重试队列文件。"""
    try:
        with open(RETRY_QUEUE_FILE, "w", encoding="utf-8") as f:
            json.dump(queue_data, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        if DEBUG:
            print(f"[ReindexWorker] Failed to update queue file: {e}")
        return False


def process_retry_queue() -> Dict[str, Any]:
    """
    处理重试队列中的所有待重试条目。
    
    对每个条目：
    1. 调用 trigger_vector_reindex()
    2. 成功：从 pending 移除
    3. 失败：retry_count += 1，达到 MAX_RETRY_COUNT 移到 failed
    
    Returns:
        {"processed": int, "success": int, "failed": int, "remaining": int}
    """
    _ensure_retry_queue_file()
    
    try:
        with open(RETRY_QUEUE_FILE, "r", encoding="utf-8") as f:
            queue_data = json.load(f)
        
        pending = queue_data.get("pending", [])
        failed = queue_data.get("failed", [])
        
        processed = 0
        success_count = 0
        failed_count = 0
        
        items_to_remove = []
        items_to_fail = []
        
        for item in pending:
            kb_entry_id = item.get("kb_entry_id")
            unanswered_id = item.get("unanswered_id")
            retry_count = item.get("retry_count", 0)
            
            # 尝试重索引
            result = trigger_vector_reindex(kb_entry_id, unanswered_id)
            
            processed += 1
            
            if result["success"]:
                items_to_remove.append(item)
                success_count += 1
                if DEBUG:
                    print(f"[ReindexWorker] Retry success: kb_id={kb_entry_id}")
            else:
                retry_count += 1
                item["retry_count"] = retry_count
                item["last_error"] = result["error"]
                item["last_retry_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
                
                if retry_count >= MAX_RETRY_COUNT:
                    items_to_fail.append(item)
                    failed_count += 1
                    if DEBUG:
                        print(f"[ReindexWorker] Retry failed after {retry_count} attempts: kb_id={kb_entry_id}")
                else:
                    if DEBUG:
                        print(f"[ReindexWorker] Retry failed (attempt {retry_count}): kb_id={kb_entry_id}, error={result['error']}")
        
        # 更新队列数据
        for item in items_to_remove:
            pending.remove(item)
        
        for item in items_to_fail:
            pending.remove(item)
            item["final_error"] = item.get("last_error")
            item["failed_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
            failed.append(item)
        
        queue_data["pending"] = pending
        queue_data["failed"] = failed
        
        _update_retry_queue(queue_data)
        
        return {
            "processed": processed,
            "success": success_count,
            "failed": failed_count,
            "remaining": len(pending),
        }
    
    except Exception as e:
        if DEBUG:
            print(f"[ReindexWorker] Process retry queue error: {e}")
        return {
            "processed": 0,
            "success": 0,
            "failed": 0,
            "remaining": 0,
            "error": str(e),
        }


# ── 后台线程处理器 ────────────────────────────────────────────────────────────────────

_retry_processor_thread = None
_retry_processor_running = False


def _retry_queue_loop(interval_seconds: int):
    """后台线程循环处理重试队列。"""
    global _retry_processor_running
    
    while _retry_processor_running:
        try:
            result = process_retry_queue()
            if DEBUG and result["processed"] > 0:
                print(f"[ReindexWorker] Retry queue processed: {result}")
        
        except Exception as e:
            if DEBUG:
                print(f"[ReindexWorker] Retry loop error: {e}")
        
        time.sleep(interval_seconds)


def start_retry_queue_processor(interval_seconds: int = RETRY_INTERVAL_SECONDS) -> bool:
    """
    启动后台线程定期处理重试队列。
    
    Args:
        interval_seconds: 处理间隔（秒）
    
    Returns:
        是否启动成功
    """
    global _retry_processor_thread, _retry_processor_running
    
    if _retry_processor_thread and _retry_processor_thread.is_alive():
        if DEBUG:
            print("[ReindexWorker] Retry processor already running")
        return True
    
    _retry_processor_running = True
    _retry_processor_thread = threading.Thread(
        target=_retry_queue_loop,
        args=(interval_seconds,),
        daemon=True,
    )
    _retry_processor_thread.start()
    
    if DEBUG:
        print(f"[ReindexWorker] Started retry queue processor (interval={interval_seconds}s)")
    
    return True


def stop_retry_queue_processor() -> bool:
    """停止后台重试队列处理器。"""
    global _retry_processor_running
    
    _retry_processor_running = False
    
    if DEBUG:
        print("[ReindexWorker] Stopped retry queue processor")
    
    return True


# ── 状态查询 ───────────────────────────────────────────────────────────────────────────

def get_retry_queue_status() -> Dict[str, Any]:
    """获取重试队列状态。"""
    _ensure_retry_queue_file()
    
    try:
        with open(RETRY_QUEUE_FILE, "r", encoding="utf-8") as f:
            queue_data = json.load(f)
        
        pending = queue_data.get("pending", [])
        failed = queue_data.get("failed", [])
        
        return {
            "pending_count": len(pending),
            "failed_count": len(failed),
            "pending_items": pending,
            "failed_items": failed,
        }
    
    except Exception as e:
        return {
            "pending_count": 0,
            "failed_count": 0,
            "error": str(e),
        }


# ── 测试入口 ───────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("[ReindexWorker] Testing...")
    
    # 测试重试队列状态
    status = get_retry_queue_status()
    print(f"[ReindexWorker] Queue status: pending={status['pending_count']}, failed={status['failed_count']}")
    
    # 测试处理重试队列
    if status["pending_count"] > 0:
        print("[ReindexWorker] Processing retry queue...")
        result = process_retry_queue()
        print(f"[ReindexWorker] Result: {result}")
    
    # 启动后台处理器
    print("[ReindexWorker] Starting background processor...")
    start_retry_queue_processor(interval_seconds=10)
    
    # 等待一会儿
    time.sleep(3)
    
    # 停止
    stop_retry_queue_processor()
    
    print("[ReindexWorker] Test complete")