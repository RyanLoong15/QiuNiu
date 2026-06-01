# -*- coding: utf-8 -*-
"""LLM Answer Generator using SiliconFlow API (compatible with OpenAI format)."""

import time, requests
from config import SILICONFLOW_API_KEY, SILICONFLOW_API_URL, LLM_MODEL, LLM_TIMEOUT, LLM_MAX_TOKENS, CHUNK_SIZE

SYSTEM_PROMPT = """你是银行核心系统智能助手「囚牛」，由RyanLoong开发。

【回答原则】
1. 只根据提供的参考资料回答，禁止编造信息
2. 如果资料中没有答案，明确说"未找到相关信息"
3. 每个关键结论都要标注来源，格式为（来源：知识库）
4. 回答风格：亲切、专业、简洁，适当使用换行和列表

【禁止行为】
- 不得泄露客户信息、系统密码、内部IP等敏感信息
- 不得回答与银行业务无关的问题"""

PROMPT_TEMPLATE = """【参考资料】
{context}

【用户问题】
{question}

【回答】"""


class Generator:
    """Calls SiliconFlow Qwen2.5-72B-Instruct for answer generation."""

    def __init__(self):
        self.api_url = f"{SILICONFLOW_API_URL}/chat/completions"
        self.headers = {
            "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
            "Content-Type": "application/json",
        }

    def generate(self, query, retrieved_entries, history=None):
        """
        Generate answer from retrieved knowledge entries.
        Falls back to direct retrieval results if LLM API is unavailable.

        Args:
            query: user's question string
            retrieved_entries: list of {"entry": {...}, "score": float}
            history: list of {"role": "user"|"assistant", "content": str} for multi-turn context

        Returns:
            {"answer": str, "sources": [...], "latency_ms": int, "model": str, "mode": str}
        """
        start = time.time()

        # Build sources always
        sources = [
            {
                "id": r["entry"].get("id"),
                "question": r["entry"].get("question", ""),
                "category": r["entry"].get("category", ""),
                "score": r["score"],
                "bm25": r.get("bm25"),
                "tfidf": r.get("tfidf"),
            }
            for r in retrieved_entries
        ]

        # Try LLM generation first
        answer, mode = self._call_llm(query, retrieved_entries, history=history)

        # Fallback: return direct retrieval results if LLM failed
        if answer is None:
            if not retrieved_entries:
                answer = "未找到相关信息，请尝试换个关键词提问。"
                mode = "fallback_no_result"
            else:
                # Compose answer from top retrieved entries
                parts = []
                for i, r in enumerate(retrieved_entries[:3], 1):
                    e = r["entry"]
                    parts.append(f"{i}. {e.get('answer', '')}")
                answer = "\n\n".join(parts)
                mode = "fallback_retrieval"

        latency_ms = int((time.time() - start) * 1000)

        return {
            "answer": answer,
            "sources": sources,
            "latency_ms": latency_ms,
            "model": LLM_MODEL if mode != "fallback_retrieval" else "direct-retrieval",
            "mode": mode,
        }

    def _call_llm(self, query, retrieved_entries, history=None):
        """
        Call LLM API. Returns (answer, mode) or (None, mode) on failure.
        """
        # Build context from retrieved entries
        if not retrieved_entries:
            context = "（知识库暂无相关内容，请说明未找到相关信息）"
        else:
            lines = []
            for i, r in enumerate(retrieved_entries, 1):
                e = r["entry"]
                answer_text = e.get("answer", "") or ""
                if len(answer_text) > CHUNK_SIZE:
                    answer_text = answer_text[:CHUNK_SIZE] + "..."
                lines.append(
                    f"[{i}] 类别：{e.get('category', '未分类')}\n"
                    f"    问题：{e.get('question', '')}\n"
                    f"    答案：{answer_text}"
                )
            context = "\n\n".join(lines)

        # Build messages
        user_prompt = PROMPT_TEMPLATE.format(context=context, question=query)
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
        ]
        # Add history conversation
        if history:
            for h in history:
                messages.append({"role": h["role"], "content": h["content"]})
        # Add current question
        messages.append({"role": "user", "content": user_prompt})

        payload = {
            "model": LLM_MODEL,
            "messages": messages,
            "max_tokens": LLM_MAX_TOKENS,
            "temperature": 0.3,
        }

        try:
            resp = requests.post(
                self.api_url,
                headers=self.headers,
                json=payload,
                timeout=LLM_TIMEOUT,
            )
            resp.raise_for_status()
            data = resp.json()
            answer = (
                data.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
                .strip()
            )
            return answer, "llm"
        except requests.exceptions.Timeout:
            print("[Generator] LLM timeout, falling back to direct retrieval")
            return None, "fallback_timeout"
        except requests.exceptions.HTTPError as e:
            status = e.response.status_code if e.response else 0
            if status in (402, 403):
                print(f"[Generator] LLM API quota/balance issue ({status}), falling back")
            else:
                print(f"[Generator] LLM HTTP error {status}, falling back")
            return None, f"fallback_http_{status}"
        except Exception as e:
            print(f"[Generator] LLM error: {e}, falling back")
            return None, "fallback_error"
