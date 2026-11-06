package com.smartlib.repository;

import com.smartlib.model.Shelf;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShelfRepository extends MongoRepository<Shelf, String> {

    Optional<Shelf> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByRowAndColumn(int row, int column);

    List<Shelf> findAllByOrderByRowAscColumnAsc();
}
