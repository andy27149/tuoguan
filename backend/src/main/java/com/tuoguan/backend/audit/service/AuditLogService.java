package com.tuoguan.backend.audit.service;

import com.tuoguan.backend.audit.dao.AuditLogDao;
import org.springframework.stereotype.Service;

/**
 * 最小版操作审计：只记录"谁在什么时候对什么做了什么"，不提供查询/展示 UI。
 * 覆盖范围限定在少数高风险、已有二次确认的写操作（删除教师、账单缴费状态、
 * 停用教学单元/学生、批量设置定价、消课移出花名册），不做全站写操作审计。
 */
@Service
public class AuditLogService {

    private final AuditLogDao auditLogDao;

    public AuditLogService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void record(Long institutionId, Long actorTeacherId, String action, String targetType, Long targetId,
                        String detail) {
        auditLogDao.insert(institutionId, actorTeacherId, action, targetType, targetId, detail);
    }
}
