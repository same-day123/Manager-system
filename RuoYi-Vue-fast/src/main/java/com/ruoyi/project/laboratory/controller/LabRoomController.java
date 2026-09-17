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
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.service.ILabRoomService;

/**
 * Laboratory room controller.
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/laboratory/room")
public class LabRoomController extends BaseController
{
    @Autowired
    private ILabRoomService labRoomService;

    @PreAuthorize("@ss.hasPermi('laboratory:room:list')")
    @GetMapping("/list")
    public TableDataInfo list(LabRoom labRoom)
    {
        startPage();
        List<LabRoom> list = labRoomService.selectLabRoomList(labRoom);
        return getDataTable(list);
    }

    @Log(title = "实验室房间", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('laboratory:room:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, LabRoom labRoom)
    {
        List<LabRoom> list = labRoomService.selectLabRoomList(labRoom);
        ExcelUtil<LabRoom> util = new ExcelUtil<LabRoom>(LabRoom.class);
        util.exportExcel(response, list, "实验室房间");
    }

    @PreAuthorize("@ss.hasPermi('laboratory:room:query')")
    @GetMapping(value = "/{roomId}")
    public AjaxResult getInfo(@PathVariable Long roomId)
    {
        return success(labRoomService.selectLabRoomByRoomId(roomId));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:room:add')")
    @Log(title = "实验室房间", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody LabRoom labRoom)
    {
        labRoom.setCreateBy(getUsername());
        return toAjax(labRoomService.insertLabRoom(labRoom));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:room:edit')")
    @Log(title = "实验室房间", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody LabRoom labRoom)
    {
        labRoom.setUpdateBy(getUsername());
        return toAjax(labRoomService.updateLabRoom(labRoom));
    }

    @PreAuthorize("@ss.hasPermi('laboratory:room:remove')")
    @Log(title = "实验室房间", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roomIds}")
    public AjaxResult remove(@PathVariable Long[] roomIds)
    {
        return toAjax(labRoomService.deleteLabRoomByRoomIds(roomIds));
    }
}
