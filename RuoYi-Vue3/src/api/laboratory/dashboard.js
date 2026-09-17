import request from '@/utils/request'

export function getDashboardSummary() {
  return request({
    url: '/laboratory/dashboard/summary',
    method: 'get'
  })
}

export function getDashboardCharts() {
  return request({
    url: '/laboratory/dashboard/charts',
    method: 'get'
  })
}
