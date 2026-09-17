package com.ruoyi.project.laboratory.domain;

import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * Asset lifecycle record for inventory, transfer, status changes and disposal.
 *
 * @author ruoyi
 */
public class LabAssetRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long assetId;

    private String recordType;

    private String fromValue;

    private String toValue;

    private String operatorName;

    private String recordContent;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getAssetId()
    {
        return assetId;
    }

    public void setAssetId(Long assetId)
    {
        this.assetId = assetId;
    }

    public String getRecordType()
    {
        return recordType;
    }

    public void setRecordType(String recordType)
    {
        this.recordType = recordType;
    }

    public String getFromValue()
    {
        return fromValue;
    }

    public void setFromValue(String fromValue)
    {
        this.fromValue = fromValue;
    }

    public String getToValue()
    {
        return toValue;
    }

    public void setToValue(String toValue)
    {
        this.toValue = toValue;
    }

    public String getOperatorName()
    {
        return operatorName;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public String getRecordContent()
    {
        return recordContent;
    }

    public void setRecordContent(String recordContent)
    {
        this.recordContent = recordContent;
    }
}
