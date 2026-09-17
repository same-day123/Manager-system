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
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.service.ILabAssetService;

/**
 * Laboratory asset controller.
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/laboratory/asset")
public class LabAssetController extends BaseController
{
    @Autowired
    private ILabAssetService labAssetService;

    @PreAuthorize("@ss.hasPermi('laboratory:asset:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabAsset labAsset)
    {
        startPage();
        List<LabAsset> list = labAssetService.selectLabAssetList(labAsset);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:query')")
    @GetMapping("/repairable")
    public TableDataInfo repairable(LabAsset labAsset)
    {
        startPage();
        List<LabAsset> list = labAssetService.selectRepairableAssetList(labAsset);
        return getDataTable(list);
    }

    @Log(title = "资产台账", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('laboratory:asset:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, LabAsset labAsset)
    {
        List<LabAsset> list = labAssetService.selectLabAssetList(labAsset);
        ExcelUtil<LabAsset> util = new ExcelUtil<LabAsset>(LabAsset.class);
        util.exportExcel(response, list, "asset");
    }

    @Log(title = "资产台账", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('laboratory:asset:import')")
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file) throws Exception
    {
        ExcelUtil<LabAsset> util = new ExcelUtil<LabAsset>(LabAsset.class);
        List<LabAsset> assetList = util.importExcel(file.getInputStream());
        String message = labAssetService.importLabAsset(assetList, getUsername());
        return success(message);
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:import')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<LabAsset> util = new ExcelUtil<LabAsset>(LabAsset.class);
        util.importTemplateExcel(response, "asset");
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:query')")
    @GetMapping(value = "/{assetId}")
    public AjaxResult getInfo(@PathVariable Long assetId)
    {
        return success(labAssetService.selectLabAssetByAssetId(assetId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:query')")
    @GetMapping(value = "/{assetId}/qrcode")
    public AjaxResult qrcode(@PathVariable Long assetId)
    {
        return success(labAssetService.buildAssetQrcode(assetId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:query')")
    @GetMapping(value = "/{assetId}/records")
    public AjaxResult records(@PathVariable Long assetId)
    {
        return success(labAssetService.selectLabAssetRecords(assetId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:add')")
    @Log(title = "资产台账", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody LabAsset labAsset)
    {
        labAsset.setCreateBy(getUsername());
        return toAjax(labAssetService.insertLabAsset(labAsset));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:edit')")
    @Log(title = "资产台账", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody LabAsset labAsset)
    {
        labAsset.setUpdateBy(getUsername());
        return toAjax(labAssetService.updateLabAsset(labAsset));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:asset:remove')")
    @Log(title = "资产台账", businessType = BusinessType.DELETE)
    @DeleteMapping("/{assetIds}")
    public AjaxResult remove(@PathVariable Long[] assetIds)
    {
        return toAjax(labAssetService.deleteLabAssetByAssetIds(assetIds));
    }
}
