package com.smartlib.repository;

import com.smartlib.model.Fine;
import com.smartlib.model.FineStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FineRepository extends MongoRepository<Fine, String> {

    List<Fine> findByStudentIdOrderByIssueDateDesc(String studentId);

    List<Fine> findByStatusOrderByIssueDateDesc(FineStatus status);

    long countByStudentIdAndStatus(String studentId, FineStatus status);
}
