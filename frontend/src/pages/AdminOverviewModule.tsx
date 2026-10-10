import { LowBalanceCard } from '../components/LowBalanceCard'
import { UnpaidBillsCard } from '../components/UnpaidBillsCard'
import { EnrollmentCard } from '../components/EnrollmentCard'
import { TodaySnapshotCard } from '../components/TodaySnapshotCard'
import { RevenueCard } from '../components/RevenueCard'

interface AdminOverviewModuleProps {
  onOpenBilling: () => void
}

export function AdminOverviewModule({ onOpenBilling }: AdminOverviewModuleProps) {
  return (
    <div className="p-5">
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <LowBalanceCard />
          <UnpaidBillsCard onOpenBilling={onOpenBilling} />
        </div>
        <div className="space-y-4">
          <EnrollmentCard />
          <TodaySnapshotCard />
          <RevenueCard />
        </div>
      </div>
    </div>
  )
}
