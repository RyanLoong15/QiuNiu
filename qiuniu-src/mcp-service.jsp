<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.qiuniu.model.User" %>
<%@ page import="com.qiuniu.model.Role" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    boolean canWrite = user.canWrite();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>囚牛 · 流水线日志 MCP 服务参数维护</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: '"Microsoft YaHei"', Arial, sans-serif;
            background: #f5f6fa;
            min-height: 100vh;
        }
        .header {
            background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
            color: white;
            padding: 15px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header h1 { font-size: 20px; }
        .header .user-info { display: flex; align-items: center; gap: 15px; font-size: 14px; }
        .btn-link {
            background: rgba(255,255,255,0.2);
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            text-decoration: none;
            font-size: 14px;
        }
        .btn-link:hover { background: rgba(255,255,255,0.3); }

        .container { max-width: 1200px; margin: 20px auto; padding: 0 20px; }

        /* 子导航标签 */
        .sub-nav {
            display: flex;
            gap: 0;
            margin-bottom: 20px;
            border-bottom: 2px solid #e0e0e0;
        }
        .sub-nav-item {
            padding: 12px 24px;
            cursor: pointer;
            font-size: 15px;
            color: #666;
            border-bottom: 3px solid transparent;
            transition: all 0.2s;
            user-select: none;
        }
        .sub-nav-item:hover { color: #4facfe; }
        .sub-nav-item.active {
            color: #4facfe;
            border-bottom-color: #4facfe;
            font-weight: 600;
        }
        .sub-nav-item.disabled {
            color: #bbb;
            cursor: default;
        }

        /* 通用工具栏 */
        .toolbar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
            flex-wrap: wrap;
            gap: 10px;
        }
        .toolbar-left { display: flex; gap: 10px; align-items: center; }
        .toolbar-right { display: flex; gap: 10px; align-items: center; }
        .search-box {
            padding: 8px 14px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-size: 14px;
            width: 220px;
            outline: none;
        }
        .search-box:focus { border-color: #4facfe; }

        /* 按钮 */
        .btn {
            padding: 8px 18px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
            transition: all 0.2s;
        }
        .btn-primary { background: #4facfe; color: white; }
        .btn-primary:hover { background: #3d9be8; }
        .btn-success { background: #52c41a; color: white; }
        .btn-success:hover { background: #45a816; }
        .btn-danger { background: #ff4d4f; color: white; }
        .btn-danger:hover { background: #e04042; }
        .btn-sm { padding: 4px 12px; font-size: 13px; }
        .btn:disabled { opacity: 0.5; cursor: not-allowed; }

        /* 数据表格 */
        .data-table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            border-radius: 10px;
            overflow: hidden;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06);
        }
        .data-table th {
            background: #fafafa;
            padding: 12px 14px;
            text-align: left;
            font-size: 13px;
            color: #666;
            font-weight: 600;
            border-bottom: 2px solid #eee;
            white-space: nowrap;
        }
        .data-table td {
            padding: 10px 14px;
            border-bottom: 1px solid #f0f0f0;
            font-size: 14px;
            color: #333;
            word-break: break-all;
        }
        .data-table tr:hover { background: #f8fbff; }
        .data-table .actions { white-space: nowrap; }
        .pwd-mask {
            color: #999;
            letter-spacing: 2px;
        }
        .empty-row td {
            text-align: center;
            color: #999;
            padding: 40px;
            font-size: 15px;
        }
        .cmd-cell { max-width: 300px; font-family: 'Courier New', monospace; font-size: 13px; color: #555; }
        .desc-cell { max-width: 300px; }
        .content-cell { max-width: 500px; }

        /* 弹窗 */
        .modal-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.4);
            z-index: 1000;
            justify-content: center;
            align-items: center;
        }
        .modal-overlay.show { display: flex; }
        .modal {
            background: white;
            border-radius: 12px;
            width: 600px;
            max-width: 95vw;
            max-height: 90vh;
            overflow-y: auto;
            box-shadow: 0 8px 40px rgba(0,0,0,0.15);
        }
        .modal-header {
            padding: 18px 24px;
            border-bottom: 1px solid #eee;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .modal-header h3 { font-size: 17px; color: #333; }
        .modal-close {
            width: 30px; height: 30px;
            border: none; background: none;
            font-size: 20px; cursor: pointer;
            color: #999; border-radius: 50%;
        }
        .modal-close:hover { background: #f0f0f0; }
        .modal-body { padding: 20px 24px; }
        .form-group {
            margin-bottom: 14px;
        }
        .form-group label {
            display: block;
            font-size: 13px;
            color: #666;
            margin-bottom: 4px;
            font-weight: 500;
        }
        .form-group input, .form-group select, .form-group textarea {
            width: 100%;
            padding: 8px 12px;
            border: 1px solid #ddd;
            border-radius: 6px;
            font-size: 14px;
            outline: none;
            font-family: inherit;
        }
        .form-group textarea {
            resize: vertical;
            min-height: 100px;
            font-family: 'Courier New', monospace;
        }
        .form-group input:focus, .form-group textarea:focus { border-color: #4facfe; }
        .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 14px;
        }
        .modal-footer {
            padding: 14px 24px;
            border-top: 1px solid #eee;
            display: flex;
            justify-content: flex-end;
            gap: 10px;
        }

        /* Tab面板 */
        .tab-panel { display: none; }
        .tab-panel.active { display: block; }

        /* 提示消息 */
        .toast {
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 12px 24px;
            border-radius: 8px;
            color: white;
            font-size: 14px;
            z-index: 2000;
            display: none;
            animation: slideIn 0.3s ease;
        }
        .toast.success { background: #52c41a; }
        .toast.error { background: #ff4d4f; }
        @keyframes slideIn { from { transform: translateX(100px); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
    </style>
</head>
<body>
    <div class="header">
        <h1>⚙️ 流水线日志 MCP 服务参数维护</h1>
        <div class="user-info">
            <span><%= user.getNickname() %></span>
            <a href="${pageContext.request.contextPath}/home.jsp" class="btn-link">功能中心</a>
            <a href="${pageContext.request.contextPath}/logout" class="btn-link">退出</a>
        </div>
    </div>

    <div class="container">
        <!-- 子导航 -->
        <div class="sub-nav">
            <div class="sub-nav-item active" data-tab="env">测试环境信息</div>
            <div class="sub-nav-item" data-tab="command">命令配置</div>
            <div class="sub-nav-item" data-tab="prompt">提示词配置</div>
        </div>

        <!-- ========== 环境信息面板 ========== -->
        <div id="panel-env" class="tab-panel active">
            <div class="toolbar">
                <div class="toolbar-left">
                    <input type="text" class="search-box" id="envSearchInput" placeholder="搜索系统名/环境/机器名..." />
                    <button class="btn btn-primary" onclick="loadEnv()">查询</button>
                </div>
                <div class="toolbar-right">
                    <% if (canWrite) { %>
                    <button class="btn btn-success" onclick="showEnvAdd()">+ 新增</button>
                    <% } %>
                </div>
            </div>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>编号</th>
                        <th>系统名</th>
                        <th>环境</th>
                        <th>集群码</th>
                        <th>机器名</th>
                        <th>IP地址</th>
                        <th>用户</th>
                        <th>密码</th>
                        <th>命令编号</th>
                        <th>提示词编号</th>
                        <% if (canWrite) { %><th>操作</th><% } %>
                    </tr>
                </thead>
                <tbody id="envTableBody">
                    <tr class="empty-row"><td colspan="<%= canWrite ? 11 : 10 %>">加载中...</td></tr>
                </tbody>
            </table>
        </div>

        <!-- ========== 命令配置面板 ========== -->
        <div id="panel-command" class="tab-panel">
            <div class="toolbar">
                <div class="toolbar-left">
                    <input type="text" class="search-box" id="cmdSearchInput" placeholder="搜索命令编号/命令内容/描述..." />
                    <button class="btn btn-primary" onclick="loadCmd()">查询</button>
                </div>
                <div class="toolbar-right">
                    <% if (canWrite) { %>
                    <button class="btn btn-success" onclick="showCmdAdd()">+ 新增</button>
                    <% } %>
                </div>
            </div>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>命令编号</th>
                        <th>命令内容</th>
                        <th>命令描述</th>
                        <% if (canWrite) { %><th>操作</th><% } %>
                    </tr>
                </thead>
                <tbody id="cmdTableBody">
                    <tr class="empty-row"><td colspan="<%= canWrite ? 4 : 3 %>">加载中...</td></tr>
                </tbody>
            </table>
        </div>

        <!-- ========== 提示词配置面板 ========== -->
        <div id="panel-prompt" class="tab-panel">
            <div class="toolbar">
                <div class="toolbar-left">
                    <input type="text" class="search-box" id="promptSearchInput" placeholder="搜索提示词编号/内容..." />
                    <button class="btn btn-primary" onclick="loadPrompt()">查询</button>
                </div>
                <div class="toolbar-right">
                    <% if (canWrite) { %>
                    <button class="btn btn-success" onclick="showPromptAdd()">+ 新增</button>
                    <% } %>
                </div>
            </div>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>提示词编号</th>
                        <th>提示词内容</th>
                        <% if (canWrite) { %><th>操作</th><% } %>
                    </tr>
                </thead>
                <tbody id="promptTableBody">
                    <tr class="empty-row"><td colspan="<%= canWrite ? 3 : 2 %>">加载中...</td></tr>
                </tbody>
            </table>
        </div>
    </div>

    <!-- ========== 环境弹窗 ========== -->
    <div class="modal-overlay" id="envModal">
        <div class="modal">
            <div class="modal-header">
                <h3 id="envModalTitle">新增环境信息</h3>
                <button class="modal-close" onclick="closeEnvModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="envFormMode" value="add" />
                <div class="form-row">
                    <div class="form-group">
                        <label>环境编号 *</label>
                        <input type="text" id="f_envId" maxlength="10" placeholder="如：ENV001" />
                    </div>
                    <div class="form-group">
                        <label>系统名 *</label>
                        <input type="text" id="f_systemName" maxlength="20" placeholder="如：核心系统" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>环境</label>
                        <input type="text" id="f_environment" maxlength="20" placeholder="如：SIT/UAT/PROD" />
                    </div>
                    <div class="form-group">
                        <label>集群码</label>
                        <input type="text" id="f_clusterCode" maxlength="30" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>机器名</label>
                        <input type="text" id="f_machineName" maxlength="30" />
                    </div>
                    <div class="form-group">
                        <label>IP地址</label>
                        <input type="text" id="f_ipAddress" maxlength="30" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>用户</label>
                        <input type="text" id="f_username" maxlength="50" />
                    </div>
                    <div class="form-group">
                        <label>密码</label>
                        <input type="password" id="f_password" maxlength="50" />
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>关联命令编号</label>
                        <input type="text" id="f_commandId" maxlength="10" />
                    </div>
                    <div class="form-group">
                        <label>关联提示词编号</label>
                        <input type="text" id="f_promptId" maxlength="10" />
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" style="background:#eee" onclick="closeEnvModal()">取消</button>
                <button class="btn btn-primary" onclick="saveEnv()">保存</button>
            </div>
        </div>
    </div>

    <!-- ========== 命令弹窗 ========== -->
    <div class="modal-overlay" id="cmdModal">
        <div class="modal">
            <div class="modal-header">
                <h3 id="cmdModalTitle">新增命令</h3>
                <button class="modal-close" onclick="closeCmdModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="cmdFormMode" value="add" />
                <div class="form-group">
                    <label>命令编号 *</label>
                    <input type="text" id="f_cmdId" maxlength="10" placeholder="如：CMD006" />
                </div>
                <div class="form-group">
                    <label>命令内容 *</label>
                    <textarea id="f_cmdCommand" rows="4" maxlength="1999" placeholder="如：cat /proc/meminfo | grep MemTotal"></textarea>
                </div>
                <div class="form-group">
                    <label>命令描述</label>
                    <input type="text" id="f_cmdDesc" maxlength="200" placeholder="如：查看服务器内存总量" />
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" style="background:#eee" onclick="closeCmdModal()">取消</button>
                <button class="btn btn-primary" onclick="saveCmd()">保存</button>
            </div>
        </div>
    </div>

    <!-- ========== 提示词弹窗 ========== -->
    <div class="modal-overlay" id="promptModal">
        <div class="modal" style="width: 700px;">
            <div class="modal-header">
                <h3 id="promptModalTitle">新增提示词</h3>
                <button class="modal-close" onclick="closePromptModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="promptFormMode" value="add" />
                <div class="form-group">
                    <label>提示词编号 *</label>
                    <input type="text" id="f_promptId" maxlength="10" placeholder="如：P010" />
                </div>
                <div class="form-group">
                    <label>提示词内容 *</label>
                    <textarea id="f_promptContent" rows="6" maxlength="2000" placeholder="请输入提示词内容..."></textarea>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn" style="background:#eee" onclick="closePromptModal()">取消</button>
                <button class="btn btn-primary" onclick="savePrompt()">保存</button>
            </div>
        </div>
    </div>

    <!-- 提示消息 -->
    <div class="toast" id="toast"></div>

    <script>
        var API = '${pageContext.request.contextPath}/api/mcp';
        var canWrite = <%= canWrite %>;

        // ========== 标签页切换 ==========
        document.querySelectorAll('.sub-nav-item:not(.disabled)').forEach(function(tab) {
            tab.addEventListener('click', function() {
                document.querySelectorAll('.sub-nav-item').forEach(function(t) { t.classList.remove('active'); });
                document.querySelectorAll('.tab-panel').forEach(function(p) { p.classList.remove('active'); });
                tab.classList.add('active');
                document.getElementById('panel-' + tab.dataset.tab).classList.add('active');
                // 切换时加载数据
                if (tab.dataset.tab === 'env') loadEnv();
                else if (tab.dataset.tab === 'command') loadCmd();
                else if (tab.dataset.tab === 'prompt') loadPrompt();
            });
        });

        // ========== 工具函数 ==========
        function esc(s) {
            if (s == null) return '';
            var d = document.createElement('div');
            d.textContent = s;
            return d.innerHTML;
        }

        function showToast(msg, type) {
            var t = document.getElementById('toast');
            t.textContent = msg;
            t.className = 'toast ' + (type || 'success');
            t.style.display = 'block';
            setTimeout(function() { t.style.display = 'none'; }, 2500);
        }

        // ========== ENV CRUD ==========
        function loadEnv() {
            var search = document.getElementById('envSearchInput').value.trim();
            var url = API + '/env';
            if (search) url += '?search=' + encodeURIComponent(search);
            fetch(url, { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) { renderEnvTable(data.data || []); })
                .catch(function(e) { showToast('查询失败: ' + e.message, 'error'); });
        }

        function renderEnvTable(list) {
            var tbody = document.getElementById('envTableBody');
            if (!list || list.length === 0) {
                tbody.innerHTML = '<tr class="empty-row"><td colspan="' + (canWrite ? 11 : 10) + '">暂无数据</td></tr>';
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var d = list[i];
                html += '<tr>';
                html += '<td>' + esc(d.envId) + '</td>';
                html += '<td>' + esc(d.systemName) + '</td>';
                html += '<td>' + esc(d.environment) + '</td>';
                html += '<td>' + esc(d.clusterCode) + '</td>';
                html += '<td>' + esc(d.machineName) + '</td>';
                html += '<td>' + esc(d.ipAddress) + '</td>';
                html += '<td>' + esc(d.username) + '</td>';
                html += '<td class="pwd-mask" title="点击显示" style="cursor:pointer" onclick="togglePwd(this,\'' + esc(d.password) + '\')">&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;</td>';
                html += '<td>' + esc(d.commandId) + '</td>';
                html += '<td>' + esc(d.promptId) + '</td>';
                if (canWrite) {
                    html += '<td class="actions">';
                    html += '<button class="btn btn-primary btn-sm" onclick="editEnv(\'' + esc(d.envId) + '\')">编辑</button> ';
                    html += '<button class="btn btn-danger btn-sm" onclick="deleteEnv(\'' + esc(d.envId) + '\',\'' + esc(d.systemName) + '\')">删除</button>';
                    html += '</td>';
                }
                html += '</tr>';
            }
            tbody.innerHTML = html;
        }

        function togglePwd(el, pwd) {
            if (el.classList.contains('pwd-mask')) {
                el.textContent = pwd || '';
                el.classList.remove('pwd-mask');
                el.title = '点击隐藏';
            } else {
                el.innerHTML = '&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;&#x2022;';
                el.classList.add('pwd-mask');
                el.title = '点击显示';
            }
        }

        function showEnvAdd() {
            document.getElementById('envFormMode').value = 'add';
            document.getElementById('envModalTitle').textContent = '新增环境信息';
            document.getElementById('f_envId').value = '';
            document.getElementById('f_envId').disabled = false;
            clearEnvForm();
            document.getElementById('envModal').classList.add('show');
        }

        function editEnv(envId) {
            fetch(API + '/env?id=' + encodeURIComponent(envId), { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (!data.data) { showToast('未找到记录', 'error'); return; }
                    var d = data.data;
                    document.getElementById('envFormMode').value = 'edit';
                    document.getElementById('envModalTitle').textContent = '编辑环境信息';
                    document.getElementById('f_envId').value = d.envId;
                    document.getElementById('f_envId').disabled = true;
                    document.getElementById('f_systemName').value = d.systemName || '';
                    document.getElementById('f_environment').value = d.environment || '';
                    document.getElementById('f_clusterCode').value = d.clusterCode || '';
                    document.getElementById('f_machineName').value = d.machineName || '';
                    document.getElementById('f_ipAddress').value = d.ipAddress || '';
                    document.getElementById('f_username').value = d.username || '';
                    document.getElementById('f_password').value = d.password || '';
                    document.getElementById('f_commandId').value = d.commandId || '';
                    document.getElementById('f_promptId').value = d.promptId || '';
                    document.getElementById('envModal').classList.add('show');
                })
                .catch(function(e) { showToast('加载失败: ' + e.message, 'error'); });
        }

        function closeEnvModal() {
            document.getElementById('envModal').classList.remove('show');
        }

        function clearEnvForm() {
            var fields = ['f_systemName','f_environment','f_clusterCode','f_machineName',
                         'f_ipAddress','f_username','f_password','f_commandId','f_promptId'];
            for (var i = 0; i < fields.length; i++) {
                document.getElementById(fields[i]).value = '';
            }
        }

        function saveEnv() {
            var mode = document.getElementById('envFormMode').value;
            var params = new URLSearchParams();
            params.append('envId', document.getElementById('f_envId').value.trim());
            params.append('systemName', document.getElementById('f_systemName').value.trim());
            params.append('environment', document.getElementById('f_environment').value.trim());
            params.append('clusterCode', document.getElementById('f_clusterCode').value.trim());
            params.append('machineName', document.getElementById('f_machineName').value.trim());
            params.append('ipAddress', document.getElementById('f_ipAddress').value.trim());
            params.append('username', document.getElementById('f_username').value.trim());
            params.append('password', document.getElementById('f_password').value.trim());
            params.append('commandId', document.getElementById('f_commandId').value.trim());
            params.append('promptId', document.getElementById('f_promptId').value.trim());
            var action = mode === 'add' ? '/env/add' : '/env/update';
            fetch(API + action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) {
                    showToast(mode === 'add' ? '新增成功' : '更新成功', 'success');
                    closeEnvModal();
                    loadEnv();
                } else {
                    showToast(data.error || '操作失败', 'error');
                }
            })
            .catch(function(e) { showToast('保存失败: ' + e.message, 'error'); });
        }

        function deleteEnv(envId, name) {
            if (!confirm('确认删除环境信息 [' + envId + '] ' + (name || '') + '？')) return;
            var params = new URLSearchParams();
            params.append('envId', envId);
            fetch(API + '/env/delete', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) { showToast('删除成功', 'success'); loadEnv(); }
                else { showToast(data.error || '删除失败', 'error'); }
            })
            .catch(function(e) { showToast('删除失败: ' + e.message, 'error'); });
        }

        // ========== COMMAND CRUD ==========
        function loadCmd() {
            var kw = document.getElementById('cmdSearchInput').value.trim();
            var url = API + '/command';
            if (kw) url += '?keyword=' + encodeURIComponent(kw);
            fetch(url, { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) { renderCmdTable(data.data || []); })
                .catch(function(e) { showToast('查询失败: ' + e.message, 'error'); });
        }

        function renderCmdTable(list) {
            var tbody = document.getElementById('cmdTableBody');
            if (!list || list.length === 0) {
                tbody.innerHTML = '<tr class="empty-row"><td colspan="' + (canWrite ? 4 : 3) + '">暂无数据</td></tr>';
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var d = list[i];
                html += '<tr>';
                html += '<td><code>' + esc(d.commandId) + '</code></td>';
                html += '<td class="cmd-cell">' + esc(d.command) + '</td>';
                html += '<td class="desc-cell">' + esc(d.description) + '</td>';
                if (canWrite) {
                    html += '<td class="actions">';
                    html += '<button class="btn btn-primary btn-sm" onclick="editCmd(\'' + esc(d.commandId) + '\')">编辑</button> ';
                    html += '<button class="btn btn-danger btn-sm" onclick="deleteCmd(\'' + esc(d.commandId) + '\')">删除</button>';
                    html += '</td>';
                }
                html += '</tr>';
            }
            tbody.innerHTML = html;
        }

        function showCmdAdd() {
            document.getElementById('cmdFormMode').value = 'add';
            document.getElementById('cmdModalTitle').textContent = '新增命令';
            document.getElementById('f_cmdId').value = '';
            document.getElementById('f_cmdId').disabled = false;
            document.getElementById('f_cmdCommand').value = '';
            document.getElementById('f_cmdDesc').value = '';
            document.getElementById('cmdModal').classList.add('show');
        }

        function editCmd(cmdId) {
            fetch(API + '/command/' + encodeURIComponent(cmdId), { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (!data.data) { showToast('未找到命令', 'error'); return; }
                    var d = data.data;
                    document.getElementById('cmdFormMode').value = 'edit';
                    document.getElementById('cmdModalTitle').textContent = '编辑命令';
                    document.getElementById('f_cmdId').value = d.commandId;
                    document.getElementById('f_cmdId').disabled = true;
                    document.getElementById('f_cmdCommand').value = d.command || '';
                    document.getElementById('f_cmdDesc').value = d.description || '';
                    document.getElementById('cmdModal').classList.add('show');
                })
                .catch(function(e) { showToast('加载失败: ' + e.message, 'error'); });
        }

        function closeCmdModal() {
            document.getElementById('cmdModal').classList.remove('show');
        }

        function saveCmd() {
            var mode = document.getElementById('cmdFormMode').value;
            var cmdId = document.getElementById('f_cmdId').value.trim();
            var cmdCommand = document.getElementById('f_cmdCommand').value.trim();
            var cmdDesc = document.getElementById('f_cmdDesc').value.trim();
            if (!cmdId) { showToast('命令编号不能为空', 'error'); return; }
            if (!cmdCommand) { showToast('命令内容不能为空', 'error'); return; }
            var params = new URLSearchParams();
            params.append('commandId', cmdId);
            params.append('command', cmdCommand);
            params.append('description', cmdDesc);
            var action = mode === 'add' ? '/command/add' : '/command/update';
            fetch(API + action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) {
                    showToast(mode === 'add' ? '新增成功' : '更新成功', 'success');
                    closeCmdModal();
                    loadCmd();
                } else {
                    showToast(data.error || '操作失败', 'error');
                }
            })
            .catch(function(e) { showToast('保存失败: ' + e.message, 'error'); });
        }

        function deleteCmd(cmdId) {
            if (!confirm('确认删除命令 [' + cmdId + ']？')) return;
            var params = new URLSearchParams();
            params.append('commandId', cmdId);
            fetch(API + '/command/delete', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) { showToast('删除成功', 'success'); loadCmd(); }
                else { showToast(data.error || '删除失败', 'error'); }
            })
            .catch(function(e) { showToast('删除失败: ' + e.message, 'error'); });
        }

        // ========== PROMPT CRUD ==========
        function loadPrompt() {
            var kw = document.getElementById('promptSearchInput').value.trim();
            var url = API + '/prompt';
            if (kw) url += '?keyword=' + encodeURIComponent(kw);
            fetch(url, { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) { renderPromptTable(data.data || []); })
                .catch(function(e) { showToast('查询失败: ' + e.message, 'error'); });
        }

        function renderPromptTable(list) {
            var tbody = document.getElementById('promptTableBody');
            if (!list || list.length === 0) {
                tbody.innerHTML = '<tr class="empty-row"><td colspan="' + (canWrite ? 3 : 2) + '">暂无数据</td></tr>';
                return;
            }
            var html = '';
            for (var i = 0; i < list.length; i++) {
                var d = list[i];
                html += '<tr>';
                html += '<td><code>' + esc(d.promptId) + '</code></td>';
                html += '<td class="content-cell">' + esc(d.content) + '</td>';
                if (canWrite) {
                    html += '<td class="actions">';
                    html += '<button class="btn btn-primary btn-sm" onclick="editPrompt(\'' + esc(d.promptId) + '\')">编辑</button> ';
                    html += '<button class="btn btn-danger btn-sm" onclick="deletePrompt(\'' + esc(d.promptId) + '\')">删除</button>';
                    html += '</td>';
                }
                html += '</tr>';
            }
            tbody.innerHTML = html;
        }

        function showPromptAdd() {
            document.getElementById('promptFormMode').value = 'add';
            document.getElementById('promptModalTitle').textContent = '新增提示词';
            document.getElementById('f_promptId').value = '';
            document.getElementById('f_promptId').disabled = false;
            document.getElementById('f_promptContent').value = '';
            document.getElementById('promptModal').classList.add('show');
        }

        function editPrompt(promptId) {
            fetch(API + '/prompt/' + encodeURIComponent(promptId), { credentials: 'same-origin' })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (!data.data) { showToast('未找到提示词', 'error'); return; }
                    var d = data.data;
                    document.getElementById('promptFormMode').value = 'edit';
                    document.getElementById('promptModalTitle').textContent = '编辑提示词';
                    document.getElementById('f_promptId').value = d.promptId;
                    document.getElementById('f_promptId').disabled = true;
                    document.getElementById('f_promptContent').value = d.content || '';
                    document.getElementById('promptModal').classList.add('show');
                })
                .catch(function(e) { showToast('加载失败: ' + e.message, 'error'); });
        }

        function closePromptModal() {
            document.getElementById('promptModal').classList.remove('show');
        }

        function savePrompt() {
            var mode = document.getElementById('promptFormMode').value;
            var promptId = document.getElementById('f_promptId').value.trim();
            var content = document.getElementById('f_promptContent').value.trim();
            if (!promptId) { showToast('提示词编号不能为空', 'error'); return; }
            if (!content) { showToast('提示词内容不能为空', 'error'); return; }
            var params = new URLSearchParams();
            params.append('promptId', promptId);
            params.append('content', content);
            var action = mode === 'add' ? '/prompt/add' : '/prompt/update';
            fetch(API + action, {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) {
                    showToast(mode === 'add' ? '新增成功' : '更新成功', 'success');
                    closePromptModal();
                    loadPrompt();
                } else {
                    showToast(data.error || '操作失败', 'error');
                }
            })
            .catch(function(e) { showToast('保存失败: ' + e.message, 'error'); });
        }

        function deletePrompt(promptId) {
            if (!confirm('确认删除提示词 [' + promptId + ']？')) return;
            var params = new URLSearchParams();
            params.append('promptId', promptId);
            fetch(API + '/prompt/delete', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.ok) { showToast('删除成功', 'success'); loadPrompt(); }
                else { showToast(data.error || '删除失败', 'error'); }
            })
            .catch(function(e) { showToast('删除失败: ' + e.message, 'error'); });
        }

        // ========== 页面加载 ==========
        loadEnv();
    </script>
</body>
</html>
