<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>登录 - 囚牛</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            min-height: 100vh;
            display: flex;
            justify-content: center;
            align-items: center;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        
        .login-container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
            width: 900px;
            max-width: 90%;
            display: flex;
        }
        
        .login-image {
            flex: 1;
            background-image: url('${pageContext.request.contextPath}/images/login-bg.jpg');
            background-size: cover;
            background-position: center;
            min-height: 500px;
        }
        
        .login-form {
            flex: 1;
            padding: 50px 40px;
            display: flex;
            flex-direction: column;
            justify-content: center;
        }
        
        .login-form h1 {
            color: #333;
            margin-bottom: 10px;
            font-size: 32px;
        }
        
        .login-form .subtitle {
            color: #666;
            margin-bottom: 40px;
            font-size: 14px;
        }
        
        .form-group {
            margin-bottom: 25px;
        }
        
        .form-group label {
            display: block;
            margin-bottom: 8px;
            color: #555;
            font-weight: 500;
        }
        
        .form-group input {
            width: 100%;
            padding: 14px 16px;
            border: 2px solid #e0e0e0;
            border-radius: 10px;
            font-size: 15px;
            transition: all 0.3s;
        }
        
        .form-group input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }
        
        .form-options {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 30px;
        }
        
        .form-options label {
            display: flex;
            align-items: center;
            color: #666;
            font-size: 14px;
            cursor: pointer;
        }
        
        .form-options input[type="checkbox"] {
            margin-right: 8px;
            width: 16px;
            height: 16px;
        }
        
        .form-options a {
            color: #667eea;
            text-decoration: none;
            font-size: 14px;
        }
        
        .form-options a:hover {
            text-decoration: underline;
        }
        
        .btn-login {
            width: 100%;
            padding: 15px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 10px;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        
        .btn-login:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
        }
        
        .btn-login:active {
            transform: translateY(0);
        }
        
        .register-link {
            text-align: center;
            margin-top: 25px;
            color: #666;
            font-size: 14px;
        }
        
        .register-link a {
            color: #667eea;
            text-decoration: none;
            font-weight: 600;
        }
        
        .register-link a:hover {
            text-decoration: underline;
        }
        
        .error-message {
            background: #fee;
            color: #c00;
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
            display: none;
        }
        
        .success-message {
            background: #efe;
            color: #060;
            padding: 12px 16px;
            border-radius: 8px;
            margin-bottom: 20px;
            font-size: 14px;
            display: none;
        }
        
        @media (max-width: 768px) {
            .login-container {
                flex-direction: column;
            }
            .login-image {
                display: none;
            }
        }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-image"></div>
        <div class="login-form">
            <h1>🦞 囚牛</h1>
            <p class="subtitle">人工智能平台</p>
            
            <div class="error-message" id="errorMsg"></div>
            <div class="success-message" id="successMsg"></div>
            
            <form id="loginForm" onsubmit="return handleLogin(event)">
                <div class="form-group">
                    <label for="username">用户名</label>
                    <input type="text" id="username" name="username" required placeholder="请输入用户名">
                </div>
                
                <div class="form-group">
                    <label for="password">密码</label>
                    <input type="password" id="password" name="password" required placeholder="请输入密码">
                </div>
                
                <div class="form-options">
                    <label>
                        <input type="checkbox" name="remember"> 记住我
                    </label>
                    <a href="#">忘记密码？</a>
                </div>
                
                <button type="submit" class="btn-login">登 录</button>
            </form>
            
            <div class="register-link">
                还没有账号？<a href="${pageContext.request.contextPath}/register.jsp">立即注册</a>
            </div>
        </div>
    </div>
    
    <script>
        function handleLogin(event) {
            event.preventDefault();
            
            const form = event.target;
            const username = form.username.value.trim();
            const password = form.password.value;
            const remember = form.remember.checked ? 'on' : '';
            
            if (!username || !password) {
                showError('请输入用户名和密码');
                return false;
            }
            
            const btn = form.querySelector('.btn-login');
            btn.disabled = true;
            btn.textContent = '登录中...';
            
            fetch('${pageContext.request.contextPath}/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: 'username=' + encodeURIComponent(username) + '&password=' + encodeURIComponent(password) + '&remember=' + remember
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showSuccess('登录成功，正在跳转...');
                    // 存储角色信息到 localStorage，供前端权限控制
                    if (data.role) {
                        localStorage.setItem('userRole', data.role);
                    }
                    if (data.nickname) {
                        localStorage.setItem('userNickname', data.nickname);
                    }
                    setTimeout(() => {
                        window.location.href = data.redirect;
                    }, 1000);
                } else {
                    showError(data.message);
                    btn.disabled = false;
                    btn.textContent = '登 录';
                }
            })
            .catch(err => {
                showError('网络错误，请稍后重试');
                btn.disabled = false;
                btn.textContent = '登 录';
            });
            
            return false;
        }
        
        function showError(msg) {
            const el = document.getElementById('errorMsg');
            el.textContent = msg;
            el.style.display = 'block';
            document.getElementById('successMsg').style.display = 'none';
        }
        
        function showSuccess(msg) {
            const el = document.getElementById('successMsg');
            el.textContent = msg;
            el.style.display = 'block';
            document.getElementById('errorMsg').style.display = 'none';
        }
        
        // 检查 URL 参数是否有注册成功的提示
        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.get('registered') === '1') {
            showSuccess('注册成功，请登录');
        }
    </script>
</body>
</html>
