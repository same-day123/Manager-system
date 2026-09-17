<template>
  <div class="app-container laboratory-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="82px">
      <el-form-item label="实验室名称" prop="roomName">
        <el-input v-model="queryParams.roomName" placeholder="请输入实验室名称" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="实验室编号" prop="roomNo">
        <el-input v-model="queryParams.roomNo" placeholder="请输入实验室编号" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属学院" prop="collegeName">
        <el-input v-model="queryParams.collegeName" placeholder="请输入所属学院" clearable style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 180px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['laboratory:room:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['laboratory:room:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['laboratory:room:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['laboratory:room:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="roomList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="实验室编号" align="center" prop="roomNo" min-width="130" />
      <el-table-column label="实验室名称" align="center" prop="roomName" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="所属学院" align="center" prop="collegeName" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="管理员" align="center" prop="managerName" width="120" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['laboratory:room:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['laboratory:room:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="roomRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="实验室编号" prop="roomNo">
          <el-input v-model="form.roomNo" placeholder="请输入实验室编号" maxlength="50" />
        </el-form-item>
        <el-form-item label="实验室名称" prop="roomName">
          <el-input v-model="form.roomName" placeholder="请输入实验室名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="所属学院" prop="collegeName">
          <el-input v-model="form.collegeName" placeholder="请输入所属学院" maxlength="100" />
        </el-form-item>
        <el-form-item label="管理员" prop="managerName">
          <el-input v-model="form.managerName" placeholder="请输入管理员姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
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
  </div>
</template>

<script setup name="LaboratoryRoom">
import { listRoom, getRoom, delRoom, addRoom, updateRoom } from "@/api/laboratory/room"

const { proxy } = getCurrentInstance()
const roomList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    roomName: undefined,
    roomNo: undefined,
    collegeName: undefined,
    status: undefined
  },
  rules: {
    roomNo: [{ required: true, message: "实验室编号不能为空", trigger: "blur" }],
    roomName: [{ required: true, message: "实验室名称不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listRoom(queryParams.value).then(response => {
    roomList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    roomId: undefined,
    roomNo: undefined,
    roomName: undefined,
    collegeName: undefined,
    managerId: undefined,
    managerName: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("roomRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.roomId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增实验室"
}

function handleUpdate(row) {
  reset()
  const roomId = row.roomId || ids.value
  getRoom(roomId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改实验室"
  })
}

function submitForm() {
  proxy.$refs["roomRef"].validate(valid => {
    if (valid) {
      const action = form.value.roomId != undefined ? updateRoom : addRoom
      action(form.value).then(() => {
        proxy.$modal.msgSuccess(form.value.roomId != undefined ? "修改成功" : "新增成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const roomIds = row.roomId || ids.value
  proxy.$modal.confirm('是否确认删除实验室编号为"' + roomIds + '"的数据项？').then(() => delRoom(roomIds)).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleExport() {
  proxy.download("laboratory/room/export", { ...queryParams.value }, `room_${new Date().getTime()}.xlsx`)
}

getList()
</script>
