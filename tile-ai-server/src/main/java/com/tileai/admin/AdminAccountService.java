package com.tileai.admin;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.rbac.AdminRole;

/**
 * 后台账号的读取与角色解析(架构 §4.4、§8.1)。
 *
 * <p><b>为什么每次都查库,不把角色放进令牌</b>:令牌一旦签发就固定了内容。
 * 若把角色写进令牌,管理员被降权或停用后,其旧令牌会在最长 2 小时内继续按旧权限放行。
 * 权限判断读的必须是"当下的事实"。t_admin_user 是极小表、访问频率低,
 * 这个查询的代价可以忽略(§13.1 允许非生成类接口 P95 < 500ms)。
 */
@Service
public class AdminAccountService {

    private final AdminUserMapper adminUserMapper;

    public AdminAccountService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    public Optional<AdminUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username)));
    }

    /**
     * 按主键取一个**启用中**的后台账号。
     *
     * <p>账号不存在或已停用一律返回空。<b>失败关闭</b>:令牌签发后账号可能被停用,
     * 而令牌在最长 2 小时内仍然有效 —— 那时必须按"无此人"处理,不能放行。
     *
     * <p>供 {@code GET /admin/auth/me} 用(会话恢复),也让 {@link #roleOf} 复用它,
     * 避免"什么算有效账号"的判定散成两份。
     */
    public Optional<AdminUser> findEnabledById(long adminId) {
        AdminUser admin = adminUserMapper.selectById(adminId);
        if (admin == null || !AdminUser.STATUS_ENABLED.equals(admin.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(admin);
    }

    /**
     * 解析某后台账号<b>当前</b>的角色。
     *
     * <p>以下情形一律返回空(即"无任何权限"):
     * 账号不存在、账号已停用、角色值无法识别。
     */
    public Optional<AdminRole> roleOf(long adminId) {
        return findEnabledById(adminId).map(admin -> AdminRole.of(admin.getRole()));
    }
}
