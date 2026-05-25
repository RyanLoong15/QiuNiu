# -*- coding: utf-8 -*-
"""
LLM Client with Fallback Chain Support.

实现 DeepSeek（主）→ Qwen（备）的 fallback 链调用机制：
- 每个 provider 支持超时、重试、retry_delay
- 429 限流：等待 3s 后重试
- 5xx 错误：重试 1 次，失败则下一级
- 所有层级失败：返回 fallback_reply

使用方式：
    from llm_client import LLMClient
    client = LLMClient()
    result = client.invoke(
        system_prompt="你是...",
        user_prompt="用户问题...",
        max_tokens=4096,
        temperature=0.3
    )
    print(result["content"])
"""

import time
import requests
from typing import Optional, Dict, Any, List

from config import (
    SILICONFLOW_API_KEY,
    SILICONFLOW_API_URL,
    LLM_FALLBACK_CHAIN,
    LLM_TIMEOUT,
    LLM_MAX_TOKENS,
    LLM_MAX_RETRIES,
    LLM_RETRY_DELAY,
    LLM_RATE_LIMIT_DELAY,
    LLM_FALLBACK_REPLY,
    DEBUG,
)


class LLMClient:
    """
    LLM Client with fallback chain support.
    
    Fallback 链：DeepSeek → Qwen
    - 主模型失败时自动切换到备用模型
    - 支持重试、超时、限流处理
    """

    def __init__(self):
        self.api_url = f"{SILICONFLOW_API_URL}/chat/completions"
        self.headers = {
            "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
            "Content-Type": "application/json",
        }
        self.fallback_chain = LLM_FALLBACK_CHAIN
        self.timeout = LLM_TIMEOUT
        self.max_retries = LLM_MAX_RETRIES
        self.retry_delay = LLM_RETRY_DELAY
        self.rate_limit_delay = LLM_RATE_LIMIT_DELAY
        self.fallback_reply = LLM_FALLBACK_REPLY

    def invoke(
        self,
        system_prompt: str,
        user_prompt: str,
        max_tokens: Optional[int] = None,
        temperature: Optional[float] = None,
        model_override: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Invoke LLM with fallback chain.
        
        Args:
            system_prompt: System prompt for the LLM
            user_prompt: User message/question
            max_tokens: Maximum tokens in response (default from config)
            temperature: Temperature for sampling (default 0.3)
            model_override: Override model (skip fallback chain)
        
        Returns:
            {
                "content": str,        # LLM response or fallback_reply
                "model": str,          # Actual model used
                "mode": str,           # "llm", "fallback_retry", "fallback_chain", "fallback_all"
                "latency_ms": int,     # Total latency in milliseconds
                "retries": int,        # Total retries attempted
                "error": Optional[str] # Error message if any
            }
        """
        start = time.time()
        
        max_tokens = max_tokens or LLM_MAX_TOKENS
        temperature = temperature or 0.3
        
        # 如果指定了 model_override，只使用该模型
        models_to_try = [model_override] if model_override else self.fallback_chain
        
        total_retries = 0
        last_error = None
        
        for model_idx, model in enumerate(models_to_try):
            retries = 0
            
            while retries <= self.max_retries:
                result = self._call_single_model(
                    model=model,
                    system_prompt=system_prompt,
                    user_prompt=user_prompt,
                    max_tokens=max_tokens,
                    temperature=temperature,
                )
                
                if result["success"]:
                    latency_ms = int((time.time() - start) * 1000)
                    return {
                        "content": result["content"],
                        "model": model,
                        "mode": "llm" if retries == 0 else "fallback_retry",
                        "latency_ms": latency_ms,
                        "retries": total_retries + retries,
                        "error": None,
                    }
                
                # 失败处理
                error_type = result["error_type"]
                last_error = result["error"]
                
                if DEBUG:
                    print(f"[LLMClient] Model {model} attempt {retries} failed: {error_type} - {last_error}")
                
                # 429 限流：等待后重试
                if error_type == "rate_limit":
                    if retries < self.max_retries:
                        if DEBUG:
                            print(f"[LLMClient] Rate limited, waiting {self.rate_limit_delay}s before retry...")
                        time.sleep(self.rate_limit_delay)
                        retries += 1
                        total_retries += 1
                        continue
                    else:
                        # 达到最大重试，切换到下一级模型
                        break
                
                # 5xx 错误：重试 1 次
                if error_type == "server_error":
                    if retries < self.max_retries:
                        if DEBUG:
                            print(f"[LLMClient] Server error, retrying in {self.retry_delay}s...")
                        time.sleep(self.retry_delay)
                        retries += 1
                        total_retries += 1
                        continue
                    else:
                        # 达到最大重试，切换到下一级模型
                        break
                
                # 其他错误（4xx 等）：直接切换到下一级模型，不重试
                if error_type in ("client_error", "timeout", "network_error", "quota_error"):
                    break
                
                # 未知错误：重试一次后切换
                if retries < self.max_retries:
                    time.sleep(self.retry_delay)
                    retries += 1
                    total_retries += 1
                else:
                    break
        
        # 所有层级都失败，返回 fallback_reply
        latency_ms = int((time.time() - start) * 1000)
        
        if DEBUG:
            print(f"[LLMClient] All models failed, returning fallback_reply")
        
        return {
            "content": self.fallback_reply,
            "model": "fallback",
            "mode": "fallback_all",
            "latency_ms": latency_ms,
            "retries": total_retries,
            "error": last_error,
        }

    def _call_single_model(
        self,
        model: str,
        system_prompt: str,
        user_prompt: str,
        max_tokens: int,
        temperature: float,
    ) -> Dict[str, Any]:
        """
        Call a single LLM model.
        
        Returns:
            {
                "success": bool,
                "content": Optional[str],
                "error_type": Optional[str],  # rate_limit, server_error, client_error, timeout, network_error, quota_error
                "error": Optional[str]
            }
        """
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]
        
        payload = {
            "model": model,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        
        try:
            resp = requests.post(
                self.api_url,
                headers=self.headers,
                json=payload,
                timeout=self.timeout,
            )
            
            # 检查状态码
            status = resp.status_code
            
            if status == 200:
                data = resp.json()
                content = (
                    data.get("choices", [{}])[0]
                    .get("message", {})
                    .get("content", "")
                    .strip()
                )
                return {"success": True, "content": content, "error_type": None, "error": None}
            
            # 429 限流
            if status == 429:
                return {
                    "success": False,
                    "content": None,
                    "error_type": "rate_limit",
                    "error": f"Rate limited (429)",
                }
            
            # 402/403 quota/balance 问题
            if status in (402, 403):
                return {
                    "success": False,
                    "content": None,
                    "error_type": "quota_error",
                    "error": f"Quota/balance issue ({status})",
                }
            
            # 5xx 服务端错误
            if 500 <= status < 600:
                return {
                    "success": False,
                    "content": None,
                    "error_type": "server_error",
                    "error": f"Server error ({status})",
                }
            
            # 其他 4xx 客户端错误
            if 400 <= status < 500:
                return {
                    "success": False,
                    "content": None,
                    "error_type": "client_error",
                    "error": f"Client error ({status}): {resp.text[:200]}",
                }
            
            # 其他状态码
            return {
                "success": False,
                "content": None,
                "error_type": "unknown",
                "error": f"Unexpected status ({status})",
            }
        
        except requests.exceptions.Timeout:
            return {
                "success": False,
                "content": None,
                "error_type": "timeout",
                "error": f"Timeout after {self.timeout}s",
            }
        
        except requests.exceptions.ConnectionError as e:
            return {
                "success": False,
                "content": None,
                "error_type": "network_error",
                "error": f"Connection error: {str(e)[:100]}",
            }
        
        except Exception as e:
            return {
                "success": False,
                "content": None,
                "error_type": "unknown",
                "error": f"Unexpected error: {str(e)[:100]}",
            }

    def quick_invoke(self, user_prompt: str, system_prompt: Optional[str] = None) -> str:
        """
        Quick invoke for simple use cases.
        
        Args:
            user_prompt: User message
            system_prompt: Optional system prompt
        
        Returns:
            LLM response content (string only)
        """
        default_system = "你是一个智能助手，请根据用户的问题给出准确、简洁的回答。"
        system_prompt = system_prompt or default_system
        
        result = self.invoke(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
        )
        
        return result["content"]


# 模块级便捷函数
_default_client = None

def get_client() -> LLMClient:
    """Get or create default LLM client."""
    global _default_client
    if _default_client is None:
        _default_client = LLMClient()
    return _default_client


def invoke_llm(
    system_prompt: str,
    user_prompt: str,
    max_tokens: Optional[int] = None,
    temperature: Optional[float] = None,
) -> str:
    """
    Module-level convenience function for LLM invoke.
    
    Returns:
        LLM response content (string only)
    """
    client = get_client()
    result = client.invoke(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        max_tokens=max_tokens,
        temperature=temperature,
    )
    return result["content"]


def chat(
    messages: List[Dict[str, str]],
    max_tokens: Optional[int] = None,
    temperature: Optional[float] = None,
    model_override: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Chat with LLM using OpenAI-style messages format.
    
    Args:
        messages: List of {"role": "system/user/assistant", "content": "..."}
        max_tokens: Maximum tokens in response
        temperature: Temperature for sampling
        model_override: Override model (skip fallback chain)
    
    Returns:
        {
            "success": bool,
            "content": str,        # LLM response or fallback_reply
            "model": str,          # Actual model used
            "mode": str,           # "llm", "fallback_retry", "fallback_chain", "fallback_all"
            "latency_ms": int,     # Total latency
            "retries": int,        # Total retries
            "error": Optional[str]
        }
    """
    client = get_client()
    
    # Extract system_prompt and user_prompt from messages
    system_prompt = ""
    user_prompt = ""
    history = []
    
    for msg in messages:
        role = msg.get("role", "")
        content = msg.get("content", "")
        
        if role == "system":
            system_prompt = content
        elif role == "user":
            # 如果已经有 user_prompt，说明是多轮对话，需要构建完整 prompt
            if user_prompt:
                # 对于多轮对话，invoke 只支持单轮，这里简化处理
                # 将历史对话作为上下文附加到 system_prompt
                system_prompt += f"\n\n【对话历史】\n{user_prompt}\n助手：...\n\n当前问题：{content}"
                user_prompt = content
            else:
                user_prompt = content
        elif role == "assistant":
            # 收集助手回复作为历史（但 invoke 不支持，需要额外处理）
            history.append(content)
    
    # 调用 invoke
    result = client.invoke(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        max_tokens=max_tokens,
        temperature=temperature,
        model_override=model_override,
    )
    
    return {
        "success": result["mode"] in ("llm", "fallback_retry"),
        "content": result["content"],
        "model": result["model"],
        "mode": result["mode"],
        "latency_ms": result["latency_ms"],
        "retries": result["retries"],
        "error": result["error"],
    }


# 测试入口
if __name__ == "__main__":
    print("[LLMClient] Testing fallback chain...")
    
    client = LLMClient()
    
    # 测试调用
    result = client.invoke(
        system_prompt="你是一个智能助手。",
        user_prompt="你好，请简单介绍一下你自己。",
        max_tokens=100,
        temperature=0.3,
    )
    
    print(f"[LLMClient] Result:")
    print(f"  - Model: {result['model']}")
    print(f"  - Mode: {result['mode']}")
    print(f"  - Latency: {result['latency_ms']}ms")
    print(f"  - Retries: {result['retries']}")
    print(f"  - Content: {result['content'][:100]}...")
    
    # 测试 quick_invoke
    print("\n[LLMClient] Testing quick_invoke...")
    response = client.quick_invoke("什么是人工智能？")
    print(f"[LLMClient] Response: {response[:200]}...")