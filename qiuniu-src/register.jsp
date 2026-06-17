<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>注册 - 囚牛</title>
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
        
        .register-container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            padding: 50px 40px;
            width: 450px;
            max-width: 90%;
        }
        
        .register-container h1 {
            color: #333;
            margin-bottom: 10px;
            font-size: 32px;
            text-align: center;
        }
        
        .register-container .subtitle {
            color: #666;
            margin-bottom: 40px;
            font-size: 14px;
            text-align: center;
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
        
        .btn-register {
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
            margin-top: 10px;
        }
        
        .btn-register:hover {
            transform: translateY(-2px);
            box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
        }
        
        .login-link {
            text-align: center;
            margin-top: 25px;
            color: #666;
            font-size: 14px;
        }
        
        .login-link a {
            color: #667eea;
            text-decoration: none;
            font-weight: 600;
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
    </style>
</head>
<body>
    <div class="register-container">
        <h1>🦞 囚牛</h1>
        <p class="subtitle">创建新账号</p>
        
        <div class="error-message" id="errorMsg"></div>
        
        <form id="registerForm" onsubmit="return handleRegister(event)">
            <div class="form-group">
                <label for="username">用户名 *</label>
                <input type="text" id="username" name="username" required placeholder="3-20 位字母或数字">
            </div>
            
            <div class="form-group">
                <label for="nickname">昵称</label>
                <input type="text" id="nickname" name="nickname" placeholder="显示名称（可选）">
            </div>
            
            <div class="form-group">
                <label for="email">邮箱</label>
                <input type="email" id="email" name="email" placeholder="your@email.com（可选）">
            </div>
            
            <div class="form-group">
                <label for="password">密码 *</label>
                <input type="password" id="password" name="password" required placeholder="至少 6 位">
            </div>
            
            <div class="form-group">
                <label for="confirmPassword">确认密码 *</label>
                <input type="password" id="confirmPassword" name="confirmPassword" required placeholder="再次输入密码">
            </div>
            
            <button type="submit" class="btn-register">注 册</button>
        </form>
        
        <div class="login-link">
            已有账号？<a href="${pageContext.request.contextPath}/login.jsp">立即登录</a>
        </div>
    </div>
    
    <script>
        function handleRegister(event) {
            event.preventDefault();
            
            const form = event.target;
            const username = form.username.value.trim();
            const nickname = form.nickname.value.trim();
            const email = form.email.value.trim();
            const password = form.password.value;
            const confirmPassword = form.confirmPassword.value;
            
            if (username.length < 3 || username.length > 20) {
                showError('用户名长度应在 3-20 位之间');
                return false;
            }
            
            if (password.length < 6) {
                showError('密码长度不能少于 6 位');
                return false;
            }
            
            if (password !== confirmPassword) {
                showError('两次输入的密码不一致');
                return false;
            }
            
            const btn = form.querySelector('.btn-register');
            btn.disabled = true;
            btn.textContent = '注册中...';
            
            fetch('${pageContext.request.contextPath}/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: 'username=' + encodeURIComponent(username) + '&nickname=' + encodeURIComponent(nickname) + '&email=' + encodeURIComponent(email) + '&password=' + encodeURIComponent(password) + '&confirmPassword=' + encodeURIComponent(confirmPassword)
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    window.location.href = data.redirect;
                } else {
                    showError(data.message);
                    btn.disabled = false;
                    btn.textContent = '注 册';
                }
            })
            .catch(err => {
                showError('网络错误，请稍后重试');
                btn.disabled = false;
                btn.textContent = '注 册';
            });
            
            return false;
        }
        
        function showError(msg) {
            const el = document.getElementById('errorMsg');
            el.textContent = msg;
            el.style.display = 'block';
            setTimeout(() => {
                el.style.display = 'none';
            }, 5000);
        }
    </script>
</body>
</html>
