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
    <title>团队看板 - 囚牛 Git</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            background: #f0f2f5;
            min-height: 100vh;
        }
        .header {
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            color: white;
            padding: 15px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 15px rgba(0,0,0,0.3);
        }
        .header h1 { font-size: 22px; letter-spacing: 2px; }
        .header .subtitle { font-size: 12px; opacity: 0.7; margin-left: 15px; }
        .header .nav { display: flex; gap: 12px; align-items: center; }
        .header .nav a {
            color: white; text-decoration: none; padding: 7px 16px;
            border-radius: 6px; background: rgba(255,255,255,0.12); font-size: 13px;
            transition: background 0.2s;
        }
        .header .nav a:hover { background: rgba(255,255,255,0.25); }
        .header .nav a.active { background: rgba(255,255,255,0.3); }

        .container { max-width: 1500px; margin: 0 auto; padding: 25px 30px; }

        .stats-bar {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 15px;
            margin-bottom: 25px;
        }
        .stat-card {
            background: white; border-radius: 12px; padding: 20px;
            text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.06);
            transition: transform 0.2s;
        }
        .stat-card:hover { transform: translateY(-2px); }
        .stat-card .number { font-size: 32px; font-weight: 700; color: #1a1a2e; }
        .stat-card .label { font-size: 13px; color: #888; margin-top: 5px; }

        .batch-toolbar {
            background: white; border-radius: 12px; padding: 15px 20px;
            margin-bottom: 20px; display: flex; gap: 10px; align-items: center;
            box-shadow: 0 2px 8px rgba(0,0,0,0.06); flex-wrap: wrap;
        }
        .btn {
            padding: 8px 18px; border: none; border-radius: 8px; cursor: pointer;
            font-size: 13px; font-weight: 500; transition: all 0.2s;
        }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
        .btn-secondary { background: #f0f0f0; color: #333; }
        .btn-warning { background: #ff9f43; color: white; }
        .btn-danger { background: #ff4757; color: white; }
        .btn-sm { padding: 5px 12px; font-size: 12px; }
        .toolbar-sep { width: 1px; height: 30px; background: #ddd; margin: 0 5px; }

        .teams-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
            gap: 20px;
        }
        .team-card {
            background: white; border-radius: 14px; overflow: hidden;
            box-shadow: 0 2px 12px rgba(0,0,0,0.07); transition: box-shadow 0.2s;
        }
        .team-card:hover { box-shadow: 0 6px 24px rgba(0,0,0,0.12); }
        .team-header {
            padding: 18px 20px; display: flex; justify-content: space-between;
            align-items: center; border-bottom: 1px solid #f0f0f0;
        }
        .team-header.team-trade  { background: rgba(102,126,234,0.08); border-left: 4px solid #667eea; }
        .team-header.team-batch  { background: rgba(17,153,142,0.08);  border-left: 4px solid #11998e; }
        .team-header.team-agent  { background: rgba(245,87,108,0.08);  border-left: 4px solid #f5576c; }
        .team-header.team-base   { background: rgba(79,172,254,0.08); border-left: 4px solid #4facfe; }
        .team-header.team-admin  { background: rgba(250,112,154,0.08);border-left: 4px solid #fa709a; }
        .team-header.team-default { background: rgba(0,0,0,0.02);    border-left: 4px solid #999; }

        .team-info .team-name { font-size: 16px; font-weight: 700; color: #1a1a2e; }
        .team-info .team-code { font-size: 12px; color: #999; margin-top: 2px; }
        .team-actions { display: flex; gap: 6px; }
        .team-body { padding: 15px 20px; }
        .team-meta { font-size: 12px; color: #aaa; margin-bottom: 12px; }
        .team-meta span { margin-right: 15px; }

        .project-list { list-style: none; }
        .project-item {
            border: 1px solid #f0f0f0; border-radius: 8px; padding: 12px 15px;
            margin-bottom: 8px; display: flex; justify-content: space-between;
            align-items: center; transition: all 0.15s;
        }
        .project-item:hover { border-color: #667eea; background: #fafafe; }
        .project-item .proj-name { font-size: 14px; color: #333; font-weight: 500; }
        .project-item .proj-branches { font-size: 11px; color: #999; margin-top: 3px; }
        .project-item .proj-actions { display: flex; gap: 5px; }
        .branch-tag {
            background: #e8f4fd; color: #1976d2; padding: 2px 7px;
            border-radius: 4px; font-size: 11px; margin-right: 4px;
        }
        .no-projects { text-align: center; padding: 20px; color: #ccc; font-size: 13px; }

        .modal {
            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.6); z-index: 1000;
            justify-content: center; align-items: center;
        }
        .modal.active { display: flex; }
        .modal-content {
            background: white; border-radius: 15px; width: 95%;
            max-width: 1100px; max-height: 90vh; overflow: hidden;
            display: flex; flex-direction: column;
        }
        .modal-header {
            padding: 18px 24px; border-bottom: 1px solid #eee;
            display: flex; justify-content: space-between; align-items: center;
            background: #fafafa; border-radius: 15px 15px 0 0;
        }
        .modal-header h2 { font-size: 18px; color: #1a1a2e; }
        .modal-close { background: none; border: none; font-size: 26px; cursor: pointer; color: #999; line-height: 1; }
        .modal-close:hover { color: #333; }
        .modal-body { padding: 20px 24px; overflow-y: auto; flex: 1; }

        .diff-panel {
            background: #1e1e1e; color: #d4d4d4; border-radius: 10px;
            font-family: 'Consolas', 'Monaco', monospace; font-size: 13px;
            padding: 15px; max-height: 60vh; overflow-y: auto;
            white-space: pre-wrap; line-height: 1.6;
        }

        .batch-progress {
            display: none; background: #fff8e1; border: 1px solid #ffe082;
            border-radius: 10px; padding: 20px; margin-bottom: 20px; text-align: center;
        }
        .batch-progress.active { display: block; }
        .batch-progress .spinner {
            width: 36px; height: 36px; border: 4px solid #e0e0e0;
            border-top-color: #667eea; border-radius: 50%;
            animation: spin 0.8s linear infinite; margin: 0 auto 12px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .batch-progress .text { font-size: 14px; color: #666; }
        .batch-progress .sub { font-size: 12px; color: #aaa; margin-top: 5px; }

        .batch-results { display: none; }
        .batch-results.active { display: block; }

        .result-summary {
            display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
            gap: 12px; margin-bottom: 20px;
        }
        .result-summary .rs-item {
            background: #f8f9fa; border-radius: 8px; padding: 12px; text-align: center;
        }
        .result-summary .rs-item .num { font-size: 24px; font-weight: 700; color: #1a1a2e; }
        .result-summary .rs-item .lbl { font-size: 12px; color: #888; margin-top: 3px; }
        .result-summary .rs-item.success .num { color: #26de81; }
        .result-summary .rs-item.fail .num { color: #ff4757; }

        .diff-accordion-item {
            border: 1px solid #e0e0e0; border-radius: 10px;
            margin-bottom: 10px; overflow: hidden;
        }
        .diff-accordion-header {
            padding: 12px 16px; background: #f8f9fa; cursor: pointer;
            display: flex; justify-content: space-between; align-items: center;
            font-size: 14px; user-select: none;
        }
        .diff-accordion-header:hover { background: #f0f0f5; }
        .diff-accordion-header .status-ok { color: #26de81; font-weight: 700; }
        .diff-accordion-header .status-fail { color: #ff4757; font-weight: 700; }
        .diff-accordion-body { display: none; padding: 12px; background: #1e1e1e; }
        .diff-accordion-body.open { display: block; }

        .no-team-section {
            margin-top: 30px; background: white; border-radius: 14px;
            padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.07);
        }
        .no-team-section h3 { font-size: 16px; color: #999; margin-bottom: 15px; }

        .mini-form { display: none; }
        .mini-form.active { display: block; }
        .form-group { margin-bottom: 15px; }
        .form-group label {
            display: block; margin-bottom: 6px; color: #555;
            font-size: 13px; font-weight: 500;
        }
        .form-group input {
            width: 100%; padding: 9px 12px; border: 2px solid #e0e0e0;
            border-radius: 8px; font-size: 13px; font-family: inherit;
        }
        .form-group input:focus { outline: none; border-color: #667eea; }
    </style>
</head>
<body>
    <div class="header">
        <div style="display:flex;align-items:center;">
            <h1>🦞 团队看板</h1>
            <span class="subtitle">Git 分支差异统一视图</span>
        </div>
        <div class="nav">
            <a href="<%= request.getContextPath() %>/team-dashboard.jsp" class="active">团队看板</a>
            <a href="<%= request.getContextPath() %>/version-compare.jsp">投产变更</a>
            <a href="<%= request.getContextPath() %>/git-projects.jsp">项目列表</a>
            <a href="<%= request.getContextPath() %>/dashboard.jsp">提示词管理</a>
            <a href="<%= request.getContextPath() %>/logout">退出</a>
        </div>
    </div>

    <div class="container">
        <div class="stats-bar" id="statsBar">
            <div class="stat-card"><div class="number" id="statTeams">-</div><div class="label">团队数量</div></div>
            <div class="stat-card"><div class="number" id="statProjects">-</div><div class="label">项目总数</div></div>
            <div class="stat-card"><div class="number" id="statNoTeam">-</div><div class="label">待分配项目</div></div>
        </div>

        <div class="batch-toolbar">
            <button class="btn btn-primary" onclick="showNewTeamForm()" id="btnNewTeam" style="display:none">+ 新建团队</button>
            <div class="toolbar-sep" id="sepNewTeam" style="display:none"></div>
            <button class="btn btn-warning" onclick="batchAllTeams()" id="btnBatchAll" disabled>⚡ 全部团队批量拉差异</button>
            <button class="btn btn-secondary" onclick="refreshDashboard()">🔄 刷新</button>
        </div>

        <div class="batch-toolbar mini-form" id="newTeamForm" style="flex-direction:column;align-items:stretch;">
            <div style="display:grid;grid-template-columns:1fr 1fr 2fr auto;gap:10px;align-items:end;">
                <div class="form-group" style="margin:0;">
                    <label>团队名称 *</label>
                    <input type="text" id="ntName" placeholder="例如：交易线团队">
                </div>
                <div class="form-group" style="margin:0;">
                    <label>团队代码 *</label>
                    <input type="text" id="ntCode" placeholder="例如：trade（英文小写，唯一）">
                </div>
                <div class="form-group" style="margin:0;">
                    <label>描述</label>
                    <input type="text" id="ntDesc" placeholder="团队职能说明（可选）">
                </div>
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-primary" onclick="createTeam()">创建</button>
                    <button class="btn btn-secondary" onclick="hideNewTeamForm()">取消</button>
                </div>
            </div>
        </div>

        <div class="batch-progress" id="batchProgress">
            <div class="spinner"></div>
            <div class="text" id="batchProgressText">正在并行拉取差异...</div>
            <div class="sub" id="batchProgressSub">请稍候，最多等待 10 分钟</div>
        </div>

        <div class="batch-results" id="batchResults"></div>

        <div class="teams-grid" id="teamsGrid">
            <div style="text-align:center;padding:40px;color:#ccc;">加载中...</div>
        </div>

        <div class="no-team-section" id="noTeamSection" style="display:none;">
            <h3>📦 尚未分配团队的项目</h3>
            <div id="noTeamProjects"></div>
        </div>
    </div>

    <div class="modal" id="diffModal">
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="diffModalTitle">分支差异</h2>
                <button class="modal-close" onclick="document.getElementById('diffModal').classList.remove('active')">x</button>
            </div>
            <div class="modal-body">
                <div class="diff-panel" id="diffModalContent">加载中...</div>
            </div>
        </div>
    </div>

    <script>
        var dashboardData = null;
        var batchRunning = false;
        var userRole = localStorage.getItem('userRole') || 'VIEWER';
        var canWrite = (userRole === 'ADMIN' || userRole === 'TEAM_ADMIN');
        var isAdmin = (userRole === 'ADMIN');

        document.addEventListener('DOMContentLoaded', function() {
            refreshDashboard();
        });

        function refreshDashboard() {
            fetch('<%= request.getContextPath() %>/team?action=dashboard')
                .then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        dashboardData = data.data;
                        renderDashboard(data.data);
                    } else {
                        alert(data.message || '加载失败');
                    }
                })
                .catch(function(err) { alert('网络错误: ' + err.message); });
        }

        // 根据角色控制按钮显隐
        function applyRolePermissions() {
            var btnNewTeam = document.getElementById('btnNewTeam');
            var sepNewTeam = document.getElementById('sepNewTeam');
            if (btnNewTeam) {
                btnNewTeam.style.display = isAdmin ? 'inline-block' : 'none';
            }
            if (sepNewTeam) {
                sepNewTeam.style.display = isAdmin ? 'block' : 'none';
            }
        }

        function renderDashboard(data) {
            var teams = data.teams || [];
            var noTeamProjects = data.noTeamProjects || [];

            // 应用角色权限
            applyRolePermissions();

            var totalProjects = 0;
            for (var i = 0; i < teams.length; i++) {
                totalProjects += (teams[i].projects || []).length;
            }
            document.getElementById('statTeams').textContent = teams.length;
            document.getElementById('statProjects').textContent = totalProjects;
            document.getElementById('statNoTeam').textContent = noTeamProjects.length;
            document.getElementById('btnBatchAll').disabled = (totalProjects === 0);

            var grid = document.getElementById('teamsGrid');
            if (teams.length === 0) {
                grid.innerHTML = '<div style="grid-column:1/-1;text-align:center;padding:60px 0;color:#999;">' +
                    '<div style="font-size:48px;margin-bottom:15px;">📂</div>' +
                    '<div>暂无团队，点击右上角"新建团队"开始</div></div>';
            } else {
                var html = '';
                for (var ti = 0; ti < teams.length; ti++) {
                    var t = teams[ti];
                    var team = t.team;
                    var projects = t.projects || [];
                    var colorClass = getTeamColorClass(team.code);
                    var createdDate = team.createdAt ? team.createdAt.substring(0, 10) : '-';
                    html += '<div class="team-card">' +
                        '<div class="team-header ' + colorClass + '">' +
                            '<div class="team-info">' +
                                '<div class="team-name">' + escHtml(team.name || '') + '</div>' +
                                '<div class="team-code">' + escHtml(team.code || '') + ' | ' + escHtml(team.description || '') + '</div>' +
                            '</div>' +
                            '<div class="team-actions">' +
                                (canWrite ? '<button class="btn btn-warning btn-sm" onclick="batchDiffTeam(' + team.id + ')" ' + (projects.length === 0 ? 'disabled' : '') + '>⚡ 批量拉差异</button>' : '') +
                                (isAdmin ? '<button class="btn btn-secondary btn-sm" onclick="deleteTeam(' + team.id + ')">删除</button>' : '') +
                            '</div>' +
                        '</div>' +
                        '<div class="team-body">' +
                            '<div class="team-meta"><span>📦 ' + projects.length + ' 个项目</span><span>🕐 ' + createdDate + '</span></div>';

                    if (projects.length === 0) {
                        html += '<div class="no-projects">暂无项目，可在"项目列表"中为此团队添加</div>';
                    } else {
                        html += '<ul class="project-list">';
                        for (var pi = 0; pi < projects.length; pi++) {
                            var p = projects[pi];
                            html += '<li class="project-item">' +
                                '<div>' +
                                    '<div class="proj-name">' + escHtml(p.name || '') + '</div>' +
                                    '<div class="proj-branches">' +
                                        '<span class="branch-tag">' + escHtml(p.baseBranch || '') + '</span>' +
                                        '<span class="branch-tag">' + escHtml(p.compareBranch || '') + '</span>' +
                                    '</div>' +
                                '</div>' +
                                '<div class="proj-actions">' +
                                    '<button class="btn btn-primary btn-sm" onclick="viewDiff(' + p.id + ')">差异</button>' +
                                    (canWrite ? '<button class="btn btn-secondary btn-sm" onclick="location.href=\'<%= request.getContextPath() %>/git-projects.jsp?teamId=' + (p.teamId || '') + '\'">管理</button>' : '') +
                                '</div>' +
                            '</li>';
                        }
                        html += '</ul>';
                    }
                    html += '</div></div>';
                }
                grid.innerHTML = html;
            }

            var noTeamSection = document.getElementById('noTeamSection');
            var noTeamDiv = document.getElementById('noTeamProjects');
            if (noTeamProjects.length > 0) {
                noTeamSection.style.display = 'block';
                var html = '<ul class="project-list">';
                for (var ni = 0; ni < noTeamProjects.length; ni++) {
                    var np = noTeamProjects[ni];
                    html += '<li class="project-item">' +
                        '<div>' +
                            '<div class="proj-name">' + escHtml(np.name || '') + '</div>' +
                            '<div class="proj-branches">' +
                                '<span class="branch-tag">' + escHtml(np.baseBranch || '') + '</span>' +
                                '<span class="branch-tag">' + escHtml(np.compareBranch || '') + '</span>' +
                            '</div>' +
                        '</div>' +
                        '<div class="proj-actions">' +
                            '<button class="btn btn-primary btn-sm" onclick="viewDiff(' + np.id + ')">差异</button>' +
                            (canWrite ? '<button class="btn btn-secondary btn-sm" onclick="location.href=\'<%= request.getContextPath() %>/git-projects.jsp\'">分配团队</button>' : '') +
                        '</div>' +
                    '</li>';
                }
                html += '</ul>';
                noTeamDiv.innerHTML = html;
            } else {
                noTeamSection.style.display = 'none';
            }
        }

        function getTeamColorClass(code) {
            var map = {
                'trade': 'team-trade',
                'batch': 'team-batch',
                'agent': 'team-agent',
                'base': 'team-base',
                'admin': 'team-admin'
            };
            return map[code] || 'team-default';
        }

        function showNewTeamForm() {
            document.getElementById('newTeamForm').classList.add('active');
            document.getElementById('ntName').focus();
        }
        function hideNewTeamForm() {
            document.getElementById('newTeamForm').classList.remove('active');
            document.getElementById('ntName').value = '';
            document.getElementById('ntCode').value = '';
            document.getElementById('ntDesc').value = '';
        }

        function createTeam() {
            var name = document.getElementById('ntName').value.trim();
            var code = document.getElementById('ntCode').value.trim();
            var description = document.getElementById('ntDesc').value.trim();
            if (!name || !code) { alert('名称和代码不能为空'); return; }

            var fd = new URLSearchParams();
            fd.append('action', 'create');
            fd.append('name', name);
            fd.append('code', code);
            fd.append('description', description);

            fetch('<%= request.getContextPath() %>/team', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            }).then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
              .then(function(data) {
                  if (data.success) {
                      hideNewTeamForm();
                      refreshDashboard();
                  } else {
                      alert(data.message || '创建失败');
                  }
              });
        }

        function deleteTeam(id) {
            if (!confirm('确定删除此团队？团队内的项目不会被删除，只是解除归属关系。')) return;
            var fd = new URLSearchParams({ action: 'delete', id: id });
            fetch('<%= request.getContextPath() %>/team', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            }).then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
              .then(function(data) {
                  if (data.success) refreshDashboard();
                  else alert(data.message || '删除失败');
              });
        }

        function viewDiff(projectId) {
            var modal = document.getElementById('diffModal');
            document.getElementById('diffModalTitle').textContent = '加载中...';
            document.getElementById('diffModalContent').textContent = '正在获取差异，请稍候...';
            modal.classList.add('active');

            fetch('<%= request.getContextPath() %>/git?action=diff&id=' + projectId)
                .then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
                .then(function(data) {
                    if (data.success) {
                        var p = data.data;
                        var diff = p.diff || '无差异';
                        document.getElementById('diffModalTitle').textContent =
                            (p.projectName || '项目') + ' — ' + (p.baseBranch || 'main') + ' vs ' + (p.compareBranch || 'develop');
                        document.getElementById('diffModalContent').textContent = diff;
                    } else {
                        document.getElementById('diffModalContent').textContent = '获取失败: ' + (data.message || '');
                    }
                });
        }

        function batchDiffTeam(teamId) {
            if (batchRunning) { alert('已有批量任务在进行中'); return; }
            runBatchDiff('?action=batchDiff&teamId=' + teamId, '团队');
        }

        function batchAllTeams() {
            if (batchRunning) { alert('已有批量任务在进行中'); return; }
            runBatchDiff('?action=batchDiff', '全部团队');
        }

        function runBatchDiff(urlSuffix, label) {
            batchRunning = true;
            var progress = document.getElementById('batchProgress');
            var results = document.getElementById('batchResults');
            progress.classList.add('active');
            results.classList.remove('active');
            document.getElementById('btnBatchAll').disabled = true;
            document.getElementById('batchProgressText').textContent = '正在为 ' + label + ' 并行拉取差异...';
            document.getElementById('batchProgressSub').textContent = '后台运行中，可以切换到其他标签页';

            fetch('<%= request.getContextPath() %>/git' + urlSuffix)
                .then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
                .then(function(data) {
                    progress.classList.remove('active');
                    batchRunning = false;
                    document.getElementById('btnBatchAll').disabled = false;
                    if (data.success) {
                        renderBatchResults(data.data);
                    } else {
                        alert(data.message || '批量差异获取失败');
                    }
                })
                .catch(function(err) {
                    progress.classList.remove('active');
                    batchRunning = false;
                    document.getElementById('btnBatchAll').disabled = false;
                    alert('网络错误: ' + err.message);
                });
        }

        function renderBatchResults(data) {
            var results = document.getElementById('batchResults');
            results.classList.add('active');

            var items = data.results || [];
            var failed = data.failed || [];

            var html = '<h3 style="margin-bottom:15px;font-size:16px;color:#1a1a2e;">📊 批量差异结果</h3>' +
                '<div class="result-summary">' +
                    '<div class="rs-item success"><div class="num">' + (data.successCount || 0) + '</div><div class="lbl">成功</div></div>' +
                    '<div class="rs-item fail"><div class="num">' + (data.failedCount || 0) + '</div><div class="lbl">失败</div></div>' +
                    '<div class="rs-item"><div class="num">' + items.length + '</div><div class="lbl">总项目</div></div>' +
                '</div>';

            if (items.length === 0 && failed.length === 0) {
                html += '<div style="text-align:center;padding:30px;color:#999;">暂无数据</div>';
            } else {
                for (var i = 0; i < items.length; i++) {
                    var item = items[i];
                    var diff = item.diff || '';
                    var statEnd = diff.indexOf('--- Details ---');
                    var stat = statEnd > 0 ? diff.substring(0, statEnd) : diff.substring(0, 500);

                    html += '<div class="diff-accordion-item">' +
                        '<div class="diff-accordion-header" onclick="toggleAccordion(this)">' +
                            '<span><strong>' + escHtml(item.projectName || '') + '</strong> &nbsp;' +
                                '<span class="branch-tag">' + escHtml(item.baseBranch || '') + '</span>' +
                                '<span class="branch-tag">' + escHtml(item.compareBranch || '') + '</span></span>' +
                            '<span><span class="status-ok">✓ 成功</span> &nbsp;▾</span>' +
                        '</div>' +
                        '<div class="diff-accordion-body">' +
                            '<div style="color:#d4d4d4;font-size:12px;margin-bottom:8px;white-space:pre-wrap;">' + escHtml(stat.trim()) + '</div>' +
                            '<pre style="white-space:pre-wrap;font-size:12px;color:#888;max-height:300px;overflow:auto;">' + escHtml(diff) + '</pre>' +
                        '</div>' +
                    '</div>';
                }

                if (failed.length > 0) {
                    html += '<div style="margin-top:15px;"><h4 style="color:#ff4757;margin-bottom:10px;">❌ 失败项目</h4>';
                    for (var fi = 0; fi < failed.length; fi++) {
                        var f = failed[fi];
                        html += '<div style="padding:8px 12px;background:#fff5f5;border-radius:6px;margin-bottom:6px;color:#ff4757;font-size:13px;">' +
                            escHtml(f.projectName || f.repoUrl || '') + ' — ' + escHtml(f.error || '') + '</div>';
                    }
                    html += '</div>';
                }
            }

            results.innerHTML = html;
            results.scrollIntoView({ behavior: 'smooth' });
        }

        function toggleAccordion(header) {
            var body = header.nextElementSibling;
            if (body.classList) {
                body.classList.toggle('open');
            }
        }

        function escHtml(s) {
            if (!s) return '';
            var d = document.createElement('div');
            d.textContent = s;
            return d.innerHTML;
        }

        // ==================== AI 手动分析 ====================

        function escJs(s) {
            if (!s) return '';
            return s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r');
        }

        function triggerAiAnalysis(projectName, diff, baseBranch, compareBranch) {
            if (!diff || diff.trim().length < 5) {
                alert('diff 内容为空，无法分析');
                return;
            }
            var modal = document.getElementById('aiModal');
            document.getElementById('aiModalTitle').textContent = 'AI 正在分析: ' + projectName;
            document.getElementById('aiModalContent').textContent = 'AI 正在分析代码风险，请稍候...';
            modal.classList.add('active');

            var payload = JSON.stringify({
                projectName: projectName || '',
                diff: diff || '',
                baseBranch: baseBranch || 'master',
                compareBranch: compareBranch || '',
                versionName: ''
            });

            fetch('<%= request.getContextPath() %>/version?action=analyzeDiff', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: payload
            })
            .then(function(r) { if(r.status===401){window.location.href='<%= request.getContextPath() %>/login.jsp';return null;} return r.json(); })
            .then(function(data) {
                if (data.success && data.data) {
                    document.getElementById('aiModalTitle').textContent = 'AI 分析结果: ' + (data.data.projectName || projectName);
                    document.getElementById('aiModalContent').textContent = data.data.analysis || '无分析结果';
                } else {
                    document.getElementById('aiModalContent').textContent = '分析失败: ' + (data.message || '未知错误');
                }
            })
            .catch(function(err) {
                document.getElementById('aiModalContent').textContent = '网络错误: ' + err.message;
            });
        }

        function closeAiModal() {
            document.getElementById('aiModal').classList.remove('active');
        }
    </script>
</body>
</html>
