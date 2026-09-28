package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    @JsonProperty("isLast")
    private boolean isLast;
    @JsonProperty("isFirst")
    private boolean isFirst;

    public static <T> PagedResponse<T> of(List<T> content, int pageNumber, int pageSize,
                                          long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return PagedResponse.<T>builder()
            .content(content)
            .pageNumber(pageNumber)
            .pageSize(pageSize)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .isLast(pageNumber == totalPages - 1)
            .isFirst(pageNumber == 0)
            .build();
    }
}
