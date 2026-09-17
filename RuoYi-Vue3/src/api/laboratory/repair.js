import request from '@/utils/request'

// 查询设备报修列表
export function listRepair(query) {
  return request({
    url: '/laboratory/repair/list',
    method: 'get',
    params: query
  })
}

// 查询设备报修详细
export function getRepair(repairId) {
  return request({
    url: '/laboratory/repair/' + repairId,
    method: 'get'
  })
}

export function getRepairRecords(repairId) {
  return request({
    url: '/laboratory/repair/' + repairId + '/records',
    method: 'get'
  })
}

// 新增设备报修
export function addRepair(data) {
  return request({
    url: '/laboratory/repair',
    method: 'post',
    data: data
  })
}

// 修改设备报修
export function updateRepair(data) {
  return request({
    url: '/laboratory/repair',
    method: 'put',
    data: data
  })
}

export function auditRepair(data) {
  return request({
    url: '/laboratory/repair/audit',
    method: 'put',
    data: data
  })
}

export function evaluateRepair(data) {
  return request({
    url: '/laboratory/repair/evaluate',
    method: 'put',
    data: data
  })
}

// 删除设备报修
export function delRepair(repairId) {
  return request({
    url: '/laboratory/repair/' + repairId,
    method: 'delete'
  })
}
