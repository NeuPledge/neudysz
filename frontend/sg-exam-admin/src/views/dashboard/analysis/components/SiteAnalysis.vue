<template>
  <Card
    :tab-list="tabListTitle"
    v-bind="$attrs"
    :active-tab-key="activeKey"
    @tabChange="onTabChange"
  >
    <p v-if="activeKey === 'tab1'">
      <ExamRecordAnalysis />
    </p>
    <p v-if="activeKey === 'tab2'">
      <SubjectAnalysis />
    </p>
  </Card>
</template>
<script lang="ts">
import {defineComponent, ref} from 'vue';
import { Card } from 'ant-design-vue';
import ExamRecordAnalysis from './ExamRecordAnalysis.vue';
import SubjectAnalysis from './SubjectAnalysis.vue';

export default defineComponent({
  name: 'DashboardTendency',
  components: { Card, ExamRecordAnalysis,SubjectAnalysis },
  setup() {
    const activeKey = ref('tab1');
    const tabListTitle = [
      {
        key: 'tab1',
        tab: '考试数量',
      },
      {
        key: 'tab2',
        tab: '题目数量',
      }
    ];

    function onTabChange(key) {
      activeKey.value = key;
    }
    return {activeKey, tabListTitle, onTabChange }
  }
});

</script>
