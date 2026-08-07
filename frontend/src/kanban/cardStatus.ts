export type CardStatus = 'done' | 'dismissedIncomplete' | 'default'

export interface CardStatusTask {
  completed: boolean
}

/**
 * PRD 6.4 优先级：全部完成（绿色+印章）> 已放学且有未完成任务（红色）> 默认色。
 * 没有分配任何任务时，视为默认色（既不算“全部完成”，也不算“未完成”）。
 */
export function computeCardStatus(tasks: CardStatusTask[], dismissed: boolean): CardStatus {
  const hasTasks = tasks.length > 0
  const allCompleted = hasTasks && tasks.every((task) => task.completed)

  if (allCompleted) {
    return 'done'
  }
  if (dismissed && hasTasks) {
    return 'dismissedIncomplete'
  }
  return 'default'
}
