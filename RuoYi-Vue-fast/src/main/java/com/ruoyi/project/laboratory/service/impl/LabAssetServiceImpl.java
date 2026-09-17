package com.ruoyi.project.laboratory.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.laboratory.constant.LabConstants;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabAssetRecord;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabAssetRecordMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;
import com.ruoyi.project.laboratory.service.ILabAssetService;
import com.ruoyi.project.laboratory.service.ILabRoomService;
import com.ruoyi.project.laboratory.util.LabSecurityUtils;
import com.ruoyi.project.laboratory.util.LabStatusUtils;
import com.ruoyi.project.laboratory.util.QrCodeUtils;

/**
 * Laboratory asset service implementation.
 *
 * @author ruoyi
 */
@Service
public class LabAssetServiceImpl implements ILabAssetService
{
    @Autowired
    private LabAssetMapper labAssetMapper;

    @Autowired
    private LabAssetRecordMapper labAssetRecordMapper;

    @Autowired
    private LabRoomMapper labRoomMapper;

    @Autowired
    private ILabRoomService labRoomService;

    @Override
    public LabAsset selectLabAssetByAssetId(Long assetId)
    {
        return labAssetMapper.selectLabAssetByAssetId(assetId);
    }

    @Override
    public List<LabAsset> selectLabAssetList(LabAsset labAsset)
    {
        if (StringUtils.isEmpty(labAsset.getDelFlag()))
        {
            labAsset.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        }
        return labAssetMapper.selectLabAssetList(labAsset);
    }

    @Override
    public List<LabAsset> selectRepairableAssetList(LabAsset labAsset)
    {
        labAsset.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        labAsset.setStatus(LabConstants.ASSET_STATUS_NORMAL);
        return labAssetMapper.selectLabAssetList(labAsset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertLabAsset(LabAsset labAsset)
    {
        validateLabAsset(labAsset);
        if (StringUtils.isEmpty(labAsset.getStatus()))
        {
            labAsset.setStatus(LabConstants.ASSET_STATUS_NORMAL);
        }
        labAsset.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        int rows = labAssetMapper.insertLabAsset(labAsset);
        if (rows > 0)
        {
            insertAssetRecord(labAsset.getAssetId(), "入库", null, labAsset.getAssetCode(),
                    LabSecurityUtils.operatorName(labAsset.getCreateBy()), "资产入库：" + labAsset.getAssetName());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLabAsset(LabAsset labAsset)
    {
        LabAsset oldAsset = labAsset.getAssetId() == null ? null : labAssetMapper.selectLabAssetByAssetId(labAsset.getAssetId());
        if (labAsset.getAssetCode() != null || labAsset.getAssetName() != null || labAsset.getRoomId() != null)
        {
            validateLabAsset(labAsset);
        }
        int rows = labAssetMapper.updateLabAsset(labAsset);
        if (rows > 0 && oldAsset != null)
        {
            recordAssetUpdate(oldAsset, labAsset);
        }
        return rows;
    }

    @Override
    public boolean checkAssetCodeUnique(LabAsset labAsset)
    {
        Long assetId = labAsset.getAssetId() == null ? -1L : labAsset.getAssetId();
        LabAsset info = labAssetMapper.checkAssetCodeUnique(labAsset.getAssetCode());
        return info == null || info.getAssetId().longValue() == assetId.longValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importLabAsset(List<LabAsset> assetList, String operName)
    {
        if (StringUtils.isNull(assetList) || assetList.size() == 0)
        {
            throw new ServiceException("导入资产数据不能为空");
        }
        int successNum = 0;
        int failureNum = 0;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder failureMsg = new StringBuilder();
        for (LabAsset asset : assetList)
        {
            try
            {
                resolveImportedRoom(asset);
                asset.setCreateBy(operName);
                insertLabAsset(asset);
                successNum++;
            }
            catch (Exception e)
            {
                failureNum++;
                failureMsg.append("<br/>").append(failureNum).append("、资产 ")
                        .append(displayAssetName(asset))
                        .append(" 导入失败：").append(e.getMessage());
            }
        }
        if (failureNum > 0)
        {
            failureMsg.insert(0, "很抱歉，导入完成但存在 " + failureNum + " 条失败数据：");
            return failureMsg.toString();
        }
        successMsg.insert(0, "恭喜，数据已全部导入成功！共 " + successNum + " 条。");
        return successMsg.toString();
    }

    @Override
    public Map<String, Object> buildAssetQrcode(Long assetId)
    {
        LabAsset asset = labAssetMapper.selectLabAssetByAssetId(assetId);
        if (asset == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        String content = "/laboratory/repair?assetId=" + asset.getAssetId();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("assetId", asset.getAssetId());
        data.put("assetCode", asset.getAssetCode());
        data.put("assetName", asset.getAssetName());
        data.put("content", content);
        data.put("svg", QrCodeUtils.generateSvg(content, asset.getAssetCode(), content));
        return data;
    }

    @Override
    public List<LabAssetRecord> selectLabAssetRecords(Long assetId)
    {
        if (labAssetMapper.selectLabAssetByAssetId(assetId) == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        return labAssetRecordMapper.selectLabAssetRecordList(assetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLabAssetByAssetId(Long assetId)
    {
        LabAsset labAsset = labAssetMapper.selectLabAssetByAssetId(assetId);
        checkAssetCanDelete(labAsset);
        int rows = labAssetMapper.deleteLabAssetByAssetId(assetId);
        if (rows > 0)
        {
            insertAssetRecord(assetId, "报废", labAsset.getAssetCode(), null, LabSecurityUtils.operatorName(null), "资产删除或报废");
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteLabAssetByAssetIds(Long[] assetIds)
    {
        for (Long assetId : assetIds)
        {
            checkAssetCanDelete(labAssetMapper.selectLabAssetByAssetId(assetId));
        }
        int rows = labAssetMapper.deleteLabAssetByAssetIds(assetIds);
        if (rows > 0)
        {
            for (Long assetId : assetIds)
            {
                insertAssetRecord(assetId, "报废", null, null, LabSecurityUtils.operatorName(null), "资产批量删除或报废");
            }
        }
        return rows;
    }

    private void validateLabAsset(LabAsset labAsset)
    {
        if (StringUtils.isEmpty(labAsset.getAssetCode()))
        {
            throw new ServiceException("资产编号不能为空");
        }
        if (StringUtils.isEmpty(labAsset.getAssetName()))
        {
            throw new ServiceException("资产名称不能为空");
        }
        if (labAsset.getRoomId() == null)
        {
            throw new ServiceException("所属实验室不能为空");
        }
        if (labRoomService.selectLabRoomByRoomId(labAsset.getRoomId()) == null)
        {
            throw new ServiceException("所属实验室不存在或已删除");
        }
        if (!checkAssetCodeUnique(labAsset))
        {
            throw new ServiceException("资产编号已存在");
        }
    }

    private void checkAssetCanDelete(LabAsset labAsset)
    {
        if (labAsset == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        if (LabConstants.ASSET_STATUS_REPAIRING.equals(labAsset.getStatus()))
        {
            throw new ServiceException("资产维修中，不能删除");
        }
    }

    private void resolveImportedRoom(LabAsset asset)
    {
        if (asset.getRoomId() == null && StringUtils.isNotEmpty(asset.getRoomName()))
        {
            LabRoom room = labRoomMapper.selectLabRoomByRoomName(asset.getRoomName());
            if (room != null)
            {
                asset.setRoomId(room.getRoomId());
            }
        }
    }

    private String displayAssetName(LabAsset asset)
    {
        if (StringUtils.isNotEmpty(asset.getAssetCode()))
        {
            return asset.getAssetCode();
        }
        if (StringUtils.isNotEmpty(asset.getAssetName()))
        {
            return asset.getAssetName();
        }
        return "未命名";
    }

    private void recordAssetUpdate(LabAsset oldAsset, LabAsset newAsset)
    {
        String operator = LabSecurityUtils.operatorName(newAsset.getUpdateBy());
        if (newAsset.getRoomId() != null && !Objects.equals(oldAsset.getRoomId(), newAsset.getRoomId()))
        {
            LabRoom newRoom = labRoomService.selectLabRoomByRoomId(newAsset.getRoomId());
            insertAssetRecord(oldAsset.getAssetId(), "调拨", oldAsset.getRoomName(),
                    newRoom == null ? String.valueOf(newAsset.getRoomId()) : newRoom.getRoomName(), operator, "资产所属实验室调整");
        }
        if (StringUtils.isNotEmpty(newAsset.getStatus()) && !Objects.equals(oldAsset.getStatus(), newAsset.getStatus()))
        {
            insertAssetRecord(oldAsset.getAssetId(), LabStatusUtils.assetRecordType(newAsset.getStatus()),
                    LabStatusUtils.assetStatusLabel(oldAsset.getStatus()), LabStatusUtils.assetStatusLabel(newAsset.getStatus()), operator, "资产状态变更");
        }
        if (hasBaseInfoChanged(oldAsset, newAsset))
        {
            insertAssetRecord(oldAsset.getAssetId(), "资料修改", null, null, operator, "资产基础资料更新");
        }
    }

    private boolean hasBaseInfoChanged(LabAsset oldAsset, LabAsset newAsset)
    {
        return changed(oldAsset.getAssetCode(), newAsset.getAssetCode())
                || changed(oldAsset.getAssetName(), newAsset.getAssetName())
                || changed(oldAsset.getAssetType(), newAsset.getAssetType())
                || changed(oldAsset.getModel(), newAsset.getModel())
                || changed(oldAsset.getPrice(), newAsset.getPrice())
                || changed(formatDate(oldAsset.getPurchaseDate()), formatDate(newAsset.getPurchaseDate()));
    }

    private boolean changed(Object oldValue, Object newValue)
    {
        return newValue != null && !Objects.equals(oldValue, newValue);
    }

    private void insertAssetRecord(Long assetId, String recordType, String fromValue, String toValue,
            String operatorName, String content)
    {
        LabAssetRecord record = new LabAssetRecord();
        record.setAssetId(assetId);
        record.setRecordType(recordType);
        record.setFromValue(fromValue);
        record.setToValue(toValue);
        record.setOperatorName(operatorName);
        record.setRecordContent(content);
        record.setCreateBy(operatorName);
        labAssetRecordMapper.insertLabAssetRecord(record);
    }

    private String formatDate(Date date)
    {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

}
