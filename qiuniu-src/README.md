# 🦞 囚牛 (QiuNiu) - 提示词管理系统

基于 Tomcat 的 Java Web 应用，用于管理和维护大模型提示词。

## 功能特性

- ✅ 用户注册与登录（密码加密存储）
- ✅ 提示词 CRUD 管理（新增、修改、删除、查询）
- ✅ 提示词分类管理
- ✅ 关键词搜索
- ✅ MySQL 数据库存储
- ✅ 响应式界面设计

---

## 📁 项目结构

```
QiuNiu/
├── src/main/
│   ├── java/com/qiuniu/
│   │   ├── dao/           # 数据访问层
│   │   ├── model/         # 数据模型
│   │   ├── servlet/       # Servlet 控制器
│   │   ├── filter/        # 过滤器
│   │   └── listener/      # 监听器
│   ├── resources/
│   │   └── db.properties  # 【配置文件】数据库连接配置
│   └── webapp/
│       ├── WEB-INF/
│       │   └── web.xml    # Web 应用配置
│       ├── images/        # 图片资源
│       ├── css/           # 样式文件
│       ├── js/            # JavaScript 文件
│       ├── login.jsp      # 登录页面
│       ├── register.jsp   # 注册页面
│       └── dashboard.jsp  # 控制台页面
├── pom.xml                # Maven 配置
└── README.md              # 本文件
```

---

## ⚙️ 配置说明

### 1. 数据库配置

**配置文件位置：** `src/main/resources/db.properties`

**需要修改的内容：**

```properties
# MySQL 数据库连接 URL
db.url=jdbc:mysql://localhost:3306/qiuniu_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8&allowPublicKeyRetrieval=true

# MySQL 用户名
db.username=root

# MySQL 密码（⚠️ 必须修改）
db.password=your_password_here
```

### 2. 数据库初始化

系统首次启动时会自动创建数据库表，但需要预先创建数据库：

```sql
-- 登录 MySQL 后执行
CREATE DATABASE qiuniu_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**自动创建的表：**
- `users` - 用户表
- `prompts` - 提示词表

---

## 🚀 部署步骤

### 环境要求

- JDK 11 或更高版本
- Apache Tomcat 9.x 或更高版本
- MySQL 8.0 或更高版本
- Maven 3.6+

### 步骤 1：编译项目

```bash
cd D:\OpenclawCode\QiuNiu
mvn clean package
```

编译后的 WAR 文件位于：`target/qiuniu.war`

### 步骤 2：部署到 Tomcat

1. 将 `target/qiuniu.war` 复制到 Tomcat 的 `webapps` 目录
2. 启动 Tomcat

```bash
# Windows
cd %TOMCAT_HOME%\bin
startup.bat

# Linux/Mac
cd $TOMCAT_HOME/bin
./startup.sh
```

### 步骤 3：访问应用

打开浏览器访问：`http://localhost:8080/qiuniu/`

---

## 📖 使用说明

### 1. 注册账号

- 访问登录页面，点击"立即注册"
- 填写用户名、密码等信息
- 用户名长度：3-20 位字母或数字
- 密码长度：至少 6 位

### 2. 登录系统

- 输入用户名和密码
- 可选"记住我"功能（7 天免登录）

### 3. 管理提示词

登录后进入控制台，可以：

- **新建提示词**：点击右上角"+ 新建提示词"按钮
- **编辑提示词**：点击列表中的"编辑"按钮
- **删除提示词**：点击列表中的"删除"按钮
- **搜索提示词**：在搜索框输入关键词
- **分类筛选**：使用分类下拉框筛选

### 4. 提示词字段说明

| 字段 | 说明 | 必填 |
|------|------|------|
| 名称 | 提示词的标题 | 是 |
| 分类 | 用于分类管理（如：代码生成、文案写作） | 否 |
| 描述 | 简要描述提示词用途 | 否 |
| 内容 | 完整的提示词文本 | 是 |

---

## 🔧 开发说明

### 技术栈

- **后端**：Java Servlet 4.0, JSP
- **数据库**：MySQL 8.0
- **连接池**：Apache DBCP2
- **构建工具**：Maven
- **密码加密**：BCrypt

### 依赖管理

所有依赖在 `pom.xml` 中配置：

```xml
<!-- 主要依赖 -->
- javax.servlet-api (Servlet 容器)
- mysql-connector-java (MySQL 驱动)
- commons-dbcp2 (数据库连接池)
- gson (JSON 处理)
- jbcrypt (密码加密)
```

### API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/login` | POST | 用户登录 |
| `/register` | POST | 用户注册 |
| `/logout` | GET | 退出登录 |
| `/prompt` | GET | 获取提示词列表 |
| `/prompt` | POST | 创建/更新/删除提示词 |

**请求参数示例：**

```
# 获取列表
GET /prompt?action=list
GET /prompt?action=list&category=代码生成
GET /prompt?action=search&keyword=关键词

# 创建
POST /prompt
action=create&name=名称&content=内容&category=分类&description=描述

# 更新
POST /prompt
action=update&id=1&name=新名称&content=新内容

# 删除
POST /prompt
action=delete&id=1
```

---

## 🔐 安全说明

- 密码使用 BCrypt 加密存储
- 会话超时时间：30 分钟
- 所有页面需要登录访问（除登录/注册页）
- 支持"记住我"功能（7 天）

---

## 📝 常见问题

### Q: 数据库连接失败？
A: 检查 `db.properties` 中的配置是否正确，确保 MySQL 服务已启动。

### Q: 表没有自动创建？
A: 检查应用日志，确保数据库已创建且有足够权限。

### Q: 中文乱码？
A: 确保 Tomcat 配置了 UTF-8 编码，数据库使用 utf8mb4 字符集。

---

## 📄 许可证

本项目仅供学习和个人使用。

---

**🦞 囚牛 - 让提示词管理更简单**
