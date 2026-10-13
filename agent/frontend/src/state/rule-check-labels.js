// 仅翻译已定义的课程类别展示值；提交及范围匹配保留服务端代码，未知值不猜含义。
export function ruleCheckConditionLabel(key, value) {
  if (key === 'courseType') {
    if (value === 'PUBLIC') return '公共选修课'
    if (value === 'PROFESSIONAL') return '专业选修课'
  }
  return value
}
