package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.TaskNote;
import com.lifeplanner.model.srv.TaskNoteCountSrv;
import com.lifeplanner.model.srv.TaskNoteSrv;
import com.lifeplanner.model.vo.TaskNoteVO;
import com.lifeplanner.repository.TaskNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskNoteService {

    private final TaskNoteRepository notes;
    private final TaskService tasks;
    private final Clock clock;

    public TaskNoteService(TaskNoteRepository notes, TaskService tasks, Clock clock) {
        this.notes = notes;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskNoteSrv> list(String taskId) {
        tasks.get(taskId);
        return notes.findByTask(taskId).stream().map(TaskNoteSrv::from).toList();
    }

    public List<TaskNoteCountSrv> counts() {
        return notes.countByActiveTask();
    }

    @Transactional
    public TaskNoteSrv create(String taskId, TaskNoteVO request) {
        requireActiveTask(taskId);
        Instant now = now();
        TaskNote note = new TaskNote(UUID.randomUUID().toString(), taskId, request.body().strip(), now, now);
        notes.insert(note);
        return TaskNoteSrv.from(note);
    }

    @Transactional
    public TaskNoteSrv update(String noteId, TaskNoteVO request) {
        TaskNote current = find(noteId);
        requireActiveTask(current.taskId());
        TaskNote updated = new TaskNote(noteId, current.taskId(), request.body().strip(), current.createdAt(), now());
        notes.update(updated);
        return TaskNoteSrv.from(updated);
    }

    @Transactional
    public void delete(String noteId) {
        requireActiveTask(find(noteId).taskId());
        notes.delete(noteId);
    }

    private TaskNote find(String noteId) {
        return notes.findById(noteId).orElseThrow(() ->
                new ApiException.NotFoundException("یادداشت پیدا نشد.", Map.of("noteId", noteId)));
    }

    private void requireActiveTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task.isArchived()) {
            throw new ApiException.ConflictException("task_archived", TaskService.MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
