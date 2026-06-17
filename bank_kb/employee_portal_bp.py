# -*- coding: utf-8 -*-
"""
员工门户 Blueprint：登录、登出、头像、问题管理
注册到 /api/employee 前缀
"""
from flask import Blueprint, request, jsonify
import hashlib, os, sys
from datetime import datetime, timedelta, timezone
import jwt as jwt_lib
import bcrypt

from db_loader import (
    get_employee_by_id, get_employee_by_no, get_employee_by_name,
    verify_employee_password, update_employee_login,
    get_employee_questions, count_employee_questions,
    insert_unanswered_question, register_employee,
)
from config import JWT_SECRET, JWT_ALGORITHM, JWT_EXPIRY_HOURS
from config import WEBAPP_ROOT, TOMCAT_CONTEXT, ALLOWED_EXTENSIONS, MAX_FILE_SIZE
from config import _LOGIN_ATTEMPTS, MAX_ATTEMPTS, WINDOW_SECONDS, _TOKEN_BLACKLIST

print(f"[DEBUG] Loading employee_portal_bp from {__file__}")

bp = Blueprint("employee_portal", __name__, url_prefix="/api/employee")


# ── 内部工具函数 ─────────────────────────────────────

def _cleanup_blacklist():
    now_ts = datetime.now(timezone.utc).timestamp()
    expired = [jti for jti, exp in _TOKEN_BLACKLIST.items() if exp <= now_ts]
    for jti in expired:
        del _TOKEN_BLACKLIST[jti]


def require_employee_auth(f):
    from functools import wraps
    @wraps(f)
    def wrapper(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"code": 401, "message": "未登录", "data": None}), 401
        token = auth_header[7:]
        print(f"[DEBUG] Authorization header: {auth_header[:50]}...")
        print(f"[DEBUG] Token (完整): {token}")
        print(f"[DEBUG] Token 长度: {len(token)}")
        try:
            print(f"[DEBUG] 准备解码 Token...", flush=True)
            payload = jwt_lib.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
            print(f"[DEBUG] Decode 成功: payload={payload}", flush=True)
        except jwt_lib.ExpiredSignatureError as e:
            print(f"[DEBUG] Token 过期: {e}", flush=True)
            return jsonify({"code": 401, "message": "登录已过期", "data": None}), 401
        except jwt_lib.InvalidTokenError as e:
            print(f"[DEBUG] Token 无效: {e}", file=sys.stderr, flush=True)
            return jsonify({"code": 401, "message": "无效凭证", "data": None}), 401
        jti = payload.get("jti")
        if jti and jti in _TOKEN_BLACKLIST:
            return jsonify({"code": 401, "message": "登录已注销", "data": None}), 401
        employee = get_employee_by_id(payload["sub"])
        if not employee or not employee["is_active"]:
            return jsonify({"code": 403, "message": "账号已禁用", "data": None}), 403
        request.employee = employee
        request.employee["character_id"] = payload.get("character_id")
        return f(*args, **kwargs)
    return wrapper


def check_rate_limit() -> bool:
    ip = request.remote_addr
    now = datetime.now(timezone.utc)
    window_start = now - timedelta(seconds=WINDOW_SECONDS)
    attempts = _LOGIN_ATTEMPTS.get(ip, [])
    attempts = [t for t in attempts if t > window_start]
    _LOGIN_ATTEMPTS[ip] = attempts
    return len(attempts) < MAX_ATTEMPTS


def record_attempt():
    ip = request.remote_addr
    if ip not in _LOGIN_ATTEMPTS:
        _LOGIN_ATTEMPTS[ip] = []
    _LOGIN_ATTEMPTS[ip].append(datetime.now(timezone.utc))


def allowed_file(filename):
    return "." in filename and \
           filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS


# ── 路由 ────────────────────────────────────────────────

@bp.route("/login", methods=["POST"])
def login():
    if not check_rate_limit():
        return jsonify({"code": 429, "message": "登录尝试次数过多，请稍后再试", "data": None}), 429

    body = request.get_json(force=True) or {}
    employee_no = (body.get("employee_no") or "").strip()
    name = (body.get("name") or "").strip()
    password = body.get("password") or ""

    if (not employee_no and not name) or not password:
        return jsonify({"code": 400, "message": "工号/姓名和密码不能为空", "data": None}), 400

    # 查找员工
    employee = None
    if employee_no:
        employee = get_employee_by_no(employee_no)
    elif name:
        employee = get_employee_by_name(name)
    
    if not employee or not verify_employee_password(employee['employee_no'], password):
        record_attempt()
        return jsonify({"code": 401, "message": "工号/姓名或密码错误", "data": None}), 401

    now = datetime.now(timezone.utc)
    print(f"[DEBUG] now = {now}, timestamp = {int(now.timestamp())}, now.timestamp() = {now.timestamp()}")
    exp = now + timedelta(hours=JWT_EXPIRY_HOURS)
    jti = hashlib.sha256(f"{employee['id']}{now.timestamp()}".encode()).hexdigest()[:16]

    payload = {
        "sub": str(employee["id"]),
        "employee_no": employee["employee_no"],
        "name": employee["name"],
        "character_id": employee.get("character_id"),
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
        "jti": jti,
    }

    token = jwt_lib.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    update_employee_login(employee["id"])

    return jsonify({
        "code": 200,
        "message": "登录成功",
        "data": {
            "token": token,
            "expires_in": JWT_EXPIRY_HOURS * 3600,
            "employee": {
                "id": employee["id"],
                "employee_no": employee["employee_no"],
                "name": employee["name"],
                "character_id": employee.get("character_id"),
            }
        }
    })


@bp.route("/logout", methods=["POST"])
@require_employee_auth
def logout():
    auth_header = request.headers.get("Authorization", "")
    token = auth_header[7:]
    try:
        payload = jwt_lib.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        jti = payload.get("jti")
        exp = payload.get("exp", 0)
        if jti:
            _cleanup_blacklist()
            _TOKEN_BLACKLIST[jti] = exp
    except Exception:
        pass
    return jsonify({"code": 200, "message": "登出成功", "data": None})


@bp.route("/profile", methods=["GET"])
@require_employee_auth
def profile():
    emp = request.employee
    unread_count = count_employee_questions(emp["id"], status="pending")
    avatar_url = None
    if emp.get("avatar_uploaded_path"):
        avatar_url = f"{TOMCAT_CONTEXT}/static/avatars/{emp['avatar_uploaded_path']}"
    return jsonify({
        "code": 200,
        "message": "获取成功",
        "data": {
            "employee_no": emp["employee_no"],
            "name": emp["name"],
            "phone": emp.get("phone"),
            "email": emp.get("email"),
            "avatar": avatar_url,
            "character_id": emp.get("character_id"),
            "unread_count": unread_count,
            "last_login_at": emp.get("last_login_at"),
        }
    })


@bp.route("/avatar/upload", methods=["POST"])
@require_employee_auth
def avatar_upload():
    if "file" not in request.files:
        return jsonify({"code": 400, "message": "请选择文件", "data": None}), 400
    file = request.files["file"]
    if file.filename == "":
        return jsonify({"code": 400, "message": "请选择文件", "data": None}), 400
    if not allowed_file(file.filename):
        return jsonify({"code": 400, "message": "仅支持 png/jpg/jpeg/gif 格式", "data": None}), 400

    file.seek(0, 2)  # 移到文件末尾
    file_size = file.tell()
    if file_size > MAX_FILE_SIZE:
        return jsonify({"code": 400, "message": "文件大小不能超过 5MB", "data": None}), 400
    file.seek(0)

    emp = request.employee
    character = None
    if emp.get("character_id"):
        from db_loader import get_character_by_id
        character = get_character_by_id(emp["character_id"])
    slug = character["slug"] if character else f"emp{emp['id']}"

    ext = file.filename.rsplit(".", 1)[1].lower()
    filename = f"{slug}_uploaded.{ext}"
    save_path = os.path.join(WEBAPP_ROOT, "static", "avatars", filename)
    os.makedirs(os.path.dirname(save_path), exist_ok=True)
    file.save(save_path)

    from db_loader import update_employee_avatar
    update_employee_avatar(emp["id"], filename)

    return jsonify({
        "code": 200,
        "message": "上传成功，管理员审核后将生效",
        "data": {"filename": filename, "url": f"{TOMCAT_CONTEXT}/static/avatars/{filename}"}
    })


@bp.route("/avatar/generate", methods=["POST"])
@require_employee_auth
def avatar_generate():
    emp = request.employee
    character = None
    if emp.get("character_id"):
        from db_loader import get_character_by_id
        character = get_character_by_id(emp["character_id"])
    slug = character["slug"] if character else f"emp{emp['id']}"
    generated_path = f"{slug}_generated.png"

    try:
        from db_loader import get_connection
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE virtual_characters SET avatar=%s, avatar_status='generated', "
            "last_avatar_generate_at=NOW(), updated_at=NOW() WHERE id=%s",
            (generated_path, emp.get("character_id"))
        )
        conn.commit()
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"[Avatar] Failed to update: {e}")

    return jsonify({
        "code": 200,
        "message": "头像生成成功（占位）",
        "data": {"url": f"{TOMCAT_CONTEXT}/static/avatars/{generated_path}"}
    })


@bp.route("/questions", methods=["GET"])
@require_employee_auth
def questions():
    emp = request.employee
    status = request.args.get("status", "pending")
    page = request.args.get("page", 1, type=int)
    page_size = request.args.get("page_size", 20, type=int)
    if page_size > 100:
        page_size = 100

    items = get_employee_questions(emp["id"], status=status, page=page, page_size=page_size)
    total = count_employee_questions(emp["id"], status=status)

    return jsonify({
        "code": 200,
        "message": "获取成功",
        "data": {"items": items, "total": total, "page": page, "page_size": page_size}
    })


@bp.route("/questions/<int:qid>/read", methods=["POST"])
@require_employee_auth
def mark_question_read(qid):
    emp = request.employee
    try:
        from db_loader import get_connection
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE unanswered_questions SET is_read=1, read_at=NOW() "
            "WHERE id=%s AND character_id=%s AND is_read=0",
            (qid, emp.get("character_id"))
        )
        conn.commit()
        affected = cursor.rowcount
        cursor.close()
        conn.close()
        if affected:
            return jsonify({"code": 200, "message": "标记已读成功", "data": None})
        else:
            return jsonify({"code": 404, "message": "问题不存在或已读", "data": None}), 404
    except Exception as e:
        return jsonify({"code": 500, "message": str(e), "data": None}), 500


@bp.route("/questions/<int:qid>/answer", methods=["POST"])
@require_employee_auth
def answer_question(qid):
    """员工回答未答问题，并自动录入知识库."""
    emp = request.employee
    body = request.get_json(silent=True) or {}
    answer = (body.get("answer") or "").strip()
    if not answer:
        return jsonify({"code": 400, "message": "回答不能为空", "data": None}), 400

    try:
        from db_loader import get_connection, answer_unanswered_question, ConcurrencyConflictError
        # 先获取问题信息
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT question, character_id, status FROM unanswered_questions WHERE id=%s", (qid,))
        row = cursor.fetchone()
        cursor.close()
        conn.close()

        if not row:
            return jsonify({"code": 404, "message": "问题不存在", "data": None}), 404
        if row[2] != 'pending':
            return jsonify({"code": 409, "message": "问题已被处理", "data": None}), 409

        try:
            success = answer_unanswered_question(
                uq_id=qid,
                answer=answer,
                answered_by=emp.get("name", ""),
            )
            if not success:
                return jsonify({"code": 404, "message": "问题不存在", "data": None}), 404
        except ConcurrencyConflictError:
            return jsonify({"code": 409, "message": "问题已被其他同事处理", "data": None}), 409

        # 自动录入知识库
        kb_entry_id = None
        try:
            conn = get_connection()
            cursor = conn.cursor()
            question_text = row[0]
            char_id = row[1]
            category = '未分类'
            if char_id:
                cursor.execute("SELECT name FROM virtual_characters WHERE id=%s", (char_id,))
                char_row = cursor.fetchone()
                if char_row:
                    category = char_row[0]
            cursor.execute(
                "INSERT INTO knowledge_base (question, answer, category, enabled) VALUES (%s, %s, %s, 1)",
                (question_text, answer, category)
            )
            kb_entry_id = cursor.lastrowid
            cursor.execute("UPDATE unanswered_questions SET kb_entry_id=%s WHERE id=%s", (kb_entry_id, qid))
            conn.commit()
            cursor.close()
            conn.close()
        except Exception as e:
            print(f"[Employee] Auto-KB insert failed: {e}")

        return jsonify({"code": 200, "message": "回答成功，已自动录入知识库", "data": {"kb_entry_id": kb_entry_id}})
    except Exception as e:
        return jsonify({"code": 500, "message": str(e), "data": None}), 500


@bp.route("/ask", methods=["POST"])
@require_employee_auth
def ask():
    emp = request.employee
    body = request.get_json(silent=True) or {}
    question = (body.get("question") or "").strip()
    if not question:
        return jsonify({"code": 400, "message": "问题不能为空", "data": None}), 400

    character_id = emp.get("character_id")
    if not character_id:
        return jsonify({"code": 400, "message": "该员工未绑定虚拟角色，无法提问", "data": None}), 400

    try:
        new_id = insert_unanswered_question(
            character_id=character_id,
            question=question,
        )
        if new_id is None:
            return jsonify({"code": 409, "message": "该问题已存在", "data": None}), 409
        return jsonify({"code": 200, "message": "提交成功", "data": {"id": new_id}})
    except Exception as e:
        return jsonify({"code": 500, "message": str(e), "data": None}), 500


@bp.route("/register", methods=["POST"])
def register():
    """
    员工注册接口
    """
    body = request.get_json(force=True) or {}
    name = (body.get("name") or "").strip()
    password = body.get("password") or ""
    character_id = body.get("character_id")  # 可选
    
    # 验证
    if not name or len(name) < 2:
        return jsonify({"code": 400, "message": "姓名至少2个字符", "data": None}), 400
    
    if not password or len(password) < 6:
        return jsonify({"code": 400, "message": "密码至少6位", "data": None}), 400
    
    # 密码哈希
    password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    
    # 注册
    result = register_employee(name, password_hash, character_id)
    
    if "error" in result:
        return jsonify({"code": 400, "message": result["error"], "data": None}), 400
    
    return jsonify({
        "code": 200,
        "message": "注册成功",
        "data": {
            "employee_no": result["employee_no"],
            "name": result["name"],
            "character_id": result.get("character_id")
        }
    })
