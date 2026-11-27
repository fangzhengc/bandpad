/**
 * REDMI Watch 6 平台描述（构建时覆盖 common/platform.js）。
 *
 * pad: true —— 手表的上滑/下滑大概率被系统手势（回表盘、通知中心）抢走，
 * 所以除了滑动手势，额外给一排屏幕按钮兜底。
 */
export default {
  id: 'watch6',
  name: 'REDMI Watch 6',
  pad: true
}
