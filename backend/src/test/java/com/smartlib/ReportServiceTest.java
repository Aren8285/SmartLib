package com.smartlib;

import com.smartlib.config.AppProperties;
import com.smartlib.dto.ReportDtos.FinesReport;
import com.smartlib.dto.ReportDtos.PopularBook;
import com.smartlib.dto.ReportDtos.WaitlistReport;
import com.smartlib.model.FineReason;
import com.smartlib.model.FineStatus;
import com.smartlib.model.LoanStatus;
import com.smartlib.repository.BookRepository;
import com.smartlib.repository.CopyRepository;
import com.smartlib.repository.LoanRepository;
import com.smartlib.repository.WaitlistRepository;
import com.smartlib.service.ReportService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private LoanRepository loanRepository;
    private BookRepository bookRepository;
    private CopyRepository copyRepository;
    private WaitlistRepository waitlistRepository;
    private MongoTemplate mongoTemplate;
    private ReportService reportService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        loanRepository = mock(LoanRepository.class);
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(CopyRepository.class);
        waitlistRepository = mock(WaitlistRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        reportService = new ReportService(loanRepository, bookRepository, copyRepository,
                waitlistRepository, mongoTemplate, new AppProperties());

        when(loanRepository.findByStatus(eq(LoanStatus.OVERDUE), any())).thenReturn(List.of());
        AggregationResults<Document> empty = mock(AggregationResults.class);
        when(empty.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class)))
                .thenReturn(empty);
        when(bookRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void csvExportsHaveStableHeadersAndFilenames() {
        String overdue = reportService.exportCsv("overdue", null, null).content();
        assertThat(overdue).startsWith("Student,Book,Due date,Days late,Accrued fine\n");
        assertThat(reportService.exportCsv("overdue", null, null).filename())
                .startsWith("smartlib-overdue-").endsWith(".csv");

        assertThat(reportService.exportCsv("fines", null, null).content())
                .startsWith("Reason,Count,Imposed,Collected,Outstanding\n");
        assertThat(reportService.exportCsv("popular", null, null).content())
                .startsWith("Rank,Title,Author,Subject,Borrows\n");
        assertThat(reportService.exportCsv("inventory", null, null).content())
                .startsWith("Book,Author,Subject,Total copies,Available copies\n");
    }

    @Test
    void csvRejectsAnUnknownExportType() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reportService.exportCsv("everything", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown export type");
    }

    @Test
    @SuppressWarnings("unchecked")
    void finesReportAggregatesImposedCollectedAndOutstandingByReason() {
        Document pending = new Document("_id",
                new Document("reason", "LATE_RETURN").append("status", "PENDING"))
                .append("count", 2).append("total", 40.0);
        Document paid = new Document("_id",
                new Document("reason", "LATE_RETURN").append("status", "PAID"))
                .append("count", 1).append("total", 10.0);
        Document damaged = new Document("_id",
                new Document("reason", "DAMAGED_BOOK").append("status", "PENDING"))
                .append("count", 1).append("total", 200.0);

        AggregationResults<Document> results = mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of(pending, paid, damaged));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("fines"), eq(Document.class)))
                .thenReturn(results);

        FinesReport report = reportService.fines(null, null);

        assertThat(report.getTotalImposed()).isEqualTo(250.0);
        assertThat(report.getTotalCollected()).isEqualTo(10.0);
        assertThat(report.getTotalOutstanding()).isEqualTo(240.0);
        assertThat(report.getByReason()).hasSize(2);
        assertThat(report.getByReason())
                .filteredOn(row -> row.getReason().equals(FineReason.LATE_RETURN.name()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCount()).isEqualTo(3);
                    assertThat(row.getImposed()).isEqualTo(50.0);
                    assertThat(row.getCollected()).isEqualTo(10.0);
                    assertThat(row.getOutstanding()).isEqualTo(40.0);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void popularBooksReturnsRankedBooksFromTheAggregation() {
        Document book = new Document("title", "Clean Code")
                .append("author", "Robert C. Martin")
                .append("subject", "Computer Science");
        Document row = new Document("_id", new ObjectId())
                .append("borrowCount", 7)
                .append("book", book);

        AggregationResults<Document> results = mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of(row));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("loans"), eq(Document.class)))
                .thenReturn(results);

        List<PopularBook> popular = reportService.popularBooks(null, null, 10);

        assertThat(popular).hasSize(1);
        assertThat(popular.get(0).getRank()).isEqualTo(1);
        assertThat(popular.get(0).getTitle()).isEqualTo("Clean Code");
        assertThat(popular.get(0).getBorrowCount()).isEqualTo(7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void waitlistReportCountsCurrentQueuesAndFulfilledAverageWait() {
        Document queue = new Document("_id", new ObjectId())
                .append("queueLength", 3)
                .append("book", new Document("title", "Discrete Mathematics"));
        Document average = new Document("_id", new ObjectId())
                .append("avgWaitMs", 2 * 3_600_000.0);
        Document overall = new Document("_id", null)
                .append("avgWaitMs", 2 * 3_600_000.0);

        AggregationResults<Document> queueResults = mock(AggregationResults.class);
        when(queueResults.getMappedResults()).thenReturn(List.of(queue));
        AggregationResults<Document> avgResults = mock(AggregationResults.class);
        when(avgResults.getMappedResults()).thenReturn(List.of(average));
        AggregationResults<Document> overallResults = mock(AggregationResults.class);
        when(overallResults.getMappedResults()).thenReturn(List.of(overall));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("waitlist"), eq(Document.class)))
                .thenReturn(queueResults, avgResults, overallResults);

        WaitlistReport report = reportService.waitlist(null, null);

        assertThat(report.getBooks()).hasSize(1);
        assertThat(report.getBooks().get(0).getQueueLength()).isEqualTo(3);
        assertThat(report.getOverallAverageWaitHours()).isEqualTo(2.0);
    }
}
