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
            "SELECT id, question, answer, category "
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
