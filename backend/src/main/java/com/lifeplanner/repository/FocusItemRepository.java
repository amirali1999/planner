package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Uses the focus_items table created (inactive) in V1; no schema change was needed to activate it. */
@Repository
public class FocusItemRepository {

    private static final String INSERT_COLUMNS = "id, title, kind, description, goal_id, status, archived_at, created_at, updated_at";
    private static final String COLUMNS = INSERT_COLUMNS + ", position";

    private static final RowMapper<FocusItem> MAPPER = (rs, n) -> new FocusItem(
            rs.getString("id"), rs.getString("title"), FocusKind.fromCode(rs.getString("kind")), rs.getString("description"),
            rs.getString("goal_id"), FocusStatus.fromCode(rs.getString("status")),
            UtcTimestamps.fromDb(rs.getString("archived_at")), UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")),
            nullableInt(rs, "position"));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public FocusItemRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void insert(FocusItem f) {
        // an item linked to a goal goes to the end of that goal's list
        db.sql("INSERT INTO focus_items (" + INSERT_COLUMNS + ", user_id, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + NEXT_POSITION + ")")
                .params(f.id(), f.title(), f.kind().code(), f.description(), f.goalId(), f.status().code(),
                        ts(f.archivedAt()), ts(f.createdAt()), ts(f.updatedAt()), user(), f.goalId(), f.goalId(), user())
                .update();
    }

    public void update(FocusItem f) {
        db.sql("""
                UPDATE focus_items SET title = ?, kind = ?, description = ?, goal_id = ?, status = ?, archived_at = ?, updated_at = ?
                WHERE id = ? AND user_id = ?""")
                .params(f.title(), f.kind().code(), f.description(), f.goalId(), f.status().code(), ts(f.archivedAt()),
                        ts(f.updatedAt()), f.id(), user())
                .update();
    }

    public Optional<FocusItem> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM focus_items WHERE id = ? AND user_id = ?").params(id, user()).query(MAPPER).optional();
    }

    /** All filters optional; order: creation time, id (stable). */
    public List<FocusItem> find(ArchiveFilter archive, String goalId, FocusKind kind, FocusStatus status) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM focus_items WHERE user_id = ? AND ").append(archive.sqlCondition("archived_at"));
        List<Object> params = new ArrayList<>(List.of(user()));
        if (goalId != null) {
            sql.append(" AND goal_id = ?");
            params.add(goalId);
        }
        if (kind != null) {
            sql.append(" AND kind = ?");
            params.add(kind.code());
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.code());
        }
        sql.append(" ORDER BY created_at, id");
        return db.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    /** SQL for "next position in goal ?" (null without goal); parameters: goalId, goalId, userId. */
    private static final String NEXT_POSITION = "CASE WHEN ? IS NULL THEN NULL ELSE "
            + "(SELECT coalesce(max(position), 0) + 1 FROM focus_items WHERE goal_id = ? AND user_id = ?) END";

    /** Moves an item to the end of its (new) goal's list, or clears the position when it has no goal. */
    public void appendToGoal(String id, String goalId) {
        db.sql("UPDATE focus_items SET position = " + NEXT_POSITION + " WHERE id = ? AND user_id = ?")
                .params(goalId, goalId, user(), id, user()).update();
    }

    public void setPosition(String id, int position) {
        db.sql("UPDATE focus_items SET position = ? WHERE id = ? AND user_id = ?").params(position, id, user()).update();
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String ts(Instant i) {
        return i == null ? null : UtcTimestamps.toDb(i);
    }
}
