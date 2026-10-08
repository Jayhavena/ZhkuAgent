const STORAGE_KEY = 'zhku.campus.submissions.v1'
const RETENTION_MS = 7 * 24 * 60 * 60 * 1000
export const isSubmissionKey = value => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value)
const isId = value => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value)
const timestamp = value => typeof value === 'number' ? value : Date.parse(value)

export function createSubmissionStore({ storage, randomUUID = () => globalThis.crypto.randomUUID(), now = Date.now } = {}) {
  let owner = null, entries = [], loaded = false
  const snapshots = new Map()
  const backing = () => { try { return storage === undefined ? globalThis.sessionStorage : storage } catch { return null } }
  function persist() {
    // 只持久化确认线索；课程、问题、正文和身份凭据绝不能随页面刷新回放。
    try {
      if (!owner || !entries.length) backing()?.removeItem(STORAGE_KEY)
      else backing()?.setItem(STORAGE_KEY, JSON.stringify({ ownerUserId: owner, entries }))
    } catch { /* 隐私模式可能禁用Storage，当前页面仍可确认，刷新恢复不作保证。 */ }
  }
  function select(userId) {
    if (!isId(userId)) throw new Error('INVALID_USER_ID')
    if (!loaded) {
      loaded = true
      try {
        const stored = JSON.parse(backing()?.getItem(STORAGE_KEY) || 'null')
        owner = stored?.ownerUserId || null
        entries = Array.isArray(stored?.entries) ? stored.entries.filter(e => isSubmissionKey(e.submissionKey)
          && Number.isFinite(timestamp(e.createdAt)) && Number.isFinite(timestamp(e.expiresAt)))
          .slice(-5).map(e => ({ submissionKey: e.submissionKey, ...(isId(e.runId) ? { runId: e.runId } : {}),
            createdAt: timestamp(e.createdAt), expiresAt: timestamp(e.expiresAt) })) : []
      } catch { owner = null; entries = [] }
    }
    if (owner !== userId) { entries = []; snapshots.clear(); owner = userId }
    const kept = entries.filter(e => e.expiresAt > now())
    for (const e of entries) if (!kept.includes(e)) snapshots.delete(e.submissionKey)
    entries = kept; persist()
  }
  function view(entry) {
    return entry ? { ...entry, ...(snapshots.has(entry.submissionKey) ? { requestSnapshot: snapshots.get(entry.submissionKey) } : {}) } : null
  }
  return {
    begin(userId, request) {
      select(userId)
      const submissionKey = randomUUID()
      if (!isSubmissionKey(submissionKey) || entries.some(e => e.submissionKey === submissionKey)) throw new Error('INVALID_SUBMISSION_KEY')
      const requestSnapshot = Object.freeze({ question: request.question,
        courses: Object.freeze(request.courses.map(c => Object.freeze({ courseId: c.courseId, category: c.category, credits: c.credits }))) })
      const entry = { submissionKey, createdAt: now(), expiresAt: now() + RETENTION_MS }
      entries.push(entry); snapshots.set(submissionKey, requestSnapshot)
      while (entries.length > 5) snapshots.delete(entries.shift().submissionKey)
      persist(); return view(entry)
    },
    confirm(reply) {
      const entry = entries.find(e => e.submissionKey === reply?.submissionKey)
      if (!owner || !entry) throw new Error('UNKNOWN_SUBMISSION')
      if (isId(reply.runId)) entry.runId = reply.runId
      if (Number.isFinite(timestamp(reply.expiresAt))) entry.expiresAt = timestamp(reply.expiresAt)
      persist(); return view(entry)
    },
    restore(userId, key) { select(userId); return view(key ? entries.find(e => e.submissionKey === key) : entries.at(-1)) },
    forget(userId) {
      if (userId && owner && userId !== owner) return
      owner = null; entries = []; snapshots.clear(); loaded = true; persist()
    }
  }
}
export const controlledSubmissions = createSubmissionStore()
