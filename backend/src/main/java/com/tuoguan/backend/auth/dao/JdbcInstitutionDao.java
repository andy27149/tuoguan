package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Institution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInstitutionDao implements InstitutionDao {

    private static final RowMapper<Institution> ROW_MAPPER = (rs, rowNum) -> new Institution(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("logo_object_key"),
            rs.getBoolean("custody_enabled"),
            rs.getBoolean("off_campus_enabled"),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            rs.getString("address"),
            rs.getDate("valid_until") != null ? rs.getDate("valid_until").toLocalDate() : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("bill_tuition_in_advance"));

    private static final String SELECT_COLUMNS =
            "id, name, logo_object_key, custody_enabled, off_campus_enabled, "
                    + "contact_name, contact_phone, address, valid_until, created_at, bill_tuition_in_advance";

    private final JdbcTemplate jdbcTemplate;

    public JdbcInstitutionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO institution (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    @Override
    public Optional<Institution> findById(Long id) {
        List<Institution> results = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM institution WHERE id = ?",
                ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    @Override
    public List<Institution> findAll() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM institution ORDER BY created_at",
                ROW_MAPPER);
    }

    @Override
    public void updateName(Long id, String name) {
        jdbcTemplate.update("UPDATE institution SET name = ? WHERE id = ?", name, id);
    }

    @Override
    public void updateLogoObjectKey(Long id, String logoObjectKey) {
        jdbcTemplate.update("UPDATE institution SET logo_object_key = ? WHERE id = ?", logoObjectKey, id);
    }

    @Override
    public void updateFeatureFlags(Long id, boolean custodyEnabled, boolean offCampusEnabled) {
        jdbcTemplate.update("UPDATE institution SET custody_enabled = ?, off_campus_enabled = ? WHERE id = ?",
                custodyEnabled, offCampusEnabled, id);
    }

    @Override
    public void updateContact(Long id, String contactName, String contactPhone, String address) {
        jdbcTemplate.update(
                "UPDATE institution SET contact_name = ?, contact_phone = ?, address = ? WHERE id = ?",
                contactName, contactPhone, address, id);
    }

    @Override
    public void updateValidUntil(Long id, java.time.LocalDate validUntil) {
        jdbcTemplate.update("UPDATE institution SET valid_until = ? WHERE id = ?",
                validUntil != null ? Date.valueOf(validUntil) : null, id);
    }

    @Override
    public void updateBillTuitionInAdvance(Long id, boolean billTuitionInAdvance) {
        jdbcTemplate.update("UPDATE institution SET bill_tuition_in_advance = ? WHERE id = ?",
                billTuitionInAdvance, id);
    }
}
