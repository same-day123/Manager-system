package com.ruoyi.project.laboratory.service;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabRoom;

/**
 * Laboratory room service.
 *
 * @author ruoyi
 */
public interface ILabRoomService
{
    public LabRoom selectLabRoomByRoomId(Long roomId);

    public List<LabRoom> selectLabRoomList(LabRoom labRoom);

    public int insertLabRoom(LabRoom labRoom);

    public int updateLabRoom(LabRoom labRoom);

    public boolean checkRoomNoUnique(LabRoom labRoom);

    public int deleteLabRoomByRoomId(Long roomId);

    public int deleteLabRoomByRoomIds(Long[] roomIds);
}
