package com.ruoyi.project.laboratory.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.service.ILabRepairService;

/**
 * Laboratory repair controller.
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/laboratory/repair")
public class LabRepairController extends BaseController
{
    @Autowired
    private ILabRepairService labRepairService;

    @PreAuthorize("@ss.hasPermi('laboratory:repair:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabRepair labRepair)
    {
        startPage();
        List<LabRepair> list = labRepairService.selectLabRepairList(labRepair);
        return getDataTable(list);
    }

    @Log(title = "设备报修", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('laboratory:repair:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, LabRepair labRepair)
    {
        List<LabRepair> list = labRepairService.selectLabRepairList(labRepair);
        ExcelUtil<LabRepair> util = new ExcelUtil<LabRepair>(LabRepair.class);
        util.exportExcel(response, list, "repair");
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:query')")
    @GetMapping(value = "/{repairId}")
    public AjaxResult getInfo(@PathVariable Long repairId)
    {
        return success(labRepairService.selectLabRepairByRepairId(repairId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:query')")
    @GetMapping(value = "/{repairId}/records")
    public AjaxResult records(@PathVariable Long repairId)
    {
        return success(labRepairService.selectLabRepairRecords(repairId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:add')")
    @Log(title = "设备报修", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody LabRepair labRepair)
    {
        labRepair.setCreateBy(getUsername());
        return toAjax(labRepairService.insertLabRepair(labRepair));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:edit')")
    @Log(title = "设备报修", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody LabRepair labRepair)
    {
        labRepair.setUpdateBy(getUsername());
        return toAjax(labRepairService.updateLabRepair(labRepair));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:audit')")
    @Log(title = "报修处理", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    public AjaxResult audit(@Validated @RequestBody LabRepair labRepair)
    {
        labRepair.setUpdateBy(getUsername());
        return toAjax(labRepairService.auditLabRepair(labRepair));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:evaluate')")
    @Log(title = "维修评价", businessType = BusinessType.UPDATE)
    @PutMapping("/evaluate")
    public AjaxResult evaluate(@Validated @RequestBody LabRepair labRepair)
    {
        labRepair.setUpdateBy(getUsername());
        return toAjax(labRepairService.evaluateLabRepair(labRepair));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:repair:remove')")
    @Log(title = "设备报修", businessType = BusinessType.DELETE)
    @DeleteMapping("/{repairIds}")
    public AjaxResult remove(@PathVariable Long[] repairIds)
    {
        return toAjax(labRepairService.deleteLabRepairByRepairIds(repairIds));
    }
}
