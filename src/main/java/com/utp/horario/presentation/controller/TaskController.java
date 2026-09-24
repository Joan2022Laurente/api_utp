package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.TaskSyncItem;
import com.utp.horario.domain.port.in.TaskSyncServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskSyncServicePort taskSyncServicePort;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskSyncItem>>> getTasks(
            @RequestParam(defaultValue = "current-student") String studentId) {
        List<TaskSyncItem> tasks = taskSyncServicePort.getTasksForStudent(studentId);
        return ResponseEntity.ok(ApiResponse.ok(tasks));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<TaskSyncItem>>> syncTasks(
            @RequestParam(defaultValue = "demo-token") String token,
            @RequestParam(defaultValue = "34374") String sectionId) {
        List<TaskSyncItem> synced = taskSyncServicePort.syncTasksFromUtp(token, sectionId);
        return ResponseEntity.ok(ApiResponse.ok("Tareas sincronizadas", synced));
    }

    @PostMapping("/{taskId}/deliver")
    public ResponseEntity<ApiResponse<TaskSyncItem>> markDelivered(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "current-student") String studentId) {
        TaskSyncItem updated = taskSyncServicePort.markTaskAsDelivered(taskId, studentId);
        return ResponseEntity.ok(ApiResponse.ok("Tarea marcada como entregada", updated));
    }
}
