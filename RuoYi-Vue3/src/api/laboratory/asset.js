import request from '@/utils/request'

// 查询实验室资产列表
export function listAsset(query) {
  return request({
    url: '/laboratory/asset/list',
    method: 'get',
    params: query
  })
}

export function listRepairableAsset(query) {
  return request({
    url: '/laboratory/asset/repairable',
    method: 'get',
    params: query
  })
}

export function getAssetQrcode(assetId) {
  return request({
    url: '/laboratory/asset/' + assetId + '/qrcode',
    method: 'get'
  })
}

export function getAssetRecords(assetId) {
  return request({
    url: '/laboratory/asset/' + assetId + '/records',
    method: 'get'
  })
}

// 查询实验室资产详细
export function getAsset(assetId) {
  return request({
    url: '/laboratory/asset/' + assetId,
    method: 'get'
  })
}

// 新增实验室资产
export function addAsset(data) {
  return request({
    url: '/laboratory/asset',
    method: 'post',
    data: data
  })
}

// 修改实验室资产
export function updateAsset(data) {
  return request({
    url: '/laboratory/asset',
    method: 'put',
    data: data
  })
}

// 删除实验室资产
export function delAsset(assetId) {
  return request({
    url: '/laboratory/asset/' + assetId,
    method: 'delete'
  })
}

export function importAssetData(data) {
  return request({
    url: '/laboratory/asset/importData',
    method: 'post',
    data: data,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
