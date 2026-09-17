package com.ruoyi.project.laboratory.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.laboratory.constant.LabConstants;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;
import com.ruoyi.project.laboratory.mapper.LabRepairMapper;
import com.ruoyi.project.laboratory.mapper.LabRepairRecordMapper;
import com.ruoyi.project.laboratory.service.ILabAssetService;
import com.ruoyi.project.laboratory.service.ILabRepairService;
import com.ruoyi.project.laboratory.util.LabRoleUtils;
import com.ruoyi.project.laboratory.util.LabSecurityUtils;
import com.ruoyi.project.laboratory.util.LabStatusUtils;
import com.ruoyi.project.system.domain.SysUser;

/**
 * Laboratory repair service implementation.
 *
 * @author ruoyi
 */
@Service
public class LabRepairServiceImpl implements ILabRepairService
{
    @Autowired
    private LabRepairMapper labRepairMapper;

    @Autowired
    private LabRepairRecordMapper labRepairRecordMapper;

    @Autowired
    private ILabAssetService labAssetService;

    @Override
    public LabRepair selectLabRepairByRepairId(Long repairId)
    {
        LabRepair labRepair = labRepairMapper.selectLabRepairByRepairId(repairId);
        checkCanAccess(labRepair);
        return labRepair;
    }

    @Override
    public List<LabRepair> selectLabRepairList(LabRepair labRepair)
    {
        // 列表可见范围与详情可见范围必须同源，统一走 LabRoleUtils，避免出现"列表看不到却点得动"。
        if (!LabRoleUtils.canViewAll())
        {
            labRepair.setApplicantId(SecurityUtils.getUserId());
        }
        if (StringUtils.isEmpty(labRepair.getDelFlag()))
        {
            labRepair.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        }
        return labRepairMapper.selectLabRepairList(labRepair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertLabRepair(LabRepair labRepair)
    {
        validateNewRepair(labRepair);
        SysUser user = SecurityUtils.getLoginUser().getUser();
        labRepair.setRepairCode(generateRepairCode());
        labRepair.setApplicantId(user.getUserId());
        labRepair.setApplicantName(user.getNickName());
        labRepair.setApplicantPhone(user.getPhonenumber());
        labRepair.setStatus(LabConstants.REPAIR_STATUS_PENDING_REVIEW);
        labRepair.setDelFlag(LabConstants.DEL_FLAG_NORMAL);

        int rows = labRepairMapper.insertLabRepair(labRepair);
        if (rows > 0)
        {
            updateAssetStatus(labRepair.getAssetId(), LabConstants.ASSET_STATUS_REPAIRING);
            insertRepairRecord(labRepair.getRepairId(), "提交报修", null, LabConstants.REPAIR_STATUS_PENDING_REVIEW,
                    "故障等级：" + statusOrText(labRepair.getFaultLevel()) + "；" + labRepair.getFaultDescription());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLabRepair(LabRepair labRepair)
    {
        LabRepair oldRepair = labRepairMapper.selectLabRepairByRepairId(labRepair.getRepairId());
        checkCanAccess(oldRepair);
        String requestedStatus = labRepair.getStatus();
        boolean manager = canManageAll();
        if (!manager)
        {
            // checkCanAccess 只保证"可见"，这里补"归属"校验：非处理角色只能改本人提交的单据。
            if (!SecurityUtils.getUserId().equals(oldRepair.getApplicantId()))
            {
                throw new ServiceException("只能修改本人提交的报修单");
            }
            if (!LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(oldRepair.getStatus()))
            {
                throw new ServiceException("只能修改待审核的本人报修单");
            }
            // 非处理角色不得更换报修资产，否则任何普通用户都能把任意"正常"资产置为"维修中"，
            // 属于跨资产的状态篡改。资产只在新增报修时选定一次。
            if (labRepair.getAssetId() != null && !labRepair.getAssetId().equals(oldRepair.getAssetId()))
            {
                throw new ServiceException("只能修改故障信息，不能更换报修资产");
            }
            labRepair.setAssetId(null);
            labRepair.setStatus(null);
            labRepair.setRepairUserId(null);
            labRepair.setRepairUserName(null);
            labRepair.setRepairCost(null);
            labRepair.setFinishTime(null);
            labRepair.setApplicantId(null);
            labRepair.setApplicantName(null);
            labRepair.setApplicantPhone(null);
        }
        else
        {
            validateStatusChange(oldRepair, labRepair);
            labRepair.setAssetId(oldRepair.getAssetId());
            labRepair.setApplicantId(null);
            labRepair.setApplicantName(null);
            labRepair.setApplicantPhone(null);
        }
        labRepair.setRating(null);
        labRepair.setEvaluationContent(null);
        labRepair.setEvaluationTime(null);

        if (LabConstants.REPAIR_STATUS_FINISHED.equals(labRepair.getStatus()))
        {
            labRepair.setFinishTime(DateUtils.getNowDate());
        }

        int rows = labRepairMapper.updateLabRepair(labRepair);
        if (rows > 0)
        {
            if (StringUtils.isNotEmpty(requestedStatus) && !requestedStatus.equals(oldRepair.getStatus()))
            {
                insertRepairRecord(labRepair.getRepairId(), "状态流转", oldRepair.getStatus(), requestedStatus,
                        "报修状态由“" + LabStatusUtils.repairStatusLabel(oldRepair.getStatus()) + "”变更为“"
                                + LabStatusUtils.repairStatusLabel(requestedStatus) + "”");
            }
            else
            {
                insertRepairRecord(labRepair.getRepairId(), manager ? "维修信息更新" : "报修信息更新",
                        oldRepair.getStatus(), oldRepair.getStatus(), "报修单信息已更新");
            }
        }
        if (rows > 0 && (LabConstants.REPAIR_STATUS_FINISHED.equals(labRepair.getStatus())
                || LabConstants.REPAIR_STATUS_REJECTED.equals(labRepair.getStatus())))
        {
            updateAssetStatus(labRepair.getAssetId(), LabConstants.ASSET_STATUS_NORMAL);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditLabRepair(LabRepair labRepair)
    {
        if (!canManageAll())
        {
            throw new ServiceException("没有权限处理报修单");
        }
        return updateLabRepair(labRepair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int evaluateLabRepair(LabRepair labRepair)
    {
        LabRepair oldRepair = labRepairMapper.selectLabRepairByRepairId(labRepair.getRepairId());
        checkCanAccess(oldRepair);
        if (!SecurityUtils.getUserId().equals(oldRepair.getApplicantId()))
        {
            throw new ServiceException("只能评价本人提交的报修单");
        }
        if (!LabConstants.REPAIR_STATUS_FINISHED.equals(oldRepair.getStatus()))
        {
            throw new ServiceException("只能评价已完成的报修单");
        }
        if (labRepair.getRating() == null || labRepair.getRating() < 1 || labRepair.getRating() > 5)
        {
            throw new ServiceException("维修评分必须在1到5之间");
        }
        LabRepair update = new LabRepair();
        update.setRepairId(labRepair.getRepairId());
        update.setRating(labRepair.getRating());
        update.setEvaluationContent(labRepair.getEvaluationContent());
        update.setEvaluationTime(DateUtils.getNowDate());
        update.setUpdateBy(LabSecurityUtils.operatorName(null));
        int rows = labRepairMapper.updateLabRepair(update);
        if (rows > 0)
        {
            insertRepairRecord(labRepair.getRepairId(), "维修评价", oldRepair.getStatus(), oldRepair.getStatus(),
                    "评分：" + labRepair.getRating() + "；" + statusOrText(labRepair.getEvaluationContent()));
        }
        return rows;
    }

    @Override
    public List<LabRepairRecord> selectLabRepairRecords(Long repairId)
    {
        checkCanAccess(labRepairMapper.selectLabRepairByRepairId(repairId));
        return labRepairRecordMapper.selectLabRepairRecordList(repairId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLabRepairByRepairId(Long repairId)
    {
        LabRepair labRepair = labRepairMapper.selectLabRepairByRepairId(repairId);
        checkCanDelete(labRepair);
        int rows = labRepairMapper.deleteLabRepairByRepairId(repairId);
        restoreAssetWhenPendingDeleted(labRepair, rows);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLabRepairByRepairIds(Long[] repairIds)
    {
        List<LabRepair> repairs = new java.util.ArrayList<LabRepair>();
        for (Long repairId : repairIds)
        {
            LabRepair labRepair = labRepairMapper.selectLabRepairByRepairId(repairId);
            checkCanDelete(labRepair);
            repairs.add(labRepair);
        }
        int rows = labRepairMapper.deleteLabRepairByRepairIds(repairIds);
        if (rows > 0)
        {
            for (LabRepair labRepair : repairs)
            {
                restoreAssetWhenPendingDeleted(labRepair, 1);
            }
        }
        return rows;
    }

    private String generateRepairCode()
    {
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int random = new Random().nextInt(900) + 100;
        return "BX" + time + random;
    }

    private void updateAssetStatus(Long assetId, String status)
    {
        LabAsset labAsset = new LabAsset();
        labAsset.setAssetId(assetId);
        labAsset.setStatus(status);
        labAsset.setUpdateBy(LabSecurityUtils.operatorName(null));
        labAssetService.updateLabAsset(labAsset);
    }

    private void restoreAssetWhenPendingDeleted(LabRepair labRepair, int rows)
    {
        if (rows > 0 && LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(labRepair.getStatus()))
        {
            updateAssetStatus(labRepair.getAssetId(), LabConstants.ASSET_STATUS_NORMAL);
        }
    }

    private void validateNewRepair(LabRepair labRepair)
    {
        if (labRepair.getAssetId() == null)
        {
            throw new ServiceException("请选择报修资产");
        }
        if (StringUtils.isEmpty(labRepair.getFaultDescription()))
        {
            throw new ServiceException("故障描述不能为空");
        }
        LabAsset asset = labAssetService.selectLabAssetByAssetId(labRepair.getAssetId());
        if (asset == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        if (!LabConstants.ASSET_STATUS_NORMAL.equals(asset.getStatus()))
        {
            throw new ServiceException("该资产当前不可报修");
        }
    }

    private void validateStatusChange(LabRepair oldRepair, LabRepair labRepair)
    {
        String newStatus = labRepair.getStatus();
        if (StringUtils.isEmpty(newStatus) || newStatus.equals(oldRepair.getStatus()))
        {
            return;
        }
        boolean allowed = false;
        if (LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(oldRepair.getStatus()))
        {
            allowed = LabConstants.REPAIR_STATUS_PENDING_REPAIR.equals(newStatus) || LabConstants.REPAIR_STATUS_REJECTED.equals(newStatus);
        }
        else if (LabConstants.REPAIR_STATUS_PENDING_REPAIR.equals(oldRepair.getStatus()))
        {
            allowed = LabConstants.REPAIR_STATUS_REPAIRING.equals(newStatus);
        }
        else if (LabConstants.REPAIR_STATUS_REPAIRING.equals(oldRepair.getStatus()))
        {
            allowed = LabConstants.REPAIR_STATUS_FINISHED.equals(newStatus);
        }
        if (!allowed)
        {
            throw new ServiceException("非法的报修状态流转");
        }
        if (labRepair.getAssetId() == null)
        {
            labRepair.setAssetId(oldRepair.getAssetId());
        }
    }

    /**
     * 可见性校验。与列表过滤同源，保证"列表能看到"等价于"详情能看到"。
     */
    private void checkCanAccess(LabRepair labRepair)
    {
        if (labRepair == null)
        {
            throw new ServiceException("报修单不存在或已删除");
        }
        if (!LabRoleUtils.canViewAll() && !SecurityUtils.getUserId().equals(labRepair.getApplicantId()))
        {
            throw new ServiceException("没有权限访问该报修单");
        }
    }

    private void checkCanDelete(LabRepair labRepair)
    {
        checkCanAccess(labRepair);
        if (!canManageAll())
        {
            // 非处理角色只能删本人提交的待审核单，归属校验不能省。
            if (!SecurityUtils.getUserId().equals(labRepair.getApplicantId()))
            {
                throw new ServiceException("只能删除本人提交的报修单");
            }
            if (!LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(labRepair.getStatus()))
            {
                throw new ServiceException("只能删除待审核的本人报修单");
            }
            return;
        }
        if (!LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(labRepair.getStatus())
                && !LabConstants.REPAIR_STATUS_REJECTED.equals(labRepair.getStatus()))
        {
            throw new ServiceException("只能删除待审核或已拒绝的报修单");
        }
    }

    /**
     * 是否具备报修处理权。统一由 {@link LabRoleUtils} 判定，不再在本类内维护角色白名单。
     */
    private boolean canManageAll()
    {
        return LabRoleUtils.canHandleRepair();
    }

    private void insertRepairRecord(Long repairId, String actionName, String fromStatus, String toStatus, String content)
    {
        LabRepairRecord record = new LabRepairRecord();
        record.setRepairId(repairId);
        record.setActionName(actionName);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setOperatorName(LabSecurityUtils.operatorName(null));
        record.setRecordContent(content);
        record.setCreateBy(record.getOperatorName());
        labRepairRecordMapper.insertLabRepairRecord(record);
    }

    private String statusOrText(String text)
    {
        return StringUtils.isEmpty(text) ? "-" : text;
    }
}
