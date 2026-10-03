package com.tileai.admin;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tileai.admin.auth.AdminUserView;
import com.tileai.admin.dto.AdminUserUpsertRequest;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.rbac.AdminRole;
import com.tileai.auth.AuthService;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 后台账号的<b>管理</b>侧服务(架构 §4.4、§8.1)。
 *
 * <p><b>为什么不并进 {@link AdminAccountService}</b>:那个类在<b>鉴权热路径</b>上
 * ——{@code AdminPermissionInterceptor} 与登录都在注入它,它每多一个写方法,
 * 判权路径的爆炸半径就大一圈。读与管分开,谁也别带累谁。
 *
 * <p>它只复用对方的读方法({@code findByUsername}),方向是单向的。
 *
 * <h2>两条防呆(实现比契约严,记账见 {@code openapi-verification.md} §58)</h2>
 *
 * <p>契约没写这两条,失败码用的是既有的 {@code 30002}(不新增码)。两条都
 * <b>在服务层用库里的现值判断</b>,不看请求体 —— 前端隐藏按钮只是别给出
 * "点了必然被拒"的入口,边界在这里。
 *
 * <p>②尤其值得说明:<b>它能触发是因为覆盖了「改角色」</b>。只防"停用/删除"的话,
 * 规则永远触发不到 —— 能调这个接口的必然是<b>启用中的超管</b>(权限点只给超管,
 * 且账号停用即通不过判权),他停用别人时自己还留着。真正的洞是<b>降级</b>:
 * 把自己或另一个超管改成运营/编辑,系统就没有超管了。
 */
@Service
public class AdminAccountAdminService {

    private final AdminUserMapper adminUserMapper;
    private final AdminAccountService adminAccountService;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountAdminService(AdminUserMapper adminUserMapper,
                                    AdminAccountService adminAccountService,
                                    PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.adminAccountService = adminAccountService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页列表(契约 {@code GET /admin/accounts})。
     *
     * <p>按 id 倒序:新建的排在前面。返回的是 {@link AdminUserView} 而不是实体
     * ——实体含 {@code passwordHash},绝不能透出去。
     */
    @Transactional(readOnly = true)
    public PageData<AdminUserView> page(int page, int size) {
        Page<AdminUser> result = adminUserMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminUser>lambdaQuery().orderByDesc(AdminUser::getId));
        return PageData.of(result.getRecords().stream().map(AdminUserView::of).toList(),
                result.getTotal(), page, size);
    }

    /**
     * 新增账号(契约 {@code POST /admin/accounts})。
     *
     * <p><b>密码必填</b>:空/太短/太长都走 {@code AuthService.validatePasswordFormat},
     * 抛的是 <b>20004</b>(契约声明),不是 10001。
     */
    @Transactional
    public long create(AdminUserUpsertRequest request) {
        String username = request.username().trim();
        if (adminAccountService.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_REGISTERED, "用户名已注册:" + username);
        }
        // 空密码会在这里抛 20004 —— 新增场景下 "password 为空" 就是格式不合规
        AuthService.validatePasswordFormat(request.password());

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setRole(request.role());
        admin.setStatus(StringUtils.hasText(request.status())
                ? request.status() : AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        return admin.getId();
    }

    /**
     * 编辑账号 / 分配角色(契约 {@code PUT /admin/accounts/{id}})。
     *
     * <p><b>密码留空 = 不修改</b>:只有 {@code hasText} 时才校验并重新编码,否则
     * {@code password_hash} 连碰都不碰。这是本接口最容易写错的一处 ——
     * 写成"空串也编码一次"就等于把用户密码静默清空。
     *
     * @param currentAdminId 操作人(来自当前会话,<b>不是</b>请求体)
     */
    @Transactional
    public void update(long id, AdminUserUpsertRequest request, long currentAdminId) {
        AdminUser target = require(id);
        String username = request.username().trim();

        adminAccountService.findByUsername(username)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.USERNAME_ALREADY_REGISTERED,
                            "用户名已注册:" + username);
                });

        String nextRole = request.role();
        // 请求没给状态就沿用现值 —— 否则"只改角色"会把启用的账号顺手弄停用
        String nextStatus = StringUtils.hasText(request.status())
                ? request.status() : target.getStatus();

        // 防呆①:不能停用自己
        if (id == currentAdminId && AdminUser.STATUS_DISABLED.equals(nextStatus)) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED, "不能停用自己");
        }
        // 防呆②:不能让"启用的超级管理员"归零(含改角色降级)
        assertKeepsOneEnabledSuperAdmin(target, nextRole, nextStatus);

        target.setUsername(username);
        target.setRole(nextRole);
        target.setStatus(nextStatus);
        if (StringUtils.hasText(request.password())) {
            AuthService.validatePasswordFormat(request.password());
            target.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        adminUserMapper.updateById(target);
    }

    /**
     * 删除账号(契约 {@code DELETE /admin/accounts/{id}})。
     *
     * <p><b>真删除</b>(物理删该行,不可撤销)。后果要说清:它写过的历史操作日志里
     * {@code admin_id} 会指向一个不存在的账号 —— 表里没有外键,不会报错,
     * 但日志页的「操作人」会显示成"已删除账号 #N"。这是**刻意保留的痕迹**:
     * 审计要的是"当时发生过什么",不是"现在还找不找得到那个人"。
     *
     * <p>两条防呆复用与 PUT 相同的一套判断("删除"等价于"改成停用"来看待)。
     *
     * @param currentAdminId 操作人(来自当前会话,<b>不是</b>请求体)
     */
    @Transactional
    public void delete(long id, long currentAdminId) {
        AdminUser target = require(id);
        if (id == currentAdminId) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED, "不能删除自己");
        }
        assertKeepsOneEnabledSuperAdmin(target, target.getRole(), AdminUser.STATUS_DISABLED);
        adminUserMapper.deleteById(id);
    }

    /** 按主键取账号;不存在抛 30001(契约声明的失败码)。 */
    private AdminUser require(long id) {
        AdminUser admin = adminUserMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        return admin;
    }

    /**
     * 防呆②:<b>任何会把"启用的超级管理员"清空的操作都拒绝。</b>
     *
     * <p>只在"本来就是启用的超管、而这次改动让它不再是"时才需要算人数 ——
     * 改一个本来就不是超管、或本来就停用的账号,不可能让超管变少。
     */
    private void assertKeepsOneEnabledSuperAdmin(AdminUser target, String nextRole,
                                                 String nextStatus) {
        if (!isEnabledSuperAdmin(target.getRole(), target.getStatus())) {
            return;
        }
        if (isEnabledSuperAdmin(nextRole, nextStatus)) {
            return;
        }
        if (countEnabledSuperAdmins() <= 1) {
            throw new BusinessException(ErrorCode.STATE_NOT_ALLOWED,
                    "系统必须保留至少一个启用的超级管理员");
        }
    }

    private static boolean isEnabledSuperAdmin(String role, String status) {
        return AdminUser.STATUS_ENABLED.equals(status)
                && AdminRole.SUPER_ADMIN.code().equals(role);
    }

    /** 当前**启用中**的超管人数 —— 判据是库里的事实,不是缓存。 */
    private long countEnabledSuperAdmins() {
        return adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getStatus, AdminUser.STATUS_ENABLED)
                .eq(AdminUser::getRole, AdminRole.SUPER_ADMIN.code()));
    }
}
