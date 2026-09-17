package com.ruoyi.project.laboratory.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.framework.aspectj.lang.annotation.Excel;
import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * Laboratory repair domain object lab_repair.
 *
 * @author ruoyi
 */
public class LabRepair extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long repairId;

    @Excel(name = "报修单号")
    private String repairCode;

    private Long assetId;

    @Excel(name = "资产名称")
    private String assetName;

    @Excel(name = "资产编号")
    private String assetCode;

    @Excel(name = "所属实验室")
    private String roomName;

    @Excel(name = "故障描述")
    private String faultDescription;

    @Excel(name = "故障等级", dictType = "lab_fault_level")
    private String faultLevel;

    private Long applicantId;

    @Excel(name = "申请人")
    private String applicantName;

    @Excel(name = "联系电话")
    private String applicantPhone;

    private Long repairUserId;

    @Excel(name = "维修人员")
    private String repairUserName;

    @Excel(name = "维修费用")
    private BigDecimal repairCost;

    @Excel(name = "完成时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    @Excel(name = "状态", dictType = "lab_repair_status")
    private String status;

    @Excel(name = "故障图片")
    private String attachmentUrls;

    @Excel(name = "维修评分")
    private Integer rating;

    @Excel(name = "维修评价")
    private String evaluationContent;

    @Excel(name = "评价时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date evaluationTime;

    private String delFlag;

    public Long getRepairId()
    {
        return repairId;
    }

    public void setRepairId(Long repairId)
    {
        this.repairId = repairId;
    }

    public String getRepairCode()
    {
        return repairCode;
    }

    public void setRepairCode(String repairCode)
    {
        this.repairCode = repairCode;
    }

    public Long getAssetId()
    {
        return assetId;
    }

    public void setAssetId(Long assetId)
    {
        this.assetId = assetId;
    }

    public String getAssetName()
    {
        return assetName;
    }

    public void setAssetName(String assetName)
    {
        this.assetName = assetName;
    }

    public String getAssetCode()
    {
        return assetCode;
    }

    public void setAssetCode(String assetCode)
    {
        this.assetCode = assetCode;
    }

    public String getRoomName()
    {
        return roomName;
    }

    public void setRoomName(String roomName)
    {
        this.roomName = roomName;
    }

    public String getFaultDescription()
    {
        return faultDescription;
    }

    public void setFaultDescription(String faultDescription)
    {
        this.faultDescription = faultDescription;
    }

    public String getFaultLevel()
    {
        return faultLevel;
    }

    public void setFaultLevel(String faultLevel)
    {
        this.faultLevel = faultLevel;
    }

    public Long getApplicantId()
    {
        return applicantId;
    }

    public void setApplicantId(Long applicantId)
    {
        this.applicantId = applicantId;
    }

    public String getApplicantName()
    {
        return applicantName;
    }

    public void setApplicantName(String applicantName)
    {
        this.applicantName = applicantName;
    }

    public String getApplicantPhone()
    {
        return applicantPhone;
    }

    public void setApplicantPhone(String applicantPhone)
    {
        this.applicantPhone = applicantPhone;
    }

    public Long getRepairUserId()
    {
        return repairUserId;
    }

    public void setRepairUserId(Long repairUserId)
    {
        this.repairUserId = repairUserId;
    }

    public String getRepairUserName()
    {
        return repairUserName;
    }

    public void setRepairUserName(String repairUserName)
    {
        this.repairUserName = repairUserName;
    }

    public BigDecimal getRepairCost()
    {
        return repairCost;
    }

    public void setRepairCost(BigDecimal repairCost)
    {
        this.repairCost = repairCost;
    }

    public Date getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(Date finishTime)
    {
        this.finishTime = finishTime;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getAttachmentUrls()
    {
        return attachmentUrls;
    }

    public void setAttachmentUrls(String attachmentUrls)
    {
        this.attachmentUrls = attachmentUrls;
    }

    public Integer getRating()
    {
        return rating;
    }

    public void setRating(Integer rating)
    {
        this.rating = rating;
    }

    public String getEvaluationContent()
    {
        return evaluationContent;
    }

    public void setEvaluationContent(String evaluationContent)
    {
        this.evaluationContent = evaluationContent;
    }

    public Date getEvaluationTime()
    {
        return evaluationTime;
    }

    public void setEvaluationTime(Date evaluationTime)
    {
        this.evaluationTime = evaluationTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
