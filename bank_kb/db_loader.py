# -*- coding: utf-8 -*-
"""MySQL knowledge loader for bank_kb Flask service."""

import mysql.connector
from config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE


def get_connection():
    """Return a reusable MySQL connection."""
    return mysql.connector.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
    )


def load_knowledge_entries():
    """
    Load all enabled knowledge entries from MySQL.
    Returns list of dicts with id, question, answer, category.
    """
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
            "SELECT id, question, answer, category, source_type, source_question_id "
            "FROM knowledge_base "
            "WHERE enabled = 1 "
            "ORDER BY id"
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        print(f"[DB] Loaded {len(rows)} knowledge entries.")
        return rows
    except mysql.connector.Error as e:
        print(f"[DB] Failed to load knowledge: {e}")
        return []


def get_entries_count():
    """Return total count of enabled entries."""
    try:
        conn = mysql.connector.connect(
            host=MYSQL_HOST,
            port=MYSQL_PORT,
            user=MYSQL_USER,
            password=MYSQL_PASSWORD,
            database=MYSQL_DATABASE,
            charset="utf8mb4",
        )
        cursor = conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM knowledge_base WHERE enabled = 1")
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        return count
    except Exception:
        return 0


# ── 角色管理 ────────────────────────────────────────────────────────────────────────

def get_all_characters():
    """
    获取所有启用的虚拟角色。
    Returns list of dicts with id, slug, name, title, system_prompt, categories, confidence_threshold, sort_order.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, slug, name, title, description, avatar, staff_user_id, system_prompt, avatar_style, "
            "       categories, confidence_threshold, sort_order, is_active, created_at, updated_at "
            "FROM virtual_characters "
            "WHERE is_active = 1 "
            "ORDER BY sort_order"
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        return rows
    except mysql.connector.Error as e:
        print(f"[DB] Failed to load characters: {e}")
        return []


def get_character_by_id(character_id: int):
    """
    根据 ID 获取单条虚拟角色。
    Returns dict with all character fields, or None if not found.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, slug, name, title, description, avatar, staff_user_id, system_prompt, avatar_style, "
            "       categories, confidence_threshold, sort_order, is_active, created_at, updated_at "
            "FROM virtual_characters "
            "WHERE id = %s",
            (character_id,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get character {character_id}: {e}")
        return None


def get_character_by_slug(slug: str):
    """
    根据 slug 获取单条虚拟角色。
    Returns dict with all character fields, or None if not found.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, slug, name, title, description, avatar, staff_user_id, system_prompt, avatar_style, "
            "       categories, confidence_threshold, sort_order, is_active, created_at, updated_at "
            "FROM virtual_characters "
            "WHERE slug = %s",
            (slug,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get character by slug {slug}: {e}")
        return None


# ── KB 条目查询 ────────────────────────────────────────────────────────────────────────

def get_kb_entry_by_id(kb_entry_id: int):
    """
    根据 ID 获取单条 KB 条目。
    Returns dict with id, question, answer, category, enabled, or None if not found.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, question, answer, category, enabled, "
            "       source_type, source_question_id, character_id "
            "FROM knowledge_base "
            "WHERE id = %s",
            (kb_entry_id,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get KB entry {kb_entry_id}: {e}")
        return None


# ── 未答问题管理 ────────────────────────────────────────────────────────────────────────────


class ConcurrencyConflictError(Exception):
    """
    并发冲突异常。
    当 answer_unanswered_question 或 ignore_unanswered_question 的 affected_rows=0 时抛出。
    表示该问题已被其他用户处理。
    """
    pass


def insert_unanswered_question(
    character_id: int,
    question: str,
    session_id: str = None,
    score: float = None,
):
    """
    写入未答问题记录。
    使用 INSERT IGNORE 或 UNIQUE 索引防止重复插入。
    
    Args:
        character_id: 关联的角色 ID
        question: 用户提问原文
        session_id: 会话 ID（可选）
        score: 置信度分数（可选）
    
    Returns:
        插入成功返回 new_id，重复或失败返回 None
    """
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 使用 INSERT IGNORE，如果违反 UNIQUE 索引则静默忽略
        cursor.execute(
            "INSERT IGNORE INTO unanswered_questions "
            "(character_id, question, session_id, score, status, created_at) "
            "VALUES (%s, %s, %s, %s, 'pending', NOW())",
            (character_id, question, session_id, score)
        )
        
        conn.commit()
        new_id = cursor.lastrowid
        
        # 如果 lastrowid=0，说明是 INSERT IGNORE 被忽略（重复）
        if new_id == 0:
            cursor.close()
            conn.close()
            return None  # 重复插入被忽略
        
        cursor.close()
        conn.close()
        return new_id
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to insert unanswered question: {e}")
        return None


def get_unanswered_questions(
    character_id: int = None,
    status: str = 'pending',
    page: int = 1,
    page_size: int = 20,
):
    """
    分页查询未答问题列表。
    
    Args:
        character_id: 角色 ID（可选，None 表示所有角色）
        status: 状态过滤（pending/answered/ignored/auto_resolved）
        page: 页码（从 1 开始）
        page_size: 每页条数
    
    Returns:
        list of dicts
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        
        # 构建 WHERE 条件
        where_clauses = []
        params = []
        
        if character_id is not None:
            where_clauses.append("character_id = %s")
            params.append(character_id)
        
        if status:
            where_clauses.append("status = %s")
            params.append(status)
        
        where_sql = " AND " .join(where_clauses) if where_clauses else ""
        
        # 计算 OFFSET
        offset = (page - 1) * page_size
        params.extend([offset, page_size])
        
        cursor.execute(
            f"SELECT id, character_id, question, session_id, score, status, "
            f"       answer, answered_by, answered_at, ignored_by, ignored_at, "
            f"       kb_entry_id, notification_sent, created_at "
            f"FROM unanswered_questions "
            f"WHERE {where_sql if where_sql else '1=1'} "
            f"ORDER BY created_at DESC "
            f"LIMIT %s, %s",
            params
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        return rows
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get unanswered questions: {e}")
        return []


def get_unanswered_stats():
    """
    获取各角色待处理未答问题的统计。
    
    Returns:
        dict: {character_id: pending_count, ...}
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT character_id, COUNT(*) as pending_count "
            "FROM unanswered_questions "
            "WHERE status = 'pending' "
            "GROUP BY character_id"
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        
        # 转换为 dict
        stats = {row['character_id']: row['pending_count'] for row in rows}
        return stats
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get unanswered stats: {e}")
        return {}


def get_unanswered_by_id(uq_id: int):
    """
    根据 ID 获取单条未答问题。
    Returns dict or None.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, character_id, question, session_id, score, status, "
            "       answer, answered_by, answered_at, ignored_by, ignored_at, "
            "       kb_entry_id, notification_sent, created_at "
            "FROM unanswered_questions "
            "WHERE id = %s",
            (uq_id,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get unanswered by id {uq_id}: {e}")
        return None


def answer_unanswered_question(
    uq_id: int,
    answer: str,
    answered_by: str,
    kb_entry_id: int = None,
):
    """
    回答未答问题。
    使用原子 UPDATE + affected_rows 检查防止并发冲突。
    
    Args:
        uq_id: 未答问题 ID
        answer: 回答内容
        answered_by: 回答者姓名
        kb_entry_id: 关联的 KB 条目 ID（可选）
    
    Returns:
        成功返回 True
    
    Raises:
        ConcurrencyConflictError: 如果 affected_rows=0（已被其他用户处理）
    """
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 原子 UPDATE：只有 status='pending' 才能更新
        cursor.execute(
            "UPDATE unanswered_questions "
            "SET status = 'answered', "
            "    answer = %s, "
            "    answered_by = %s, "
            "    answered_at = NOW(), "
            "    kb_entry_id = %s "
            "WHERE id = %s AND status = 'pending'",
            (answer, answered_by, kb_entry_id, uq_id)
        )
        
        affected_rows = cursor.rowcount
        conn.commit()
        cursor.close()
        conn.close()
        
        # affected_rows=0 表示已被处理，抛出并发冲突异常
        if affected_rows == 0:
            raise ConcurrencyConflictError(
                f"Question {uq_id} has already been processed by another user"
            )
        
        return True
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to answer unanswered question {uq_id}: {e}")
        return False


def ignore_unanswered_question(
    uq_id: int,
    ignored_by: str,
    reason: str = None,
):
    """
    忽略未答问题。
    使用原子 UPDATE + affected_rows 检查防止并发冲突。
    
    Args:
        uq_id: 未答问题 ID
        ignored_by: 忽略操作人
        reason: 忽略原因（可选）
    
    Returns:
        成功返回 True
    
    Raises:
        ConcurrencyConflictError: 如果 affected_rows=0（已被其他用户处理）
    """
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 原子 UPDATE：只有 status='pending' 才能更新
        # 注意：ignored_reason 字段不存在，reason 参数暂不存储
        cursor.execute(
            "UPDATE unanswered_questions "
            "SET status = 'ignored', "
            "    ignored_by = %s, "
            "    ignored_at = NOW() "
            "WHERE id = %s AND status = 'pending'",
            (ignored_by, uq_id)
        )
        
        affected_rows = cursor.rowcount
        conn.commit()
        cursor.close()
        conn.close()
        
        # affected_rows=0 表示已被处理，抛出并发冲突异常
        if affected_rows == 0:
            raise ConcurrencyConflictError(
                f"Question {uq_id} has already been processed by another user"
            )
        
        return True
    
    except mysql.connector.Error as e:
        print(f"[DB] Failed to ignore unanswered question {uq_id}: {e}")
        return False


# ═════════════════════════════════════════════════════════════════
# 角色头像更新
# ═════════════════════════════════════════════════════════════════

def update_character_avatar(character_id: int, avatar_path: str):
    """
    更新角色头像路径。
    Returns True if successful, False otherwise.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE virtual_characters SET avatar = %s, updated_at = NOW() WHERE id = %s",
            (avatar_path, character_id)
        )
        conn.commit()
        cursor.close()
        conn.close()
        print(f"[DB] Updated avatar for character {character_id}: {avatar_path}")
        return True
    except mysql.connector.Error as e:
        print(f"[DB] Failed to update avatar for character {character_id}: {e}")
        return False

# ═══════════════════════════════════════════════════════════════
# 员工管理 (员工门户)
# ═══════════════════════════════════════════════════════════════

def get_employee_by_id(emp_id: int):
    """
    根据 ID 获取员工.
    Returns dict or None.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT e.id, e.employee_no, e.name, "
            "       e.avatar_uploaded_path, e.is_active, "
            "       v.id AS character_id "
            "FROM employees e "
            "LEFT JOIN virtual_characters v ON v.employee_id = e.id "
            "WHERE e.id = %s",
            (emp_id,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get employee by id {emp_id}: {e}")
        return None


def get_employee_by_no(employee_no: str):
    """
    根据工号获取员工（用于登录）.
    Returns dict or None.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT e.id, e.employee_no, e.name, e.password_hash, "
            "       e.avatar_uploaded_path, e.is_active, "
            "       v.id AS character_id "
            "FROM employees e "
            "LEFT JOIN virtual_characters v ON v.employee_id = e.id "
            "WHERE e.employee_no = %s AND e.is_active = 1",
            (employee_no,)
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        return row
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get employee by no {employee_no}: {e}")
        return None


def verify_employee_password(employee_no: str, password: str) -> bool:
    """
    校验员工登录密码.
    使用 bcrypt 校验 password_hash 字段.
    Returns bool.
    """
    import bcrypt
    employee = get_employee_by_no(employee_no)
    if not employee:
        return False
    try:
        return bcrypt.checkpw(password.encode('utf-8'), employee['password_hash'].encode('utf-8'))
    except Exception as e:
        print(f"[DB] Password verify failed: {e}")
        return False


def update_employee_login(emp_id: int):
    """更新最后登录时间."""
    try:
        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(
            "UPDATE employees SET last_login_at = NOW() WHERE id = %s",
            (emp_id,)
        )
        conn.commit()
        cursor.close()
        conn.close()
        return True
    except mysql.connector.Error as e:
        print(f"[DB] Failed to update employee login {emp_id}: {e}")
        return False


def update_employee_avatar(emp_id: int, avatar_path: str):
    """
    更新员工上传头像路径，同时更新关联角色的 avatar 字段.
    Returns True if successful.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 1. 更新 employees 表
        cursor.execute(
            "UPDATE employees SET avatar_uploaded_path = %s, updated_at = NOW() WHERE id = %s",
            (avatar_path, emp_id)
        )
        
        # 2. 同步到 virtual_characters 表
        cursor.execute(
            "UPDATE virtual_characters SET avatar = %s, avatar_status = 'uploaded', "
            "       updated_at = NOW() "
            "WHERE employee_id = %s",
            (avatar_path, emp_id)
        )
        
        conn.commit()
        cursor.close()
        conn.close()
        return True
    except mysql.connector.Error as e:
        print(f"[DB] Failed to update employee avatar {emp_id}: {e}")
        return False


# ═══════════════════════════════════════════════════════════════
# 未答问题：员工视角
# ═══════════════════════════════════════════════════════════════

def get_employee_questions(employee_id: int, status: str = None, page: int = 1, page_size: int = 20):
    """
    获取当前员工关联角色的未答问题列表.
    Returns list of dicts.
    """
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        
        # 找到员工关联的角色
        cursor.execute("SELECT id FROM virtual_characters WHERE employee_id = %s", (employee_id,))
        char_row = cursor.fetchone()
        if not char_row:
            cursor.close()
            conn.close()
            return []
        character_id = char_row['id']
        
        # 构建查询
        where_clauses = ["character_id = %s"]
        params = [character_id]
        
        if status:
            where_clauses.append("status = %s")
            params.append(status)
        
        offset = (page - 1) * page_size
        params.extend([offset, page_size])
        
        cursor.execute(
            f"SELECT id, question, score, status, created_at, answer, answered_at "
            f"FROM unanswered_questions "
            f"WHERE {' AND '.join(where_clauses)} "
            f"ORDER BY created_at DESC "
            f"LIMIT %s, %s",
            params
        )
        rows = cursor.fetchall()
        
        # 批量标记已读
        if rows and status != 'read':
            ids = [r['id'] for r in rows]
            placeholders = ','.join(['%s'] * len(ids))
            cursor.execute(
                f"UPDATE unanswered_questions "
                f"SET is_read = 1, read_at = NOW() "
                f"WHERE id IN ({placeholders}) AND is_read = 0",
                ids
            )
            conn.commit()
        
        cursor.close()
        conn.close()
        return rows
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get employee questions: {e}")
        return []


def count_employee_questions(employee_id: int, status: str = None) -> int:
    """统计员工关联角色的未答问题数量."""
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        cursor.execute("SELECT id FROM virtual_characters WHERE employee_id = %s", (employee_id,))
        char_row = cursor.fetchone()
        if not char_row:
            cursor.close()
            conn.close()
            return 0
        character_id = char_row[0]
        
        where_clauses = ["character_id = %s"]
        params = [character_id]
        
        if status:
            where_clauses.append("status = %s")
            params.append(status)
        
        cursor.execute(
            f"SELECT COUNT(*) FROM unanswered_questions WHERE {' AND '.join(where_clauses)}",
            params
        )
        count = cursor.fetchone()[0]
        cursor.close()
        conn.close()
        return count
    except mysql.connector.Error as e:
        print(f"[DB] Failed to count employee questions: {e}")
        return 0


def get_employee_by_name(name: str) -> dict:
    """根据姓名获取员工信息."""
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT e.id, e.employee_no, e.name, e.password_hash, 
                   e.avatar_uploaded_path, e.is_active, e.character_id,
                   vc.name as character_name, vc.title as character_title
            FROM employees e
            LEFT JOIN virtual_characters vc ON e.character_id = vc.id
            WHERE e.name = %s AND e.is_active = 1
        """, (name,))
        
        row = cursor.fetchone()
        cursor.close()
        conn.close()
        
        if row:
            return {
                'id': row[0],
                'employee_no': row[1],
                'name': row[2],
                'password_hash': row[3],
                'avatar_uploaded_path': row[4],
                'is_active': row[5],
                'character_id': row[6],
                'character_name': row[7],
                'character_title': row[8]
            }
        return None
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get employee by name: {e}")
        return None


def register_employee(name: str, password_hash: str, character_id: int = None) -> dict:
    """注册新员工."""
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # 检查姓名是否已存在
        cursor.execute("SELECT id FROM employees WHERE name = %s", (name,))
        if cursor.fetchone():
            cursor.close()
            conn.close()
            return {'error': '姓名已注册'}
        
        # 生成唯一工号
        import time
        employee_no = f"EMP{int(time.time())}"
        
        # 插入新员工
        cursor.execute("""
            INSERT INTO employees (employee_no, name, password_hash, character_id, is_active)
            VALUES (%s, %s, %s, %s, 1)
        """, (employee_no, name, password_hash, character_id))
        
        conn.commit()
        
        # 获取新插入的员工
        employee_id = cursor.lastrowid
        cursor.close()
        conn.close()
        
        return {'id': employee_id, 'employee_no': employee_no, 'name': name, 'character_id': character_id}
    except mysql.connector.Error as e:
        print(f"[DB] Failed to register employee: {e}")
        return {'error': str(e)}


def get_document_by_id(import_id: int) -> dict:
    """根据导入ID获取文档信息（文件名、路径）"""
    try:
        conn = get_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            "SELECT id, filename, file_path FROM document_imports WHERE id = %s",
            (import_id,)
        )
        result = cursor.fetchone()
        cursor.close()
        conn.close()
        return result or {}
    except mysql.connector.Error as e:
        print(f"[DB] Failed to get document: {e}")
        return {}

