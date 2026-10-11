package com.tuoguan.backend.platform.dao;

import com.tuoguan.backend.platform.domain.InstitutionRenewalRecord;
import com.tuoguan.backend.platform.domain.RenewalPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInstitutionRenewalRecordDao implements InstitutionRenewalRecordDao {

    private static final RowMapper<InstitutionRenewalRecord> ROW_MAPPER = (rs, rowNum) -> new InstitutionRenewalRecord(
            rs.getLong("id"),
            rs.getLong("institution_id"),
            RenewalPlan.valueOf(rs.getString("plan")),
            rs.getBigDecimal("amount"),
            rs.getDate("valid_until").toLocalDate(),
            rs.getString("note"),
            rs.getLong("recorded_by_teacher_id"),
            rs.getTimestamp("created_at").toInstant());

    private static final String SELECT_COLUMNS =
            "id, institution_id, plan, amount, valid_until, note, recorded_by_teacher_id, created_at";

    private final JdbcTemplate jdbcTemplate;

    public JdbcInstitutionRenewalRecordDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(Long institutionId, RenewalPlan plan, BigDecimal amount, LocalDate validUntil, String note,
                        Long recordedByTeacherId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO institution_renewal_record "
                            + "(institution_id, plan, amount, valid_until, note, recorded_by_teacher_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, institutionId);
            ps.setString(2, plan.name());
            if (amount != null) {
                ps.setBigDecimal(3, amount);
            } else {
                ps.setNull(3, Types.DECIMAL);
            }
            ps.setDate(4, Date.valueOf(validUntil));
            ps.setString(5, note);
            ps.setLong(6, recordedByTeacherId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public List<InstitutionRenewalRecord> findAllByInstitutionId(Long institutionId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM institution_renewal_record "
                        + "WHERE institution_id = ? ORDER BY created_at DESC, id DESC",
                ROW_MAPPER, institutionId);
    }

    @Override
    public Optional<InstitutionRenewalRecord> findLatestByInstitutionId(Long institutionId) {
        List<InstitutionRenewalRecord> results = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM institution_renewal_record "
                        + "WHERE institution_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                ROW_MAPPER, institutionId);
        return results.stream().findFirst();
    }
}
