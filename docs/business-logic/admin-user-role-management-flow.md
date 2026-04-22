# 管理员用户搜索与角色管理链路实现说明

本文说明后台“按条件查用户”和“修改用户角色”的真实实现。它不是一个复杂状态机，但它直接决定谁拥有后台权限，因此属于高风险治理链路。

## 1. 入口与核心类

### 1.1 HTTP 入口

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`

暴露两个接口：

- `GET /api/users/admin/search`
- `POST /api/users/admin/role`

### 1.2 核心服务

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserService.java`

### 1.3 相关 DTO

- `backend/community-app/src/main/java/com/nowcoder/community/user/dto/AdminUserResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/dto/UpdateUserRoleRequest.java`

## 2. 搜索链路怎么工作

### 2.1 接口输入

`GET /api/users/admin/search` 支持三个可选查询条件：

- `userId`
- `username`
- `email`

但服务端要求三选一，不能全空。

### 2.2 解析优先级

`AdminUserService.resolveSearchTarget(...)` 的优先级是固定的：

1. 如果传了 `userId`，按 `userId` 查
2. 否则如果传了 `username`，按用户名查
3. 否则如果传了 `email`，按邮箱查
4. 如果都没传，抛 `INVALID_ARGUMENT`

所以这不是“多条件组合搜索”，而是“按单一主键/唯一键定位一个用户”。

### 2.3 查询结果如何返回

查到用户后，会映射成 `AdminUserResponse`，当前返回字段包括：

- `id`
- `username`
- `email`
- `type`
- `status`
- `headerUrl`
- `createTime`

如果没查到：

- 返回 `null`

这表示当前接口的语义更接近“查一个候选用户”，不是分页用户列表。

## 3. 角色修改链路怎么工作

### 3.1 HTTP 层只做两件事

`POST /api/users/admin/role` 的 controller 很薄，只负责：

1. 从 `Authentication` 中取当前操作人 `actorUserId`
2. 把请求交给 `adminUserService.updateRole(...)`

真正的业务保护都在 service 里。

### 3.2 输入校验

`updateRole(...)` 会先做一组强约束校验。

#### 3.2.1 request 不能为空

如果请求体本身是空的，直接抛：

- `INVALID_ARGUMENT`

#### 3.2.2 必须显式二次确认

代码要求：

- `request.isConfirm()` 必须为 `true`

否则报错：

- `需要二次确认（confirm=true）`

这说明角色变更不是普通表单提交，而是一个需要显式确认的危险操作。

#### 3.2.3 必须填写 reason

`reason` 会先 `trim`，空字符串视为非法。

也就是说：

- 前端不能只传空格
- 每次角色变更都必须留下人工理由

#### 3.2.4 必须指定目标用户

`targetUserId` 为空会直接拒绝。

### 3.3 目标用户校验

服务会用：

- `userMapper.selectById(targetUserId)`

查目标用户。

如果目标不存在，抛：

- `目标用户不存在`

这一步确保不会把角色更新打到空气数据上。

### 3.4 最关键的保护：禁止把自己降权

代码里有一个非常明确的保护：

- 如果 `targetUserId.equals(actorUserId)` 并且 `toType != 1`
- 直接抛 `FORBIDDEN`

也就是说，管理员不能把自己改成非管理员。

这个限制的价值非常大，因为它避免了两类事故：

- 误操作导致系统里没有管理员
- 恶意脚本先拿管理员身份，再自我降权制造审计混乱

### 3.5 相同角色不会重复写库

如果：

- `fromType == toType`

服务直接返回，不做更新。

这意味着该接口天然具备“同值更新 no-op”语义。

### 3.6 真正落库只有一条 update

实际更新调用：

- `userMapper.updateType(targetUserId, toType)`

如果更新行数 `<= 0`，抛：

- `更新用户角色失败`

当前实现非常直接：

- 不走复杂审批流
- 不维护角色变更历史表
- 直接改 `user.type`

因此 `user.type` 就是当前生效角色的主事实字段。

### 3.7 变更后只写审计日志，不写审计表

成功更新后会输出一条结构化日志：

- `action=admin_user_role_update`
- `actorUserId`
- `targetUserId`
- `fromType`
- `toType`
- `reason`

这意味着当前审计依赖应用日志，而不是数据库审计表。

对运维/排查来说，要查谁改了谁，需要回日志系统检索。

## 4. 这条链路的真实业务边界

### 4.1 它改的是“用户类型”，不是一套细粒度 RBAC

当前代码暴露的是：

- `type`

不是：

- 多角色集合
- 权限点集合
- 资源级授权策略

因此它更接近“用户身份等级切换”，不是完整的 RBAC 平台。

### 4.2 它没有审批和回滚流程

目前实现里没有看到：

- 申请单
- 审批单
- 生效时间
- 自动回滚

所以这是一个立即生效的后台治理操作。

### 4.3 它和权限系统是联动的，但联动发生在别处

`AdminUserController` 自己不写鉴权逻辑，真正“谁能调用它”通常由安全配置控制。

也就是说：

- 本文讲的是业务写路径
- 至于哪些角色可以访问这些接口，需要去对应 security rules / filter chain 看

## 5. 失败路径与风险点

### 5.1 全空查询条件会直接报参数错误

`search` 不是模糊搜索接口，传空不会返回全量用户。

### 5.2 改角色必须传确认标记和原因

少一个都不行。

这两个约束说明作者有意把“误触发”成本抬高。

### 5.3 找不到目标用户会失败

不会静默忽略。

### 5.4 自己不能把自己降成非管理员

这是最关键的保护逻辑。

### 5.5 更新失败会抛内部错误

如果数据库更新行数异常，当前实现不会吞掉，而是明确报错。

## 6. 初学者需要知道的几个实现事实

### 6.1 搜索接口不是列表接口

它不是后台用户管理台那种分页检索，而是“按唯一字段快速定位单个用户”。

### 6.2 角色变更主事实只有 `user.type`

当前没有单独的角色表或绑定表。

### 6.3 审计记录只在日志里

如果以后要做合规增强，这里最先要补的是：

- 审计表
- 审批流
- 变更前后快照

### 6.4 这个链路属于治理面，不是普通用户面

虽然代码量不大，但风险高于普通 CRUD，因为它直接影响后台权限边界。

## 7. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/AdminUserService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/dto/AdminUserResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/dto/UpdateUserRoleRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/entity/User.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/mapper/UserMapper.java`
