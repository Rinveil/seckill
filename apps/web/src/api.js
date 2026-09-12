const API = ''

export async function getActivities() {
  const res = await fetch(`${API}/api/activity/list`)
  return res.json()
}

export async function getActivity(id) {
  const res = await fetch(`${API}/api/activity/${id}`)
  return res.json()
}

export async function grab(activityId) {
  const res = await fetch(`${API}/api/seckill/${activityId}`, {
    method: 'POST',
    headers: { 'X-User-Id': '10001' }
  })
  return res.json()
}
