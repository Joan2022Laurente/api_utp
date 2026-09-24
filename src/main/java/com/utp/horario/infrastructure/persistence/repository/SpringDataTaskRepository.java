package com.utp.horario.infrastructure.persistence.repository;

import com.utp.horario.infrastructure.persistence.entity.TaskSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataTaskRepository extends JpaRepository<TaskSyncEntity, String> {
    List<TaskSyncEntity> findByStudentId(String studentId);
}
