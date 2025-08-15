package com.zzk.system.service.impl;

import java.util.List;
import com.zzk.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.zzk.system.mapper.CameraMapper;
import com.zzk.system.domain.Camera;
import com.zzk.system.service.ICameraService;

/**
 * 摄像头信息Service业务层处理
 * 
 * @author ruoyi
 * @date 2025-08-07
 */
@Service
public class CameraServiceImpl implements ICameraService 
{
    @Autowired
    private CameraMapper cameraMapper;

    /**
     * 查询摄像头信息
     * 
     * @param id 摄像头信息主键
     * @return 摄像头信息
     */
    @Override
    public Camera selectCameraById(Long id)
    {
        return cameraMapper.selectCameraById(id);
    }

    /**
     * 查询摄像头信息列表
     * 
     * @param camera 摄像头信息
     * @return 摄像头信息
     */
    @Override
    public List<Camera> selectCameraList(Camera camera)
    {
        return cameraMapper.selectCameraList(camera);
    }

    /**
     * 新增摄像头信息
     * 
     * @param camera 摄像头信息
     * @return 结果
     */
    @Override
    public int insertCamera(Camera camera)
    {
        camera.setCreateTime(DateUtils.getNowDate());
        return cameraMapper.insertCamera(camera);
    }

    /**
     * 修改摄像头信息
     * 
     * @param camera 摄像头信息
     * @return 结果
     */
    @Override
    public int updateCamera(Camera camera)
    {
        camera.setUpdateTime(DateUtils.getNowDate());
        return cameraMapper.updateCamera(camera);
    }

    /**
     * 批量删除摄像头信息
     * 
     * @param ids 需要删除的摄像头信息主键
     * @return 结果
     */
    @Override
    public int deleteCameraByIds(Long[] ids)
    {
        return cameraMapper.deleteCameraByIds(ids);
    }

    /**
     * 删除摄像头信息信息
     * 
     * @param id 摄像头信息主键
     * @return 结果
     */
    @Override
    public int deleteCameraById(Long id)
    {
        return cameraMapper.deleteCameraById(id);
    }
}
