package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabRoom;

/**
 * Laboratory room mapper.
 *
 * @author ruoyi
 */
public interface LabRoomMapper
{
    public LabRoom selectLabRoomByRoomId(Long roomId);

    public LabRoom selectLabRoomByRoomName(String roomName);

    public List<LabRoom> selectLabRoomList(LabRoom labRoom);

    public int insertLabRoom(LabRoom labRoom);

    public int updateLabRoom(LabRoom labRoom);

    public LabRoom checkRoomNoUnique(String roomNo);

    public int deleteLabRoomByRoomId(Long roomId);

    public int deleteLabRoomByRoomIds(Long[] roomIds);
}
