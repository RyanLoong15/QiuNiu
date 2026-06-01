# -*- coding: utf-8 -*-
"""
Embedding Client - SiliconFlow Embedding API
支持 BAAI/bge-large-zh-v1.5 中文优化模型
"""

import time
import requests
from typing import List, Union, Optional


class EmbeddingClient:
    """
    SiliconFlow Embedding 客户端
    
    模型: BAAI/bge-large-zh-v1.5
    维度: 1024
    输入限制: 单条最大 512 tokens
    """

    def __init__(
        self,
        api_url: str = None,
        api_key: str = None,
        model: str = "BAAI/bge-large-zh-v1.5",
        batch_size: int = 20,
        timeout: int = 30,
    ):
        from config import SILICONFLOW_API_KEY, SILICONFLOW_API_URL

        self.api_url = f"{api_url or SILICONFLOW_API_URL}/embeddings"
        self.api_key = api_key or SILICONFLOW_API_KEY
        self.model = model
        self.batch_size = batch_size
        self.timeout = timeout

    def encode(self, texts: Union[str, List[str]]) -> List[float]:
        """
        将文本转换为向量

        Args:
            texts: 单个文本或文本列表

        Returns:
            向量列表 (每个文本一个向量)
        """
        if isinstance(texts, str):
            texts = [texts]

        payload = {
            "model": self.model,
            "input": texts,
        }

        try:
            response = requests.post(
                self.api_url,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=self.timeout,
            )
            response.raise_for_status()
            data = response.json()

            embeddings = [item["embedding"] for item in data["data"]]
            return embeddings[0] if len(embeddings) == 1 else embeddings

        except requests.exceptions.Timeout:
            print(f"[EmbeddingClient] Timeout calling API for {len(texts)} texts")
            return None
        except requests.exceptions.HTTPError as e:
            print(f"[EmbeddingClient] HTTP error {e.response.status_code}: {e.response.text}")
            return None
        except Exception as e:
            print(f"[EmbeddingClient] Error: {e}")
            return None

    def encode_batch(
        self, texts: List[str], batch_size: int = None, progress_callback=None
    ) -> List[List[float]]:
        """
        批量编码文本，自动分批处理

        Args:
            texts: 文本列表
            batch_size: 每批数量，默认 self.batch_size
            progress_callback: 进度回调 (current, total)

        Returns:
            向量列表
        """
        batch_size = batch_size or self.batch_size
        all_embeddings = []
        total = len(texts)

        for i in range(0, total, batch_size):
            batch = texts[i : i + batch_size]
            embeddings = self.encode(batch)

            if embeddings is None:
                print(f"[EmbeddingClient] Batch {i//batch_size + 1} failed, retrying...")
                time.sleep(2)
                embeddings = self.encode(batch)
                if embeddings is None:
                    print(f"[EmbeddingClient] Batch {i//batch_size + 1} retry failed, skipping")
                    embeddings = [[0.0] * 1024] * len(batch)

            all_embeddings.extend(embeddings)

            if progress_callback:
                progress_callback(i + len(batch), total)

            # 避免频率限制
            if i + batch_size < total:
                time.sleep(0.3)

        return all_embeddings

    def encode_single(self, text: str) -> Optional[List[float]]:
        """单条文本编码，返回 None 表示失败"""
        result = self.encode(text)
        if result is None or (isinstance(result, list) and len(result) == 0):
            return None
        return result[0] if isinstance(result[0], list) else result


# ── 全局单例 ─────────────────────────────────────────────────────────
_embedding_client = None


def get_embedding_client() -> EmbeddingClient:
    global _embedding_client
    if _embedding_client is None:
        _embedding_client = EmbeddingClient()
    return _embedding_client


# ── 测试 ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    client = EmbeddingClient()

    # 测试单条
    print("Testing single text...")
    vec = client.encode("什么是银行活期存款利率？")
    if vec:
        print(f"  Vector dim: {len(vec)}")
        print(f"  Sample values: {vec[:5]}")
    else:
        print("  Failed!")

    # 测试批量
    print("\nTesting batch...")
    texts = [
        "银行活期存款利率是多少？",
        "定期存款和活期存款有什么区别？",
        "如何计算贷款利息？",
    ]
    vecs = client.encode_batch(texts)
    if vecs:
        print(f"  Batch size: {len(vecs)}, Vector dim: {len(vecs[0])}")
    else:
        print("  Failed!")
