package com.utp.horario.application.usecase;

import com.utp.horario.domain.model.TaskSyncItem;
import com.utp.horario.domain.port.in.TaskSyncServicePort;
import com.utp.horario.domain.port.out.TaskSyncRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskSyncServiceImpl implements TaskSyncServicePort {

    private final TaskSyncRepositoryPort taskSyncRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;

    @Override
    public List<TaskSyncItem> getTasksForStudent(String studentId) {
        List<TaskSyncItem> tasks = taskSyncRepositoryPort.findByStudentId(studentId);
        if (tasks.isEmpty()) {
            tasks = syncTasksFromUtp("demo-token", "34374");
        }
        return tasks;
    }

    @Override
    public List<TaskSyncItem> syncTasksFromUtp(String token, String sectionId) {
        List<TaskSyncItem> fetched = utpPortalGatewayPort.fetchTasks(token, sectionId);
        return taskSyncRepositoryPort.saveAll(fetched);
    }

    @Override
    public TaskSyncItem markTaskAsDelivered(String taskId, String studentId) {
        TaskSyncItem item = taskSyncRepositoryPort.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada: " + taskId));

        TaskSyncItem updated = TaskSyncItem.builder()
                .id(item.getId())
                .courseName(item.getCourseName())
                .sectionId(item.getSectionId())
                .homeworkId(item.getHomeworkId())
                .title(item.getTitle())
                .type(item.getType())
                .week(item.getWeek())
                .homeworkStatus("DELIVERED")
                .assignmentProgress("FINISHED")
                .dueDate(item.getDueDate())
                .deliveredDate(LocalDateTime.now())
                .maxScore(item.getMaxScore())
                .score(item.getScore())
                .isDelivered(true)
                .build();

        return taskSyncRepositoryPort.save(updated);
    }
}
