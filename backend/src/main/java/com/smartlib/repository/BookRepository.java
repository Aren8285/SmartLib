package com.smartlib.repository;

import com.smartlib.model.Book;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface BookRepository extends MongoRepository<Book, String> {

    Book findByIsbn(String isbn);

    @Query("{ '$or': [ " +
           "{ 'title': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'author': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'subject': { '$regex': ?0, '$options': 'i' } } " +
           "] }")
    List<Book> searchByQuery(String query);
}

