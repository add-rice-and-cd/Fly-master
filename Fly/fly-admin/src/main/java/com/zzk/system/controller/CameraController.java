package com.zzk.system.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zzk.common.annotation.Log;
import com.zzk.common.core.controller.BaseController;
import com.zzk.common.core.domain.AjaxResult;
import com.zzk.common.enums.BusinessType;
import com.zzk.system.domain.Camera;
import com.zzk.system.service.ICameraService;
import com.zzk.common.utils.poi.ExcelUtil;
import com.zzk.common.core.page.TableDataInfo;

/**
 * 摄像头信息Controller
 * 
 * @author ruoyi
 * @date 2025-08-07
 */
@RestController
@RequestMapping("/Camera/camera")
public class CameraController extends BaseController
{
    @Autowired
    private ICameraService cameraService;

    /**
     * 查询摄像头信息列表
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:list')")
    @GetMapping("/list")
    public TableDataInfo list(Camera camera)
    {
        startPage();
        List<Camera> list = cameraService.selectCameraList(camera);
        return getDataTable(list);
    }

    /**
     * 导出摄像头信息列表
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:export')")
    @Log(title = "摄像头信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Camera camera)
    {
        List<Camera> list = cameraService.selectCameraList(camera);
        ExcelUtil<Camera> util = new ExcelUtil<Camera>(Camera.class);
        util.exportExcel(response, list, "摄像头信息数据");
    }

    /**
     * 获取摄像头信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(cameraService.selectCameraById(id));
    }

    /**
     * 新增摄像头信息
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:add')")
    @Log(title = "摄像头信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Camera camera)
    {
        return toAjax(cameraService.insertCamera(camera));
    }

    /**
     * 修改摄像头信息
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:edit')")
    @Log(title = "摄像头信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Camera camera)
    {
        return toAjax(cameraService.updateCamera(camera));
    }

    /**
     * 删除摄像头信息
     */
    @PreAuthorize("@ss.hasPermi('Camera:camera:remove')")
    @Log(title = "摄像头信息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(cameraService.deleteCameraByIds(ids));
    }
}
