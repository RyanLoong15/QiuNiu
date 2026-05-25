<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.qiuniu.model.User" %>
<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String resultId = request.getParameter("id");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>比对详情 - 囚牛</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #0d1117; min-height: 100vh; color: #c9d1d9; }
        
        .header {
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
            color: white; padding: 12px 30px; display: flex; justify-content: space-between;
            align-items: center; box-shadow: 0 2px 15px rgba(0,0,0,0.3);
            position: sticky; top: 0; z-index: 100;
        }
        .header h1 { font-size: 18px; display: flex; align-items: center; gap: 10px; }
        .header .back-btn { color: white; text-decoration: none; padding: 6px 14px; border-radius: 6px; background: rgba(255,255,255,0.12); font-size: 13px; }
        .header .back-btn:hover { background: rgba(255,255,255,0.25); }
        
        .container { max-width: 1600px; margin: 0 auto; padding: 20px; }

        /* Info Bar */
        .info-bar { background: #161b22; border-radius: 12px; padding: 16px 20px; margin-bottom: 16px; border: 1px solid #30363d; }
        .info-bar-top { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }
        .info-bar h2 { font-size: 18px; color: #58a6ff; margin-bottom: 8px; }
        .info-bar .meta { font-size: 13px; color: #8b949e; }
        .info-bar .meta span { margin-right: 16px; }
        .info-bar .meta strong { color: #c9d1d9; }
        
        .stats-row { display: flex; gap: 20px; margin-top: 12px; flex-wrap: wrap; }
        .stat-item { background: #21262d; padding: 10px 16px; border-radius: 8px; text-align: center; }
        .stat-item .num { font-size: 24px; font-weight: 700; }
        .stat-item .num.add { color: #3fb950; }
        .stat-item .num.del { color: #f85149; }
        .stat-item .num.files { color: #58a6ff; }
        .stat-item .label { font-size: 11px; color: #8b949e; margin-top: 2px; }

        /* Tabs */
        .tabs { display: flex; gap: 4px; margin-bottom: 16px; background: #161b22; border-radius: 10px; padding: 4px; width: fit-content; }
        .tab { padding: 8px 18px; border-radius: 8px; cursor: pointer; font-size: 14px; color: #8b949e; transition: all 0.2s; }
        .tab:hover { color: #c9d1d9; }
        .tab.active { background: #21262d; color: #58a6ff; font-weight: 600; }

        /* Diff Container */
        .diff-container { background: #161b22; border-radius: 12px; border: 1px solid #30363d; overflow: hidden; }
        .diff-header { padding: 12px 16px; background: #21262d; border-bottom: 1px solid #30363d; display: flex; justify-content: space-between; align-items: center; }
        .diff-header h3 { font-size: 14px; color: #c9d1d9; }
        .diff-header .file-count { font-size: 12px; color: #8b949e; }
        
        /* File List */
        .file-list { max-height: 400px; overflow-y: auto; }
        .file-item { padding: 10px 16px; border-bottom: 1px solid #21262d; cursor: pointer; transition: background 0.15s; display: flex; align-items: center; gap: 10px; }
        .file-item:hover { background: #21262d; }
        .file-item.active { background: #1f6feb33; border-left: 3px solid #58a6ff; }
        .file-status { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
        .file-status.added { background: #23863633; color: #3fb950; }
        .file-status.modified { background: #9e6a0333; color: #d29922; }
        .file-status.deleted { background: #da363333; color: #f85149; }
        .file-path { font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; color: #c9d1d9; flex: 1; }
        .file-stats { font-size: 12px; color: #8b949e; }
        .file-stats .add { color: #3fb950; }
        .file-stats .del { color: #f85149; }

        /* Diff View */
        .diff-view { background: #0d1117; }
        .diff-line { display: flex; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; line-height: 20px; }
        .diff-line.add { background: #0b2815; }
        .diff-line.del { background: #350f0f; }
        .diff-line.hunk { background: #161b22; }
        .diff-line-num { width: 50px; text-align: right; padding-right: 12px; color: #484f58; user-select: none; border-right: 1px solid #21262d; background: #161b22; }
        .diff-line-num.old { color: #f8514980; }
        .diff-line-num.new { color: #3fb95080; }
        .diff-line-content { flex: 1; padding-left: 12px; white-space: pre; overflow-x: auto; }
        .diff-line-content.add { color: #3fb950; }
        .diff-line-content.del { color: #f85149; }
        .diff-line-content.hunk { color: #8b949e; font-style: italic; }

        /* AI Analysis */
        .ai-panel { background: #161b22; border-radius: 12px; border: 1px solid #30363d; margin-top: 16px; }
        .ai-header { padding: 12px 16px; background: #21262d; border-bottom: 1px solid #30363d; display: flex; justify-content: space-between; align-items: center; }
        .ai-header h3 { font-size: 14px; color: #a371f7; display: flex; align-items: center; gap: 8px; }
        .ai-content { padding: 16px; font-size: 13px; line-height: 1.8; white-space: pre-wrap; max-height: 400px; overflow-y: auto; }
        .ai-empty { padding: 20px; text-align: center; color: #8b949e; }
        .ai-btn { background: #238636; color: white; border: none; padding: 6px 14px; border-radius: 6px; cursor: pointer; font-size: 12px; }
        .ai-btn:hover { background: #2ea043; }
        .ai-btn:disabled { opacity: 0.5; cursor: not-allowed; }

        /* Error State */
        .error-panel { background: #161b22; border-radius: 12px; border: 1px solid #f85149; padding: 20px; margin-bottom: 16px; }
        .error-panel h3 { color: #f85149; margin-bottom: 10px; }
        .error-panel pre { background: #0d1117; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 12px; }

        /* Loading */
        .loading { text-align: center; padding: 60px; color: #8b949e; }
        .loading .spinner { width: 40px; height: 40px; border: 3px solid #30363d; border-top-color: #58a6ff; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 16px; }
        @keyframes spin { to { transform: rotate(360deg); } }

        /* Scrollbar */
        ::-webkit-scrollbar { width: 8px; height: 8px; }
        ::-webkit-scrollbar-track { background: #161b22; }
        ::-webkit-scrollbar-thumb { background: #30363d; border-radius: 4px; }
        ::-webkit-scrollbar-thumb:hover { background: #484f58; }

        /* No diff */
        .no-diff { padding: 40px; text-align: center; color: #8b949e; }
        .no-diff .icon { font-size: 48px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <div class="header">
        <div style="display:flex;align-items:center;gap:20px;">
            <a href="<%= request.getContextPath() %>/version-compare.jsp" class="back-btn">← 返回列表</a>
            <h1 id="pageTitle">比对详情</h1>
        </div>
        <div style="display:flex;gap:10px;align-items:center;">
            <span id="headerVersion" style="font-size:13px;color:#8b949e;"></span>
        </div>
    </div>

    <div class="container">
        <!-- Loading -->
        <div class="loading" id="loadingDiv">
            <div class="spinner"></div>
            <div>加载中...</div>
        </div>

        <!-- Error Panel -->
        <div class="error-panel" id="errorPanel" style="display:none;">
            <h3>比对失败</h3>
            <pre id="errorText"></pre>
        </div>

        <!-- Main Content -->
        <div id="mainContent" style="display:none;">
            <!-- Info Bar -->
            <div class="info-bar">
                <div class="info-bar-top">
                    <div>
                        <h2 id="projectName">-</h2>
                        <div class="meta" id="projectMeta"></div>
                    </div>
                    <div class="stats-row">
                        <div class="stat-item">
                            <div class="num files" id="statFiles">0</div>
                            <div class="label">变更文件</div>
                        </div>
                        <div class="stat-item">
                            <div class="num add" id="statAdd">+0</div>
                            <div class="label">新增行</div>
                        </div>
                        <div class="stat-item">
                            <div class="num del" id="statDel">-0</div>
                            <div class="label">删除行</div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Tabs -->
            <div class="tabs">
                <div class="tab active" data-tab="diff" onclick="switchTab('diff')">代码差异</div>
                <div class="tab" data-tab="files" onclick="switchTab('files')">文件列表</div>
            </div>

            <!-- Diff Container -->
            <div class="diff-container" id="diffTab">
                <div class="diff-header">
                    <h3>代码变更</h3>
                    <span class="file-count" id="fileCount">0 个文件</span>
                </div>
                <div class="file-list" id="fileList"></div>
                <div class="diff-view" id="diffView"></div>
            </div>

            <!-- Files Tab -->
            <div id="filesTab" style="display:none;">
                <div class="diff-container">
                    <div class="diff-header">
                        <h3>变更文件列表</h3>
                    </div>
                    <div id="filesOnlyList" style="padding:12px;"></div>
                </div>
            </div>

            <!-- AI Panel -->
            <div class="ai-panel">
                <div class="ai-header">
                    <h3>🤖 AI 风险分析</h3>
                    <button class="ai-btn" id="aiBtn" onclick="runAiAnalysis()">分析风险</button>
                </div>
                <div class="ai-content" id="aiContent">
                    <div class="ai-empty">暂无分析，点击上方按钮开始 AI 分析</div>
                </div>
            </div>
        </div>
    </div>

    <script>
        var resultId = '<%= resultId != null ? resultId : "" %>';
        var detailData = null;
        var parsedFiles = [];

        document.addEventListener('DOMContentLoaded', function() {
            if (!resultId) {
                document.getElementById('loadingDiv').innerHTML = '<div style="color:#f85149;">缺少 id 参数</div>';
                return;
            }
            loadDetail();
        });

        function loadDetail() {
            fetch('<%= request.getContextPath() %>/version?action=detail&id=' + resultId)
                .then(function(r) { return r.json(); })
                .then(function(data) {
                    document.getElementById('loadingDiv').style.display = 'none';
                    if (!data.success) {
                        document.getElementById('errorPanel').style.display = 'block';
                        document.getElementById('errorText').textContent = data.message || '加载失败';
                        return;
                    }
                    detailData = data.data;
                    renderDetail();
                })
                .catch(function(err) {
                    document.getElementById('loadingDiv').style.display = 'none';
                    document.getElementById('errorPanel').style.display = 'block';
                    document.getElementById('errorText').textContent = '网络错误: ' + err.message;
                });
        }

        function renderDetail() {
            if (!detailData) return;

            document.getElementById('mainContent').style.display = 'block';
            document.getElementById('pageTitle').textContent = detailData.projectName || '比对详情';
            document.getElementById('headerVersion').textContent = detailData.versionName || '';

            // Stats
            document.getElementById('statFiles').textContent = detailData.changedFiles || '0';
            document.getElementById('statAdd').textContent = '+' + (detailData.insertions || '0');
            document.getElementById('statDel').textContent = '-' + (detailData.deletions || '0');

            // Meta
            var meta = '<span>团队: <strong>' + escHtml(detailData.teamName || '-') + '</strong></span>';
            meta += '<span>分支: <strong>' + escHtml(detailData.baseBranch || 'master') + '</strong> vs <strong>' + escHtml(detailData.compareBranch || '') + '</strong></span>';
            meta += '<span>状态: <strong>' + escHtml(detailData.status || '-') + '</strong></span>';
            document.getElementById('projectMeta').innerHTML = meta;

            // Error check
            if (detailData.status === 'FAILED') {
                document.getElementById('errorPanel').style.display = 'block';
                document.getElementById('errorText').textContent = detailData.error || '未知错误';
                return;
            }

            // No changes
            if (!detailData.hasChanges) {
                document.getElementById('diffView').innerHTML = '<div class="no-diff"><div class="icon">✓</div><div>此仓库无代码变更</div></div>';
                return;
            }

            // Parse diff
            parsedFiles = parseDiff(detailData.diffDetail || '');
            renderFileList();
            renderDiff();

            // AI Analysis
            if (detailData.aiAnalysis && detailData.aiAnalysis.length > 5) {
                document.getElementById('aiContent').textContent = detailData.aiAnalysis;
                document.getElementById('aiBtn').textContent = '重新分析';
            }
        }

        function parseDiff(diffText) {
            var files = [];
            var lines = diffText.split('\n');
            var currentFile = null;
            var i = 0;

            while (i < lines.length) {
                var line = lines[i];

                // New file: diff --git a/path b/path
                if (line.startsWith('diff --git ')) {
                    if (currentFile) files.push(currentFile);
                    var match = line.match(/diff --git a\/(.+) b\/(.+)/);
                    if (match) {
                        currentFile = {
                            path: match[2],
                            oldPath: match[1],
                            status: 'modified',
                            additions: 0,
                            deletions: 0,
                            hunks: [],
                            lines: []
                        };
                    }
                    i++;
                    continue;
                }

                // Status detection
                if (currentFile) {
                    if (line.startsWith('new file mode')) {
                        currentFile.status = 'added';
                    } else if (line.startsWith('deleted file mode')) {
                        currentFile.status = 'deleted';
                    }

                    // Hunk header: @@ -a,b +c,d @@
                    if (line.startsWith('@@')) {
                        currentFile.hunks.push(line);
                    }

                    // Count additions/deletions
                    if (line.startsWith('+') && !line.startsWith('+++')) {
                        currentFile.additions++;
                        currentFile.lines.push({ type: 'add', content: line.substring(1), oldNum: null, newNum: currentFile.additions + currentFile.deletions });
                    } else if (line.startsWith('-') && !line.startsWith('---')) {
                        currentFile.deletions++;
                        currentFile.lines.push({ type: 'del', content: line.substring(1), oldNum: currentFile.additions + currentFile.deletions, newNum: null });
                    } else if (line.startsWith('@@')) {
                        currentFile.lines.push({ type: 'hunk', content: line, oldNum: null, newNum: null });
                    }
                }

                i++;
            }

            if (currentFile) files.push(currentFile);
            return files;
        }

        function renderFileList() {
            var html = '';
            document.getElementById('fileCount').textContent = parsedFiles.length + ' 个文件';

            for (var i = 0; i < parsedFiles.length; i++) {
                var f = parsedFiles[i];
                html += '<div class="file-item' + (i === 0 ? ' active' : '') + '" data-index="' + i + '" onclick="selectFile(' + i + ')">';
                html += '<span class="file-status ' + f.status + '">' + getStatusLabel(f.status) + '</span>';
                html += '<span class="file-path">' + escHtml(f.path) + '</span>';
                html += '<span class="file-stats"><span class="add">+' + f.additions + '</span> <span class="del">-' + f.deletions + '</span></span>';
                html += '</div>';
            }

            document.getElementById('fileList').innerHTML = html;
        }

        function renderDiff() {
            if (parsedFiles.length === 0) {
                document.getElementById('diffView').innerHTML = '<div class="no-diff">无 diff 数据</div>';
                return;
            }

            var html = '';
            for (var i = 0; i < parsedFiles.length; i++) {
                var f = parsedFiles[i];
                html += '<div class="diff-file-block" data-file-index="' + i + '">';
                html += '<div style="padding:8px 12px;background:#21262d;border-bottom:1px solid #30363d;font-family:monospace;font-size:13px;color:#58a6ff;">' + escHtml(f.path) + '</div>';

                for (var j = 0; j < f.lines.length; j++) {
                    var l = f.lines[j];
                    html += '<div class="diff-line ' + l.type + '">';
                    html += '<span class="diff-line-num old">' + (l.oldNum || '') + '</span>';
                    html += '<span class="diff-line-num new">' + (l.newNum || '') + '</span>';
                    html += '<span class="diff-line-content ' + l.type + '">' + escHtml(l.content) + '</span>';
                    html += '</div>';
                }

                html += '</div>';
            }

            document.getElementById('diffView').innerHTML = html;
        }

        function selectFile(index) {
            var items = document.querySelectorAll('.file-item');
            for (var i = 0; i < items.length; i++) {
                items[i].classList.toggle('active', parseInt(items[i].getAttribute('data-index')) === index);
            }

            // Scroll to file
            var blocks = document.querySelectorAll('.diff-file-block');
            if (blocks[index]) {
                blocks[index].scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }

        function switchTab(tab) {
            var tabs = document.querySelectorAll('.tab');
            for (var i = 0; i < tabs.length; i++) {
                tabs[i].classList.toggle('active', tabs[i].getAttribute('data-tab') === tab);
            }
            document.getElementById('diffTab').style.display = tab === 'diff' ? 'block' : 'none';
            document.getElementById('filesTab').style.display = tab === 'files' ? 'block' : 'none';
        }

        function getStatusLabel(status) {
            switch (status) {
                case 'added': return '新增';
                case 'deleted': return '删除';
                case 'modified': return '修改';
                default: return status;
            }
        }

        function runAiAnalysis() {
            if (!detailData) return;
            if (!detailData.diffDetail || detailData.diffDetail.trim().length < 10) {
                alert('diff 内容为空，无法分析');
                return;
            }

            var btn = document.getElementById('aiBtn');
            btn.disabled = true;
            btn.textContent = '分析中...';
            document.getElementById('aiContent').textContent = 'AI 正在分析代码风险，请稍候...';

            var payload = JSON.stringify({
                projectName: detailData.projectName || '',
                diff: detailData.diffDetail || '',
                baseBranch: detailData.baseBranch || 'master',
                compareBranch: detailData.compareBranch || '',
                versionName: detailData.versionName || ''
            });

            fetch('<%= request.getContextPath() %>/version?action=analyzeDiff', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: payload
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                btn.disabled = false;
                btn.textContent = '重新分析';
                if (data.success && data.data && data.data.analysis) {
                    document.getElementById('aiContent').textContent = data.data.analysis;
                    // Save to backend
                    saveAiAnalysis(data.data.analysis);
                } else {
                    document.getElementById('aiContent').textContent = '分析失败: ' + (data.message || '未知错误');
                }
            })
            .catch(function(err) {
                btn.disabled = false;
                btn.textContent = '分析风险';
                document.getElementById('aiContent').textContent = '网络错误: ' + err.message;
            });
        }

        function saveAiAnalysis(analysis) {
            // Update UI to show saved
            // The analysis is automatically saved by doAnalyzeDiff
        }

        function escHtml(s) {
            if (!s) return '';
            var d = document.createElement('div');
            d.textContent = s;
            return d.innerHTML;
        }
    </script>
</body>
</html>
