import requests
# -*- coding: utf-8 -*-
"""Document Import Blueprint - 文档导入 API"""

import os
import sys
import json
import uuid
import traceback
from datetime import datetime
from flask import Blueprint, request, jsonify
from werkzeug.utils import secure_filename
import mysql.connector

# 导入配置和工具
sys.path.insert(0, os.path.dirname(__file__))
from config import *
from db_loader import get_connection

# 创建 Blueprint
doc_import_bp = Blueprint('document_import', __name__)

# 上传文件存储目录（从 config.py 导入 UPLOAD_DIR）
os.makedirs(UPLOAD_DIR, exist_ok=True)

ALLOWED_EXTENSIONS = {'.docx', '.pdf', '.txt', '.md', '.pptx'}

# ─── 工具函数 ───────────────────────────────────────────────────

def allowed_file(filename):
    """检查文件扩展名是否允许"""
    ext = os.path.splitext(filename)[1].lower()
    return ext in ALLOWED_EXTENSIONS


def parse_docx(file_path):
    """解析 .docx 文件，返回分段列表"""
    try:
        from docx import Document
        doc = Document(file_path)
        chunks = []
        current_chunk = ""
        for para in doc.paragraphs:
            text = para.text.strip()
            if not text:
                continue
            current_chunk += text + "\n"
            # 每段约 500 字，超过则分段
            if len(current_chunk) > 500:
                chunks.append(current_chunk.strip())
                current_chunk = ""
        if current_chunk.strip():
            chunks.append(current_chunk.strip())
        return chunks
    except Exception as e:
        print(f"[DocImport] 解析 .docx 失败: {e}")
        return []


def parse_pdf(file_path):
    """解析 .pdf 文件，返回分段列表"""
    try:
        import PyPDF2
        chunks = []
        with open(file_path, 'rb') as f:
            reader = PyPDF2.PdfReader(f)
            for page in reader.pages:
                text = page.extract_text()
                if text:
                    # 按段落分段
                    paras = [p.strip() for p in text.split('\n\n') if p.strip()]
                    for para in paras:
                        chunks.append(para)
        return chunks
    except Exception as e:
        print(f"[DocImport] 解析 .pdf 失败: {e}")
        return []


def parse_txt(file_path):
    """解析 .txt / .md 文件，返回分段列表"""
    try:
        # Try UTF-8 first, then GBK
        content = None
        for enc in ['utf-8', 'gbk', 'utf-8-sig']:
            try:
                with open(file_path, 'r', encoding=enc) as f:
                    content = f.read()
                break
            except UnicodeDecodeError:
                continue
        if content is None:
            # Fallback: read as binary
            with open(file_path, 'rb') as f:
                content = f.read().decode('utf-8', errors='ignore')
        # 按段落分段（支持多种换行符）
        paras = [p.strip() for p in content.replace('\r\n', '\n').replace('\r', '\n').split('\n\n') if p.strip()]
        if not paras and content.strip():
            paras = [content.strip()]
        return paras if paras else []
    except Exception as e:
        print(f"[DocImport] 解析 .txt 失败: {e}")
        return []


def parse_pptx(file_path):
    """Parse .pptx file, return chunks per slide (title->question, body+notes->answer)"""
    try:
        from pptx import Presentation
        prs = Presentation(file_path)
        chunks = []
        for i, slide in enumerate(prs.slides):
            title_text = ""
            body_text = ""
            for shape in slide.shapes:
                if not hasattr(shape, "text"):
                    continue
                text = shape.text.strip()
                if not text:
                    continue
                if shape.shape_type == 14:  # MSO_PLACEHOLDER
                    if hasattr(shape, "placeholder_format") and shape.placeholder_format.idx == 0:
                        title_text = text
                    else:
                        body_text += text + "\n"
                else:
                    body_text += text + "\n"
            # Speaker notes
            notes_text = ""
            if slide.has_notes_slide:
                notes_text = slide.notes_slide.notes_text_frame.text.strip()
            
            full_text = body_text.strip()
            if notes_text:
                full_text += "\n[备注] " + notes_text
            
            if full_text:
                question = title_text if title_text else full_text[:50]
                chunks.append({
                    "question": question,
                    "answer": f"[Slide {i+1}] {full_text}",
                    "slide_num": i + 1
                })
        return chunks
    except Exception as e:
        print(f"[DocImport] parse .pptx failed: {e}")
        return []

def insert_document_import(filename, file_path, file_size, created_by, character_id=None):
    """插入 document_imports 记录，返回 import_id"""
    conn = get_connection()
    try:
        cur = conn.cursor()
        sql = """INSERT INTO document_imports 
                  (filename, file_path, file_size, status, created_by, character_id, created_at)
                  VALUES (%s, %s, %s, 'pending', %s, %s, NOW())"""
        cur.execute(sql, (filename, file_path, file_size, created_by, character_id))
        import_id = cur.lastrowid
        conn.commit()
        return import_id
    except Exception as e:
        print(f"[DocImport] 插入 document_imports 失败: {e}")
        conn.rollback()
        return None
    finally:
        cur.close()
        conn.close()


def insert_document_chunks(import_id, chunks, character_id=None):
    """插入 document_chunks 记录，支持 str 和 dict 格式"""
    conn = get_connection()
    try:
        cur = conn.cursor()
        for i, chunk in enumerate(chunks):
            if isinstance(chunk, dict):
                question = chunk.get('question', '')
                answer = chunk.get('answer', '')
                content = answer or question
                slide_num = chunk.get('slide_num')
                sql = """INSERT INTO document_chunks 
                          (import_id, chunk_index, content, question, answer, slide_num, character_id, created_at)
                          VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())"""
                cur.execute(sql, (import_id, i, content, question, answer, slide_num, character_id))
            else:
                sql = """INSERT INTO document_chunks 
                          (import_id, chunk_index, content, character_id, created_at)
                          VALUES (%s, %s, %s, %s, NOW())"""
                cur.execute(sql, (import_id, i, chunk, character_id))
        conn.commit()
        return True
    except Exception as e:
        print(f"[DocImport] insert document_chunks failed: {e}")
        conn.rollback()
        return False
    finally:
        cur.close()
        conn.close()




def check_filename_duplicate(filename):
    """检查文件名是否已存在，返回 (is_duplicate, existing_record)"""
    conn = get_connection()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT id, filename, status, created_at FROM document_imports WHERE filename=%s ORDER BY id DESC LIMIT 1",
            (filename,)
        )
        row = cur.fetchone()
        return (row is not None, row)
    except Exception as e:
        print(f"[DocImport] check_filename_duplicate failed: {e}")
        return (False, None)
    finally:
        cur.close()
        conn.close()

# ─── API 路由 ───────────────────────────────────────────────────

@doc_import_bp.route('/check-duplicate', methods=['GET'])
def check_duplicate():
    """检查文件名是否已存在（供前端选择文件后预检）"""
    filename = request.args.get('filename', '')
    if not filename:
        return jsonify({'error': '缺少 filename 参数'}), 400

    is_dup, record = check_filename_duplicate(filename)
    if is_dup:
        return jsonify({
            'duplicate': True,
            'filename': filename,
            'existing_id': record['id'],
            'existing_status': record['status'],
            'existing_created_at': str(record['created_at']),
            'message': f'文件名 "{filename}" 已存在（状态：{record["status"]}），请勿重复导入'
        })
    else:
        return jsonify({'duplicate': False, 'filename': filename})

@doc_import_bp.route('/import', methods=['POST'])
def upload_document():
    """上传并导入文档"""
    try:
        # 检查文件
        if 'files' not in request.files:
            return jsonify({'error': '未上传文件'}), 400
        
        files = request.files.getlist('files')
        character_id = request.form.get('character_id') or None
        created_by = request.form.get('created_by', 'admin')
        publish_to_kb = request.form.get('publish_to_kb', '').lower() == 'true'
        
        if character_id:
            try:
                character_id = int(character_id)
            except:
                character_id = None
        
        results = []
        
        for file in files:
            if file.filename == '':
                continue
            # 检查文件名是否重复
            orig_filename = file.filename
            is_dup, dup_record = check_filename_duplicate(orig_filename)
            if is_dup:
                results.append({
                    'filename': orig_filename,
                    'status': 'duplicate',
                    'message': f'文件名 "{orig_filename}" 已存在（状态：{dup_record["status"]}），请勿重复导入'
                })
                continue
            if not allowed_file(file.filename):
                results.append({'filename': file.filename, 'status': 'error', 'message': '不支持的文件类型'})
                continue
            
            # 保存文件
            filename = secure_filename(file.filename)
            ext = os.path.splitext(filename)[1].lower()
            unique_name = str(uuid.uuid4()) + ext
            file_path = os.path.join(UPLOAD_DIR, unique_name)
            file.save(file_path)
            file_size = os.path.getsize(file_path)
            
            # 插入导入记录
            import_id = insert_document_import(filename, file_path, file_size, created_by, character_id)
            if not import_id:
                results.append({'filename': filename, 'status': 'error', 'message': '数据库记录失败'})
                continue
            
            # 解析文件
            chunks = []
            if ext == '.docx':
                chunks = parse_docx(file_path)
            elif ext == '.pdf':
                chunks = parse_pdf(file_path)
            elif ext == '.pptx':
                chunks = parse_pptx(file_path)
            else:  # .txt, .md
                chunks = parse_txt(file_path)
            
            if not chunks:
                # 更新状态为失败
                conn = get_connection()
                cur = conn.cursor()
                cur.execute("UPDATE document_imports SET status='failed', error_message='解析失败，未提取到内容' WHERE id=%s", (import_id,))
                conn.commit()
                cur.close()
                conn.close()
                results.append({'filename': filename, 'status': 'error', 'message': '解析失败，未提取到内容'})
                continue
            
            # 插入分块
            success = insert_document_chunks(import_id, chunks, character_id)
            if not success:
                results.append({'filename': filename, 'status': 'error', 'message': '插入分块失败'})
                continue
            
            # 如果勾选了"同步到公共知识库"，则写入 knowledge_base 表
            if publish_to_kb:
                conn = get_connection()
                try:
                    cur = conn.cursor()
                    # 合并所有 chunks 为完整文档内容
                    full_content = '\n\n'.join(c if isinstance(c, str) else c.get('answer', c.get('question', '')) for c in chunks)
                    # 文档名称 + 内容摘要作为 question（提升 BM25 检索命中率）
                    content_preview = full_content[:200].replace('\n', ' ').replace('\r', ' ')
                    question = filename + ' - ' + content_preview
                    # 完整内容作为 answer，启用状态
                    sql = """INSERT INTO knowledge_base 
                              (question, answer, category, source_type, source_question_id, character_id, enabled, created_at)
                          VALUES (%s, %s, %s, 'document', %s, %s, 1, NOW())"""
                    cur.execute(sql, (question, full_content, '文档导入', import_id, character_id))
                    conn.commit()
                    cur.close()
                    print(f'[DocImport] Published document to knowledge_base: {filename}')
                except Exception as e:
                    print(f'[DocImport] Publish to KB failed: {e}')
                    conn.rollback()
                finally:
                    conn.close()
            
            # 更新状态为完成
            conn = get_connection()
            cur = conn.cursor()
            cur.execute("UPDATE document_imports SET status='completed', progress=100 WHERE id=%s", (import_id,))
            conn.commit()
            cur.close()
            conn.close()
            
            results.append({'filename': filename, 'status': 'success', 'chunks': len(chunks), 'import_id': import_id})
            
            # Trigger BM25 reindex so new content is searchable
            try:
                import threading
                def _reindex():
                    import time; time.sleep(2)
                    try:
                        requests.post('http://localhost:5001/reload', timeout=5)
                        print(f'[DocImport] Reindex triggered for {filename}')
                    except Exception as e:
                        print(f'[DocImport] Reindex failed: {e}')
                threading.Thread(target=_reindex, daemon=True).start()
            except Exception:
                pass
        
        return jsonify({'results': results})
    
    except Exception as e:
        print(f"[DocImport] 上传文档异常: {e}")
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500


@doc_import_bp.route('/imports', methods=['GET'])
def list_imports():
    """查询导入历史"""
    try:
        conn = get_connection()
        cur = conn.cursor(dictionary=True)
        cur.execute("""SELECT id, filename, status, progress, error_message, 
                              created_at, updated_at, created_by 
                       FROM document_imports 
                       ORDER BY created_at DESC 
                       LIMIT 100""")
        rows = cur.fetchall()
        cur.close()
        conn.close()
        return jsonify(rows)
    except Exception as e:
        return jsonify({'error': str(e)}), 500
