package com.ruoyi.project.laboratory.util;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;

/**
 * 实验室模块操作人取值。
 *
 * <p>原先 LabRepairServiceImpl 与 LabAssetServiceImpl 各写了一份 {@code operatorName()}，
 * 一份带 fallback、一份不带，这里合并成一个签名。
 *
 * <p>取值优先级：调用方传入的账号 → 当前登录用户 → {@value #SYSTEM_OPERATOR}。
 * 兜底存在的意义是异步/定时/无安全上下文的场景下写履历不至于抛异常。
 *
 * @author ruoyi
 */
public final class LabSecurityUtils
{
    /** 取不到任何用户时的兜底操作人。 */
    public static final String SYSTEM_OPERATOR = "system";

    private LabSecurityUtils()
    {
    }

    /**
     * 取操作人账号。
     *
     * @param preferred 调用方已知的账号，可为 null
     */
    public static String operatorName(String preferred)
    {
        if (StringUtils.isNotEmpty(preferred))
        {
            return preferred;
        }
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return SYSTEM_OPERATOR;
        }
    }
}
