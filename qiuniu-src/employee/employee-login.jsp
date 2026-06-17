<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>员工登录 - 囚牛银行知识库</title>
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

        .login-container {
            background: white;
            border-radius: 16px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            padding: 40px;
            width: 100%;
            max-width: 400px;
        }

        .login-header {
            text-align: center;
            margin-bottom: 30px;
        }

        .login-header h1 {
            color: #2c3e50;
            font-size: 28px;
            margin-bottom: 10px;
        }

        .login-header p {
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

        .btn-login {
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

        .btn-login:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
        }

        .btn-login:disabled {
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

        .register-link {
            text-align: center;
            margin-top: 20px;
            font-size: 14px;
            color: #7f8c8d;
        }

        .register-link a {
            color: #667eea;
            text-decoration: none;
            font-weight: 500;
        }

        .register-link a:hover {
            text-decoration: underline;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-header">
            <h1>员工登录</h1>
            <p>囚牛银行知识库</p>
        </div>

        <div class="error-message" id="errorMessage"></div>
        
        <div class="loading" id="loading">
            <div class="loading-spinner"></div>
            <p style="margin-top: 10px; color: #7f8c8d;">登录中...</p>
        </div>

        <form id="loginForm">
            <div class="form-group">
                <label for="name">姓名</label>
                <input type="text" id="name" name="name" required 
                       placeholder="请输入姓名"
                       autocomplete="name">
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input type="password" id="password" name="password" required 
                       placeholder="请输入密码"
                       autocomplete="current-password">
            </div>

            <button type="submit" class="btn-login" id="btnLogin">登录</button>
        </form>

        <div class="register-link">
            还没有账号？<a href="employee-register.jsp">立即注册</a>
        </div>
    </div>

    <script>
        document.getElementById('loginForm').addEventListener('submit', async function(e) {
            e.preventDefault();

            const name = document.getElementById('name').value.trim();
            const password = document.getElementById('password').value;

            // 验证
            if (!name) {
                showError('请输入姓名');
                return;
            }

            if (!password) {
                showError('请输入密码');
                return;
            }

            // 显示加载
            document.getElementById('loading').style.display = 'block';
            document.getElementById('btnLogin').disabled = true;
            hideError();

            try {
                const response = await fetch(`${pageContext.request.contextPath}/api/employee/login`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        name: name,
                        password: password
                    })
                });

                const data = await response.json();

                if (data.code === 200 && data.data && data.data.token) {
                    // 存储登录信息
                    localStorage.setItem('emp_token', data.data.token);
                    localStorage.setItem('emp_name', data.data.name);
                    localStorage.setItem('emp_no', data.data.employee_no || '');
                    localStorage.setItem('emp_char_id', data.data.character_id || '');
                    
                    // 跳转到员工门户
                    window.location.href = `${pageContext.request.contextPath}/employee/employee-portal.jsp`;
                } else {
                    showError(data.message || '登录失败');
                }
            } catch (error) {
                showError('网络错误，请稍后重试');
                console.error('登录失败:', error);
            } finally {
                document.getElementById('loading').style.display = 'none';
                document.getElementById('btnLogin').disabled = false;
            }
        });

        // 支持回车登录
        document.getElementById('password').addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                document.getElementById('loginForm').dispatchEvent(new Event('submit'));
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
    </script>
</body>
</html>
