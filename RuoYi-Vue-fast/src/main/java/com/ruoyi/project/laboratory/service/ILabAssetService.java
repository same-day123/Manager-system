package com.ruoyi.project.laboratory.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabAssetRecord;

/**
 * Laboratory asset service.
 *
 * @author ruoyi
 */
public interface ILabAssetService
{
    public LabAsset selectLabAssetByAssetId(Long assetId);

    public List<LabAsset> selectLabAssetList(LabAsset labAsset);

    public List<LabAsset> selectRepairableAssetList(LabAsset labAsset);

    public int insertLabAsset(LabAsset labAsset);

    public int updateLabAsset(LabAsset labAsset);

    public boolean checkAssetCodeUnique(LabAsset labAsset);

    public String importLabAsset(List<LabAsset> assetList, String operName);

    public Map<String, Object> buildAssetQrcode(Long assetId);

    public List<LabAssetRecord> selectLabAssetRecords(Long assetId);

    public int deleteLabAssetByAssetId(Long assetId);

    public int deleteLabAssetByAssetIds(Long[] assetIds);
}
