# NOTICE · 第三方组件与权利归属

本仓库自有代码以 MIT 发布（见 LICENSE）。以下内容不属于本仓库作者，版权归各自权利人。

## 1. 小米穿戴 SDK（闭源）

- 文件：`phone/app/libs/xms-wearable-lib_1.4_release.aar`
- 权利人：小米科技 / Xiaomi
- 来源：小米官方 Vela 快应用文档站提供的「互联互通（interconnect）开发测试示例包」
  `https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip`
- 内置目的：让 clone 本仓库的人不需要额外申请即可编译通过
- 限制：**不得单独抽取、转售或用于商业分发**。正式发布请按小米官方
  「小米穿戴第三方 APP 能力开放接口文档」的流程自行获取授权。如权利人提出异议，
  会立即从本仓库移除。

## 2. 小米手环快应用运行环境

- Vela OS / 快应用运行时、AstroBox 等工具版权归小米所有，本仓库不分发。
- `scripts/ref/logo_official_128.png` 取自上述官方示例包，仅作 PNG 字节结构模板使用
  （手环对图标格式要求苛刻，见 README「手环图标为什么这么挑剔」）。

## 3. npm 依赖

`band/package.json` 中的 `aiot-toolkit`、`@aiot-toolkit/velasim` 遵循各自许可证，
未收录进本仓库，由 `npm install` 获取。

## 4. 安卓构建依赖

Android Gradle Plugin 8.1.4、AndroidX 组件通过 Maven 获取，遵循 Apache License 2.0。
