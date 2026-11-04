package com.smartlib.repository;

import com.smartlib.model.Reflection;
import com.smartlib.model.ReflectionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReflectionRepository extends MongoRepository<Reflection, String> {

    boolean existsByLoanId(String loanId);

    Optional<Reflection> findByLoanId(String loanId);

    List<Reflection> findByStudentIdOrderBySubmissionDateDesc(String studentId);

    List<Reflection> findByStatusOrderBySubmissionDateAsc(ReflectionStatus status);

    long countByStudentIdAndStatus(String studentId, ReflectionStatus status);

    List<Reflection> findByStudentIdAndStatus(String studentId, ReflectionStatus status);

    long countByStatus(ReflectionStatus status);
}
