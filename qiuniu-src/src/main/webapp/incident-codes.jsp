<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.qiuniu.model.User" %>
<%@ page import="com.qiuniu.model.Role" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    boolean isAdmin = "ADMIN".equals(user.getRole().getCode());
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>代码关联管理 - 囚牛</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: "Microsoft YaHei", Arial, sans-serif; background: #f0f2f8; min-height: 100vh; }
        .header { background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%); color: white; padding: 12px 24px; display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
        .header h1 { font-size: 20px; }
        .header .right { display: flex; align-items: center; gap: 12px; }
        .header a { color: rgba(255,255,255,0.85); text-decoration: none; font-size: 14px; }
        .header a:hover { color: white; }

        .container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }
        .page-title { font-size: 24px; color: #2c3e50; margin-bottom: 20px; display: flex; align-items: center; gap: 10px; }
        .page-title span { font-size: 28px; }

        .toolbar { background: white; padding: 15px 20px; border-radius: 8px; margin-bottom: 15px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
        .toolbar select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; min-width: 250px; }
        .toolbar .hint { color: #999; font-size: 13px; }

        .btn { padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; transition: all 0.2s; text-decoration: none; display: inline-block; }
        .btn-primary { background: #3498db; color: white; }
        .btn-primary:hover { background: #2980b9; }
        .btn-success { background: #27ae60; color: white; }
        .btn-success:hover { background: #219a52; }
        .btn-danger { background: #e74c3c; color: white; }
        .btn-danger:hover { background: #c0392b; }
        .btn-secondary { background: #95a5a6; color: white; }
        .btn-secondary:hover { background: #7f8c8d; }
        .btn-warning { background: #f39c12; color: white; }
        .btn-warning:hover { background: #d68910; }

        .card { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); overflow: hidden; }
        .card-header { padding: 15px 20px; border-bottom: 1px solid #eee; font-weight: 600; font-size: 16px; display: flex; justify-content: space-between; align-items: center; }
        .card-body { padding: 0; }

        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; font-size: 14px; }
        th { background: #f8f9fa; font-weight: 600; color: #2c3e50; }
        tr:hover { background: #f8f9fa; }
        .mono { font-family: Consolas, monospace; font-size: 13px; background: #f5f5f5; padding: 2px 6px; border-radius: 3px; }
        .snippet { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

        .actions { display: flex; gap: 8px; }
        .actions .btn { padding: 6px 12px; font-size: 13px; }

        .modal { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; align-items: center; justify-content: center; }
        .modal.show { display: flex; }
        .modal-content { background: white; border-radius: 8px; width: 90%; max-width: 650px; max-height: 90vh; overflow: auto; }
        .modal-header { padding: 15px 20px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
        .modal-header h3 { font-size: 18px; }
        .modal-close { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
        .modal-close:hover { color: #333; }
        .modal-body { padding: 20px; }
        .modal-footer { padding: 15px 20px; border-top: 1px solid #eee; text-align: right; display: flex; justify-content: flex-end; gap: 10px; }

        .form-group { margin-bottom: 15px; }
        .form-group label { display: block; margin-bottom: 5px; font-weight: 500; color: #2c3e50; font-size: 14px; }
        .form-group input, .form-group textarea { width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; font-family: inherit; }
        .form-group textarea { min-height: 70px; resize: vertical; font-family: Consolas, monospace; }
        .form-row { display: flex; gap: 15px; }
        .form-row .form-group { flex: 1; }

        .empty { text-align: center; padding: 60px 20px; color: #999; }
        .empty-icon { font-size: 48px; margin-bottom: 15px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚠️ 历史教训 - 代码关联管理</h1>
        <div class="right">
            <a href="<%= request.getContextPath() %>/home.jsp">🏠 功能中心</a>
            <span style="color:rgba(255,255,255,0.3)">|</span>
            <a href="<%= request.getContextPath() %>/incidents.jsp">⚠️ 事故列表</a>
            <span style="color:rgba(255,255,255,0.3)">|</span>
            <a href="<%= request.getContextPath() %>/logout">退出登录</a>
        </div>
    </div>

    <div class="container">
        <div class="page-title">
            <span>🔗</span> 事故代码关联管理
        </div>

        <div class="toolbar">
            <label style="font-weight:600; white-space:nowrap;">选择事故：</label>
            <select id="incidentSelect" onchange="onIncidentChange()">
                <option value="">-- 请选择事故 --</option>
            </select>
            <span class="hint" id="hintText">选择事故后查看关联的代码仓/文件</span>
        </div>

        <div class="card">
            <div class="card-header">
                <span>代码关联列表</span>
                <button class="btn btn-success" onclick="showAddModal()" id="addBtn" disabled>➕ 添加关联</button>
            </div>
            <div class="card-body">
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>代码仓</th>
                            <th>文件路径</th>
                            <th>行号</th>
                            <th>问题代码片段</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="codeLinkTableBody">
                        <tr><td colspan="6" class="empty">请先选择事故</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <!-- Add/Edit Modal -->
    <div class="modal" id="codeLinkModal">
        <div class="modal-content">
            <div class="modal-header">
                <h3 id="modalTitle">添加代码关联</h3>
                <button class="modal-close" onclick="closeModal()">&times;</button>
            </div>
            <div class="modal-body">
                <form id="codeLinkForm">
                    <input type="hidden" id="linkId">
                    <div class="form-group">
                        <label>代码仓名称 *</label>
                        <input type="text" id="repoName" required placeholder="如：qiuniu-src">
                    </div>
                    <div class="form-group">
                        <label>代码仓地址</label>
                        <input type="text" id="repoUrl" placeholder="如：https://github.com/xxx/qiuniu-src">
                    </div>
                    <div class="form-group">
                        <label>文件路径 *</label>
                        <input type="text" id="filePath" required placeholder="如：src/main/java/com/qiuniu/dao/UserDAO.java">
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>起始行号</label>
                            <input type="number" id="lineStart" min="1" placeholder="可选">
                        </div>
                        <div class="form-group">
                            <label>结束行号</label>
                            <input type="number" id="lineEnd" min="1" placeholder="可选">
                        </div>
                    </div>
                    <div class="form-group">
                        <label>问题代码片段</label>
                        <textarea id="codeSnippet" placeholder="粘贴有问题的代码片段..."></textarea>
                    </div>
                    <div class="form-group">
                        <label>修复代码片段</label>
                        <textarea id="fixSnippet" placeholder="粘贴修复后的代码片段..."></textarea>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal()">取消</button>
                <button class="btn btn-primary" onclick="saveCodeLink()">保存</button>
            </div>
        </div>
    </div>

    <script>
        const CTX = '<%= request.getContextPath() %>';
        let currentIncidentId = 0;
        let allIncidents = [];

        function loadIncidents() {
            fetch(CTX + '/api/incidents?action=list&page=1&pageSize=1000')
                .then(r => r.json())
                .then(data => {
                    allIncidents = data.items || [];
                    const sel = document.getElementById('incidentSelect');
                    sel.innerHTML = '<option value="">-- 请选择事故 --</option>';
                    allIncidents.forEach(inc => {
                        const opt = document.createElement('option');
                        opt.value = inc.id;
                        opt.textContent = (inc.incidentNo || '') + ' - ' + (inc.title || '');
                        sel.appendChild(opt);
                    });
                })
                .catch(err => console.error('加载事故列表失败:', err));
        }

        function onIncidentChange() {
            const sel = document.getElementById('incidentSelect');
            currentIncidentId = parseInt(sel.value) || 0;
            document.getElementById('addBtn').disabled = !currentIncidentId;
            if (!currentIncidentId) {
                document.getElementById('codeLinkTableBody').innerHTML =
                    '<tr><td colspan="6" class="empty">请先选择事故</td></tr>';
                return;
            }
            loadCodeLinks();
        }

        function loadCodeLinks() {
            if (!currentIncidentId) return;
            fetch(CTX + '/api/incidents?action=getCodeLinks&incidentId=' + currentIncidentId)
                .then(r => r.json())
                .then(data => {
                    const tbody = document.getElementById('codeLinkTableBody');
                    const list = data.list || [];
                    if (list.length === 0) {
                        tbody.innerHTML = '<tr><td colspan="6" class="empty"><div class="empty-icon">📭</div>暂无代码关联记录</td></tr>';
                        return;
                    }
                    tbody.innerHTML = list.map(link => {
                        const lineInfo = [link.lineStart, link.lineEnd].filter(Boolean).join('-');
                        const codePreview = (link.codeSnippet || '').replace(/</g, '&lt;').replace(/>/g, '&gt;').substring(0, 80);
                        return '<tr>' +
                            '<td>' + link.id + '</td>' +
                            '<td><span class="mono">' + escapeHtml(link.repoName || '') + '</span></td>' +
                            '<td><span class="mono snippet" title="' + escapeHtml(link.filePath || '') + '">' + escapeHtml(link.filePath || '') + '</span></td>' +
                            '<td>' + (lineInfo || '-') + '</td>' +
                            '<td><span class="snippet" title="' + escapeHtml(link.codeSnippet || '') + '">' + escapeHtml(codePreview) + '</span></td>' +
                            '<td class="actions">' +
                                '<button class="btn btn-warning" onclick="editCodeLink(' + link.id + ')">编辑</button>' +
                                '<button class="btn btn-danger" onclick="deleteCodeLink(' + link.id + ')">删除</button>' +
                            '</td>' +
                        '</tr>';
                    }).join('');
                })
                .catch(err => {
                    console.error(err);
                    document.getElementById('codeLinkTableBody').innerHTML =
                        '<tr><td colspan="6" class="empty">加载失败</td></tr>';
                });
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }

        function showAddModal() {
            if (!currentIncidentId) { alert('请先选择事故'); return; }
            document.getElementById('modalTitle').textContent = '添加代码关联';
            document.getElementById('codeLinkForm').reset();
            document.getElementById('linkId').value = '';
            document.getElementById('codeLinkModal').classList.add('show');
        }

        function editCodeLink(id) {
            fetch(CTX + '/api/incidents?action=getCodeLinks&incidentId=' + currentIncidentId)
                .then(r => r.json())
                .then(data => {
                    const list = data.list || [];
                    const link = list.find(l => l.id === id);
                    if (!link) { alert('记录不存在'); return; }
                    document.getElementById('modalTitle').textContent = '编辑代码关联';
                    document.getElementById('linkId').value = link.id;
                    document.getElementById('repoName').value = link.repoName || '';
                    document.getElementById('repoUrl').value = link.repoUrl || '';
                    document.getElementById('filePath').value = link.filePath || '';
                    document.getElementById('lineStart').value = link.lineStart || '';
                    document.getElementById('lineEnd').value = link.lineEnd || '';
                    document.getElementById('codeSnippet').value = link.codeSnippet || '';
                    document.getElementById('fixSnippet').value = link.fixSnippet || '';
                    document.getElementById('codeLinkModal').classList.add('show');
                });
        }

        function closeModal() {
            document.getElementById('codeLinkModal').classList.remove('show');
        }

        function saveCodeLink() {
            const id = document.getElementById('linkId').value;
            const repoName = document.getElementById('repoName').value.trim();
            const filePath = document.getElementById('filePath').value.trim();
            if (!repoName || !filePath) {
                alert('请填写必填字段（代码仓名称、文件路径）');
                return;
            }
            const params = new URLSearchParams();
            params.append('incidentId', currentIncidentId);
            params.append('repoName', repoName);
            params.append('repoUrl', document.getElementById('repoUrl').value.trim());
            params.append('filePath', filePath);
            params.append('lineStart', document.getElementById('lineStart').value);
            params.append('lineEnd', document.getElementById('lineEnd').value);
            params.append('codeSnippet', document.getElementById('codeSnippet').value);
            params.append('fixSnippet', document.getElementById('fixSnippet').value);

            const action = id ? 'updateCodeLink' : 'addCodeLink';
            if (id) params.append('id', id);

            fetch(CTX + '/api/incidents?action=' + action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(r => r.json())
            .then(result => {
                if (result.ok || result.id) {
                    closeModal();
                    loadCodeLinks();
                } else {
                    alert('保存失败: ' + (result.message || '未知错误'));
                }
            })
            .catch(err => alert('保存失败: ' + err.message));
        }

        function deleteCodeLink(id) {
            if (!confirm('确定要删除这条代码关联记录吗？')) return;
            fetch(CTX + '/api/incidents?action=deleteCodeLink&id=' + id, {
                method: 'POST'
            })
            .then(r => r.json())
            .then(result => {
                if (result.ok) {
                    loadCodeLinks();
                } else {
                    alert('删除失败: ' + (result.message || '未知错误'));
                }
            })
            .catch(err => alert('删除失败: ' + err.message));
        }

        // Init
        loadIncidents();
    </script>
</body>
</html>
