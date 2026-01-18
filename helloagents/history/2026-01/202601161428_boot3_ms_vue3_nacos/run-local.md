# 迭代 0 本地启动说明（Gateway + Auth + Vue3）

> 目标：在新环境下可复现最小闭环：`Vue3 -> Gateway -> Auth` 登录/refresh/logout。

---

## 0. 前置要求
- Java：17
- Maven：3.8+
- Node：20+（本仓库已用 npm + package-lock）
- 基础设施：Nacos + MySQL + Redis

本仓库提供 `deploy/docker-compose.yml` 作为示例；若环境无法访问 Docker Hub，请使用本地安装或公司内镜像源启动。

---

## 1. 启动基础设施（示例）

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d
```

> 若你没有 `deploy/.env`：可从 `deploy/.env.example` 复制一份（已在 `.gitignore` 中忽略）。

---

## 2. 设置关键环境变量（必须）

`gateway` 与 `auth-service` 必须使用同一把 HMAC 密钥（>= 32 字节）：

```bash
export AUTH_JWT_HMAC_SECRET="${AUTH_JWT_HMAC_SECRET:?请设置一个 >= 32 字节的随机密钥}"
export GATEWAY_JWT_HMAC_SECRET="${GATEWAY_JWT_HMAC_SECRET:-$AUTH_JWT_HMAC_SECRET}"
```

MySQL/Redis 若使用默认本地端口，可不设置；否则按需设置：

```bash
export AUTH_DB_URL="jdbc:mysql://127.0.0.1:3306/community?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
export AUTH_DB_USERNAME="community"
export AUTH_DB_PASSWORD="community"

export AUTH_REDIS_HOST="127.0.0.1"
export AUTH_REDIS_PORT="6379"
```

---

## 3. 启动后端服务

### 3.1 启动 auth-service（端口 8082）
```bash
mvn -pl auth-service -am spring-boot:run
```

### 3.2 启动 gateway（端口 8080）
```bash
mvn -pl gateway -am spring-boot:run
```

### 3.3（迭代 2）启动 social-service（端口 8086）
```bash
mvn -pl social-service -am spring-boot:run
```

健康检查（示例）：
- `GET http://localhost:8080/actuator/health`
- `GET http://localhost:8082/actuator/health`
- `GET http://localhost:8086/actuator/health`

---

## 4. 启动前端（Vue3）

```bash
npm -C frontend install
npm -C frontend run dev
```

- 打开：`http://localhost:5173`
- Vite 已配置 proxy：`/api -> http://localhost:8080`

---

## 5. 冒烟验证

### 5.1 手工
1) 前端登录  
2) 进入首页后调用 `/api/auth/me`  
3) 等待 access 过期后触发 refresh（或手动调用 `/api/auth/refresh`）  
4) 登出 `/api/auth/logout`  

### 5.2 脚本
```bash
GATEWAY_URL="http://localhost:8080" USERNAME="aaa" PASSWORD="aaa" scripts/smoke-i0-auth.sh
```
