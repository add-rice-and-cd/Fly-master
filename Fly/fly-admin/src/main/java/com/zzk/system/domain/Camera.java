package com.zzk.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.zzk.common.annotation.Excel;
import com.zzk.common.core.domain.BaseEntity;

/**
 * 摄像头信息对象 camera
 * 
 * @author ruoyi
 * @date 2025-08-07
 */
public class Camera extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 摄像头ID */
    private Long id;

    /** 摄像头名称 */
    @Excel(name = "摄像头名称")
    private String name;

    /** 经度 */
    @Excel(name = "经度")
    private Double longitude;

    /** 纬度 */
    @Excel(name = "纬度")
    private Double latitude;

    /** 视频流地址 */
    @Excel(name = "视频流地址")
    private String streamUrl;

    /** 状态（0关闭 1开启） */
    @Excel(name = "状态", readConverterExp = "0=关闭,1=开启")
    private Long status;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setName(String name) 
    {
        this.name = name;
    }

    public String getName() 
    {
        return name;
    }
    public void setLongitude(Double longitude) 
    {
        this.longitude = longitude;
    }

    public Double getLongitude() 
    {
        return longitude;
    }
    public void setLatitude(Double latitude) 
    {
        this.latitude = latitude;
    }

    public Double getLatitude() 
    {
        return latitude;
    }
    public void setStreamUrl(String streamUrl) 
    {
        this.streamUrl = streamUrl;
    }

    public String getStreamUrl() 
    {
        return streamUrl;
    }
    public void setStatus(Long status) 
    {
        this.status = status;
    }

    public Long getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("longitude", getLongitude())
            .append("latitude", getLatitude())
            .append("streamUrl", getStreamUrl())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
