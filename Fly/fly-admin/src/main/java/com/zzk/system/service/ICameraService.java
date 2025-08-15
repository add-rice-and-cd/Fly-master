package com.zzk.system.service;

import java.util.List;
import com.zzk.system.domain.Camera;

/**
 * 摄像头信息Service接口
 * 
 * @author ruoyi
 * @date 2025-08-07
 */
public interface ICameraService 
{
    /**
     * 查询摄像头信息
     * 
     * @param id 摄像头信息主键
     * @return 摄像头信息
     */
    public Camera selectCameraById(Long id);

    /**
     * 查询摄像头信息列表
     * 
     * @param camera 摄像头信息
     * @return 摄像头信息集合
     */
    public List<Camera> selectCameraList(Camera camera);

    /**
     * 新增摄像头信息
     * 
     * @param camera 摄像头信息
     * @return 结果
     */
    public int insertCamera(Camera camera);

    /**
     * 修改摄像头信息
     * 
     * @param camera 摄像头信息
     * @return 结果
     */
    public int updateCamera(Camera camera);

    /**
     * 批量删除摄像头信息
     * 
     * @param ids 需要删除的摄像头信息主键集合
     * @return 结果
     */
    public int deleteCameraByIds(Long[] ids);

    /**
     * 删除摄像头信息信息
     * 
     * @param id 摄像头信息主键
     * @return 结果
     */
    public int deleteCameraById(Long id);
}
