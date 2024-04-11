package com.dev.gu.repository;

import com.dev.gu.dto.Progress;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface ProgressRedisRepository extends CrudRepository<Progress, String> {
    List<Progress> findAllByTaskId(String taskId);
}
