<template>
  <div ref="chartRef" :style="{ height, width }"></div>
</template>
<script lang="ts" setup>
  import { onMounted, ref, Ref } from 'vue';
  import { useECharts } from '/@/hooks/web/useECharts';
  import { basicProps } from './props';
  import { getSubjectNumByType } from "/@/api/sys/dashboard";

  defineProps({
    ...basicProps,
  });
  const chartRef = ref<HTMLDivElement | null>(null);
  const { setOptions } = useECharts(chartRef as Ref<HTMLDivElement>);

  onMounted(() => {
    getSubjectNumByType().then((res) => {
      const { subjectTypeNumPercent } = res;
      const seriesData = [];
      Object.keys(subjectTypeNumPercent).forEach(key => {
        seriesData.push({name: key, value: subjectTypeNumPercent[key]});
      })
      setOptions({
        tooltip: {
          trigger: 'item',
        },
        legend: {
          bottom: '1%'
        },
        series: [
          {
            color: ['#5ab1ef', '#b6a2de', '#67e0e3', '#2ec7c9','#2e47c9','#3413c9'],
            name: '题目数量',
            type: 'pie',
            top: '0%',
            radius: ['20%', '60%'],
            avoidLabelOverlap: true,
            itemStyle: {
              borderRadius: 10,
              borderColor: '#fff',
              borderWidth: 2,
            },
            label: {
              show: true,
              position: 'bottom',
            },
            emphasis: {
              label: {
                show: true,
                fontSize: '12',
                fontWeight: 'bold',
              },
            },
            labelLine: {
              show: true,
            },
            data: seriesData,
            animationType: 'scale',
            animationEasing: 'exponentialInOut',
            animationDelay: function () {
              return Math.random() * 100;
            },
          },
        ],
      });
    });
  });
</script>
