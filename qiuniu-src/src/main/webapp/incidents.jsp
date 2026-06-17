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
    <title>历史教训 - 囚牛</title>
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
        .toolbar input[type="text"] { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; width: 200px; font-size: 14px; }
        .toolbar select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
        
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
        .card-header { padding: 15px 20px; border-bottom: 1px solid #eee; font-weight: 600; font-size: 16px; }
        .card-body { padding: 0; }
        
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #eee; font-size: 14px; }
        th { background: #f8f9fa; font-weight: 600; color: #2c3e50; }
        tr:hover { background: #f8f9fa; }
        
        .badge { padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
        .badge-critical { background: #fadbd8; color: #c0392b; }
        .badge-high { background: #fdebd0; color: #d68910; }
        .badge-medium { background: #d6eaf8; color: #2980b9; }
        .badge-low { background: #d5f5e3; color: #27ae60; }
        .badge-active { background: #d5f5e3; color: #27ae60; }
        .badge-resolved { background: #ebedef; color: #7f8c8d; }
        
        .modal { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; align-items: center; justify-content: center; }
        .modal.show { display: flex; }
        .modal-content { background: white; border-radius: 8px; width: 90%; max-width: 600px; max-height: 90vh; overflow: auto; }
        .modal-header { padding: 15px 20px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; }
        .modal-header h3 { font-size: 18px; }
        .modal-close { background: none; border: none; font-size: 24px; cursor: pointer; color: #999; }
        .modal-close:hover { color: #333; }
        .modal-body { padding: 20px; }
        .modal-footer { padding: 15px 20px; border-top: 1px solid #eee; text-align: right; gap: 10px; display: flex; justify-content: flex-end; }
        .code-section { margin-top: 18px; border-top: 2px dashed #ddd; padding-top: 15px; }
        .code-section h4 { font-size: 14px; color: #e67e22; margin-bottom: 12px; }
        .form-row { display: flex; gap: 12px; }
        .form-group.half { flex: 1; }
        .inline-fields { display: flex; gap: 8px; }
        
        .form-group { margin-bottom: 15px; }
        .form-group label { display: block; margin-bottom: 5px; font-weight: 500; color: #2c3e50; }
        .form-group input, .form-group textarea, .form-group select { width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; font-family: inherit; }
        .form-group textarea { min-height: 80px; resize: vertical; }
        
        .empty { text-align: center; padding: 60px 20px; color: #999; }
        .empty-icon { font-size: 48px; margin-bottom: 15px; }
        
        .actions { display: flex; gap: 8px; }
        .actions .btn { padding: 6px 12px; font-size: 13px; }
        
        .search-bar { display: flex; gap: 10px; flex: 1; }
        .search-bar input { flex: 1; }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚠️ 历史教训 - 投产事故管理</h1>
        <div class="right">
            <a href="<%= request.getContextPath() %>/home.jsp">🏠 功能中心</a>
            <span class="divider">|</span>
            <a href="<%= request.getContextPath() %>/logout">退出登录</a>
        </div>
    </div>
    
    <div class="container">
        <div class="page-title">
            <span>⚠️</span> 投产事故历史教训库
        </div>
        
        <div class="toolbar">
            <div class="search-bar">
                <input type="text" id="searchKeyword" placeholder="搜索事故编号、描述...">
                <select id="searchSeverity">
                    <option value="">全部严重级别</option>
                    <option value="critical">Critical</option>
                    <option value="high">High</option>
                    <option value="medium">Medium</option>
                    <option value="low">Low</option>
                </select>
                <select id="searchStatus">
                    <option value="">全部状态</option>
                    <option value="active">Active</option>
                    <option value="resolved">Resolved</option>
                </select>
                <button class="btn btn-primary" onclick="searchIncidents()">🔍 搜索</button>
            </div>
            <button class="btn btn-success" onclick="showAddModal()">➕ 添加事故</button>
        </div>
        
        <div class="card">
            <div class="card-header">事故列表</div>
            <div class="card-body">
                <table>
                    <thead>
                        <tr>
                            <th>事故编号</th>
                            <th>事故类型</th>
                            <th>严重级别</th>
                            <th>发生时间</th>
                            <th>状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="incidentTableBody">
                        <tr><td colspan="6" class="empty">加载中...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    
    <!-- Add/Edit Modal -->
    <div class="modal" id="incidentModal">
        <div class="modal-content">
            <div class="modal-header">
                <h3 id="modalTitle">添加事故</h3>
                <button class="modal-close" onclick="closeModal()">&times;</button>
            </div>
            <div class="modal-body">
                <form id="incidentForm">
                    <input type="hidden" id="incidentId">
                    <div class="form-group">
                        <label>事故编号 *</label>
                        <input type="text" id="incidentNo" required placeholder="如: INC-2024-001">
                    </div>
                    <div class="form-group">
                        <label>事故类型 *</label>
                        <select id="incidentType" required>
                            <option value="">请选择</option>
                            <option value="数据异常">数据异常</option>
                            <option value="性能问题">性能问题</option>
                            <option value="功能缺陷">功能缺陷</option>
                            <option value="安全漏洞">安全漏洞</option>
                            <option value="配置错误">配置错误</option>
                            <option value="其他">其他</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>严重级别 *</label>
                        <select id="severity" required>
                            <option value="">请选择</option>
                            <option value="critical">Critical - 严重</option>
                            <option value="high">High - 高</option>
                            <option value="medium">Medium - 中</option>
                            <option value="low">Low - 低</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>发生时间 *</label>
                        <input type="datetime-local" id="occurrenceTime" required>
                    </div>
                    <div class="form-group">
                        <label>根因分析 *</label>
                        <textarea id="rootCause" required placeholder="分析事故发生的根本原因..."></textarea>
                    </div>
                    <div class="form-group">
                        <label>解决方案 *</label>
                        <textarea id="solution" required placeholder="记录解决措施和预防方案..."></textarea>
                    </div>
                    <div class="form-group">
                        <label>状态</label>
                        <select id="status">
                            <option value="active">Active - 进行中</option>
                            <option value="resolved">Resolved - 已解决</option>
                        </select>
                    </div>
                    
                    <div class="code-section">
                        <h4>📁 关联代码（可选）</h4>
                        <input type="hidden" id="codeLinkId">
                        <div class="form-row">
                            <div class="form-group half">
                                <label>代码仓</label>
                                <input type="text" id="codeRepoName" placeholder="如: qiuniu-src">
                            </div>
                            <div class="form-group half">
                                <label>仓库 URL</label>
                                <input type="text" id="codeRepoUrl" placeholder="https://github.com/...">
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="form-group half">
                                <label>代码文件路径</label>
                                <input type="text" id="codeFilePath" placeholder="如: src/main/java/X.java">
                            </div>
                            <div class="form-group half">
                                <label>行号范围</label>
                                <div class="inline-fields">
                                    <input type="number" id="codeLineStart" placeholder="起始行" style="width:48%">
                                    <input type="number" id="codeLineEnd" placeholder="结束行" style="width:48%">
                                </div>
                            </div>
                        </div>
                        <div class="form-group">
                            <label>问题代码片段</label>
                            <textarea id="codeSnippet" placeholder="有问题的代码..." rows="3"></textarea>
                        </div>
                        <div class="form-group">
                            <label>修复代码片段</label>
                            <textarea id="codeFixSnippet" placeholder="修复后的代码..." rows="3"></textarea>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal()">取消</button>
                <button class="btn btn-primary" onclick="saveIncident()">保存</button>
            </div>
        </div>
    </div>
    
    <script>
        const CTX = '<%= request.getContextPath() %>';
        let currentPage = 1;
        
        function loadIncidents(page = 1) {
            currentPage = page;
            const keyword = document.getElementById('searchKeyword').value;
            const severity = document.getElementById('searchSeverity').value;
            const status = document.getElementById('searchStatus').value;
            
            let url = CTX + '/api/incidents?action=list&page=' + page + '&pageSize=10';
            if (keyword) url += '&keyword=' + encodeURIComponent(keyword);
            if (severity) url += '&severity=' + encodeURIComponent(severity);
            if (status) url += '&status=' + encodeURIComponent(status);
            
            fetch(url)
                .then(r => r.json())
                .then(data => {
                    const tbody = document.getElementById('incidentTableBody');
                    if (!data.list || data.list.length === 0) {
                        tbody.innerHTML = '<tr><td colspan="6" class="empty"><div class="empty-icon">📭</div>暂无事故记录</td></tr>';
                        return;
                    }
                    tbody.innerHTML = data.list.map(item => {
                        const severity = (item.severity || 'low').toUpperCase();
                        const time = item.occurrenceTime ? new Date(item.occurrenceTime).toLocaleString('zh-CN') : '';
                        const statusBadge = item.status === 'active' ? 'Active' : 'Resolved';
                        const statusClass = item.status === 'active' ? 'active' : 'resolved';
                        return '<tr>' +
                            '<td><strong>' + (item.incidentNo || '') + '</strong></td>' +
                            '<td>' + (item.incidentType || '') + '</td>' +
                            '<td><span class="badge badge-' + (item.severity || 'low') + '">' + severity + '</span></td>' +
                            '<td>' + time + '</td>' +
                            '<td><span class="badge badge-' + statusClass + '">' + statusBadge + '</span></td>' +
                            '<td class="actions">' +
                                '<button class="btn btn-warning" onclick="editIncident(' + item.id + ')">编辑</button>' +
                                '<button class="btn btn-danger" onclick="deleteIncident(' + item.id + ')">删除</button>' +
                            '</td>' +
                        '</tr>';
                    }).join('');
                })
                .catch(err => {
                    console.error(err);
                    document.getElementById('incidentTableBody').innerHTML = '<tr><td colspan="6" class="empty">加载失败</td></tr>';
                });
        }
        
        function searchIncidents() {
            loadIncidents(1);
        }
        
        function showAddModal() {
            document.getElementById('modalTitle').textContent = '添加事故';
            document.getElementById('incidentForm').reset();
            document.getElementById('incidentId').value = '';
            document.getElementById('codeLinkId').value = '';
            document.getElementById('incidentModal').classList.add('show');
        }
        
        function editIncident(id) {
            fetch(CTX + '/api/incidents?action=get&id=' + id)
                .then(r => r.json())
                .then(data => {
                    document.getElementById('modalTitle').textContent = '编辑事故';
                    document.getElementById('incidentId').value = data.id;
                    document.getElementById('incidentNo').value = data.incidentNo || '';
                    document.getElementById('incidentType').value = data.incidentType || '';
                    document.getElementById('severity').value = data.severity || '';
                    document.getElementById('rootCause').value = data.rootCause || '';
                    document.getElementById('solution').value = data.solution || '';
                    document.getElementById('status').value = data.status || 'active';
                    if (data.occurrenceTime) {
                        document.getElementById('occurrenceTime').value = data.occurrenceTime.slice(0, 16);
                    }
                    // Load code link data
                    fetch(CTX + '/api/incidents?action=getCodeLinks&incidentId=' + id)
                        .then(r => r.json())
                        .then(cd => {
                            if (cd.list && cd.list.length > 0) {
                                const link = cd.list[0];
                                document.getElementById('codeLinkId').value = link.id;
                                document.getElementById('codeRepoName').value = link.repoName || '';
                                document.getElementById('codeRepoUrl').value = link.repoUrl || '';
                                document.getElementById('codeFilePath').value = link.filePath || '';
                                document.getElementById('codeLineStart').value = link.lineStart || '';
                                document.getElementById('codeLineEnd').value = link.lineEnd || '';
                                document.getElementById('codeSnippet').value = link.codeSnippet || '';
                                document.getElementById('codeFixSnippet').value = link.fixSnippet || '';
                            }
                        });
                    document.getElementById('incidentModal').classList.add('show');
                });
        }
        
        function closeModal() {
            document.getElementById('incidentModal').classList.remove('show');
        }
        
        function saveIncident() {
            const id = document.getElementById('incidentId').value;
            const data = {
                incidentNo: document.getElementById('incidentNo').value,
                incidentType: document.getElementById('incidentType').value,
                severity: document.getElementById('severity').value,
                occurrenceTime: document.getElementById('occurrenceTime').value,
                rootCause: document.getElementById('rootCause').value,
                solution: document.getElementById('solution').value,
                status: document.getElementById('status').value
            };
            
            if (!data.incidentNo || !data.incidentType || !data.severity || !data.occurrenceTime) {
                alert('请填写必填字段');
                return;
            }
            
            const action = id ? 'update' : 'create';
            const formData = new FormData();
            formData.append('action', action);
            if (id) formData.append('id', id);
            Object.keys(data).forEach(k => formData.append(k, data[k]));
            
            const codeFilePath = document.getElementById('codeFilePath').value;
            
            fetch(CTX + '/api/incidents', {
                method: 'POST',
                body: formData
            })
            .then(r => r.json())
            .then(result => {
                if (result.ok) {
                    // 同时保存或更新代码关联
                    if (codeFilePath) {
                        const incidentId = id || result.id;
                        const codeLinkId = document.getElementById('codeLinkId').value;
                        const linkForm = new FormData();
                        if (codeLinkId) {
                            linkForm.append('action', 'updateCodeLink');
                            linkForm.append('id', codeLinkId);
                        } else {
                            linkForm.append('action', 'addCodeLink');
                        }
                        linkForm.append('incidentId', incidentId);
                        linkForm.append('repoName', document.getElementById('codeRepoName').value);
                        linkForm.append('repoUrl', document.getElementById('codeRepoUrl').value);
                        linkForm.append('filePath', codeFilePath);
                        linkForm.append('lineStart', document.getElementById('codeLineStart').value);
                        linkForm.append('lineEnd', document.getElementById('codeLineEnd').value);
                        linkForm.append('codeSnippet', document.getElementById('codeSnippet').value);
                        linkForm.append('fixSnippet', document.getElementById('codeFixSnippet').value);
                        
                        fetch(CTX + '/api/incidents', { method: 'POST', body: linkForm })
                            .then(r2 => r2.json())
                            .then(lr => { if (!lr.ok) console.warn('Code link save warning:', lr); })
                            .catch(e => console.error('Code link save error:', e));
                    }
                    closeModal();
                    loadIncidents(currentPage);
                } else {
                    alert('保存失败: ' + (result.message || '未知错误'));
                }
            })
            .catch(err => {
                alert('保存失败: ' + err.message);
            });
        }
        
        function deleteIncident(id) {
            if (!confirm('确定要删除这条事故记录吗？')) return;
            
            fetch(CTX + '/api/incidents', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'action=delete&id=' + id
            })
            .then(r => r.json())
            .then(result => {
                if (result.success) {
                    loadIncidents(currentPage);
                } else {
                    alert('删除失败: ' + (result.message || '未知错误'));
                }
            })
            .catch(err => {
                alert('删除失败: ' + err.message);
            });
        }
        
        // Init
        loadIncidents();
    </script>
</body>
</html>
