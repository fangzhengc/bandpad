/**
 * 设备/平台描述。
 *
 * 这个文件入库的默认内容是「小米手环 9 Pro」，构建 REDMI Watch 6 版时
 * scripts/build.sh 会用 platform.watch6.js 临时覆盖它，打完包再还原。
 *
 * 所以业务代码里**不要**写设备名判断，统一读这里的字段：
 *   platform.pad  —— 屏幕上要不要显示翻页/加速按钮（手表的上/下滑容易被系统手势抢走）
 * 以后要加新设备，复制一份 platform.xxx.js + manifest.xxx.json 即可。
 */
export default {
  id: 'band9pro',
  name: '小米手环 9 Pro',
  pad: false
}
