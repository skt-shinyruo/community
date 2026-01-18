# auth

## Purpose
legacy-community 中的旧认证模块：提供注册、激活、登录、登出、验证码与 ticket 登录态等能力（迁移期保留，目标态由 `auth-service` 替代）。

## Module Overview
- **Responsibility：** 注册/登录/退出；邮箱激活；验证码；登录态（ticket）管理；权限授权入口
- **Status：** 🟡Migration
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 用户注册与邮箱激活
**Module:** auth
用户完成注册后需要通过邮件激活账号才能登录。

#### Scenario: 注册成功并发送激活邮件
前置条件：用户名/邮箱未被占用
- 返回“注册成功”提示
- 邮箱收到激活链接

#### Scenario: 激活链接生效
前置条件：用户处于未激活状态
- 激活成功后可登录

### Requirement: 登录与登录态
**Module:** auth
使用验证码与 ticket（Redis）完成登录态维护。

#### Scenario: 登录成功写入 ticket Cookie
前置条件：验证码正确、密码正确、账号已激活
- 响应写入 cookie `ticket`
- 后续请求可识别登录用户

## API Interfaces（现状）
- `GET /register`、`POST /register`
- `GET /activation/{userId}/{code}`
- `GET /kaptcha`
- `GET /login`、`POST /login`
- `GET /logout`

## Data Models
### user
（详见 `helloagents/wiki/data.md` 的 “user” 小节）

## Dependencies
- user（用户数据）
- infra（拦截器注入 SecurityContext）
- message（通知/站内信依赖登录态展示未读数）

## Change History
- （暂无）
