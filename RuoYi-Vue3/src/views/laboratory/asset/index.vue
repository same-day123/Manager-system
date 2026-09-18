<template>
  <div class="app-container laboratory-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="资产编号" prop="assetCode">
        <el-input v-model="queryParams.assetCode" placeholder="请输入资产编号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="资产名称" prop="assetName">
        <el-input v-model="queryParams.assetName" placeholder="请输入资产名称" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="资产类型" prop="assetType">
        <el-select v-model="queryParams.assetType" placeholder="请选择资产类型" clearable style="width: 180px">
          <el-option v-for="dict in lab_asset_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 160px">
          <el-option v-for="dict in lab_asset_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['laboratory:asset:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['laboratory:asset:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['laboratory:asset:import']">导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['laboratory:asset:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['laboratory:asset:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="assetList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="资产编号" align="center" prop="assetCode" min-width="140" />
      <el-table-column label="资产名称" align="center" prop="assetName" min-width="170" :show-overflow-tooltip="true" />
      <el-table-column label="资产类型" align="center" prop="assetType" width="130">
        <template #default="scope">
          <dict-tag :options="lab_asset_type" :value="scope.row.assetType" />
        </template>
      </el-table-column>
      <el-table-column label="规格型号" align="center" prop="model" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="所属实验室" align="center" prop="roomName" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="价格" align="center" prop="price" width="110" />
      <el-table-column label="购置日期" align="center" prop="purchaseDate" width="130" />
      <el-table-column label="状态" align="center" prop="status" width="110">
        <template #default="scope">
          <dict-tag :options="lab_asset_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <!-- 操作列宽度 320px 的依据：本列有 4 个「图标 + 两字」按钮（修改 / 履历 / 标签 / 删除），
           实测并排需要约 306px；原先的 260px 会把「删除」挤到第二行，行高由 52px 撑到 63px。
           320px 在 1920 屏下不引入横向滚动（本表声明合计 1465px < 内容区 1720px）。 -->
      <el-table-column label="操作" align="center" width="320" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['laboratory:asset:edit']">修改</el-button>
          <el-button link type="primary" icon="Clock" @click="handleRecords(scope.row)" v-hasPermi="['laboratory:asset:query']">履历</el-button>
          <el-button link type="primary" icon="Grid" @click="handleQrcode(scope.row)" v-hasPermi="['laboratory:asset:query']">标签</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['laboratory:asset:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="assetRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资产编号" prop="assetCode">
              <el-input v-model="form.assetCode" placeholder="请输入资产编号" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产名称" prop="assetName">
              <el-input v-model="form.assetName" placeholder="请输入资产名称" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资产类型" prop="assetType">
              <el-select v-model="form.assetType" placeholder="请选择资产类型" filterable style="width: 100%">
                <el-option v-for="dict in lab_asset_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号" prop="model">
              <el-input v-model="form.model" placeholder="请输入规格型号" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="价格" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置日期" prop="purchaseDate">
              <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择购置日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="所属实验室" prop="roomId">
          <el-input v-model="form.roomName" placeholder="请选择实验室" readonly>
            <template #append>
              <el-button type="primary" icon="Search" @click="openRoomDialog">选择</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in lab_asset_status" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确定</el-button>
          <el-button @click="cancel">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="选择实验室" v-model="roomOpen" width="760px" append-to-body>
      <el-form :model="roomQueryParams" ref="roomQueryRef" :inline="true" label-width="82px">
        <el-form-item label="实验室名称" prop="roomName">
          <el-input v-model="roomQueryParams.roomName" placeholder="请输入实验室名称" clearable style="width: 220px" @keyup.enter="handleRoomQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleRoomQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetRoomQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="roomLoading" :data="roomList" height="340" highlight-current-row>
        <el-table-column label="实验室编号" align="center" prop="roomNo" min-width="130" />
        <el-table-column label="实验室名称" align="center" prop="roomName" min-width="160" />
        <el-table-column label="所属学院" align="center" prop="collegeName" min-width="150" />
        <el-table-column label="操作" align="center" width="90">
          <template #default="scope">
            <el-button link type="primary" icon="CircleCheck" @click="handleSelectRoom(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="roomTotal > 0" :total="roomTotal" v-model:page="roomQueryParams.pageNum" v-model:limit="roomQueryParams.pageSize" @pagination="getRoomList" />
    </el-dialog>

    <el-dialog title="资产导入" v-model="importOpen" width="520px" append-to-body>
      <el-upload ref="uploadRef" drag :limit="1" accept=".xlsx,.xls" :auto-upload="false" :http-request="handleImportRequest">
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖入 Excel 文件或点击选择</div>
      </el-upload>
      <div class="import-tip">
        <el-button link type="primary" icon="Download" @click="downloadTemplate">下载导入模板</el-button>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitImport">开始导入</el-button>
          <el-button @click="importOpen = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="资产二维码标签" v-model="qrOpen" width="420px" append-to-body>
      <div class="qr-card">
        <div class="qr-svg" v-html="qrInfo.svg"></div>
        <p>{{ qrInfo.assetCode }} · {{ qrInfo.assetName }}</p>
        <span>{{ qrInfo.content }}</span>
      </div>
    </el-dialog>

    <el-drawer v-model="recordOpen" title="资产生命周期履历" size="520px">
      <el-timeline class="record-timeline">
        <el-timeline-item v-for="item in assetRecords" :key="item.recordId" :timestamp="parseTime(item.createTime)" placement="top">
          <h4>{{ item.recordType }}</h4>
          <p>{{ item.recordContent }}</p>
          <span v-if="item.fromValue || item.toValue">{{ recordValueLabel(item, item.fromValue) }} → {{ recordValueLabel(item, item.toValue) }}</span>
          <small>{{ item.operatorName }}</small>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="assetRecords.length === 0" description="暂无履历记录" />
    </el-drawer>
  </div>
</template>

<script setup name="LaboratoryAsset">
import { listAsset, getAsset, delAsset, addAsset, updateAsset, importAssetData, getAssetQrcode, getAssetRecords } from "@/api/laboratory/asset"
import { listRoom } from "@/api/laboratory/room"

const { proxy } = getCurrentInstance()
const { lab_asset_type, lab_asset_status } = useDict("lab_asset_type", "lab_asset_status")

const assetList = ref([])
const roomList = ref([])
const assetRecords = ref([])
const open = ref(false)
const roomOpen = ref(false)
const importOpen = ref(false)
const qrOpen = ref(false)
const recordOpen = ref(false)
const loading = ref(true)
const roomLoading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const roomTotal = ref(0)
const title = ref("")
const qrInfo = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    assetCode: undefined,
    assetName: undefined,
    assetType: undefined,
    status: undefined
  },
  roomQueryParams: {
    pageNum: 1,
    pageSize: 10,
    roomName: undefined,
    status: "0"
  },
  rules: {
    assetCode: [{ required: true, message: "资产编号不能为空", trigger: "blur" }],
    assetName: [{ required: true, message: "资产名称不能为空", trigger: "blur" }],
    roomId: [{ required: true, message: "请选择所属实验室", trigger: "change" }]
  }
})

const { queryParams, roomQueryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listAsset(queryParams.value).then(response => {
    assetList.value = response.rows
    total.value = response.total
  }).finally(() => {
    loading.value = false
  })
}

function getRoomList() {
  roomLoading.value = true
  listRoom(roomQueryParams.value).then(response => {
    roomList.value = response.rows
    roomTotal.value = response.total
  }).finally(() => {
    roomLoading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    assetId: undefined,
    assetCode: undefined,
    assetName: undefined,
    assetType: undefined,
    model: undefined,
    price: 0,
    purchaseDate: undefined,
    roomId: undefined,
    roomName: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("assetRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleRoomQuery() {
  roomQueryParams.value.pageNum = 1
  getRoomList()
}

function resetRoomQuery() {
  proxy.resetForm("roomQueryRef")
  handleRoomQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.assetId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增资产"
}

function handleUpdate(row) {
  reset()
  const assetId = row.assetId || ids.value
  getAsset(assetId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改资产"
  })
}

function submitForm() {
  proxy.$refs["assetRef"].validate(valid => {
    if (valid) {
      const action = form.value.assetId != undefined ? updateAsset : addAsset
      action(form.value).then(() => {
        proxy.$modal.msgSuccess(form.value.assetId != undefined ? "修改成功" : "新增成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const assetIds = row.assetId || ids.value
  proxy.$modal.confirm('是否确认删除资产编号为"' + assetIds + '"的数据项？').then(() => delAsset(assetIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  proxy.download("laboratory/asset/export", { ...queryParams.value }, `asset_${new Date().getTime()}.xlsx`)
}

function handleImport() {
  importOpen.value = true
  nextTick(() => proxy.$refs.uploadRef?.clearFiles())
}

function submitImport() {
  proxy.$refs.uploadRef.submit()
}

function handleImportRequest(options) {
  const formData = new FormData()
  formData.append("file", options.file)
  importAssetData(formData).then(response => {
    proxy.$modal.msgSuccess(response.msg || response.data || "导入成功")
    importOpen.value = false
    getList()
  }).catch(() => {
    options.onError()
  })
}

function downloadTemplate() {
  proxy.download("laboratory/asset/importTemplate", {}, `asset_template_${new Date().getTime()}.xlsx`)
}

function handleQrcode(row) {
  getAssetQrcode(row.assetId).then(response => {
    qrInfo.value = response.data || {}
    qrOpen.value = true
  })
}

function handleRecords(row) {
  getAssetRecords(row.assetId).then(response => {
    assetRecords.value = response.data || []
    recordOpen.value = true
  })
}

// 与后端 LabStatusUtils.assetRecordType(status) 的返回口径一一对应：
// 只有这三种履历的 fromValue / toValue 存的是资产状态码（0 正常 / 1 停用 / 2 维修中），
// 其余（入库 / 报废 / 调拨 / 资料修改）存的是资产编号或实验室名称，不能过字典，
// 否则会把编号和房间名当成状态码去查标签。后端改这个方法时这里必须同步改。
const ASSET_STATUS_RECORD_TYPES = ["启用", "停用", "维修"]

/** 把履历条目的 from / to 值转成可读文案，非状态类履历原样返回。 */
function recordValueLabel(record, value) {
  if (!value) return "-"
  if (!ASSET_STATUS_RECORD_TYPES.includes(record.recordType)) return value
  return selectDictLabel(lab_asset_status.value, value) || value
}

function openRoomDialog() {
  roomOpen.value = true
  roomQueryParams.value.pageNum = 1
  getRoomList()
}

function handleSelectRoom(row) {
  form.value.roomId = row.roomId
  form.value.roomName = row.roomName
  roomOpen.value = false
  proxy.$refs["assetRef"]?.validateField("roomId")
}

getList()
</script>

<style scoped lang="scss">
.import-tip {
  margin-top: 12px;
}

.qr-card {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 12px 0 4px;
}

.qr-svg {
  width: 260px;
  height: auto;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #fff;
}

.qr-svg :deep(svg) {
  display: block;
  width: 100%;
  height: auto;
}

.qr-card p {
  margin: 8px 0 0;
  font-weight: 700;
}

.qr-card span {
  color: #667085;
  font-size: 12px;
}

.record-timeline {
  padding: 6px 18px 0 4px;
}

.record-timeline h4 {
  margin: 0 0 6px;
}

.record-timeline p {
  margin: 0 0 6px;
  color: #344054;
}

.record-timeline span,
.record-timeline small {
  display: block;
  color: #667085;
}
</style>
