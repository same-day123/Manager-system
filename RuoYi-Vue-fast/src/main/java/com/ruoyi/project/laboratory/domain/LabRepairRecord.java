package com.ruoyi.project.laboratory.domain;

import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * Repair processing record for the laboratory repair timeline.
 *
 * @author ruoyi
 */
public class LabRepairRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long repairId;

    private String actionName;

    private String fromStatus;

    private String toStatus;

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

    public Long getRepairId()
    {
        return repairId;
    }

    public void setRepairId(Long repairId)
    {
        this.repairId = repairId;
    }

    public String getActionName()
    {
        return actionName;
    }

    public void setActionName(String actionName)
    {
        this.actionName = actionName;
    }

    public String getFromStatus()
    {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus)
    {
        this.fromStatus = fromStatus;
    }

    public String getToStatus()
    {
        return toStatus;
    }

    public void setToStatus(String toStatus)
    {
        this.toStatus = toStatus;
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
