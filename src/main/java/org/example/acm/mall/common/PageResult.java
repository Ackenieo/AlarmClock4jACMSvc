package org.example.acm.mall.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "总条数") long total,
        @Schema(description = "当前页，从 1 开始") long page,
        @Schema(description = "每页条数") long size,
        @Schema(description = "数据") List<T> records) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(mapper).toList());
    }
}
