import request from '@/utils/request'

export function listRoom(query) {
  return request({
    url: '/laboratory/room/list',
    method: 'get',
    params: query
  })
}

export function getRoom(roomId) {
  return request({
    url: '/laboratory/room/' + roomId,
    method: 'get'
  })
}

export function addRoom(data) {
  return request({
    url: '/laboratory/room',
    method: 'post',
    data: data
  })
}

export function updateRoom(data) {
  return request({
    url: '/laboratory/room',
    method: 'put',
    data: data
  })
}

export function delRoom(roomId) {
  return request({
    url: '/laboratory/room/' + roomId,
    method: 'delete'
  })
}
