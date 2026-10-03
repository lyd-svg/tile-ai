<script setup lang="ts">
import { Modal } from 'ant-design-vue'

import { confirmState, settleConfirm } from './AppConfirm'

/**
 * 确认框的**宿主** —— 全项目**只挂一次**,位置在 `App.vue` 的 `<ConfigProvider>` **之内**。
 *
 * ## 为什么必须是"组件"而不是 `Modal.confirm`
 *
 * 见 `AppConfirm.ts` 的文件头:**静态方法脱离应用上下文**,既拿不到主题
 * (按钮会成 antd 默认蓝),也躲不开"两个汉字自动插空格"(按钮成「确 定」)。
 * 做成**声明式组件**并挂在根上,这两个问题一起解决。
 *
 * ## 为什么单独一个文件
 *
 * 这样 **antd 只出现在这里** —— `AppConfirm.ts` 是纯状态,页面与测试都不碰 antd。
 * 将来要换弹窗实现(或换 antd 版本),**只动这一个文件**。
 */
</script>

<template>
  <Modal
    :open="confirmState.open"
    :title="confirmState.title"
    :ok-text="confirmState.okText"
    :cancel-text="confirmState.cancelText"
    :ok-type="confirmState.danger ? 'danger' : 'primary'"
    @ok="settleConfirm(true)"
    @cancel="settleConfirm(false)"
  >
    {{ confirmState.content }}
  </Modal>
</template>
