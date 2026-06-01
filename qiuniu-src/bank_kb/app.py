# -*- coding: utf-8 -*-
"""
Bank KB - Flask API Service
Provides RAG-based knowledge Q&A with virtual human persona.
"""

from flask import Flask, request, jsonify, send_from_directory
import atexit, threading, time, os
from werkzeug.utils import secure_filename

from config import TOP_K, LLM_MODEL
from flask_cors import CORS
from db_loader import load_knowledge_entries, get_entries_count
from retriever import HybridRetriever
from generator import Generator

app = Flask(__name__)
CORS(app)
retriever = HybridRetriever()
generator = Generator()

# ── Global index lock ─────────────────────────────────────────────
_index_lock = threading.Lock()
_entries_cache = []


def _build_index():
    """Load from DB and build the retrieval index."""
    global _entries_cache
    entries = load_knowledge_entries()
    with _index_lock:
        retriever.build_index(entries)
        _entries_cache = entries
    print(f"[App] Index ready: {len(entries)} entries")


# ── Background reindex ────────────────────────────────────────────
def _schedule_reindex():
    """Trigger a background reindex after a short delay."""
    def _do():
        time.sleep(2)  # debounce rapid reloads
        _build_index()
        print("[App] Background reindex complete.")
    t = threading.Thread(target=_do, daemon=True)
    t.start()


# ── Init ──────────────────────────────────────────────────────────
_build_index()
atexit.register(lambda: None)  # no-op, kept for future cleanup


# ═══════════════════════════════════════════════════════════════════
# API Endpoints
# ═══════════════════════════════════════════════════════════════════

@app.route("/chat", methods=["POST"])
def chat():
    """
    Multi-turn RAG chat endpoint.
    Request: {"query": "...", "top_k": 5, "history": [{"role": "user", "content": "..."}, ...]}
    Response: {"answer": "...", "sources": [...], "latency_ms": ..., "model": "..."}
    """
    body = request.get_json(force=True) or {}
    q = (body.get("query") or "").strip()
    if not q:
        return jsonify({"error": "query is required", "code": 400}), 400

    top_k = int(body.get("top_k", TOP_K))
    history = body.get("history", [])

    # Query expansion: inject key terms from conversation history into the query
    # so the retriever can find contextually relevant entries for follow-up questions
    q_expanded = _expand_query_with_history(q, history)

    with _index_lock:
        results = retriever.retrieve(q_expanded, top_k=top_k)

    gen_result = generator.generate(q, results, history=history)
    return jsonify({
        "answer": gen_result["answer"],
        "sources": gen_result["sources"],
        "latency_ms": gen_result.get("latency_ms", 0),
        "model": gen_result.get("model", LLM_MODEL),
    })


def _expand_query_with_history(query, history):
    """
    Expand query with key terms from conversation history.
    Helps follow-up questions (e.g., '第三步具体做什么') retrieve relevant entries
    even when the short query alone would match only generic documents.
    """
    if not history:
        return query

    # Collect named entities and domain keywords from recent turns
    import re
    # Key patterns to extract: 银行/系统/产品/流程 names, numbered steps, domain terms
    history_text = " ".join([
        msg.get("content", "") for msg in history[-6:]  # last 3 turns
        if isinstance(msg, dict)
    ])

    # Extract domain keywords (Chinese noun phrases, technical terms)
    keywords = []
    # Match: 银行..., 系统..., 产品..., 步骤/N, 年/月/日, specific technical terms
    patterns = [
        r'[\u4e00-\u9fff]{2,6}(?:系统|银行|流程|处理|清算|计息|报表|交易|账户|核算|产品|模块)',
        r'\d+[\u4e00-\u9fff]+(?:步|阶段|环节|流程)',
        r'[\u4e00-\u9fff]{2,4}(?:日终|批处理|对账|资金)',
    ]
    for pat in patterns:
        keywords.extend(re.findall(pat, history_text))

    # Deduplicate while preserving order
    seen = set()
    unique = []
    for kw in keywords:
        if kw not in seen:
            seen.add(kw)
            unique.append(kw)

    if unique:
        expanded = " ".join(unique) + " " + query
        return expanded
    return query


@app.route("/query", methods=["POST"])
def query():
    """
    RAG Q&A endpoint.
    Request: {"query": "问题", "top_k": 5}
    Response: {"answer": "...", "sources": [...], "latency_ms": ..., "model": "..."}
    """
    body = request.get_json(force=True) or {}
    q = (body.get("query") or "").strip()
    if not q:
        return jsonify({"error": "query is required", "code": 400}), 400

    top_k = int(body.get("top_k", TOP_K))

    with _index_lock:
        results = retriever.retrieve(q, top_k=top_k)

    gen_result = generator.generate(q, results)
    return jsonify({
        "answer": gen_result["answer"],
        "sources": gen_result["sources"],
        "retrieved_count": len(results),
        "latency_ms": gen_result.get("latency_ms", 0),
        "model": gen_result.get("model", LLM_MODEL),
        "mode": gen_result.get("mode", "llm"),
    })


@app.route("/reload", methods=["POST"])
def reload():
    """
    Trigger knowledge base reindex (called after KB entries are updated).
    """
    _schedule_reindex()
    return jsonify({"status": "ok", "message": "reindex scheduled"})


@app.route("/status", methods=["GET"])
def status():
    """Health check + index stats."""
    count = get_entries_count()
    return jsonify({
        "status": "healthy",
        "entries_count": count,
        "indexed_count": len(_entries_cache),
        "model": LLM_MODEL,
    })


@app.route("/health", methods=["GET"])
def health():
    """Simple liveness probe."""
    return jsonify({"status": "ok"})


@app.route("/ingest", methods=["POST"])
def ingest():
    """
    Import documents into the knowledge base.
    Accepts: multipart/form-data with 'file' field (or 'files' array)
    Also accepts: application/json with {"path": "D:/path/to/file.txt"} or {"dir": "D:/path/to/dir"}
    Returns: {"status": "ok", "imported": N, "errors": [...]}
    """
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    try:
        from ingest import ingest_file, ingest_directory
    except ImportError:
        return jsonify({"status": "error", "error": "ingest module not available", "code": 500}), 500

    imported = 0
    errors = []

    # Handle JSON body (e.g. {"path": "D:/docs/file.txt"} or {"dir": "D:/docs"})
    if request.is_json:
        body = request.get_json(force=True)
        if body.get("path"):
            filepath = body["path"]
            category = body.get("category", "导入文档")
            try:
                count = ingest_file(filepath, category=category)
                imported += count
            except Exception as e:
                errors.append({"file": filepath, "error": str(e)})
        elif body.get("dir"):
            dirpath = body["dir"]
            category = body.get("category")
            try:
                ingest_directory(dirpath, category=category)
            except Exception as e:
                errors.append({"dir": dirpath, "error": str(e)})
        else:
            return jsonify({"error": "provide path or dir in JSON body", "code": 400}), 400
    else:
        # Handle multipart file upload
        files = request.files.getlist("file") or request.files.getlist("files")
        category = request.form.get("category", "导入文档")
        # Debug logging to file
        import datetime
        with open("D:/qiuniu_install/bank_kb/ingest_debug.log", "a", encoding="utf-8") as logf:
            logf.write(f"\n[{datetime.datetime.now()}] Content-Type: {request.content_type}\n")
            logf.write(f"Files received: {len(files)}\n")
            for f in files:
                logf.write(f"  File: name={f.name}, filename={f.filename}, content_type={f.content_type}\n")
            logf.write(f"Category: {category}\n")
        if not files or all(f.filename == '' for f in files):
            return jsonify({"error": "no files provided", "code": 400}), 400

        save_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "raw")
        os.makedirs(save_dir, exist_ok=True)

        for f in files:
            if f.filename == '':
                continue
            safe_name = secure_filename(f.filename)
            filepath = os.path.join(save_dir, safe_name)
            try:
                f.save(filepath)
                count = ingest_file(filepath, category=category)
                imported += count
            except Exception as e:
                errors.append({"file": f.filename, "error": str(e)})

    # Trigger reindex
    _schedule_reindex()

    return jsonify({
        "status": "ok",
        "imported": imported,
        "errors": errors,
    })


# ═══════════════════════════════════════════════════════════════════


@app.route("/api/characters", methods=["GET"])
def api_characters():
    import pymysql
    try:
        conn = pymysql.connect(host='localhost', user='root', password='NewPassword123!',
                               database='qiuniu_db', charset='utf8mb4')
        cur = conn.cursor(pymysql.cursors.DictCursor)
        cur.execute('SELECT id, slug, name, title, description, avatar, avatar_style, categories FROM virtual_characters WHERE is_active=1 ORDER BY sort_order')
        rows = cur.fetchall()
        conn.close()
        return jsonify(rows)
    except Exception as e:
        return jsonify({'error': str(e)}), 500



@app.route("/api/history", methods=["GET"])
def api_history():
    # No persistent chat history storage yet; return empty list
    return jsonify({"history": []})



@app.route("/api/unanswered", methods=["GET"])
def api_unanswered():
    import pymysql
    try:
        status = request.args.get("status", "pending")
        page_size = int(request.args.get("page_size", 10))
        conn = pymysql.connect(host='localhost', user='root', password='NewPassword123!',
                               database='qiuniu_db', charset='utf8mb4')
        cur = conn.cursor(pymysql.cursors.DictCursor)
        cur.execute('SELECT id, question, status, created_at FROM unanswered_questions WHERE status=%s ORDER BY created_at DESC LIMIT %s', (status, page_size))
        rows = cur.fetchall()
        cur.execute('SELECT COUNT(*) as total FROM unanswered_questions WHERE status=%s', (status,))
        total = cur.fetchone()['total']
        conn.close()
        return jsonify({"questions": rows, "total": total})
    except Exception as e:
        return jsonify({"error": str(e)}), 500



@app.route("/api/chat", methods=["POST", "OPTIONS"])
def api_chat():
    """Frontend chat endpoint - wraps /chat with character persona."""
    if request.method == "OPTIONS":
        return jsonify({}), 200
    body = request.get_json(force=True) or {}
    q = (body.get("query") or "").strip()
    character_id = body.get("character_id")
    if not q:
        return jsonify({"error": "query is required", "code": 400}), 400

    # Load character system prompt if character_id provided
    history = []
    if character_id:
        import pymysql
        try:
            conn = pymysql.connect(host='localhost', user='root', password='NewPassword123!',
                                   database='qiuniu_db', charset='utf8mb4')
            cur = conn.cursor(pymysql.cursors.DictCursor)
            cur.execute('SELECT system_prompt, name FROM virtual_characters WHERE id=%s', (character_id,))
            row = cur.fetchone()
            conn.close()
            if row and row.get('system_prompt'):
                history = [{"role": "system", "content": row['system_prompt']}]
        except Exception:
            pass

    with _index_lock:
        results = retriever.retrieve(q, top_k=TOP_K)

    gen_result = generator.generate(q, results, history=history)
    return jsonify({
        "answer": gen_result["answer"],
        "sources": gen_result["sources"],
        "latency_ms": gen_result.get("latency_ms", 0),
        "model": gen_result.get("model", LLM_MODEL),
    })



@app.route("/api/clear", methods=["POST", "OPTIONS"])
def api_clear():
    """Clear chat history (frontend session only, no server-side state)."""
    if request.method == "OPTIONS":
        return jsonify({}), 200
    return jsonify({"status": "ok"})



@app.route("/api/unanswered/<int:uq_id>/answer", methods=["POST", "OPTIONS"])
def api_unanswered_answer(uq_id):
    if request.method == "OPTIONS":
        return jsonify({}), 200
    body = request.get_json(force=True) or {}
    answer = body.get("answer", "")
    import pymysql
    try:
        conn = pymysql.connect(host='localhost', user='root', password='NewPassword123!',
                               database='qiuniu_db', charset='utf8mb4')
        cur = conn.cursor()
        cur.execute('UPDATE unanswered_questions SET status=%s, answer=%s WHERE id=%s', ('answered', answer, uq_id))
        conn.commit()
        conn.close()
        return jsonify({"status": "ok"})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/unanswered/<int:uq_id>/ignore", methods=["POST", "OPTIONS"])
def api_unanswered_ignore(uq_id):
    if request.method == "OPTIONS":
        return jsonify({}), 200
    import pymysql
    try:
        conn = pymysql.connect(host='localhost', user='root', password='NewPassword123!',
                               database='qiuniu_db', charset='utf8mb4')
        cur = conn.cursor()
        cur.execute('UPDATE unanswered_questions SET status=%s WHERE id=%s', ('ignored', uq_id))
        conn.commit()
        conn.close()
        return jsonify({"status": "ok"})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/document/import", methods=["POST", "OPTIONS"])
def api_document_import():
    """Proxy: import document into knowledge base."""
    if request.method == "OPTIONS":
        return jsonify({}), 200
    # Reuse the existing /ingest endpoint logic
    return ingest()

if __name__ == "__main__":
    print("Starting Bank KB API on port 5001...")
    app.run(host="0.0.0.0", port=5001, debug=False, threaded=True)