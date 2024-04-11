package com.dev.gu.repository;

import com.dev.gu.dto.Task;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface TaskRedisRepository extends CrudRepository<Task, String> {

    List<Task> findAllByStatusAndRoomId(Boolean status, String roomId);
}
