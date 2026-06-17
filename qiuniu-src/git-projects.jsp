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
    <title>投产变更 - 囚牛</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #f0f2f8; min-height: 100vh; }

        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white; padding: 14px 28px; display: flex;
            justify-content: space-between; align-items: center; box-shadow: 0 2px 12px rgba(0,0,0,0.15);
        }
        .header h1 { font-size: 22px; letter-spacing: 1px; }
        .header .nav { display: flex; gap: 8px; }
        .header .nav a { color: white; text-decoration: none; padding: 7px 14px; border-radius: 6px; background: rgba(255,255,255,0.18); font-size: 13px; transition: background 0.2s; }
        .header .nav a:hover { background: rgba(255,255,255,0.3); }
        .header .nav a.active { background: rgba(255,255,255,0.35); font-weight: 600; }

        .container { max-width: 1400px; margin: 0 auto; padding: 24px 28px; }

        /* 投产操作区 */
        .deploy-section {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            border-radius: 16px; padding: 24px 28px; margin-bottom: 24px;
            box-shadow: 0 4px 20px rgba(102,126,234,0.35);
        }
        .deploy-section h2 { font-size: 18px; margin-bottom: 6px; color: white; }
        .deploy-section p { font-size: 13px; color: rgba(255,255,255,0.8); margin-bottom: 18px; }
        .deploy-form { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; }
        .form-item { display: flex; flex-direction: column; gap: 6px; }
        .form-item label { font-size: 12px; color: rgba(255,255,255,0.85); font-weight: 500; }
        .form-item input, .form-item select {
            padding: 10px 14px; border: 2px solid rgba(255,255,255,0.4);
            border-radius: 10px; font-size: 14px; background: rgba(255,255,255,0.95);
            color: #1a1a2e; min-width: 200px; font-family: inherit;
        }
        .form-item input:focus, .form-item select:focus { outline: none; border-color: white; background: white; }
        .btn-deploy {
            padding: 10px 28px; background: white; color: #667eea; border: none;
            border-radius: 10px; font-size: 14px; font-weight: 700; cursor: pointer;
            transition: all 0.2s; white-space: nowrap;
        }
        .btn-deploy:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.2); }
        .btn-deploy:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

        /* 任务进度条 */
        .task-progress {
            display: none; margin-top: 16px; background: rgba(255,255,255,0.15); border-radius: 10px; padding: 14px 18px;
        }
        .task-progress.active { display: block; }
        .task-progress .progress-info { display: flex; justify-content: space-between; color: white; font-size: 13px; margin-bottom: 10px; }
        .progress-bar-bg { background: rgba(255,255,255,0.25); border-radius: 6px; height: 8px; overflow: hidden; }
        .progress-bar-fill { background: #4ade80; height: 100%; border-radius: 6px; transition: width 0.5s; width: 0%; }
        .task-progress .progress-detail { color: rgba(255,255,255,0.8); font-size: 12px; margin-top: 8px; }

        /* 筛选栏 */
        .filter-bar {
            background: white; border-radius: 14px; padding: 16px 20px; margin-bottom: 16px;
            display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        .filter-bar label { font-size: 13px; color: #666; font-weight: 500; white-space: nowrap; }
        .filter-bar select, .filter-bar input {
            padding: 8px 12px; border: 1.5px solid #e4e7ed; border-radius: 8px;
            font-size: 13px; background: white; font-family: inherit;
        }
        .filter-bar select:focus, .filter-bar input:focus { outline: none; border-color: #667eea; }
        .filter-bar input[type="text"] { min-width: 220px; }
        .filter-stats { margin-left: auto; font-size: 13px; color: #888; display: flex; gap: 16px; align-items: center; }
        .filter-stats .stat-num { font-weight: 700; color: #667eea; font-size: 16px; }

        .btn { padding: 8px 16px; border: none; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500; transition: all 0.2s; }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
        .btn-secondary { background: #f0f0f5; color: #555; }
        .btn-secondary:hover { background: #e8e8f0; }
        .btn-danger { background: #ff4757; color: white; }
        .btn-danger:hover { background: #e8404f; }
        .btn-sm { padding: 5px 11px; font-size: 12px; }
        .btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none !important; }

        /* 项目卡片网格 */
        .project-grid {
            display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 16px;
        }

        .project-card {
            background: white; border-radius: 14px; padding: 18px 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05); transition: all 0.2s;
            border: 2px solid transparent; position: relative;
        }
        .project-card:hover { box-shadow: 0 6px 24px rgba(102,126,234,0.15); border-color: #e0e7ff; }
        .project-card.no-team { border-left: 4px solid #ddd; }
        .project-card.has-team { border-left: 4px solid #667eea; }

        .card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
        .card-title { font-size: 16px; font-weight: 700; color: #1a1a2e; }
        .team-badge {
            display: inline-block; background: #eef0ff; color: #667eea; font-size: 11px;
            padding: 2px 8px; border-radius: 20px; font-weight: 600; margin-left: 8px;
        }
        .team-badge.none { background: #f0f0f0; color: #aaa; }

        .card-url { font-size: 12px; color: #888; margin-bottom: 10px; word-break: break-all; font-family: Consolas, monospace; }
        .card-branches { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
        .branch-tag { background: #eef4ff; color: #3b82f6; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 500; }
        .branch-tag.base { background: #ecfdf5; color: #059669; }
        .branch-tag.compare { background: #fff7ed; color: #ea580c; }

        .card-meta { font-size: 12px; color: #bbb; margin-bottom: 12px; display: flex; gap: 12px; }
        .card-actions { display: flex; gap: 8px; border-top: 1px solid #f0f0f5; padding-top: 12px; flex-wrap: wrap; }
        .card-actions .btn { flex: 1; text-align: center; min-width: 0; }

        .empty-state { text-align: center; padding: 80px 20px; color: #bbb; grid-column: 1/-1; }
        .empty-state .icon { font-size: 56px; margin-bottom: 16px; }
        .empty-state h3 { font-size: 18px; margin-bottom: 8px; color: #999; }
        .empty-state p { font-size: 13px; }

        /* Modal */
        .modal { display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000; justify-content: center; align-items: center; }
        .modal.active { display: flex; }
        .modal-content { background: white; border-radius: 16px; width: 580px; max-width: 95%; max-height: 90vh; overflow-y: auto; }
        .modal-header { padding: 18px 24px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; align-items: center; background: #fafafa; border-radius: 16px 16px 0 0; }
        .modal-header h2 { font-size: 17px; color: #1a1a2e; }
        .modal-close { background: none; border: none; font-size: 26px; cursor: pointer; color: #999; line-height: 1; }
        .modal-close:hover { color: #333; }
        .modal-body { padding: 22px 24px; }
        .modal-footer { padding: 16px 24px; border-top: 1px solid #eee; display: flex; justify-content: flex-end; gap: 10px; }
        .form-group { margin-bottom: 16px; }
        .form-group label { display: block; margin-bottom: 6px; color: #555; font-size: 13px; font-weight: 600; }
        .form-group label span { color: #d32f2f; margin-left: 2px; }
        .form-group input, .form-group select { width: 100%; padding: 10px 13px; border: 2px solid #e4e7ed; border-radius: 9px; font-size: 14px; font-family: inherit; transition: border-color 0.2s; }
        .form-group input:focus, .form-group select:focus { outline: none; border-color: #667eea; }
        .form-group .hint { font-size: 11px; color: #aaa; margin-top: 4px; }
        .two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

        /* 批量进度弹窗 */
        .batch-progress-modal { width: 520px; }
        .batch-progress-modal .progress-section { padding: 8px 0; }
        .batch-progress-modal .progress-section .p-bar-bg { background: #f0f0f5; border-radius: 6px; height: 10px; margin: 10px 0; }
        .batch-progress-modal .progress-section .p-bar-fill { background: linear-gradient(90deg, #667eea, #764ba2); height: 100%; border-radius: 6px; transition: width 0.5s; }
        .batch-progress-modal .progress-section .p-info { display: flex; justify-content: space-between; font-size: 13px; color: #666; }
        .batch-progress-modal .log-list { max-height: 200px; overflow-y: auto; background: #f8f9fa; border-radius: 8px; padding: 10px 14px; margin-top: 12px; font-size: 12px; color: #555; }
        .batch-progress-modal .log-item { padding: 3px 0; border-bottom: 1px solid #eee; display: flex; gap: 8px; }
        .batch-progress-modal .log-item:last-child { border-bottom: none; }
        .batch-progress-modal .log-ok { color: #388e3c; font-weight: 600; }
        .batch-progress-modal .log-fail { color: #d32f2f; font-weight: 600; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🐉 囚牛 · 投产变更</h1>
        <div class="nav">
            <a href="<%= request.getContextPath() %>/team-dashboard.jsp">团队看板</a>
            <a href="<%= request.getContextPath() %>/version-compare.jsp">差异记录</a>
            <a href="<%= request.getContextPath() %>/git-projects.jsp" class="active">代码仓管理</a>
            <a href="<%= request.getContextPath() %>/dashboard.jsp">提示词管理</a>
            <a href="<%= request.getContextPath() %>/incidents.jsp">历史教训</a>
            <a href="<%= request.getContextPath() %>/logout">退出</a>
        </div>
    </div>

    <div class="container">
        <!-- 投产操作区 -->
        <div class="deploy-section">
            <h2>🚀 发起投产变更比对</h2>
            <p>一键比对所有代码仓的 master 分支与投产日 release 分支差异，结果自动存入数据库</p>
            <div class="deploy-form">
                <div class="form-item">
                    <label for="deployDate">投产日期</label>
                    <input type="date" id="deployDate">
                </div>
                <div class="form-item">
                    <label>&nbsp;</label>
                    <button class="btn-deploy" id="btnDeploy" onclick="startDeploy()">⚡ 发起比对</button>
                </div>
            </div>
            <!-- 进度 -->
            <div class="task-progress" id="taskProgress">
                <div class="progress-info">
                    <span id="progressLabel">比对进行中...</span>
                    <span id="progressCount">0 / 0</span>
                </div>
                <div class="progress-bar-bg">
                    <div class="progress-bar-fill" id="progressBar"></div>
                </div>
                <div class="progress-detail" id="progressDetail"></div>
            </div>
        </div>

        <!-- 筛选栏 -->
        <div class="filter-bar">
            <label>团队：</label>
            <select id="teamFilter" onchange="onFilterChange()">
                <option value="">全部团队</option>
            </select>
            <label>搜索：</label>
            <input type="text" id="searchInput" placeholder="搜索项目名称或仓库地址..." oninput="onFilterChange()" style="width:240px;">
            <div class="filter-stats">
                <span>共 <span class="stat-num" id="totalCount">0</span> 个代码仓</span>
            </div>
            <div style="flex:1;"></div>
            <button class="btn btn-secondary" onclick="openAnalysisHistory()">历史报告</button>
            <button class="btn btn-secondary" onclick="openAddModal()">+ 添加代码仓</button>
        </div>

        <!-- 项目列表 -->
        <div class="project-grid" id="projectGrid">
            <div class="empty-state"><div class="icon">📂</div><h3>加载中...</h3><p>正在获取代码仓列表...</p></div>
        </div>
    </div>

    <!-- 添加/编辑模态框 -->
    <div class="modal" id="projectModal">
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="modalTitle">添加代码仓</h2>
                <button class="modal-close" onclick="closeModal()">&times;</button>
            </div>
            <div class="modal-body">
                <input type="hidden" id="projectId">
                <div class="form-group">
                    <label for="projectName">项目名称 <span>*</span></label>
                    <input type="text" id="projectName" placeholder="例如：交易中台服务">
                </div>
                <div class="form-group">
                    <label for="teamSelect">所属团队</label>
                    <select id="teamSelect">
                        <option value="">-- 暂不分配 --</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="repoUrl">Git 仓库地址 <span>*</span></label>
                    <input type="text" id="repoUrl" placeholder="https://github.com/company/repo.git">
                </div>
                <div class="two-col">
                    <div class="form-group">
                        <label for="baseBranch">基准分支</label>
                        <input type="text" id="baseBranch" value="master" placeholder="默认：master">
                        <div class="hint">投产比对的基准分支</div>
                    </div>
                    <div class="form-group">
                        <label for="compareBranch">投产分支前缀</label>
                        <input type="text" id="compareBranch" value="release-" placeholder="release-">
                        <div class="hint">系统自动拼接日期</div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeModal()">取消</button>
                <button class="btn btn-primary" onclick="saveProject()">保存</button>
            </div>
        </div>
    </div>

    <!-- 代码分析模态框 -->
    <div class="modal" id="analysisModal">
        <div class="modal-content" style="width:700px;">
            <div class="modal-header">
                <h2 id="analysisTitle">🔍 代码分析</h2>
                <button class="modal-close" onclick="closeAnalysis()">&times;</button>
            </div>
            <div class="modal-body" id="analysisBody">
                <!-- 动态内容 -->
            </div>
        </div>
    </div>

    <!-- 批量进度模态框 -->
    <div class="modal" id="batchProgressModal">
        <div class="modal-content batch-progress-modal">
            <div class="modal-header">
                <h2 id="batchTitle">比对进度</h2>
                <button class="modal-close" onclick="cancelBatch()">&times;</button>
            </div>
            <div class="modal-body">
                <div class="progress-section">
                    <div class="p-info">
                        <span id="bpLabel">正在比对...</span>
                        <span id="bpCount">0 / 0</span>
                    </div>
                    <div class="p-bar-bg">
                        <div class="p-bar-fill" id="bpBar" style="width:0%"></div>
                    </div>
                </div>
                <div class="log-list" id="bpLog"></div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="cancelBatch()">关闭</button>
            </div>
        </div>
    </div>

    <script>
        var allProjects = [];
        var allTeams = [];
        var currentTeam = '';
        var currentSearch = '';
        var batchIntervalId = null;
        var currentTaskId = null;

        document.addEventListener('DOMContentLoaded', function() {
            loadTeams();
            loadProjects();
            // 默认选中明天日期
            var t = new Date();
            t.setDate(t.getDate() + 1);
            document.getElementById('deployDate').value = t.toISOString().split('T')[0];
        });

        function loadTeams() {
            fetch('<%= request.getContextPath() %>/team?action=list')
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        allTeams = data.data || [];
                        renderTeamOptions();
                    }
                });
        }

        function renderTeamOptions() {
            var sel = document.getElementById('teamFilter');
            sel.innerHTML = '<option value="">全部团队</option>';
            allTeams.forEach(function(t) {
                sel.innerHTML += '<option value="' + t.id + '">' + escHtml(t.name) + ' (' + escHtml(t.code) + ')</option>';
            });
            var formSel = document.getElementById('teamSelect');
            formSel.innerHTML = '<option value="">-- 暂不分配 --</option>';
            allTeams.forEach(function(t) {
                formSel.innerHTML += '<option value="' + t.id + '">' + escHtml(t.name) + '</option>';
            });
        }

        function loadProjects() {
            var url = '<%= request.getContextPath() %>/git?action=list';
            fetch(url)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        allProjects = data.data || [];
                        renderProjects();
                    }
                });
        }

        function onFilterChange() {
            currentTeam = document.getElementById('teamFilter').value;
            currentSearch = document.getElementById('searchInput').value.trim().toLowerCase();
            renderProjects();
        }

        function renderProjects() {
            var grid = document.getElementById('projectGrid');
            var filtered = allProjects.filter(function(p) {
                if (currentTeam && String(p.teamId) !== currentTeam) return false;
                if (currentSearch) {
                    var match = (p.name && p.name.toLowerCase().includes(currentSearch)) ||
                                (p.repoUrl && p.repoUrl.toLowerCase().includes(currentSearch));
                    if (!match) return false;
                }
                return true;
            });

            document.getElementById('totalCount').textContent = filtered.length;

            if (filtered.length === 0) {
                grid.innerHTML = '<div class="empty-state"><div class="icon">📂</div><h3>暂无代码仓</h3><p>' +
                    (currentSearch ? '没有匹配的项目，请修改搜索条件' : '点击右上角"添加代码仓"开始配置') + '</p></div>';
                return;
            }

            var html = '';
            filtered.forEach(function(p) {
                var teamCls = p.teamId ? 'has-team' : 'no-team';
                var teamBadge = p.teamId && p.teamName
                    ? '<span class="team-badge">' + escHtml(p.teamName) + '</span>'
                    : '<span class="team-badge none">未分配</span>';
                var base = p.baseBranch || 'master';
                var compare = p.compareBranch || 'develop';
                html += '<div class="project-card ' + teamCls + '">' +
                    '<div class="card-header">' +
                        '<div class="card-title">' + escHtml(p.name) + teamBadge + '</div>' +
                    '</div>' +
                    '<div class="card-url">' + escHtml(p.repoUrl) + '</div>' +
                    '<div class="card-branches">' +
                        '<span class="branch-tag base">基准: ' + escHtml(base) + '</span>' +
                        '<span class="branch-tag compare">投产: ' + escHtml(compare) + '日期</span>' +
                    '</div>' +
                    '<div class="card-meta">' +
                        (p.lastSync ? '上次同步: ' + p.lastSync : '从未同步') +
                    '</div>' +
                    '<div class="card-actions">' +
                        '<button class="btn btn-primary btn-sm" onclick="startAnalysis(this, ' + p.id + ', \'' + escJs(p.name) + '\')">🔍 全量分析</button>' +
                        '<button class="btn btn-secondary btn-sm" onclick="editProject(' + p.id + ')">编辑</button>' +
                        '<button class="btn btn-danger btn-sm" onclick="deleteProject(' + p.id + ')">删除</button>' +
                    '</div>' +
                '</div>';
            });
            grid.innerHTML = html;
        }

        // 发起投产比对
        function startDeploy() {
            var deployDate = document.getElementById('deployDate').value;
            if (!deployDate) { alert('请选择投产日期'); return; }
            if (allProjects.length === 0) { alert('当前没有任何代码仓，请先添加'); return; }
            if (!confirm('将对 ' + allProjects.length + ' 个代码仓发起比对（master vs release-' + deployDate.replace(/-/g, '') + '），确认继续？')) return;

            document.getElementById('btnDeploy').disabled = true;
            document.getElementById('batchProgressModal').classList.add('active');
            document.getElementById('batchTitle').textContent = '🚀 投产比对进行中';
            document.getElementById('bpLabel').textContent = '正在提交任务...';
            document.getElementById('bpCount').textContent = '0 / ' + allProjects.length;
            document.getElementById('bpBar').style.width = '0%';
            document.getElementById('bpLog').innerHTML = '';

            var fd = new URLSearchParams();
            fd.append('action', 'compareAll');
            fd.append('deployDate', deployDate);

            fetch('<%= request.getContextPath() %>/version', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    currentTaskId = data.data.taskId;
                    pollTaskStatus(currentTaskId);
                } else {
                    alert('提交失败: ' + (data.message || '未知错误'));
                    document.getElementById('btnDeploy').disabled = false;
                    document.getElementById('batchProgressModal').classList.remove('active');
                }
            })
            .catch(function(err) {
                alert('网络错误: ' + err.message);
                document.getElementById('btnDeploy').disabled = false;
                document.getElementById('batchProgressModal').classList.remove('active');
            });
        }

        function pollTaskStatus(taskId) {
            batchIntervalId = setInterval(function() {
                fetch('<%= request.getContextPath() %>/version?action=status&id=' + taskId)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (!data.success) { clearInterval(batchIntervalId); return; }
                        var vc = data.data;
                        var done = (vc.successCount || 0) + (vc.failCount || 0);
                        var total = vc.totalRepos || allProjects.length;
                        var pct = total > 0 ? Math.round(done / total * 100) : 0;
                        document.getElementById('bpBar').style.width = pct + '%';
                        document.getElementById('bpCount').textContent = done + ' / ' + total;
                        if (vc.status === 'PENDING') {
                            document.getElementById('bpLabel').textContent = '⏳ 任务等待中...';
                        } else if (vc.status === 'DONE' || vc.status === 'FAILED') {
                            clearInterval(batchIntervalId);
                            document.getElementById('bpLabel').textContent = vc.status === 'DONE' ? '✅ 比对完成' : '⚠️ 比对结束';
                            document.getElementById('btnDeploy').disabled = false;
                            addLogItem('batch', vc.status === 'DONE'
                                ? ('✅ 完成！成功 ' + vc.successCount + ' 个，失败 ' + vc.failCount + ' 个')
                                : ('⚠️ 结束 - 成功 ' + vc.successCount + ' 个，失败 ' + vc.failCount + ' 个'));
                        } else {
                            document.getElementById('bpLabel').textContent = '🔥 比对进行中...';
                        }
                    });
            }, 3000);
        }

        function addLogItem(type, msg) {
            var el = document.getElementById(type === 'batch' ? 'bpLog' : 'bpLog');
            if (!el) return;
            var cls = msg.indexOf('✅') >= 0 || msg.indexOf('成功') >= 0 ? 'log-ok' : (msg.indexOf('失败') >= 0 || msg.indexOf('⚠') >= 0 ? 'log-fail' : '');
            el.innerHTML = '<div class="log-item ' + cls + '">' + escHtml(msg) + '</div>' + el.innerHTML;
        }

        function cancelBatch() {
            if (batchIntervalId) { clearInterval(batchIntervalId); batchIntervalId = null; }
            document.getElementById('batchProgressModal').classList.remove('active');
            document.getElementById('btnDeploy').disabled = false;
        }

        // 项目 CRUD
        function openAddModal() {
            document.getElementById('modalTitle').textContent = '添加代码仓';
            document.getElementById('projectId').value = '';
            document.getElementById('projectName').value = '';
            document.getElementById('teamSelect').value = currentTeam;
            document.getElementById('repoUrl').value = '';
            document.getElementById('baseBranch').value = 'master';
            document.getElementById('compareBranch').value = 'release-';
            document.getElementById('projectModal').classList.add('active');
        }

        function closeModal() {
            document.getElementById('projectModal').classList.remove('active');
        }

        function editProject(id) {
            var p = allProjects.find(function(x) { return x.id === id; });
            if (!p) return;
            document.getElementById('modalTitle').textContent = '编辑代码仓';
            document.getElementById('projectId').value = p.id;
            document.getElementById('projectName').value = p.name || '';
            document.getElementById('teamSelect').value = p.teamId || '';
            document.getElementById('repoUrl').value = p.repoUrl || '';
            document.getElementById('baseBranch').value = p.baseBranch || 'master';
            document.getElementById('compareBranch').value = p.compareBranch || 'release-';
            document.getElementById('projectModal').classList.add('active');
        }

        function saveProject() {
            var id = document.getElementById('projectId').value;
            var name = document.getElementById('projectName').value.trim();
            var teamId = document.getElementById('teamSelect').value;
            var repoUrl = document.getElementById('repoUrl').value.trim();
            var baseBranch = document.getElementById('baseBranch').value.trim() || 'master';
            var compareBranch = document.getElementById('compareBranch').value.trim() || 'release-';
            if (!name || !repoUrl) { alert('项目名称和仓库地址不能为空'); return; }

            var fd = new URLSearchParams();
            fd.append('action', id ? 'update' : 'create');
            if (id) fd.append('id', id);
            fd.append('name', name);
            fd.append('teamId', teamId);
            fd.append('repoUrl', repoUrl);
            fd.append('baseBranch', baseBranch);
            fd.append('compareBranch', compareBranch);

            fetch('<%= request.getContextPath() %>/git', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    closeModal();
                    loadProjects();
                } else {
                    alert(data.message || '保存失败');
                }
            });
        }

        function deleteProject(id) {
            if (!confirm('确定要删除这个代码仓吗？')) return;
            var fd = new URLSearchParams({ action: 'delete', id: id });
            fetch('<%= request.getContextPath() %>/git', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) loadProjects();
                else alert(data.message || '删除失败');
            });
        }

        function escHtml(text) {
            if (!text) return '';
            var d = document.createElement('div');
            d.textContent = text;
            return d.innerHTML;
        }

        function escJs(text) {
            if (!text) return '';
            return text.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
        }

        // ── 代码分析 ───────────────────────────────────────────
        var ruleSets = [];
        var analysisIntervalId = null;
        var currentReportId = null;

        function startAnalysis(btnEl, projectId, projectName) {
                        document.getElementById('analysisTitle').textContent = '🔍 代码分析 - ' + projectName;
            document.getElementById('analysisBody').innerHTML = '<div style="text-align:center;padding:30px;"><div style="font-size:32px;margin-bottom:10px;">⏳</div><div>加载中...</div></div>';
            document.getElementById('analysisModal').classList.add('active');

            // 获取规则集列表
            fetch('<%= request.getContextPath() %>/codeAnalysis?action=rulesets')
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        ruleSets = data.data || [];
                        showAnalysisStartForm(projectId, projectName);
                    }
                });
        }

        function showAnalysisStartForm(projectId, projectName) {
            var html = '<div style="background:#f8f9fa;border-radius:12px;padding:20px;margin-bottom:16px;">' +
                '<div style="font-size:14px;font-weight:600;color:#555;margin-bottom:12px;">\ud83e\udd16 智能规范匹配</div>' +
                '<div style="background:#e8f5e9;border-radius:8px;padding:12px 16px;margin-bottom:16px;font-size:13px;color:#2e7d32;">' +
                    '\u2705 系统将自动识别文件类型（Java/前端/Shell/通用），并为每种代码匹配最合适的审查规范' +
                '</div>' +
                '<div class="form-group">' +
                    '<label>分析分支</label>' +
                    '<input type="text" id="analyzeBranch" value="main" placeholder="默认：main" style="width:100%;padding:10px 13px;border:2px solid #e4e7ed;border-radius:9px;font-size:14px;">' +
                '</div>' +
                '<div style="font-size:12px;color:#888;margin-bottom:14px;">\ud83d\udccb 将克隆代码仓并分析所有代码文件，分析时间取决于仓库大小</div>' +
                '<button class="btn btn-primary" style="width:100%;padding:12px;font-size:15px;font-weight:700;" onclick="doStartAnalysis(this, ' + projectId + ')">\ud83d\ude80 开始全量分析</button>' +
            '</div>';
            document.getElementById('analysisBody').innerHTML = html;
        }

        function doStartAnalysis(btnEl, projectId) {
            if (btnEl) { btnEl.disabled = true; btnEl.innerHTML = '\u1f525 \u5206\u6790\u4e2d...'; btnEl.style.opacity = '0.7'; }
            var ruleSet = 'auto';
            var branch = document.getElementById('analyzeBranch').value.trim() || 'main';

            var fd = new URLSearchParams();
            fd.append('action', 'start');
            fd.append('projectId', projectId);
            fd.append('ruleSet', ruleSet);
            fd.append('branch', branch);

            document.getElementById('analysisProgress').style.display = 'block';
            document.getElementById('analysisProgress').innerHTML = '<div style="text-align:center;padding:30px;"><div style="font-size:32px;margin-bottom:10px;">🚀</div><div style="font-weight:600;color:#667eea;margin-bottom:4px;">分析任务已启动</div><div style="font-size:12px;color:#888;">正在克隆代码仓，请稍候...（大仓库可能需要几分钟）</div><div id="apBar" style="margin-top:16px;background:#f0f0f5;border-radius:6px;height:8px;overflow:hidden;"><div id="apFill" style="background:linear-gradient(90deg,#667eea,#764ba2);height:100%;width:0%;transition:width 0.5s;border-radius:6px;"></div></div><div id="apInfo" style="margin-top:8px;font-size:13px;color:#666;text-align:center;">正在连接仓库...</div></div>';

            fetch('<%= request.getContextPath() %>/codeAnalysis', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    currentReportId = data.data.reportId;
                    pollAnalysisStatus(currentReportId);
                } else {
                    document.getElementById('analysisProgress').innerHTML = '<div style="color:#d32f2f;text-align:center;padding:20px;">❌ 启动失败: ' + escHtml(data.message) + '</div>';
                }
            })
            .catch(function(err) {
                document.getElementById('analysisProgress').innerHTML = '<div style="color:#d32f2f;text-align:center;padding:20px;">网络错误: ' + escHtml(err.message) + '</div>';
            });
        }

        function pollAnalysisStatus(reportId) {
            if (analysisIntervalId) clearInterval(analysisIntervalId);
            analysisIntervalId = setInterval(function() {
                fetch('<%= request.getContextPath() %>/codeAnalysis?action=status&id=' + reportId)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (!data.success) { clearInterval(analysisIntervalId); return; }
                        var s = data.data;
                        if (s.status === 'PENDING') {
                            document.getElementById('apInfo').textContent = '⏳ 任务等待中...';
                        } else if (s.status === 'RUNNING') {
                            var pct = s.totalFiles > 0 ? Math.round(s.analyzedFiles / s.totalFiles * 100) : 0;
                            document.getElementById('apFill').style.width = pct + '%';
                            document.getElementById('apInfo').textContent = '🔥 分析中 ' + s.analyzedFiles + ' / ' + s.totalFiles + ' 个文件...';
                        } else if (s.status === 'DONE') {
                            clearInterval(analysisIntervalId);
                            showAnalysisReport(reportId);
                        } else if (s.status === 'FAILED') {
                            clearInterval(analysisIntervalId);
                            document.getElementById('analysisProgress').innerHTML = '<div style="color:#d32f2f;text-align:center;padding:20px;">❌ 分析失败: ' + escHtml(s.report) + '</div>';
                        }
                    });
            }, 3000);
        }

        function showAnalysisReport(reportId) {
            clearInterval(analysisIntervalId);
            document.getElementById('analysisProgress').style.display = 'none';
            fetch('<%= request.getContextPath() %>/codeAnalysis?action=get&id=' + reportId)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (!data.success || !data.data) return;
                    var r = data.data;
                    var costSec = r.costMs ? (r.costMs / 1000).toFixed(1) : '-';
                    var incidentHtml = '';
                    if (r.incidentCheckResult) {
                        incidentHtml = '<div style="border:1px solid #fbbf24;background:#fffbeb;border-radius:12px;overflow:hidden;margin-bottom:16px;">' +
                            '<div style="background:#fbbf24;padding:12px 16px;font-weight:600;font-size:13px;color:#92400e;">⚠️ 历史事故风险检核结果（重点关注）</div>' +
                            '<div style="padding:16px;background:#fff;font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;">' + formatReport(r.incidentCheckResult) + '</div>' +
                        '</div>';
                    }
                    var html = '<div style="margin-bottom:16px;">' +
                        '<div style="display:flex;gap:10px;margin-bottom:12px;flex-wrap:wrap;">' +
                            '<div style="flex:1;min-width:120px;background:#eef0ff;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:24px;font-weight:700;color:#667eea;">' + r.analyzedFiles + '</div><div style="font-size:12px;color:#888;">分析文件</div></div>' +
                            '<div style="flex:1;min-width:120px;background:#fff7ed;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:24px;font-weight:700;color:#ea580c;">' + r.totalIssues + '</div><div style="font-size:12px;color:#888;">发现问题</div></div>' +
                            '<div style="flex:1;min-width:120px;background:#ecfdf5;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:24px;font-weight:700;color:#059669;">' + costSec + 's</div><div style="font-size:12px;color:#888;">分析耗时</div></div>' +
                        '</div>' +
                        '<div style="font-size:13px;color:#888;margin-bottom:8px;">规范: ' + escHtml(r.ruleSet) + ' | 报告ID: ' + r.id + '</div>' +
                    '</div>' +
                    incidentHtml +
                    '<div style="border:1px solid #e4e7ed;border-radius:12px;overflow:hidden;">' +
                        '<div style="background:#fafafa;padding:12px 16px;border-bottom:1px solid #eee;font-weight:600;font-size:13px;color:#555;display:flex;justify-content:space-between;align-items:center;">' +
                            '<span>📋 分析报告</span>' +
                            '<button class="btn btn-sm btn-secondary" onclick="downloadReport(' + r.id + ')">📥 下载</button>' +
                        '</div>' +
                        '<div id="reportContent" style="max-height:500px;overflow-y:auto;padding:16px;background:#fff;font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;">' + formatReport(r.report) + '</div>' +
                    '</div>';
                    document.getElementById('analysisResult').style.display = 'block';
                    document.getElementById('analysisResult').innerHTML = html;
                });
        }

        function formatReport(text) {
            if (!text) return '';
            return escHtml(text);
        }

        function downloadReport(reportId) {
            window.open('<%= request.getContextPath() %>/codeAnalysis?action=get&id=' + reportId, '_blank');
        }

        function closeAnalysis() {
            if (analysisIntervalId) { clearInterval(analysisIntervalId); analysisIntervalId = null; }
            document.getElementById('analysisModal').classList.remove('active');
        }
    
        // === \u5386\u53f2\u62a5\u544a ===
        var currentHistoryPage = 1;

        function openAnalysisHistory() {
            currentHistoryPage = 1;
            document.getElementById('historyBody').innerHTML = '<div style="text-align:center;padding:40px;color:#888;">\u52a0\u8f7d\u4e2d...</div>';
            document.getElementById('historyModal').classList.add('active');
            loadHistoryPage(1);
        }

        function loadHistoryPage(page) {
            currentHistoryPage = page;
            fetch('/qiuniu/codeAnalysis?action=history&page=' + page + '&pageSize=10')
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) renderHistoryList(data.data);
                    else document.getElementById('historyBody').innerHTML = '<div style="text-align:center;padding:40px;color:#d32f2f;">\u52a0\u8f7d\u5931\u8d25: ' + escHtml(data.message || '') + '</div>';
                });
        }

        function renderHistoryList(d) {
            var rows = '';
            for (var i = 0; i < d.list.length; i++) {
                var r = d.list[i];
                var statusTxt = r.status === 'DONE' ? '\u5b8c\u6210' : (r.status === 'FAILED' ? '\u5931\u8d25' : '\u8fdb\u884c\u4e2d');
                var statusBg = r.status === 'DONE' ? '#e8f5e9' : (r.status === 'FAILED' ? '#ffebee' : '#fff8e1');
                var statusColor = r.status === 'DONE' ? '#2e7d32' : (r.status === 'FAILED' ? '#c62828' : '#f57c00');
                var issues = r.totalIssues || 0;
                var cost = r.costMs ? (r.costMs / 1000).toFixed(1) + 's' : '-';
                var created = r.createdAt ? r.createdAt.replace('T', ' ').substring(0, 19) : '-';
                rows += '<tr>' +
                    '<td style="padding:10px 12px;font-weight:600;max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="' + escHtml(r.projectName || '') + '">' + escHtml(r.projectName || '-') + '</td>' +
                    '<td style="padding:10px 12px;color:#555;">' + getRuleName(r.ruleSet) + '</td>' +
                    '<td style="padding:10px 12px;"><span style="background:' + statusBg + ';color:' + statusColor + ';padding:3px 10px;border-radius:12px;font-size:12px;font-weight:600;">' + statusTxt + '</span></td>' +
                    '<td style="padding:10px 12px;text-align:center;color:#888;">' + (r.analyzedFiles||0) + '/' + (r.totalFiles||0) + '</td>' +
                    '<td style="padding:10px 12px;text-align:center;font-weight:700;font-size:15px;color:' + (issues > 0 ? '#ea580c' : '#388e3c') + ';">' + issues + '</td>' +
                    '<td style="padding:10px 12px;text-align:center;color:#888;">' + cost + '</td>' +
                    '<td style="padding:10px 12px;color:#888;white-space:nowrap;font-size:12px;">' + created + '</td>' +
                    '<td style="padding:10px 12px;white-space:nowrap;"><button class="btn btn-sm btn-primary" style="margin-right:4px;" onclick="viewHistoryReport(' + r.id + ')">\u67e5\u770b</button><button class="btn btn-sm btn-danger" onclick="deleteHistoryReport(' + r.id + ')">\u5220\u9664</button></td>' +
                '</tr>';
            }
            var pagHtml = '';
            if (d.totalPages > 1) {
                pagHtml = '<div style="padding:12px;display:flex;justify-content:center;gap:8px;align-items:center;">' +
                    (d.page > 1 ? '<button class="btn btn-sm btn-secondary" onclick="loadHistoryPage(' + (d.page-1) + ')">\u4e0a\u4e00\u9875</button>' : '') +
                    '<span style="font-size:13px;color:#888;padding:0 8px;">\u7b2c ' + d.page + ' / ' + d.totalPages + ' \u9875\uff0c\u5171 ' + d.total + ' \u6761</span>' +
                    (d.page < d.totalPages ? '<button class="btn btn-sm btn-secondary" onclick="loadHistoryPage(' + (d.page+1) + ')">\u4e0b\u4e00\u9875</button>' : '') +
                '</div>';
            }
            var header = '<div style="padding:12px 16px;background:#fafafa;border-bottom:1px solid #eee;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">' +
                '<div style="font-size:13px;color:#888;">\u5171 <b>' + d.total + '</b> \u6761\u62a5\u544a</div>' +
                '<div style="display:flex;gap:8px;align-items:center;">' +
                    '<input type="number" id="purgeDays" value="90" min="1" style="width:72px;padding:6px 8px;border:1.5px solid #e4e7ed;border-radius:6px;font-size:13px;">' +
                    '<button class="btn btn-sm btn-danger" onclick="purgeOldReports()">\u6e05\u7406 N \u5929\u524d</button>' +
                '</div>' +
            '</div>';
            var table = '<table style="width:100%;border-collapse:collapse;font-size:13px;"><thead style="background:#f5f6fa;"><tr>' +
                '<th style="padding:10px 12px;text-align:left;color:#666;font-weight:600;">\u9879\u76ee</th>' +
                '<th style="padding:10px 12px;text-align:left;color:#666;font-weight:600;">\u89c4\u8303</th>' +
                '<th style="padding:10px 12px;text-align:center;color:#666;font-weight:600;">\u72b6\u6001</th>' +
                '<th style="padding:10px 12px;text-align:center;color:#666;font-weight:600;">\u8fdb\u5ea6</th>' +
                '<th style="padding:10px 12px;text-align:center;color:#666;font-weight:600;">\u95ee\u9898</th>' +
                '<th style="padding:10px 12px;text-align:center;color:#666;font-weight:600;">\u8017\u65f6</th>' +
                '<th style="padding:10px 12px;text-align:left;color:#666;font-weight:600;">\u65f6\u95f4</th>' +
                '<th style="padding:10px 12px;text-align:center;color:#666;font-weight:600;">\u64cd\u4f5c</th>' +
                '</tr></thead><tbody>' + rows + '</tbody></table>';
            document.getElementById('historyBody').innerHTML = (rows ? header + table + pagHtml : '<div style="text-align:center;padding:60px;color:#bbb;">\u6682\u65e0\u5206\u6790\u62a5\u544a</div>');
        }

        function viewHistoryReport(id) {
            document.getElementById('historyModal').classList.remove('active');
            document.getElementById('analysisModal').classList.add('active');
            document.getElementById('analysisBody').innerHTML = '<div style="text-align:center;padding:40px;color:#888;">\u52a0\u8f7d\u4e2d...</div>';
            fetch('/qiuniu/codeAnalysis?action=get&id=' + id)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success && data.data) {
                        var r = data.data;
                        document.getElementById('analysisTitle').textContent = '\u5206\u6790\u62a5\u544a - ' + (r.projectName || '');
                        var cost = r.costMs ? (r.costMs/1000).toFixed(1)+'s' : '-';
                        document.getElementById('analysisBody').innerHTML =
                            '<div style="padding:16px 20px;background:#fafafa;border-bottom:1px solid #eee;">' +
                                '<div style="display:flex;gap:10px;flex-wrap:wrap;">' +
                                    '<div style="flex:1;min-width:120px;background:#eef0ff;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:22px;font-weight:700;color:#667eea;">' + (r.analyzedFiles||0) + '</div><div style="font-size:12px;color:#888;">\u5206\u6790\u6587\u4ef6</div></div>' +
                                    '<div style="flex:1;min-width:120px;background:#fff7ed;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:22px;font-weight:700;color:#ea580c;">' + (r.totalIssues||0) + '</div><div style="font-size:12px;color:#888;">\u53d1\u73b0\u95ee\u9898</div></div>' +
                                    '<div style="flex:1;min-width:120px;background:#ecfdf5;border-radius:10px;padding:12px;text-align:center;"><div style="font-size:22px;font-weight:700;color:#059669;">' + cost + '</div><div style="font-size:12px;color:#888;">\u8017\u65f6</div></div>' +
                                '</div>' +
                                '<div style="font-size:12px;color:#888;margin-top:10px;">\u89c4\u8303: ' + getRuleName(r.ruleSet) + ' &nbsp;|&nbsp; ' + (r.createdAt||'') + '</div>' +
                            '</div>' +
                            '<div style="padding:20px;font-size:13px;line-height:1.8;white-space:pre-wrap;word-break:break-all;max-height:65vh;overflow-y:auto;">' + formatReport(r.report) + '</div>';
                    } else {
                        document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">\u62a5\u544a\u4e0d\u5b58\u5728</div>';
                    }
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                }).catch(function(err) {
                    document.getElementById('analysisBody').innerHTML = '<div style="color:#d32f2f;padding:40px;text-align:center;">加载失败: ' + (err.message || '网络错误') + '</div>';
                });
        }

        function deleteHistoryReport(id) {
            if (!confirm('\u786e\u5b9a\u5220\u9664\u8fd9\u6761\u62a5\u544a\uff1f')) return;
            fetch('/qiuniu/codeAnalysis', {method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'action=adminDelete&id='+id})
                .then(function(r){return r.json()})
                .then(function(d){ if(d.success) loadHistoryPage(currentHistoryPage); else alert('\u5220\u9664\u5931\u8d25: '+(d.message||'')); });
        }

        function purgeOldReports() {
            var days = document.getElementById('purgeDays') ? document.getElementById('purgeDays').value : 90;
            if (!confirm('\u786e\u5b9a\u5220\u9664 '+days+'\u5929\u524d\u7684\u6240\u6709\u62a5\u544a\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u6062\u590d\uff01')) return;
            fetch('/qiuniu/codeAnalysis', {method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'action=adminPurge&days='+days})
                .then(function(r){return r.json()})
                .then(function(d){ alert(d.message||''); if(d.success) loadHistoryPage(1); });
        }

        function closeHistory() {
            document.getElementById('historyModal').classList.remove('active');
        }

        function getRuleName(rs) {
            var m = {'alibaba-java':'\u963f\u91cc\u5df1\u4e4bJava\u89c4\u8303','generic-security':'\u901a\u7528\u5b89\u5168\u5ba1\u67e5','google-java':'Google Java\u89c4\u8303','frontend-security':'\u524d\u7aef\u5b89\u5168\u89c4\u8303'};
            return m[rs] || rs || 'default';
        }

    </script>
    <!-- 历史报告弹窗 -->
    <div class="modal" id="historyModal">
        <div class="modal-content" style="width:900px;max-width:95%;">
            <div class="modal-header">
                <h2 id="historyTitle">历史分析报告</h2>
                <button class="modal-close" onclick="closeHistory()">&times;</button>
            </div>
            <div class="modal-body" id="historyBody" style="padding:0;">
                <!-- 动态内容 -->
            </div>
        </div>
    </div>


</body>
</html>
