package com.ruoyi.project.laboratory.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import com.ruoyi.framework.security.LoginUser;
import com.ruoyi.project.system.domain.SysRole;
import com.ruoyi.project.system.domain.SysUser;

/**
 * 实验室模块测试用登录上下文。
 *
 * <p>三类业务的 Service 入口都会先取当前登录用户（申请人、操作人、可见范围过滤），
 * 没有安全上下文时会抛「获取用户信息异常」，测试就变成假绿灯。这里把安装上下文
 * 的样板收敛到一处，各测试类不再各写一份。
 *
 * <p>两个必须守住的细节：
 * <ul>
 * <li>普通用户 id 用 {@link #NORMAL_USER_ID}（100），<b>不能用 1</b>——
 * {@code SecurityUtils.isAdmin(userId)} 的判定是 {@code userId != null && 1L == userId}，
 * 用 1 会让权限判定直接短路成超级管理员，整组权限用例静默失真。</li>
 * <li>{@link LoginUser} 必须走 <b>4 参数</b>构造。2 参数的
 * {@code LoginUser(SysUser, Set)} 不赋值 userId 字段，{@code SecurityUtils.getUserId()}
 * 会返回 null，报修单的申请人字段就成了 null。</li>
 * </ul>
 *
 * @author ruoyi
 */
public final class LabTestSupport
{
    /** 普通测试用户 id。切勿改成 1。 */
    public static final Long NORMAL_USER_ID = 100L;

    /** 另一个普通用户 id，用于构造「他人提交的单据」。 */
    public static final Long OTHER_USER_ID = 200L;

    /** 测试用部门 id。 */
    public static final Long NORMAL_DEPT_ID = 100L;

    /** 测试用户账号，会写进报修履历的操作人字段。 */
    public static final String NORMAL_USER_NAME = "lab_tester";

    /** 测试用户昵称，会写进报修单的申请人姓名字段。 */
    public static final String NORMAL_NICK_NAME = "测试用户";

    /** 测试用户手机号。 */
    public static final String NORMAL_PHONE = "13800000000";

    private LabTestSupport()
    {
    }

    /**
     * 安装一个普通登录上下文（id 固定 100），并授予给定角色。
     */
    public static void loginAs(String... roleKeys)
    {
        loginAsUser(NORMAL_USER_ID, roleKeys);
    }

    /**
     * 安装内置超级管理员上下文（id = 1），角色列表为空——用于验证「无视角色列表」这一条。
     */
    public static void loginAsAdmin()
    {
        install(buildUser(1L, new String[0]));
    }

    /**
     * 安装指定 id 的登录上下文。
     */
    public static void loginAsUser(Long userId, String... roleKeys)
    {
        install(buildUser(userId, roleKeys));
    }

    /**
     * 清理上下文。测试类应在 {@code @AfterEach} 里调用，避免用例之间互相污染。
     */
    public static void logout()
    {
        SecurityContextHolder.clearContext();
    }

    /**
     * 构造真实角色数据，供被测方法内部按 roleKey 遍历。
     */
    public static List<SysRole> roleList(String... roleKeys)
    {
        List<SysRole> roles = new ArrayList<SysRole>();
        for (String roleKey : roleKeys)
        {
            SysRole role = new SysRole();
            role.setRoleKey(roleKey);
            roles.add(role);
        }
        return roles;
    }

    /**
     * 构造一个不依赖线程上下文的用户对象，用于直接调用权限判定的 SysUser 重载。
     */
    public static SysUser user(Long userId, String... roleKeys)
    {
        return buildUser(userId, roleKeys);
    }

    private static SysUser buildUser(Long userId, String... roleKeys)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(NORMAL_USER_NAME);
        user.setNickName(NORMAL_NICK_NAME);
        user.setPhonenumber(NORMAL_PHONE);
        user.setRoles(roleList(roleKeys));
        return user;
    }

    private static void install(SysUser user)
    {
        Set<String> permissions = new HashSet<String>();
        LoginUser loginUser = new LoginUser(user.getUserId(), NORMAL_DEPT_ID, user, permissions);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(loginUser, null, null);
        SecurityContextHolder.setContext(new SecurityContextImpl(token));
    }
}
