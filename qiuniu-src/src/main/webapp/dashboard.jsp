<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.qiuniu.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>控制台 - 囚牛</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            background: #f5f6fa;
            min-height: 100vh;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .header h1 {
            font-size: 24px;
        }
        
        .header .user-info {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .header .user-name {
            font-size: 14px;
        }
        
        .btn-logout {
            background: rgba(255,255,255,0.2);
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            text-decoration: none;
            font-size: 14px;
        }
        
        .btn-logout:hover {
            background: rgba(255,255,255,0.3);
        }
        
        .container {
            max-width: 1400px;
            margin: 0 auto;
            padding: 30px;
        }
        
        .toolbar {
            background: white;
            padding: 20px;
            border-radius: 10px;
            margin-bottom: 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 15px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        
        .search-box {
            display: flex;
            gap: 10px;
            flex: 1;
            min-width: 300px;
        }
        
        .search-box input {
            flex: 1;
            padding: 10px 15px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 14px;
        }
        
        .search-box input:focus {
            outline: none;
            border-color: #667eea;
        }
        
        .btn {
            padding: 10px 20px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 14px;
            transition: all 0.2s;
        }
        
        .btn-primary {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
        }
        
        .btn-secondary {
            background: #f0f0f0;
            color: #333;
        }
        
        .btn-secondary:hover {
            background: #e0e0e0;
        }
        
        .btn-danger {
            background: #ff4757;
            color: white;
        }
        
        .btn-danger:hover {
            background: #ff3344;
        }
        
        .btn-sm {
            padding: 6px 12px;
            font-size: 12px;
        }
        
        .category-filter {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        
        .category-filter select {
            padding: 10px 15px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 14px;
        }
        
        .content {
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        
        .prompt-list {
            list-style: none;
        }
        
        .prompt-item {
            border: 1px solid #e0e0e0;
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 15px;
            transition: all 0.2s;
        }
        
        .prompt-item:hover {
            border-color: #667eea;
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.1);
        }
        
        .prompt-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 10px;
        }
        
        .prompt-title {
            font-size: 18px;
            color: #333;
            font-weight: 600;
        }
        
        .prompt-category {
            background: #667eea;
            color: white;
            padding: 4px 10px;
            border-radius: 20px;
            font-size: 12px;
            margin-left: 10px;
        }
        
        .prompt-actions {
            display: flex;
            gap: 8px;
        }
        
        .prompt-description {
            color: #666;
            font-size: 14px;
            margin-bottom: 10px;
        }
        
        .prompt-content {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 8px;
            font-size: 14px;
            color: #333;
            white-space: pre-wrap;
            max-height: 200px;
            overflow-y: auto;
            font-family: 'Consolas', monospace;
        }
        
        .prompt-meta {
            margin-top: 10px;
            font-size: 12px;
            color: #999;
        }
        
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #999;
        }
        
        .empty-state h3 {
            margin-bottom: 10px;
        }
        
        /* Modal */
        .modal {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            z-index: 1000;
            justify-content: center;
            align-items: center;
        }
        
        .modal.active {
            display: flex;
        }
        
        .modal-content {
            background: white;
            border-radius: 15px;
            width: 600px;
            max-width: 90%;
            max-height: 90vh;
            overflow-y: auto;
        }
        
        .modal-header {
            padding: 20px;
            border-bottom: 1px solid #e0e0e0;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .modal-header h2 {
            font-size: 20px;
            color: #333;
        }
        
        .modal-close {
            background: none;
            border: none;
            font-size: 24px;
            cursor: pointer;
            color: #999;
        }
        
        .modal-close:hover {
            color: #333;
        }
        
        .modal-body {
            padding: 20px;
        }
        
        .modal-footer {
            padding: 20px;
            border-top: 1px solid #e0e0e0;
            display: flex;
            justify-content: flex-end;
            gap: 10px;
        }
        
        .form-group {
            margin-bottom: 20px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 8px;
            color: #555;
            font-weight: 500;
        }
        
        .form-group input,
        .form-group textarea,
        .form-group select {
            width: 100%;
            padding: 12px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 14px;
            font-family: inherit;
        }
        
        .form-group textarea {
            min-height: 200px;
            resize: vertical;
        }
        
        .form-group input:focus,
        .form-group textarea:focus,
        .form-group select:focus {
            outline: none;
            border-color: #667eea;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🦞 囚牛 - 提示词管理系统</h1>
        <div class="user-info">
            <span class="user-name">欢迎，<%= user.getNickname() != null ? user.getNickname() : user.getUsername() %></span>
            <a href="<%= request.getContextPath() %>/home.jsp" class="btn-logout">🏠 功能中心</a>
            <a href="<%= request.getContextPath() %>/team-dashboard.jsp" class="btn-logout">📊 团队看板</a>
            <a href="<%= request.getContextPath() %>/version-compare.jsp" class="btn-logout">🚀 投产变更</a>
            <a href="<%= request.getContextPath() %>/git-projects.jsp" class="btn-logout">Git项目</a>
            <a href="<%= request.getContextPath() %>/logout" class="btn-logout">退出登录</a>
        </div>
    </div>
    
    <div class="container">
        <div class="toolbar">
            <div class="search-box">
                <input type="text" id="searchInput" placeholder="搜索提示词名称、内容或描述...">
                <button class="btn btn-primary" onclick="searchPrompts()">搜索</button>
            </div>
            <div class="category-filter">
                <select id="categorySelect" onchange="filterByCategory()">
                    <option value="">全部分类</option>
                </select>
                <button class="btn btn-primary" onclick="openModal()">+ 新建提示词</button>
            </div>
        </div>
        
        <div class="content">
            <ul class="prompt-list" id="promptList">
                <li class="empty-state">
                    <h3>加载中...</h3>
                </li>
            </ul>
        </div>
    </div>
    
    <!-- 新建/编辑提示词模态框 -->
    <div class="modal" id="promptModal">
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="modalTitle">新建提示词</h2>
                <button class="modal-close" onclick="closeModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="promptId">
                <div class="form-group">
                    <label for="promptFileType">文件类型 *</label>
                    <input type="text" id="promptFileType" required placeholder="如：java、sql、sh、yaml、json、js、ts、go、py 等">
                </div>
                <div class="form-group">
                    <label for="promptDisplayName">显示名称 *</label>
                    <input type="text" id="promptDisplayName" required placeholder="如：Java 代码风险分析">
                </div>
                <div class="form-group">
                    <label for="promptSystemPrompt">系统提示词（AI角色定义）*</label>
                    <textarea id="promptSystemPrompt" required rows="5" placeholder="定义 AI 的角色和能力，如：你是一位资深 Java 架构师，专注于..."></textarea>
                </div>
                <div class="form-group">
                    <label for="promptUserTemplate">用户提示词模板 *（{diff} 会被替换为实际差异内容）</label>
                    <textarea id="promptUserTemplate" required rows="6" placeholder="如：请分析以下代码变更存在的风险：&#10;&#10;【项目】{projectName} | 版本 {versionName}&#10;&#10;【变更内容】&#10;{diff}"></textarea>
                </div>
                <div class="form-group" style="display:flex;gap:16px;">
                    <div style="flex:1;">
                        <label for="promptMaxDiffLines">最大差异行数</label>
                        <input type="number" id="promptMaxDiffLines" value="300" placeholder="300">
                    </div>
                    <div style="flex:1;">
                        <label for="promptPriority">优先级（越小越优先）</label>
                        <input type="number" id="promptPriority" value="100" placeholder="100">
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal()">取消</button>
                <button class="btn btn-primary" onclick="savePrompt()">保存</button>
            </div>
        </div>
    </div>
    
    <script>
        let currentPrompts = [];
        
        document.addEventListener('DOMContentLoaded', function() {
            loadPrompts();
            loadCategories();
        });

        function loadPrompts(category) {
            var url = '<%= request.getContextPath() %>/prompt?action=list';
            if (category) url += '&category=' + encodeURIComponent(category);
            fetch(url)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        currentPrompts = data.data || [];
                        renderPrompts(currentPrompts);
                    }
                })
                .catch(function(err) { console.error('加载失败:', err); });
        }

        function loadCategories() {
            fetch('<%= request.getContextPath() %>/prompt?action=categories')
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        var select = document.getElementById('categorySelect');
                        var cats = data.data || [];
                        cats.forEach(function(cat) {
                            var opt = document.createElement('option');
                            opt.value = cat;
                            opt.textContent = cat;
                            select.appendChild(opt);
                        });
                    }
                });
        }

        function renderPrompts(prompts) {
            var list = document.getElementById('promptList');
            if (!prompts || prompts.length === 0) {
                list.innerHTML = '<li class="empty-state"><h3>暂无提示词</h3></li>';
                return;
            }
            var html = '';
            prompts.forEach(function(p) {
                html += '<li class="prompt-item">' +
                    '<div class="prompt-header">' +
                        '<div>' +
                            '<span class="prompt-title">' + escapeHtml(p.displayName) + '</span>' +
                            '<span class="prompt-category">' + escapeHtml(p.fileType) + '</span>' +
                        '</div>' +
                        '<div class="prompt-actions">' +
                            '<button class="btn btn-secondary btn-sm" onclick="editPrompt(' + p.id + ')">编辑</button>' +
                            (p.fileType !== 'default' ? '<button class="btn btn-danger btn-sm" onclick="deletePrompt(' + p.id + ')">删除</button>' : '') +
                        '</div>' +
                    '</div>' +
                    '<div class="prompt-content">' +
                        '<div style="margin-bottom:6px;color:#888;font-size:12px;">【系统提示词】</div>' +
                        '<div style="white-space:pre-wrap;margin-bottom:12px;">' + escapeHtml(p.systemPrompt) + '</div>' +
                        '<div style="margin-bottom:6px;color:#888;font-size:12px;">【用户模板】</div>' +
                        '<div style="white-space:pre-wrap;">' + escapeHtml(p.userPromptTemplate) + '</div>' +
                    '</div>' +
                    '<div class="prompt-meta">' +
                        '最大差异行数：' + (p.maxDiffLines || '-') + ' | ' +
                        '优先级：' + (p.priority || '-') + ' | ' +
                        '创建时间：' + formatDate(p.createdAt) +
                    '</div>' +
                '</li>';
            });
            list.innerHTML = html;
        }

        function searchPrompts() {
            var kw = document.getElementById('searchInput').value.trim();
            if (kw) {
                fetch('<%= request.getContextPath() %>/prompt?action=search&keyword=' + encodeURIComponent(kw))
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (data.success) renderPrompts(data.data || []);
                    });
            } else {
                loadPrompts();
            }
        }

        function filterByCategory() {
            loadPrompts(document.getElementById('categorySelect').value);
        }

        function openModal() {
            document.getElementById('modalTitle').textContent = '新建提示词';
            document.getElementById('promptId').value = '';
            document.getElementById('promptFileType').value = '';
            document.getElementById('promptFileType').disabled = false;
            document.getElementById('promptDisplayName').value = '';
            document.getElementById('promptSystemPrompt').value = '';
            document.getElementById('promptUserTemplate').value = '';
            document.getElementById('promptMaxDiffLines').value = '300';
            document.getElementById('promptPriority').value = '100';
            document.getElementById('promptModal').classList.add('active');
        }

        function closeModal() {
            document.getElementById('promptModal').classList.remove('active');
        }

        function editPrompt(id) {
            var p = currentPrompts.find(function(x) { return x.id === id; });
            if (!p) return;
            document.getElementById('modalTitle').textContent = '编辑提示词';
            document.getElementById('promptId').value = p.id;
            document.getElementById('promptFileType').value = p.fileType;
            document.getElementById('promptFileType').disabled = true;
            document.getElementById('promptDisplayName').value = p.displayName;
            document.getElementById('promptSystemPrompt').value = p.systemPrompt;
            document.getElementById('promptUserTemplate').value = p.userPromptTemplate;
            document.getElementById('promptMaxDiffLines').value = p.maxDiffLines || 300;
            document.getElementById('promptPriority').value = p.priority || 100;
            document.getElementById('promptModal').classList.add('active');
        }

        function savePrompt() {
            var id = document.getElementById('promptId').value;
            var fileType = document.getElementById('promptFileType').value.trim();
            var displayName = document.getElementById('promptDisplayName').value.trim();
            var systemPrompt = document.getElementById('promptSystemPrompt').value.trim();
            var userTemplate = document.getElementById('promptUserTemplate').value.trim();
            var maxDiffLines = parseInt(document.getElementById('promptMaxDiffLines').value) || 300;
            var priority = parseInt(document.getElementById('promptPriority').value) || 100;

            if (!fileType || !displayName || !systemPrompt || !userTemplate) {
                alert('文件类型、显示名称、系统提示词、用户模板均不能为空');
                return;
            }

            var payload = {
                action: id ? 'update' : 'create'
            };
            if (id) payload.id = id;
            payload.fileType = fileType;
            payload.displayName = displayName;
            payload.systemPrompt = systemPrompt;
            payload.userPromptTemplate = userTemplate;
            payload.maxDiffLines = maxDiffLines;
            payload.priority = priority;

            fetch('<%= request.getContextPath() %>/prompt', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    closeModal();
                    loadPrompts();
                    loadCategories();
                } else {
                    alert(data.message || '操作失败');
                }
            })
            .catch(function() { alert('网络错误'); });
        }

        function deletePrompt(id) {
            if (!confirm('确定要删除这个提示词吗？')) return;
            var payload = { action: 'delete', id: id };
            fetch('<%= request.getContextPath() %>/prompt', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    loadPrompts();
                    loadCategories();
                } else {
                    alert(data.message || '删除失败');
                }
            });
        }

        function escapeHtml(text) {
            if (!text) return '';
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        function formatDate(dateStr) {
            if (!dateStr) return '-';
            var d = new Date(dateStr);
            return d.toLocaleString('zh-CN');
        }

        document.getElementById('searchInput').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') searchPrompts();
        });
    </script>
</body>
</html>
