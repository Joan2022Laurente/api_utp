package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.TaskSyncItem;

import java.util.List;
import java.util.Optional;

public interface TaskSyncRepositoryPort {
    TaskSyncItem save(TaskSyncItem task);
    List<TaskSyncItem> saveAll(List<TaskSyncItem> tasks);
    List<TaskSyncItem> findByStudentId(String studentId);
    Optional<TaskSyncItem> findById(String id);
}
