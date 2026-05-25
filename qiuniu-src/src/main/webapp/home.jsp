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
    <title>囚牛 - 功能中心</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: '"Microsoft YaHei"', Arial, sans-serif;
            background: #f5f6fa;
            min-height: 100vh;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 30px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header h1 { font-size: 24px; }
        .header .user-info { display: flex; align-items: center; gap: 15px; }
        .btn-logout {
            background: rgba(255,255,255,0.2);
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            text-decoration: none;
            font-size: 14px;
        }
        .btn-logout:hover { background: rgba(255,255,255,0.3); }
        .container {
            max-width: 1100px;
            margin: 40px auto;
            padding: 0 20px;
        }
        .page-title {
            text-align: center;
            color: #333;
            font-size: 28px;
            margin-bottom: 40px;
            font-weight: 600;
        }
        .page-title span {
            color: #764ba2;
        }
        .module-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 30px;
        }
        .module-card {
            background: white;
            border-radius: 16px;
            padding: 40px 30px;
            text-align: center;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s;
            box-shadow: 0 2px 12px rgba(0,0,0,0.06);
            text-decoration: none;
            color: inherit;
            display: block;
            border: 2px solid transparent;
        }
        .module-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 8px 30px rgba(0,0,0,0.12);
            border-color: #667eea;
        }
        .module-card.disabled {
            cursor: default;
            opacity: 0.7;
        }
        .module-card.disabled:hover {
            transform: none;
            box-shadow: 0 2px 12px rgba(0,0,0,0.06);
            border-color: transparent;
        }
        .module-icon {
            width: 72px;
            height: 72px;
            margin: 0 auto 20px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 36px;
        }
        .module-icon.version { background: linear-gradient(135deg, #667eea22, #764ba222); }
        .module-icon.knowledge { background: linear-gradient(135deg, #f093fb22, #f5576c22); }
        .module-icon.mcp { background: linear-gradient(135deg, #4facfe22, #00f2fe22); }
        .module-name {
            font-size: 22px;
            font-weight: 600;
            color: #333;
            margin-bottom: 12px;
        }
        .module-desc {
            font-size: 14px;
            color: #888;
            line-height: 1.6;
        }
        .module-tag {
            display: inline-block;
            margin-top: 16px;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 12px;
        }
        .module-tag.active { background: #667eea22; color: #667eea; }
        .module-tag.soon { background: #4facfe22; color: #00a8cc; }
        .footer {
            text-align: center;
            margin-top: 60px;
            color: #aaa;
            font-size: 13px;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🐉 囚牛</h1>
        <div class="user-info">
            <span class="user-name">${ user != null ? user.nickname : "" }</span>
            <a href="${pageContext.request.contextPath}/logout" class="btn-logout">退出</a>
        </div>
    </div>

    <div class="container">
        <h2 class="page-title">选择<span>功能模块</span></h2>
        <div class="module-grid">
            <a href="${pageContext.request.contextPath}/dashboard.jsp" class="module-card">
                <div class="module-icon version">📊</div>
                <div class="module-name">版本管理</div>
                <div class="module-desc">Git 代码仓库版本分析<br>投产变更比对<br>代码质量审查</div>
                <span class="module-tag active">进行中</span>
            </a>

            <a href="${pageContext.request.contextPath}/knowledge-base.jsp" class="module-card">
                <div class="module-icon knowledge">📚</div>
                <div class="module-name">知识库</div>
                <div class="module-desc">AI 智能问答助手<br>基于私有知识库<br>精准回答业务问题</div>
                <span class="module-tag active">进行中</span>
            </a>

            <a href="${pageContext.request.contextPath}/mcp-service.jsp" class="module-card">
                <div class="module-icon mcp">⚙️</div>
                <div class="module-name">流水线日志 MCP 服务参数维护</div>
                <div class="module-desc">MCP 服务参数维护<br>命令/提示词/环境配置<br>工具链配置管理</div>
                <span class="module-tag active">进行中</span>
            </a>
        </div>

        <div class="footer">
            囚牛 · QiuNiu v1.0
        </div>
    </div>
</body>
</html>