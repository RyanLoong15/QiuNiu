# -*- coding: utf-8 -*-
"""
Milvus Client - 向量数据库操作封装
支持 Zilliz Cloud (托管版) 和自建 Milvus
"""

import json
import time
from typing import List, Dict, Any, Optional, Union

from pymilvus import (
    connections,
    Collection,
    CollectionSchema,
    FieldSchema,
    DataType,
    utility,
    milvus_pymilvus,
)

from config import (
    ZILLIZ_URI,
    ZILLIZ_TOKEN,
    EMBEDDING_DIM,
    COLLECTION_NAME,
    MILVUS_INDEX_TYPE,
    MILVUS_METRIC_TYPE,
)


class MilvusClient:
    """
    Milvus 向量数据库客户端

    功能:
    - 创建/删除 Collection
    - 插入/删除/更新向量
    - 向量检索 (ANN Search)
    - 获取 Collection 统计信息
    """

    def __init__(
        self,
        alias: str = "default",
        uri: str = None,
        token: str = None,
    ):
        """
        Args:
            alias: 连接别名
            uri: Milvus 连接 URI (Zilliz Cloud 或自建)
            token: API Token (Zilliz Cloud)
        """
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
            # Zilliz Cloud 使用 URI + Token
            if self.uri and self.token:
                connections.connect(
                    alias=self.alias,
                    uri=self.uri,
                    token=self.token,
                )
            else:
                # 自建 Milvus
                connections.connect(
                    alias=self.alias,
                    host="localhost",
                    port="19530",
                )

            self._connected = True
            print(f"[MilvusClient] Connected to {self.uri}")

        except Exception as e:
            print(f"[MilvusClient] Connection failed: {e}")
            self._connected = False

    def is_connected(self) -> bool:
        """检查连接状态"""
        try:
            return connections.has_connection(self.alias)
        except:
            return False

    # ── Collection 管理 ───────────────────────────────────────────────

    def create_collection(
        self,
        name: str = None,
        dimension: int = None,
        drop_existing: bool = False,
    ) -> bool:
        """
        创建 Collection

        Args:
            name: Collection 名称
            dimension: 向量维度
            drop_existing: 如果存在是否删除重建

        Returns:
            是否成功
        """
        name = name or COLLECTION_NAME
        dimension = dimension or EMBEDDING_DIM

        if not self.is_connected():
            print("[MilvusClient] Not connected")
            return False

        try:
            # 检查是否已存在
            if utility.has_collection(name, using=self.alias):
                if drop_existing:
                    utility.drop_collection(name, using=self.alias)
                    print(f"[MilvusClient] Dropped existing collection: {name}")
                else:
                    print(f"[MilvusClient] Collection already exists: {name}")
                    return True

            # 定义 Schema
            fields = [
                # 主键，自动递增
                FieldSchema(
                    name="id",
                    dtype=DataType.INT64,
                    is_primary=True,
                    auto_id=True,
                ),
                # 关联 MySQL knowledge_base.id
                FieldSchema(
                    name="kb_id",
                    dtype=DataType.INT64,
                    description="MySQL knowledge_base.id",
                ),
                # 问题向量 (question embedding)
                FieldSchema(
                    name="question_vec",
                    dtype=DataType.FLOAT_VECTOR,
                    dim=dimension,
                ),
                # 回答向量 (answer embedding) - 可选，用于更精细检索
                FieldSchema(
                    name="answer_vec",
                    dtype=DataType.FLOAT_VECTOR,
                    dim=dimension,
                ),
                # 元数据 (JSON 字符串)
                FieldSchema(
                    name="metadata",
                    dtype=DataType.VARCHAR,
                    max_length=4000,
                    description="JSON metadata",
                ),
                # 分类标签
                FieldSchema(
                    name="category",
                    dtype=DataType.VARCHAR,
                    max_length=100,
                    description="Knowledge category",
                ),
            ]

            schema = CollectionSchema(
                fields=fields,
                description="QiuNiu Knowledge Base Vector Index",
                enable_dynamic_field=False,
            )

            # 创建 Collection
            collection = Collection(
                name=name,
                schema=schema,
                using=self.alias,
            )

            # 创建索引
            self._create_index(collection, dimension)

            print(f"[MilvusClient] Created collection: {name}")
            return True

        except Exception as e:
            print(f"[MilvusClient] Create collection failed: {e}")
            return False

    def _create_index(self, collection: Collection, dimension: int):
        """为 Collection 创建索引"""
        try:
            # 问题向量索引 (HNSW)
            q_index_params = {
                "metric_type": MILVUS_METRIC_TYPE,
                "index_type": MILVUS_INDEX_TYPE,
                "params": {"M": 16, "efConstruction": 128},
            }
            q_index = {
                "index_type": MILVUS_INDEX_TYPE,
                "metric_type": MILVUS_METRIC_TYPE,
                "params": {"M": 16, "efConstruction": 128},
            }
            collection.create_index(
                field_name="question_vec",
                index_params=q_index,
            )

            # 回答向量索引 (HNSW)
            a_index = {
                "index_type": MILVUS_INDEX_TYPE,
                "metric_type": MILVUS_METRIC_TYPE,
                "params": {"M": 16, "efConstruction": 128},
            }
            collection.create_index(
                field_name="answer_vec",
                index_params=a_index,
            )

            print(f"[MilvusClient] Index created (HNSW, {MILVUS_METRIC_TYPE})")

        except Exception as e:
            print(f"[MilvusClient] Create index warning: {e}")

    def load_collection(self, name: str = None) -> bool:
        """加载 Collection 到内存"""
        name = name or COLLECTION_NAME

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
        """删除 Collection"""
        name = name or COLLECTION_NAME

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
        """获取 Collection 中的向量数量"""
        name = name or COLLECTION_NAME

        if not self.is_connected():
            return 0

        try:
            collection = Collection(name, using=self.alias)
            stats = collection.num_entities
            return stats
        except Exception as e:
            print(f"[MilvusClient] Get stats failed: {e}")
            return 0

    # ── 数据操作 ─────────────────────────────────────────────────────

    def insert(
        self,
        kb_ids: List[int],
        question_vecs: List[List[float]],
        answer_vecs: List[List[float]],
        categories: List[str],
        metadatas: List[str] = None,
        collection_name: str = None,
    ) -> List[int]:
        """
        批量插入向量

        Args:
            kb_ids: MySQL knowledge_base.id 列表
            question_vecs: 问题向量列表
            answer_vecs: 回答向量列表
            categories: 分类列表
            metadatas: 元数据 JSON 字符串列表
            collection_name: Collection 名称

        Returns:
            插入的 ID 列表
        """
        collection_name = collection_name or COLLECTION_NAME

        if not self.is_connected():
            print("[MilvusClient] Not connected, cannot insert")
            return []

        try:
            collection = Collection(collection_name, using=self.alias)

            # 准备数据
            if metadatas is None:
                metadatas = ["{}"] * len(kb_ids)

            data = [
                kb_ids,               # id (auto_id)
                question_vecs,        # question_vec
                answer_vecs,          # answer_vec
                metadatas,           # metadata
                categories,           # category
            ]

            # 插入
            result = collection.insert(data)
            collection.flush()

            ids = result.primary_keys
            print(f"[MilvusClient] Inserted {len(ids)} vectors")
            return ids

        except Exception as e:
            print(f"[MilvusClient] Insert failed: {e}")
            return []

    def delete_by_kb_id(self, kb_id: int, collection_name: str = None) -> bool:
        """根据 kb_id 删除向量"""
        collection_name = collection_name or COLLECTION_NAME

        if not self.is_connected():
            return False

        try:
            collection = Collection(collection_name, using=self.alias)
            expr = f"kb_id == {kb_id}"
            collection.delete(expr)
            collection.flush()
            print(f"[MilvusClient] Deleted kb_id={kb_id}")
            return True
        except Exception as e:
            print(f"[MilvusClient] Delete failed: {e}")
            return False

    def upsert(
        self,
        kb_id: int,
        question_vec: List[float],
        answer_vec: List[float],
        category: str,
        metadata: dict = None,
        collection_name: str = None,
    ) -> bool:
        """
        插入或更新向量 (先删后插)

        Args:
            kb_id: MySQL knowledge_base.id
            question_vec: 问题向量
            answer_vec: 回答向量
            category: 分类
            metadata: 元数据字典
            collection_name: Collection 名称

        Returns:
            是否成功
        """
        collection_name = collection_name or COLLECTION_NAME

        # 先删除
        self.delete_by_kb_id(kb_id, collection_name)

        # 再插入
        metadata_str = json.dumps(metadata or {}, ensure_ascii=False)
        ids = self.insert(
            kb_ids=[kb_id],
            question_vecs=[question_vec],
            answer_vecs=[answer_vec],
            categories=[category],
            metadatas=[metadata_str],
            collection_name=collection_name,
        )

        return len(ids) > 0

    def delete_collection(self, collection_name: str = None) -> bool:
        """删除整个 Collection"""
        collection_name = collection_name or COLLECTION_NAME

        if not self.is_connected():
            return False

        try:
            utility.drop_collection(collection_name, using=self.alias)
            print(f"[MilvusClient] Dropped collection: {collection_name}")
            return True
        except Exception as e:
            print(f"[MilvusClient] Drop collection failed: {e}")
            return False

    # ── 向量检索 ─────────────────────────────────────────────────────

    def search(
        self,
        query_vectors: List[List[float]],
        top_k: int = 10,
        expr: str = None,
        search_field: str = "question_vec",
        collection_name: str = None,
        output_fields: List[str] = None,
    ) -> List[List[Dict]]:
        """
        ANN 向量检索

        Args:
            query_vectors: 查询向量列表 (支持多向量批量查询)
            top_k: 返回数量
            expr: 过滤表达式，如 'category == "产品"'
            search_field: 搜索字段 (question_vec / answer_vec)
            collection_name: Collection 名称
            output_fields: 输出字段列表

        Returns:
            搜索结果列表，每个元素是一个查询的结果列表
            结构: [[{"kb_id": 1, "distance": 0.85, "metadata": {...}}, ...], ...]
        """
        collection_name = collection_name or COLLECTION_NAME
        output_fields = output_fields or ["kb_id", "metadata", "category"]

        if not self.is_connected():
            print("[MilvusClient] Not connected, cannot search")
            return []

        try:
            collection = Collection(collection_name, using=self.alias)

            # 搜索参数
            search_params = {
                "metric_type": MILVUS_METRIC_TYPE,
                "params": {"ef": 128},
            }

            # 执行搜索
            results = collection.search(
                data=query_vectors,
                anns_field=search_field,
                param=search_params,
                limit=top_k,
                expr=expr,
                output_fields=output_fields,
            )

            # 整理结果
            formatted_results = []
            for hits in results:
                hits_list = []
                for hit in hits:
                    entity = hit.entity
                    hits_list.append({
                        "kb_id": entity.get("kb_id"),
                        "distance": hit.distance,
                        "metadata": entity.get("metadata"),
                        "category": entity.get("category"),
                    })
                formatted_results.append(hits_list)

            return formatted_results

        except Exception as e:
            print(f"[MilvusClient] Search failed: {e}")
            return []

    def search_question(
        self,
        query_vector: List[float],
        top_k: int = 10,
        category: str = None,
        collection_name: str = None,
    ) -> List[Dict]:
        """
        搜索问题向量

        Args:
            query_vector: 查询向量
            top_k: 返回数量
            category: 分类过滤
            collection_name: Collection 名称

        Returns:
            搜索结果列表
        """
        collection_name = collection_name or COLLECTION_NAME

        # 构建过滤表达式
        expr = None
        if category:
            expr = f'category == "{category}"'

        results = self.search(
            query_vectors=[query_vector],
            top_k=top_k,
            expr=expr,
            search_field="question_vec",
            collection_name=collection_name,
        )

        return results[0] if results else []

    def search_answer(
        self,
        query_vector: List[float],
        top_k: int = 10,
        category: str = None,
        collection_name: str = None,
    ) -> List[Dict]:
        """
        搜索回答向量

        Args:
            query_vector: 查询向量
            top_k: 返回数量
            category: 分类过滤
            collection_name: Collection 名称

        Returns:
            搜索结果列表
        """
        collection_name = collection_name or COLLECTION_NAME

        expr = None
        if category:
            expr = f'category == "{category}"'

        results = self.search(
            query_vectors=[query_vector],
            top_k=top_k,
            expr=expr,
            search_field="answer_vec",
            collection_name=collection_name,
        )

        return results[0] if results else []

    # ── 工具方法 ─────────────────────────────────────────────────────

    def has_collection(self, name: str = None) -> bool:
        """检查 Collection 是否存在"""
        name = name or COLLECTION_NAME
        if not self.is_connected():
            return False
        try:
            return utility.has_collection(name, using=self.alias)
        except:
            return False

    def close(self):
        """关闭连接"""
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

    client = MilvusClient()

    if client.is_connected():
        print("Connected!")

        # 测试创建 Collection
        print("\nCreating collection...")
        client.create_collection(drop_existing=True)

        # 测试插入
        print("\nInserting test data...")
        kb_ids = [1, 2, 3]
        question_vecs = [[0.1] * EMBEDDING_DIM, [0.2] * EMBEDDING_DIM, [0.3] * EMBEDDING_DIM]
        answer_vecs = [[0.4] * EMBEDDING_DIM, [0.5] * EMBEDDING_DIM, [0.6] * EMBEDDING_DIM]
        categories = ["产品", "流程", "产品"]
        metadatas = [
            json.dumps({"question": "测试问题1", "answer": "测试回答1"}),
            json.dumps({"question": "测试问题2", "answer": "测试回答2"}),
            json.dumps({"question": "测试问题3", "answer": "测试回答3"}),
        ]

        ids = client.insert(kb_ids, question_vecs, answer_vecs, categories, metadatas)
        print(f"Inserted IDs: {ids}")

        # 加载 Collection
        client.load_collection()

        # 测试搜索
        print("\nSearching...")
        results = client.search_question(
            query_vector=[0.15] * EMBEDDING_DIM,
            top_k=2,
        )
        print(f"Search results: {len(results)} hits")
        for r in results:
            print(f"  kb_id={r['kb_id']}, distance={r['distance']:.4f}")

        # 测试删除
        print("\nDeleting...")
        client.delete_by_kb_id(1)

        # 获取统计
        print(f"\nCollection stats: {client.get_collection_stats()} entities")

        # 测试删除 Collection
        # client.drop_collection()

        client.close()

    else:
        print("Connection failed! Check ZILLIZ_URI and ZILLIZ_TOKEN")
        print(f"Current URI: {ZILLIZ_URI}")
