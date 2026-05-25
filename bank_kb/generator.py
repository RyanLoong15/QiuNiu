# -*- coding: utf-8 -*-
"""
LLM Answer Generator with character-aware prompt.
Uses llm_client for fallback chain (DeepSeek → Qwen → fallback).
"""

import time
from config import CHUNK_SIZE
from db_loader import get_character_by_id, get_document_by_id
from llm_client import chat

# 默认 SYSTEM_PROMPT（当角色不存在时使用）
DEFAULT_SYSTEM_PROMPT = """你是银行核心系统智能助手，由RyanLoong开发。

【回答原则】
1. 只根据提供的参考资料回答，禁止编造信息
2. 如果资料中没有答案，明确说"未找到相关信息"
3. 每个关键结论都要标注来源，格式为（来源：知识库）
4. 回答风格：亲切、专业、简洁

【禁止行为】
- 不得泄露客户信息、系统密码、内部IP等敏感信息
- 不得回答与银行业务无关的问题"""

PROMPT_TEMPLATE = """【参考资料】
{context}

【用户问题】
{question}

【回答】"""

# 多轮对话Prompt模板
CONVERSATIONAL_PROMPT_TEMPLATE = """【对话历史】
{chat_history}

【参考资料】
{context}

【用户问题】
{question}

【回答】"""


class Generator:
    """LLM answer generator with character support."""

    def generate(self, query, retrieved_entries, history=None, character_id=1):
        """
        Generate answer from retrieved knowledge entries.
        Uses character's system_prompt for role-specific responses.

        Args:
            query: user's question string
            retrieved_entries: list of {"entry": {...}, "score": float}
            history: list of {"role": "user/assistant", "content": "..."} for multi-turn
            character_id: 角色ID（默认1=囚牛）

        Returns:
            {
                "answer": str,
                "sources": [...],
                "latency_ms": int,
                "model": str,
                "mode": str,
                "character_id": int,
                "character_name": str,
            }
        """
        start = time.time()

        # 获取角色信息
        character = get_character_by_id(character_id)
        character_name = character.get("name", "助手") if character else "助手"
        system_prompt = character.get("system_prompt", DEFAULT_SYSTEM_PROMPT) if character else DEFAULT_SYSTEM_PROMPT

        # Build sources always
        sources = []
        for r in retrieved_entries:
            entry = r["entry"]
            source = {
                "id": entry.get("id"),
                "question": entry.get("question", ""),
                "answer": entry.get("answer", ""),  # 添加 answer，用于来源详情
                "category": entry.get("category", ""),
                "score": r["score"],
                "bm25": r.get("bm25"),
                "tfidf": r.get("tfidf"),
                "keyword_score": r.get("keyword_score"),
                "vector_score": r.get("vector_score"),
            }
            # 如果是文档导入，添加文档链接
            if entry.get("source_type") == "document" and entry.get("source_question_id"):
                doc = get_document_by_id(entry["source_question_id"])
                if doc and doc.get("file_path"):
                    source["document_name"] = doc["filename"]
                    source["document_path"] = doc["file_path"]
            sources.append(source)

        # Try LLM generation first
        answer, mode, model = self._call_llm(query, retrieved_entries, history=history, system_prompt=system_prompt)

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
            model = "direct-retrieval"

        latency_ms = int((time.time() - start) * 1000)

        return {
            "answer": answer,
            "sources": sources,
            "latency_ms": latency_ms,
            "model": model,
            "mode": mode,
            "character_id": character_id,
            "character_name": character_name,
        }

    def _call_llm(self, query, retrieved_entries, history=None, system_prompt=None):
        """
        Call LLM via llm_client.chat().
        Returns (answer, mode, model) or (None, mode, model) on failure.
        """
        system_prompt = system_prompt or DEFAULT_SYSTEM_PROMPT

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
        messages = [{"role": "system", "content": system_prompt}]

        # 如果有历史对话，使用多轮对话模板
        if history and len(history) > 0:
            # 构建对话历史文本
            history_lines = []
            for h in history:
                role = "用户" if h.get("role") == "user" else "助手"
                content = h.get("content", "")
                history_lines.append(f"{role}：{content}")
            chat_history = "\n".join(history_lines)

            user_prompt = CONVERSATIONAL_PROMPT_TEMPLATE.format(
                chat_history=chat_history,
                context=context,
                question=query
            )
        else:
            user_prompt = PROMPT_TEMPLATE.format(context=context, question=query)

        messages.append({"role": "user", "content": user_prompt})

        # Call llm_client.chat()
        result = chat(messages=messages)

        if result.get("success"):
            answer = result.get("content", "").strip()
            model = result.get("model", "unknown")
            return answer, "llm", model
        else:
            error = result.get("error", "unknown")
            model = result.get("model", "none")
            print(f"[Generator] LLM failed: {error}, model={model}")
            return None, f"fallback_{error[:20]}", model
