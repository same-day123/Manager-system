<template>
  <div class="lab-dashboard">
    <section class="dashboard-head">
      <div>
        <p class="eyebrow">实验室运维工作台</p>
        <h1>资产状态、报修趋势与维修成本总览</h1>
      </div>
      <div class="head-actions">
        <el-button v-if="checkPermi(['laboratory:repair:add'])" type="primary" icon="Plus" @click="go('/laboratory/repair')">提交报修</el-button>
        <el-button v-if="checkPermi(['laboratory:asset:list'])" icon="Tickets" @click="go('/laboratory/asset')">资产台账</el-button>
        <el-button v-if="canViewDashboard" icon="Refresh" @click="loadDashboard">刷新</el-button>
      </div>
    </section>

    <section v-if="canViewDashboard" class="metric-grid" v-loading="loading">
      <article v-for="item in metrics" :key="item.key" class="metric-card">
        <div class="metric-icon" :class="item.tone">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ summary[item.key] || 0 }}</strong>
        </div>
      </article>
    </section>

    <section v-if="canViewDashboard" class="chart-grid" v-loading="loading">
      <article class="chart-panel wide">
        <div class="panel-title">
          <h2>近七日报修趋势</h2>
          <span>{{ trendTotal }} 单</span>
        </div>
        <div ref="trendRef" class="chart-box"></div>
      </article>
      <article class="chart-panel">
        <div class="panel-title">
          <h2>故障等级分布</h2>
        </div>
        <div ref="faultRef" class="chart-box"></div>
      </article>
      <article class="chart-panel">
        <div class="panel-title">
          <h2>实验室维修次数</h2>
        </div>
        <div ref="roomRef" class="chart-box"></div>
      </article>
      <article class="chart-panel wide">
        <div class="panel-title">
          <h2>近七日维修费用</h2>
          <span>￥{{ costTotal.toFixed(2) }}</span>
        </div>
        <div ref="costRef" class="chart-box"></div>
      </article>
    </section>

    <el-empty
      v-if="!canViewDashboard"
      description="当前角色没有运维工作台的查看权限，这里只显示可访问的快捷入口"
    />

    <section v-if="quickItems.length" class="quick-grid">
      <article v-for="item in quickItems" :key="item.title" class="quick-card" @click="go(item.path)">
        <el-icon><component :is="item.icon" /></el-icon>
        <div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup name="Index">
import * as echarts from "echarts"
import { getDashboardSummary, getDashboardCharts } from "@/api/laboratory/dashboard"
import { checkPermi } from "@/utils/permission"

const router = useRouter()
const { lab_fault_level } = useDict("lab_fault_level")

// 首页是所有人的默认落地页，但看板接口受 laboratory:dashboard:view 保护。
// 没有该权限的角色（例如只带系统管理权限的账号）必须跳过请求，否则一登录就两个 403 报错。
const canViewDashboard = computed(() => checkPermi(["laboratory:dashboard:view"]))

const loading = ref(false)
const trendRef = ref(null)
const faultRef = ref(null)
const roomRef = ref(null)
const costRef = ref(null)
const chartInstances = []
const summary = reactive({
  assetTotal: 0,
  repairingAssetTotal: 0,
  pendingRepairTotal: 0,
  finishedRepairTotal: 0
})
const charts = reactive({
  repairTrend: [],
  faultLevelDistribution: [],
  roomRepairRanking: [],
  repairCostTrend: []
})

const metrics = [
  { key: "assetTotal", label: "资产总数", icon: "Tickets", tone: "blue" },
  { key: "repairingAssetTotal", label: "维修中资产", icon: "Tools", tone: "amber" },
  { key: "pendingRepairTotal", label: "待审核报修", icon: "Warning", tone: "red" },
  { key: "finishedRepairTotal", label: "已完成报修", icon: "CircleCheck", tone: "green" }
]

// 快捷入口按权限过滤，避免点到未授权菜单后落到 404。
const quickItems = computed(() => [
  { title: "实验室房间", desc: "维护房间编号、学院、管理员和启停状态", path: "/laboratory/room", icon: "OfficeBuilding", perm: "laboratory:room:list" },
  { title: "资产台账", desc: "查看资产状态、履历、导入台账和二维码标签", path: "/laboratory/asset", icon: "Tickets", perm: "laboratory:asset:list" },
  { title: "设备报修", desc: "提交故障、跟踪处理时间线并完成评价", path: "/laboratory/repair", icon: "Tools", perm: "laboratory:repair:list" }
].filter(item => checkPermi([item.perm])))

const trendTotal = computed(() => sumValues(charts.repairTrend))
const costTotal = computed(() => sumValues(charts.repairCostTrend))

// 后端趋势接口是 group by 日期，没有报修的那天不会返回记录。
// 直接画会把"近 7 天"压成一条更短的折线（横轴看着每天都有数据），
// 所以前端固定补齐最近 7 天、缺失日补 0。
function fillMissingDays(data) {
  const map = new Map(normalizeItems(data).map(item => [item.name, item.value]))
  const today = new Date()
  const series = []
  for (let offset = 6; offset >= 0; offset--) {
    const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset)
    const key = String(day.getMonth() + 1).padStart(2, "0") + "-" + String(day.getDate()).padStart(2, "0")
    series.push({ name: key, value: map.get(key) || 0 })
  }
  return series
}

function loadDashboard() {
  loading.value = true
  Promise.all([getDashboardSummary(), getDashboardCharts()]).then(([summaryRes, chartRes]) => {
    Object.assign(summary, summaryRes.data || {})
    Object.assign(charts, chartRes.data || {})
    nextTick(renderCharts)
  }).finally(() => {
    loading.value = false
  })
}

function renderCharts() {
  disposeCharts()
  chartInstances.push(renderLine(trendRef.value, fillMissingDays(charts.repairTrend), "#2563eb", "报修单"))
  chartInstances.push(renderPie(faultRef.value, charts.faultLevelDistribution))
  chartInstances.push(renderBar(roomRef.value, charts.roomRepairRanking))
  chartInstances.push(renderLine(costRef.value, fillMissingDays(charts.repairCostTrend), "#0f766e", "维修费用"))
}

function renderLine(el, data, color, name) {
  if (!el) return null
  const chart = echarts.init(el)
  const items = normalizeItems(data)
  chart.setOption({
    color: [color],
    grid: { left: 34, right: 18, top: 24, bottom: 28 },
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: items.map(item => item.name), boundaryGap: false, axisTick: { show: false } },
    yAxis: { type: "value", splitLine: { lineStyle: { color: "#edf1f7" } } },
    series: [{
      name,
      type: "line",
      smooth: true,
      symbolSize: 7,
      areaStyle: { color: color + "1f" },
      data: items.map(item => item.value)
    }]
  })
  return chart
}

function renderPie(el, data) {
  if (!el) return null
  const chart = echarts.init(el)
  const items = normalizeItems(data).map(item => ({
    name: selectDictLabel(lab_fault_level.value, item.name) || item.name || "未分级",
    value: item.value
  }))
  chart.setOption({
    color: ["#64748b", "#f59e0b", "#dc2626", "#2563eb"],
    tooltip: { trigger: "item" },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
    series: [{
      type: "pie",
      radius: ["46%", "72%"],
      center: ["50%", "42%"],
      label: { formatter: "{b}" },
      data: items
    }]
  })
  return chart
}

function renderBar(el, data) {
  if (!el) return null
  const chart = echarts.init(el)
  const items = normalizeItems(data)
  chart.setOption({
    color: ["#334155"],
    grid: { left: 86, right: 20, top: 16, bottom: 28 },
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" } },
    xAxis: { type: "value", splitLine: { lineStyle: { color: "#edf1f7" } } },
    yAxis: { type: "category", data: items.map(item => item.name), axisTick: { show: false } },
    series: [{ type: "bar", data: items.map(item => item.value), barWidth: 14, borderRadius: [0, 6, 6, 0] }]
  })
  return chart
}

function normalizeItems(data) {
  return (data || []).map(item => ({
    name: item.name || item.NAME || "",
    value: Number(item.value || item.VALUE || 0)
  }))
}

function sumValues(data) {
  return normalizeItems(data).reduce((sum, item) => sum + item.value, 0)
}

function disposeCharts() {
  while (chartInstances.length) {
    const chart = chartInstances.pop()
    chart && chart.dispose()
  }
}

function handleResize() {
  chartInstances.forEach(chart => chart && chart.resize())
}

function go(path) {
  router.push(path)
}

onMounted(() => {
  if (canViewDashboard.value) {
    loadDashboard()
  }
  window.addEventListener("resize", handleResize)
})

onBeforeUnmount(() => {
  disposeCharts()
  window.removeEventListener("resize", handleResize)
})
</script>

<style scoped lang="scss">
.lab-dashboard {
  min-height: calc(100vh - 84px);
  padding: 22px;
  background: #f4f7fb;
  color: #172033;
}

.dashboard-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 22px 24px;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-weight: 700;
}

h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
}

.head-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.metric-grid,
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.metric-card,
.quick-card,
.chart-panel {
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #ffffff;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}

.metric-card p {
  margin: 0 0 4px;
  color: #667085;
}

.metric-card strong {
  font-size: 30px;
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 8px;
  font-size: 23px;
}

.metric-icon.blue { color: #2563eb; background: #eff6ff; }
.metric-icon.amber { color: #b45309; background: #fffbeb; }
.metric-icon.red { color: #dc2626; background: #fef2f2; }
.metric-icon.green { color: #15803d; background: #f0fdf4; }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.chart-panel {
  min-height: 320px;
  padding: 18px;
}

.chart-panel.wide {
  min-height: 300px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-title h2 {
  margin: 0;
  font-size: 17px;
}

.panel-title span {
  color: #667085;
  font-weight: 700;
}

.chart-box {
  width: 100%;
  height: 248px;
}

.quick-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.quick-card {
  display: flex;
  gap: 14px;
  padding: 18px;
  cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease;
}

.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(16, 24, 40, 0.08);
}

.quick-card > .el-icon {
  margin-top: 2px;
  color: #2563eb;
  font-size: 24px;
}

.quick-card h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.quick-card p {
  margin: 0;
  color: #667085;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .metric-grid,
  .chart-grid,
  .quick-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .dashboard-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .chart-grid,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
