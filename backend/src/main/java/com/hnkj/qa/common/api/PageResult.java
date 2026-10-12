package com.hnkj.qa.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页结果。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 总页数 */
    private int pages;

    /**
     * 由 Spring Data 的 Page 转换而来，并支持元素映射。
     */
    public static <E, T> PageResult<T> of(Page<E> source, Function<E, T> mapper) {
        List<T> records = source.getContent().stream().map(mapper).toList();
        return new PageResult<>(
                records,
                source.getTotalElements(),
                source.getNumber() + 1,
                source.getSize(),
                source.getTotalPages()
        );
    }
}
