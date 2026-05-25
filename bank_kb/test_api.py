# -*- coding: utf-8 -*-
"""用 Python 测试 Flask API（绕过 PowerShell 解析问题）"""
import urllib.request
import json
import sys

BASE = "<a href="http://127.0.0.1:5001">http://127.0.0.1:5001</a>"

def get(path):
    url = BASE + path
    try:
        req = urllib.request.urlopen(url, timeout=5)
        data = req.read().decode("utf-8")
        return json.loads(data)
    except Exception as e:
        return {"error": str(e)}

print("=" * 60)
print("Testing Flask API endpoints...")
print("=" * 60)

for path in ["/health", "/status"]:
    print(f"\n[GET {path}]")
    result = get(path)
    print(json.dumps(result, ensure_ascii=False, indent=2))

print("\n" + "=" * 60)
print("Done.")
print("=" * 60)
