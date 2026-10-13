import { createPortal } from 'react-dom'
import { useEscapeToClose } from '../hooks/useEscapeToClose'

interface HelpModalProps {
  role: 'admin' | 'teacher'
  onClose: () => void
}

// 内容来自 docs/training/操作培训手册.html 的文字部分（不含截图——用户已经在看真实
// 界面，没必要在帮助弹窗里再看一遍自己的截图）。这是刻意的重复维护：改了任何一处涉及
// 的功能流程，都要同时检查并更新另一处，见该 html 文件顶部的维护提示注释。
export function HelpModal({ role, onClose }: HelpModalProps) {
  useEscapeToClose(onClose)

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        className="flex max-h-[85vh] w-full max-w-xl flex-col overflow-hidden rounded-2xl border border-panel-border bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="帮助"
      >
        <div className="flex items-center justify-between gap-2 border-b border-panel-border bg-gradient-to-r from-[#4F86F7]/10 to-[#9B6BFF]/10 p-4">
          <div className="flex items-center gap-2">
            <span className="text-xl" aria-hidden="true">
              💡
            </span>
            <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">
              {role === 'admin' ? '机构管理员帮助' : '教师帮助'}
            </h2>
          </div>
          <button
            type="button"
            aria-label="关闭弹窗"
            onClick={onClose}
            className="rounded-lg px-1.5 py-0.5 text-xl leading-none text-panel-muted hover:bg-white/60 hover:text-panel-ink"
          >
            ×
          </button>
        </div>
        <div className="space-y-3 overflow-y-auto bg-[#f7f8fc] p-4 text-sm text-[#565b7a]">
          {role === 'admin' ? <AdminHelpContent /> : <TeacherHelpContent />}
        </div>
        <div className="flex justify-end border-t border-panel-border p-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-panel-border px-3 py-1 text-sm text-[#5d5480] hover:bg-[#faf7ff]"
          >
            关闭
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

function HelpStep({ icon, title, children }: { icon: string; title: string; children: React.ReactNode }) {
  return (
    <section className="flex gap-3 rounded-xl border border-panel-border bg-white p-3 shadow-sm">
      <span
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#4F86F7] to-[#9B6BFF] text-base"
        aria-hidden="true"
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1 space-y-1">
        <h3 className="font-semibold text-panel-ink">{title}</h3>
        <div className="space-y-1">{children}</div>
      </div>
    </section>
  )
}

function HelpSectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h3 className="flex items-center gap-1.5 pt-2 font-semibold text-panel-ink">
      <span aria-hidden="true">❓</span>
      {children}
    </h3>
  )
}

function HelpFaq({ question, children }: { question: string; children: React.ReactNode }) {
  return (
    <details className="group rounded-xl border border-panel-border bg-white p-3 shadow-sm open:border-[#9B6BFF]/40">
      <summary className="flex cursor-pointer list-none items-start gap-2 font-medium text-panel-ink marker:content-none">
        <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#4F86F7]/15 text-xs font-bold text-[#4F86F7]">
          Q
        </span>
        <span>{question}</span>
      </summary>
      <p className="mt-2 pl-7">{children}</p>
    </details>
  )
}

function AdminHelpContent() {
  return (
    <>
      <HelpStep icon="🔑" title="第一次登录：改密码">
        <p>
          平台方会给你一个手机号和初始密码。第一次登录系统会强制跳到"修改密码"页面，必须改完密码才能进系统。<strong>系统目前没有忘记密码后的找回/重置功能，设置时一定要记牢或用密码管理工具保存。</strong>
        </p>
      </HelpStep>
      <HelpStep icon="🏫" title="把机构基础信息配好">
        <p>左侧「基础配置」填机构名称、传 Logo。系统默认同时支持托管和课外课，不需要额外开通。</p>
      </HelpStep>
      <HelpStep icon="👩‍🏫" title="建教师账号">
        <p>
          左侧「教师列表」→「新增教师」，填手机号、姓名、初始密码，角色选「教师」（也要管后台就选「管理员」）。初始密码只明文显示一次，记得马上告诉对方。
        </p>
      </HelpStep>
      <HelpStep icon="📚" title="建教学单元（托管班 / 课外课）">
        <p>左侧「教学单元」：托管班填名字+负责教师；课外课填名字+时长+负责教师，单价可以先空着在定价中心补。</p>
      </HelpStep>
      <HelpStep icon="🧒" title="录入学生">
        <p>
          左侧「学生总览」→「新建学生」，选托管班（留空=纯课外课学生）、可多选报名课外课。建好会自动生成家长分享链接。
        </p>
      </HelpStep>
      <HelpStep icon="💰" title="设置定价">
        <p>左侧「定价中心」：托管学费+餐费（可批量套用全部班级）、课外课单价。托管班没设学费会导致生成账单失败。</p>
      </HelpStep>
      <HelpStep icon="📊" title="日常先看「机构总览」">
        <p>默认落地页，5 张卡片：课时不足预警、账单欠费提醒、在读规模、今日运营快照、收入快照。</p>
      </HelpStep>
      <HelpStep icon="📋" title="学生总览：管全机构学生的地方">
        <p>
          全机构学生列表，按姓名搜索、按身份筛选。只要报了课外课的学生（不论是否同时在托管班）都有「对账单/充值」按钮。
        </p>
      </HelpStep>
      <HelpStep icon="🧾" title="账单管理：怎么收钱">
        <p>
          月底生成账单，可单班级生成，也可以「一键生成本月账单」批量生成——范围是当前筛选出来
          的学生，不筛选就是全机构（含纯课外课学生）。账单=学费+餐费+课外课费用；课外课部分先扣
          充值余额，不够的部分自动计入账单，不用手动区分。
        </p>
      </HelpStep>

      <HelpSectionTitle>常见问题</HelpSectionTitle>
      <HelpFaq question="忘记密码了怎么办？">
        目前系统里没有"忘记密码"的自助找回或重置入口，教师列表里也没有重置按钮，需要联系技术支持处理。建议用密码管理工具保存好密码。
      </HelpFaq>
      <HelpFaq question="一个学生既在托管班又报了课外课，算哪种？">
        两种身份都算（"双重身份学生"）。月度账单里会同时有托管学费/餐费和课外课费用。
      </HelpFaq>
      <HelpFaq question="关掉托管或课外课开关，原来的数据会被删掉吗？">
        不会。开关只隐藏对应菜单入口，已有数据都还在，重新打开就能看到。
      </HelpFaq>
      <HelpFaq question="纯课外课学生也要生成账单吗？">
        要。学费/餐费是 0，只统计当月未被充值覆盖的课外课费用，不加筛选条件一键生成时会包含这类学生。
      </HelpFaq>
    </>
  )
}

function TeacherHelpContent() {
  return (
    <>
      <HelpStep icon="🔑" title="第一次登录：改密码">
        <p>
          管理员会给你一个手机号和初始密码，第一次登录会强制要求改密码，改完才能进系统。<strong>系统目前没有忘记密码后的找回/重置功能，设置时一定要记牢。</strong>
        </p>
      </HelpStep>
      <HelpStep icon="📋" title="托管看板：每天的主战场">
        <p>
          班级只能由管理员在后台创建——没有班不是自己建，是联系管理员建班并指定给你。日常操作：布置任务（会自动同步给同学籍班其他学生）、打卡、打勾完成、评分+点评、用餐/请假登记、放学确认。
        </p>
      </HelpStep>
      <HelpStep icon="✅" title="课外课：点名消课">
        <p>
          顶部点「消课」：选课程+日期（不能选未来） → 花名册默认全勾，没来的手动取消 →
          余额不足会标红但不阻断消课 → 点「确认消课」会先二次确认，<strong>这一步不可撤销</strong>。
        </p>
      </HelpStep>
      <HelpStep icon="🔗" title="把分享链接/海报发给家长">
        <p>学生卡片上的 🔗「链接」和 🖼「海报」按钮，发给家长，家长不用注册登录就能看。</p>
      </HelpStep>

      <HelpSectionTitle>常见问题</HelpSectionTitle>
      <HelpFaq question="忘记密码了怎么办？">
        目前系统里没有"忘记密码"的自助找回或重置入口，管理员那边也没有重置按钮，需要联系技术支持处理。建议用密码管理工具保存好密码。
      </HelpFaq>
      <HelpFaq question="看板上没有班级怎么办？">
        找管理员建班或分配课程——教学单元现在都只能由管理员创建，教师没有自建入口。
      </HelpFaq>
      <HelpFaq question="消课点错了，能撤销吗？">
        不能直接撤销，所以才会在确认消课前弹二次确认。真的点错了找管理员在后台处理。
      </HelpFaq>
      <HelpFaq question="消课到底扣谁的钱、扣多少？">
        不用操心——系统自动判断：充过值先扣余额，没充值/不够的部分月底自动算进账单，照实点名就行。
      </HelpFaq>
      <HelpFaq question="同一个学生，是托管班学生还是课外课学生？">
        不需要区分，花名册统一按姓名排序显示全部学生，点名方式完全一样。
      </HelpFaq>
    </>
  )
}
