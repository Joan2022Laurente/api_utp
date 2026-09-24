package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.TaskSyncItem;

import java.util.List;

public interface TaskSyncServicePort {
    List<TaskSyncItem> getTasksForStudent(String studentId);
    List<TaskSyncItem> syncTasksFromUtp(String token, String sectionId);
    TaskSyncItem markTaskAsDelivered(String taskId, String studentId);
}
