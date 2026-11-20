package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.TaskNote;
import com.lifeplanner.model.srv.TaskNoteCountSrv;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskNoteRepository {

    private static final String COLUMNS = "n.id, n.task_id, n.body, n.created_at, n.updated_at";
    private static final String OWNED = " FROM task_notes n JOIN tasks t ON t.id = n.task_id WHERE t.user_id = ?";
    private static final String OWNED_TASK = "task_id IN (SELECT id FROM tasks WHERE user_id = ?)";
    private static final RowMapper<TaskNote> MAPPER = (rs, row) -> new TaskNote(
            rs.getString("id"), rs.getString("task_id"), rs.getString("body"),
            UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public TaskNoteRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    public List<TaskNote> findByTask(String taskId) {
        return db.sql("SELECT " + COLUMNS + OWNED + " AND n.task_id = ? ORDER BY n.created_at DESC, n.id DESC")
                .params(currentUser.id(), taskId).query(MAPPER).list();
    }

    public Optional<TaskNote> findById(String noteId) {
        return db.sql("SELECT " + COLUMNS + OWNED + " AND n.id = ?")
                .params(currentUser.id(), noteId).query(MAPPER).optional();
    }

    public List<TaskNoteCountSrv> countByActiveTask() {
        return db.sql("SELECT n.task_id, count(*) AS note_count" + OWNED + " AND t.archived_at IS NULL GROUP BY n.task_id")
                .param(currentUser.id())
                .query((rs, row) -> new TaskNoteCountSrv(rs.getString("task_id"), rs.getInt("note_count"))).list();
    }

    public void insert(TaskNote note) {
        db.sql("""
                INSERT INTO task_notes (id, task_id, body, created_at, updated_at)
                SELECT ?, id, ?, ?, ? FROM tasks WHERE id = ? AND user_id = ?
                """).params(note.id(), note.body(), UtcTimestamps.toDb(note.createdAt()), UtcTimestamps.toDb(note.updatedAt()),
                        note.taskId(), currentUser.id()).update();
    }

    public void update(TaskNote note) {
        db.sql("UPDATE task_notes SET body = ?, updated_at = ? WHERE id = ? AND " + OWNED_TASK)
                .params(note.body(), UtcTimestamps.toDb(note.updatedAt()), note.id(), currentUser.id()).update();
    }

    public void delete(String noteId) {
        db.sql("DELETE FROM task_notes WHERE id = ? AND " + OWNED_TASK)
                .params(noteId, currentUser.id()).update();
    }
}
