package com.ruoyi.project.laboratory.service;

import java.util.Map;

/**
 * Laboratory dashboard service.
 *
 * @author ruoyi
 */
public interface ILabDashboardService
{
    public Map<String, Object> selectSummary();

    public Map<String, Object> selectCharts();
}
