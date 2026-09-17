package com.ruoyi.project.laboratory.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.laboratory.constant.LabConstants;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;
import com.ruoyi.project.laboratory.service.ILabRoomService;

/**
 * Laboratory room service implementation.
 *
 * @author ruoyi
 */
@Service
public class LabRoomServiceImpl implements ILabRoomService
{
    @Autowired
    private LabRoomMapper labRoomMapper;

    @Autowired
    private LabAssetMapper labAssetMapper;

    @Override
    public LabRoom selectLabRoomByRoomId(Long roomId)
    {
        return labRoomMapper.selectLabRoomByRoomId(roomId);
    }

    @Override
    public List<LabRoom> selectLabRoomList(LabRoom labRoom)
    {
        if (StringUtils.isEmpty(labRoom.getDelFlag()))
        {
            labRoom.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        }
        return labRoomMapper.selectLabRoomList(labRoom);
    }

    @Override
    public int insertLabRoom(LabRoom labRoom)
    {
        validateLabRoom(labRoom);
        if (StringUtils.isEmpty(labRoom.getStatus()))
        {
            labRoom.setStatus(LabConstants.ROOM_STATUS_NORMAL);
        }
        labRoom.setDelFlag(LabConstants.DEL_FLAG_NORMAL);
        return labRoomMapper.insertLabRoom(labRoom);
    }

    @Override
    public int updateLabRoom(LabRoom labRoom)
    {
        // 与 LabAssetServiceImpl.updateLabAsset 对齐：只有真的改了基础字段才做完整校验，
        // 允许内部调用方（如只改状态、只改备注）做部分更新而不被"编号/名称不能为空"卡住。
        if (labRoom.getRoomNo() != null || labRoom.getRoomName() != null)
        {
            validateLabRoom(labRoom);
        }
        return labRoomMapper.updateLabRoom(labRoom);
    }

    @Override
    public boolean checkRoomNoUnique(LabRoom labRoom)
    {
        Long roomId = labRoom.getRoomId() == null ? -1L : labRoom.getRoomId();
        LabRoom info = labRoomMapper.checkRoomNoUnique(labRoom.getRoomNo());
        return info == null || info.getRoomId().longValue() == roomId.longValue();
    }

    @Override
    public int deleteLabRoomByRoomId(Long roomId)
    {
        checkRoomCanDelete(roomId);
        LabRoom labRoom = new LabRoom();
        labRoom.setRoomId(roomId);
        labRoom.setDelFlag(LabConstants.DEL_FLAG_DELETED);
        return labRoomMapper.updateLabRoom(labRoom);
    }

    @Override
    public int deleteLabRoomByRoomIds(Long[] roomIds)
    {
        for (Long roomId : roomIds)
        {
            checkRoomCanDelete(roomId);
        }
        return labRoomMapper.deleteLabRoomByRoomIds(roomIds);
    }

    private void validateLabRoom(LabRoom labRoom)
    {
        if (StringUtils.isEmpty(labRoom.getRoomNo()))
        {
            throw new ServiceException("实验室编号不能为空");
        }
        if (StringUtils.isEmpty(labRoom.getRoomName()))
        {
            throw new ServiceException("实验室名称不能为空");
        }
        if (!checkRoomNoUnique(labRoom))
        {
            throw new ServiceException("实验室编号已存在");
        }
    }

    private void checkRoomCanDelete(Long roomId)
    {
        if (labAssetMapper.countLabAssetByRoomId(roomId) > 0)
        {
            throw new ServiceException("实验室下存在资产，不能删除");
        }
    }
}
