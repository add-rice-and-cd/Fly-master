import request from '@/utils/request'

// 查询摄像头信息列表
export function listCamera(query) {
  return request({
    url: '/Camera/camera/list',
    method: 'get',
    params: query
  })
}

// 查询摄像头信息详细
export function getCamera(id) {
  return request({
    url: '/Camera/camera/' + id,
    method: 'get'
  })
}

// 新增摄像头信息
export function addCamera(data) {
  return request({
    url: '/Camera/camera',
    method: 'post',
    data: data
  })
}

// 修改摄像头信息
export function updateCamera(data) {
  return request({
    url: '/Camera/camera',
    method: 'put',
    data: data
  })
}

// 删除摄像头信息
export function delCamera(id) {
  return request({
    url: '/Camera/camera/' + id,
    method: 'delete'
  })
}
