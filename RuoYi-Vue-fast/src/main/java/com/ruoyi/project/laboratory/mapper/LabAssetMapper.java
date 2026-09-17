package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabAsset;

/**
 * Laboratory asset mapper.
 *
 * @author ruoyi
 */
public interface LabAssetMapper
{
    public LabAsset selectLabAssetByAssetId(Long assetId);

    public List<LabAsset> selectLabAssetList(LabAsset labAsset);

    public LabAsset checkAssetCodeUnique(String assetCode);

    public int countLabAssetByRoomId(Long roomId);

    public int insertLabAsset(LabAsset labAsset);

    public int updateLabAsset(LabAsset labAsset);

    public int deleteLabAssetByAssetId(Long assetId);

    public int deleteLabAssetByAssetIds(Long[] assetIds);
}
