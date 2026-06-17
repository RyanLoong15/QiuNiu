<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>员工门户 - 银行知识库</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Microsoft YaHei', Arial, sans-serif; background: #f5f7fa; color: #333; }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            padding: 16px 32px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .header h1 { font-size: 20px; }
        .header-right { display: flex; align-items: center; gap: 16px; }
        .avatar-img {
            width: 40px; height: 40px;
            border-radius: 50%;
            object-fit: cover;
            border: 2px solid rgba(255,255,255,0.7);
            cursor: pointer;
        }
        .btn-logout {
            background: rgba(255,255,255,0.2);
            color: #fff;
            border: 1px solid rgba(255,255,255,0.4);
            padding: 6px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
        }
        .btn-logout:hover { background: rgba(255,255,255,0.35); }
        .container { max-width: 1100px; margin: 24px auto; padding: 0 24px; }
        .card {
            background: #fff;
            border-radius: 12px;
            box-shadow: 0 2px 12px rgba(0,0,0,0.08);
            padding: 24px;
            margin-bottom: 24px;
        }
        .card h3 {
            font-size: 16px;
            color: #333;
            margin-bottom: 16px;
            padding-bottom: 12px;
            border-bottom: 1px solid #f0f0f0;
        }
        .badge {
            display: inline-block;
            background: #e74c3c;
            color: #fff;
            font-size: 12px;
            padding: 2px 8px;
            border-radius: 10px;
            margin-left: 6px;
        }
        .question-item {
            padding: 14px 16px;
            border: 1px solid #eee;
            border-radius: 8px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            transition: box-shadow 0.2s;
        }
        .question-item:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
        .question-text { flex: 1; font-size: 14px; color: #333; }
        .question-meta { font-size: 12px; color: #999; margin-top: 4px; }
        .btn-answer-q {
            padding: 5px 12px;
            border: 1px solid #667eea;
            border-radius: 14px;
            background: transparent;
            color: #667eea;
            font-size: 12px;
            cursor: pointer;
            transition: all 0.2s;
            margin-right: 6px;
        }
        .btn-answer-q:hover { background: #667eea; color: #fff; }
        .avatar-section { display: flex; align-items: center; gap: 24px; }
        .avatar-preview {
            width: 100px; height: 100px;
            border-radius: 50%;
            object-fit: cover;
            border: 3px solid #eee;
            background: #fafafa;
        }
        .avatar-actions { display: flex; flex-direction: column; gap: 10px; }
        .btn-upload {
            padding: 10px 20px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 600;
            background: #667eea;
            color: #fff;
        }
        .hidden { display: none !important; }
        .empty-tip { text-align: center; color: #aaa; padding: 40px 0; font-size: 14px; }
        .pagination { display: flex; justify-content: center; gap: 8px; margin-top: 16px; }
        .pagination button {
            padding: 6px 14px;
            border: 1px solid #ddd;
            background: #fff;
            border-radius: 6px;
            cursor: pointer;
        }
        .pagination button.active { background: #667eea; color: #fff; border-color: #667eea; }
        .pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
    </style>
</head>
<body>

<div class="header">
    <h1>银行知识库 · 员工门户</h1>
    <div class="header-right">
        <img class="avatar-img" id="headerAvatar" src="" alt="头像" title="点击管理头像">
        <span id="headerName" style="font-size:14px;"></span>
        <button class="btn-logout" onclick="doLogout()">退出登录</button>
    </div>
</div>

<div class="container">
    <!-- 未答问题列表 -->
    <div class="card">
        <h3>我的未答问题 <span class="badge" id="unreadBadge">0</span></h3>
        <div id="questionList"></div>
        <div class="pagination" id="pagination"></div>
        <div class="empty-tip hidden" id="emptyTip">暂无未答问题 🎉</div>
    </div>

    <!-- 头像管理 -->
    <div class="card">
        <h3>虚拟角色头像管理</h3>
        <div class="avatar-section">
            <img class="avatar-preview" id="avatarPreview" src="" alt="头像预览">
            <div class="avatar-actions">
                <label class="btn-upload">
                    上传真实照片
                    <input type="file" id="fileInput" accept="image/png,image/jpg,image/jpeg,image/gif"
                           style="display:none;" onchange="doUpload()">
                </label>
            </div>
        </div>
        <div id="avatarMsg" style="margin-top:12px;font-size:13px;color:#666;"></div>
    </div>
</div>

<script>
    const API = '${pageContext.request.contextPath}/api/employee'; // 通过 Tomcat 代理转发到 Flask
    let currentPage = 1;
    const PAGE_SIZE = 20;

    function authHeader() {
        return { 'Authorization': 'Bearer ' + localStorage.getItem('emp_token') };
    }

    function checkLogin() {
        if (!localStorage.getItem('emp_token')) {
            window.location.href = '<%= request.getContextPath() %>/employee/login';
        }
    }

    function doLogout() {
        fetch(API + '/logout', { headers: authHeader() }).finally(() => {
            localStorage.clear();
            window.location.href = '<%= request.getContextPath() %>/employee/login';
        });
    }

    function loadProfile() {
        fetch(API + '/profile', { headers: authHeader() })
            .then(r => r.json())
            .then(res => {
                if (res.code !== 200) { checkLogin(); return; }
                const d = res.data;
                document.getElementById('headerName').textContent = d.name;
                if (d.avatar) {
                    document.getElementById('headerAvatar').src = d.avatar + '?t=' + Date.now();
                    document.getElementById('avatarPreview').src = d.avatar + '?t=' + Date.now();
                } else {
                    document.getElementById('headerAvatar').src = '<%= request.getContextPath() %>/static/avatars/default.png';
                    document.getElementById('avatarPreview').src = '<%= request.getContextPath() %>/static/avatars/default.png';
                }
                document.getElementById('unreadBadge').textContent = d.unread_count || 0;
            })
            .catch(() => checkLogin());
    }

    function loadQuestions(page) {
        currentPage = page || 1;
        fetch(API + '/questions?page=' + currentPage + '&page_size=' + PAGE_SIZE, {
            headers: authHeader()
        })
        .then(r => r.json())
        .then(res => {
            if (res.code !== 200) return;
            const list = document.getElementById('questionList');
            const tip = document.getElementById('emptyTip');
            list.innerHTML = '';

            if (!res.data.items || res.data.items.length === 0) {
                tip.classList.remove('hidden');
                return;
            }
            tip.classList.add('hidden');

            res.data.items.forEach(q => {
                const div = document.createElement('div');
                div.className = 'question-item';
                const statusText = q.status === 'pending' ? '待回答' : (q.status === 'answered' ? '已回答' : q.status);
                const answeredInfo = q.answer ? '<div class="question-meta">回答：' + escHtml(q.answer.substring(0, 50)) + '...</div>' : '';
                const answerBtn = q.status === 'pending' ? '<button class="btn-answer-q" onclick="showAnswerForm(' + q.id + ', this)">回答</button>' : '';
                div.innerHTML =
                    '<div style="flex:1;">' +
                        '<div class="question-text">' + escHtml(q.question) + '</div>' +
                        '<div class="question-meta">' + statusText + ' · ' + (q.created_at || '') + '</div>' +
                        answeredInfo +
                    '</div>' +
                    answerBtn;
                list.appendChild(div);
            });

            // 分页
            renderPagination(res.data.total, res.data.page, res.data.page_size);
        });
    }

    function markRead(qid, btn) {
        fetch(API + '/questions/' + qid + '/read', {
            method: 'POST',
            headers: authHeader()
        })
        .then(r => r.json())
        .then(res => {
            if (res.code === 200) {
                btn.textContent = '已读';
                btn.classList.add('done');
                btn.disabled = true;
                // 刷新未读数
                loadProfile();
            }
        });
    }

    function showAnswerForm(qid, btn) {
        const item = btn.closest('.question-item');
        // 防止重复添加
        if (item.querySelector('.answer-form')) return;
        const form = document.createElement('div');
        form.className = 'answer-form';
        form.style.cssText = 'margin-top:10px;display:flex;gap:8px;align-items:flex-start;';
        form.innerHTML = '<textarea placeholder="输入回答..." style="flex:1;padding:8px;border:1px solid #ddd;border-radius:6px;resize:vertical;min-height:60px;font-size:13px;"></textarea>' +
            '<button class="btn-submit-answer" onclick="submitAnswer(' + qid + ', this)" style="padding:8px 16px;background:#667eea;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:13px;">提交</button>';
        item.querySelector('div[style]').appendChild(form);
        form.querySelector('textarea').focus();
    }

    function submitAnswer(qid, btn) {
        const form = btn.closest('.answer-form');
        const textarea = form.querySelector('textarea');
        const answer = textarea.value.trim();
        if (!answer) { alert('请输入回答'); return; }
        btn.disabled = true;
        btn.textContent = '提交中...';
        fetch(API + '/questions/' + qid + '/answer', {
            method: 'POST',
            headers: { ...authHeader(), 'Content-Type': 'application/json' },
            body: JSON.stringify({ answer: answer })
        })
        .then(r => r.json())
        .then(res => {
            if (res.code === 200) {
                btn.textContent = '✓ 已回答';
                btn.style.background = '#27ae60';
                textarea.disabled = true;
                // 自动标记已读
                fetch(API + '/questions/' + qid + '/read', {
                    method: 'POST',
                    headers: authHeader()
                }).finally(() => {
                    loadQuestions(1);
                    loadProfile();
                });
            } else {
                btn.textContent = '提交';
                btn.disabled = false;
                alert(res.message || '提交失败');
            }
        })
        .catch(() => { btn.textContent = '提交'; btn.disabled = false; });
    }

    function renderPagination(total, page, pageSize) {
        const totalPages = Math.ceil(total / pageSize);
        const pag = document.getElementById('pagination');
        if (totalPages <= 1) { pag.innerHTML = ''; return; }
        let html = '';
        html += '<button ' + (page <= 1 ? 'disabled' : '') + ' onclick="loadQuestions(' + (page-1) + ')">上一页</button>';
        for (let i = 1; i <= totalPages; i++) {
            html += '<button class="' + (i === page ? 'active' : '') + '" onclick="loadQuestions(' + i + ')">' + i + '</button>';
        }
        html += '<button ' + (page >= totalPages ? 'disabled' : '') + ' onclick="loadQuestions(' + (page+1) + ')">下一页</button>';
        pag.innerHTML = html;
    }

    function doUpload() {
        const fileInput = document.getElementById('fileInput');
        if (!fileInput.files.length) return;
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        document.getElementById('avatarMsg').textContent = '上传中...';
        fetch(API + '/avatar/upload', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('emp_token') },
            body: formData
        })
        .then(r => r.json())
        .then(res => {
            if (res.code === 200) {
                document.getElementById('avatarMsg').style.color = '#27ae60';
                document.getElementById('avatarMsg').textContent = '上传成功！管理员审核后将自动生效。';
                if (res.data && res.data.url) {
                    document.getElementById('avatarPreview').src = res.data.url + '?t=' + Date.now();
                }
            } else {
                document.getElementById('avatarMsg').style.color = '#e74c3c';
                document.getElementById('avatarMsg').textContent = res.message || '上传失败';
            }
        })
        .catch(() => {
            document.getElementById('avatarMsg').style.color = '#e74c3c';
            document.getElementById('avatarMsg').textContent = '网络错误';
        });
    }

    function escHtml(s) {
        if (!s) return '';
        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    // 初始化
    checkLogin();
    loadProfile();
    loadQuestions(1);
</script>
</body>
</html>
