<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>员工注册 - 囚牛银行知识库</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .register-container {
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            padding: 40px;
            width: 100%;
            max-width: 400px;
        }

        .register-header {
            text-align: center;
            margin-bottom: 30px;
        }

        .register-header h1 {
            color: #2c3e50;
            font-size: 28px;
            margin-bottom: 10px;
        }

        .register-header p {
            color: #7f8c8d;
            font-size: 14px;
        }

        .form-group {
            margin-bottom: 20px;
        }

        .form-group label {
            display: block;
            color: #2c3e50;
            font-size: 14px;
            margin-bottom: 8px;
            font-weight: 500;
        }

        .form-group input {
            width: 100%;
            padding: 12px 16px;
            border: 2px solid #e0e0e0;
            border-radius: 8px;
            font-size: 14px;
            transition: all 0.3s;
        }

        .form-group input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }

        .btn-register {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 8px;
            font-size: 16px;
            font-weight: 500;
            cursor: pointer;
            transition: transform 0.2s;
        }

        .btn-register:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
        }

        .btn-register:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: none;
        }

        .error-message {
            background: #fee;
            color: #c33;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
            display: none;
        }

        .success-message {
            background: #d4edda;
            color: #155724;
            padding: 12px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
            display: none;
        }

        .loading {
            display: none;
            text-align: center;
            margin-bottom: 20px;
        }

        .loading-spinner {
            border: 3px solid #f3f3f3;
            border-top: 3px solid #667eea;
            border-radius: 50%;
            width: 40px;
            height: 40px;
            animation: spin 1s linear infinite;
            margin: 0 auto;
        }

        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }

        .login-link {
            text-align: center;
            margin-top: 20px;
            font-size: 14px;
            color: #7f8c8d;
        }

        .login-link a {
            color: #667eea;
            text-decoration: none;
            font-weight: 500;
        }

        .login-link a:hover {
            text-decoration: underline;
        }

        .character-match {
            background: #e8f5e9;
            border-left: 4px solid #4caf50;
            padding: 12px;
            margin-bottom: 20px;
            border-radius: 4px;
            display: none;
        }

        .character-match p {
            color: #2e7d32;
            font-size: 14px;
            margin: 0;
        }
    </style>
</head>
<body>
    <div class="register-container">
        <div class="register-header">
            <h1>员工注册</h1>
            <p>囚牛银行知识库</p>
        </div>

        <div class="error-message" id="errorMessage"></div>
        <div class="success-message" id="successMessage"></div>
        
        <div class="character-match" id="characterMatch">
            <p>✅ 检测到您是 <strong id="characterName"></strong>（<span id="characterTitle"></span>），注册后将自动绑定该虚拟人物。</p>
        </div>

        <div class="loading" id="loading">
            <div class="loading-spinner"></div>
            <p style="margin-top: 10px; color: #7f8c8d;">注册中...</p>
        </div>

        <form id="registerForm">
            <div class="form-group">
                <label for="name">姓名</label>
                <input type="text" id="name" name="name" required 
                       placeholder="请输入真实姓名"
                       autocomplete="name">
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input type="password" id="password" name="password" required 
                       placeholder="请输入密码（至少6位）"
                       minlength="6">
            </div>

            <div class="form-group">
                <label for="confirmPassword">确认密码</label>
                <input type="password" id="confirmPassword" name="confirmPassword" required 
                       placeholder="请再次输入密码">
            </div>

            <button type="submit" class="btn-register" id="btnRegister">注册</button>
        </form>

        <div class="login-link">
            已有账号？<a href="employee-login.jsp">立即登录</a>
        </div>
    </div>

    <script>
        // 检查姓名是否匹配虚拟人物
        let matchedCharacter = null;

        document.getElementById('name').addEventListener('blur', async function() {
            const name = this.value.trim();
            if (name.length < 2) return;

            try {
                const response = await fetch(`${pageContext.request.contextPath}/api/virtual-human/characters`);
                const characters = await response.json();
                
                const match = characters.find(c => c.name === name);
                if (match) {
                    matchedCharacter = match;
                    document.getElementById('characterName').textContent = match.name;
                    document.getElementById('characterTitle').textContent = match.title;
                    document.getElementById('characterMatch').style.display = 'block';
                } else {
                    matchedCharacter = null;
                    document.getElementById('characterMatch').style.display = 'none';
                }
            } catch (error) {
                console.error('检查姓名匹配失败:', error);
            }
        });

        // 注册表单提交
        document.getElementById('registerForm').addEventListener('submit', async function(e) {
            e.preventDefault();

            const name = document.getElementById('name').value.trim();
            const password = document.getElementById('password').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            // 验证
            if (name.length < 2) {
                showError('姓名至少2个字符');
                return;
            }

            if (password.length < 6) {
                showError('密码至少6位');
                return;
            }

            if (password !== confirmPassword) {
                showError('两次输入的密码不一致');
                return;
            }

            // 显示加载
            document.getElementById('loading').style.display = 'block';
            document.getElementById('btnRegister').disabled = true;
            hideError();
            hideSuccess();

            try {
                const response = await fetch(`${pageContext.request.contextPath}/api/employee/register`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        name: name,
                        password: password,
                        character_id: matchedCharacter ? matchedCharacter.id : null
                    })
                });

                const data = await response.json();

                if (data.code === 200) {
                    showSuccess('注册成功！正在跳转到登录页面...');
                    setTimeout(() => {
                        window.location.href = 'employee-login.jsp';
                    }, 2000);
                } else {
                    showError(data.message || '注册失败');
                }
            } catch (error) {
                showError('网络错误，请稍后重试');
                console.error('注册失败:', error);
            } finally {
                document.getElementById('loading').style.display = 'none';
                document.getElementById('btnRegister').disabled = false;
            }
        });

        function showError(message) {
            const errorDiv = document.getElementById('errorMessage');
            errorDiv.textContent = message;
            errorDiv.style.display = 'block';
        }

        function hideError() {
            document.getElementById('errorMessage').style.display = 'none';
        }

        function showSuccess(message) {
            const successDiv = document.getElementById('successMessage');
            successDiv.textContent = message;
            successDiv.style.display = 'block';
        }

        function hideSuccess() {
            document.getElementById('successMessage').style.display = 'none';
        }
    </script>
</body>
</html>
