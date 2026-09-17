<template>
  <div class="app-container laboratory-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="报修单号" prop="repairCode">
        <el-input v-model="queryParams.repairCode" placeholder="请输入报修单号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="资产名称" prop="assetName">
        <el-input v-model="queryParams.assetName" placeholder="请输入资产名称" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="申请人" prop="applicantName">
        <el-input v-model="queryParams.applicantName" placeholder="请输入申请人" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 180px">
          <el-option v-for="dict in lab_repair_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['laboratory:repair:add']">新增报修</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['laboratory:repair:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['laboratory:repair:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['laboratory:repair:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="repairList" table-layout="fixed" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="报修单号" align="center" prop="repairCode" min-width="160" :show-overflow-tooltip="true" />
      <!-- 资产名称与资产编号合并为一列：两者是同一实体的两种标识，
           各占一列会白耗约 110px。编号作为次要信息降到第二行小字，信息不丢。 -->
      <el-table-column label="报修资产" align="center" min-width="200">
        <template #default="scope">
          <div class="cell-stack">
            <span class="cell-main">{{ scope.row.assetName || "-" }}</span>
            <span class="cell-sub">{{ scope.row.assetCode || "-" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="实验室" align="center" prop="roomName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="故障等级" align="center" prop="faultLevel" width="110">
        <template #default="scope">
          <dict-tag :options="lab_fault_level" :value="scope.row.faultLevel" />
        </template>
      </el-table-column>
      <el-table-column label="处理状态" align="center" prop="status" width="120">
        <template #default="scope">
          <dict-tag :options="lab_repair_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- 联系电话同理并入申请人列，它只是偶尔才需要看的信息 -->
      <el-table-column label="申请人" align="center" min-width="130">
        <template #default="scope">
          <div class="cell-stack">
            <span class="cell-main">{{ scope.row.applicantName || "-" }}</span>
            <span class="cell-sub">{{ scope.row.applicantPhone || "-" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评分" align="center" prop="rating" width="80">
        <template #default="scope">
          <span>{{ scope.row.rating ? scope.row.rating + " 分" : "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" align="center" prop="finishTime" width="160">
        <template #default="scope">
          <span>{{ scope.row.finishTime ? parseTime(scope.row.finishTime) : "-" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <!-- 操作列宽度 220px 的依据：这几个按钮受权限与状态互斥控制，实测同时最多出现 3 个
           （例如「待审核」态的非管理员是 详情 / 修改 / 删除，管理员是 详情 / 处理 / 删除），
           原先的 330px 是按 5 个按钮预留的，属于过度预留。 -->
      <el-table-column label="操作" align="center" width="220" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button v-if="canStudentEdit(scope.row)" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['laboratory:repair:edit']">修改</el-button>
          <el-button v-if="canAudit(scope.row)" link type="primary" icon="CircleCheck" @click="handleProcess(scope.row)" v-hasPermi="['laboratory:repair:audit']">处理</el-button>
          <el-button v-if="canEvaluate(scope.row)" link type="primary" icon="Star" @click="handleEvaluate(scope.row)" v-hasPermi="['laboratory:repair:evaluate']">评价</el-button>
          <el-button v-if="canDelete(scope.row)" link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['laboratory:repair:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="860px" append-to-body>
      <!-- 弹窗顶部补一条关键信息：原先从列表点进详情后，看不出这单是谁报的、什么时候报的 -->
      <div v-if="form.repairId" class="repair-meta">
        <span><em>报修单号</em>{{ form.repairCode || "-" }}</span>
        <span><em>申请人</em>{{ form.applicantName || "-" }}</span>
        <span><em>提交时间</em>{{ form.createTime ? parseTime(form.createTime) : "-" }}</span>
      </div>
      <el-form ref="repairRef" :model="form" :rules="rules" label-width="100px" :disabled="viewMode">
        <section class="detail-section">
          <h3>基础信息</h3>
          <el-form-item label="报修资产" prop="assetId">
            <el-input v-model="form.assetName" placeholder="请选择资产" readonly>
              <template #append v-if="canSelectAsset">
                <el-button type="primary" icon="Search" @click="openAssetDialog">选择资产</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="故障等级" prop="faultLevel">
            <el-select v-model="form.faultLevel" placeholder="请选择故障等级" style="width: 100%">
              <el-option v-for="dict in lab_fault_level" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="故障描述" prop="faultDescription">
            <el-input v-model="form.faultDescription" type="textarea" :rows="4" placeholder="请描述故障现象、位置和影响范围" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="故障图片" prop="attachmentUrls">
            <image-upload v-model="form.attachmentUrls" :limit="4" :file-size="5" :disabled="viewMode || processMode" />
          </el-form-item>
        </section>

        <section v-if="showProcessFields" class="detail-section">
          <h3>维修处理</h3>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="维修人员" prop="repairUserName">
                <el-input v-model="form.repairUserName" placeholder="请输入维修人员" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="维修费用" prop="repairCost">
                <el-input-number v-model="form.repairCost" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="状态" prop="status">
            <dict-tag v-if="viewMode" :options="lab_repair_status" :value="form.status" />
            <el-radio-group v-else v-model="form.status">
              <el-radio v-for="item in nextStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.finishTime" label="完成时间">
            <span>{{ parseTime(form.finishTime) }}</span>
          </el-form-item>
        </section>

        <section v-if="viewMode && form.rating" class="detail-section">
          <h3>维修评价</h3>
          <el-form-item label="评分">
            <el-rate v-model="form.rating" disabled show-score />
          </el-form-item>
          <el-form-item label="评价内容">
            <span>{{ form.evaluationContent || "-" }}</span>
          </el-form-item>
        </section>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <section v-if="form.repairId" class="detail-section timeline-section">
        <h3>处理时间线</h3>
        <el-timeline>
          <el-timeline-item v-for="item in repairRecords" :key="item.recordId" :timestamp="parseTime(item.createTime)" placement="top">
            <div class="timeline-card">
              <strong>{{ item.actionName }}</strong>
              <p>{{ item.recordContent }}</p>
              <span>{{ statusLabel(item.fromStatus) }} → {{ statusLabel(item.toStatus) }} · {{ item.operatorName }}</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="repairRecords.length === 0" description="暂无处理记录" />
      </section>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="!viewMode" type="primary" @click="submitForm">确定</el-button>
          <el-button @click="cancel">{{ viewMode ? "关闭" : "取消" }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择可报修资产" v-model="assetOpen" width="860px" append-to-body>
      <el-form :model="assetQueryParams" ref="assetQueryRef" :inline="true" label-width="82px">
        <el-form-item label="资产名称" prop="assetName">
          <el-input v-model="assetQueryParams.assetName" placeholder="请输入资产名称" clearable style="width: 240px" @keyup.enter="handleAssetQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleAssetQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetAssetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="assetLoading" :data="assetList" height="360" highlight-current-row>
        <el-table-column label="资产编号" align="center" prop="assetCode" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="资产名称" align="center" prop="assetName" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="规格型号" align="center" prop="model" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="所属实验室" align="center" prop="roomName" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" icon="CircleCheck" @click="handleSelectAsset(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="assetTotal > 0" :total="assetTotal" v-model:page="assetQueryParams.pageNum" v-model:limit="assetQueryParams.pageSize" @pagination="getAssetList" />
    </el-dialog>

    <el-dialog title="维修评价" v-model="evaluateOpen" width="560px" append-to-body>
      <el-form ref="evaluateRef" :model="evaluateForm" :rules="evaluateRules" label-width="90px">
        <el-form-item label="评分" prop="rating">
          <el-rate v-model="evaluateForm.rating" show-score />
        </el-form-item>
        <el-form-item label="评价内容" prop="evaluationContent">
          <el-input v-model="evaluateForm.evaluationContent" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请输入维修评价" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitEvaluate">提交评价</el-button>
          <el-button @click="evaluateOpen = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="LaboratoryRepair">
import { listRepair, getRepair, getRepairRecords, delRepair, addRepair, updateRepair, auditRepair, evaluateRepair } from "@/api/laboratory/repair"
import { listRepairableAsset, getAsset } from "@/api/laboratory/asset"
import { checkPermi } from "@/utils/permission"
import { canHandleLabRepair } from "@/utils/labPermission"

const { proxy } = getCurrentInstance()
const route = useRoute()
const { lab_fault_level, lab_repair_status } = useDict("lab_fault_level", "lab_repair_status")

const repairList = ref([])
const assetList = ref([])
const repairRecords = ref([])
const open = ref(false)
const assetOpen = ref(false)
const evaluateOpen = ref(false)
const loading = ref(true)
const assetLoading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const assetTotal = ref(0)
const title = ref("")
const viewMode = ref(false)
const processMode = ref(false)
const oldStatus = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    repairCode: undefined,
    assetName: undefined,
    applicantName: undefined,
    status: undefined
  },
  assetQueryParams: {
    pageNum: 1,
    pageSize: 10,
    assetName: undefined
  },
  evaluateForm: {
    repairId: undefined,
    rating: 5,
    evaluationContent: undefined
  },
  rules: {
    assetId: [{ required: true, message: "请选择资产", trigger: "change" }],
    faultDescription: [{ required: true, message: "故障描述不能为空", trigger: "blur" }]
  },
  evaluateRules: {
    rating: [{ required: true, message: "请选择评分", trigger: "change" }]
  }
})

const { queryParams, assetQueryParams, form, rules, evaluateForm, evaluateRules } = toRefs(data)
// 与后端 LabRoleUtils.canHandleRepair 同源，避免"按钮可见但接口 403"这类不一致。
const isManager = computed(() => canHandleLabRepair())
const showProcessFields = computed(() => viewMode.value || processMode.value)
// 报修资产只在「新增报修」时选定。编辑已有单据时不允许换资产：
// 否则普通用户能借"修改本人待审核单"把任意"正常"资产置为"维修中"（后端也会拒绝）。
const canSelectAsset = computed(() => !viewMode.value && !processMode.value && form.value.repairId == null)
const nextStatusOptions = computed(() => {
  const map = {
    "0": ["1", "4"],
    "1": ["2"],
    "2": ["3"]
  }
  const values = map[oldStatus.value || form.value.status] || []
  return lab_repair_status.value.filter(item => values.includes(item.value))
})

function getList() {
  loading.value = true
  listRepair(queryParams.value).then(response => {
    repairList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function getAssetList() {
  assetLoading.value = true
  listRepairableAsset(assetQueryParams.value).then(response => {
    assetList.value = response.rows
    assetTotal.value = response.total
  }).finally(() => {
    assetLoading.value = false
  })
}

function cancel() {
  open.value = false
  viewMode.value = false
  processMode.value = false
  repairRecords.value = []
  reset()
}

function reset() {
  form.value = {
    repairId: undefined,
    repairCode: undefined,
    assetId: undefined,
    assetName: undefined,
    faultDescription: undefined,
    faultLevel: "1",
    attachmentUrls: undefined,
    applicantId: undefined,
    applicantName: undefined,
    applicantPhone: undefined,
    repairUserId: undefined,
    repairUserName: undefined,
    repairCost: 0,
    finishTime: undefined,
    status: "0",
    rating: undefined,
    evaluationContent: undefined,
    evaluationTime: undefined,
    remark: undefined
  }
  oldStatus.value = ""
  proxy.resetForm("repairRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleAssetQuery() {
  assetQueryParams.value.pageNum = 1
  getAssetList()
}

function resetAssetQuery() {
  proxy.resetForm("assetQueryRef")
  handleAssetQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.repairId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  viewMode.value = false
  processMode.value = false
  open.value = true
  title.value = "新增设备报修"
}

function handleAddWithAsset(assetId) {
  handleAdd()
  getAsset(assetId).then(response => {
    if (response.data) {
      form.value.assetId = response.data.assetId
      form.value.assetName = response.data.assetName
    }
  })
}

function handleView(row) {
  reset()
  getRepair(row.repairId).then(response => {
    form.value = response.data
    oldStatus.value = response.data.status
    viewMode.value = true
    processMode.value = false
    open.value = true
    title.value = "报修详情"
    loadRecords(row.repairId)
  })
}

function handleUpdate(row) {
  reset()
  const repairId = row.repairId || ids.value
  getRepair(repairId).then(response => {
    form.value = response.data
    oldStatus.value = response.data.status
    viewMode.value = false
    processMode.value = false
    open.value = true
    title.value = "修改设备报修"
    loadRecords(response.data.repairId)
  })
}

function handleProcess(row) {
  reset()
  getRepair(row.repairId).then(response => {
    form.value = response.data
    oldStatus.value = response.data.status
    viewMode.value = false
    processMode.value = true
    open.value = true
    title.value = "处理设备报修"
    loadRecords(row.repairId)
  })
}

function submitForm() {
  proxy.$refs["repairRef"].validate(valid => {
    if (!valid) return
    if (processMode.value && form.value.status === oldStatus.value) {
      proxy.$modal.msgWarning("请选择下一步处理状态")
      return
    }
    if (form.value.repairId != undefined) {
      const action = processMode.value ? auditRepair : updateRepair
      action(form.value).then(() => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
    } else {
      addRepair(form.value).then(() => {
        proxy.$modal.msgSuccess("新增成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleEvaluate(row) {
  evaluateForm.value = {
    repairId: row.repairId,
    rating: row.rating || 5,
    evaluationContent: row.evaluationContent
  }
  evaluateOpen.value = true
}

function submitEvaluate() {
  proxy.$refs["evaluateRef"].validate(valid => {
    if (!valid) return
    evaluateRepair(evaluateForm.value).then(() => {
      proxy.$modal.msgSuccess("评价成功")
      evaluateOpen.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const repairIds = row.repairId || ids.value
  proxy.$modal.confirm('是否确认删除设备报修编号为"' + repairIds + '"的数据项？').then(() => delRepair(repairIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  proxy.download("laboratory/repair/export", { ...queryParams.value }, `repair_${new Date().getTime()}.xlsx`)
}

function openAssetDialog() {
  assetOpen.value = true
  assetQueryParams.value.pageNum = 1
  getAssetList()
}

function handleSelectAsset(row) {
  form.value.assetId = row.assetId
  form.value.assetName = row.assetName
  assetOpen.value = false
  proxy.$refs["repairRef"]?.validateField("assetId")
}

function loadRecords(repairId) {
  getRepairRecords(repairId).then(response => {
    repairRecords.value = response.data || []
  })
}

function canStudentEdit(row) {
  return row.status === "0" && checkPermi(["laboratory:repair:edit"]) && !isManager.value
}

function canAudit(row) {
  return isManager.value && ["0", "1", "2"].includes(row.status)
}

function canEvaluate(row) {
  return !isManager.value && row.status === "3" && !row.rating && checkPermi(["laboratory:repair:evaluate"])
}

function canDelete(row) {
  return checkPermi(["laboratory:repair:remove"]) && (isManager.value ? ["0", "4"].includes(row.status) : row.status === "0")
}

function statusLabel(status) {
  if (!status) return "-"
  return selectDictLabel(lab_repair_status.value, status) || status
}

onMounted(() => {
  getList()
  if (route.query.assetId) {
    handleAddWithAsset(route.query.assetId)
  }
})
</script>

<style scoped lang="scss">
.repair-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  padding: 10px 14px;
  margin-bottom: 14px;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #f8fbff;
}

.repair-meta span {
  font-size: 13px;
  color: #172033;
}

.repair-meta em {
  margin-right: 6px;
  font-style: normal;
  font-size: 12px;
  color: #667085;
}

.detail-section {
  padding: 2px 0 12px;
  border-bottom: 1px solid #edf1f7;
}

.detail-section + .detail-section {
  padding-top: 14px;
}

.detail-section h3 {
  margin: 0 0 14px;
  font-size: 16px;
  color: #172033;
}

.timeline-section {
  margin-top: 14px;
  border-bottom: 0;
}

.timeline-card {
  padding: 10px 12px;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #f8fbff;
}

.timeline-card p {
  margin: 6px 0;
  color: #344054;
}

.timeline-card span {
  color: #667085;
  font-size: 12px;
}
</style>
