import requests
# -*- coding: utf-8 -*-
"""Document Import Blueprint - 文档导入 API"""

import os
from flask import send_file
from env_config import FLASK_BASE_URL
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
from admin_config_bp import get_document_upload_path

# 创建 Blueprint
doc_import_bp = Blueprint('document_import', __name__)

# 上传文件存储目录（从 config.py 导入 UPLOAD_DIR）
os.makedirs(UPLOAD_DIR, exist_ok=True)

ALLOWED_EXTENSIONS = {'.docx', '.pdf', '.txt', '.md', '.pptx', '.xlsx', '.xls'}

# ─── 工具函数 ───────────────────────────────────────────────────

def allowed_file(filename):
    """检查文件扩展名是否允许"""
    ext = os.path.splitext(filename)[1].lower()
    return ext in ALLOWED_EXTENSIONS


def parse_docx(file_path):
    """全面解析 .docx 文件，返回分段列表

    支持：
    - 正文段落
    - 表格（含嵌套表格、合并单元格）
    - 页眉/页脚（含奇偶页、首页不同）
    - 文本框/形状中的文字（通过 XML 检索 w:txbxContent）
    - 图片替代文本
    """
    try:
        from docx import Document
        from docx.oxml.ns import qn
        doc = Document(file_path)
        chunks = []
        current_chunk = ""

        def _append(text):
            """累加文本，超过 500 字自动分段"""
            nonlocal current_chunk
            if not text:
                return
            current_chunk += text + "\n"
            while len(current_chunk) > 500:
                idx = current_chunk.rfind('\n', 0, 500)
                if idx < 0:
                    idx = 500
                chunks.append(current_chunk[:idx].strip())
                current_chunk = current_chunk[idx:].strip() + "\n"

        def _flush():
            """提交当前累积块"""
            nonlocal current_chunk
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
                current_chunk = ""

        def _extract_paragraphs(para_list):
            """从段落列表提取文本"""
            for para in para_list:
                text = para.text.strip()
                if text:
                    _append(text)

        def _extract_table_recursive(table):
            """递归提取表格（含嵌套表格）"""
            for row in table.rows:
                parts = []
                for cell in row.cells:
                    cell_text = cell.text.strip()
                    if cell_text:
                        parts.append(cell_text)
                    # 递归提取内嵌表格
                    for t in cell.tables:
                        _extract_table_recursive(t)
                if parts:
                    _append(' | '.join(parts))

        def _extract_textbox_xml(body_elem):
            """从 XML 中提取文本框(w:txbxContent)中的文字"""
            for txbx in body_elem.iter(qn('w:txbxContent')):
                for p in txbx.iter(qn('w:p')):
                    texts = [t.text for t in p.iter(qn('w:t')) if t.text]
                    line = ''.join(texts).strip()
                    if line:
                        _append(line)

        # ---- 1. 正文段落 ----
        _extract_paragraphs(doc.paragraphs)

        # ---- 2. 表格 ----
        for table in doc.tables:
            _extract_table_recursive(table)
            _flush()  # 表格间分隔

        # ---- 3. 页眉/页脚 ----
        for section in doc.sections:
            # 首页不同、奇偶页不同的页眉
            headers = [section.header]
            try:
                if section.different_first_page_header_footer:
                    headers.append(section.first_page_header)
            except Exception:
                pass
            try:
                if getattr(section, 'even_page_header', None):
                    headers.append(section.even_page_header)
            except Exception:
                pass
            for h in headers:
                if h and h.paragraphs:
                    _extract_paragraphs(h.paragraphs)
                    # 页眉中也可能有表格
                    for t in h.tables:
                        _extract_table_recursive(t)

            footers = [section.footer]
            try:
                if section.different_first_page_header_footer:
                    footers.append(section.first_page_footer)
            except Exception:
                pass
            try:
                if getattr(section, 'even_page_footer', None):
                    footers.append(section.even_page_footer)
            except Exception:
                pass
            for f in footers:
                if f and f.paragraphs:
                    _extract_paragraphs(f.paragraphs)
                    for t in f.tables:
                        _extract_table_recursive(t)

        # ---- 4. 文本框/形状中的文字（XML 深度检索）----
        _extract_textbox_xml(doc.element.body)
        # 也检查页眉页脚中的文本框
        for section in doc.sections:
            for h in [section.header, getattr(section, 'first_page_header', None),
                       getattr(section, 'even_page_header', None)]:
                if h is not None:
                    _extract_textbox_xml(h._element)
            for f in [section.footer, getattr(section, 'first_page_footer', None),
                       getattr(section, 'even_page_footer', None)]:
                if f is not None:
                    _extract_textbox_xml(f._element)

        # ---- 5. 图片替代文本 ----
        try:
            for shape in doc.inline_shapes:
                if shape.alt_text:
                    _append(f"[图片描述: {shape.alt_text}]")
        except Exception:
            pass

        _flush()

        # 如果最终还是空的，尝试最低级 XML 兜底：提取所有 w:t 文本
        if not chunks:
            all_texts = []
            for t_elem in doc.element.body.iter(qn('w:t')):
                if t_elem.text and t_elem.text.strip():
                    all_texts.append(t_elem.text.strip())
            if all_texts:
                _append(' '.join(all_texts))
                _flush()

        return chunks
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"[DocImport] 解析 .docx 失败: {e}")
        return []


# RapidOCR 引擎（懒加载，OCR 比较慢，按需初始化）
_ocr_engine = None
_ocr_enabled = True  # 可通过环境变量 DISABLE_PDF_OCR=1 关闭 OCR

def _get_ocr_engine():
    """按需加载 RapidOCR 引擎"""
    global _ocr_engine
    if _ocr_engine is None:
        import os
        if os.environ.get('DISABLE_PDF_OCR', '').strip() in ('1', 'true', 'yes'):
            return None
        try:
            from rapidocr_onnxruntime import RapidOCR
            _ocr_engine = RapidOCR()
            print('[DocImport] RapidOCR loaded OK')
        except ImportError as e:
            print(f'[DocImport] RapidOCR not available: {e}')
            _ocr_engine = None
    return _ocr_engine


def parse_pdf(file_path):
    """解析 .pdf 文件，返回分段列表

    支持（分层降级）：
    - pypdf 文字提取（文字版 PDF）
    - fitz/PyMuPDF 文字提取（部分 PDF pypdf 拿不到但 fitz 能拿到）
    - RapidOCR 图片 OCR（扫描版 PDF，无文字层）
    - 按段落自动分段，每段上限 500 字符
    """
    try:
        import pypdf

        chunks = []
        current_chunk = ""

        def _append(text):
            nonlocal current_chunk
            if not text:
                return
            current_chunk += text.strip() + "\n"
            while len(current_chunk) > 500:
                idx = current_chunk.rfind('\n', 0, 500)
                if idx < 0:
                    idx = 500
                chunks.append(current_chunk[:idx].strip())
                current_chunk = current_chunk[idx:].strip() + "\n"

        def _flush():
            nonlocal current_chunk
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
                current_chunk = ""

        # ---- Layer 1: pypdf 文字提取 ----
        with open(file_path, 'rb') as f:
            reader = pypdf.PdfReader(f)
            for page in reader.pages:
                text = page.extract_text()
                if text and text.strip():
                    _append(text)
            _flush()

        # ---- Layer 2: fitz 文字提取（pypdf 无内容时） ----
        if not chunks:
            try:
                import fitz
                doc = fitz.open(file_path)
                for page in doc:
                    text = page.get_text().strip()
                    if text:
                        _append(text)
                doc.close()
                _flush()
            except ImportError:
                pass

        # ---- Layer 3: RapidOCR（扫描版 PDF，两个文字层都空时） ----
        if not chunks:
            ocr = _get_ocr_engine()
            if ocr is not None:
                try:
                    import fitz
                    doc = fitz.open(file_path)
                    for page in doc:
                        mat = fitz.Matrix(2.0, 2.0)  # 144 DPI
                        pix = page.get_pixmap(matrix=mat)
                        img_data = pix.tobytes('png')
                        result, _ = ocr(img_data)
                        if result:
                            page_text = ' '.join([r[1] for r in result])
                            _append(page_text)
                    doc.close()
                    _flush()
                    if chunks:
                        print(f'[DocImport] RapidOCR extracted {len(chunks)} chunks')
                except Exception as e:
                    print(f'[DocImport] RapidOCR failed: {e}')

        return chunks
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f'[DocImport] 解析 .pdf 失败: {e}')
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

def parse_xlsx(file_path):
    """全面解析 .xlsx / .xls 文件，返回分段列表

    支持：
    - 所有工作表（按 sheet 名 + 内容分段）
    - 表格数据（逐行，保留表头上下文）
    - 合并单元格
    - 命名区域（named ranges）
    - 工作表页眉页脚
    - 批注内容
    """
    try:
        import openpyxl
        from openpyxl.utils import get_column_letter
        from openpyxl.cell.text import InlineFont
        from openpyxl.cell.rich_text import TextBlock, CellRichText

        wb = openpyxl.load_workbook(file_path, data_only=True, rich_text=False)
        chunks = []
        current_chunk = ""

        def _append(text):
            nonlocal current_chunk
            if not text:
                return
            current_chunk += text + "\n"
            while len(current_chunk) > 500:
                idx = current_chunk.rfind('\n', 0, 500)
                if idx < 0:
                    idx = 500
                chunks.append(current_chunk[:idx].strip())
                current_chunk = current_chunk[idx:].strip() + "\n"

        def _flush():
            nonlocal current_chunk
            if current_chunk.strip():
                chunks.append(current_chunk.strip())
                current_chunk = ""

        # ---- 1. 解析每个工作表 ----
        for sheet_name in wb.sheetnames:
            ws = wb[sheet_name]

            # 跳过空表
            if ws.max_row < 1 or ws.max_column < 1:
                continue

            # 收集合并单元格的值（合并单元格只在左上角有值，其他为空）
            merged_cell_values = {}
            for merged_range in ws.merged_cells.ranges:
                top_left_cell = ws.cell(merged_range.min_row, merged_range.min_col)
                val = top_left_cell.value
                if val is not None:
                    val_str = str(val).strip()
                    if val_str:
                        for row in range(merged_range.min_row, merged_range.max_row + 1):
                            for col in range(merged_range.min_col, merged_range.max_col + 1):
                                merged_cell_values[(row, col)] = val_str

            # ---- 表头行 ----
            header = []
            for col in range(1, ws.max_column + 1):
                cell_val = merged_cell_values.get((1, col), ws.cell(1, col).value)
                header.append(str(cell_val) if cell_val is not None else '')
            has_header = any(h for h in header)

            # ---- 数据行 ----
            row_start = 2 if has_header else 1
            for row_idx in range(row_start, ws.max_row + 1):
                row_parts = []
                for col_idx in range(1, ws.max_column + 1):
                    val = merged_cell_values.get((row_idx, col_idx), ws.cell(row_idx, col_idx).value)
                    if val is None:
                        val = ''
                    else:
                        # 处理富文本（CellRichText）
                        if isinstance(val, CellRichText):
                            val = ''.join(
                                t.text if isinstance(t, TextBlock) else str(t)
                                for t in val
                            )
                        elif hasattr(val, 'text'):  # InlineFont 等
                            val = str(val.text) if val.text else ''
                        else:
                            val = str(val).strip()
                    row_parts.append(val)

                # 全空行跳过
                if not any(p.strip() for p in row_parts):
                    continue

                # 如果有表头，用 "列名: 值" 格式
                if has_header:
                    row_text = ', '.join(
                        f"{h}={v}" if h and v else v
                        for h, v in zip(header, row_parts) if v
                    )
                else:
                    row_text = ' | '.join(v for v in row_parts if v)

                if row_text:
                    _append(row_text)

            _flush()

            # ---- 工作表备注（批注）----
            try:
                for row in ws.iter_rows():
                    for cell in row:
                        comment = cell.comment
                        if comment and comment.text and comment.text.strip():
                            _append(f"[{sheet_name} {cell.coordinate} 备注] {comment.text.strip()}")
            except Exception:
                pass

            _flush()

        # ---- 2. 命名区域（named ranges）----
        try:
            for name, defn in wb.defined_names.items():
                try:
                    dests = list(defn.destinations)
                    for sheet, coord in dests:
                        if sheet in wb:
                            ws = wb[sheet]
                            vals = []
                            for row in ws[coord]:
                                for cell in row:
                                    if cell.value is not None:
                                        vals.append(str(cell.value))
                            if vals:
                                _append(f"[命名区域: {name}] {' | '.join(vals)}")
                except Exception:
                    pass
            _flush()
        except Exception:
            pass

        return chunks
    except Exception as e:
        import traceback
        traceback.print_exc()
        print(f"[DocImport] 解析 .xlsx/.xls 失败: {e}")
        return []


def _make_relative_path(abs_path):
    """将绝对路径转为相对于 UPLOAD_DIR 的相对路径，便于跨机器迁移。
    
    存储格式：相对于 UPLOAD_DIR 的相对路径（如 "a1b2c3d.pdf"）
    下载时通过 os.path.join(UPLOAD_DIR, relative_path) 还原为绝对路径。
    """
    upload_dir = get_document_upload_path()
    try:
        rel = os.path.relpath(abs_path, upload_dir)
        # 如果 rel 以 .. 开头说明不在 UPLOAD_DIR 下，降级存绝对路径
        if rel.startswith('..'):
            return abs_path
        return rel
    except Exception:
        return abs_path


def insert_document_import(filename, file_path, file_size, created_by, character_id=None):
    """插入 document_imports 记录，返回 import_id
    
    file_path 存相对路径（相对于 UPLOAD_DIR），便于内网迁移。
    """
    conn = get_connection()
    try:
        cur = conn.cursor()
        # 存相对路径，避免硬编码 Windows 绝对路径
        rel_path = _make_relative_path(file_path)
        sql = """INSERT INTO document_imports 
                  (filename, file_path, file_size, status, created_by, character_id, created_at)
                  VALUES (%s, %s, %s, 'pending', %s, %s, NOW())"""
        cur.execute(sql, (filename, rel_path, file_size, created_by, character_id))
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
            orig_filename = file.filename  # 保留原始文件名（中文）
            # 从原始文件名提取扩展名（secure_filename会剥离中文，导致只有扩展名残留）
            raw_ext = os.path.splitext(orig_filename)[1].lower()
            # safe_ext 用于文件类型判断
            ext = raw_ext
            # 唯一文件名：UUID + 安全扩展名
            safe_ext = raw_ext if raw_ext in ALLOWED_EXTENSIONS else '.bin'
            unique_name = str(uuid.uuid4()) + safe_ext
            upload_dir = get_document_upload_path()
            os.makedirs(upload_dir, exist_ok=True)
            file_path = os.path.join(upload_dir, unique_name)
            file.save(file_path)
            file_size = os.path.getsize(file_path)
            
            # 从安全扩展名中提取扩展用于文件类型判断
            # (已在上面统一为 ext = raw_ext)
            
            # 插入导入记录（使用原始文件名）
            import_id = insert_document_import(orig_filename, file_path, file_size, created_by, character_id)
            if not import_id:
                results.append({'filename': orig_filename, 'status': 'error', 'message': '数据库记录失败'})
                continue
            
            # 解析文件
            chunks = []
            if ext == '.docx':
                chunks = parse_docx(file_path)
            elif ext == '.pdf':
                chunks = parse_pdf(file_path)
            elif ext == '.pptx':
                chunks = parse_pptx(file_path)
            elif ext in ('.xlsx', '.xls'):
                chunks = parse_xlsx(file_path)
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
                results.append({'filename': orig_filename, 'status': 'error', 'message': '解析失败，未提取到内容'})
                continue
            
            # 插入分块
            success = insert_document_chunks(import_id, chunks, character_id)
            if not success:
                results.append({'filename': orig_filename, 'status': 'error', 'message': '插入分块失败'})
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
                    question = orig_filename + ' - ' + content_preview
                    # 完整内容作为 answer，启用状态
                    sql = """INSERT INTO knowledge_base 
                              (question, answer, category, source_type, source_question_id, character_id, enabled, created_at)
                          VALUES (%s, %s, %s, 'document', %s, %s, 1, NOW())"""
                    cur.execute(sql, (question, full_content, '文档导入', import_id, character_id))
                    conn.commit()
                    cur.close()
                    print(f'[DocImport] Published document to knowledge_base: {orig_filename}')
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
            
            results.append({'filename': orig_filename, 'status': 'success', 'chunks': len(chunks), 'import_id': import_id, 'message': f'成功提取 {len(chunks)} 个分块'})
        
        # 所有文件处理完后，一次性触发 BM25 重建索引
        if results:
            try:
                import threading
                def _reindex():
                    import time; time.sleep(3)
                    try:
                        resp = requests.post(f'{FLASK_BASE_URL}/reload', timeout=10)
                        print(f'[DocImport] Reindex triggered, status={resp.status_code}')
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


def _resolve_upload_path(db_path):
    """将 DB 中的 file_path 解析为绝对路径。
    
    - 绝对路径（旧数据）：直接使用
    - 相对路径（新数据）：拼接 UPLOAD_DIR
    """
    if not db_path:
        return None
    # 使用 os.path 判断绝对/相对路径（避免 pathlib 依赖）
    if os.path.isabs(db_path):
        return db_path
    # 相对路径：拼接 UPLOAD_DIR
    upload_dir = get_document_upload_path()
    return os.path.join(upload_dir, db_path)


@doc_import_bp.route('/<int:import_id>/download', methods=['GET'])
def download_document(import_id):
    """
    下载原始文档文件。
    用于知识库搜索结果中提供文档下载。
    """
    try:
        conn = get_connection()
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT id, filename, file_path FROM document_imports WHERE id = %s AND status = 'completed'",
            (import_id,)
        )
        doc = cur.fetchone()
        cur.close()
        conn.close()

        if not doc or not doc.get('file_path'):
            return jsonify({'error': 'Document not found'}), 404

        file_path = doc['file_path']
        # 解析为绝对路径（支持相对路径和绝对路径）
        abs_path = _resolve_upload_path(file_path)
        if not abs_path or not os.path.isfile(abs_path):
            return jsonify({'error': 'File not found on disk: ' + str(abs_path)}), 404

        # 原始文件名（保留中文）
        orig_name = doc['filename']
        return send_file(
            abs_path,
            as_attachment=True,
            download_name=orig_name,
            mimetype='application/octet-stream'
        )
    except Exception as e:
        traceback.print_exc()
        return jsonify({'error': str(e)}), 500
