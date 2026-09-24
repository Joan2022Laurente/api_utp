package com.utp.horario.infrastructure.persistence.adapter;

import com.utp.horario.domain.model.TaskSyncItem;
import com.utp.horario.domain.port.out.TaskSyncRepositoryPort;
import com.utp.horario.infrastructure.persistence.entity.TaskSyncEntity;
import com.utp.horario.infrastructure.persistence.repository.SpringDataTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TaskSyncRepositoryAdapter implements TaskSyncRepositoryPort {

    private final SpringDataTaskRepository repository;

    @Override
    public TaskSyncItem save(TaskSyncItem task) {
        TaskSyncEntity entity = toEntity(task);
        TaskSyncEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<TaskSyncItem> saveAll(List<TaskSyncItem> tasks) {
        List<TaskSyncEntity> entities = tasks.stream().map(this::toEntity).toList();
        return repository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TaskSyncItem> findByStudentId(String studentId) {
        return repository.findByStudentId(studentId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<TaskSyncItem> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    private TaskSyncEntity toEntity(TaskSyncItem d) {
        return TaskSyncEntity.builder()
                .id(d.getId())
                .studentId("current-student")
                .courseName(d.getCourseName())
                .sectionId(d.getSectionId())
                .homeworkId(d.getHomeworkId())
                .title(d.getTitle())
                .type(d.getType())
                .week(d.getWeek())
                .homeworkStatus(d.getHomeworkStatus())
                .assignmentProgress(d.getAssignmentProgress())
                .dueDate(d.getDueDate())
                .deliveredDate(d.getDeliveredDate())
                .maxScore(d.getMaxScore())
                .score(d.getScore())
                .isDelivered(d.getIsDelivered())
                .build();
    }

    private TaskSyncItem toDomain(TaskSyncEntity e) {
        return TaskSyncItem.builder()
                .id(e.getId())
                .courseName(e.getCourseName())
                .sectionId(e.getSectionId())
                .homeworkId(e.getHomeworkId())
                .title(e.getTitle())
                .type(e.getType())
                .week(e.getWeek())
                .homeworkStatus(e.getHomeworkStatus())
                .assignmentProgress(e.getAssignmentProgress())
                .dueDate(e.getDueDate())
                .deliveredDate(e.getDeliveredDate())
                .maxScore(e.getMaxScore())
                .score(e.getScore())
                .isDelivered(e.getIsDelivered())
                .build();
    }
}
