# -*- coding: utf-8 -*-
"""Admin Config Blueprint - 系统配置管理"""

import os
import sys
import traceback
from datetime import datetime
from flask import Blueprint, request, jsonify

sys.path.insert(0, os.path.dirname(__file__))
from config import UPLOAD_DIR
from db_loader import get_connection

bp = Blueprint('admin_config', __name__, url_prefix='/api/admin')

def get_config(key, default=None):
    """从 system_config 表读取配置项"""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("SELECT config_value FROM system_config WHERE config_key = %s", (key,))
        row = cursor.fetchone()
        if row and row['config_value']:
            return row['config_value']
        return default
    finally:
        cursor.close()
        conn.close()

def set_config(key, value):
    """写入配置项到 system_config 表"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            "INSERT INTO system_config (config_key, config_value, updated_at) "
            "VALUES (%s, %s, NOW()) "
            "ON DUPLICATE KEY UPDATE config_value = %s, updated_at = NOW()",
            (key, value, value)
        )
        conn.commit()
        return True
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        cursor.close()
        conn.close()

def get_document_upload_path():
    """获取文档上传路径（优先从 DB 配置，否则返回默认值）"""
    db_path = get_config('document_upload_path')
    if db_path:
        return db_path
    return UPLOAD_DIR

# ─── API 路由 ─────────────────────────────────────────────

@bp.route('/config', methods=['GET'])
def get_all_configs():
    """获取所有系统配置"""
    conn = get_connection()
    cursor = conn.cursor(dictionary=True)
    try:
        cursor.execute("SELECT config_key, config_value, updated_at FROM system_config")
        rows = cursor.fetchall()
        configs = {}
        for row in rows:
            configs[row['config_key']] = {
                'value': row['config_value'],
                'updated_at': row['updated_at'].isoformat() if row['updated_at'] else None
            }
        # 保证总有 document_upload_path
        if 'document_upload_path' not in configs:
            configs['document_upload_path'] = {
                'value': UPLOAD_DIR,
                'updated_at': None
            }
        return jsonify({'success': True, 'data': configs})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)})
    finally:
        cursor.close()
        conn.close()

@bp.route('/config/<key>', methods=['GET'])
def get_config_api(key):
    """获取单个配置项"""
    try:
        value = get_config(key)
        if value is None:
            # 尝试返回默认值
            if key == 'document_upload_path':
                value = UPLOAD_DIR
        return jsonify({'success': True, 'data': {key: value}})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)})

@bp.route('/config/<key>', methods=['POST'])
def set_config_api(key):
    """设置配置项"""
    try:
        data = request.get_json(force=True)
        value = data.get('value', '')
        if not value or not value.strip():
            return jsonify({'success': False, 'message': '值不能为空'})
        
        # 验证路径：如果是文档上传路径，创建目录
        if key == 'document_upload_path':
            try:
                os.makedirs(value, exist_ok=True)
            except Exception as e:
                return jsonify({'success': False, 'message': f'目录创建失败: {str(e)}'})
        
        set_config(key, value.strip())
        return jsonify({'success': True, 'message': '保存成功'})
    except Exception as e:
        return jsonify({'success': False, 'message': str(e)})

@bp.route('/config/<key>', methods=['DELETE'])
def delete_config_api(key):
    """删除配置项（恢复默认值）"""
    conn = get_connection()
    cursor = conn.cursor()
    try:
        cursor.execute("DELETE FROM system_config WHERE config_key = %s", (key,))
        conn.commit()
        return jsonify({'success': True, 'message': '已恢复默认值'})
    except Exception as e:
        conn.rollback()
        return jsonify({'success': False, 'message': str(e)})
    finally:
        cursor.close()
        conn.close()
