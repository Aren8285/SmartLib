package com.smartlib;

import com.smartlib.dto.BookResponse;
import com.smartlib.dto.ShelfRequestDto;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.Book;
import com.smartlib.model.Copy;
import com.smartlib.model.Shelf;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.ShelfRepository;
import com.smartlib.service.BookService;
import com.smartlib.service.ShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShelfServiceTest {

    private ShelfRepository shelfRepository;
    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private BookService bookService;
    private ShelfService shelfService;

    private Shelf shelf;
    private ShelfRequestDto dto;

    @BeforeEach
    void setup() {
        shelfRepository = mock(ShelfRepository.class);
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        bookService = mock(BookService.class);
        shelfService = new ShelfService(shelfRepository, bookRepository, copyRepository, bookService);

        shelf = Shelf.builder().id("shelf1").code("A1").section("Computer Science")
                .row(1).column(1).capacity(2).build();
        dto = new ShelfRequestDto();
        dto.setCode("a1");
        dto.setSection("Computer Science");
        dto.setRow(1);
        dto.setColumn(1);
        dto.setCapacity(12);
    }

    @Test
    void create_normalisesCodeAndRejectsDuplicateCode() {
        when(shelfRepository.existsByCode("A1")).thenReturn(true);

        assertThatThrownBy(() -> shelfService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
        verify(shelfRepository, never()).save(any(Shelf.class));
    }

    @Test
    void create_rejectsAnOccupiedGridPosition() {
        when(shelfRepository.existsByCode("A1")).thenReturn(false);
        when(shelfRepository.existsByRowAndColumn(1, 1)).thenReturn(true);

        assertThatThrownBy(() -> shelfService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already occupies row 1, column 1");
        verify(shelfRepository, never()).save(any(Shelf.class));
    }

    @Test
    void delete_nonEmptyShelfIsRejected() {
        when(shelfRepository.findById("shelf1")).thenReturn(Optional.of(shelf));
        when(bookRepository.countByShelfId("shelf1")).thenReturn(2L);

        assertThatThrownBy(() -> shelfService.delete("shelf1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still holds 2 book");
        verify(shelfRepository, never()).delete(any(Shelf.class));
    }

    @Test
    void delete_emptyShelfSucceeds() {
        when(shelfRepository.findById("shelf1")).thenReturn(Optional.of(shelf));
        when(bookRepository.countByShelfId("shelf1")).thenReturn(0L);

        shelfService.delete("shelf1");

        verify(shelfRepository).delete(shelf);
    }

    @Test
    void update_capacityBelowCurrentBookCountIsRejected() {
        when(shelfRepository.findById("shelf1")).thenReturn(Optional.of(shelf));
        when(shelfRepository.findByCode("A1")).thenReturn(Optional.of(shelf));
        when(shelfRepository.findAll()).thenReturn(List.of(shelf));
        when(bookRepository.countByShelfId("shelf1")).thenReturn(5L);
        dto.setCapacity(4);

        assertThatThrownBy(() -> shelfService.update("shelf1", dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Capacity cannot be lower");
        verify(shelfRepository, never()).save(any(Shelf.class));
    }

    @Test
    void assignBook_fullShelfIsRejected() {
        Book book = Book.builder().id("book1").title("Clean Code").shelfId(null).build();
        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(shelfRepository.findById("shelf1")).thenReturn(Optional.of(shelf));
        when(bookRepository.countByShelfId("shelf1")).thenReturn(2L);

        assertThatThrownBy(() -> shelfService.assignBook("book1", "shelf1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is full (2/2)");
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void assignBook_movesBookAndAllItsCopies() {
        Book book = Book.builder().id("book1").title("Clean Code").shelfId(null).build();
        Copy copy1 = Copy.builder().id("copy1").bookId("book1").build();
        Copy copy2 = Copy.builder().id("copy2").bookId("book1").build();
        BookResponse response = new BookResponse();
        response.setId("book1");
        response.setShelfId("shelf1");

        when(bookRepository.findById("book1")).thenReturn(Optional.of(book));
        when(shelfRepository.findById("shelf1")).thenReturn(Optional.of(shelf));
        when(bookRepository.countByShelfId("shelf1")).thenReturn(0L);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(copyRepository.findByBookId("book1")).thenReturn(List.of(copy1, copy2));
        when(bookService.toResponse(book)).thenReturn(response);

        BookResponse result = shelfService.assignBook("book1", "shelf1");

        assertThat(result.getShelfId()).isEqualTo("shelf1");
        assertThat(book.getShelfId()).isEqualTo("shelf1");
        assertThat(copy1.getShelfId()).isEqualTo("shelf1");
        assertThat(copy2.getShelfId()).isEqualTo("shelf1");
        verify(copyRepository).saveAll(List.of(copy1, copy2));
    }
}
