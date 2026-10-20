#!/usr/bin/env python3
"""生成机构数据导入模板（Excel，三张数据表 + 一张说明页）。

用法：
    python3 generate_template.py [输出文件路径，默认 机构数据导入模板.xlsx]

这个模板给机构负责人/管理员填写老师、教学单元（托管班+课外课）、学生三类数据，
填完之后用同目录下的 import_to_sql.py 转换成可以在生产环境执行的 SQL 文件。

设计上特意不包含"机构""密码"这两类信息：
- 机构：一次导入只对应一个机构，导入时用管理员手机号识别机构，不需要在表格里
  逐行重复填写机构名/机构 id。
- 密码：系统强制教师首次登录必须改密，初始密码内容本身不重要，统一在导入时
  设成固定值（888888），不需要机构负责人为每个老师编一个密码。
"""
import sys

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.worksheet.datavalidation import DataValidation

HEADER_FILL = PatternFill(start_color="DCE6F1", end_color="DCE6F1", fill_type="solid")
EXAMPLE_FILL = PatternFill(start_color="F2F2F2", end_color="F2F2F2", fill_type="solid")
HEADER_FONT = Font(bold=True)
WRAP = Alignment(wrap_text=True, vertical="top")


def style_header(ws, ncols):
    for col in range(1, ncols + 1):
        cell = ws.cell(row=1, column=col)
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = WRAP


def mark_example_row(ws, row, ncols):
    for col in range(1, ncols + 1):
        ws.cell(row=row, column=col).fill = EXAMPLE_FILL


def build_instructions_sheet(wb):
    ws = wb.active
    ws.title = "说明"
    lines = [
        "这个文件用来结构化记录一个机构的老师、教学单元（托管班/课外课）、学生数据，",
        "填完之后交给技术负责人，用 import_to_sql.py 转换成导入用的 SQL。",
        "",
        "【临时编号】三张表最左边都有一列「临时编号」：",
        "  这只是方便在这个文件内部互相引用（比如「学生」表要填「所在托管班」时，填的是",
        "  「教学单元」表里的临时编号，不是系统里的真实数据库编号）。导入时程序会自动把",
        "  临时编号换成系统生成的真实编号，你不需要、也不可能自己填真实编号。",
        "  同一张表内，临时编号不能重复；建议老师表用 T1、T2……，教学单元表用 U1、U2……，",
        "  学生表用 S1、S2……（前缀不强制，换成别的写法也可以，只要同表内不重复）。",
        "",
        "【密码】不需要填。系统会给每个老师统一设置一个初始密码，并且强制要求老师",
        "  第一次登录时必须自己改密码，所以初始密码具体是什么不重要。",
        "",
        "【多选格子】比如学生表的「报名课外课编号」可以填多个教学单元的临时编号，",
        "  用逗号隔开即可，中文逗号「，」和英文逗号「,」都可以，也可以混用，比如：",
        "  U3,U5，U8",
        "",
        "【必填/选填】列标题带 * 号的是必填项，不带的是选填项（可以留空）。",
        "",
        "【三张表的关系】",
        "  老师 → 教学单元：每个教学单元（托管班/课外课）必须指定一个已经存在的老师。",
        "  教学单元 → 学生：",
        "    - 托管班和学生是一对一：一个学生最多属于一个托管班（可以不属于任何托管班，",
        "      代表这个学生只上课外课，不托管）。",
        "    - 课外课和学生是多对多：一个学生可以报多门课外课，一门课外课也可以有多个学生。",
        "  填表顺序建议：先填「教师」表，再填「教学单元」表（这时候老师已经有临时编号可以引用），",
        "  最后填「学生」表（这时候教学单元已经有临时编号可以引用）。",
    ]
    ws.column_dimensions["A"].width = 100
    for i, line in enumerate(lines, start=1):
        cell = ws.cell(row=i, column=1, value=line)
        cell.alignment = Alignment(wrap_text=False, vertical="top")
    ws.sheet_view.showGridLines = False


def build_teacher_sheet(wb):
    ws = wb.create_sheet("教师")
    headers = ["临时编号*", "手机号*", "姓名*", "角色（默认老师）"]
    ws.append(headers)
    style_header(ws, len(headers))
    ws.append(["T1", "13800000001", "张老师", "老师"])
    ws.append(["T2", "13800000002", "李老师", "管理员"])
    mark_example_row(ws, 2, len(headers))
    mark_example_row(ws, 3, len(headers))
    widths = [10, 16, 12, 18]
    for col, w in zip("ABCD", widths):
        ws.column_dimensions[col].width = w
    note = ws.cell(row=1, column=len(headers) + 2,
                    value="手机号在全平台范围内唯一，不能和其他机构的老师手机号重复，建议用真实手机号。")
    note.alignment = WRAP
    dv = DataValidation(type="list", formula1='"老师,管理员"', allow_blank=True,
                         showDropDown=False)
    dv.error = "请选择\"老师\"或\"管理员\"，留空默认是\"老师\""
    ws.add_data_validation(dv)
    dv.add("D2:D1000")
    return ws


def build_teaching_unit_sheet(wb):
    ws = wb.create_sheet("教学单元")
    headers = ["临时编号*", "名称*", "类型*", "老师编号*\n（引用教师表）",
               "月费\n（仅托管班）", "餐费/天\n（仅托管班）",
               "课时单价\n（仅课外课）", "单次课时时长-分钟\n（仅课外课）"]
    ws.append(headers)
    style_header(ws, len(headers))
    ws.append(["U1", "托管一班", "托管班", "T1", 1500, 15, "", ""])
    ws.append(["U2", "数学思维课", "课外课", "T2", "", "", 80, 60])
    mark_example_row(ws, 2, len(headers))
    mark_example_row(ws, 3, len(headers))
    widths = [10, 16, 10, 14, 10, 12, 10, 18]
    for col, w in zip("ABCDEFGH", widths):
        ws.column_dimensions[col].width = w
    dv = DataValidation(type="list", formula1='"托管班,课外课"', allow_blank=False,
                         showDropDown=False)
    dv.error = "类型只能是\"托管班\"或\"课外课\""
    ws.add_data_validation(dv)
    dv.add("C2:C1000")
    return ws


def build_student_sheet(wb):
    ws = wb.create_sheet("学生")
    headers = ["临时编号*", "姓名*", "学校班级", "所在托管班编号\n（引用教学单元表，托管班类型）",
               "报名课外课编号\n（引用教学单元表，课外课类型，可填多个用逗号分隔）"]
    ws.append(headers)
    style_header(ws, len(headers))
    ws.append(["S1", "小明", "三年级2班", "U1", "U2"])
    ws.append(["S2", "小红", "", "", "U2"])
    mark_example_row(ws, 2, len(headers))
    mark_example_row(ws, 3, len(headers))
    widths = [10, 12, 14, 20, 30]
    for col, w in zip("ABCDE", widths):
        ws.column_dimensions[col].width = w
    return ws


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "机构数据导入模板.xlsx"
    wb = Workbook()
    build_instructions_sheet(wb)
    build_teacher_sheet(wb)
    build_teaching_unit_sheet(wb)
    build_student_sheet(wb)
    wb.save(out_path)
    print(f"模板已生成：{out_path}")
    print("示例行（浅灰色底色的两行）填完真实数据后记得删掉，不要连同示例一起导入。")


if __name__ == "__main__":
    main()
