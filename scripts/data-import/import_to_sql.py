#!/usr/bin/env python3
"""把填好的机构数据导入模板（Excel）转换成可以在生产环境执行的 SQL 文件。

用法：
    python3 import_to_sql.py 填好的模板.xlsx --admin-phone 13800000000 [-o import.sql]

设计要点（详见 README.md）：
- 不要求提前知道机构的数据库自增 id：生成的 SQL 里包了一个存储过程，先按
  --admin-phone 指定的管理员手机号查出 institution_id，查不到就直接报错中止，
  一条数据都不会写；查到了才继续插入。
- 整批数据包在一个事务里，中途任何一条插入失败，整个事务都不会提交，
  不会出现"插了一半"的脏数据。
- 教学单元/学生表里"引用其他表"用的是模板里人填的临时编号（T1/U1/S1 这种），
  不是数据库 id；生成的 SQL 用 MySQL 会话变量（SET @unit_U1 = LAST_INSERT_ID()）
  把临时编号和刚插入那一行的真实 id 对应起来，所以不需要先知道任何真实 id。
- 所有老师的初始密码统一设成固定值（默认 888888，用 BCrypt 加密，和后端用的
  Spring Security BCryptPasswordEncoder 完全兼容），系统本身会强制教师首次
  登录改密，初始密码内容不重要。
- 校验尽量在生成 SQL 之前做完（模板内部的必填项/格式/临时编号引用是否存在/
  同类型名称是否重复），发现问题会把所有错误一次性列出来再退出，不会生成
  半成品 SQL 文件。模板内部查不出来的问题（比如手机号和生产库里已有的其他
  机构老师重复）只能等真正执行 SQL 时由数据库唯一约束兜底，失败时事务会
  完整回滚，不会留下脏数据。
"""
import argparse
import re
import sys
import uuid
from decimal import Decimal, InvalidOperation

try:
    import bcrypt
except ImportError:
    print("错误：缺少 bcrypt 库，先运行 pip3 install bcrypt openpyxl", file=sys.stderr)
    sys.exit(1)

try:
    from openpyxl import load_workbook
except ImportError:
    print("错误：缺少 openpyxl 库，先运行 pip3 install bcrypt openpyxl", file=sys.stderr)
    sys.exit(1)

BCRYPT_ROUNDS = 10  # 必须和后端 PasswordEncoderConfig 里 new BCryptPasswordEncoder() 的默认强度一致


def sql_str(value):
    """把 Python 字符串转成安全的 MySQL 字符串字面量；None 转成 NULL（不加引号）。"""
    if value is None:
        return "NULL"
    escaped = str(value).replace("\\", "\\\\").replace("'", "\\'")
    return f"'{escaped}'"


def sql_decimal(value):
    if value is None:
        return "NULL"
    return f"{value:.2f}"


def sql_bool(value):
    return "TRUE" if value else "FALSE"


def cell_str(value):
    """读 Excel 单元格，统一成去掉首尾空白的字符串，空值统一成 None。"""
    if value is None:
        return None
    s = str(value).strip()
    return s if s else None


def split_multi(value):
    """按中英文逗号拆分多选格子，去空白、去重复、保持出现顺序。"""
    if not value:
        return []
    parts = re.split(r"[,，]", value)
    seen = set()
    result = []
    for p in parts:
        p = p.strip()
        if p and p not in seen:
            seen.add(p)
            result.append(p)
    return result


def parse_decimal(value, field_name, row_label, errors, allow_zero=True):
    if value is None or value == "":
        return None
    try:
        d = Decimal(str(value))
    except InvalidOperation:
        errors.append(f"{row_label}：{field_name} 不是合法数字（{value!r}）")
        return None
    if d < 0 or (d == 0 and not allow_zero):
        errors.append(f"{row_label}：{field_name} 必须是{'非负' if allow_zero else '正'}数（{value!r}）")
        return None
    return d


def read_sheet_rows(ws):
    """跳过表头（可能占 1-2 行，用是否全部是字符串且在第一行判断不了，统一约定表头固定第 1 行），
    返回从第 2 行开始、且不是完全空白的行，每行是一个 {列序号: 值} 的 dict（1-based）。
    """
    rows = []
    for row in ws.iter_rows(min_row=2):
        values = [cell.value for cell in row]
        if all(v is None or str(v).strip() == "" for v in values):
            continue
        rows.append(values)
    return rows


# generate_template.py 生成的教师表示例行的手机号——review 发现如果机构负责人忘记
# 删除示例行（说明页之前没有提醒到位，已经修复，但这里再加一道代码层面的兜底），
# 会把一个角色是"管理员"、密码是默认值的虚构账号当真实数据导入生产库。这两个号码
# 是脚本自己生成的示例值，真实机构撞上的概率可以忽略不计，撞上了直接拦截更安全。
EXAMPLE_TEACHER_PHONES = {"13800000001", "13800000002"}


def load_teachers(ws, errors):
    teachers = {}  # tmp_id -> dict
    phones_seen = {}
    for idx, row in enumerate(read_sheet_rows(ws), start=2):
        tmp_id = cell_str(row[0] if len(row) > 0 else None)
        phone = cell_str(row[1] if len(row) > 1 else None)
        name = cell_str(row[2] if len(row) > 2 else None)
        role_cn = cell_str(row[3] if len(row) > 3 else None)
        label = f"教师表第 {idx} 行"

        if phone in EXAMPLE_TEACHER_PHONES:
            errors.append(f"{label}：手机号 {phone} 是模板自带的示例数据，"
                           f"看起来忘记删除示例行了，请删掉这一行再导入")

        if not tmp_id:
            errors.append(f"{label}：缺少临时编号")
            continue
        if tmp_id in teachers:
            errors.append(f"{label}：临时编号 {tmp_id} 和前面某一行重复")
            continue
        if not phone:
            errors.append(f"{label}（{tmp_id}）：缺少手机号")
        elif not phone.isdigit() or not (7 <= len(phone) <= 20):
            errors.append(f"{label}（{tmp_id}）：手机号格式看起来不对（{phone!r}），应为 7-20 位纯数字")
        elif phone in phones_seen:
            errors.append(f"{label}（{tmp_id}）：手机号 {phone} 和表里 {phones_seen[phone]} 重复"
                           f"（手机号在全平台范围内必须唯一）")
        else:
            phones_seen[phone] = tmp_id
        if not name:
            errors.append(f"{label}（{tmp_id}）：缺少姓名")

        role = "TEACHER"
        if role_cn in (None, "", "老师"):
            role = "TEACHER"
        elif role_cn == "管理员":
            role = "ADMIN"
        else:
            errors.append(f"{label}（{tmp_id}）：角色只能是【老师】或【管理员】，收到的是 {role_cn!r}")

        teachers[tmp_id] = {"tmp_id": tmp_id, "phone": phone, "name": name, "role": role, "row": idx}
    return teachers


def load_teaching_units(ws, teachers, errors):
    units = {}
    names_by_type = {}
    for idx, row in enumerate(read_sheet_rows(ws), start=2):
        tmp_id = cell_str(row[0] if len(row) > 0 else None)
        name = cell_str(row[1] if len(row) > 1 else None)
        type_cn = cell_str(row[2] if len(row) > 2 else None)
        teacher_tmp_id = cell_str(row[3] if len(row) > 3 else None)
        tuition = row[4] if len(row) > 4 else None
        meal = row[5] if len(row) > 5 else None
        price_per_lesson = row[6] if len(row) > 6 else None
        duration = row[7] if len(row) > 7 else None
        label = f"教学单元表第 {idx} 行"

        if not tmp_id:
            errors.append(f"{label}：缺少临时编号")
            continue
        if tmp_id in units:
            errors.append(f"{label}：临时编号 {tmp_id} 和前面某一行重复")
            continue
        if not name:
            errors.append(f"{label}（{tmp_id}）：缺少名称")

        if type_cn == "托管班":
            billing_mode = "MONTHLY"
        elif type_cn == "课外课":
            billing_mode = "LESSON_COUNT"
        else:
            errors.append(f"{label}（{tmp_id}）：类型只能是【托管班】或【课外课】，收到的是 {type_cn!r}")
            continue

        if not teacher_tmp_id:
            errors.append(f"{label}（{tmp_id}）：缺少老师编号")
        elif teacher_tmp_id not in teachers:
            errors.append(f"{label}（{tmp_id}）：老师编号 {teacher_tmp_id} 在【教师】表里找不到")

        if name:
            key = (billing_mode, name)
            if key in names_by_type:
                errors.append(f"{label}（{tmp_id}）：同类型下名称「{name}」和 {names_by_type[key]} 重复"
                               f"（系统要求同机构同类型教学单元名称不能重复）")
            else:
                names_by_type[key] = tmp_id

        tuition_amount = meal_amount = price = None
        duration_minutes = None
        if billing_mode == "MONTHLY":
            tuition_amount = parse_decimal(tuition, "月费", f"{label}（{tmp_id}）", errors)
            meal_amount = parse_decimal(meal, "餐费/天", f"{label}（{tmp_id}）", errors)
            if tuition is None or tuition == "":
                errors.append(f"{label}（{tmp_id}）：托管班必须填月费")
            if meal is None or meal == "":
                errors.append(f"{label}（{tmp_id}）：托管班必须填餐费/天")
            if price_per_lesson not in (None, "") or duration not in (None, ""):
                errors.append(f"{label}（{tmp_id}）：托管班不应该填课时单价/课时时长，会被忽略")
        else:
            price = parse_decimal(price_per_lesson, "课时单价", f"{label}（{tmp_id}）", errors, allow_zero=False)
            if price_per_lesson is None or price_per_lesson == "":
                errors.append(f"{label}（{tmp_id}）：课外课必须填课时单价")
            if duration is None or duration == "":
                errors.append(f"{label}（{tmp_id}）：课外课必须填单次课时时长-分钟")
            else:
                try:
                    duration_minutes = int(duration)
                    if duration_minutes <= 0:
                        errors.append(f"{label}（{tmp_id}）：单次课时时长-分钟必须是正整数")
                        duration_minutes = None
                except (TypeError, ValueError):
                    errors.append(f"{label}（{tmp_id}）：单次课时时长-分钟不是合法整数（{duration!r}）")
            if tuition not in (None, "") or meal not in (None, ""):
                errors.append(f"{label}（{tmp_id}）：课外课不应该填月费/餐费，会被忽略")

        units[tmp_id] = {
            "tmp_id": tmp_id, "name": name, "billing_mode": billing_mode,
            "teacher_tmp_id": teacher_tmp_id, "tuition": tuition_amount, "meal": meal_amount,
            "price_per_lesson": price, "duration_minutes": duration_minutes, "row": idx,
        }
    return units


def load_students(ws, units, errors):
    students = []
    tmp_ids_seen = set()
    for idx, row in enumerate(read_sheet_rows(ws), start=2):
        tmp_id = cell_str(row[0] if len(row) > 0 else None)
        name = cell_str(row[1] if len(row) > 1 else None)
        school_class = cell_str(row[2] if len(row) > 2 else None)
        class_unit_tmp_id = cell_str(row[3] if len(row) > 3 else None)
        course_tmp_ids_raw = cell_str(row[4] if len(row) > 4 else None)
        label = f"学生表第 {idx} 行"

        if not tmp_id:
            errors.append(f"{label}：缺少临时编号")
            continue
        if tmp_id in tmp_ids_seen:
            errors.append(f"{label}：临时编号 {tmp_id} 和前面某一行重复")
            continue
        tmp_ids_seen.add(tmp_id)
        if not name:
            errors.append(f"{label}（{tmp_id}）：缺少姓名")

        if class_unit_tmp_id:
            unit = units.get(class_unit_tmp_id)
            if unit is None:
                errors.append(f"{label}（{tmp_id}）：所在托管班编号 {class_unit_tmp_id} 在【教学单元】表里找不到")
            elif unit["billing_mode"] != "MONTHLY":
                errors.append(f"{label}（{tmp_id}）：所在托管班编号 {class_unit_tmp_id} 对应的是课外课，不是托管班")

        course_tmp_ids = split_multi(course_tmp_ids_raw)
        for c in course_tmp_ids:
            unit = units.get(c)
            if unit is None:
                errors.append(f"{label}（{tmp_id}）：报名课外课编号 {c} 在【教学单元】表里找不到")
            elif unit["billing_mode"] != "LESSON_COUNT":
                errors.append(f"{label}（{tmp_id}）：报名课外课编号 {c} 对应的是托管班，不是课外课")

        students.append({
            "tmp_id": tmp_id, "name": name, "school_class": school_class,
            "class_unit_tmp_id": class_unit_tmp_id if class_unit_tmp_id in units else None,
            "course_tmp_ids": [c for c in course_tmp_ids if c in units], "row": idx,
        })
    return students


def generate_sql(teachers, units, students, admin_phone, default_password, source_name):
    password_hash = bcrypt.hashpw(default_password.encode("utf-8"), bcrypt.gensalt(rounds=BCRYPT_ROUNDS)).decode("utf-8")

    monthly_count = sum(1 for u in units.values() if u["billing_mode"] == "MONTHLY")
    lesson_count = len(units) - monthly_count
    enrollment_count = sum(len(s["course_tmp_ids"]) for s in students)

    lines = []
    lines.append("-- " + "=" * 60)
    lines.append("-- 机构数据批量导入脚本（自动生成，请先通读一遍再执行）")
    lines.append(f"-- 源模板文件：{source_name}")
    lines.append(f"-- 目标机构识别方式：管理员手机号 = {admin_phone}")
    lines.append(f"-- 本次导入：教师 {len(teachers)} 人，教学单元 {len(units)} 个"
                 f"（托管班 {monthly_count} 个，课外课 {lesson_count} 个），"
                 f"学生 {len(students)} 人，课外课报名关系 {enrollment_count} 条")
    lines.append(f"-- 所有教师初始密码统一为：{default_password}（已用 BCrypt 加密，登录后会强制要求改密）")
    lines.append("--")
    lines.append("-- 执行前必须先备份：./deploy/backup.sh --mysql-only")
    lines.append("-- 执行方式（必须用支持 DELIMITER 的 mysql 命令行客户端，不要用 --force 参数，")
    lines.append("-- 不能漏掉 --default-character-set=utf8mb4——2026-10-22 实测复现过：漏了这个")
    lines.append("-- 参数，mysql 客户端会用自己的默认连接字符集解释这份 UTF-8 文件，把所有中文")
    lines.append("-- 姓名/机构名双重编码成乱码真实写进数据库，且不会有任何报错提示）：")
    lines.append("--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T mysql \\")
    lines.append("--     mysql --default-character-set=utf8mb4 -uroot -p\"$MYSQL_ROOT_PASSWORD\" \\")
    lines.append("--     <数据库名> < 本文件")
    lines.append("-- 中途任何一步出错都会整体中止、不提交事务，不会留下部分写入的数据，")
    lines.append("-- 改好模板重新生成 SQL 再跑一次即可，不需要先手工清理。")
    lines.append("-- " + "=" * 60)
    lines.append("")
    lines.append("DROP PROCEDURE IF EXISTS tuoguan_import_batch;")
    lines.append("")
    lines.append("DELIMITER $$")
    lines.append("CREATE PROCEDURE tuoguan_import_batch()")
    lines.append("BEGIN")
    lines.append("    DECLARE v_institution_id BIGINT;")
    lines.append("")
    lines.append("    -- review 发现的真实问题：不声明这个 HANDLER 的话，\"中途失败整体回滚\"")
    lines.append("    -- 这个安全保证只在用一次性管道（mysql ... < file.sql）执行时成立——那种")
    lines.append("    -- 方式下出错会直接断开连接，服务端对断开的会话自动回滚未提交事务。但如果")
    lines.append("    -- 在交互式会话里 source 这个文件、中途报错后又在同一个未断开的会话里")
    lines.append("    -- source 改好的文件重新导入，第二个文件开头的 DROP PROCEDURE 是一条 DDL，")
    lines.append("    -- DDL 会隐式 COMMIT 当前会话里任何还开着的事务——会把第一次失败时已经")
    lines.append("    -- 插入的半成品数据连同这次的新数据一起提交，不会有任何报错提示（已实测")
    lines.append("    -- 复现）。显式声明这个 HANDLER 让任何未预期的 SQL 错误都先 ROLLBACK 再")
    lines.append("    -- 重新抛出，不依赖\"连接是否断开\"这个外部条件，无论用哪种方式执行都安全。")
    lines.append("    DECLARE EXIT HANDLER FOR SQLEXCEPTION")
    lines.append("    BEGIN")
    lines.append("        ROLLBACK;")
    lines.append("        RESIGNAL;")
    lines.append("    END;")
    lines.append("")
    lines.append("    SELECT institution_id INTO v_institution_id")
    lines.append(f"    FROM teacher WHERE phone = {sql_str(admin_phone)} AND role = 'ADMIN'")
    lines.append("    LIMIT 1;")
    lines.append("")
    lines.append("    IF v_institution_id IS NULL THEN")
    lines.append("        SIGNAL SQLSTATE '45000'")
    lines.append("            SET MESSAGE_TEXT = "
                  "'找不到该管理员手机号对应的机构"
                  "，已中止导入，未写入任何数据';")
    lines.append("    END IF;")
    lines.append("")
    lines.append("    START TRANSACTION;")
    lines.append("")
    lines.append("    -- ---------- 教师 ----------")
    for t in teachers.values():
        lines.append(
            "    INSERT INTO teacher (institution_id, phone, name, password_hash, role, must_change_password) "
            f"VALUES (v_institution_id, {sql_str(t['phone'])}, {sql_str(t['name'])}, "
            f"{sql_str(password_hash)}, {sql_str(t['role'])}, TRUE);"
        )
        lines.append(f"    SET @teacher_{t['tmp_id']} = LAST_INSERT_ID();")
    lines.append("")
    lines.append("    -- ---------- 教学单元 + 托管班计费 ----------")
    for u in units.values():
        teacher_var = f"@teacher_{u['teacher_tmp_id']}"
        lines.append(
            "    INSERT INTO teaching_unit (institution_id, teacher_id, name, billing_mode, "
            "lesson_duration_minutes, price_per_lesson, active) "
            f"VALUES (v_institution_id, {teacher_var}, {sql_str(u['name'])}, {sql_str(u['billing_mode'])}, "
            f"{u['duration_minutes'] if u['duration_minutes'] is not None else 'NULL'}, "
            f"{sql_decimal(u['price_per_lesson'])}, TRUE);"
        )
        lines.append(f"    SET @unit_{u['tmp_id']} = LAST_INSERT_ID();")
        if u["billing_mode"] == "MONTHLY":
            lines.append(
                "    INSERT INTO class_billing_rate (institution_id, teaching_unit_id, "
                "tuition_rate_per_month, meal_rate_per_day) "
                f"VALUES (v_institution_id, @unit_{u['tmp_id']}, "
                f"{sql_decimal(u['tuition'])}, {sql_decimal(u['meal'])});"
            )
    lines.append("")
    lines.append("    -- ---------- 学生 + 课外课报名 ----------")
    for s in students:
        class_unit_var = f"@unit_{s['class_unit_tmp_id']}" if s["class_unit_tmp_id"] else "NULL"
        # student.share_token 是 CHAR(32) NOT NULL，后端生成逻辑是
        # UUID.randomUUID().toString().replace("-", "")（JdbcStudentDao.java），
        # 必须去掉连字符变成 32 位，否则写不进这个定长列（实测复现过 Data too long 报错）。
        share_token = uuid.uuid4().hex
        lines.append(
            "    INSERT INTO student (institution_id, teaching_unit_id, name, school_class_name, "
            "enrolled, share_token) "
            f"VALUES (v_institution_id, {class_unit_var}, {sql_str(s['name'])}, "
            f"{sql_str(s['school_class'])}, TRUE, {sql_str(share_token)});"
        )
        lines.append(f"    SET @student_{s['tmp_id']} = LAST_INSERT_ID();")
        for c in s["course_tmp_ids"]:
            lines.append(
                "    INSERT INTO student_unit_enrollment (institution_id, student_id, teaching_unit_id, active) "
                f"VALUES (v_institution_id, @student_{s['tmp_id']}, @unit_{c}, TRUE);"
            )
    lines.append("")
    lines.append("    COMMIT;")
    lines.append("END$$")
    lines.append("DELIMITER ;")
    lines.append("")
    lines.append("CALL tuoguan_import_batch();")
    lines.append("DROP PROCEDURE tuoguan_import_batch;")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("template", help="填好的机构数据导入模板 .xlsx 文件路径")
    parser.add_argument("--admin-phone", required=True, help="目标机构的管理员登录手机号，用来在执行 SQL 时识别机构")
    parser.add_argument("-o", "--output", default="import.sql", help="生成的 SQL 文件路径，默认 import.sql")
    parser.add_argument("--default-password", default="888888",
                         help="统一给所有导入教师设置的初始密码（默认 888888，教师首次登录会被强制要求改密）")
    args = parser.parse_args()

    if not args.admin_phone.isdigit():
        print(f"错误：--admin-phone 看起来不是纯数字手机号：{args.admin_phone!r}", file=sys.stderr)
        sys.exit(1)

    wb = load_workbook(args.template, data_only=True)
    for required in ("教师", "教学单元", "学生"):
        if required not in wb.sheetnames:
            print(f"错误：模板里找不到【{required}】这个 sheet，是不是用错文件了？", file=sys.stderr)
            sys.exit(1)

    errors = []
    teachers = load_teachers(wb["教师"], errors)
    units = load_teaching_units(wb["教学单元"], teachers, errors)
    students = load_students(wb["学生"], units, errors)

    if errors:
        print(f"模板里发现 {len(errors)} 处问题，已全部列出，改完模板后重新运行本脚本：\n", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(1)

    if not teachers and not units and not students:
        print("警告：没有读到任何数据行，是不是模板里只剩示例行、还没填真实数据？", file=sys.stderr)
        sys.exit(1)

    sql = generate_sql(teachers, units, students, args.admin_phone, args.default_password, args.template)
    with open(args.output, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"校验通过，已生成 {args.output}")
    print(f"教师 {len(teachers)} 人，教学单元 {len(units)} 个，学生 {len(students)} 人")
    print("执行前请先通读一遍生成的 SQL，并务必先跑一次数据库备份：./deploy/backup.sh --mysql-only")


if __name__ == "__main__":
    main()
