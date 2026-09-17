-- ============================================================================
-- [可选] 实验室索引口径修正（按需执行，**不参与 [N/7] 顺序**）
-- 用途：把已存在的数据库（如 education_system）里的索引口径，对齐到
--       laboratory_schema.sql 的设计意图：
--         ① 删除与「逻辑删除」语义冲突的两个唯一索引
--            lab_asset.uni_asset_code / lab_repair.uni_repair_code
--         ② 补齐线上库缺失、而 schema 里应有的 4 个普通索引
-- 依赖：laboratory_schema.sql（需要五张业务表已存在；表不存在则跳过，不报错）
-- 幂等：是（先查 information_schema 再动作，重复执行无副作用）
-- 目标库：education_system
-- 为什么单独成文件而不并进 laboratory_schema.sql：
--   空库重建时实验室表本来就**不带**唯一索引，本脚本对空库是纯 no-op；
--   它只服务于"把已经跑起来的老库纠回来"这一件事，属维护脚本，
--   与 laboratory_cleanup.sql 同类，按需执行即可，不占 [N/7] 的位置。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 为什么必须删掉这两个唯一索引（真缺陷，不是风格问题）
-- ----------------------------------------------------------------------------
-- 本系统所有删除都是**逻辑删除**：deleteLabAssetByAssetId 执行的是
--     update lab_asset set del_flag = '2' where asset_id = ?
-- 行永远留在表里。而 LabAssetServiceImpl.checkAssetCodeUnique / checkRoomNoUnique
-- 的判定都带 `and del_flag = '0'`，等于承认"编号在删除后可以重用"。
--
-- 于是出现下面这条必然踩到的路径：
--     ① 建资产 LAB-001                    → 成功
--     ② 删除该资产（del_flag='2'，行还在）  → 成功
--     ③ 重新建编号同为 LAB-001 的资产
--        应用层：checkAssetCodeUnique 查 del_flag='0' → 查不到 → 放行
--        数据库：uni_asset_code 唯一索引看到历史行 → 拒绝
--     ④ 前端拿到 500，且错误信息是 SQL 层的，用户完全看不懂
--
-- lab_repair.uni_repair_code 同理：删除一张待审核报修单后，若单号生成器
-- 恰好重复（或是演示/测试时手工指定单号），同样会撞唯一索引。
--
-- 编号唯一性改由**应用层**保证（checkAssetCodeUnique / checkRoomNoUnique），
-- 这与若依原生的做法也一致：sys_user.user_name、sys_dept 等表都没有把
-- 唯一的业务编号做成数据库唯一索引。
-- ----------------------------------------------------------------------------

drop procedure if exists fix_lab_indexes;
delimiter //
create procedure fix_lab_indexes()
begin
    -- ========== ① 删掉与逻辑删除冲突的唯一索引 ==========
    if exists (select 1 from information_schema.tables
               where table_schema = database() and table_name = 'lab_asset') then
        if exists (select 1 from information_schema.statistics
                   where table_schema = database() and table_name = 'lab_asset'
                     and index_name = 'uni_asset_code') then
            alter table lab_asset drop index uni_asset_code;
        end if;
    end if;

    if exists (select 1 from information_schema.tables
               where table_schema = database() and table_name = 'lab_repair') then
        if exists (select 1 from information_schema.statistics
                   where table_schema = database() and table_name = 'lab_repair'
                     and index_name = 'uni_repair_code') then
            alter table lab_repair drop index uni_repair_code;
        end if;
    end if;

    -- ========== ② 补齐 schema 定义的普通索引（该建的建，已有的跳过） ==========

    -- lab_room.room_no：房间列表按编号检索是高频路径，线上库此前无索引（全表扫描）
    if exists (select 1 from information_schema.tables
               where table_schema = database() and table_name = 'lab_room') then
        if not exists (select 1 from information_schema.statistics
                       where table_schema = database() and table_name = 'lab_room'
                         and index_name = 'idx_lab_room_no') then
            alter table lab_room add index idx_lab_room_no (room_no);
        end if;
    end if;

    if exists (select 1 from information_schema.tables
               where table_schema = database() and table_name = 'lab_asset') then
        if not exists (select 1 from information_schema.statistics
                       where table_schema = database() and table_name = 'lab_asset'
                         and index_name = 'idx_lab_asset_code') then
            alter table lab_asset add index idx_lab_asset_code (asset_code);
        end if;
        if not exists (select 1 from information_schema.statistics
                       where table_schema = database() and table_name = 'lab_asset'
                         and index_name = 'idx_lab_asset_status') then
            alter table lab_asset add index idx_lab_asset_status (status);
        end if;
    end if;

    if exists (select 1 from information_schema.tables
               where table_schema = database() and table_name = 'lab_repair') then
        if not exists (select 1 from information_schema.statistics
                       where table_schema = database() and table_name = 'lab_repair'
                         and index_name = 'idx_lab_repair_code') then
            alter table lab_repair add index idx_lab_repair_code (repair_code);
        end if;
        if not exists (select 1 from information_schema.statistics
                       where table_schema = database() and table_name = 'lab_repair'
                         and index_name = 'idx_lab_repair_status') then
            alter table lab_repair add index idx_lab_repair_status (status);
        end if;
    end if;
end//
delimiter ;

call fix_lab_indexes();
drop procedure if exists fix_lab_indexes;

-- ----------------------------------------------------------------------------
-- 自检（执行完可以手动跑一遍，期望：只列出普通索引，没有任何 Non_unique = 0 的行）
-- ----------------------------------------------------------------------------
-- select table_name, index_name, non_unique, column_name
-- from information_schema.statistics
-- where table_schema = database()
--   and table_name in ('lab_room', 'lab_asset', 'lab_repair')
-- order by table_name, index_name, seq_in_index;
