package com.smartlib.repository;

import com.smartlib.model.Copy;
import com.smartlib.model.CopyStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CopyRepository extends MongoRepository<Copy, String> {
    List<Copy> findByBookId(String bookId);

    long countByBookId(String bookId);
    List<Copy> findByBookIdAndStatus(String bookId, CopyStatus status);
    long countByBookIdAndStatus(String bookId, CopyStatus status);
    long countByStatus(CopyStatus status);
}

