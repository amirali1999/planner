package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.DecimalCodec;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.CheckIn;
import com.lifeplanner.model.KeyResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Key Results and their append-only check-ins. There is intentionally no update/delete for check-ins. */
@Repository
public class KeyResultRepository {

    /** KR plus its latest check-in by the domain ordering (measured_at, created_at, id). */
    private static final String SELECT_WITH_LATEST = """
            SELECT kr.id, kr.goal_id, kr.name, kr.unit, kr.baseline, kr.target, kr.weight,
                   kr.archived_at, kr.created_at, kr.updated_at,
                   c.value AS latest_value, c.measured_at AS latest_measured_at
            FROM key_results kr
            JOIN goals g ON g.id = kr.goal_id
            LEFT JOIN key_result_checkins c ON c.id = (
                SELECT c2.id FROM key_result_checkins c2
                WHERE c2.key_result_id = kr.id
                ORDER BY c2.measured_at DESC, c2.created_at DESC, c2.id DESC
                LIMIT 1)
            """;

    private static final RowMapper<KeyResult> KR_MAPPER = (rs, n) -> new KeyResult(
            rs.getString("id"),
            rs.getString("goal_id"),
            rs.getString("name"),
            rs.getString("unit"),
            DecimalCodec.fromDb(rs.getString("baseline")),
            DecimalCodec.fromDb(rs.getString("target")),
            DecimalCodec.fromDb(rs.getString("weight")),
            UtcTimestamps.fromDb(rs.getString("archived_at")),
            UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")),
            DecimalCodec.fromDb(rs.getString("latest_value")),
            UtcTimestamps.fromDb(rs.getString("latest_measured_at")));

    private static final RowMapper<CheckIn> CHECK_IN_MAPPER = (rs, n) -> new CheckIn(
            rs.getString("id"),
            rs.getString("key_result_id"),
            DecimalCodec.fromDb(rs.getString("value")),
            UtcTimestamps.fromDb(rs.getString("measured_at")),
            rs.getString("note"),
            UtcTimestamps.fromDb(rs.getString("created_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public KeyResultRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void insert(KeyResult kr) {
        db.sql("""
                INSERT INTO key_results (id, goal_id, name, unit, baseline, target, weight, archived_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(kr.id(), kr.goalId(), kr.name(), kr.unit(),
                        DecimalCodec.toDb(kr.baseline()), DecimalCodec.toDb(kr.target()), DecimalCodec.toDb(kr.weight()),
                        kr.archivedAt() == null ? null : UtcTimestamps.toDb(kr.archivedAt()),
                        UtcTimestamps.toDb(kr.createdAt()), UtcTimestamps.toDb(kr.updatedAt()))
                .update();
    }

    /** Mutable columns only; check-ins are never touched (append-only history stays as recorded). */
    public void update(KeyResult kr) {
        db.sql("""
                UPDATE key_results SET name = ?, unit = ?, baseline = ?, target = ?, weight = ?, archived_at = ?, updated_at = ?
                WHERE id = ? AND goal_id IN (SELECT id FROM goals WHERE user_id = ?)""")
                .params(kr.name(), kr.unit(), DecimalCodec.toDb(kr.baseline()), DecimalCodec.toDb(kr.target()),
                        DecimalCodec.toDb(kr.weight()), kr.archivedAt() == null ? null : UtcTimestamps.toDb(kr.archivedAt()),
                        UtcTimestamps.toDb(kr.updatedAt()), kr.id(), user())
                .update();
    }

    public Optional<KeyResult> findById(String id) {
        return db.sql(SELECT_WITH_LATEST + " WHERE kr.id = ? AND g.user_id = ?").params(id, user()).query(KR_MAPPER).optional();
    }

    public List<KeyResult> findByGoal(String goalId, boolean includeArchived) {
        String archivedFilter = includeArchived ? "" : " AND kr.archived_at IS NULL";
        return db.sql(SELECT_WITH_LATEST + " WHERE kr.goal_id = ? AND g.user_id = ?" + archivedFilter + " ORDER BY kr.created_at, kr.id")
                .params(goalId, user()).query(KR_MAPPER).list();
    }

    /** Active KRs (with latest check-in) of many goals in ONE query, for batched progress (tree, dashboard). */
    public List<KeyResult> findActiveByGoals(Collection<String> goalIds) {
        if (goalIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(goalIds.size(), "?"));
        List<Object> params = new java.util.ArrayList<>(List.of(user()));
        params.addAll(goalIds);
        return db.sql(SELECT_WITH_LATEST + " WHERE g.user_id = ? AND kr.archived_at IS NULL AND kr.goal_id IN (" + placeholders + ")"
                        + " ORDER BY kr.created_at, kr.id")
                .params(params).query(KR_MAPPER).list();
    }

    public void insertCheckIn(CheckIn c) {
        db.sql("""
                INSERT INTO key_result_checkins (id, key_result_id, value, measured_at, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""")
                .params(c.id(), c.keyResultId(), DecimalCodec.toDb(c.value()), UtcTimestamps.toDb(c.measuredAt()),
                        c.note(), UtcTimestamps.toDb(c.createdAt()))
                .update();
    }

    /** Full history in chronological order; the last element is the one that drives {@code current}. */
    public List<CheckIn> findCheckIns(String keyResultId) {
        return db.sql("""
                SELECT c.id, c.key_result_id, c.value, c.measured_at, c.note, c.created_at
                FROM key_result_checkins c
                JOIN key_results kr ON kr.id = c.key_result_id
                JOIN goals g ON g.id = kr.goal_id
                WHERE c.key_result_id = ? AND g.user_id = ?
                ORDER BY c.measured_at, c.created_at, c.id""")
                .params(keyResultId, user()).query(CHECK_IN_MAPPER).list();
    }
}
