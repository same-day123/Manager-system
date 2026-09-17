package com.ruoyi.project.laboratory.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.laboratory.service.ILabDashboardService;

/**
 * Laboratory dashboard controller.
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/laboratory/dashboard")
public class LabDashboardController extends BaseController
{
    @Autowired
    private ILabDashboardService labDashboardService;

    @PreAuthorize("@ss.hasPermi('laboratory:dashboard:view')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(labDashboardService.selectSummary());
    }

    @PreAuthorize("@ss.hasPermi('laboratory:dashboard:view')")
    @GetMapping("/charts")
    public AjaxResult charts()
    {
        return success(labDashboardService.selectCharts());
    }
}
