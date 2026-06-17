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
    <title>投产变更范围 - 囚牛</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #f0f2f5; min-height: 100vh; }
        .header {
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            color: white; padding: 15px 30px; display: flex; justify-content: space-between;
            align-items: center; box-shadow: 0 2px 15px rgba(0,0,0,0.3);
        }
        .header h1 { font-size: 22px; }
        .header .subtitle { font-size: 12px; opacity: 0.7; margin-left: 15px; }
        .header .nav { display: flex; gap: 12px; align-items: center; }
        .header .nav a { color: white; text-decoration: none; padding: 7px 16px; border-radius: 6px; background: rgba(255,255,255,0.12); font-size: 13px; transition: background 0.2s; }
        .header .nav a:hover { background: rgba(255,255,255,0.25); }
        .header .nav a.active { background: rgba(255,255,255,0.3); }

        .container { max-width: 1400px; margin: 0 auto; padding: 25px 30px; }

        /* Date / Version Selector Panel */
        .selector-panel {
            background: white; border-radius: 14px; padding: 24px; margin-bottom: 20px;
            box-shadow: 0 2px 12px rgba(0,0,0,0.06); display: flex; align-items: flex-end;
            gap: 16px; flex-wrap: wrap;
        }
        .sp-inner { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap; flex: 1; }
        .form-group { }
        .form-group label { display: block; margin-bottom: 6px; color: #555; font-size: 13px; font-weight: 500; }
        .form-group input[type="date"] {
            padding: 10px 14px; border: 2px solid #e0e0e0; border-radius: 8px;
            font-size: 15px; font-family: inherit; cursor: pointer;
        }
        .form-group input:focus { outline: none; border-color: #667eea; }
        .form-group .hint { font-size: 11px; color: #aaa; margin-top: 4px; }
        .version-preview {
            padding: 10px 16px; background: #f5f7ff; border-radius: 8px;
            border: 1px solid #e0e0ff; font-size: 14px; color: #333; min-width: 200px;
        }
        .version-preview strong { color: #667eea; }
        .version-preview .branches { font-size: 12px; color: #888; margin-top: 4px; }

        .btn { padding: 10px 22px; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 500; transition: all 0.2s; }
        .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
        .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
        .btn-secondary { background: #f0f0f0; color: #333; }
        .btn-sm { padding: 6px 14px; font-size: 12px; }
        .btn:disabled { opacity: 0.5; cursor: not-allowed; }

        /* Stats */
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 14px; margin-bottom: 20px; }
        .stat-card { background: white; border-radius: 12px; padding: 18px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.05); }
        .stat-card .num { font-size: 30px; font-weight: 800; color: #1a1a2e; }
        .stat-card .num.g { color: #388e3c; }
        .stat-card .num.r { color: #d32f2f; }
        .stat-card .num.o { color: #f57c00; }
        .stat-card .label { font-size: 12px; color: #888; margin-top: 4px; }

        /* Team Cards */
        .teams-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(420px, 1fr)); gap: 18px; margin-bottom: 20px; }
        .team-card { background: white; border-radius: 14px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.07); }
        .team-header {
            padding: 14px 18px; display: flex; justify-content: space-between; align-items: center;
            border-bottom: 1px solid #f0f0f0;
        }
        .team-name { font-size: 15px; font-weight: 700; color: #1a1a2e; }
        .team-stats { font-size: 12px; color: #888; }
        .team-stats .changed { color: #d32f2f; font-weight: 700; }
        .team-stats .clean { color: #388e3c; }
        .team-body { padding: 10px 16px; max-height: 320px; overflow-y: auto; }

        .repo-item { padding: 10px 12px; border-radius: 8px; margin-bottom: 6px; border: 1px solid #f0f0f0; }
        .repo-item.changed { border-left: 3px solid #d32f2f; background: #fff9f9; }
        .repo-item.clean { border-left: 3px solid #388e3c; background: #f9fff9; }
        .repo-item.failed { border-left: 3px solid #d32f2f; background: #fff0f0; }
        .repo-name { font-size: 13px; font-weight: 600; color: #333; }
        .repo-meta { font-size: 11px; color: #888; margin-top: 2px; }
        .repo-meta .ins { color: #388e3c; }
        .repo-meta .del { color: #d32f2f; }
        .repo-error { font-size: 11px; color: #d32f2f; margin-top: 2px; }
        .toggle-btn { font-size: 11px; color: #667eea; cursor: pointer; margin-top: 4px; display: inline-block; }
        .toggle-btn:hover { text-decoration: underline; }

        .detail-block { display: none; margin-top: 6px; background: #f5f5f5; border-radius: 6px; padding: 8px 10px; font-size: 12px; color: #555; white-space: pre-wrap; font-family: Consolas, monospace; }
        .detail-block.open { display: block; }

        /* AI Analysis */
        .ai-block { margin-top: 8px; background: #f8f4ff; border: 1px solid #e0d4ff; border-radius: 8px; padding: 10px 12px; }
        .ai-block-title { font-size: 12px; font-weight: 700; color: #764ba2; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
        .ai-loading { font-size: 12px; color: #888; text-align: center; padding: 8px; }
        .ai-error { font-size: 12px; color: #d32f2f; background: #ffebee; padding: 8px; border-radius: 6px; }
        .ai-content { background: white; border-radius: 6px; padding: 10px; font-size: 12px; color: #333; white-space: pre-wrap; line-height: 1.7; max-height: 350px; overflow: auto; }

        /* Progress */
        .progress-panel { display: none; background: #fff8e1; border: 1px solid #ffe082; border-radius: 10px; padding: 20px; margin-bottom: 20px; }
        .progress-panel.active { display: block; }
        .progress-bar-wrap { background: #e0e0e0; border-radius: 10px; height: 10px; margin: 12px 0; overflow: hidden; }
        .progress-bar-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); border-radius: 10px; transition: width 1s; }
        .progress-info { display: flex; justify-content: space-between; font-size: 13px; color: #666; }

        /* History */
        .history-panel { background: white; border-radius: 14px; padding: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
        .history-title { font-size: 16px; font-weight: 700; color: #1a1a2e; margin-bottom: 14px; padding-bottom: 10px; border-bottom: 2px solid #f0f0f0; }
        .history-title span { font-size: 12px; color: #999; font-weight: 400; margin-left: 8px; }
        .history-list { }
        .history-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 14px; border: 1px solid #eee; border-radius: 10px; margin-bottom: 8px; cursor: pointer; transition: all 0.15s; }
        .history-item:hover { border-color: #667eea; background: #fafafe; }
        .history-item.active { border-color: #667eea; background: #f0f4ff; }
        .history-item .ver-name { font-size: 15px; font-weight: 700; color: #1a1a2e; }
        .history-item .ver-meta { font-size: 12px; color: #999; margin-top: 2px; }
        .badge { padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 600; }
        .badge-done { background: #e8f5e9; color: #388e3c; }
        .badge-failed { background: #ffebee; color: #d32f2f; }
        .badge-pending { background: #fff3e0; color: #f57c00; }
        .badge-running { background: #e3f2fd; color: #1976d2; }

        .empty-state { text-align: center; padding: 40px; color: #bbb; font-size: 14px; }
        .empty-state .icon { font-size: 36px; margin-bottom: 10px; }
    </style>
</head>
<body>
    <div class="header">
        <div style="display:flex;align-items:center;">
            <h1>🚀 投产变更范围</h1>
            <span class="subtitle">版本变更看板</span>
        </div>
        <div class="nav">
            <a href="<%= request.getContextPath() %>/team-dashboard.jsp">团队看板</a>
            <a href="<%= request.getContextPath() %>/version-compare.jsp" class="active">投产变更</a>
            <a href="<%= request.getContextPath() %>/git-projects.jsp">项目列表</a>
            <a href="<%= request.getContextPath() %>/dashboard.jsp">提示词管理</a>
            <a href="<%= request.getContextPath() %>/incidents.jsp">历史教训</a>
            <a href="<%= request.getContextPath() %>/logout">退出</a>
        </div>
    </div>

    <div class="container">
        <!-- Date Selector -->
        <div class="selector-panel">
            <div class="sp-inner">
                <div class="form-group">
                    <label>投产日期</label>
                    <input type="date" id="inputDate" value="2026-04-17" onchange="onDateChange()">
                </div>
                <div class="version-preview" id="versionPreview">
                    <div><strong id="previewVersion">2026-04-17</strong></div>
                    <div class="branches" id="previewBranches">master vs <strong>release-20260417</strong></div>
                </div>
            </div>
            <button class="btn btn-primary" id="btnCompare" onclick="doCompare()">
                发起对比
            </button>
        </div>

        <!-- Progress -->
        <div class="progress-panel" id="progressPanel">
            <div style="font-size:15px;font-weight:700;color:#1a1a2e;margin-bottom:4px;">
                <span id="progressTitle">正在对比中...</span>
            </div>
            <div style="font-size:13px;color:#888;margin-bottom:10px;" id="progressSub">后台运行中，页面可切换</div>
            <div class="progress-bar-wrap">
                <div class="progress-bar-fill" id="progressBar" style="width:0%"></div>
            </div>
            <div class="progress-info">
                <span id="progressCount">0 / 0</span>
                <span id="progressPercent">0%</span>
            </div>
        </div>

        <!-- Stats -->
        <div class="stats-grid" id="statsGrid" style="display:none;">
            <div class="stat-card"><div class="num" id="statTotal">0</div><div class="label">总代码仓</div></div>
            <div class="stat-card"><div class="num g" id="statSuccess">0</div><div class="label">成功</div></div>
            <div class="stat-card"><div class="num r" id="statFail">0</div><div class="label">失败</div></div>
            <div class="stat-card"><div class="num o" id="statChanged">0</div><div class="label">有变更</div></div>
            <div class="stat-card"><div class="num" id="statFiles">0</div><div class="label">变更文件数</div></div>
        </div>

        <!-- Team Grid -->
        <div id="teamGrid" style="display:none;"></div>

        <!-- History -->
        <div class="history-panel" id="historyPanel">
            <div class="history-title">
                历史对比记录
                <span>按时间倒序</span>
            </div>
            <div class="history-list" id="historyList">
                <div class="empty-state"><div class="icon">📋</div>暂无记录</div>
            </div>
        </div>
    </div>

    <script>
        var currentTaskId = null;
        var pollTimer = null;
        var currentVersionData = null;
        var currentVersion = null;
        var currentBaseBranch = 'master';

        document.addEventListener('DOMContentLoaded', function() {
            loadHistory();
            onDateChange();
        });

        function onDateChange() {
            var dateInput = document.getElementById('inputDate').value;
            if (!dateInput) return;
            // versionName 显示原始日期，compareBranch = release-YYYYMMDD
            var parts = dateInput.split('-');
            var yearFull = parts[0];   // 2026
            var month = parts[1];       // 04
            var day = parts[2];          // 17
            var releaseBranch = 'release-' + yearFull + month + day;  // release-20260417
            currentVersion = dateInput; // 2026-04-17 as version display name
            document.getElementById('previewVersion').textContent = dateInput;
            document.getElementById('previewBranches').innerHTML =
                '<strong>master</strong> vs <strong>' + releaseBranch + '</strong>';
        }

        function doCompare() {
            var dateInput = document.getElementById('inputDate').value;
            if (!dateInput) { alert('请选择投产日期'); return; }
            var parts = dateInput.split('-');
            var yearFull = parts[0];
            var month = parts[1];
            var day = parts[2];
            var releaseBranch = 'release-' + yearFull + month + day;
            var versionName = dateInput;        // 2026-04-17
            var compareBranch = releaseBranch; // release-20260417

            var btn = document.getElementById('btnCompare');
            btn.disabled = true;
            btn.textContent = '提交中...';

            var fd = new URLSearchParams();
            fd.append('action', 'start');
            fd.append('versionName', versionName);
            fd.append('baseBranch', currentBaseBranch);
            fd.append('compareBranch', compareBranch);

            fetch('<%= request.getContextPath() %>/version', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: fd.toString()
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                btn.disabled = false;
                btn.textContent = '发起对比';
                if (data.success) {
                    currentTaskId = data.data.id;
                    currentVersion = versionName;
                    startPolling(currentTaskId);
                    loadHistory();
                } else {
                    alert(data.message || '发起失败');
                }
            })
            .catch(function(err) {
                btn.disabled = false;
                btn.textContent = '发起对比';
                alert('网络错误: ' + err.message);
            });
        }

        function startPolling(taskId) {
            document.getElementById('progressPanel').classList.add('active');
            document.getElementById('statsGrid').style.display = 'none';
            document.getElementById('teamGrid').style.display = 'none';
            document.getElementById('progressBar').style.width = '0%';
            document.getElementById('progressCount').textContent = '0 / ?';
            document.getElementById('progressPercent').textContent = '0%';

            if (pollTimer) clearInterval(pollTimer);
            pollTimer = setInterval(function() {
                fetch('<%= request.getContextPath() %>/version?action=status&id=' + taskId)
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (!data.success) return;
                        var vc = data.data;
                        var total = vc.totalRepos || 1;
                        var done = (vc.successCount || 0) + (vc.failCount || 0);
                        var pct = Math.round(done / total * 100);
                        document.getElementById('progressBar').style.width = pct + '%';
                        document.getElementById('progressCount').textContent = done + ' / ' + total;
                        document.getElementById('progressPercent').textContent = pct + '%';

                        if (vc.status === 'PENDING') {
                            document.getElementById('progressTitle').textContent = '等待调度...';
                        } else if (vc.status === 'RUNNING') {
                            document.getElementById('progressTitle').textContent = '正在对比中...';
                        } else if (vc.status === 'DONE' || vc.status === 'FAILED') {
                            clearInterval(pollTimer);
                            pollTimer = null;
                            document.getElementById('progressPanel').classList.remove('active');
                            loadDetail(taskId);
                            loadHistory();
                        }
                    });
            }, 3000);
        }

        function loadDetail(taskId) {
            fetch('<%= request.getContextPath() %>/version?action=result&id=' + taskId)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (data.success) renderResult(data.data);
                });
        }

        function renderResult(result) {
            currentVersionData = result;
            document.getElementById('statsGrid').style.display = 'grid';
            document.getElementById('teamGrid').style.display = 'block';

            // Stats
            var teams = result.teamSummary ? Object.keys(result.teamSummary) : [];
            var all = result.allResults || [];
            var totalFiles = 0, totalChanged = 0;
            for (var i = 0; i < all.length; i++) {
                if (all[i].status === 'SUCCESS') {
                    totalFiles += parseInt(all[i].changedFiles || 0);
                    if (all[i].hasChanges) totalChanged++;
                }
            }
            document.getElementById('statTotal').textContent = result.totalRepos || 0;
            document.getElementById('statSuccess').textContent = result.successCount || 0;
            document.getElementById('statFail').textContent = result.failCount || 0;
            document.getElementById('statChanged').textContent = totalChanged;
            document.getElementById('statFiles').textContent = totalFiles;

            // Team grid
            var grid = document.getElementById('teamGrid');
            if (teams.length === 0) {
                grid.innerHTML = '<div class="empty-state"><div class="icon">📂</div>暂无数据</div>';
                return;
            }

            var html = '';
            for (var ti = 0; ti < teams.length; ti++) {
                var tName = teams[ti];
                var t = result.teamSummary[tName];
                var repos = t.repos || [];
                var changedRepos = repos.filter(function(r) { return r.hasChanges && r.status === 'SUCCESS'; });
                var cleanRepos = repos.filter(function(r) { return !r.hasChanges && r.status === 'SUCCESS'; });
                var failRepos = repos.filter(function(r) { return r.status === 'FAILED'; });
                var totalF = 0;
                for (var ri = 0; ri < repos.length; ri++) {
                    totalF += parseInt(repos[ri].changedFiles || 0);
                }

                html += '<div class="team-card">' +
                    '<div class="team-header">' +
                        '<div class="team-name">' + escHtml(tName) + '</div>' +
                        '<div class="team-stats">' +
                            '<span class="changed">' + changedRepos.length + ' 有变更</span>' +
                            ' &nbsp;' + cleanRepos.length + ' 无变更' +
                            (failRepos.length > 0 ? ' &nbsp;<span style="color:#d32f2f;">' + failRepos.length + ' 失败</span>' : '') +
                            ' &nbsp;|&nbsp; ' + totalF + ' 文件变更' +
                        '</div>' +
                    '</div>' +
                    '<div class="team-body">';

                for (var ri = 0; ri < repos.length; ri++) {
                    var repo = repos[ri];
                    var cls = repo.status === 'FAILED' ? 'failed' : (repo.hasChanges ? 'changed' : 'clean');
                    var repoDetailId = 'repo-' + tName.replace(/\s/g, '_') + '-' + ri;

                    html += '<div class="repo-item ' + cls + '">' +
                        '<div class="repo-name">' + escHtml(repo.projectName || '') + '</div>' +
                        '<div class="repo-meta">';

                    if (repo.status === 'FAILED') {
                        html += '<span style="color:#d32f2f;">&#10060; ' + escHtml(repo.error || '未知错误') + '</span>';
                    } else if (repo.hasChanges) {
                        html += '<span class="ins">+' + (repo.insertions || '0') + '</span> &nbsp;' +
                                '<span class="del">-' + (repo.deletions || '0') + '</span> &nbsp;' +
                                '<span>' + (repo.changedFiles || 0) + ' 文件变更</span>';
                    } else {
                        html += '<span style="color:#388e3c;">&#10004; 无变更</span>';
                    }

                    html += '</div>';

                    // Diff summary
                    if (repo.diffSummary && repo.diffSummary.length > 5 && repo.status !== 'FAILED') {
                        var shortSum = repo.diffSummary.length > 300 ? repo.diffSummary.substring(0, 300) + '...' : repo.diffSummary;
                        html += '<div style="font-size:11px;color:#888;margin-top:3px;white-space:pre-wrap;">' + escHtml(shortSum) + '</div>';
                    }

                    // Detail link
                    if (repo.id && repo.status !== 'FAILED') {
                        html += '<div style="margin-top:6px;"><a href="version-detail.jsp?id=' + repo.id + '" style="font-size:12px;color:#667eea;text-decoration:none;">查看详情 →</a></div>';
                    }

                    // AI analysis
                    if (repo.aiAnalysis && repo.aiAnalysis.length > 5) {
                        var aiId = 'ai-' + repoDetailId;
                        html += '<div style="margin-top:8px;">';
                        html += '<div style="font-size:11px;font-weight:700;color:#764ba2;margin-bottom:4px;">AI 风险分析</div>';
                        html += '<div class="ai-content" id="' + aiId + '">' + escHtml(repo.aiAnalysis) + '</div>';
                        html += '</div>';
                    }

                    html += '</div>';
                }

                html += '</div></div>';
            }
            grid.innerHTML = html;

            // Highlight history
            highlightHistory(result.taskId);
        }

        function toggleEl(id, btn) {
            var el = document.getElementById(id);
            if (!el) return;
            el.classList.toggle('open');
            if (btn) btn.textContent = el.classList.contains('open') ? '收起详情' : '展开详情';
        }

        function loadHistory() {
            fetch('<%= request.getContextPath() %>/version?action=list')
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    if (!data.success) return;
                    var list = data.data || [];
                    var el = document.getElementById('historyList');
                    if (list.length === 0) {
                        el.innerHTML = '<div class="empty-state"><div class="icon">📋</div>暂无记录，选择上方日期发起首次对比</div>';
                        return;
                    }
                    var html = '';
                    for (var i = 0; i < list.length; i++) {
                        var vc = list[i];
                        var created = vc.createdAt ? vc.createdAt.replace('T', ' ').substring(0, 19) : '-';
                        var isActive = vc.id === currentTaskId;
                        html += '<div class="history-item' + (isActive ? ' active' : '') + '" data-task-id="' + vc.id + '" onclick="selectHistory(' + vc.id + ')">' +
                            '<div>' +
                                '<div class="ver-name">' + escHtml(vc.versionName || '') + '</div>' +
                                '<div class="ver-meta">' + (vc.baseBranch || 'master') + ' vs ' + escHtml(vc.compareBranch || '') + ' &nbsp;|&nbsp; ' + created + ' &nbsp;|&nbsp; ' + vc.totalRepos + ' 个代码仓</div>' +
                            '</div>' +
                            '<div>' +
                                '<span class="badge badge-' + (vc.status || 'pending').toLowerCase() + '">' + vc.status + '</span>' +
                                '<div style="text-align:right;font-size:11px;color:#aaa;margin-top:3px;">' +
                                    (vc.successCount || 0) + ' ok / ' + (vc.failCount || 0) + ' fail' +
                                '</div>' +
                            '</div>' +
                        '</div>';
                    }
                    el.innerHTML = html;
                });
        }

        function selectHistory(taskId) {
            currentTaskId = taskId;
            highlightHistory(taskId);
            if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
            document.getElementById('progressPanel').classList.remove('active');
            loadDetail(taskId);
        }

        function highlightHistory(taskId) {
            var items = document.querySelectorAll('.history-item');
            for (var i = 0; i < items.length; i++) {
                items[i].classList.toggle('active', parseInt(items[i].getAttribute('data-task-id')) === taskId);
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
            var modal = document.getElementById('aiModal2');
            document.getElementById('aiModalTitle2').textContent = 'AI 正在分析: ' + projectName;
            document.getElementById('aiModalContent2').textContent = 'AI 正在分析代码风险，请稍候...';
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
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success && data.data) {
                    document.getElementById('aiModalTitle2').textContent = 'AI 分析结果: ' + (data.data.projectName || projectName);

                    // 历史事故风险检核 - 独立醒目区块
                    var existingCheck = document.getElementById('incidentCheckSection');
                    if (existingCheck) existingCheck.remove();

                    if (data.data.incidentCheckResult && data.data.incidentCheckResult.trim()) {
                        var checkDiv = document.createElement('div');
                        checkDiv.id = 'incidentCheckSection';
                        checkDiv.style.cssText = 'background:#fff3cd; border:2px solid #ff9800; border-radius:8px; padding:16px; margin-bottom:16px;';
                        var title = document.createElement('h3');
                        title.style.cssText = 'color:#e65100; margin:0 0 10px 0;';
                        title.textContent = '⚠️ 历史事故风险检核';
                        checkDiv.appendChild(title);
                        var content = document.createElement('div');
                        content.style.cssText = 'white-space:pre-wrap; font-family:inherit; font-size:13px;';
                        content.textContent = data.data.incidentCheckResult;
                        checkDiv.appendChild(content);
                        var content2 = document.getElementById('aiModalContent2');
                        content2.parentNode.insertBefore(checkDiv, content2);
                    }

                    document.getElementById('aiModalContent2').textContent = data.data.aiAnalysis || '无分析结果';
                } else {
                    document.getElementById('aiModalContent2').textContent = '分析失败: ' + (data.message || '未知错误');
                }
            })
            .catch(function(err) {
                document.getElementById('aiModalContent2').textContent = '网络错误: ' + err.message;
            });
        }
    </script>
</body>
</html>
