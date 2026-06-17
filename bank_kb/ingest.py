# -*- coding: utf-8 -*-
"""Document Ingest Module - 导入文件到知识库
支持格式：.md, .txt, .pdf, .docx
"""

import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from db_loader import get_connection

# -- 文档解析器 --

def parse_txt(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return f.read()

def parse_md(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        return f.read()

def parse_pdf(filepath):
    try:
        from PyPDF2 import PdfReader
        reader = PdfReader(filepath)
        text = '\n'.join(page.extract_text() or '' for page in reader.pages)
        return text
    except ImportError:
        print('[Ingest] PyPDF2 not installed, skipping PDF')
        return ''

def parse_docx(filepath):
    try:
        from docx import Document
        doc = Document(filepath)
        text = '\n'.join(p.text for p in doc.paragraphs)
        return text
    except ImportError:
        print('[Ingest] python-docx not installed, skipping DOCX')
        return ''

PARSERS = {'.txt': parse_txt, '.md': parse_md, '.pdf': parse_pdf, '.docx': parse_docx}

# -- 文本分块 --

def chunk_text(text, chunk_size=2000, overlap=200):
    if len(text) <= chunk_size:
        return [text]
    chunks = []
    paragraphs = re.split(r'\n\s*\n', text)
    current = ''
    for para in paragraphs:
        if len(current) + len(para) + 2 > chunk_size and current:
            chunks.append(current.strip())
            current = current[-overlap:] + '\n\n' + para
        else:
            current = (current + '\n\n' + para) if current else para
    if current.strip():
        chunks.append(current.strip())
    return chunks

# -- 导入到数据库 --

def ingest_file(filepath, category='导入文档'):
    ext = os.path.splitext(filepath)[1].lower()
    parser = PARSERS.get(ext)
    if not parser:
        print(f'[Ingest] Unsupported format: {ext}')
        return 0
    text = parser(filepath)
    if not text.strip():
        print(f'[Ingest] Empty content: {filepath}')
        return 0
    filename = os.path.basename(filepath)
    chunks = chunk_text(text)
    count = 0
    conn = get_connection()
    cursor = conn.cursor()
    for i, chunk in enumerate(chunks):
        summary = chunk[:100].replace('\n', ' ').replace('\r', '')
        question = f'[{filename}] 第{i+1}段：{summary}...'
        cursor.execute(
            'INSERT INTO knowledge_base (question, answer, category, enabled) VALUES (%s, %s, %s, 1)',
            (question, chunk, category)
        )
        count += 1
    conn.commit()
    cursor.close()
    conn.close()
    print(f'[Ingest] Imported {count} chunks from {filename}')
    return count

def ingest_directory(dirpath, category=None):
    total = 0
    for root, dirs, files in os.walk(dirpath):
        for f in files:
            ext = os.path.splitext(f)[1].lower()
            if ext in PARSERS:
                filepath = os.path.join(root, f)
                cat = category or os.path.splitext(f)[0]
                total += ingest_file(filepath, category=cat)
    print(f'[Ingest] Total imported: {total} chunks')
    return total

# -- CLI --

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python ingest.py <file_or_directory> [category]')
        sys.exit(1)
    target = sys.argv[1]
    cat = sys.argv[2] if len(sys.argv) > 2 else None
    if os.path.isfile(target):
        ingest_file(target, category=cat)
    elif os.path.isdir(target):
        ingest_directory(target, category=cat)
    else:
        print(f'Path not found: {target}')