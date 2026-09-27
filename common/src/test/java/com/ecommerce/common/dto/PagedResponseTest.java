package com.ecommerce.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PagedResponse DTO Unit Tests")
class PagedResponseTest {

    @Test
    @DisplayName("Should create PagedResponse with builder")
    void testPagedResponseBuilder() {
        List<String> content = Arrays.asList("item1", "item2", "item3");

        PagedResponse<String> response = PagedResponse.<String>builder()
            .content(content)
            .pageNumber(0)
            .pageSize(3)
            .totalElements(10)
            .totalPages(4)
            .isFirst(true)
            .isLast(false)
            .build();

        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(10);
        assertThat(response.getTotalPages()).isEqualTo(4);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("Should use factory method to create first page")
    void testPagedResponseOfFirstPage() {
        List<String> content = Arrays.asList("item1", "item2");

        PagedResponse<String> response = PagedResponse.of(content, 0, 2, 5);

        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(5);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("Should use factory method to create middle page")
    void testPagedResponseOfMiddlePage() {
        List<String> content = Arrays.asList("item3", "item4");

        PagedResponse<String> response = PagedResponse.of(content, 1, 2, 5);

        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isFalse();
    }

    @Test
    @DisplayName("Should use factory method to create last page")
    void testPagedResponseOfLastPage() {
        List<String> content = Arrays.asList("item5");

        PagedResponse<String> response = PagedResponse.of(content, 2, 2, 5);

        assertThat(response.getPageNumber()).isEqualTo(2);
        assertThat(response.isFirst()).isFalse();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("Should calculate total pages correctly")
    void testTotalPagesCalculation() {
        List<String> content = Collections.emptyList();

        PagedResponse<String> response = PagedResponse.of(content, 0, 10, 25);

        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should handle single page results")
    void testSinglePageResults() {
        List<String> content = Arrays.asList("item1", "item2");

        PagedResponse<String> response = PagedResponse.of(content, 0, 10, 2);

        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty content")
    void testEmptyContent() {
        List<String> content = Collections.emptyList();

        PagedResponse<String> response = PagedResponse.of(content, 0, 10, 0);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
        assertThat(response.getTotalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should support different content types")
    void testPagedResponseWithDifferentTypes() {
        List<Integer> intContent = Arrays.asList(1, 2, 3);
        PagedResponse<Integer> intResponse = PagedResponse.of(intContent, 0, 3, 3);

        assertThat(intResponse.getContent()).isEqualTo(intContent);
        assertThat(intResponse.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should allow setter modifications")
    void testSetterModifications() {
        PagedResponse<String> response = new PagedResponse<>();
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setContent(Arrays.asList("test"));

        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(20);
        assertThat(response.getContent()).contains("test");
    }

    @Test
    @DisplayName("Should handle large total elements")
    void testLargeTotalElements() {
        List<String> content = Collections.emptyList();

        PagedResponse<String> response = PagedResponse.of(content, 0, 50, 1000000);

        assertThat(response.getTotalPages()).isEqualTo(20000);
    }

    @Test
    @DisplayName("Should handle fractional page calculations")
    void testFractionalPageCalculation() {
        List<String> content = Collections.emptyList();

        PagedResponse<String> response = PagedResponse.of(content, 0, 3, 10);

        assertThat(response.getTotalPages()).isEqualTo(4);
    }
}
