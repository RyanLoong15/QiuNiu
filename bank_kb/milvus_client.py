# -*- coding: utf-8 -*-
"""
Milvus Client - 向量数据库操作封装
支持 Zilliz Cloud (托管版) 和自建 Milvus

[WARN] Embedding 生成由 embedding_client.py 负责
   本模块只负责 Milvus 的读写操作
"""

import json
import time
from typing import List, Dict, Any, Optional

from pymilvus import (
    connections,
    Collection,
    CollectionSchema,
    FieldSchema,
    DataType,
    utility,
)

from config import (
    ZILLIZ_URI,
    ZILLIZ_TOKEN,
    EMBEDDING_DIM,
    MILVUS_COLLECTION,
    MILVUS_INDEX_TYPE,
    MILVUS_METRIC_TYPE,
    LOCAL_EMBEDDING_DIM,
    FORCE_LOCAL_EMBEDDING,
)


class MilvusClient:
    """
    Milvus 向量数据库客户端

    功能:
    - 创建/删除 Collection
    - 插入/删除/更新向量
    - 向量检索 (ANN Search)
    - 获取 Collection 统计信息

    注意: Embedding 由外部负责，本类只存/取向量
    """

    def __init__(
        self,
        alias: str = "default",
        uri: str = None,
        token: str = None,
    ):
        self.alias = alias
        self.uri = uri or ZILLIZ_URI
        self.token = token or ZILLIZ_TOKEN
        self._connected = False
        self._collection = None
        self._connect()

    def _connect(self):
        """建立连接"""
        if self._connected:
            return
        try:
            if self.uri and self.token:
                connections.connect(
                    alias=self.alias,
                    uri=self.uri,
                    token=self.token,
                )
                print(f"[MilvusClient] Connected to Zilliz Cloud: {self.uri[:40]}...")
            else:
                from config import MILVUS_HOST, MILVUS_PORT
                connections.connect(
                    alias=self.alias,
                    host=MILVUS_HOST,
                    port=str(MILVUS_PORT),
                )
                print(f"[MilvusClient] Connected to self-hosted Milvus: {MILVUS_HOST}:{MILVUS_PORT}")
            self._connected = True
        except Exception as e:
            print(f"[MilvusClient] Connection failed: {e}")
            self._connected = False

    def is_connected(self) -> bool:
        try:
            return connections.has_connection(self.alias)
        except:
            return False

    # ── Collection 管理 ─────────────────────────────────────────────

    def create_collection(
        self,
        name: str = None,
        dimension: int = None,
        drop_existing: bool = False,
    ) -> bool:
        """
        创建 Collection

        Collection Schema:
        - id: INT64 主键，自增
        - kb_id: INT64，关联 MySQL knowledge_base.id
        - question: VARCHAR(1024)，问题文本（用于召回后展示）
        - answer: VARCHAR(4096)，回答文本
        - category: VARCHAR(256)，分类
        - question_vec: FLOAT_VECTOR，问题向量
        """
        name = name or MILVUS_COLLECTION
        dimension = dimension or (LOCAL_EMBEDDING_DIM if FORCE_LOCAL_EMBEDDING else EMBEDDING_DIM)

        if not self.is_connected():
            print("[MilvusClient] Not connected")
            return False

        try:
            if utility.has_collection(name, using=self.alias):
                if drop_existing:
                    utility.drop_collection(name, using=self.alias)
                    print(f"[MilvusClient] Dropped existing collection: {name}")
                else:
                    print(f"[MilvusClient] Collection already exists: {name}")
                    return True

            fields = [
                FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
                FieldSchema(name="kb_id", dtype=DataType.INT64, description="MySQL knowledge_base.id"),
                FieldSchema(name="question", dtype=DataType.VARCHAR, max_length=1024),
                FieldSchema(name="answer", dtype=DataType.VARCHAR, max_length=4096),
                FieldSchema(name="category", dtype=DataType.VARCHAR, max_length=256),
                FieldSchema(name="question_vec", dtype=DataType.FLOAT_VECTOR, dim=dimension),
            ]

            schema = CollectionSchema(
                fields=fields,
                description="QiuNiu Knowledge Base Vector Index",
                enable_dynamic_field=False,
            )

            collection = Collection(name=name, schema=schema, using=self.alias)
            self._create_index(collection, dimension)
            print(f"[MilvusClient] Created collection: {name}, dim={dimension}")
            return True

        except Exception as e:
            print(f"[MilvusClient] Create collection failed: {e}")
            return False

    def _create_index(self, collection: Collection, dimension: int):
        try:
            index_params = {
                "metric_type": MILVUS_METRIC_TYPE,
                "index_type": MILVUS_INDEX_TYPE,
                "params": {"M": 16, "efConstruction": 128},
            }
            collection.create_index(field_name="question_vec", index_params=index_params)
            print(f"[MilvusClient] Index created: {MILVUS_INDEX_TYPE}({MILVUS_METRIC_TYPE})")
        except Exception as e:
            print(f"[MilvusClient] Create index warning: {e}")

    def load_collection(self, name: str = None) -> bool:
        name = name or MILVUS_COLLECTION
        if not self.is_connected():
            return False
        try:
            collection = Collection(name, using=self.alias)
            collection.load()
            self._collection = collection
            print(f"[MilvusClient] Loaded collection: {name}")
            return True
        except Exception as e:
            print(f"[MilvusClient] Load collection failed: {e}")
            return False

    def drop_collection(self, name: str = None) -> bool:
        name = name or MILVUS_COLLECTION
        if not self.is_connected():
            return False
        try:
            utility.drop_collection(name, using=self.alias)
            print(f"[MilvusClient] Dropped collection: {name}")
            return True
        except Exception as e:
            print(f"[MilvusClient] Drop collection failed: {e}")
            return False

    def get_collection_stats(self, name: str = None) -> int:
        name = name or MILVUS_COLLECTION
        if not self.is_connected():
            return 0
        try:
            collection = Collection(name, using=self.alias)
            return collection.num_entities
        except Exception as e:
            print(f"[MilvusClient] Get stats failed: {e}")
            return 0

    # ── 数据操作 ─────────────────────────────────────────────────────

    def insert(
        self,
        kb_ids: List[int],
        questions: List[str],
        answers: List[str],
        categories: List[str],
        vectors: List[List[float]],
    ) -> List[int]:
        """
        批量插入向量

        Args:
            kb_ids: MySQL knowledge_base.id 列表
            questions: 问题文本列表
            answers: 回答文本列表
            categories: 分类列表
            vectors: 向量列表（由外部 Embedding 服务生成）

        Returns:
            插入的 primary key 列表（自增 ID）
        """
        if not self.is_connected():
            print("[MilvusClient] Not connected, cannot insert")
            return []

        name = MILVUS_COLLECTION
        try:
            collection = Collection(name, using=self.alias)

            # 截断字段长度，防止超长导致插入失败
            q_trunc = [str(q)[:1024] for q in questions]
            a_trunc = [str(a)[:4096] for a in answers]
            c_trunc = [str(c)[:256] for c in categories]

            data = [kb_ids, q_trunc, a_trunc, c_trunc, vectors]
            result = collection.insert(data)
            # collection.flush()  # 批量写入时去掉实时 flush，让 Milvus 自动异步刷盘
            ids = result.primary_keys
            print(f"[MilvusClient] Inserted {len(ids)} vectors")
            return list(ids)
        except Exception as e:
            print(f"[MilvusClient] Insert failed: {e}")
            return []

    def delete_by_kb_id(self, kb_id: int) -> bool:
        if not self.is_connected():
            return False
        try:
            collection = Collection(MILVUS_COLLECTION, using=self.alias)
            expr = f"kb_id == {kb_id}"
            collection.delete(expr)
            collection.flush()
            print(f"[MilvusClient] Deleted kb_id={kb_id}")
            return True
        except Exception as e:
            print(f"[MilvusClient] Delete failed: {e}")
            return False

    def delete_by_kb_ids(self, kb_ids: List[int]) -> bool:
        if not kb_ids:
            return True
        if not self.is_connected():
            return False
        try:
            collection = Collection(MILVUS_COLLECTION, using=self.alias)
            if len(kb_ids) == 1:
                expr = f"kb_id == {kb_ids[0]}"
            else:
                id_str = ", ".join(str(i) for i in kb_ids)
                expr = f"kb_id in [{id_str}]"
            collection.delete(expr)
            collection.flush()
            print(f"[MilvusClient] Deleted {len(kb_ids)} entries")
            return True
        except Exception as e:
            print(f"[MilvusClient] Batch delete failed: {e}")
            return False

    # ── 向量检索 ─────────────────────────────────────────────────────

    def search(
        self,
        query_vectors: List[List[float]],
        top_k: int = 10,
        category: str = None,
        categories: List[str] = None,
        expr: str = None,
    ) -> List[List[Dict]]:
        """
        ANN 向量检索

        Args:
            query_vectors: 查询向量列表（支持批量）
            top_k: 返回数量
            category: 分类过滤（单值，兼容旧接口）
            categories: 分类过滤（列表，多分类）
            expr: 过滤表达式（高级用法）

        Returns:
            List[List[Dict]]: 每个查询向量的命中结果
            每个命中: {"kb_id": int, "question": str, "answer": str,
                      "category": str, "distance": float}
        """
        if not self.is_connected():
            print("[MilvusClient] Not connected, cannot search")
            return [[] for _ in query_vectors]

        name = MILVUS_COLLECTION
        try:
            collection = Collection(name, using=self.alias)

            # 构建过滤表达式
            exprs = []
            # 单分类过滤（兼容旧接口）
            if category:
                exprs.append(f'category == "{category}"')
            # 多分类过滤（新接口）
            if categories and len(categories) > 0:
                category_list = ", ".join(f'"{c}"' for c in categories)
                exprs.append(f'category in [{category_list}]')
            # 自定义过滤表达式
            if expr:
                exprs.append(expr)
            final_expr = " && ".join(exprs) if exprs else None

            search_params = {
                "metric_type": MILVUS_METRIC_TYPE,
                "params": {"ef": 128},
            }

            results = collection.search(
                data=query_vectors,
                anns_field="question_vec",
                param=search_params,
                limit=top_k,
                expr=final_expr,
                output_fields=["kb_id", "question", "answer", "category"],
            )

            # 整理结果
            formatted = []
            for hits in results:
                hits_list = []
                for hit in hits:
                    hits_list.append({
                        "kb_id": hit.entity.get("kb_id"),
                        "question": hit.entity.get("question", ""),
                        "answer": hit.entity.get("answer", ""),
                        "category": hit.entity.get("category", ""),
                        "distance": float(hit.distance),
                    })
                formatted.append(hits_list)
            return formatted

        except Exception as e:
            print(f"[MilvusClient] Search failed: {e}")
            return [[] for _ in query_vectors]

    def search_single(
        self,
        query_vector: List[float],
        top_k: int = 10,
        category: str = None,
        categories: List[str] = None,
    ) -> List[Dict]:
        """搜索单条向量（便捷方法）"""
        results = self.search([query_vector], top_k=top_k, category=category, categories=categories)
        return results[0] if results else []

    # ── 工具方法 ─────────────────────────────────────────────────────

    def has_collection(self, name: str = None) -> bool:
        name = name or MILVUS_COLLECTION
        if not self.is_connected():
            return False
        try:
            return utility.has_collection(name, using=self.alias)
        except:
            return False

    def close(self):
        try:
            connections.disconnect(alias=self.alias)
            self._connected = False
            print("[MilvusClient] Disconnected")
        except Exception as e:
            print(f"[MilvusClient] Disconnect failed: {e}")


# ── 全局单例 ─────────────────────────────────────────────────────────
_milvus_client = None


def get_milvus_client() -> MilvusClient:
    global _milvus_client
    if _milvus_client is None:
        _milvus_client = MilvusClient()
    return _milvus_client


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("Testing MilvusClient...")

    client = get_milvus_client()

    if not client.is_connected():
        print("[FAIL] Cannot connect to Milvus.")
        print("   请检查 ZILLIZ_URI/ZILLIZ_TOKEN 或 MILVUS_HOST/MILVUS_PORT")
        print("   自建 Milvus 默认: localhost:19530")
    else:
        print(f"[OK] Connected! Collection exists: {client.has_collection()}")
        stats = client.get_collection_stats()
        print(f"   Entities: {stats}")
